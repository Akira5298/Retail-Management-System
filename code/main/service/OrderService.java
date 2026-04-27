package tokyoera.service;

import tokyoera.model.AdminOrder;
import tokyoera.model.CartItem;
import tokyoera.model.CustomOrderItem;
import tokyoera.model.OrderReceipt;
import tokyoera.model.User;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

// This service handles the full order lifecycle:
// checking out, saving pending/cancelled orders, querying order history, and deleting orders.
public class OrderService {
    private static final String STATUS_PENDING = "PENDING";
    private static final String STATUS_PAID = "PAID";
    private static final String STATUS_CANCELLED = "CANCELLED";

    private final DatabaseManager databaseManager;
    private final ProductService productService;

    public OrderService(DatabaseManager databaseManager, ProductService productService) {
        this.databaseManager = databaseManager;
        this.productService = productService;
    }

    public OrderReceipt checkout(User user, List<CartItem> cartItems, String paymentMethod) {
        return checkout(user, cartItems, paymentMethod, null, 0.0);
    }

    public OrderReceipt checkout(User user, List<CartItem> cartItems, String paymentMethod, String couponCode, double discountAmount) {
        if (cartItems.isEmpty()) {
            throw new IllegalArgumentException("Cannot pay with empty cart");
        }

        // ── Pre-checkout stock validation ──────────────────────────────────────
        // Check every item in the cart has enough stock for its size before we touch the DB
        for (CartItem item : cartItems) {
            if (item.getQuantity() <= 0) {
                throw new IllegalArgumentException("Quantity must be at least 1");
            }
            if (!productService.hasEnoughStockForSize(item.getProduct().getId(), item.getSize(), item.getQuantity())) {
                throw new IllegalArgumentException("Stock exceeded for " + item.getProduct().getName());
            }
        }

        String insertOrder = "INSERT INTO orders(user_id, total_amount, status, coupon_code, discount_amount) VALUES (?, ?, ?, ?, ?)";
        String insertItem = """
                INSERT INTO order_items(order_id, product_id, product_name, category, size, quantity, base_price,
                customization_fee, customized, custom_english, custom_japanese, subtotal)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """;
        String insertTransaction = "INSERT INTO transactions(order_id, amount, payment_method) VALUES (?, ?, ?)";

        double cartTotal = cartItems.stream().mapToDouble(CartItem::subtotal).sum();
        double finalTotal = Math.max(0, cartTotal - discountAmount); // apply coupon discount

        // ── Database transaction ───────────────────────────────────────────────
        // Everything runs in a single transaction so if anything fails, nothing gets saved
        try (Connection connection = databaseManager.getConnection()) {
            connection.setAutoCommit(false);
            try (PreparedStatement orderStatement = connection.prepareStatement(insertOrder, PreparedStatement.RETURN_GENERATED_KEYS)) {
                // Step 1: Insert the order header
                orderStatement.setInt(1, user.getId());
                orderStatement.setDouble(2, finalTotal);
                orderStatement.setString(3, STATUS_PAID);
                orderStatement.setString(4, couponCode);
                orderStatement.setDouble(5, discountAmount);
                orderStatement.executeUpdate();

                int orderId;
                try (ResultSet generatedKeys = orderStatement.getGeneratedKeys()) {
                    if (!generatedKeys.next()) {
                        throw new IllegalStateException("Failed to generate order id");
                    }
                    orderId = generatedKeys.getInt(1);
                }

                try (PreparedStatement itemStatement = connection.prepareStatement(insertItem)) {
                    // Step 2: Insert each line item
                    for (CartItem item : cartItems) {
                        itemStatement.setInt(1, orderId);
                        itemStatement.setInt(2, item.getProduct().getId());
                        itemStatement.setString(3, item.getProduct().getName());
                        itemStatement.setString(4, item.getProduct().getClothingType().getLabel());
                        itemStatement.setString(5, item.getSize());
                        itemStatement.setInt(6, item.getQuantity());
                        itemStatement.setDouble(7, item.getProduct().getPrice());
                        itemStatement.setDouble(8, item.isCustomized() ? item.getProduct().getCustomizationFee() : 0);
                        itemStatement.setInt(9, item.isCustomized() ? 1 : 0);
                        itemStatement.setString(10, item.isCustomized() ? item.getCustomEnglish() : item.getProduct().getDefaultEnglishMessage());
                        itemStatement.setString(11, item.isCustomized() ? item.getCustomJapanese() : item.getProduct().getDefaultJapaneseMessage());
                        itemStatement.setDouble(12, item.subtotal());
                        itemStatement.addBatch();
                    }
                    itemStatement.executeBatch();
                    // Step 3: Deduct stock per size (inside the same transaction for atomicity)
                    for (CartItem item : cartItems) {
                        productService.consumeStockForSize(connection, item.getProduct().getId(), item.getSize(), item.getQuantity());
                    }
                }

                // Step 4: Record the payment transaction
                try (PreparedStatement transactionStatement = connection.prepareStatement(insertTransaction)) {
                    transactionStatement.setInt(1, orderId);
                    transactionStatement.setDouble(2, finalTotal);
                    transactionStatement.setString(3, paymentMethod);
                    transactionStatement.executeUpdate();
                }

                connection.commit();
                return new OrderReceipt(orderId, LocalDateTime.now(), cartItems, finalTotal);
            } catch (Exception exception) {
                connection.rollback(); // undo everything if any step failed
                throw exception;
            } finally {
                connection.setAutoCommit(true);
            }
        } catch (SQLException exception) {
            throw new IllegalStateException("Checkout transaction failed", exception);
        }
    }

