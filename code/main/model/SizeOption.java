package tokyoera.model;

// All the clothing sizes the store supports.
// We use underscore prefix for 2X/3X because Java identifiers can't start with a digit.
public enum SizeOption {
    XS, S, M, L, XL, _2X, _3X;

    // Returns a human-readable label (e.g. "2X" instead of "_2X")
    public String displayValue() {
        return switch (this) {
            case _2X -> "2X";
            case _3X -> "3X";
            default -> name();
        };
    }
}
