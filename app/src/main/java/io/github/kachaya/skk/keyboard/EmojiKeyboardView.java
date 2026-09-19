package io.github.kachaya.skk.keyboard;

import android.content.Context;
import android.util.TypedValue;
import android.view.ContextThemeWrapper;
import android.view.GestureDetector;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.widget.Button;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;

import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import io.github.kachaya.skk.R;

/**
 * # group: ごとにカテゴリ分けされた絵文字を表示し、選択して入力できるキーボードビューです。
 * RecyclerView と GridLayoutManager を用い、スムーズなスライド＆フェードアニメーションによるグループ切り替えをサポートします。
 */
public class EmojiKeyboardView extends KeyboardView {

    public interface OnCloseListener {
        void onClose();
    }

    private OnCloseListener mCloseListener;
    private Map<String, List<EmojiParser.EmojiItem>> mEmojiGroups;
    private List<String> mGroupKeys;
    private String mCurrentGroup;
    private final LinearLayout mCategoryBarLayout;
    private final HorizontalScrollView mCategoryScroll;
    private final RecyclerView mEmojiRecyclerView;
    private final EmojiRecyclerViewAdapter mEmojiAdapter;
    private final GestureDetector mGestureDetector;
    private final List<Button> mCategoryButtons = new ArrayList<>();

    public EmojiKeyboardView(Context context) {
        super(context);
        setOrientation(VERTICAL);

        mEmojiGroups = EmojiParser.parse(context);
        mGroupKeys = new ArrayList<>(mEmojiGroups.keySet());
        if (!mGroupKeys.isEmpty()) {
            mCurrentGroup = mGroupKeys.get(0);
        }

        // Top Header Bar: [ HorizontalScrollView (Categories) ] [ Close Button (Fixed Right) ]
        LinearLayout topBar = new LinearLayout(context);
        topBar.setOrientation(HORIZONTAL);
        topBar.setGravity(Gravity.CENTER_VERTICAL);
        topBar.setPadding(4, 4, 4, 4);

        mCategoryScroll = new HorizontalScrollView(context);
        mCategoryScroll.setHorizontalScrollBarEnabled(false);
        mCategoryBarLayout = new LinearLayout(context);
        mCategoryBarLayout.setOrientation(HORIZONTAL);
        mCategoryBarLayout.setGravity(Gravity.CENTER_VERTICAL);
        mCategoryScroll.addView(mCategoryBarLayout, new LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT));

        LayoutParams scrollLp = new LayoutParams(0, LayoutParams.WRAP_CONTENT, 1.0f);
        topBar.addView(mCategoryScroll, scrollLp);

        // Fixed Close Button on the right matching functional keys
        Button closeBtn = new Button(new ContextThemeWrapper(context, R.style.FunctionalKeyButton), null, 0);
        closeBtn.setText("閉じる");
        closeBtn.setAllCaps(false);
        closeBtn.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
        closeBtn.setBackgroundResource(R.drawable.bg_function_button_selector);
        int closePadH = (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 12, getResources().getDisplayMetrics());
        closeBtn.setPadding(closePadH, 0, closePadH, 0);

