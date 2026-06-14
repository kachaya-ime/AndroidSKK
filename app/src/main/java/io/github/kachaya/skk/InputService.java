package io.github.kachaya.skk;

import android.annotation.SuppressLint;
import android.content.SharedPreferences;
import android.graphics.Matrix;
import android.graphics.RectF;
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
import android.view.WindowManager;
import android.view.inputmethod.CursorAnchorInfo;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputConnection;
import android.widget.PopupWindow;
import android.widget.TextView;

import androidx.preference.PreferenceManager;

import java.util.List;

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
    /** 変換エンジン。状態遷移やローマ字かな変換のメインロジックを保持します。 */
    private SKKEngine mEngine;

    /** 辞書管理マネージャ。システム辞書およびユーザー辞書へのアクセスを提供します。 */
    private Dictionary mDictionary;

    /** ソフトウェアキーボードおよび変換候補を表示するビュー。 */
    private InputView mInputView;

    /** 共有設定の参照。リスナーがガベージコレクションされないよう強参照で保持します。 */
    private SharedPreferences mPrefs;

    // 設定項目
    /** URI 入力などの特定のフィールドで自動的に英字モードに切り替える設定。 */
    private boolean mAutoAsciiMode = false;
    /** SandS (Space and Shift) 機能を有効にする設定。 */
    private boolean mSandS = false;
    /** モード切替時にカーソル付近にツールチップを表示する時間（ミリ秒）。0 の場合は非表示。 */
    private int mTooltipDuration = 1000;

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
    /** 未確定文字列の開始 X 座標（存在しない場合は -1）。 */
    private float mComposingHorizontal = -1;
    /** UI（ツールチップ）の更新リクエストがあるかどうか。 */
    private boolean mNeedsTooltipUpdate = false;
    /** ツールチップ表示用のポップアップ。 */
    private PopupWindow mTooltipPopup;
    /** ツールチップを閉じる実行タスク。 */
    private final Runnable mHideRunnable = () -> {
        if (mTooltipPopup != null) {
            mTooltipPopup.dismiss();
        }
    };

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
    public static void setupDefaultPreferences(android.content.Context context) {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);

        // 1. 動的なデフォルト判定（XMLの static な値より先に処理して優先させる）
        if (!prefs.contains("keyboard_type")) {
            android.content.res.Configuration config = context.getResources().getConfiguration();
            boolean hasHardwareKeyboard = (config.keyboard != android.content.res.Configuration.KEYBOARD_NOKEYS &&
                    config.keyboard != android.content.res.Configuration.KEYBOARD_UNDEFINED);
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
            mInputView = new InputView(this);
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
        mHasComposingText = false;

        mIsInputTypeNull = false;
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
                        }
                    default:
                        break;
                }
                break;
            case InputType.TYPE_NULL:
                mIsInputTypeNull = true;
                break;
            default:
                break;
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
            mInputView.doStartInputView(editorInfo, restarting);
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

        // ウィンドウが表示されたタイミングでUI状態を最新にする
        requestUIUpdate();
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
        mDictionary.commitChanges();
        super.onDestroy();
    }

    /**
     * エディタ内での選択範囲やカーソル位置が更新された際に呼び出されます。
     */
    @Override
    public void onUpdateSelection(int oldSelStart, int oldSelEnd, int newSelStart, int newSelEnd, int candidatesStart, int candidatesEnd) {
        super.onUpdateSelection(oldSelStart, oldSelEnd, newSelStart, newSelEnd, candidatesStart, candidatesEnd);
        // 位置が変わっただけであれば resetCursorState() は行わず、
        // onUpdateCursorAnchorInfo での更新を待つ（ちらつき防止のため）
    }

    /**
     * エディタからカーソルやテキストの矩形情報が通知された際に呼び出されます。
     * ステータス表示をキャレット位置に追従させるための座標計算を行います。
     *
     * @param cursorAnchorInfo 通知された情報
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

            int composingStart = cursorAnchorInfo.getComposingTextStart();
            if (composingStart >= 0) {
                RectF firstCharRect = cursorAnchorInfo.getCharacterBounds(composingStart);
                if (firstCharRect != null) {
                    float[] compPoints = new float[]{firstCharRect.left, firstCharRect.top};
                    matrix.mapPoints(compPoints);
                    mComposingHorizontal = compPoints[0];
                } else {
                    mComposingHorizontal = -1;
                }
            } else {
                mComposingHorizontal = -1;
            }
        }

        // 視認性が変わった場合はアイコンを更新
        if (wasInvisible != mIsCursorInvisible) {
            updateStatusIcon();
            if (mIsCursorInvisible) {
                // カーソルが消えた場合は、不整合を防ぐためエンジンの未確定状態をリセットする
                mEngine.reset();
                setComposingText("", 1);
            }
        }

        // すでに表示中のツールチップがあれば位置を合わせる
        updateTooltipPosition();

        // 座標が更新されたので、保留中のツールチップ表示リクエストがあれば実行する
        performTooltipUpdate();
    }

    /**
     * カーソルに関連する座標情報を初期状態に戻します。
     */
    private void resetCursorState() {
        mCursorHorizontal = 0;
        mCursorTop = 0;
        mCursorBottom = 0;
        mComposingHorizontal = -1;
        mIsCursorInvisible = true;
    }

    /**
     * 表示中のツールチップの位置を最新のカーソル座標に合わせて更新します。
     * スクリーン絶対座標を IME ウィンドウのベース座標を考慮して変換適用します。
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

        View tv = mTooltipPopup.getContentView();
        tv.measure(View.MeasureSpec.UNSPECIFIED, View.MeasureSpec.UNSPECIFIED);
        int popupHeight = tv.getMeasuredHeight();
        float density = getResources().getDisplayMetrics().density;

        // スクリーン座標を IME ウィンドウの相対座標に変換するための基準取得
        int[] locScreen = new int[2];
        int[] locWindow = new int[2];
        View anchor = (mInputView != null) ? mInputView.getRootView() : null;
        if (anchor != null) {
            anchor.getLocationOnScreen(locScreen);
            anchor.getLocationInWindow(locWindow);
        }

        // ウィンドウ自体のスクリーン上での開始位置を正確に算出
        int winX = locScreen[0] - locWindow[0];
        int winY = locScreen[1] - locWindow[1];

        int x = (mComposingHorizontal != -1) ? (int) mComposingHorizontal : (int) mCursorHorizontal + (int) (4 * density);
        int y = (int) mCursorTop - popupHeight - (int) (30 * density);

        // オフセット補正
        int finalX = x - winX;
        int finalY = y - winY;

        // 補正結果が異常な場合（Titan 等の特定デバイス）は補正を無効化
        if (finalY < -100) {
            finalX = x;
            finalY = y;
        }

        try {
            mTooltipPopup.update(finalX, finalY, -1, -1);
        } catch (Exception ignored) {
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
        readPrefs();
    }

    /**
     * 現在の入力状態（エディタの属性やカーソルの可視性）から、
     * SKK による処理を無効にすべきかどうかを判定します。
     *
     * @return 無効な場合は true
     */
    private boolean isInputDisabled() {
        // カーソルが表示されていない場合も、物理キー入力をシステムに流し、IME UI を隠すために disabled と見なす
        return mIsInputTypeNull || getCurrentInputConnection() == null || mIsCursorInvisible;
    }

    /**
     * 最新の設定値を SharedPreferences から読み込みます。
     * InputView が存在する場合はその設定も一括して更新します。
     */
    private void readPrefs() {
        SharedPreferences prefs = (mPrefs != null) ? mPrefs : PreferenceManager.getDefaultSharedPreferences(this);
        mSandS = prefs.getBoolean("s_and_s", false);
        // 設定画面（秒）の値を内部用のミリ秒に変換
        mTooltipDuration = prefs.getInt("tooltip_duration_sec", 1) * 1000;
        mAutoAsciiMode = prefs.getBoolean("auto_ascii_mode", false);

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
        if (mIsInputTypeNull) return false;
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

        if (event.isCtrlPressed()) {
            if (mEngine.processCtrlKey(keyCode)) {
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
        if (mSandS && mSpacePressed) {
            c = event.getUnicodeChar(KeyEvent.META_SHIFT_ON);
            mSandSUsed = true;
        } else {
            c = event.getUnicodeChar();
        }

        InputConnection ic = getCurrentInputConnection();
        if (c == 0 || ic == null) {
            return false;
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
        if (isInputDisabled()) return;
        if (!mEngine.handleBackspace()) {
            sendDownUpKeyEvents(KeyEvent.KEYCODE_DEL);
        }
    }

    /**
     * エンターキー処理を実行します。
     */
    void handleEnter() {
        if (isInputDisabled()) return;
        if (!mEngine.handleEnter()) {
            sendDownUpKeyEvents(KeyEvent.KEYCODE_ENTER);
        }
    }

    /**
     * かなモードの切り替えを実行します。
     */
    void handleToggleKana() {
        if (isInputDisabled()) return;
        mEngine.toggleKana();
    }

    /**
     * ひらがなモードへの復帰を実行します。
     */
    void handleKanaKey() {
        if (isInputDisabled()) return;
        mEngine.handleKanaKey();
    }

    /**
     * キーボードの「モード」ボタン押下時の処理。
     * 英数モードならひらがなへ、かなモードなら相互に切り替えます。
     */
    void handleModeButton() {
        if (isInputDisabled()) return;
        if (mEngine.getMode() == SKKModeHalfLatin.INSTANCE) {
            mEngine.handleKanaKey();
        } else {
            mEngine.toggleKana();
        }
    }

    /**
     * Ctrl キーとの組み合わせ入力を処理します。
     *
     * @param keyCode KeyEvent で定義されているキーコード
     */
    void handleCtrlKey(int keyCode) {
        if (isInputDisabled()) return;
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
        if (isInputDisabled()) return;
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
        if (isInputDisabled()) return;
        if (!mEngine.handleDpad(keyCode)) {
            sendDownUpKeyEvents(keyCode);
        }
    }

    /**
     * 候補リストを UI に設定し、候補表示エリアを可視化します。
     *
     * @param list 表示する候補文字列のリスト
     */
    public void setCandidates(List<String> list) {
        if (mInputView != null) {
            if (list != null) {
                mInputView.setCandidates(list);
                mInputView.showCandidatesView();
            } else {
                mInputView.hideCandidatesView();
            }
        }
    }

    /**
     * 詳細な候補情報（ユーザー辞書フラグ等を含む）を UI に設定します。
     *
     * @param candidates 表示する候補オブジェクトのリスト
     */
    public void setCandidateObjects(List<Candidate> candidates) {
        if (mInputView != null) {
            if (candidates != null) {
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
     * 再変換の準備として、カーソル直前の指定文字列を削除します。
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
     * 未確定文字列をエディタに設定します。
     *
     * @param text              未確定文字列
     * @param newCursorPosition 新しいカーソル位置
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
        mHasComposingText = text.length() != 0;
        ic.setComposingText(text, newCursorPosition);
    }

    /**
     * ステータス UI（アイコンおよびツールチップ）の更新リクエストを登録します。
     * 座標が必要なツールチップ等は座標確定後に更新します。
     */
    public void requestUIUpdate() {
        // ステータスアイコンは座標に依存しないため、即座に更新する
        updateStatusIcon();

        // ツールチップは座標が必要なため、保留フラグを立てて実行を試みる
        mNeedsTooltipUpdate = true;
        performTooltipUpdate();
    }

    /**
     * ステータスアイコンを最新の状態に更新します。
     */
    private void updateStatusIcon() {
        if (isInputDisabled()) {
            // カーソル不可視等の場合はアイコンを表示しない
            hideStatusIcon();
            return;
        }
        int iconRes = mEngine.getCurrentIcon();
        if (iconRes != 0) {
            showStatusIcon(iconRes);
        } else {
            hideStatusIcon();
        }
    }

    /**
     * ステータス表示（アイコン等）を完全に消去します。
     */
    public void dismissStatusUI() {
        mNeedsTooltipUpdate = false;
        hideStatusIcon();
        mHideHandler.removeCallbacks(mHideRunnable);
        if (mTooltipPopup != null) {
            mTooltipPopup.dismiss();
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
     * 指定されたテキストでツールチップを即座に表示、または更新します。
     * スクリーン絶対座標を IME ウィンドウのベース座標を考慮して変換適用します。
     *
     * @param text 表示するテキスト
     */
    private void showTooltipNow(String text) {
        mHideHandler.removeCallbacks(mHideRunnable);

        // スクリーン座標を IME ウィンドウ内の相対座標に変換するための基準取得
        int[] locScreen = new int[2];
        int[] locWindow = new int[2];
        View anchor = (mInputView != null) ? mInputView.getRootView() : null;
        if (anchor != null) {
            anchor.getLocationOnScreen(locScreen);
            anchor.getLocationInWindow(locWindow);
        }

        // ウィンドウ自体のスクリーン上での開始位置を正確に算出
        int winX = locScreen[0] - locWindow[0];
        int winY = locScreen[1] - locWindow[1];

        if (mTooltipPopup != null && mTooltipPopup.isShowing()) {
            // 既存のポップアップがあれば内容と位置だけ更新（ちらつき防止）
            TextView tv = (TextView) mTooltipPopup.getContentView();
            tv.setText(text);
            updateTooltipPosition();
        } else {
            // 新規作成
            LayoutInflater inflater = LayoutInflater.from(this);
            TextView tv = (TextView) inflater.inflate(R.layout.tooltip_view, null);
            tv.setText(text);

            mTooltipPopup = new PopupWindow(tv, ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            mTooltipPopup.setClippingEnabled(false);
            mTooltipPopup.setAnimationStyle(0); // アニメーションを無効化
            mTooltipPopup.setWindowLayoutType(WindowManager.LayoutParams.TYPE_APPLICATION_ATTACHED_DIALOG);

            tv.measure(View.MeasureSpec.UNSPECIFIED, View.MeasureSpec.UNSPECIFIED);
            int popupHeight = tv.getMeasuredHeight();
            float density = getResources().getDisplayMetrics().density;

            int x = (mComposingHorizontal != -1) ? (int) mComposingHorizontal : (int) mCursorHorizontal + (int) (4 * density);
            int y = (int) mCursorTop - popupHeight - (int) (30 * density);

            // オフセット補正
            int finalX = x - winX;
            int finalY = y - winY;

            // 補正結果が異常な場合（Titan 等の特定デバイス）は補正を無効化
            if (finalY < -100) {
                finalX = x;
                finalY = y;
            }

            logI(String.format("showTooltip: SCR(%.1f, %.1f), WIN(%d, %d), DST(%d, %d)",
                    mCursorHorizontal, mCursorTop, winX, winY, finalX, finalY));

            try {
                // anchor をアンカーにして算出した相対座標で表示
                mTooltipPopup.showAtLocation(anchor, Gravity.NO_GRAVITY, finalX, finalY);
            } catch (Exception e) {
                logI("Failed to show tooltip: " + e.getMessage());
            }
        }
        mHideHandler.postDelayed(mHideRunnable, mTooltipDuration);
    }
}
