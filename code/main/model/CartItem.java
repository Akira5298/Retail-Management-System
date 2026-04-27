package tokyoera.model;

// Represents one line in the shopping cart — a product at a specific size and quantity.
// Customization text is stored here so custom and non-custom versions of the same product
// can be treated as separate cart entries.
public class CartItem {
    private final Product product;     // what was added
    private final String size;         // which size was selected (e.g. "M")
    private int quantity;              // mutable — user can increase/decrease in cart
    private final boolean customized;  // true if the buyer entered custom sleeve text
    private final String customEnglish;
    private final String customJapanese;

    public CartItem(Product product, String size, int quantity, boolean customized, String customEnglish, String customJapanese) {
        if (product == null) {
            throw new IllegalArgumentException("Product cannot be null");
        }
        if (size == null || size.isBlank()) {
            throw new IllegalArgumentException("Size cannot be null or blank");
        }
        if (quantity <= 0) {
            throw new IllegalArgumentException("Quantity must be greater than 0");
        }
        this.product = product;
        this.size = size;
        this.quantity = quantity;
        this.customized = customized;
        this.customEnglish = customEnglish;
        this.customJapanese = customJapanese;
    }

    public Product getProduct() {
        return product;
    }

    public String getSize() {
        return size;
    }

    public int getQuantity() {
        return quantity;
    }

    public void setQuantity(int quantity) {
        this.quantity = quantity;
    }

    public boolean isCustomized() {
        return customized;
    }

    public String getCustomEnglish() {
        return customEnglish;
    }

    public String getCustomJapanese() {
        return customJapanese;
    }

    // Unit price = base product price + optional customization fee
    public double unitPrice() {
        return product.getPrice() + (customized ? product.getCustomizationFee() : 0);
    }

    // Total cost for this line item (unit price × quantity)
    public double subtotal() {
        return unitPrice() * quantity;
    }

    // Returns a human-readable customization label shown in dialogs and receipts
    public String customizationLabel() {
        if (!customized) {
            return "Default sleeves";
        }
        String en = customEnglish == null ? "" : customEnglish;
        String jp = customJapanese == null ? "" : customJapanese;
        return "EN: " + en + " | JP: " + jp;
    }
}
