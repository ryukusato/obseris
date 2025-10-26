package org.yourcompany.yourproject.view;
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

public class InfoPanel extends JPanel {
    private final GameLogic gameLogic;
    private ClearInfo currentInfo = null;
    private final Timer displayTimer;

    public InfoPanel(GameLogic gameLogic) {
        this.gameLogic = gameLogic;
        setPreferredSize(new Dimension(120, 100));
        setBorder(BorderFactory.createTitledBorder("INFO"));
        setOpaque(false);

        // 2秒後に表示をクリアするためのタイマー
        this.displayTimer = new Timer(2000, e -> repaint());
        displayTimer.setRepeats(false);
    }

    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);

        ClearInfo newInfo = gameLogic.getLastClearInfo();

        // 新しい情報が来たらタイマーをリスタート
        if (newInfo != null && newInfo.getLinesCleared() > 0 && newInfo != currentInfo) {
            this.currentInfo = newInfo;
            displayTimer.restart();
        }

        // タイマーが動いている間だけ情報を描画
        if (displayTimer.isRunning() && currentInfo != null) {
            drawInfo(g, currentInfo);
        }
    }

    private void drawInfo(Graphics g, ClearInfo info) {
        Graphics2D g2d = (Graphics2D) g;
        g2d.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        
        int yPos = 30;
        
        if (info.isPerfectClear()) {
            drawText(g2d, "PERFECT CLEAR", yPos, 16, Color.MAGENTA);
            yPos += 25;
        }
        if (info.isB2B()) {
            drawText(g2d, "Back-to-Back", yPos, 14, Color.ORANGE);
            yPos += 20;
        }
        if (!info.getClearType().isEmpty()) {
            drawText(g2d, info.getClearType(), yPos, 14, Color.CYAN);
            yPos += 20;
        }
        if (info.getComboCount() > 0) {
            drawText(g2d, info.getComboCount() + " COMBO", yPos, 14, Color.YELLOW);
        }
    }

    private void drawText(Graphics2D g2d, String text, int y, int size, Color color) {
        g2d.setColor(color);
        g2d.setFont(new Font("Arial", Font.BOLD, size));
        FontMetrics fm = g2d.getFontMetrics();
        int x = (getWidth() - fm.stringWidth(text)) / 2;
        g2d.drawString(text, x, y);
    }
}