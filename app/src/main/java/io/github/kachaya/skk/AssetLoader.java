package io.github.kachaya.skk;

import android.content.Context;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

/**
 * アセットフォルダからのデータ読み込みを担当するユーティリティクラスです。
 * <p>
 * 文字列の読み込みに加え、JSON オブジェクトや JSON 配列としてのパース機能を提供します。
 * </p>
 */
public class AssetLoader {
    /**
     * 指定されたアセットファイルを UTF-8 文字列として読み込みます。
     *
     * @param context コンテキスト
     * @param fileName アセット内のファイル名（パス）
     * @return ファイルの内容。エラーが発生した場合は null。
     */
    public static String loadAssetString(Context context, String fileName) {
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(context.getAssets().open(fileName), StandardCharsets.UTF_8))) {
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                sb.append(line).append("\n");
            }
            return sb.toString();
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * 指定されたアセットファイルを読み込み、JSONArray としてパースします。
     *
     * @param context コンテキスト
     * @param fileName アセット内のファイル名
     * @return パースされた JSONArray。エラーまたはファイル不在時は null。
     */
    public static JSONArray loadJsonArray(Context context, String fileName) {
        try {
            String json = loadAssetString(context, fileName);
            return (json != null) ? new JSONArray(json) : null;
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * 指定されたアセットファイルを読み込み、JSONObject としてパースします。
     *
     * @param context コンテキスト
     * @param fileName アセット内のファイル名
     * @return パースされた JSONObject。エラーまたはファイル不在時は null。
     */
    public static JSONObject loadJsonObject(Context context, String fileName) {
        try {
            String json = loadAssetString(context, fileName);
            return (json != null) ? new JSONObject(json) : null;
        } catch (Exception e) {
            return null;
        }
    }
}
