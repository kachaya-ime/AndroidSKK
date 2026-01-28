package io.github.kachaya.skk;

import java.util.HashMap;
import java.util.Map;

/**
 * ローマ字かな変換テーブルを管理する Trie (トライ) 構造のマップクラスです。
 * <p>
 * {@link HashMap} をベースに、登録されたキーの各プレフィックスを中間ノードとして保持することで、
 * 入力文字列に対する最長前方一致検索（Longest Prefix Match）を高速に行います。
 * </p>
 */
class RomajiMap {
    /**
     * ローマ字（キー）とそれに対応する変換情報ノードのマップ。
     * ルールとして登録された完全なキーのほか、その経路となる中間文字列もノードとして格納されます。
     */
    private final Map<String, Node> mMap = new HashMap<>();

    /**
     * 基本的なかな変換ルールを追加します。
     *
     * @param key   変換元のローマ字（例: "ka"）
     * @param value 変換後のかな（例: "か"）
     */
    void put(String key, String value) {
        put(key, value, null);
    }

    /**
     * 引き継ぎ文字列を含む変換ルールをマップに追加します。
     * <p>
     * 指定されたキーの各プレフィックスが未登録の場合、自動的に中間ノードとして登録されます。
     * すでにノードが存在する場合は、その {@code leaf} 状態を維持したまま、値を更新します。
     * これにより、"n" (ん) のように、「それ自体が変換値を持ちながら、さらなる入力も待機する」ノードを正しく管理できます。
     * </p>
     *
     * @param key   変換元のローマ字
     * @param value 変換後のかな
     * @param next  確定後にバッファに戻すべき文字
     */
    void put(String key, String value, String next) {
        Node oldNode = mMap.get(key);
        if (oldNode == null) {
            mMap.put(key, new Node(key, value, next, true));
            // Trie のパスを形成するため、親プレフィックスを再帰的に生成して登録
            for (int i = key.length() - 1; i > 0; i--) {
                String prefix = key.substring(0, i);
                Node prefixNode = mMap.get(prefix);
                if (prefixNode != null) {
                    break;
                }
                mMap.put(prefix, new Node(prefix, null, null, false));
            }
        } else {
            // 既存ノードの値を更新。leaf 状態は継承する。
            mMap.put(key, new Node(key, value, next, oldNode.leaf));
        }
    }

    /**
     * 子音と 5 つの母音（あいうえお）に対応するかなをまとめて登録します。
     *
     * @param consonant 子音（例: "k"）
     * @param a         "a" に対応するかな（例: "か"）
     * @param i         "i" に対応するかな（例: "き"）
     * @param u         "u" に対応するかな（例: "く"）
     * @param e         "e" に対応するかな（例: "け"）
     * @param o         "o" に対応するかな（例: "こ"）
     */
    void putGodan(String consonant, String a, String i, String u, String e, String o) {
        put(consonant + "a", a);
        put(consonant + "i", i);
        put(consonant + "u", u);
        put(consonant + "e", e);
        put(consonant + "o", o);
    }

    /**
     * 子音が重なった場合の促音（っ）の特殊ルールを登録します。
     * 例: putSokuon("s") は "ss" -> "っ" + "s" を登録します。
     *
     * @param consonant 対象となる子音
     */
    void putSokuon(String consonant) {
        put(consonant + consonant, "っ", consonant);
    }

    /**
     * 入力文字列に対して、保持しているテーブルの中から最長前方一致するノードを検索します。
     *
     * @param key 検索対象の文字列
     * @return 一致した最長の {@link Node}。全く一致しない場合は null。
     */
    Node prefixSearch(String key) {
        for (int i = key.length(); i > 0; i--) {
            String prefix = key.substring(0, i);
            Node node = mMap.get(prefix);
            if (node != null) {
                return node;
            }
        }
        return null;
    }

    /**
     * ローマ字かな変換の 1 つのノード（確定したルールまたは中間状態）を保持する不変クラスです。
     */
    static class Node {
        /** このノードを識別するローマ字キー（例: "ky", "n"）。 */
        private final String key;
        /**
         * 変換後のかな文字列（例: "きょ", "ん"）。
         * 変換ルールが定義されていない中間ノード（探索の経路のみ）の場合は null になります。
         */
        private final String value;
        /**
         * 変換確定後に未確定バッファへ差し戻す文字列。
         * 促音の連続（例: "tt" -> "っ" を出力して "t" をバッファに戻す）などの特殊な規則に使用されます。
         */
        private final String next;
        /**
         * このノードが末端ノード（これ以上長い一致ルールが存在しない）かどうか。
         * false の場合、後続の入力による、より長いルールへの一致（例: "n" に対する "na"）を待機します。
         */
        private final boolean leaf;

        Node(String key, String value, String next, boolean leaf) {
            this.key = key;
            this.value = value;
            this.next = next;
            this.leaf = leaf;
        }

        /**
         * このノードのローマ字キーを取得します。
         *
         * @return ローマ字キー文字列
         */
        String getKey() {
            return key;
        }

        /**
         * 変換後の値（かな）を取得します。
         *
         * @return 変換後のかな文字列。中間ノード等で値が定義されていない場合は null。
         */
        String getValue() {
            return value;
        }

        /**
         * 変換確定後に次回入力へ引き継ぐ（バッファに戻す）文字列を取得します。
         *
         * @return 引き継ぎ文字列。存在しない場合は null。
         */
        String getNext() {
            return next;
        }

        /**
         * このノードが末端（これ以上長いルールがない）であるか判定します。
         *
         * @return 末端ノードであれば true
         */
        boolean isLeaf() {
            return leaf;
        }
    }
}
