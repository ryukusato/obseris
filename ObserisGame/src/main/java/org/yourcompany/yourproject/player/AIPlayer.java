package org.yourcompany.yourproject.player;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.nio.FloatBuffer;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Queue;
import java.util.Set;
import java.util.concurrent.ConcurrentLinkedQueue;

import org.yourcompany.yourproject.config.GameAction;
import org.yourcompany.yourproject.config.SpinType;
import org.yourcompany.yourproject.model.Board;
import org.yourcompany.yourproject.model.GameLogic;
import org.yourcompany.yourproject.model.RotationSystem;
import org.yourcompany.yourproject.model.Shape;
import org.yourcompany.yourproject.model.Tetromino;

import ai.onnxruntime.OnnxTensor;
import ai.onnxruntime.OrtEnvironment;
import ai.onnxruntime.OrtException;
import ai.onnxruntime.OrtSession;

/**
 * CNN (ONNX) モデルで盤面評価と即時報酬の計算を行うAIプレイヤー。
 * Python の agent.py のロジックを移植。
 */
public class AIPlayer implements Player {

    private final ConcurrentLinkedQueue<GameAction> actionQueue = new ConcurrentLinkedQueue<>();
    private volatile boolean isThinking = false;

    // --- ONNXモデル関連 ---
    private final OrtEnvironment env;
    private final OrtSession session;
    private final String inputNameBoard;
    private final String inputNameFeature;
    private volatile GameLogic opponentLogic;

