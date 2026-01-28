package io.github.kachaya.skk;

import android.view.KeyEvent;

/**
 * 全角カタカナモードを管理するクラスです。
 * <p>
 * ローマ字入力を受け取り、全角のカタカナへと変換を行います。
 * 確定済みのテキストや辞書からの候補を全角カタカナへ変換する振る舞いを持ちます。
 * </p>
 */
enum SKKModeFullKatakana implements SKKMode {
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
     * 与えられたテキストを全角カタカナへ変換します。
     *
     * @param text 変換対象のテキスト
     * @return 全角カタカナへ変換された CharSequence
     */
    @Override
    public CharSequence convertText(CharSequence text) {
        return RomajiConverter.toWideKatakana(text);
    }

    /**
     * カタカナモードからのトグル先を取得します。
     * 半角カタカナの使用設定が有効な場合は半角カタカナへ、無効な場合はひらがなへ遷移します。
     *
     * @param context SKKエンジンのコンテキスト
     * @return 遷移先の {@link SKKMode}
     */
    @Override
    public SKKMode getToggledKanaMode(SKKEngine context) {
        if (context.useJisx0201Kana()) {
            return SKKModeHalfKatakana.INSTANCE;
        } else {
            return SKKModeFullHiragana.INSTANCE;
        }
    }

    /**
     * カタカナモード有効時に表示するアイコンのリソース ID を取得します。
     *
     * @return アイコンのリソース ID
     */
    @Override
    public int getIcon() {
        return R.drawable.ic_mode_full_katakana;
    }

    /**
     * モード切替時に表示するツールチップ文字列を取得します。
     *
     * @return "ア"
     */
    @Override
    public String getText() {
        return "ア";
    }
}
