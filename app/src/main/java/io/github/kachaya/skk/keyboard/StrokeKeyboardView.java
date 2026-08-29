package io.github.kachaya.skk.keyboard;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.res.Resources;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.drawable.Drawable;
import android.util.AttributeSet;
import android.view.MotionEvent;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.res.ResourcesCompat;

import io.github.kachaya.skk.R;

/**
 * 手書きジェスチャ（ストローク）による入力を管理するキーボードビューです。
 * <p>
 * 画面上の描画軌跡を解析し、アルファベット、数字、記号等の文字コードとして認識します。
 * 上方向へのスワイプによるヘルプ表示機能や、Shift/Ctrl 等の修飾キー状態のインジケータ表示を含みます。
 * </p>
 */
public class StrokeKeyboardView extends KeyboardView {

    /** アルファベット認識エンジン。 */
    private Stroke alphabetStroke;
    /** 数字認識エンジン。 */
    private Stroke numericStroke;
    /** 記号認識エンジン。 */
    private Stroke punctuationStroke;

    /** 記号（ドットタップ）入力待ちフラグ。 */
    private boolean mPunctuationFlag;
    /** 現在の一時的 Shift 状態。 */
    private boolean mShiftSingleFlag = false;
    /** 現在の Shift ロック状態。 */
    private boolean mShiftLockFlag = false;
    /** 現在の Control 状態。 */
    private boolean mCtrlSingleFlag = false;
    /** 現在のモードアイコン。 */
    private Drawable mModeIconDrawable = null;

    /** 現在描画中の軌跡 Path オブジェクト。 */
    private Path mStrokePath;
    /** 軌跡描画用の Paint オブジェクト。 */
    private Paint mPaint;

    /** 最後のタッチ座標 X。 */
    private float mLastX;
    /** 最後のタッチ座標 Y。 */
    private float mLastY;
    /** タッチの遊び（移動判定しきい値）。 */
    private float mTouchTolerance;

    /** ヘルプ表示がトリガーされたかどうか。 */
    private boolean mHelpTriggered = false;

    /** Shift（単発）表示用アイコン。 */
    private Drawable mShiftSingleDrawable;
    /** Shift（ロック）表示用アイコン。 */
    private Drawable mShiftLockDrawable;
    /** Control 表示用アイコン。 */
    private Drawable mCtrlSingleDrawable;

    /** ヘルプ通知用リスナー。 */
    private OnHelpListener mHelpListener;

    /**
     * ストローク入力ヘルプの表示リクエストを受け取るリスナーインターフェースです。
     */
    public interface OnHelpListener {
        /** ヘルプ表示が要求された際に呼び出されます。 */
        void onHelpTriggered();
    }

    /**
     * ヘルプリスナーを設定します。
     *
     * @param listener リスナー
     */
    public void setOnHelpListener(OnHelpListener listener) {
        mHelpListener = listener;
    }

    public StrokeKeyboardView(Context context) {
        super(context);
        init(context);
    }

    public StrokeKeyboardView(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        init(context);
    }

    /**
     * ビューの初期化（ペイント設定、アイコン読み込み、認識エンジンの準備）を行います。
     */
    private void init(Context context) {
        setWillNotDraw(false);
        readPrefs();

        alphabetStroke = new Stroke(Stroke.ALPHABET_DIC);
        numericStroke = new Stroke(Stroke.NUMERIC_DIC);
        punctuationStroke = new Stroke(Stroke.PUNCTUATION_DIC);

        Resources res = context.getResources();

        mShiftSingleDrawable = ResourcesCompat.getDrawable(res, R.drawable.ic_shift_single, null);
        mShiftLockDrawable = ResourcesCompat.getDrawable(res, R.drawable.ic_shift_lock, null);
        mCtrlSingleDrawable = ResourcesCompat.getDrawable(res, R.drawable.ic_ctrl_single, null);

        mStrokePath = new Path();

        mPaint = new Paint();
        mPaint.setAntiAlias(true);
        mPaint.setDither(true);
        mPaint.setColor(Color.WHITE); // 線の色
        mPaint.setStyle(Paint.Style.STROKE);
        mPaint.setStrokeJoin(Paint.Join.ROUND);
        mPaint.setStrokeCap(Paint.Cap.ROUND);
        mPaint.setStrokeWidth(3f); // 線の太さ
    }

