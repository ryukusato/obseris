package org.yourcompany.yourproject.config;
import java.awt.event.KeyEvent;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * キー設定をアプリケーション全体で共有するためのクラス。
 * プレイヤーごとにキー設定を管理できるように拡張されています。
 */
public class KeyConfig {

    // マップをプレイヤーごとに用意 (Key: プレイヤー番号, Value: アクション名とキーコードのマップ)
    private static final Map<Integer, Map<String, List<Integer>>> playerKeyMappings = new HashMap<>();

    // アプリケーション起動時にデフォルト設定を読み込む
    static {
        loadDefaultKeys();
    }

    /**
     * デフォルトのキー設定を読み込みます。
     */
    public static void loadDefaultKeys() {
        playerKeyMappings.clear();
        
        // --- プレイヤー1のデフォルトキー (キーボード左側) ---
        Map<String, List<Integer>> p1Keys = new HashMap<>();
        p1Keys.put("moveLeft", new ArrayList<>(List.of(KeyEvent.VK_A)));
        p1Keys.put("moveRight", new ArrayList<>(List.of(KeyEvent.VK_D)));
        p1Keys.put("rotateLeft", new ArrayList<>(List.of(KeyEvent.VK_Z)));
        p1Keys.put("rotateRight", new ArrayList<>(List.of(KeyEvent.VK_X)));
        p1Keys.put("softDrop", new ArrayList<>(List.of(KeyEvent.VK_S)));
        p1Keys.put("hardDrop", new ArrayList<>(List.of(KeyEvent.VK_SPACE)));
        p1Keys.put("hold", new ArrayList<>(List.of(KeyEvent.VK_C)));
        playerKeyMappings.put(1, p1Keys);

        // --- プレイヤー2のデフォルトキー (キーボード右側) ---
        Map<String, List<Integer>> p2Keys = new HashMap<>();
        p2Keys.put("moveLeft", new ArrayList<>(List.of(KeyEvent.VK_LEFT)));
        p2Keys.put("moveRight", new ArrayList<>(List.of(KeyEvent.VK_RIGHT)));
        p2Keys.put("rotateLeft", new ArrayList<>(List.of(KeyEvent.VK_DOWN)));
        p2Keys.put("rotateRight", new ArrayList<>(List.of(KeyEvent.VK_UP)));
        p2Keys.put("softDrop", new ArrayList<>(List.of(KeyEvent.VK_NUMPAD2)));
        p2Keys.put("hardDrop", new ArrayList<>(List.of(KeyEvent.VK_ENTER)));
        p2Keys.put("hold", new ArrayList<>(List.of(KeyEvent.VK_NUMPAD0)));
        playerKeyMappings.put(2, p2Keys);
    }

    /**
     * 指定されたプレイヤーの操作に割り当てられているキーコードのリストを取得します。
     * @param player プレイヤー番号 (1 or 2)
     * @param actionCommand 操作の内部名 (例: "moveLeft")
     * @return キーコードのリスト
     */
    public static List<Integer> getKeysForAction(int player, String actionCommand) {
        return playerKeyMappings.getOrDefault(player, Collections.emptyMap())
                                .getOrDefault(actionCommand, Collections.emptyList());
    }

    /**
     * 指定されたプレイヤーの操作のキー設定を更新します。
     * @param player プレイヤー番号 (1 or 2)
     * @param actionCommand 操作の内部名
     * @param keyCodes 新しいキーコードのリスト
     */
    public static void setKeysForAction(int player, String actionCommand, List<Integer> keyCodes) {
        Map<String, List<Integer>> playerKeys = playerKeyMappings.get(player);
        if (playerKeys != null) {
            playerKeys.put(actionCommand, new ArrayList<>(keyCodes)); // 安全のためコピーを格納
        }
    }

    /**
     * キーコードを人間が読める文字列に変換します。
     * @param keyCode キーコード
     * @return キーの文字列表現
     */
    public static String getKeyText(int keyCode) {
        if (keyCode == KeyEvent.VK_SPACE) {
            return "SPACE";
        }
        return KeyEvent.getKeyText(keyCode);
    }
}