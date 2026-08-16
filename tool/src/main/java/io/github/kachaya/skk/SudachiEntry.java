package io.github.kachaya.skk;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * Sudachi 辞書の 1 行（エントリ）を保持し、解析を行うデータモデルクラスです。
 * Sudachi 辞書の各カラムの保持、バリデーション、カテゴリ分類、および TSV 形式への変換を担当します。
 */
public class SudachiEntry {
    private static final Pattern UNICODE_PATTERN = Pattern.compile("\\\\u([0-9a-fA-F]{4})");

    /** 左文脈 ID */
    public int lid;
    /** 右文脈 ID */
    public int rid;
    /** 単語生起コスト（低いほど優先度が高い） */
    public int cost;
    /** 表層形（実際に出現する表記） */
    public String surface;
    /** 品詞分類 1（大分類。例: 名詞, 動詞） */
    public String pos1;
    /** 品詞分類 2（中分類。例: 普通名詞, 数詞, 固有名詞） */
    public String pos2;
    /** 品詞分類 3（小分類。例: 人名, 地名） */
    public String pos3;
    /** 品詞分類 4（細分類） */
    public String pos4;
    /** 活用型（例: 五段-カ行, 上一段-カ行, *） */
    public String type;
    /** 活用形（例: 終止形-一般, 連用形-一般, *） */
    public String form;
    /** 読み（カタカナからひらがなへ正規化済み） */
    public String reading;
    /** 正規化表記（送り仮名の揺れなどを統一した標準的な表記） */
    public String normalizedForm;
    /** 辞書引きの際の分割型（A, B, C） */
    public String splitType;

    /**
     * SudachiEntry の新しいインスタンスを構築します。
     */
    public SudachiEntry() {
    }

    /**
     * Sudachi 辞書特有のエスケープ文字を復元します。
     *
     * @param s エスケープされた文字列
     * @return 復元後の文字列
     */
    private static String unescape(String s) {
        if (s == null) {
            return null;
        }
        return UNICODE_PATTERN.matcher(s).replaceAll(match -> {
            int codePoint = Integer.parseInt(match.group(1), 16);
            return String.valueOf((char) codePoint);
        });
    }

    /**
     * CSV の 1 行をパースしてフィールドに格納します。
     *
     * @param line 解析対象の行
     */
    public void parse(String line) {
        String[] cols = line.split(",");
        if (cols.length < 15) {
            return;
        }
        lid = Integer.parseInt(cols[1]);
        rid = Integer.parseInt(cols[2]);
        cost = Integer.parseInt(cols[3]);
        surface = unescape(cols[4]);
        pos1 = cols[5];
        pos2 = cols[6];
        pos3 = cols[7];
        pos4 = cols[8];
        type = cols[9];
        form = cols[10];
        reading = DictUtil.toHiragana(unescape(cols[11]));
        normalizedForm = unescape(cols[12]);
        splitType = cols[14];
    }

    /**
     * このエントリが基本的なフィルタ（分割型 A、文語以外、感動詞以外など）を通過するか判定します。
     *
     * @return 変換対象として有効な場合は true
     */
    public boolean isValidBase() {
        if (!"A".equals(splitType)) return false;
        if (type.startsWith("文語")) return false;
        if ("感動詞".equals(pos1)) return false;
        if ("ＡＡ".equals(pos2)) return false;
        return true;
    }

    /**
     * 読みがひらがな（および長音）のみで構成されているか判定します。
     *
     * @return ひらがなのみの場合は true
     */
    public boolean isReadingHiraganaOnly() {
        return DictUtil.isHiraganaOnly(reading);
    }

    /**
     * 表記が仮名（ひらがな・カタカナ・長音）のみで構成されているか判定します。
     *
     * @return 仮名のみの場合は true
     */
    public boolean isSurfaceKanaOnly() {
        return DictUtil.isKanaOnly(surface);
    }

    /**
     * 表記および正規化表記が日本語文字（漢字・ひらがな・カタカナ・長音・〆）のみで構成されているか判定します。
     *
     * @return 日本語文字のみの場合は true
     */
    public boolean isSurfaceJapaneseOnly() {
        return DictUtil.isJapaneseOnly(surface) && DictUtil.isJapaneseOnly(normalizedForm);
    }

    /**
     * 読みと表記の長音符（ー）の有無が一致しているか判定します。
     *
     * @return 一致している場合は true
     */
    public boolean hasConsistentLongVowels() {
        return reading.contains("ー") == surface.contains("ー");
    }

    /**
     * 表記から生成した正規表現に対して、読みが妥当であるか判定します。
     * 表記内の漢字などをワイルドカードとして扱います。
     *
     * @return 妥当な組み合わせの場合は true
     */
    public boolean matchesReadingRegex() {
        return DictUtil.matchesReading(surface, reading);
    }

