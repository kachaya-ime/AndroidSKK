package io.github.kachaya.skk;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.ContextThemeWrapper;
import android.view.HapticFeedbackConstants;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.widget.Button;
import android.widget.HorizontalScrollView;
import android.widget.ImageButton;
import android.widget.LinearLayout;

import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.preference.PreferenceManager;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * SKK の入力ビュー（キーボード UI）を管理するクラスです。
 * <p>
 * 変換候補を表示するエリア（HorizontalScrollView）と、記号ボタン等を配置するキーボードエリアを統合して管理します。
 * システム構成（物理キーボードの有無）に応じたレイアウトの自動切り替えや、
 * ユーザー設定（1行表示モード、触覚フィードバック等）に基づく動的な UI 再構築を担当します。
 * </p>
 */
public class InputView extends LinearLayout {

    /** 親となる InputService への参照。キー入力や選択イベントの通知に使用します。 */
    private final InputService mInputService;
    /** 候補ボタンを格納するコンテナレイアウト。 */
    private final LinearLayout mCandidatesLayout;

    // 設定項目
    /** 候補表示エリアのスクロール制御用ビュー。 */
    private final HorizontalScrollView mCandidatesView;
    /** 記号ボタンセットを配置するコンテナレイアウト。 */
    private final LinearLayout mKeyboardLayout;
    /** 現在表示されている記号ボタン（文字ボタン）のリスト。 */
    private final List<Button> mSymbolButtons = new ArrayList<>();
    /** 記号セット（Primary/Secondary）を切り替えるための ImageButton。 */
    private final ImageButton mToggleKeyboardButton;
    /** 候補表示中にキーボードエリアを隠し、候補のみを表示するかどうかの設定。 */
    private boolean mInputSingleLine;

    // キーボードのボタン
    /** ボタン押下時の触覚フィードバック（バイブレーション）の有効フラグ。 */
    private boolean mHapticEnabled;
    /** 記号バー（プライマリー）のリスト。 */
    private List<KeyConfig> mSymbolsPrimary;
    /** 記号バー（セカンダリー）のリスト。 */
    private List<KeyConfig> mSymbolsSecondary;
    /** 現在セカンダリの記号セットが表示されているかどうかの状態。 */
    private boolean mIsAlternativeSymbols = false;

    /** キーボードの種類 ("symbols" または "qwerty")。 */
    private String mKeyboardType;
    /** QWERTY キーボードでの Shift 状態。 */
    private boolean mIsShifted = false;
    /** QWERTY キーボードでの Shift ロック状態。 */
    private boolean mIsShiftLocked = false;
    /** QWERTY キーボードでの Control 状態。 */
    private boolean mIsControl = false;
    /** QWERTY キーボードでの 記号 状態。 */
    private boolean mIsSymbol = false;
    /** QWERTY キーボードでの 記号 ロック状態。 */
    private boolean mIsSymbolLocked = false;

    /** 現在のレイアウト定義（表示中のもの）。 */
    private KeyConfig[][] mCurrentLayout;

    private KeyConfig[][] mNormalLayout;
    private KeyConfig[][] mShiftLayout;
    private KeyConfig[][] mSymbolLayout;

    /** Primary と Secondary のうち、より多い方の記号数。ボタン生成数の基準になります。 */
    private int mMaxButtonCount;
    /** 現在画面に表示されている候補ボタンの配列。 */
    private Button[] mCandidateButton;

    /** 最後に受け取った EditorInfo。設定変更時の UI 再構築に使用します。 */
    private EditorInfo mLastEditorInfo;

    /** レイアウトの最終更新日時。キャッシュの無効化判定に使用します。 */
    private long mLayoutUpdatedAt = 0;

    /** キーリピート用のハンドラ。 */
    private final Handler mRepeatHandler = new Handler(Looper.getMainLooper());
    /** キーリピート用の実行タスク。 */
    private Runnable mRepeatRunnable;

