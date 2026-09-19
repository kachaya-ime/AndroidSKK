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
