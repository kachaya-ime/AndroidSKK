package io.github.kachaya.skk;

import java.io.BufferedReader;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;

/**
 * 辞書作成や文字列処理に関するユーティリティクラス。
 */
public class DictUtil {

    private static final Map<Character, Integer> kanjiDictMap = new HashMap<>();

    static {
        Path dictPath = Paths.get("./work/kanji-dict.tsv");
        if (Files.exists(dictPath)) {
            try (BufferedReader reader = Files.newBufferedReader(dictPath, StandardCharsets.UTF_8)) {
                String line;
                while ((line = reader.readLine()) != null) {
                    if (line.isEmpty()) continue;
                    String[] parts = line.split("\t");
                    if (parts.length >= 2) {
                        char kanji = parts[0].charAt(0);
                        try {
                            int grade = Integer.parseInt(parts[1]);
                            kanjiDictMap.put(kanji, grade);
                        } catch (NumberFormatException e) {
                            // ignore
                        }
                    }
                }
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }
    }

    /**
     * 指定された文字の漢字学年（常用漢字などの区分）を取得します。
     * 辞書にない場合は 0 を返します。
     *
     * @param c 判定対象の文字
     * @return 漢字学年
     */
    public static int getKanjiGrade(char c) {
        Integer value = kanjiDictMap.get(c);
        if (value != null) return value;
        return 0;
    }

    /**
     * 文字列内のカタカナをひらがなに変換します。波ダッシュ「〜」は長音「ー」に正規化します。
     *
     * @param s 変換対象の文字列
     * @return 変換後のひらがな文字列
     */
    public static String toHiragana(String s) {
        if (s == null) return null;
        StringBuilder sb = new StringBuilder(s.length());
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c >= 'ァ' && c <= 'ヶ') {
                sb.append((char) (c - 'ァ' + 'ぁ'));
            } else if (c == '〜' || c == '～') {
                sb.append('ー');
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
        if (s == null) return null;
        StringBuilder sb = new StringBuilder();
        for (char c : s.toCharArray()) {
            if (c >= 'ぁ' && c <= 'ゖ') {
                sb.append((char) (c - 'ぁ' + 'ァ'));
            } else {
                sb.append(c);
            }
        }
        return sb.toString();
    }

    /**
     * 指定された文字がひらがなかどうかを判定します。長音「ー」や波ダッシュ「〜」も含みます。
     *
     * @param c 判定対象の文字
     * @return ひらがなであれば true
     */
    public static boolean isHiragana(char c) {
        return (c >= 'ぁ' && c <= 'ゖ') || c == 'ー' || c == '〜' || c == '～';
    }

    /**
     * 指定された文字がカタカナかどうかを判定します。長音「ー」や波ダッシュ「〜」も含みます。
     *
     * @param c 判定対象の文字
     * @return カタカナであれば true
     */
    public static boolean isKatakana(char c) {
        return (c >= 'ァ' && c <= 'ヶ') || c == 'ー' || c == '〜' || c == '～';
    }

    /**
     * 指定された文字がかな（ひらがなまたはカタカナ）かどうかを判定します。
     *
     * @param c 判定対象の文字
     * @return かなであれば true
     */
    public static boolean isKana(char c) {
        return isHiragana(c) || isKatakana(c);
    }

    /**
     * 文字列がひらがなのみで構成されているか判定します。
     *
     * @param s 判定対象の文字列
     * @return ひらがなのみであれば true
     */
    public static boolean isHiraganaOnly(String s) {
        if (s == null) return false;
        for (char c : s.toCharArray()) {
            if (!isHiragana(c)) {
                return false;
            }
        }
        return true;
    }

    /**
     * 文字列がかな（ひらがなまたはカタカナ）のみで構成されているか判定します。
     *
     * @param s 判定対象の文字列
     * @return かなのみであれば true
     */
    public static boolean isKanaOnly(String s) {
        if (s == null) return false;
        for (char c : s.toCharArray()) {
            if (!isHiragana(c) && !isKatakana(c)) {
                return false;
            }
        }
        return true;
    }

    /**
     * 文字列にカタカナが含まれているか判定します。
     *
     * @param s 判定対象の文字列
     * @return カタカナが含まれていれば true
     */
    public static boolean containsKatakana(String s) {
        if (s == null) return false;
        for (char c : s.toCharArray()) {
            if (c >= 'ァ' && c <= 'ヶ') {
                return true;
            }
        }
        return false;
    }

    /**
     * 指定された文字が日本語（かな、または漢字）かどうかを判定します。
     *
     * @param c 判定対象の文字
     * @return 日本語であれば true
     */
    public static boolean isJapanese(char c) {
        if (isKana(c)) return true;
        if (getKanjiGrade(c) != 0) return true;
        return false;
    }

    /**
     * 文字列の中に1文字でも日本語（かな、または漢字）が含まれているかを判定します。
     *
     * @param s 判定対象の文字列
     * @return 日本語が1文字でも含まれていれば true
     */
    public static boolean containsJapanese(String s) {
        if (s == null) return false;
        for (char c : s.toCharArray()) {
            if (isJapanese(c)) {
                return true;
            }
        }
        return false;
    }

    /**
     * 文字列が日本語（かな、または漢字）のみで構成されているか判定します。
     *
     * @param s 判定対象の文字列
     * @return 日本語のみであれば true
     */
    public static boolean isJapanese(String s) {
        if (s == null) return false;
        for (char c : s.toCharArray()) {
            if (!isJapanese(c)) {
                return false;
            }
        }
        return true;
    }

    /**
     * 指定された文字が SKK の読みとして有効（ひらがなまたは長音）か判定します。
     *
     * @param c 判定対象の文字
     * @return 有効な読み文字であれば true
     */
    public static boolean isValidReading(char c) {
        return (c >= 'ぁ' && c <= 'ゖ') || c == 'ー';
    }

    /**
     * 文字列が SKK の読みとして有効（ひらがなまたは長音のみ）か判定します。
     *
     * @param s 判定対象の文字列
     * @return 有効な読み文字列であれば true
     */
    public static boolean isValidReading(String s) {
        if (s == null) return false;
        for (char c : s.toCharArray()) {
            if (!isValidReading(c)) {
                return false;
            }
        }
        return true;
    }
}
