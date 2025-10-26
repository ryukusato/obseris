package org.yourcompany.yourproject.view;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;

import javax.swing.BorderFactory;
import javax.swing.JPanel;
import javax.swing.Timer;

import org.yourcompany.yourproject.config.ClearInfo;
import org.yourcompany.yourproject.model.GameLogic;
import org.yourcompany.yourproject.model.Tetromino;

public class HoldPanel extends JPanel {
    private final InfoPanel infoPanel;
    private final HoldMinoPanel holdMinoPanel;

    public HoldPanel(GameLogic gameLogic) {
        setLayout(new BorderLayout());
        setPreferredSize(new Dimension(120, 0));
        setOpaque(false);

        holdMinoPanel = new HoldMinoPanel(gameLogic);
        holdMinoPanel.setBorder(BorderFactory.createTitledBorder("HOLD"));
        holdMinoPanel.setPreferredSize(new Dimension(120, 120));
        holdMinoPanel.setOpaque(false);
        
        infoPanel = new InfoPanel();
        infoPanel.setBorder(BorderFactory.createTitledBorder("INFO"));
        infoPanel.setBackground(Color.BLACK);

        add(holdMinoPanel, BorderLayout.NORTH);
        add(infoPanel, BorderLayout.CENTER);
    }
    
    public void updateInfoPanel(ClearInfo info) {
        infoPanel.updateInfo(info);
    }
    
    private class HoldMinoPanel extends JPanel {
        private final GameLogic gameLogic;
        public HoldMinoPanel(GameLogic gameLogic) { this.gameLogic = gameLogic; }
        @Override
        protected void paintComponent(Graphics g) {
            super.paintComponent(g);
            Tetromino holdTetromino = gameLogic.getHoldTetromino();
            if (holdTetromino == null) return;
            drawCenteredMino(g, holdTetromino, 20);
        }
    }
    
    private class InfoPanel extends JPanel {
        private String clearText = "", b2bText = "", comboText = "",perfectClearText = "";
        private final Timer displayTimer;
        public InfoPanel() {
            displayTimer = new Timer(2000, e -> {
                clearText = b2bText = comboText = perfectClearText = "";
                repaint();
            });
            displayTimer.setRepeats(false);
        }
        public void updateInfo(ClearInfo info) {
            if (info.isPerfectClear()) {
            this.perfectClearText = "PERFECT CLEAR";
        } else {
            this.perfectClearText = "";
        }

            clearText = info.getClearType();
            b2bText = info.isB2B() ? "B2B" : "";
            comboText = info.getComboCount() > 0 ? info.getComboCount() + " COMBO" : "";
            if (!clearText.isEmpty() || !comboText.isEmpty()) displayTimer.restart();
            else displayTimer.stop();
            repaint();
        }
        @Override
        protected void paintComponent(Graphics g) {
            super.paintComponent(g);
            Graphics2D g2d = (Graphics2D) g;
            g2d.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            int yPos = 30;
            if (!perfectClearText.isEmpty()) {drawText(g2d, perfectClearText, yPos, 16, Color.MAGENTA);yPos += 25;}
            if (!b2bText.isEmpty()) { drawText(g2d, b2bText, yPos, 14, Color.ORANGE); yPos += 20; }
            if (!clearText.isEmpty()) { drawText(g2d, clearText, yPos, 14, Color.CYAN); yPos += 20; }
            if (!comboText.isEmpty()) { drawText(g2d, comboText, yPos, 14, Color.YELLOW); }
        }
    }

    // --- 描画ヘルパーメソッド ---
    static void drawCenteredMino(Graphics g, Tetromino tetromino, int blockSize) {
        int[][] coords = tetromino.getCoords();
        int minX = 0, maxX = 0, minY = 0, maxY = 0;
        for (int[] p : coords) {
            minX = Math.min(minX, p[0]); maxX = Math.max(maxX, p[0]);
            minY = Math.min(minY, p[1]); maxY = Math.max(maxY, p[1]);
        }
        int shapeWidth = (maxX - minX + 1) * blockSize;
        int shapeHeight = (maxY - minY + 1) * blockSize;
        int offsetX = (g.getClipBounds().width - shapeWidth) / 2;
        int offsetY = (g.getClipBounds().height - shapeHeight) / 2;
        
        g.setColor(tetromino.getColor());
        for (int[] p : coords) {
            int drawX = offsetX + (p[0] - minX) * blockSize;
            int drawY = offsetY + (p[1] - minY) * blockSize;
            g.fillRect(drawX, drawY, blockSize, blockSize);
            g.setColor(Color.DARK_GRAY);
            g.drawRect(drawX, drawY, blockSize, blockSize);
            g.setColor(tetromino.getColor());
        }
    }
    static void drawText(Graphics2D g2d, String text, int y, int size, Color color) {
        g2d.setColor(color);
        g2d.setFont(new Font("Arial", Font.BOLD, size));
        FontMetrics fm = g2d.getFontMetrics();
        int x = (g2d.getClipBounds().width - fm.stringWidth(text)) / 2;
        g2d.drawString(text, x, y);
    }
}