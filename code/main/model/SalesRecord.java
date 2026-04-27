package tokyoera.model;

// A single row of aggregated sales data used in the admin reporting table and CSV export.
// Each record groups quantity and revenue by date + product + category.
public class SalesRecord {
    private final String date;          // sales date (yyyy-MM-dd)
    private final String productName;
    private final String category;      // clothing type label (Hoodie / Sweatshirt / T-Shirt)
    private final int quantity;         // total units sold on that date
    private final double salesAmount;   // total revenue for that group in RM

    public SalesRecord(String date, String productName, String category, int quantity, double salesAmount) {
        this.date = date;
        this.productName = productName;
        this.category = category;
        this.quantity = quantity;
        this.salesAmount = salesAmount;
    }

    public String getDate() {
        return date;
    }

    public String getProductName() {
        return productName;
    }

    public String getCategory() {
        return category;
    }

    public int getQuantity() {
        return quantity;
    }

    public double getSalesAmount() {
        return salesAmount;
    }
}
