package io.github.kachaya.skk;

import android.view.KeyEvent;

/**
 * 見出し語入力モード（▽）の状態を管理するクラスです。
 * <p>
 * 漢字変換のキーとなる読み（見出し語）をバッファに蓄積します。
 * スペースキーにより漢字変換（{@link SKKStateHeadwordConversion}）を開始し、
 * Tab キーまたは Ctrl-I により辞書からの補完候補（Suggestions）を表示します。
 * </p>
 */
enum SKKStateHeadword implements SKKState {
    /** シングルトンインスタンス。 */
    INSTANCE;

    // --- 状態フラグ ---

    /**
     * この状態が一時的な入力状態（▽）であることを示します。
     *
     * @return 常に true
     */
    @Override
    public boolean isTransient() {
        return true;
    }

    /**
     * 見出し語入力中は候補選択中ではありません。
     *
     * @return 常に false
     */
    @Override
    public boolean isConverting() {
        return false;
    }

    // --- メタ情報 ---

    /**
     * 状態表示シンボルを取得します。
     *
     * @return "▽"
     */
    @Override
    public String getText() {
        return "▽";
    }

    /**
     * この状態ではアイコンを表示しません。
     *
     * @return 0
     */
    @Override
    public int getIcon() {
        return 0;
    }

    // --- キー入力処理 ---

    /**
     * 見出し語入力中のキー入力を処理します。
     * 変換中ではないため、通常の文字追加や特殊な拡張キー判定を行います。
     *
     * @param context SKKエンジンのコンテキスト
     * @param code    Unicode コードポイント
     * @return 常に false (SKKEngine 側で processRomaji 等へ流すため)
     */
    @Override
    public boolean processKey(SKKEngine context, int code) {
        return false;
    }

    /**
     * Ctrlキーと同時押しのキー入力を処理します。
     * 補完候補の選択（Ctrl-I）や、入力を 1 文字削除（Ctrl-W）する操作を処理します。
     * 見出し語入力中は、モード側によるカーソル移動をブロックします。
     *
     * @param context SKKエンジンのコンテキスト
     * @param keyCode キーコード
     * @return イベントを消費した場合は true
     */
    @Override
    public boolean processCtrlKey(SKKEngine context, int keyCode) {
        switch (keyCode) {
            case KeyEvent.KEYCODE_Q:
                SKKMode nextMode = context.getToggledKanaMode();
                if (nextMode != null) {
                    context.setMode(nextMode);
                }
                return true;
            case KeyEvent.KEYCODE_W:
                // DDSKK 仕様: Ctrl-W で見出し語の 1 文字削除 (Backspace 相当)
                return context.handleBackspace();
            case KeyEvent.KEYCODE_I:
                // 明示的な補完開始
                if (context.getSuggestionList() == null || context.getSuggestionList().isEmpty()) {
                    context.updateSuggestions();
                } else {
                    context.chooseAdjacentSuggestion(true);
                }
                return true;
            case KeyEvent.KEYCODE_J:
                context.handleKanaKey();
                return true;
            case KeyEvent.KEYCODE_G:
                return context.handleCancel();

            // カーソル移動のガード: 見出し語入力中はエディタのカーソル移動を抑制する
            case KeyEvent.KEYCODE_P:
            case KeyEvent.KEYCODE_N:
            case KeyEvent.KEYCODE_B:
            case KeyEvent.KEYCODE_F:
                return true;
        }
        return false;
    }

    /**
     * Tab キーの入力を処理します。
     * 補完リストが未表示なら補完を開始し、表示中なら選択を切り替えます（DDSKK方式）。
     *
     * @param context   SKKエンジンのコンテキスト
     * @param isShifted Shift押下中かどうか
     * @return 常に true
     */
    @Override
    public boolean processTab(SKKEngine context, boolean isShifted) {
        if (context.getSuggestionList() == null || context.getSuggestionList().isEmpty()) {
            context.updateSuggestions();
        } else {
            context.chooseAdjacentSuggestion(!isShifted);
        }
        return true;
    }

    /**
     * Enter キーの入力を処理します。
     * 確定して Direct モードへ遷移します。
     *
     * @param context SKKエンジンのコンテキスト
     * @return 常に true
     */
    @Override
    public boolean processEnter(SKKEngine context) {
        finish(context);
        context.changeState(SKKStateDirect.INSTANCE);
        return true;
    }

    /**
     * 方向（DPAD）キー入力を処理します。
     * 見出し語入力中は、標準のカーソル移動動作を抑制します。
     *
     * @param context SKKエンジンのコンテキスト
     * @param keyCode キーコード
     * @return 常に true
     */
    @Override
    public boolean processDpad(SKKEngine context, int keyCode) {
        return true;
    }

    /**
     * Back キーイベントを処理します。
     * 一時的な状態（▽）であるため、キャンセルとして処理します。
     *
     * @param context SKKエンジンのコンテキスト
     * @return 常に true
     */
    @Override
    public boolean handleBackKey(SKKEngine context) {
        return handleCancel(context);
    }

    // --- ライフサイクル ---

    /**
     * 見出し語入力状態へ遷移した際の初期化を行います。
     * 前の状態（変換中など）の候補表示が残らないよう、クリーンアップを実行します。
     *
     * @param context SKKエンジンのコンテキスト
     */
    @Override
    public void onEnterState(SKKEngine context) {
        context.clearCandidates();
    }

    @Override
    public void onExitState(SKKEngine context) {
    }

    // --- テキスト入力処理 ---

