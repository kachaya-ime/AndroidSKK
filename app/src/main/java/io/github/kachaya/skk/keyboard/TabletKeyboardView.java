package io.github.kachaya.skk.keyboard;

import android.content.Context;
import android.util.AttributeSet;

import androidx.annotation.Nullable;

/**
 * Tablet 配列のソフトウェアキーボードを表示・管理するビューです。
 * <p>
 * 通常・シフトの 2 つのレイアウトを保持し、修飾キーの状態に応じて動的に表示を切り替えます。
 * 削除キー等のリピート入力（長押し）機能も備えています。
 * </p>
 */
public class TabletKeyboardView extends BaseLayoutKeyboardView {

    public TabletKeyboardView(Context context) {
        super(context, "custom_tablet_layout");
    }

    public TabletKeyboardView(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs, "custom_tablet_layout");
    }

    public TabletKeyboardView(Context context, String baseKey) {
        super(context, baseKey);
    }
}
