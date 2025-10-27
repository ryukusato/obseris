package org.yourcompany.yourproject.model;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import java.util.Queue;
import java.util.stream.Collectors;

import org.yourcompany.yourproject.config.ClearInfo;
import org.yourcompany.yourproject.config.SpinType;
import org.yourcompany.yourproject.controller.VersusManager;

/**
 * ゲームの進行状態とルール全体を管理するモデル。
 * 硬直時間（ARE/Line Clear Delay）を制御するステートマシンを導入。
 */
public class GameLogic {

    // --- 設定可能なパラメータ ---
    public static double SDF = 20.0;
    public static int ARE_FRAMES = 0;
    public static int LINE_CLEAR_DELAY_FRAMES = 0;

    // --- ゲーム進行状態 ---
    private enum GamePhase { PLAYING, CLEAR_ANIMATION, ENTRY_DELAY }
    private GamePhase phase = GamePhase.PLAYING;
    private int delayCounter = 0;

    // --- フィールド宣言 ---
    private final Board board;
    private Tetromino currentTetromino;
    private Tetromino holdTetromino;
    private final Queue<Shape.Tetrominoes> nextShapesQueue;
    private final List<Tetromino> nextQueue;
    private long score;
    private boolean isGameOver;
    private boolean canHold;
    private VersusManager versusManager;
    private int pendingGarbage = 0;
    private boolean lastActionWasRotation;
    private int lastKickIndex;
    private boolean isB2BActive;
    private int comboCount;
    private boolean isLockdownActive;
    private long lockdownStartTime;
    private int lockdownResetCount;
    private static final long LOCKDOWN_DELAY = 500;
    private static final int MAX_LOCKDOWN_RESETS = 15;
    private long lastFallTime;
    private final long fallInterval = 1000;
    private ClearInfo lastClearInfo = null;

    public GameLogic() {
        board = new Board();
        score = 0;
        isGameOver = false;
        holdTetromino = null;
        canHold = true;
        isB2BActive = false;
        comboCount = -1;
        nextShapesQueue = new LinkedList<>();
        fillNextShapesQueue();
        fillNextShapesQueue();
        nextQueue = new ArrayList<>();
        for (int i = 0; i < 5; i++) {
            nextQueue.add(createNewPieceFromQueue());
        }
        spawnNewTetromino();
    }

    public void setVersusManager(VersusManager manager) {
        this.versusManager = manager;
    }

    public void update() {
        if (isGameOver) return;

        switch (phase) {
            case CLEAR_ANIMATION:
                delayCounter--;
                if (delayCounter <= 0) {
                    board.clearLines();
                    phase = GamePhase.ENTRY_DELAY;
                    delayCounter = ARE_FRAMES;
                }
                break;
            case ENTRY_DELAY:
                delayCounter--;
                if (delayCounter <= 0) {
                    spawnNewTetromino();
                    phase = GamePhase.PLAYING;
                }
                break;
            case PLAYING:
                if (currentTetromino == null) {
                    spawnNewTetromino();
                    if (isGameOver) return;
                }
                updatePlaying();
                break;
        }
    }

    private void updatePlaying() {
        if (isGrounded()) {
            if (!isLockdownActive) {
                isLockdownActive = true;
                lockdownStartTime = System.currentTimeMillis();
            } else if (System.currentTimeMillis() - lockdownStartTime > LOCKDOWN_DELAY) {
                placeAndStartDelay();
            }
        } else {
            deactivateLockdown();
            long currentTime = System.currentTimeMillis();
            if (currentTime - lastFallTime > fallInterval) {
                moveDown();
            }
        }
    }

    private void placeAndStartDelay() {
        if (currentTetromino == null) return;
        lastClearInfo = null;
        board.placeTetromino(currentTetromino);

        if (isLockedOut(currentTetromino)) {
            setGameOver();
            return;
        }

        int linesToClearCount = countFullLines();
        SpinType spinType = getSpinType();
        boolean isDifficultClear = (spinType != SpinType.NONE) || linesToClearCount == 4;
        boolean b2bBonusApplied = this.isB2BActive && isDifficultClear;
        boolean isPerfectClear = linesToClearCount > 0 && board.isBoardEmpty();
        int attackPower = 0;

        if (linesToClearCount > 0) {
            comboCount++;
            attackPower = calculateAttack(linesToClearCount, spinType, b2bBonusApplied, comboCount);
            long baseScore = calculateScore(linesToClearCount, spinType);
            if (b2bBonusApplied) baseScore *= 1.5;
            score += baseScore + (comboCount > 0 ? 50L * comboCount : 0);
            this.isB2BActive = isDifficultClear;

            String clearType = createClearTypeText(linesToClearCount, spinType);
            this.lastClearInfo = new ClearInfo(clearType, linesToClearCount, spinType, b2bBonusApplied, comboCount, isPerfectClear);

            if (versusManager != null && attackPower > 0) {
                int remainingAttack = versusManager.offsetGarbage(this, attackPower);
                if (remainingAttack > 0) versusManager.sendAttack(this, remainingAttack);
            }
            if (isPerfectClear) {
                score += 3000;
                if (versusManager != null) versusManager.sendAttack(this, 10);
            }
            
            phase = GamePhase.CLEAR_ANIMATION;
            delayCounter = LINE_CLEAR_DELAY_FRAMES;
        } else {
            comboCount = -1;
            phase = GamePhase.ENTRY_DELAY;
            delayCounter = ARE_FRAMES;
        }

        currentTetromino = null;
    }

