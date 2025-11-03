package org.yourcompany.yourproject.view;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Graphics;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JLabel;
import javax.swing.JPanel;

import org.yourcompany.yourproject.model.GameLogic;

public class SidePanel extends JPanel {
    private final JLabel scoreLabel;
    private final GameLogic gameLogic;

    public SidePanel(GameLogic gameLogic) {
        this.gameLogic = gameLogic;
        setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));
        setPreferredSize(new Dimension(120, 600)); 

        NextPanel nextPanel = new NextPanel(gameLogic);
        
        
        scoreLabel = new JLabel("0");
        scoreLabel.setFont(new Font("Helvetica", Font.BOLD, 24));
        scoreLabel.setAlignmentX(Component.CENTER_ALIGNMENT);
        
        JPanel scorePanel = new JPanel();
        scorePanel.setBorder(BorderFactory.createTitledBorder("SCORE"));
        scorePanel.add(scoreLabel);
        scorePanel.setMaximumSize(new Dimension(120, 80)); 
        
        add(Box.createRigidArea(new Dimension(0, 15)));
        add(nextPanel);
        add(scorePanel);
        add(Box.createVerticalGlue());
    }

    public void updateScore(long score) {
        scoreLabel.setText(String.valueOf(score));
    }

    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        
        // 現在のラベルのテキストを取得
        String currentScoreText = scoreLabel.getText();
        // GameLogic から最新のスコアを取得して文字列に変換
        String newScoreText = String.valueOf(gameLogic.getScore());
        
        // スコアが変更されている場合のみ updateScore を呼び出す
        // (paintComponent 内での不要なsetText呼び出しによる無限ループやパフォーマンス低下を防ぐため)
        if (!currentScoreText.equals(newScoreText)) {
            updateScore(gameLogic.getScore());
        }
    }
}