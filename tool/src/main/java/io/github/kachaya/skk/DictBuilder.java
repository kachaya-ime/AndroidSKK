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
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.TreeMap;

import jdbm.btree.BTree;
import jdbm.helper.StringComparator;
import jdbm.RecordManager;
import jdbm.RecordManagerFactory;

public class DictBuilder {
    static String SYS_DIC_NAME = "skk_main_dict";
    static String RES_RAW_DIR = "app/src/main/res/raw/";
    static String BTREE_NAME = "skk_dict";

    static RecordManager recman;
    static BTree btree;

    static Map<String, LinkedHashSet<String>> map = new TreeMap<>();

    static void readDic(String filename) throws IOException {
        System.err.println(filename);
        File file = new File(filename);
        FileInputStream fis = new FileInputStream(file);
        InputStreamReader isr = new InputStreamReader(fis, "EUC-JP");
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
            String[] values = line.substring(idx + 1).split("/");

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

    static public void main(String argv[]) throws Exception {

        readDic("./data/SKK-JISYO.L");
        readDic("./data/SKK-JISYO.geo");
        readDic("./data/SKK-JISYO.jinmei");
        readDic("./data/SKK-JISYO.propernoun");
        readDic("./data/SKK-JISYO.station");

        writeDic();

    }

}
