package io.github.kachaya.skk.engine;

import android.view.KeyEvent;

/**
 * 全角ひらがなモードを管理するクラスです。
 * <p>
 * SKK の標準的な入力モードであり、ローマ字入力を受け取り、全角のひらがなへと変換を行います。
 * </p>
 */
public enum SKKModeFullHiragana implements SKKMode {
    /** シングルトンインスタンス。 */
    INSTANCE;

    /**
     * キー入力をローマ字かな変換エンジンへ渡します。
     *
     * @param context SKKエンジンのコンテキスト
     * @param code    入力されたキーの Unicode コードポイント
     */
    @Override
    public void processKey(SKKEngine context, int code) {
        context.processRomaji(code);
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
        // ひらがなモードでも常にカーソル移動を処理する。
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
     * 与えられたテキストを変換せずにそのまま返します。
     *
     * @param text 変換対象の CharSequence
     * @return そのままの CharSequence
     */
    @Override
    public CharSequence convertText(CharSequence text) {
        return text;
    }

    /**
     * ひらがなモードからのトグル先を取得します。
     *
     * @param context SKKエンジンのコンテキスト
     * @return {@link SKKModeFullKatakana#INSTANCE}
     */
    @Override
    public SKKMode getToggledKanaMode(SKKEngine context) {
        return SKKModeFullKatakana.INSTANCE;
    }

    /**
     * ひらがなモード有効時に表示するアイコンの種類を取得します。
     *
     * @return {@link SKKIcon#FULL_HIRAGANA}
     */
    @Override
    public SKKIcon getIcon() {
        return SKKIcon.FULL_HIRAGANA;
    }

    /**
     * モード切替時に表示するツールチップ文字列を取得します。
     *
     * @return "あ"
     */
    @Override
    public String getText() {
        return "あ";
    }
}
