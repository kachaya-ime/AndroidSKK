package io.github.kachaya.skk;

/**
 * SKK の「入力モード」を定義するインターフェースです。
 * <p>
 * 入力モード（ひらがな、カタカナ、英数、全角英数）に応じた文字変換の振る舞いや、
 * モード固有のアイコン、ツールチップ表示などを Strategy パターンで管理します。
 * </p>
 */
interface SKKMode {
    /**
     * 指定されたキーコードを現在のモードに従って処理します。
     *
     * @param context SKKエンジンのコンテキスト
     * @param code    入力されたキーの Unicode コードポイント
     */
    void processKey(SKKEngine context, int code);

    /**
     * Ctrlキーと同時押しのキー入力を、現在のモードに従って処理します。
     *
     * @param context SKKエンジンのコンテキスト
     * @param keyCode KeyEventで定義されているキーコード
     * @return イベントを消費した場合は true
     */
    boolean processCtrlKey(SKKEngine context, int keyCode);

    /**
     * 与えられたテキストを、現在のモードの性質に合わせて最終変換（半角化、カナ化等）します。
     *
     * @param text 変換対象の CharSequence
     * @return 変換後の CharSequence
     */
    CharSequence convertText(CharSequence text);

    /**
     * かなモード（ひらがなとカタカナ）を交互に切り替える際の、遷移先となるモードを取得します。
     *
     * @param context SKKエンジンのコンテキスト（設定の参照用）
     * @return 遷移先の SKKMode インスタンス、または null
     */
    SKKMode getToggledKanaMode(SKKEngine context);

    /**
     * このモードが有効な時に表示するアイコンのリソース ID を取得します。
     *
     * @return アイコンのリソース ID、または 0
     */
    int getIcon();

    /**
     * モード切替時などにツールチップとして表示する代表文字（ひ, カ, A 等）を取得します。
     *
     * @return 表示用文字列、または null
     */
    String getText();
}
