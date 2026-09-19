package io.github.kachaya.skk;

import android.annotation.SuppressLint;
import android.app.Dialog;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.graphics.Matrix;
import android.graphics.drawable.ColorDrawable;
import android.inputmethodservice.InputMethodService;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.text.InputType;
import android.util.Log;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.CursorAnchorInfo;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputConnection;
import android.widget.PopupWindow;
import android.widget.TextView;
import android.widget.ViewFlipper;

import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.preference.PreferenceManager;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import io.github.kachaya.skk.engine.Candidate;
import io.github.kachaya.skk.engine.Dictionary;
import io.github.kachaya.skk.engine.SKKEngine;
import io.github.kachaya.skk.engine.SKKIcon;
import io.github.kachaya.skk.engine.SKKModeFullHiragana;

/**
 * SKK 入力メソッドのメインサービス実装です。
 * <p>
 * Android の {@link InputMethodService} を継承し、システムからの入力イベント処理、
 * エディタの状態監視、変換エンジンの制御、および UI の表示管理を統括します。
 * {@link SharedPreferences.OnSharedPreferenceChangeListener} を実装し、設定変更をリアルタイムに反映します。
 * </p>
 */
public class InputService extends InputMethodService implements SharedPreferences.OnSharedPreferenceChangeListener {

    /** ツールチップ消去用のタイマー制御ハンドラ。 */
    private final Handler mHideHandler = new Handler(Looper.getMainLooper());
    /** システムに転送中の Ctrl キーコードセット。 */
    private final Set<Integer> mForwardedCtrlKeys = new HashSet<>();
    /** 変換エンジン。状態遷移やローマ字かな変換のメインロジックを保持します。 */
    private SKKEngine mEngine;

    /** 辞書管理マネージャ。システム辞書およびユーザー辞書へのアクセスを提供します。 */
    private Dictionary mDictionary;

    /** ソフトウェアキーボードおよび変換候補を表示するビュー。 */
    private InputView mInputView;

    /** 共有設定の参照。リスナーがガベージコレクションされないよう強参照で保持します。 */
    private SharedPreferences mPrefs;

    static final Map<Integer, Character> JIS_NORMAL_MAP = new HashMap<>();
    static final Map<Integer, Character> JIS_SHIFTED_MAP = new HashMap<>();

    static {
        JIS_NORMAL_MAP.put(KeyEvent.KEYCODE_EQUALS, '^');
        JIS_NORMAL_MAP.put(KeyEvent.KEYCODE_LEFT_BRACKET, '@');
        JIS_NORMAL_MAP.put(KeyEvent.KEYCODE_RIGHT_BRACKET, '[');
        JIS_NORMAL_MAP.put(KeyEvent.KEYCODE_APOSTROPHE, ':');
        JIS_NORMAL_MAP.put(KeyEvent.KEYCODE_BACKSLASH, ']');
        JIS_NORMAL_MAP.put(KeyEvent.KEYCODE_YEN, '¥');
        JIS_NORMAL_MAP.put(KeyEvent.KEYCODE_RO, '\\');

        JIS_SHIFTED_MAP.put(KeyEvent.KEYCODE_2, '"');
        JIS_SHIFTED_MAP.put(KeyEvent.KEYCODE_6, '&');
        JIS_SHIFTED_MAP.put(KeyEvent.KEYCODE_7, '\'');
        JIS_SHIFTED_MAP.put(KeyEvent.KEYCODE_8, '(');
        JIS_SHIFTED_MAP.put(KeyEvent.KEYCODE_9, ')');
        JIS_SHIFTED_MAP.put(KeyEvent.KEYCODE_MINUS, '=');
        JIS_SHIFTED_MAP.put(KeyEvent.KEYCODE_EQUALS, '~');
        JIS_SHIFTED_MAP.put(KeyEvent.KEYCODE_LEFT_BRACKET, '`');
        JIS_SHIFTED_MAP.put(KeyEvent.KEYCODE_RIGHT_BRACKET, '{');
        JIS_SHIFTED_MAP.put(KeyEvent.KEYCODE_SEMICOLON, '+');
        JIS_SHIFTED_MAP.put(KeyEvent.KEYCODE_APOSTROPHE, '*');
        JIS_SHIFTED_MAP.put(KeyEvent.KEYCODE_BACKSLASH, '}');
        JIS_SHIFTED_MAP.put(KeyEvent.KEYCODE_YEN, '|');
        JIS_SHIFTED_MAP.put(KeyEvent.KEYCODE_RO, '_');
    }

    // 設定項目
    /** URI 入力などの特定のフィールドで自動的に英字モードに切り替える設定。 */
    private boolean mAutoAsciiMode = false;
    /** SandS (Space and Shift) 機能を有効にする設定。 */
    private boolean mSandS = false;
    /** 物理キーボードに日本語配列（JIS）を使用する設定。 */
    private boolean mUseJisPhysicalKeyboard = false;
    private boolean mUseZenkakuKey = true;
    private boolean mUseEisuKey = true;
    private boolean mUseKanaKey = true;
    /** モード切替時にカーソル付近にツールチップを表示する時間（ミリ秒）。0 の場合は非表示。 */
    private int mTooltipDuration = 1000;
    /** ツールチップ表示位置（"top" または "bottom"）。 */
    private String mTooltipPosition = "top";
    /** 起動時の入力モード（"japanese" または "english"）。 */
    private String mStartupMode = "japanese";

    /** 現在のエディタが TYPE_NULL（入力を受け付けない）かどうか。 */
    private boolean mIsInputTypeNull = false;
    /** カーソルが画面上で不可視状態かどうか（位置計算に使用）。 */
    private boolean mIsCursorInvisible = true;

    /**
     * onKeyDown() で Enter キーを消費したかどうかのフラグ。
     * システムへのイベント波及を制御するために使用します。
     */
    private boolean isEnterUsed = false;

    /** SandS 用：現在スペースキーが物理的に押下されているかどうか。 */
    private boolean mSpacePressed = false;
    /** SandS 用：スペースキーが他の文字入力と組み合わせて（Shiftとして）使用されたかどうか。 */
    private boolean mSandSUsed = false;

    /** 現在 InputConnection 経由で Composing Text（未確定文字列）を設定中かどうか。 */
    private boolean mHasComposingText = false;
    /** スクリーン座標系でのキャレットの X 座標。 */
    private float mCursorHorizontal = 0;
    /** スクリーン座標系でのキャレットの上端 Y 座標。 */
    private float mCursorTop = 0;
    /** スクリーン座標系でのキャレットの下端 Y 座標。 */
    private float mCursorBottom = 0;
    /** UI（ツールチップ）の更新リクエストがあるかどうか。 */
    private boolean mNeedsTooltipUpdate = false;
    /** 単語登録セッション中かどうか。 */
    private boolean mIsRegistering = false;
    /** ツールチップ表示用のポップアップ。 */
    private PopupWindow mTooltipPopup;
    /** ストローク入力ヘルプ表示用のポップアップ。 */
    private PopupWindow mHelpPopup;
    /** ツールチップを閉じる実行タスク。 */
    private final Runnable mHideRunnable = () -> {
        if (!mIsRegistering && mTooltipPopup != null) {
            mTooltipPopup.dismiss();
        }
    };
    /** ウィンドウ表示時のツールチップ表示遅延用タスク。 */
    private final Runnable mShowTooltipRunnable = this::requestUIUpdate;

