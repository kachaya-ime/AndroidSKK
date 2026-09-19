package io.github.kachaya.skk.keyboard;

import android.annotation.SuppressLint;
import android.content.Context;
import android.util.TypedValue;
import android.view.ContextThemeWrapper;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.List;

import io.github.kachaya.skk.R;

/**
 * RecyclerView を用いて高速に絵文字グリッドを表示・再利用するためのアダプターです。
 */
public class EmojiRecyclerViewAdapter extends RecyclerView.Adapter<EmojiRecyclerViewAdapter.EmojiViewHolder> {

    public interface OnEmojiClickListener {
        void onEmojiClick(EmojiParser.EmojiItem item);
    }

    private final Context mContext;
    private List<EmojiParser.EmojiItem> mItems = new ArrayList<>();
    private OnEmojiClickListener mListener;

    public EmojiRecyclerViewAdapter(Context context) {
        mContext = context;
    }

    public void setItems(List<EmojiParser.EmojiItem> items) {
        mItems = items != null ? items : new ArrayList<>();
        notifyDataSetChanged();
    }

    public void setOnEmojiClickListener(OnEmojiClickListener listener) {
        mListener = listener;
    }

    @NonNull
    @Override
    public EmojiViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        @SuppressLint("AppCompatCustomView")
        Button b = new Button(new ContextThemeWrapper(mContext, R.style.CharacterButton), null, 0) {
            @SuppressLint("WrongCall")
            @Override
            protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
                super.onMeasure(widthMeasureSpec, widthMeasureSpec);
            }
        };
        b.setTextSize(TypedValue.COMPLEX_UNIT_SP, 28);
        b.setAllCaps(false);
        b.setPadding(0, 0, 0, 0);
        b.setBackgroundResource(R.drawable.bg_character_button_selector);

        int marginPx = (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 2, mContext.getResources().getDisplayMetrics());
        RecyclerView.LayoutParams lp = new RecyclerView.LayoutParams(
                RecyclerView.LayoutParams.MATCH_PARENT,
                RecyclerView.LayoutParams.WRAP_CONTENT
        );
        lp.setMargins(marginPx, marginPx, marginPx, marginPx);
        b.setLayoutParams(lp);

        return new EmojiViewHolder(b);
    }

    @Override
    public void onBindViewHolder(@NonNull EmojiViewHolder holder, int position) {
        EmojiParser.EmojiItem item = mItems.get(position);
        holder.button.setText(item.emoji);
        holder.button.setTag(item);
        holder.button.setOnClickListener(v -> {
            if (mListener != null && v.getTag() instanceof EmojiParser.EmojiItem) {
                mListener.onEmojiClick((EmojiParser.EmojiItem) v.getTag());
            }
        });
    }

    @Override
    public int getItemCount() {
        return mItems.size();
    }

    public static class EmojiViewHolder extends RecyclerView.ViewHolder {
        public final Button button;

        public EmojiViewHolder(@NonNull View itemView) {
            super(itemView);
            this.button = (Button) itemView;
        }
    }
}
