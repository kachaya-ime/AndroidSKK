package io.github.kachaya.skk.keyboard;

import android.graphics.Path;
import android.graphics.PathMeasure;
import android.graphics.PointF;

import androidx.core.graphics.PathParser;

import java.util.ArrayList;
import java.util.List;

/**
 * 手書きのジェスチャ（Path）を解析し、登録された辞書から最適な文字コードを特定する認識エンジンクラスです。
 *
 * <p>認識プロセスは以下のステップで行われます：</p>
 * <ol>
 *   <li><b>Pathのリサンプリング</b>: 入力された軌跡を固定数（デフォルト32点）の点列に変換し、サンプリング密度の影響を排除します。</li>
 *   <li><b>座標の正規化</b>: 座標を 0.0〜1.0 の範囲に収めます。極端な長方形の場合はアスペクト比を維持し、それ以外は正方形にスケーリングしてサイズや位置の変動を吸収します。</li>
 *   <li><b>特徴抽出</b>: 方向配列（16方向）、角（bends）の数、正規化空間での全長、移動距離（累積および変位）、始点終点間距離などの幾何学的特徴を抽出します。</li>
 *   <li><b>辞書フィルタリング</b>: 角の数（許容誤差±2）や始点・終点の方向（16方向中±2）が明らかに異なる候補を段階的に排除し、計算負荷を抑えつつ精度を高めます。</li>
 *   <li><b>類似度計算（スコアリング）</b>: 残った候補に対し、量子化された方向差の平均と、移動特性の差分（重み付けあり）に基づき不一致スコアを算出します。</li>
 * </ol>
 */
public class Stroke {

    /** 方向の量子化分割数。360度をこの数で分割します（現在は16方向、22.5度刻み）。 */
    public static final int DIRECTION_COUNT = 16;
    /** デフォルトのサンプリング点数。複雑なジェスチャも十分に表現可能な値です。 */
    public static final int DEFAULT_SAMPLING_COUNT = 32;
    /** アルファベット辞書のリスト */
    public static final List<DictionaryEntry> ALPHABET_DIC = new ArrayList<>();
    /** 数字辞書のリスト */
    public static final List<DictionaryEntry> NUMERIC_DIC = new ArrayList<>();
    /** 記号辞書のリスト */
    public static final List<DictionaryEntry> PUNCTUATION_DIC = new ArrayList<>();