    /**
     * ローマ字かな変換 engine による拡張コマンド（q, >, <, ?, . 等）を処理します。
     * かなトグル、動的候補提示、および接頭辞・接尾辞を伴う変換の開始を行います。
     *
     * @param context SKKエンジンのコンテキスト
     * @param text    確定したかな、または拡張コマンド文字
     * @param isUpper 大文字入力（Shift押下）されたかどうか
     * @return 拡張コマンドとして処理された場合は true
     */
    @Override
    public boolean processRomajiExtension(SKKEngine context, String text, boolean isUpper) {
        if (text == null) {
            return false;
        }

        if (text.endsWith(">") || text.endsWith("<") || text.endsWith("?")) {
            char trigger = text.charAt(text.length() - 1);
            StringBuilder headwordBuffer = context.getHeadword();

            // トリガー文字の前の残り（'y' など）があれば見出し語に追加
            if (text.length() > 1) {
                headwordBuffer.append(text.substring(0, text.length() - 1));
            }

            String headwordStr = headwordBuffer.toString();
            // "today", "date", "now" 等のキーワード判定を行い、動的候補を表示
            if (context.showDynamicCandidates(headwordStr)) {
                return true;
            }
            // キーワードでなければ辞書検索（接頭辞・接尾辞として ">" を付与）
            headwordBuffer.append('>');
            context.conversionStart();
            return true;
        }

        switch (text) {
            case "q": // DDSKK 仕様: 見出し語入力中に q でカタカナ変換確定
                // 選択中の補完候補（Suggestion）を確定
                context.pickCurrentSuggestion();
                return true;
        }
        return false;
    }

    /**
     * 確定したかなテキストを見出し語バッファに追加します。
     * <p>
     * スペースが入力された場合は変換を開始します。
     * 大文字入力（Shift押下）された場合は送り仮名入力状態（{@link SKKStateOkurigana}）へ遷移します。
     * </p>
     *
     * @param context SKKエンジンのコンテキスト
     * @param text    確定したかなテキスト
     * @param initial 入力に使われた最初の 1 文字
     * @param isUpper 大文字入力（Shift押下）されたかどうか
     */
    @Override
    public void processText(SKKEngine context, String text, char initial, boolean isUpper) {
        if (text != null && text.equals(" ")) {
            context.conversionStart();
            return;
        }
        if (isUpper) {
            context.setOkuriConsonant(null);
            context.setOkurigana(null);
            context.changeState(SKKStateOkurigana.INSTANCE);
            SKKStateOkurigana.INSTANCE.processText(context, text, initial, false);
        } else {
            if (text != null) {
                StringBuilder headword = context.getHeadword();
                headword.append(text);
            }
        }
    }

    @Override
    public void onFinishRomaji(SKKEngine context) {
    }

    // --- 削除・キャンセル ---

    /**
     * 見出し語バッファから 1 文字削除します。
     *
     * @param context SKKエンジンのコンテキスト
     * @return 削除が行われた場合は true
     */
    @Override
    public boolean processBackspace(SKKEngine context) {
        StringBuilder headword = context.getHeadword();
        int len = headword.length();
        if (len > 0) {
            headword.deleteCharAt(len - 1);
            return true;
        }
        return false;
    }

    @Override
    public void beforeBackspace(SKKEngine context) {
    }

    /**
     * 見出し語バッファから文字が削除された後の処理を行います。
     * バッファが空になった場合は確定モード（{@link SKKStateDirect}）へ戻ります。
     *
     * @param context SKKエンジンのコンテキスト
     */
    @Override
    public void afterBackspace(SKKEngine context) {
        StringBuilder headword = context.getHeadword();
        int len = headword.length();
        boolean hasComposing = context.hasComposing();

        if (len == 0 && !hasComposing) {
            context.changeState(SKKStateDirect.INSTANCE);
        }
    }

    /**
     * 見出し語入力をキャンセルし、確定モード（{@link SKKStateDirect}）へ戻ります。
     *
     * @param context SKKエンジンのコンテキスト
     * @return 常に true
     */
    @Override
    public boolean handleCancel(SKKEngine context) {
        context.changeState(SKKStateDirect.INSTANCE);
        return true;
    }

    // --- 確定・モード切替 ---

    /**
     * 現在の見出し語バッファの内容をそのまま確定させます。
     *
     * @param context SKKエンジンのコンテキスト
     * @return 常に true
     */
    @Override
    public boolean finish(SKKEngine context) {
        StringBuilder headword = context.getHeadword();
        int len = headword.length();
        if (len > 0) {
            CharSequence text = context.convertText(headword);
            context.commitTextSKK(text, 1);
        }
        return true;
    }

    /**
     * ひらがな/カタカナをトグルした際、現在のバッファ内容をトグル後の文字種で確定させます。
     *
     * @param context SKKエンジンのコンテキスト
     */
    @Override
    public void toggleKana(SKKEngine context) {
        StringBuilder headword = context.getHeadword();
        int len = headword.length();
        if (len > 0) {
            SKKMode toggledMode = context.getToggledKanaMode();
            CharSequence text = toggledMode.convertText(headword);
            context.commitTextSKK(text, 1);
        }
        context.changeState(SKKStateDirect.INSTANCE);
    }

    /**
     * エディタに表示する未確定の見出し語文字列を取得します。
     *
     * @param context SKKエンジンのコンテキスト
     * @return 現在の見出し語、または null
     */
    @Override
    public CharSequence getComposingText(SKKEngine context) {
        StringBuilder headword = context.getHeadword();
        return context.convertText(headword);
    }
}
