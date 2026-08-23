package io.github.kachaya.skk.engine;

import android.view.KeyEvent;

/**
 * 半角英数モード（ASCII）を管理するクラスです。
 * <p>
 * キー入力を受け取り、半角のアルファベットや記号として直接入力（確定）を行います。
 * SKKMode インターフェースにおける標準的な直接入力の振る舞いを実装しています。
 * </p>
 */
public enum SKKModeHalfLatin implements SKKMode {
    /** シングルトンインスタンス。 */
    INSTANCE;

    /**
     * キー入力を直接コミットします。
     *
     * @param context SKKエンジンのコンテキスト
     * @param code    入力されたキーの Unicode コードポイント
     */
    @Override
    public void processKey(SKKEngine context, int code) {
        String text = new String(Character.toChars(code));
        context.commitTextSKK(convertText(text), 1);
    }

    /**
     * Ctrlキー入力を処理します。Ctrl-J によるかなモード復帰を提供します。
     * <p>
     * Ctrl-P, N, B, F によるカーソル移動をサポートします。
     * </p>
     *
     * @param context SKKエンジンのコンテキスト
     * @param keyCode KeyEventで定義されているキーコード
     * @return イベントを消費した場合は true
     */
    @Override
    public boolean processCtrlKey(SKKEngine context, int keyCode) {
        // 半角英数モードでは常にカーソル移動を処理する。
        // （バッファがある場合にこれを阻止するかどうかは、State 側の責務とする）
        switch (keyCode) {
            case KeyEvent.KEYCODE_J:
                context.handleKanaKey();
                return true;
            case KeyEvent.KEYCODE_P:
                context.sendDownUpKeyEvents(KeyEvent.KEYCODE_DPAD_UP);
                return true;
            case KeyEvent.KEYCODE_N:
                context.sendDownUpKeyEvents(KeyEvent.KEYCODE_DPAD_DOWN);
                return true;
            case KeyEvent.KEYCODE_B:
                context.sendDownUpKeyEvents(KeyEvent.KEYCODE_DPAD_LEFT);
                return true;
            case KeyEvent.KEYCODE_F:
                context.sendDownUpKeyEvents(KeyEvent.KEYCODE_DPAD_RIGHT);
                return true;
        }
        return false;
    }

    /**
     * 与えられたテキストを変換せずにそのまま返します（半角英数）。
     *
     * @param text 変換対象の CharSequence
     * @return そのままの CharSequence
     */
    @Override
    public CharSequence convertText(CharSequence text) {
        return text;
    }

    /**
     * 半角英数モードはトグル対象のかなモードを持ちません。
     *
     * @param context SKKエンジンのコンテキスト
     * @return null
     */
    @Override
    public SKKMode getToggledKanaMode(SKKEngine context) {
        return null;
    }

    /**
     * 半角英数モード有効時に表示するアイコンの種類を取得します。
     *
     * @return {@link SKKIcon#NONE}
     */
    @Override
    public SKKIcon getIcon() {
        return SKKIcon.NONE;
    }

    /**
     * モード切替時に表示するツールチップ文字列を取得します。
     *
     * @return "A"
     */
    @Override
    public String getText() {
        return "A";
    }
}
