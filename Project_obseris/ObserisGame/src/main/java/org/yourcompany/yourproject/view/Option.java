package org.yourcompany.yourproject.view;
import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JDialog;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JTabbedPane;
import javax.swing.JTextField;
import javax.swing.SwingConstants;

import org.yourcompany.yourproject.config.KeyConfig;

public class Option extends JFrame {

    public Option() {
        setTitle("Options");
        setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
        setLayout(new BorderLayout(10, 10));

        // タブ付きペインを作成
        JTabbedPane tabbedPane = new JTabbedPane();

        // プレイヤー1とプレイヤー2の設定パネルをそれぞれ作成し、タブに追加
        JPanel player1Panel = createPlayerKeySettingPanel(1);
        JPanel player2Panel = createPlayerKeySettingPanel(2);
        
        tabbedPane.addTab("Player 1 Keys", player1Panel);
        tabbedPane.addTab("Player 2 Keys", player2Panel);

        // 下部のボタンパネル
        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.CENTER));
        JButton backButton = new JButton("閉じる");
        backButton.addActionListener(e -> dispose());
        
        JButton defaultButton = new JButton("デフォルトに戻す");
        defaultButton.addActionListener(e -> {
            int result = JOptionPane.showConfirmDialog(this, "全てのキー設定を初期化しますか？", "確認", JOptionPane.YES_NO_OPTION);
            if (result == JOptionPane.YES_OPTION) {
                KeyConfig.loadDefaultKeys();
                dispose();
                new Option();
            }
        });
        buttonPanel.add(defaultButton);
        buttonPanel.add(backButton);

        // 全体をフレームに追加
        add(tabbedPane, BorderLayout.CENTER);
        add(buttonPanel, BorderLayout.SOUTH);

        pack();
        setLocationRelativeTo(null);
        setResizable(false);
        setVisible(true);
    }

    private JPanel createPlayerKeySettingPanel(int playerNum) {
        JPanel mainPanel = new JPanel();
        mainPanel.setLayout(new BoxLayout(mainPanel, BoxLayout.Y_AXIS));
        mainPanel.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));

        mainPanel.add(createKeySettingRow("左移動", "moveLeft", playerNum));
        mainPanel.add(createKeySettingRow("右移動", "moveRight", playerNum));
        mainPanel.add(createKeySettingRow("左回転", "rotateLeft", playerNum));
        mainPanel.add(createKeySettingRow("右回転", "rotateRight", playerNum));
        mainPanel.add(createKeySettingRow("ソフトドロップ", "softDrop", playerNum));
        mainPanel.add(createKeySettingRow("ハードドロップ", "hardDrop", playerNum));
        mainPanel.add(createKeySettingRow("ホールド", "hold", playerNum));
        mainPanel.add(Box.createVerticalGlue());
        
        return mainPanel;
    }

    private JPanel createKeySettingRow(String labelName, String actionCommand, int playerNum) {
        JPanel panel = new JPanel(new BorderLayout(10, 0));
        panel.setMaximumSize(new Dimension(350, 40));

        JLabel label = new JLabel(labelName, JLabel.LEFT);
        label.setPreferredSize(new Dimension(100, 30));
        panel.add(label, BorderLayout.WEST);

        JTextField keyField = new JTextField();
        keyField.setEditable(false);
        keyField.setFocusable(false);
        updateKeyField(keyField, actionCommand, playerNum);
        panel.add(keyField, BorderLayout.CENTER);

        JButton changeButton = new JButton("変更");
        changeButton.addActionListener(e -> {
            showKeyEditDialog(actionCommand, keyField, playerNum);
        });
        panel.add(changeButton, BorderLayout.EAST);

        return panel;
    }

    private void showKeyEditDialog(String actionCommand, JTextField keyFieldToUpdate, int playerNum) {
        JDialog dialog = new JDialog(this, "キーを押してください", true);
        dialog.setSize(300, 150);
        dialog.setLocationRelativeTo(this);
        dialog.setLayout(new BorderLayout(10, 10));

        JLabel infoLabel = new JLabel("割り当てたいキーを押してください (複数可)", SwingConstants.CENTER);
        
        JTextField displayField = new JTextField();
        displayField.setEditable(false);
        displayField.setHorizontalAlignment(JTextField.CENTER);
        displayField.setFont(new Font("Arial", Font.BOLD, 16));

        List<Integer> newKeys = new ArrayList<>();

        KeyAdapter keyAdapter = new KeyAdapter() {
            @Override
            public void keyPressed(KeyEvent e) {
                int keyCode = e.getKeyCode();
                if (!newKeys.contains(keyCode)) {
                    newKeys.add(keyCode);
                    String text = newKeys.stream().map(KeyConfig::getKeyText).collect(Collectors.joining(", "));
                    displayField.setText(text);
                }
            }
        };

        dialog.addKeyListener(keyAdapter);
        dialog.setFocusable(true);
        dialog.requestFocusInWindow();

        JPanel buttonPanel = new JPanel(new FlowLayout());
        JButton okButton = new JButton("OK");
        okButton.addActionListener(e -> {
            if (!newKeys.isEmpty()) { // 何かキーが押された場合のみ更新
                KeyConfig.setKeysForAction(playerNum, actionCommand, newKeys);
                updateKeyField(keyFieldToUpdate, actionCommand, playerNum);
            }
            dialog.dispose();
        });

        JButton cancelButton = new JButton("Cancel");
        cancelButton.addActionListener(e -> dialog.dispose());

        buttonPanel.add(okButton);
        buttonPanel.add(cancelButton);

        dialog.add(infoLabel, BorderLayout.NORTH);
        dialog.add(displayField, BorderLayout.CENTER);
        dialog.add(buttonPanel, BorderLayout.SOUTH);
        
        dialog.setVisible(true);
    }

    private void updateKeyField(JTextField keyField, String actionCommand, int playerNum) {
        List<Integer> keyCodes = KeyConfig.getKeysForAction(playerNum, actionCommand);
        String text = keyCodes.stream().map(KeyConfig::getKeyText).collect(Collectors.joining(", "));
        keyField.setText(text);
    }
}