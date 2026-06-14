package io.github.kachaya.skk;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

/**
 * 1つのキーの定義（ラベルとウェイト）を保持するクラスです。
 * 通常・シフト・記号などの各レイアウトは、このオブジェクトの独立したリストとして構成されます。
 */
public class KeyConfig {

    // --- QWERTY Layout Defaults ---

    public static final String DEFAULT_QWERTY_NORMAL = KeyConfig.layoutFromConfigArray(new KeyConfig[][]{
            {
                    new KeyConfig("1"),
                    new KeyConfig("2"),
                    new KeyConfig("3"),
                    new KeyConfig("4"),
                    new KeyConfig("5"),
                    new KeyConfig("6"),
                    new KeyConfig("7"),
                    new KeyConfig("8"),
                    new KeyConfig("9"),
                    new KeyConfig("0"),
            },
            {
                    new KeyConfig("q"),
                    new KeyConfig("w"),
                    new KeyConfig("e"),
                    new KeyConfig("r"),
                    new KeyConfig("t"),
                    new KeyConfig("y"),
                    new KeyConfig("u"),
                    new KeyConfig("i"),
                    new KeyConfig("o"),
                    new KeyConfig("p"),
            },
            {
                    new KeyConfig("a"),
                    new KeyConfig("s"),
                    new KeyConfig("d"),
                    new KeyConfig("f"),
                    new KeyConfig("g"),
                    new KeyConfig("h"),
                    new KeyConfig("j"),
                    new KeyConfig("k"),
                    new KeyConfig("l"),
                    new KeyConfig("-"),
            },
            {
                    new KeyConfig("⇧", 1.5f),
                    new KeyConfig("z"),
                    new KeyConfig("x"),
                    new KeyConfig("c"),
                    new KeyConfig("v"),
                    new KeyConfig("b"),
                    new KeyConfig("n"),
                    new KeyConfig("m"),
                    new KeyConfig("⌫", 1.5f),
            },
            {
                    new KeyConfig("Ctrl", 1.5f),
                    new KeyConfig("Sym"),
                    new KeyConfig(","),
                    new KeyConfig("⌴", 2.0f),
                    new KeyConfig("."),
                    new KeyConfig("◂"),
                    new KeyConfig("▸"),
                    new KeyConfig("⏎", 1.5f),
            }
    });

    public static final String DEFAULT_QWERTY_SHIFT = KeyConfig.layoutFromConfigArray(new KeyConfig[][]{
            {
                    new KeyConfig("1"),
                    new KeyConfig("2"),
                    new KeyConfig("3"),
                    new KeyConfig("4"),
                    new KeyConfig("5"),
                    new KeyConfig("6"),
                    new KeyConfig("7"),
                    new KeyConfig("8"),
                    new KeyConfig("9"),
                    new KeyConfig("0"),
            },
            {
                    new KeyConfig("Q"),
                    new KeyConfig("W"),
                    new KeyConfig("E"),
                    new KeyConfig("R"),
                    new KeyConfig("T"),
                    new KeyConfig("Y"),
                    new KeyConfig("U"),
                    new KeyConfig("I"),
                    new KeyConfig("O"),
                    new KeyConfig("P"),
            },
            {
                    new KeyConfig("A"),
                    new KeyConfig("S"),
                    new KeyConfig("D"),
                    new KeyConfig("F"),
                    new KeyConfig("G"),
                    new KeyConfig("H"),
                    new KeyConfig("J"),
                    new KeyConfig("K"),
                    new KeyConfig("L"),
                    new KeyConfig("/"),
            },
            {
                    new KeyConfig("⇧", 1.5f),
                    new KeyConfig("Z"),
                    new KeyConfig("X"),
                    new KeyConfig("C"),
                    new KeyConfig("V"),
                    new KeyConfig("B"),
                    new KeyConfig("N"),
                    new KeyConfig("M"),
                    new KeyConfig("⌫", 1.5f),
            },
            {
                    new KeyConfig("Ctrl", 1.5f),
                    new KeyConfig("Sym"),
                    new KeyConfig("<"),
                    new KeyConfig("⌴", 2.0f),
                    new KeyConfig(">"),
                    new KeyConfig("◂"),
                    new KeyConfig("▸"),
                    new KeyConfig("⏎", 1.5f),
            }
    });

