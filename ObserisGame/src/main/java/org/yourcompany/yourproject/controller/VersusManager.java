package org.yourcompany.yourproject.controller;
import org.yourcompany.yourproject.config.GameAction;
import org.yourcompany.yourproject.model.GameLogic;
import org.yourcompany.yourproject.player.Player;
public class VersusManager {
    private GameLogic player1Logic;
    private GameLogic player2Logic;
    private final Player player1;
    private final Player player2;

    public VersusManager(Player p1, Player p2) {
        this.player1Logic = new GameLogic();
        this.player2Logic = new GameLogic();
    
        
        // Playerインスタンスをセット
        this.player1 = p1;
        this.player2 = p2;

        // GameLogicインスタンスにお互いをマネージャーとして登録
        this.player1Logic.setVersusManager(this);
        this.player2Logic.setVersusManager(this);
    }

    /**
     * ゲームのメインループ。GameControllerのタイマーから呼ばれる。
     */
    public void update() {
        // Player1の更新
        if (!player1Logic.isGameOver()) {
            GameAction p1Action = player1.getAction(player1Logic);
            executeAction(player1Logic, p1Action);
            player1Logic.update();
        }

        // Player2の更新
        if (!player2Logic.isGameOver()) {
            GameAction p2Action = player2.getAction(player2Logic);
            executeAction(player2Logic, p2Action);
            player2Logic.update();
        }
    }

    /**
     * プレイヤーからの攻撃を相手に送る
     * @param sender 攻撃元のGameLogic
     * @param lines 送るお邪魔の行数
     */
    public void sendAttack(GameLogic sender, int lines) {
        if (sender == player1Logic) {
            player2Logic.receiveGarbage(lines);
        } else {
            player1Logic.receiveGarbage(lines);
        }
    }
    
    /**
     * 溜まっているお邪魔ブロックを相殺する
     * @param sender 相殺を試みるプレイヤーのGameLogic
     * @param attackPower 相殺に使う攻撃力
     * @return 相殺しきって相手に送るべき攻撃力
     */
    public int offsetGarbage(GameLogic sender, int attackPower) {
        if (sender == player1Logic) {
            return player1Logic.offsetGarbage(attackPower);
        } else {
            return player2Logic.offsetGarbage(attackPower);
        }
    }

    private void executeAction(GameLogic logic, GameAction action) {
        if (action == null || action == GameAction.NONE) return;
        switch (action) {
            case MOVE_LEFT -> logic.moveLeft();
            case MOVE_RIGHT -> logic.moveRight();
            case ROTATE_LEFT -> logic.rotateLeft();
            case ROTATE_RIGHT -> logic.rotateRight();
            case SOFT_DROP -> logic.softDrop();
            case HARD_DROP -> logic.hardDrop();
            case HOLD -> logic.hold();
        }
    }

    public void resetGame() {
        // 2人のGameLogicインスタンスを新しく作り直す
        this.player1Logic = new GameLogic();
        this.player2Logic = new GameLogic();
        
        // GameLogicにVersusManagerを再度セットアップする
        // (この処理はコンストラクタでも行っているはずです)
        this.player1Logic.setVersusManager(this);
        this.player2Logic.setVersusManager(this);
    }


    // --- Viewが描画するためのゲッター ---
    public GameLogic getPlayer1Logic() { return player1Logic; }
    public GameLogic getPlayer2Logic() { return player2Logic; }
}