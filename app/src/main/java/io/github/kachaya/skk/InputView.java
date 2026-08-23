package io.github.kachaya.skk;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;
import android.view.ContextThemeWrapper;
import android.view.HapticFeedbackConstants;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.widget.Button;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;

import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.preference.PreferenceManager;

import java.util.List;

import io.github.kachaya.skk.engine.Candidate;
import io.github.kachaya.skk.keyboard.KeyConfig;
import io.github.kachaya.skk.keyboard.KeyboardState;
import io.github.kachaya.skk.keyboard.KeyboardView;
import io.github.kachaya.skk.keyboard.LayoutManager;
import io.github.kachaya.skk.keyboard.QwertyKeyboardView;
import io.github.kachaya.skk.keyboard.StrokeKeyboardView;
import io.github.kachaya.skk.keyboard.SymbolKeyboardView;

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

    private KeyboardView mCurrentKeyboardView;

    /** 候補表示中にキーボードエリアを隠し、候補のみを表示するかどうかの設定。 */
    private boolean mInputSingleLine;

    // キーボードの状態
    /** 触覚フィードバック（バイブレーション）の有効フラグ。 */
    private boolean mHapticEnabled;

    /** 画面サイズに合わせて調整されたボタンの高さ。 */
    private int mAdjustedButtonHeight;

    /** キーボードの種類 ("symbols" または "qwerty" または "stroke")。 */
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

    /** 現在画面に表示されている候補ボタンの配列。 */
    private Button[] mCandidateButton;

    /** 最後に受け取った EditorInfo。設定変更時の UI 再構築に使用します。 */
    private EditorInfo mLastEditorInfo;

    /** レイアウトの最終更新日時。キャッシュの無効化判定に使用します。 */
    private long mLayoutUpdatedAt = 0;

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

        // システムナビゲーションバー（3ボタンナビ等）との重なりを防止するためのインセット処理
        ViewCompat.setOnApplyWindowInsetsListener(this, (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            // ナビゲーションバーの高さを底部のパディングとして設定
            v.setPadding(v.getPaddingLeft(), v.getPaddingTop(), v.getPaddingRight(), systemBars.bottom);
            return insets;
        });
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        // 小さい画面（Motorola Razrのアウトディスプレイ等）でIMEが画面を占領しすぎないよう、
        // 全体の高さを画面の半分までに制限します。
        // ただし、最小サイズを確保した結果それを超える場合は操作性を優先します。
        int screenHeight = getResources().getDisplayMetrics().heightPixels;
        int maxHeight = Math.max(screenHeight / 2, mAdjustedButtonHeight * 5);

        int heightMode = MeasureSpec.getMode(heightMeasureSpec);
        int heightSize = MeasureSpec.getSize(heightMeasureSpec);

        if (heightMode == MeasureSpec.EXACTLY) {
            heightSize = Math.min(heightSize, maxHeight);
        } else if (heightMode == MeasureSpec.AT_MOST) {
            heightSize = Math.min(heightSize, maxHeight);
        } else {
            heightSize = maxHeight;
            heightMode = MeasureSpec.AT_MOST;
        }

        super.onMeasure(widthMeasureSpec, MeasureSpec.makeMeasureSpec(heightSize, heightMode));
    }

    @Override
    protected void onConfigurationChanged(android.content.res.Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        // 画面の向きやサイズが変わった際に高さを再計算する
        readPrefs();
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
        float heightScale = 1.0f;
        try {
            heightScale = Float.parseFloat(prefs.getString("keyboard_height_scale", "1.0"));
        } catch (Exception e) {
            // ignore
        }
        long updatedAt = prefs.getLong(LayoutManager.PREF_LAYOUT_UPDATED, 0);

        // 以前の値と比較して、実際に変更があったかを確認する
        boolean changed = (mKeyboardType != null) && (
                !type.equals(mKeyboardType) ||
                        updatedAt != mLayoutUpdatedAt ||
                        mInputSingleLine != (singleLine && "symbols".equals(type))
        );

        mHapticEnabled = haptic;
        mKeyboardType = type;
        mInputSingleLine = singleLine && "symbols".equals(type);
        mLayoutUpdatedAt = updatedAt;

        // 画面の高さに応じてボタンの高さを調整する（Motorola Razrのアウトディスプレイ等への対応）
        int screenHeight = getResources().getDisplayMetrics().heightPixels;
        float density = getResources().getDisplayMetrics().density;
        int defaultButtonHeight = (int) (getResources().getDimension(R.dimen.button_height) * heightScale);
        int minButtonHeight = (int) (32 * density); // 最小32dpを確保

        // QWERTY（4行）+ 候補（1行）の計5行が画面の半分に収まるように制限
        int maxAllowedHeight = (screenHeight / 2) / 5;
        int newAdjustedButtonHeight = Math.max(minButtonHeight, Math.min(defaultButtonHeight, maxAllowedHeight));

        if (mAdjustedButtonHeight != newAdjustedButtonHeight) {
            mAdjustedButtonHeight = newAdjustedButtonHeight;
            changed = (mKeyboardType != null); // 高さが変わった場合も再描画が必要
        }

        // 候補ビューの高さを適用
        if (mCandidatesView != null) {
            ViewGroup.LayoutParams lp = mCandidatesView.getLayoutParams();
            if (lp != null) {
                lp.height = mAdjustedButtonHeight;
                mCandidatesView.setLayoutParams(lp);
            }
        }

        if (mCurrentKeyboardView != null) {
            mCurrentKeyboardView.setRowHeight(mAdjustedButtonHeight);
            mCurrentKeyboardView.readPrefs();
        }

        if (changed && mLastEditorInfo != null) {
            doStartInputView(mLastEditorInfo, true);
        }
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
        mCurrentKeyboardView = null;

        int height = LayoutParams.WRAP_CONTENT;

        switch (mKeyboardType) {
            case "qwerty":
                QwertyKeyboardView qv = new QwertyKeyboardView(getContext());
                qv.setRowHeight(mAdjustedButtonHeight);
                qv.setOnKeyActionListener(this::onClickKey);
                mCurrentKeyboardView = qv;
                break;
            case "stroke":
                StrokeKeyboardView sv = new StrokeKeyboardView(getContext());
                sv.setOnKeyActionListener(this::onClickKey);
                sv.setOnHelpListener(() -> mInputService.showStrokeHelp());
                mCurrentKeyboardView = sv;
                height = 4 * mAdjustedButtonHeight;
                break;
            default:
                SymbolKeyboardView syv = new SymbolKeyboardView(getContext());
                syv.setOnKeyActionListener(this::onClickKey);
                mCurrentKeyboardView = syv;
                height = mAdjustedButtonHeight;
                break;
        }

        if (mCurrentKeyboardView != null) {
            mKeyboardLayout.addView(mCurrentKeyboardView, new LayoutParams(LayoutParams.MATCH_PARENT, height));
        }
        updateKeys();
    }

    /**
     * キーボードのラベルを現在の状態に合わせて更新します。
     */
    private void updateKeys() {
        if (mCurrentKeyboardView != null) {
            mCurrentKeyboardView.updateState(new KeyboardState(mIsShifted, mIsShiftLocked, mIsControl, mIsSymbol, mIsSymbolLocked));
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
     * ソフトウェアキーボード上のキーがクリックされた際のメインハンドラです。
     * <p>
     * Shift/Ctrl/Sym 等の修飾キーのトグル管理、および通常の文字・機能キーの
     * サービス（{@link InputService}）への通知を担当します。
     * </p>
     *
     * @param config クリックされたキーの構成情報
     */
    private void onClickKey(KeyConfig config) {
        logI("onClickKey: label=" + config.label + ", code=" + config.code + ", shift=" + mIsShifted);
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
                    char c = key.charAt(0);
                    if (mIsShifted) {
                        c = Character.toUpperCase(c);
                    }
                    mInputService.processKey(c);
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
        if (mInputSingleLine) {
            mKeyboardLayout.setVisibility(GONE);
        } else {
            mKeyboardLayout.setVisibility(VISIBLE);
        }
    }

    /**
     * 候補表示エリアを非表示にします。
     * 1行表示設定の状態に応じて、候補エリアを完全に消す（GONE）か、透明にする（INVISIBLE）かを選択します。    /**
     * QWERTY キーボードの場合は常に領域を確保（INVISIBLE）します。
     */
    public void hideCandidatesView() {
        if (mInputSingleLine) {
            mCandidatesView.setVisibility(GONE);
        } else {
            mCandidatesView.setVisibility(INVISIBLE);
        }
        mKeyboardLayout.setVisibility(VISIBLE);
    }

}