    // Saves the current cart as a PENDING or CANCELLED order (without charging or deducting stock)
    public void saveCartAsOrder(User user, List<CartItem> cartItems, String status) {
        if (cartItems == null || cartItems.isEmpty()) {
            return;
        }
        if (!STATUS_PENDING.equals(status) && !STATUS_CANCELLED.equals(status)) {
            throw new IllegalArgumentException("Unsupported status: " + status);
        }

        String insertOrder = "INSERT INTO orders(user_id, total_amount, status) VALUES (?, ?, ?)";
        String insertItem = """
                INSERT INTO order_items(order_id, product_id, product_name, category, size, quantity, base_price,
                customization_fee, customized, custom_english, custom_japanese, subtotal)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """;

        double total = cartItems.stream().mapToDouble(CartItem::subtotal).sum();

        try (Connection connection = databaseManager.getConnection()) {
            connection.setAutoCommit(false);
            try (PreparedStatement orderStatement = connection.prepareStatement(insertOrder, PreparedStatement.RETURN_GENERATED_KEYS);
                 PreparedStatement itemStatement = connection.prepareStatement(insertItem)) {
                orderStatement.setInt(1, user.getId());
                orderStatement.setDouble(2, total);
                orderStatement.setString(3, status);
                orderStatement.executeUpdate();

                int orderId;
                try (ResultSet generatedKeys = orderStatement.getGeneratedKeys()) {
                    if (!generatedKeys.next()) {
                        throw new IllegalStateException("Failed to generate order id");
                    }
                    orderId = generatedKeys.getInt(1);
                }

                for (CartItem item : cartItems) {
                    itemStatement.setInt(1, orderId);
                    itemStatement.setInt(2, item.getProduct().getId());
                    itemStatement.setString(3, item.getProduct().getName());
                    itemStatement.setString(4, item.getProduct().getClothingType().getLabel());
                    itemStatement.setString(5, item.getSize());
                    itemStatement.setInt(6, item.getQuantity());
                    itemStatement.setDouble(7, item.getProduct().getPrice());
                    itemStatement.setDouble(8, item.isCustomized() ? item.getProduct().getCustomizationFee() : 0);
                    itemStatement.setInt(9, item.isCustomized() ? 1 : 0);
                    itemStatement.setString(10, item.isCustomized() ? item.getCustomEnglish() : item.getProduct().getDefaultEnglishMessage());
                    itemStatement.setString(11, item.isCustomized() ? item.getCustomJapanese() : item.getProduct().getDefaultJapaneseMessage());
                    itemStatement.setDouble(12, item.subtotal());
                    itemStatement.addBatch();
                }
                itemStatement.executeBatch();
                connection.commit();
            } catch (Exception exception) {
                connection.rollback();
                throw exception;
            } finally {
                connection.setAutoCommit(true);
            }
        } catch (SQLException exception) {
            throw new IllegalStateException("Failed to save cart as " + status, exception);
        }
    }

    // Returns all orders placed by a specific user, newest first
    public List<AdminOrder> getOrdersByUser(int userId) {
        String sql = "SELECT o.id, o.user_id, u.username, o.total_amount, o.status, o.created_at, " +
                 "COALESCE((SELECT SUM(quantity) FROM order_items WHERE order_id = o.id), 0) as item_count " +
                 "FROM orders o JOIN users u ON u.id = o.user_id " +
                 "WHERE o.user_id = ? ORDER BY o.id DESC";
        List<AdminOrder> result = new ArrayList<>();
        try (Connection connection = databaseManager.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setInt(1, userId);
            try (ResultSet resultSet = statement.executeQuery()) {
                while (resultSet.next()) {
                    result.add(new AdminOrder(
                            resultSet.getInt("id"),
                            resultSet.getInt("user_id"),
                            resultSet.getString("username"),
                            resultSet.getDouble("total_amount"),
                            resultSet.getString("status"),
                                resultSet.getString("created_at"),
                                resultSet.getInt("item_count")
                    ));
                }
            }
        } catch (SQLException exception) {
            throw new IllegalStateException("Failed to load user orders", exception);
        }
        return result;
    }

