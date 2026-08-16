package io.github.kachaya.skk;

import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 辞書作成や文字列処理に関するユーティリティクラス。
 */
public class DictUtil {

    /**
     * 文字列内のカタカナをひらがなに変換します。
     *
     * @param s 変換対象の文字列
     * @return 変換後のひらがな文字列
     */
    public static String toHiragana(String s) {
        StringBuilder sb = new StringBuilder();
        for (char c : s.toCharArray()) {
            if (c >= 'ァ' && c <= 'ヶ' || c == 'ヽ' || c == 'ヾ') {
                sb.append((char) (c - 'ァ' + 'ぁ'));
            } else {
                sb.append(c);
            }
        }
        return sb.toString();
    }

    /**
     * 文字列内のひらがなをカタカナに変換します。
     *
     * @param s 変換対象の文字列
     * @return 変換後のカタカナ文字列
     */
    public static String toKatakana(String s) {
        StringBuilder sb = new StringBuilder();
        for (char c : s.toCharArray()) {
            if (c >= 'ぁ' && c <= 'ゖ' || c == 'ゝ' || c == 'ゞ') {
                sb.append((char) (c - 'ぁ' + 'ァ'));
            } else {
                sb.append(c);
            }
        }
        return sb.toString();
    }

    /**
     * 文字列が日本語文字（漢字、ひらがな、カタカナ、長音）を含んでいるか判定します。
     *
     * @param s 判定対象の文字列
     * @return 日本語文字を含む場合は true
     */
    public static boolean hasJapanese(String s) {
        return s.matches(".*[\\p{IsHan}\\p{IsHiragana}\\p{Iskatakana}ー].*");
    }

    /**
     * 文字列がひらがな（および長音）のみで構成されているか判定します。
     *
     * @param s 判定対象の文字列
     * @return ひらがなのみの場合は true
     */
    public static boolean isHiraganaOnly(String s) {
        return s != null && s.matches("^[\\p{IsHiragana}ー]+$");
    }

    /**
     * 文字列が仮名（ひらがな・カタカナ・長音）のみで構成されているか判定します。
     *
     * @param s 判定対象の文字列
     * @return 仮名のみの場合は true
     */
    public static boolean isKanaOnly(String s) {
        return s != null && s.matches("^[\\p{IsHiragana}\\p{IsKatakana}ー]+$");
    }

    /**
     * 文字列が日本語文字（漢字・ひらがな・カタカナ・長音・〆）のみで構成されているか判定します。
     *
     * @param s 判定対象の文字列
     * @return 日本語文字のみの場合は true
     */
    public static boolean isJapaneseOnly(String s) {
        return s != null && s.matches("^[\\p{IsHan}\\p{IsKatakana}\\p{IsHiragana}ー〆]+$");
    }

    /**
     * 表記と読みの組み合わせが妥当であるか判定します。
     * 表記内の非ひらがな部分をワイルドカードとして読みと比較します。
     *
     * @param surface 表記
     * @param reading 読み
     * @return 妥当な組み合わせの場合は true
     */
    public static boolean matchesReading(String surface, String reading) {
        if (surface == null || reading == null) return false;
        String regex = surface.replaceAll("[^\\p{IsHiragana}]+", ".+");
        return reading.matches(regex);
    }

    /**
     * ひらがなから頭文字を取得するためのハッシュマップ
     */
    private static final Map<String, String> romajiHeadMap = new HashMap<>() {
        {
            put("あ", "a"); put("い", "i"); put("う", "u"); put("え", "e"); put("お", "o");
            put("か", "k"); put("き", "k"); put("く", "k"); put("け", "k"); put("こ", "k");
            put("さ", "s"); put("し", "s"); put("す", "s"); put("せ", "s"); put("そ", "s");
            put("た", "t"); put("ち", "tc"); put("つ", "t"); put("て", "t"); put("と", "t");
            put("な", "n"); put("に", "n"); put("ぬ", "n"); put("ね", "n"); put("の", "n");
            put("は", "h"); put("ひ", "h"); put("ふ", "hf"); put("へ", "h"); put("ほ", "h");
            put("ま", "m"); put("み", "m"); put("む", "m"); put("め", "m"); put("も", "m");
            put("や", "y"); put("ゆ", "y"); put("よ", "y");
            put("ら", "r"); put("り", "r"); put("る", "r"); put("れ", "r"); put("ろ", "r");
            put("わ", "w"); put("を", "w"); put("ん", "n");
            put("が", "g"); put("ぎ", "g"); put("ぐ", "g"); put("げ", "g"); put("ご", "g");
            put("ざ", "z"); put("じ", "zj"); put("ず", "z"); put("ぜ", "z"); put("ぞ", "z");
            put("だ", "d"); put("ぢ", "d"); put("づ", "d"); put("で", "d"); put("ど", "d");
            put("ば", "b"); put("び", "b"); put("ぶ", "b"); put("べ", "b"); put("ぼ", "b");
            put("ぱ", "p"); put("ぴ", "p"); put("ぷ", "p"); put("ぺ", "p"); put("ぽ", "p");
        }
    };

    /**
     * ひらがなからローマ文字の先頭の文字（SKK の送り仮名用サフィックス）を取得します。
     * 複数の候補がある場合は先頭のものを返します。
     *
     * @param hiragana 判定対象のひらがな（1文字以上）
     * @return ローマ字の頭文字。見つからない場合は null。
     */
    public static String getRomajiHead(String hiragana) {
        String key = hiragana.substring(0, 1);
        return romajiHeadMap.get(key);
    }

    /**
     * 表記を語幹（漢字部分）と送り仮名（ひらがな部分）に分割します。
     * 例: "書き" -> ["書", "き"]
     *
     * @param surface 表記
     * @return [語幹, 送り仮名] の配列。分割できない場合は null。
     */
    public static String[] parseSurface(String surface) {
        Pattern p = Pattern.compile("^(.*\\p{IsHan})(\\p{IsHiragana}+)$");
        Matcher m = p.matcher(surface);
        if (m.find()) {
            String[] result = new String[2];
            result[0] = m.group(1);
            result[1] = m.group(2);
            return result;
        }
        return null;
    }
}
