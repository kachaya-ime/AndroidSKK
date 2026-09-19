package io.github.kachaya.skk.keyboard;

import android.content.Context;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * キーボード配列の永続化（保存・読み込み）およびカスタマイズ用パレット・構成情報を一元管理するクラスです。
 * <p>
 * アプリの内部ストレージ（files/layouts/）に JSON ファイルとしてレイアウト情報を保持します。
 * </p>
 */
public class LayoutManager {
    /** レイアウトファイルを保存するディレクトリ名。 */
    private static final String LAYOUT_DIR = "layouts";
    /** レイアウト更新日時を記録するための設定キー。 */
    public static final String PREF_LAYOUT_UPDATED = "layout_updated_at";

    /** パレット用：特殊キー (QWERTY等) */
    public static final List<KeyConfig> PALETTE_SPECIAL_KEYS = new ArrayList<KeyConfig>() {{
        add(new KeyConfig(KeyConfig.CODE_SPACE));
        add(new KeyConfig(KeyConfig.CODE_ENTER));
        add(new KeyConfig(KeyConfig.CODE_BACKSPACE));
        add(new KeyConfig(KeyConfig.CODE_SHIFT));
        add(new KeyConfig(KeyConfig.CODE_CTRL));
        add(new KeyConfig(KeyConfig.CODE_TAB));
        add(new KeyConfig(KeyConfig.CODE_LEFT));
        add(new KeyConfig(KeyConfig.CODE_UP));
        add(new KeyConfig(KeyConfig.CODE_DOWN));
        add(new KeyConfig(KeyConfig.CODE_RIGHT));
        add(new KeyConfig(KeyConfig.CODE_SYM));
        add(new KeyConfig(KeyConfig.CODE_ABC));
        add(new KeyConfig(KeyConfig.CODE_GAP));
    }};

    /** パレット用：Tablet特殊キー（Symキーを除外） */
    public static final List<KeyConfig> PALETTE_SPECIAL_KEYS_TABLET = new ArrayList<KeyConfig>() {{
        add(new KeyConfig(KeyConfig.CODE_SPACE));
        add(new KeyConfig(KeyConfig.CODE_ENTER));
        add(new KeyConfig(KeyConfig.CODE_BACKSPACE));
        add(new KeyConfig(KeyConfig.CODE_SHIFT));
        add(new KeyConfig(KeyConfig.CODE_CTRL));
        add(new KeyConfig(KeyConfig.CODE_TAB));
        add(new KeyConfig(KeyConfig.CODE_LEFT));
        add(new KeyConfig(KeyConfig.CODE_UP));
        add(new KeyConfig(KeyConfig.CODE_DOWN));
        add(new KeyConfig(KeyConfig.CODE_RIGHT));
        add(new KeyConfig(KeyConfig.CODE_GAP));
    }};

    /** パレット用：英数字 */
    public static final List<KeyConfig> PALETTE_ALPHA_KEYS = new ArrayList<KeyConfig>() {{
        String alpha = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz";
        for (char c : alpha.toCharArray()) {
            add(new KeyConfig(String.valueOf(c)));
        }
    }};

    /** パレット用：記号（QWERTYカスタマイズ用。数字を含む標準的なセット） */
    public static final List<KeyConfig> PALETTE_SYMBOL_KEYS = new ArrayList<KeyConfig>() {{
        String symbols = "0123456789!\"#$%&'()*+,-./:;<=>?@[\\]^_`{|}~¥";
        for (char c : symbols.toCharArray()) {
            add(new KeyConfig(String.valueOf(c)));
        }
    }};

    /** パレット用：記号バー専用（物理キーボードにない記号を補完するための最小限の ASCII セット） */
    public static final List<KeyConfig> PALETTE_SYMBOL_BAR_KEYS = new ArrayList<KeyConfig>() {{
        String basic = "!\"#$%&'()*+,-./:;<=>?@[\\]^_`{|}~¥";
        for (char c : basic.toCharArray()) {
            add(new KeyConfig(String.valueOf(c)));
        }
    }};

