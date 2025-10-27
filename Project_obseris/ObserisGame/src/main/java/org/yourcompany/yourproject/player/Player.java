package org.yourcompany.yourproject.player;
import org.yourcompany.yourproject.config.GameAction;
import org.yourcompany.yourproject.model.GameLogic;
public interface Player {
    /**
     * 現在のゲーム状態を元に、実行すべきアクションを決定して返します。
     * @param gameState 自身の現在のゲーム状態
     * @return 実行するGameAction
     */
    GameAction getAction(GameLogic gameState);
}