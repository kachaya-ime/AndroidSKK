package io.github.kachaya.skk;

import java.util.HashMap;
import java.util.Map;

/**
 * ローマ字からかなへの変換、および文字種変換（全角/半角、ひらがな/カタカナ）を担当するクラスです。
 * <p>
 * SKK のローマ字入力ルールに基づき, {@link RomajiMap} (Trie構造) を用いた最長前方一致検索を行います。
 * 逐次的なキー入力をバッファリングし、変換が確定したタイミングで {@link SKKEngine} へテキストを通知します。
 * </p>
 */
public class RomajiConverter {

    /**
     * 静的に定義されたローマ字かな変換テーブル。
     * 五十音、促音（putSokuon）、SKK特有の記号（z＋キー）などのルールを保持します。
     * <p>
     * 伝統的な SKK に倣い、wi/we には「ゐ/ゑ」を割り当て、うぃ/うぇ は whi/whe で入力します。
     * </p>
     */
    private static final RomajiMap romajiMap = new RomajiMap() {{
        putGodan("", "あ", "い", "う", "え", "お");
        putGodan("b", "ば", "び", "ぶ", "べ", "ぼ");
        putGodan("by", "びゃ", "びぃ", "びゅ", "びぇ", "びょ");
        putGodan("c", "か", "し", "く", "せ", "こ");
        putGodan("cy", "ちゃ", "ちぃ", "ちゅ", "ちぇ", "ちょ");
        putGodan("ch", "ちゃ", "ち", "ちゅ", "ちぇ", "ちょ");
        putGodan("d", "だ", "ぢ", "づ", "で", "ど");
        putGodan("dh", "でゃ", "でぃ", "でゅ", "でぇ", "でょ");
        putGodan("dy", "ぢゃ", "ぢぃ", "ぢゅ", "ぢぇ", "ぢょ");
        putGodan("dw", "どぁ", "どぃ", "どぅ", "どぇ", "どぉ");
        putGodan("f", "ふぁ", "ふぃ", "ふ", "ふぇ", "ふぉ");
        putGodan("g", "が", "ぎ", "ぐ", "げ", "ご");
        putGodan("gy", "ぎゃ", "ぎぃ", "ぎゅ", "ぎぇ", "ぎょ");
        putGodan("gw", "ぐぁ", "ぐぃ", "ぐぅ", "ぐぇ", "ぐぉ");
        putGodan("h", "は", "ひ", "ふ", "へ", "ほ");
        putGodan("hy", "ひゃ", "ひぃ", "ひゅ", "ひぇ", "ひょ");
        putGodan("j", "じゃ", "じ", "じゅ", "じぇ", "じょ");
        putGodan("k", "か", "き", "く", "け", "こ");
        putGodan("ky", "きゃ", "きぃ", "きゅ", "きぇ", "きょ");
        putGodan("kw", "くぁ", "くぃ", "くぅ", "くぇ", "くぉ");
        putGodan("m", "ま", "み", "む", "め", "も");
        putGodan("my", "みゃ", "みぃ", "みゅ", "みぇ", "みょ");
        putGodan("n", "な", "に", "ぬ", "ね", "の");
        putGodan("ny", "にゃ", "にぃ", "にゅ", "にぇ", "にょ");
        putGodan("p", "ぱ", "ぴ", "ぷ", "ぺ", "ぽ");
        putGodan("py", "ぴゃ", "ぴぃ", "ぴゅ", "ぴぇ", "ぴょ");
        putGodan("r", "ら", "り", "る", "れ", "ろ");
        putGodan("ry", "りゃ", "りぃ", "りゅ", "りぇ", "りょ");
        putGodan("s", "さ", "し", "す", "せ", "そ");
        putGodan("sh", "しゃ", "し", "しゅ", "しぇ", "しょ");
        putGodan("sy", "しゃ", "しぃ", "しゅ", "しぇ", "しょ");
        putGodan("sw", "すぁ", "すぃ", "すぅ", "すぇ", "すぉ");
        putGodan("t", "た", "ち", "つ", "て", "と");
        putGodan("th", "てゃ", "てぃ", "てゅ", "てぇ", "てょ");
        putGodan("ty", "ちゃ", "ちぃ", "ちゅ", "ちぇ", "ちょ");
        putGodan("ts", "つぁ", "つぃ", "つ", "つぇ", "つぉ");
        putGodan("tw", "とぁ", "とぃ", "とぅ", "とぇ", "とぉ");
        putGodan("v", "ゔぁ", "ゔぃ", "ゔ", "ゔぇ", "ゔぉ");
        putGodan("w", "わ", "ゐ", "う", "ゑ", "を");
        putGodan("wh", "うぁ", "うぃ", "う", "うぇ", "うぉ");
        putGodan("x", "ぁ", "ぃ", "ぅ", "ぇ", "ぉ");
        putGodan("xy", "ゃ", "ぃ", "ゅ", "ぇ", "ょ");
        putGodan("y", "や", "いぃ", "ゆ", "いぇ", "よ");
        putGodan("z", "ざ", "じ", "ず", "ぜ", "ぞ");
        putGodan("zy", "じゃ", "じぃ", "じゅ", "じぇ", "じょ");

        put("xka", "ヵ");
        put("xke", "ヶ");
        put("xwa", "ゎ");

        put("xtu", "っ");

        put("z ", "　"); // DDSKK
        put("z!", "！"); // DDSKK
        put("z#", "♯"); // CorvusSKK
        put("z(", "（"); // DDSKK
        put("z)", "）"); // DDSKK
        put("z*", "※"); // AquaSKK / CorvusSKK
        put("z+", "±"); // AquaSKK / CorvusSKK
        put("z,", "\u2025"); // AquaSKK
        put("z-", "\u301C"); // AquaSKK 波ダッシュ
        put("z.", "\u2026"); // AquaSKK / CorvusSKK 三点リーダ
        put("z/", "・"); // DDSKK
        put("z0", "○"); // AquaSKK
        put("z1", "①"); // AquaSKK
        put("z2", "②"); // AquaSKK
        put("z3", "③"); // AquaSKK
        put("z4", "④"); // AquaSKK
        put("z5", "⑤"); // AquaSKK
        put("z6", "⑥"); // AquaSKK
        put("z7", "⑦"); // AquaSKK
        put("z8", "⑧"); // AquaSKK
        put("z9", "⑨"); // AquaSKK
        put("z:", "："); // DDSKK
        put("z;", "；"); // DDSKK
        put("z<", "〈"); // AquaSKK
        put("z=", "≠"); // AquaSKK
        put("z>", "〉"); // AquaSKK
        put("z?", "？"); // DDSKK
        put("z@", "◎"); // AquaSKK
        put("z[", "『"); // AquaSKK / CorvusSKK
        put("z\\", "＼"); // DDSKK
        put("z]", "』"); // AquaSKK / CorvusSKK
        put("zh", "←"); // DDSKK
        put("zj", "↓"); // DDSKK
        put("zk", "↑"); // DDSKK
        put("zl", "→"); // DDSKK
        put("z{", "【"); // AquaSKK
        put("z}", "】"); // AquaSKK
        put("z~", "\uFF5E"); // AquaSKK 全角チルダ

        // 促音（っ）のルール。子音が重なった場合に「っ」を出し、次の子音をバッファに残す。
        putSokuon("b");
        putSokuon("c");
        putSokuon("d");
        putSokuon("f");
        putSokuon("g");
        putSokuon("h");
        putSokuon("j");
        putSokuon("k");
        putSokuon("m");
        putSokuon("p");
        putSokuon("r");
        putSokuon("s");
        putSokuon("t");
        putSokuon("v");
        putSokuon("w");
        putSokuon("x");
        putSokuon("y");
        putSokuon("z");

        put("n", "ん");
        put("nn", "ん");

        put("-", "ー"); // DDSKK
        put("~", "\u301C"); // DDSKK 波ダッシュ
        put("[", "「"); // DDSKK
        put("]", "」"); // DDSKK
    }};
    /** 全角カタカナから半角カタカナへの変換テーブル。 */
    private static final Map<Character, String> halfWidthKatakanaMap = new HashMap<>() {{
        // 清音
        put('ア', "ｱ");
        put('イ', "ｲ");
        put('ウ', "ｳ");
        put('エ', "ｴ");
        put('オ', "ｵ");

        put('カ', "ｶ");
        put('キ', "ｷ");
        put('ク', "ｸ");
        put('ケ', "ｹ");
        put('コ', "ｺ");

        put('サ', "ｻ");
        put('シ', "ｼ");
        put('ス', "ｽ");
        put('セ', "ｾ");
        put('ソ', "ｿ");

        put('タ', "ﾀ");
        put('チ', "ﾁ");
        put('ツ', "ﾂ");
        put('テ', "ﾃ");
        put('ト', "ﾄ");

        put('ナ', "ﾅ");
        put('ニ', "ﾆ");
        put('ヌ', "ﾇ");
        put('ネ', "ﾈ");
        put('ノ', "ﾉ");

        put('ハ', "ﾊ");
        put('ヒ', "ﾋ");
        put('フ', "ﾌ");
        put('ヘ', "ﾍ");
        put('ホ', "ﾎ");

        put('マ', "ﾏ");
        put('ミ', "ﾐ");
        put('ム', "ﾑ");
        put('メ', "ﾒ");
        put('モ', "ﾓ");

        put('ヤ', "ﾔ");
        put('ユ', "ﾕ");
        put('ヨ', "ﾖ");

        put('ラ', "ﾗ");
        put('リ', "ﾘ");
        put('ル', "ﾙ");
        put('レ', "ﾚ");
        put('ロ', "ﾛ");

        put('ワ', "ﾜ");
        put('ヲ', "ｦ");
        put('ン', "ﾝ");

        // 拗音・促音
        put('ァ', "ｧ"); // \uFF67
        put('ィ', "ｨ"); // \uFF68
        put('ゥ', "ｩ"); // \uFF69
        put('ェ', "ｴ"); // \uFF6A
        put('ォ', "ｫ"); // \uFF6B
        put('ャ', "ｬ"); // \uFF6C
        put('ュ', "ｭ"); // \uFF6D
        put('ョ', "ｮ"); // \uFF6E
        put('ッ', "ｯ"); // \uFF6F

        // 濁音
        put('ガ', "ｶﾞ");
        put('ギ', "ｷﾞ");
        put('グ', "ｸﾞ");
        put('ゲ', "ｹﾞ");
        put('ゴ', "ｺﾞ");

        put('ザ', "ｻﾞ");
        put('ジ', "ｼﾞ");
        put('ズ', "ｽﾞ");
        put('ゼ', "ｾﾞ");
        put('ゾ', "ｿﾞ");

        put('ダ', "ﾀﾞ");
        put('ヂ', "ﾁﾞ");
        put('ヅ', "ﾂﾞ");
        put('デ', "ﾃﾞ");
        put('ド', "ﾄﾞ");

        put('バ', "ﾊﾞ");
        put('ビ', "ﾋﾞ");
        put('ブ', "ﾌﾞ");
        put('ベ', "ﾍﾞ");
        put('ボ', "ﾎﾞ");

        // 半濁音
        put('パ', "ﾊﾟ");
        put('ピ', "ﾋﾟ");
        put('プ', "ﾌﾟ");
        put('ペ', "ﾍﾟ");
        put('ポ', "ﾎﾟ");

        // 特殊
        put('ヴ', "ｳﾞ");
        put('ー', "ｰ");

        // 記号
        put('、', "､");
        put('。', "｡");
        put('・', "･");
        put('「', "｢");
        put('」', "｣");
    }};
    /** 現在入力中の未確定ローマ字バッファ。変換ルールが成立するまで蓄積されます。 */
    private final StringBuilder mComposing = new StringBuilder();
    /** 変換確定時のテキストコミットや状態通知を行う engine への参照。 */
    private final SKKEngine mEngine;
    /**
     * 大文字入力（Shift押下）により、送り仮名の「入力待ち」状態にあるかどうか。
     * 例: 'K' 入力後の "k" 状態で、次の入力が送り仮名として扱われるべきであることを示します。
     */
    private boolean mShiftSent = false;

