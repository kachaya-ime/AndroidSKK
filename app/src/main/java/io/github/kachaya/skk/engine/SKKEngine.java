package io.github.kachaya.skk.engine;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.res.Resources;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.style.BackgroundColorSpan;
import android.text.style.UnderlineSpan;
import android.util.Log;
import android.view.inputmethod.InputConnection;

import androidx.preference.PreferenceManager;

import java.time.LocalDateTime;
import java.time.chrono.JapaneseDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import io.github.kachaya.skk.BuildConfig;
import io.github.kachaya.skk.InputService;
import io.github.kachaya.skk.R;

/**
 * SKK の入力・変換ロジックを統括する engine クラスです。
 * <p>
 * ユーザーのキー入力を受け取り、現在の状態（{@link SKKState}）やモード（{@link SKKMode}）に応じて、
 * ローマ字変換、辞書検索（漢字変換）、単語登録、補完（Suggestion）などのアクションを制御します。
 * 入力サービス（{@link InputService}）と辞書（{@link Dictionary}）の仲介役として動作します。
 * </p>
 */
public class SKKEngine {

    /** 親となる入力サービスへの参照。 */
    private final InputService mService;
    /** 使用する辞書管理インスタンス。 */
    private final Dictionary mDictionary;
    /** 各種リソースアクセスのためのコンテキスト。 */
    private final Context mContext;
    /** ローマ字かな変換エンジン。 */
    private final RomajiConverter mConverter;
    /**
     * 漢字変換または Abbrev 変換の「見出し語（Headword）」バッファ。
     * 変換開始から確定まで内容が保持されます。
     */
    private final StringBuilder mHeadword = new StringBuilder();
    /**
     * 単語登録プロセスのコンテキストを保持するスタック。
     * 再帰的な登録（登録中に別の単語を登録する等）に対応するため、{@link Deque} を使用します。
     */
    private final Deque<RegistrationInfo> mRegistrationStack = new ArrayDeque<>();
    /** ▽（見出し語入力中）の背景色。 */
    private final int mColorComposing;
    /** ▼（変換中）の背景色。 */
    private final int mColorConverting;
    /** 全角入力が優先される記号（。、など）のマッピングテーブル。 */
    private final Map<String, String> mFullWidthSeparatorMap;
    /** 現在の入力モード。初期値は全角ひらがな。 */
    private SKKMode mMode = SKKModeFullHiragana.INSTANCE;
    /** 現在の入力状態。初期値は確定（Direct）モード。 */
    private SKKState mState = SKKStateDirect.INSTANCE;
    /** 現在ユーザーに提示されている変換候補のリスト。 */
    private List<Candidate> mCandidateList = Collections.emptyList();
    /** 候補リスト内で現在選択されている項目のインデックス。 */
    private int mCurrentCandidateIndex;
    /** 現在提示されている補完のリスト。 */
    private List<String> mSuggestionList = Collections.emptyList();
    /** 補完リスト内で現在選択されている項目のインデックス。 */
    private int mCurrentSuggestionIndex;
    /** 現在入力中の送り仮名。確定したかなのみが格納されます。 */
    private String mOkurigana = null;
    /**
     * 送り仮名の先頭となるアルファベットの子音（送り子音）。
     * 辞書検索（送りありエントリの特定）に必須の情報です。
     */
    private String mOkuriConsonant = null;
    /** 状態表示（▼▽）の目印を表示するかどうか。 */
    private boolean mDisplayState = true;
    /** 半角カタカナを使用するかどうか。 */
    private boolean mUseJisx0201Kana = false;
    /** 設定により ユーザー辞書への学習機能をを有効にするかどうか。 */
    private boolean mEnableLearning = true;
    /** 候補選択が最後に達したときに辞書登録するかどうか。 */
    private boolean mRegisterOnLastCandidate = true;
    /** 直近の正常な確定情報（再変換用）。 */
    private ConversionInfo mLastConversion = null;


    /**
     * 現在の入力モードを取得します。
     *
     * @return 現在の {@link SKKMode}
     */
    public SKKMode getMode() {
        return mMode;
    }

    public Context getContext() {
        return mContext;
    }

    /**
     * SKKEngine を構築します。
     *
     * @param context    親サービス（リソースアクセス用）
     * @param dictionary 検索・学習に使用する辞書
     */
    public SKKEngine(Context context, Dictionary dictionary) {
        mContext = context;
        mService = (InputService) context;
        mDictionary = dictionary;
        mConverter = new RomajiConverter(this);

        mFullWidthSeparatorMap = new HashMap<>();
        readPrefs();

        Resources res = context.getResources();
        mColorComposing = res.getColor(R.color.composing_composing, context.getTheme());
        mColorConverting = res.getColor(R.color.composing_converting, context.getTheme());
    }

    /**
     * デバッグビルド時のみログを出力します。
     *
     * @param msg ログメッセージ
     */
    private void logI(String msg) {
        if (BuildConfig.DEBUG) {
            Log.i("SKKEngine", msg);
        }
    }

