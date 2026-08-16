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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

/**
 * Sudachi 辞書（CSV形式）を解析し、品詞・活用ごとに分類した SKK 辞書形式（.skk）および
 * デバッグ・検証用の TSV 形式に変換するツールクラスです。
 * <p>
 * このコンバーターは以下の主要なワークフローを制御します：
 * </p>
 * <ul>
 *   <li><b>読み込みと分類:</b> {@link SudachiEntry} を使用して Sudachi 辞書ファイルをパースし、
 *       品詞や正規化表記に基づいて適切なカテゴリ（名詞、人名、地名等）に分類します。</li>
 *   <li><b>動詞活用の自動生成:</b> 内部で保持する {@code VERB_INFLECTION_TABLE} を参照し、
 *       五段、一段、変格などの活用型に応じた SKK の「送りあり」エントリ（例：読み末尾への 'k', 'g', 'r' 等の付与）を機械的に生成します。</li>
 *   <li><b>SKK 特有の処理:</b> 接頭辞への {@code >} の付与、接尾辞への先頭 {@code >} の付与、英小文字表記エントリの abbrev 登録など、
 *       SKK 辞書として機能させるための変換を行います。</li>
 *   <li><b>重複排除とソート:</b> 同一の読みに対して複数の表記がある場合、重複を排除しつつ五十音順（Unicodeコードポイント順）にソートして出力します。</li>
 *   <li><b>検証用出力:</b> 各処理段階の結果を TSV 形式で出力し、変換ロジックのデバッグを容易にします。</li>
 * </ul>
 */
public class SudachiDictConverter {
    /**
     * 読み込む Sudachi 辞書ファイルのリスト。data ディレクトリに配置されている必要があります。
     */
    private static final List<String> SUDACHI_DIC_FILES = Arrays.asList(
            "core_lex.csv",
            "small_lex.csv",
            "notcore_lex.csv"
    );

    /** Sudachi 辞書ファイルが配置されているディレクトリのパス。 */
    private final Path dataPath = Paths.get("data");
    /** 変換過程の作業ファイルや出力ファイルを保存するディレクトリのパス。 */
    private final Path workPath = Paths.get("work");
    /** カテゴリ（品詞等）をキーとし、Sudachi エントリのリストを値に持つマップ。 */
    private final Map<String, List<SudachiEntry>> sudachiCategoryMap = new LinkedHashMap<>();
    private final Map<String, List<SkkEntry>> skkCategoryMap = new LinkedHashMap<>();

    /**
     * SudachiDictConverter の新しいインスタンスを構築します。
     * デフォルトではカレントディレクトリ直下の {@code data} ディレクトリをソースとし、
     * 出力先として {@code work} ディレクトリを準備（存在しない場合は作成）します。
     *
     * @throws IOException 作業ディレクトリの作成に失敗した場合
     */
    SudachiDictConverter() throws IOException {
        Files.createDirectories(workPath);
    }

    /**
     * コンバーターのエントリーポイント。
     *
     * @param args コマンドライン引数（現在は使用しません）
     */
    public static void main(String[] args) {
        try {
            SudachiDictConverter converter = new SudachiDictConverter();
            converter.readSudachiDict();
            converter.dumpSudachiDict();
            converter.buildSkkDict();
//            converter.dumpSkkDict();
            converter.writeSkkDict();
        } catch (IOException e) {
            System.err.println("Error converting Sudachi dictionary: " + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * 指定されたカテゴリに Sudachi エントリを追加します。
     *
     * @param category     カテゴリ名
     * @param sudachiEntry 追加するエントリ
     */
    private void addSudachiEntry(String category, SudachiEntry sudachiEntry) {
        sudachiCategoryMap.computeIfAbsent(category, k -> new ArrayList<>()).add(sudachiEntry);
    }

    /**
     * Sudachi 辞書ファイルを読み込み、各エントリのカテゴリ分類を行います。
     * パース、基本的なフィルタリング、およびカテゴリの判定には {@link SudachiEntry} のメソッドを使用します。
     * 読み込み対象は {@link #SUDACHI_DIC_FILES} で定義されたファイル群です。
     *
     * @throws IOException ファイルの読み込み中にエラーが発生した場合
     */
    private void readSudachiDict() throws IOException {
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
                    if (line.isEmpty()) {
                        continue;
                    }
                    SudachiEntry entry = new SudachiEntry();
                    entry.parse(line);

                    if (!entry.isValidBase()) {
                        continue;
                    }
                    if (!entry.isReadingHiraganaOnly()) {
                        continue;
                    }

                    String category = entry.getCategory();
                    if (category != null) {
                        addSudachiEntry(category, entry);
                    }
                }
            } catch (IOException e) {
                System.err.println("Error processing " + path + ": " + e.getMessage());
            }

        }
    }

