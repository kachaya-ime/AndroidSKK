package io.github.kachaya.skk;

import android.view.KeyEvent;

/**
 * 漢字変換モード（▼）の状態を管理するクラスです。
 * <p>
 * 提示された変換候補リスト（{@link Candidate}）の中から、ユーザーが目的の単語を選択しているフェーズです。
 * </p>
 */
enum SKKStateHeadwordConversion implements SKKState {
    /** シングルトンインスタンス。 */
    INSTANCE;

    // --- 状態フラグ ---

    /**
     * この状態が一時的な入力状態（▼）であることを示します。
     *
     * @return 常に true
     */
    @Override
    public boolean isTransient() {
        return true;
    }

    /**
     * 現在、候補リストから選択中（▼）であることを示します。
     *
     * @return 常に true
     */
    @Override
    public boolean isConverting() {
        return true;
    }

    // --- メタ情報 ---

    /**
     * 状態表示シンボルを取得します。
     *
     * @return "▼"
     */
    @Override
    public String getText() {
        return "▼";
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
     * 変換候補選択中のキー入力を処理します。
     * スペースでの次候補移動、xでの前候補移動、Xでの候補削除などを行います。
     *
     * @param context SKKエンジンのコンテキスト
     * @param code    Unicode コードポイント
     * @return 常に true (イベントを消費)
     */
    @Override
    public boolean processKey(SKKEngine context, int code) {
        switch (code) {
            case ' ':
                context.chooseAdjacentCandidate(true);
                break;
            case '>':
                // 現在の候補を確定し、即座に接尾語入力（>）を伴う見出し語入力を開始
                context.pickCurrentCandidate();
                context.changeState(SKKStateHeadword.INSTANCE);
                StringBuilder headword = context.getHeadword();
                headword.append('>');
                break;
            case 'x':
                context.chooseAdjacentCandidate(false);
                break;
            case 'X':
                // 選択中の候補をユーザー辞書から削除
                context.purgeCurrentCandidate();
                break;
            default:
                // その他のキー入力は現在の候補を確定してから処理
                context.pickCurrentCandidate();
                context.processKey(code);
                break;
        }
        return true;
    }

    /**
     * Ctrlキーと同時押しのキー入力を処理します。
     * 候補の選択移動や、Ctrl-J による確定などを共通処理として提供します。
     *
     * @param context SKKエンジンのコンテキスト
     * @param keyCode KeyEventで定義されているキーコード
     * @return イベントを消費した場合は true
     */
    @Override
    public boolean processCtrlKey(SKKEngine context, int keyCode) {
        switch (keyCode) {
            case KeyEvent.KEYCODE_N:
            case KeyEvent.KEYCODE_F:
                context.chooseAdjacentCandidate(true);
                return true;
            case KeyEvent.KEYCODE_P:
            case KeyEvent.KEYCODE_B:
                context.chooseAdjacentCandidate(false);
                return true;
            case KeyEvent.KEYCODE_J:
                context.handleKanaKey();
                return true;
            case KeyEvent.KEYCODE_G:
                return handleCancel(context);
            case KeyEvent.KEYCODE_Q:
                context.toggleKana();
                return true;
        }
        return false;
    }

    /**
     * Tab キーの入力を処理します。
     *
     * @param context   SKKエンジンのコンテキスト
     * @param isShifted Shift押下中かどうか
     * @return 常に true
     */
    @Override
    public boolean processTab(SKKEngine context, boolean isShifted) {
        context.chooseAdjacentCandidate(!isShifted);
        return true;
    }

    /**
     * Enter キーの入力を処理します。現在の候補を確定します。
     *
     * @param context SKKエンジンのコンテキスト
     * @return 常に true
     */
    @Override
    public boolean processEnter(SKKEngine context) {
        context.pickCurrentCandidate();
        return true;
    }

    /**
     * 方向（DPAD）キーの入力を処理します。
     *
     * @param context SKKエンジンのコンテキスト
     * @param keyCode KeyEvent で定義されているキーコード
     * @return 常に true
     */
    @Override
    public boolean processDpad(SKKEngine context, int keyCode) {
        if (keyCode == KeyEvent.KEYCODE_DPAD_LEFT) {
            context.chooseAdjacentCandidate(false);
        } else if (keyCode == KeyEvent.KEYCODE_DPAD_RIGHT) {
            context.chooseAdjacentCandidate(true);
        }
        return true;
    }

    /**
     * システムの「戻る」ボタン押下時のイベントを処理します。キャンセルとして扱います。
     *
     * @param context SKKエンジンのコンテキスト
     * @return 常に true
     */
    @Override
    public boolean handleBackKey(SKKEngine context) {
        return handleCancel(context);
    }

    // --- ライフサイクル ---

    @Override
    public void onEnterState(SKKEngine context) {
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
     * 変換確定したテキスト（かな・記号等）を処理します。現在の候補を確定した上で次の入力を処理します。
     *
     * @param context SKKエンジンのコンテキスト
     * @param text    入力されたテキスト
     * @param initial 入力に使われた最初の 1 文字
     * @param isUpper 大文字入力（Shift押下）されたかどうか
     */
    @Override
    public void processText(SKKEngine context, String text, char initial, boolean isUpper) {
        context.pickCurrentCandidate();
        SKKStateDirect.INSTANCE.processText(context, text, initial, isUpper);
    }

    @Override
    public void onFinishRomaji(SKKEngine context) {
    }

    // --- 削除・キャンセル ---

    /**
     * バックスペースキーによる削除処理を行います。変換中は何もしません。
     *
     * @param context SKKエンジンのコンテキスト
     * @return 常に false
     */
    @Override
    public boolean processBackspace(SKKEngine context) {
        return false;
    }

    /**
     * バックスペース処理が実行される直前の状態保存などを行います。
     * 送り仮名がある場合は見出し語バッファに戻し、変換前の状態を復元します。
     *
     * @param context SKKエンジンのコンテキスト
     */
    @Override
    public void beforeBackspace(SKKEngine context) {
        String okurigana = context.getOkurigana();
        if (okurigana != null) {
            StringBuilder headword = context.getHeadword();
            headword.append(okurigana);
            context.setOkurigana(null);
            context.setOkuriConsonant(null);
        }
    }

    /**
     * バックスペース処理が実行された後の状態遷移などを制御します。
     * 見出し語入力状態（▽）へ戻ります。
     *
     * @param context SKKエンジンのコンテキスト
     */
    @Override
    public void afterBackspace(SKKEngine context) {
        StringBuilder headword = context.getHeadword();
        int len = headword.length();
        if (len == 0) {
            context.changeState(SKKStateDirect.INSTANCE);
        } else {
            context.changeState(SKKStateHeadword.INSTANCE);
            context.updateSuggestions();
        }
    }

    /**
     * 現在の入力操作を中断し、見出し語入力状態に戻ります。
     *
     * @param context SKKエンジンのコンテキスト
     * @return 常に true
     */
    @Override
    public boolean handleCancel(SKKEngine context) {
        beforeBackspace(context);
        afterBackspace(context);
        return true;
    }

    // --- 確定・モード切替 ---

    /**
     * 現在の未確定入力を強制的に確定させます。
     *
     * @param context SKKエンジンのコンテキスト
     * @return 常に true
     */
    @Override
    public boolean finish(SKKEngine context) {
        context.pickCurrentCandidate();
        return true;
    }

    /**
     * かなモード（ひらがな/カタカナ）をトグルします。
     * 候補を確定させてからモードを切り替えます。
     *
     * @param context SKKエンジンのコンテキスト
     */
    @Override
    public void toggleKana(SKKEngine context) {
        context.pickCurrentCandidate();
        SKKStateDirect.INSTANCE.toggleKana(context);
    }

    /**
     * エディタに送信する未確定文字列（現在選択中の候補）を構築します。
     *
     * @param context SKKエンジンのコンテキスト
     * @return 候補文字列
     */
    @Override
    public CharSequence getComposingText(SKKEngine context) {
        String candidate = context.getCurrentCandidate();
        return context.convertText(candidate);
    }
}
