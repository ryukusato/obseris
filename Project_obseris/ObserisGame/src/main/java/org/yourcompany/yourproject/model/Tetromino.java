package org.yourcompany.yourproject.model;
import java.awt.Color;
import java.util.Arrays;

public class Tetromino {
    private final Shape.Tetrominoes pieceShape;
    private int[][] coords;
    private int x, y;
    private int rotationState;

    public Tetromino(Shape.Tetrominoes shape) {
        this.pieceShape = shape;
        this.coords = new int[4][2];
        setShape(shape.allCoords.get(0));
        this.rotationState = 0;
    }

    private void setShape(int[][] newCoords) {
        for (int i = 0; i < 4; i++) {
            this.coords[i] = Arrays.copyOf(newCoords[i], newCoords[i].length);
        }
    }

    /**
     * テトリミノの座標と回転をガイドラインに基づいた初期状態に戻します。
     */
    public void resetPositionAndState() {
        this.rotationState = 0;
        setShape(pieceShape.allCoords.get(0));

        // --- 水平位置の決定 ---
        // Oミノは中央(5列目)、他は少し左(4列目)から出現
        if (this.pieceShape == Shape.Tetrominoes.SquareShape) {
            this.x = 4;
        } else {
            this.x = 3;
        }

        // --- 垂直位置の決定 ---
        // ミノの下端が見える盤面の21行目に来るように調整
        int minY = 0;
        for (int[] p : this.coords) {
            minY = Math.min(minY, p[1]);
        }
        int hiddenRows = Board.TOTAL_BOARD_HEIGHT - Board.VISIBLE_BOARD_HEIGHT;
        this.y = hiddenRows - minY;
    }

    public void applyRotation(int[][] newCoords, int newX, int newY, int newRotationState) {
        setShape(newCoords);
        this.x = newX;
        this.y = newY;
        this.rotationState = newRotationState;
    }
    public int[][] getCoordsForRotation(int rot) {
        return pieceShape.allCoords.get(rot);
    }
    public void setSimulatedState(int x, int y, int rot) {
        this.x = x;
        this.y = y;
        this.rotationState = rot;
        this.coords = getCoordsForRotation(rot);
    }
    
    // --- ゲッター ---
    public int getRotationState() { return this.rotationState; }
    public Shape.Tetrominoes getPieceShape() { return pieceShape; }
    public int getNextRotationState(boolean clockwise) {
        return (this.rotationState + (clockwise ? 1 : 3)) % 4;
    }
    public int[][] getRotatedCoords(boolean clockwise) {
        if (pieceShape == Shape.Tetrominoes.SquareShape) return coords;
        return pieceShape.allCoords.get(getNextRotationState(clockwise));
    }
    public int getX() { return x; }
    public int getY() { return y; }
    public Color getColor() { return pieceShape.color; }
    public int[][] getCoords() { return coords; }

    // --- 移動用メソッド ---
    public void moveDown() { y++; }
    public void moveBy(int dx, int dy) { x += dx; y += dy; }
}