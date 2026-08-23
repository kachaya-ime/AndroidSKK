package io.github.kachaya.skk.keyboard;

import android.content.Context;
import android.util.AttributeSet;
import android.view.HapticFeedbackConstants;
import android.view.View;
import android.widget.LinearLayout;

import androidx.annotation.Nullable;
import androidx.preference.PreferenceManager;

/**
 * すべてのソフトウェアキーボードビューの基底抽象クラスです。
 * <p>
 * 共通のリスナー管理、触覚フィードバック（バイブレーション）、および修飾キー状態の更新インターフェースを提供します。
 * </p>
 */
public abstract class KeyboardView extends LinearLayout {

    /** キーアクションの通知先リスナー。 */
    public OnKeyActionListener mListener;
    /** 触覚フィードバックが有効かどうか。 */
    protected boolean mHapticEnabled;

    public KeyboardView(Context context) {
        super(context);
    }

    public KeyboardView(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
    }

    /**
     * キーアクションリスナーを設定します。
     *
     * @param listener リスナー
     */
    public void setOnKeyActionListener(OnKeyActionListener listener) {
        mListener = listener;
    }

    /**
     * キーボードの行の高さを設定します。
     * <p>
     * サブクラスでレイアウトの再構築が必要な場合にオーバーライドします。
     * </p>
     *
     * @param height 行の高さ（ピクセル）
     */
    public void setRowHeight(int height) {
        // サブクラスで必要に応じて実装
    }

    /**
     * 共有設定から最新の設定（触覚フィードバックの有無等）を読み込みます。
     * サブクラスでオーバーライドする場合は super.readPrefs() を呼び出す必要があります。
     */
    public void readPrefs() {
        mHapticEnabled = PreferenceManager.getDefaultSharedPreferences(getContext())
                .getBoolean("haptic_feedback", true);
    }

    /**
     * キーボードの修飾キー状態（Shift, Ctrl 等）を反映させます。
     *
     * @param state 新しい状態
     */
    public abstract void updateState(KeyboardState state);

    /**
     * 設定で有効な場合のみ、指定されたビューに対して触覚フィードバック（キー押下バイブ）を実行します。
     *
     * @param v 対象のビュー
     */
    protected void performHapticFeedback(View v) {
        if (mHapticEnabled) {
            v.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP,
                    HapticFeedbackConstants.FLAG_IGNORE_GLOBAL_SETTING);
        }
    }
}