    static {
        RawEntry[] source = {
                new RawEntry('i', '1', '\'', "m 6,1 v 10"),
                new RawEntry(KeyConfig.CODE_SHIFT, 0, '!', "m 6,11 v -10"),
                new RawEntry(KeyConfig.CODE_SPACE, KeyConfig.CODE_SPACE, '-', "m 1,6 h 10"),
                new RawEntry(KeyConfig.CODE_BACKSPACE, KeyConfig.CODE_BACKSPACE, KeyConfig.CODE_BACKSPACE, "m 11,6 h -10"),
                new RawEntry(KeyConfig.CODE_ENTER, KeyConfig.CODE_ENTER, ',', "m 10,2 -8,8"),
                new RawEntry(KeyConfig.CODE_CTRL, KeyConfig.CODE_CTRL, '/', "m 2,10 8,-8"),
                new RawEntry(KeyConfig.CODE_SYM, KeyConfig.CODE_SYM, '\\', "m 2,2 8,8"),
                new RawEntry(KeyConfig.CODE_CANCEL, KeyConfig.CODE_CANCEL, KeyConfig.CODE_CANCEL, "m 10,10 -8,-8"),
                new RawEntry(KeyConfig.CODE_DOWN, KeyConfig.CODE_DOWN, ':', "m 6,1 v 10 -10"),
                new RawEntry(KeyConfig.CODE_UP, KeyConfig.CODE_UP, '|', "m 6,11 v -10 10"),
                new RawEntry(KeyConfig.CODE_RIGHT, KeyConfig.CODE_RIGHT, '_', "m 1,6 h 10 -10"),
                new RawEntry(KeyConfig.CODE_LEFT, KeyConfig.CODE_LEFT, 0, "m 11,6 h -10 10"),
                new RawEntry(0, '8', ';', "m 10,2 -8,8 8,-8"),
                new RawEntry(KeyConfig.CODE_8_2_8, KeyConfig.CODE_8_2_8, KeyConfig.CODE_8_2_8, "m 2,10 8,-8 -8,8"),
                new RawEntry(KeyConfig.CODE_10_4_10, KeyConfig.CODE_10_4_10, KeyConfig.CODE_10_4_10, "m 2,2 8,8 -8,-8"),
                new RawEntry(0, 0, '`', "m 10,10 -8,-8 8,8"),
                new RawEntry(KeyConfig.CODE_SHORTCUT, KeyConfig.CODE_SHORTCUT, 0, "m 3,11 c 0,0 5,-4 5,-7 0,-3 -2,-3 -2,-3 0,0 -2,0 -2,3 0,3 5,7 5,7"),
                new RawEntry('a', '1', '^', "m 2,11 c 0,0 3,-10 4,-10 1,0 4,10 4,10"),
                new RawEntry('b', 0, 0, "m 3,11 v -7 c 0,0 0,-3 3,-3 3,0 3,2 3,2 0,3 -5,3 -5,3 7,0 6,5 2,5 -1,0 -2,-1 -2,-1"),
                new RawEntry('b', 0, 0, "m 3,1 v 8 c 0,0 0,-8 3,-8 3,0 3,2 3,2 0,3 -5,3 -5,3 7,0 6,5 2,5 -2,0 -3,-1 -3,-1"),
                new RawEntry('c', 0, '(', "m 9,3 c 0,0 -1,-2 -3,-2 -4,0 -4,5 -4,5 0,0 0,5 4,5 2,0 3,-2 3,-2"),
                new RawEntry('c', 0, '(', "m 8,1 c 0,0 -4,1 -4,5 0,4 4,5 4,5"),
                new RawEntry('d', 0, 0, "m 3,1 v 8 c 0,0 0,-8 3,-8 4,0 4,5 4,5 0,0 0,5 -4,5 -2,0 -3,-1 -3,-1"),
                new RawEntry('d', 0, 0, "m 3,11 v -8 c 0,0 0,-2 2,-2 5,0 5,5 5,5 0,0 0,5 -6,5"),
                new RawEntry('e', 0, '{', "m 8,2 c 0,0 -5,0 -5,4 0,4 5,0 5,0 -5,0 -5,4 0,4"),
                new RawEntry('f', 0, 0, "m 9,1 h -4 c -1,0 -2,1 -2,2 v 8"),
                new RawEntry('f', 0, KeyConfig.CODE_TAB, "m 3,11 v -8 c 0,-1 1,-2 2,-2 h 4"),
                new RawEntry('g', 0, 0, "m 9,2 c 0,0 -1,-1 -3,-1 -4,0 -4,5 -4,5 0,0 0,5 4,5 3,0 3,-3 3,-3 0,-2 -3,-2 -3,-2 h 4"),
                new RawEntry('g', '6', 0, "m 8,1 c 0,0 -5,0 -5,6 0,4 3,4 3,4 0,0 3,0 3,-3 0,-2 -2,-2 -2,-2 -2,0 -3,1 -3,1"),
                new RawEntry('h', '9', '#', "m 3,1 v 10 c 0,0 0,-6 3,-6 3,0 3,6 3,6"),
                new RawEntry('h', '9', '#', "m 2,1 v 9 c 0,1 1,1 2,0 l 4,-8 c 1,-1 2,-1 2,0 v 9"),
                new RawEntry('j', 0, ',', "m 9,1 v 8 c 0,1 -1,2 -2,2 h -4"),
                new RawEntry('k', 0, '+', "m 10,1 c 0,0 -3,7 -6,7 -2,0 -2,-2 -2,-2 0,0 0,-2 2,-2 3,0 6,7 6,7"),
                new RawEntry('k', 0, '+', "m 11,3 c 0,0 -4,5 -7,5 -4,0 -4,-4 0,-4 3,0 7,5 7,5"),
                new RawEntry('l', '4', '(', "m 2,1 v 8 c 0,1 1,2 2,2 h 6"),
                new RawEntry('l', '4', '(', "m 3,2 v 4 c 0,0 0,2 2,2 h 4"),
                new RawEntry('m', 0, 0, "m 2,11 c 0,0 0,-10 2,-10 2,0 2,6 2,6 0,0 0,-6 2,-6 2,0 2,10 2,10"),
                new RawEntry('m', 0, 0, "m 2,1 v 10 c 0,0 0,-10 2,-10 2,0 2,6 2,6 0,0 0,-6 2,-6 2,0 2,10 2,10"),
                new RawEntry('n', 0, '"', "m 2,11 v -9 c 0,-1 1,-1 2,0 l 4,8 c 1,1 2,1 2,0 l 0,-9"),
                new RawEntry('o', '0', '@', "m 5,1 c 0,0 -3,1 -3,5 0,5  4,5  4,5 0,0  4,0  4,-5 0,-4 -3,-5 -3,-5"),
                new RawEntry('o', '0', '@', "m 7,1 c 0,0  3,1  3,5 0,5 -4,5 -4,5 0,0 -4,0 -4,-5 0,-4  3,-5  3,-5"),
                new RawEntry('p', 0, 0, "m 3,1 v 10 c 0,0 0,-10 3,-10 3,0 3,2 3,2 0,3 -5,3 -5,3"),
                new RawEntry('p', 0, 0, "m 3,11 v -7 c 0,0 0,-3 3,-3 3,0 3,2 3,2 0,3 -5,3 -5,3"),
                new RawEntry('q', 0, 0, "m 4,2 c 0,0 -2,1 -2,5 0,4 3,4 3,4 0,0 4,0 4,-5 0,-4 -3,-5 -3,-5 h 4"),
                new RawEntry('r', 0, 0, "m 3,1 v 10 c 0,0 0,-10 3,-10 3,0 3,2 3,2 0,3 -5,3 -5,3 4,0 5,5 5,5"),
                new RawEntry('r', 0, 0, "m 3,11 v -7 c 0,0 0,-3 3,-3 3,0 3,2 3,2 0,3 -5,3 -5,3 4,0 5,5 5,5"),
                new RawEntry('s', '5', '$', "m 9,3 c 0,0 -1,-2 -3,-2 0,0 -3,0 -3,3 0,3 6,1 6,4 0,3 -3,3 -3,3 -2,0 -3,-2 -3,-2"),
                new RawEntry('s', '5', '$', "m 9,1 c 0,0 -4,0 -5,1 -1,1 -1,4 -1,4 0,0 7,-3 7,2 0,3 -4,3 -4,3 -2,0 -3,-1 -3,-1"),
                new RawEntry('t', '7', '?', "m 3,1 h 4 c 2,0 2,1 2,2 v 8"),
                new RawEntry('t', '7', '?', "m 3,1 h 5 c 1,0 1,1 0,4 l -2,6"),
                new RawEntry('u', '0', 0, "m 2,1 v 6 c 0,0 0,4 4,4 4,0 4,-4 4,-4 v -6"),
                new RawEntry('v', '0', 0, "m 10,1 c 0,0 -3,10 -4,10 -1,0 -4,-10 -4,-10"),
                new RawEntry('v', 0, 0, "m 2,1 c 0,0 2,10 3,10 1,0 2,-9 2,-9 0,-1 1,-1 2,-1 h 1"),
                new RawEntry('w', 0, 0, "m 2,1 c 0,0 0,10 2,10 2,0 2,-6 2,-6 0,0 0,6 2,6 2,0 2,-10 2,-10"),
                new RawEntry('x', '6', '*', "m 2,1 c 0,0 4,7 6,7 2,0 2,-2 2,-2 0,0 0,-2 -2,-2 -2,0 -6,7 -6,7"),
                new RawEntry('x', '6', '*', "m 1,3 c 0,0 4,5 7,5 4,0 4,-4 0,-4 -3,0 -7,5 -7,5"),
                new RawEntry('y', '8', '&', "m 3,1 v 2 c 0,0 0,2 2,2 2,0 3,-4 3,-4 0,0 -1,10 -3,10 -1,0 -2,-1 -1,-3 1,-2 6,-4 6,-4"),
                new RawEntry('y', '8', '&', "m 3,1 c 0,0 5,4 5,7 0,3 -2,3 -2,3 0,0 -2,0 -2,-3 0,-3 5,-7 5,-7"),
                new RawEntry('z', '2', '=', "m 2,1 h 7 c 1,0 1,1 0,2 l -6,6 c -1,1 -1,2 0,2 h 7"),
                new RawEntry('z', '2', '=', "m 3,4 c 0,0 0,-3 3,-3 3,0 3,3 3,3 0,2 -6,7 -6,7 h 6"),
                new RawEntry('b', '3', '}', "m 4,2 c 0,0 1,-1 2,-1 4,0 4,5 0,5 4,0 4,5 0,5 -2,0 -3,-2 -3,-2"),
                new RawEntry('s', '5', '$', "m 4,1 -1,5 c 0,0 7,-3 7,2 0,3 -4,3 -4,3 -2,0 -3,-1 -3,-1"),
                new RawEntry(0, '8', '&', "m 4,1 c 0,0 5,0 5,2 0,2 -6,2 -6,5 0,3 3,3 3,3 0,0 3,0 3,-3 0,-3 -6,-5 -6,-5"),
                new RawEntry(0, '8', '&', "m 9,1 c 0,0 -5,4 -5,7 0,3 2,3 2,3 0,0 2,0 2,-3 0,-3 -5,-7 -5,-7"),
                new RawEntry('s', '9', 0, "m 8,1 c 0,0 -5,0 -5,3 0,3 5,2 6,-1 l -2,8"),
                new RawEntry(0, 0, '?', "m 3,6 c 0,0 1,-2 3,-2 0,0 3,0 3,2 0,3 -3,3 -3,8"),
                new RawEntry(0, 0, ')', "m 4,1 c 0,0 4,1 4,5 0,4 -4,5 -4,5"),
                new RawEntry(0, 0, '<', "m 11,10 c 0,0 -10,-3 -10,-4 0,-1 10,-4 10,-4"),
                new RawEntry(0, 0, '>', "m 1,10 c 0,0 10,-3 10,-4 0,-1 -10,-4 -10,-4"),
                new RawEntry(0, 0, '[', "m 9,9 c 0,0 -1,2 -3,2 -4,0 -4,-5 0,-5 -4,0 -4,-5 0,-5 1,0 2,1 2,1"),
                new RawEntry(0, 0, ']', "m 3,9 c 0,0 1,2 3,2 4,0 4,-5 0,-5 4,0 4,-5 0,-5 -1,0 -2,1 -2,1"),
                new RawEntry(0, 0, '%', "m 1,1 c 0,0 4,4 4,7 0,4 -3,4 -3,0 0,-3 3,-5 4,-5 1,0 4,2 4,5 0,4 -3,4 -3,0 0,-3.5 4,-7 4,-7"),
                new RawEntry(0, 0, '~', "m 9,1 v 9 c 0,2 -1,0 -3,-4 -2,-4 -3,-6 -3,-4 v 9"),
        };

        for (RawEntry re : source) {
            try {
                Path path = PathParser.createPathFromPathData(re.svg);
                List<PointF> pts = PathSampler.sample(path);
                if (pts.size() >= 2) {
                    FeatureVector fv = FeatureExtractor.extract(Preprocessor.normalize(pts));
                    if (re.a != 0) ALPHABET_DIC.add(new DictionaryEntry(re.a, fv));
                    if (re.n != 0) NUMERIC_DIC.add(new DictionaryEntry(re.n, fv));
                    if (re.p != 0) PUNCTUATION_DIC.add(new DictionaryEntry(re.p, fv));
                }
            } catch (Exception ignored) {
            }
        }
        // 各辞書の内容をダンプ（デバッグ用）
//        dumpDictionary("ALPHABET", ALPHABET_DIC);
//        dumpDictionary("NUMERIC", NUMERIC_DIC);
//        dumpDictionary("PUNCTUATION", PUNCTUATION_DIC);
    }