    /**
     * 修飾キーの状態を反映し、インジケータ表示のために再描画を要求します。
     *
     * @param state 新しい状態
     */
    @Override
    public void updateState(KeyboardState state) {
        mShiftSingleFlag = state.shifted;
        mShiftLockFlag = state.shiftLocked;
        mCtrlSingleFlag = state.control;
        if (state.modeIconResId != 0) {
            mModeIconDrawable = ResourcesCompat.getDrawable(getResources(), state.modeIconResId, null);
        } else {
            mModeIconDrawable = null;
        }
        invalidate();
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        if (w <= 0 || h <= 0) {
            return;
        }

        // タッチの遊び（1.0mm相当）をDPIに基づいて計算。これは描画開始判定に使用。
        float xdpi = getContext().getResources().getDisplayMetrics().xdpi;
        mTouchTolerance = (xdpi / 25.4f) * 1.0f;

        alphabetStroke = new Stroke(Stroke.ALPHABET_DIC);
        numericStroke = new Stroke(Stroke.NUMERIC_DIC);
        punctuationStroke = new Stroke(Stroke.PUNCTUATION_DIC);
    }

    /**
     * タッチイベントを処理し、軌跡の描画と文字認識を実行します。
     * <p>
     * DOWN で開始、MOVE で軌跡更新（上方向スワイプでヘルプ）、UP で認識確定を行います。
     * 短いタップ（遊びの範囲内）は記号入力（ドットタップ）モードの切り替えとして扱います。
     * </p>
     */
    @SuppressLint("ClickableViewAccessibility")
    @Override
    public boolean onTouchEvent(MotionEvent event) {
        float x = event.getX();
        float y = event.getY();

        switch (event.getAction()) {
            case MotionEvent.ACTION_DOWN:
                mHelpTriggered = false;
                mStrokePath.reset();
                mStrokePath.moveTo(x, y);
                mLastX = x;
                mLastY = y;
                invalidate(); // 再描画を要求
                return true; // イベントを消費

            case MotionEvent.ACTION_MOVE:
                float dx = x - mLastX;
                float dy = y - mLastY;

                // エリア外（上方向）へのスワイプによるヘルプ表示判定
                if (!mHelpTriggered && y < -mTouchTolerance * 2) {
                    mHelpTriggered = true;
                    if (mHelpListener != null) {
                        mHelpListener.onHelpTriggered();
                    }
                    mStrokePath.reset();
                    invalidate();
                    return true;
                }

                // エリア外（下方向）へのスワイプによるヘルプ非表示判定
                if (mHelpTriggered) {
                    return true;
                }

                double d = Math.sqrt(dx * dx + dy * dy);
                if (d >= mTouchTolerance) {
                    mStrokePath.quadTo(mLastX, mLastY, (x + mLastX) / 2, (y + mLastY) / 2);
                    mLastX = x;
                    mLastY = y;
                    invalidate(); // 再描画を要求
                }
                return true; // イベントを消費

            case MotionEvent.ACTION_UP:
                if (mHelpTriggered) {
                    mHelpTriggered = false;
                    return true;
                }
                mStrokePath.lineTo(mLastX, mLastY);

                RectF bounds = new RectF();
                mStrokePath.computeBounds(bounds, true);
                boolean isTap = bounds.width() < mTouchTolerance && bounds.height() < mTouchTolerance;
                if (isTap) {
                    if (mPunctuationFlag) {
                        mPunctuationFlag = false;
                        processCode('.');
                    } else {
                        mPunctuationFlag = true;
                    }
                } else {
                    Stroke engine;
                    if (mPunctuationFlag) {
                        engine = punctuationStroke;
                    } else if (bounds.centerX() > (getWidth() / 2.0f)) {
                        engine = numericStroke;
                    } else {
                        engine = alphabetStroke;
                    }

                    int code = engine.recognize(mStrokePath);
                    if (code != 0) {
                        processCode(code);
                        if (mPunctuationFlag) {
                            mPunctuationFlag = false;
                        }
                    }
                }
                mStrokePath.reset();
                invalidate(); // 再描画を要求
                return true; // イベントを消費
        }
        return super.onTouchEvent(event);
    }

