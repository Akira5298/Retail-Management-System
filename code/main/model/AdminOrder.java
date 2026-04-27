package tokyoera.model;

// Represents one order as seen by the admin (read from the orders table with a JOIN on users).
// Used in the Admin dashboard's Orders table and also in the user's Order History dialog.
public class AdminOrder {
    private final int orderId;
    private final int userId;
    private final String username;     // who placed this order
    private final double totalAmount;  // order total in RM
    private String status;             // mutable: PENDING / PAID / CANCELLED
    private final String createdAt;    // stored as a formatted datetime string

    private final int itemCount;       // how many distinct order_items rows belong to this order

    public AdminOrder(int orderId, int userId, String username, double totalAmount,
                      String status, String createdAt, int itemCount) {
        this.orderId = orderId;
        this.userId = userId;
        this.username = username;
        this.totalAmount = totalAmount;
        this.status = status;
        this.createdAt = createdAt;
        this.itemCount = itemCount;
    }

    public int getItemCount() {
        return itemCount;
    }

    public int getOrderId() {
        return orderId;
    }

    public int getUserId() {
        return userId;
    }

    public String getUsername() {
        return username;
    }

    public double getTotalAmount() {
        return totalAmount;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getCreatedAt() {
        return createdAt;
    }
}
