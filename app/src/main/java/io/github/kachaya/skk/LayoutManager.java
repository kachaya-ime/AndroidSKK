package io.github.kachaya.skk;

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
 * キーボード配列の保存・読み込みを管理するクラスです。
 * アプリの内部ストレージにファイルとして保存します。
 */
public class LayoutManager {
    private static final String LAYOUT_DIR = "layouts";
    public static final String PREF_LAYOUT_UPDATED = "layout_updated_at";

    /**
     * 指定されたキーのレイアウトをファイルから読み込みます。
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
     * 指定されたキーのレイアウトをファイルに保存します。
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
     * 管理されているすべてのレイアウトキーのリストを返します。
     */
    public static List<String> getManagedKeys() {
        List<String> keys = new ArrayList<>();
        keys.add("custom_qwerty_layout_normal");
        keys.add("custom_qwerty_layout_shift");
        keys.add("custom_qwerty_layout_symbol");
        keys.add("custom_symbols_layout");
        return keys;
    }

    private static File getLayoutFile(Context context, String key) {
        File dir = new File(context.getFilesDir(), LAYOUT_DIR);
        return new File(dir, key + ".json");
    }

    /**
     * 保存されているすべてのレイアウトファイルを削除します。
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
