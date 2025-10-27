package org.yourcompany.yourproject.view;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Font;
import java.awt.GridLayout;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.SwingConstants;

import org.yourcompany.yourproject.config.PlayerType;

public class Title extends JFrame {
    public Title() {
        setTitle("Tetris Title");
        setSize(400, 300);
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setLocationRelativeTo(null);

        JPanel panel = new JPanel(new BorderLayout());
        panel.setBackground(Color.LIGHT_GRAY);

        JLabel titleLabel = new JLabel("TETRIS VERSUS", SwingConstants.CENTER);
        titleLabel.setFont(new Font("Arial", Font.BOLD, 48));
        panel.add(titleLabel, BorderLayout.CENTER);

        // --- ボタンパネルのレイアウトを変更 ---
        JPanel buttonPanel = new JPanel(new GridLayout(4, 1, 10, 10)); // 縦に並べる
        buttonPanel.setBorder(BorderFactory.createEmptyBorder(20, 50, 20, 50));

        // Player vs Player ボタン
        JButton pvpButton = new JButton("Player vs Player");
        pvpButton.addActionListener(e -> {
            new VersusGameFrame(PlayerType.HUMAN, PlayerType.HUMAN);
            dispose();
        });

        // Player vs AI ボタン
        JButton pvcButton = new JButton("Player vs AI");
        pvcButton.addActionListener(e -> {
            new VersusGameFrame(PlayerType.HUMAN, PlayerType.AI);
            dispose();
        });
        
        // AI vs AI ボタン
        JButton cvcButton = new JButton("AI vs AI");
        cvcButton.addActionListener(e -> {
            new VersusGameFrame(PlayerType.AI, PlayerType.AI);
            dispose();
        });

        // Option ボタン
        JButton optionButton = new JButton("Option");
        optionButton.addActionListener(e -> new Option().setVisible(true));

        buttonPanel.add(pvpButton);
        buttonPanel.add(pvcButton);
        buttonPanel.add(cvcButton);
        buttonPanel.add(optionButton);
        panel.add(buttonPanel, BorderLayout.SOUTH);

        add(panel);
        setVisible(true);
    }
}