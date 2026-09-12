package io.github.kachaya.skk;

import com.worksap.nlp.sudachi.Config;
import com.worksap.nlp.sudachi.Dictionary;
import com.worksap.nlp.sudachi.DictionaryFactory;
import com.worksap.nlp.sudachi.JapaneseDictionary;
import com.worksap.nlp.sudachi.dictionary.Grammar;
import com.worksap.nlp.sudachi.dictionary.Lexicon;
import com.worksap.nlp.sudachi.dictionary.POS;
import com.worksap.nlp.sudachi.dictionary.WordInfo;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Sudachi バイナリ辞書（{@code system_full.dic}）をロードし、
 * 単語抽出・フィルタリング・品詞カテゴリ分類を行って直接 SKK 辞書ビルダーへ受け渡すクラス。
 */
public class SudachiDictLoader {

    private final Path dataPath;
    private final Path workPath;

    public SudachiDictLoader() throws IOException {
        this(Paths.get("data"), Paths.get("work"));
    }

    public SudachiDictLoader(Path dataPath, Path workPath) throws IOException {
        this.dataPath = dataPath;
        this.workPath = workPath;
        Files.createDirectories(workPath);
    }

    /**
     * Sudachi バイナリ辞書（{@code data/system_full.dic}）を解析し、抽出した単語を直接 {@link SkkDictBuilder} に処理させます。
     *
     * @param skkBuilder SKK 辞書ビルダー
     * @throws IOException 辞書ファイルの読み込み中にエラーが発生した場合
     */
    public void load(SkkDictBuilder skkBuilder) throws IOException {
        Path dicPath = dataPath.resolve("system_full.dic");
        if (!Files.exists(dicPath)) {
            throw new FileNotFoundException("Sudachi binary dictionary not found: " + dicPath);
        }

        Path leftIdDefPath = dataPath.resolve("left-id.def");
        Map<String, Integer> posToIdMap = loadPosToIdMap(leftIdDefPath);

        Config config = Config.defaultConfig().systemDictionary(dicPath);
        Path skippedLogPath = workPath.resolve("skipped_words.tsv");
        System.out.println("Writing skipped words log to: " + skippedLogPath.toAbsolutePath());

        List<TempEntry> tempEntries = new ArrayList<>();
        Map<String, Map<Integer, Integer>> posCostFreqMap = new HashMap<>();
        Map<String, Integer> posMaxCostMap = new HashMap<>();

        try (Dictionary rawDictionary = new DictionaryFactory().create(config);
             BufferedWriter skippedWriter = Files.newBufferedWriter(skippedLogPath, StandardCharsets.UTF_8)) {

            if (!(rawDictionary instanceof JapaneseDictionary japaneseDictionary)) {
                throw new IllegalStateException("Failed to access JapaneseDictionary.");
            }
            Lexicon lexicon = japaneseDictionary.getLexicon();
            Grammar grammar = japaneseDictionary.getGrammar();

            int wordCount = lexicon.size();
            System.out.println("Total words in binary: " + wordCount);

            // 1-pass目: 単語の抽出、カテゴリ判定、コストが0以外の品詞ごとの出現頻度・最大値の集計
            for (int wordId = 0; wordId < wordCount; wordId++) {
                WordInfo wordInfo = lexicon.getWordInfo(wordId);
                int[] aunitSplit = wordInfo.getAunitSplit();
                boolean isAUnit = (aunitSplit == null || aunitSplit.length == 0 || (aunitSplit.length == 1 && aunitSplit[0] == wordId));
                if (!isAUnit) {
                    continue; // 複合語（B・C単位）は除外
                }

                short posId = wordInfo.getPOSId();
                if (posId == -1) {
                    continue;
                }
                POS pos = grammar.getPartOfSpeechString(posId);
                if (pos == null) {
                    continue;
                }

                String surface = wordInfo.getSurface();
                String reading = DictUtil.toHiragana(wordInfo.getReadingForm());
                String normalizedForm = wordInfo.getNormalizedForm();
                int leftId = lexicon.getLeftId(wordId);
                int rightId = lexicon.getRightId(wordId);

                String posKey = getPosKey(pos);
                if (leftId == -1 || rightId == -1) {
                    Integer idFromPos = posToIdMap.get(posKey);
                    if (idFromPos != null) {
                        if (leftId == -1) {
                            leftId = idFromPos;
                        }
                        if (rightId == -1) {
                            rightId = idFromPos;
                        }
                    }
                }

                int cost = lexicon.getCost(wordId);

                String pos1 = pos.get(0); // 大分類
                String pos2 = pos.get(1); // 中分類
                String type = pos.get(4); // 活用型

                if (type.startsWith("文語")) {
                    continue;
                }
                if ("感動詞".equals(pos1)) {
                    continue;
                }
                if ("ＡＡ".equals(pos2)) {
                    continue;
                }
                if (!DictUtil.isHiraganaOnly(reading)) {
                    writeSkippedLog(skippedWriter, "invalid_reading", wordId, wordInfo, pos, leftId, rightId, cost, reading);
                    continue;
                }

                String category = getCategory(surface, reading, normalizedForm, pos);
                if (category != null) {
                    if (cost != 0) {
                        posCostFreqMap.computeIfAbsent(posKey, k -> new HashMap<>())
                                .merge(cost, 1, Integer::sum);
                        posMaxCostMap.merge(posKey, cost, Math::max);
                    }
                    tempEntries.add(new TempEntry(pos, posKey, surface, reading, cost, category));
                } else {
                    writeSkippedLog(skippedWriter, "invalid_category", wordId, wordInfo, pos, leftId, rightId, cost, reading);
                }
            }

            // 品詞ごとの最頻値（もっともよく現れるコスト）の算出
            Map<String, Integer> posModeCostMap = new HashMap<>();
            for (Map.Entry<String, Map<Integer, Integer>> entry : posCostFreqMap.entrySet()) {
                String key = entry.getKey();
                Map<Integer, Integer> freqMap = entry.getValue();
                int modeCost = 0;
                int maxCount = -1;
                for (Map.Entry<Integer, Integer> costEntry : freqMap.entrySet()) {
                    int costVal = costEntry.getKey();
                    int count = costEntry.getValue();
                    if (count > maxCount) {
                        maxCount = count;
                        modeCost = costVal;
                    } else if (count == maxCount && costVal < modeCost) {
                        modeCost = costVal;
                    }
                }
                posModeCostMap.put(key, modeCost);
            }

            // 2-pass目: コスト補完および最頻値コストを持つエントリの最大値+1への書き換えを行いつつ SkkDictBuilder に渡す
            for (TempEntry entry : tempEntries) {
                int finalCost = entry.rawCost;
                Integer modeCost = posModeCostMap.get(entry.posKey);
                Integer maxCost = posMaxCostMap.get(entry.posKey);

                if (finalCost == 0) {
                    finalCost = (modeCost != null) ? modeCost : 30000;
                }

                if (modeCost != null && maxCost != null && finalCost == modeCost) {
                    finalCost = maxCost;
                }

                skkBuilder.processWord(entry.category, entry.surface, entry.reading, entry.pos1, entry.pos3, entry.type, finalCost);
            }
        }
    }

