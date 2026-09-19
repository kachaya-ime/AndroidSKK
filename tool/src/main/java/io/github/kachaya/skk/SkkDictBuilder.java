package io.github.kachaya.skk;

import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import jdbm.RecordManager;
import jdbm.RecordManagerFactory;
import jdbm.btree.BTree;
import jdbm.helper.StringComparator;

/**
 * Sudachi 単語データから SKK 辞書形式（{@code .skk} テキスト辞書および JDBM B+Tree {@code .db} バイナリ辞書）を構築するビルダー。
 */
public class SkkDictBuilder {

    private static final String SYS_DIC_NAME = "skk_main_dict";
    private static final String DB_OUTPUT_DIR = "app/src/main/assets/";
    private static final String BTREE_NAME = "skk_dict";

    private final Path workPath;
    private final Map<String, List<SkkEntry>> skkCategoryMap = new LinkedHashMap<>();

    public SkkDictBuilder() {
        this(Paths.get("work"));
    }

    public SkkDictBuilder(Path workPath) {
        this.workPath = workPath;
    }

    public void buildOutput() throws IOException {
        dumpSkkDict();
        writeSkkDict();
        writeDbDict();
    }

    /**
     * 単語情報を受け取り、内部の SKK カテゴリマップに蓄積します。
     */
    public void processWord(String category, String surface, String reading, String pos1, String pos3, String type, int cost) {
        if (DictUtil.isKanaOnly(surface)) {
            return;
        }

        if ("*".equals(type)) {
            if ("接頭辞".equals(category)) {
                addSkkEntry(category, new SkkEntry(reading + ">", surface, cost));
            } else if ("接尾辞".equals(category)) {
                addSkkEntry(category, new SkkEntry(">" + reading, surface, cost));
            } else if ("助数詞可能".equals(pos3)) {
                addSkkEntry(category, new SkkEntry("#" + reading, "#1" + surface, cost));
                addSkkEntry(category, new SkkEntry("#" + reading, "#3" + surface, cost));
                addSkkEntry(category, new SkkEntry("#" + reading, "#2" + surface, cost));
                addSkkEntry(category, new SkkEntry("#" + reading, "#0" + surface, cost));
                addSkkEntry(category, new SkkEntry("#" + reading, "#4" + surface, cost));
                addSkkEntry(category, new SkkEntry("#" + reading, "#5" + surface, cost));
            } else if ("日英".equals(category)) {
                addSkkEntry(category, new SkkEntry(surface, DictUtil.toKatakana(reading), cost));
            } else {
                addSkkEntry(category, new SkkEntry(reading, surface, cost));
            }
            return;
        }

        // 形容詞
        if ("形容詞".equals(category)) {
            if (surface.endsWith("い") && reading.endsWith("い")) {
                String surfaceStem = surface.substring(0, surface.length() - 1);
                String readingStem = reading.substring(0, reading.length() - 1);
                addSkkEntry(category, new SkkEntry(readingStem + "i", surfaceStem, cost));
                addSkkEntry(category, new SkkEntry(readingStem + "k", surfaceStem, cost));
            }
            return;
        }

        // 動詞
        if ("動詞".equals(category)) {
            String[] ss = DictUtil.parseSurface(surface);
            if (ss != null) {
                List<SkkEntry> inflected = getInflectedEntries(type, surface, reading, cost);
                for (SkkEntry e : inflected) {
                    addSkkEntry(category, e);
                }
            }
        }
    }

    private void addSkkEntry(String category, SkkEntry skkEntry) {
        skkCategoryMap.computeIfAbsent(category, k -> new ArrayList<>()).add(skkEntry);
    }

    public void dumpSkkDict() throws IOException {
        for (Map.Entry<String, List<SkkEntry>> entry : skkCategoryMap.entrySet()) {
            String category = entry.getKey();
            Path outputPath = workPath.resolve(category + "_skk.tsv");
            writeSkkTsv(outputPath, entry.getValue());
        }
    }

    private void writeSkkTsv(Path path, List<SkkEntry> entries) throws IOException {
        System.out.println("Generating TSV: " + path.toAbsolutePath());
        entries.sort(Comparator
                .comparing((SkkEntry e) -> e.reading)
                .thenComparingInt(e -> e.cost)
                .thenComparing(e -> e.surface)
        );

        try (BufferedWriter writer = Files.newBufferedWriter(path, StandardCharsets.UTF_8)) {
            for (SkkEntry entry : entries) {
                writer.write(entry.toTsv() + "\n");
            }
        }
        System.out.println("Success. Output written to " + path.getFileName() + " (Total entries: " + entries.size() + ")");
    }

