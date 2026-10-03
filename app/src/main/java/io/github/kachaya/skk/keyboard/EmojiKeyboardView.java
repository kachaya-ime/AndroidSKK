package io.github.kachaya.skk.keyboard;

import android.annotation.SuppressLint;
import android.content.Context;
import android.util.TypedValue;
import android.view.ContextThemeWrapper;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.viewpager2.widget.ViewPager2;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import io.github.kachaya.skk.R;

/**
 * # group: ごとにカテゴリ分けされた絵文字を表示し、選択して入力できるキーボードビューです。
 * ViewPager2 を用いて指でのスムーズな左右スワイプ切り替えをサポートします。
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
    private final ViewPager2 mViewPager;
    private final EmojiPagerAdapter mPagerAdapter;
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

        // ViewPager2 for smooth horizontal paging
        mViewPager = new ViewPager2(context);
        mPagerAdapter = new EmojiPagerAdapter();
        mViewPager.setAdapter(mPagerAdapter);

        mViewPager.registerOnPageChangeCallback(new ViewPager2.OnPageChangeCallback() {
            @Override
            public void onPageSelected(int position) {
                super.onPageSelected(position);
                if (position >= 0 && position < mGroupKeys.size()) {
                    mCurrentGroup = mGroupKeys.get(position);
                    updateCategorySelection();
                }
            }
        });

        LayoutParams vpLp = new LayoutParams(LayoutParams.MATCH_PARENT, 0, 1.0f);
        addView(mViewPager, vpLp);

        initCategories();
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
    @SuppressLint("NotifyDataSetChanged")
    public void readPrefs() {
        super.readPrefs();
        mEmojiGroups = EmojiParser.parse(getContext());
        mGroupKeys = new ArrayList<>(mEmojiGroups.keySet());
        if (mCurrentGroup == null && !mGroupKeys.isEmpty()) {
            mCurrentGroup = mGroupKeys.get(0);
        }
        initCategories();
        if (mPagerAdapter != null) {
            mPagerAdapter.notifyDataSetChanged();
        }
        if (mViewPager != null && mCurrentGroup != null) {
            int index = mGroupKeys.indexOf(mCurrentGroup);
            if (index >= 0) {
                mViewPager.setCurrentItem(index, false);
            }
        }
    }

    @Override
    public void updateState(KeyboardState state) {
        // 絵文字キーボードは修飾キーの状態に依存しません
    }

    private void switchToGroup(String groupName) {
        int index = mGroupKeys.indexOf(groupName);
        if (index >= 0 && index < mGroupKeys.size()) {
            mViewPager.setCurrentItem(index, true);
        }
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

    private class EmojiPagerAdapter extends RecyclerView.Adapter<PageViewHolder> {

        @NonNull
        @Override
        public PageViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            RecyclerView rv = new RecyclerView(parent.getContext());
            rv.setLayoutParams(new ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT
            ));
            rv.setLayoutManager(new GridLayoutManager(parent.getContext(), 10));
            rv.setItemAnimator(null);
            rv.setItemViewCacheSize(100);
            rv.setHasFixedSize(true);

            EmojiRecyclerViewAdapter adapter = new EmojiRecyclerViewAdapter(parent.getContext());
            adapter.setOnEmojiClickListener(item -> {
                if (mListener != null) {
                    mListener.onKey(new KeyConfig(item.emoji));
                }
            });
            rv.setAdapter(adapter);

            return new PageViewHolder(rv, adapter);
        }

        @Override
        public void onBindViewHolder(@NonNull PageViewHolder holder, int position) {
            if (mGroupKeys != null && position >= 0 && position < mGroupKeys.size()) {
                String group = mGroupKeys.get(position);
                List<EmojiParser.EmojiItem> items = mEmojiGroups != null ? mEmojiGroups.get(group) : null;
                holder.adapter.setItems(items);
            }
        }

        @Override
        public int getItemCount() {
            return mGroupKeys != null ? mGroupKeys.size() : 0;
        }
    }

    private static class PageViewHolder extends RecyclerView.ViewHolder {
        final RecyclerView recyclerView;
        final EmojiRecyclerViewAdapter adapter;

        PageViewHolder(@NonNull View itemView, EmojiRecyclerViewAdapter adapter) {
            super(itemView);
            this.recyclerView = (RecyclerView) itemView;
            this.adapter = adapter;
        }
    }
}
