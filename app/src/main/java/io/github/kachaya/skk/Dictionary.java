package io.github.kachaya.skk;

import android.content.Context;
import android.util.Log;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import jdbm.RecordManager;
import jdbm.RecordManagerFactory;
import jdbm.btree.BTree;
import jdbm.helper.StringComparator;
import jdbm.helper.Tuple;
import jdbm.helper.TupleBrowser;

/**
 * SKK の辞書データを管理・検索するクラスです。
 * <p>
 * アセット（raw リソース）から展開される読み取り専用の「システム辞書」と、
 * ユーザーの登録・学習内容を保持する書き込み可能な「ユーザー辞書」の 2 つを管理します。
 * 内部ストレージ上の JDBM データベースを使用して BTree インデックスを構築し、高速な検索を実現しています。
 * </p>
 */
public class Dictionary {

    /** システム辞書の DB ファイルベース名。 */
    private static final String MAIN_DICT = "skk_main_dict";
    /** ユーザー辞書の DB ファイルベース名。 */
    private static final String USER_DICT = "skk_user_dict";
    /** DB 内で使用する BTree の名前。 */
    private static final String BTREE_NAME = "skk_dict";
    /** 見出し語内の数値を抽出・置換するための正規表現パターン。 */
    private static final Pattern PAT_NUM_IN_KEY = Pattern.compile("[0-9]+");
    /** アプリの内部ファイルディレクトリの絶対パス。 */
    private final String mFilesDirPath;
    /** システム辞書（読み取り専用）の BTree インスタンス。 */
    private BTree mBTreeMainDict;
    /** ユーザー辞書の永続化を管理するレコードマネージャ。 */
    private RecordManager mRecManUserDict;
    /** ユーザー辞書（読み書き可能）の BTree インスタンス。 */
    private BTree mBTreeUserDict;
    /** ロールバック（Undo）機能のための前回操作キー。 */
    private String mOldKey = null;
    /** ロールバック（Undo）機能のための前回操作時の値。 */
    private String mOldValue = null;

    /**
     * デバッグビルド時のみログを出力する内部ユーティリティです。
     *
     * @param msg ログメッセージ
     */
    private void logI(String msg) {
        if (BuildConfig.DEBUG) {
            Log.i("Dictionary", msg);
        }
    }

    /**
     * Dictionary を初期化し、辞書データベースをオープンします。
     * データベースファイルが存在しない場合は、リソースからコピーまたは新規作成を行います。
     *
     * @param context アプリケーションコンテキスト
     */
    public Dictionary(Context context) {
        mFilesDirPath = context.getFilesDir().getAbsolutePath();
        // システム辞書のロード
        try {
            copyFromResRaw(context);
            RecordManager recMan = RecordManagerFactory.createRecordManager(mFilesDirPath + "/" + MAIN_DICT);
            mBTreeMainDict = BTree.load(recMan, recMan.getNamedObject(BTREE_NAME));
        } catch (IOException e) {
            Log.e("Dictionary", "MainDictionary: " + e.getMessage());
            mBTreeMainDict = null;
        }
        // ユーザー辞書のロード（存在しない場合は新規作成）
        try {
            mRecManUserDict = RecordManagerFactory.createRecordManager(mFilesDirPath + "/" + USER_DICT);
            long recId = mRecManUserDict.getNamedObject(BTREE_NAME);
            if (recId == 0) {
                mBTreeUserDict = BTree.createInstance(mRecManUserDict, new StringComparator());
                mRecManUserDict.setNamedObject(BTREE_NAME, mBTreeUserDict.getRecid());
                mRecManUserDict.commit();
            } else {
                mBTreeUserDict = BTree.load(mRecManUserDict, recId);
            }
        } catch (IOException e) {
            Log.e("Dictionary", "UserDictionary: " + e.getMessage());
            mRecManUserDict = null;
            mBTreeUserDict = null;
        }
    }