    private int countFullLines() {
        int count = 0;
        for (int y = 0; y < Board.TOTAL_BOARD_HEIGHT; y++) {
            boolean lineIsFull = true;
            for (int x = 0; x < Board.BOARD_WIDTH; x++) {
                if (board.getGridAt(x, y) == null) {
                    lineIsFull = false;
                    break;
                }
            }
            if (lineIsFull) count++;
        }
        return count;
    }

    private String createClearTypeText(int linesCleared, SpinType spinType) {
        String clearType = "";
        if (spinType == SpinType.T_SPIN) clearType = "T-SPIN ";
        else if (spinType == SpinType.T_SPIN_MINI) clearType = "T-SPIN MINI ";
        
        switch (linesCleared) {
            case 1: clearType += "SINGLE"; break;
            case 2: clearType += "DOUBLE"; break;
            case 3: clearType += "TRIPLE"; break;
            case 4: clearType = "QUAD"; break;
        }
        return clearType;
    }

    private void spawnNewTetromino() {
        if (applyGarbage()) return;
        currentTetromino = nextQueue.remove(0);
        nextQueue.add(createNewPieceFromQueue());
        currentTetromino.resetPositionAndState();
        canHold = true;
        resetLockdownState();
        lastFallTime = System.currentTimeMillis();
        lastActionWasRotation = false;
        if (!board.isValidPosition(currentTetromino.getCoords(), currentTetromino.getX(), currentTetromino.getY())) {
            setGameOver();
        }
    }

    private boolean applyGarbage() {
        if (this.pendingGarbage <= 0) return false;
        if (board.addGarbageLines(this.pendingGarbage)) {
            setGameOver();
            return true;
        }
        this.pendingGarbage = 0;
        return false;
    }

    private void moveDown() {
        if (isGameOver || currentTetromino == null) return;
        if (board.isValidPosition(currentTetromino.getCoords(), currentTetromino.getX(), currentTetromino.getY() + 1)) {
            currentTetromino.moveDown();
            lastFallTime = System.currentTimeMillis();
            lastActionWasRotation = false;
            this.lockdownResetCount = 0;
        }
    }

    public void rotateRight() { handleRotation(true); }
    public void rotateLeft() { handleRotation(false); }
    public void moveLeft() { handleMove(-1, 0); }
    public void moveRight() { handleMove(1, 0); }

    public void softDrop() {
        if (isGameOver || currentTetromino == null) return;
        long softDropInterval = (long) (this.fallInterval / SDF);
        long currentTime = System.currentTimeMillis();
        if (currentTime - lastFallTime >= softDropInterval) {
            moveDown();
            score += 1;
        }
    }

    public void hardDrop() {
        if (isGameOver || currentTetromino == null) return;
        int cellsDropped = 0;
        while (board.isValidPosition(currentTetromino.getCoords(), currentTetromino.getX(), currentTetromino.getY() + 1)) {
            currentTetromino.moveDown();
            cellsDropped++;
        }
        this.lockdownResetCount = 0;
        score += cellsDropped * 2L;
        placeAndStartDelay();
    }

    public void hold() {
        if (isGameOver || currentTetromino == null || !canHold || phase != GamePhase.PLAYING) return;
        Tetromino temp = currentTetromino;
        if (holdTetromino == null) {
            holdTetromino = temp;
            phase = GamePhase.ENTRY_DELAY;
            delayCounter = ARE_FRAMES;
            currentTetromino = null;
        } else {
            currentTetromino = holdTetromino;
            holdTetromino = temp;
            currentTetromino.resetPositionAndState();
            if (!board.isValidPosition(currentTetromino.getCoords(), currentTetromino.getX(), currentTetromino.getY())) {
                setGameOver();
            }
        }
        holdTetromino.resetPositionAndState();
        canHold = false;
        resetLockdownState();
    }
    
    private void handleRotation(boolean clockwise) {
        if (isGameOver || currentTetromino == null) return;
        RotationSystem.RotationResult result = RotationSystem.tryRotate(currentTetromino, board, clockwise);
        if (result.success()) {
            lastActionWasRotation = true;
            lastKickIndex = result.kickIndex();
            tryResetLockdownTimer();
        }
    }

    private void handleMove(int dx, int dy) {
        if (isGameOver || currentTetromino == null) return;
        if (board.isValidPosition(currentTetromino.getCoords(), currentTetromino.getX() + dx, currentTetromino.getY() + dy)) {
            currentTetromino.moveBy(dx, dy);
            lastActionWasRotation = false;
            tryResetLockdownTimer();
        }
    }
    
