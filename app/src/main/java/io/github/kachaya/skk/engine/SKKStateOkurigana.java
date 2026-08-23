package io.github.kachaya.skk.engine;

import android.view.KeyEvent;

/**
 * 送り仮名入力中（▽）の状態を管理するクラスです。
 * <p>
 * 見出し語入力モード（{@link SKKStateHeadword}）に続き、動詞や形容詞等の「送り仮名」を入力するフェーズです。
 * 送り仮名の最初の文字が入力された時点でその子音（送り子音）を特定し、
 * ローマ字変換が完了したタイミングで辞書検索（変換）を開始します。
 * </p>
 */
public enum SKKStateOkurigana implements SKKState {
    /** シングルトンインスタンス。 */
    INSTANCE;

    // --- 状態フラグ ---

    /**
     * この状態が一時的なもの（確定が必要な状態）であることを示します。
     *
     * @return 常に true
     */
    @Override
    public boolean isTransient() {
        return true;
    }

    /**
     * 送り仮名入力中は候補選択中ではありません。
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
     * @return {@link SKKIcon#NONE}
     */
    @Override
    public SKKIcon getIcon() {
        return SKKIcon.NONE;
    }

    // --- キー入力処理 ---

    /**
     * 送り仮名入力中のキー入力を処理します。
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
     * 送り仮名入力中の制御キー処理を行います。
     * 送り入力中は、モード側によるカーソル移動をブロックします。
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
            case KeyEvent.KEYCODE_G:
                return handleCancel(context);
            case KeyEvent.KEYCODE_Q:
                context.toggleKana();
                return true;

            // カーソル移動のガード: 送り入力中はエディタのカーソル移動を抑制する
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
     * 送り仮名入力中は標準の Tab 動作を抑制します。
     *
     * @param context   SKKエンジンのコンテキスト
     * @param isShifted Shift押下中かどうか
     * @return 常に true
     */
    @Override
    public boolean processTab(SKKEngine context, boolean isShifted) {
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
     * 方向キーの入力を処理します。
     * 送り仮名入力中は標準の移動動作を抑制します。
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
     * Back キーイベントを処理します。キャンセルとして処理します。
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
     * 入力されたテキストを送り仮名バッファに追加します。
     * <p>
     * 送り仮名の 1 文字目の場合は、その入力に使われた最初の子音を「送り子音」として
     * {@link SKKEngine} に登録します。
     * </p>
     *
     * @param context SKKエンジンのコンテキスト
     * @param text    入力されたテキスト
     * @param initial 入力に使われた最初の文字
     * @param isUpper 大文字入力（Shift押下）かどうか
     */
    @Override
    public void processText(SKKEngine context, String text, char initial, boolean isUpper) {
        if (text != null) {
            String currentOkr = context.getOkurigana();
            if (currentOkr == null) {
                context.setOkurigana(text);
                String consonant = String.valueOf(initial);
                context.setOkuriConsonant(consonant);
            } else {
                String nextOkr = currentOkr + text;
                context.setOkurigana(nextOkr);
            }
        }
    }

    /**
     * 送り仮名のローマ字かな変換が 1 文字分完了した際の処理です。
     * 送り仮名の特定が完了したとみなし、辞書検索（変換開始）をトリガーします。
     *
     * @param context SKKエンジンのコンテキスト
     */
    @Override
    public void onFinishRomaji(SKKEngine context) {
        context.conversionStart();
    }

    // --- 削除・キャンセル ---

    /**
     * バックスペースによる送り仮名の削除を処理します。
     *
     * @param context SKKエンジンのコンテキスト
     * @return 削除が行われた場合は true
     */
    @Override
    public boolean processBackspace(SKKEngine context) {
        String okr = context.getOkurigana();
        if (okr != null) {
            int len = okr.length();
            if (len > 0) {
                String nextOkr = okr.substring(0, len - 1);
                context.setOkurigana(nextOkr);
                return true;
            }
        }
        return false;
    }

    @Override
    public void beforeBackspace(SKKEngine context) {
    }

    /**
     * 送り仮名が空になった場合、見出し語入力モード（{@link SKKStateHeadword}）へ戻ります。
     *
     * @param context SKKエンジンのコンテキスト
     */
    @Override
    public void afterBackspace(SKKEngine context) {
        String okr = context.getOkurigana();
        boolean isEmpty = (okr == null || okr.length() == 0);
        if (isEmpty) {
            context.setOkurigana(null);
            context.setOkuriConsonant(null);
            context.changeState(SKKStateHeadword.INSTANCE);
        }
    }

    /**
     * 送り仮名入力をキャンセルし、見出し語入力モード（{@link SKKStateHeadword}）へ戻ります。
     *
     * @param context SKKエンジンのコンテキスト
     * @return 常に true
     */
    @Override
    public boolean handleCancel(SKKEngine context) {
        context.setOkurigana(null);
        context.setOkuriConsonant(null);
        context.changeState(SKKStateHeadword.INSTANCE);
        return true;
    }

    // --- 確定・モード切替 ---

    /**
     * 現在の入力を強制的に確定させます。
     *
     * @param context SKKエンジンのコンテキスト
     * @return 確定に成功した場合は true
     */
    @Override
    public boolean finish(SKKEngine context) {
        return SKKStateHeadword.INSTANCE.finish(context);
    }

    /**
     * かなモードをトグルします。
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
     * エディタに表示する未確定文字列を生成します。
     * 「見出し語*送り仮名」の形式（例: ▽漢字*a）で構築されます。
     *
     * @param context SKKエンジンのコンテキスト
     * @return 生成された CharSequence
     */
    @Override
    public CharSequence getComposingText(SKKEngine context) {
        StringBuilder sb = new StringBuilder();

        StringBuilder headword = context.getHeadword();
        CharSequence convertedKey = context.convertText(headword);
        sb.append(convertedKey).append("*");

        String okr = context.getOkurigana();
        if (okr != null) {
            CharSequence convertedOkr = context.convertText(okr);
            sb.append(convertedOkr);
        }

        return sb.toString();
    }
}
