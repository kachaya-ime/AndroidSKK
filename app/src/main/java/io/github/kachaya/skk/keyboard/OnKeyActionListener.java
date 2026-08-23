package io.github.kachaya.skk.keyboard;

/**
 * キーボードからの入力を受け取るためのリスナーインターフェースです。
 */
public interface OnKeyActionListener {
    /**
     * キーが押された際に呼び出されます。
     *
     * @param config 押されたキーの構成情報
     */
    void onKey(KeyConfig config);
}
