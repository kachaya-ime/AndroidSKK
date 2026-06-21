package io.github.kachaya.skk;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

/**
 * Sudachi 辞書（CSV形式）を解析し、品詞ごとに分類した SKK 辞書形式（.skk）および
 * デバッグ用の TSV 形式に変換するツールクラスです。
 * <p>
 * このコンバーターは以下の特徴を持ちます：
 * <ul>
 *   <li>送り仮名を自動判定し、SKK の「送りあり（okuri-ari）」形式を生成</li>
 *   <li>助数詞に対して SKK 標準の数値変換テンプレート（#0〜#3）を付与</li>
 *   <li>接頭辞・接尾辞への SKK 特有のマーカー（&gt;）の付与</li>
 *   <li>正規化表記（Normalized Form）を利用した不適切な送り仮名エントリの除外</li>
 *   <li>異体字や旧字体など、漢字の表記揺れの許容</li>
 * </ul>
 * </p>
 */
public class SudachiDictConverter {
    /**
     * コンバーターのエントリーポイント。
     *
     * @param args コマンドライン引数（現在は使用しません）
     */
    public static void main(String[] args) {
        try {
            SudachiDictConverter converter = new SudachiDictConverter();
            converter.convert();
        } catch (IOException e) {
            System.err.println("Error converting Sudachi dictionary: " + e.getMessage());
            e.printStackTrace();
        }
    }

    /** 読み込む Sudachi 辞書ファイルのリスト。data ディレクトリに配置されている必要があります。 */
    private static final List<String> SUDACHI_DIC_FILES = Arrays.asList(
            "core_lex.csv",
            "small_lex.csv"
    );

    /**
     * Sudachi 辞書の 1 行（エントリ）を保持するデータモデルクラスです。
     */
    private static class SudachiEntry {
        /** 左文脈 ID */
        public int lid;
        /** 右文脈 ID */
        public int rid;
        /** 単語生起コスト（低いほど優先度が高い） */
        public int cost;
        /** 表層形（実際の表記） */
        public String surface;
        /** 品詞分類 1（大分類） */
        public String pos1;
        /** 品詞分類 2（中分類） */
        public String pos2;
        /** 品詞分類 3（小分類） */
        public String pos3;
        /** 品詞分類 4（細分類） */
        public String pos4;
        /** 活用型（五段、形容詞など） */
        public String type;
        /** 活用形（終止形、連用形など） */
        public String form;
        /** 読み（カタカナ） */
        public String reading;
        /** 正規化表記（送り仮名の揺れを統一した標準的な表記） */
        public String normalizedForm;
        /** 分割型 */
        public String splitType;

        /** ひらがなに変換された読み */
        public String hiragana;
        /** 解析前の元の CSV 行文字列 */
        public String rawLine;

        SudachiEntry() {
        }

        /**
         * CSV の 1 行をパースしてフィールドに格納します。
         *
         * @param line 解析対象の行
         */
        void parse(String line) {
            this.rawLine = line;
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
            reading = unescape(cols[11]);
            normalizedForm = unescape(cols[12]);
            splitType = cols[14];
            hiragana = DictUtil.toHiragana(reading);
        }

        /**
         * 送り仮名が適切かどうかをチェックします。
         * <p>
         * 正規化表記と送り仮名のパターン（漢字を除いた末尾の仮名部分）が一致する場合、
         * 漢字の表記揺れ（異体字など）として許容し、変換候補として採用します。
         * </p>
         *
         * @return 標準的、または許容される送り仮名であれば true
         */
        boolean isStandardOkurigana() {
            if (normalizedForm.equals(surface)) return true;
            String okuriSurface = extractOkurigana(surface);
            String okuriNorm = extractOkurigana(normalizedForm);
            return !okuriNorm.isEmpty() && okuriSurface.equals(okuriNorm);
        }

        /**
         * 文字列の末尾から連続する仮名（送り仮名部分）を抽出します。
         *
         * @param s 対象文字列
         * @return 抽出された送り仮名、見つからない場合は空文字
         */
        private String extractOkurigana(String s) {
            int lastNonKana = -1;
            for (int i = 0; i < s.length(); i++) {
                if (!DictUtil.isKana(s.charAt(i))) {
                    lastNonKana = i;
                }
            }
            if (lastNonKana == -1) return "";
            return s.substring(lastNonKana + 1);
        }
    }