    /**
     * 共有設定（SharedPreferences）から最新の設定を読み込み、エンジンへ反映します。
     * <p>
     * 句読点の種類（。、 vs ．，）、状態表示シンボルの有無、
     * 半角カタカナの使用設定を更新します。
     * </p>
     */
    public void readPrefs() {

        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(mContext);

        // 句読点の設定を直接マップに反映
        boolean useKutenJp = prefs.getBoolean("use_kuten_jp", true);
        String kuten = useKutenJp ? "。" : "．";
        mFullWidthSeparatorMap.put(".", kuten);

        boolean useToutenJp = prefs.getBoolean("use_touten_jp", true);
        String touten = useToutenJp ? "、" : "，";
        mFullWidthSeparatorMap.put(",", touten);

        // 各種フラグの更新
        mDisplayState = prefs.getBoolean("display_state", true);
        mUseJisx0201Kana = prefs.getBoolean("use_jisx0201_kana", false);
        mEnableLearning = prefs.getBoolean("enable_learning", true);
        mRegisterOnLastCandidate = prefs.getBoolean("register_on_last_candidate", true);

        logI("readPrefs: useKutenJp =" + useKutenJp);
        logI("readPrefs: useToutenJp =" + useToutenJp);
        logI("readPrefs: useJisx0201Kana =" + mUseJisx0201Kana);
        logI("readPrefs: enableLearning =" + mEnableLearning);
        logI("readPrefs: displayState =" + mDisplayState);


    }

    /**
     * 漢字変換、Abbrev 変換、または単語登録バッファのいずれかに未確定の内容があるか判定します。
     *
     * @return SKK 独自の入力バッファに編集中の内容がある場合は true
     */
    public boolean isComposing() {
        boolean hasRegistration = !mRegistrationStack.isEmpty();
        boolean hasConverterComposing = mConverter.hasComposing();
        boolean hasHeadword = mHeadword.length() != 0;
        return hasRegistration || hasConverterComposing || hasHeadword;
    }

    /**
     * システム（IME）による標準的なカーソル移動を許可すべきかを判定します。
     *
     * @return 移動可能な状態（バッファが空）なら true
     */
    public boolean canMoveCursor() {
        return !isComposing();
    }

    /**
     * 現在の入力モードに基づいて、システムへのキーイベント波及を無視すべきか判定します。
     * <p>
     * 以前は直接入力（Direct Input）モードで true を返していましたが、
     * 現在は全角英数変換や Ctrl-J 処理などのために、常に SKK 側で入力を処理します。
     * </p>
     *
     * @return 常に false (SKK がキーイベントをハンドルする)
     */
    public boolean ignoresKeyEvent() {
        return false;
    }

    /**
     * 現在の入力状態（State）およびモード（Mode）に基づいてキー入力を処理します。
     * <p>
     * 1. 現在の {@link SKKState} に対してキー処理を試みます。<br>
     * 2. 状態側で処理されなかった場合、現在の {@link SKKMode} に対してキー処理を委譲します。<br>
     * 処理完了後、エディタ上のインライン表示（Composing Text）を最新状態に更新します。
     * </p>
     *
     * @param code 入力された Unicode コードポイント
     */
    public void processKey(int code) {
        boolean consumed = mState.processKey(this, code);
        if (!consumed) {
            mMode.processKey(this, code);
        }
        updateComposingText();
    }

    /**
     * Ctrlキーと同時押しのキー入力を処理します。
     * <p>
     * 状態（State）およびモード（Mode）に処理を委譲します。
     * 共通のデフォルト処理（カーソル移動等）は行わず、各状態・モードの責務とします。
     * </p>
     *
     * @param keyCode KeyEventで定義されているキーコード
     * @return イベントを消費した場合は true
     */
    public boolean processCtrlKey(int keyCode) {
        if (mState.processCtrlKey(this, keyCode)) {
            updateComposingText();
            return true;
        }
        if (mMode.processCtrlKey(this, keyCode)) {
            updateComposingText();
            return true;
        }
        return false;
    }

    /**
     * Tabキー入力を処理します。
     *
     * @param isShifted Shiftキーが押されている（逆順選択）かどうか
     * @return イベントを消費した場合は true
     */
    public boolean processTab(boolean isShifted) {
        if (mState.processTab(this, isShifted)) {
            updateComposingText();
            return true;
        }
        return false;
    }

    /**
     * 方向（DPAD）キー入力を処理します。
     *
     * @param keyCode KeyEvent で定義されているキーコード
     * @return イベントを消費した場合は true
     */
    public boolean handleDpad(int keyCode) {
        if (mState.processDpad(this, keyCode)) {
            updateComposingText();
            return true;
        }
        return false;
    }

    /**
     * システム（エディタ）に対してキーイベント送信をリクエストします。
     *
     * @param keyCode KeyEvent で定義されているキーコード
     */
    public void sendDownUpKeyEvents(int keyCode) {
        mService.sendDownUpKeyEvents(keyCode);
    }

    /**
     * キーコードをローマ字かな変換エンジンへ渡し、変換を継続します。
     *
     * @param code キーコード
     */
    public void processRomaji(int code) {
        mConverter.processKey(code);
    }

    /**
     * ローマ字かな変換エンジンから確定テキスト（または記号）を受け取り、処理します。
     * <p>
     * まず、現在の状態（State）に対して "q" や "/" といった特殊な制御文字としての処理機会を与えます。
     * 消費されなかった場合、設定に基づいた句読点変換を試みた後、最終的なかな入力を確定させます。
     * </p>
     *
     * @param text    確定したかな文字列
     * @param initial 変換元となった入力の最初の 1 文字
     * @param isUpper シフトキーが押されていたかどうか
     */
    public void commitRomajiText(String text, char initial, boolean isUpper) {
        if (text != null) {
            // 状態（State）側での特殊キー処理（q, l, /, >, . 等）を優先
            boolean handledByState = mState.processRomajiExtension(this, text, isUpper);
            if (handledByState) {
                return;
            }
        }

        // 句読点変換の適用 (Mapにない場合は元のtextをそのまま使用)
        String committedText = mFullWidthSeparatorMap.getOrDefault(text, text);
        mState.processText(this, committedText, initial, isUpper);
    }

