package io.github.kachaya.skk;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 1つのキーの定義（ラベルとウェイト）を保持するクラスです。
 * 通常・シフト・記号などの各レイアウトは、このオブジェクトの独立したリストとして構成されます。
 */
public class KeyConfig {

    // --- Functional Codes ---
    public static final int CODE_NONE = 0;
    public static final int CODE_SHIFT = 1;
    public static final int CODE_ENTER = 2;
    public static final int CODE_BACKSPACE = 3;
    public static final int CODE_SPACE = 4;
    public static final int CODE_SYM = 5;
    public static final int CODE_CTRL = 6;
    public static final int CODE_TAB = 7;
    public static final int CODE_LEFT = 8;
    public static final int CODE_RIGHT = 9;
    public static final int CODE_UP = 10;
    public static final int CODE_DOWN = 11;
    public static final int CODE_GAP = 12;

    /** ファイル保存用の識別名に変換します */
    public static String codeToString(int code) {
        switch (code) {
            case CODE_SHIFT:
                return "SHIFT";
            case CODE_ENTER:
                return "ENTER";
            case CODE_BACKSPACE:
                return "BACKSPACE";
            case CODE_SPACE:
                return "SPACE";
            case CODE_SYM:
                return "SYM";
            case CODE_CTRL:
                return "CTRL";
            case CODE_TAB:
                return "TAB";
            case CODE_LEFT:
                return "LEFT";
            case CODE_RIGHT:
                return "RIGHT";
            case CODE_UP:
                return "UP";
            case CODE_DOWN:
                return "DOWN";
            case CODE_GAP:
                return "GAP";
            default:
                return null;
        }
    }

    /** 識別名からコードに変換します（大文字小文字を区別しません） */
    public static int stringToCode(String s) {
        if (s == null || s.isEmpty()) {
            return CODE_NONE;
        }
        switch (s.toUpperCase()) {
            case "SHIFT":
                return CODE_SHIFT;
            case "ENTER":
                return CODE_ENTER;
            case "BACKSPACE":
                return CODE_BACKSPACE;
            case "SPACE":
                return CODE_SPACE;
            case "SYM":
                return CODE_SYM;
            case "CTRL":
                return CODE_CTRL;
            case "TAB":
                return CODE_TAB;
            case "LEFT":
                return CODE_LEFT;
            case "RIGHT":
                return CODE_RIGHT;
            case "UP":
                return CODE_UP;
            case "DOWN":
                return CODE_DOWN;
            case "GAP":
                return CODE_GAP;
            default:
                return CODE_NONE;
        }
    }