    /**
     * 指定されたキーに対応するレイアウト情報をファイルから読み込みます。
     *
     * @param context コンテキスト
     * @param key レイアウトを識別するキー名
     * @param defaultValue ファイルが存在しない場合のデフォルト値
     * @return 読み込まれたレイアウトの JSON 文字列
     */
    public static String loadLayout(Context context, String key, String defaultValue) {
        File file = getLayoutFile(context, key);
        if (file.exists()) {
            StringBuilder sb = new StringBuilder();
            try (FileInputStream fis = new FileInputStream(file);
                 InputStreamReader isr = new InputStreamReader(fis, StandardCharsets.UTF_8);
                 BufferedReader reader = new BufferedReader(isr)) {
                String line;
                while ((line = reader.readLine()) != null) {
                    sb.append(line).append("\n");
                }
                return sb.toString().trim();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        return defaultValue;
    }

    /**
     * 指定されたキーのレイアウト情報をファイルに保存します。
     *
     * @param context コンテキスト
     * @param key レイアウトを識別するキー名
     * @param layoutJson 保存するレイアウトの JSON 文字列
     */
    public static void saveLayout(Context context, String key, String layoutJson) {
        File file = getLayoutFile(context, key);
        File dir = file.getParentFile();
        if (dir != null && !dir.exists()) {
            dir.mkdirs();
        }

        try (FileOutputStream fos = new FileOutputStream(file)) {
            fos.write(layoutJson.getBytes(StandardCharsets.UTF_8));
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    /**
     * アプリによって管理されている（カスタマイズ可能な）レイアウトキーのリストを返します。
     *
     * @return レイアウトキーのリスト
     */
    public static List<String> getManagedKeys() {
        List<String> keys = new ArrayList<>();
        keys.add("custom_qwerty_layout_normal");
        keys.add("custom_qwerty_layout_shift");
        keys.add("custom_qwerty_layout_symbol");
        keys.add("custom_tablet_layout_normal");
        keys.add("custom_tablet_layout_shift");
        keys.add("custom_symbols_layout");
        return keys;
    }

    /**
     * 指定されたターゲットレイアウトキーに対応する特殊キーパレットを返します。
     *
     * @param targetPrefKey 対象のレイアウトキー（例: "custom_qwerty_layout", "custom_tablet_layout"）
     * @return 特殊キーパレットのリスト
     */
    public static List<KeyConfig> getSpecialKeysPalette(String targetPrefKey) {
        if ("custom_tablet_layout".equals(targetPrefKey)) {
            return PALETTE_SPECIAL_KEYS_TABLET;
        }
        return PALETTE_SPECIAL_KEYS;
    }

    /**
     * 指定されたターゲットレイアウトキーに対応する英字キーパレットを返します。
     *
     * @param targetPrefKey 対象のレイアウトキー
     * @return 英字キーパレットのリスト
     */
    public static List<KeyConfig> getAlphaKeysPalette(String targetPrefKey) {
        return PALETTE_ALPHA_KEYS;
    }

    /**
     * 指定されたターゲットレイアウトキーに対応する記号キーパレットを返します。
     *
     * @param targetPrefKey 対象のレイアウトキー
     * @return 記号キーパレットのリスト
     */
    public static List<KeyConfig> getSymbolKeysPalette(String targetPrefKey) {
        if ("combined_symbols".equals(targetPrefKey)) {
            return PALETTE_SYMBOL_BAR_KEYS;
        }
        return PALETTE_SYMBOL_KEYS;
    }

    /**
     * 指定されたターゲットレイアウトキーが記号バー（単一モード構成）かどうかを返します。
     *
     * @param targetPrefKey 対象のレイアウトキー
     * @return 記号バーの場合は true
     */
    public static boolean isSymbolBar(String targetPrefKey) {
        return "combined_symbols".equals(targetPrefKey);
    }

    /**
     * 指定されたターゲットレイアウトキーが「記号」サブモード（3モード構成）を持つかどうかを返します。
     *
     * @param targetPrefKey 対象のレイアウトキー
     * @return 記号モードを持つ場合は true
     */
    public static boolean hasSymbolMode(String targetPrefKey) {
        return "custom_qwerty_layout".equals(targetPrefKey);
    }

    /**
     * 指定されたキーに対応するファイルオブジェクトを取得します。
     */
    private static File getLayoutFile(Context context, String key) {
        File dir = new File(context.getFilesDir(), LAYOUT_DIR);
        return new File(dir, key + ".json");
    }

    /**
     * 指定されたターゲットレイアウトキーに対応する保存済みカスタムレイアウトファイルを削除し、初期状態に戻します。
     *
     * @param context コンテキスト
     * @param targetPrefKey 対象のレイアウトキー（例: "custom_qwerty_layout", "custom_tablet_layout", "combined_symbols"）
     */
    public static void clearLayout(Context context, String targetPrefKey) {
        if ("combined_symbols".equals(targetPrefKey)) {
            File file = getLayoutFile(context, "custom_symbols_layout");
            if (file.exists()) {
                file.delete();
            }
        } else {
            String[] suffixes = {"_normal", "_shift", "_symbol"};
            for (String suffix : suffixes) {
                File file = getLayoutFile(context, targetPrefKey + suffix);
                if (file.exists()) {
                    file.delete();
                }
            }
        }
    }

    /**
     * 内部ストレージに保存されているすべてのカスタムレイアウトファイルを削除し、初期状態に戻します。
     *
     * @param context コンテキスト
     */
    public static void clearLayouts(Context context) {
        File dir = new File(context.getFilesDir(), LAYOUT_DIR);
        if (dir.exists() && dir.isDirectory()) {
            File[] files = dir.listFiles();
            if (files != null) {
                for (File file : files) {
                    file.delete();
                }
            }
        }
    }
}
