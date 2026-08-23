package io.github.kachaya.skk.engine;

import android.view.KeyEvent;

/**
 * 確定モード（通常の直接入力状態）を管理するクラスです。
 * <p>
 * SKK の基本となる状態であり、入力された文字はローマ字かな変換を経て直接エディタへコミットされます。
 * 大文字入力（Shift押下）による見出し語入力（{@link SKKStateHeadword}）への遷移や、
 * スラッシュ入力による Abbrev モード（{@link SKKStateAbbrev}）への遷移の起点となります。
 * また、単語登録セッション中のスペースキー等の特殊な挙動もこのクラスで制御されます。
 * </p>
 */
public enum SKKStateDirect implements SKKState {
    /** シングルトンインスタンス。 */
    INSTANCE;

    // --- 状態フラグ ---

    /**
     * 確定モードは一時的な入力状態ではありません。
     *
     * @return 常に false
     */
    @Override
    public boolean isTransient() {
        return false;
    }

    /**
     * 確定モードは候補選択中ではありません。
     *
     * @return 常に false
     */
    @Override
    public boolean isConverting() {
        return false;
    }

    // --- メタ情報 ---

    /**
     * 確定モードでは状態を示すシンボルを表示しません。
     *
     * @return null
     */
    @Override
    public String getText() {
        return null;
    }

    /**
     * 確定モードでは状態を示すアイコンを表示しません。
     *
     * @return {@link SKKIcon#NONE}
     */
    @Override
    public SKKIcon getIcon() {
        return SKKIcon.NONE;
    }

    // --- キー入力処理 ---

    /**
     * キー入力を処理します。
     * 単語登録中など、特定のコンテキストにおけるスペースキー等の挙動を制御します。
     *
     * @param context SKKエンジンのコンテキスト
     * @param code    Unicode コードポイント、または特殊な制御コード
     * @return イベントを消費した場合は true
     */
    @Override
    public boolean processKey(SKKEngine context, int code) {
        boolean isSpace = (code == ' ');
        boolean hasRegistration = !context.isRegistrationStackEmpty();

        if (isSpace && hasRegistration) {
            SKKEngine.RegistrationInfo regInfo = context.peekRegistrationInfo();
            int entryLen = regInfo.entry.length();
            if (entryLen == 0) {
                return true; // 登録単語が空の状態でのスペースは無視
            }
        }
        return false;
    }

    /**
     * Ctrl キーと同時押しのキー入力を処理します。
     * 再変換（Ctrl-U）、かなモードへの復帰（Ctrl-J）、キャンセル（Ctrl-G）、
     * および単語登録中のカーソル移動ブロックなどを処理します。
     *
     * @param context SKKエンジンのコンテキスト
     * @param keyCode KeyEventで定義されているキーコード
     * @return イベントを消費した場合は true
     */
    @Override
    public boolean processCtrlKey(SKKEngine context, int keyCode) {
        switch (keyCode) {
            case KeyEvent.KEYCODE_U:
                return context.reConversion();
            case KeyEvent.KEYCODE_J:
                context.handleKanaKey();
                return true;
            case KeyEvent.KEYCODE_G:
                return context.handleCancel();
            case KeyEvent.KEYCODE_Q:
                context.toggleKana();
                return true;

            // カーソル移動のガード
            case KeyEvent.KEYCODE_P:
            case KeyEvent.KEYCODE_N:
            case KeyEvent.KEYCODE_B:
            case KeyEvent.KEYCODE_F:
                // 単語登録中はモード側に処理を流さず、カーソル移動をブロックする
                if (!context.isRegistrationStackEmpty()) {
                    return true;
                }
                break;
        }
        return false;
    }

    /**
     * Tab キーの入力を処理します。確定モードでは消費しません。
     *
     * @param context   SKKエンジンのコンテキスト
     * @param isShifted Shift押下中かどうか
     * @return 常に false
     */
    @Override
    public boolean processTab(SKKEngine context, boolean isShifted) {
        return false;
    }

    /**
     * Enter キーの入力を処理します。
     * 単語登録中の場合は登録を完了（確定）させます。
     *
     * @param context SKKエンジンのコンテキスト
     * @return 単語登録を完了した場合は true
     */
    @Override
    public boolean processEnter(SKKEngine context) {
        if (!context.isRegistrationStackEmpty()) {
            context.finishRegistration();
            return true;
        }
        return false;
    }

    /**
     * 方向（DPAD）キー入力を処理します。
     * 見出し語入力中でなければ、システムによる標準的なカーソル移動を許可します。
     *
     * @param context SKKエンジンのコンテキスト
     * @param keyCode KeyEventで定義されているキーコード
     * @return イベントを消費した場合は true
     */
    @Override
    public boolean processDpad(SKKEngine context, int keyCode) {
        // 単語登録中（バッファあり）の状態であれば、システムへのカーソル移動をブロックする
        return !context.canMoveCursor();
    }

    /**
     * 確定モードでは Back キーイベントを消費しません。
     *
     * @param context SKKエンジンのコンテキスト
     * @return 常に false
     */
    @Override
    public boolean handleBackKey(SKKEngine context) {
        return false;
    }

    // --- ライフサイクル ---