    // Returns all orders in the system; can optionally filter by status (PENDING/PAID/CANCELLED)
    public List<AdminOrder> getAllOrders(String statusFilter) {
        StringBuilder sql = new StringBuilder(
                    "SELECT o.id, o.user_id, u.username, o.total_amount, o.status, o.created_at, " +
                    "COALESCE((SELECT SUM(quantity) FROM order_items WHERE order_id = o.id), 0) as item_count " +
                    "FROM orders o JOIN users u ON u.id = o.user_id");
        if (statusFilter != null && !statusFilter.isBlank() && !"All".equalsIgnoreCase(statusFilter)) {
            sql.append(" WHERE o.status = ?");
        }
        sql.append(" ORDER BY o.id DESC");

        List<AdminOrder> result = new ArrayList<>();
        try (Connection connection = databaseManager.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql.toString())) {
            if (statusFilter != null && !statusFilter.isBlank() && !"All".equalsIgnoreCase(statusFilter)) {
                statement.setString(1, statusFilter);
            }
            try (ResultSet resultSet = statement.executeQuery()) {
                while (resultSet.next()) {
                    result.add(new AdminOrder(
                            resultSet.getInt("id"),
                            resultSet.getInt("user_id"),
                            resultSet.getString("username"),
                            resultSet.getDouble("total_amount"),
                            resultSet.getString("status"),
                                resultSet.getString("created_at"),
                                resultSet.getInt("item_count")
                    ));
                }
            }
        } catch (SQLException exception) {
            throw new IllegalStateException("Failed to load orders", exception);
        }
        return result;
    }

    // Admin can change an order's status — only the three known statuses are allowed
    public void updateOrderStatus(int orderId, String status) {
        if (!STATUS_PENDING.equals(status) && !STATUS_PAID.equals(status) && !STATUS_CANCELLED.equals(status)) {
            throw new IllegalArgumentException("Invalid status. Allowed: PENDING, PAID, CANCELLED");
        }
        String sql = "UPDATE orders SET status = ? WHERE id = ?";
        try (Connection connection = databaseManager.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, status);
            statement.setInt(2, orderId);
            statement.executeUpdate();
        } catch (SQLException exception) {
            throw new IllegalStateException("Failed to update order status", exception);
        }
    }

    // Deletes an order and all its related items and transaction records from the database
    // Uses a transaction so either all three deletes succeed or none do
    public void deleteOrder(int orderId) {
        try (Connection connection = databaseManager.getConnection()) {
            connection.setAutoCommit(false);
            try {
                try (PreparedStatement ps = connection.prepareStatement(
                        "DELETE FROM order_items WHERE order_id = ?")) {
                    ps.setInt(1, orderId);
                    ps.executeUpdate();
                }
                try (PreparedStatement ps = connection.prepareStatement(
                        "DELETE FROM transactions WHERE order_id = ?")) {
                    ps.setInt(1, orderId);
                    ps.executeUpdate();
                }
                try (PreparedStatement ps = connection.prepareStatement(
                        "DELETE FROM orders WHERE id = ?")) {
                    ps.setInt(1, orderId);
                    ps.executeUpdate();
                }
                connection.commit();
            } catch (Exception exception) {
                connection.rollback();
                throw exception;
            } finally {
                connection.setAutoCommit(true);
            }
        } catch (SQLException exception) {
            throw new IllegalStateException("Failed to delete order", exception);
        }
    }

    public List<CustomOrderItem> getCustomOrders() {
        String sql = """
                SELECT oi.order_id, oi.product_name, u.username,
                       oi.custom_english, oi.custom_japanese, oi.customization_fee,
                       oi.size, oi.quantity
                FROM order_items oi
                JOIN orders o ON o.id = oi.order_id
                JOIN users u ON u.id = o.user_id
                WHERE oi.customized = 1
                ORDER BY oi.order_id DESC
                """;
        List<CustomOrderItem> result = new ArrayList<>();
        try (Connection connection = databaseManager.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql);
             ResultSet resultSet = statement.executeQuery()) {
            while (resultSet.next()) {
                result.add(new CustomOrderItem(
                        resultSet.getInt("order_id"),
                        resultSet.getString("product_name"),
                        resultSet.getString("username"),
                        resultSet.getString("custom_english"),
                        resultSet.getString("custom_japanese"),
                        resultSet.getDouble("customization_fee"),
                        resultSet.getString("size"),
                        resultSet.getInt("quantity")
                ));
            }
        } catch (SQLException exception) {
            throw new IllegalStateException("Failed to load custom orders", exception);
        }
        return result;
    }
}
