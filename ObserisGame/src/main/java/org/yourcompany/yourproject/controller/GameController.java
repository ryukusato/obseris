package org.yourcompany.yourproject.controller;
import org.yourcompany.yourproject.config.*;
import org.yourcompany.yourproject.player.*;
import org.yourcompany.yourproject.view.VersusGameFrame;

import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;

import javax.swing.*;

public class GameController {

    private final VersusManager versusManager;
    private final VersusGameFrame gameFrame;
    private final Player player1;
    private final Player player2;
    private Timer gameLoopTimer;

    private final InputState player1Input = new InputState();
    private final InputState player2Input = new InputState();

    public GameController(VersusManager manager, VersusGameFrame frame, Player p1, Player p2) {
        this.versusManager = manager;
        this.gameFrame = frame;
        this.player1 = p1;
        this.player2 = p2;
    }

    public void startGame() {
        setupKeyListeners();
        gameLoopTimer = new Timer(16, e -> updateGame()); // 約60FPS
        gameLoopTimer.start();
    }

    private void updateGame() {
        // HumanPlayerの内部状態を更新
        if (player1 instanceof HumanPlayer p1) p1.update(player1Input, versusManager.getPlayer1Logic());
        if (player2 instanceof HumanPlayer p2) p2.update(player2Input, versusManager.getPlayer2Logic());
        
        // ゲーム全体のロジックを更新
        versusManager.update();
        gameFrame.repaint();

        boolean p1Over = versusManager.getPlayer1Logic().isGameOver();
        boolean p2Over = versusManager.getPlayer2Logic().isGameOver();
        if (p1Over || p2Over) {
            gameLoopTimer.stop(); // タイマーを停止
            String message;
            if (p1Over && p2Over) message = "引き分け！";
            else message = "ゲームオーバー！\n勝者: " + (p1Over ? "プレイヤー2" : "プレイヤー1");

            // 選択肢付きのダイアログを表示
            Object[] options = {"再戦", "終了"};
            int choice = JOptionPane.showOptionDialog(
                gameFrame,
                message,
                "ゲーム終了",
                JOptionPane.YES_NO_OPTION,
                JOptionPane.INFORMATION_MESSAGE,
                null,
                options,
                options[0] // デフォルトは "再戦"
            );

            if (choice == 0) { // 0 = "再戦" が選ばれた
                // ゲーム状態をリセット
                versusManager.resetGame(); 
                // 入力状態をリセット
                player1Input.reset();
                player2Input.reset();
                gameFrame.resetUI(versusManager);
                // タイマーを再開
                gameLoopTimer.start();
            } else { // 1 = "終了" またはダイアログが閉じられた
                gameFrame.dispose(); // ウィンドウを閉じる
                // (アプリケーション全体を終了する場合は System.exit(0);)
            }
        }
    }

    private void setupKeyListeners() {
        gameFrame.addKeyListener(new KeyAdapter() {
            @Override
            public void keyPressed(KeyEvent e) {
                updateInputState(e.getKeyCode(), true);
            }
            @Override
            public void keyReleased(KeyEvent e) {
                updateInputState(e.getKeyCode(), false);
            }
        });
        gameFrame.setFocusable(true);
        gameFrame.requestFocusInWindow();
    }
    
    private void updateInputState(int keyCode, boolean isPressed) {
        // Player 1
        if (KeyConfig.getKeysForAction(1, "moveLeft").contains(keyCode)) player1Input.left = isPressed;
        if (KeyConfig.getKeysForAction(1, "moveRight").contains(keyCode)) player1Input.right = isPressed;
        if (KeyConfig.getKeysForAction(1, "rotateLeft").contains(keyCode)) player1Input.rotateLeft = isPressed;
        if (KeyConfig.getKeysForAction(1, "rotateRight").contains(keyCode)) player1Input.rotateRight = isPressed;
        if (KeyConfig.getKeysForAction(1, "softDrop").contains(keyCode)) player1Input.softDrop = isPressed;
        if (KeyConfig.getKeysForAction(1, "hardDrop").contains(keyCode)) player1Input.hardDrop = isPressed;
        if (KeyConfig.getKeysForAction(1, "hold").contains(keyCode)) player1Input.hold = isPressed;

        // Player 2
        if (KeyConfig.getKeysForAction(2, "moveLeft").contains(keyCode)) player2Input.left = isPressed;
        if (KeyConfig.getKeysForAction(2, "moveRight").contains(keyCode)) player2Input.right = isPressed;
        if (KeyConfig.getKeysForAction(2, "rotateLeft").contains(keyCode)) player2Input.rotateLeft = isPressed;
        if (KeyConfig.getKeysForAction(2, "rotateRight").contains(keyCode)) player2Input.rotateRight = isPressed;
        if (KeyConfig.getKeysForAction(2, "softDrop").contains(keyCode)) player2Input.softDrop = isPressed;
        if (KeyConfig.getKeysForAction(2, "hardDrop").contains(keyCode)) player2Input.hardDrop = isPressed;
        if (KeyConfig.getKeysForAction(2, "hold").contains(keyCode)) player2Input.hold = isPressed;
    }
}