    private static boolean validateForOkuri(String okurigana, String s, Entry entry) {
        boolean isValidForOkuri = true;
        if (okurigana != null) {
            boolean found = false;
            for (List<String> lst : entry.okuri_blocks) {
                if (lst.get(0).equals(okurigana) && lst.contains(s)) {
                    found = true;
                    break;
                }
            }
            if (!found) {
                isValidForOkuri = false;
            }
        }
        return isValidForOkuri;
    }

    /**
     * 語句を SKK 辞書のエスケープ形式（concat）に変換します。
     * '/' や ';' が含まれる場合に必要です。
     *
     * @param val 変換対象の語句
     * @return エスケープ済みの語句
     */
    public static String escape(String val) {
        if (val == null) return null;
        // すでに concat 形式ならそのまま
        if (val.startsWith("(concat \"") && val.endsWith("\")")) {
            return val;
        }
        // 特殊文字が含まれていなければそのまま
        if (val.indexOf('/') == -1 && val.indexOf(';') == -1) {
            return val;
        }
        // 8進数エスケープ形式に置換
        return "(concat \"" + val.replace(";", "\\073").replace("/", "\\057") + "\")";
    }

    /**
     * 実行バイナリ内のリソース（raw）から、システム辞書の DB ファイルを内部ストレージへ展開します。
     * ファイルサイズが一致する場合はコピーをスキップします。
     *
     * @param context コンテキスト
     * @throws IOException ファイルアクセスエラー時にスローされます
     */
    private void copyFromResRaw(Context context) throws IOException {
        String dbFileName = mFilesDirPath + "/" + MAIN_DICT + ".db";
        File dbFile = new File(dbFileName);

        BufferedInputStream bis = new BufferedInputStream(context.getResources().openRawResource(R.raw.skk_main_dict));
        if (bis.available() == dbFile.length()) {
            bis.close();
            return;
        }

        BufferedOutputStream bos = new BufferedOutputStream(Files.newOutputStream(Paths.get(dbFileName)));
        int size;
        byte[] buf = new byte[16 * 1024];
        while ((size = bis.read(buf, 0, buf.length)) > 0) {
            bos.write(buf, 0, size);
        }
        bos.flush();
        bos.close();
        bis.close();
    }

    /**
     * 指定された BTree から、指定された文字列で始まるキーを前方一致で検索します。
     * 検索結果は最大 5 件に制限されます。
     *
     * @param key   検索するプレフィックス文字列
     * @param bTree 検索対象の BTree インスタンス
     * @return 見つかったキーのリスト
     */
    private List<String> findKeys(String key, BTree bTree) {
        List<String> list = new ArrayList<>();
        Tuple tuple = new Tuple();
        TupleBrowser browser;
        try {
            browser = bTree.browse(key);
            while (list.size() < 5) {
                if (!browser.getNext(tuple)) {
                    break;
                }
                String str = (String) tuple.getKey();
                if (!str.startsWith(key)) {
                    break;
                }
                // 送りありエントリは（ここでは）飛ばす
                if (RomajiConverter.isAlphabet(str.charAt(str.length() - 1)) && !RomajiConverter.isAlphabet(str.charAt(0))) {
                    continue;
                }
                list.add(str);
            }
        } catch (IOException ignored) {
        }
        return list;
    }

