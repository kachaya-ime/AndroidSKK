package io.github.kachaya.skk.keyboard;

import android.content.ClipData;
import android.content.ClipDescription;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.DragEvent;
import android.view.HapticFeedbackConstants;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.RadioGroup;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.preference.PreferenceManager;

import com.google.android.flexbox.FlexboxLayout;
import com.google.android.material.tabs.TabLayout;

import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import io.github.kachaya.skk.R;

/**
 * キーボード配列のカスタマイズ（ドラッグ＆ドロップ編集）を行うためのアクティビティです。
 * <p>
 * 画面上部に編集中のレイアウトプレビュー、中央に操作メニュー（保存・初期化）、
 * 下部にキーのパレットを表示します。パレットは「機能」「英数」「記号」をタブで切り替えて使用します。
 * </p>
 */
public class KeyboardCustomizerActivity extends AppCompatActivity {

    public static final String EXTRA_PREF_KEY = "pref_key";
    public static final String EXTRA_TITLE = "title";

    private String mTargetPrefKey;

    private static final Map<String, String> DEFAULT_LAYOUTS = new HashMap<>();

    private void loadDefaultLayouts() {
        if (DEFAULT_LAYOUTS.isEmpty()) {
            DEFAULT_LAYOUTS.put("custom_qwerty_layout_normal", DefaultLayouts.get(this, "custom_qwerty_layout_normal"));
            DEFAULT_LAYOUTS.put("custom_qwerty_layout_shift", DefaultLayouts.get(this, "custom_qwerty_layout_shift"));
            DEFAULT_LAYOUTS.put("custom_qwerty_layout_symbol", DefaultLayouts.get(this, "custom_qwerty_layout_symbol"));
            DEFAULT_LAYOUTS.put("combined_symbols", DefaultLayouts.get(this, "combined_symbols"));
        }
    }

    private LinearLayout mPreviewContainer;
    private View mDeleteZone;
    private FlexboxLayout mPaletteSpecial;
    private FlexboxLayout mPaletteAlpha;
    private FlexboxLayout mPaletteOther;
    private TabLayout mPaletteTabs;

    private Set<String> mUsedChars = new HashSet<>();

    private enum EditMode {NORMAL, SHIFT, SYMBOL}

    private EditMode mEditMode = EditMode.NORMAL;

    private Map<EditMode, String> mLayoutBuffers = new HashMap<>();
    private boolean mHapticEnabled;
    private View mInsertionIndicator;

    private final ActivityResultLauncher<String> mBackupLauncher = registerForActivityResult(
            new ActivityResultContracts.CreateDocument("application/json"),
            this::backupLayout
    );

    private final ActivityResultLauncher<String[]> mRestoreLauncher = registerForActivityResult(
            new ActivityResultContracts.OpenDocument(),
            this::restoreLayout
    );

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        loadDefaultLayouts();
        setContentView(R.layout.activity_keyboard_customizer);

        Intent intent = getIntent();
        mTargetPrefKey = intent.getStringExtra(EXTRA_PREF_KEY);
        if (mTargetPrefKey == null) {
            mTargetPrefKey = "custom_qwerty_layout";
        }

        String title = intent.getStringExtra(EXTRA_TITLE);
        if (title != null) {
            setTitle(title);
        }

        mPreviewContainer = findViewById(R.id.keyboard_preview_container);
        mDeleteZone = findViewById(R.id.delete_zone);
        mPaletteSpecial = findViewById(R.id.key_palette_special);
        mPaletteAlpha = findViewById(R.id.key_palette_alpha);
        mPaletteOther = findViewById(R.id.key_palette_other);
        mPaletteTabs = findViewById(R.id.palette_tabs);

        mPaletteTabs.addTab(mPaletteTabs.newTab().setText("機能"));
        mPaletteTabs.addTab(mPaletteTabs.newTab().setText("英字"));
        mPaletteTabs.addTab(mPaletteTabs.newTab().setText("数字・記号"));

        mPaletteTabs.addOnTabSelectedListener(new TabLayout.OnTabSelectedListener() {
            @Override
            public void onTabSelected(TabLayout.Tab tab) {
                updatePaletteVisibility(tab.getPosition());
            }

            @Override
            public void onTabUnselected(TabLayout.Tab tab) {
            }

            @Override
            public void onTabReselected(TabLayout.Tab tab) {
            }
        });