    /**
     * このエントリの品詞情報から、変換用のカテゴリ名を決定します。
     *
     * @return カテゴリ名。スキップ対象の場合は null。
     */
    public String getCategory() {
        if ("記号".equals(pos1)) {
            if (surface.length() == 1 && surface.matches("^[\\p{IsHan}]$")) return "記号";
            return null;
        }
        if ("補助記号".equals(pos1)) {
            if (!"一般".equals(pos2)) return null;
            if (surface.equals(normalizedForm)) return null;
            if (surface.matches("^[\\p{IsHan}\\p{IsKatakana}\\p{IsHiragana}ー]+$")) return null;
            return "補助記号";
        }

        // 英小文字表記
        if (surface.matches("^[a-z]+$")) {
            if ("固有名詞".equals(pos2)) return null;
            if (!DictUtil.toKatakana(reading).equals(normalizedForm)) return null;
            return "日英";
        }

        if (!isSurfaceJapaneseOnly()) return null;
        if (!hasConsistentLongVowels()) return null;
        if (!matchesReadingRegex()) return null;

        switch (pos1) {
            case "名詞" -> {
                switch (pos2) {
                    case "数詞" -> {
                        if (surface.matches("^[\\p{IsHan}]+$")) return "数詞";
                    }
                    case "固有名詞" -> {
                        switch (pos3) {
                            case "人名" -> {
                                if (!"一般".equals(pos4)) return "人名";
                            }
                            case "地名" -> {
                                return "地名";
                            }
                            case "一般" -> {
                                return "固有名詞";
                            }
                        }
                    }
                    case "普通名詞" -> {
                        return "普通名詞";
                    }
                }
            }
            case "接頭辞" -> {
                if (surface.matches("^[\\p{IsHan}]+$")) return "接頭辞";
            }
            case "動詞" -> {
                return "動詞";
            }
            default -> {
                return pos1;
            }
        }
        return null;
    }

    /**
     * デバッグ用 TSV 出力のための品詞情報文字列（カンマ区切り）を生成します。
     *
     * @return 品詞情報文字列
     */
    public String getPosString() {
        return String.join(",", pos1, pos2, pos3, pos4, type, form);
    }

    /**
     * デバッグ用 TSV 出力のための 1 行を生成します。
     *
     * @return TSV 文字列
     */
    public String toTsv() {
        return String.join("\t",
                reading,
                surface,
                getPosString(),
                String.valueOf(cost),
                normalizedForm
        );
    }

    /** 活用型をキーとした動詞活用ルールのテーブル。 */
    private static final Map<String, List<InflectionRule>> VERB_INFLECTION_TABLE = new LinkedHashMap<>();