    /**
     * Sudachi 辞書特有のエスケープ文字を復元します。
     *
     * @param s エスケープされた文字列
     * @return 復元後の文字列
     */
    private static String unescape(String s) {
        if (s == null) return null;
        return s.replace("\\u0022", "\"")
                .replace("\\u0028", "(")
                .replace("\\u0029", ")")
                .replace("\\u002f", "/")
                .replace("\\u002F", "/")
                .replace("\\u002c", ",")
                .replace("\\u002C", ",");
    }

    // 品詞ごとに分類されたエントリリスト
    private final List<SudachiEntry> nounEntries = new ArrayList<>();
    private final List<SudachiEntry> verbalNounEntries = new ArrayList<>();
    private final List<SudachiEntry> adverbialNounEntries = new ArrayList<>();
    private final List<SudachiEntry> personEntries = new ArrayList<>();
    private final List<SudachiEntry> placeEntries = new ArrayList<>();
    private final List<SudachiEntry> stationEntries = new ArrayList<>();
    private final List<SudachiEntry> organizationEntries = new ArrayList<>();
    private final List<SudachiEntry> properNounEntries = new ArrayList<>();
    private final List<SudachiEntry> numeralEntries = new ArrayList<>();
    private final List<SudachiEntry> pronounEntries = new ArrayList<>();
    private final List<SudachiEntry> verbEntries = new ArrayList<>();
    private final List<SudachiEntry> adjectiveEntries = new ArrayList<>();
    private final List<SudachiEntry> adjectivalNounEntries = new ArrayList<>();
    private final List<SudachiEntry> adnominalEntries = new ArrayList<>();
    private final List<SudachiEntry> adverbEntries = new ArrayList<>();
    private final List<SudachiEntry> conjunctionEntries = new ArrayList<>();
    private final List<SudachiEntry> interjectionEntries = new ArrayList<>();
    private final List<SudachiEntry> particleEntries = new ArrayList<>();
    private final List<SudachiEntry> auxiliaryVerbEntries = new ArrayList<>();
    private final List<SudachiEntry> prefixEntries = new ArrayList<>();
    private final List<SudachiEntry> suffixEntries = new ArrayList<>();
    private final List<SudachiEntry> verbSuffixEntries = new ArrayList<>();
    private final List<SudachiEntry> adjectivalSuffixEntries = new ArrayList<>();
    private final List<SudachiEntry> symbolEntries = new ArrayList<>();
    private final List<SudachiEntry> punctuationEntries = new ArrayList<>();
    private final List<SudachiEntry> alphanumericEntries = new ArrayList<>();
    private final List<SudachiEntry> counterEntries = new ArrayList<>();
    private final List<SudachiEntry> costZeroEntries = new ArrayList<>();
    private final List<SudachiEntry> uninflectedEntries = new ArrayList<>();
    private final List<SudachiEntry> inflectedEntries = new ArrayList<>();
    private final List<String> mismatchLogs = new ArrayList<>();

