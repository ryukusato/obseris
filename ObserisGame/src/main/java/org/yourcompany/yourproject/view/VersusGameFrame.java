package org.yourcompany.yourproject.view;
import java.awt.GridLayout;

import javax.swing.BorderFactory;
import javax.swing.JFrame;
import javax.swing.JPanel;

import org.yourcompany.yourproject.config.PlayerType;
import org.yourcompany.yourproject.controller.GameController;
import org.yourcompany.yourproject.controller.VersusManager;
import org.yourcompany.yourproject.player.AIPlayer;
import org.yourcompany.yourproject.player.HumanPlayer;
import org.yourcompany.yourproject.player.Player;

public class VersusGameFrame extends JFrame {

    // ▼▼▼ フィールドとして保持 ▼▼▼
    private final PlayerUIPanel player1UI;
    private final PlayerUIPanel player2UI;
    // ▲▲▲

    public VersusGameFrame(PlayerType player1Type, PlayerType player2Type,String player1modelResourceName,String player2modelResourceName) {
        // 1. 選択されたモードに応じてプレイヤーを生成
        Player player1 = createPlayer(player1Type,player1modelResourceName);
        Player player2 = createPlayer(player2Type,player2modelResourceName);

        // 2. VersusManagerを生成
        VersusManager versusManager = new VersusManager(player1, player2);

        if (player1 instanceof AIPlayer aIPlayer) {
            aIPlayer.setOpponent(versusManager.getPlayer2Logic());
        }
        if (player2 instanceof AIPlayer aIPlayer) {
            aIPlayer.setOpponent(versusManager.getPlayer1Logic());
        }

        // 3. UIコンポーネントを生成 ▼▼▼ フィールドに格納 ▼▼▼
        this.player1UI = new PlayerUIPanel(versusManager.getPlayer1Logic());
        this.player2UI = new PlayerUIPanel(versusManager.getPlayer2Logic());
        
        // 4. Controllerを生成 (this を VersusGameFrame として渡す)
        GameController gameController = new GameController(versusManager, this, player1, player2);

        // --- ウィンドウのセットアップ ---
        setTitle("Tetris Versus [" + player1Type + " vs " + player2Type + "]");
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setResizable(false);

        JPanel mainPanel = new JPanel(new GridLayout(1, 2, 10, 10));
        mainPanel.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
        mainPanel.add(player1UI); // フィールドの player1UI を追加
        mainPanel.add(player2UI); // フィールドの player2UI を追加
        add(mainPanel);

        pack();
        setLocationRelativeTo(null);
        
        // フレームを表示してからゲームを開始
        setVisible(true);
        gameController.startGame();
    }

    /**
     * PlayerTypeに応じてHumanPlayerまたはAIPlayerのインスタンスを返すファクトリメソッド
     */
    private Player createPlayer(PlayerType type, String modelResourceName ) {
        if (type == PlayerType.HUMAN) {
            return new HumanPlayer();
        } else {
            return new AIPlayer(modelResourceName);
        }
    }

    // ▼▼▼ メソッド追加 ▼▼▼
    /**
     * GameControllerから呼び出され、
     * 両方のプレイヤーUIパネルに新しいGameLogicをセットして再構築させます。
     */
    public void resetUI(VersusManager manager) {
        player1UI.resetGameLogic(manager.getPlayer1Logic());
        player2UI.resetGameLogic(manager.getPlayer2Logic());
    }
    // ▲▲▲
}