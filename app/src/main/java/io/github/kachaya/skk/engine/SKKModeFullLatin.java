package io.github.kachaya.skk.engine;

import android.view.KeyEvent;

/**
 * 全角英数モード（JISX0208 Latin）を管理するクラスです。
 * <p>
 * キー入力を受け取り、全角のアルファベットや記号として直接入力（確定）を行います。
 * </p>
 */
public enum SKKModeFullLatin implements SKKMode {
    /** シングルトンインスタンス。 */
    INSTANCE;

    /**
     * キー入力を全角に変換して直接コミットします。
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
     * 与えられたテキストを全角英数へ変換します。
     *
     * @param text 変換対象のテキスト
     * @return 全角英数へ変換された CharSequence
     */
    @Override
    public CharSequence convertText(CharSequence text) {
        return RomajiConverter.toWideLatin(text);
    }

    /**
     * 全角英数モードはトグル対象のかなモードを持ちません。
     *
     * @param context SKKエンジンのコンテキスト
     * @return null
     */
    @Override
    public SKKMode getToggledKanaMode(SKKEngine context) {
        return null;
    }

    /**
     * 全角英数モード有効時に表示するアイコンの種類を取得します。
     *
     * @return {@link SKKIcon#FULL_LATIN}
     */
    @Override
    public SKKIcon getIcon() {
        return SKKIcon.FULL_LATIN;
    }

    /**
     * モード切替時に表示するツールチップ文字列を取得します。
     *
     * @return "全英"
     */
    @Override
    public String getText() {
        return "全英";
    }
}