    /**
     * システムおよびユーザー辞書から変換候補を検索し、統合されたリストを返します。
     * <p>
     * 検索キーに数字が含まれる場合、SKK の仕様に基づき数字を '#' に置換して検索を行います。
     * 検索結果は、ユーザー辞書の学習内容（直近に選択されたもの）が優先的に先頭に配置されます。
     * </p>
     *
     * @param key       見出し語（"かな" または "漢字"）
     * @param okurigana 送り仮名。送りなし変換の場合は null。
     * @return 候補オブジェクト {@link Candidate} のリスト。見つからない場合は空のリスト。
     */
    public List<Candidate> findCandidates(String key, String okurigana) {
        List<String> nums = new ArrayList<>();
        String searchKey = key;

        // 数値部分を抽出して # に置換
        Matcher m = PAT_NUM_IN_KEY.matcher(key);
        boolean hasNum = false;
        while (m.find()) {
            hasNum = true;
            nums.add(m.group());
        }
        if (hasNum) {
            searchKey = m.replaceAll("#");
        }

        // システム辞書の検索
        List<String> list1 = new ArrayList<>();
        String[] cands = getMainDictCandidates(searchKey);
        if (cands != null) {
            Collections.addAll(list1, cands);
        }

        // ユーザー辞書の検索
        Entry entry = getUserDictEntry(searchKey);
        List<String> list2 = (entry != null) ? entry.candidates : null;
        Set<String> userCandsSet = new HashSet<>();

        if (list1.isEmpty() && list2 == null) {
            return Collections.emptyList();
        }

        // 学習内容（ユーザー辞書）の優先反映
        if (list2 != null) {
            int idx = 0;
            for (String s : list2) {
                boolean isValidForOkuri = validateForOkuri(okurigana, s, entry);
                if (isValidForOkuri) {
                    userCandsSet.add(s);
                    list1.remove(s);
                    list1.add(idx++, s);
                }
            }
        }

        if (list1.isEmpty()) {
            return Collections.emptyList();
        }

        List<Candidate> candidates = new ArrayList<>();
        for (String rawCand : list1) {
            String template;
            String annotation;
            int i = rawCand.lastIndexOf(';');
            if (i != -1) {
                template = rawCand.substring(0, i);
                annotation = rawCand.substring(i + 1);
            } else {
                template = rawCand;
                annotation = null;
            }
            // デコードおよび数値置換処理は Candidate クラス側で行われる
            boolean isUser = userCandsSet.contains(rawCand);
            candidates.add(new Candidate(rawCand, template, annotation, nums, isUser));
        }
        return candidates;
    }

    /**
     * 動的補完のために、指定された文字列で始まる見出し語を検索します。
     *
     * @param key 入力途中のプレフィックス
     * @return 前方一致する見出し語のリスト。システム・ユーザー辞書の両方から取得されます。
     */
    public List<String> findSuggestions(String key) {
        List<String> list = new ArrayList<>(findKeys(key, mBTreeMainDict));
        List<String> list2 = findKeys(key, mBTreeUserDict);
        int idx = 0;
        for (String s : list2) {
            list.remove(s);
            list.add(idx++, s);
        }
        return list;
    }

    /**
     * ユーザー辞書のベース名を取得します。
     *
     * @return ユーザー辞書ファイル名
     */
    public String getUserDictionaryName() {
        return USER_DICT;
    }

    /**
     * ユーザー辞書の全内容を削除し、データベースを初期状態にリセットします。
     */
    public void clearUserDictionary() {
        try {
            mBTreeUserDict = BTree.createInstance(mRecManUserDict, new StringComparator());
            mRecManUserDict.setNamedObject(BTREE_NAME, mBTreeUserDict.getRecid());
            mRecManUserDict.commit();
            Log.d("Dictionary", "Cleared all entries from user dictionary.");
        } catch (IOException e) {
            Log.e("Dictionary", "Failed to clear user dictionary", e);
            throw new RuntimeException("Failed to clear user dictionary", e);
        }
    }

    /**
     * システム辞書から、指定された見出し語に対応する候補群（生データ）を抽出します。
     *
     * @param key 見出し語
     * @return スラッシュで区切られた候補の配列。存在しない場合は null。
     */
    public String[] getMainDictCandidates(String key) {
        String value;
        try {
            value = (String) mBTreeMainDict.find(key);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }

        if (value == null) {
            return null;
        }
        return value.substring(1).split("/");
    }

