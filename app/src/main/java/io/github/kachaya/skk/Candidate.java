package io.github.kachaya.skk;

import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * SKK の変換候補 1 つを表現するデータモデルクラスです。
 * <p>
 * 数値変換（#0〜#5）を含むテンプレートの解決、および
 * SKK 辞書特有のエスケープシーケンスのデコードを担当します。
 * </p>
 */
public class Candidate {
    /** 漢数字（#2, #3 用）。 */
    private static final String[] KANJI_NUMBERS = {"〇", "一", "二", "三", "四", "五", "六", "七", "八", "九"};
    /** 漢数字・旧字体（#4 用）。 */
    private static final String[] KANJI_NUMBERS_OLD = {"零", "壱", "弐", "参", "肆", "伍", "陸", "漆", "捌", "玖"};
    /** 十、百、千の単位（#3 用）。 */
    private static final String[] UNITS = {"", "十", "百", "千"};
    /** 十、百、千の単位・旧字体（#4 用）。 */
    private static final String[] UNITS_OLD = {"", "拾", "百", "阡"};
    /** 万以上の大きな位の単位。 */
    private static final String[] BIG_UNITS = {"", "万", "億", "兆", "京"};
    /** 数値テンプレート（#0〜#9）にマッチする正規表現パターン。 */
    private static final Pattern PAT_NUM_TEMPLATE = Pattern.compile("#([0-9])");
    /** SKK 辞書内のエスケープシーケンス（\073 などの8進数表現）を抽出するためのパターン。 */
    private static final Pattern PAT_ESCAPE_NUM = Pattern.compile("\\\\([0-9]+)");
    /** 辞書に登録されている未加工の候補文字列. アノテーション等を含みます。 */
    public String rawCandidate;
    /** 実際にユーザーに提示・確定される候補文字列。数値変換等が解決済みです。 */
    public String candidate;
    /** 候補に付随する注釈（アノテーション）。存在しない場合は null です。 */
    public String annotation;
    /** ユーザー辞書由来の候補かどうか。 */
    public boolean isUserDict;
    /** 動的に生成された（日時情報など、学習・削除が不要な）候補かどうか。 */
    public boolean isDynamic = false;

    /**
     * 新しい変換候補オブジェクトを構築します。
     *
     * @param rawCandidate 生の辞書データ（アノテーション等を含む）
     * @param template     表示・確定用のテンプレート文字列（未デコード）
     * @param annotation   候補の注釈（未デコード、存在しない場合は null）
     * @param actualNums   見出し語から抽出された数値のリスト
     * @param isUserDict   ユーザー辞書由来かどうか
     */
    Candidate(String rawCandidate, String template, String annotation, List<String> actualNums, boolean isUserDict) {
        this.rawCandidate = rawCandidate;
        this.isUserDict = isUserDict;

        // エスケープ解除とテンプレート解決
        String decodedTemplate = unescape(template);
        this.annotation = (annotation != null) ? unescape(annotation) : null;

        if (actualNums != null && !actualNums.isEmpty()) {
            this.candidate = resolveNumericTemplate(decodedTemplate, actualNums);
        } else {
            this.candidate = decodedTemplate;
        }
    }

    /**
     * 動的な（学習不要な）候補としてフラグを設定します。
     *
     * @param dynamic 動体候補なら true
     * @return このインスタンス自身
     */
    public Candidate setDynamic(boolean dynamic) {
        this.isDynamic = dynamic;
        return this;
    }

    /**
     * SKK 辞書形式特有のエスケープ（Lisp の (concat ...) や 8進数文字表現）をデコードします。
     *
     * @param text デコード対象の文字列
     * @return デコード後の文字列
     */
    private String unescape(String text) {
        if (text == null) return null;
        int len = text.length();
        if (len < 12) {
            return text;
        }
        if (text.charAt(0) != '(' || !text.startsWith("(concat \"") || !text.endsWith("\")")) {
            return text;
        }

        // "(concat \"" と "\")" を取り除く
        String inner = text.substring(9, len - 2);
        Matcher m = PAT_ESCAPE_NUM.matcher(inner);
        StringBuffer sb = new StringBuffer();
        while (m.find()) {
            try {
                int num = Integer.parseInt(m.group(1), 8); // 8進数としてパース
                m.appendReplacement(sb, Matcher.quoteReplacement(Character.toString((char) num)));
            } catch (NumberFormatException e) {
                m.appendReplacement(sb, Matcher.quoteReplacement(m.group()));
            }
        }
        m.appendTail(sb);
        return sb.toString();
    }

