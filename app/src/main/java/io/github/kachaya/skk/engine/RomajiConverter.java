package io.github.kachaya.skk.engine;

import android.content.Context;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

import io.github.kachaya.skk.AssetLoader;

/**
 * ローマ字からかなへの変換、および文字種変換（全角/半角、ひらがな/カタカナ）を担当するクラスです。
 * <p>
 * SKK のローマ字入力ルールに基づき, {@link RomajiMap} (Trie構造) を用いた最長前方一致検索を行います。
 * 逐次的なキー入力をバッファリングし、変換が確定したタイミングで {@link SKKEngine} へテキストを通知します。
 * </p>
 */
public class RomajiConverter {

    private static RomajiMap romajiMap;
    private static Map<Character, String> halfWidthKatakanaMap;

    public static synchronized void load(Context context) {
        if (romajiMap != null && halfWidthKatakanaMap != null) return;

        romajiMap = new RomajiMap();
        JSONArray romajiArray = AssetLoader.loadJsonArray(context, "romaji_table.json");
        if (romajiArray != null) {
            for (int i = 0; i < romajiArray.length(); i++) {
                JSONObject obj = romajiArray.optJSONObject(i);
                if (obj != null) {
                    String key = obj.optString("key");
                    String value = obj.optString("value");
                    String next = obj.has("next") ? obj.optString("next") : null;
                    romajiMap.put(key, value, next);
                }
            }
        }

        halfWidthKatakanaMap = new HashMap<>();
        JSONObject kanaObj = AssetLoader.loadJsonObject(context, "kana_map.json");
        if (kanaObj != null) {
            Iterator<String> keys = kanaObj.keys();
            while (keys.hasNext()) {
                String full = keys.next();
                String half = kanaObj.optString(full);
                if (full.length() > 0) {
                    halfWidthKatakanaMap.put(full.charAt(0), half);
                }
            }
        }
    }

    /** 現在入力中の未確定ローマ字バッファ。変換ルールが成立するまで蓄積されます。 */
    private final StringBuilder mComposing = new StringBuilder();
    /** 変換確定時のテキストコミットや状態通知を行う engine への参照。 */
    private final SKKEngine mEngine;
    /**
     * 大文字入力（Shift押下）により、送り仮名の「入力待ち」状態にあるかどうか。
     * 例: 'K' 入力後の "k" 状態で、次の入力が送り仮名として扱われるべきであることを示します。
     */
    private boolean mShiftSent = false;

    /**
     * RomajiConverter を初期化します。
     *
     * @param engine 連携する SKKEngine のインスタンス
     */
    public RomajiConverter(SKKEngine engine) {
        mEngine = engine;
        load(engine.getContext());
    }

    /**
     * 与えられた文字がアルファベット (A-Z, a-z) かどうかを判定します。
     *
     * @param code Unicode コードポイント
     * @return アルファベットなら true
     */
    public static boolean isAlphabet(int code) {
        return ((code >= 'A' && code <= 'Z') || (code >= 'a' && code <= 'z'));
    }

    /**
     * 半角文字（ASCII）を対応する全角文字に変換します。
     * <p>
     * スペース(U+0020 → U+3000)、円記号、チルダ(U+007E → U+FF5E) などの特殊変換を含みます。
     * </p>
     *
     * @param code 半角文字のコードポイント
     * @return 対応する全角文字のコードポイント
     */
    public static int toWideLatin(int code) {
        if (code == 0x20) { // スペース -> 全角スペース (U+3000)
            return 0x3000;
        }
        if (code == '\u00A5') { // 円記号 (U+00A5) -> 全角円記号 (U+FFE5)
            return 0xFFE5;
        }
        if (0x21 <= code && code <= 0x7E) {
            // ASCII 記号・英数字を対応する全角（U+FF01〜U+FF5E）に一括変換
            // 0x7E (~) は 0xFF5E (～: 全角チルダ) になる
            return code - 0x20 + 0xFF00;
        }
        return code;
    }

