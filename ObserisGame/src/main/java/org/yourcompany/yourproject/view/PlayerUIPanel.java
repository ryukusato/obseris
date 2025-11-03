package org.yourcompany.yourproject.view;
import java.awt.BorderLayout;
import java.awt.Dimension;

import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JPanel;

import org.yourcompany.yourproject.model.GameLogic;

/**
 * 一人のプレイヤーのUIコンポーネント（HOLD, 盤面, NEXT/SCORE）をまとめるパネル。
 * InfoPanelがHoldPanelの下に配置されるようにレイアウトが変更されています。
 */
public class PlayerUIPanel extends JPanel {

    public PlayerUIPanel(GameLogic gameLogic) {
        setupPanel(gameLogic);
    }

    private void setupPanel(GameLogic gameLogic) {
        // メインのレイアウト
        setLayout(new BorderLayout(5, 5));

        // --- 中央 ---
        TetrisPanel tetrisPanel = new TetrisPanel(gameLogic);
        add(tetrisPanel, BorderLayout.CENTER);

        // --- 右側 ---
        SidePanel sidePanel = new SidePanel(gameLogic);
        add(sidePanel, BorderLayout.EAST);

        // --- 左側 (HoldとInfoをまとめる) ---
        JPanel westPanel = new JPanel();
        westPanel.setLayout(new BoxLayout(westPanel, BoxLayout.Y_AXIS));
        westPanel.setOpaque(false); 

        HoldPanel holdPanel = new HoldPanel(gameLogic);
        InfoPanel infoPanel = new InfoPanel(gameLogic);

        westPanel.add(holdPanel);
        westPanel.add(Box.createRigidArea(new Dimension(0, 10))); 
        westPanel.add(infoPanel);
        westPanel.add(Box.createVerticalGlue());

        add(westPanel, BorderLayout.WEST);
    }

    public void resetGameLogic(GameLogic newLogic) {
        // 既存のコンポーネントをすべて削除
        removeAll();
        
        // 新しいGameLogicでコンポーネントを再構築
        setupPanel(newLogic);

        // コンポーネントの変更をUIに反映させる
        revalidate();
        repaint();
    }
}