    private final List<DictionaryEntry> dictionary;

    private static void dumpDictionary(String name, List<DictionaryEntry> dic) {
        System.out.println("--- Dictionary: " + name + " (Size=" + dic.size() + ") ---");
        for (DictionaryEntry entry : dic) {
            char c = (char) entry.code;
            System.out.println("  '" + (c > ' ' ? c : "?") + "' (code=" + entry.code + ") fv=" + entry.fv);
        }
    }

    /**
     * 指定された辞書を使用して Stroke エンジンを初期化します。
     *
     * @param dictionary このエンジンが認識対象とする辞書エントリのリスト
     */
    public Stroke(List<DictionaryEntry> dictionary) {
        this.dictionary = dictionary;
    }

    /** 方向インデックス間の円環的な距離（最小の角度差）を計算します */
    private static int dirDist(int a, int b) {
        int d = Math.abs(a - b);
        return Math.min(d, DIRECTION_COUNT - d);
    }

    /**
     * 入力された Path を解析し、最適な文字コードを特定します。
     *
     * @param path 入力された手書きの軌跡
     * @return 最も一致する文字コード（UnicodeまたはKey定数）。一致するものがない場合や軌跡が短すぎる場合は 0
     */
    public int recognize(Path path) {

        // 1. Path → 固定数の点列にリサンプリング
        List<PointF> pts = PathSampler.sample(path);
        if (pts.size() < 2) {
            return 0;
        }

        // 2. 前処理（座標の正規化）
        List<PointF> norm = Preprocessor.normalize(pts);

        // 3. 特徴抽出（方向配列、角の数、正規化空間での全長、移動距離（累積および変位）、始点終点間距離などの幾何学的特徴を抽出します。
        FeatureVector fv = FeatureExtractor.extract(norm);

        // 4. 辞書検索（フィルタリングなし：全件をスコアリング対象とする）
        List<DictionaryEntry> candidates = new ArrayList<>(dictionary);
        if (candidates.isEmpty()) return 0;

        // 5. 残った候補からスコア（不一致度）で最終判定
        class ScoredEntry implements Comparable<ScoredEntry> {
            final DictionaryEntry entry;
            final double score;

            ScoredEntry(DictionaryEntry entry, double score) {
                this.entry = entry;
                this.score = score;
            }

            @Override
            public int compareTo(ScoredEntry o) {
                return Double.compare(this.score, o.score);
            }
        }

        List<ScoredEntry> scoredEntries = new ArrayList<>();
        for (DictionaryEntry e : candidates) {
            double dirDiffSum = 0;
            int n = Math.min(fv.dir.length, e.fv.dir.length);
            for (int i = 0; i < n; i++) {
                int d = dirDist(fv.dir[i], e.fv.dir[i]);
                dirDiffSum += d;
            }
            double avgDirDiff = (n > 0) ? (dirDiffSum / n) : 0;
            scoredEntries.add(new ScoredEntry(e, avgDirDiff));
        }
        java.util.Collections.sort(scoredEntries);

        // デバッグ出力：入力 FV と上位3候補の情報をログ出力
        if (!scoredEntries.isEmpty()) {
            System.out.println("Input FV: " + fv);
            System.out.println("Top 3 candidates (Filtered Size=" + scoredEntries.size() + "):");
            for (int i = 0; i < Math.min(3, scoredEntries.size()); i++) {
                ScoredEntry se = scoredEntries.get(i);
                char c = (char) se.entry.code;
                System.out.println("  #" + (i + 1) + ": '" + (c > ' ' ? c : "?") + "' (code=" + se.entry.code + ") score=" + String.format("%.2e", se.score) + " fv=" + se.entry.fv);
            }
        }

        if (!scoredEntries.isEmpty()) {
            int bestCode = scoredEntries.get(0).entry.code;
            System.out.println("Recognized: " + (char) bestCode + " (score=" + String.format("%.2f", scoredEntries.get(0).score) + ")");
            return bestCode;
        }

        return 0;
    }