    /**
     * RomajiConverter を初期化します。
     *
     * @param engine 連携する SKKEngine のインスタンス
     */
    RomajiConverter(SKKEngine engine) {
        mEngine = engine;
    }

    /**
     * 与えられた文字がアルファベット (A-Z, a-z) かどうかを判定します。
     *
     * @param code Unicode コードポイント
     * @return アルファベットなら true
     */
    public static boolean isAlphabet(int code) {
        return ((code >= 'A' && code <= 'Z') || (code >= 'a' && code <= 'z'));
    }

    /**
     * 半角文字（ASCII）を対応する全角文字に変換します。
     * <p>
     * スペース(U+0020 → U+3000)、円記号、チルダ(U+007E → U+FF5E) などの特殊変換を含みます。
     * </p>
     *
     * @param code 半角文字のコードポイント
     * @return 対応する全角文字のコードポイント
     */
    public static int toWideLatin(int code) {
        if (code == 0x20) { // スペース -> 全角スペース (U+3000)
            return 0x3000;
        }
        if (code == '\u00A5') { // 円記号 (U+00A5) -> 全角円記号 (U+FFE5)
            return 0xFFE5;
        }
        if (0x21 <= code && code <= 0x7E) {
            // ASCII 記号・英数字を対応する全角（U+FF01〜U+FF5E）に一括変換
            // 0x7E (~) は 0xFF5E (～: 全角チルダ) になる
            return code - 0x20 + 0xFF00;
        }
        return code;
    }

