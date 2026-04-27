package tokyoera.model;

// Represents the type of clothing sold (e.g. Hoodie, Sweatshirt, T-Shirt).
// Each type carries a base production cost in RM that feeds into profit calculations.
public enum ClothingType {
    HOODIE("Hoodie", 230.0),
    SWEATSHIRT("Sweatshirt", 200.0),
    T_SHIRT("T-shirt", 160.0);

    private final String label;       // friendly display name
    private final double costPrice;   // what it costs to produce (RM, shipping excluded)

    ClothingType(String label, double costPrice) {
        this.label = label;
        this.costPrice = costPrice;
    }

    public String getLabel() {
        return label;
    }

    /** Manufacturing/production cost in RM (shipping excluded). */
    public double getCostPrice() {
        return costPrice;
    }

    // Parses a string to a ClothingType — accepts both enum name and display label.
    // Falls back to HOODIE if the string doesn't match anything.
    public static ClothingType fromString(String input) {
        for (ClothingType type : values()) {
            if (type.name().equalsIgnoreCase(input) || type.label.equalsIgnoreCase(input)) {
                return type;
            }
        }
        return HOODIE;
    }
}
