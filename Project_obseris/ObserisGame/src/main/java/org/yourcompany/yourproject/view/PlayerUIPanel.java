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
        // メインのレイアウト
        setLayout(new BorderLayout(5, 5));

        // --- 中央 ---
        TetrisPanel tetrisPanel = new TetrisPanel(gameLogic);
        add(tetrisPanel, BorderLayout.CENTER);

        // --- 右側 ---
        SidePanel sidePanel = new SidePanel(gameLogic);
        add(sidePanel, BorderLayout.EAST);

        // --- 左側 (HoldとInfoをまとめる) ---
        // 新しいパネルを左側用に作成
        JPanel westPanel = new JPanel();
        westPanel.setLayout(new BoxLayout(westPanel, BoxLayout.Y_AXIS));
        westPanel.setOpaque(false); // 背景を透過させる

        // HoldPanelとInfoPanelを生成
        HoldPanel holdPanel = new HoldPanel(gameLogic);
        InfoPanel infoPanel = new InfoPanel(gameLogic);

        // Holdを上(NORTH)に、Infoを下(SOUTH)に配置
        westPanel.add(holdPanel);
        westPanel.add(Box.createRigidArea(new Dimension(0, 10))); // 10pxの隙間
        westPanel.add(infoPanel);
        westPanel.add(Box.createVerticalGlue());

        // まとめたwestPanelを、全体の左(WEST)に配置
        add(westPanel, BorderLayout.WEST);
    }
}