    /**
     * 辞書に含まれる一つのエントリ（文字コードと特徴量）を表すクラスです。
     */
    public static class DictionaryEntry {
        /** 文字コード（UnicodeまたはKey定数） */
        public final int code;
        /** その文字に対応するジェスチャの特徴量 */
        public final FeatureVector fv;

        public DictionaryEntry(int code, FeatureVector fv) {
            this.code = code;
            this.fv = fv;
        }
    }

    /**
     * ジェスチャから抽出された幾何学的な特徴量を保持するクラスです。
     */
    public static class FeatureVector {
        /** 各区間の量子化された方向インデックス（0〜DIRECTION_COUNT-1） */
        int[] dir;
        /** 正規化された [0, 1] 空間での軌跡の全長 */
        double normalizedLength;
        /** 始点の移動方向インデックス */
        int startDir;
        /** 終点の移動方向インデックス */
        int endDir;
        /** 始点と終点の直線距離。閉じ具合の指標になります（'o'と'c'の区別など） */
        double startEndDist;
        /** X方向の総移動距離（絶対値の和）。往復運動の強さを示します（'c'と'e'の区別など） */
        double travelX;
        /** Y方向の総移動距離（絶対値の和） */
        double travelY;

        @Override
        public String toString() {
            return "FV{len=" + String.format("%.2f", normalizedLength) + ", dist=" + String.format("%.2f", startEndDist) + ", travelX=" + String.format("%.2f", travelX) + ", travelY=" + String.format("%.2f", travelY) + ", sDir=" + startDir + ", eDir=" + endDir + "}";
        }
    }