    /**
     * 1 つの「かな」としての区切りが完了した際（Leafノード到達時）のコールバックです。
     */
    public void onFinishRomaji() {
        mState.onFinishRomaji(this);
    }

    /**
     * かなキー（Ctrl-J）入力を処理し、確定モード・ひらがなモードへ戻します。
     * 進行中のローマ字変換や漢字変換を強制終了し、バッファをリセットして初期状態に復帰します。
     */
    public void handleKanaKey() {
        mConverter.flush();
        mState.finish(this);
        // 状態を強制的に確定モードへ戻し、バッファをリセットする
        changeState(SKKStateDirect.INSTANCE);
        if (mMode != SKKModeFullHiragana.INSTANCE) {
            changeMode(SKKModeFullHiragana.INSTANCE, false);
        }
        updateComposingText();
    }

    /**
     * 戻る（Back）キー入力を処理します。
     *
     * @return SKK 側でイベントを消費した場合は true
     */
    public boolean handleBackKey() {
        if (mState.handleBackKey(this)) {
            updateComposingText();
            return true;
        }
        return false;
    }

    /**
     * エンターキー入力を処理し、現在の入力を確定させます。
     *
     * @return SKK 内部の確定処理として消費した場合は true
     */
    public boolean handleEnter() {
        if (mState.processEnter(this)) {
            updateComposingText();
            return true;
        }
        return false;
    }

    /**
     * バックスペースキー入力を処理します。
     * <p>
     * 優先順位に基づき、以下の順で削除を試みます：
     * <ol>
     *   <li>ローマ字かな変換エンジンの未確定バッファ</li>
     *   <li>現在の状態（State）による個別処理（見出し語の末尾削除や変換キャンセル等）</li>
     * </ol>
     * </p>
     *
     * @return 何らかの削除処理を実行し、イベントを消費した場合は true
     */
    public boolean handleBackspace() {
        SKKState state = mState;
        state.beforeBackspace(this);

        // 1. ローマ字変換エンジンの未確定バッファ ('k' まで打った状態など) を優先して消す
        if (mConverter.handleBackspace()) {
            state.afterBackspace(this);
            updateComposingText();
            return true;
        }

        // 2. 状態（State）側での削除処理（見出し語（Headword）の末尾削除、変換キャンセルなど）
        if (state.processBackspace(this)) {
            state.afterBackspace(this);
            updateComposingText();
            return true;
        }

        // 3. 一時的な状態（▽モード等）が空になった場合の自動差し戻し判定
        if (!state.isTransient()) {
            return false;
        }
        state.afterBackspace(this);

        updateComposingText();
        return true;
    }

    /**
     * キャンセル操作（Ctrl-G 等）を処理します。
     *
     * @return 何らかのキャンセル処理が実行された場合は true
     */
    public boolean handleCancel() {
        boolean result = false;
        if (mConverter.reset()) {
            result = true;
        }
        if (mState.handleCancel(this)) {
            result = true;
        }
        if (result) {
            updateComposingText();
        }
        return result;
    }

    /**
     * 状態を強制的に英数（ASCII）モードへ切り替えます。
     */
    public void toASCIIMode() {
        mConverter.flush();
        changeMode(SKKModeHalfLatin.INSTANCE, true);
        updateComposingText();
    }

    /**
     * かな（ひらがな/カタカナ）を交互に切り替えます。
     */
    public void toggleKana() {
        mConverter.flush();
        mState.toggleKana(this);
        updateComposingText();
    }

    /**
     * 現在のモードに基づき、トグルした際になるべき次の「かな」モードを取得します。
     *
     * @return 次の {@link SKKMode}
     */
    public SKKMode getToggledKanaMode() {
        return mMode.getToggledKanaMode(this);
    }

    /**
     * 与えられたテキストを、現在のモードの規則に従って最終変換（半角化など）します。
     *
     * @param text 変換対象のテキスト
     * @return 変換後のテキスト
     */
    public CharSequence convertText(CharSequence text) {
        return mMode.convertText(text);
    }

    /**
     * 指定されたテキストをエディタにコミットします。
     * <p>
     * 単語登録セッション中の場合は、エディタへの直接出力ではなく登録バッファ（{@link RegistrationInfo#entry}）に蓄積します。
     * </p>
     *
     * @param text               コミットするテキスト
     * @param newCursorPosition コミット後の新しいカーソル位置
     */
    public void commitTextSKK(CharSequence text, int newCursorPosition) {
        InputConnection ic = mService.getCurrentInputConnection();
        if (ic == null) {
            return;
        }

        RegistrationInfo regInfo = mRegistrationStack.peekFirst();
        if (regInfo != null) {
            regInfo.entry.append(text);
        } else {
            ic.commitText(text, newCursorPosition);
        }
    }

    /**
     * 新しい入力セッションの開始時に、エンジンの内部状態およびバッファを初期化します。
     * <p>
     * 以前のセッションの見出し語や未確定文字列が残らないよう、バッファをクリアし、
     * エディタ上の表示も最新の状態（空）に更新します。
     * </p>
     */
    public void resetOnStartInput() {
        reset();
        if (mState.isTransient()) {
            changeState(SKKStateDirect.INSTANCE);
        }
        mService.requestUIUpdate();
        updateComposingText();
    }

    /**
     * 現在提示されている補完候補リストを取得します。
     *
     * @return 補完候補のリスト、または null
     */
    public List<String> getSuggestionList() {
        return mSuggestionList;
    }

