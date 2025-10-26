package org.yourcompany.yourproject;
import javax.swing.SwingUtilities;

import org.yourcompany.yourproject.view.Title;

/**
 * アプリケーションのメインエントリーポイント。
 * 最初にタイトル画面を表示します。
 */
public class Main {
    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            Title titleFrame = new Title();
            titleFrame.setVisible(true);
        });
    }
}