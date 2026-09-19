package io.github.kachaya.skk.engine;

/**
 * Ctrl キーとの組み合わせで実行されるアクションの定義です。
 */
public enum CtrlAction {
    NONE("none", "なし (割り当てなし)"),
    CURSOR_LEFT("cursor_left", "左移動 / 前候補"),
    CURSOR_RIGHT("cursor_right", "右移動 / 次候補"),
    CURSOR_UP("cursor_up", "上移動 / 前候補"),
    CURSOR_DOWN("cursor_down", "下移動 / 次候補"),
    FORWARD_DELETE("forward_delete", "カーソル右削除 (Forward Delete)"),
    LINE_START("line_start", "行頭移動 (Line Start)"),
    LINE_END("line_end", "行末移動 (Line End)"),
    KILL_LINE("kill_line", "行末まで削除 (Kill Line)"),
    KILL_LINE_BACKWARD("kill_line_backward", "行頭まで削除"),
    KILL_WORD_BACKWARD("kill_word_backward", "単語単位で削除"),
    LAUNCH_SETTINGS("launch_settings", "設定画面を開く"),
    KANA_KEY("kana_key", "確定 / かなキー"),
    CANCEL("cancel", "キャンセル"),
    TOGGLE_KANA("toggle_kana", "かな / カナ切替"),
    TOGGLE_EN_JP("toggle_en_jp", "英語 / 日本語切替"),
    RE_CONVERSION("re_conversion", "再変換"),
    CONVERT_PREV_WORD("convert_prev_word", "カーソル前のひらがなを変換"),
    CONVERT_NEXT_WORD("convert_next_word", "カーソル後のひらがなを変換"),
    DELETE_CHAR("delete_char", "1文字削除"),
    COMPLETION("completion", "補完"),
    OPEN_EMOJI("open_emoji", "絵文字ピッカーを開く");

    private final String mId;
    private final String mLabel;

    CtrlAction(String id, String label) {
        mId = id;
        mLabel = label;
    }

    /**
     * アクションの識別 ID を取得します。
     *
     * @return 識別 ID 文字列
     */
    public String getId() {
        return mId;
    }

    /**
     * アクションの表示用ラベルを取得します。
     *
     * @return 表示用ラベル文字列
     */
    public String getLabel() {
        return mLabel;
    }

    /**
     * 識別 ID から対応する CtrlAction を取得します。
     *
     * @param id 識別 ID 文字列
     * @return 対応する CtrlAction。該当しない場合は NONE
     */
    public static CtrlAction fromId(String id) {
        if (id == null) {
            return NONE;
        }
        for (CtrlAction action : values()) {
            if (action.mId.equals(id)) {
                return action;
            }
        }
        return NONE;
    }
}