    /**
     * ユーザー辞書から指定された見出し語に対応するエントリを読み込み、構造化します。
     *
     * @param key 見出し語
     * @return 構造化されたエントリ。存在しない場合は null。
     */
    private Entry getUserDictEntry(String key) {
        String value;
        try {
            value = (String) mBTreeUserDict.find(key);
        } catch (IOException ignored) {
            return null;
        }
        if (value == null) {
            return null;
        }

        String[] va_array = value.substring(1).split("/");
        List<String> cd = new ArrayList<>();
        for (String str : va_array) {
            if (str.startsWith("[")) {
                break;
            }
            cd.add(str);
        }

        List<List<String>> okr = new ArrayList<>();
        if (value.contains("[") && value.contains("]")) {
            va_array = value.split("[\\[\\]]");
            for (int i = 1; i < va_array.length; i++) {
                if (va_array[i].equals("/")) {
                    continue;
                }
                String[] va_array2 = va_array[i].split("/");
                List<String> tmp_okr = new ArrayList<>();
                Collections.addAll(tmp_okr, va_array2);
                okr.add(tmp_okr);
            }
        }
        return new Entry(cd, okr);
    }

    /**
     * ユーザー辞書の全内容をテキスト形式で一括エクスポートします。
     *
     * @return 辞書全件の文字列リスト
     */
    public List<String> exportUserDictionary() {
        List<String> list = new ArrayList<>();
        Tuple tuple = new Tuple();
        try {
            mRecManUserDict.commit();
            TupleBrowser browser = mBTreeUserDict.browse();
            while (browser.getNext(tuple)) {
                list.add(tuple.getKey() + " " + tuple.getValue());
            }
        } catch (IOException ignored) {
        }
        return list;
    }

    /**
     * テキスト形式の辞書データを、現在のユーザー辞書へ一括インポートします。
     *
     * @param entries 「キー 値」形式の文字列リスト
     */
    public void importUserDictionary(List<String> entries) {
        for (String entry : entries) {
            String[] parts = entry.split(" ");
            if (parts.length != 2) {
                continue;
            }
            try {
                mBTreeUserDict.insert(parts[0], parts[1], true);
                mRecManUserDict.commit();
            } catch (IOException ignored) {
            }
        }
    }

    /**
     * ユーザー辞書に新しい変換エントリを追加、または既存の学習順序を更新します。
     * <p>
     * 選択された候補をリストの先頭へ移動させることで、次回以降の優先順位を上げます。
     * また、直前の状態を保持し、{@link #rollback()} による 1 段階の Undo を可能にします。
     * </p>
     *
     * @param key   見出し語キー
     * @param val   確定された候補の生データ
     * @param okuri 送り仮名。存在しない場合は null。
     */
    public void addEntry(String key, String val, String okuri) {
        logI("addEntry: key=" + key + ", val=" + val + ", okuri=" + okuri);

        mOldKey = key;
        val = escape(val); // 保存前にエスケープを保証
        StringBuilder new_val = new StringBuilder();
        Entry entry = getUserDictEntry(key);

        if (entry == null) {
            new_val.append("/").append(val).append("/");
            if (okuri != null) {
                new_val.append("[").append(okuri).append("/").append(val).append("/]/");
            }
            mOldValue = null;
        } else {
            List<String> cands = entry.candidates;
            cands.remove(val);
            cands.add(0, val); // 常に先頭に追加（学習）

            List<List<String>> okrs = entry.okuri_blocks;
            if (okuri != null) {
                boolean found = false;
                for (List<String> lst : okrs) {
                    if (lst.get(0).equals(okuri)) {
                        found = true;
                        if (!lst.contains(val)) {
                            lst.add(val);
                        }
                    }
                }
                if (!found) {
                    List<String> new_okr = new ArrayList<>();
                    new_okr.add(okuri);
                    new_okr.add(val);
                    okrs.add(new_okr);
                }
            }

            for (String str : cands) {
                new_val.append("/").append(str);
            }
            for (List<String> lst : okrs) {
                new_val.append("/[");
                for (String str : lst) {
                    new_val.append(str).append("/");
                }
                new_val.append("]");
            }
            new_val.append("/");

            try {
                mOldValue = (String) mBTreeUserDict.find(key);
            } catch (IOException ignored) {
            }
        }

        try {
            mBTreeUserDict.insert(key, new_val.toString(), true);
            mRecManUserDict.commit();
        } catch (IOException ignored) {
        }
    }