    /**
     * 変換処理のメインフローを実行します。
     * CSV ファイルの読み込み、解析、分類、および各形式（TSV/SKK）でのファイル出力を行います。
     *
     * @throws IOException ファイルアクセスエラー時にスローされます
     */
    public void convert() throws IOException {
        System.out.println("SudachiDictConverter is running.");

        Path dataPath = Paths.get("./data");
        Path workPath = Paths.get("./work");
        if (!Files.exists(workPath)) {
            Files.createDirectories(workPath);
        }

        Path outputPath = workPath.resolve("skip-lex.csv");

        try (BufferedWriter writer = Files.newBufferedWriter(outputPath, StandardCharsets.UTF_8)) {
            for (String fileName : SUDACHI_DIC_FILES) {
                Path path = dataPath.resolve(fileName);
                if (!Files.exists(path)) {
                    System.err.println("Skip: File not found: " + path);
                    continue;
                }

                System.out.println("Processing: " + fileName);
                try (BufferedReader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
                    String line;
                    while ((line = reader.readLine()) != null) {
                        if (line.isEmpty() || line.startsWith("#")) {
                            continue;
                        }
                        SudachiEntry entry = new SudachiEntry();
                        entry.parse(line);

                        // 変換対象外の除外設定
                        if (entry.pos1.equals("空白")) continue; ;
                        if (entry.pos2.equals("ＡＡ")) continue;
                        if (entry.pos2.equals("フィラー")) continue;
//                        if (entry.lid != entry.rid) continue;
                        if (!entry.splitType.equals("A")) continue;

                        // 表記（surface）に半角スペースが含まれるものは除外する
                        if (entry.surface.contains(" ")) continue;

                        // 英単語表記（大文字・小文字・数字のみ）を除外
                        if (entry.surface.matches("^[a-zA-Z0-9]+$")) continue;

                        // 品詞体系に基づいた分類
                        switch (entry.pos1) {
                            case "接頭辞" -> prefixEntries.add(entry);
                            case "接尾辞" -> {
                                if (entry.pos2.equals("動詞的")) {
                                    verbSuffixEntries.add(entry);
                                } else if (entry.pos2.equals("形容詞的")) {
                                    adjectivalSuffixEntries.add(entry);
                                } else if (entry.pos2.equals("名詞的") && entry.pos3.equals("助数詞")) {
                                    counterEntries.add(entry);
                                } else {
                                    suffixEntries.add(entry);
                                }
                            }
                            case "動詞" -> verbEntries.add(entry);
                            case "形容詞" -> adjectiveEntries.add(entry);
                            case "形状詞" -> adjectivalNounEntries.add(entry);
                            case "連体詞" -> adnominalEntries.add(entry);
                            case "副詞" -> adverbEntries.add(entry);
                            case "接続詞" -> conjunctionEntries.add(entry);
                            case "感動詞" -> interjectionEntries.add(entry);
                            case "助詞" -> particleEntries.add(entry);
                            case "代名詞" -> pronounEntries.add(entry);
                            case "助動詞" -> auxiliaryVerbEntries.add(entry);
                            case "記号" -> symbolEntries.add(entry);
                            case "補助記号" -> punctuationEntries.add(entry);
                            case "名詞" -> processNoun(entry);
                            default -> {
                                if (entry.type.equals("*")) {
                                    uninflectedEntries.add(entry);
                                } else {
                                    inflectedEntries.add(entry);
                                }
                            }
                        }
                    }
                } catch (IOException e) {
                    System.err.println("Error processing " + path + ": " + e.getMessage());
                }
            }
        }

        // TSV 形式の書き出し (デバッグ用)
        writeTsv(workPath.resolve("名詞.tsv"), nounEntries);
        writeTsv(workPath.resolve("サ変名詞.tsv"), verbalNounEntries);
        writeTsv(workPath.resolve("副詞可能名詞.tsv"), adverbialNounEntries);
        writeTsv(workPath.resolve("人名.tsv"), personEntries);
        writeTsv(workPath.resolve("地名.tsv"), placeEntries);
        writeTsv(workPath.resolve("駅名.tsv"), stationEntries);
        writeTsv(workPath.resolve("組織名.tsv"), organizationEntries);
        writeTsv(workPath.resolve("固有名詞.tsv"), properNounEntries);
        writeTsv(workPath.resolve("数詞.tsv"), numeralEntries);
        writeTsv(workPath.resolve("代名詞.tsv"), pronounEntries);
        writeTsv(workPath.resolve("動詞.tsv"), verbEntries);
        writeTsv(workPath.resolve("形容詞.tsv"), adjectiveEntries);
        writeTsv(workPath.resolve("形状詞.tsv"), adjectivalNounEntries);
        writeTsv(workPath.resolve("連体詞.tsv"), adnominalEntries);
        writeTsv(workPath.resolve("副詞.tsv"), adverbEntries);
        writeTsv(workPath.resolve("接続詞.tsv"), conjunctionEntries);
        writeTsv(workPath.resolve("感動詞.tsv"), interjectionEntries);
        writeTsv(workPath.resolve("助詞.tsv"), particleEntries);
        writeTsv(workPath.resolve("助動詞.tsv"), auxiliaryVerbEntries);
        writeTsv(workPath.resolve("接頭辞.tsv"), prefixEntries);
        writeTsv(workPath.resolve("接尾辞.tsv"), suffixEntries);
        writeTsv(workPath.resolve("動詞的接尾辞.tsv"), verbSuffixEntries);
        writeTsv(workPath.resolve("形容詞的接尾辞.tsv"), adjectivalSuffixEntries);
        writeTsv(workPath.resolve("助数詞.tsv"), counterEntries);
        writeTsv(workPath.resolve("記号.tsv"), symbolEntries);
        writeTsv(workPath.resolve("補助記号.tsv"), punctuationEntries);
        writeTsv(workPath.resolve("英数字.tsv"), alphanumericEntries);
        writeTsv(workPath.resolve("コストゼロ.tsv"), costZeroEntries);
        writeTsv(workPath.resolve("非活用語.tsv"), uninflectedEntries);
        writeTsv(workPath.resolve("活用語.tsv"), inflectedEntries);

        // SKK 形式の書き出し
        writeSkk(workPath.resolve("名詞.skk"), nounEntries);
        writeSkk(workPath.resolve("サ変名詞.skk"), verbalNounEntries);
        writeSkk(workPath.resolve("副詞可能名詞.skk"), adverbialNounEntries);
        writeSkk(workPath.resolve("人名.skk"), personEntries);
        writeSkk(workPath.resolve("地名.skk"), placeEntries);
        writeSkk(workPath.resolve("駅名.skk"), stationEntries);
        writeSkk(workPath.resolve("組織名.skk"), organizationEntries);
        writeSkk(workPath.resolve("固有名詞.skk"), properNounEntries);
        writeSkk(workPath.resolve("数詞.skk"), numeralEntries);
        writeSkk(workPath.resolve("代名詞.skk"), pronounEntries);
        writeSkk(workPath.resolve("動詞.skk"), verbEntries);
        writeSkk(workPath.resolve("形容詞.skk"), adjectiveEntries);
        writeSkk(workPath.resolve("形状詞.skk"), adjectivalNounEntries);
        writeSkk(workPath.resolve("連体詞.skk"), adnominalEntries);
        writeSkk(workPath.resolve("副詞.skk"), adverbEntries);
        writeSkk(workPath.resolve("接続詞.skk"), conjunctionEntries);
        writeSkk(workPath.resolve("感動詞.skk"), interjectionEntries);
        writeSkk(workPath.resolve("助詞.skk"), particleEntries);
        writeSkk(workPath.resolve("助動詞.skk"), auxiliaryVerbEntries);
        writeSkk(workPath.resolve("接頭辞.skk"), prefixEntries);
        writeSkk(workPath.resolve("接尾辞.skk"), suffixEntries);
        writeSkk(workPath.resolve("動詞的接尾辞.skk"), verbSuffixEntries);
        writeSkk(workPath.resolve("形容詞制接尾辞.skk"), adjectivalSuffixEntries);
        writeSkk(workPath.resolve("助数詞.skk"), counterEntries);
        writeSkk(workPath.resolve("記号.skk"), symbolEntries);
        writeSkk(workPath.resolve("補助記号.skk"), punctuationEntries);
        writeSkk(workPath.resolve("英数字.skk"), alphanumericEntries);
        writeSkk(workPath.resolve("非活用語.skk"), uninflectedEntries);
        writeSkk(workPath.resolve("活用語.skk"), inflectedEntries);

        // 不整合ログの出力
        if (!mismatchLogs.isEmpty()) {
            Path mismatchPath = workPath.resolve("mismatch.log");
            System.out.println("Writing mismatch logs to: " + mismatchPath.toAbsolutePath());
            Files.write(mismatchPath, mismatchLogs, StandardCharsets.UTF_8);
        }
    }

