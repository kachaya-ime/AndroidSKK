package io.github.kachaya.skk;

import android.content.SharedPreferences;
import android.graphics.Typeface;
import android.os.Bundle;
import android.widget.Toast;

import androidx.appcompat.app.ActionBar;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.preference.EditTextPreference;
import androidx.preference.Preference;
import androidx.preference.PreferenceFragmentCompat;
import androidx.preference.PreferenceManager;

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
        /**
         * Preference リソースをロードし、各項目のリスナー設定や初期化を行います。
         * 記号設定用の入力欄への等幅フォント適用や、リセットボタンの処理、アプリ情報の表示などを担当します。
         *
         * @param savedInstanceState 保存された状態
         * @param rootKey            PreferenceScreen のルートキー
         */
        @Override
        public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
            setPreferencesFromResource(R.xml.root_preferences, rootKey);

            // 記号ボタン設定の編集時に位置が分かりやすいよう、入力欄を等幅フォントに設定
            EditTextPreference.OnBindEditTextListener monospaceListener = editText -> editText.setTypeface(Typeface.MONOSPACE);
            EditTextPreference symbolsPrimary = findPreference("symbols_primary");
            if (symbolsPrimary != null) {
                symbolsPrimary.setOnBindEditTextListener(monospaceListener);
            }
            EditTextPreference symbolsSecondary = findPreference("symbols_secondary");
            if (symbolsSecondary != null) {
                symbolsSecondary.setOnBindEditTextListener(monospaceListener);
            }

            // 記号設定を初期状態にリセットするためのボタン処理
            Preference resetPreference = findPreference("reset_symbols");
            if (resetPreference != null) {
                resetPreference.setOnPreferenceClickListener(preference -> {
                    showResetConfirmDialog();
                    return true;
                });
            }

            // アプリバージョンのサマリーに現在のビルド情報を動的に反映
            Preference versionPref = findPreference("app_version");
            if (versionPref != null) {
                versionPref.setSummary(BuildConfig.VERSION_NAME);
            }
        }

        /**
         * 記号設定リセットの最終確認を行うダイアログを表示します。
         */
        private void showResetConfirmDialog() {
            new AlertDialog.Builder(getContext())
                    .setTitle("設定のリセット")
                    .setMessage("記号の設定を初期状態に戻しますか？")
                    .setPositiveButton("戻す", (dialog, which) -> resetPreferences())
                    .setNegativeButton("キャンセル", null)
                    .show();
        }

        /**
         * SharedPreferences から記号関連のカスタム設定を削除し、設定画面を再ロードします。
         */
        private void resetPreferences() {
            SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(getContext());
            prefs.edit()
                    .remove("symbols_primary")
                    .remove("symbols_secondary")
                    .apply();

            // 設定を即座に UI へ反映させるために Preference をロードし直す
            setPreferencesFromResource(R.xml.root_preferences, null);
            Toast.makeText(getContext(), "デフォルトに戻しました", Toast.LENGTH_SHORT).show();
        }
    }
}
