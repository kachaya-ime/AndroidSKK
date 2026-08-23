package io.github.kachaya.skk.keyboard;

import android.content.Context;
import android.view.ContextThemeWrapper;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ImageButton;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import io.github.kachaya.skk.R;

/**
 * 記号や特定の機能キーを 1 行（バー状）で表示・管理するビューです。
 * <p>
 * 物理キーボード使用時など、限られたスペースで補助的な記号入力手段を提供します。
 * 2 つの記号セットをトグルボタンで切り替えて表示できます。
 * </p>
 */
public class SymbolKeyboardView extends KeyboardView {

    /** 1つ目の記号セット。 */
    private List<KeyConfig> mSymbolsPrimary;
    /** 2つ目（切り替え後）の記号セット。 */
    private List<KeyConfig> mSymbolsSecondary;
    /** 表示される最大ボタン数。 */
    private int mMaxButtonCount;
    /** 現在、2つ目の記号セットを表示中かどうか。 */
    private boolean mIsAlternativeSymbols = false;

    /** 表示中の記号ボタンのリスト。 */
    private final List<Button> mSymbolButtons = new ArrayList<>();
    /** レイアウト（セット）を切り替えるためのトグルボタン。 */
    private final ImageButton mToggleKeyboardButton;

    public SymbolKeyboardView(Context context) {
        super(context);
        setOrientation(HORIZONTAL);

        int style = R.style.FunctionButton;
        mToggleKeyboardButton = new ImageButton(new ContextThemeWrapper(context, style), null, 0);
        mToggleKeyboardButton.setOnClickListener(this::onClickToggleKeyboardButton);
        mToggleKeyboardButton.setImageResource(R.drawable.ic_keyboard_swap);

        readPrefs();
        buildKeyboard();
    }

    /**
     * カスタム記号レイアウトの設定を読み込みます。
     */
    @Override
    public void readPrefs() {
        super.readPrefs();

        KeyConfig[][] symLayout = KeyConfig.layoutFromAnyString(LayoutManager.loadLayout(getContext(), "custom_symbols_layout", DefaultLayouts.get(getContext(), "custom_symbols_layout")));
        mSymbolsPrimary = symLayout.length > 0 ? Arrays.asList(symLayout[0]) : new ArrayList<>();
        mSymbolsSecondary = symLayout.length > 1 ? Arrays.asList(symLayout[1]) : new ArrayList<>();
        mMaxButtonCount = Math.max(mSymbolsPrimary.size(), mSymbolsSecondary.size());

        if (!mSymbolButtons.isEmpty()) {
            buildKeyboard();
        }
    }

    /**
     * 記号バーは現状、修飾キーの状態を反映しません。
     *
     * @param state 新しい状態
     */
    @Override
    public void updateState(KeyboardState state) {
        // 記号バーは修飾キーの状態に依存しない
    }

    /**
     * 現在の定義に基づいてボタンを生成し、記号バーを構築します。
     */
    private void buildKeyboard() {
        removeAllViews();
        mSymbolButtons.clear();

        LayoutParams lp = new LayoutParams(0, LayoutParams.MATCH_PARENT, 1.0f);

        for (int i = 0; i < mMaxButtonCount; i++) {
            Button b = KeyViewFactory.createKeyButton(getContext(), new KeyConfig(""));
            b.setOnTouchListener((v, event) -> {
                if (event.getAction() == MotionEvent.ACTION_DOWN) {
                    performHapticFeedback(v);
                }
                return false;
            });
            b.setOnClickListener(v -> {
                KeyConfig config = (KeyConfig) v.getTag();
                if (config != null && mListener != null) {
                    mListener.onKey(config);
                }
            });
            addView(b, lp);
            mSymbolButtons.add(b);
        }

        if (mToggleKeyboardButton.getParent() != null) {
            ((ViewGroup) mToggleKeyboardButton.getParent()).removeView(mToggleKeyboardButton);
        }
        addView(mToggleKeyboardButton, lp);

        updateSymbolButtons();
    }

    /**
     * 選択されている記号セットのラベルをボタンに反映させます。
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
     * トグルボタン押下時の処理。記号セットを切り替えます。
     */
    private void onClickToggleKeyboardButton(View v) {
        performHapticFeedback(v);
        mIsAlternativeSymbols = !mIsAlternativeSymbols;
        updateSymbolButtons();
    }
}