    /**
     * 指定されたエントリリストを SKK 辞書形式でファイルに書き出します。
     * 送りあり・送りなしの自動分類を行います。
     *
     * @param path    出力先のパス
     * @param entries 変換対象のエントリリスト
     * @throws IOException ファイルアクセスエラー時にスローされます
     */
    private void writeSkk(Path path, List<SudachiEntry> entries) throws IOException {
        System.out.println("Generating SKK dictionary: " + path.toAbsolutePath());
        Map<String, List<SudachiEntry>> okuriAri = new TreeMap<>();
        Map<String, List<SudachiEntry>> okuriNasi = new TreeMap<>();

        for (SudachiEntry entry : entries) {
            if (!entry.type.equals("*")) {
                addOkuriAri(entry, okuriAri, okuriNasi);
            } else {
                addOkuriNasi(entry, okuriNasi);
            }
        }

        try (BufferedWriter writer = Files.newBufferedWriter(path, StandardCharsets.UTF_8)) {
            writer.write(";; okuri-ari entries.\n");
            writeEntries(writer, okuriAri, true);
            writer.write(";; okuri-nasi entries.\n");
            writeEntries(writer, okuriNasi, false);
        }
        System.out.println("Success. Output written to " + path.getFileName());
    }

    /**
     * 活用語を送りあり（okuri-ari）セクション用のマップに追加します。
     * <p>
     * 読みの末尾から送り仮名を除いた「読みの語幹」に、
     * 送り仮名 1 文字目のローマ字識別子（例: 'k'）を付加したものをキーとします。
     * </p>
     */
    private void addOkuriAri(SudachiEntry entry, Map<String, List<SudachiEntry>> okuriAri, Map<String, List<SudachiEntry>> okuriNasi) {
        String surface = entry.surface;
        String hiragana = entry.hiragana;

        // 送り仮名が非標準的なもの（揺れや間違い）を除外
        if (!entry.isStandardOkurigana()) {
            mismatchLogs.add(entry.rawLine);
            return;
        }

        int lastNonKana = -1;
        for (int i = 0; i < surface.length(); i++) {
            if (!DictUtil.isKana(surface.charAt(i))) {
                lastNonKana = i;
            }
        }

        if (lastNonKana == -1) {
            addOkuriNasi(entry, okuriNasi);
            return;
        }

        int okuriStart = lastNonKana + 1;
        if (okuriStart >= surface.length()) {
            addOkuriNasi(entry, okuriNasi);
            return;
        }

        String okuriGana = surface.substring(okuriStart);
        char firstOkuri = okuriGana.charAt(0);
        char[] okuriChars = getOkuriChars(firstOkuri);

        if (hiragana.endsWith(okuriGana)) {
            String readingStem = hiragana.substring(0, hiragana.length() - okuriGana.length());
            for (char okuriChar : okuriChars) {
                String key = readingStem + okuriChar;
                if (entry.pos1.equals("接頭辞")) {
                    key = key + ">";
                } else if (entry.pos1.equals("接尾辞")) {
                    key = ">" + key;
                }
                okuriAri.computeIfAbsent(key, k -> new ArrayList<>()).add(entry);
            }
        } else {
            // 読みが送り仮名で終わっていない（読みと送りが矛盾している）場合は除外
            mismatchLogs.add(entry.rawLine);
        }
    }

