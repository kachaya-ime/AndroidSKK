package io.github.kachaya.skk;

import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Bundle;
import android.text.Html;
import android.text.Spanned;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.ActionBar;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.preference.ListPreference;
import androidx.preference.Preference;
import androidx.preference.PreferenceFragmentCompat;
import androidx.preference.PreferenceManager;
import androidx.preference.SwitchPreference;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.TreeMap;

import io.github.kachaya.skk.keyboard.KeyConfig;
import io.github.kachaya.skk.keyboard.LayoutManager;

/**
 * SKK の動作設定やカスタマイズを行うための設定画面アクティビティです。
 * <p>
 * Android Jetpack の Preference ライブラリを使用しており、入力ルール、表示設定、記号ボタンの定義などの
 * ユーザー設定を管理します。内部の {@link SettingsFragment} が実際の UI 構築を担当します。
 * </p>
 */
public class SettingsActivity extends AppCompatActivity {

    /**
     * アクティビティ生成時の初期化を行います。
     * 設定用のフラグメントを画面に配置し、アクションバーのセットアップを行います。
     *
     * @param savedInstanceState 保存された状態
     */
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.settings_activity);
        if (savedInstanceState == null) {
            getSupportFragmentManager()
                    .beginTransaction()
                    .replace(R.id.settings, new SettingsFragment())
                    .commit();
        }
        ActionBar actionBar = getSupportActionBar();
        if (actionBar != null) {
            actionBar.setDisplayHomeAsUpEnabled(false);
        }
    }

    /**
     * 各設定項目の表示と、ユーザー操作に応じた動的な動作定義を行うフラグメントクラスです。
     */
    public static class SettingsFragment extends PreferenceFragmentCompat {

        private final ActivityResultLauncher<String> mBackupLauncher = registerForActivityResult(
                new ActivityResultContracts.CreateDocument("application/json"),
                this::backupSettings
        );

        private final ActivityResultLauncher<String[]> mRestoreLauncher = registerForActivityResult(
                new ActivityResultContracts.OpenDocument(),
                this::restoreSettings
        );

        /**
         * Preference リソースをロードし、各項目のリスナー設定や初期化を行います。
         * 記号設定用の入力欄への等幅フォント適用や、リセットボタンの処理、アプリ情報の表示などを担当します。
         *
         * @param savedInstanceState 保存された状態
         * @param rootKey            PreferenceScreen のルートキー
         */
        @Override
        public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
            // デフォルト値の一括適用（物理キーボード判定を含む）
            InputService.setupDefaultPreferences(getContext());

            setPreferencesFromResource(R.xml.root_preferences, rootKey);

            Preference legalInfoPref = findPreference("legal_info");
            if (legalInfoPref != null) {
                legalInfoPref.setOnPreferenceClickListener(preference -> {
                    // 親アクティビティのメソッドを直接呼び出す
                    if (getActivity() instanceof SettingsActivity) {
                        ((SettingsActivity) getActivity()).showLegalInfoDialog();
                    }
                    return true;
                });
            }

            ListPreference keyboardTypePref = findPreference("keyboard_type");
            SwitchPreference inputSingleLinePref = findPreference("input_single_line");
            if (keyboardTypePref != null && inputSingleLinePref != null) {
                // 初期状態の反映
                String currentType = keyboardTypePref.getValue();
                inputSingleLinePref.setEnabled("symbols".equals(currentType));

                keyboardTypePref.setOnPreferenceChangeListener((preference, newValue) -> {
                    String newType = (String) newValue;
                    boolean isSymbols = "symbols".equals(newType);
                    inputSingleLinePref.setEnabled(isSymbols);
                    if (!isSymbols) {
                        inputSingleLinePref.setChecked(false);
                    }
                    return true;
                });
            }

            // アプリバージョンのサマリーに現在のビルド情報を動的に反映
            Preference versionPref = findPreference("app_version");
            if (versionPref != null) {
                versionPref.setSummary(BuildConfig.VERSION_NAME);
            }

            Preference backupPref = findPreference("backup_settings");
            if (backupPref != null) {
                backupPref.setOnPreferenceClickListener(preference -> {
                    mBackupLauncher.launch("skk_backup.json");
                    return true;
                });
            }

            Preference restorePref = findPreference("restore_settings");
            if (restorePref != null) {
                restorePref.setOnPreferenceClickListener(preference -> {
                    mRestoreLauncher.launch(new String[]{"application/json", "text/plain"});
                    return true;
                });
            }

            Preference resetPref = findPreference("reset_settings");
            if (resetPref != null) {
                resetPref.setOnPreferenceClickListener(preference -> {
                    new AlertDialog.Builder(requireContext())
                            .setTitle("設定の初期化")
                            .setMessage("すべての設定を初期状態に戻しますか？\n（学習辞書は初期化されません）")
                            .setPositiveButton("初期化", (dialog, which) -> {
                                SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(requireContext());
                                prefs.edit().clear().apply();
                                // レイアウトファイルも削除
                                LayoutManager.clearLayouts(requireContext());
                                // デフォルト値を再適用
                                InputService.setupDefaultPreferences(requireContext());
                                Toast.makeText(requireContext(), "設定を初期化しました", Toast.LENGTH_SHORT).show();
                                if (getActivity() != null) {
                                    getActivity().recreate();
                                }
                            })
                            .setNegativeButton("キャンセル", null)
                            .show();
                    return true;
                });
            }
        }

        private void backupSettings(Uri uri) {
            if (uri == null) {
                return;
            }
            try (OutputStream os = getContext().getContentResolver().openOutputStream(uri, "wt")) {
                SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(getContext());
                Map<String, ?> allEntries = prefs.getAll();
                TreeMap<String, Object> sortedMap = new TreeMap<>();

                // まずは通常の SharedPreferences を入れる
                for (Map.Entry<String, ?> entry : allEntries.entrySet()) {
                    String key = entry.getKey();
                    Object value = entry.getValue();

                    // レイアウト関連のキーは後で個別に処理するためここでは除外
                    if (key.endsWith("_normal") || key.endsWith("_shift") || key.endsWith("_symbol") ||
                            key.endsWith("_primary") || key.endsWith("_secondary")) {
                        continue;
                    }
                    sortedMap.put(key, value);
                }

                // LayoutManager から独立したレイアウトを取得
                for (String key : LayoutManager.getManagedKeys()) {
                    String layoutStr = LayoutManager.loadLayout(getContext(), key, null);
                    if (layoutStr != null) {
                        sortedMap.put(key, KeyConfig.layoutFromAnyString(layoutStr));
                    }
                }

                StringBuilder sb = new StringBuilder();
                sb.append("{\n");
                int count = 0;
                int total = sortedMap.size();
                for (Map.Entry<String, Object> entry : sortedMap.entrySet()) {
                    sb.append("  \"").append(entry.getKey()).append("\": ");
                    Object val = entry.getValue();
                    if (val instanceof KeyConfig[][]) {
                        // 配列（レイアウト）は特定の整形を行う
                        sb.append(KeyConfig.layoutToJsonString((KeyConfig[][]) val));
                    } else if (val instanceof String) {
                        sb.append(JSONObject.quote((String) val));
                    } else {
                        sb.append(val);
                    }
                    if (++count < total) sb.append(",");
                    sb.append("\n");
                }
                sb.append("}");

                os.write(sb.toString().getBytes(StandardCharsets.UTF_8));
                Toast.makeText(getContext(), "バックアップを保存しました", Toast.LENGTH_SHORT).show();
            } catch (Exception e) {
                Toast.makeText(getContext(), "バックアップの保存に失敗しました: " + e.getMessage(), Toast.LENGTH_LONG).show();
            }
        }

        private void restoreSettings(Uri uri) {
            if (uri == null) {
                return;
            }
            try (InputStream is = getContext().getContentResolver().openInputStream(uri);
                 BufferedReader reader = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8))) {

                StringBuilder sb = new StringBuilder();
                String line;
                while ((line = reader.readLine()) != null) {
                    sb.append(line);
                }

                JSONObject json = new JSONObject(sb.toString());
                SharedPreferences.Editor editor = PreferenceManager.getDefaultSharedPreferences(getContext()).edit();

                java.util.Iterator<String> keys = json.keys();
                while (keys.hasNext()) {
                    String key = keys.next();
                    Object value = json.get(key);

                    if (value instanceof JSONArray) {
                        // 構造化されたレイアウトをファイルに保存
                        String jsonStr = value.toString();
                        LayoutManager.saveLayout(getContext(), key, jsonStr);
                    } else if (value instanceof Boolean) {
                        editor.putBoolean(key, (Boolean) value);
                    } else if (value instanceof Integer) {
                        editor.putInt(key, (Integer) value);
                    } else if (value instanceof Long) {
                        editor.putLong(key, (Long) value);
                    } else if (value instanceof Float) {
                        editor.putFloat(key, (Float) value);
                    } else if (value instanceof String) {
                        editor.putString(key, (String) value);
                    }
                }
                // レイアウト更新を通知
                editor.putLong(LayoutManager.PREF_LAYOUT_UPDATED, System.currentTimeMillis());
                editor.apply();
                Toast.makeText(getContext(), "設定を復元しました", Toast.LENGTH_SHORT).show();
                getActivity().recreate();
            } catch (Exception e) {
                Toast.makeText(getContext(), "復元に失敗しました: " + e.getMessage(), Toast.LENGTH_LONG).show();
            }
        }
    }

    /**
     * assets/legal_info.txt から法的情報を読み込んでダイアログ表示します。
     * フラグメント側を汚さないよう、アクティビティ側のメソッドとして分離しています。
     */
    private void showLegalInfoDialog() {
        StringBuilder markdownBuilder = new StringBuilder();

        // try-with-resources による安全な自動クローズ
        try (InputStream inputStream = getAssets().open("legal_info.txt");
             InputStreamReader streamReader = new InputStreamReader(inputStream, StandardCharsets.UTF_8);
             BufferedReader reader = new BufferedReader(streamReader)) {

            String line;
            while ((line = reader.readLine()) != null) {
                markdownBuilder.append(line).append("\n");
            }

            // 2. 簡易マークダウン ➡️ HTML 置換処理
            String htmlText = markdownBuilder.toString();

            // バックォート3つ（```）で囲まれたコードブロックの置換処理
            htmlText = htmlText.replaceAll("(?s)```(.*?)```", "<br><tt>$1</tt><br>");

            // 見出しの置換 (### と ##)
            htmlText = htmlText.replaceAll("(?m)^###\\s+(.+)$", "<br><b>◆ $1</b><br>");
            htmlText = htmlText.replaceAll("(?m)^##\\s+(.+)$", "<br><b>■ $1</b><hr>");

            // 太字の置換 (**text**)
            htmlText = htmlText.replaceAll("\\*\\*(.*?)\\*\\*", "<b>$1</b>");

            // 箇条書きの置換 (- 文字)
            htmlText = htmlText.replaceAll("(?m)^-\\s+(.+)$", "・ $1<br>");

            // 改行の保持（マークダウンの改行をHTMLの<br>に変換）
            htmlText = htmlText.replaceAll("\n", "<br>");

            // 3. HTML文字列をAndroidのリッチテキスト(Spanned)に変換
            Spanned spannedText;
            spannedText = Html.fromHtml(htmlText, Html.FROM_HTML_MODE_LEGACY);

            // 4. ダイアログにセットして表示
            new AlertDialog.Builder(this)
                    .setTitle("法的情報・ライセンス")
                    .setMessage(spannedText)
                    .setPositiveButton("閉じる", null)
                    .show();
        } catch (IOException e) {
            Toast.makeText(this, "ファイルの読み込みに失敗しました", Toast.LENGTH_SHORT).show();
            e.printStackTrace();
        }
    }
}