    public static final String DEFAULT_QWERTY_SYMBOL = KeyConfig.layoutFromConfigArray(new KeyConfig[][]{
            {
                    new KeyConfig("1"),
                    new KeyConfig("2"),
                    new KeyConfig("3"),
                    new KeyConfig("4"),
                    new KeyConfig("5"),
                    new KeyConfig("6"),
                    new KeyConfig("7"),
                    new KeyConfig("8"),
                    new KeyConfig("9"),
                    new KeyConfig("0"),
            },
            {
                    new KeyConfig("~"),
                    new KeyConfig("@"),
                    new KeyConfig("#"),
                    new KeyConfig("$"),
                    new KeyConfig("%"),
                    new KeyConfig("^"),
                    new KeyConfig("&"),
                    new KeyConfig("*"),
                    new KeyConfig("("),
                    new KeyConfig(")"),
            },
            {
                    new KeyConfig("+"),
                    new KeyConfig("="),
                    new KeyConfig("_"),
                    new KeyConfig("/"),
                    new KeyConfig("|"),
                    new KeyConfig("\\"),
                    new KeyConfig("{"),
                    new KeyConfig("}"),
                    new KeyConfig("["),
                    new KeyConfig("]"),
            },
            {
                    new KeyConfig("Tab", 1.5f),
                    new KeyConfig("`"),
                    new KeyConfig("\""),
                    new KeyConfig("'"),
                    new KeyConfig(";"),
                    new KeyConfig(":"),
                    new KeyConfig("<"),
                    new KeyConfig(">"),
                    new KeyConfig("⌫", 1.5f),
            },
            {
                    new KeyConfig("Ctrl", 1.5f),
                    new KeyConfig("Sym"),
                    new KeyConfig("!"),
                    new KeyConfig("⌴", 2.0f),
                    new KeyConfig("?"),
                    new KeyConfig("◂"),
                    new KeyConfig("▸"),
                    new KeyConfig("⏎", 1.5f),
            }
    });

    // --- Symbols Bar Defaults ---

    public static final String DEFAULT_SYMBOLS_PRIMARY = KeyConfig.layoutFromConfigArray(new KeyConfig[][]{
            {
                    new KeyConfig("~"),
                    new KeyConfig("{"),
                    new KeyConfig("}"),
                    new KeyConfig("<"),
                    new KeyConfig(">"),
                    new KeyConfig("["),
                    new KeyConfig("]"),
                    new KeyConfig(","),
                    new KeyConfig("."),
                    new KeyConfig("-")
            }
    });

    public static final String DEFAULT_SYMBOLS_SECONDARY = KeyConfig.layoutFromConfigArray(new KeyConfig[][]{
            {
                    new KeyConfig("`"),
                    new KeyConfig("^"),
                    new KeyConfig("|"),
                    new KeyConfig("\\"),
                    new KeyConfig("%"),
                    new KeyConfig("&"),
                    new KeyConfig("$"),
                    new KeyConfig("¥"),
                    new KeyConfig(";"),
                    new KeyConfig("=")
            }
    });

    // --- Functional Codes ---
    public static final String CODE_SHIFT = "SHIFT";
    public static final String CODE_ENTER = "ENTER";
    public static final String CODE_BACKSPACE = "BACKSPACE";
    public static final String CODE_SPACE = "SPACE";
    public static final String CODE_SYM = "SYM";
    public static final String CODE_CTRL = "CTRL";
    public static final String CODE_TAB = "TAB";
    public static final String CODE_LEFT = "LEFT";
    public static final String CODE_RIGHT = "RIGHT";
    public static final String CODE_UP = "UP";
    public static final String CODE_DOWN = "DOWN";
    public static final String CODE_GAP = "GAP";