    private Map<String, List<String>> buildSortedCandidatesMap(List<SkkEntry> entries) {
        Map<String, Map<String, Integer>> readingToSurfaceCostMap = new TreeMap<>();
        for (SkkEntry entry : entries) {
            Map<String, Integer> surfaceCostMap = readingToSurfaceCostMap.computeIfAbsent(entry.reading, k -> new LinkedHashMap<>());
            surfaceCostMap.merge(entry.surface, entry.cost, Math::min);
        }

        Map<String, List<String>> resultMap = new TreeMap<>();
        for (Map.Entry<String, Map<String, Integer>> entry : readingToSurfaceCostMap.entrySet()) {
            String reading = entry.getKey();
            Map<String, Integer> surfaceCostMap = entry.getValue();

            List<Map.Entry<String, Integer>> candidateList = new ArrayList<>(surfaceCostMap.entrySet());
            if (reading.startsWith("#")) {
                String counterSurface = extractCounterSurface(candidateList);
                candidateList.sort((a, b) -> {
                    int pA = getNumericPriority(counterSurface, a.getKey());
                    int pB = getNumericPriority(counterSurface, b.getKey());
                    if (pA != pB) {
                        return Integer.compare(pA, pB);
                    }
                    return a.getKey().compareTo(b.getKey());
                });
            } else {
                candidateList.sort(Map.Entry.<String, Integer>comparingByValue().thenComparing(Map.Entry::getKey));
            }

            List<String> sortedSurfaces = new ArrayList<>();
            for (Map.Entry<String, Integer> cand : candidateList) {
                sortedSurfaces.add(cand.getKey());
            }
            resultMap.put(reading, sortedSurfaces);
        }
        return resultMap;
    }

    private static String extractCounterSurface(List<Map.Entry<String, Integer>> candidateList) {
        for (Map.Entry<String, Integer> entry : candidateList) {
            String key = entry.getKey();
            if (key != null && key.matches("^#[0-9].*")) {
                return key.substring(2);
            }
        }
        return "";
    }

    private static int getNumericPriority(String surface, String candidateKey) {
        if (candidateKey == null || !candidateKey.startsWith("#") || candidateKey.length() < 2) {
            return 99;
        }
        char typeChar = candidateKey.charAt(1);

        boolean isKatakanaOrLatin = (surface != null && (DictUtil.isKatakanaOnly(surface) || surface.matches("^[a-zA-Z]+$")));
        boolean isDateOrCurrency = (surface != null && (surface.contains("年") || surface.contains("月") || surface.contains("日") || surface.contains("円") || surface.contains("時") || surface.contains("分") || surface.contains("秒")));

        if (isKatakanaOrLatin) {
            // Katakana / Latin units (e.g. ページ, kg, ドル): Arabic numerals first, then mixed, then kanji
            switch (typeChar) {
                case '0': return 0; // 半角
                case '1': return 1; // 全角
                case '5': return 2; // 混合
                case '2': return 3; // 漢数字
                case '3': return 4; // 位取り
                case '4': return 5; // 旧字体
                default:  return 6;
            }
        } else if (isDateOrCurrency) {
            // Date / Currency (e.g. 年, 月, 日, 円): Half-width, positional kanji, simple kanji, full-width
            switch (typeChar) {
                case '0': return 0; // 半角
                case '3': return 1; // 位取りあり漢数字
                case '2': return 2; // 漢数字
                case '1': return 3; // 全角
                case '5': return 4; // 混合
                case '4': return 5; // 旧字体
                default:  return 6;
            }
        } else {
            // General Kanji / Other counters (e.g. 人, 冊, 本, 個): Half-width, simple kanji, positional kanji, full-width
            switch (typeChar) {
                case '0': return 0; // 半角
                case '2': return 1; // 漢数字
                case '3': return 2; // 位取りあり漢数字
                case '1': return 3; // 全角
                case '5': return 4; // 混合
                case '4': return 5; // 旧字体
                default:  return 6;
            }
        }
    }

    public void writeSkkDict() throws IOException {
        for (Map.Entry<String, List<SkkEntry>> map : skkCategoryMap.entrySet()) {
            String category = map.getKey();
            if (category.startsWith("_")) {
                continue;
            }
            List<SkkEntry> entries = map.getValue();
            Map<String, List<String>> readingMap = buildSortedCandidatesMap(entries);

            Path outputPath = workPath.resolve(category + ".skk");
            try (BufferedWriter writer = Files.newBufferedWriter(outputPath, StandardCharsets.UTF_8)) {
                for (Map.Entry<String, List<String>> entry : readingMap.entrySet()) {
                    String reading = entry.getKey();
                    List<String> surfaces = entry.getValue();
                    StringBuilder sb = new StringBuilder(reading);
                    sb.append(" /");
                    for (String surface : surfaces) {
                        sb.append(surface).append("/");
                    }
                    sb.append("\n");
                    writer.write(sb.toString());
                }
            }
        }
    }