    /**
     * 現在選択されている補完候補を取得します。
     *
     * @return 補完候補文字列、または null
     */
    public String getCurrentSuggestion() {
        if (mSuggestionList.isEmpty()) {
            return null;
        }
        if (mCurrentSuggestionIndex < 0 || mCurrentSuggestionIndex >= mSuggestionList.size()) {
            return null;
        }
        return mSuggestionList.get(mCurrentSuggestionIndex);
    }

    /**
     * 補完リストの中から、前後の候補を選択します。
     *
     * @param isForward 次に進む（正順）なら true
     */
    public void chooseAdjacentSuggestion(boolean isForward) {
        if (mSuggestionList.isEmpty()) {
            return;
        }
        int nextIndex = mCurrentSuggestionIndex;
        if (isForward) {
            nextIndex++;
        } else {
            nextIndex--;
        }

        int listSize = mSuggestionList.size();
        if (nextIndex > listSize - 1) {
            nextIndex = 0;
        } else if (nextIndex < 0) {
            nextIndex = listSize - 1;
        }

        mCurrentSuggestionIndex = nextIndex;
        mService.requestChooseCandidate(mCurrentSuggestionIndex);
    }

    /**
     * 変換候補リストの中から、前後の候補を選択します。
     * <p>
     * リストの末尾を超えた場合は単語登録（Register）へ、先頭を超えた場合は変換キャンセル（見出し語入力状態へ復帰）となります。
     * </p>
     *
     * @param isForward 次に進む（正順）なら true
     */
    public void chooseAdjacentCandidate(boolean isForward) {
        if (mCandidateList.isEmpty()) {
            return;
        }
        int nextIndex = mCurrentCandidateIndex;
        if (isForward) {
            nextIndex++;
        } else {
            nextIndex--;
        }

        int listSize = mCandidateList.size();
        if (nextIndex > listSize - 1) {
            if (mEnableLearning && mRegisterOnLastCandidate) {
                boolean isAbbrevChoose = (mState == SKKStateAbbrevConversion.INSTANCE);
                registerStart(isAbbrevChoose);
            } else {
                mCurrentCandidateIndex = 0;
                mService.requestChooseCandidate(mCurrentCandidateIndex);
            }
            updateComposingText();
            return;
        } else if (nextIndex < 0) {
            if (mOkurigana != null) {
                mHeadword.append(mOkurigana);
                mOkurigana = null;
                mOkuriConsonant = null;
            }
            if (mState == SKKStateHeadwordConversion.INSTANCE) {
                changeState(SKKStateHeadword.INSTANCE);
            } else {
                changeState(SKKStateAbbrev.INSTANCE);
            }
            updateSuggestions();
            updateComposingText();

            mCurrentCandidateIndex = 0;
            return;
        }
        mCurrentCandidateIndex = nextIndex;
        mService.requestChooseCandidate(mCurrentCandidateIndex);
        updateComposingText();
    }

    /**
     * 候補一覧ビューから特定の候補が手動で選択（タップ）された際の処理を行います。
     * 現在の状態が変換中（▼）であれば漢字候補を、入力中（▽）であれば補完候補を選択します。
     *
     * @param index 選択された項目のインデックス
     */
    public void pickCandidateViewManually(int index) {
        if (mState.isConverting()) {
            pickCandidate(index);
            updateComposingText();
        } else {
            boolean isAbbrev = (mState == SKKStateAbbrev.INSTANCE);
            boolean isHeadword = (mState == SKKStateHeadword.INSTANCE);
            if (isAbbrev || isHeadword) {
                pickSuggestion(index);
                updateComposingText();
            }
        }
    }

    /**
     * 未確定のローマ字入力があるかどうかを判定します。
     *
     * @return 入力中なら true
     */
    public boolean hasComposing() {
        return mConverter.hasComposing();
    }

    /**
     * 現在の見出し語（Headword）バッファを取得します。
     *
     * @return 見出し語の StringBuilder
     */
    public StringBuilder getHeadword() {
        return mHeadword;
    }

    /**
     * 現在の送り仮名を取得します。
     *
     * @return 送り仮名文字列
     */
    public String getOkurigana() {
        return mOkurigana;
    }

    /**
     * 送り仮名を設定します。
     *
     * @param okr 送り仮名
     */
    public void setOkurigana(String okr) {
        mOkurigana = okr;
    }

    /**
     * 送り子音を設定します。
     *
     * @param c 送り子音
     */
    public void setOkuriConsonant(String c) {
        mOkuriConsonant = c;
    }

    /**
     * 単語登録バッファスタックが空かどうかを判定します。
     *
     * @return 空なら true
     */
    public boolean isRegistrationStackEmpty() {
        return mRegistrationStack.isEmpty();
    }

    /**
     * 現在の（スタック最上層の）単語登録情報を取得します。
     *
     * @return RegistrationInfo インスタンス、または null
     */
    public RegistrationInfo peekRegistrationInfo() {
        return mRegistrationStack.peekFirst();
    }

