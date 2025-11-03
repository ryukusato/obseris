package org.yourcompany.yourproject.model;
import java.awt.Point;

/**
 * SRS（スーパーローテーションシステム）に基づいたテトリミノの回転とウォールキックを処理します。
 */
public final class RotationSystem {

    /**
     * 回転の結果を格納するレコード。
     * @param success 回転が成功したか
     * @param kickIndex 成功時に使用されたウォールキックテストのインデックス (0-4)
     */
    public record RotationResult(boolean success, int kickIndex, int newX, int newY, int newRot) {
        // 元の互換性のため、キックインデックスのみのコンストラクタも用意
        public RotationResult(boolean success, int kickIndex) {
            this(success, kickIndex, 0, 0, 0);
        }
    }

    // J, L, S, T, Z テトリミノ用のウォールキックデータ
    private static final Point[][] WALL_KICK_DATA_JLSTZ = new Point[][] {
        // 0 -> R
        { new Point(0, 0), new Point(-1, 0), new Point(-1, -1), new Point(0, 2), new Point(-1, 2) },
        // R -> 0
        { new Point(0, 0), new Point(1, 0), new Point(1, 1), new Point(0, -2), new Point(1, -2) },
        // R -> 2
        { new Point(0, 0), new Point(1, 0), new Point(1, 1), new Point(0, -2), new Point(1, -2) },
        // 2 -> R
        { new Point(0, 0), new Point(-1, 0), new Point(-1, -1), new Point(0, 2), new Point(-1, 2) },
        // 2 -> L
        { new Point(0, 0), new Point(1, 0), new Point(1, -1), new Point(0, 2), new Point(1, 2) },
        // L -> 2
        { new Point(0, 0), new Point(-1, 0), new Point(-1, 1), new Point(0, -2), new Point(-1, -2) },
        // L -> 0
        { new Point(0, 0), new Point(-1, 0), new Point(-1, 1), new Point(0, -2), new Point(-1, -2) },
        // 0 -> L
        { new Point(0, 0), new Point(1, 0), new Point(1, -1), new Point(0, 2), new Point(1, 2) }
    };

    // I テトリミノ用のウォールキックデータ
    private static final Point[][] WALL_KICK_DATA_I = new Point[][] {
        // 0 -> R
        { new Point(0, 0), new Point(-2, 0), new Point(1, 0), new Point(-2, 1), new Point(1, -2) },
        // R -> 0
        { new Point(0, 0), new Point(2, 0), new Point(-1, 0), new Point(2, -1), new Point(-1, 2) },
        // R -> 2
        { new Point(0, 0), new Point(-1, 0), new Point(2, 0), new Point(-1, -2), new Point(2, 1) },
        // 2 -> R
        { new Point(0, 0), new Point(1, 0), new Point(-2, 0), new Point(1, 2), new Point(-2, -1) },
        // 2 -> L
        { new Point(0, 0), new Point(2, 0), new Point(-1, 0), new Point(2, -1), new Point(-1, 2) },
        // L -> 2
        { new Point(0, 0), new Point(-2, 0), new Point(1, 0), new Point(-2, 1), new Point(1, -2) },
        // L -> 0
        { new Point(0, 0), new Point(1, 0), new Point(-2, 0), new Point(1, 2), new Point(-2, -1) },
        // 0 -> L
        { new Point(0, 0), new Point(-1, 0), new Point(2, 0), new Point(-1, -2), new Point(2, 1) }
    };

    /**
     * テトリミノの回転を試みます。
     * @param tetromino 回転させるテトリミノ
     * @param board ゲームボード
     * @param clockwise 時計回りの場合はtrue
     * @return 回転の結果 (成功したか、どのキックを使ったか)
     */
    public static RotationResult tryRotate(Tetromino tetromino, Board board, boolean clockwise) {
        RotationResult result = simulateRotation(
            tetromino.getX(), tetromino.getY(), tetromino.getRotationState(),
            tetromino.getPieceShape(), board, clockwise
        );

        if (result.success()) {
            // 成功した場合のみ、実際のミノに結果を適用
            int[][] newCoords = tetromino.getCoordsForRotation(result.newRot());
            tetromino.applyRotation(newCoords, result.newX(), result.newY(), result.newRot());
        }
        return result;
    }
    public static RotationResult simulateRotation(int currentX, int currentY, int currentRot,
                                                  Shape.Tetrominoes shape, Board board, boolean clockwise) {
        if (shape == Shape.Tetrominoes.SquareShape) {
            return new RotationResult(true, 0, currentX, currentY, currentRot);
        }

        int nextRot = (currentRot + (clockwise ? 1 : 3)) % 4;
        int[][] rotatedCoords = shape.allCoords.get(nextRot);
        Point[] kickTests = getWallKickTests(shape, currentRot, nextRot);

        for (int i = 0; i < kickTests.length; i++) {
            Point testOffset = kickTests[i];
            int testX = currentX + testOffset.x;
            int testY = currentY + testOffset.y;

            if (board.isValidPosition(rotatedCoords, testX, testY)) {
                // 成功した結果を返す (実際のミノは動かさない)
                return new RotationResult(true, i, testX, testY, nextRot);
            }
        }
        return new RotationResult(false, -1, 0, 0, 0);
    }


    private static Point[] getWallKickTests(Shape.Tetrominoes shape, int currentRotation, int nextRotation) {
        Point[][] kickData = (shape == Shape.Tetrominoes.LineShape) ? WALL_KICK_DATA_I : WALL_KICK_DATA_JLSTZ;

        if (currentRotation == 0 && nextRotation == 1) return kickData[0]; // 0 -> R
        if (currentRotation == 1 && nextRotation == 0) return kickData[1]; // R -> 0
        if (currentRotation == 1 && nextRotation == 2) return kickData[2]; // R -> 2
        if (currentRotation == 2 && nextRotation == 1) return kickData[3]; // 2 -> R
        if (currentRotation == 2 && nextRotation == 3) return kickData[4]; // 2 -> L
        if (currentRotation == 3 && nextRotation == 2) return kickData[5]; // L -> 2
        if (currentRotation == 3 && nextRotation == 0) return kickData[6]; // L -> 0
        if (currentRotation == 0 && nextRotation == 3) return kickData[7]; // 0 -> L

        return new Point[]{ new Point(0, 0) }; // Fallback
    }
}