    /**
     * InputView インスタンスを生成し、初期セットアップを行います。
     * レイアウトのインフレート、各ビューの取得、およびトグルボタンの生成を含みます。
     *
     * @param context コンテキスト
     */
    public InputView(Context context) {
        super(context);
        mInputService = (InputService) context;

        readPrefs();

        View layout = LayoutInflater.from(context).inflate(R.layout.input, this);
        mCandidatesView = layout.findViewById(R.id.candidate_view);
        mCandidatesLayout = layout.findViewById(R.id.candidates_layout);
        mKeyboardLayout = layout.findViewById(R.id.keyboard_layout);
        hideCandidatesView();

        // トグルボタンの生成とスタイル設定
        int style = R.style.FunctionButton;
        mToggleKeyboardButton = new ImageButton(new ContextThemeWrapper(context, style), null, 0);
        mToggleKeyboardButton.setOnClickListener(this::onClickToggleKeyboardButton);
        mToggleKeyboardButton.setImageResource(R.drawable.ic_keyboard_swap);

        // システムナビゲーションバー（3ボタンナビ等）との重なりを防止するためのインセット処理
        ViewCompat.setOnApplyWindowInsetsListener(this, (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            // ナビゲーションバーの高さを底部のパディングとして設定
            v.setPadding(v.getPaddingLeft(), v.getPaddingTop(), v.getPaddingRight(), systemBars.bottom);
            return insets;
        });
    }

    /**
     * デバッグビルド時のみログを出力します。
     *
     * @param msg ログメッセージ
     */
    private void logI(String msg) {
        if (BuildConfig.DEBUG) {
            Log.i("InputView", msg);
        }
    }

    /**
     * 最新の設定値を SharedPreferences から読み込み、内部状態を更新します。
     * <p>
     * 設定値が存在しない場合は、ハードウェア構成（物理キーボードの有無）に基づいた
     * 動的なデフォルト値（symbols または qwerty）を選択して適用します。
     * </p>
     */
    public void readPrefs() {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(getContext());

        boolean singleLine = prefs.getBoolean("input_single_line", false);
        boolean haptic = prefs.getBoolean("haptic_feedback", true);

        // 物理キーボードの有無に応じてデフォルト値を決定
        android.content.res.Configuration config = getContext().getResources().getConfiguration();
        boolean hasHardwareKeyboard = (config.keyboard != android.content.res.Configuration.KEYBOARD_NOKEYS &&
                config.keyboard != android.content.res.Configuration.KEYBOARD_UNDEFINED);
        String defaultType = hasHardwareKeyboard ? "symbols" : "qwerty";

        String type = prefs.getString("keyboard_type", defaultType);
        long updatedAt = prefs.getLong(LayoutManager.PREF_LAYOUT_UPDATED, 0);

        boolean changed = (mKeyboardType != null);

        mHapticEnabled = haptic;
        mKeyboardType = type;
        mInputSingleLine = singleLine;
        mLayoutUpdatedAt = updatedAt;

        mNormalLayout = loadIndependentLayout("custom_qwerty_layout", "_normal", KeyConfig.DEFAULT_QWERTY_NORMAL);
        mShiftLayout = loadIndependentLayout("custom_qwerty_layout", "_shift", KeyConfig.DEFAULT_QWERTY_SHIFT);
        mSymbolLayout = loadIndependentLayout("custom_qwerty_layout", "_symbol", KeyConfig.DEFAULT_QWERTY_SYMBOL);

        // 現在のモードに応じたレイアウトを再設定
        if (mIsSymbol) {
            mCurrentLayout = mSymbolLayout;
        } else if (mIsShifted) {
            mCurrentLayout = mShiftLayout;
        } else {
            mCurrentLayout = mNormalLayout;
        }

        KeyConfig[][] symLayout = KeyConfig.layoutFromAnyString(LayoutManager.loadLayout(getContext(), "custom_symbols_layout", KeyConfig.DEFAULT_SYMBOLS_LAYOUT));
        mSymbolsPrimary = symLayout.length > 0 ? Arrays.asList(symLayout[0]) : new ArrayList<>();
        mSymbolsSecondary = symLayout.length > 1 ? Arrays.asList(symLayout[1]) : new ArrayList<>();
        mMaxButtonCount = Math.max(mSymbolsPrimary.size(), mSymbolsSecondary.size());

        if (changed && mLastEditorInfo != null) {
            doStartInputView(mLastEditorInfo, true);
        }
    }

