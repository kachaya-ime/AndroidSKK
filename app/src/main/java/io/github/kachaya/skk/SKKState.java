package io.github.kachaya.skk;

/**
 * SKK の「入力状態」を定義するインターフェースです。
 * <p>
 * 入力状態（直接入力、見出し語入力中、変換中など）に応じたキー入力の振る舞いや、
 * エディタへの表示文字列（Composing Text）の生成などを Strategy パターンで管理します。
 * </p>
 */
public interface SKKState {
    // --- 状態フラグ ---

    /**
     * この状態が一時的な入力状態（▽, ▼等）であり、最終的に確定操作が必要かどうかを判定します。
     *
     * @return 一時的な状態なら true
     */
    boolean isTransient();

    /**
     * 現在、候補リストから変換候補を選択中（▼）かどうかを判定します。
     *
     * @return 候補選択中なら true
     */
    boolean isConverting();

    // --- メタ情報 ---

    /**
     * 現在の状態を示すシンボルテキスト（▽, ▼等）を取得します。
     *
     * @return 状態表示テキスト、または null
     */
    String getText();

    /**
     * 現在の状態を示すアイコンのリソース ID を取得します。
     *
     * @return アイコンリソース ID、または 0
     */
    int getIcon();

    // --- キー入力処理 ---

    /**
     * 指定されたキーコードを現在の状態に従って処理します。
     *
     * @param context SKKエンジンのコンテキスト
     * @param code    入力されたキーの Unicode コードポイント
     * @return イベントを消費した場合は true
     */
    boolean processKey(SKKEngine context, int code);

    /**
     * Ctrlキーと同時押しのキー入力を処理します。
     *
     * @param context SKKエンジンのコンテキスト
     * @param keyCode KeyEventで定義されているキーコード
     * @return イベントを消費した場合は true
     */
    boolean processCtrlKey(SKKEngine context, int keyCode);

    /**
     * Tab キーの入力を処理します。
     *
     * @param context   SKKエンジンのコンテキスト
     * @param isShifted Shift押下中かどうか
     * @return イベントを消費した場合は true
     */
    boolean processTab(SKKEngine context, boolean isShifted);

    /**
     * Enter キーの入力を処理します。
     *
     * @param context SKKエンジンのコンテキスト
     * @return イベントを消費した場合は true
     */
    boolean processEnter(SKKEngine context);

    /**
     * 方向（DPAD）キーの入力を処理します。
     *
     * @param context SKKエンジンのコンテキスト
     * @param keyCode KeyEvent で定義されているキーコード
     * @return イベントを消費した場合は true
     */
    boolean processDpad(SKKEngine context, int keyCode);

    /**
     * システムの「戻る」ボタン押下時のイベントを処理します。
     *
     * @param context SKKエンジンのコンテキスト
     * @return イベントを消費した場合は true
     */
    boolean handleBackKey(SKKEngine context);

    // --- ライフサイクル ---

    /**
     * この状態に入った際の初期化処理を行います。
     *
     * @param context SKKエンジンのコンテキスト
     */
    void onEnterState(SKKEngine context);

    /**
     * この状態から脱ける際のクリーンアップ処理を行います。
     *
     * @param context SKKエンジンのコンテキスト
     */
    void onExitState(SKKEngine context);

    // --- テキスト入力処理 ---

    /**
     * ローマ字かな変換エンジンによる拡張コマンド（q, l, / 等）を処理します。
     *
     * @param context SKKエンジンのコンテキスト
     * @param text    変換確定したテキスト
     * @param isUpper 大文字入力（Shift押下）されたかどうか
     * @return 拡張コマンドとして処理された場合は true
     */
    boolean processRomajiExtension(SKKEngine context, String text, boolean isUpper);

    /**
     * 変換確定したテキスト（かな・記号等）を処理します。
     *
     * @param context SKKエンジンのコンテキスト
     * @param text    入力されたテキスト
     * @param initial 入力に使われた最初の 1 文字
     * @param isUpper 大文字入力（Shift押下）されたかどうか
     */
    void processText(SKKEngine context, String text, char initial, boolean isUpper);

    /**
     * ローマ字かな変換が 1 文字分完了した際の通知を受け取ります。
     *
     * @param context SKKエンジンのコンテキスト
     */
    void onFinishRomaji(SKKEngine context);

    // --- 削除・キャンセル ---

    /**
     * バックスペースキーによる削除処理を行います。
     *
     * @param context SKKエンジンのコンテキスト
     * @return 削除イベントを消費した場合は true
     */
    boolean processBackspace(SKKEngine context);

    /**
     * バックスペース処理が実行される直前の状態保存などを行います。
     *
     * @param context SKKエンジンのコンテキスト
     */
    void beforeBackspace(SKKEngine context);

    /**
     * バックスペース処理が実行された後の状態遷移などを制御します。
     *
     * @param context SKKエンジンのコンテキスト
     */
    void afterBackspace(SKKEngine context);

    /**
     * 現在の入力操作を中断し、可能であれば前の状態に戻します。
     *
     * @param context SKKエンジンのコンテキスト
     * @return キャンセルが実行された場合は true
     */
    boolean handleCancel(SKKEngine context);

    // --- 確定・モード切替 ---

    /**
     * 現在の未確定入力を強制的に確定させ、状態をリセットします。
     *
     * @param context SKKエンジンのコンテキスト
     * @return 確定処理が行われた場合は true
     */
    boolean finish(SKKEngine context);

    /**
     * かなモード（ひらがな/カタカナ）をトグルします。
     *
     * @param context SKKエンジンのコンテキスト
     */
    void toggleKana(SKKEngine context);

    /**
     * エディタに送信する未確定文字列（Composing Text）を構築します。
     *
     * @param context SKKエンジンのコンテキスト
     * @return 構築された CharSequence、または表示するものがない場合は null
     */
    CharSequence getComposingText(SKKEngine context);
}
