package org.yourcompany.yourproject.view;
import java.awt.Dimension;
import java.awt.Graphics;
import java.util.List;

import javax.swing.BorderFactory;
import javax.swing.JPanel;

import org.yourcompany.yourproject.model.GameLogic;
import org.yourcompany.yourproject.model.Tetromino;

public class NextPanel extends JPanel {
    private final GameLogic gameLogic;
    private static final int BLOCK_SIZE = 18;
    private static final int SLOT_HEIGHT = 90;

    public NextPanel(GameLogic gameLogic) {
        this.gameLogic = gameLogic;
        setPreferredSize(new Dimension(120, SLOT_HEIGHT * 5));
        setBorder(BorderFactory.createTitledBorder("NEXT"));
        setOpaque(false);
    }

    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        List<Tetromino> nextQueue = gameLogic.getNextQueue();
        for (int i = 0; i < nextQueue.size(); i++) {
            Graphics slotGraphics = g.create(0, i * SLOT_HEIGHT, getWidth(), SLOT_HEIGHT);
            HoldPanel.drawCenteredMino(slotGraphics, nextQueue.get(i), BLOCK_SIZE);
            slotGraphics.dispose();
        }
    }
}