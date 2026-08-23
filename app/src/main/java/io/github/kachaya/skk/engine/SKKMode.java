package io.github.kachaya.skk.engine;

/**
 * SKK の「入力モード」の振る舞いを定義するインターフェースです。
 * <p>
 * 入力モード（ひらがな、カタカナ、英数、全角英数）に応じた文字変換のロジック、
 * モード固有のアイコン表示、およびツールチップテキストの提供を Strategy パターンで管理します。
 * </p>
 */
public interface SKKMode {
    /**
     * 指定されたキーコードを現在のモードの規則に従って処理します。
     *
     * @param context SKK エンジンのコンテキスト
     * @param code 入力されたキーの Unicode コードポイント
     */
    void processKey(SKKEngine context, int code);

    /**
     * Ctrl キーと同時押しのキー入力を、現在のモードに従って処理します。
     *
     * @param context SKK エンジンのコンテキスト
     * @param keyCode KeyEvent で定義されているキーコード
     * @return イベントを消費した場合は true
     */
    boolean processCtrlKey(SKKEngine context, int keyCode);

    /**
     * 与えられたテキストを、現在のモードの性質に合わせて最終変換（半角化、全角化等）します。
     *
     * @param text 変換対象の文字列
     * @return 変換後の文字列
     */
    CharSequence convertText(CharSequence text);

    /**
     * かなモード（ひらがなとカタカナ）を交互に切り替える際の、遷移先となるモードを取得します。
     *
     * @param context SKK エンジンのコンテキスト
     * @return 遷移先の SKKMode インスタンス
     */
    SKKMode getToggledKanaMode(SKKEngine context);

    /**
     * このモードが有効な時に表示するアイコンの種類を取得します。
     *
     * @return アイコンの種類
     */
    SKKIcon getIcon();

    /**
     * モード切替時にツールチップ（カーソル付近の表示）として出す代表文字を取得します。
     *
     * @return 表示用文字列、または null
     */
    String getText();
}