        RadioGroup modeSelector = findViewById(R.id.mode_selector);
        boolean isSymbolBar = "combined_symbols".equals(mTargetPrefKey);

        if (isSymbolBar) {
            modeSelector.setVisibility(View.GONE);
        }

        modeSelector.setOnCheckedChangeListener((group, checkedId) -> {
            // 現在のモードのレイアウトをバッファに保存
            mLayoutBuffers.put(mEditMode, serializeLayout());

            if (checkedId == R.id.radio_normal) {
                mEditMode = EditMode.NORMAL;
            } else if (checkedId == R.id.radio_shift) {
                mEditMode = EditMode.SHIFT;
            } else if (checkedId == R.id.radio_symbol) {
                mEditMode = EditMode.SYMBOL;
            }

            // 新しいモードのレイアウトを表示
            renderKeyboard(mLayoutBuffers.get(mEditMode));
            updateUsedChars();
            setupPalette();
        });

        mHapticEnabled = PreferenceManager.getDefaultSharedPreferences(this).getBoolean("haptic_feedback", true);

        // 挿入位置を示すインジケーターの初期化
        mInsertionIndicator = new View(this);
        mInsertionIndicator.setBackgroundColor(0x6633B5E5); // 半透明の青色で挿入箇所を強調

        mDeleteZone.setOnDragListener((v, event) -> {
            if (event.getAction() == DragEvent.ACTION_DROP) {
                View draggedView = (View) event.getLocalState();
                if (draggedView != null && draggedView.getParent() != null) {
                    ViewGroup parent = (ViewGroup) draggedView.getParent();
                    parent.removeView(draggedView);
                    refreshAllViews();
                }
                return true;
            }
            return true;
        });

