package io.github.kachaya.skk.engine;

import android.content.Context;
import android.content.SharedPreferences;
import android.view.KeyEvent;

import androidx.preference.PreferenceManager;

import org.json.JSONObject;

import java.util.HashMap;
import java.util.Map;

/**
 * Ctrl キーショートカット（Ctrl+アルファベット）のカスタマイズ設定を管理するクラスです。
 */
public class CtrlShortcutManager {

    public static final String PREF_KEY = "ctrl_shortcuts_config";

    /**
     * システムで予約されている Ctrl キー組み合わせ（Ctrl+A, Ctrl+C など）かどうかを判定します。
     *
     * @param keyCode キーコード
     * @return システム予約されている場合は true
     */
    public static boolean isSystemReserved(int keyCode) {
        switch (keyCode) {
            case KeyEvent.KEYCODE_A:
            case KeyEvent.KEYCODE_C:
            case KeyEvent.KEYCODE_V:
            case KeyEvent.KEYCODE_X:
            case KeyEvent.KEYCODE_Y:
            case KeyEvent.KEYCODE_Z:
                return true;
            default:
                return false;
        }
    }

    /**
     * システム予約キーのラベルを取得します。
     *
     * @param keyCode キーコード
     * @return システム予約機能の説明ラベル
     */
    public static String getReservedLabel(int keyCode) {
        switch (keyCode) {
            case KeyEvent.KEYCODE_A:
                return "システム予約 (全選択)";
            case KeyEvent.KEYCODE_C:
                return "システム予約 (コピー)";
            case KeyEvent.KEYCODE_V:
                return "システム予約 (貼り付け)";
            case KeyEvent.KEYCODE_X:
                return "システム予約 (切り取り)";
            case KeyEvent.KEYCODE_Y:
                return "システム予約 (やり直し)";
            case KeyEvent.KEYCODE_Z:
                return "システム予約 (元に戻す)";
            default:
                return "";
        }
    }

    /**
     * SKK 標準で固定予約されている Ctrl キー組み合わせ（Ctrl+J, Ctrl+G など）かどうかを判定します。
     *
     * @param keyCode キーコード
     * @return SKK 標準予約されている場合は true
     */
    public static boolean isSkkStandardReserved(int keyCode) {
        switch (keyCode) {
            case KeyEvent.KEYCODE_J:
            case KeyEvent.KEYCODE_G:
            case KeyEvent.KEYCODE_Q:
            case KeyEvent.KEYCODE_F:
            case KeyEvent.KEYCODE_B:
            case KeyEvent.KEYCODE_P:
            case KeyEvent.KEYCODE_N:
                return true;
            default:
                return false;
        }
    }

    /**
     * SKK 標準予約キーのラベルを取得します。
     *
     * @param keyCode キーコード
     * @return SKK 標準機能の説明ラベル
     */
    public static String getSkkStandardLabel(int keyCode) {
        switch (keyCode) {
            case KeyEvent.KEYCODE_J:
                return "SKK標準 (確定)";
            case KeyEvent.KEYCODE_G:
                return "SKK標準 (キャンセル)";
            case KeyEvent.KEYCODE_Q:
                return "SKK標準 (かな/カナ切替)";
            case KeyEvent.KEYCODE_F:
                return "SKK標準 (右移動 / 次候補)";
            case KeyEvent.KEYCODE_B:
                return "SKK標準 (左移動 / 前候補)";
            case KeyEvent.KEYCODE_P:
                return "SKK標準 (上移動 / 前候補)";
            case KeyEvent.KEYCODE_N:
                return "SKK標準 (下移動 / 次候補)";
            default:
                return "";
        }
    }

    /**
     * デフォルトのキー割り当てマップを取得します。
     *
     * @return キーコードと CtrlAction のデフォルトマップ
     */
    public static Map<Integer, CtrlAction> getDefaultMappings() {
        Map<Integer, CtrlAction> defaults = new HashMap<>();
        defaults.put(KeyEvent.KEYCODE_B, CtrlAction.CURSOR_LEFT);
        defaults.put(KeyEvent.KEYCODE_F, CtrlAction.CURSOR_RIGHT);
        defaults.put(KeyEvent.KEYCODE_P, CtrlAction.CURSOR_UP);
        defaults.put(KeyEvent.KEYCODE_N, CtrlAction.CURSOR_DOWN);
        defaults.put(KeyEvent.KEYCODE_D, CtrlAction.FORWARD_DELETE);
        defaults.put(KeyEvent.KEYCODE_H, CtrlAction.DELETE_CHAR);
        defaults.put(KeyEvent.KEYCODE_E, CtrlAction.LINE_END);
        defaults.put(KeyEvent.KEYCODE_K, CtrlAction.KILL_LINE);
        defaults.put(KeyEvent.KEYCODE_S, CtrlAction.LAUNCH_SETTINGS);
        defaults.put(KeyEvent.KEYCODE_O, CtrlAction.OPEN_EMOJI);
        defaults.put(KeyEvent.KEYCODE_J, CtrlAction.KANA_KEY);
        defaults.put(KeyEvent.KEYCODE_G, CtrlAction.CANCEL);
        defaults.put(KeyEvent.KEYCODE_Q, CtrlAction.TOGGLE_KANA);
        defaults.put(KeyEvent.KEYCODE_U, CtrlAction.RE_CONVERSION);
        defaults.put(KeyEvent.KEYCODE_L, CtrlAction.CONVERT_PREV_WORD);
        defaults.put(KeyEvent.KEYCODE_R, CtrlAction.CONVERT_NEXT_WORD);
        defaults.put(KeyEvent.KEYCODE_W, CtrlAction.KILL_WORD_BACKWARD);
        defaults.put(KeyEvent.KEYCODE_I, CtrlAction.COMPLETION);
        defaults.put(KeyEvent.KEYCODE_SPACE, CtrlAction.TOGGLE_EN_JP);
        defaults.put(KeyEvent.KEYCODE_ENTER, CtrlAction.NONE);
        defaults.put(KeyEvent.KEYCODE_DEL, CtrlAction.KILL_LINE_BACKWARD);
        return defaults;
    }

