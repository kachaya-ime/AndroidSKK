package io.github.kachaya.skk;

import android.content.Context;
import android.util.TypedValue;
import android.view.ContextThemeWrapper;
import android.widget.Button;

/**
 * キーボードの各キー（Button）を生成し、共通のスタイルを適用するためのファクトリクラスです。
 * <p>
 * 実際の入力ビュー（InputView）とカスタマイズ画面（KeyboardCustomizerActivity）の両方で
 * 一貫した見た目を維持するために使用されます。
 * </p>
 */
public class KeyViewFactory {

    /**
     * 指定された KeyConfig に基づいて、スタイル適用済みの Button を生成します。
     *
     * @param context コンテキスト
     * @param config  キーの構成情報
     * @return スタイルが適用された Button インスタンス
     */
    public static Button createKeyButton(Context context, KeyConfig config) {
        // 機能キー（コードあり）か文字キーかでスタイルと背景を切り替える
        boolean isFunctional = (config.code != KeyConfig.CODE_NONE);
        int style = isFunctional ? R.style.FunctionalKeyButton : R.style.CharacterButton;
        int bgRes = isFunctional ? R.drawable.bg_function_button_selector : R.drawable.bg_character_button_selector;

        // ContextThemeWrapper を使用してスタイルを適用した Button を生成
        Button b = new Button(new ContextThemeWrapper(context, style), null, 0);
        b.setText(config.label);
        b.setTag(config);
        b.setAllCaps(false);
        b.setPadding(0, 0, 0, 0);

        // 背景を明示的に設定（Borderless スタイルによる背景消失の防止）
        b.setBackgroundResource(bgRes);

        // ラベルが 2 文字以上の場合はフォントサイズを調整（縮小）
        if (config.label != null && config.label.length() >= 2) {
            b.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
        }

        return b;
    }
}