    /**
     * エディタ上の未確定文字列（Composing Text）の表示を構築・更新します。
     * <p>
     * 単語登録スタックの内容、現在の状態シンボル（▽▼）、および見出し語・ローマ字バッファの内容を連結し、
     * 適切な背景色（{@link BackgroundColorSpan}）と下線を付与してエディタに送信します。
     * </p>
     */
    void updateComposingText() {
        SpannableStringBuilder ct = new SpannableStringBuilder();

        if (!mRegistrationStack.isEmpty()) {
            Iterator<RegistrationInfo> iterator = mRegistrationStack.descendingIterator();
            while (iterator.hasNext()) {
                RegistrationInfo regInfo = iterator.next();
                int bgStart = ct.length();
                if (mDisplayState) {
                    ct.append("▼");
                }
                ct.append(regInfo.displayKey).append("：");
                int bgEnd = ct.length();
                BackgroundColorSpan regBgSpan = new BackgroundColorSpan(mColorConverting);
                ct.setSpan(regBgSpan, bgStart, bgEnd, Spanned.SPAN_COMPOSING);
                ct.append(regInfo.entry);
            }
        }

        BackgroundColorSpan bg = null;
        int bgStart = 0;

        if (mState.isConverting()) {
            bg = new BackgroundColorSpan(mColorConverting);
            bgStart = ct.length();
            if (mDisplayState) {
                ct.append("▼");
            }
        } else if (mState.isTransient()) {
            bg = new BackgroundColorSpan(mColorComposing);
            bgStart = ct.length();
            if (mDisplayState) {
                ct.append("▽");
            }
        }

        CharSequence stateText = mState.getComposingText(this);
        if (stateText != null) {
            ct.append(stateText);
        }
        CharSequence converterText = mConverter.getComposing();
        ct.append(converterText);

        if (bg != null) {
            int bgEnd = ct.length();
            ct.setSpan(bg, bgStart, bgEnd, Spanned.SPAN_COMPOSING);
        }
        int totalLen = ct.length();
        if (totalLen != 0) {
            UnderlineSpan underline = new UnderlineSpan();
            ct.setSpan(underline, 0, totalLen, Spanned.SPAN_COMPOSING);
        }

        mService.setComposingText(ct, 1);
    }

    /**
     * 漢字変換の辞書検索（見出し語検索）を開始します。
     * <p>
     * 候補が見つからない場合、設定（{@link #mEnableLearning}）が有効であれば
     * 直ちに単語登録モードへ移行します。
     * </p>
     */
    public void conversionStart() {
        boolean success = conversionStartInternal(false, false);
        if (!success) {
            if (mEnableLearning) {
                registerStart(false);
            } else {
                // 学習無効時は登録に移行せず、そのまま確定
                StringBuilder sb = new StringBuilder(mHeadword);
                if (mOkurigana != null) {
                    sb.append(mOkurigana);
                } else if (mOkuriConsonant != null) {
                    sb.append(mOkuriConsonant);
                }
                commitTextSKK(mMode.convertText(sb), 1);
                reset();
                changeState(SKKStateDirect.INSTANCE);
            }
        }
    }

    /**
     * Abbrev モードでの辞書検索を開始します。
     */
    public void abbrevConversionStart() {
        boolean success = conversionStartInternal(true, false);
        if (!success) {
            if (mEnableLearning) {
                registerStart(true);
            } else {
                // Abbrev の場合もそのまま確定
                commitTextSKK(mMode.convertText(mHeadword), 1);
                reset();
                changeState(SKKStateDirect.INSTANCE);
            }
        }
    }

    /**
     * 辞書検索プロセスの共通実装です。
     *
     * @param abbrev        Abbrev モードかどうか
     * @param lastCandidate リストの末尾の候補を最初から選択するかどうか
     * @return 1 つ以上の候補が見つかった場合は true
     */
    private boolean conversionStartInternal(boolean abbrev, boolean lastCandidate) {
        String query = mHeadword.toString();
        if (mOkuriConsonant != null) {
            query += mOkuriConsonant;
        }

        List<Candidate> list = mDictionary.findCandidates(query, mOkurigana);
        if (list.isEmpty()) {
            return false;
        }

        // キーワード '@' 由来のエントリなどを動的としてマーク
        if (query.equals("@")) {
            for (Candidate c : list) {
                c.setDynamic(true);
            }
        }

        if (abbrev) {
            changeState(SKKStateAbbrevConversion.INSTANCE);
        } else {
            changeState(SKKStateHeadwordConversion.INSTANCE);
        }
        mCandidateList = list;
        int listSize = list.size();
        mCurrentCandidateIndex = lastCandidate ? listSize - 1 : 0;
        updateCandidates();
        if (mCurrentCandidateIndex != 0) {
            mService.requestChooseCandidate(mCurrentCandidateIndex);
        }
        return true;
    }

    /**
     * 直近の正常な確定情報（再変換用）。
     *
     * @return 再変換が正常に開始された場合は true
     */
    public boolean reConversion() {
        if (mLastConversion == null) {
            return false;
        }

        ConversionInfo info = mLastConversion;
        String candidate = info.candidate;
        if (mService.prepareReConversion(candidate)) {
            mDictionary.rollback();

            mHeadword.setLength(0);
            mHeadword.append(info.headword);
            mOkurigana = info.okurigana;
            mOkuriConsonant = info.okuriConsonant;
            mCandidateList = info.list;
            mCurrentCandidateIndex = info.index;
            mMode = info.mode;
            updateCandidates();

            if (info.abbrev) {
                changeState(SKKStateAbbrevConversion.INSTANCE);
            } else {
                changeState(SKKStateHeadwordConversion.INSTANCE);
            }
            return true;
        }
        return false;
    }

    /**
     * 現在の候補リストを整形し、UI 側（InputService）へ送ります。
     */
    private void updateCandidates() {
        mService.setCandidateObjects(mCandidateList);
    }

