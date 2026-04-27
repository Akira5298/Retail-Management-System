package tokyoera.model;

// Represents an order item that had a custom sleeve message — used in the admin's
// "Custom Orders" tab so the admin can see exactly what text to print on each item.
public class CustomOrderItem {
    private final int orderId;
    private final String productName;
    private final String customerUsername; // who ordered it
    private final String customEnglish;    // English sleeve text the buyer entered
    private final String customJapanese;   // Japanese sleeve text the buyer entered
    private final double customizationFee;
    private final String size;
    private final int quantity;

    public CustomOrderItem(int orderId, String productName, String customerUsername,
                           String customEnglish, String customJapanese,
                           double customizationFee, String size, int quantity) {
        this.orderId = orderId;
        this.productName = productName;
        this.customerUsername = customerUsername;
        this.customEnglish = customEnglish;
        this.customJapanese = customJapanese;
        this.customizationFee = customizationFee;
        this.size = size;
        this.quantity = quantity;
    }

    public int getOrderId() {
        return orderId;
    }

    public String getProductName() {
        return productName;
    }

    public String getCustomerUsername() {
        return customerUsername;
    }

    public String getCustomEnglish() {
        return customEnglish;
    }

    public String getCustomJapanese() {
        return customJapanese;
    }

    public double getCustomizationFee() {
        return customizationFee;
    }

    public String getSize() {
        return size;
    }

    public int getQuantity() {
        return quantity;
    }
}