    static {
        // 五段活用
        VERB_INFLECTION_TABLE.put("五段-カ行", List.of(new InflectionRule(null, 1, "き", "い")));
        VERB_INFLECTION_TABLE.put("五段-ガ行", List.of(new InflectionRule(null, 1, "ぎ", "い")));
        VERB_INFLECTION_TABLE.put("五段-サ行", List.of(new InflectionRule(null, 1, "し")));
        VERB_INFLECTION_TABLE.put("五段-タ行", List.of(new InflectionRule(null, 1, "ち")));
        VERB_INFLECTION_TABLE.put("五段-ナ行", List.of(new InflectionRule(null, 1, "に")));
        VERB_INFLECTION_TABLE.put("五段-バ行", List.of(new InflectionRule(null, 1, "び", "ん")));
        VERB_INFLECTION_TABLE.put("五段-マ行", List.of(new InflectionRule(null, 1, "み", "ん")));
        VERB_INFLECTION_TABLE.put("五段-ラ行", List.of(new InflectionRule("る", 1, "り", "ん", "た")));
        VERB_INFLECTION_TABLE.put("五段-ワア行", List.of(new InflectionRule(null, 1, "わ", "い", "う", "え", "お", "た")));

        // 一段活用（共通のフォールバックルール）
        InflectionRule ichidanElse = new InflectionRule(null, 1, "り", "や", "ん");

        // 上一段
        VERB_INFLECTION_TABLE.put("上一段-ア行", List.of(new InflectionRule("いる", 2, "い"), ichidanElse));
        VERB_INFLECTION_TABLE.put("上一段-カ行", List.of(new InflectionRule("きる", 2, "き"), ichidanElse));
        VERB_INFLECTION_TABLE.put("上一段-ガ行", List.of(new InflectionRule("ぎる", 2, "ぎ"), ichidanElse));
        VERB_INFLECTION_TABLE.put("上一段-ザ行", List.of(new InflectionRule("じる", 2, "じ"), ichidanElse));
        VERB_INFLECTION_TABLE.put("上一段-タ行", List.of(new InflectionRule("ちる", 2, "ち"), ichidanElse));
        VERB_INFLECTION_TABLE.put("上一段-ナ行", List.of(new InflectionRule("にる", 2, "に"), ichidanElse));
        VERB_INFLECTION_TABLE.put("上一段-ハ行", List.of(new InflectionRule("ひる", 2, "ひ"), ichidanElse));
        VERB_INFLECTION_TABLE.put("上一段-バ行", List.of(new InflectionRule("びる", 2, "び"), ichidanElse));
        VERB_INFLECTION_TABLE.put("上一段-マ行", List.of(new InflectionRule("みる", 2, "み"), ichidanElse));
        VERB_INFLECTION_TABLE.put("上一段-ラ行", List.of(new InflectionRule("りる", 2, "り"), ichidanElse));

        // 下一段
        VERB_INFLECTION_TABLE.put("下一段-ア行", List.of(new InflectionRule("える", 2, "え"), ichidanElse));
        VERB_INFLECTION_TABLE.put("下一段-カ行", List.of(new InflectionRule("ける", 2, "け"), ichidanElse));
        VERB_INFLECTION_TABLE.put("下一段-ガ行", List.of(new InflectionRule("げる", 2, "げ"), ichidanElse));
        VERB_INFLECTION_TABLE.put("下一段-サ行", List.of(new InflectionRule("せる", 2, "せ"), ichidanElse));
        VERB_INFLECTION_TABLE.put("下一段-ザ行", List.of(new InflectionRule("ぜる", 2, "ぜ"), ichidanElse));
        VERB_INFLECTION_TABLE.put("下一段-タ行", List.of(new InflectionRule("てる", 2, "て"), ichidanElse));
        VERB_INFLECTION_TABLE.put("下一段-ダ行", List.of(new InflectionRule("でる", 2, "で"), ichidanElse));
        VERB_INFLECTION_TABLE.put("下一段-ナ行", List.of(new InflectionRule("ねる", 2, "ね"), ichidanElse));
        VERB_INFLECTION_TABLE.put("下一段-ハ行", List.of(new InflectionRule("へる", 2, "へ"), ichidanElse));
        VERB_INFLECTION_TABLE.put("下一段-バ行", List.of(new InflectionRule("べる", 2, "べ"), ichidanElse));
        VERB_INFLECTION_TABLE.put("下一段-マ行", List.of(new InflectionRule("める", 2, "め"), ichidanElse));
        VERB_INFLECTION_TABLE.put("下一段-ラ行", List.of(new InflectionRule("れる", 2, "れ"), ichidanElse));

        // 変格活用
        VERB_INFLECTION_TABLE.put("カ行変格", List.of(
                new InflectionRule("くる", 2, "き"),
                ichidanElse
        ));
        VERB_INFLECTION_TABLE.put("サ行変格", List.of(
                new InflectionRule("する", 2, "し"),
                new InflectionRule("ずる", 2, "じ"),
                ichidanElse
        ));
    }

    /**
     * このエントリの活用型に基づき、SKK 辞書用のエントリ（送りあり）を生成します。
     * 内部で保持するひらがなベースの活用ルールを、{@link DictUtil#getRomajiHead(String)} を用いて
     * SKK 固有の英字サフィックスに変換します。
     *
     * @return SKK エントリのリスト。活用ルールがない場合は空リスト。
     */
    public List<SkkEntry> getInflectedEntries() {
        List<SkkEntry> result = new ArrayList<>();
        List<InflectionRule> rules = VERB_INFLECTION_TABLE.get(this.type);
        if (rules != null) {
            for (InflectionRule rule : rules) {
                if (rule.suffixMatch == null || surface.endsWith(rule.suffixMatch)) {
                    String surfaceStem = surface.substring(0, surface.length() - rule.dropCount);
                    String readingStem = reading.substring(0, reading.length() - rule.dropCount);
                    for (String hiraganaSuffix : rule.okuriSuffixes) {
                        String skkSuffixesString = DictUtil.getRomajiHead(hiraganaSuffix);
                        if (skkSuffixesString != null) {
                            for (char c : skkSuffixesString.toCharArray()) {
                                result.add(new SkkEntry(readingStem + c, surfaceStem));
                            }
                        }
                    }
                    break;
                }
            }
        }
        return result;
    }

    /**
     * 動詞の活用ルールを定義する内部クラスです。
     */
    private static class InflectionRule {
        /** マッチ条件となる末尾の文字列（null の場合は無条件マッチ） */
        final String suffixMatch;
        /** 末尾から削る文字数 */
        final int dropCount;
        /** 活用の起点となるひらがなサフィックスのリスト（例: "か", "き"） */
        final String[] okuriSuffixes;

        /**
         * InflectionRule の新しいインスタンスを構築します。
         *
         * @param suffixMatch    マッチ条件となる末尾の文字列
         * @param dropCount      末尾から削る文字数
         * @param okuriSuffixes  活用の起点となるひらがなサフィックスのリスト
         */
        InflectionRule(String suffixMatch, int dropCount, String... okuriSuffixes) {
            this.suffixMatch = suffixMatch;
            this.dropCount = dropCount;
            this.okuriSuffixes = okuriSuffixes;
        }
    }
}