    private KeyConfig[][] loadIndependentLayout(String baseKey, String suffix, String defaultLayout) {
        String key = baseKey + suffix;
        String layoutStr = LayoutManager.loadLayout(getContext(), key, defaultLayout);
        return KeyConfig.layoutFromAnyString(layoutStr);
    }

    /**
     * 入力ビューが表示される際の再初期化処理を行います。
     * エディタの属性（EditorInfo）に応じた処理や、記号ボタンの動的生成・配置を行います。
     *
     * @param editorInfo エディタの情報
     * @param restarting 入力が再開された場合は true
     */
    public void doStartInputView(EditorInfo editorInfo, boolean restarting) {
        logI("doStartInputView: editorInfo=" + editorInfo + ", restarting=" + restarting);
        mLastEditorInfo = editorInfo;

        mKeyboardLayout.removeAllViews();
        mSymbolButtons.clear();

        switch (mKeyboardType) {
            case "qwerty":
                buildKeyboard();
                break;
            default:
                buildSymbolBar();
                break;
        }
    }

    /**
     * 従来の 1 行記号バーを構築します。
     */
    private void buildSymbolBar() {
        LayoutParams lp = new LinearLayout.LayoutParams(0, LayoutParams.MATCH_PARENT, 1.0f);

        // 横方向のコンテナを作成（input.xml の keyboard_layout が vertical になったため）
        LinearLayout row = new LinearLayout(getContext());
        row.setOrientation(LinearLayout.HORIZONTAL);
        mKeyboardLayout.addView(row, new LayoutParams(LayoutParams.MATCH_PARENT, (int) getResources().getDimension(R.dimen.button_height)));

        // 設定された最大数分の記号ボタンを生成して追加
        for (int i = 0; i < mMaxButtonCount; i++) {
            // 初期状態（空）の KeyConfig でボタンを生成
            Button b = KeyViewFactory.createKeyButton(getContext(), new KeyConfig(""));
            b.setOnTouchListener((v, event) -> {
                if (event.getAction() == MotionEvent.ACTION_DOWN && mHapticEnabled) {
                    v.setHapticFeedbackEnabled(true);
                    v.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP, HapticFeedbackConstants.FLAG_IGNORE_GLOBAL_SETTING);
                }
                return false;
            });
            b.setOnClickListener(v -> {
                KeyConfig config = (KeyConfig) v.getTag();
                if (config != null) {
                    onClickKey(config);
                }
            });
            row.addView(b, lp);
            mSymbolButtons.add(b);
        }

        // トグルボタンを末尾に追加
        if (mToggleKeyboardButton.getParent() != null) {
            ((ViewGroup) mToggleKeyboardButton.getParent()).removeView(mToggleKeyboardButton);
        }
        row.addView(mToggleKeyboardButton, lp);