    /**
     * 入力途中の見出し語に基づき、動的補完（Suggestion）    /**
     * リストを更新します。
     */
    public void updateSuggestions() {
        if (mHeadword.length() == 0) {
            mSuggestionList = Collections.emptyList();
            mService.hideCandidatesView();
            return;
        }

        String query = mHeadword.toString();
        if (mOkuriConsonant != null) {
            query += mOkuriConsonant;
        }

        List<String> list = mDictionary.findSuggestions(query);
        if (!list.isEmpty()) {
            mSuggestionList = list;
            mCurrentSuggestionIndex = 0;
            // 漢字候補をクリアして補完リストを表示
            mService.setCandidateObjects(null);
            mService.setCandidates(list);
        } else {
            mSuggestionList = Collections.emptyList();
            mService.hideCandidatesView();
        }
    }

    /**
     * キーワードに基づいて、現在日時等の動的な候補リストを提示します。
     *
     * @param key "today", "date", "now", "time" 等のキーワード
     * @return 候補が見つかり提示を開始した場合は true
     */
    public boolean showDynamicCandidates(String key) {
        if (key == null) return false;

        List<Candidate> list = new ArrayList<>();
        LocalDateTime now = LocalDateTime.now();

        if (key.equalsIgnoreCase("today") || key.equalsIgnoreCase("date")) {
            // 日付バリエーション
            list.add(createDynamicCand(now.format(DateTimeFormatter.ofPattern("yyyy年M月d日"))));
            list.add(createDynamicCand(now.format(DateTimeFormatter.ofPattern("yyyy年M月d日(E)", Locale.JAPANESE))));
            list.add(createDynamicCand(now.format(DateTimeFormatter.ofPattern("yyyy/MM/dd"))));
            list.add(createDynamicCand(now.format(DateTimeFormatter.ofPattern("yyyy-MM-dd"))));
            list.add(createDynamicCand(now.format(DateTimeFormatter.ofPattern("M月d日"))));

            // 和暦 (令和等)
            try {
                JapaneseDate jDate = JapaneseDate.from(now);
                list.add(createDynamicCand(jDate.format(DateTimeFormatter.ofPattern("Gy年M月d日", Locale.JAPANESE))));
            } catch (Exception ignored) {
            }

        } else if (key.equalsIgnoreCase("now") || key.equalsIgnoreCase("time")) {
            // 時刻バリエーション
            list.add(createDynamicCand(now.format(DateTimeFormatter.ofPattern("H:mm"))));
            list.add(createDynamicCand(now.format(DateTimeFormatter.ofPattern("HH:mm:ss"))));
            list.add(createDynamicCand(now.format(DateTimeFormatter.ofPattern("H時m分"))));
            list.add(createDynamicCand(now.format(DateTimeFormatter.ofPattern("yyyy/MM/dd HH:mm"))));
        }

        if (list.isEmpty()) {
            return false;
        }

        changeState(SKKStateHeadwordConversion.INSTANCE);
        mCandidateList = list;
        mCurrentCandidateIndex = 0;
        updateCandidates();
        updateComposingText();
        return true;
    }

    /**
     * 動的に生成される候補オブジェクトを作成します。
     *
     * @param text 候補テキスト
     * @return 生成された Candidate インスタンス
     */
    private Candidate createDynamicCand(String text) {
        // 動的生成候補はシステム辞書扱い（削除不可）とし、動的フラグを立てる
        return new Candidate(text, text, null, null, false).setDynamic(true);
    }

    /**
     * 単語登録セッションを開始します。
     * <p>
     * 現在の見出し語（および送り仮名）を保持したまま、新しい登録レイヤーをスタックに積み、
     * ユーザーが登録語本体を入力できる確定（Normal）状態へ移行します。
     * </p>
     *
     * @param abbrev Abbrev モードからの移行かどうか
     */
    private void registerStart(boolean abbrev) {
        CharSequence convertedKey = mMode.convertText(mHeadword);
        StringBuilder displayKey = new StringBuilder(convertedKey);
        if (mOkurigana != null) {
            CharSequence convertedOkr = mMode.convertText(mOkurigana);
            displayKey.append("*").append(convertedOkr);
        }

        String keyStr = mHeadword.toString();
        String displayKeyStr = displayKey.toString();
        RegistrationInfo regInfo = new RegistrationInfo(
                keyStr,
                mOkurigana,
                mOkuriConsonant,
                displayKeyStr,
                abbrev,
                mMode
        );
        mRegistrationStack.addFirst(regInfo);
        changeState(SKKStateDirect.INSTANCE);
    }

    /**
     * 単語登録を中断し、元の候補選択状態に戻ります。
     */
    public void cancelRegister() {
        RegistrationInfo regInfo = mRegistrationStack.peekFirst();
        if (regInfo == null) {
            return;
        }
        mRegistrationStack.removeFirst();
        mHeadword.setLength(0);
        mHeadword.append(regInfo.key);
        mOkurigana = regInfo.okurigana;
        mOkuriConsonant = regInfo.okuriConsonant;
        mMode = regInfo.mode;

        boolean success = conversionStartInternal(regInfo.abbrev, true);
        if (success) {
            return;
        }

        if (mOkurigana != null) {
            mHeadword.append(mOkurigana);
            mOkurigana = null;
            mOkuriConsonant = null;
        }
        if (regInfo.abbrev) {
            changeState(SKKStateAbbrev.INSTANCE);
        } else {
            changeState(SKKStateHeadword.INSTANCE);
        }
        updateSuggestions();
    }

