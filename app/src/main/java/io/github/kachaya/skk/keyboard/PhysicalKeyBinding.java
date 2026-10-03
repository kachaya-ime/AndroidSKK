package io.github.kachaya.skk.keyboard;

import android.content.Context;
import android.content.SharedPreferences;
import android.view.KeyEvent;

import androidx.preference.PreferenceManager;

/**
 * 日本語物理キーボード固有のモード切替キー（半角/全角、英数、かな等）の割り当てを管理するクラスです。
 */
public class PhysicalKeyBinding {

    private final int keyCode;

    /**
     * PhysicalKeyBinding を構築します。
     *
     * @param keyCode キーコード
     */
    public PhysicalKeyBinding(int keyCode) {
        this.keyCode = keyCode;
    }

    public int getKeyCode() {
        return keyCode;
    }

    /**
     * 未設定状態かどうかを判定します。
     *
     * @return 未設定の場合は true
     */
    public boolean isUnset() {
        return keyCode == 0;
    }

    /**
     * 日本語キーボード固有のモード切替キーかどうかを判定します。
     *
     * @param keyCode キーコード
     * @return 日本語キーボード固有キーの場合は true
     */
    public static boolean isJapaneseKeyboardKey(int keyCode) {
        switch (keyCode) {
            case KeyEvent.KEYCODE_ZENKAKU_HANKAKU:
            case KeyEvent.KEYCODE_EISU:
            case KeyEvent.KEYCODE_KANA:
            case KeyEvent.KEYCODE_KATAKANA_HIRAGANA:
            case KeyEvent.KEYCODE_HENKAN:
            case KeyEvent.KEYCODE_MUHENKAN:
                return true;
            default:
                return false;
        }
    }

    /**
     * キーイベントがこのキーバインドに一致するか判定します。
     * 日本語キーボード固有キーでない場合は一致とみなしません。
     *
     * @param event KeyEvent
     * @return 一致する場合は true
     */
    public boolean matches(KeyEvent event) {
        if (isUnset() || !isJapaneseKeyboardKey(keyCode)) {
            return false;
        }
        return event.getKeyCode() == keyCode;
    }

    /**
     * 文字列形式（"keyCode"）にシリアライズします。
     *
     * @return シリアライズ文字列
     */
    public String serialize() {
        return String.valueOf(keyCode);
    }

    /**
     * シリアライズ文字列から PhysicalKeyBinding を復元します。
     *
     * @param str シリアライズ文字列
     * @return PhysicalKeyBinding オブジェクト
     */
    public static PhysicalKeyBinding deserialize(String str) {
        if (str == null || str.trim().isEmpty()) {
            return new PhysicalKeyBinding(0);
        }
        try {
            int code = Integer.parseInt(str.trim());
            return new PhysicalKeyBinding(code);
        } catch (Exception e) {
            return new PhysicalKeyBinding(0);
        }
    }

    /**
     * SharedPreferences からキーバインド設定を読み込みます。
     *
     * @param prefs                  SharedPreferences
     * @param prefKey                設定キー
     * @param legacySwitchKey        旧スイッチ設定キー（任意）
     * @param defaultSerializedValue デフォルトのシリアライズ文字列
     * @return PhysicalKeyBinding オブジェクト
     */
    public static PhysicalKeyBinding load(SharedPreferences prefs, String prefKey, String legacySwitchKey, String defaultSerializedValue) {
        if (!prefs.contains(prefKey) && legacySwitchKey != null && prefs.contains(legacySwitchKey)) {
            boolean legacyEnabled = prefs.getBoolean(legacySwitchKey, true);
            if (!legacyEnabled) {
                return new PhysicalKeyBinding(0);
            }
        }
        String val = prefs.getString(prefKey, defaultSerializedValue);
        return deserialize(val);
    }

    /**
     * Context 経由で SharedPreferences からキーバインド設定を読み込みます。
     */
    public static PhysicalKeyBinding load(Context context, String prefKey, String legacySwitchKey, String defaultSerializedValue) {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
        return load(prefs, prefKey, legacySwitchKey, defaultSerializedValue);
    }

    /**
     * キーバインド設定を SharedPreferences に保存します。
     */
    public static void save(Context context, String prefKey, PhysicalKeyBinding binding) {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
        prefs.edit().putString(prefKey, binding.serialize()).apply();
    }

    /**
     * 画面表示用のラベルを取得します。
     *
     * @return 表示用文字列（例: "半角/全角", "かな"）
     */
    public String getDisplayLabel() {
        if (isUnset()) {
            return "未設定";
        }
        return getSingleKeyLabel(keyCode);
    }

    /**
     * 日本語キーボード固有キーの表示用ラベルを取得します。
     */
    public static String getSingleKeyLabel(int keyCode) {
        switch (keyCode) {
            case KeyEvent.KEYCODE_ZENKAKU_HANKAKU:
                return "半角/全角";
            case KeyEvent.KEYCODE_EISU:
                return "英数";
            case KeyEvent.KEYCODE_KANA:
                return "かな";
            case KeyEvent.KEYCODE_KATAKANA_HIRAGANA:
                return "ひらがな/カタカナ";
            case KeyEvent.KEYCODE_HENKAN:
                return "変換";
            case KeyEvent.KEYCODE_MUHENKAN:
                return "無変換";
            default:
                return String.valueOf(keyCode);
        }
    }
}