    /**
     * 分類された Sudachi エントリを、デバッグ用の TSV ファイルとして出力ディレクトリに書き出します。
     * 出力ファイル名は「カテゴリ名_sudachi.tsv」となります。
     *
     * @throws IOException ファイルの出力中にエラーが発生した場合
     */
    private void dumpSudachiDict() throws IOException {
        for (Map.Entry<String, List<SudachiEntry>> entry : sudachiCategoryMap.entrySet()) {
            String category = entry.getKey();
            Path outputPath = workPath.resolve(category + "_sudachi.tsv");
            writeSudachiTsv(outputPath, entry.getValue());
        }
    }

    /**
     * エントリリストをデバッグ用の TSV 形式でファイルに書き出します。
     * 書き出し内容の生成には {@link SudachiEntry#toTsv()} を使用します。
     *
     * @param path    出力先のパス
     * @param entries 対象のエントリリスト
     * @throws IOException ファイルアクセスエラー時にスローされます
     */
    private void writeSudachiTsv(Path path, List<SudachiEntry> entries) throws IOException {
        System.out.println("Generating TSV: " + path.toAbsolutePath());
        // 読み、正規化表記、活用型、表層形の順でソート
        entries.sort(Comparator
                .comparing((SudachiEntry e) -> e.reading)
                .thenComparing(e -> e.surface)
                .thenComparing(e -> e.type)
                .thenComparing(e -> e.form)
                .thenComparing(e -> e.normalizedForm)
        );

        try (BufferedWriter writer = Files.newBufferedWriter(path, StandardCharsets.UTF_8)) {
            for (SudachiEntry entry : entries) {
                writer.write(entry.toTsv() + "\n");
            }
        }
        System.out.println("Success. Output written to " + path.getFileName() + " (Total entries: " + entries.size() + ")");
    }

    /**
     * 指定されたカテゴリに SKK エントリを追加します。
     *
     * @param category カテゴリ名
     * @param skkEntry 追加する SKK エントリ
     */
    private void addSkkEntry(String category, SkkEntry skkEntry) {
        skkCategoryMap.computeIfAbsent(category, k -> new ArrayList<>()).add(skkEntry);
    }

    /**
     * 抽出された Sudachi エントリから SKK 辞書形式のエントリを構築します。
     * 正規化表記と表層形の送り仮名の整合性を厳格にチェックした上で、
     * 動詞については {@link SudachiEntry#getInflectedEntries()} を使用して送り仮名付きエントリを生成します。
     *
     * @throws IOException 検証用 TSV の出力中にエラーが発生した場合
     */
    private void buildSkkDict() throws IOException {
        for (Map.Entry<String, List<SudachiEntry>> categoryMap : sudachiCategoryMap.entrySet()) {
            String category = categoryMap.getKey();
            if (category.startsWith("_")) {
                continue;
            }
            List<SudachiEntry> sudachiEntries = categoryMap.getValue();
            for (SudachiEntry sudachiEntry : sudachiEntries) {
                String reading = sudachiEntry.reading;
                String surface = sudachiEntry.surface;
                String normalizedForm = sudachiEntry.normalizedForm;
                if (sudachiEntry.isSurfaceKanaOnly()) {
                    // 表記が仮名のみならばスキップ
                    continue;
                }
                if (sudachiEntry.type.equals("*")) {

                    if (category.equals("接頭辞")) {
                        addSkkEntry(category, new SkkEntry(reading + ">", surface));
                    }
                    if (category.equals("接尾辞")) {
                        addSkkEntry(category, new SkkEntry(">" + reading, surface));
                    }
                    if (category.equals("接尾辞")) {
                        addSkkEntry(category, new SkkEntry(">" + reading, surface));
                    }
                    if (sudachiEntry.pos3.equals("助数詞可能")) {
                        addSkkEntry(category, new SkkEntry("#" + reading, "#1" + surface));
                        addSkkEntry(category, new SkkEntry("#" + reading, "#3" + surface));
                        addSkkEntry(category, new SkkEntry("#" + reading, "#2" + surface));
                        addSkkEntry(category, new SkkEntry("#" + reading, "#0" + surface));
                        addSkkEntry(category, new SkkEntry("#" + reading, "#4" + surface));
                        addSkkEntry(category, new SkkEntry("#" + reading, "#5" + surface));
                    }
                    if (category.equals("日英")) {
                        // abbrev
                        addSkkEntry(category, new SkkEntry(surface, DictUtil.toKatakana(reading)));
                    } else {
                        addSkkEntry(category, new SkkEntry(reading, surface));  // そのまま
                    }
                    continue;
                }
                // 形容詞
                if (sudachiEntry.form.equals("語幹-一般")) {
                    addSkkEntry(category, new SkkEntry(reading + "i", surface));
                    addSkkEntry(category, new SkkEntry(reading + "k", surface));
                    continue;
                }
                // 動詞
                if (!sudachiEntry.form.equals("終止形-一般")) {
                    continue;
                }
                String[] ss = DictUtil.parseSurface(normalizedForm);
                if (ss == null) {
                    continue;
                }
                String[] ss2 = DictUtil.parseSurface(surface);
                if (ss2 == null) {
                    continue;
                }
                if (!ss[1].equals(ss2[1])) {
                    continue;
                }
                List<SkkEntry> inflected = sudachiEntry.getInflectedEntries();
                for (SkkEntry e : inflected) {
                    addSkkEntry(category, e);
                }
            }
        }
    }