    public void writeDbDict() throws IOException {
        List<SkkEntry> allEntries = new ArrayList<>();
        for (Map.Entry<String, List<SkkEntry>> entry : skkCategoryMap.entrySet()) {
            String category = entry.getKey();
            if (category.startsWith("_")) {
                continue;
            }
            allEntries.addAll(entry.getValue());
        }

        Map<String, List<String>> globalReadingMap = buildSortedCandidatesMap(allEntries);

        Files.createDirectories(workPath);

        Path txtPath = workPath.resolve(SYS_DIC_NAME + ".txt");
        System.out.println("Generating unified SKK text dict: " + txtPath.toAbsolutePath());

        String dbPath = DB_OUTPUT_DIR + SYS_DIC_NAME;
        Path dbPathObj = Paths.get(dbPath + ".db");
        if (dbPathObj.getParent() != null) {
            Files.createDirectories(dbPathObj.getParent());
        }
        Files.deleteIfExists(dbPathObj);
        Files.deleteIfExists(Paths.get(dbPath + ".lg"));

        System.out.println("Generating JDBM binary dict: " + dbPathObj.toAbsolutePath());

        RecordManager recman = RecordManagerFactory.createRecordManager(dbPath);
        BTree btree = BTree.createInstance(recman, new StringComparator());
        recman.setNamedObject(BTREE_NAME, btree.getRecid());

        try (BufferedWriter bw = Files.newBufferedWriter(txtPath, StandardCharsets.UTF_8)) {
            for (Map.Entry<String, List<String>> entry : globalReadingMap.entrySet()) {
                String key = entry.getKey();
                List<String> list = entry.getValue();
                StringBuilder sb = new StringBuilder();
                for (String value : list) {
                    sb.append("/").append(value);
                }
                sb.append("/");
                String formattedCandidates = sb.toString();

                btree.insert(key, formattedCandidates, true);
                bw.write(key + " " + formattedCandidates + "\n");
            }
        }

        recman.commit();
        recman.close();
        Files.deleteIfExists(Paths.get(dbPath + ".lg"));

        System.out.println("Success. Output written to " + SYS_DIC_NAME + ".db and " + SYS_DIC_NAME + ".txt (Total keys: " + globalReadingMap.size() + ")");
    }

    private static final Map<String, List<InflectionRule>> VERB_INFLECTION_TABLE = new LinkedHashMap<>();

