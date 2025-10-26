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

    public VersusGameFrame(PlayerType player1Type, PlayerType player2Type) {
        // 1. 選択されたモードに応じてプレイヤーを生成
        Player player1 = createPlayer(player1Type);
        Player player2 = createPlayer(player2Type);

        // 2. VersusManagerを生成
        VersusManager versusManager = new VersusManager(player1, player2);

        // 3. UIコンポーネントを生成
        PlayerUIPanel player1UI = new PlayerUIPanel(versusManager.getPlayer1Logic());
        PlayerUIPanel player2UI = new PlayerUIPanel(versusManager.getPlayer2Logic());
        
        // 4. Controllerを生成
        GameController gameController = new GameController(versusManager, this, player1, player2);

        // --- ウィンドウのセットアップ ---
        setTitle("Tetris Versus [" + player1Type + " vs " + player2Type + "]");
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setResizable(false);

        JPanel mainPanel = new JPanel(new GridLayout(1, 2, 10, 10));
        mainPanel.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
        mainPanel.add(player1UI);
        mainPanel.add(player2UI);
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
    private Player createPlayer(PlayerType type) {
        if (type == PlayerType.HUMAN) {
            return new HumanPlayer();
        } else {
            return new AIPlayer();
        }
    }
}