    /**
     * 単語登録を完了し、現在の登録情報を辞書に保存します。
     */
    public void finishRegistration() {
        RegistrationInfo regInfo = mRegistrationStack.peekFirst();
        if (regInfo == null) {
            return;
        }

        String entry = regInfo.entry.toString();
        if (entry.length() > 0) {
            String key = regInfo.key;
            if (regInfo.okuriConsonant != null) {
                key += regInfo.okuriConsonant;
            }
            mDictionary.addEntry(key, entry, regInfo.okurigana);
        }

        mRegistrationStack.removeFirst();
        // 登録開始時のモードを復元する
        mMode = regInfo.mode;
        updateUI();
    }

    /**
     * 現在選択中の候補文字列（送り込み）を取得します。
     *
     * @return 確定用文字列
     */
    public String getCurrentCandidate() {
        if (mCandidateList.isEmpty()) {
            return "";
        }
        Candidate c = mCandidateList.get(mCurrentCandidateIndex);
        String candidate = c.candidate;
        if (mOkurigana != null) {
            return candidate.concat(mOkurigana);
        }
        return candidate;
    }

    /**
     * 現在選択されている変換候補を確定します。
     */
    public void pickCurrentCandidate() {
        pickCandidate(mCurrentCandidateIndex);
    }

    /**
     * 指定されたインデックスの候補を確定し、学習情報を反映させます。
     *
     * @param index 候補のインデックス
     */
    public void pickCandidate(int index) {
        if (!mState.isConverting() || index < 0 || index >= mCandidateList.size()) {
            return;
        }

        Candidate c = mCandidateList.get(index);
        String candidateStr = c.candidate;
        if (mOkurigana != null) {
            candidateStr = candidateStr + mOkurigana;
        }
        CharSequence finalCandidate = mMode.convertText(candidateStr);
        commitTextSKK(finalCandidate, 1);

        String key = mHeadword.toString();
        if (mOkuriConsonant != null) {
            key += mOkuriConsonant;
        }

        // 動的に生成された候補（学習不要な候補）は辞書登録（学習）をスキップする
        if (mEnableLearning && !c.isDynamic && c.rawCandidate != null) {
            logI("Learning: key=" + key + ", cand=" + c.rawCandidate);
            mDictionary.addEntry(key, c.rawCandidate, mOkurigana);
        } else {
            logI("Skip learning: enable=" + mEnableLearning + ", dynamic=" + c.isDynamic + ", raw=" + (c.rawCandidate != null));
        }

        if (mRegistrationStack.isEmpty()) {
            boolean isAbbrevChoose = (mState == SKKStateAbbrevConversion.INSTANCE);
            mLastConversion = new ConversionInfo(
                    finalCandidate.toString(),
                    mCandidateList,
                    index,
                    mHeadword.toString(),
                    mOkurigana,
                    mOkuriConsonant,
                    isAbbrevChoose,
                    mMode);
        }
        changeState(SKKStateDirect.INSTANCE);
    }

    /**
     * 現在選択されている変換候補をユーザー辞書から削除し、候補リストを更新します。
     */
    public void purgeCurrentCandidate() {
        if (mCandidateList.isEmpty()) {
            return;
        }

        Candidate c = mCandidateList.get(mCurrentCandidateIndex);

        // ユーザー辞書由来でない場合は削除しない
        if (!c.isUserDict) {
            return;
        }

        String key = mHeadword.toString();
        if (mOkuriConsonant != null) {
            key += mOkuriConsonant;
        }

        // 辞書から削除
        mDictionary.removeEntry(key, c.rawCandidate, mOkurigana);

        // リストから削除して UI を更新
        mCandidateList.remove(mCurrentCandidateIndex);

        if (mCandidateList.isEmpty()) {
            // 全ての候補がなくなったら変換キャンセル扱い
            if (mOkurigana != null) {
                mHeadword.append(mOkurigana);
                mOkurigana = null;
                mOkuriConsonant = null;
            }
            if (mState == SKKStateHeadwordConversion.INSTANCE) {
                changeState(SKKStateHeadword.INSTANCE);
            } else {
                changeState(SKKStateAbbrev.INSTANCE);
            }
            updateSuggestions();
        } else {
            // インデックスを調整して再表示
            if (mCurrentCandidateIndex >= mCandidateList.size()) {
                mCurrentCandidateIndex = mCandidateList.size() - 1;
            }
            updateCandidates();
            mService.requestChooseCandidate(mCurrentCandidateIndex);
        }
        updateComposingText();
    }

    /**
     * 現在選択中の補完候補（Suggestion）を確定し、変換中状態へ移行します。
     */
    public void pickCurrentSuggestion() {
        pickSuggestion(mCurrentSuggestionIndex);
    }

    /**
     * 指定されたインデックスの補完候補を確定します。
     *
     * @param index 補完リスト内のインデックス
     */
    public void pickSuggestion(int index) {
        if (index < 0 || index >= mSuggestionList.size()) {
            return;
        }
        String s = mSuggestionList.get(index);

        mHeadword.setLength(0);
        mHeadword.append(s);
        if (mState == SKKStateAbbrev.INSTANCE) {
            abbrevConversionStart();
        } else if (mState == SKKStateHeadword.INSTANCE) {
            int lastIndex = s.length() - 1;
            int lastCode = s.codePointAt(lastIndex);
            if (RomajiConverter.isAlphabet(lastCode)) {
                mHeadword.deleteCharAt(lastIndex);
                int upperCode = Character.toUpperCase(lastCode);
                processKey(upperCode);
            } else {
                conversionStart();
            }
        }
    }