    /**
     * 軌跡（点列）の前処理を行う内部クラスです。
     */
    public static class Preprocessor {
        /**
         * 点列を正規化し、位置とサイズを一定に揃えます。
         * 基本的に [0, 1] の正方形に収まるようスケーリングしますが、
         * アスペクト比が極端な場合（一辺が他方の1/3以下）は、
         * 長辺側に合わせたスケーリングを行い、直線的な形状が歪みすぎるのを防ぎます。
         *
         * @param pts 元の点列
         * @return 正規化された点列
         */
        public static List<PointF> normalize(List<PointF> pts) {
            float minX = Float.MAX_VALUE, minY = Float.MAX_VALUE;
            float maxX = -Float.MAX_VALUE, maxY = -Float.MAX_VALUE;
            for (PointF p : pts) {
                minX = Math.min(minX, p.x); minY = Math.min(minY, p.y);
                maxX = Math.max(maxX, p.x); maxY = Math.max(maxY, p.y);
            }
            float w = maxX - minX, h = maxY - minY;
            float scaleX, scaleY;
            if (w < h / 3.0f) {
                scaleX = scaleY = 1.0f / Math.max(h, 1e-9f);
            } else if (h < w / 3.0f) {
                scaleX = scaleY = 1.0f / Math.max(w, 1e-9f);
            } else {
                scaleX = 1.0f / Math.max(w, 1e-9f);
                scaleY = 1.0f / Math.max(h, 1e-9f);
            }
            List<PointF> out = new ArrayList<>();
            for (PointF p : pts) {
                out.add(new PointF((p.x - minX) * scaleX, (p.y - minY) * scaleY));
            }
            return out;
        }
    }

