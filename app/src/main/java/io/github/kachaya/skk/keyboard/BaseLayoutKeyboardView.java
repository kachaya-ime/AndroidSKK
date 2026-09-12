package io.github.kachaya.skk.keyboard;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;

import androidx.annotation.Nullable;

import io.github.kachaya.skk.R;

/**
 * カスタムレイアウト（QWERTY、Tablet 等）を持つソフトウェアキーボードビューの共通基底クラスです。
 * <p>
 * 通常・シフト・記号等のレイアウト読み込み、修飾キー状態の反映、Button ビューの構築、
 * およびキーリピート入力（長押し）の処理を共通で提供します。
 * </p>
 */
public abstract class BaseLayoutKeyboardView extends KeyboardView {

    /** 通常（小文字）レイアウトの定義。 */
    protected KeyConfig[][] mNormalLayout;
    /** シフト（大文字）レイアウトの定義。 */
    protected KeyConfig[][] mShiftLayout;
    /** 記号レイアウトの定義。 */
    protected KeyConfig[][] mSymbolLayout;
    /** 現在画面に構築されているレイアウト。 */
    protected KeyConfig[][] mCurrentLayout;

    /** レイアウトのベースキー名。 */
    protected String mBaseKey;

    /** 1行あたりの高さ。 */
    protected int mRowHeight;
    /** 現在の Shift 状態。 */
    protected boolean mIsShifted;
    /** 現在の Shift ロック状態。 */
    protected boolean mIsShiftLocked;
    /** 現在の Control 状態。 */
    protected boolean mIsControl;
    /** 現在の 記号レイアウト 状態。 */
    protected boolean mIsSymbol;

    /** リピート入力用のハンドラ。 */
    protected final Handler mRepeatHandler = new Handler(Looper.getMainLooper());
    /** リピート実行タスク。 */
    protected Runnable mRepeatRunnable;

    public BaseLayoutKeyboardView(Context context, String baseKey) {
        super(context);
        mBaseKey = baseKey;
        setOrientation(VERTICAL);
        readPrefs();
    }

    public BaseLayoutKeyboardView(Context context, @Nullable AttributeSet attrs, String baseKey) {
        super(context, attrs);
        mBaseKey = baseKey;
        setOrientation(VERTICAL);
        readPrefs();
    }

    /**
     * 行の高さを設定し、キーボードを再構築します。
     *
     * @param height 行の高さ（ピクセル）
     */
    @Override
    public void setRowHeight(int height) {
        if (mRowHeight != height) {
            mRowHeight = height;
            buildKeyboard();
        }
    }

    /**
     * 共有設定および内部ストレージから、最新のレイアウト定義を読み込みます。
     */
    @Override
    public void readPrefs() {
        super.readPrefs();

        if (mRowHeight <= 0) {
            mRowHeight = (int) getResources().getDimension(R.dimen.button_height);
        }
        mNormalLayout = loadIndependentLayout(mBaseKey, "_normal", DefaultLayouts.get(getContext(), mBaseKey + "_normal"));
        mShiftLayout = loadIndependentLayout(mBaseKey, "_shift", DefaultLayouts.get(getContext(), mBaseKey + "_shift"));
        mSymbolLayout = loadIndependentLayout(mBaseKey, "_symbol", DefaultLayouts.get(getContext(), mBaseKey + "_symbol"));

        updateLayout();
    }

    /**
     * 指定されたサフィックスを持つレイアウトファイルを読み込みます。
     */
    protected KeyConfig[][] loadIndependentLayout(String baseKey, String suffix, String defaultLayout) {
        String key = baseKey + suffix;
        String layoutStr = LayoutManager.loadLayout(getContext(), key, defaultLayout);
        return KeyConfig.layoutFromAnyString(layoutStr);
    }

