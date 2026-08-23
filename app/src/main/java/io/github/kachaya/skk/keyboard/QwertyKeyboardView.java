package io.github.kachaya.skk.keyboard;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.view.MotionEvent;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;

import io.github.kachaya.skk.R;

/**
 * QWERTY 配列のソフトウェアキーボードを表示・管理するビューです。
 * <p>
 * 通常・シフト・記号の 3 つのレイアウトを保持し、修飾キーの状態に応じて動的に表示を切り替えます。
 * 削除キー等のリピート入力（長押し）機能も備えています。
 * </p>
 */
public class QwertyKeyboardView extends KeyboardView {

    /** 通常（小文字）レイアウトの定義。 */
    private KeyConfig[][] mNormalLayout;
    /** シフト（大文字）レイアウトの定義。 */
    private KeyConfig[][] mShiftLayout;
    /** 記号レイアウトの定義。 */
    private KeyConfig[][] mSymbolLayout;
    /** 現在画面に構築されているレイアウト。 */
    private KeyConfig[][] mCurrentLayout;

    /** 1行あたりの高さ。 */
    private int mRowHeight;
    /** 現在の Shift 状態。 */
    private boolean mIsShifted;
    /** 現在の Shift ロック状態。 */
    private boolean mIsShiftLocked;
    /** 現在の Control 状態。 */
    private boolean mIsControl;
    /** 現在の 記号レイアウト 状態。 */
    private boolean mIsSymbol;

    /** リピート入力用のハンドラ。 */
    private final Handler mRepeatHandler = new Handler(Looper.getMainLooper());
    /** リピート実行タスク。 */
    private Runnable mRepeatRunnable;

    public QwertyKeyboardView(Context context) {
        super(context);
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
        mNormalLayout = loadIndependentLayout("custom_qwerty_layout", "_normal", DefaultLayouts.get(getContext(), "custom_qwerty_layout_normal"));
        mShiftLayout = loadIndependentLayout("custom_qwerty_layout", "_shift", DefaultLayouts.get(getContext(), "custom_qwerty_layout_shift"));
        mSymbolLayout = loadIndependentLayout("custom_qwerty_layout", "_symbol", DefaultLayouts.get(getContext(), "custom_qwerty_layout_symbol"));

        updateLayout();
    }

    /**
     * 指定されたサフィックスを持つレイアウトファイルを読み込みます。
     */
    private KeyConfig[][] loadIndependentLayout(String baseKey, String suffix, String defaultLayout) {
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
    private void updateLayout() {
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
            buildKeyboard();
        } else {
            updateKeyStates();
        }
    }

    /**
     * 現在のレイアウト定義に基づき、Button ビューを生成・配置してキーボード UI を構築します。
     */
    private void buildKeyboard() {
        removeAllViews();

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
    private void updateKeyStates() {
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
    private void setupRepeatKey(Button b, KeyConfig config) {
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
