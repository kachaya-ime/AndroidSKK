package io.github.kachaya.skk;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.provider.DocumentsContract;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.ListView;

import androidx.activity.result.ActivityResult;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.snackbar.Snackbar;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.util.ArrayList;
import java.util.List;

import io.github.kachaya.skk.engine.Dictionary;

/**
 * ユーザー辞書の内容を管理するための管理用アクティビティです。
 * <p>
 * 学習済みエントリの一覧表示、個別の削除、外部ストレージへのエクスポート、
 * およびテキストファイルからのインポート機能を提供します。
 * Android の Storage Access Framework (SAF) を介して、セキュアなファイル操作を行います。
 * </p>
 */
public class DictionaryTool extends AppCompatActivity {

    /** 辞書マネージャ。BTree データベースへの実操作を委譲します。 */
    private Dictionary mDictionary;
    /** 辞書エクスポート用の保存先指定を処理する結果ランチャー。 */
    private final ActivityResultLauncher<Intent> mExportResultLauncher = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(),
            this::onExportActivityResult);
    /** ListView に辞書エントリ（文字列）を表示するためのアダプター。 */
    private ArrayAdapter<String> mAdapter;
    /** 辞書インポート用のファイル選択を処理する結果ランチャー。 */
    private final ActivityResultLauncher<Intent> mImportResultLauncher = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(),
            this::onImportActivityResult);
    /** アクティビティのオプションメニューへの参照。リストの空き状況に応じた動的制御に使用します。 */
    private Menu mOptionsMenu;

    /**
     * アクティビティ生成時のライフサイクルメソッドです。
     * UI コンポーネントの初期化、辞書データの読み込み、および削除用ダイアログの設定を行います。
     *
     * @param savedInstanceState 保存された状態
     */
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_dictionary_tool);
        mDictionary = new Dictionary(this);

        ListView listView = findViewById(R.id.list_view);
        listView.setEmptyView(findViewById(R.id.empty_text));

        List<String> dataList = new ArrayList<>();

        mAdapter = new ArrayAdapter<>(this, android.R.layout.simple_list_item_1, dataList);
        listView.setAdapter(mAdapter);

        // 初回起動時のデータ同期
        refreshList();

        // 項目タップによる候補個別削除フローの設定
        listView.setOnItemClickListener((parent, view, position, id) -> {
            final String item = (String) parent.getItemAtPosition(position);
            showCandidateSelectionDialog(item);
        });
    }

    /**
     * リスト表示を最新の状態に更新します。
     */
    private void refreshList() {
        mAdapter.clear();
        mAdapter.addAll(exportDictionary());
        mAdapter.notifyDataSetChanged();
        invalidateOptionsMenu();
    }

    /**
     * 指定されたエントリ（見出し語 候補データ）を解析し、候補を個別に削除するためのダイアログを表示します。
     *
     * @param entryText 解析対象の辞書行文字列
     */
    private void showCandidateSelectionDialog(String entryText) {
        String[] parts = entryText.split(" ", 2);
        if (parts.length < 2) return;

        String key = parts[0];
        String value = parts[1]; // 例: "/候補1/候補2/[送り/候補3/]/ "

        // 候補のパース
        List<CandidateItem> candidates = parseCandidates(value);
        String[] displayItems = new String[candidates.size() + 1];
        for (int i = 0; i < candidates.size(); i++) {
            displayItems[i] = candidates.get(i).displayText;
        }
        displayItems[candidates.size()] = "エントリ全体を削除";

        new AlertDialog.Builder(this)
                .setTitle("「" + key + "」の削除項目を選択")
                .setItems(displayItems, (dialog, which) -> {
                    View view = findViewById(R.id.list_view);
                    if (which == candidates.size()) {
                        // エントリ全体の削除
                        new AlertDialog.Builder(this)
                                .setTitle("エントリの一括削除")
                                .setMessage("「" + key + "」のすべての候補を削除しますか？")
                                .setPositiveButton("はい", (d, w) -> {
                                    mDictionary.deleteEntry(key);
                                    Snackbar.make(view, "エントリ「" + key + "」を削除しました", Snackbar.LENGTH_SHORT).show();
                                    refreshList();
                                })
                                .setNegativeButton("いいえ", null)
                                .show();
                    } else {
                        // 特定の候補の削除
                        CandidateItem target = candidates.get(which);
                        mDictionary.removeEntry(key, target.rawValue, target.okurigana);
                        Snackbar.make(view, "「" + target.displayText + "」を削除しました", Snackbar.LENGTH_SHORT).show();
                        refreshList();
                    }
                })
                .setNegativeButton("キャンセル", null)
                .show();
    }

    /**
     * SKK 辞書形式の値をパースして候補リストを生成します。
     *
     * @param value パース対象の値文字列
     * @return 構造化された候補アイテムのリスト
     */
    private List<CandidateItem> parseCandidates(String value) {
        List<CandidateItem> items = new ArrayList<>();
        if (!value.startsWith("/") || value.length() < 2) return items;

        String content = value.substring(1);
        if (content.endsWith("/")) {
            content = content.substring(0, content.length() - 1);
        }

        // 送りありブロックを分離
        String[] blocks = content.split("\\[");

        // 第1ブロックは送りなし候補群
        String[] normalCands = blocks[0].split("/");
        for (String s : normalCands) {
            if (!s.isEmpty()) {
                items.add(new CandidateItem(s, s, null));
            }
        }

        // 第2ブロック以降は送りあり候補群
        for (int i = 1; i < blocks.length; i++) {
            String block = blocks[i].replace("]", "");
            String[] parts = block.split("/");
            if (parts.length >= 2) {
                String okr = parts[0];
                for (int j = 1; j < parts.length; j++) {
                    if (!parts[j].isEmpty()) {
                        items.add(new CandidateItem(parts[j] + "（送り：" + okr + "）", parts[j], okr));
                    }
                }
            }
        }
        return items;
    }

    /**
     * メニューリソースをインフレートし、ActionBar へ配置します。
     *
     * @param menu メニューインスタンス
     * @return 常に true
     */
    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        mOptionsMenu = menu;
        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.dictionary_tool_menu, menu);
        return true;
    }

    /**
     * メニューの表示直前に状態を更新します。
     * リストが空の場合は、エクスポートや全件削除を選択できないように無効化します。
     *
     * @param menu メニューインスタンス
     * @return 常に true
     */
    @Override
    public boolean onPrepareOptionsMenu(Menu menu) {
        if (mOptionsMenu != null) {
            boolean hasItems = mAdapter.getCount() > 0;

            MenuItem exportItem = mOptionsMenu.findItem(R.id.menu_export);
            MenuItem clearItem = mOptionsMenu.findItem(R.id.menu_clear);

            if (exportItem != null) {
                exportItem.setEnabled(hasItems);
            }
            if (clearItem != null) {
                clearItem.setEnabled(hasItems);
            }
        }
        return super.onPrepareOptionsMenu(menu);
    }

    /**
     * オプションメニューの選択イベントを処理します。
     *
     * @param item 選択された項目
     * @return 処理を消費した場合は true
     */
    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        int itemId = item.getItemId();
        if (itemId == R.id.menu_import) {
            onClickImportDictionary();
            return true;
        } else if (itemId == R.id.menu_export) {
            onClickExportDictionary();
            return true;
        } else if (itemId == R.id.menu_clear) {
            onClickClearDictionary();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    /**
     * 外部ファイル用の推奨ファイル名を取得します。
     *
     * @return デフォルトファイル名
     */
    public String getDefaultFileName() {
        return mDictionary.getUserDictionaryName() + ".txt";
    }

    /**
     * テキスト形式のリストから辞書データを取り込みます。
     *
     * @param entries 辞書エントリの文字列リスト
     */
    public void importDictionary(List<String> entries) {
        mDictionary.importUserDictionary(entries);
    }

    /**
     * 現在のユーザー辞書の内容を文字列リストとして取得します。
     *
     * @return 辞書データ全件のリスト
     */
    public List<String> exportDictionary() {
        return mDictionary.exportUserDictionary();
    }

    /**
     * インポート処理（ファイル選択ピッカー）を起動します。
     */
    private void onClickImportDictionary() {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("text/plain");
        intent.putExtra(DocumentsContract.EXTRA_INITIAL_URI, getDefaultFileName());
        mImportResultLauncher.launch(intent);
    }

    /**
     * エクスポート処理（保存先ファイル作成ピッカー）を起動します。
     */
    private void onClickExportDictionary() {
        Intent intent = new Intent(Intent.ACTION_CREATE_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("text/plain");
        intent.putExtra(Intent.EXTRA_TITLE, getDefaultFileName());
        mExportResultLauncher.launch(intent);
    }

    /**
     * インポートピッカーの結果を受け取り、バックグラウンドでのファイル読み込みと辞書反映を行います。
     *
     * @param result ピッカーの戻り値
     */
    private void onImportActivityResult(ActivityResult result) {
        if (result.getResultCode() == Activity.RESULT_OK) {
            Intent resultData = result.getData();
            if (resultData != null) {
                Uri uri = resultData.getData();
                try {
                    InputStream inputStream = getContentResolver().openInputStream(uri);
                    if (inputStream != null) {
                        BufferedReader reader = new BufferedReader((new InputStreamReader(inputStream)));
                        String entry;
                        ArrayList<String> entries = new ArrayList<>();
                        while ((entry = reader.readLine()) != null) {
                            entries.add(entry);
                        }
                        reader.close();
                        importDictionary(entries);
                        refreshList();
                    }
                } catch (Exception ignored) {
                }
            }
        }
    }

    /**
     * エクスポートピッカーの結果を受け取り、辞書データを指定された URI へ書き出します。
     *
     * @param result ピッカーの戻り値
     */
    private void onExportActivityResult(ActivityResult result) {
        if (result.getResultCode() == Activity.RESULT_OK) {
            Intent resultData = result.getData();
            if (resultData != null) {
                Uri uri = resultData.getData();
                try (OutputStream outputStream = getContentResolver().openOutputStream(uri, "wt")) {
                    if (outputStream != null) {
                        try (BufferedWriter writer = new BufferedWriter((new OutputStreamWriter(outputStream)))) {
                            for (String entry : exportDictionary()) {
                                writer.write(entry + "\n");
                            }
                            writer.flush();
                        }
                    }
                } catch (Exception ignored) {
                }
            }
        }
    }

    /**
     * ユーザー辞書の全件削除（初期化）を実行します。
     * 実行前にユーザーへ警告ダイアログを表示します。
     */
    private void onClickClearDictionary() {
        new AlertDialog.Builder(this)
                .setTitle("辞書のクリア")
                .setMessage("ユーザー辞書のすべての単語を削除します。\nよろしいですか？")
                .setPositiveButton("はい、削除します", (dialog, which) -> {
                    mDictionary.clearUserDictionary();
                    Snackbar.make(findViewById(R.id.list_view), "ユーザー辞書をクリアしました", Snackbar.LENGTH_SHORT).show();
                    refreshList();
                })
                .setNegativeButton("いいえ", null)
                .show();
    }

    /**
     * 内部用の候補データ保持クラスです。
     */
    private static class CandidateItem {
        /** 表示用文字列（送り仮名情報などを含む）。 */
        String displayText;
        /** 辞書に保存されている生データ。 */
        String rawValue;
        /** 関連付けられている送り仮名（存在しない場合は null）。 */
        String okurigana;

        CandidateItem(String d, String r, String o) {
            displayText = d;
            rawValue = r;
            okurigana = o;
        }
    }

}
