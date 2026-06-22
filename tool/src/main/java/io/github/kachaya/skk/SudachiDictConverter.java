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
 *   <li>Sudachi の活用型（五段、一段等）に基づき、SKK の「送りあり（okuri-ari）」エントリを機械的に生成</li>
 *   <li>原則として「終止形」のみを登録対象とし、撥音便などの活用形や不適切な送り仮名の混入を防止</li>
 *   <li>助数詞に対して SKK 標準の数値変換テンプレート（#0〜#3）を付与</li>
 *   <li>接頭辞・接尾辞への SKK 特有のマーカー（&gt;）の付与</li>
 *   <li>正規化表記（Normalized Form）を利用しつつ、「憂う」のような読みの異なる語彙バリエーションを許容</li>
 *   <li>異体字、旧字体、歴史的仮名遣いなど、日本語特有の表記揺れを柔軟に考慮したマッチング</li>
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
         * 歴史的仮名遣い（旧仮名）や、小書き文字を使わない古い表記（「頰つぺた」など）も
         * 許容範囲として扱います。
         * </p>
         *
         * @return 標準的、または許容される送り仮名であれば true
         */
        boolean isStandardOkurigana() {
            if (normalizedForm.equals(surface)) return true;

            // 表記を正規化（カタカナ・旧仮名・小書き文字・波ダッシュ・ヶ を統一）して比較
            String hSurface = normalizeForConsistency(surface);
            String hNorm = normalizeForConsistency(normalizedForm);

            if (hNorm.equals(hSurface)) return true;

            // 読み（hiragana）が正規化表記の送り仮名で終わっていない場合、
            // それは単なる送り仮名の揺れではなく、別の語彙や活用（例：「憂う」vs「憂える」）
            // である可能性が高いため、正規の語彙として許容する。
            // 逆に、読みが一致したまま送り仮名だけが異なる（例：「行う」vs「行なう」）場合は、
            // 送り方の揺れとみなして、正規化表記側を優先し除外する。
            String okuriNorm = extractOkurigana(normalizedForm);
            if (!okuriNorm.isEmpty() && !hiragana.endsWith(okuriNorm)) {
                String kSurface = surface.replaceAll("[ぁ-ゖァ-ヺー〜～]+", "");
                String kNorm = normalizedForm.replaceAll("[ぁ-ゖァ-ヺー〜～]+", "");
                if (!kSurface.isEmpty() && kSurface.equals(kNorm)) return true;
            }

            String okuriSurface = extractOkurigana(surface);
            if (okuriNorm.isEmpty()) return false;

            // 送り仮名部分についても同様に正規化して比較
            String s1 = normalizeForConsistency(okuriSurface);
            String s2 = normalizeForConsistency(okuriNorm);
            return s1.equals(s2);
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

                        // 表記（surface）に半角スペースや句読点が含まれるものは除外する
                        // （"." は略称などで使われるため、ここでは除外対象から外す）
                        if (entry.surface.contains(" ") || entry.surface.contains("、") ||
                                entry.surface.contains("。") || entry.surface.contains(",")) {
                            continue;
                        }

                        // 英単語表記（大文字・小文字・数字のみ）を除外
                        if (entry.surface.matches("^[a-zA-Z0-9]+$")) continue;

                        // 表記に含まれる仮名や記号が、読みの中に正しい順序で現れるかチェックする
                        // （「北國」は仮名がないのでパス、「発しん」は「しん」が含まれるのでパス、
                        //   「ボー然」は「ぼー」が「ぼうぜん」に含まれないので除外）
                        if (!isKanaConsistent(entry.surface, entry.hiragana)) {
                            continue;
                        }

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

        // 活用語の場合、原則として終止形のみを登録対象とする
        // （撥音便などの活用形や、送り仮名の過剰なバリエーションを排除するため）
        if (entry.form != null && !entry.form.startsWith("終止形")) {
            return;
        }

        // 送り仮名が非標準的なもの（揺れや間違い）を除外
        if (!entry.isStandardOkurigana()) {
            mismatchLogs.add(entry.rawLine);
            return;
        }

        // 表記と読みが一致するものは、送りありエントリとしては登録しない
        if (surface.equals(hiragana) || surface.equals(DictUtil.toKatakana(hiragana))) {
            return;
        }

        // 活用型に基づいた機械的生成
        char[] okuriChars = getOkuriCharsFromType(entry.type, hiragana);
        if (okuriChars != null && hiragana.length() > 1) {
            String readingStem = hiragana.substring(0, hiragana.length() - 1);
            for (char okuriChar : okuriChars) {
                String key = readingStem + okuriChar;
                if (entry.pos1.equals("接頭辞")) {
                    key = key + ">";
                } else if (entry.pos1.equals("接尾辞")) {
                    key = ">" + key;
                }
                okuriAri.computeIfAbsent(key, k -> new ArrayList<>()).add(entry);
            }
            return;
        }

        // 活用型が判定できない場合のフォールバック
        int lastNonKanaPos = -1;
        for (int i = 0; i < surface.length(); i++) {
            if (!DictUtil.isKana(surface.charAt(i))) {
                lastNonKanaPos = i;
            }
        }

        if (lastNonKanaPos == -1) {
            addOkuriNasi(entry, okuriNasi);
            return;
        }

        int okuriStart = lastNonKanaPos + 1;
        if (okuriStart >= surface.length()) {
            addOkuriNasi(entry, okuriNasi);
            return;
        }

        String okuriGana = surface.substring(okuriStart);
        char firstOkuri = okuriGana.charAt(0);
        char[] triggers = getOkuriChars(firstOkuri);

        if (hiragana.endsWith(okuriGana)) {
            String readingStem = hiragana.substring(0, hiragana.length() - okuriGana.length());
            for (char okuriChar : triggers) {
                String key = readingStem + okuriChar;
                if (entry.pos1.equals("接頭辞")) {
                    key = key + ">";
                } else if (entry.pos1.equals("接尾辞")) {
                    key = ">" + key;
                }
                okuriAri.computeIfAbsent(key, k -> new ArrayList<>()).add(entry);
            }
        } else {
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
            case 'あ' -> new char[]{'a'}; case 'い' -> new char[]{'i'}; case 'う' -> new char[]{'u', 'w'}; case 'え' -> new char[]{'e'}; case 'お' -> new char[]{'o'};
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
            default -> {
                if (c >= 'a' && c <= 'z') yield new char[]{c};
                yield new char[0];
            }
        };
    }

    /**
     * Sudachi の活用型と読みから、SKK の送り識別子（ローマ字）を機械的に決定します。
     *
     * @param type     活用型（五段-サ行、一段、形容詞など）
     * @param hiragana 読み（ひらがな）
     * @return 送り識別子の配列、判定不能な場合は null
     */
    private char[] getOkuriCharsFromType(String type, String hiragana) {
        if (type == null || hiragana == null || hiragana.isEmpty()) return null;

        // 形容詞は「〜い(i)」「〜く(k)」の両方の活用を考慮
        if (type.contains("形容詞")) return new char[]{'i', 'k'};

        // 五段-ワ行は活用によって送り仮名の先頭が w, i, u, e, o に変化するため、これらを網羅する
        if (type.contains("五段-ワ行")) return new char[]{'w', 'i', 'u', 'e', 'o'};

        // その他の動詞・助動詞などは、終止形の末尾の文字から送り識別子を決定する
        // これによりカ行なら 'k'、サ行なら 's' など、その行のコンソナントが選ばれる
        return getOkuriChars(hiragana.charAt(hiragana.length() - 1));
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
                    char[] triggers = getOkuriCharsFromType(e.type, e.hiragana);
                    if (triggers != null && !e.surface.isEmpty()) {
                        // 機械的生成の場合は最後の一文字を送り仮名として除去
                        candidateText = e.surface.substring(0, e.surface.length() - 1);
                    } else {
                        int lastNonKana = -1;
                        for (int i = 0; i < e.surface.length(); i++) {
                            if (!DictUtil.isKana(e.surface.charAt(i))) {
                                lastNonKana = i;
                            }
                        }
                        candidateText = e.surface.substring(0, lastNonKana + 1);
                    }
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
     * 表記（surface）に含まれる仮名や記号が、読み（hiragana）と矛盾していないか確認します。
     * 漢字を除いた「かな部分」を取り出し、それが読みの中に正しい順序で存在するかを判定します。
     *
     * @param surface  表記
     * @param hiragana 読み（ひらがな）
     * @return 矛盾がなければ true
     */
    private boolean isKanaConsistent(String surface, String hiragana) {
        String nReading = normalizeForConsistency(hiragana);
        int lastPos = 0;
        StringBuilder currentSegment = new StringBuilder();

        for (int i = 0; i < surface.length(); i++) {
            char c = surface.charAt(i);
            if (DictUtil.isKana(c)) {
                currentSegment.append(c);
            } else {
                if (currentSegment.length() > 0) {
                    String segment = normalizeForConsistency(currentSegment.toString());
                    int foundPos = nReading.indexOf(segment, lastPos);
                    if (foundPos == -1) return false;
                    lastPos = foundPos + segment.length();
                    currentSegment.setLength(0);
                }
            }
        }
        if (currentSegment.length() > 0) {
            String segment = normalizeForConsistency(currentSegment.toString());
            int foundPos = nReading.indexOf(segment, lastPos);
            return foundPos != -1;
        }
        return true;
    }

    /**
     * 比較のため、文字列を正規化（かな変換、旧仮名、小書き文字、波ダッシュ、ヶ/ケ/か、歴史的仮名遣いの統一）します。
     *
     * @param s 対象文字列
     * @return 正規化後の文字列
     */
    private static String normalizeForConsistency(String s) {
        if (s == null) return "";
        return DictUtil.toHiragana(s)
                .replace("ゐ", "い").replace("ゑ", "え").replace("を", "お")
                .replace("っ", "つ").replace("ゃ", "や").replace("ゅ", "ゆ").replace("ょ", "よ")
                .replace("〜", "ー").replace("～", "ー")
                .replace("ゖ", "か").replace("け", "か").replace("が", "か").replace("げ", "か")
                .replace("ぢ", "じ").replace("づ", "ず")
                .replace("は", "わ").replace("ひ", "い").replace("ふ", "う").replace("へ", "え").replace("ほ", "お");
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