    /**
     * ユーザー辞書から特定の見出し語に対応する特定の候補を削除します。
     * 削除の結果、エントリが空になった場合はエントリ自体を削除します。
     *
     * @param key   見出し語キー
     * @param val   削除する候補の生データ
     * @param okuri 送り仮名。送りなしの場合は null。
     */
    public void removeEntry(String key, String val, String okuri) {
        val = escape(val); // 比較前にエスケープ形式に統一
        Entry entry = getUserDictEntry(key);
        if (entry == null) {
            return;
        }

        // 送りなし候補リストから削除
        entry.candidates.remove(val);

        // 送りありブロックから削除
        if (okuri != null) {
            Iterator<List<String>> it = entry.okuri_blocks.iterator();
            while (it.hasNext()) {
                List<String> lst = it.next();
                if (lst.get(0).equals(okuri)) {
                    lst.remove(val);
                    // 送り仮名以外に候補がなくなったらブロックごと削除
                    if (lst.size() <= 1) {
                        it.remove();
                    }
                }
            }
        }

        // 候補が完全になくなった場合はエントリ自体を削除
        if (entry.candidates.isEmpty() && entry.okuri_blocks.isEmpty()) {
            deleteEntry(key);
        } else {
            // 文字列を再構築して更新
            StringBuilder new_val = new StringBuilder();
            for (String str : entry.candidates) {
                new_val.append("/").append(str);
            }
            for (List<String> lst : entry.okuri_blocks) {
                new_val.append("/[");
                for (String str : lst) {
                    new_val.append(str).append("/");
                }
                new_val.append("]");
            }
            new_val.append("/");

            try {
                mBTreeUserDict.insert(key, new_val.toString(), true);
                mRecManUserDict.commit();
            } catch (IOException ignored) {
            }
        }
    }

    /**
     * ユーザー辞書から特定のキーに対応するエントリを完全に削除します。
     *
     * @param key 削除する見出し語
     */
    public void deleteEntry(String key) {
        try {
            mBTreeUserDict.remove(key);
            mRecManUserDict.commit();
        } catch (IOException ignored) {
        }
        if (key.equals(mOldKey)) {
            mOldKey = null;
            mOldValue = null;
        }
    }

    /**
     * 直前の {@link #addEntry(String, String, String)} 操作を取り消し、辞書の状態を 1 段階戻します。
     * 再変換（Re-conversion）時などに、学習による順序変更をキャンセルするために使用されます。
     */
    public void rollback() {
        if (mOldKey == null) {
            return;
        }
        try {
            if (mOldValue == null) {
                mBTreeUserDict.remove(mOldKey);
            } else {
                mBTreeUserDict.insert(mOldKey, mOldValue, true);
            }
            mRecManUserDict.commit();
        } catch (IOException ignored) {
        }
        mOldValue = null;
        mOldKey = null;
    }

    /**
     * 現在までのデータベースの変更をディスクへ永続化（フラッシュ）します。
     */
    public void commitChanges() {
        try {
            mRecManUserDict.commit();
        } catch (Exception ignored) {
        }
    }

    /**
     * ユーザー辞書の 1 つのエントリを構造化した内部クラスです。
     * SKK 辞書形式の文字列（例: "/候補1/候補2/[送り/候補3/]/") をパースして保持します。
     */
    public static class Entry {
        /** 送りなしの変換候補リスト。 */
        public List<String> candidates;
        /**
         * 送りあり（動詞・形容詞等）の候補ブロックリスト。
         * 各リストの 0 番目の要素が送り仮名、それ以降がその送りに対する候補です。
         */
        public List<List<String>> okuri_blocks;

        Entry(List<String> cd, List<List<String>> okr) {
            candidates = cd;
            okuri_blocks = okr;
        }
    }
}
