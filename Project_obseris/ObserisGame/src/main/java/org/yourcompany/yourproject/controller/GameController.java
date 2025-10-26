package org.yourcompany.yourproject.controller;
import org.yourcompany.yourproject.config.*;
import org.yourcompany.yourproject.player.*;

import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;

import javax.swing.*;

public class GameController {

    private final VersusManager versusManager;
    private final JFrame gameFrame;
    private final Player player1;
    private final Player player2;
    private Timer gameLoopTimer;

    private final InputState player1Input = new InputState();
    private final InputState player2Input = new InputState();

    public GameController(VersusManager manager, JFrame frame, Player p1, Player p2) {
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
            gameLoopTimer.stop();
            String message;
            if (p1Over && p2Over) message = "引き分け！";
            else message = "ゲームオーバー！\n勝者: " + (p1Over ? "プレイヤー2" : "プレイヤー1");
            JOptionPane.showMessageDialog(gameFrame, message);
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