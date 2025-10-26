package org.yourcompany.yourproject.view;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics;

import javax.swing.JPanel;

import org.yourcompany.yourproject.model.Board;
import org.yourcompany.yourproject.model.GameLogic;
import org.yourcompany.yourproject.model.Tetromino;

public class TetrisPanel extends JPanel {
    private final GameLogic gameLogic;

    public TetrisPanel(GameLogic gameLogic) {
        this.gameLogic = gameLogic;
        // パネルの推奨サイズは「見える」高さで設定
        setPreferredSize(new Dimension(Board.BOARD_WIDTH * 30, Board.VISIBLE_BOARD_HEIGHT * 30));
        setBackground(Color.BLACK);
        setFocusable(true);
    }

    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);

        // 描画順序: グリッド → 設置済みブロック → ゴースト → 操作中ブロック
        drawGrid(g);
        drawBoard(g);

        Tetromino current = gameLogic.getCurrentTetromino();
        if (current != null) {
            drawGhostPiece(g, current);
            drawTetromino(g, current);
        }

        drawGarbageBar(g);

        if (gameLogic.isGameOver()) {
            drawGameOver(g);
        }
    }

    private int getCellWidth() {
        return getWidth() / Board.BOARD_WIDTH;
    }

    private int getCellHeight() {
        return getHeight() / Board.VISIBLE_BOARD_HEIGHT;
    }

    private void drawGrid(Graphics g) {
        g.setColor(new Color(40, 40, 40));
        for (int i = 0; i <= Board.BOARD_WIDTH; i++) {
            g.drawLine(i * getCellWidth(), 0, i * getCellWidth(), getHeight());
        }
        for (int i = 0; i <= Board.VISIBLE_BOARD_HEIGHT; i++) {
            g.drawLine(0, i * getCellHeight(), getWidth(), i * getCellHeight());
        }
    }

    private void drawBoard(Graphics g) {
        Board board = gameLogic.getBoard();
        int hiddenRows = Board.TOTAL_BOARD_HEIGHT - Board.VISIBLE_BOARD_HEIGHT;

        // visibleYは画面上の行 (0~19)
        for (int visibleY = 0; visibleY < Board.VISIBLE_BOARD_HEIGHT; visibleY++) {
            for (int x = 0; x < Board.BOARD_WIDTH; x++) {
                // gridYは内部データ上の行 (20~39)
                int gridY = visibleY + hiddenRows;
                Color color = board.getGridAt(x, gridY);

                if (color != null) {
                    // 描画には画面上の座標(visibleY)を使う
                    drawBlock(g, x * getCellWidth(), visibleY * getCellHeight(), color);
                }
            }
        }
    }

    private void drawTetromino(Graphics g, Tetromino tetromino) {
        int[][] coords = tetromino.getCoords();
        Color color = tetromino.getColor();
        int cellWidth = getCellWidth();
        int cellHeight = getCellHeight();
        int hiddenRows = Board.TOTAL_BOARD_HEIGHT - Board.VISIBLE_BOARD_HEIGHT;

        for (int[] p : coords) {
            // tetYは内部データ上の絶対Y座標
            int tetY = tetromino.getY() + p[1];
            // 画面上の描画Y座標（行数）に変換
            int drawRowY = tetY - hiddenRows;

            // 見える範囲にあるブロックだけ描画
            if (drawRowY >= 0) {
                int drawX = (tetromino.getX() + p[0]) * cellWidth;
                int drawY = drawRowY * cellHeight;
                drawBlock(g, drawX, drawY, color);
            }
        }
    }

    private void drawGhostPiece(Graphics g, Tetromino tetromino) {
        int ghostY = tetromino.getY();
        while (gameLogic.getBoard().isValidPosition(tetromino.getCoords(), tetromino.getX(), ghostY + 1)) {
            ghostY++;
        }

        if (ghostY <= tetromino.getY()) return;

        int[][] coords = tetromino.getCoords();
        Color ghostColor = new Color(128, 128, 128, 100); // 半透明のグレー
        int cellWidth = getCellWidth();
        int cellHeight = getCellHeight();
        int hiddenRows = Board.TOTAL_BOARD_HEIGHT - Board.VISIBLE_BOARD_HEIGHT;
        
        g.setColor(ghostColor);
        for (int[] p : coords) {
            int ghostBlockY = ghostY + p[1];
            int drawRowY = ghostBlockY - hiddenRows;

            if (drawRowY >= 0) {
                int drawX = (tetromino.getX() + p[0]) * cellWidth;
                int drawY = drawRowY * cellHeight;
                g.fillRect(drawX, drawY, cellWidth, cellHeight);
            }
        }
    }
    
    private void drawBlock(Graphics g, int x, int y, Color color) {
        g.setColor(color);
        g.fillRect(x, y, getCellWidth(), getCellHeight());
        g.setColor(color.darker());
        g.drawRect(x, y, getCellWidth() - 1, getCellHeight() - 1);
    }
    
    private void drawGarbageBar(Graphics g) {
        int pendingGarbage = gameLogic.getPendingGarbage();
        if (pendingGarbage <= 0) return;

        g.setColor(new Color(255, 50, 50));
        int barWidth = 15;
        int barHeight = Math.min(pendingGarbage * getCellHeight(), getHeight());
        
        g.fillRect(0, getHeight() - barHeight, barWidth, barHeight);
        
        g.setColor(Color.WHITE);
        g.setFont(new Font("Arial", Font.BOLD, 12));
        g.drawString(String.valueOf(pendingGarbage), 2, getHeight() - 5);
    }

    private void drawGameOver(Graphics g) {
        String msg = "Game Over";
        Font font = new Font("Helvetica", Font.BOLD, 30);
        FontMetrics metrics = getFontMetrics(font);
        
        g.setColor(new Color(0, 0, 0, 150));
        g.fillRect(0, getHeight() / 2 - 30, getWidth(), 50);

        g.setColor(Color.WHITE);
        g.setFont(font);
        g.drawString(msg, (getWidth() - metrics.stringWidth(msg)) / 2, getHeight() / 2);
    }
}