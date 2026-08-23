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
 * キーボード配列の永続化（保存・読み込み）を管理するクラスです。
 * <p>
 * アプリの内部ストレージ（files/layouts/）に JSON ファイルとしてレイアウト情報を保持します。
 * </p>
 */
public class LayoutManager {
    /** レイアウトファイルを保存するディレクトリ名。 */
    private static final String LAYOUT_DIR = "layouts";
    /** レイアウト更新日時を記録するための設定キー。 */
    public static final String PREF_LAYOUT_UPDATED = "layout_updated_at";

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
        keys.add("custom_symbols_layout");
        return keys;
    }

    /**
     * 指定されたキーに対応するファイルオブジェクトを取得します。
     */
    private static File getLayoutFile(Context context, String key) {
        File dir = new File(context.getFilesDir(), LAYOUT_DIR);
        return new File(dir, key + ".json");
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
