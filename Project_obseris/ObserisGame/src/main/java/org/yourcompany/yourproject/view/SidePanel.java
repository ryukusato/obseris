package org.yourcompany.yourproject.view;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Font;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JLabel;
import javax.swing.JPanel;

import org.yourcompany.yourproject.model.GameLogic;

public class SidePanel extends JPanel {
    private final JLabel scoreLabel;

    public SidePanel(GameLogic gameLogic) {
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
}