    /**
     * 名詞や非活用語を送りなし（okuri-nasi）セクション用のマップに追加します。
     * 助数詞の場合は読みの先頭に '#' を付与します。
     */
    private void addOkuriNasi(SudachiEntry entry, Map<String, List<SudachiEntry>> okuriNasi) {
        // 表記と読みが同じ「ひらがなのみ」の単語はスキップ
        if (entry.surface.equals(entry.hiragana)) return;

        // 表記と読みが同じ「カタカナのみ」の単語について
        if (entry.surface.equals(DictUtil.toKatakana(entry.hiragana))) {
            // 固有名詞（人名・地名など）以外はスキップ
            if (!entry.pos2.equals("固有名詞")) return;
        }

        String key = entry.hiragana;
        if (entry.pos3.equals("助数詞") || entry.pos3.equals("助数詞可能")) {
            key = "#" + key;
        } else if (entry.pos1.equals("接頭辞")) {
            key = key + ">";
        } else if (entry.pos1.equals("接尾辞")) {
            key = ">" + key;
        }

        okuriNasi.computeIfAbsent(key, k -> new ArrayList<>()).add(entry);
    }

    /**
     * ひらがな 1 文字に対応する SKK の送り識別子（ローマ字）を取得します。
     * 「ふ」→「h, f」のように、複数のローマ字表記がある場合はすべて返します。
     */
    private char[] getOkuriChars(char c) {
        return switch (c) {
            case 'あ' -> new char[]{'a'}; case 'い' -> new char[]{'i'}; case 'う' -> new char[]{'u'}; case 'え' -> new char[]{'e'}; case 'お' -> new char[]{'o'};
            case 'か', 'き', 'く', 'け', 'こ' -> new char[]{'k'};
            case 'さ', 'し', 'す', 'せ', 'そ' -> new char[]{'s'};
            case 'た', 'ち', 'つ', 'て', 'と' -> new char[]{'t'};
            case 'な', 'に', 'ぬ', 'ね', 'の' -> new char[]{'n'};
            case 'は', 'ひ', 'へ', 'ほ' -> new char[]{'h'};
            case 'ふ' -> new char[]{'h', 'f'};
            case 'ま', 'み', 'む', 'め', 'も' -> new char[]{'m'};
            case 'や', 'ゆ', 'よ' -> new char[]{'y'};
            case 'ら', 'り', 'る', 'れ', 'ろ' -> new char[]{'r'};
            case 'わ' -> new char[]{'w'}; case 'を' -> new char[]{'w'};
            case 'ん' -> new char[]{'n'};
            case 'が', 'ぎ', 'ぐ', 'げ', 'ご' -> new char[]{'g'};
            case 'ざ', 'ず', 'ぜ', 'ぞ' -> new char[]{'z'};
            case 'じ' -> new char[]{'z', 'j'};
            case 'だ', 'ぢ', 'づ', 'で', 'ど' -> new char[]{'d'};
            case 'ば', 'び', 'ぶ', 'べ', 'ぼ' -> new char[]{'b'};
            case 'ぱ', 'ぴ', 'ぷ', 'ぺ', 'ぽ' -> new char[]{'p'};
            case 'ぁ', 'ぃ', 'ぅ', 'ぇ', 'ぉ' -> getOkuriChars((char) (c + 1));
            case 'ゃ', 'ゅ', 'ょ' -> new char[]{'y'};
            case 'っ' -> new char[]{'t'};
            default -> new char[]{Character.toLowerCase(c)};
        };
    }