    /**
     * デバッグビルド時のみログを出力する内部ユーティリティです。
     *
     * @param msg ログメッセージ
     */
    private void logI(String msg) {
        if (BuildConfig.DEBUG) {
            Log.i("InputService", msg);
        }
    }

    /**
     * システムの設定（画面の向きやダークモード状態等）が変更されたときに呼び出されます。
     */
    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        if (mEngine != null) {
            mEngine.updateColors();
            requestUIUpdate();
        }
        mInputView = null;
        setInputView(onCreateInputView());
        if (mTooltipPopup != null && mTooltipPopup.isShowing()) {
            mTooltipPopup.dismiss();
            mTooltipPopup = null;
        }
    }

    /**
     * サービスの生成時に呼び出されます。
     * 初期設定の適用、変換エンジン、辞書の初期化、および設定変更リスナーの登録を行います。
     */
    @Override
    public void onCreate() {
        logI("onCreate()");
        super.onCreate();

        // 初期設定の一括適用
        setupDefaultPreferences(this);

        mPrefs = PreferenceManager.getDefaultSharedPreferences(this);
        mDictionary = new Dictionary(this);
        mEngine = new SKKEngine(this, mDictionary);

        // 設定の初期読み込み
        readPrefs();
        // 設定変更をリアルタイムに検知するために登録
        mPrefs.registerOnSharedPreferenceChangeListener(this);
    }

    /**
     * 初回起動時や設定未初期化時に、デフォルト値を一括適用します。
     * 物理キーボードの有無による動的な判定を XML のデフォルト値より優先させます。
     */
    public static void setupDefaultPreferences(Context context) {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);

        // 1. 動的なデフォルト判定（XMLの static な値より先に処理して優先させる）
        if (!prefs.contains("keyboard_type")) {
            Configuration config = context.getResources().getConfiguration();
            boolean hasHardwareKeyboard = (config.keyboard != Configuration.KEYBOARD_NOKEYS &&
                    config.keyboard != Configuration.KEYBOARD_UNDEFINED);
            String defaultType = hasHardwareKeyboard ? "symbols" : "qwerty";

            // commit() を使用して、直後の setDefaultValues がこの値を認識できるようにする
            prefs.edit().putString("keyboard_type", defaultType).commit();
        }

        // 2. その他の静的なデフォルト値を XML から適用（既存の設定は壊さない）
        PreferenceManager.setDefaultValues(context, R.xml.root_preferences, false);
    }

    /**
     * 画面の向きの変更など、UI 構成が変更された際に呼び出されます。
     */
    @Override
    public void onInitializeInterface() {
        logI("onInitializeInterface()");
        super.onInitializeInterface();
    }

    /**
     * 入力ビュー（キーボード UI）を生成します。
     * システムが UI を必要としたタイミングで呼び出されます。
     *
     * @return 生成された {@link InputView} インスタンス
     */
    @Override
    public View onCreateInputView() {
        logI("onCreateInputView()");
        if (mInputView == null) {
            mInputView = new InputView(this, getThemedContext(this));
        } else {
            // クラッシュ防止: 既に親がいる場合は古い親から切り離す
            ViewGroup parent = (ViewGroup) mInputView.getParent();
            if (parent != null) {
                parent.removeView(mInputView);
            }
        }
        return mInputView;
    }

    /**
     * エディタへの入力が開始（または再開）される際に呼び出されます。
     * エディタの属性に応じた初期モードの設定や、カーソル監視のリクエスト、UI の初期状態設定を行います。
     *
     * @param attribute  エディタの属性情報
     * @param restarting 入力が再開された場合は true
     */
    @Override
    public void onStartInput(EditorInfo attribute, boolean restarting) {
        logI("onStartInput: Cursor Start=" + attribute.initialSelStart + ", Cursor End=" + attribute.initialSelEnd);
        logI("onStartInput: restarting=" + restarting);
        super.onStartInput(attribute, restarting);

        readPrefs();

        // 状態のリセット。新しいセッション開始時はカーソル位置不明として扱う
        resetCursorState();
        mNeedsTooltipUpdate = false;

        // カーソル情報のリアルタイム監視をリクエスト (即時通知も含める)
        InputConnection ic = getCurrentInputConnection();
        if (ic != null) {
            ic.requestCursorUpdates(InputConnection.CURSOR_UPDATE_IMMEDIATE | InputConnection.CURSOR_UPDATE_MONITOR);
        } else {
            Log.w("onStartInput", "InputConnection is null");
        }

        if (mSandS) {
            mSpacePressed = false;
            mSandSUsed = false;
        }

        mIsInputTypeNull = (attribute.inputType & InputType.TYPE_MASK_CLASS) == InputType.TYPE_NULL;

        if (!restarting) {
            mHasComposingText = false;
            mEngine.resetOnStartInput();

            // 入力タイプに応じた初期モード判定
            switch (attribute.inputType & InputType.TYPE_MASK_CLASS) {
                case InputType.TYPE_CLASS_NUMBER:
                case InputType.TYPE_CLASS_DATETIME:
                case InputType.TYPE_CLASS_PHONE:
                    mEngine.toASCIIMode();
                    break;
                case InputType.TYPE_CLASS_TEXT:
                    int variation = attribute.inputType & InputType.TYPE_MASK_VARIATION;
                    switch (variation) {
                        case InputType.TYPE_TEXT_VARIATION_PASSWORD:
                        case InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD:
                        case InputType.TYPE_TEXT_VARIATION_WEB_PASSWORD:
                        case InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS:
                        case InputType.TYPE_TEXT_VARIATION_WEB_EMAIL_ADDRESS:
                        case InputType.TYPE_TEXT_VARIATION_FILTER:
                            mEngine.toASCIIMode();
                            break;
                        case InputType.TYPE_TEXT_VARIATION_URI:
                            if (mAutoAsciiMode) {
                                mEngine.toASCIIMode();
                            } else if ("english".equals(mStartupMode)) {
                                mEngine.toASCIIMode();
                            } else {
                                mEngine.changeMode(SKKModeFullHiragana.INSTANCE, false);
                            }
                            break;
                        default:
                            if ("english".equals(mStartupMode)) {
                                mEngine.toASCIIMode();
                            } else {
                                mEngine.changeMode(SKKModeFullHiragana.INSTANCE, false);
                            }
                            break;
                    }
                    break;
                case InputType.TYPE_NULL:
                    break;
                default:
                    if ("english".equals(mStartupMode)) {
                        mEngine.toASCIIMode();
                    } else {
                        mEngine.changeMode(SKKModeFullHiragana.INSTANCE, false);
                    }
                    break;
            }
        } else {
            logI("onStartInput: restarting session, preserving state.");
        }

        // 開始時に強制的に状態をチェックしてUIを更新（カーソル不可視ならアイコンを隠す）
        updateStatusIcon();

        // 画面キーボードの表示状態をシステムに要求
        updateInputViewShown();
        if (!mIsInputTypeNull) {
            // IME切り替え時などの消失防止のため明示的に表示要求
            requestShowSelf(0);
        } else {
            // 入力不可フィールドでは非表示を要求
            requestHideSelf(0);
        }
    }

    /**
     * 入力ビューが表示される直前に呼び出されます。
     *
     * @param editorInfo エディタの情報
     * @param restarting 再起動かどうか
     */
    @Override
    public void onStartInputView(EditorInfo editorInfo, boolean restarting) {
        logI("onStartInputView: restarting=" + restarting);
        super.onStartInputView(editorInfo, restarting);
        if (mInputView != null) {
            // 表示される前に最新の設定（ファイルからのレイアウト等）を確実に読み込む
            mInputView.readPrefs();
            mInputView.doStartInputView(editorInfo, restarting);
            if (restarting) {
                requestUIUpdate();
            }
        }
    }

    /**
     * 入力ビューが閉じられる際に呼び出されます。
     *
     * @param finishingInput 入力を終了するかどうか
     */
    @Override
    public void onFinishInputView(boolean finishingInput) {
        logI("onFinishInputView: finishingInput=" + finishingInput);
        // 入力ビューが閉じる際（アニメーション開始前）にステータスUIを隠し、
        // 予約済みの表示リクエストもキャンセルすることで、ツールチップがキーボードと一緒に流れるのを防ぎます。
        dismissStatusUI();
        super.onFinishInputView(finishingInput);
    }

    /**
     * 入力セッションが完全に終了する際に呼び出されます。
     * 候補ビューの非表示化などを行います。
     */
    @Override
    public void onFinishInput() {
        logI("onFinishInput()");
        super.onFinishInput();
        dismissStatusUI();
        hideCandidatesView();
        // セッション終了時はキーボードを隠す
        requestHideSelf(0);
    }

    /**
     * 入力ウィンドウが隠れた際に呼び出されます。
     */
    @Override
    public void onWindowHidden() {
        logI("onWindowHidden()");
        super.onWindowHidden();
        dismissStatusUI();
    }

    /**
     * 入力ウィンドウが表示された際に呼び出されます。
     * 設定画面から戻った際などの更新を確実にするために、設定を再読み込みします。
     */
    @Override
    public void onWindowShown() {
        logI("onWindowShown()");
        super.onWindowShown();
        readPrefs();

        // システムに最新のカーソル情報を要求
        InputConnection ic = getCurrentInputConnection();
        if (ic != null) {
            ic.requestCursorUpdates(InputConnection.CURSOR_UPDATE_IMMEDIATE | InputConnection.CURSOR_UPDATE_MONITOR);
        }

        // ウィンドウが表示された直後はアニメーション中の可能性があるため、
        // アニメーション完了を見越して遅延させてから UI 更新を行うことでツールチップの「流れ」を抑制します。
        mHideHandler.postDelayed(mShowTooltipRunnable, 1000);
    }

    /**
     * IME サービスが破棄される際に呼び出されます。
     * 辞書の変更を永続化し、リスナーを解除します。
     */
    @Override
    public void onDestroy() {
        logI("onDestroy()");
        if (mPrefs != null) {
            mPrefs.unregisterOnSharedPreferenceChangeListener(this);
        }
        if (mDictionary != null) {
            mDictionary.close();
        }
        super.onDestroy();
    }

    /**
     * カーソル位置やテキストの選択状態が変更された際に呼び出されます。
     * <p>
     * SKK では通常、{@link #onUpdateCursorAnchorInfo(CursorAnchorInfo)} で詳細な座標情報を取得するため、
     * このメソッドでは特別なリセット処理は行わず、キャレットの移動に追従した UI 更新の準備のみを行います。
     * </p>
     */
    @Override
    public void onUpdateSelection(int oldSelStart, int oldSelEnd, int newSelStart, int newSelEnd, int candidatesStart, int candidatesEnd) {
        super.onUpdateSelection(oldSelStart, oldSelEnd, newSelStart, newSelEnd, candidatesStart, candidatesEnd);
        // 位置が変わっただけであれば resetCursorState() は行わず、
        // onUpdateCursorAnchorInfo での更新を待つ（ちらつき防止のため）
    }

    /**
     * エディタからカーソルやテキストの矩形情報（CursorAnchorInfo）が通知された際に呼び出されます。
     * <p>
     * ステータス表示アイコンやツールチップをキャレット位置に正確に追従させるための座標計算を行います。
     * カーソルが不可視状態（画面外など）になった場合は、エンジンの状態をリセットし UI を非表示にします。
     * </p>
     *
     * @param cursorAnchorInfo エディタから渡される座標およびテキスト構造の情報
     */
    @Override
    public void onUpdateCursorAnchorInfo(CursorAnchorInfo cursorAnchorInfo) {
        super.onUpdateCursorAnchorInfo(cursorAnchorInfo);
        if (cursorAnchorInfo == null) {
            return;
        }

        int flags = cursorAnchorInfo.getInsertionMarkerFlags();
        boolean wasInvisible = mIsCursorInvisible;
        // FLAG_HAS_VISIBLE_REGION を用いてカーソルが実際に表示されているかを判定
        mIsCursorInvisible = (flags & CursorAnchorInfo.FLAG_HAS_VISIBLE_REGION) == 0;

        Matrix matrix = cursorAnchorInfo.getMatrix();
        if (matrix != null) {
            float[] points = new float[]{
                    cursorAnchorInfo.getInsertionMarkerHorizontal(),
                    cursorAnchorInfo.getInsertionMarkerTop(),
                    cursorAnchorInfo.getInsertionMarkerHorizontal(),
                    cursorAnchorInfo.getInsertionMarkerBottom(),
            };
            matrix.mapPoints(points);
            mCursorHorizontal = points[0];
            mCursorTop = points[1];
            mCursorBottom = points[3];

        }

        // 視認性が変わった場合はアイコンを更新
        if (wasInvisible != mIsCursorInvisible) {
            updateStatusIcon();
        }

        // すでに表示中のツールチップ・候補ポップアップがあれば位置を合わせる
        updateTooltipPosition();
        if (mInputView != null) {
            mInputView.updateCandidatePopupPosition();
        }

        // 座標が更新されたので、保留中のツールチップ表示リクエストがあれば実行する
        performTooltipUpdate();
    }

    /**
     * カーソルに関連する座標情報を初期状態に戻します。
     * 新しい入力セッションの開始時や、カーソル位置が不明になった際に呼び出されます。
     */
    private void resetCursorState() {
        mCursorHorizontal = 0;
        mCursorTop = 0;
        mCursorBottom = 0;
        mIsCursorInvisible = true;
    }

    /**
     * 表示中のツールチップの位置を最新のカーソル座標に合わせて更新します。
     * <p>
     * エディタから取得したスクリーン絶対座標を IME ウィンドウの左上角を基準とした相対座標に変換して適用します。
     * これにより、キーボード（IME ウィンドウ）の表示範囲外であっても、キャレット（入力位置）の直下に正確に配置されます。
     * カーソルが不可視になった場合はツールチップを非表示にします。
     * </p>
     */
    private void updateTooltipPosition() {
        if (mTooltipPopup == null || !mTooltipPopup.isShowing()) {
            return;
        }
        // カーソルが不可視になったらツールチップも消す
        if (mIsCursorInvisible) {
            mTooltipPopup.dismiss();
            return;
        }

        int[] coords = calculateTooltipScreenCoordinates(mTooltipPopup.getContentView());
        showPopupAtScreenLocation(mTooltipPopup, coords[0], coords[1]);
    }

    /**
     * ポップアップコンテンツビュー内の TextView に対してテキストを設定します。
     */
    private void setTooltipViewText(View popupContentView, String text) {
        if (popupContentView == null) return;
        TextView tv = popupContentView.findViewById(R.id.tooltip_text);
        if (tv != null) {
            tv.setText(text);
        } else if (popupContentView instanceof TextView) {
            ((TextView) popupContentView).setText(text);
        }
    }

    @SuppressLint("DiscouragedApi")
    private int getStatusBarHeight() {
        if (mInputView != null) {
            WindowInsetsCompat insets = ViewCompat.getRootWindowInsets(mInputView);
            if (insets != null) {
                androidx.core.graphics.Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
                if (systemBars.top > 0) {
                    return systemBars.top;
                }
            }
        }
        int resourceId = getResources().getIdentifier("status_bar_height", "dimen", "android");
        if (resourceId > 0) {
            return getResources().getDimensionPixelSize(resourceId);
        }
        return 0;
    }

    /**
     * ツールチップの表示位置（スクリーン絶対座標）を計算します。
     * 上下に十分なスペースがない場合は自動的に位置を反転（Flip）し、画面内に収まるよう調整します。
     *
     * @param contentView ツールチップのコンテンツビュー
     * @return {x, y} スクリーン絶対座標の配列
     */
    private int[] calculateTooltipScreenCoordinates(View contentView) {
        int screenWidth = getResources().getDisplayMetrics().widthPixels;
        int screenHeight = getResources().getDisplayMetrics().heightPixels;
        contentView.measure(View.MeasureSpec.UNSPECIFIED, View.MeasureSpec.UNSPECIFIED);
        float density = getResources().getDisplayMetrics().density;
        int popupWidth = contentView.getMeasuredWidth();
        int popupHeight = contentView.getMeasuredHeight();
        int margin = (int) (2 * density);

        int keyboardTop = screenHeight;
        if (mInputView != null && mInputView.isShown()) {
            int[] loc = new int[2];
            mInputView.getLocationOnScreen(loc);
            if (loc[1] > 0) {
                keyboardTop = loc[1];
            }
        }

        int targetX = (int) mCursorHorizontal - (popupWidth / 2);
        // 左右が画面からはみ出さないようクランプ
        targetX = Math.max(0, Math.min(targetX, screenWidth - popupWidth));

        float caretTop = Math.min(mCursorTop, mCursorBottom);
        float caretBottom = Math.max(mCursorTop, mCursorBottom);

        int topY = (int) caretTop - popupHeight - margin;
        int bottomY = (int) caretBottom + margin;
        int targetY;

        if ("top".equals(mTooltipPosition)) {
            if (topY < 0 && (bottomY + popupHeight) <= keyboardTop) {
                targetY = bottomY;
            } else {
                targetY = topY;
            }
        } else {
            if ((bottomY + popupHeight) > keyboardTop && topY >= 0) {
                targetY = topY;
            } else {
                targetY = bottomY;
            }
        }

        int statusBarHeight = getStatusBarHeight();
        int maxAllowedY = Math.max(statusBarHeight, keyboardTop - popupHeight);
        targetY = Math.max(statusBarHeight, Math.min(targetY, maxAllowedY));

        return new int[]{targetX, targetY};
    }

    private View getAnchorView() {
        if (mInputView != null && mInputView.getWindowToken() != null) {
            return mInputView.getRootView();
        }
        try {
            Dialog dialog = getWindow();
            if (dialog != null && dialog.getWindow() != null) {
                return dialog.getWindow().getDecorView();
            }
        } catch (Exception e) {
            // ignore
        }
        return null;
    }

    /**
     * PopupWindow を指定されたスクリーン絶対座標に表示、または更新します。
     * <p>
     * IME ウィンドウの左上角からの相対座標に内部で変換することで、
     * {@link PopupWindow#setClippingEnabled(boolean)} が false の場合にウィンドウ外への描画を可能にします。
     * </p>
     *
     * @param popup 表示・更新する PopupWindow
     * @param screenX 表示したい場所のスクリーン X 座標
     * @param screenY 表示したい場所のスクリーン Y 座標
     */
    private void showPopupAtScreenLocation(PopupWindow popup, int screenX, int screenY) {
        View anchor = getAnchorView();
        if (anchor == null) return;

        int[] loc = new int[2];
        anchor.getLocationOnScreen(loc);

        // ウィンドウ相対座標への変換
        int x = screenX - loc[0];
        int y = screenY - loc[1];

        try {
            if (popup.isShowing()) {
                popup.update(x, y, -1, -1);
            } else {
                // 表示前に共通の設定を適用
                popup.setClippingEnabled(false);
                popup.showAtLocation(anchor, Gravity.NO_GRAVITY, x, y);
            }
        } catch (Exception e) {
            logI("Failed to show/update popup: " + e.getMessage());
        }
    }

    /**
     * 設定値が変更された際のコールバックです。
     *
     * @param sharedPreferences 共有設定インスタンス
     * @param key               変更された設定のキー
     */
    @Override
    public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String key) {
        logI("onSharedPreferenceChanged: key=" + key);
        if ("keyboard_theme".equals(key)) {
            mInputView = null;
            setInputView(onCreateInputView());
        }
        if ("keyboard_type".equals(key)) {
            updateInputViewShown();
        }
        readPrefs();
    }

    /**
     * キーボードのテーマ設定（システム設定に従う/ダーク/ライト）に基づいて、
     * 適切な UIMode（Night Mode）を適用したコンテキストを生成して返します。
     *
     * @param context 元のコンテキスト
     * @return テーマが適用されたコンテキスト
     */
    public static Context getThemedContext(Context context) {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
        String themeMode = prefs.getString("keyboard_theme", "auto");

        if ("auto".equals(themeMode)) {
            return context;
        }

        Configuration config = new Configuration(context.getResources().getConfiguration());
        if ("dark".equals(themeMode)) {
            config.uiMode = (config.uiMode & ~Configuration.UI_MODE_NIGHT_MASK) | Configuration.UI_MODE_NIGHT_YES;
        } else if ("light".equals(themeMode)) {
            config.uiMode = (config.uiMode & ~Configuration.UI_MODE_NIGHT_MASK) | Configuration.UI_MODE_NIGHT_NO;
        }
        return context.createConfigurationContext(config);
    }


    /**
     * 現在の入力状態（エディタの属性やカーソルの可視性）から、
     * SKK による処理を無効にすべきかどうかを判定します。
     *
     * @return 無効な場合は true
     */
    private boolean isInputDisabled() {
        return mIsInputTypeNull || getCurrentInputConnection() == null;
    }

    /**
     * 最新の設定値を SharedPreferences から読み込みます。
     * InputView が存在する場合はその設定も一括して更新します。
     */
    private void readPrefs() {
        SharedPreferences prefs = (mPrefs != null) ? mPrefs : PreferenceManager.getDefaultSharedPreferences(this);
        mSandS = prefs.getBoolean("s_and_s", false);
        mUseJisPhysicalKeyboard = prefs.getBoolean("use_jis_physical_keyboard", false);
        mUseZenkakuKey = prefs.getBoolean("use_zenkaku_key", true);
        mUseEisuKey = prefs.getBoolean("use_eisu_key", true);
        mUseKanaKey = prefs.getBoolean("use_kana_key", true);
        // 設定画面（秒）の値を内部用のミリ秒に変換
        mTooltipDuration = prefs.getInt("tooltip_duration_sec", 1) * 1000;
        mTooltipPosition = prefs.getString("tooltip_position", "top");
        mAutoAsciiMode = prefs.getBoolean("auto_ascii_mode", false);
        mStartupMode = prefs.getString("startup_mode", "japanese");

        // 辞書データベースの再読み込み（設定画面や辞書ツールでの変更を反映）
        if (mDictionary != null) {
            mDictionary.reloadUserDictionary();
            mDictionary.reloadImportedDictionary();
        }

        // エンジン側の設定を更新
        if (mEngine != null) {
            mEngine.readPrefs();
        }

        // UI（InputView）側の設定もここで統合して更新
        if (mInputView != null) {
            mInputView.readPrefs();
        }
    }

    /**
     * システムがフル UI（ソフトキーボード）を表示すべきかどうかを判断します。
     *
     * @return 入力不可フィールド（TYPE_NULL）でない場合は true
     */
    @Override
    @SuppressLint("MissingSuperCall")
    public boolean onEvaluateInputViewShown() {
        if (mIsInputTypeNull) {
            return false;
        }
        return true;
    }

    /**
     * フルスクリーンモードを使用するかどうかを判断します。
     *
     * @return 常に false
     */
    @Override
    public boolean onEvaluateFullscreenMode() {
        return false;
    }

    /**
     * キーが離された際のイベント処理です。
     * SandS のスペースキー処理や、Enter キーの重複発火防止を行います。
     *
     * @param keyCode キーコード
     * @param event   イベント情報
     * @return イベントを消費した場合は true
     */
    @Override
    public boolean onKeyUp(int keyCode, KeyEvent event) {
        if (isInputDisabled()) {
            return super.onKeyUp(keyCode, event);
        }

        if (mEngine.ignoresKeyEvent()) {
            return super.onKeyUp(keyCode, event);
        }

        switch (keyCode) {
            case KeyEvent.KEYCODE_SPACE:
                if (mSandS) {
                    mSpacePressed = false;
                    if (!mSandSUsed) {
                        processKey(' ');
                    }
                    mSandSUsed = false;
                    return true;
                }
                break;
            case KeyEvent.KEYCODE_ENTER:
                if (isEnterUsed) {
                    isEnterUsed = false;
                    return true;
                }
                break;
            default:
                break;
        }

        if (event.isCtrlPressed() && mForwardedCtrlKeys.contains(keyCode)) {
            mForwardedCtrlKeys.remove(keyCode);
            InputConnection ic = getCurrentInputConnection();
            if (ic != null) {
                ic.sendKeyEvent(event);
                return true;
            }
        }

        return super.onKeyUp(keyCode, event);
    }

    /**
     * キーが押された際のイベント処理です。
     * Ctrl 組合せ文字、SandS、および SKK エンジンの各状態に応じた処理の振り分けを行います。
     *
     * @param keyCode キーコード
     * @param event   イベント情報
     * @return イベントを消費した場合は true
     */
    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (isInputDisabled()) {
            // カーソル不可視等の場合は、物理キー操作をそのままシステムに渡す
            return super.onKeyDown(keyCode, event);
        }
        logI("onKeyDown keyCode=" + keyCode);

        if (event.isCtrlPressed()) {
            if (mEngine.processCtrlKey(keyCode)) {
                if (mSandS && keyCode == KeyEvent.KEYCODE_SPACE) {
                    mSandSUsed = true;
                }
                return true;
            }
            if (keyCode == KeyEvent.KEYCODE_DEL) {
                if (!mEngine.handleBackspace()) {
                    sendDownUpKeyEvents(KeyEvent.KEYCODE_DEL);
                }
                return true;
            }
            InputConnection ic = getCurrentInputConnection();
            if (ic != null) {
                mForwardedCtrlKeys.add(keyCode);
                ic.sendKeyEvent(event);
                return true;
            }
        }

        if (keyCode == KeyEvent.KEYCODE_TAB) {
            boolean isShifted = false;
            if (mSandS) {
                if (mSpacePressed) {
                    isShifted = true;
                    mSandSUsed = true;
                }
            } else {
                if ((event.getMetaState() & KeyEvent.META_SHIFT_ON) != 0) {
                    isShifted = true;
                }
            }
            if (mEngine.processTab(isShifted)) {
                return true;
            }
        }

        if (mEngine.ignoresKeyEvent()) {
            return super.onKeyDown(keyCode, event);
        }

        switch (keyCode) {
            case KeyEvent.KEYCODE_ZENKAKU_HANKAKU:
                if (mUseZenkakuKey && mEngine != null) {
                    mEngine.toggleEnglishJapanese();
                    return true;
                }
                break;
            case KeyEvent.KEYCODE_GRAVE:
                if (mUseJisPhysicalKeyboard && mUseZenkakuKey && mEngine != null) {
                    mEngine.toggleEnglishJapanese();
                    return true;
                }
                break;
            case KeyEvent.KEYCODE_EISU:
                if (mUseEisuKey && mEngine != null) {
                    mEngine.toASCIIMode();
                    return true;
                }
                break;
            case KeyEvent.KEYCODE_KANA:
                if (mUseKanaKey && mEngine != null) {
                    mEngine.handleKanaKey();
                    return true;
                }
                break;
            case KeyEvent.KEYCODE_ESCAPE:
                if (mEngine.handleCancel()) {
                    return true;
                }
                break;
            case KeyEvent.KEYCODE_BACK:
                if (mEngine.handleBackKey()) {
                    return true;
                }
                break;
            case KeyEvent.KEYCODE_DEL:
                if (mEngine.handleBackspace()) {
                    return true;
                }
                break;
            case KeyEvent.KEYCODE_ENTER:
                if (mEngine.handleEnter()) {
                    isEnterUsed = true;
                    return true;
                }
                break;
            case KeyEvent.KEYCODE_SPACE:
                if (mSandS) {
                    mSpacePressed = true;
                } else {
                    processKey(' ');
                }
                return true;
            case KeyEvent.KEYCODE_DPAD_UP:
            case KeyEvent.KEYCODE_DPAD_DOWN:
            case KeyEvent.KEYCODE_DPAD_LEFT:
            case KeyEvent.KEYCODE_DPAD_RIGHT:
                if (mEngine.handleDpad(keyCode)) {
                    return true;
                }
                break;
            case KeyEvent.KEYCODE_SYM:
                toggleEmojiPicker();
                return true;
            default:
                if (translateKeyDown(event)) {
                    return true;
                }
        }

        return super.onKeyDown(keyCode, event);
    }

    /**
     * 押下されたキーを Unicode 文字として解析し、SKK エンジンに渡します。
     * SandS 設定時はスペースキーの状態を Shift 状態として考慮します。
     *
     * @param event キーイベント
     * @return 文字として処理された場合は true
     */
    private boolean translateKeyDown(KeyEvent event) {
        int c;
        boolean isShifted = false;
        if (mSandS && mSpacePressed) {
            isShifted = true;
            mSandSUsed = true;
        } else if ((event.getMetaState() & KeyEvent.META_SHIFT_ON) != 0) {
            isShifted = true;
        }

        if (mUseJisPhysicalKeyboard) {
            int keyCode = event.getKeyCode();
            Map<Integer, Character> map = isShifted ? JIS_SHIFTED_MAP : JIS_NORMAL_MAP;
            Character mapped = map.get(keyCode);
            if (mapped != null) {
                c = mapped;
            } else {
                c = event.getUnicodeChar(isShifted ? KeyEvent.META_SHIFT_ON : 0);
            }
        } else {
            if (mSandS && mSpacePressed) {
                c = event.getUnicodeChar(KeyEvent.META_SHIFT_ON);
                mSandSUsed = true;
            } else {
                c = event.getUnicodeChar();
            }
        }

        InputConnection ic = getCurrentInputConnection();
        if (c == 0 || ic == null) {
            return false;
        }

        if (!Character.isLetter(c) && !Character.isDigit(c) && c != '.' && c != ',' && c != '/' && c != '>' && c != '<' && c != '?' && c != '-' && c != '~' && c != '[' && c != ']') {
            if (mEngine != null && mEngine.hasComposing()) {
                mEngine.getConverter().flush();
            }
            commitText(String.valueOf((char) c), 1);
            return true;
        }

        processKey(c);
        return true;
    }

    /**
     * 文字コード（または特殊コード）を変換 engine に渡して処理を継続します。
     *
     * @param code 文字コード
     */
    void processKey(int code) {
        if (isInputDisabled()) {
            // カーソル不可視等の場合は、画面キーボードからの入力も受け付けない
            return;
        }
        mEngine.processKey(code);
    }

    /**
     * バックスペース処理を実行します。
     */
    void handleBackspace() {
        if (isInputDisabled()) {
            return;
        }
        if (!mEngine.handleBackspace()) {
            sendDownUpKeyEvents(KeyEvent.KEYCODE_DEL);
        }
    }

    /**
     * エンターキー処理を実行します。
     */
    void handleEnter() {
        if (isInputDisabled()) {
            return;
        }
        if (!mEngine.handleEnter()) {
            sendDownUpKeyEvents(KeyEvent.KEYCODE_ENTER);
        }
    }

    /**
     * Ctrl キーとの組み合わせ入力を処理します。
     *
     * @param keyCode KeyEvent で定義されているキーコード
     */
    void handleCtrlKey(int keyCode) {
        if (isInputDisabled()) {
            return;
        }
        if (!mEngine.processCtrlKey(keyCode)) {
            // エンジンで消費されなかった場合は、Ctrl キーとの組み合わせとしてシステムに送信
            InputConnection ic = getCurrentInputConnection();
            if (ic != null) {
                long now = SystemClock.uptimeMillis();
                ic.sendKeyEvent(new KeyEvent(now, now, KeyEvent.ACTION_DOWN, keyCode, 0,
                        KeyEvent.META_CTRL_ON | KeyEvent.META_CTRL_LEFT_ON));
                ic.sendKeyEvent(new KeyEvent(now, now, KeyEvent.ACTION_UP, keyCode, 0,
                        KeyEvent.META_CTRL_ON | KeyEvent.META_CTRL_LEFT_ON));
            }
        }
    }

    /**
     * Tab キー入力を処理します。
     *
     * @param isShifted Shift 状態かどうか
     */
    void handleTab(boolean isShifted) {
        if (isInputDisabled()) {
            return;
        }
        if (!mEngine.processTab(isShifted)) {
            sendDownUpKeyEvents(KeyEvent.KEYCODE_TAB);
        }
    }

    /**
     * D-Pad（カーソルキー）操作を処理します。
     *
     * @param keyCode KeyEvent で定義されているキーコード
     */
    void handleDpad(int keyCode) {
        if (isInputDisabled()) {
            return;
        }
        if (!mEngine.handleDpad(keyCode)) {
            sendDownUpKeyEvents(keyCode);
        }
    }

    /**
     * 物理キーボードが接続されているかどうかを判定します。
     *
     * @return 物理キーボードが接続されている場合は true
     */
    public boolean hasHardwareKeyboardConnected() {
        Configuration config = getResources().getConfiguration();
        return (config.keyboard != Configuration.KEYBOARD_NOKEYS &&
                config.keyboard != Configuration.KEYBOARD_UNDEFINED &&
                config.hardKeyboardHidden != Configuration.HARDKEYBOARDHIDDEN_YES);
    }

    /**
     * 現在のキーボードタイプに応じたフローティング候補表示の設定状態を返します。
     * 物理キーボードが接続されている場合は常に true を返します。
     *
     * @return フローティング表示を使用する場合は true
     */
    public boolean isFloatingCandidateEnabled() {
        if (hasHardwareKeyboardConnected()) {
            return true;
        }
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);
        String type = prefs.getString("keyboard_type", "qwerty");
        switch (type) {
            case "stroke":
                return prefs.getBoolean("floating_candidate_stroke", true);
            case "symbols":
                return prefs.getBoolean("floating_candidate_symbols", true);
            case "tablet":
                return prefs.getBoolean("floating_candidate_tablet", false);
            case "qwerty":
                return prefs.getBoolean("floating_candidate_qwerty", false);
            case "none":
                return prefs.getBoolean("floating_candidate_none", true);
            default:
                return false;
        }
    }

    /**
     * 候補リストを UI に設定し、候補表示エリアを可視化します。
     *
     * @param list 表示する候補文字列のリスト
     */
    public void setCandidates(List<String> list) {
        if (mInputView != null) {
            if (list != null && !isFloatingCandidateEnabled()) {
                mInputView.setCandidates(list);
                mInputView.showCandidatesView();
            } else {
                mInputView.hideCandidatesView();
            }
        }
    }

    /**
     * 絵文字ピッカーを開きます。
     */
    public void openEmojiPicker() {
        if (mInputView != null) {
            mInputView.showEmojiPicker();
        }
    }

    /**
     * 絵文字ピッカーの表示・非表示を切り替えます。
     */
    public void toggleEmojiPicker() {
        if (mInputView != null) {
            if (mInputView.isEmojiPickerShowing()) {
                mInputView.hideEmojiPicker();
            } else {
                mInputView.showEmojiPicker();
            }
        }
    }

    /**
     * 詳細な候補情報（ユーザー辞書フラグ等を含む）を元に UI に設定します。
     *
     * @param candidates 表示する候補オブジェクトのリスト
     */
    public void setCandidateObjects(List<Candidate> candidates) {
        if (mInputView != null) {
            if (candidates != null && !isFloatingCandidateEnabled()) {
                mInputView.setCandidateObjects(candidates);
                mInputView.showCandidatesView();
            } else {
                mInputView.hideCandidatesView();
            }
        }
    }

    /**
     * 候補表示エリアを非表示にします。
     */
    public void hideCandidatesView() {
        if (mInputView != null) {
            mInputView.hideCandidatesView();
        }
    }

    /**
     * 指定されたインデックスの候補を UI 上で選択状態にします。
     *
     * @param index 選択するインデックス
     */
    public void requestChooseCandidate(int index) {
        if (mInputView != null) {
            mInputView.selectCandidate(index);
        }
    }

    /**
     * 候補表示エリアが直接タップされた際の処理を行います。
     *
     * @param index タップされた候補のインデックス
     */
    public void pickCandidateViewManually(int index) {
        mEngine.pickCandidateViewManually(index);
    }

    /**
     * 再変換の準備として、カーソル直前の指定文字列をエディタから削除します。
     * <p>
     * エディタ側のテキストが指定された文字列と一致する場合のみ削除を実行し、
     * 変換エンジン側で再変換セッションを開始できる状態にします。
     * </p>
     *
     * @param candidate 再変換対象の文字列
     * @return 削除に成功した場合は true
     */
    public boolean prepareReConversion(String candidate) {
        InputConnection ic = getCurrentInputConnection();
        if (ic != null && candidate.equals(ic.getTextBeforeCursor(candidate.length(), 0))) {
            ic.deleteSurroundingText(candidate.length(), 0);
            return true;
        }
        return false;
    }

    /**
     * エディタに対してテキストをコミット（確定入力）します。
     *
     * @param text              コミットするテキスト
     * @param newCursorPosition 文字列内での新しいカーソル相対位置
     */
    public void commitText(CharSequence text, int newCursorPosition) {
        InputConnection ic = getCurrentInputConnection();
        if (ic != null) {
            ic.commitText(text, newCursorPosition);
            mHasComposingText = false;
        }
    }

    /**
     * エディタに対して未確定文字列（Composing Text）を設定します。
     *
     * @param text              設定する未確定文字列
     * @param newCursorPosition 文字列内での新しいカーソル相対位置
     */
    public void setComposingText(CharSequence text, int newCursorPosition) {
        InputConnection ic = getCurrentInputConnection();
        if (ic == null) {
            mHasComposingText = false;
            return;
        }
        if (!mHasComposingText && text.length() == 0) {
            return;
        }
        if (text.length() == 0) {
            mHasComposingText = false;
            ic.setComposingText("", newCursorPosition);
            ic.finishComposingText();
            return;
        }
        mHasComposingText = true;
        ic.setComposingText(text, newCursorPosition);
    }

    /**
     * ステータス UI（アイコンおよびツールチップ）の更新リクエストを登録します。
     * <p>
     * 座標が必要なツールチップ等は、{@link #onUpdateCursorAnchorInfo(CursorAnchorInfo)} により
     * 座標が確定したタイミングで表示されます。
     * </p>
     */
    public void requestUIUpdate() {
        // ステータスアイコンは座標に依存しないため、即座に更新する
        updateStatusIcon();

        // キーボードのモード表示等も更新する
        if (mInputView != null) {
            mInputView.updateKeys(mEngine.getCurrentTooltip(), getIconResourceId(mEngine.getCurrentIcon()));
        }

        // ツールチップは座標が必要なため、保留フラグを立てて実行を試みる
        mNeedsTooltipUpdate = true;
        performTooltipUpdate();
    }

    /**
     * ステータスアイコンを最新の状態に更新します。
     */
    private void updateStatusIcon() {
        if (isInputDisabled()) {
            // 入力不可または接続がない場合はアイコンを表示しない
            hideStatusIcon();
            return;
        }
        SKKIcon icon = mEngine.getCurrentIcon();
        int iconRes = getIconResourceId(icon);
        if (iconRes != 0) {
            showStatusIcon(iconRes);
        } else {
            hideStatusIcon();
        }
    }

    /**
     * 指定された SKKIcon に対応する Android リソース ID を取得します。
     *
     * @param icon アイコンの種類
     * @return リソース ID、または 0
     */
    private int getIconResourceId(SKKIcon icon) {
        if (icon == null) return 0;
        switch (icon) {
            case FULL_HIRAGANA:
                return R.drawable.ic_mode_full_hiragana;
            case FULL_KATAKANA:
                return R.drawable.ic_mode_full_katakana;
            case FULL_LATIN:
                return R.drawable.ic_mode_full_latin;
            case HALF_KATAKANA:
                return R.drawable.ic_mode_half_katakana;
            case ABBREV:
                return R.drawable.ic_mode_abbrev;
            default:
                return 0;
        }
    }

    /**
     * ステータス表示（アイコン等）を完全に消去します。
     */
    public void dismissStatusUI() {
        mNeedsTooltipUpdate = false;
        mIsRegistering = false;
        hideStatusIcon();
        mHideHandler.removeCallbacks(mHideRunnable);
        mHideHandler.removeCallbacks(mShowTooltipRunnable);
        if (mTooltipPopup != null) {
            mTooltipPopup.dismiss();
        }
        if (mInputView != null) {
            mInputView.hideFloatingCandidates();
        }
        if (mHelpPopup != null) {
            mHelpPopup.dismiss();
        }
    }

    /**
     * 保留中のツールチップ更新があれば実行します。
     * 座標が取得できていない、またはカーソルが不可視の場合は表示を保留します。
     */
    private void performTooltipUpdate() {
        if (!mNeedsTooltipUpdate || isInputDisabled()) {
            return;
        }
        // カーソル座標が未確定（0,0）またはカーソルが不可視の場合は保留
        if ((mCursorHorizontal == 0 && mCursorTop == 0) || mIsCursorInvisible) {
            return;
        }

        // 表示条件が整ったのでフラグを落とす
        mNeedsTooltipUpdate = false;

        // ツールチップの表示・更新
        if (mTooltipDuration > 0) {
            String text = mEngine.getCurrentTooltip();
            if (text != null && !text.isEmpty()) {
                showTooltipNow(text);
            }
        }
    }

    /**
     * 単語登録用ポップアップを非表示にします。
     */
    public void hideRegistrationPopup() {
        mIsRegistering = false;
        hideFullWidthCandidatePopup();
    }

    /**
     * 変換候補用ポップアップを非表示にします。
     */
    public void hideFloatingCandidates() {
        if (!mIsRegistering && mInputView != null) {
            mInputView.hideFloatingCandidates();
        }
    }

    public void navigateCandidateFlexbox2D(int dRow, int dCol) {
        if (mInputView != null) {
            mInputView.navigateCandidateFlexbox2D(dRow, dCol);
        }
    }

    public void showCandidateFlexboxPopup(String header, List<String> items, int selectedIdx) {
        if (mInputView != null) {
            mInputView.showCandidateFlexboxPopup(header, items, selectedIdx);
        }
    }

    public void hideFullWidthCandidatePopup() {
        if (mInputView != null) {
            mInputView.hideFullWidthCandidatePopup();
        }
    }

    public float getCursorTop() {
        return mCursorTop;
    }

    public float getCursorBottom() {
        return mCursorBottom;
    }

    public float getCursorHorizontal() {
        return mCursorHorizontal;
    }

    public boolean isCursorInvisible() {
        return mIsCursorInvisible;
    }

    public int getCurrentCandidateIndex() {
        return mEngine != null ? mEngine.getCurrentCandidateIndex() : 0;
    }

    public void setCandidateIndex(int index) {
        if (mEngine != null) {
            mEngine.setCandidateIndex(index);
        }
    }

    /**
     * 指定されたテキストでツールチップを即座に表示、または更新します。
     * <p>
     * Gboard 等の標準的な IME の挙動に合わせ、キャレットの直下付近に表示されるよう制御します。
     * PopupWindow を IME ウィンドウ（InputView）に紐付けつつ、相対座標を指定することで
     * システムによるウィンドウ外描画の制限を回避しています。
     * </p>
     *
     * @param text 表示するテキスト
     */
    private void showTooltipNow(String text) {
        mHideHandler.removeCallbacks(mHideRunnable);

        if (mTooltipPopup != null && mTooltipPopup.isShowing()) {
            setTooltipViewText(mTooltipPopup.getContentView(), text);
            updateTooltipPosition();
        } else {
            LayoutInflater inflater = LayoutInflater.from(this);
            View view = inflater.inflate(R.layout.tooltip_view, null);
            setTooltipViewText(view, text);

            mTooltipPopup = new PopupWindow(view, ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            mTooltipPopup.setFocusable(false);
            mTooltipPopup.setAnimationStyle(0); // アニメーションを無効化

            int[] coords = calculateTooltipScreenCoordinates(view);
            showPopupAtScreenLocation(mTooltipPopup, coords[0], coords[1]);

            logI(String.format("showTooltip: SCR(%.1f, %.1f), TARGET(%d, %d)",
                    mCursorHorizontal, mCursorTop, coords[0], coords[1]));
        }
        if (!mIsRegistering) {
            mHideHandler.postDelayed(mHideRunnable, mTooltipDuration);
        }
    }

    /**
     * ストローク入力のヘルプをポップアップで表示します。
     * <p>
     * 内部で {@link ViewFlipper} を使用しており、「前へ」「次へ」ボタンで
     * 複数のヘルプページをループ表示します。
     * </p>
     * <p>
     * 配置ロジック：
     * 1. 画面全体の幅とコンテンツの計測高さを取得し、PopupWindow のサイズを確定させます。
     * 2. アンカービュー（InputView のルート）のスクリーン位置を取得します。
     * 3. キーボードの上端にピッタリ重なるスクリーン座標を算出し、それをアンカー基準の相対オフセットに変換します。
     * 4. {@code setClippingEnabled(false)} により、IME ウィンドウの境界を超えた位置への表示を実現します。
     * </p>
     */
    public void showStrokeHelp() {
        if (mHelpPopup != null && mHelpPopup.isShowing()) {
            return;
        }

        LayoutInflater inflater = LayoutInflater.from(this);
        View helpView = inflater.inflate(R.layout.stroke_help, null);
        int screenWidth = getResources().getDisplayMetrics().widthPixels;

        // 表示前にサイズを計測して高さを確定させる
        helpView.measure(View.MeasureSpec.makeMeasureSpec(screenWidth, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED));
        int helpHeight = helpView.getMeasuredHeight();

        View anchor = getAnchorView();
        if (anchor != null) {
            int[] loc = new int[2];
            anchor.getLocationOnScreen(loc);

            // 幅を実数値で指定
            mHelpPopup = new PopupWindow(helpView, screenWidth, ViewGroup.LayoutParams.WRAP_CONTENT);
            mHelpPopup.setFocusable(true);
            mHelpPopup.setOutsideTouchable(true);
            mHelpPopup.setTouchable(true);
            mHelpPopup.setBackgroundDrawable(new ColorDrawable(0));
            mHelpPopup.setAnimationStyle(0);

            ViewFlipper flipper = (ViewFlipper) helpView;
            flipper.setOnClickListener(v -> flipper.showNext());

            // スクリーン絶対座標 (キーボードの上端 - 算出された高さ)
            int targetX = loc[0];
            int targetY = loc[1] - helpHeight;

            int statusBarHeight = getStatusBarHeight();
            // 画面上端（ステータスバー領域）を超える場合はステータスバーの下に固定
            if (targetY < statusBarHeight) {
                targetY = statusBarHeight;
            }

            logI(String.format("showStrokeHelp: SCR_Y=%d, H=%d", targetY, helpHeight));
            showPopupAtScreenLocation(mHelpPopup, targetX, targetY);
        }
    }

}