    /**
     * テンプレート内の #n を見出し語から抽出された数値リストの各値で置換します。
     *
     * @param template 置換対象のテンプレート
     * @param nums     置換に使用する数値のリスト
     * @return 解決後の文字列
     */
    private String resolveNumericTemplate(String template, List<String> nums) {
        Matcher m = PAT_NUM_TEMPLATE.matcher(template);
        StringBuffer sb = new StringBuffer();
        int count = 0;

        while (m.find()) {
            if (count < nums.size()) {
                int type = Integer.parseInt(m.group(1));
                String resolved = convertNum(nums.get(count), type);
                m.appendReplacement(sb, Matcher.quoteReplacement(resolved));
                count++;
            } else {
                m.appendReplacement(sb, m.group());
            }
        }
        m.appendTail(sb);
        return sb.toString();
    }

    /**
     * 指定されたタイプ（#0〜#5）に基づいて数値文字列を変換します。
     *
     * @param numStr 変換元の数値文字列
     * @param type   変換タイプ（0: そのまま, 1: 全角, 2: 漢数字, 3: 位取りあり, 4: 旧字体, 5: 混合）
     * @return 変換後の文字列
     */
    private String convertNum(String numStr, int type) {
        switch (type) {
            case 1: // 全角
                return RomajiConverter.toWideLatin(numStr).toString();
            case 2: // 漢数字 (一二三)
                return convertToKanji(numStr);
            case 3: // 位取りあり漢数字 (百二十三)
            case 4: // 位取りあり漢数字・旧字体 (百弐拾参)
            case 5: // 混合 (1億2345万6789)
                return convertWithUnits(numStr, type);
            case 0: // そのまま
            default:
                return numStr;
        }
    }

    /**
     * 数値文字列を単純な漢数字（一二三）に変換します。
     *
     * @param numStr 変換元の数値文字列
     * @return 変換後の文字列
     */
    private String convertToKanji(String numStr) {
        StringBuilder sb = new StringBuilder();
        for (char c : numStr.toCharArray()) {
            if (Character.isDigit(c)) {
                sb.append(KANJI_NUMBERS[c - '0']);
            } else {
                sb.append(c);
            }
        }
        return sb.toString();
    }

    /**
     * 位取りありの変換（タイプ 3, 4, 5）を一括で処理します。
     *
     * @param numStr 変換元の数値文字列
     * @param type   変換タイプ
     * @return 変換後の文字列
     */
    private String convertWithUnits(String numStr, int type) {
        try {
            long val = Long.parseLong(numStr);
            if (val == 0) {
                return (type == 4) ? "零" : (type == 5 ? "0" : "〇");
            }

            boolean old = (type == 4);
            StringBuilder res = new StringBuilder();
            int bigUnitIdx = 0;

            while (val > 0) {
                int part = (int) (val % 10000);
                if (part > 0) {
                    StringBuilder partSb = new StringBuilder();
                    if (type == 5) {
                        partSb.append(part);
                    } else {
                        for (int i = 0; i < 4; i++) {
                            int d = part % 10;
                            if (d > 0) {
                                String unit = old ? UNITS_OLD[i] : UNITS[i];
                                String digit = (d == 1 && i > 0) ? "" : (old ? KANJI_NUMBERS_OLD[d] : KANJI_NUMBERS[d]);
                                partSb.insert(0, digit + unit);
                            }
                            part /= 10;
                        }
                    }
                    res.insert(0, partSb + BIG_UNITS[bigUnitIdx]);
                }
                val /= 10000;
                bigUnitIdx++;
            }
            return res.toString();
        } catch (NumberFormatException e) {
            return numStr;
        }
    }
}