    private void setGameOver() {
        isGameOver = true;
        currentTetromino = null;
    }

    private void tryResetLockdownTimer() {
        if (isGrounded()) {
            if (lockdownResetCount < MAX_LOCKDOWN_RESETS) {
                lockdownStartTime = System.currentTimeMillis();
                lockdownResetCount++;
            } else {
                placeAndStartDelay();
            }
        }
    }

    public void receiveGarbage(int lines) { this.pendingGarbage += lines; }
    
    public int offsetGarbage(int attackPower) {
        this.pendingGarbage -= attackPower;
        int overflow = -this.pendingGarbage;
        if (this.pendingGarbage < 0) this.pendingGarbage = 0;
        return overflow > 0 ? overflow : 0;
    }
    
    private int calculateAttack(int linesCleared, SpinType spinType, boolean isB2B, int combo) {
        int attack = 0;
        if (spinType == SpinType.T_SPIN) attack = linesCleared * 2;
        else if (spinType == SpinType.T_SPIN_MINI) attack = (linesCleared == 2) ? 2 : 1;
        else {
            switch (linesCleared) {
                case 1: attack = 0; break;
                case 2: attack = 1; break;
                case 3: attack = 2; break;
                case 4: attack = 4; break;
            }
        }
        if (isB2B && attack > 0) attack += 1;
        if (combo >= 1) {
            int[] comboBonus = {0, 0, 1, 1, 2, 2, 3, 3, 4, 4, 4, 5};
            attack += (combo < comboBonus.length) ? comboBonus[combo] : comboBonus[comboBonus.length - 1];
        }
        return attack;
    }
    
    private SpinType getSpinType() {
        if (currentTetromino == null || currentTetromino.getPieceShape() != Shape.Tetrominoes.TShape || !lastActionWasRotation) return SpinType.NONE;
        int x = currentTetromino.getX();
        int y = currentTetromino.getY();
        int[][] corners = {{y - 1, x - 1}, {y - 1, x + 1}, {y + 1, x - 1}, {y + 1, x + 1}};
        int occupiedCorners = 0;
        for (int[] corner : corners) if (isOccupied(corner[1], corner[0])) occupiedCorners++;
        if (occupiedCorners >= 3) return SpinType.T_SPIN;
        if (lastKickIndex > 0 && occupiedCorners >= 2) return SpinType.T_SPIN_MINI;
        return SpinType.NONE;
    }

    private long calculateScore(int linesCleared, SpinType spinType) {
        return switch (spinType) {
            case T_SPIN -> switch (linesCleared) {
                case 1 -> 800; case 2 -> 1200; case 3 -> 1600; default -> 400;
            };
            case T_SPIN_MINI -> switch (linesCleared) {
                case 1 -> 200; case 2 -> 400; default -> 100;
            };
            case NONE -> switch (linesCleared) {
                case 1 -> 100; case 2 -> 300; case 3 -> 500; case 4 -> 800; default -> 0;
            };
        };
    }

    private void resetLockdownState() {
        isLockdownActive = false;
        lockdownResetCount = 0;
    }

    private void deactivateLockdown() { isLockdownActive = false; }

    private void fillNextShapesQueue() {
        List<Shape.Tetrominoes> shapes = Arrays.stream(Shape.Tetrominoes.values()).filter(s -> s != Shape.Tetrominoes.NoShape).collect(Collectors.toList());
        Collections.shuffle(shapes);
        nextShapesQueue.addAll(shapes);
    }

    private Tetromino createNewPieceFromQueue() {
        if (nextShapesQueue.size() <= 7) fillNextShapesQueue();
        return new Tetromino(nextShapesQueue.poll());
    }

    private boolean isGrounded() {
        if (currentTetromino == null) return false;
        return !board.isValidPosition(currentTetromino.getCoords(), currentTetromino.getX(), currentTetromino.getY() + 1);
    }

    private boolean isOccupied(int x, int y) {
        return x < 0 || x >= Board.BOARD_WIDTH || y < 0 || y >= Board.TOTAL_BOARD_HEIGHT || board.getGridAt(x, y) != null;
    }

    private boolean isLockedOut(Tetromino tetromino) {
        int[][] coords = tetromino.getCoords();
        int pieceY = tetromino.getY();
        int hiddenRows = Board.TOTAL_BOARD_HEIGHT - Board.VISIBLE_BOARD_HEIGHT;
        for (int[] p : coords) {
            if (pieceY + p[1] >= hiddenRows) return false;
        }
        return true;
    }
    
    public Board getBoard() { return board; }
    public Tetromino getCurrentTetromino() { return currentTetromino; }
    public Tetromino getHoldTetromino() { return holdTetromino; }
    public long getScore() { return score; }
    public List<Tetromino> getNextQueue() { return nextQueue; }
    public boolean isGameOver() { return isGameOver; }
    public int getPendingGarbage() { return pendingGarbage; }
    public ClearInfo getLastClearInfo() { return lastClearInfo; }
    public int getComboCount(){return comboCount;}
    public boolean isB2BActive(){return isB2BActive;}
}