package io.github.kachaya.skk.engine;

import android.view.KeyEvent;

/**
 * 半角カタカナモード（JISX0201）を管理するクラスです。
 * <p>
 * ローマ字入力を受け取り、半角のカタカナへと変換を行います。
 * 確定済みのテキストや辞書からの候補を半角カタカナへ変換する振る舞いを持ちます。
 * </p>
 */
public enum SKKModeHalfKatakana implements SKKMode {
    /** シングルトンインスタンス。 */
    INSTANCE;

    /**
     * キー入力をローマ字かな変換エンジンへ渡し、半角カタカナ変換プロセスを継続します。
     *
     * @param context SKKエンジンのコンテキスト
     * @param code    入力されたキーの Unicode コードポイント
     */
    @Override
    public void processKey(SKKEngine context, int code) {
        context.processRomaji(code);
    }

    /**
     * Ctrlキー入力を処理します。割り当てられた CtrlAction に応じて各種アクションを実行します。
     *
     * @param context SKKエンジンのコンテキスト
     * @param action  割り当てられている CtrlAction
     * @return イベントを消費した場合は true
     */
    @Override
    public boolean processCtrlKey(SKKEngine context, CtrlAction action) {
        switch (action) {
            case KANA_KEY:
                context.handleKanaKey();
                return true;
            case TOGGLE_EN_JP:
                context.toggleEnglishJapanese();
                return true;
            case LAUNCH_SETTINGS:
                context.launchSettings();
                return true;
            case OPEN_EMOJI:
                context.openEmojiPicker();
                return true;
            case CURSOR_UP:
                context.sendDownUpKeyEvents(KeyEvent.KEYCODE_DPAD_UP);
                return true;
            case CURSOR_DOWN:
                context.sendDownUpKeyEvents(KeyEvent.KEYCODE_DPAD_DOWN);
                return true;
            case CURSOR_LEFT:
                context.sendDownUpKeyEvents(KeyEvent.KEYCODE_DPAD_LEFT);
                return true;
            case CURSOR_RIGHT:
                context.sendDownUpKeyEvents(KeyEvent.KEYCODE_DPAD_RIGHT);
                return true;
            case FORWARD_DELETE:
                context.sendDownUpKeyEvents(KeyEvent.KEYCODE_FORWARD_DEL);
                return true;
            case DELETE_CHAR:
                if (!context.handleBackspace()) {
                    context.sendDownUpKeyEvents(KeyEvent.KEYCODE_DEL);
                }
                return true;
            case LINE_START:
                context.sendDownUpKeyEvents(KeyEvent.KEYCODE_MOVE_HOME);
                return true;
            case LINE_END:
                context.sendDownUpKeyEvents(KeyEvent.KEYCODE_MOVE_END);
                return true;
            case KILL_LINE:
                return context.handleKillLine();
            case KILL_LINE_BACKWARD:
                return context.handleKillLineBackward();
            case KILL_WORD_BACKWARD:
                return context.handleKillWordBackward();
            default:
                break;
        }
        return false;
    }

    /**
     * 与えられたテキストを半角カタカナへ変換します。
     * 濁点や半濁点は 2 文字（例: 「ガ」→「ｶﾞ」）に分解されます。
     *
     * @param text 変換対象のテキスト
     * @return 半角カタカナへ変換された CharSequence
     */
    @Override
    public CharSequence convertText(CharSequence text) {
        return RomajiConverter.toHalfKatakana(text);
    }

    /**
     * 半角カタカナモードからのトグル先を取得します。
     *
     * @param context SKKエンジンのコンテキスト
     * @return {@link SKKModeFullHiragana#INSTANCE}
     */
    @Override
    public SKKMode getToggledKanaMode(SKKEngine context) {
        return SKKModeFullHiragana.INSTANCE;
    }

    /**
     * 半角カタカナモード有効時に表示するアイコンの種類を取得します。
     *
     * @return {@link SKKIcon#HALF_KATAKANA}
     */
    @Override
    public SKKIcon getIcon() {
        return SKKIcon.HALF_KATAKANA;
    }

    /**
     * モード切替時に表示するツールチップ文字列を取得します。
     *
     * @return "ｶﾅ"
     */
    @Override
    public String getText() {
        return "ｶﾅ";
    }
}
