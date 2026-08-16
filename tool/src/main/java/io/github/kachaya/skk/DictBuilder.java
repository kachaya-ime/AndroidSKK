package io.github.kachaya.skk;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import jdbm.btree.BTree;
import jdbm.helper.StringComparator;
import jdbm.RecordManager;
import jdbm.RecordManagerFactory;

/**
 * SKK 辞書ファイルを読み込み、Android アプリで使用可能な形式に変換・統合するツールクラスです。
 * <p>
 * このクラスは以下の処理を行います：
 * </p>
 * <ul>
 *   <li>複数の SKK 形式辞書ファイル（EUC-JP/UTF-8）の読み込みとマージ。</li>
 *   <li>重複するエントリの排除と、注釈（;以降）の除去。</li>
 *   <li>JDBM (B+Tree) を使用したバイナリ辞書ファイル（.db）の生成。</li>
 *   <li>Android アプリの {@code res/raw} ディレクトリへのバイナリ出力。</li>
 *   <li>検証用のテキスト形式辞書（skk_main_dict.txt）の出力。</li>
 * </ul>
 */
public class DictBuilder {
    /** 生成するシステム辞書のベース名。 */
    static String SYS_DIC_NAME = "skk_main_dict";
    /** Android アプリの raw リソースディレクトリのパス。 */
    static String RES_RAW_DIR = "app/src/main/res/raw/";
    /** JDBM 内で使用する BTree の名前。 */
    static String BTREE_NAME = "skk_dict";

    /** JDBM のレコードマネージャ。 */
    static RecordManager recman;
    /** 高速な検索を実現するための B+Tree インデックス。 */
    static BTree btree;

    /**
     * 読み込み中の辞書データを保持する一時マップ。
     * キーは「読み」、値はソートおよび重複排除された「候補」のセットです。
     */
    static Map<String, LinkedHashSet<String>> map = new TreeMap<>();

    /**
     * SKK辞書ファイルを読み込み、内部マップにマージします。
     *
     * @param filename 辞書ファイルのパス
     * @param encoding ファイルのエンコーディング (EUC-JP, UTF-8など)
     * @throws IOException I/Oエラーが発生した場合
     */
    static void readDic(String filename, String encoding) throws IOException {
        System.err.println(filename);
        File file = new File(filename);
        if (!file.exists()) {
            System.err.println("Skip: File not found: " + filename);
            return;
        }
        FileInputStream fis = new FileInputStream(file);
        InputStreamReader isr = new InputStreamReader(fis, encoding);
        BufferedReader br = new BufferedReader(isr);
        String line;
        while ((line = br.readLine()) != null) {
            if (line.startsWith(";;")) {
                continue;
            }

            int idx = line.indexOf(' ');
            if (idx == -1) {
                continue;
            }

            if (line.contains("う゛")) {
                // System.out.println(line);
                line = line.replace("う゛", "\u3094"); // "ゔ"
            }

            String key = line.substring(0, idx);
            String candidatesPart = line.substring(idx + 1);
            if (candidatesPart.startsWith("/")) {
                candidatesPart = candidatesPart.substring(1);
            }
            if (candidatesPart.endsWith("/")) {
                candidatesPart = candidatesPart.substring(0, candidatesPart.length() - 1);
            }

            List<String> values = splitCandidates(candidatesPart);

            LinkedHashSet<String> set = map.get(key);
            if (set == null) {
                set = new LinkedHashSet<>();
            }

            for (String value : values) {
                if (value.length() == 0) {
                    continue;
                }
                // 注釈があると同じ表記が複数でてしまうので消す（例: 「読み /表記;注釈/」の注釈部分）
                idx = value.indexOf(';');
                if (idx > 0) {
                    value = value.substring(0, idx);
                }
                // (concat ...) などの Lisp 式を含むエントリは現在は非対応としてスキップする場合はここで行う
                // if (value.contains("(concat")) {
                //     continue;
                // }

                set.add(value);
                map.put(key, set);
            }
        }
        br.close();
    }

