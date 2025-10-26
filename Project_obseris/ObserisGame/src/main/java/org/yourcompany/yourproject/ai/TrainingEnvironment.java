package org.yourcompany.yourproject.ai;
import java.util.List;
import java.util.Queue;
import java.util.Scanner;
import java.util.stream.Collectors;

import org.yourcompany.yourproject.config.GameAction;
import org.yourcompany.yourproject.model.Board;
import org.yourcompany.yourproject.model.GameLogic;
import org.yourcompany.yourproject.player.Finesse;

import com.google.gson.Gson;

/**
 * Pythonからの標準入出力を受け取り、ゲーム環境を操作するためのラッパー。
 * AIの学習（トレーニング）専用の実行ファイルとなります。
 */
public class TrainingEnvironment {

    // Pythonから送られてくる高レベル命令のJSONに対応するクラス
    static class LandingSpotCommand {
        int x;
        int y;
        int rot; // 0-3 (回転状態)
        boolean hardDrop;
    }

    public static void main(String[] args) {
        GameLogic logic = new GameLogic();
        Scanner scanner = new Scanner(System.in);
        Gson gson = new Gson();

        // 最初の状態を送る
        System.out.println(gson.toJson(new GameState(logic)));
        System.out.flush();

        while (scanner.hasNextLine() && !logic.isGameOver()) {
            try {
                // 1. Pythonから「最終設置位置」の命令を受け取る
                String commandJson = scanner.nextLine();
                LandingSpotCommand command = gson.fromJson(commandJson, LandingSpotCommand.class);

                // 2. Finesse (経路探索) でキー操作に変換
                Queue<GameAction> actions = Finesse.findPath(logic, command.x, command.rot);
                
                // 3. 計算されたキー操作をすべて実行
                while (!actions.isEmpty()) {
                    executeAction(logic, actions.poll());
                    logic.update();
                }

                // 4. ハードドロップ (命令にあれば)
                if (command.hardDrop) {
                    executeAction(logic, GameAction.HARD_DROP);
                    // ハードドロップ後は自動でupdate()が内部で呼ばれる
                }
                
                // 5. update()を呼び、ゲームを次の状態（硬直時間など）に進める
                logic.update();

                // 6. 最終的なゲーム状態をPythonに送る
                System.out.println(gson.toJson(new GameState(logic)));
                System.out.flush();

            } catch (Exception e) {
                System.err.println("Error in Java: " + e.getMessage());
                break;
            }
        }
        scanner.close();
    }
    
    private static void executeAction(GameLogic logic, GameAction action) {
        if (action == null) return;
        switch (action) {
            case MOVE_LEFT -> logic.moveLeft();
            case MOVE_RIGHT -> logic.moveRight();
            case ROTATE_LEFT -> logic.rotateLeft();
            case ROTATE_RIGHT -> logic.rotateRight();
            case SOFT_DROP -> logic.softDrop();
            case HARD_DROP -> logic.hardDrop();
            case HOLD -> logic.hold();
            case NONE -> { /* 何もしない */ }
        }
    }
    
    /**
     * Pythonに渡すためのゲーム状態を保持するデータクラス。
     * AIの判断に必要な情報をすべて含めます。
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