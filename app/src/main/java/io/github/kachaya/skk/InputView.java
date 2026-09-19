package io.github.kachaya.skk;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.graphics.Color;
import android.util.Log;
import android.util.TypedValue;
import android.view.ContextThemeWrapper;
import android.view.Gravity;
import android.view.HapticFeedbackConstants;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;
import android.view.inputmethod.EditorInfo;
import android.widget.Button;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.PopupWindow;
import android.widget.ScrollView;
import android.widget.TextView;

import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.preference.PreferenceManager;

import com.google.android.flexbox.FlexboxLayout;

import java.util.List;

import io.github.kachaya.skk.engine.Candidate;
import io.github.kachaya.skk.keyboard.DefaultLayouts;
import io.github.kachaya.skk.keyboard.EmojiKeyboardView;
import io.github.kachaya.skk.keyboard.KeyConfig;
import io.github.kachaya.skk.keyboard.KeyboardState;
import io.github.kachaya.skk.keyboard.KeyboardView;
import io.github.kachaya.skk.keyboard.LayoutManager;
import io.github.kachaya.skk.keyboard.QwertyKeyboardView;
import io.github.kachaya.skk.keyboard.StrokeKeyboardView;
import io.github.kachaya.skk.keyboard.SymbolKeyboardView;
import io.github.kachaya.skk.keyboard.TabletKeyboardView;

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
    /** 候補バーの全体レイアウト。 */
    private final View mCandidateBarLayout;

    // 設定項目
    /** 候補表示エリアのスクロール制御用ビュー。 */
    private final HorizontalScrollView mCandidatesView;
    /** 記号ボタンセットを配置するコンテナレイアウト。 */
    private final LinearLayout mKeyboardLayout;
    /** 展開表示用のスクロールビュー。 */
    private ScrollView mCandidateExpandedScroll;
    /** 展開表示用のFlexboxレイアウト。 */
    private FlexboxLayout mCandidateExpandedFlexbox;
    /** 候補の展開・折りたたみボタン。 */
    private Button mBtnExpandCandidates;
    /** 候補が展開されているかどうかのフラグ。 */
    private boolean mIsExpanded = false;

    private KeyboardView mCurrentKeyboardView;

    // キーボードの状態
    /** 触覚フィードバック（バイブレーション）の有効フラグ。 */
    private boolean mHapticEnabled;

    /** 画面サイズに合わせて調整されたボタンの高さ。 */
    private int mAdjustedButtonHeight;
    /** 画面サイズに合わせて調整された候補ビューの高さ。 */
    private int mAdjustedCandidateHeight;
    /** 画面サイズに合わせて調整された候補のテキストサイズ（ピクセル）。 */
    private float mAdjustedCandidateTextSize;

    /** キーボードの種類 ("symbols" または "qwerty" または "stroke")。 */
    private String mKeyboardType;

    /** ストロークキーボードの寄せ方向 ("left", "center", "right")。 */
    private String mStrokeAlign;
    /** ストロークキーボードの幅の倍率。 */
    private float mStrokeWidthScale;
    /** Tablet キーボードの寄せ方向 ("left", "center", "right")。 */
    private String mTabletAlign;
    /** Tablet キーボードの幅の倍率。 */
    private float mTabletWidthScale;

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
    /** 現在のモードテキスト（「あ」「ア」等）。 */
    private String mModeText = "";
    /** 現在のモードアイコンのリソース ID。 */
    private int mModeIconResId = 0;

    /** 現在画面に表示されている候補ボタンの配列。 */
    private Button[] mCandidateButton;

    /** 最後に受け取った EditorInfo。設定変更時の UI 再構築に使用します。 */
    private EditorInfo mLastEditorInfo;

    /** レイアウトの最終更新日時。キャッシュの無効化判定に使用します。 */
    private long mLayoutUpdatedAt = 0;
    /** キーボードの行数（レイアウトに基づく）。 */
    private int mRowCount = 4;

    /**
     * InputView インスタンスを生成し、初期セットアップを行います。
     * レイアウトのインフレート、各ビューの取得、およびトグルボタンの生成を含みます。
     *
     * @param context コンテキスト
     */
    public InputView(Context context) {
        this(context instanceof InputService ? (InputService) context : null, InputService.getThemedContext(context));
    }

    /**
     * InputView インスタンスを生成し、初期セットアップを行います。
     * レイアウトのインフレート、各ビューの取得、およびトグルボタンの生成を含みます。
     *
     * @param inputService 入力サービス
     * @param themedContext テーマ適用済みコンテキスト
     */
    public InputView(InputService inputService, Context themedContext) {
        super(themedContext);
        mInputService = inputService;

        readPrefs();

        View layout = LayoutInflater.from(themedContext).inflate(R.layout.input, this);
        mCandidateBarLayout = layout.findViewById(R.id.candidate_bar_layout);
        mCandidatesView = layout.findViewById(R.id.candidate_view);
        mCandidatesLayout = layout.findViewById(R.id.candidates_layout);
        mKeyboardLayout = layout.findViewById(R.id.keyboard_layout);
        mCandidateExpandedScroll = layout.findViewById(R.id.candidate_expanded_scroll);
        mCandidateExpandedFlexbox = layout.findViewById(R.id.candidate_expanded_flexbox);
        mBtnExpandCandidates = layout.findViewById(R.id.btn_expand_candidates);
        if (mBtnExpandCandidates != null) {
            mBtnExpandCandidates.setOnClickListener(v -> toggleExpanded());
        }
        hideCandidatesView();

        // システムナビゲーションバー（3ボタンナビ等）との重なりを防止するためのインセット処理
        ViewCompat.setOnApplyWindowInsetsListener(this, (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            // ナビゲーションバーの高さを底部のパディングとして設定し、
            // 左右のインセット（カメラパンチホール等）も考慮する
            v.setPadding(systemBars.left, v.getPaddingTop(), systemBars.right, systemBars.bottom);

            // インセットが変わった場合（回転時など）、ボタンの高さを再計算する必要がある可能性がある
            readPrefs();
            return insets;
        });
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        if ("none".equals(mKeyboardType)) {
            super.onMeasure(widthMeasureSpec, MeasureSpec.makeMeasureSpec(1, MeasureSpec.EXACTLY));
            return;
        }
        // 小さい画面（Motorola Razrのアウトディスプレイ等）でIMEが画面を占領しすぎないよう、
        // 全体の高さを画面の半分までに制限します。
        // ただし、最小サイズを確保した結果それを超える場合は操作性を優先します。
        int screenHeight = getResources().getDisplayMetrics().heightPixels;
        int screenWidth = getResources().getDisplayMetrics().widthPixels;
        // ナビゲーションバーのパディング分を考慮して最大高さを計算する
        int expectedKeyboardHeight;
        if ("stroke".equals(mKeyboardType)) {
            int baseSize = Math.min(screenWidth, screenHeight);
            expectedKeyboardHeight = (int) (baseSize * mStrokeWidthScale) / 2;
        } else if ("emoji".equals(mKeyboardType)) {
            expectedKeyboardHeight = mAdjustedButtonHeight * 5;
        } else {
            expectedKeyboardHeight = mAdjustedButtonHeight * mRowCount;
        }
        int candidateHeight = (mCandidateBarLayout != null && mCandidateBarLayout.getVisibility() != GONE) ? mAdjustedCandidateHeight : 0;
        int maxHeight = Math.max(screenHeight / 2, candidateHeight + expectedKeyboardHeight) + getPaddingBottom();

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
    protected void onConfigurationChanged(Configuration newConfig) {
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

        boolean haptic = prefs.getBoolean("haptic_feedback", true);

        // 物理キーボードの有無に応じてデフォルト値を決定
        Configuration config = getContext().getResources().getConfiguration();
        boolean hasHardwareKeyboard = (config.keyboard != Configuration.KEYBOARD_NOKEYS &&
                config.keyboard != Configuration.KEYBOARD_UNDEFINED);
        String defaultType = hasHardwareKeyboard ? "symbols" : "qwerty";

        String type = prefs.getString("keyboard_type", defaultType);
        mStrokeAlign = prefs.getString("stroke_align", "right");
        mTabletAlign = prefs.getString("tablet_align", "right");
        float strokeWidthScale = 1.0f;
        try {
            strokeWidthScale = Float.parseFloat(prefs.getString("stroke_width_scale", "1.0"));
        } catch (Exception e) {
            // ignore
        }
        mStrokeWidthScale = strokeWidthScale;

        float tabletWidthScale = 1.0f;
        try {
            tabletWidthScale = Float.parseFloat(prefs.getString("tablet_width_scale", "1.0"));
        } catch (Exception e) {
            // ignore
        }
        mTabletWidthScale = tabletWidthScale;

        float heightScale = 1.0f;
        try {
            String heightPrefKey = "keyboard_height_scale_" + type;
            String heightVal = prefs.getString(heightPrefKey, null);
            if (heightVal == null) {
                heightVal = prefs.getString("keyboard_height_scale", "1.0");
            }
            heightScale = Float.parseFloat(heightVal);
        } catch (Exception e) {
            // ignore
        }
        float candidateHeightScale = 1.0f;
        try {
            candidateHeightScale = Float.parseFloat(prefs.getString("candidate_height_scale", "1.0"));
        } catch (Exception e) {
            // ignore
        }
        float candidateTextSizeScale = 1.0f;
        try {
            candidateTextSizeScale = Float.parseFloat(prefs.getString("candidate_text_size_scale", "1.0"));
        } catch (Exception e) {
            // ignore
        }
        long updatedAt = prefs.getLong(LayoutManager.PREF_LAYOUT_UPDATED, 0);

        // 以前の値と比較して、実際に変更があったかを確認する
        boolean changed = (mKeyboardType != null) && (
                !type.equals(mKeyboardType) ||
                        updatedAt != mLayoutUpdatedAt ||
                        mStrokeWidthScale != strokeWidthScale ||
                        mTabletWidthScale != tabletWidthScale ||
                        !mStrokeAlign.equals(prefs.getString("stroke_align", "right")) ||
                        !mTabletAlign.equals(prefs.getString("tablet_align", "right"))
        );
        mStrokeAlign = prefs.getString("stroke_align", "right");
        mTabletAlign = prefs.getString("tablet_align", "right");
        mStrokeWidthScale = strokeWidthScale;
        mTabletWidthScale = tabletWidthScale;

        mHapticEnabled = haptic;
        mKeyboardType = type;
        mLayoutUpdatedAt = updatedAt;

        // 画面の高さに応じてボタンの高さを調整する（Motorola Razrのアウトディスプレイ等への対応）
        int screenHeight = getResources().getDisplayMetrics().heightPixels;
        float density = getResources().getDisplayMetrics().density;
        int defaultButtonHeight = (int) (getResources().getDimension(R.dimen.button_height) * heightScale);

        // レイアウトの行数を取得する（QWERTYは4行とは限らない。ストローク・なしはキーを持たない）
        if ("stroke".equals(type) || "none".equals(type)) {
            mRowCount = 0; // キーを持たない
        } else if ("symbols".equals(type)) {
            mRowCount = 1;
        } else {
            String baseKey = "tablet".equals(type) ? "custom_tablet_layout" : "custom_qwerty_layout";
            String layoutStr = LayoutManager.loadLayout(getContext(), baseKey + "_normal", DefaultLayouts.get(getContext(), baseKey + "_normal"));
            KeyConfig[][] layout = KeyConfig.layoutFromAnyString(layoutStr);
            mRowCount = Math.max(1, layout.length);
        }

        // ストローク・なしキーボードの場合はキーをタッチするわけではない（キー自体が存在しない）ので、
        // 最小高さの制限を設けない
        int minButtonHeight = ("stroke".equals(type) || "none".equals(type)) ? 0 : (int) (32 * density);

        // 各キーボードの行数 + 候補（1行）の計が「使用可能な画面の半分」に収まるように制限
        // ナビゲーションバーのパディング（getPaddingBottom）を差し引いた高さを基準にする
        int availableHeight = Math.max(0, screenHeight - getPaddingBottom());
        int totalRows;
        if ("stroke".equals(type)) {
            // ストロークの場合、キーボードの高さは幅に依存するため、
            // ここでは候補ビューのための「標準的な」高さを確保するために 5 行分（4行+候補1行）として計算
            totalRows = 5;
        } else if ("none".equals(type)) {
            totalRows = 1;
        } else {
            totalRows = mRowCount + 1;
        }
        int maxAllowedHeight = (availableHeight / 2) / totalRows;
        int newAdjustedButtonHeight = Math.max(minButtonHeight, Math.min(defaultButtonHeight, maxAllowedHeight));

        int newAdjustedCandidateHeight = (int) (getResources().getDimension(R.dimen.candidate_height) * candidateHeightScale);
        float newAdjustedCandidateTextSize = getResources().getDimension(R.dimen.candidate_text_size) * candidateTextSizeScale;

        if (mAdjustedButtonHeight != newAdjustedButtonHeight ||
                mAdjustedCandidateHeight != newAdjustedCandidateHeight ||
                mAdjustedCandidateTextSize != newAdjustedCandidateTextSize) {
            mAdjustedButtonHeight = newAdjustedButtonHeight;
            mAdjustedCandidateHeight = newAdjustedCandidateHeight;
            mAdjustedCandidateTextSize = newAdjustedCandidateTextSize;
            changed = (mKeyboardType != null); // 高さが変わった場合も再描画が必要
        }

        // 候補ビューの高さを適用
        if (mCandidatesView != null) {
            ViewGroup.LayoutParams lp = mCandidatesView.getLayoutParams();
            if (lp != null) {
                lp.height = mAdjustedCandidateHeight;
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

        if ("none".equals(mKeyboardType)) {
            setBackgroundColor(Color.TRANSPARENT);
            if (mCandidateBarLayout != null) mCandidateBarLayout.setVisibility(GONE);
            if (mCandidateExpandedScroll != null) mCandidateExpandedScroll.setVisibility(GONE);
            mKeyboardLayout.setVisibility(GONE);
            height = 1;
        } else {
            setBackground(null);
            if (mCandidateExpandedScroll != null) mCandidateExpandedScroll.setVisibility(GONE);
            mKeyboardLayout.setVisibility(VISIBLE);

            switch (mKeyboardType) {
                case "tablet":
                    TabletKeyboardView tv = new TabletKeyboardView(getContext());
                    tv.setRowHeight(mAdjustedButtonHeight);
                    tv.setOnKeyActionListener(this::onClickKey);
                    mCurrentKeyboardView = tv;
                    break;
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
                    // ストロークエリアの高さは、幅（baseSize * scale）の半分として計算される
                    int baseSize = Math.min(getResources().getDisplayMetrics().widthPixels, getResources().getDisplayMetrics().heightPixels);
                    height = (int) (baseSize * mStrokeWidthScale) / 2;
                    break;
                case "emoji":
                    EmojiKeyboardView ev = new EmojiKeyboardView(getContext());
                    ev.setOnKeyActionListener(this::onClickKey);
                    mCurrentKeyboardView = ev;
                    height = mAdjustedButtonHeight * 5;
                    break;
                case "symbols":
                default:
                    SymbolKeyboardView syv = new SymbolKeyboardView(getContext());
                    syv.setOnKeyActionListener(this::onClickKey);
                    mCurrentKeyboardView = syv;
                    height = mAdjustedButtonHeight;
                    break;
            }
        }

        if (mCurrentKeyboardView != null) {
            LayoutParams lp = new LayoutParams(LayoutParams.MATCH_PARENT, height);
            if (mCurrentKeyboardView instanceof StrokeKeyboardView) {
                int screenWidth = getResources().getDisplayMetrics().widthPixels;
                int screenHeight = getResources().getDisplayMetrics().heightPixels;
                int baseSize = Math.min(screenWidth, screenHeight);
                int targetWidth = (int) (baseSize * mStrokeWidthScale);
                lp.width = targetWidth;
                lp.height = targetWidth / 2;

                int gravity = Gravity.END;
                if ("left".equals(mStrokeAlign)) {
                    gravity = Gravity.START;
                }
                lp.gravity = gravity;
            } else if (mCurrentKeyboardView instanceof TabletKeyboardView) {
                int screenWidth = getResources().getDisplayMetrics().widthPixels;
                int targetWidth = (int) (screenWidth * mTabletWidthScale);
                lp.width = (mTabletWidthScale >= 1.0f) ? LayoutParams.MATCH_PARENT : targetWidth;

                int gravity = Gravity.END;
                if ("left".equals(mTabletAlign)) {
                    gravity = Gravity.START;
                }
                lp.gravity = gravity;
            }
            mKeyboardLayout.addView(mCurrentKeyboardView, lp);
        }
        updateKeys();
        if (!restarting) {
            hideCandidatesView();
        } else if (mInputService != null) {
            mInputService.requestUIUpdate();
        }
    }

    /**
     * 絵文字ピッカー（候補ビューイメージのキーボード領域表示）を表示します。
     */
    public void showEmojiPicker() {
        if (mKeyboardLayout != null) {
            mKeyboardLayout.removeAllViews();
            EmojiKeyboardView ev = new EmojiKeyboardView(getContext());
            ev.setOnKeyActionListener(this::onClickKey);
            ev.setOnCloseListener(this::hideEmojiPicker);
            mCurrentKeyboardView = ev;
            int height = mAdjustedButtonHeight * 5;
            LayoutParams lp = new LayoutParams(LayoutParams.MATCH_PARENT, height);
            mKeyboardLayout.addView(ev, lp);
            mKeyboardLayout.setVisibility(VISIBLE);
        }
    }

    /**
     * 絵文字ピッカーを閉じ、通常のキーボードビューに戻します。
     */
    public void hideEmojiPicker() {
        if (mLastEditorInfo != null) {
            doStartInputView(mLastEditorInfo, true);
        }
    }

    /**
     * 絵文字ピッカーが表示中かどうかを判定します。
     *
     * @return 表示中の場合は true
     */
    public boolean isEmojiPickerShowing() {
        return mCurrentKeyboardView instanceof EmojiKeyboardView;
    }

    /**
     * キーボードのラベルを現在の状態に合わせて更新します。
     *
     * @param modeText      現在のモードテキスト
     * @param modeIconResId 現在のモードアイコンのリソース ID
     */
    public void updateKeys(String modeText, int modeIconResId) {
        mModeText = modeText;
        mModeIconResId = modeIconResId;
        if (mCurrentKeyboardView != null) {
            mCurrentKeyboardView.updateState(new KeyboardState(mIsShifted, mIsShiftLocked, mIsControl, mIsSymbol, mIsSymbolLocked, mModeText, mModeIconResId));
        }
    }

    /**
     * キーボードのラベルを現在の状態に合わせて更新します。
     *
     * @param modeText 現在のモードテキスト
     * @deprecated {@link #updateKeys(String, int)} を使用してください。
     */
    @Deprecated
    public void updateKeys(String modeText) {
        updateKeys(modeText, mModeIconResId);
    }

    /**
     * キーボードのラベルを現在の状態に合わせて更新します（内部保持しているモードテキストとアイコンを使用）。
     */
    public void updateKeys() {
        updateKeys(mModeText, mModeIconResId);
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
        if (mIsControl) {
            mIsControl = false;
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
                case KeyConfig.CODE_ABC:
                    mIsSymbol = false;
                    mIsSymbolLocked = false;
                    mIsControl = false;
                    mIsShifted = mIsShiftLocked;
                    updateKeys();
                    break;
                case KeyConfig.CODE_TAB:
                    if (mIsControl) {
                        mInputService.handleCtrlKey(KeyEvent.KEYCODE_TAB);
                    } else {
                        mInputService.handleTab(mIsShifted);
                    }
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
                    if (mIsControl) {
                        mInputService.handleCtrlKey(KeyEvent.KEYCODE_DEL);
                    } else {
                        mInputService.handleBackspace();
                    }
                    resetModifiers();
                    break;
                case KeyConfig.CODE_ENTER:
                    if (mIsControl) {
                        mInputService.handleCtrlKey(KeyEvent.KEYCODE_ENTER);
                    } else {
                        mInputService.handleEnter();
                    }
                    resetModifiers();
                    break;
                case KeyConfig.CODE_SPACE:
                    if (mIsControl) {
                        mInputService.handleCtrlKey(KeyEvent.KEYCODE_SPACE);
                    } else {
                        mInputService.processKey(' ');
                    }
                    resetModifiers();
                    break;
            }
        } else {
            String key = config.label;
            if (key != null && !key.isEmpty()) {
                if (mCurrentKeyboardView instanceof EmojiKeyboardView) {
                    mInputService.commitText(key, 1);
                    resetModifiers();
                } else if (key.length() == 1) {
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
                } else {
                    mInputService.commitText(key, 1);
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

    private void toggleExpanded() {
        mIsExpanded = !mIsExpanded;
        updateExpansionState();
    }

    private void updateExpansionState() {
        int screenHeight = getResources().getDisplayMetrics().heightPixels;
        int screenWidth = getResources().getDisplayMetrics().widthPixels;
        int expectedKeyboardHeight;
        if ("stroke".equals(mKeyboardType)) {
            int baseSize = Math.min(screenWidth, screenHeight);
            expectedKeyboardHeight = (int) (baseSize * mStrokeWidthScale) / 2;
        } else {
            expectedKeyboardHeight = mAdjustedButtonHeight * mRowCount;
        }

        if (mIsExpanded) {
            mKeyboardLayout.setVisibility(GONE);
            if (mCandidateExpandedScroll != null) {
                ViewGroup.LayoutParams lp = mCandidateExpandedScroll.getLayoutParams();
                lp.height = expectedKeyboardHeight;
                mCandidateExpandedScroll.setLayoutParams(lp);
                mCandidateExpandedScroll.setVisibility(VISIBLE);

                // Gboardのように、展開時に現在選択中（または1行表示の右端付近）の候補位置へスクロールして続きを表示
                mCandidateExpandedScroll.post(() -> {
                    if (mCandidateButton != null && mCandidateExpandedFlexbox != null) {
                        int selectedIdx = 0;
                        for (int i = 0; i < mCandidateButton.length; i++) {
                            if (mCandidateButton[i].isSelected()) {
                                selectedIdx = i;
                                break;
                            }
                        }
                        View selectedChip = mCandidateExpandedFlexbox.getChildAt(selectedIdx);
                        if (selectedChip != null) {
                            int chipTop = selectedChip.getTop() + mCandidateExpandedFlexbox.getTop();
                            mCandidateExpandedScroll.scrollTo(0, Math.max(0, chipTop - (int) (8 * getResources().getDisplayMetrics().density)));
                        }
                    }
                });
            }
            if (mBtnExpandCandidates != null) {
                mBtnExpandCandidates.setText("▲");
            }
        } else {
            mKeyboardLayout.setVisibility(VISIBLE);
            if (mCandidateExpandedScroll != null) {
                mCandidateExpandedScroll.setVisibility(GONE);
            }
            if (mBtnExpandCandidates != null) {
                mBtnExpandCandidates.setText("▼");
            }
        }
        requestLayout();
    }

    /**
     * 候補表示エリアの内容を完全にクリアします。
     */
    private void clearCandidates() {
        if (mCandidatesLayout != null) {
            mCandidatesLayout.removeAllViews();
        }
        if (mCandidateExpandedFlexbox != null) {
            mCandidateExpandedFlexbox.removeAllViews();
        }
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
        LayoutParams lp = new LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.MATCH_PARENT);

        for (int i = 0; i < candidates.size(); i++) {
            Button b = new Button(new ContextThemeWrapper(getContext(), style), null, style);
            b.setTextSize(TypedValue.COMPLEX_UNIT_PX, mAdjustedCandidateTextSize);
            b.setOnClickListener(this::onClickCandidateButton);
            b.setTag(i);
            b.setText(candidates.get(i));
            mCandidatesLayout.addView(b, lp);
            mCandidateButton[i] = b;

            TextView chip = (TextView) LayoutInflater.from(getContext()).inflate(R.layout.candidate_chip_item, mCandidateExpandedFlexbox, false);
            chip.setTextSize(TypedValue.COMPLEX_UNIT_PX, mAdjustedCandidateTextSize);
            chip.setText(candidates.get(i));
            chip.setOnClickListener(this::onClickCandidateButton);
            chip.setTag(i);
            mCandidateExpandedFlexbox.addView(chip);
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
        LayoutParams lp = new LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.MATCH_PARENT);

        for (int i = 0; i < candidates.size(); i++) {
            Candidate c = candidates.get(i);

            int style = c.isUserDict ? R.style.UserCandidateButton : R.style.CandidateButton;

            Button b = new Button(new ContextThemeWrapper(getContext(), style), null, style);
            b.setTextSize(TypedValue.COMPLEX_UNIT_PX, mAdjustedCandidateTextSize);
            b.setOnClickListener(this::onClickCandidateButton);
            b.setTag(i);

            String label = c.candidate;
            if (c.annotation != null) {
                label += ";" + c.annotation;
            }
            b.setText(label);
            mCandidatesLayout.addView(b, lp);
            mCandidateButton[i] = b;

            TextView chip = (TextView) LayoutInflater.from(getContext()).inflate(R.layout.candidate_chip_item, mCandidateExpandedFlexbox, false);
            chip.setTextSize(TypedValue.COMPLEX_UNIT_PX, mAdjustedCandidateTextSize);
            chip.setText(label);
            chip.setOnClickListener(this::onClickCandidateButton);
            chip.setTag(i);
            mCandidateExpandedFlexbox.addView(chip);
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

            int buttonLeft = selectedButton.getLeft();
            int buttonWidth = selectedButton.getWidth();
            int viewWidth = mCandidatesView.getWidth();
            int scrollToX = buttonLeft + (buttonWidth / 2) - (viewWidth / 2);
            mCandidatesView.scrollTo(scrollToX, 0);

            if (mCandidateExpandedFlexbox != null && mCandidateExpandedScroll != null) {
                for (int i = 0; i < mCandidateExpandedFlexbox.getChildCount(); i++) {
                    View child = mCandidateExpandedFlexbox.getChildAt(i);
                    child.setSelected(i == index);
                }
                View selectedChip = mCandidateExpandedFlexbox.getChildAt(index);
                if (selectedChip != null) {
                    float density = getResources().getDisplayMetrics().density;
                    int padding = (int) (8 * density);
                    int chipTop = selectedChip.getTop() + mCandidateExpandedFlexbox.getTop();
                    int chipBottom = chipTop + selectedChip.getHeight();

                    int scrollY = mCandidateExpandedScroll.getScrollY();
                    int viewHeight = mCandidateExpandedScroll.getHeight();

                    if (viewHeight > 0) {
                        if (chipTop < scrollY + padding) {
                            mCandidateExpandedScroll.smoothScrollTo(0, Math.max(0, chipTop - padding));
                        } else if (chipBottom > scrollY + viewHeight - padding) {
                            mCandidateExpandedScroll.smoothScrollTo(0, chipBottom - viewHeight + padding);
                        }
                    }
                }
            }
        });
    }

    /**
     * 候補表示エリアを可視化します。
     * フローティング候補表示が有効な場合は候補エリアを非表示 (GONE) にします。
     */
    public void showCandidatesView() {
        if (mInputService != null && mInputService.isFloatingCandidateEnabled()) {
            if (mCandidateBarLayout != null) mCandidateBarLayout.setVisibility(GONE);
            if (mCandidatesView != null) mCandidatesView.setVisibility(GONE);
            if (mBtnExpandCandidates != null) mBtnExpandCandidates.setVisibility(GONE);
            if (mCandidateExpandedScroll != null) mCandidateExpandedScroll.setVisibility(GONE);
            mKeyboardLayout.setVisibility(VISIBLE);
            return;
        }
        if (mCandidateBarLayout != null) mCandidateBarLayout.setVisibility(VISIBLE);
        if (mCandidatesView != null) mCandidatesView.setVisibility(VISIBLE);
        if (mBtnExpandCandidates != null) mBtnExpandCandidates.setVisibility(VISIBLE);
        updateExpansionState();
    }

    /**
     * 候補表示エリアを非表示にします。
     */
    public void hideCandidatesView() {
        mIsExpanded = false;
        if (mCandidateBarLayout != null) mCandidateBarLayout.setVisibility(GONE);
        if (mCandidatesView != null) mCandidatesView.setVisibility(GONE);
        if (mBtnExpandCandidates != null) mBtnExpandCandidates.setVisibility(GONE);
        if (mCandidateExpandedScroll != null) mCandidateExpandedScroll.setVisibility(GONE);
        mKeyboardLayout.setVisibility(VISIBLE);
        updateExpansionState();
    }

    private PopupWindow mCandidatePopup;

    public void hideFloatingCandidates() {
        hideFullWidthCandidatePopup();
    }

    public void hideFullWidthCandidatePopup() {
        if (mCandidatePopup != null && mCandidatePopup.isShowing()) {
            mCandidatePopup.dismiss();
        }
    }

    public void updateCandidatePopupPosition() {
        if (mCandidatePopup == null || !mCandidatePopup.isShowing()) {
            return;
        }
        if (mInputService != null && mInputService.isCursorInvisible()) {
            mCandidatePopup.dismiss();
            return;
        }

        int[] coordsAndSize = calculateCandidatePopupScreenCoordinates(mCandidatePopup.getContentView());
        showPopupAtScreenLocationWithSize(mCandidatePopup, coordsAndSize[0], coordsAndSize[1],
                getResources().getDisplayMetrics().widthPixels, coordsAndSize[2]);
    }

    @SuppressLint("DiscouragedApi")
    private int getStatusBarHeight() {
        WindowInsetsCompat insets = ViewCompat.getRootWindowInsets(this);
        if (insets != null) {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            if (systemBars.top > 0) {
                return systemBars.top;
            }
        }
        int resourceId = getResources().getIdentifier("status_bar_height", "dimen", "android");
        if (resourceId > 0) {
            return getResources().getDimensionPixelSize(resourceId);
        }
        return 0;
    }

    private int calculateTopMaxHeight(View contentView) {
        View header = contentView.findViewById(R.id.candidate_header);
        int headerHeight = (header != null && header.getVisibility() == View.VISIBLE) ? header.getMeasuredHeight() : 0;

        FlexboxLayout flexbox = contentView.findViewById(R.id.candidate_flexbox);
        int rowHeight = 0;
        if (flexbox != null && flexbox.getChildCount() > 0) {
            for (int i = 0; i < flexbox.getChildCount(); i++) {
                View child = flexbox.getChildAt(i);
                int h = child.getMeasuredHeight();
                if (h > rowHeight) {
                    rowHeight = h;
                }
            }
            if (rowHeight == 0) {
                rowHeight = (int) (40 * getResources().getDisplayMetrics().density);
            }
            View firstChild = flexbox.getChildAt(0);
            if (firstChild != null && firstChild.getLayoutParams() instanceof MarginLayoutParams) {
                MarginLayoutParams lp = (MarginLayoutParams) firstChild.getLayoutParams();
                rowHeight += lp.topMargin + lp.bottomMargin;
            } else {
                rowHeight += (int) (6 * getResources().getDisplayMetrics().density);
            }
        } else {
            rowHeight = (int) (44 * getResources().getDisplayMetrics().density);
        }

        // 4 rows of chips + header + padding
        return headerHeight + (4 * rowHeight) + (int) (16 * getResources().getDisplayMetrics().density);
    }

    private int[] calculateCandidatePopupScreenCoordinates(View contentView) {
        int screenWidth = getResources().getDisplayMetrics().widthPixels;
        int screenHeight = getResources().getDisplayMetrics().heightPixels;
        float density = getResources().getDisplayMetrics().density;
        int margin = (int) (2 * density);

        int keyboardTop = screenHeight;
        if (isShown()) {
            int[] loc = new int[2];
            getLocationOnScreen(loc);
            if (loc[1] > 0) {
                keyboardTop = loc[1];
            }
        }

        float cursorTop = mInputService != null ? mInputService.getCursorTop() : 0;
        float cursorBottom = mInputService != null ? mInputService.getCursorBottom() : 0;

        float caretTop = Math.min(cursorTop, cursorBottom);
        float caretBottom = Math.max(cursorTop, cursorBottom);

        int statusBarHeight = getStatusBarHeight();
        int spaceTop = Math.max(0, (int) caretTop - statusBarHeight - margin);
        int spaceBottom = Math.max(0, keyboardTop - (int) caretBottom - margin);

        boolean useTop = (spaceTop >= spaceBottom);
        int chosenSpace = useTop ? spaceTop : spaceBottom;

        contentView.measure(MeasureSpec.makeMeasureSpec(screenWidth, MeasureSpec.EXACTLY),
                MeasureSpec.UNSPECIFIED);
        int measuredContentHeight = contentView.getMeasuredHeight();

        int allowedMaxHeight;
        if (useTop) {
            int max4LinesHeight = calculateTopMaxHeight(contentView);
            allowedMaxHeight = Math.min(chosenSpace, max4LinesHeight);
        } else {
            allowedMaxHeight = Math.max((int) (40 * density), chosenSpace);
        }

        int finalPopupHeight = Math.min(measuredContentHeight, allowedMaxHeight);

        int targetX = 0;
        int targetY;

        if (useTop) {
            targetY = (int) caretTop - finalPopupHeight - margin;
        } else {
            targetY = (int) caretBottom + margin;
        }

        targetY = Math.max(statusBarHeight, Math.min(targetY, Math.max(statusBarHeight, keyboardTop - finalPopupHeight)));

        return new int[]{targetX, targetY, finalPopupHeight};
    }

    private void showPopupAtScreenLocationWithSize(PopupWindow popup, int screenX, int screenY, int width, int height) {
        View anchor = getRootView();
        if (anchor == null) anchor = this;

        int[] loc = new int[2];
        anchor.getLocationOnScreen(loc);

        int x = screenX - loc[0];
        int y = screenY - loc[1];

        try {
            if (popup.isShowing()) {
                popup.update(x, y, width, height);
            } else {
                popup.setClippingEnabled(false);
                popup.showAtLocation(anchor, Gravity.NO_GRAVITY, x, y);
                popup.update(x, y, width, height);
            }
        } catch (Exception e) {
            // ignore
        }
    }

    public void navigateCandidateFlexbox2D(int dRow, int dCol) {
        if (mCandidatePopup == null || !mCandidatePopup.isShowing()) {
            return;
        }
        View popupView = mCandidatePopup.getContentView();
        if (popupView == null) return;
        FlexboxLayout flexbox = popupView.findViewById(R.id.candidate_flexbox);
        if (flexbox == null || flexbox.getChildCount() == 0) return;

        int count = flexbox.getChildCount();
        int currentIdx = mInputService != null ? mInputService.getCurrentCandidateIndex() : 0;

        if (dCol != 0) {
            int nextIdx = currentIdx + (dCol > 0 ? 1 : -1);
            if (nextIdx >= 0 && nextIdx < count) {
                if (mInputService != null) mInputService.setCandidateIndex(nextIdx);
            }
            return;
        }

        if (dRow != 0) {
            View currentChip = flexbox.getChildAt(currentIdx);
            if (currentChip == null) return;

            int currX = currentChip.getLeft() + currentChip.getWidth() / 2;
            int currY = currentChip.getTop() + currentChip.getHeight() / 2;
            int chipHeight = currentChip.getHeight();

            int bestIndex = -1;
            double minDistance = Double.MAX_VALUE;

            for (int i = 0; i < count; i++) {
                if (i == currentIdx) continue;
                View targetChip = flexbox.getChildAt(i);
                if (targetChip == null) continue;

                int targetX = targetChip.getLeft() + targetChip.getWidth() / 2;
                int targetY = targetChip.getTop() + targetChip.getHeight() / 2;

                boolean isCorrectVerticalDirection;
                if (dRow > 0) {
                    isCorrectVerticalDirection = (targetY >= currY + chipHeight * 0.4f);
                } else {
                    isCorrectVerticalDirection = (targetY <= currY - chipHeight * 0.4f);
                }

                if (isCorrectVerticalDirection) {
                    int dy = Math.abs(targetY - currY);
                    int dx = Math.abs(targetX - currX);
                    double distance = (dy * dy) + (dx * dx * 2.5);

                    if (distance < minDistance) {
                        minDistance = distance;
                        bestIndex = i;
                    }
                }
            }

            if (bestIndex != -1) {
                if (mInputService != null) mInputService.setCandidateIndex(bestIndex);
            } else {
                if (mInputService != null) {
                    if (dRow > 0 && currentIdx < count - 1) {
                        mInputService.setCandidateIndex(count - 1);
                    } else if (dRow < 0 && currentIdx > 0) {
                        mInputService.setCandidateIndex(0);
                    }
                }
            }
        }
    }

    public void showCandidateFlexboxPopup(String header, List<String> items, int selectedIdx) {
        int screenWidth = getResources().getDisplayMetrics().widthPixels;

        View popupView = null;
        if (mCandidatePopup != null && mCandidatePopup.isShowing()) {
            View current = mCandidatePopup.getContentView();
            if (current != null && current.findViewById(R.id.candidate_flexbox) != null) {
                popupView = current;
            }
        }

        if (popupView == null) {
            LayoutInflater inflater = LayoutInflater.from(getContext());
            popupView = inflater.inflate(R.layout.candidate_floating, null);

            if (mCandidatePopup != null && mCandidatePopup.isShowing()) {
                mCandidatePopup.setContentView(popupView);
            } else {
                mCandidatePopup = new PopupWindow(popupView, screenWidth, ViewGroup.LayoutParams.WRAP_CONTENT);
                mCandidatePopup.setFocusable(false);
                mCandidatePopup.setAnimationStyle(0);
            }
        }

        final View finalPopupView = popupView;
        TextView headerView = finalPopupView.findViewById(R.id.candidate_header);
        FlexboxLayout flexbox = finalPopupView.findViewById(R.id.candidate_flexbox);

        if (headerView != null) {
            if (header != null && !header.isEmpty()) {
                headerView.setVisibility(View.VISIBLE);
                headerView.setText(header);
            } else {
                headerView.setVisibility(View.GONE);
            }
        }

        if (flexbox != null && items != null) {
            boolean sameSize = (flexbox.getChildCount() == items.size());
            if (sameSize) {
                for (int i = 0; i < items.size(); i++) {
                    View child = flexbox.getChildAt(i);
                    if (child instanceof TextView) {
                        TextView chip = (TextView) child;
                        chip.setText(items.get(i));
                        chip.setSelected(i == selectedIdx);
                    }
                }
                scrollToSelectedChip(finalPopupView, flexbox, selectedIdx);
            } else {
                flexbox.removeAllViews();
                LayoutInflater inflater = LayoutInflater.from(getContext());
                for (int i = 0; i < items.size(); i++) {
                    TextView chip = (TextView) inflater.inflate(R.layout.candidate_chip_item, flexbox, false);
                    chip.setText(items.get(i));
                    final int index = i;
                    chip.setOnClickListener(v -> {
                        if (mInputService != null) {
                            mInputService.pickCandidateViewManually(index);
                        }
                    });
                    chip.setSelected(i == selectedIdx);
                    flexbox.addView(chip);
                }

                flexbox.getViewTreeObserver().addOnGlobalLayoutListener(new ViewTreeObserver.OnGlobalLayoutListener() {
                    @Override
                    public void onGlobalLayout() {
                        flexbox.getViewTreeObserver().removeOnGlobalLayoutListener(this);
                        scrollToSelectedChip(finalPopupView, flexbox, selectedIdx);
                    }
                });
            }
        }

        int[] coordsAndSize = calculateCandidatePopupScreenCoordinates(finalPopupView);
        showPopupAtScreenLocationWithSize(mCandidatePopup, coordsAndSize[0], coordsAndSize[1], screenWidth, coordsAndSize[2]);
    }

    private void scrollToSelectedChip(View popupView, FlexboxLayout flexbox, int selectedIdx) {
        if (selectedIdx < 0 || selectedIdx >= flexbox.getChildCount()) {
            return;
        }
        ScrollView scroll = popupView.findViewById(R.id.candidate_scroll);
        View selectedChip = flexbox.getChildAt(selectedIdx);
        if (scroll != null && selectedChip != null) {
            float density = getResources().getDisplayMetrics().density;
            int padding = (int) (8 * density);

            int chipTop = selectedChip.getTop() + flexbox.getTop();
            int chipBottom = chipTop + selectedChip.getHeight();

            int scrollY = scroll.getScrollY();
            int viewHeight = scroll.getHeight();

            if (viewHeight <= 0) {
                scroll.post(() -> scrollToSelectedChip(popupView, flexbox, selectedIdx));
                return;
            }

            if (chipTop - padding < scrollY) {
                scroll.smoothScrollTo(0, Math.max(0, chipTop - padding));
            } else if (chipBottom + padding > (scrollY + viewHeight)) {
                scroll.smoothScrollTo(0, chipBottom + padding - viewHeight);
            }
        }
    }

    private void showFullWidthCandidatePopup(String text) {
        int screenWidth = getResources().getDisplayMetrics().widthPixels;

        View view = null;
        if (mCandidatePopup != null && mCandidatePopup.isShowing()) {
            View current = mCandidatePopup.getContentView();
            if (current != null && current.findViewById(R.id.tooltip_text) != null) {
                view = current;
            }
        }

        if (view != null) {
            setTooltipViewText(view, text);
            updateCandidatePopupPosition();
        } else {
            LayoutInflater inflater = LayoutInflater.from(getContext());
            view = inflater.inflate(R.layout.tooltip_view, null);
            setTooltipViewText(view, text);

            int[] coordsAndSize = calculateCandidatePopupScreenCoordinates(view);
            int targetHeight = coordsAndSize[2];

            if (mCandidatePopup != null && mCandidatePopup.isShowing()) {
                mCandidatePopup.setContentView(view);
                updateCandidatePopupPosition();
            } else {
                mCandidatePopup = new PopupWindow(view, screenWidth, targetHeight);
                mCandidatePopup.setFocusable(false);
                mCandidatePopup.setAnimationStyle(0);

                showPopupAtScreenLocationWithSize(mCandidatePopup, coordsAndSize[0], coordsAndSize[1], screenWidth, targetHeight);
            }
        }
    }

    private void setTooltipViewText(View popupContentView, String text) {
        if (popupContentView == null) return;
        TextView tv = popupContentView.findViewById(R.id.tooltip_text);
        if (tv != null) {
            tv.setText(text);
        } else if (popupContentView instanceof TextView) {
            ((TextView) popupContentView).setText(text);
        }
    }

}