    /**
     * 点列から幾何学的な特徴を抽出する内部クラスです。
     */
    public static class FeatureExtractor {
        /**
         * 正規化された点列から各幾何学的特徴を抽出します。
         *
         * @param pts 正規化済みの点列
         * @return 抽出された特徴ベクトル
         */
        public static FeatureVector extract(List<PointF> pts) {
            FeatureVector fv = new FeatureVector();
            int n = pts.size() - 1;
            fv.dir = new int[n];
            double[] ang = new double[n];
            for (int i = 0; i < n; i++) {
                ang[i] = Math.atan2(pts.get(i + 1).y - pts.get(i).y, pts.get(i + 1).x - pts.get(i).x);
                double deg = (Math.toDegrees(ang[i]) + 360) % 360;
                double bucketSize = 360.0 / DIRECTION_COUNT;
                fv.dir[i] = (int) (((deg + bucketSize / 2.0) % 360) / bucketSize);
            }
            fv.normalizedLength = 0;
            fv.travelX = 0;
            fv.travelY = 0;
            for (int i = 0; i < n; i++) {
                float dx = pts.get(i + 1).x - pts.get(i).x;
                float dy = pts.get(i + 1).y - pts.get(i).y;
                fv.normalizedLength += Math.hypot(dx, dy);
                fv.travelX += Math.abs(dx);
                fv.travelY += Math.abs(dy);
            }
            fv.startDir = fv.dir.length > 0 ? fv.dir[0] : 0;
            fv.endDir = fv.dir.length > 0 ? fv.dir[fv.dir.length - 1] : 0;
            fv.startEndDist = Math.hypot(pts.get(0).x - pts.get(n).x, pts.get(0).y - pts.get(n).y);
            return fv;
        }
    }