    /**
     * エンジンの内部状態（バッファ、変換候補、登録スタック等）をすべて初期化します。
     * 辞書以外の実行時キャッシュをクリアし、初期の確定モード（Direct）へ戻る準備を整えます。
     */
    public void reset() {
        clearBuffers();
        mRegistrationStack.clear();
    }

    /**
     * 入力バッファや変換候補などの一時的な状態をクリアします。
     * 単語登録スタック（mRegistrationStack）は維持されます。
     */
    public void clearBuffers() {
        mConverter.reset();
        mHeadword.setLength(0);
        mOkurigana = null;
        mOkuriConsonant = null;
        clearCandidates();
    }

    /**
     * 漢字変換候補および補完候補のリストをクリアし、候補一覧ビューを非表示にします。
     * 状態遷移時や、入力をキャンセルしてクリーンアップが必要な際に呼び出されます。
     */
    public void clearCandidates() {
        mCandidateList = Collections.emptyList();
        mSuggestionList = Collections.emptyList();
        mCurrentCandidateIndex = 0;
        mCurrentSuggestionIndex = 0;
        mService.hideCandidatesView();
    }

    /**
     * 入力モードを変更します。
     *
     * @param mode   新しいモード
     * @param finish 現在の状態を強制確定させてリセットする場合は true
     */
    public void changeMode(SKKMode mode, boolean finish) {
        if (finish) {
            mState.finish(this);
            changeState(SKKStateDirect.INSTANCE);
        }
        mMode = mode;
        updateUI();
    }

    /**
     * 入力状態を変更せずに、入力モードのみを変更します。
     * <p>
     * ▽モード（見出し語入力中）での一時的なモード変更などに使用されます。
     * </p>
     *
     * @param mode 新しいモード
     */
    public void setMode(SKKMode mode) {
        mMode = mode;
        updateUI();
    }

    /**
     * 入力状態を変更します。
     *
     * @param state  新しい状態
     * @param finish 現在の状態を強制確定させる場合は true
     */
    public void changeState(SKKState state, boolean finish) {
        if (finish) {
            mState.finish(this);
        }
        changeState(state);
    }

    /**
     * 入力状態を変更します。
     *
     * @param state 新しい状態
     */
    public void changeState(SKKState state) {
        mState.onExitState(this);
        mState = state;
        mState.onEnterState(this);
        updateUI();
    }

    /**
     * 半角カタカナを使用するかどうかを取得します。
     *
     * @return 使用する場合は true
     */
    public boolean useJisx0201Kana() {
        return mUseJisx0201Kana;
    }

    /**
     * アイコンやツールチップなどのUI表示を更新します。
     */
    private void updateUI() {
        mService.requestUIUpdate();
    }

    /**
     * 現在の状態またはモードに応じたアイコンの種類を取得します。
     *
     * @return アイコンの種類
     */
    public SKKIcon getCurrentIcon() {
        SKKIcon icon = mState.getIcon();
        if (icon == SKKIcon.NONE) {
            icon = mMode.getIcon();
        }
        return icon;
    }

    /**
     * 現在の状態またはモードに応じたツールチップテキストを取得します。
     *
     * @return ツールチップテキスト
     */
    public String getCurrentTooltip() {
        String text = mState.getText();
        if (text == null) {
            text = mMode.getText();
        }
        return text;
    }

    /**
     * 単語登録の際に必要な一時情報を保持する内部構造体クラスです。
     */
    public static class RegistrationInfo {
        /** 登録対象の見出し語（辞書キー）。 */
        public String key;
        /** 登録対象の送り仮名。 */
        public String okurigana;
        /** 登録対象の送り子音（辞書検索用）。 */
        public String okuriConsonant;
        /** 登録中の画面に表示されるプロンプト用の見出し語（例: "かな*s"）。 */
        public String displayKey;
        /** ユーザーが入力した登録単語本体（バリュー）。 */
        public StringBuilder entry;
        /** Abbrev 変換からの登録かどうか。 */
        public boolean abbrev;
        /** 登録開始時点での入力モード（セッション終了時に復元するため）。 */
        public SKKMode mode;

        public RegistrationInfo(String key, String okurigana, String okuriConsonant, String displayKey, boolean abbrev, SKKMode mode) {
            this.key = key;
            this.okurigana = okurigana;
            this.okuriConsonant = okuriConsonant;
            this.displayKey = displayKey;
            this.abbrev = abbrev;
            this.mode = mode;
            entry = new StringBuilder();
        }
    }

    /**
     * 再変換（変換のやり直し）を可能にするための直近の変換情報を保持する内部クラスです。
     */
    private static class ConversionInfo {
        /** 確定された候補文字列。 */
        String candidate;
        /** 確定時に使用していた候補リスト全体。 */
        List<Candidate> list;
        /** 選択された項目のインデックス。 */
        int index;
        /** 使用された見出し語（Headword）。 */
        String headword;
        /** 使用された送り仮名。 */
        String okurigana;
        /** 使用された送り子音。 */
        String okuriConsonant;
        /** Abbrev モードであったかどうか。 */
        boolean abbrev;
        /** 確定時の入力モード。 */
        SKKMode mode;

        ConversionInfo(String candidate, List<Candidate> list, int index, String headword, String okurigana, String okuriConsonant, boolean abbrev, SKKMode mode) {
            this.candidate = candidate;
            this.list = list;
            this.index = index;
            this.headword = headword;
            this.okurigana = okurigana;
            this.okuriConsonant = okuriConsonant;
            this.abbrev = abbrev;
            this.mode = mode;
        }
    }
}