    /**
     * 単語の品詞情報や表記・読みの特徴から、カテゴリ名を決定します。
     */
    public String getCategory(String surface, String reading, String normalizedForm, POS pos) {
        String pos1 = pos.get(0);
        String pos2 = pos.get(1);
        String pos3 = pos.get(2);
        String pos4 = pos.get(3);

        if ("記号".equals(pos1)) {
            if (DictUtil.isSingleKanji(surface)) return "記号";
            return null;
        }

        if ("補助記号".equals(pos1)) {
            if (!"一般".equals(pos2)) return null;
            if (surface.equals(normalizedForm)) return null;
            if (DictUtil.isJapaneseTextOnly(surface)) return null;
            return "補助記号";
        }

        if (DictUtil.isLowerAlphabetOnly(surface)) {
            if ("固有名詞".equals(pos2)) return null;
            if (!DictUtil.toKatakana(reading).equals(normalizedForm)) return null;
            return "日英";
        }

        if (!(DictUtil.isJapaneseOnly(surface) && DictUtil.isJapaneseOnly(normalizedForm))) {
            return null;
        }

        if (!DictUtil.matchesChoonCount(reading, surface)) {
            return null;
        }

        if (!(DictUtil.matchesReading(surface, reading))) {
            return null;
        }

        switch (pos1) {
            case "名詞" -> {
                switch (pos2) {
                    case "数詞" -> {
                        if (DictUtil.isKanjiOnly(surface)) return "数詞";
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
                if (DictUtil.isKanjiOnly(surface)) return "接頭辞";
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

    private void writeSkippedLog(BufferedWriter writer, String reason, int wordId, WordInfo wordInfo, POS pos, int leftId, int rightId, int cost, String reading) throws IOException {
        String surface = (wordInfo != null && wordInfo.getSurface() != null) ? wordInfo.getSurface() : "";
        String readingStr = (reading != null) ? reading : ((wordInfo != null && wordInfo.getReadingForm() != null) ? wordInfo.getReadingForm() : "");
        String posStr = (pos != null) ? String.join(",", pos) : "";

        writer.write(reason + "\t" + wordId + "\t" + surface + "\t" + readingStr + "\t" + posStr + "\t" + leftId + "\t" + rightId + "\t" + cost);
        writer.newLine();
    }

    /**
     * left-id.def から POS（品詞6要素）から ID へのマップを読み込みます。
     * 6番目以降の要素は無視し、最初に見つかった ID を使用します。
     *
     * @param path left-id.def ファイルのパス
     * @return POS文字列をキー、IDを値とするマップ
     * @throws IOException ファイル読み込みエラーが発生した場合
     */
    public Map<String, Integer> loadPosToIdMap(Path path) throws IOException {
        Map<String, Integer> posToIdMap = new LinkedHashMap<>();
        if (!Files.exists(path)) {
            System.out.println("Warning: " + path + " not found. Skipping POS to ID mapping.");
            return posToIdMap;
        }

        try (BufferedReader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
            String line;
            while ((line = reader.readLine()) != null) {
                line = line.trim();
                if (line.isEmpty() || line.startsWith("#")) {
                    continue;
                }
                String[] parts = line.split("\\s+", 2);
                if (parts.length < 2) {
                    continue;
                }
                try {
                    int id = Integer.parseInt(parts[0]);
                    String[] posTokens = parts[1].split(",");
                    int count = Math.min(posTokens.length, 6);
                    List<String> firstSix = Arrays.asList(posTokens).subList(0, count);
                    String posKey = String.join(",", firstSix);
                    posToIdMap.putIfAbsent(posKey, id);
                } catch (NumberFormatException e) {
                    // 数値に変換できないIDの行はスキップ
                }
            }
        }
        return posToIdMap;
    }

    private String getPosKey(POS pos) {
        if (pos == null) {
            return "";
        }
        int count = Math.min(pos.size(), 6);
        List<String> list = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            list.add(pos.get(i));
        }
        return String.join(",", list);
    }

    private static class TempEntry {
        final POS pos;
        final String posKey;
        final String surface;
        final String reading;
        final int rawCost;
        final String category;
        final String pos1;
        final String pos3;
        final String type;

        TempEntry(POS pos, String posKey, String surface, String reading,
                  int rawCost, String category) {
            this.pos = pos;
            this.posKey = posKey;
            this.surface = surface;
            this.reading = reading;
            this.rawCost = rawCost;
            this.category = category;
            this.pos1 = (pos != null && !pos.isEmpty()) ? pos.get(0) : "";
            this.pos3 = (pos != null && pos.size() > 2) ? pos.get(2) : "";
            this.type = (pos != null && pos.size() > 4) ? pos.get(4) : "*";
        }
    }
}