    /**
     * スラッシュ区切りの候補文字列を分割します。
     * SKK 辞書のエントリにおいて、候補内に Lisp 式等の括弧が含まれる場合があるため、
     * 括弧のネストを考慮して正しく分割を行います。
     *
     * @param s 候補部分の文字列（例: "表記1/表記2/(lisp...)/"）
     * @return 分割された各候補のリスト
     */
    private static List<String> splitCandidates(String s) {
        List<String> res = new ArrayList<>();
        int start = 0;
        int nest = 0;
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c == '(') nest++;
            else if (c == ')') nest--;
            else if (c == '/' && nest == 0) {
                res.add(s.substring(start, i));
                start = i + 1;
            }
        }
        if (start < s.length()) {
            res.add(s.substring(start));
        }
        return res;
    }

    /**
     * 読み込んだ辞書データをテキストファイルおよび JDBM データベースに出力します。
     * テキストファイルはプロジェクトルートに、バイナリファイルは {@link #RES_RAW_DIR} に出力されます。
     *
     * @throws IOException I/O エラーが発生した場合
     */
    static void writeDic() throws IOException {
        BufferedWriter bw = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(
                new File(SYS_DIC_NAME + ".txt")), "UTF-8"));

        String dbPath = RES_RAW_DIR + SYS_DIC_NAME;
        Files.deleteIfExists(Paths.get(dbPath + ".db"));
        Files.deleteIfExists(Paths.get(dbPath + ".lg"));

        recman = RecordManagerFactory.createRecordManager(dbPath);
        btree = BTree.createInstance(recman, new StringComparator());
        recman.setNamedObject(BTREE_NAME, btree.getRecid());

        for (Map.Entry<String, LinkedHashSet<String>> entry : map.entrySet()) {
            String key = entry.getKey();
            LinkedHashSet<String> set = entry.getValue();
            StringBuilder sb = new StringBuilder();
            for (String value : set) {
                sb.append("/");
                sb.append(value);
            }
            sb.append("/");
            btree.insert(key, sb.toString(), true);
            bw.write(key + " " + sb.toString() + "\n");
        }

        recman.commit();
        recman.close();
        Files.deleteIfExists(Paths.get(dbPath + ".lg"));

        bw.flush();
        bw.close();
    }

    /**
     * 辞書構築のメインエントリポイントです。
     * <p>
     * {@code data} ディレクトリにある標準的な SKK 辞書ファイルと、
     * {@code work} ディレクトリにある Sudachi から変換された辞書ファイルを順番に読み込み、
     * それらを統合して最終的なシステム辞書ファイルを構築します。
     * </p>
     *
     * @param argv コマンドライン引数（現在は使用しません）
     * @throws Exception 辞書構築中に予期しないエラーが発生した場合
     */
    static public void main(String argv[]) throws Exception {

        // 標準SKK辞書 (基礎語彙)
        readDic("./data/SKK-JISYO.L", "EUC-JP");
        readDic("./work/動詞.skk", "UTF-8");
        readDic("./work/形容詞.skk", "UTF-8");
        readDic("./work/普通名詞.skk", "UTF-8");
        readDic("./work/補助記号.skk", "UTF-8");
        readDic("./work/記号.skk", "UTF-8");
        readDic("./work/形状詞.skk", "UTF-8");
        readDic("./work/助詞.skk", "UTF-8");
        readDic("./work/数詞.skk", "UTF-8");
        readDic("./work/接続詞.skk", "UTF-8");
        readDic("./work/接頭辞.skk", "UTF-8");
        readDic("./work/接尾辞.skk", "UTF-8");
        readDic("./work/代名詞.skk", "UTF-8");
        readDic("./work/日英.skk", "UTF-8");
        readDic("./work/副詞.skk", "UTF-8");
        readDic("./work/連体詞.skk", "UTF-8");

        // 固有名詞
        readDic("./data/SKK-JISYO.propernoun", "EUC-JP");
        readDic("./work/固有名詞.skk", "UTF-8");

        readDic("./data/SKK-JISYO.jinmei", "EUC-JP");
        readDic("./work/人名.skk", "UTF-8");

        readDic("./data/SKK-JISYO.geo", "EUC-JP");
        readDic("./work/地名.skk", "UTF-8");

        readDic("./data/SKK-JISYO.station", "EUC-JP");


        writeDic();

    }

}
