package org.yourcompany.yourproject.view;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Font;
import java.awt.GridLayout;
import java.io.IOException;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel; // ★ インポート
import javax.swing.SwingConstants;

import org.yourcompany.yourproject.config.PlayerType;

public class Title extends JFrame {

    /**
     * ★ 1. 利用可能なAIモデル (.onnx) のリストを保持する
     */
    private final String[] availableModels;

    public Title() {
        setTitle("Tetris Title");
        setSize(400, 300);
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setLocationRelativeTo(null);
        
        // ★ 2. (コンストラクタの最初で)利用可能なモデルをスキャンする
        this.availableModels = scanAvailableModels();

        JPanel panel = new JPanel(new BorderLayout());
        panel.setBackground(Color.LIGHT_GRAY);

        JLabel titleLabel = new JLabel("TETRIS VERSUS", SwingConstants.CENTER);
        titleLabel.setFont(new Font("Arial", Font.BOLD, 48));
        panel.add(titleLabel, BorderLayout.CENTER);

        JPanel buttonPanel = new JPanel(new GridLayout(4, 1, 10, 10)); 
        buttonPanel.setBorder(BorderFactory.createEmptyBorder(20, 50, 20, 50));

        // --- Player vs Player ボタン ---
        JButton pvpButton = new JButton("Player vs Player");
        pvpButton.addActionListener(e -> {
            new VersusGameFrame(PlayerType.HUMAN, PlayerType.HUMAN, null, null);
            dispose();
        });

        // --- Player vs AI ボタン (★ 修正) ---
        JButton pvcButton = new JButton("Player vs AI");
        pvcButton.addActionListener(e -> {
            // スキャンしたモデルリストからAI (Player 2) を選択させる
            String aiModel = getAIModelSelection("AI (Player 2) のモデルを選択してください:", availableModels);
            
            // ユーザーがキャンセルしなかった場合
            if (aiModel != null) {
                new VersusGameFrame(PlayerType.HUMAN, PlayerType.AI, null, aiModel);
                dispose();
            }
        });
        
        // --- AI vs AI ボタン (★ 修正) ---
        JButton cvcButton = new JButton("AI vs AI");
        cvcButton.addActionListener(e -> {
            // AI 1 のモデルを選択
            String ai1Model = getAIModelSelection("AI (Player 1) のモデルを選択してください:", availableModels);

            if (ai1Model != null) {
                // AI 2 のモデルを選択
                String ai2Model = getAIModelSelection("AI (Player 2) のモデルを選択してください:", availableModels);

                if (ai2Model != null) {
                    new VersusGameFrame(PlayerType.AI, PlayerType.AI, ai1Model, ai2Model);
                    dispose();
                }
            }
        });

        // --- Option ボタン ---
        JButton optionButton = new JButton("Option");
        optionButton.addActionListener(e -> new Option().setVisible(true));

        buttonPanel.add(pvpButton);
        buttonPanel.add(pvcButton);
        buttonPanel.add(cvcButton);
        buttonPanel.add(optionButton);
        panel.add(buttonPanel, BorderLayout.SOUTH);

        add(panel);
        setVisible(true);
    }

    /**
     * ★ 3. AIのモデル名を選択させる「ドロップダウン」ダイアログを表示する
     */
    private String getAIModelSelection(String message, String[] modelList) {
        
        if (modelList == null || modelList.length == 0) {
             // スキャン失敗時やモデルが0個の場合は、手入力ダイアログに戻す
             return (String) JOptionPane.showInputDialog(
                this, 
                message + "\n(モデルが見つからないため、手動で入力してください)", 
                "AI Model Selection (Scan failed)", 
                JOptionPane.QUESTION_MESSAGE, null, null, 
                "tetris_model_examination.onnx" // デフォルト
             );
        }

        // JOptionPane に選択肢の配列 (modelList) を渡すと、自動的にドロップダウンリストになる
        return (String) JOptionPane.showInputDialog(
            this, // 親フレーム
            message, // メッセージ
            "AI Model Selection", // タイトル
            JOptionPane.QUESTION_MESSAGE,
            null, // アイコン
            modelList, // ★ 選択肢の配列 (ドロップダウンになる)
            modelList[0] // 初期値 (リストの先頭)
        );
    }

    /**
     * ★ 4. resources フォルダをスキャンして、利用可能なAIモデル (.onnx) のリストを取得する
     * * @return (例: ["model_A.onnx", "model_B.onnx"])
     * @note この方法はIDE実行時には動作しますが、JARファイルにすると動作しません。
     */
    private String[] scanAvailableModels() {
        List<String> modelNames = new ArrayList<>();
        
        try {
            // 1. クラスパスのルート (resources フォルダがコピーされる場所) を取得
            URL rootUrl = Title.class.getClassLoader().getResource("");
            if (rootUrl == null) {
                System.err.println("Could not find classpath root.");
                return new String[0];
            }
            
            // "file:..." で始まるURIでなければスキャンできない (JARファイルなど)
            if (!"file".equals(rootUrl.getProtocol())) {
                 System.err.println("Cannot scan models: Not running from file system (maybe a JAR?).");
                 // ★ JAR実行時のフォールバック (手動リスト)
                 // ここに、resources に置いたモデル名を手動で書いておくのが確実
                 return new String[] {
                    "tetris_model_examination.onnx", 
                    "milestone_gen_1900.onnx",
                    "tetris_model_v2_final.onnx"
                    // 他のモデルファイル...
                 };
            }
            
            Path rootPath = Paths.get(rootUrl.toURI());

            // 2. "resources" フォルダ (rootPath) 以下をスキャン
            try (Stream<Path> stream = Files.walk(rootPath, 5)) { // 5階層までスキャン
                modelNames = stream
                    .filter(path -> path.toString().endsWith(".onnx")) // .onnx ファイルのみ
                    .map(path -> path.getFileName().toString())  // フルパスからファイル名のみ抽出
                    .filter(name -> {
                        // 3. ペアとなる ".data" ファイルの存在も確認
                        //    (AIPlayer がロードできることの保証)
                        String dataName = name + ".data";
                        return (Title.class.getClassLoader().getResource(dataName) != null);
                    })
                    .distinct() // 重複排除
                    .collect(Collectors.toList());
            }

        } catch (IOException | URISyntaxException e) {
            e.printStackTrace();
            System.err.println("Failed to scan models. Returning empty list.");
            return new String[0];
        }
        
        System.out.println("Found " + modelNames.size() + " valid models: " + modelNames);
        return modelNames.toArray(new String[0]);
    }
}