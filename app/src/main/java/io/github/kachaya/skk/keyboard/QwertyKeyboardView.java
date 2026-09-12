package io.github.kachaya.skk.keyboard;

import android.content.Context;
import android.util.AttributeSet;

import androidx.annotation.Nullable;

/**
 * QWERTY 配列のソフトウェアキーボードを表示・管理するビューです。
 * <p>
 * 通常・シフト・記号の 3 つのレイアウトを保持し、修飾キーの状態に応じて動的に表示を切り替えます。
 * 削除キー等のリピート入力（長押し）機能も備えています。
 * </p>
 */
public class QwertyKeyboardView extends BaseLayoutKeyboardView {

    public QwertyKeyboardView(Context context) {
        super(context, "custom_qwerty_layout");
    }

    public QwertyKeyboardView(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs, "custom_qwerty_layout");
    }

    public QwertyKeyboardView(Context context, String baseKey) {
        super(context, baseKey);
    }
}
