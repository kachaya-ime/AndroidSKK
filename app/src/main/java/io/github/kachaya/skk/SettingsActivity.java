package io.github.kachaya.skk;

import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Bundle;
import android.text.Html;
import android.text.Spanned;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.appcompat.app.ActionBar;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.fragment.app.Fragment;
import androidx.preference.Preference;
import androidx.preference.PreferenceFragmentCompat;
import androidx.preference.PreferenceManager;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Iterator;
import java.util.Map;
import java.util.TreeMap;

import io.github.kachaya.skk.keyboard.KeyConfig;
import io.github.kachaya.skk.keyboard.LayoutManager;

/**
 * SKK の動作設定やカスタマイズを行うための設定画面アクティビティです。
 * <p>
 * Android Jetpack の Preference ライブラリを使用しており、入力ルール、表示設定、記号ボタンの定義などの
 * ユーザー設定を管理します。内部の {@link SettingsFragment} および各サブフラグメントが実際の UI 構築を担当します。
 * </p>
 */
public class SettingsActivity extends AppCompatActivity implements
        PreferenceFragmentCompat.OnPreferenceStartFragmentCallback {

    private final ActivityResultLauncher<String> mBackupLauncher = registerForActivityResult(
            new ActivityResultContracts.CreateDocument("application/json"),
            this::backupSettings
    );

    private final ActivityResultLauncher<String[]> mRestoreLauncher = registerForActivityResult(
            new ActivityResultContracts.OpenDocument(),
            this::restoreSettings
    );

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
            actionBar.setDisplayHomeAsUpEnabled(true);
        }

        getSupportFragmentManager().addOnBackStackChangedListener(() -> {
            ActionBar ab = getSupportActionBar();
            if (ab != null) {
                if (getSupportFragmentManager().getBackStackEntryCount() > 0) {
                    ab.setDisplayHomeAsUpEnabled(true);
                } else {
                    ab.setDisplayHomeAsUpEnabled(true);
                    ab.setTitle(R.string.title_activity_settings);
                }
            }
        });
    }

    @Override
    public boolean onSupportNavigateUp() {
        if (getSupportFragmentManager().popBackStackImmediate()) {
            return true;
        }
        finish();
        return true;
    }

    @Override
    public boolean onPreferenceStartFragment(@NonNull PreferenceFragmentCompat caller, @NonNull Preference pref) {
        final Bundle args = pref.getExtras();
        String fragmentName = pref.getFragment();
        if (fragmentName == null) {
            return false;
        }
        final Fragment fragment = getSupportFragmentManager().getFragmentFactory().instantiate(
                getClassLoader(),
                fragmentName);
        fragment.setArguments(args);
        getSupportFragmentManager().beginTransaction()
                .replace(R.id.settings, fragment)
                .addToBackStack(null)
                .commit();
        if (pref.getTitle() != null && getSupportActionBar() != null) {
            getSupportActionBar().setTitle(pref.getTitle());
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        }
        return true;
    }

    /**
     * ルート設定画面のフラグメントクラスです。
     */
    public static class SettingsFragment extends PreferenceFragmentCompat {

        @Override
        public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
            InputService.setupDefaultPreferences(getContext());
            setPreferencesFromResource(R.xml.root_preferences, rootKey);

            Preference legalInfoPref = findPreference("legal_info");
            if (legalInfoPref != null) {
                legalInfoPref.setOnPreferenceClickListener(preference -> {
                    if (getActivity() instanceof SettingsActivity) {
                        ((SettingsActivity) getActivity()).showLegalInfoDialog();
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
                    if (getActivity() instanceof SettingsActivity) {
                        ((SettingsActivity) getActivity()).mBackupLauncher.launch("skk_backup.json");
                    }
                    return true;
                });
            }

            Preference restorePref = findPreference("restore_settings");
            if (restorePref != null) {
                restorePref.setOnPreferenceClickListener(preference -> {
                    if (getActivity() instanceof SettingsActivity) {
                        ((SettingsActivity) getActivity()).mRestoreLauncher.launch(new String[]{"application/json", "text/plain"});
                    }
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
                                LayoutManager.clearLayouts(requireContext());
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
    }

    /**
     * 入力・変換設定サブ画面のフラグメントクラスです。
     */
    public static class InputSettingsFragment extends PreferenceFragmentCompat {
        @Override
        public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
            setPreferencesFromResource(R.xml.preferences_input, rootKey);
        }
    }

    /**
     * 物理キーボード設定サブ画面のフラグメントクラスです。
     */
    public static class PhysicalKeyboardSettingsFragment extends PreferenceFragmentCompat {
        @Override
        public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
            setPreferencesFromResource(R.xml.preferences_physical_keyboard, rootKey);
        }
    }

    /**
     * 表示設定サブ画面のフラグメントクラスです。
     */
    public static class DisplaySettingsFragment extends PreferenceFragmentCompat {
        @Override
        public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
            setPreferencesFromResource(R.xml.preferences_display, rootKey);
        }
    }

    /**
     * 画面キーボード設定サブ画面のフラグメントクラスです。
     */
    public static class KeyboardSettingsFragment extends PreferenceFragmentCompat {
        @Override
        public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
            setPreferencesFromResource(R.xml.preferences_screen_keyboard, rootKey);
        }
    }

    /**
     * 辞書・学習設定サブ画面のフラグメントクラスです。
     */
    public static class DictionarySettingsFragment extends PreferenceFragmentCompat {
        @Override
        public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
            setPreferencesFromResource(R.xml.preferences_dictionary, rootKey);
        }
    }

    /**
     * 現在の設定（SharedPreferences およびキーボードレイアウト）を JSON ファイルにバックアップします。
     *
     * @param uri 保存先ファイルの URI
     */
    private void backupSettings(Uri uri) {
        if (uri == null) {
            return;
        }
        try (OutputStream os = getContentResolver().openOutputStream(uri, "wt")) {
            SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);
            Map<String, ?> allEntries = prefs.getAll();
            TreeMap<String, Object> sortedMap = new TreeMap<>();

            for (Map.Entry<String, ?> entry : allEntries.entrySet()) {
                String key = entry.getKey();
                Object value = entry.getValue();

                if (key.endsWith("_normal") || key.endsWith("_shift") || key.endsWith("_symbol") ||
                        key.endsWith("_primary") || key.endsWith("_secondary")) {
                    continue;
                }
                sortedMap.put(key, value);
            }

            for (String key : LayoutManager.getManagedKeys()) {
                String layoutStr = LayoutManager.loadLayout(this, key, null);
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
            Toast.makeText(this, "バックアップを保存しました", Toast.LENGTH_SHORT).show();
        } catch (Exception e) {
            Toast.makeText(this, "バックアップの保存に失敗しました: " + e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    /**
     * 指定された JSON ファイルから設定およびキーボードレイアウトを復元します。
     *
     * @param uri 読み込み元ファイルの URI
     */
    private void restoreSettings(Uri uri) {
        if (uri == null) {
            return;
        }
        try (InputStream is = getContentResolver().openInputStream(uri);
             BufferedReader reader = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8))) {

            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                sb.append(line);
            }

            JSONObject json = new JSONObject(sb.toString());
            SharedPreferences.Editor editor = PreferenceManager.getDefaultSharedPreferences(this).edit();

            Iterator<String> keys = json.keys();
            while (keys.hasNext()) {
                String key = keys.next();
                Object value = json.get(key);

                if (value instanceof JSONArray) {
                    String jsonStr = value.toString();
                    LayoutManager.saveLayout(this, key, jsonStr);
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
            editor.putLong(LayoutManager.PREF_LAYOUT_UPDATED, System.currentTimeMillis());
            editor.apply();
            Toast.makeText(this, "設定を復元しました", Toast.LENGTH_SHORT).show();
            recreate();
        } catch (Exception e) {
            Toast.makeText(this, "復元に失敗しました: " + e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    /**
     * assets/legal_info.txt から法的情報を読み込んでダイアログ表示します。
     */
    private void showLegalInfoDialog() {
        StringBuilder markdownBuilder = new StringBuilder();

        try (InputStream inputStream = getAssets().open("legal_info.txt");
             InputStreamReader streamReader = new InputStreamReader(inputStream, StandardCharsets.UTF_8);
             BufferedReader reader = new BufferedReader(streamReader)) {

            String line;
            while ((line = reader.readLine()) != null) {
                markdownBuilder.append(line).append("\n");
            }

            String htmlText = markdownBuilder.toString();
            htmlText = htmlText.replaceAll("(?s)```(.*?)```", "<br><tt>$1</tt><br>");
            htmlText = htmlText.replaceAll("(?m)^###\\s+(.+)$", "<br><b>◆ $1</b><br>");
            htmlText = htmlText.replaceAll("(?m)^##\\s+(.+)$", "<br><b>■ $1</b><hr>");
            htmlText = htmlText.replaceAll("\\*\\*(.*?)\\*\\*", "<b>$1</b>");
            htmlText = htmlText.replaceAll("(?m)^-\\s+(.+)$", "・ $1<br>");
            htmlText = htmlText.replaceAll("\n", "<br>");

            Spanned spannedText = Html.fromHtml(htmlText, Html.FROM_HTML_MODE_LEGACY);

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