    /**
     * 文字列内のすべての半角 ASCII 文字を全角に変換します。
     *
     * @param str 変換対象の文字列
     * @return 全角変換後の文字列。入力が null なら null。
     */
    public static CharSequence toWideLatin(CharSequence str) {
        if (str == null) {
            return null;
        }

        int len = str.length();
        StringBuilder buf = new StringBuilder(len);
        for (int i = 0; i < len; i++) {
            buf.append((char) toWideLatin(str.charAt(i)));
        }
        return buf;
    }

    /**
     * ひらがなを対応する全角カタカナに変換します。
     *
     * @param ch 変換対象のひらがな
     * @return 対応するカタカナ。範囲外なら元の文字。
     */
    public static char toWideKatakana(char ch) {
        if (ch >= 'ぁ' && ch <= 'ゖ') {
            return (char) (ch - 'ぁ' + 'ァ');
        }
        return ch;
    }

    /**
     * 文字列内のすべてのひらがなをカタカナに変換します。
     *
     * @param cs 変換対象の文字列
     * @return カタカナ変換後の文字列
     */
    public static String toWideKatakana(CharSequence cs) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < cs.length(); i++) {
            sb.append(toWideKatakana(cs.charAt(i)));
        }
        return sb.toString();
    }

    /**
     * 文字列内のすべての全角ひらがな・カタカナを半角カタカナに変換します。
     * 濁点・半濁点は 2 文字（例: 「ガ」→「ｶﾞ」）に分解されます。
     *
     * @param cs 変換対象の文字列
     * @return 半角カタカナ変換後の文字列
     */
    public static String toHalfKatakana(CharSequence cs) {
        StringBuilder sb = new StringBuilder();
        String katakana = toWideKatakana(cs);
        for (int i = 0; i < katakana.length(); i++) {
            char c = katakana.charAt(i);
            sb.append(halfWidthKatakanaMap.getOrDefault(c, String.valueOf(c)));
        }
        return sb.toString();
    }

    /**
     * 現在バッファに蓄積されている未確定のローマ字を取得します。
     *
     * @return 未確定ローマ字列
     */
    public CharSequence getComposing() {
        return mComposing;
    }

    /**
     * 未確定のローマ字バッファに文字が残っているか判定します。
     *
     * @return バッファが空でなければ true
     */
    public boolean hasComposing() {
        return mComposing.length() != 0;
    }

    /**
     * キー入力を処理し、ローマ字かな変換を試みます。
     * <p>
     * 入力された文字をバッファに追加し、{@link RomajiMap} を用いて検索を行います。
     * 検索結果に応じて以下の処理を行います：
     * <ul>
     *   <li><b>完全一致 (Leaf):</b> 変換値をエンジンにコミットし、バッファをクリア（または次の文字を継承）します。</li>
     *   <li><b>中間一致:</b> さらなる入力を待機します。大文字入力の場合は送り仮名フラグを更新します。</li>
     *   <li><b>部分一致:</b> 一致した部分のみを確定させ、残りをバッファに保持して再検索します。</li>
     *   <li><b>不一致:</b> 入力された文字列をそのまま（変換不能として）確定させます。</li>
     * </ul>
     * </p>
     *
     * @param code 入力されたキーの Unicode コードポイント
     */
    public void processKey(int code) {
        boolean isUpper = (code >= 'A' && code <= 'Z');
        if (isUpper) {
            code = Character.toLowerCase(code);
        }

        mComposing.append((char) code);

        while (mComposing.length() > 0) {
            String current = mComposing.toString();
            char initialChar = current.charAt(0);
            RomajiMap.Node node = romajiMap.prefixSearch(current);

            if (node == null) {
                // ローマ字表にないシーケンスの場合は、先頭の1文字をそのまま確定として放出
                mEngine.commitRomajiText(current.substring(0, 1), initialChar, isUpper);
                mComposing.delete(0, 1);
                isUpper = false;
                mShiftSent = false;
                continue;
            }

            if (node.getKey().length() == current.length()) {
                if (node.isLeaf()) {
                    // 完全一致する変換ルールが見つかった場合
                    if (mShiftSent) {
                        isUpper = false;
                        mShiftSent = false;
                    }
                    mEngine.commitRomajiText(node.getValue(), initialChar, isUpper);
                    mComposing.setLength(0);
                    // 「っ」の処理などのため、次に引き継ぐ文字列があればバッファに戻す
                    if (node.getNext() != null) {
                        mComposing.append(node.getNext());
                        isUpper = false;
                        continue;
                    } else {
                        mEngine.onFinishRomaji();
                    }
                } else {
                    // 中間一致（さらに後続の文字でルールが完結する可能性がある）の場合
                    if (mShiftSent) {
                        isUpper = false;
                    } else if (isUpper) {
                        mShiftSent = true;
                    }
                    mEngine.commitRomajiText(null, '\0', isUpper);
                }
                break;
            } else {
                if (node.getValue() != null) {
                    // 部分一致（入力の先頭部分だけがルールに適合）した場合
                    mEngine.commitRomajiText(node.getValue(), initialChar, false);
                    mShiftSent = false;
                    mComposing.delete(0, node.getKey().length());
                    if (node.getNext() != null) {
                        mComposing.insert(0, node.getNext());
                    }
                    isUpper = false;
                    continue;
                } else {
                    // 中間一致のルールから外れた場合（例: "kx" で "k" は中間一致だが "kx" は不適合）
                    // 一致していた部分（英字）を放出してバッファを詰める
                    boolean commitUpper = false;
                    if (mShiftSent) {
                        commitUpper = true;
                        mShiftSent = false;
                    }
                    mEngine.commitRomajiText(node.getKey(), initialChar, commitUpper);
                    mComposing.delete(0, node.getKey().length());
                    isUpper = false;
                    continue;
                }
            }
        }
    }

    /**
     * バッファに残っている未確定ローマ字を、可能な限り変換してすべて強制的に放出します。
     * <p>
     * モード切り替え時や、現在の入力を強制的に確定させたい場合（Enter押下時など）に呼び出されます。
     * </p>
     *
     * @return 1 文字以上放出された場合は true
     */
    public boolean flush() {
        if (mComposing.length() == 0) {
            reset();
            return false;
        }
        while (true) {
            char initialChar = '\0';
            if (mComposing.length() > 0) {
                initialChar = mComposing.charAt(0);
            }
            RomajiMap.Node node = romajiMap.prefixSearch(mComposing.toString());
            if (node == null) {
                break;
            }
            if (node.getValue() != null) {
                // 確定できる部分があれば放出
                mEngine.commitRomajiText(node.getValue(), initialChar, false);
                mComposing.delete(0, node.getKey().length());
                if (node.getNext() != null) {
                    mComposing.append(node.getNext());
                }
            } else {
                // 変換できないキー部分を放出
                mEngine.commitRomajiText(node.getKey(), initialChar, false);
                mComposing.delete(0, node.getKey().length());
            }
        }
        if (mComposing.length() != 0) {
            // それでも残った文字をそのまま放出
            mEngine.commitRomajiText(mComposing.toString(), mComposing.charAt(0), false);
        }
        reset();
        return true;
    }

    /**
     * 未確定状態を完全にリセットします。
     * バッファをクリアし、送り仮名待ち（Shift）フラグも解除します。
     *
     * @return リセット前, バッファが空でなかった場合は true
     */
    public boolean reset() {
        mShiftSent = false;
        if (mComposing.length() == 0) {
            return false;
        }
        mComposing.setLength(0);
        return true;
    }

    /**
     * 未確定バッファの末尾から 1 文字削除します。
     * バッファが空になった場合、送り仮名待ち状態も解除されます。
     *
     * @return 削除が行われた場合は true
     */
    public boolean handleBackspace() {
        if (mComposing.length() > 0) {
            mComposing.deleteCharAt(mComposing.length() - 1);
            if (mComposing.length() == 0) {
                mShiftSent = false;
            }
            return true;
        }
        return false;
    }
}
