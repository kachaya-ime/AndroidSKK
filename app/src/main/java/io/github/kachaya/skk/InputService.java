package io.github.kachaya.skk;

import android.annotation.SuppressLint;
import android.content.SharedPreferences;
import android.graphics.Matrix;
import android.graphics.RectF;
import android.inputmethodservice.InputMethodService;
import android.os.Handler;
import android.os.Looper;
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
    /** UI（ステータスアイコン・ツールチップ）の更新リクエストがあるかどうか。 */
    private boolean mNeedsUIUpdate = false;
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
     * 変換エンジン、辞書の初期化、および設定変更リスナーの登録を行います。
     */
    @Override
    public void onCreate() {
        logI("onCreate()");
        super.onCreate();
        mDictionary = new Dictionary(this);
        mEngine = new SKKEngine(this, mDictionary);

        // 設定変更をリアルタイムに検知するために登録
        mPrefs = PreferenceManager.getDefaultSharedPreferences(this);
        mPrefs.registerOnSharedPreferenceChangeListener(this);
        readPrefs();
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
     * エディタの属性（パスワード、数値入力など）に応じた初期モードの設定や、
     * カーソル監視のリクエストを行います。
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

        // 状態のリセット
        resetCursorState();
        mNeedsUIUpdate = false;

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
    }

    /**
     * 入力ウィンドウが隠れた際に呼び出されます。
     */
    @Override
    public void onWindowHidden() {
        logI("onWindowHidden()");
        super.onWindowHidden();
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
        // カーソルが移動（タップ等）した可能性があるため、一旦座標を無効化する
        resetCursorState();
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

        // すでに表示中のツールチップがあれば位置を合わせる
        updateTooltipPosition();

        // 座標が更新されたので、保留中の表示リクエストがあれば実行する
        performUIUpdate();
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
        int popupHeight = tv.getMeasuredHeight();
        float density = getResources().getDisplayMetrics().density;

        int x = (mComposingHorizontal != -1) ? (int) mComposingHorizontal : (int) mCursorHorizontal + (int) (4 * density);
        int y = (int) mCursorTop - popupHeight - (int) (30 * density);

        mTooltipPopup.update(x, y, -1, -1);
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
        return mIsInputTypeNull || getCurrentInputConnection() == null;
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
     * @return 常に true
     */
    @Override
    @SuppressLint("MissingSuperCall")
    public boolean onEvaluateInputViewShown() {
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
     * 文字コード（または特殊コード）を変換エンジンに渡して処理を継続します。
     *
     * @param code 文字コード
     */
    void processKey(int code) {
        if (isInputDisabled()) {
            return;
        }
        mEngine.processKey(code);
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
     * @return 設定に成功した場合は true
     */
    public boolean setComposingText(CharSequence text, int newCursorPosition) {
        InputConnection ic = getCurrentInputConnection();
        if (ic == null) {
            mHasComposingText = false;
            return false;
        }
        if (!mHasComposingText && text.length() == 0) {
            return true;
        }
        mHasComposingText = text.length() != 0;
        return ic.setComposingText(text, newCursorPosition);
    }

    /**
     * ステータス UI（アイコンおよびツールチップ）の更新リクエストを登録します。
     * 座標が確定するまで一旦表示を隠し、確定後に最新の情報を表示します。
     */
    public void requestUIUpdate() {
        mNeedsUIUpdate = true;
        hideStatusIcon();
        performUIUpdate();
    }

    /**
     * ステータス表示（アイコン等）を完全に消去します。
     */
    public void dismissStatusUI() {
        mNeedsUIUpdate = false;
        hideStatusIcon();
        mHideHandler.removeCallbacks(mHideRunnable);
        if (mTooltipPopup != null) {
            mTooltipPopup.dismiss();
        }
    }

    /**
     * 保留中の UI 更新があれば実行します。
     * 座標が取得できていない、またはカーソルが不可視の場合は表示を保留します。
     */
    private void performUIUpdate() {
        if (!mNeedsUIUpdate || isInputDisabled()) {
            return;
        }
        // ビューが準備できていない、座標が未確定（0,0）、またはカーソルが不可視（座標があっても領域外など）の場合は保留
        if (mInputView == null || mInputView.getWindowToken() == null 
                || (mCursorHorizontal == 0 && mCursorTop == 0) 
                || mIsCursorInvisible) {
            return;
        }

        // 表示条件が整ったのでフラグを落とす
        mNeedsUIUpdate = false;

        // 1. ステータスアイコンの更新
        int iconRes = mEngine.getCurrentIcon();
        if (iconRes != 0) {
            showStatusIcon(iconRes);
        } else {
            hideStatusIcon();
        }

        // 2. ツールチップの更新
        if (mTooltipDuration > 0) {
            String text = mEngine.getCurrentTooltip();
            if (text != null && !text.isEmpty()) {
                showTooltipNow(text);
            }
        }
    }

    /**
     * 指定されたテキストでツールチップを即座に表示します。
     *
     * @param text 表示するテキスト
     */
    private void showTooltipNow(String text) {
        mHideHandler.removeCallbacks(mHideRunnable);
        if (mTooltipPopup != null) {
            mTooltipPopup.dismiss();
        }

        LayoutInflater inflater = LayoutInflater.from(this);
        TextView tv = (TextView) inflater.inflate(R.layout.tooltip_view, null);
        tv.setText(text);

        mTooltipPopup = new PopupWindow(tv, ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        mTooltipPopup.setClippingEnabled(false);
        mTooltipPopup.setAnimationStyle(0); // アニメーションを無効化
        mTooltipPopup.setWindowLayoutType(WindowManager.LayoutParams.TYPE_APPLICATION_ATTACHED_DIALOG);

        float density = getResources().getDisplayMetrics().density;
        tv.measure(View.MeasureSpec.UNSPECIFIED, View.MeasureSpec.UNSPECIFIED);
        int popupHeight = tv.getMeasuredHeight();

        int x = (mComposingHorizontal != -1) ? (int) mComposingHorizontal : (int) mCursorHorizontal + (int) (4 * density);
        int y = (int) mCursorTop - popupHeight - (int) (30 * density);

        try {
            mTooltipPopup.showAtLocation(mInputView, Gravity.NO_GRAVITY, x, y);
        } catch (Exception e) {
            logI("Failed to show tooltip: " + e.getMessage());
        }
        mHideHandler.postDelayed(mHideRunnable, mTooltipDuration);
    }
}
