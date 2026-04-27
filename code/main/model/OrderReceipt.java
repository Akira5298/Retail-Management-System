package tokyoera.model;

import java.time.LocalDateTime;
import java.util.List;

// A simple read-only snapshot of a completed order.
// Passed to PaymentSuccessDialog, InvoiceDialog, and PdfService so they all show the same data.
public class OrderReceipt {
    private final int orderId;
    private final LocalDateTime createdAt; // when the order was placed
    private final List<CartItem> items;    // the items that were purchased
    private final double total;            // grand total in RM

    public OrderReceipt(int orderId, LocalDateTime createdAt, List<CartItem> items, double total) {
        this.orderId = orderId;
        this.createdAt = createdAt;
        this.items = items;
        this.total = total;
    }

    public int getOrderId() {
        return orderId;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public List<CartItem> getItems() {
        return items;
    }

    public double getTotal() {
        return total;
    }
}