        updateSymbolButtons();
    }

    /**
     * 現在のトグル状態に基づいて、記号ボタンのラベル（テキスト）を更新します。
     * 文字が設定されていないインデックスのボタンは無効化されます。
     */
    private void updateSymbolButtons() {
        List<KeyConfig> currentSet = mIsAlternativeSymbols ? mSymbolsSecondary : mSymbolsPrimary;
        for (int i = 0; i < mSymbolButtons.size(); i++) {
            Button b = mSymbolButtons.get(i);
            if (i < currentSet.size()) {
                KeyConfig config = currentSet.get(i);
                b.setText(config.label);
                b.setTag(config);
                b.setEnabled(true);
                b.setClickable(true);
            } else {
                b.setText("");
                b.setTag(null);
                b.setEnabled(false);
                b.setClickable(false);
            }
        }
    }

    /**
     * QWERTY キーボードを構築します。
     */
    private void buildKeyboard() {
        int height = (int) getResources().getDimension(R.dimen.button_height);

        for (int i = 0; i < mCurrentLayout.length; i++) {
            LinearLayout row = new LinearLayout(getContext());
            row.setOrientation(LinearLayout.HORIZONTAL);
            mKeyboardLayout.addView(row, new LayoutParams(LayoutParams.MATCH_PARENT, height));

            for (int j = 0; j < mCurrentLayout[i].length; j++) {
                KeyConfig config = mCurrentLayout[i][j];

                if (config.code == KeyConfig.CODE_GAP || "GAP".equals(config.label)) {
                    View gapView = new View(getContext());
                    gapView.setTag(config);
                    row.addView(gapView, new LayoutParams(0, LayoutParams.MATCH_PARENT, config.weight));
                    continue;
                }

                // 共通ファクトリを使用してボタンを生成
                Button b = KeyViewFactory.createKeyButton(getContext(), config);

                if (config.isRepeatable()) {
                    setupRepeatKey(b, config);
                } else {
                    b.setOnTouchListener((v, event) -> {
                        if (event.getAction() == MotionEvent.ACTION_DOWN && mHapticEnabled) {
                            v.setHapticFeedbackEnabled(true);
                            v.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP, HapticFeedbackConstants.FLAG_IGNORE_GLOBAL_SETTING);
                        }
                        return false;
                    });
                    b.setOnClickListener(v -> {
                        KeyConfig c = (KeyConfig) v.getTag();
                        onClickKey(c);
                    });
                }

                row.addView(b, new LayoutParams(0, LayoutParams.MATCH_PARENT, config.weight));
            }
        }
        updateKeys();
    }

    /**
     * 指定されたキーにリピート入力を設定します（バックスペースとカーソルキー用）。
     *
     * @param b      対象のボタン
     * @param config キー設定
     */
    private void setupRepeatKey(Button b, KeyConfig config) {
        if (!config.isRepeatable()) {
            return;
        }

        b.setOnTouchListener((v, event) -> {
            switch (event.getAction()) {
                case MotionEvent.ACTION_DOWN:
                    if (mHapticEnabled) {
                        v.setHapticFeedbackEnabled(true);
                        v.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP, HapticFeedbackConstants.FLAG_IGNORE_GLOBAL_SETTING);
                    }
                    if (mRepeatRunnable != null) {
                        mRepeatHandler.removeCallbacks(mRepeatRunnable);
                    }
                    mRepeatRunnable = new Runnable() {
                        @Override
                        public void run() {
                            if (config.isRepeatable()) {
                                onClickKey(config);
                                mRepeatHandler.postDelayed(this, 50); // リピート間隔
                            }
                        }
                    };
                    // 最初の実行
                    if (config.isRepeatable()) {
                        onClickKey(config);
                        mRepeatHandler.postDelayed(mRepeatRunnable, 500); // リピート開始までの待ち時間
                    }
                    v.setPressed(true);
                    return true;

                case MotionEvent.ACTION_UP:
                case MotionEvent.ACTION_CANCEL:
                    if (mRepeatRunnable != null) {
                        mRepeatHandler.removeCallbacks(mRepeatRunnable);
                        mRepeatRunnable = null;
                    }
                    v.setPressed(false);
                    return true;
            }
            return false;
        });
    }

    /**
     * キーボードのラベルを現在の状態に合わせて更新します。
     * 各モードが独立したレイアウトを持つようになったため、レイアウトを切り替えて再構築します。
     */
    private void updateKeys() {
        if (!"qwerty".equals(mKeyboardType)) {
            return;
        }

        KeyConfig[][] nextLayout;
        if (mIsSymbol) {
            nextLayout = mSymbolLayout;
        } else if (mIsShifted) {
            nextLayout = mShiftLayout;
        } else {
            nextLayout = mNormalLayout;
        }

        if (mCurrentLayout != nextLayout) {
            mCurrentLayout = nextLayout;
            mKeyboardLayout.removeAllViews();
            buildKeyboard();
        } else {
            // レイアウト構造が変わらない場合でも、特殊キーの選択状態（背景色など）を更新する
            for (int i = 0; i < mKeyboardLayout.getChildCount(); i++) {
                View v = mKeyboardLayout.getChildAt(i);
                if (v instanceof LinearLayout) {
                    LinearLayout row = (LinearLayout) v;
                    for (int j = 0; j < row.getChildCount(); j++) {
                        View bv = row.getChildAt(j);
                        if (bv instanceof Button) {
                            Button b = (Button) bv;
                            KeyConfig config = (KeyConfig) b.getTag();
                            switch (config.code) {
                                case KeyConfig.CODE_SHIFT:
                                    b.setSelected(mIsShifted);
                                    break;
                                case KeyConfig.CODE_CTRL:
                                    b.setSelected(mIsControl);
                                    break;
                                case KeyConfig.CODE_SYM:
                                    b.setSelected(mIsSymbol);
                                    break;
                            }
                        }
                    }
                }
            }
        }
    }

    /**
     * 一時的な状態（Shift/Sym）を解除し、通常レイアウトに戻します。
     */
    private void resetModifiers() {
        boolean changed = false;
        if (!mIsShiftLocked && mIsShifted) {
            mIsShifted = false;
            changed = true;
        }
        if (!mIsSymbolLocked && mIsSymbol) {
            mIsSymbol = false;
            changed = true;
        }
        if (changed) {
            updateKeys();
        }
    }

    /**
     * キーのクリック処理。
     */
    private void onClickKey(KeyConfig config) {
        if (config.code != KeyConfig.CODE_NONE) {
            switch (config.code) {
                case KeyConfig.CODE_SHIFT:
                    if (mIsShiftLocked) {
                        mIsShiftLocked = false;
                        mIsShifted = false;
                    } else if (mIsShifted) {
                        mIsShiftLocked = true;
                    } else {
                        mIsShifted = true;
                    }
                    mIsControl = false;
                    mIsSymbol = false;
                    mIsSymbolLocked = false;
                    updateKeys();
                    break;
                case KeyConfig.CODE_CTRL:
                    mIsControl = !mIsControl;
                    mIsShifted = mIsShiftLocked && !mIsControl;
                    mIsSymbol = false;
                    mIsSymbolLocked = false;
                    updateKeys();
                    break;
                case KeyConfig.CODE_SYM:
                    if (mIsSymbolLocked) {
                        mIsSymbolLocked = false;
                        mIsSymbol = false;
                    } else if (mIsSymbol) {
                        mIsSymbolLocked = true;
                    } else {
                        mIsSymbol = true;
                    }
                    mIsShifted = mIsShiftLocked && !mIsSymbol;
                    mIsControl = false;
                    updateKeys();
                    break;
                case KeyConfig.CODE_TAB:
                    mInputService.handleTab(mIsShifted);
                    resetModifiers();
                    break;
                case KeyConfig.CODE_LEFT:
                    mInputService.handleDpad(KeyEvent.KEYCODE_DPAD_LEFT);
                    resetModifiers();
                    break;
                case KeyConfig.CODE_RIGHT:
                    mInputService.handleDpad(KeyEvent.KEYCODE_DPAD_RIGHT);
                    resetModifiers();
                    break;
                case KeyConfig.CODE_UP:
                    mInputService.handleDpad(KeyEvent.KEYCODE_DPAD_UP);
                    resetModifiers();
                    break;
                case KeyConfig.CODE_DOWN:
                    mInputService.handleDpad(KeyEvent.KEYCODE_DPAD_DOWN);
                    resetModifiers();
                    break;
                case KeyConfig.CODE_BACKSPACE:
                    mInputService.handleBackspace();
                    resetModifiers();
                    break;
                case KeyConfig.CODE_ENTER:
                    mInputService.handleEnter();
                    resetModifiers();
                    break;
                case KeyConfig.CODE_SPACE:
                    mInputService.processKey(' ');
                    resetModifiers();
                    break;
            }
        } else {
            String key = config.label;
            if (key != null && key.length() == 1) {
                if (mIsControl) {
                    int keyCode = getKeyCode(key.charAt(0));
                    if (keyCode != KeyEvent.KEYCODE_UNKNOWN) {
                        mInputService.handleCtrlKey(keyCode);
                    }
                    mIsControl = false;
                    mIsShifted = mIsShiftLocked;
                    updateKeys();
                } else {
                    mInputService.processKey(key.charAt(0));
                    resetModifiers();
                }
            }
        }
    }

    /**
     * 文字から KeyEvent のキーコードを取得します（Ctrl用）。
     */
    private int getKeyCode(char c) {
        char lower = Character.toLowerCase(c);
        if (lower >= 'a' && lower <= 'z') {
            return KeyEvent.KEYCODE_A + (lower - 'a');
        }
        switch (lower) {
            case '[':
                return KeyEvent.KEYCODE_LEFT_BRACKET;
            case ']':
                return KeyEvent.KEYCODE_RIGHT_BRACKET;
            case '\\':
                return KeyEvent.KEYCODE_BACKSLASH;
            case '/':
                return KeyEvent.KEYCODE_SLASH;
            case ',':
                return KeyEvent.KEYCODE_COMMA;
            case '.':
                return KeyEvent.KEYCODE_PERIOD;
        }
        return KeyEvent.KEYCODE_UNKNOWN;
    }

    /**
     * 記号セット切り替えボタンのクリック処理。
     * 状態を反転させ、ボタンの表示を更新します。
     *
     * @param v クリックされた ImageButton
     */
    private void onClickToggleKeyboardButton(View v) {
        if (mHapticEnabled) {
            v.setHapticFeedbackEnabled(true);
            v.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP, HapticFeedbackConstants.FLAG_IGNORE_GLOBAL_SETTING);
        }
        mIsAlternativeSymbols = !mIsAlternativeSymbols;
        updateSymbolButtons();
    }


    /**
     * 候補ボタンがタップされた際の処理。
     * ボタンの Tag に格納されたインデックスを取得し、InputService へ通知します。
     *
     * @param v クリックされた候補 Button
     */
    private void onClickCandidateButton(View v) {
        if (mHapticEnabled) {
            v.setHapticFeedbackEnabled(true);
            v.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP, HapticFeedbackConstants.FLAG_IGNORE_GLOBAL_SETTING);
        }
        int index = (int) v.getTag();
        mInputService.pickCandidateViewManually(index);
    }

    /**
     * 候補表示エリアの内容を完全にクリアします。
     */
    private void clearCandidates() {
        mCandidatesLayout.removeAllViews();
        mCandidateButton = null;
    }

    /**
     * 指定された候補リストに基づいて、候補表示エリアにボタンを生成・配置します。
     * 生成後、最初の候補を選択（ハイライト）状態にします。
     *
     * @param candidates 表示する候補文字列のリスト
     */
    public void setCandidates(List<String> candidates) {
        clearCandidates();
        if (candidates == null) {
            return;
        }

        mCandidateButton = new Button[candidates.size()];
        int style = R.style.CandidateButton;
        LayoutParams lp = new LinearLayout.LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.MATCH_PARENT);

        for (int i = 0; i < candidates.size(); i++) {
            Button b = new Button(new ContextThemeWrapper(getContext(), style), null, style);
            b.setOnClickListener(this::onClickCandidateButton);
            b.setTag(i);
            b.setText(candidates.get(i));
            mCandidatesLayout.addView(b, lp);
            mCandidateButton[i] = b;
        }
        selectCandidate(0);
    }

    /**
     * 詳細な候補情報（ユーザー辞書フラグ等を含む）を元にボタンを生成します。
     *
     * @param candidates 候補オブジェクトのリスト
     */
    public void setCandidateObjects(List<Candidate> candidates) {
        clearCandidates();
        if (candidates == null) {
            return;
        }

        mCandidateButton = new Button[candidates.size()];
        LayoutParams lp = new LinearLayout.LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.MATCH_PARENT);

        for (int i = 0; i < candidates.size(); i++) {
            Candidate c = candidates.get(i);

            // 辞書の種類に応じて適用するスタイルを切り替える
            int style = c.isUserDict ? R.style.UserCandidateButton : R.style.CandidateButton;

            Button b = new Button(new ContextThemeWrapper(getContext(), style), null, style);
            b.setOnClickListener(this::onClickCandidateButton);
            b.setTag(i);

            String label = c.candidate;
            if (c.annotation != null) {
                label += ";" + c.annotation;
            }
            b.setText(label);

            mCandidatesLayout.addView(b, lp);
            mCandidateButton[i] = b;
        }
        selectCandidate(0);
    }

    /**
     * 指定されたインデックスの候補を視覚的に選択状態（ハイライト）にします。
     * また、選択された候補が画面中央に来るようにスクロール位置を調整します。
     *
     * @param index 選択する候補のインデックス
     */
    public void selectCandidate(int index) {
        if (mCandidateButton == null || index < 0 || index >= mCandidateButton.length) {
            return;
        }
        mCandidatesView.post(() -> {
            for (Button button : mCandidateButton) {
                button.setSelected(false);
            }
            final Button selectedButton = mCandidateButton[index];
            selectedButton.setSelected(true);

            // スクロール位置の計算：選択されたボタンが中央に配置されるように調整
            int buttonLeft = selectedButton.getLeft();
            int buttonWidth = selectedButton.getWidth();
            int viewWidth = mCandidatesView.getWidth();
            int scrollToX = buttonLeft + (buttonWidth / 2) - (viewWidth / 2);
            mCandidatesView.scrollTo(scrollToX, 0);
        });
    }

    /**
     * 候補表示エリアを可視化します。
     * 1行表示設定が有効な場合は、キーボードエリアを非表示にして候補エリアを優先します。
     */
    public void showCandidatesView() {
        mCandidatesView.setVisibility(VISIBLE);
        switch (mKeyboardType) {
            case "qwerty":
                mKeyboardLayout.setVisibility(VISIBLE);
                break;
            default:
                if (mInputSingleLine) {
                    mKeyboardLayout.setVisibility(GONE);
                } else {
                    mKeyboardLayout.setVisibility(VISIBLE);
                }
                break;
        }
    }

    /**
     * 候補表示エリアを非表示にします。
     * 1行表示設定の状態に応じて、候補エリアを完全に消す（GONE）か、透明にする（INVISIBLE）かを選択します。    /**
     * QWERTY キーボードの場合は常に領域を確保（INVISIBLE）します。
     */
    public void hideCandidatesView() {
        switch (mKeyboardType) {
            case "qwerty":
                mCandidatesView.setVisibility(INVISIBLE);
                break;
            default:
                if (mInputSingleLine) {
                    mCandidatesView.setVisibility(GONE);
                } else {
                    mCandidatesView.setVisibility(INVISIBLE);
                }
                break;
        }
        mKeyboardLayout.setVisibility(VISIBLE);
    }

}
