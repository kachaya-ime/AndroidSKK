package io.github.kachaya.skk.keyboard;

import android.content.Context;

import io.github.kachaya.skk.AssetLoader;

/**
 * アセットからデフォルトのキーボードレイアウト JSON を取得するためのクラスです。
 * <p>
 * アプリに含まれる標準的な QWERTY 配列や記号配列の定義をキー名に基づいて返します。
 * </p>
 */
public class DefaultLayouts {
    /**
     * 指定されたキー名に対応するデフォルトレイアウトの JSON 文字列を取得します。
     *
     * @param context コンテキスト
     * @param key レイアウトを識別するキー名
     * @return レイアウトの JSON 文字列。見つからない場合は null。
     */
    public static String get(Context context, String key) {
        switch (key) {
            case "custom_qwerty_layout_normal":
                return AssetLoader.loadAssetString(context, "layouts/qwerty_normal.json");
            case "custom_qwerty_layout_shift":
                return AssetLoader.loadAssetString(context, "layouts/qwerty_shift.json");
            case "custom_qwerty_layout_symbol":
                return AssetLoader.loadAssetString(context, "layouts/qwerty_symbol.json");
            case "combined_symbols":
            case "custom_symbols_layout":
                return AssetLoader.loadAssetString(context, "layouts/combined_symbols.json");
            default:
                return null;
        }
    }
}
