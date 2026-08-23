package io.github.kachaya.skk.engine;

/**
 * SKK の現在の入力モードや状態を示すアイコンの種類を定義する列挙型です。
 * <p>
 * エンジン層が Android のリソース ID (R.drawable) に直接依存するのを防ぐ抽象レイヤーとして機能します。
 * </p>
 */
public enum SKKIcon {
    /** アイコンを表示しない（無効状態）。 */
    NONE,
    /** 全角ひらがなモード（あ）。 */
    FULL_HIRAGANA,
    /** 全角カタカナモード（ア）。 */
    FULL_KATAKANA,
    /** 全角英数モード（Ａ）。 */
    FULL_LATIN,
    /** 半角カタカナモード（ｱ）。 */
    HALF_KATAKANA,
    /** Abbrev モード（Aｱ）。 */
    ABBREV
}
