package org.yourcompany.yourproject.player;
import org.yourcompany.yourproject.config.*;
import org.yourcompany.yourproject.model.*;

import java.util.Random;

public class AIPlayer implements Player {
    private final Random random = new Random();
    private int actionCooldown = 0;

    @Override
    public GameAction getAction(GameLogic gameState) {
        // 簡単なクールダウンを設け、毎フレームは判断しないようにする
        if (actionCooldown > 0) {
            actionCooldown--;
            return GameAction.NONE;
        }
        actionCooldown = 5; // 5フレームに1回行動する

        if (gameState.getCurrentTetromino() == null) {
            return GameAction.NONE;
        }

        // 非常にシンプルなダミーAI: ランダムなアクションを返す
        int choice = random.nextInt(100);
        if (choice < 15) return GameAction.MOVE_LEFT;
        if (choice < 30) return GameAction.MOVE_RIGHT;
        if (choice < 40) return GameAction.ROTATE_RIGHT;
        if (choice < 60) return GameAction.SOFT_DROP;
        if (choice < 65) return GameAction.HARD_DROP; // ハードドロップは少し控えめに
        
        return GameAction.SOFT_DROP; // 基本は下に落とす
    }
}