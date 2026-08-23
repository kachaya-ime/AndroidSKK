package io.github.kachaya.skk.engine;

/**
 * SKK の「入力状態」の振る舞いを定義するインターフェースです。
 * <p>
 * 直接入力（Direct）、見出し語入力中（▽）、変換中（▼）などの状態遷移と、
 * それぞれの状態におけるキーイベントの解釈を Strategy パターンで管理します。
 * </p>
 */
public interface SKKState {
    // --- 状態フラグ ---

    /**
     * この状態が一時的な入力状態（▽, ▼等）であり、最終的に確定操作（Enter等）が必要かどうかを判定します。
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
     * 現在の状態を示すアイコンの種類を取得します。
     *
     * @return アイコンの種類
     */
    SKKIcon getIcon();

    // --- キー入力処理 ---

    /**
     * 指定されたキーコードを現在の状態の規則に従って処理します。
     *
     * @param context SKK エンジンのコンテキスト
     * @param code 入力されたキーの Unicode コードポイント
     * @return イベントを消費した場合は true
     */
    boolean processKey(SKKEngine context, int code);

    /**
     * Ctrl キーと同時押しのキー入力を、現在の状態に従って処理します。
     *
     * @param context SKK エンジンのコンテキスト
     * @param keyCode KeyEvent で定義されているキーコード
     * @return イベントを消費した場合は true
     */
    boolean processCtrlKey(SKKEngine context, int keyCode);

    /**
     * Tab キーの入力を処理します。主に変換候補の選択や補完に使用されます。
     *
     * @param context SKK エンジンのコンテキスト
     * @param isShifted Shift 押下中（逆順選択）かどうか
     * @return イベントを消費した場合は true
     */
    boolean processTab(SKKEngine context, boolean isShifted);

    /**
     * Enter キーの入力を処理します。現在の入力を確定させるために使用されます。
     *
     * @param context SKK エンジンのコンテキスト
     * @return イベントを消費した場合は true
     */
    boolean processEnter(SKKEngine context);

    /**
     * 方向（DPAD）キーの入力を処理します。
     *
     * @param context SKK エンジンのコンテキスト
     * @param keyCode KeyEvent で定義されているキーコード
     * @return イベントを消費した場合は true
     */
    boolean processDpad(SKKEngine context, int keyCode);

    /**
     * システムの「戻る」ボタン押下時のイベントを処理します。
     *
     * @param context SKK エンジンのコンテキスト
     * @return イベントを消費した場合は true
     */
    boolean handleBackKey(SKKEngine context);

    // --- ライフサイクル ---

    /**
     * この状態へ遷移した直後の初期化処理を行います。
     *
     * @param context SKK エンジンのコンテキスト
     */
    void onEnterState(SKKEngine context);

    /**
     * この状態から別の状態へ遷移する直前のクリーンアップ処理を行います。
     *
     * @param context SKK エンジンのコンテキスト
     */
    void onExitState(SKKEngine context);

    // --- テキスト入力処理 ---

    /**
     * ローマ字かな変換エンジンによる確定テキストに基づき、拡張コマンド（q, l, / 等）を処理します。
     *
     * @param context SKK エンジンのコンテキスト
     * @param text 変換確定したテキスト
     * @param isUpper 大文字入力（Shift 押下）されたかどうか
     * @return 拡張コマンドとして処理された場合は true
     */
    boolean processRomajiExtension(SKKEngine context, String text, boolean isUpper);

    /**
     * 変換確定したテキスト（かな・記号等）を受け取り、バッファへの追加やエディタへの送信を行います。
     *
     * @param context SKK エンジンのコンテキスト
     * @param text 入力されたテキスト
     * @param initial 入力に使われた最初の 1 文字（ローマ字の先頭等）
     * @param isUpper 大文字入力（Shift 押下）されたかどうか
     */
    void processText(SKKEngine context, String text, char initial, boolean isUpper);

    /**
     * ローマ字かな変換が 1 文字分（1 シーケンス分）完了した際の通知を受け取ります。
     *
     * @param context SKK エンジンのコンテキスト
     */
    void onFinishRomaji(SKKEngine context);

    // --- 削除・キャンセル ---

    /**
     * バックスペースキーによる削除処理を実行します。
     *
     * @param context SKK エンジンのコンテキスト
     * @return 削除イベントを消費した場合は true
     */
    boolean processBackspace(SKKEngine context);

    /**
     * バックスペース処理が実行される直前に呼び出されます。
     *
     * @param context SKK エンジンのコンテキスト
     */
    void beforeBackspace(SKKEngine context);

    /**
     * バックスペース処理が実行された直後に呼び出されます。状態の自動差し戻し等に使用します。
     *
     * @param context SKK エンジンのコンテキスト
     */
    void afterBackspace(SKKEngine context);

    /**
     * 現在の入力操作（変換中等）を中断し、前の状態に戻します。
     *
     * @param context SKK エンジンのコンテキスト
     * @return キャンセルが実行された場合は true
     */
    boolean handleCancel(SKKEngine context);

    // --- 確定・モード切替 ---

    /**
     * 現在の未確定入力を強制的に確定（エディタへ送信）させ、状態をリセットします。
     *
     * @param context SKK エンジンのコンテキスト
     * @return 確定処理が行われた場合は true
     */
    boolean finish(SKKEngine context);

    /**
     * かなモード（ひらがな/カタカナ）を交互に切り替えます。
     *
     * @param context SKK エンジンのコンテキスト
     */
    void toggleKana(SKKEngine context);

    /**
     * エディタにインライン表示する未確定文字列（Composing Text）を構築します。
     *
     * @param context SKK エンジンのコンテキスト
     * @return 表示する文字列。表示するものがない場合は null
     */
    CharSequence getComposingText(SKKEngine context);
}