    /**
     * 特徴量間の類似度（不一致度）計算を行う内部クラスです。
     */
    public static class Matcher {
        /** 方向インデックス間の円環的な距離（最小の角度差）を計算します */
        private static int dirDist(int a, int b) {
            int d = Math.abs(a - b);
            return Math.min(d, DIRECTION_COUNT - d);
        }

        /**
         * 2つの特徴ベクトルの非類似度スコアを計算します。数値が小さいほど類似しています。
         * 方向の差の平均に加え、移動特性や閉じ具合に重み付けをして合算します。
         *
         * @param s 認識対象（入力）の特徴
         * @param g 比較対象（辞書）の特徴
         * @return 総合的な非類似度スコア
         */
        public static double score(FeatureVector s, FeatureVector g) {
            double result = 0;

            if (s == null || g == null) return Double.MAX_VALUE;
            int n = Math.min(s.dir.length, g.dir.length);
            if (n == 0) return Double.MAX_VALUE;

            if (Math.abs(s.travelX - g.travelX) > 1.0f) return Double.MAX_VALUE;
            if (Math.abs(s.travelY - g.travelY) > 1.0f) return Double.MAX_VALUE;

            if (dirDist(s.startDir, g.startDir) > 2) return Double.MAX_VALUE;
            if (dirDist(s.endDir, g.endDir) > 2) return Double.MAX_VALUE;

            double dirDiffSum = 0;
            for (int i = 0; i < n; i++) {
                int d = dirDist(s.dir[i], g.dir[i]);
                dirDiffSum += d * d;
            }
            double avgDirDiff = dirDiffSum / n;

            result += avgDirDiff * avgDirDiff;  // 方向差分の2乗

            return result;
        }
    }

    /**
     * Path オブジェクトから座標を抽出し、点列に変換するための内部クラスです。
     */
    public static class PathSampler {
        /**
         * Path を均等にサンプリングして固定数の点列に変換します。
         * サンプリング点数は {@link #DEFAULT_SAMPLING_COUNT} が使用されます。
         *
         * @param path 入力された軌跡
         * @return PointF のリスト
         */
        public static List<PointF> sample(Path path) {
            List<PointF> pts = new ArrayList<>();
            PathMeasure pm = new PathMeasure(path, false);
            float length = pm.getLength();
            float interval = length / (DEFAULT_SAMPLING_COUNT - 1);
            float[] pos = new float[2];
            for (int i = 0; i < DEFAULT_SAMPLING_COUNT; i++) {
                pm.getPosTan(i * interval, pos, null);
                pts.add(new PointF(pos[0], pos[1]));
            }
            return pts;
        }
    }

    private static class RawEntry {
        final int a, n, p;
        final String svg;

        RawEntry(int a, int n, int p, String svg) {
            this.a = a;
            this.n = n;
            this.p = p;
            this.svg = svg;
        }
    }
}
