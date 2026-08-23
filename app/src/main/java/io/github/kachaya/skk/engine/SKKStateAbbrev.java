package io.github.kachaya.skk.engine;

import android.view.KeyEvent;

/**
 * Abbrev モードの見出し語入力状態（▽）を管理するクラスです。
 * <p>
 * ローマ字かな変換を行わず、入力されたアルファベットを直接見出し語（Headword）として蓄積します。
 * スペースキーにより、蓄積された英単語等をキーとした漢字変換（{@link SKKStateAbbrevConversion}）を開始します。
 * Tab キーまたは Ctrl-I により辞書からの補完候補（Suggestions）を表示します。
 * </p>
 */
public enum SKKStateAbbrev implements SKKState {
    /** シングルトンインスタンス。 */
    INSTANCE;

    // --- 状態フラグ ---

    /**
     * この状態が一時的な入力状態であることを示します。
     *
     * @return 常に true
     */
    @Override
    public boolean isTransient() {
        return true;
    }

    /**
     * Abbrev モードは候補選択中ではありません。
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
     * @return "Aｱ"
     */
    @Override
    public String getText() {
        return "Aｱ";
    }

    /**
     * abbrev モード有効時に表示するアイコンの種類を取得します。
     *
     * @return {@link SKKIcon#ABBREV}
     */
    @Override
    public SKKIcon getIcon() {
        return SKKIcon.ABBREV;
    }

    // --- キー入力処理 ---

    /**
     * キー入力を処理します。
     * 入力されたアルファベットを見出し語バッファに追加し、スペースキーで変換を開始します。
     *
     * @param context SKKエンジンのコンテキスト
     * @param code    Unicode コードポイント
     * @return 常に true（イベントを消費）
     */
    @Override
    public boolean processKey(SKKEngine context, int code) {
        StringBuilder headword = context.getHeadword();
        switch (code) {
            case ' ':
                int len = headword.length();
                if (len != 0) {
                    context.abbrevConversionStart();
                }
                break;
            case '>':
                // "today", "date", "now" 等のキーワード判定を行い、動的候補を表示
                if (context.showDynamicCandidates(headword.toString())) {
                    return true;
                }
                headword.append('>');
                break;
            case '.':
                context.pickCurrentSuggestion();
                break;
            default:
                headword.append((char) code);
                break;
        }
        return true;
    }

    /**
     * Ctrlキーと同時押しのキー入力を処理します。
     * 補完候補の選択や削除、キャンセル等を処理します。
     * Abbrev 入力中は、モード側によるカーソル移動をブロックします。
     *
     * @param context SKKエンジンのコンテキスト
     * @param keyCode キーコード
     * @return イベントを消費した場合は true
     */
    @Override
    public boolean processCtrlKey(SKKEngine context, int keyCode) {
        switch (keyCode) {
            case KeyEvent.KEYCODE_J:
                context.handleKanaKey();
                return true;
            case KeyEvent.KEYCODE_Q:
                // Abbrev モードでの Ctrl-Q は全角英数として確定
                StringBuilder headword = context.getHeadword();
                int len = headword.length();
                if (len > 0) {
                    CharSequence text = RomajiConverter.toWideLatin(headword);
                    context.commitTextSKK(text, 1);
                }
                context.changeState(SKKStateDirect.INSTANCE);
                return true;
            case KeyEvent.KEYCODE_I:
                // 明示的な補完開始
                if (context.getSuggestionList() == null || context.getSuggestionList().isEmpty()) {
                    context.updateSuggestions();
                } else {
                    context.chooseAdjacentSuggestion(true);
                }
                return true;
            case KeyEvent.KEYCODE_G:
                // キャンセル（SKKState のデフォルト処理を統合）
                return handleCancel(context);

            // カーソル移動のガード: Abbrev 入力中はエディタのカーソル移動を抑制する
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
     * 補完リストが未表示なら補完を開始し、表示中なら選択を切り替えます。
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
     * Enter キー入力を処理します。確定して Direct モードへ遷移します。
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
     * 方向キー入力を処理します。
     * Abbrev 入力中は、標準のカーソル移動動作を抑制します。
     *
     * @param context SKKエンジンのコンテキスト
     * @param keyCode KeyEvent で定義されているキーコード
     * @return 常に true
     */
    @Override
    public boolean processDpad(SKKEngine context, int keyCode) {
        return true;
    }

    /**
     * Back キー入力を処理します。キャンセルとして扱います。
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
     * Abbrev 入力状態へ遷移した際の初期化を行います。
     * 前の状態の候補表示が残らないよう、クリーンアップを実行します。
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

    @Override
    public boolean processRomajiExtension(SKKEngine context, String text, boolean isUpper) {
        return false;
    }

    /**
     * テキスト入力を処理します。入力されたテキストを見出し語バッファに追加します。
     *
     * @param context SKKエンジンのコンテキスト
     * @param text    入力されたテキスト
     * @param initial 入力に使われた最初の 1 文字
     * @param isUpper 大文字入力（Shift押下）されたかどうか
     */
    @Override
    public void processText(SKKEngine context, String text, char initial, boolean isUpper) {
        StringBuilder headword = context.getHeadword();
        headword.append(text);
    }

    @Override
    public void onFinishRomaji(SKKEngine context) {
    }

    // --- 削除・キャンセル ---

    /**
     * バックスペースキーによる削除処理を行います。
     * @param context SKKエンジンのコンテキスト
     * @return 削除イベントを消費した場合は true
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
     * バックスペース後の処理を行います。
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
     * Abbrev 入力をキャンセルし、確定モード（{@link SKKStateDirect}）へ戻ります。
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
     * 現在のバッファ内容をそのまま確定させます。
     *
     * @param context SKKエンジンのコンテキスト
     * @return 確定処理が行われた場合は true
     */
    @Override
    public boolean finish(SKKEngine context) {
        StringBuilder headword = context.getHeadword();
        int len = headword.length();
        if (len > 0) {
            context.commitTextSKK(headword, 1);
            return true;
        }
        return false;
    }

    /**
     * かなモード（ひらがな/カタカナ）をトグルします。
     * 確定させてから Direct モードのトグル処理を呼びます。
     *
     * @param context SKKエンジンのコンテキスト
     */
    @Override
    public void toggleKana(SKKEngine context) {
        finish(context);
        context.changeState(SKKStateDirect.INSTANCE);
        SKKStateDirect.INSTANCE.toggleKana(context);
    }

    /**
     * エディタに表示する未確定の文字列を取得します。
     *
     * @param context SKKエンジンのコンテキスト
     * @return 現在のバッファ内容
     */
    @Override
    public CharSequence getComposingText(SKKEngine context) {
        return context.getHeadword();
    }
}