    /** パレット用：特殊キー */
    public static final List<KeyConfig> PALETTE_SPECIAL_KEYS = new ArrayList<KeyConfig>() {{
        add(new KeyConfig("⌴", CODE_SPACE));
        add(new KeyConfig("⏎", CODE_ENTER));
        add(new KeyConfig("⌫", CODE_BACKSPACE));
        add(new KeyConfig("⇧", CODE_SHIFT));
        add(new KeyConfig("Ctrl", CODE_CTRL));
        add(new KeyConfig("Tab", CODE_TAB));
        add(new KeyConfig("◂", CODE_LEFT));
        add(new KeyConfig("▴", CODE_UP));
        add(new KeyConfig("▾", CODE_DOWN));
        add(new KeyConfig("▸", CODE_RIGHT));
        add(new KeyConfig("Sym", CODE_SYM));
        add(new KeyConfig("Gap", CODE_GAP));
    }};

    /** パレット用：英数字 */
    public static final List<KeyConfig> PALETTE_ALPHA_KEYS = new ArrayList<KeyConfig>() {{
        String alpha = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz";
        for (char c : alpha.toCharArray()) {
            add(new KeyConfig(String.valueOf(c)));
        }
    }};

    /** パレット用：記号（QWERTYカスタマイズ用。数字を含む標準的なセット） */
    public static final List<KeyConfig> PALETTE_SYMBOL_KEYS = new ArrayList<KeyConfig>() {{
        String symbols = "0123456789!\"#$%&'()*+,-./:;<=>?@[\\]^_`{|}~¥";
        for (char c : symbols.toCharArray()) {
            add(new KeyConfig(String.valueOf(c)));
        }
    }};

    /** パレット用：記号バー専用（物理キーボードにない記号を補完するための最小限の ASCII セット） */
    public static final List<KeyConfig> PALETTE_SYMBOL_BAR_KEYS = new ArrayList<KeyConfig>() {{
        // 基本的な記号 (ASCII文字)
        String basic = "!\"#$%&'()*+,-./:;<=>?@[\\]^_`{|}~¥";
        for (char c : basic.toCharArray()) add(new KeyConfig(String.valueOf(c)));
    }};

    public String label;
    public float weight;
    public String code;

    public KeyConfig(String label) {
        this(label, 1.0f, null);
    }

    public KeyConfig(String label, String code) {
        this(label, 1.0f, code);
    }

    public KeyConfig(String label, float weight) {
        this(label, weight, null);
    }

    public KeyConfig(String label, float weight, String code) {
        this.label = label;
        this.weight = weight;
        this.code = code;
    }

    public static boolean isRepeatableLabel(String label) {
        return "⌫".equals(label) || "◂".equals(label) || "▸".equals(label) || "▴".equals(label) || "▾".equals(label);
    }

    public boolean isRepeatable() {
        if (code != null) {
            return CODE_BACKSPACE.equals(code) || CODE_LEFT.equals(code) ||
                    CODE_RIGHT.equals(code) || CODE_UP.equals(code) || CODE_DOWN.equals(code);
        }
        return isRepeatableLabel(this.label);
    }

    /**
     * レイアウト文字列（内部形式）を JSON 配列に変換します。
     */
    public static JSONArray layoutToJson(String layoutStr) throws JSONException {
        JSONArray jsonRows = new JSONArray();
        if (layoutStr == null || layoutStr.isEmpty()) return jsonRows;
        String[] lines = layoutStr.split("\n");
        for (String line : lines) {
            JSONArray jsonRow = new JSONArray();
            List<KeyConfig> configs = listFromString(line);
            for (KeyConfig config : configs) {
                JSONObject obj = new JSONObject();
                obj.put("label", config.label);
                if (config.weight != 1.0f) {
                    obj.put("weight", config.weight);
                }
                if (config.code != null) {
                    obj.put("code", config.code);
                }
                jsonRow.put(obj);
            }
            jsonRows.put(jsonRow);
        }
        return jsonRows;
    }