        int closeMarginH = (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 6, getResources().getDisplayMetrics());
        LayoutParams closeLp = new LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT);
        closeLp.setMargins(closeMarginH, 4, closeMarginH, 4);
        closeBtn.setLayoutParams(closeLp);
        closeBtn.setOnClickListener(v -> {
            performHapticFeedback(v);
            if (mCloseListener != null) {
                mCloseListener.onClose();
            }
        });
        topBar.addView(closeBtn);

        addView(topBar, new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT));

        // Emoji Grid RecyclerView (Optimized for performance)
        mEmojiRecyclerView = new RecyclerView(context);
        mEmojiRecyclerView.setLayoutManager(new GridLayoutManager(context, 10)); // 8 columns
        mEmojiRecyclerView.setItemAnimator(null); // Disable animations for instant updates
        mEmojiRecyclerView.setItemViewCacheSize(100); // Increase view cache
        mEmojiRecyclerView.setHasFixedSize(true);

        mEmojiAdapter = new EmojiRecyclerViewAdapter(context);
        mEmojiAdapter.setOnEmojiClickListener(item -> {
            if (mListener != null) {
                mListener.onKey(new KeyConfig(item.emoji));
            }
        });
        mEmojiRecyclerView.setAdapter(mEmojiAdapter);

        LayoutParams rvLp = new LayoutParams(LayoutParams.MATCH_PARENT, 0, 1.0f);
        addView(mEmojiRecyclerView, rvLp);

        // Gesture detector for horizontal swipes
        mGestureDetector = new GestureDetector(context, new GestureDetector.SimpleOnGestureListener() {
            private static final int SWIPE_THRESHOLD = 100;
            private static final int SWIPE_VELOCITY_THRESHOLD = 200;

            @Override
            public boolean onFling(MotionEvent e1, MotionEvent e2, float velocityX, float velocityY) {
                if (e1 == null || e2 == null) return false;
                float diffX = e2.getX() - e1.getX();
                float diffY = e2.getY() - e1.getY();
                if (Math.abs(diffX) > Math.abs(diffY) * 2.0f && Math.abs(diffX) > SWIPE_THRESHOLD && Math.abs(velocityX) > SWIPE_VELOCITY_THRESHOLD) {
                    if (diffX > 0) {
                        switchToAdjacentGroup(-1);
                    } else {
                        switchToAdjacentGroup(1);
                    }
                    return true;
                }
                return false;
            }
        });

        setOnTouchListener((v, event) -> {
            if (event.getAction() == MotionEvent.ACTION_UP) {
                v.performClick();
            }
            return mGestureDetector.onTouchEvent(event);
        });

        mEmojiRecyclerView.setOnTouchListener((v, event) -> {
            if (event.getAction() == MotionEvent.ACTION_UP) {
                v.performClick();
            }
            return mGestureDetector.onTouchEvent(event) || v.onTouchEvent(event);
        });

        initCategories();
        updateEmojis(1);
    }

    @Override
    protected void performHapticFeedback(View v) {
        // 絵文字ピッカーでは振動フィードバックを行わない
    }

    @Override
    public boolean performClick() {
        super.performClick();
        return true;
    }

    public void setOnCloseListener(OnCloseListener listener) {
        mCloseListener = listener;
    }

    @Override
    public void readPrefs() {
        super.readPrefs();
        mEmojiGroups = EmojiParser.parse(getContext());
        mGroupKeys = new ArrayList<>(mEmojiGroups.keySet());
        if (mCurrentGroup == null && !mGroupKeys.isEmpty()) {
            mCurrentGroup = mGroupKeys.get(0);
        }
        initCategories();
        updateEmojis(1);
    }

    @Override
    public void updateState(KeyboardState state) {
        // 絵文字キーボードは修飾キーの状態に依存しません
    }

    private void switchToGroup(String groupName) {
        if (groupName.equals(mCurrentGroup)) return;
        int oldIdx = mGroupKeys.indexOf(mCurrentGroup);
        int newIdx = mGroupKeys.indexOf(groupName);
        int direction = newIdx > oldIdx ? 1 : -1;
        mCurrentGroup = groupName;
        performHapticFeedback(this);
        updateCategorySelection();
        updateEmojis(direction);
    }

    private void switchToAdjacentGroup(int direction) {
        if (mGroupKeys == null || mGroupKeys.isEmpty()) return;
        int idx = mGroupKeys.indexOf(mCurrentGroup);
        if (idx == -1) idx = 0;
        int newIndex = (idx + direction + mGroupKeys.size()) % mGroupKeys.size();
        mCurrentGroup = mGroupKeys.get(newIndex);
        performHapticFeedback(this);
        updateCategorySelection();
        updateEmojis(direction);
    }

    private void initCategories() {
        mCategoryBarLayout.removeAllViews();
        mCategoryButtons.clear();
        if (mEmojiGroups == null || mEmojiGroups.isEmpty()) return;

        Context context = getContext();
        int padH = (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 12, getResources().getDisplayMetrics());
        int marginH = (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 6, getResources().getDisplayMetrics());

        for (int i = 0; i < mGroupKeys.size(); i++) {
            String groupName = mGroupKeys.get(i);
            Button btn = new Button(new ContextThemeWrapper(context, R.style.CharacterButton), null, 0);
            btn.setText(groupName);
            btn.setAllCaps(false);
            btn.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
            btn.setBackgroundResource(R.drawable.bg_emoji_category_button);
            btn.setPadding(padH, 0, padH, 0);

            LayoutParams lp = new LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT);
            lp.setMargins(marginH, 4, marginH, 4);
            btn.setLayoutParams(lp);

            btn.setOnClickListener(v -> {
                performHapticFeedback(v);
                switchToGroup(groupName);
            });

            mCategoryBarLayout.addView(btn);
            mCategoryButtons.add(btn);
        }
        updateCategorySelection();
    }

    private void updateCategorySelection() {
        for (int i = 0; i < mGroupKeys.size(); i++) {
            String groupName = mGroupKeys.get(i);
            Button btn = mCategoryButtons.get(i);
            boolean isSelected = groupName.equals(mCurrentGroup);
            btn.setSelected(isSelected);

            if (isSelected) {
                final View selectedBtn = btn;
                mCategoryScroll.post(() -> mCategoryScroll.smoothScrollTo(selectedBtn.getLeft() - 50, 0));
            }
        }
    }

    private void updateEmojis(int direction) {
        if (mEmojiGroups == null || mCurrentGroup == null) return;
        List<EmojiParser.EmojiItem> items = mEmojiGroups.get(mCurrentGroup);

        float startX = direction > 0 ? 60f : -60f;
        mEmojiRecyclerView.setTranslationX(startX);
        mEmojiRecyclerView.setAlpha(0.2f);
        mEmojiAdapter.setItems(items);
        mEmojiRecyclerView.scrollToPosition(0);

        mEmojiRecyclerView.animate()
                .translationX(0f)
                .alpha(1f)
                .setDuration(180)
                .start();
    }
}