    /**
     * 修飾キーの状態が変更された際に呼び出され、必要に応じて表示レイアウトを切り替えます。
     *
     * @param state 新しい状態
     */
    @Override
    public void updateState(KeyboardState state) {
        boolean layoutChanged = (mIsShifted != state.shifted || mIsShiftLocked != state.shiftLocked
                || mIsControl != state.control || mIsSymbol != state.symbol);
        mIsShifted = state.shifted;
        mIsShiftLocked = state.shiftLocked;
        mIsControl = state.control;
        mIsSymbol = state.symbol;

        if (layoutChanged) {
            updateLayout();
        }
    }

    /**
     * 現在の状態に適したレイアウトを選択し、UI を更新します。
     */
    protected void updateLayout() {
        KeyConfig[][] nextLayout;
        if (mIsSymbol && mSymbolLayout != null && mSymbolLayout.length > 0) {
            nextLayout = mSymbolLayout;
        } else if (mIsShifted) {
            nextLayout = mShiftLayout;
        } else {
            nextLayout = mNormalLayout;
        }

        if (mCurrentLayout != nextLayout) {
            mCurrentLayout = nextLayout;
            buildKeyboard();
        } else {
            updateKeyStates();
        }
    }

    /**
     * 現在のレイアウト定義に基づき、Button ビューを生成・配置してキーボード UI を構築します。
     */
    protected void buildKeyboard() {
        removeAllViews();

        if (mCurrentLayout == null) return;

        for (KeyConfig[] rowConfig : mCurrentLayout) {
            LinearLayout row = new LinearLayout(getContext());
            row.setOrientation(HORIZONTAL);
            addView(row, new LayoutParams(LayoutParams.MATCH_PARENT, mRowHeight));

            for (KeyConfig config : rowConfig) {
                if (config.code == KeyConfig.CODE_GAP || "GAP".equals(config.label)) {
                    View gapView = new View(getContext());
                    row.addView(gapView, new LayoutParams(0, LayoutParams.MATCH_PARENT, config.weight));
                    continue;
                }

                Button b = KeyViewFactory.createKeyButton(getContext(), config);
                if (config.isRepeatable()) {
                    setupRepeatKey(b, config);
                } else {
                    b.setOnTouchListener((v, event) -> {
                        if (event.getAction() == MotionEvent.ACTION_DOWN) {
                            performHapticFeedback(v);
                        }
                        return false;
                    });
                    b.setOnClickListener(v -> {
                        if (mListener != null) {
                            mListener.onKey((KeyConfig) v.getTag());
                        }
                    });
                }
                row.addView(b, new LayoutParams(0, LayoutParams.MATCH_PARENT, config.weight));
            }
        }
        updateKeyStates();
    }

    /**
     * Shift/Ctrl/Sym ボタンの選択（ハイライト）状態を最新の内部フラグと同期させます。
     */
    protected void updateKeyStates() {
        for (int i = 0; i < getChildCount(); i++) {
            View v = getChildAt(i);
            if (v instanceof LinearLayout) {
                LinearLayout row = (LinearLayout) v;
                for (int j = 0; j < row.getChildCount(); j++) {
                    View bv = row.getChildAt(j);
                    if (bv instanceof Button) {
                        Button b = (Button) bv;
                        KeyConfig config = (KeyConfig) b.getTag();
                        if (config == null) continue;
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

    /**
     * 指定されたボタンに対して、リピート入力（長押しによる連続発火）の挙動を設定します。
     */
    protected void setupRepeatKey(Button b, KeyConfig config) {
        b.setOnTouchListener((v, event) -> {
            switch (event.getAction()) {
                case MotionEvent.ACTION_DOWN:
                    performHapticFeedback(v);
                    if (mRepeatRunnable != null) {
                        mRepeatHandler.removeCallbacks(mRepeatRunnable);
                    }
                    mRepeatRunnable = new Runnable() {
                        @Override
                        public void run() {
                            if (mListener != null) {
                                mListener.onKey(config);
                            }
                            mRepeatHandler.postDelayed(this, 50);
                        }
                    };
                    if (mListener != null) {
                        mListener.onKey(config);
                    }
                    mRepeatHandler.postDelayed(mRepeatRunnable, 500);
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
}