    /**
     * マップに蓄積された候補群を SKK 辞書形式で書き出します。
     * 助数詞（#）に対するテンプレート生成や、スラッシュのエスケープ処理を行います。
     */
    private void writeEntries(BufferedWriter writer, Map<String, List<SudachiEntry>> map, boolean okuriAri) throws IOException {
        for (Map.Entry<String, List<SudachiEntry>> entry : map.entrySet()) {
            String reading = entry.getKey();
            List<SudachiEntry> candidates = entry.getValue();

            // コスト順（頻度順）にソート
            candidates.sort(Comparator.comparingInt(e -> e.cost));

            StringBuilder sb = new StringBuilder();
            sb.append(reading).append(" /");

            boolean isNumeric = reading.startsWith("#");
            Set<String> seen = new HashSet<>();
            for (SudachiEntry e : candidates) {
                String candidateText;
                if (okuriAri) {
                    // 送りありの場合、表層形から送り仮名部分を削除した漢字部分のみを候補とする
                    int lastNonKana = -1;
                    for (int i = 0; i < e.surface.length(); i++) {
                        if (!DictUtil.isKana(e.surface.charAt(i))) {
                            lastNonKana = i;
                        }
                    }
                    candidateText = e.surface.substring(0, lastNonKana + 1);
                } else {
                    candidateText = e.surface;
                }

                if (isNumeric) {
                    // 数値変換用のテンプレートを追加 (#0〜#3)
                    // 表記に漢字が含まれるかどうかで優先順序を調整
                    boolean hasKanji = false;
                    for (char c : candidateText.toCharArray()) {
                        if (DictUtil.getKanjiGrade(c) != 0 || (c >= '\u4e00' && c <= '\u9faf')) {
                            hasKanji = true;
                            break;
                        }
                    }

                    int[] order = hasKanji ? new int[]{0, 3, 2, 1} : new int[]{0, 1, 3, 2};
                    for (int i : order) {
                        String numericCandidate = "#" + i + candidateText;
                        numericCandidate = escapeCandidate(numericCandidate);
                        if (seen.add(numericCandidate)) {
                            sb.append(numericCandidate).append("/");
                        }
                    }
                } else {
                    candidateText = escapeCandidate(candidateText);
                    if (seen.add(candidateText)) {
                        sb.append(candidateText).append("/");
                    }
                }
            }
            sb.append("\n");
            writer.write(sb.toString());
        }
    }