    /**
     * JSON 配列をレイアウト文字列（内部形式）に変換します。
     */
    public static String jsonToLayout(JSONArray jsonRows) throws JSONException {
        if (jsonRows == null) return "";
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < jsonRows.length(); i++) {
            JSONArray jsonRow = jsonRows.getJSONArray(i);
            for (int j = 0; j < jsonRow.length(); j++) {
                JSONObject obj = jsonRow.getJSONObject(j);
                String label = obj.getString("label");
                float weight = (float) obj.optDouble("weight", 1.0);
                String code = obj.has("code") ? obj.getString("code") : null;
                sb.append(new KeyConfig(label, weight, code).toString());
                if (j < jsonRow.length() - 1) sb.append(" ");
            }
            if (i < jsonRows.length() - 1) sb.append("\n");
        }
        return sb.toString();
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append(label).append("\u0001").append(weight);
        if (code != null) {
            sb.append("\u0001").append(code);
        }
        return sb.toString();
    }

    public static KeyConfig fromString(String s) {
        if (s == null || s.isEmpty()) return new KeyConfig("", 1.0f);
        String[] parts = s.split("\u0001");
        String label = parts.length > 0 ? parts[0] : "";
        float weight = 1.0f;
        String code = null;

        if (parts.length > 1) {
            try {
                weight = Float.parseFloat(parts[1]);
            } catch (NumberFormatException ignored) {
            }
        }
        if (parts.length > 2) {
            code = parts[2].isEmpty() ? null : parts[2];
        }

        // 互換性のため、codeが未指定ならlabelから推測する
        if (code == null) {
            code = inferCodeFromLabel(label);
        }

        return new KeyConfig(label, weight, code);
    }

    public static String inferCodeFromLabel(String label) {
        if (label == null) return null;
        switch (label) {
            case "⇧":
                return CODE_SHIFT;
            case "⏎":
            case "Enter":
                return CODE_ENTER;
            case "⌫":
                return CODE_BACKSPACE;
            case "⌴":
            case "Space":
                return CODE_SPACE;
            case "Sym":
                return CODE_SYM;
            case "Ctrl":
                return CODE_CTRL;
            case "Tab":
                return CODE_TAB;
            case "◂":
                return CODE_LEFT;
            case "▸":
                return CODE_RIGHT;
            case "▴":
                return CODE_UP;
            case "▾":
                return CODE_DOWN;
            case "GAP":
            case "Gap":
                return CODE_GAP;
            default:
                return null;
        }
    }

    public static List<KeyConfig> listFromString(String s) {
        List<KeyConfig> list = new ArrayList<>();
        if (s == null || s.isEmpty()) return list;
        String[] keys = s.trim().split("\\s+");
        for (String k : keys) {
            list.add(fromString(k));
        }
        return list;
    }

    /** KeyConfigの2次元配列からレイアウト文字列を生成します */
    public static String layoutFromConfigArray(KeyConfig[][] data) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < data.length; i++) {
            for (int k = 0; k < data[i].length; k++) {
                sb.append(data[i][k].toString());
                if (k < data[i].length - 1) sb.append(" ");
            }
            if (i < data.length - 1) sb.append("\n");
        }
        return sb.toString().trim();
    }

    /** レイアウト全体（行のリスト）を保存用の文字列に変換します */
    public static String layoutToString(List<List<KeyConfig>> layout) {
        StringBuilder sb = new StringBuilder();
        for (List<KeyConfig> row : layout) {
            sb.append(rowToLineString(row)).append("\n");
        }
        return sb.toString().trim();
    }

    /** 1行分のリストを文字列に変換します */
    public static String rowToLineString(List<KeyConfig> row) {
        StringBuilder sb = new StringBuilder();
        for (KeyConfig config : row) {
            sb.append(config.toString()).append(" ");
        }
        return sb.toString().trim();
    }
}