    /**
     * 認識されたコードまたは文字を KeyConfig に変換し、リスナーへ通知します。
     */
    private void processCode(int code) {
        if (mListener == null) {
            return;
        }
        if (code == 0) {
            return;
        }

        // ドットタップによる記号入力モードのキャンセル処理
        if (mPunctuationFlag && (code == KeyConfig.CODE_BACKSPACE || code == '.')) {
            mPunctuationFlag = false;
            if (code == KeyConfig.CODE_BACKSPACE) return;
        }

        KeyConfig config;
        // KeyConfig で定義されている機能コードかどうかを確認
        if (KeyConfig.codeToString(code) != null) {
            config = new KeyConfig(code);
        } else {
            // 文字として処理
            config = new KeyConfig(String.valueOf((char) code));
        }
        mListener.onKey(config);
    }

    /**
     * 背景、中心線、ガイドテキスト（a, 1）、修飾キーインジケータ、および軌跡を描画します。
     */
    @Override
    protected void onDraw(@NonNull Canvas canvas) {
        super.onDraw(canvas);
        int w = getWidth();
        int h = getHeight();
        float r = h / 100f;

        mPaint.setStrokeWidth(r / 2);
        mPaint.setStyle(Paint.Style.STROKE);
        canvas.drawRect(r / 2.0f, r / 2.0f, w - (r / 2.0f), h - (r / 2.0f), mPaint);
        canvas.drawLine(w / 2.0f, r / 2.0f, w / 2.0f, h - (r / 2.0f), mPaint);

        mPaint.setStyle(Paint.Style.FILL_AND_STROKE);
        mPaint.setTextSize(h * 0.1f);
        mPaint.setTextAlign(Paint.Align.CENTER);
        float ofsX = h * 0.07f;
        float ofsY = h * 0.08f + ((mPaint.descent() + mPaint.ascent()) / 2);
        canvas.drawText("a", ofsX, h - ofsY, mPaint);
        canvas.drawText("1", w - ofsX, h - ofsY, mPaint);

        mPaint.setStyle(Paint.Style.FILL_AND_STROKE);
        float cx = h * 0.1f;
        float cy = h * 0.1f;
        r = h * 0.05f;
        int left = (int) (cx - r);
        int top = (int) (cy - r);
        int right = (int) (cx + r);
        int bottom = (int) (cy + r);

        if (mCtrlSingleFlag) {
            mCtrlSingleDrawable.setBounds(left, top, right, bottom);
            mCtrlSingleDrawable.draw(canvas);
        } else if (mPunctuationFlag) {
            canvas.drawCircle(cx, cy, r, mPaint);
        } else {
            if (mShiftLockFlag) {
                mShiftLockDrawable.setBounds(left, top, right, bottom);
                mShiftLockDrawable.draw(canvas);
            } else if (mShiftSingleFlag) {
                mShiftSingleDrawable.setBounds(left, top, right, bottom);
                mShiftSingleDrawable.draw(canvas);
            }
        }
        mPaint.setStyle(Paint.Style.STROKE);
        canvas.drawPath(mStrokePath, mPaint);

        // モード表示
        if (mModeIconDrawable != null) {
            // 右上に表示。余白は h * 0.05f 程度、サイズは h * 0.1f 程度
            float size = h * 0.1f;
            float margin = h * 0.05f;
            int leftMode = (int) (w - margin - size);
            int topMode = (int) margin;
            int rightMode = (int) (w - margin);
            int bottomMode = (int) (margin + size);
            mModeIconDrawable.setBounds(leftMode, topMode, rightMode, bottomMode);
            mModeIconDrawable.draw(canvas);
        }
    }
}