    /**
     * 確定モードに遷移した際の初期化処理を行います。
     * 進行中のローマ字変換バッファ、変換候補、補完リストをリセットし、
     * クリーンな直接入力状態を構築します。単語登録スタックは維持されます。
     *
     * @param context SKKエンジンのコンテキスト
     */
    @Override
    public void onEnterState(SKKEngine context) {
        context.clearBuffers();
    }

    /**
     * 確定モードを脱ける際の処理を行います。
     *
     * @param context SKKエンジンのコンテキスト
     */
    @Override
    public void onExitState(SKKEngine context) {
    }

    // --- テキスト入力処理 ---

    /**
     * ローマ字かな変換エンジンによる拡張入力（q, l, /, >, <, ? 等）を処理します。
     * かなモードのトグル、英数モードへの切替、Abbrev 状態（{@link SKKStateAbbrev}）への遷移、
     * および接頭辞・接尾辞を伴う見出し語入力の開始を行います。
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

        switch (text) {
            case "q":
                toggleKana(context);
                return true;
            case "l":
                if (isUpper) {
                    context.changeMode(SKKModeFullLatin.INSTANCE, true);
                } else {
                    context.changeMode(SKKModeHalfLatin.INSTANCE, true);
                }
                return true;
            case "/":
                context.changeState(SKKStateAbbrev.INSTANCE, true);
                return true;
            case ">":
            case "<":
            case "?":
                // DDSKK 仕様: 接頭辞・接尾辞トリガによる見出し語入力開始
                context.changeState(SKKStateHeadword.INSTANCE);
                context.getHeadword().append('>');
                return true;
        }
        return false;
    }

    /**
     * 確定したテキストを処理します。
     * 大文字入力の場合は見出し語入力状態（{@link SKKStateHeadword}）へ遷移して入力を継続します。
     *
     * @param context SKKエンジンのコンテキスト
     * @param text    入力されたかな、または記号文字列
     * @param initial 入力に使われた最初の 1 文字
     * @param isUpper 大文字入力（Shift押下）されたかどうか
     */
    @Override
    public void processText(SKKEngine context, String text, char initial, boolean isUpper) {
        if (isUpper) {
            context.changeState(SKKStateHeadword.INSTANCE);
            if (text != null) {
                SKKStateHeadword.INSTANCE.processText(context, text, initial, false);
            }
        } else {
            if (text != null) {
                CharSequence converted = context.convertText(text);
                context.commitTextSKK(converted, 1);
            }
        }
    }

    /**
     * ローマ字かな変換が完了した際の通知を処理します。
     *
     * @param context SKKエンジンのコンテキスト
     */
    @Override
    public void onFinishRomaji(SKKEngine context) {
    }

    // --- 削除・キャンセル ---

    /**
     * バックスペースキーによる削除処理を行います。
     * 単語登録中の場合は、登録バッファの末尾を 1 文字削除します。
     * バッファが空の状態で押された場合は、単語登録をキャンセルして前の状態に戻ります。
     *
     * @param context SKKエンジンのコンテキスト
     * @return 削除イベントを消費した場合は true
     */
    @Override
    public boolean processBackspace(SKKEngine context) {
        if (!context.isRegistrationStackEmpty()) {
            SKKEngine.RegistrationInfo regInfo = context.peekRegistrationInfo();
            int len = regInfo.entry.length();
            if (len > 0) {
                regInfo.entry.deleteCharAt(len - 1);
            } else {
                // バッファが空なら登録自体をキャンセル
                context.cancelRegister();
            }
            return true;
        }
        return false;
    }

    /**
     * バックスペース前の処理を行います。確定モードでは特に行いません。
     *
     * @param context SKKエンジンのコンテキスト
     */
    @Override
    public void beforeBackspace(SKKEngine context) {
    }

    /**
     * バックスペース後の処理を行います。確定モードでは特に行いません。
     *
     * @param context SKKエンジンのコンテキスト
     */
    @Override
    public void afterBackspace(SKKEngine context) {
    }

    /**
     * 入力操作の中断処理を行います。
     * 単語登録セッションの中断や、再変換のトリガーとして機能します。
     *
     * @param context SKKエンジンのコンテキスト
     * @return 何らかの中断処理が実行された場合は true
     */
    @Override
    public boolean handleCancel(SKKEngine context) {
        boolean hasRegistration = !context.isRegistrationStackEmpty();
        if (hasRegistration) {
            context.cancelRegister();
            return true;
        }
        return context.reConversion();
    }

    // --- 確定・モード切替 ---

    /**
     * 現在の入力を強制的に確定させます。確定モードでは特に行いません。
     *
     * @param context SKKエンジンのコンテキスト
     * @return 常に false
     */
    @Override
    public boolean finish(SKKEngine context) {
        return false;
    }

    /**
     * かなモード（ひらがな/カタカナ）を交互に切り替えます。
     *
     * @param context SKKエンジンのコンテキスト
     */
    @Override
    public void toggleKana(SKKEngine context) {
        SKKMode nextMode = context.getToggledKanaMode();
        context.changeMode(nextMode, false);
    }

    /**
     * 未確定文字列を取得します。確定モードでは常に null です。
     *
     * @param context SKKエンジンのコンテキスト
     * @return null
     */
    @Override
    public CharSequence getComposingText(SKKEngine context) {
        return null;
    }
}
