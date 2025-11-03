package org.yourcompany.yourproject.player;

import org.yourcompany.yourproject.config.*;
import org.yourcompany.yourproject.model.*;
import java.util.LinkedList;
import java.util.Queue;

public class HumanPlayer implements Player {
    // PPTの一般的な設定値（フレーム数 @ 60FPS）
    private static final int DAS_FRAMES = 10;
    private static final int ARR_FRAMES = 2;

    private final Queue<GameAction> actionQueue = new LinkedList<>();
    
    private int dasCounter = 0;
    private int arrCounter = 0;
    private boolean isDasRight = false;

    // --- 先行入力用のバッファ ---
    private GameAction bufferedRotation = GameAction.NONE;
    private boolean bufferedHold = false;
    private Tetromino lastSeenTetromino = null;
    
    private boolean prevRotateLeft = false, prevRotateRight = false, prevHardDrop = false, prevHold = false;

    public HumanPlayer() {}

    /**
     * 毎フレーム、現在のキー入力状態とゲーム状態を受け取って内部状態を更新する
     * @param state 最新のキー入力状態
     * @param gameLogic 対応するプレイヤーのゲームロジック
     */
    public void update(InputState state, GameLogic gameLogic) {
        Tetromino currentTetromino = gameLogic.getCurrentTetromino();

        // 新しいピースが出現した瞬間の処理 (先行入力の実行)
        if (currentTetromino != null && lastSeenTetromino == null) {
            if (bufferedRotation != GameAction.NONE) {
                actionQueue.offer(bufferedRotation);
            }
            if (bufferedHold) {
                actionQueue.offer(GameAction.HOLD);
            }
        }
        // バッファは新しいピースの出現時にクリア
        if (currentTetromino != lastSeenTetromino) {
            bufferedRotation = GameAction.NONE;
            bufferedHold = false;
        }
        this.lastSeenTetromino = currentTetromino;
        
        // 入力処理
        if (currentTetromino != null) { // ピース操作中
            handleHorizontalMovement(state);
            handleSinglePressActions(state);
        } else { // 硬直時間中 (先行入力の受付)
            handleInitialActions(state);
        }

        // 状態更新
        prevRotateLeft = state.rotateLeft;
        prevRotateRight = state.rotateRight;
        prevHardDrop = state.hardDrop;
        prevHold = state.hold;
    }

    /**
     * 硬直時間中の入力（先行入力）をバッファに保存します。
     */
    private void handleInitialActions(InputState state) {
        if (state.rotateLeft) bufferedRotation = GameAction.ROTATE_LEFT;
        if (state.rotateRight) bufferedRotation = GameAction.ROTATE_RIGHT;
        if (state.hold) bufferedHold = true;
    }

    private void handleHorizontalMovement(InputState state) {
        boolean left = state.left;
        boolean right = state.right;

        if (left && !right) {
            if (isDasRight) dasCounter = 0; // DCD
            isDasRight = false;
            if (dasCounter == 0) actionQueue.offer(GameAction.MOVE_LEFT);
            dasCounter++;
            if (dasCounter > DAS_FRAMES) {
                arrCounter++;
                if (arrCounter >= ARR_FRAMES) {
                    actionQueue.offer(GameAction.MOVE_LEFT);
                    arrCounter = 0;
                }
            }
        } else if (right && !left) {
            if (!isDasRight) dasCounter = 0; // DCD
            isDasRight = true;
            if (dasCounter == 0) actionQueue.offer(GameAction.MOVE_RIGHT);
            dasCounter++;
            if (dasCounter > DAS_FRAMES) {
                arrCounter++;
                if (arrCounter >= ARR_FRAMES) {
                    actionQueue.offer(GameAction.MOVE_RIGHT);
                    arrCounter = 0;
                }
            }
        } else {
            dasCounter = 0;
            arrCounter = 0;
        }
    }
    
    private void handleSinglePressActions(InputState state) {
        if (state.rotateLeft && !prevRotateLeft) actionQueue.offer(GameAction.ROTATE_LEFT);
        if (state.rotateRight && !prevRotateRight) actionQueue.offer(GameAction.ROTATE_RIGHT);
        if (state.hardDrop && !prevHardDrop) actionQueue.offer(GameAction.HARD_DROP);
        if (state.hold && !prevHold) actionQueue.offer(GameAction.HOLD);
        if (state.softDrop) actionQueue.offer(GameAction.SOFT_DROP);
    }

    @Override
    public GameAction getAction(GameLogic gameState) {
        return actionQueue.poll();
    }
}