    static {
        // 五段活用
        VERB_INFLECTION_TABLE.put("五段-カ行", List.of(new InflectionRule("く", 1, "き", "い")));
        VERB_INFLECTION_TABLE.put("五段-ガ行", List.of(new InflectionRule("ぐ", 1, "ぎ", "い")));
        VERB_INFLECTION_TABLE.put("五段-サ行", List.of(new InflectionRule("す", 1, "し")));
        VERB_INFLECTION_TABLE.put("五段-タ行", List.of(new InflectionRule("つ", 1, "ち")));
        VERB_INFLECTION_TABLE.put("五段-ナ行", List.of(new InflectionRule("ぬ", 1, "に")));
        VERB_INFLECTION_TABLE.put("五段-バ行", List.of(new InflectionRule("ぶ", 1, "び", "ん")));
        VERB_INFLECTION_TABLE.put("五段-マ行", List.of(new InflectionRule("む", 1, "み", "ん")));
        VERB_INFLECTION_TABLE.put("五段-ラ行", List.of(new InflectionRule("る", 1, "り", "ん", "た")));
        VERB_INFLECTION_TABLE.put("五段-ワア行", List.of(new InflectionRule("う", 1, "わ", "い", "う", "え", "お", "た")));

        // 上一段
        VERB_INFLECTION_TABLE.put("上一段-ア行", List.of(new InflectionRule("いる", 2, "い"), new InflectionRule("る", 1, "り", "や", "ん", "さ")));
        VERB_INFLECTION_TABLE.put("上一段-カ行", List.of(new InflectionRule("きる", 2, "き"), new InflectionRule("る", 1, "り", "や", "ん", "さ")));
        VERB_INFLECTION_TABLE.put("上一段-ガ行", List.of(new InflectionRule("ぎる", 2, "ぎ"), new InflectionRule("る", 1, "り", "や", "ん", "さ")));
        VERB_INFLECTION_TABLE.put("上一段-ザ行", List.of(new InflectionRule("じる", 2, "じ"), new InflectionRule("る", 1, "り", "や", "ん", "さ")));
        VERB_INFLECTION_TABLE.put("上一段-タ行", List.of(new InflectionRule("ちる", 2, "ち"), new InflectionRule("る", 1, "り", "や", "ん", "さ")));
        VERB_INFLECTION_TABLE.put("上一段-ナ行", List.of(new InflectionRule("にる", 2, "に"), new InflectionRule("る", 1, "り", "や", "ん", "さ")));
        VERB_INFLECTION_TABLE.put("上一段-ハ行", List.of(new InflectionRule("ひる", 2, "ひ"), new InflectionRule("る", 1, "り", "や", "ん", "さ")));
        VERB_INFLECTION_TABLE.put("上一段-バ行", List.of(new InflectionRule("びる", 2, "び"), new InflectionRule("る", 1, "り", "や", "ん", "さ")));
        VERB_INFLECTION_TABLE.put("上一段-マ行", List.of(new InflectionRule("みる", 2, "み"), new InflectionRule("る", 1, "り", "や", "ん", "さ")));
        VERB_INFLECTION_TABLE.put("上一段-ラ行", List.of(new InflectionRule("りる", 2, "り"), new InflectionRule("る", 1, "り", "や", "ん", "さ")));

        // 下一段
        VERB_INFLECTION_TABLE.put("下一段-ア行", List.of(new InflectionRule("える", 2, "え"), new InflectionRule("る", 1, "り", "や", "ん", "さ")));
        VERB_INFLECTION_TABLE.put("下一段-カ行", List.of(new InflectionRule("ける", 2, "け"), new InflectionRule("る", 1, "り", "や", "ん", "さ")));
        VERB_INFLECTION_TABLE.put("下一段-ガ行", List.of(new InflectionRule("げる", 2, "げ"), new InflectionRule("る", 1, "り", "や", "ん", "さ")));
        VERB_INFLECTION_TABLE.put("下一段-サ行", List.of(new InflectionRule("せる", 2, "せ"), new InflectionRule("る", 1, "り", "や", "ん", "さ")));
        VERB_INFLECTION_TABLE.put("下一段-ザ行", List.of(new InflectionRule("ぜる", 2, "ぜ"), new InflectionRule("る", 1, "り", "や", "ん", "さ")));
        VERB_INFLECTION_TABLE.put("下一段-タ行", List.of(new InflectionRule("てる", 2, "て"), new InflectionRule("る", 1, "り", "や", "ん", "さ")));
        VERB_INFLECTION_TABLE.put("下一段-ダ行", List.of(new InflectionRule("でる", 2, "で"), new InflectionRule("る", 1, "り", "や", "ん", "さ")));
        VERB_INFLECTION_TABLE.put("下一段-ナ行", List.of(new InflectionRule("ねる", 2, "ね"), new InflectionRule("る", 1, "り", "や", "ん", "さ")));
        VERB_INFLECTION_TABLE.put("下一段-ハ行", List.of(new InflectionRule("へる", 2, "へ"), new InflectionRule("る", 1, "り", "や", "ん", "さ")));
        VERB_INFLECTION_TABLE.put("下一段-バ行", List.of(new InflectionRule("べる", 2, "べ"), new InflectionRule("る", 1, "り", "や", "ん", "さ")));
        VERB_INFLECTION_TABLE.put("下一段-マ行", List.of(new InflectionRule("める", 2, "め"), new InflectionRule("る", 1, "り", "や", "ん", "さ")));
        VERB_INFLECTION_TABLE.put("下一段-ラ行", List.of(new InflectionRule("れる", 2, "れ"), new InflectionRule("る", 1, "り", "や", "ん", "さ")));

        // 変格活用
        List<InflectionRule> sahenSuruRules = List.of(
                new InflectionRule("する", 2, "し")
        );
        List<InflectionRule> sahenZuruRules = List.of(
                new InflectionRule("ずる", 2, "じ")
        );
        List<InflectionRule> sahenZeruRules = List.of(
                new InflectionRule("ぜる", 2, "じ")
        );
        List<InflectionRule> kahenRules = List.of(
                new InflectionRule("くる", 2, "き")
        );

        VERB_INFLECTION_TABLE.put("サ変-スル", sahenSuruRules);
        VERB_INFLECTION_TABLE.put("サ変-ズル", sahenZuruRules);
        VERB_INFLECTION_TABLE.put("サ変-エル", sahenZeruRules);
        VERB_INFLECTION_TABLE.put("サ行変格", sahenSuruRules);

        VERB_INFLECTION_TABLE.put("カ変", kahenRules);
        VERB_INFLECTION_TABLE.put("カ行変格", kahenRules);
    }

