// ファイル: org/yourcompany/yourproject/ai/TrainingEnvironment.java

package org.yourcompany.yourproject.ai;
import java.util.List;
import java.util.Scanner;
import java.util.stream.Collectors;

import org.yourcompany.yourproject.config.GameAction;
import org.yourcompany.yourproject.model.Board;
import org.yourcompany.yourproject.model.GameLogic;

import com.google.gson.Gson;

/**
 * Pythonからの標準入出力を受け取り、ゲーム環境を操作するためのラッパー。
 * AIの学習（トレーニング）専用の実行ファイルとなります。
 * (AIの経路探索リスト("path")を受け取るように修正済み)
 */
public class TrainingEnvironment {

    // Python (agent.py) が送るJSONに対応するクラス
    static class AiCommand {
        List<String> path; // C++ (tetris_simulator.cpp) が生成したキー操作リスト
        boolean useHold;
    }

    public static void main(String[] args) {
        System.out.println("JAVA_PROCESS_STARTED"); 
        System.out.flush();
        GameLogic logic = new GameLogic();
        Scanner scanner = new Scanner(System.in);
        Gson gson = new Gson();

        // 最初の状態を送る
        System.out.println(gson.toJson(new GameState(logic)));
        System.out.flush();

        while (scanner.hasNextLine() && !logic.isGameOver()) {
            try {
                // 1. Pythonから「キー操作リスト」の命令を受け取る
                String commandJson = scanner.nextLine();
                AiCommand command = gson.fromJson(commandJson, AiCommand.class); // LandingSpotCommand から AiCommand に変更

                if (command == null || command.path == null) {
                    throw new Exception("無効なコマンドを受信しました: " + commandJson);
                }

                // 2. ホールドを実行 (命令にあれば)
                if (command.useHold) {
                    executeAction(logic, GameAction.HOLD);
                    logic.update(); // ホールド後、次のピースが出るまでロジックを更新
                }
                
                // 3. 計算されたキー操作 (path) を順に実行
                // (Finesse.findPath の呼び出しは削除)
                for (String actionName : command.path) {
                    if (logic.isGameOver()) break; // 途中でゲームオーバーになったら中断

                    // 文字列を GameAction enum に変換
                    GameAction action = GameAction.valueOf(actionName.toUpperCase());
                    executeAction(logic, action);
                    
                    // ハードドロップ以外は、手動でロジックを更新
                    // (ハードドロップは内部で自動的にロジック更新(placeAndStartDelay)が走るため)
                    if (action != GameAction.HARD_DROP) {
                        logic.update();
                    }
                }
                
                // 4. (安全策) 
                // AIのパス実行により、ミノが置かれ硬直時間(ENTRY_DELAY)に入っているはず。
                // 次のミノをスポーンさせるために update() を呼ぶ。
                if (logic.getCurrentTetromino() == null && !logic.isGameOver()) {
                     logic.update();
                }

                // 5. 最終的なゲーム状態をPythonに送る
                System.out.println(gson.toJson(new GameState(logic)));
                System.out.flush();

            } catch (Exception e) {
                System.err.println("Error in Java: " + e.getMessage());
                e.printStackTrace(System.err); // AI学習時にエラー詳細がわかるようスタックトレースを出力
                break;
            }
        }
        scanner.close();
    }
    
    private static void executeAction(GameLogic logic, GameAction action) {
        if (action == null) return;
        switch (action) {
            case MOVE_LEFT: logic.moveLeft(); break;
            case MOVE_RIGHT: logic.moveRight(); break;
            case ROTATE_LEFT: logic.rotateLeft(); break;
            case ROTATE_RIGHT: logic.rotateRight(); break;
            case SOFT_DROP: logic.softDrop(); break;
            case HARD_DROP: logic.hardDrop(); break;
            case HOLD: logic.hold(); break;
            case NONE: /* 何もしない */ break;
        }
    }
    
    /**
     * Pythonに渡すためのゲーム状態を保持するデータクラス。
     * (このクラスはAIの要求と一致しているため変更不要)
     */
    static class GameState {
        public final boolean isGameOver;
        public final double[][] board; // 盤面 (色ではなく、0:空, 1:ブロックで表現)
        public final String currentMino;
        public final String holdMino;
        public final List<String> nextMinos;
        public final int pendingGarbage;
        public final long score;
        public final int comboCount;
        public final boolean isB2BActive;

        public GameState(GameLogic logic) {
            this.isGameOver = logic.isGameOver();
            this.score = logic.getScore();
            this.pendingGarbage = logic.getPendingGarbage();
            this.comboCount = logic.getComboCount();
            this.isB2BActive = logic.isB2BActive();

            // 盤面を 0/1 の配列に変換
            this.board = new double[Board.TOTAL_BOARD_HEIGHT][Board.BOARD_WIDTH];
            for (int y = 0; y < Board.TOTAL_BOARD_HEIGHT; y++) {
                for (int x = 0; x < Board.BOARD_WIDTH; x++) {
                    this.board[y][x] = (logic.getBoard().getGridAt(x, y) != null) ? 1.0 : 0.0;
                }
            }
            
            this.currentMino = (logic.getCurrentTetromino() != null) ? 
                                logic.getCurrentTetromino().getPieceShape().name() : null;
            this.holdMino = (logic.getHoldTetromino() != null) ? 
                                logic.getHoldTetromino().getPieceShape().name() : null;
            this.nextMinos = logic.getNextQueue().stream()
                                  .map(t -> t.getPieceShape().name())
                                  .collect(Collectors.toList());
        }
    }
}