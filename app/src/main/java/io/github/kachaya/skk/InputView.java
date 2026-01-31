package io.github.kachaya.skk;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;
import android.view.ContextThemeWrapper;
import android.view.HapticFeedbackConstants;
import android.view.LayoutInflater;
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
import java.util.List;

/**
 * SKK の入力ビュー（キーボード UI）を管理するクラスです。
 * <p>
 * 変換候補を表示するエリア（HorizontalScrollView）と、記号ボタン等を配置するキーボードエリアを統合して管理します。
 * ユーザーの設定に応じて、候補表示時のレイアウト動的変更（1行表示モードなど）も担当します。
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
    /** 通常時（Primary）に使用する記号の配列。 */
    private String[] mSymbolsPrimary;
    /** 切り替え時（Secondary）に使用する記号の配列。 */
    private String[] mSymbolsSecondary;
    /** 現在セカンダリの記号セットが表示されているかどうかの状態。 */
    private boolean mIsAlternativeSymbols = false;
    /** Primary と Secondary のうち、より多い方の記号数。ボタン生成数の基準になります。 */
    private int mMaxButtonCount;
    /** 現在画面に表示されている候補ボタンの配列。 */
    private Button[] mCandidateButton;

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
     * 最新の設定値を SharedPreferences から読み込みます。
     * 記号ボタンの定義や表示フラグが含まれます。
     */
    public void readPrefs() {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(getContext());

        mInputSingleLine = prefs.getBoolean("input_single_line", false);
        mHapticEnabled = prefs.getBoolean("haptic_feedback", true);

        String def1 = getContext().getString(R.string.default_symbols_primary);
        String def2 = getContext().getString(R.string.default_symbols_secondary);
        mSymbolsPrimary = prefs.getString("symbols_primary", def1).split("");
        mSymbolsSecondary = prefs.getString("symbols_secondary", def2).split("");
        mMaxButtonCount = Math.max(mSymbolsPrimary.length, mSymbolsSecondary.length);
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

        mKeyboardLayout.removeAllViewsInLayout();
        mKeyboardLayout.removeAllViews();
        mSymbolButtons.clear();

        LayoutParams lp = new LinearLayout.LayoutParams(0, LayoutParams.MATCH_PARENT, 1.0f);
        int style = R.style.CharacterButton;

        // 設定された最大数分の記号ボタンを生成して追加
        for (int i = 0; i < mMaxButtonCount; i++) {
            Button b = new Button(new ContextThemeWrapper(getContext(), style), null, 0);
            b.setOnClickListener(v -> onClickCharacterButton((Button) v));
            mKeyboardLayout.addView(b, lp);
            mSymbolButtons.add(b);
        }

        // トグルボタンを末尾に追加
        if (mToggleKeyboardButton.getParent() != null) {
            ((ViewGroup) mToggleKeyboardButton.getParent()).removeView(mToggleKeyboardButton);
        }
        mKeyboardLayout.addView(mToggleKeyboardButton, lp);

        updateSymbolButtons();
    }

    /**
     * 現在のトグル状態に基づいて、記号ボタンのラベル（テキスト）を更新します。
     * 文字が設定されていないインデックスのボタンは無効化されます。
     */
    private void updateSymbolButtons() {
        String[] currentLabels = mIsAlternativeSymbols ? mSymbolsSecondary : mSymbolsPrimary;
        for (int i = 0; i < mSymbolButtons.size(); i++) {
            Button b = mSymbolButtons.get(i);
            if (i < currentLabels.length) {
                b.setText(currentLabels[i]);
                b.setEnabled(true);
                b.setClickable(true);
            } else {
                b.setText("");
                b.setEnabled(false);
                b.setClickable(false);
            }
        }
    }

    /**
     * 記号セット切り替えボタンのクリック処理。
     * 状態を反転させ、ボタンの表示を更新します。
     *
     * @param v クリックされた ImageButton
     */
    private void onClickToggleKeyboardButton(View v) {
        if (mHapticEnabled) {
            v.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP);
        }
        mIsAlternativeSymbols = !mIsAlternativeSymbols;
        updateSymbolButtons();
    }

    /**
     * 記号ボタン（文字ボタン）のクリック処理。
     * ボタンのテキストを 1 文字として取り出し、InputService へ送信します。
     *
     * @param b クリックされた Button
     */
    private void onClickCharacterButton(Button b) {
        if (mHapticEnabled) {
            b.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP);
        }
        CharSequence cs = b.getText();
        if (cs.length() > 0) {
            mInputService.processKey(cs.charAt(0));
        }
    }

    /**
     * 候補ボタンがタップされた際の処理。
     * ボタンの Tag に格納されたインデックスを取得し、InputService へ通知します。
     *
     * @param v クリックされた候補 Button
     */
    private void onClickCandidateButton(View v) {
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
     * 1行表示設定の状態に応じて、候補エリアを完全に消す（GONE）か、透明にする（INVISIBLE）かを選択します。
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
