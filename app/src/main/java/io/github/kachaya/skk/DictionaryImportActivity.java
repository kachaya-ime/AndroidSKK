package io.github.kachaya.skk;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.activity.result.ActivityResult;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.snackbar.Snackbar;

import java.io.InputStream;
import java.nio.charset.CharacterCodingException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import io.github.kachaya.skk.engine.Dictionary;

/**
 * 外部 SKK 辞書（SKK-JISYO.L 等）の Web ダウンロード、インポート、および削除（クリア）を管理するアクティビティです。
 */
public class DictionaryImportActivity extends AppCompatActivity {

    private Dictionary mDictionary;

    private final ActivityResultLauncher<Intent> mImportResultLauncher = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(),
            this::onImportActivityResult);

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_dictionary_import);
        mDictionary = new Dictionary(this);

        Button btnDownload = findViewById(R.id.btn_download_skk);
        Button btnImport = findViewById(R.id.btn_import_skk);
        Button btnClear = findViewById(R.id.btn_clear_imported);

        if (btnDownload != null) {
            btnDownload.setOnClickListener(v -> onClickDownloadSkkDictionary());
        }
        if (btnImport != null) {
            btnImport.setOnClickListener(v -> onClickImportDictionary());
        }
        if (btnClear != null) {
            btnClear.setOnClickListener(v -> onClickClearImportedDictionary());
        }
    }

    /**
     * 定番 SKK 辞書の Web ダウンロードリンク一覧ダイアログを表示し、外部ブラウザを起動します。
     */
    private void onClickDownloadSkkDictionary() {
        String[] titles = new String[]{
                "SKK-JISYO.L (大型辞書 / 推奨)",
                "SKK-JISYO.M (中型辞書)",
                "SKK-JISYO.S (小型辞書)",
                "SKK-JISYO.jinmei (人名辞書)",
                "SKK-JISYO.geo (地名辞書)",
                "SKK-JISYO.propernoun (固有名詞辞書)",
                "SKK-JISYO.station (駅名辞書)",
                "skk-dev 辞書リポジトリ (GitHub)"
        };

        String[] urls = new String[]{
                "https://raw.githubusercontent.com/skk-dev/dict/master/SKK-JISYO.L",
                "https://raw.githubusercontent.com/skk-dev/dict/master/SKK-JISYO.M",
                "https://raw.githubusercontent.com/skk-dev/dict/master/SKK-JISYO.S",
                "https://raw.githubusercontent.com/skk-dev/dict/master/SKK-JISYO.jinmei",
                "https://raw.githubusercontent.com/skk-dev/dict/master/SKK-JISYO.geo",
                "https://raw.githubusercontent.com/skk-dev/dict/master/SKK-JISYO.propernoun",
                "https://raw.githubusercontent.com/skk-dev/dict/master/SKK-JISYO.station",
                "https://github.com/skk-dev/dict"
        };

        new AlertDialog.Builder(this)
                .setTitle("定番SKK辞書のダウンロード (Web)")
                .setItems(titles, (dialog, which) -> {
                    String targetUrl = urls[which];
                    try {
                        Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(targetUrl));
                        startActivity(intent);
                        Snackbar.make(findViewById(android.R.id.content),
                                "ダウンロード完了後、「ローカルファイルからインポート」を選択してください",
                                Snackbar.LENGTH_LONG).show();
                    } catch (Exception e) {
                        Snackbar.make(findViewById(android.R.id.content),
                                "ブラウザの起動に失敗しました",
                                Snackbar.LENGTH_SHORT).show();
                    }
                })
                .setNegativeButton("キャンセル", null)
                .show();
    }

    /**
     * インポート処理（ファイル選択ピッカー）を起動します。
     */
    private void onClickImportDictionary() {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("*/*");
        String[] mimeTypes = new String[]{"text/plain", "text/*", "application/octet-stream", "*/*"};
        intent.putExtra(Intent.EXTRA_MIME_TYPES, mimeTypes);
        mImportResultLauncher.launch(intent);
    }

    private void onImportActivityResult(ActivityResult result) {
        if (result.getResultCode() == Activity.RESULT_OK && result.getData() != null) {
            Uri uri = result.getData().getData();
            if (uri != null) {
                showEncodingSelectionDialog(uri);
            }
        }
    }

    private void showEncodingSelectionDialog(Uri uri) {
        String[] encodings = new String[]{"自動判定（推奨）", "EUC-JP (SKK本家辞書 SKK-JISYO.L 等)", "UTF-8"};
        new AlertDialog.Builder(this)
                .setTitle("文字コードの選択")
                .setSingleChoiceItems(encodings, 0, null)
                .setPositiveButton("インポート開始", (dialog, which) -> {
                    int selectedIndex = ((AlertDialog) dialog).getListView().getCheckedItemPosition();
                    startImportProcess(uri, selectedIndex);
                })
                .setNegativeButton("キャンセル", null)
                .show();
    }

    private void startImportProcess(Uri uri, int encodingMode) {
        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(60, 40, 60, 40);

        ProgressBar progressBar = new ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal);
        progressBar.setIndeterminate(true);
        layout.addView(progressBar);

        TextView progressText = new TextView(this);
        progressText.setText("辞書ファイルを準備中...");
        progressText.setPadding(0, 20, 0, 0);
        layout.addView(progressText);

        boolean[] cancelled = new boolean[]{false};

        AlertDialog progressDialog = new AlertDialog.Builder(this)
                .setTitle("SKK辞書のインポート")
                .setView(layout)
                .setCancelable(false)
                .setNegativeButton("キャンセル", (dialog, which) -> cancelled[0] = true)
                .create();

        progressDialog.show();

        Handler mainHandler = new Handler(Looper.getMainLooper());
        ExecutorService executor = Executors.newSingleThreadExecutor();

        executor.execute(() -> {
            int importedCount = 0;
            Exception errorException = null;

            Dictionary.ImportProgressListener listener = new Dictionary.ImportProgressListener() {
                @Override
                public void onProgress(int count, String currentKey) {
                    mainHandler.post(() -> progressText.setText(count + " 件処理済み (" + currentKey + ")"));
                }

                @Override
                public boolean isCancelled() {
                    return cancelled[0];
                }
            };

            try {
                if (encodingMode == 0) {
                    try (InputStream is = getContentResolver().openInputStream(uri)) {
                        if (is != null) {
                            importedCount = mDictionary.importSkkDictionary(is, "UTF-8", listener);
                        }
                    } catch (CharacterCodingException e) {
                        if (!cancelled[0]) {
                            mainHandler.post(() -> progressText.setText("文字コードを EUC-JP に切り替えて再実行中..."));
                            try (InputStream is2 = getContentResolver().openInputStream(uri)) {
                                if (is2 != null) {
                                    importedCount = mDictionary.importSkkDictionary(is2, "EUC-JP", listener);
                                }
                            }
                        }
                    }
                } else {
                    String targetCharset = (encodingMode == 1) ? "EUC-JP" : "UTF-8";
                    try (InputStream is = getContentResolver().openInputStream(uri)) {
                        if (is != null) {
                            importedCount = mDictionary.importSkkDictionary(is, targetCharset, listener);
                        }
                    }
                }
            } catch (Exception e) {
                errorException = e;
            }

            final int finalCount = importedCount;
            final Exception finalError = errorException;

            mainHandler.post(() -> {
                if (progressDialog.isShowing()) {
                    progressDialog.dismiss();
                }
                if (cancelled[0]) {
                    Snackbar.make(findViewById(android.R.id.content), "インポートが中断されました", Snackbar.LENGTH_SHORT).show();
                } else if (finalError != null) {
                    new AlertDialog.Builder(DictionaryImportActivity.this)
                            .setTitle("インポートエラー")
                            .setMessage("辞書の読み込み中にエラーが発生しました:\n" + finalError.getMessage())
                            .setPositiveButton("OK", null)
                            .show();
                } else {
                    Snackbar.make(findViewById(android.R.id.content), finalCount + " 件のエントリをインポートしました", Snackbar.LENGTH_LONG).show();
                }
            });

            executor.shutdown();
        });
    }

    private void onClickClearImportedDictionary() {
        new AlertDialog.Builder(this)
                .setTitle("インポート辞書のクリア")
                .setMessage("インポートした追加SKK辞書のすべての単語を削除しますか？\n（ユーザー学習辞書は保持されます）")
                .setPositiveButton("はい、削除します", (dialog, which) -> {
                    mDictionary.clearImportedDictionary();
                    Snackbar.make(findViewById(android.R.id.content), "インポート辞書をクリアしました", Snackbar.LENGTH_SHORT).show();
                })
                .setNegativeButton("いいえ", null)
                .show();
    }
}