    /**
     * 構築された SKK 辞書エントリを、デバッグ用の TSV ファイルとして出力ディレクトリに書き出します。
     * 出力ファイル名は「カテゴリ名_skk.tsv」となります。
     *
     * @throws IOException ファイルの出力中にエラーが発生した場合
     */
    private void dumpSkkDict() throws IOException {
        // 確認用出力
        for (Map.Entry<String, List<SkkEntry>> entry : skkCategoryMap.entrySet()) {
            String category = entry.getKey();
            Path outputPath = workPath.resolve(category + "_skk.tsv");
            writeSkkTsv(outputPath, entry.getValue());
        }
    }

    /**
     * 構築された SKK 辞書エントリを、カテゴリごとに検証用の TSV ファイルとして書き出します。
     * 書き出し内容の生成には {@link SkkEntry#toTsv()} を使用します。
     *
     * @param path    出力先のパス
     * @param entries 対象のエントリリスト
     * @throws IOException ファイルの書き出し中にエラーが発生した場合
     */
    private void writeSkkTsv(Path path, List<SkkEntry> entries) throws IOException {
        System.out.println("Generating TSV: " + path.toAbsolutePath());
        // 読み、表記の順でソート
        entries.sort(Comparator
                .comparing((SkkEntry e) -> e.reading)
                .thenComparing(e -> e.surface)
        );

        try (BufferedWriter writer = Files.newBufferedWriter(path, StandardCharsets.UTF_8)) {
            for (SkkEntry entry : entries) {
                writer.write(entry.toTsv() + "\n");
            }
        }
        System.out.println("Success. Output written to " + path.getFileName() + " (Total entries: " + entries.size() + ")");
    }

    /**
     * 構築された SKK 辞書データを、各カテゴリごとに .skk ファイルとして書き出します。
     * 読み（キー）および候補文字列（値）の両方を五十音順にソートし、重複を排除して出力します。
     *
     * @throws IOException ファイルの書き出し中にエラーが発生した場合
     */
    private void writeSkkDict() throws IOException {

        for (Map.Entry<String, List<SkkEntry>> map : skkCategoryMap.entrySet()) {
            String category = map.getKey();
            if (category.startsWith("_")) {
                continue;
            }
            List<SkkEntry> entries = map.getValue();

            // 読みをキーとし、重複を排除かつソートされた表記の集合を値に持つマップ
            Map<String, Set<String>> readingMap = new TreeMap<>();
            for (SkkEntry entry : entries) {
                readingMap.computeIfAbsent(entry.reading, k -> new TreeSet<>()).add(entry.surface);
            }

            Path outputPath = workPath.resolve(category + ".skk");
            try (BufferedWriter writer = Files.newBufferedWriter(outputPath, StandardCharsets.UTF_8)) {
                // すべての読み（キー）に対して処理
                for (Map.Entry<String, Set<String>> entry : readingMap.entrySet()) {
                    String reading = entry.getKey();
                    Set<String> surfaces = entry.getValue();
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

}
