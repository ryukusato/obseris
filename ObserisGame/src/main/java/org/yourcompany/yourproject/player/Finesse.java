package org.yourcompany.yourproject.player;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.Map;
import java.util.Queue;
import java.util.Set;

import org.yourcompany.yourproject.config.GameAction;
import org.yourcompany.yourproject.model.Board;
import org.yourcompany.yourproject.model.GameLogic;
import org.yourcompany.yourproject.model.RotationSystem;
import org.yourcompany.yourproject.model.RotationSystem.RotationResult;
import org.yourcompany.yourproject.model.Shape;
import org.yourcompany.yourproject.model.Tetromino;

/**
 * Finesse（最適経路探索）を計算するクラス。
 * 幅優先探索(BFS)を使い、目標地点までの最短のキー操作を見つけます。
 */
public class Finesse {

    // 探索空間の状態を表すレコード
    private record MoveState(int x, int y, int rot) {}

    /**
     * 目標の (x, rot) に到達するための最短キー操作のキューを返します。
     * @param logic 現在のGameLogic (盤面とミノの初期状態のため)
     * @param targetX 目標のX座標
     * @param targetRot 目標の回転状態 (0-3)
     * @return 最短の GameAction キュー
     */
    public static Queue<GameAction> findPath(GameLogic logic, int targetX, int targetRot) {
        Queue<GameAction> path = new LinkedList<>();
        Tetromino piece = logic.getCurrentTetromino();
        if (piece == null) return path;

        MoveState startState = new MoveState(piece.getX(), piece.getY(), piece.getRotationState());
        Shape.Tetrominoes shape = piece.getPieceShape();
        Board board = logic.getBoard();

        // BFS用のデータ構造
        Queue<MoveState> queue = new LinkedList<>();
        // どの状態から来たか(親)と、どのアクションで来たかを記録
        Map<MoveState, MoveState> parent = new HashMap<>();
        Map<MoveState, GameAction> actionUsed = new HashMap<>();
        Set<MoveState> visited = new HashSet<>();

        queue.add(startState);
        visited.add(startState);
        parent.put(startState, null);

        MoveState finalState = null;

        while (!queue.isEmpty()) {
            MoveState currentState = queue.poll();

            // ゴール判定: X座標と回転が一致したら成功
            if (currentState.x() == targetX && currentState.rot() == targetRot) {
                finalState = currentState;
                break;
            }

            // --- 全てのアクションを試す ---
            // (ハードドロップ、ホールドは経路探索に含まない)
            GameAction[] actionsToTry = {
                GameAction.MOVE_LEFT, GameAction.MOVE_RIGHT,
                GameAction.ROTATE_LEFT, GameAction.ROTATE_RIGHT,
                GameAction.SOFT_DROP
            };

            for (GameAction action : actionsToTry) {
                MoveState nextState = simulateAction(currentState, action, shape, board);
                if (nextState != null && !visited.contains(nextState)) {
                    visited.add(nextState);
                    parent.put(nextState, currentState);
                    actionUsed.put(nextState, action);
                    queue.add(nextState);
                }
            }
        }

        // --- 経路の復元 ---
        if (finalState != null) {
            MoveState current = finalState;
            while (parent.get(current) != null) {
                path.add(actionUsed.get(current));
                current = parent.get(current);
            }
            Collections.reverse((LinkedList<GameAction>) path);
        }

        return path;
    }

    /**
     * 1つのアクションをシミュレートし、次の状態を返す
     */
    private static MoveState simulateAction(MoveState from, GameAction action, 
                                            Shape.Tetrominoes shape, Board board) {
        int x = from.x();
        int y = from.y();
        int rot = from.rot();
        
        switch (action) {
            case MOVE_LEFT:
                int[][] coordsL = shape.allCoords.get(rot);
                if (board.isValidPosition(coordsL, x - 1, y)) {
                    return new MoveState(x - 1, y, rot);
                }
                break;
            case MOVE_RIGHT:
                int[][] coordsR = shape.allCoords.get(rot);
                if (board.isValidPosition(coordsR, x + 1, y)) {
                    return new MoveState(x + 1, y, rot);
                }
                break;
            case SOFT_DROP:
                int[][] coordsD = shape.allCoords.get(rot);
                if (board.isValidPosition(coordsD, x, y + 1)) {
                    return new MoveState(x, y + 1, rot);
                }
                break;
            case ROTATE_LEFT:
                RotationResult resL = RotationSystem.simulateRotation(x, y, rot, shape, board, false);
                if (resL.success()) {
                    return new MoveState(resL.newX(), resL.newY(), resL.newRot());
                }
                break;
            case ROTATE_RIGHT:
                RotationResult resR = RotationSystem.simulateRotation(x, y, rot, shape, board, true);
                if (resR.success()) {
                    return new MoveState(resR.newX(), resR.newY(), resR.newRot());
                }
                break;
        }
        return null; // 移動失敗
    }
}