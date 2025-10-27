package org.yourcompany.yourproject.model;
import java.awt.Color;
import java.util.Arrays;

public class Board {
    public static final int BOARD_WIDTH = 10;
    public static final int VISIBLE_BOARD_HEIGHT = 20; // プレイヤーに見える盤面の高さ
    public static final int TOTAL_BOARD_HEIGHT = 40;   // 内部データとしての盤面の全高

    private final Color[][] grid;

    public Board() {
        grid = new Color[TOTAL_BOARD_HEIGHT][BOARD_WIDTH];
    }

    /**
     * 指定された座標が盤面内で有効かつ空であるかをチェックします。
     */
    public boolean isValidPosition(int[][] shape, int pieceX, int pieceY) {
        for (int[] point : shape) {
            int boardX = pieceX + point[0];
            int boardY = pieceY + point[1];

            // 盤面の範囲外かチェック
            if (boardX < 0 || boardX >= BOARD_WIDTH || boardY < 0 || boardY >= TOTAL_BOARD_HEIGHT) {
                return false;
            }
            // 他ブロックとの衝突チェック
            if (grid[boardY][boardX] != null) {
                return false;
            }
        }
        return true;
    }

    /**
     * テトリミノを盤面に固定します。
     */
    public void placeTetromino(Tetromino tetromino) {
        if (tetromino == null) return;
        Color color = tetromino.getColor();
        int pieceX = tetromino.getX();
        int pieceY = tetromino.getY();
        int[][] coords = tetromino.getCoords();
        for (int[] p : coords) {
            int boardX = pieceX + p[0];
            int boardY = pieceY + p[1];
            if (boardY >= 0 && boardY < TOTAL_BOARD_HEIGHT && boardX >= 0 && boardX < BOARD_WIDTH) {
                grid[boardY][boardX] = color;
            }
        }
    }

    /**
     * 揃ったラインを消去し、上のブロックを下にずらします。
     */
    public int clearLines() {
        int linesCleared = 0;
        for (int y = TOTAL_BOARD_HEIGHT - 1; y >= 0; y--) {
            boolean lineIsFull = true;
            for (int x = 0; x < BOARD_WIDTH; x++) {
                if (grid[y][x] == null) {
                    lineIsFull = false;
                    break;
                }
            }
            if (lineIsFull) {
                linesCleared++;
                // y行を削除し、それより上の行をすべて1段下にずらす
                for (int rowToMove = y; rowToMove > 0; rowToMove--) {
                    grid[rowToMove] = grid[rowToMove - 1];
                }
                grid[0] = new Color[BOARD_WIDTH]; // 一番上の行を空にする
                y++; // 同じ行を再度チェック
            }
        }
        return linesCleared;
    }
    
    /**
     * お邪魔ブロックを指定された行数せり上げます。
     * @param lineCount せり上げる行数
     * @return せり上がりの結果、ブロックが盤面の上限を突き抜けたらtrue（ゲームオーバー）
     */
    public boolean addGarbageLines(int lineCount) {
        if (lineCount <= 0) return false;

        // 1. せり上がりでブロックが盤面外に押し出されるかチェック (ゲームオーバー条件C)
        for (int y = 0; y < lineCount; y++) {
            for (int x = 0; x < BOARD_WIDTH; x++) {
                if (grid[y][x] != null) {
                    return true; // ブロックが消滅＝ゲームオーバー
                }
            }
        }

        // 2. 既存の行を上にずらす
        for (int y = 0; y < TOTAL_BOARD_HEIGHT - lineCount; y++) {
            grid[y] = grid[y + lineCount];
        }

        // 3. 下にお邪魔ブロック行を追加
        int holePosition = (int) (Math.random() * BOARD_WIDTH);
        for (int y = TOTAL_BOARD_HEIGHT - lineCount; y < TOTAL_BOARD_HEIGHT; y++) {
            Color[] garbageLine = new Color[BOARD_WIDTH];
            Arrays.fill(garbageLine, Color.GRAY);
            garbageLine[holePosition] = null;
            grid[y] = garbageLine;
        }
        return false;
    }

    public boolean isBoardEmpty() {
        for (int y = 0; y < TOTAL_BOARD_HEIGHT; y++) {
            for (int x = 0; x < BOARD_WIDTH; x++) {
                if (grid[y][x] != null) return false;
            }
        }
        return true;
    }

    public Color getGridAt(int x, int y) {
        if (y >= 0 && y < TOTAL_BOARD_HEIGHT && x >= 0 && x < BOARD_WIDTH) {
            return grid[y][x];
        }
        return null;
    }
}