    /**
     * 文字列内のすべての半角 ASCII 文字を全角に変換します。
     *
     * @param str 変換対象の文字列
     * @return 全角変換後の文字列。入力が null なら null。
     */
    public static CharSequence toWideLatin(CharSequence str) {
        if (str == null) {
            return null;
        }

        int len = str.length();
        StringBuilder buf = new StringBuilder(len);
        for (int i = 0; i < len; i++) {
            buf.append((char) toWideLatin(str.charAt(i)));
        }
        return buf;
    }

    /**
     * ひらがなを対応する全角カタカナに変換します。
     *
     * @param ch 変換対象のひらがな
     * @return 対応するカタカナ。範囲外なら元の文字。
     */
    public static char toWideKatakana(char ch) {
        if (ch >= 'ぁ' && ch <= 'ゖ') {
            return (char) (ch - 'ぁ' + 'ァ');
        }
        return ch;
    }

    /**
     * 文字列内のすべてのひらがなをカタカナに変換します。
     *
     * @param cs 変換対象の文字列
     * @return カタカナ変換後の文字列
     */
    public static String toWideKatakana(CharSequence cs) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < cs.length(); i++) {
            sb.append(toWideKatakana(cs.charAt(i)));
        }
        return sb.toString();
    }

    /**
     * 文字列内のすべての全角ひらがな・カタカナを半角カタカナに変換します。
     * 濁点・半濁点は 2 文字（例: 「ガ」→「ｶﾞ」）に分解されます。
     *
     * @param cs 変換対象の文字列
     * @return 半角カタカナ変換後の文字列
     */
    public static String toHalfKatakana(CharSequence cs) {
        StringBuilder sb = new StringBuilder();
        String katakana = toWideKatakana(cs);
        for (int i = 0; i < katakana.length(); i++) {
            char c = katakana.charAt(i);
            sb.append(halfWidthKatakanaMap.getOrDefault(c, String.valueOf(c)));
        }
        return sb.toString();
    }

    /**
     * 現在バッファに蓄積されている未確定のローマ字を取得します。
     *
     * @return 未確定ローマ字列
     */
    CharSequence getComposing() {
        return mComposing;
    }

    /**
     * 未確定のローマ字バッファに文字が残っているか判定します。
     *
     * @return バッファが空でなければ true
     */
    boolean hasComposing() {
        return mComposing.length() != 0;
    }

    /**
     * キー入力を処理し、ローマ字かな変換を試みます。
     * <p>
     * 入力された文字をバッファに追加し、{@link RomajiMap} を用いて検索を行います。
     * 検索結果に応じて以下の処理を行います：
     * <ul>
     *   <li><b>完全一致 (Leaf):</b> 変換値をエンジンにコミットし、バッファをクリア（または次の文字を継承）します。</li>
     *   <li><b>中間一致:</b> さらなる入力を待機します。大文字入力の場合は送り仮名フラグを更新します。</li>
     *   <li><b>部分一致:</b> 一致した部分のみを確定させ、残りをバッファに保持して再検索します。</li>
     *   <li><b>不一致:</b> 入力された文字列をそのまま（変換不能として）確定させます。</li>
     * </ul>
     * </p>
     *
     * @param code 入力されたキーの Unicode コードポイント
     */
    void processKey(int code) {
        // シフトキーの状態をチェック。SKKでは ASCII 大文字のみを状態遷移のトリガーとする。
        // ギリシャ文字等の大文字が誤反応するのを防ぐため、明示的に A-Z の範囲に限定する。
        boolean isUpper = (code >= 'A' && code <= 'Z');
        if (isUpper) { // ローマ字変換のために小文字として扱う
            code = Character.toLowerCase(code);
        }

        mComposing.append((char) code);

        while (true) {
            char initialChar = '\0';
            if (mComposing.length() > 0) {
                initialChar = mComposing.charAt(0);
            }
            RomajiMap.Node node = romajiMap.prefixSearch(mComposing.toString());
            if (node == null) {
                // ローマ字表にないシーケンスの場合は、バッファをそのまま確定として放出
                mEngine.commitRomajiText(mComposing.toString(), initialChar, isUpper);
                mShiftSent = false;
                mComposing.setLength(0);
                mEngine.onFinishRomaji();
                break;
            }
            if (node.getKey().length() == mComposing.length()) {
                if (node.isLeaf()) {
                    // 完全一致する変換ルールが見つかった場合
                    if (mShiftSent) {
                        isUpper = false;
                        mShiftSent = false;
                    }
                    mEngine.commitRomajiText(node.getValue(), initialChar, isUpper);
                    mComposing.setLength(0);
                    // 「っ」の処理などのため、次に引き継ぐ文字列があればバッファに戻す
                    if (node.getNext() != null) {
                        mComposing.append(node.getNext());
                    } else {
                        mEngine.onFinishRomaji();
                    }
                } else {
                    // 中間一致（さらに後続の文字でルールが完結する可能性がある）の場合
                    if (mShiftSent) {
                        isUpper = false;
                    } else if (isUpper) {
                        mShiftSent = true;
                    }
                    mEngine.commitRomajiText(null, '\0', isUpper);
                }
                break;
            } else {
                if (node.getValue() != null) {
                    // 部分一致（入力の先頭部分だけがルールに適合）した場合
                    mEngine.commitRomajiText(node.getValue(), initialChar, false);
                    mShiftSent = false;
                    mComposing.delete(0, node.getKey().length());
                    if (node.getNext() != null) {
                        mComposing.insert(0, node.getNext());
                    }
                } else {
                    // ルール不適合部分を切り離して確定
                    mEngine.commitRomajiText(node.getKey(), initialChar, false);
                    mShiftSent = false;
                    mComposing.delete(0, node.getKey().length());
                }
            }
        }
    }

    /**
     * バッファに残っている未確定ローマ字を、可能な限り変換してすべて強制的に放出します。
     * <p>
     * モード切り替え時や、現在の入力を強制的に確定させたい場合（Enter押下時など）に呼び出されます。
     * </p>
     *
     * @return 1 文字以上放出された場合は true
     */
    boolean flush() {
        if (mComposing.length() == 0) {
            reset();
            return false;
        }
        while (true) {
            char initialChar = '\0';
            if (mComposing.length() > 0) {
                initialChar = mComposing.charAt(0);
            }
            RomajiMap.Node node = romajiMap.prefixSearch(mComposing.toString());
            if (node == null) {
                break;
            }
            if (node.getValue() != null) {
                // 確定できる部分があれば放出
                mEngine.commitRomajiText(node.getValue(), initialChar, false);
                mComposing.delete(0, node.getKey().length());
                if (node.getNext() != null) {
                    mComposing.append(node.getNext());
                }
            } else {
                // 変換できないキー部分を放出
                mEngine.commitRomajiText(node.getKey(), initialChar, false);
                mComposing.delete(0, node.getKey().length());
            }
        }
        if (mComposing.length() != 0) {
            // それでも残った文字をそのまま放出
            mEngine.commitRomajiText(mComposing.toString(), mComposing.charAt(0), false);
        }
        reset();
        return true;
    }

    /**
     * 未確定状態を完全にリセットします。
     * バッファをクリアし、送り仮名待ち（Shift）フラグも解除します。
     *
     * @return リセット前, バッファが空でなかった場合は true
     */
    boolean reset() {
        mShiftSent = false;
        if (mComposing.length() == 0) {
            return false;
        }
        mComposing.setLength(0);
        return true;
    }

    /**
     * 未確定バッファの末尾から 1 文字削除します。
     * バッファが空になった場合、送り仮名待ち状態も解除されます。
     *
     * @return 削除が行われた場合は true
     */
    boolean handleBackspace() {
        if (mComposing.length() > 0) {
            mComposing.deleteCharAt(mComposing.length() - 1);
            if (mComposing.length() == 0) {
                mShiftSent = false;
            }
            return true;
        }
        return false;
    }
}