    // --- 移植された報酬 (Reward) 定義 ---
    // (agent.py の REWARDS 定義に基づく)
    private static final Map<Integer, Double> REWARD_LINE_CLEAR = Map.of(
        0, 0.0,
        1, 1.0,
        2, 3.0,
        3, 6.0,
        4, 10.0
    );
    private static final double REWARD_T_SPIN = 5.0;
    private static final double REWARD_T_SPIN_MINI = 2.0;
    private static final double REWARD_B2B = 1.5;
    private static final double REWARD_COMBO = 0.5;
    private static final double REWARD_ATTACK = 0.8;
    private static final Map<Shape.Tetrominoes, Integer> SHAPE_TO_INDEX = Map.of(
        Shape.Tetrominoes.TShape, 0,
        Shape.Tetrominoes.ZShape, 1,
        Shape.Tetrominoes.SShape, 2,
        Shape.Tetrominoes.LineShape, 3,
        Shape.Tetrominoes.SquareShape, 4,
        Shape.Tetrominoes.LShape, 5,
        Shape.Tetrominoes.MirroredLShape, 6,
        Shape.Tetrominoes.NoShape, -1 // マッピング外
    );
    private static final int NUM_SHAPE_TYPES = 7;
    private static final int FEATURE_INPUT_SIZE = (NUM_SHAPE_TYPES * 5 * 2) + 2;
    // --- AI思考用内部クラス (変更なし) ---
    // (SearchState と LandingSpot は元の AIPlayer.java と同じ)
    private record SearchState(int x, int y, int rot, GameAction lastAction) {
        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            SearchState that = (SearchState) o;
            return x == that.x && y == that.y && rot == that.rot;
        }
        @Override
        public int hashCode() { return Objects.hash(x, y, rot); }
    }
    private static class LandingSpot {
        public final List<GameAction> path;
        public final Board futureBoard;
        public final int linesCleared;
        public final SpinType spinType;
        public final long scoreDelta; // (元のロジック: スコア)
        public final int attackPower;
        public final int pendingGarbageAfter;
        public final int comboCountAfter;
        public final boolean b2bActiveAfter;
        public final boolean isGameOver;
        public final boolean usedHold;
        public final int finalX, finalY, finalRot;
        public double aiScore = Double.NEGATIVE_INFINITY;
        public final List<Tetromino> futureNextQueue;


        public LandingSpot(List<GameAction> path, Board futureBoard, int linesCleared,
                           SpinType spinType, long scoreDelta, int attackPower,
                           int pendingGarbageAfter, int comboCountAfter,
                           boolean b2bActiveAfter, boolean isGameOver, boolean usedHold,
                           int finalX, int finalY, int finalRot,
                           List<Tetromino> futureNextQueue) {
            this.path = path;
            this.futureBoard = futureBoard;
            this.linesCleared = linesCleared;
            this.spinType = spinType;
            this.scoreDelta = scoreDelta;
            this.attackPower = attackPower;
            this.pendingGarbageAfter = pendingGarbageAfter;
            this.comboCountAfter = comboCountAfter;
            this.b2bActiveAfter = b2bActiveAfter;
            this.isGameOver = isGameOver;
            this.usedHold = usedHold;
            this.finalX = finalX;
            this.finalY = finalY;
            this.finalRot = finalRot;
            this.futureNextQueue = futureNextQueue;
        }
    }

    // --- AIPlayer メインロジック ---

    /**
     * コンストラクタでONNXモデルをロードする
     */
    public AIPlayer(String modelResourceName) {
    //"tetris_model_examination.onnx";
    String dataResourceName  = modelResourceName + ".data";

    File tempDir = null;
    File tempOnnxFile = null;
    File tempDataFile = null;

    try {
        this.env = OrtEnvironment.getEnvironment();

        // 1) 一時ディレクトリを作る（名前はランダムでもOK）
        tempDir = Files.createTempDirectory("onnx_model_").toFile();
        tempDir.deleteOnExit();

        // 2) リソースを「元の名前」のまま一時ディレクトリへコピーする
        tempOnnxFile = new File(tempDir, modelResourceName);
        tempDataFile  = new File(tempDir, dataResourceName);

        copyResourceToFile(modelResourceName, tempOnnxFile);
        copyResourceToFile(dataResourceName, tempDataFile);

        // 3) セッション生成（ONNX Runtime は同じディレクトリにある .data を見つけられる）
        String modelPath = tempOnnxFile.getAbsolutePath();
        this.session = env.createSession(modelPath, new OrtSession.SessionOptions());

        this.inputNameBoard = "board_tensor_input";
        this.inputNameFeature = "feature_tensor_input";
        if (!this.session.getInputInfo().containsKey(this.inputNameBoard) ||
            !this.session.getInputInfo().containsKey(this.inputNameFeature)) {
            throw new RuntimeException("モデルに入力名 " + this.inputNameBoard + " または " + this.inputNameFeature + " が見つかりません。");
            }
        System.out.println("ONNX v2 Model (from temp dir) loaded.");

        // 4) JVM 終了時に一時ディレクトリごと削除（簡易）
        final File finalDir = tempDir;
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            if (finalDir.exists()) {
                for (File f : finalDir.listFiles()) {
                    f.delete();
                }
                finalDir.delete();
            }
        }));

    } catch (Exception e) {
        e.printStackTrace();
        if (tempOnnxFile != null) tempOnnxFile.delete();
        if (tempDataFile != null) tempDataFile.delete();
        if (tempDir != null) tempDir.delete();
        throw new RuntimeException("Failed to load ONNX model from temp file", e);
        }
    }
    /**
     * ★ ゲームエンジンから相手の参照を受け取るためのメソッド
     * @param opponentLogic 相手プレイヤーの GameLogic
     */
    public void setOpponent(GameLogic opponentLogic) {
        this.opponentLogic = opponentLogic;
    }

    @Override
    public GameAction getAction(GameLogic gameState) {
        GameAction action = actionQueue.poll();
        if (action != null) {
            return action;
        }
        if (!isThinking && actionQueue.isEmpty()) {
            requestBestMove(gameState);
        }
        return GameAction.NONE;
    }

    public void requestBestMove(GameLogic mylogic) {
        final GameLogic currentOpponentLogic = this.opponentLogic;
        if (isThinking || !actionQueue.isEmpty() || mylogic.isGameOver()) {
            return;
        }
        isThinking = true;

        new Thread(() -> {
            try {
                LandingSpot bestMove = findBestMove(mylogic,currentOpponentLogic);
                if (bestMove != null) {
                    if (bestMove.usedHold) {
                        // 1. 最初に「HOLD」アクションをキューに追加する
                        actionQueue.add(GameAction.HOLD);
                    }
                    actionQueue.addAll(bestMove.path);
                    actionQueue.add(GameAction.HARD_DROP);
                }
            } catch (Exception e) {
                 e.printStackTrace();
            } finally {
                isThinking = false;
            }
        }).start();
    }

    private LandingSpot findBestMove(GameLogic myLogic, GameLogic opponentLogic) {
        List<LandingSpot> allPossibleMoves = new ArrayList<>();
        
        // 1. 現行ミノでの手をすべて計算 (変更なし)
        if (myLogic.getCurrentTetromino() != null) {
            generateMovesForPiece(myLogic, myLogic.getCurrentTetromino(), false, allPossibleMoves);
        }

        // 2. ホールド可能な場合、ホールドした後の手をすべて計算 (変更なし)
        if (myLogic.getCanHold()) { 
            Tetromino holdPiece = myLogic.getHoldTetromino();
            Tetromino pieceToSimulate;
            
            if (holdPiece == null) {
                if (myLogic.getNextQueue() != null && !myLogic.getNextQueue().isEmpty()) {
                    pieceToSimulate = new Tetromino(myLogic.getNextQueue().get(0).getPieceShape());
                    generateMovesForPiece(myLogic, pieceToSimulate, true, allPossibleMoves);
                }
            } else {
                pieceToSimulate = new Tetromino(holdPiece.getPieceShape());
                generateMovesForPiece(myLogic, pieceToSimulate, true, allPossibleMoves);
            }
        }
        try {
            evaluateMovesWithCNN(allPossibleMoves, myLogic, opponentLogic);
        } catch (OrtException e) {
            e.printStackTrace();
            return null; // 推論失敗
        }

        // 3. 全ての「あり得る手」をAIの評価関数でスコア付け
        LandingSpot bestSpot = null;
        double bestScore = Double.NEGATIVE_INFINITY;

        for (LandingSpot spot : allPossibleMoves) {
            double score;
            if (spot.isGameOver) {
                score = -999999999.0;
            } else {
                score = spot.aiScore;
            }
            
            if (score > bestScore) {
                bestScore = score;
                bestSpot = spot;
            }
        }
        return bestSpot;
    }


    /**
     * (★ 移植) CNN (ONNX) モデルで盤面を評価する
     * @param board 評価対象の盤面 (40x10)
     * @return CNNが予測した盤面の「価値 (Value)」 V(s')
     */
    private void evaluateMovesWithCNN(List<LandingSpot> moves, GameLogic myLogic, GameLogic opponentLogic) throws OrtException {
        
        int batchSize = moves.size();
        if (batchSize == 0) return;

        // --- 1. 定数情報 (全バッチ共通) を取得 ---
        Board opponentCurrentBoard = opponentLogic.getBoard();
        List<Tetromino> opponentCurrentQueue = opponentLogic.getNextQueue();
        float opponentCurrentGarbage = (float) opponentLogic.getPendingGarbage();

        // --- 2. 2つの入力テンソル用のバッファを作成 ---
        // (Batch, 2, 40, 10)
        long[] boardShape = {batchSize, 2, Board.TOTAL_BOARD_HEIGHT, Board.BOARD_WIDTH};
        int boardDataSize = batchSize * 2 * Board.TOTAL_BOARD_HEIGHT * Board.BOARD_WIDTH;
        FloatBuffer boardInputBuffer = FloatBuffer.allocate(boardDataSize);

        // (Batch, 72)
        long[] featureShape = {batchSize, FEATURE_INPUT_SIZE};
        int featureDataSize = batchSize * FEATURE_INPUT_SIZE;
        FloatBuffer featureInputBuffer = FloatBuffer.allocate(featureDataSize);

        // --- 3. バッファにデータを充填 ---
        for (int i = 0; i < batchSize; i++) {
            LandingSpot spot = moves.get(i);
            
            // A. 盤面バッファ [N, 2, 40, 10]
            int boardOffset = i * 2 * (Board.TOTAL_BOARD_HEIGHT * Board.BOARD_WIDTH);
            
            // A-1. チャンネル 0: 自分の未来盤面
            fillBoardBuffer(boardInputBuffer, spot.futureBoard, boardOffset);
            
            // A-2. チャンネル 1: 相手の現在盤面
            fillBoardBuffer(boardInputBuffer, opponentCurrentBoard, boardOffset + (Board.TOTAL_BOARD_HEIGHT * Board.BOARD_WIDTH));

            
            // B. 特徴量バッファ [N, 72]
            int featureOffset = i * FEATURE_INPUT_SIZE;
            
            List<Tetromino> queueForThisMove = spot.futureNextQueue;

            // B-1. 自分のネクストキュー (35)
            fillQueueBuffer(featureInputBuffer, queueForThisMove, featureOffset);
            
            // B-2. T相手のネクストキュー (35)
            fillQueueBuffer(featureInputBuffer, opponentCurrentQueue, featureOffset + 35);
            
            // B-3. 自分の未来のお邪魔 (1)
            featureInputBuffer.put(featureOffset + 70, (float) spot.pendingGarbageAfter);
            
            // B-4. 相手の現在のお邪魔 (1)
            featureInputBuffer.put(featureOffset + 71, opponentCurrentGarbage);
        }

        boardInputBuffer.rewind();
        featureInputBuffer.rewind();

        // --- 4. テンソルを作成し、マップに格納 ---
        OnnxTensor boardTensor = OnnxTensor.createTensor(this.env, boardInputBuffer, boardShape);
        OnnxTensor featureTensor = OnnxTensor.createTensor(this.env, featureInputBuffer, featureShape);

        Map<String, OnnxTensor> inputs = new HashMap<>();
        inputs.put(this.inputNameBoard, boardTensor);
        inputs.put(this.inputNameFeature, featureTensor);

        // --- 5. モデルの実行 (バッチ推論) ---
        try (OrtSession.Result result = this.session.run(inputs)) {
            
            // (Batch, 1) の形状で出力される
            float[][] outputValues = (float[][]) result.get(0).getValue();
            
            // --- 6. 結果を LandingSpot に書き戻す ---
            for (int i = 0; i < batchSize; i++) {
                LandingSpot spot = moves.get(i);
                               
                // V(s') (CNNの評価値)
                double value = (double) outputValues[i][0];
                
                // 総合スコア = r + V(s')
                spot.aiScore = value;
            }
            
        } // result.close()

        // テンソルをクローズ
        boardTensor.close();
        featureTensor.close();
    }
    
    private void fillBoardBuffer(FloatBuffer buffer, Board board, int offset) {
        int pos = offset;
        for (int y = 0; y < Board.TOTAL_BOARD_HEIGHT; y++) {
            for (int x = 0; x < Board.BOARD_WIDTH; x++) {
                float value = (board.getGridAt(x, y) != null) ? 1.0f : 0.0f;
                buffer.put(pos++, value);
            }
        }
    }


    private void fillQueueBuffer(FloatBuffer buffer, List<Tetromino> queue, int offset) {
        // 5 (個) x 7 (種類) = 35 float の領域をゼロクリア
        for (int i = 0; i < 35; i++) {
            buffer.put(offset + i, 0.0f);
        }

        for (int i = 0; i < 5; i++) {
            if (queue != null && i < queue.size()) {
                Tetromino t = queue.get(i);
                if (t != null && t.getPieceShape() != null) {
                    int shapeIndex = SHAPE_TO_INDEX.getOrDefault(t.getPieceShape(), -1);
                    if (shapeIndex != -1) {
                        // (i * 7) + shapeIndex の位置を 1.0f にする
                        buffer.put(offset + (i * NUM_SHAPE_TYPES) + shapeIndex, 1.0f);
                    }
                }
            }
        }
    }

    // --- 以下のメソッド群は、元の AIPlayer.java から変更ありません ---
    // (探索ロジックはCNNでも共通して必要なため)

    private void generateMovesForPiece(GameLogic logic, Tetromino piece, boolean isHoldMove, List<LandingSpot> results) {
        if (piece == null || piece.getPieceShape() == Shape.Tetrominoes.NoShape) return;
        
        Board currentBoard = logic.getBoard();
        Set<SearchState> visited = new HashSet<>();
        Queue<SearchState> queue = new LinkedList<>();
        Map<SearchState, SearchState> parentMap = new HashMap<>();

        piece.resetPositionAndState(); 
        SearchState startState = new SearchState(piece.getX(), piece.getY(), 0, GameAction.NONE);

        if (!currentBoard.isValidPosition(piece.getCoordsForRotation(0), startState.x(), startState.y())) {
            LandingSpot gameOverSpot = calculateLandingResult(
                logic, 
                currentBoard, piece.getPieceShape(),
                startState.x(), startState.y(), startState.rot(),
                GameAction.NONE, isHoldMove
            );
            List<Tetromino> futureQueueForSpot = getFutureQueue(logic, isHoldMove);
            LandingSpot finalGameOverSpot = new LandingSpot(
                new ArrayList<>(), gameOverSpot.futureBoard, gameOverSpot.linesCleared,
                gameOverSpot.spinType, gameOverSpot.scoreDelta, gameOverSpot.attackPower,
                gameOverSpot.pendingGarbageAfter, gameOverSpot.comboCountAfter,
                gameOverSpot.b2bActiveAfter, gameOverSpot.isGameOver, isHoldMove,
                startState.x(), startState.y(), startState.rot(),
                futureQueueForSpot // ★ 修正: 追加
            );
            results.add(finalGameOverSpot);
            return;
        }

        queue.add(startState);
        visited.add(startState);
        parentMap.put(startState, null);

        Set<String> foundLandings = new HashSet<>();

        while (!queue.isEmpty()) {
            SearchState currentState = queue.poll();
            
            int finalY = dropPiece(currentBoard, piece.getCoordsForRotation(currentState.rot()), currentState.x(), currentState.y());
            String landingKey = String.format("%d,%d,%d", currentState.x(), finalY, currentState.rot());

            if (foundLandings.add(landingKey)) {
                LandingSpot spot = calculateLandingResult(
                    logic,
                    currentBoard, piece.getPieceShape(),
                    currentState.x(), finalY, currentState.rot(),
                    currentState.lastAction(), isHoldMove
                );
                
                List<GameAction> path = reconstructPath(parentMap, currentState);
                List<Tetromino> futureQueueForSpot = getFutureQueue(logic, isHoldMove);
                
                LandingSpot finalSpot = new LandingSpot(path, spot.futureBoard, spot.linesCleared, 
                    spot.spinType, spot.scoreDelta, spot.attackPower, 
                    spot.pendingGarbageAfter, spot.comboCountAfter, 
                    spot.b2bActiveAfter, spot.isGameOver, isHoldMove,
                    currentState.x(), finalY, currentState.rot(),futureQueueForSpot);

                results.add(finalSpot);
            }

            final GameAction[] ACTIONS = {
                GameAction.MOVE_LEFT, GameAction.MOVE_RIGHT, GameAction.ROTATE_LEFT, 
                GameAction.ROTATE_RIGHT, GameAction.SOFT_DROP
            };

            for (GameAction action : ACTIONS) {
                tryMove(action, currentState, piece, currentBoard, queue, visited, parentMap);
            }
        }
    }

    private List<Tetromino> getFutureQueue(GameLogic logic, boolean isHoldMove) {
        boolean isHoldEmpty = (logic.getHoldTetromino() == null);
        
        if (isHoldMove && isHoldEmpty) {
            // "空のHold" を使った場合 -> キューが1つズレる (offset=1)
            // [1番目, 2番目, 3番目, 4番目, 5番目]
            List<Tetromino> currentQueue = logic.getNextQueue(); 
            if (currentQueue == null) {
                return new ArrayList<>(); // 空のリストを返す
            }
            
            // 新しいリスト [2番目, 3番目, 4番目, 5番目, 6番目] を作成
            List<Tetromino> shiftedQueue = new ArrayList<>();
            
            // 1. [2番目]～[5番目]をコピー
            for (int i = 1; i < currentQueue.size(); i++) {
                shiftedQueue.add(currentQueue.get(i));
            }
            // 2. ★ 修正: GameLogic から「6個目のミノ」を取得して追加する
            Tetromino sixthPiece = logic.getSixthPiece(); // <-- 先ほど追加したメソッドを呼び出す
            if (sixthPiece != null) {
                shiftedQueue.add(sixthPiece);
            }
            // これで shiftedQueue は 5個のミノのリストになる
            return shiftedQueue;
        } else {
            // それ以外 (Holdしない / Hold交換) の場合 -> 現在のキュー (offset=0)
            return logic.getNextQueue();
        }
    }

    private void tryMove(GameAction action, SearchState currentState, Tetromino piece, Board board, 
                         Queue<SearchState> queue, Set<SearchState> visited, 
                         Map<SearchState, SearchState> parentMap) {
        
        SearchState nextState = null;
        int[][] currentCoords = piece.getCoordsForRotation(currentState.rot());

        switch (action) {
            case MOVE_LEFT:
                int nxLeft = currentState.x() - 1;
                if (board.isValidPosition(currentCoords, nxLeft, currentState.y())) {
                    nextState = new SearchState(nxLeft, currentState.y(), currentState.rot(), action);
                }
                break;
            case MOVE_RIGHT:
                int nxRight = currentState.x() + 1;
                if (board.isValidPosition(currentCoords, nxRight, currentState.y())) {
                    nextState = new SearchState(nxRight, currentState.y(), currentState.rot(), action);
                }
                break;
            case SOFT_DROP:
                int nyDrop = currentState.y() + 1;
                if (board.isValidPosition(currentCoords, currentState.x(), nyDrop)) {
                    nextState = new SearchState(currentState.x(), nyDrop, currentState.rot(), action);
                }
                break;
            case ROTATE_RIGHT:
            case ROTATE_LEFT:
                RotationSystem.RotationResult result = RotationSystem.simulateRotation(
                    currentState.x(), currentState.y(), currentState.rot(),
                    piece.getPieceShape(), board, (action == GameAction.ROTATE_RIGHT)
                );
                if (result.success()) {
                    nextState = new SearchState(result.newX(), result.newY(), result.newRot(), action);
                }
                break;
            default:
                break;
        }

        if (nextState != null && !visited.contains(nextState)) {
            visited.add(nextState);
            parentMap.put(nextState, currentState);
            queue.add(nextState);
        }
    }

    private LandingSpot calculateLandingResult(
        GameLogic logic,
        Board boardBeforePlace, Shape.Tetrominoes shape,
        int finalX, int finalY, int finalRot,
        GameAction lastAction, boolean isHoldMove
    ) {
        Board futureBoard = new Board(boardBeforePlace); 
        Tetromino landingPiece = new Tetromino(shape);
        landingPiece.setSimulatedState(finalX, finalY, finalRot); 
        
        boolean isGameOver = isLockedOut(landingPiece.getCoords(), finalY);
        futureBoard.placeTetromino(landingPiece);

        boolean wasRotation = (lastAction == GameAction.ROTATE_LEFT || lastAction == GameAction.ROTATE_RIGHT);
        SpinType spinType = getSpinType(futureBoard, landingPiece, wasRotation);

        int linesCleared = futureBoard.countFullLines();
        if (linesCleared > 0) {
            futureBoard.clearLines();
        }

        int currentCombo = logic.getComboCount();
        boolean currentB2B = logic.isB2BActive();
        
        int comboCountAfter;
        boolean b2bActiveAfter = currentB2B;
        boolean usedHold = logic.getCanHold();
        int pendingGarbageAfter = logic.getPendingGarbage();
        int attackPower = 0;
        long scoreDelta = 0;
        boolean isDifficultClear = (spinType != SpinType.NONE) || (linesCleared == 4);

        if (linesCleared > 0) {
            comboCountAfter = currentCombo + 1;
            boolean b2bBonusApplied = currentB2B && isDifficultClear;
            
            // ★ JavaのGameLogic が持つ火力計算ロジックを使用
            int baseAttack = calculateAttack(linesCleared, spinType, b2bBonusApplied, comboCountAfter);
            if (baseAttack > 0) {
                int remainingGarbage = pendingGarbageAfter - baseAttack;
                if (remainingGarbage < 0) {
                    attackPower = -remainingGarbage;
                    pendingGarbageAfter = 0;
                } else {
                    pendingGarbageAfter = remainingGarbage;
                    attackPower = 0;
                }
            }
            
            // ★ JavaのGameLogic が持つスコア計算ロジックを使用
            scoreDelta = calculateScore(linesCleared, spinType);
            if (b2bBonusApplied) scoreDelta = Math.round(scoreDelta * 1.5);
            scoreDelta += (comboCountAfter > 0) ? 50L * comboCountAfter : 0;
            
            b2bActiveAfter = isDifficultClear;
            
            if (futureBoard.isBoardEmpty()) {
                attackPower += 10;
                scoreDelta += 3000;
            }
        } else {
            comboCountAfter = -1;
        }

        return new LandingSpot(
            new ArrayList<>(),
            futureBoard, linesCleared, spinType,
            scoreDelta, attackPower, pendingGarbageAfter,
            comboCountAfter, b2bActiveAfter, isGameOver, isHoldMove,
            finalX, finalY, finalRot,null
        );
    }
    
    private int dropPiece(Board board, int[][] coords, int startX, int startY) {
        int y = startY;
        while (board.isValidPosition(coords, startX, y + 1)) {
            y++;
        }
        return y;
    }

    private List<GameAction> reconstructPath(Map<SearchState, SearchState> parentMap, SearchState endState) {
        LinkedList<GameAction> path = new LinkedList<>();
        SearchState curr = endState;
        
        while (curr != null && parentMap.get(curr) != null) {
            if (curr.lastAction() != GameAction.NONE) {
                path.addFirst(curr.lastAction());
            }
            curr = parentMap.get(curr);
        }
        return path;
    }

    private boolean isLockedOut(int[][] coords, int pieceY) {
        int hiddenRows = Board.TOTAL_BOARD_HEIGHT - Board.VISIBLE_BOARD_HEIGHT;
        for (int[] p : coords) {
            if (pieceY + p[1] >= hiddenRows) return false;
        }
        return true;
    }

    private SpinType getSpinType(Board board, Tetromino tetromino, boolean lastActionWasRotation) {
        if (tetromino.getPieceShape() != Shape.Tetrominoes.TShape || !lastActionWasRotation) {
            return SpinType.NONE;
        }
        int x = tetromino.getX();
        int y = tetromino.getY();
        int rotation = tetromino.getRotationState();

        int[][] corners = {{y - 1, x - 1}, {y - 1, x + 1}, {y + 1, x - 1}, {y + 1, x + 1}};
        int occupiedCorners = 0;
        for (int[] corner : corners) if (isOccupied(board, corner[1], corner[0])) occupiedCorners++;

        if (occupiedCorners < 3) return SpinType.NONE;

        int[][] frontCorners;
        switch (rotation) {
            case 0: frontCorners = new int[][]{{y - 1, x - 1}, {y - 1, x + 1}}; break;
            case 1: frontCorners = new int[][]{{y - 1, x + 1}, {y + 1, x + 1}}; break;
            case 2: frontCorners = new int[][]{{y + 1, x - 1}, {y + 1, x + 1}}; break;
            default: frontCorners = new int[][]{{y - 1, x - 1}, {y + 1, x - 1}}; break;
        }

        int occupiedFrontCorners = 0;
        for (int[] corner : frontCorners) if (isOccupied(board, corner[1], corner[0])) occupiedFrontCorners++;

        return (occupiedFrontCorners == 2) ? SpinType.T_SPIN : SpinType.T_SPIN_MINI;
    }

    private boolean isOccupied(Board board, int x, int y) {
        return x < 0 || x >= Board.BOARD_WIDTH || y < 0 || y >= Board.TOTAL_BOARD_HEIGHT || board.getGridAt(x, y) != null;
    }

    // 火力とスコアの計算は、JavaのGameLogic のロジックをそのまま流用
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
    
    
    private void copyResourceToFile(String resourceName, File dest) throws IOException {
        try (InputStream in = getClass().getClassLoader().getResourceAsStream(resourceName)) {
            if (in == null) throw new FileNotFoundException("Resource not found: " + resourceName);
            Files.copy(in, dest.toPath(), StandardCopyOption.REPLACE_EXISTING);
            dest.deleteOnExit();
        }
    }

}