package io.github.kachaya.skk;

/**
 * SKK 辞書のエントリ（読みと表記のペア）を保持するデータクラスです。
 */
public class SkkEntry {
    /** SKK 辞書における読み（送りなし: "かな", 送りあり: "かか", abbrev: "kaka" 等） */
    public String reading;
    /** 対応する表記（例: "仮名", "書か" 等） */
    public String surface;

    /**
     * SkkEntry の新しいインスタンスを構築します。
     *
     * @param reading 読み
     * @param surface 表記
     */
    public SkkEntry(String reading, String surface) {
        this.reading = reading;
        this.surface = surface;
    }

    /**
     * デバッグ用 TSV 出力のための 1 行を生成します。
     *
     * @return TSV 文字列
     */
    public String toTsv() {
        return String.join("\t", reading, surface);
    }
}
