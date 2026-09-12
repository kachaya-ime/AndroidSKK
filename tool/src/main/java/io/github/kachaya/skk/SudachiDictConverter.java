package io.github.kachaya.skk;

import java.io.IOException;

/**
 * Sudachi 辞書のコンバーターエントリポイント。
 * <p>
 * {@link SudachiDictLoader} を用いて Sudachi 辞書データを読み込み、
 * {@link SkkDictBuilder} を順次実行して SKK 辞書を生成します。
 * </p>
 */
public class SudachiDictConverter {

    /**
     * コンバーターのエントリーポイント。
     *
     * @param args コマンドライン引数
     */
    public static void main(String[] args) {
        try {
            SudachiDictLoader loader = new SudachiDictLoader();
            SkkDictBuilder skkBuilder = new SkkDictBuilder();

            loader.load(skkBuilder);
            skkBuilder.buildOutput();
        } catch (IOException e) {
            System.err.println("Error converting Sudachi dictionary: " + e.getMessage());
            e.printStackTrace();
        }
    }
}
