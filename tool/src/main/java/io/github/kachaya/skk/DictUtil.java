package io.github.kachaya.skk;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 辞書作成や日本語文字列処理に関する独立したユーティリティクラス。
 */
public final class DictUtil {

    private DictUtil() {
    }

    /**
     * 文字列内の全角カタカナをひらがなに変換します。
     *
     * @param s 変換対象の文字列
     * @return 変換後のひらがな文字列
     */
    public static String toHiragana(String s) {
        if (s == null) return null;
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
     * 文字列内の全角ひらがなをカタカナに変換します。
     *
     * @param s 変換対象の文字列
     * @return 変換後のカタカナ文字列
     */
    public static String toKatakana(String s) {
        if (s == null) return null;
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
     * 文字列が英小文字のみで構成されているか判定します。
     *
     * @param s 判定対象の文字列。null の場合は false を返します。
     * @return 英小文字のみの場合は true
     */
    public static boolean isLowerAlphabetOnly(String s) {
        return s != null && s.matches("^[a-z]+$");
    }

    /**
     * 文字列がひらがな（および長音）のみで構成されているか判定します。
     *
     * @param s 判定対象の文字列。null の場合は false を返します。
     * @return ひらがなのみの場合は true
     */
    public static boolean isHiraganaOnly(String s) {
        return s != null && s.matches("^[\\p{IsHiragana}ー]+$");
    }

    /**
     * 文字列がカタカナ（および長音）のみで構成されているか判定します。
     *
     * @param s 判定対象の文字列。null の場合は false を返します。
     * @return カタカナのみの場合は true
     */
    public static boolean isKatakanaOnly(String s) {
        return s != null && s.matches("^[\\p{IsKatakana}ー]+$");
    }

    /**
     * 文字列が仮名（ひらがな・カタカナ・長音）のみで構成されているか判定します。
     *
     * @param s 判定対象の文字列。null の場合は false を返します。
     * @return 仮名のみの場合は true
     */
    public static boolean isKanaOnly(String s) {
        return s != null && s.matches("^[\\p{IsHiragana}\\p{IsKatakana}ー]+$");
    }

    /**
     * 文字列が漢字のみで構成されているか判定します。
     *
     * @param s 判定対象の文字列。null の場合は false を返します。
     * @return 漢字のみの場合は true
     */
    public static boolean isKanjiOnly(String s) {
        return s != null && s.matches("^[\\p{IsHan}]+$");
    }

    /**
     * 文字列が 1 文字の漢字であるか判定します。
     *
     * @param s 判定対象の文字列
     * @return 1 文字の漢字の場合は true
     */
    public static boolean isSingleKanji(String s) {
        return s != null && s.length() == 1 && isKanjiOnly(s);
    }

    /**
     * 文字列が日本語文字（漢字・ひらがな・カタカナ・長音・〆）のみで構成されているか判定します。
     *
     * @param s 判定対象の文字列。null の場合は false を返します。
     * @return 日本語文字のみの場合は true
     */
    public static boolean isJapaneseOnly(String s) {
        return s != null && s.matches("^[\\p{IsHan}\\p{IsKatakana}\\p{IsHiragana}ー〆]+$");
    }

    /**
     * 文字列が基本的な日本語文字（漢字・ひらがな・カタカナ・長音）のみで構成されているか判定します。
     *
     * @param s 判定対象の文字列。null の場合は false を返します。
     * @return 基本的な日本語文字のみの場合は true
     */
    public static boolean isJapaneseTextOnly(String s) {
        return s != null && s.matches("^[\\p{IsHan}\\p{IsKatakana}\\p{IsHiragana}ー]+$");
    }

    /**
     * 表記と読みの組み合わせが妥当であるか判定します。
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
     * 2つの文字列に含まれる長音符号（「ー」）の個数が一致するか判定します。
     *
     * @param s1 判定対象の文字列1
     * @param s2 判定対象の文字列2
     * @return 一致する場合は true
     */
    public static boolean matchesChoonCount(String s1, String s2) {
        if (s1 == null || s2 == null) return false;
        return countChar(s1, 'ー') == countChar(s2, 'ー');
    }

    private static int countChar(String s, char c) {
        int count = 0;
        for (int i = 0; i < s.length(); i++) {
            if (s.charAt(i) == c) {
                count++;
            }
        }
        return count;
    }

    /**
     * 表記を語幹（漢字を含む部分）と送り仮名（末尾のひらがな部分）に分割します。
     * 例: "書き" -> ["書", "き"]
     *
     * @param surface 表記
     * @return [語幹, 送り仮名] の配列。分割できない場合は null。
     */
    public static String[] parseSurface(String surface) {
        if (surface == null) return null;
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