    /**
     * 設定から現在の Ctrl キー割り当てマップを読み込みます。
     *
     * @param context コンテキスト
     * @return キーコードと CtrlAction のマップ
     */
    public static Map<Integer, CtrlAction> loadMappings(Context context) {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
        return loadMappings(prefs);
    }

    /**
     * SharedPreferences から現在の Ctrl キー割り当てマップを読み込みます。
     *
     * @param prefs SharedPreferences
     * @return キーコードと CtrlAction のマップ
     */
    public static Map<Integer, CtrlAction> loadMappings(SharedPreferences prefs) {
        Map<Integer, CtrlAction> mappings = getDefaultMappings();
        String jsonStr = prefs.getString(PREF_KEY, null);
        if (jsonStr == null || jsonStr.isEmpty()) {
            return mappings;
        }
        try {
            JSONObject json = new JSONObject(jsonStr);
            for (int keyCode = KeyEvent.KEYCODE_A; keyCode <= KeyEvent.KEYCODE_Z; keyCode++) {
                if (isSystemReserved(keyCode) || isSkkStandardReserved(keyCode)) {
                    continue;
                }
                char letter = (char) ('A' + keyCode - KeyEvent.KEYCODE_A);
                String keyStr = String.valueOf(letter);
                if (json.has(keyStr)) {
                    String actionId = json.getString(keyStr);
                    mappings.put(keyCode, CtrlAction.fromId(actionId));
                }
            }
            if (json.has("SPACE")) {
                mappings.put(KeyEvent.KEYCODE_SPACE, CtrlAction.fromId(json.getString("SPACE")));
            }
            if (json.has("ENTER")) {
                mappings.put(KeyEvent.KEYCODE_ENTER, CtrlAction.fromId(json.getString("ENTER")));
            }
            if (json.has("DEL")) {
                mappings.put(KeyEvent.KEYCODE_DEL, CtrlAction.fromId(json.getString("DEL")));
            }
        } catch (Exception ignored) {
        }
        return mappings;
    }

    /**
     * Ctrl キー割り当てマップを設定に保存します。
     *
     * @param context  コンテキスト
     * @param mappings キーコードと CtrlAction のマップ
     */
    public static void saveMappings(Context context, Map<Integer, CtrlAction> mappings) {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
        saveMappings(prefs, mappings);
    }

    /**
     * Ctrl キー割り当てマップを SharedPreferences に保存します。
     *
     * @param prefs    SharedPreferences
     * @param mappings キーコードと CtrlAction のマップ
     */
    public static void saveMappings(SharedPreferences prefs, Map<Integer, CtrlAction> mappings) {
        try {
            JSONObject json = new JSONObject();
            for (Map.Entry<Integer, CtrlAction> entry : mappings.entrySet()) {
                int code = entry.getKey();
                if (isSystemReserved(code) || isSkkStandardReserved(code)) {
                    continue;
                }
                if (code == KeyEvent.KEYCODE_SPACE) {
                    json.put("SPACE", entry.getValue().getId());
                } else if (code == KeyEvent.KEYCODE_ENTER) {
                    json.put("ENTER", entry.getValue().getId());
                } else if (code == KeyEvent.KEYCODE_DEL) {
                    json.put("DEL", entry.getValue().getId());
                } else if (code >= KeyEvent.KEYCODE_A && code <= KeyEvent.KEYCODE_Z) {
                    char letter = (char) ('A' + code - KeyEvent.KEYCODE_A);
                    json.put(String.valueOf(letter), entry.getValue().getId());
                }
            }
            prefs.edit().putString(PREF_KEY, json.toString()).apply();
        } catch (Exception ignored) {
        }
    }

    /**
     * Ctrl キーショートカットの設定を初期状態にリセットします。
     *
     * @param context コンテキスト
     */
    public static void resetToDefaults(Context context) {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
        prefs.edit().remove(PREF_KEY).apply();
    }
}
