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
 * SKK辞書ファイルを読み込み、JDBM形式のバイナリ辞書を構築するクラス。
 */
public class DictBuilder {
    static String SYS_DIC_NAME = "skk_main_dict";
    static String RES_RAW_DIR = "app/src/main/res/raw/";
    static String BTREE_NAME = "skk_dict";

    static RecordManager recman;
    static BTree btree;

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
                // 注釈があると同じ表記が複数でてしまうので消す
                idx = value.indexOf(';');
                if (idx > 0) {
                    value = value.substring(0, idx);
                }
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
     * スラッシュ区切りの候補文字列を分割します。括弧のネストを考慮します。
     *
     * @param s 候補文字列
     * @return 分割された候補のリスト
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
     * 読み込んだ辞書データをテキストファイルおよびJDBMデータベースに出力します。
     *
     * @throws IOException I/Oエラーが発生した場合
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
     * 辞書構築のメインエントリポイント。
     * 各種辞書ファイルを順番に読み込み、最終的な辞書を構築します。
     *
     * @param argv コマンドライン引数
     * @throws Exception 構築中にエラーが発生した場合
     */
    static public void main(String argv[]) throws Exception {

        // 1. 最優先: 標準SKK辞書 (基礎語彙)
        readDic("./data/SKK-JISYO.L", "EUC-JP");

        // 2. 現代語・一般語の補完 (Sudachi由来の頻度順)
        readDic("./work/名詞.skk", "UTF-8");
        readDic("./work/サ変名詞.skk", "UTF-8");
        readDic("./work/副詞可能名詞.skk", "UTF-8");
        readDic("./work/動詞.skk", "UTF-8");
        readDic("./work/形容詞.skk", "UTF-8");
        readDic("./work/形状詞.skk", "UTF-8");
        readDic("./work/副詞.skk", "UTF-8");
        readDic("./work/連体詞.skk", "UTF-8");
        readDic("./work/接続詞.skk", "UTF-8");
        // readDic("./work/感動詞.skk", "UTF-8");

        // 3. 固有名詞 (標準辞書とSudachiの新しい語彙を交互に)
        readDic("./data/SKK-JISYO.jinmei", "EUC-JP");
        readDic("./work/人名.skk", "UTF-8");

        readDic("./data/SKK-JISYO.geo", "EUC-JP");
        readDic("./work/地名.skk", "UTF-8");

        readDic("./data/SKK-JISYO.station", "EUC-JP");
        readDic("./work/駅名.skk", "UTF-8");

        readDic("./data/SKK-JISYO.propernoun", "EUC-JP");
        readDic("./work/組織名.skk", "UTF-8");
        readDic("./work/固有名詞.skk", "UTF-8");

        // 4. その他補助・記号類
        readDic("./work/代名詞.skk", "UTF-8");
        readDic("./work/助数詞.skk", "UTF-8");
        readDic("./work/数詞.skk", "UTF-8");
        readDic("./work/接頭辞.skk", "UTF-8");
        readDic("./work/接尾辞.skk", "UTF-8");
        readDic("./work/動詞的接尾辞.skk", "UTF-8");
        readDic("./work/形容詞的接尾辞.skk", "UTF-8");
        // readDic("./work/英数字.skk", "UTF-8");
        readDic("./work/活用語.skk", "UTF-8");
        readDic("./work/非活用語.skk", "UTF-8");
        // readDic("./work/記号.skk", "UTF-8");
        // readDic("./work/補助記号.skk", "UTF-8");

        writeDic();

    }

}