    public static List<SkkEntry> getInflectedEntries(String type, String surface, String reading) {
        return getInflectedEntries(type, surface, reading, 0);
    }

    public static List<SkkEntry> getInflectedEntries(String type, String surface, String reading, int cost) {
        List<SkkEntry> result = new ArrayList<>();
        if (type == null || surface == null || reading == null) {
            return result;
        }

        if (type.startsWith("カ変") || type.startsWith("カ行")) {
            if (surface.endsWith("来る") && reading.endsWith("くる")) {
                String surfaceStem = surface.substring(0, surface.length() - 2) + "来";
                String readingStem = reading.substring(0, reading.length() - 2);

                String[] kiSuffixes = {"c", "d", "m", "n", "s", "t", "y"};
                for (String s : kiSuffixes) {
                    result.add(new SkkEntry(readingStem + "き" + s, surfaceStem, cost));
                }

                String[] kuSuffixes = {"r", "n"};
                for (String s : kuSuffixes) {
                    result.add(new SkkEntry(readingStem + "く" + s, surfaceStem, cost));
                }

                String[] koSuffixes = {"i", "n", "r", "s", "y", "z"};
                for (String s : koSuffixes) {
                    result.add(new SkkEntry(readingStem + "こ" + s, surfaceStem, cost));
                }
            }
            return result;
        }

        List<InflectionRule> rules = VERB_INFLECTION_TABLE.get(type);
        if (rules == null) {
            if (type.startsWith("サ変-スル") || type.contains("サ変") || type.startsWith("サ行")) {
                rules = VERB_INFLECTION_TABLE.get("サ変-スル");
            } else if (type.startsWith("サ変-ズル")) {
                rules = VERB_INFLECTION_TABLE.get("サ変-ズル");
            } else if (type.startsWith("サ変-エル")) {
                rules = VERB_INFLECTION_TABLE.get("サ変-エル");
            }
        }
        if (rules != null) {
            for (InflectionRule rule : rules) {
                if (rule.suffixMatch == null || surface.endsWith(rule.suffixMatch)) {
                    if (surface.length() >= rule.dropCount && reading.length() >= rule.dropCount) {
                        String surfaceStem = surface.substring(0, surface.length() - rule.dropCount);
                        String readingStem = reading.substring(0, reading.length() - rule.dropCount);
                        for (String hiraganaSuffix : rule.okuriSuffixes) {
                            String skkSuffixesString = getRomajiHead(hiraganaSuffix);
                            if (skkSuffixesString != null) {
                                for (char c : skkSuffixesString.toCharArray()) {
                                    result.add(new SkkEntry(readingStem + c, surfaceStem, cost));
                                }
                            }
                        }
                    }
                }
            }
        }
        return result;
    }

    /**
     * ひらがな1文字から対応するローマ字頭文字（SKKの送り仮名用子音文字）へのマッピングテーブル。
     */
    private static final Map<String, String> ROMAJI_HEAD_MAP = new HashMap<>() {
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
            put("ぱ", "p"); put("ぴ", "p"); put("ぷ", "p"); put("ぺ", "p"); put("ぼ", "p");
        }
    };

    /**
     * ひらがな（文字列の先頭1文字）に対応するローマ字の頭文字（SKK の送り仮名用子音サフィックス）を取得します。
     *
     * @param hiragana 判定対象のひらがな
     * @return ローマ字の頭文字を表す文字列。マッピングが存在しない場合は null。
     */
    private static String getRomajiHead(String hiragana) {
        if (hiragana == null || hiragana.isEmpty()) return null;
        String key = hiragana.substring(0, 1);
        return ROMAJI_HEAD_MAP.get(key);
    }

    private static class InflectionRule {
        final String suffixMatch;
        final int dropCount;
        final String[] okuriSuffixes;

        InflectionRule(String suffixMatch, int dropCount, String... okuriSuffixes) {
            this.suffixMatch = suffixMatch;
            this.dropCount = dropCount;
            this.okuriSuffixes = okuriSuffixes;
        }
    }

    public static class SkkEntry {
        public String reading;
        public String surface;
        public int cost;

        public SkkEntry(String reading, String surface) {
            this(reading, surface, 0);
        }

        public SkkEntry(String reading, String surface, int cost) {
            this.reading = reading;
            this.surface = surface;
            this.cost = cost;
        }

        public String toTsv() {
            return String.join("\t", reading, surface, String.valueOf(cost));
        }
    }
}