    /** 各機能のデフォルトの表示ラベルを返します */
    public static String getDefaultLabel(int code) {
        switch (code) {
            case CODE_SPACE:
                return "⌴";
            case CODE_ENTER:
                return "⏎";
            case CODE_BACKSPACE:
                return "⌫";
            case CODE_SHIFT:
                return "⇧";
            case CODE_CTRL:
                return "Ctrl";
            case CODE_TAB:
                return "Tab";
            case CODE_LEFT:
                return "◂";
            case CODE_UP:
                return "▴";
            case CODE_DOWN:
                return "▾";
            case CODE_RIGHT:
                return "▸";
            case CODE_SYM:
                return "Sym";
            case CODE_GAP:
                return "Gap";
            default:
                return "";
        }
    }

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
                    new KeyConfig(CODE_SHIFT, 1.5f),
                    new KeyConfig("z"),
                    new KeyConfig("x"),
                    new KeyConfig("c"),
                    new KeyConfig("v"),
                    new KeyConfig("b"),
                    new KeyConfig("n"),
                    new KeyConfig("m"),
                    new KeyConfig(CODE_BACKSPACE, 1.5f),
            },
            {
                    new KeyConfig(CODE_CTRL, 1.5f),
                    new KeyConfig(CODE_SYM),
                    new KeyConfig(","),
                    new KeyConfig(CODE_SPACE, 2.0f),
                    new KeyConfig("."),
                    new KeyConfig(CODE_LEFT),
                    new KeyConfig(CODE_RIGHT),
                    new KeyConfig(CODE_ENTER, 1.5f),
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
                    new KeyConfig(CODE_SHIFT, 1.5f),
                    new KeyConfig("Z"),
                    new KeyConfig("X"),
                    new KeyConfig("C"),
                    new KeyConfig("V"),
                    new KeyConfig("B"),
                    new KeyConfig("N"),
                    new KeyConfig("M"),
                    new KeyConfig(CODE_BACKSPACE, 1.5f),
            },
            {
                    new KeyConfig(CODE_CTRL, 1.5f),
                    new KeyConfig(CODE_SYM),
                    new KeyConfig("<"),
                    new KeyConfig(CODE_SPACE, 2.0f),
                    new KeyConfig(">"),
                    new KeyConfig(CODE_LEFT),
                    new KeyConfig(CODE_RIGHT),
                    new KeyConfig(CODE_ENTER, 1.5f),
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
                    new KeyConfig(CODE_TAB, 1.5f),
                    new KeyConfig("`"),
                    new KeyConfig("\""),
                    new KeyConfig("'"),
                    new KeyConfig(";"),
                    new KeyConfig(":"),
                    new KeyConfig("<"),
                    new KeyConfig(">"),
                    new KeyConfig(CODE_BACKSPACE, 1.5f),
            },
            {
                    new KeyConfig(CODE_CTRL, 1.5f),
                    new KeyConfig(CODE_SYM),
                    new KeyConfig("!"),
                    new KeyConfig(CODE_SPACE, 2.0f),
                    new KeyConfig("?"),
                    new KeyConfig(CODE_LEFT),
                    new KeyConfig(CODE_RIGHT),
                    new KeyConfig(CODE_ENTER, 1.5f),
            }
    });

    // --- Symbols Bar Defaults ---

    public static final String DEFAULT_SYMBOLS_LAYOUT = KeyConfig.layoutFromConfigArray(new KeyConfig[][]{
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
            },
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

    /** パレット用：特殊キー */
    public static final List<KeyConfig> PALETTE_SPECIAL_KEYS = new ArrayList<KeyConfig>() {{
        add(new KeyConfig(CODE_SPACE));
        add(new KeyConfig(CODE_ENTER));
        add(new KeyConfig(CODE_BACKSPACE));
        add(new KeyConfig(CODE_SHIFT));
        add(new KeyConfig(CODE_CTRL));
        add(new KeyConfig(CODE_TAB));
        add(new KeyConfig(CODE_LEFT));
        add(new KeyConfig(CODE_UP));
        add(new KeyConfig(CODE_DOWN));
        add(new KeyConfig(CODE_RIGHT));
        add(new KeyConfig(CODE_SYM));
        add(new KeyConfig(CODE_GAP));
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
        for (char c : basic.toCharArray()) {
            add(new KeyConfig(String.valueOf(c)));
        }
    }};

    public String label;
    public float weight;
    public int code;

    /** 文字キーのコンストラクタ（ウェイト 1.0） */
    public KeyConfig(String label) {
        this(label, 1.0f, CODE_NONE);
    }

    /** 文字キーのコンストラクタ（ウェイト指定） */
    public KeyConfig(String label, float weight) {
        this(label, weight, CODE_NONE);
    }

    /** 機能キーのコンストラクタ（ウェイト 1.0） */
    public KeyConfig(int code) {
        this(getDefaultLabel(code), 1.0f, code);
    }

    /** 機能キーのコンストラクタ（ウェイト指定） */
    public KeyConfig(int code, float weight) {
        this(getDefaultLabel(code), weight, code);
    }

    /** 内部用マスターコンストラクタ */
    public KeyConfig(String label, float weight, int code) {
        this.label = label;
        this.weight = weight;
        this.code = code;
    }

    public boolean isRepeatable() {
        switch (code) {
            case CODE_BACKSPACE:
            case CODE_LEFT:
            case CODE_RIGHT:
            case CODE_UP:
            case CODE_DOWN:
                return true;
            default:
                return false;
        }
    }

    /**
     * レイアウト（行のリスト）を、人間が読みやすい特定の JSON 形式の文字列に変換します。
     * 各キーを 1 行に配置するコンパクトな形式です。
     */
    public static String layoutToJsonString(KeyConfig[][] layout) {
        StringBuilder sb = new StringBuilder();
        sb.append("[\n");
        for (int i = 0; i < layout.length; i++) {
            sb.append("    [\n");
            for (int j = 0; j < layout[i].length; j++) {
                KeyConfig config = layout[i][j];
                String escapedLabel = config.label.replace("\\", "\\\\").replace("\"", "\\\"");
                sb.append("      { \"label\": \"").append(escapedLabel).append("\"");
                if (config.code != CODE_NONE) {
                    sb.append(", \"code\": \"").append(codeToString(config.code)).append("\"");
                }
                if (config.weight != 1.0f) {
                    sb.append(", \"weight\": ").append(config.weight);
                }
                sb.append(" }");
                if (j < layout[i].length - 1) {
                    sb.append(",");
                }
                sb.append("\n");
            }
            sb.append("    ]");
            if (i < layout.length - 1) {
                sb.append(",");
            }
            sb.append("\n");
        }
        sb.append("  ]");
        return sb.toString();
    }

    /**
     * 指定されたマップ（キー：プレフィックス名、値：レイアウト）を、
     * バックアップ用の JSON 形式文字列に変換します。
     */
    public static String backupToJsonString(Map<String, KeyConfig[][]> layouts) {
        StringBuilder sb = new StringBuilder();
        sb.append("{\n");
        List<String> keys = new ArrayList<>(layouts.keySet());
        for (int i = 0; i < keys.size(); i++) {
            String key = keys.get(i);
            sb.append("  \"").append(key).append("\": ");
            sb.append(layoutToJsonString(layouts.get(key)));
            if (i < keys.size() - 1) {
                sb.append(",");
            }
            sb.append("\n");
        }
        sb.append("}");
        return sb.toString();
    }

    /**
     * JSON 文字列からレイアウト（KeyConfig の 2 次元配列）を復元します。
     */
    public static KeyConfig[][] layoutFromAnyString(String s) {
        if (s == null || s.isEmpty()) {
            return new KeyConfig[0][0];
        }

        try {
            JSONArray json = new JSONArray(s);
            if (json.length() > 0 && json.optJSONObject(0) != null) {
                // 1次元配列（1行のみ）の場合、2次元配列に包み直す
                JSONArray wrapper = new JSONArray();
                wrapper.put(json);
                json = wrapper;
            }
            int rowCount = json.length();
            KeyConfig[][] layout = new KeyConfig[rowCount][];
            for (int i = 0; i < rowCount; i++) {
                JSONArray jsonRow = json.optJSONArray(i);
                if (jsonRow == null) {
                    layout[i] = new KeyConfig[0];
                    continue;
                }
                layout[i] = new KeyConfig[jsonRow.length()];
                for (int j = 0; j < jsonRow.length(); j++) {
                    JSONObject obj = jsonRow.optJSONObject(j);
                    if (obj == null) {
                        layout[i][j] = new KeyConfig("");
                        continue;
                    }
                    String label = obj.optString("label", "");
                    float weight = (float) obj.optDouble("weight", 1.0);
                    int code = obj.has("code") ? stringToCode(obj.getString("code")) : CODE_NONE;
                    layout[i][j] = new KeyConfig(label, weight, code);
                }
            }
            return layout;
        } catch (JSONException e) {
            // 解析不能な場合は空のレイアウトを返す
            return new KeyConfig[0][0];
        }
    }

    @Override
    public String toString() {
        JSONObject obj = new JSONObject();
        try {
            obj.put("label", label);
            if (weight != 1.0f) {
                obj.put("weight", weight);
            }
            if (code != CODE_NONE) {
                obj.put("code", codeToString(code));
            }
        } catch (JSONException ignored) {
        }
        return obj.toString();
    }

    /** JSON文字列から単一のKeyConfigを生成します */
    public static KeyConfig fromString(String s) {
        try {
            JSONObject obj = new JSONObject(s);
            String label = obj.optString("label", "");
            float weight = (float) obj.optDouble("weight", 1.0);
            int code = obj.has("code") ? stringToCode(obj.getString("code")) : CODE_NONE;
            return new KeyConfig(label, weight, code);
        } catch (Exception e) {
            return new KeyConfig("", 1.0f);
        }
    }

    /** KeyConfigの2次元配列からレイアウト文字列(JSON形式)を生成します */
    public static String layoutFromConfigArray(KeyConfig[][] data) {
        return layoutToJsonString(data);
    }
}
