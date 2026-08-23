package io.github.kachaya.skk.keyboard;

/**
 * キーボードの修飾キー（Shift, Ctrl, Sym 等）の状態を保持するクラスです。
 */
public class KeyboardState {
    /** Shift キーが一時的に押されている（または 1 文字分有効な）状態。 */
    public final boolean shifted;
    /** Shift キーがロックされている（常時大文字入力）状態。 */
    public final boolean shiftLocked;
    /** Control キーが有効な状態。 */
    public final boolean control;
    /** 記号入力レイアウトが有効な状態。 */
    public final boolean symbol;
    /** 記号入力レイアウトがロックされている状態。 */
    public final boolean symbolLocked;

    /**
     * キーボードの修飾状態を構築します。
     *
     * @param shifted      Shift状態
     * @param shiftLocked  Shiftロック状態
     * @param control      Control状態
     * @param symbol       記号状態
     * @param symbolLocked 記号ロック状態
     */
    public KeyboardState(boolean shifted, boolean shiftLocked, boolean control, boolean symbol, boolean symbolLocked) {
        this.shifted = shifted;
        this.shiftLocked = shiftLocked;
        this.control = control;
        this.symbol = symbol;
        this.symbolLocked = symbolLocked;
    }
}