        loadLayout();
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.keyboard_customizer_menu, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        int id = item.getItemId();
        if (id == R.id.action_save) {
            saveLayout();
            return true;
        } else if (id == R.id.action_backup) {
            String fileName = "combined_symbols".equals(mTargetPrefKey) ?
                    "skk_symbol_layout.json" : "skk_qwerty_layout.json";
            mBackupLauncher.launch(fileName);
            return true;
        } else if (id == R.id.action_restore) {
            mRestoreLauncher.launch(new String[]{"application/json", "text/plain"});
            return true;
        } else if (id == R.id.action_reset) {
            resetToDefault();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    private void refreshAllViews() {
        String current = serializeLayout();
        if ("combined_symbols".equals(mTargetPrefKey)) {
            mLayoutBuffers.put(EditMode.NORMAL, current);
            renderKeyboard(current);
        } else {
            mLayoutBuffers.put(mEditMode, current);
            renderKeyboard(current);
        }
        updateUsedChars();
        setupPalette();
    }

    private void updateUsedChars() {
        mUsedChars.clear();
        // mLayoutBuffers に保持されている全モードのレイアウトを走査して使用中の文字を特定する
        for (String layoutStr : mLayoutBuffers.values()) {
            if (layoutStr == null) continue;
            KeyConfig[][] layout = KeyConfig.layoutFromAnyString(layoutStr);
            for (KeyConfig[] row : layout) {
                for (KeyConfig cfg : row) {
                    if (cfg.label != null) {
                        mUsedChars.add(cfg.label);
                    }
                }
            }
        }
    }

    private void loadLayout() {
        mPreviewContainer.removeAllViews();

        if ("combined_symbols".equals(mTargetPrefKey)) {
            String layoutStr = LayoutManager.loadLayout(this, "custom_symbols_layout", DEFAULT_LAYOUTS.get("combined_symbols"));
            mLayoutBuffers.put(EditMode.NORMAL, layoutStr);
            renderKeyboard(layoutStr);
        } else {
            // 各モードのレイアウトを読み込む
            loadIndependentLayout(EditMode.NORMAL, "_normal");
            loadIndependentLayout(EditMode.SHIFT, "_shift");
            loadIndependentLayout(EditMode.SYMBOL, "_symbol");

            renderKeyboard(mLayoutBuffers.get(mEditMode));
        }
        updateUsedChars();
        setupPalette();
    }

    private void loadIndependentLayout(EditMode mode, String suffix) {
        String key = mTargetPrefKey + suffix;
        String defaultVal = DEFAULT_LAYOUTS.get(key);
        if (defaultVal == null) defaultVal = "";

        String saved = LayoutManager.loadLayout(this, key, defaultVal);
        mLayoutBuffers.put(mode, saved);
    }


    private void renderKeyboard(String layoutStr) {
        if (layoutStr == null) {
            return;
        }
        renderKeyboard(KeyConfig.layoutFromAnyString(layoutStr));
    }

    private void renderKeyboard(KeyConfig[][] layout) {
        mPreviewContainer.removeAllViews();
        // レイアウトが空の場合は、編集を可能にするため空の行を1つ作成する
        if (layout == null || layout.length == 0) {
            layout = new KeyConfig[][]{{}};
        }

        for (KeyConfig[] rowConfigs : layout) {
            LinearLayout rowLayout = createRowLayout();
            if (rowConfigs.length == 0) {
                TextView tv = new TextView(this);
                tv.setText("ここへキーをドラッグして追加");
                tv.setTextColor(android.graphics.Color.LTGRAY);
                tv.setGravity(android.view.Gravity.CENTER);
                tv.setLayoutParams(new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
                tv.setTag("placeholder");
                rowLayout.addView(tv);
            } else {
                for (KeyConfig config : rowConfigs) {
                    rowLayout.addView(createKeyView(config, true));
                }
            }
            mPreviewContainer.addView(rowLayout);
        }
    }

    private LinearLayout createRowLayout() {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                (int) getResources().getDimension(R.dimen.button_height));
        row.setLayoutParams(lp);
        row.setOnDragListener(new RowDragListener());
        return row;
    }

    private View createKeyView(KeyConfig config, boolean isInKeyboard) {
        final View view;
        float density = getResources().getDisplayMetrics().density;

        // パレット用の10等分幅計算
        int paletteItemWidth = 0;
        int paletteMargin = 0;
        if (!isInKeyboard) {
            int screenWidth = getResources().getDisplayMetrics().widthPixels;
            int totalPadding = (int) (24 * density);
            int scrollbarWidth = (int) (4 * density);
            int availableWidth = screenWidth - totalPadding - scrollbarWidth;
            paletteItemWidth = availableWidth / 10;
        }

        if (config.code == KeyConfig.CODE_GAP || "GAP".equals(config.label)) {
            // GAPキーの表示（編集画面用）
            TextView tv = new TextView(this);
            tv.setText("Gap");
            tv.setGravity(android.view.Gravity.CENTER);
            tv.setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, 10);

            if (isInKeyboard) {
                LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, config.weight);
                tv.setLayoutParams(lp);
                tv.setBackgroundColor(android.graphics.Color.LTGRAY);
                tv.setTextColor(android.graphics.Color.GRAY);
                tv.setAlpha(0.6f);
            } else {
                FlexboxLayout.LayoutParams lp = new FlexboxLayout.LayoutParams(paletteItemWidth, (int) getResources().getDimension(R.dimen.button_height));
                lp.setMargins(paletteMargin, paletteMargin, paletteMargin, paletteMargin);
                tv.setLayoutParams(lp);
                tv.setBackgroundColor(android.graphics.Color.DKGRAY);
                tv.setTextColor(android.graphics.Color.WHITE);
            }
            view = tv;
        } else {
            // 共通ファクトリを使用してボタンを生成
            Button b = KeyViewFactory.createKeyButton(this, config);

            if (isInKeyboard) {
                b.setMinWidth(0);
                b.setMinimumWidth(0);
                LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, config.weight);
                b.setLayoutParams(lp);
            } else {
                b.setAlpha(1.0f);
                if (mUsedChars.contains(config.label)) {
                    // すでにレイアウトにある文字は、明るい白
                    b.setTextColor(android.graphics.Color.WHITE);
                } else {
                    // 未配置のキーは、グレー
                    b.setTextColor(android.graphics.Color.GRAY);
                }

                FlexboxLayout.LayoutParams lp = new FlexboxLayout.LayoutParams(paletteItemWidth, (int) getResources().getDimension(R.dimen.button_height));
                lp.setMargins(paletteMargin, paletteMargin, paletteMargin, paletteMargin);
                b.setLayoutParams(lp);
            }
            view = b;
        }

        view.setTag(config);

        // タップで編集ダイアログを表示
        view.setOnClickListener(v -> {
            if (mHapticEnabled) {
                v.setHapticFeedbackEnabled(true);
                v.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP, HapticFeedbackConstants.FLAG_IGNORE_GLOBAL_SETTING);
            }
            if (isInKeyboard || config.code == KeyConfig.CODE_GAP || "GAP".equals(config.label)) {
                showKeyEditDialog(config);
            }
        });

        // ロングタップでドラッグ開始
        view.setOnLongClickListener(v -> {
            if (mHapticEnabled) {
                v.setHapticFeedbackEnabled(true);
                v.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS, HapticFeedbackConstants.FLAG_IGNORE_GLOBAL_SETTING);
            }
            ClipData.Item item = new ClipData.Item(config.toString());
            ClipData dragData = new ClipData(config.toString(), new String[]{ClipDescription.MIMETYPE_TEXT_PLAIN}, item);
            View.DragShadowBuilder shadow = new View.DragShadowBuilder(view);
            v.startDragAndDrop(dragData, shadow, v, 0);
            if (isInKeyboard) {
                v.setVisibility(View.INVISIBLE);
            }
            return true;
        });

        // キー本体のOnDragListenerは削除（行単位のRowDragListenerで一括制御し、挿入位置の視覚的フィードバックを実現するため）

        return view;
    }


    private void showKeyEditDialog(KeyConfig config) {
        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(40, 20, 40, 20);

        boolean isGap = config.code == KeyConfig.CODE_GAP || "GAP".equals(config.label);
        boolean isSymbolBar = "combined_symbols".equals(mTargetPrefKey);

        KeyConfigProvider configProvider = null;

        // 記号バー編集時は機能変更を許可しない
        if (!isGap && !isSymbolBar) {
            configProvider = createFunctionalField(layout, config);
        }

        android.widget.EditText editWeight = createField(layout, "ウェイト (1.0標準)", String.valueOf(config.weight));
        editWeight.setInputType(android.text.InputType.TYPE_CLASS_NUMBER | android.text.InputType.TYPE_NUMBER_FLAG_DECIMAL);

        final KeyConfigProvider finalProvider = configProvider;

        new androidx.appcompat.app.AlertDialog.Builder(this)
                .setTitle(isGap ? "GAPの調整" : "キーの編集")
                .setView(layout)
                .setPositiveButton("OK", (dialog, which) -> {
                    try {
                        if (!isGap && finalProvider != null) {
                            finalProvider.applyTo(config);
                        }
                        float weight = Float.parseFloat(editWeight.getText().toString());
                        if (weight > 0) {
                            config.weight = weight;
                        }

                        refreshAllViews();
                    } catch (NumberFormatException e) {
                        // ignore
                    }
                })
                .setNegativeButton("キャンセル", null)
                .show();
    }

    private interface KeyConfigProvider {
        void applyTo(KeyConfig config);
    }

    private KeyConfigProvider createFunctionalField(LinearLayout container, KeyConfig config) {
        TextView tv = new TextView(this);
        tv.setText("入力文字 / 機能");
        tv.setTextSize(12);
        tv.setPadding(0, 16, 0, 0);
        container.addView(tv);

        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);

        android.widget.Spinner spinner = new android.widget.Spinner(this);
        String[] funcLabels = {"(文字入力)", "Shift", "Enter", "Backspace", "Space", "Sym", "Ctrl", "Tab", "左移動", "右移動", "上移動", "下移動"};
        int[] funcCodes = {KeyConfig.CODE_NONE, KeyConfig.CODE_SHIFT, KeyConfig.CODE_ENTER, KeyConfig.CODE_BACKSPACE, KeyConfig.CODE_SPACE, KeyConfig.CODE_SYM, KeyConfig.CODE_CTRL, KeyConfig.CODE_TAB, KeyConfig.CODE_LEFT, KeyConfig.CODE_RIGHT, KeyConfig.CODE_UP, KeyConfig.CODE_DOWN};

        android.widget.ArrayAdapter<String> adapter = new android.widget.ArrayAdapter<>(this, android.R.layout.simple_spinner_item, funcLabels);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinner.setAdapter(adapter);

        android.widget.EditText etLabel = new android.widget.EditText(this);
        etLabel.setLayoutParams(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1.0f));
        etLabel.setHint("表示ラベル");
        etLabel.setText(config.label);

        int selected = 0;
        if (config.code != KeyConfig.CODE_NONE) {
            for (int i = 1; i < funcCodes.length; i++) {
                if (funcCodes[i] == config.code) {
                    selected = i;
                    break;
                }
            }
        }
        spinner.setSelection(selected);

        // 機能が選択された時に、デフォルトのラベルをセットする
        final int[] finalCodes = funcCodes;
        spinner.setOnItemSelectedListener(new android.widget.AdapterView.OnItemSelectedListener() {
            private boolean isInitial = true;

            @Override
            public void onItemSelected(android.widget.AdapterView<?> parent, android.view.View view, int position, long id) {
                if (isInitial) {
                    isInitial = false;
                    return;
                }
                int code = finalCodes[position];
                if (code != KeyConfig.CODE_NONE) {
                    String defaultLabel = KeyConfig.getDefaultLabel(code);
                    if (!defaultLabel.isEmpty()) {
                        etLabel.setText(defaultLabel);
                    }
                }
            }

            @Override
            public void onNothingSelected(android.widget.AdapterView<?> parent) {
            }
        });

        row.addView(spinner);
        row.addView(etLabel);
        container.addView(row);

        return (cfg) -> {
            int pos = spinner.getSelectedItemPosition();
            cfg.code = finalCodes[pos];
            cfg.label = etLabel.getText().toString();
        };
    }

    private android.widget.EditText createField(LinearLayout container, String label, String value) {
        TextView tv = new TextView(this);
        tv.setText(label);
        tv.setTextSize(12);
        container.addView(tv);
        android.widget.EditText et = new android.widget.EditText(this);
        et.setText(value);
        container.addView(et);
        return et;
    }

    private void updatePaletteVisibility(int position) {
        switch (position) {
            case 0:
                mPaletteSpecial.setVisibility(View.VISIBLE);
                mPaletteAlpha.setVisibility(View.GONE);
                mPaletteOther.setVisibility(View.GONE);
                break;
            case 1:
                mPaletteSpecial.setVisibility(View.GONE);
                mPaletteAlpha.setVisibility(View.VISIBLE);
                mPaletteOther.setVisibility(View.GONE);
                break;
            case 2:
                mPaletteSpecial.setVisibility(View.GONE);
                mPaletteAlpha.setVisibility(View.GONE);
                mPaletteOther.setVisibility(View.VISIBLE);
                break;
        }
    }

    /**
     * パレット（キーのストック）を構築します。
     * 設定対象（QWERTY か記号バーか）に応じて、タブの表示やパレットの内容を動的に切り替えます。
     */
    private void setupPalette() {
        mPaletteSpecial.removeAllViews();
        mPaletteAlpha.removeAllViews();
        mPaletteOther.removeAllViews();

        boolean isSymbolBar = "combined_symbols".equals(mTargetPrefKey);

        if (!isSymbolBar) {
            mPaletteTabs.setVisibility(View.VISIBLE);
            for (KeyConfig config : KeyConfig.PALETTE_SPECIAL_KEYS) {
                mPaletteSpecial.addView(createKeyView(config, false));
            }
            fillPaletteDummies(mPaletteSpecial, KeyConfig.PALETTE_SPECIAL_KEYS.size());

            for (KeyConfig config : KeyConfig.PALETTE_ALPHA_KEYS) {
                mPaletteAlpha.addView(createKeyView(config, false));
            }
            fillPaletteDummies(mPaletteAlpha, KeyConfig.PALETTE_ALPHA_KEYS.size());

            for (KeyConfig config : KeyConfig.PALETTE_SYMBOL_KEYS) {
                mPaletteOther.addView(createKeyView(config, false));
            }
            fillPaletteDummies(mPaletteOther, KeyConfig.PALETTE_SYMBOL_KEYS.size());

            updatePaletteVisibility(mPaletteTabs.getSelectedTabPosition());
        } else {
            mPaletteTabs.setVisibility(View.GONE);
            mPaletteSpecial.setVisibility(View.GONE);
            mPaletteAlpha.setVisibility(View.GONE);
            mPaletteOther.setVisibility(View.VISIBLE); // 記号バー編集時は常に記号パレットを表示

            for (KeyConfig config : KeyConfig.PALETTE_SYMBOL_BAR_KEYS) {
                mPaletteOther.addView(createKeyView(config, false));
            }
            fillPaletteDummies(mPaletteOther, KeyConfig.PALETTE_SYMBOL_BAR_KEYS.size());
        }
    }

    /**
     * パレットの各行が中途半端な数で終わった際、左寄せを維持するために不可視のダミービューを挿入します。
     *
     * @param palette      対象の FlexboxLayout
     * @param currentCount 現在の実体キーの数
     */
    private void fillPaletteDummies(FlexboxLayout palette, int currentCount) {
        int remainder = currentCount % 10;
        if (remainder == 0) {
            return;
        }
        int dummiesNeeded = 10 - remainder;

        float density = getResources().getDisplayMetrics().density;
        int screenWidth = getResources().getDisplayMetrics().widthPixels;
        int totalPadding = (int) (24 * density);
        int scrollbarWidth = (int) (4 * density);
        int availableWidth = screenWidth - totalPadding - scrollbarWidth;
        int paletteItemWidth = availableWidth / 10;

        for (int i = 0; i < dummiesNeeded; i++) {
            View dummy = new View(this);
            FlexboxLayout.LayoutParams lp = new FlexboxLayout.LayoutParams(
                    paletteItemWidth, (int) getResources().getDimension(R.dimen.button_height));
            dummy.setLayoutParams(lp);
            dummy.setVisibility(View.INVISIBLE);
            palette.addView(dummy);
        }
    }

    private List<String> serializeAllRows() {
        return null; // 不使用
    }

    /**
     * 現在プレビューコンテナに配置されているキー構成を JSON 文字列にシリアライズします。
     *
     * @return レイアウトを表す JSON 文字列
     */
    private String serializeLayout() {
        List<List<KeyConfig>> layout = serializeLayoutToConfigs();
        KeyConfig[][] arr = new KeyConfig[layout.size()][];
        for (int i = 0; i < layout.size(); i++) {
            arr[i] = layout.get(i).toArray(new KeyConfig[0]);
        }
        return KeyConfig.layoutToJsonString(arr);
    }

    /**
     * 編集したキーボードレイアウトを内部ストレージへ保存します。
     * <p>
     * 通常モード、シフトモード、記号モードのすべてのバッファを永続化し、
     * 更新日時を SharedPreferences に記録することで IME 本体へ変更を通知します。
     * </p>
     */
    private void saveLayout() {
        if ("combined_symbols".equals(mTargetPrefKey)) {
            LayoutManager.saveLayout(this, "custom_symbols_layout", serializeLayout());
        } else {
            // 現在の編集内容をバッファに反映
            mLayoutBuffers.put(mEditMode, serializeLayout());

            // 全モード（通常・シフト・記号）を一括で保存
            LayoutManager.saveLayout(this, mTargetPrefKey + "_normal", mLayoutBuffers.get(EditMode.NORMAL));
            LayoutManager.saveLayout(this, mTargetPrefKey + "_shift", mLayoutBuffers.get(EditMode.SHIFT));
            LayoutManager.saveLayout(this, mTargetPrefKey + "_symbol", mLayoutBuffers.get(EditMode.SYMBOL));
        }

        // 変更を通知するために SharedPreferences を更新
        PreferenceManager.getDefaultSharedPreferences(this).edit()
                .putLong(LayoutManager.PREF_LAYOUT_UPDATED, System.currentTimeMillis())
                .commit();

        Toast.makeText(this, "配置を保存しました", Toast.LENGTH_SHORT).show();
    }

    private List<List<KeyConfig>> serializeLayoutToConfigs() {
        List<List<KeyConfig>> layout = new ArrayList<>();
        for (int idx = 0; idx < mPreviewContainer.getChildCount(); idx++) {
            View child = mPreviewContainer.getChildAt(idx);
            if (child instanceof LinearLayout) {
                LinearLayout rowLayout = (LinearLayout) child;
                List<KeyConfig> rowConfigs = new ArrayList<>();
                for (int j = 0; j < rowLayout.getChildCount(); j++) {
                    View v = rowLayout.getChildAt(j);
                    if (v == mInsertionIndicator || "placeholder".equals(v.getTag())) continue;
                    KeyConfig config = (KeyConfig) v.getTag();
                    if (config != null) {
                        rowConfigs.add(config);
                    }
                }
                layout.add(rowConfigs);
            }
        }
        return layout;
    }

    private void resetToDefault() {
        if ("combined_symbols".equals(mTargetPrefKey)) {
            String defaultSym = DEFAULT_LAYOUTS.get("combined_symbols");
            mLayoutBuffers.put(EditMode.NORMAL, defaultSym);
            renderKeyboard(defaultSym);
        } else {
            // 全モードをデフォルト値にリセット
            mLayoutBuffers.put(EditMode.NORMAL, DEFAULT_LAYOUTS.get(mTargetPrefKey + "_normal"));
            mLayoutBuffers.put(EditMode.SHIFT, DEFAULT_LAYOUTS.get(mTargetPrefKey + "_shift"));
            mLayoutBuffers.put(EditMode.SYMBOL, DEFAULT_LAYOUTS.get(mTargetPrefKey + "_symbol"));
            // 現在表示中のモードのレイアウトを再描画
            renderKeyboard(mLayoutBuffers.get(mEditMode));
        }
        updateUsedChars();
        setupPalette();
    }

    private void backupLayout(Uri uri) {
        if (uri == null) return;
        try (OutputStream os = getContentResolver().openOutputStream(uri, "wt")) {
            if (os == null) return;
            Map<String, KeyConfig[][]> layouts = new LinkedHashMap<>();
            if ("combined_symbols".equals(mTargetPrefKey)) {
                layouts.put("custom_symbols_layout", KeyConfig.layoutFromAnyString(serializeLayout()));
            } else {
                mLayoutBuffers.put(mEditMode, serializeLayout());
                layouts.put(mTargetPrefKey + "_normal", KeyConfig.layoutFromAnyString(mLayoutBuffers.get(EditMode.NORMAL)));
                layouts.put(mTargetPrefKey + "_shift", KeyConfig.layoutFromAnyString(mLayoutBuffers.get(EditMode.SHIFT)));
                layouts.put(mTargetPrefKey + "_symbol", KeyConfig.layoutFromAnyString(mLayoutBuffers.get(EditMode.SYMBOL)));
            }
            String jsonString = KeyConfig.backupToJsonString(layouts);
            os.write(jsonString.getBytes(StandardCharsets.UTF_8));
            Toast.makeText(this, "配置をバックアップしました", Toast.LENGTH_SHORT).show();
        } catch (Exception e) {
            Toast.makeText(this, "バックアップに失敗しました: " + e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    private void restoreLayout(Uri uri) {
        if (uri == null) return;
        try (InputStream is = getContentResolver().openInputStream(uri);
             BufferedReader reader = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8))) {
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) sb.append(line);

            JSONObject json = new JSONObject(sb.toString());
            if ("combined_symbols".equals(mTargetPrefKey)) {
                if (json.has("custom_symbols_layout")) {
                    mLayoutBuffers.put(EditMode.NORMAL, json.getJSONArray("custom_symbols_layout").toString());
                }
            } else {
                String[] suffixes = {"_normal", "_shift", "_symbol"};
                EditMode[] modes = {EditMode.NORMAL, EditMode.SHIFT, EditMode.SYMBOL};
                for (int i = 0; i < suffixes.length; i++) {
                    String key = mTargetPrefKey + suffixes[i];
                    if (json.has(key)) {
                        mLayoutBuffers.put(modes[i], json.getJSONArray(key).toString());
                    }
                }
            }
            renderKeyboard(mLayoutBuffers.get(mEditMode));
            updateUsedChars();
            setupPalette();
            Toast.makeText(this, "配置を復元しました（保存ボタンで確定してください）", Toast.LENGTH_LONG).show();
        } catch (Exception e) {
            Toast.makeText(this, "復元に失敗しました: " + e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    private class RowDragListener implements View.OnDragListener {
        @Override
        public boolean onDrag(View v, DragEvent event) {
            ViewGroup owner = (ViewGroup) v;
            switch (event.getAction()) {
                case DragEvent.ACTION_DRAG_STARTED:
                    // ドラッグ中のキーの幅をインジケーターに反映させる（左右に分かれるエフェクト）
                    View dragged = (View) event.getLocalState();
                    if (dragged != null) {
                        int w = dragged.getWidth();
                        if (w > 0) {
                            mInsertionIndicator.setLayoutParams(new LinearLayout.LayoutParams(
                                    w, (int) getResources().getDimension(R.dimen.button_height)));
                        }
                    }
                    return true;

                case DragEvent.ACTION_DRAG_LOCATION:
                    // 挿入予定位置にインジケーターを移動
                    int index = calculateDropIndex(owner, event.getX());
                    View placeholderView = owner.findViewWithTag("placeholder");
                    if (placeholderView != null) {
                        placeholderView.setVisibility(View.GONE);
                    }
                    if (mInsertionIndicator.getParent() != owner || owner.indexOfChild(mInsertionIndicator) != index) {
                        if (mInsertionIndicator.getParent() != null) {
                            ((ViewGroup) mInsertionIndicator.getParent()).removeView(mInsertionIndicator);
                        }
                        owner.addView(mInsertionIndicator, index);
                    }
                    break;

                case DragEvent.ACTION_DRAG_EXITED:
                    if (mInsertionIndicator.getParent() == owner) {
                        owner.removeView(mInsertionIndicator);
                    }
                    View pView = owner.findViewWithTag("placeholder");
                    if (pView != null) {
                        pView.setVisibility(View.VISIBLE);
                    }
                    break;

                case DragEvent.ACTION_DROP:
                    if (mInsertionIndicator.getParent() != null) {
                        ((ViewGroup) mInsertionIndicator.getParent()).removeView(mInsertionIndicator);
                    }
                    View draggedView = (View) event.getLocalState();
                    String keyData = event.getClipData().getItemAt(0).getText().toString();
                    KeyConfig config = KeyConfig.fromString(keyData);

                    // キーボード内での移動かどうかを判定
                    boolean isFromLayout = false;
                    if (draggedView != null && draggedView.getParent() instanceof ViewGroup) {
                        ViewGroup p = (ViewGroup) draggedView.getParent();
                        if (p.getParent() == mPreviewContainer) {
                            isFromLayout = true;
                        }
                    }

                    if (isFromLayout) {
                        ViewGroup oldParent = (ViewGroup) draggedView.getParent();
                        oldParent.removeView(draggedView);
                    }

                    View newKey = createKeyView(config, true);
                    int dropIndex = calculateDropIndex(owner, event.getX());
                    owner.addView(newKey, dropIndex);
                    newKey.setVisibility(View.VISIBLE);

                    refreshAllViews();
                    break;

                case DragEvent.ACTION_DRAG_ENDED:
                    if (mInsertionIndicator.getParent() == owner) {
                        owner.removeView(mInsertionIndicator);
                    }
                    View endPlaceholder = owner.findViewWithTag("placeholder");
                    if (endPlaceholder != null) {
                        endPlaceholder.setVisibility(View.VISIBLE);
                    }
                    if (event.getLocalState() instanceof View) {
                        ((View) event.getLocalState()).setVisibility(View.VISIBLE);
                    }
                    break;
            }
            return true;
        }

        private int calculateDropIndex(ViewGroup container, float x) {
            // ウェイトに基づいた安定した位置計算を行い、レイアウト変更によるチャタリング（小刻みな揺れ）を防ぐ
            float totalWeight = 0;
            int childCount = container.getChildCount();
            List<KeyConfig> configs = new ArrayList<>();
            for (int i = 0; i < childCount; i++) {
                View child = container.getChildAt(i);
                if (child == mInsertionIndicator || "placeholder".equals(child.getTag())) {
                    continue;
                }
                KeyConfig cfg = (KeyConfig) child.getTag();
                if (cfg != null) {
                    configs.add(cfg);
                    totalWeight += cfg.weight;
                }
            }

            if (totalWeight <= 0) {
                return 0;
            }

            float containerWidth = container.getWidth();
            if (containerWidth <= 0) {
                return 0;
            }

            int indicatorWidth = mInsertionIndicator.getLayoutParams().width;
            // インジケーターが表示されている場合、キーが占有できる幅はその分減少している
            float availableWidth = containerWidth;
            if (mInsertionIndicator.getParent() == container) {
                availableWidth -= indicatorWidth;
            }

            float accumulatedWeight = 0;
            for (int i = 0; i < configs.size(); i++) {
                float w = configs.get(i).weight;
                // 各キーの境界（中央付近）を判定基準にする
                float thresholdX = ((accumulatedWeight + w / 2f) / totalWeight) * availableWidth;
                if (x < thresholdX) {
                    return i;
                }
                accumulatedWeight += w;
            }
            return configs.size();
        }
    }
}
