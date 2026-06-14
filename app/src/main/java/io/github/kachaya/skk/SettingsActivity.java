package io.github.kachaya.skk;

import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Bundle;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.ActionBar;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.preference.Preference;
import androidx.preference.PreferenceFragmentCompat;
import androidx.preference.PreferenceManager;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.util.Map;
import java.util.TreeMap;

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
            if (uri == null) return;
            try (OutputStream os = getContext().getContentResolver().openOutputStream(uri)) {
                SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(getContext());
                Map<String, ?> allEntries = prefs.getAll();
                TreeMap<String, Object> sortedMap = new TreeMap<>();

                for (Map.Entry<String, ?> entry : allEntries.entrySet()) {
                    String key = entry.getKey();
                    Object value = entry.getValue();

                    // モダンな独立レイアウトキーは構造化して保存
                    if (key.endsWith("_normal") || key.endsWith("_shift") || key.endsWith("_symbol") ||
                            key.endsWith("_primary") || key.endsWith("_secondary")) {
                        if (value instanceof String) {
                            sortedMap.put(key, KeyConfig.layoutToJson((String) value));
                        }
                    } else {
                        sortedMap.put(key, value);
                    }
                }

                StringBuilder sb = new StringBuilder();
                sb.append("{\n");
                int count = 0;
                int total = sortedMap.size();
                for (Map.Entry<String, Object> entry : sortedMap.entrySet()) {
                    sb.append("  \"").append(entry.getKey()).append("\": ");
                    Object val = entry.getValue();
                    if (val instanceof JSONArray) {
                        // 配列（レイアウト）は全体のインデントに合わせて整形
                        sb.append(((JSONArray) val).toString(2).replace("\n", "\n  "));
                    } else if (val instanceof String) {
                        sb.append(JSONObject.quote((String) val));
                    } else {
                        sb.append(val);
                    }
                    if (++count < total) sb.append(",");
                    sb.append("\n");
                }
                sb.append("}");

                os.write(sb.toString().getBytes());
                Toast.makeText(getContext(), "バックアップを保存しました", Toast.LENGTH_SHORT).show();
            } catch (Exception e) {
                Toast.makeText(getContext(), "バックアップの保存に失敗しました: " + e.getMessage(), Toast.LENGTH_LONG).show();
            }
        }

        private void restoreSettings(Uri uri) {
            if (uri == null) return;
            try (InputStream is = getContext().getContentResolver().openInputStream(uri);
                 BufferedReader reader = new BufferedReader(new InputStreamReader(is))) {

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
                        // 構造化されたレイアウトを内部形式に戻す
                        editor.putString(key, KeyConfig.jsonToLayout((JSONArray) value));
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
                editor.apply();
                Toast.makeText(getContext(), "設定を復元しました", Toast.LENGTH_SHORT).show();
                getActivity().recreate();
            } catch (Exception e) {
                Toast.makeText(getContext(), "復元に失敗しました: " + e.getMessage(), Toast.LENGTH_LONG).show();
            }
        }
    }
}