    /**
     * 候補文字列内に含まれる記号（スラッシュ、セミコロン）を SKK 互換形式でエスケープします。
     *
     * @param s エスケープ対象の文字列
     * @return エスケープ済み文字列（Lisp concat 形式、または元の文字列）
     */
    private String escapeCandidate(String s) {
        if (s.contains("/") || s.contains(";")) {
            String escaped = s.replace("\"", "\\\"")
                    .replace("/", "\\057")
                    .replace(";", "\\073");
            return "(concat \"" + escaped + "\")";
        }
        return s;
    }

    /**
     * 名詞エントリをさらに詳細な分類（人名、地名、駅名、組織、サ変名詞など）へ振り分けます。
     *
     * @param entry 解析済みの Sudachi エントリ
     */
    private void processNoun(SudachiEntry entry) {
        if (entry.pos2.equals("固有名詞")) {
            switch (entry.pos3) {
                case "人名" -> personEntries.add(entry);
                case "地名" -> {
                    if (entry.pos4.equals("駅")) {
                        stationEntries.add(entry);
                    } else {
                        placeEntries.add(entry);
                    }
                }
                case "組織" -> organizationEntries.add(entry);
                default -> properNounEntries.add(entry);
            }
        } else if (entry.pos2.equals("数詞")) {
            numeralEntries.add(entry);
        } else if (entry.pos2.equals("普通名詞")) {
            switch (entry.pos3) {
                case "サ変可能" -> verbalNounEntries.add(entry);
                case "副詞可能" -> adverbialNounEntries.add(entry);
                case "助数詞可能" -> counterEntries.add(entry);
                default -> {
                    if (entry.surface.matches("^[a-zA-Z0-9]+$")) {
                        alphanumericEntries.add(entry);
                    } else {
                        nounEntries.add(entry);
                    }
                }
            }
        } else {
            nounEntries.add(entry);
        }
    }

    /**
     * エントリリストをデバッグ用の TSV 形式でファイルに書き出します。
     *
     * @param path    出力先のパス
     * @param entries 対象のエントリリスト
     * @throws IOException ファイルアクセスエラー時にスローされます
     */
    private void writeTsv(Path path, List<SudachiEntry> entries) throws IOException {
        System.out.println("Generating TSV: " + path.toAbsolutePath());
        // 読み、正規化表記、活用型、表層形の順でソート
        entries.sort(Comparator
                .comparing((SudachiEntry e) -> e.hiragana)
                .thenComparing(e -> e.normalizedForm)
                .thenComparing(e -> e.type)
                .thenComparing(e -> e.form)
                .thenComparing(e -> e.surface)
        );

        try (BufferedWriter writer = Files.newBufferedWriter(path, StandardCharsets.UTF_8)) {
            for (SudachiEntry entry : entries) {
                String pos = String.join(",", entry.pos1, entry.pos2, entry.pos3, entry.pos4, entry.type, entry.form);
                String tsv = String.join("\t", entry.hiragana, entry.surface, entry.normalizedForm, "" + entry.cost, pos);
                writer.write(tsv + "\n");
            }
        }
        System.out.println("Success. Output written to " + path.getFileName() + " (Total entries: " + entries.size() + ")");
    }
}
