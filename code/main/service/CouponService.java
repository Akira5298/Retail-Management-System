package tokyoera.service;

import tokyoera.model.CartItem;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

// This service manages coupon codes — validating them, calculating discount amounts,
// and letting the admin create or delete them.
public class CouponService {
    private final DatabaseManager databaseManager;

    public CouponService(DatabaseManager databaseManager) {
        this.databaseManager = databaseManager;
    }

    /**
     * Validates a coupon code.
     *
     * @param code the coupon code to validate
     * @return empty Optional if valid, or a non-empty Optional containing the error message
     */
    public Optional<String> validate(String code) {
        return validate(code, List.of());
    }

    public Optional<String> validate(String code, List<CartItem> cartItems) {
        if (code == null || code.isBlank()) return Optional.of("Coupon code cannot be empty.");
        String sql = "SELECT discount_type, discount_value, expiry_date, active, applies_to_product_id FROM coupons WHERE code = ?";
        try (Connection conn = databaseManager.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, code.trim().toUpperCase());
            try (ResultSet rs = stmt.executeQuery()) {
                if (!rs.next()) return Optional.of("Invalid coupon code.");
                // Check if the coupon is still active
                if (rs.getInt("active") == 0) return Optional.of("This coupon is no longer active.");
                // Check if the coupon has expired
                String expiryDate = rs.getString("expiry_date");
                if (expiryDate != null && !expiryDate.isBlank()) {
                    try {
                        LocalDate expiry = LocalDate.parse(expiryDate);
                        if (LocalDate.now().isAfter(expiry)) return Optional.of("This coupon has expired.");
                    } catch (Exception ignored) {}
                }
                // If the coupon is for a specific product, make sure it's in the cart
                int appliesToProductId = rs.getInt("applies_to_product_id");
                if (!rs.wasNull() && cartItems != null && !cartItems.isEmpty()) {
                    boolean eligible = cartItems.stream()
                            .anyMatch(item -> item.getProduct().getId() == appliesToProductId);
                    if (!eligible) {
                        return Optional.of("This coupon is not applicable to items in your cart.");
                    }
                }
                return Optional.empty(); // all checks passed
            }
        } catch (SQLException exception) {
            throw new IllegalStateException("Failed to validate coupon", exception);
        }
    }

    /**
     * Calculates the discount amount for the given coupon applied to a subtotal.
     *
     * @param code     the coupon code (assumed valid)
     * @param subtotal the cart subtotal before discount
     * @return the discount amount (always ≥ 0 and ≤ subtotal)
     */
    public double getDiscountAmount(String code, double subtotal) {
        return getDiscountAmount(code, subtotal, List.of());
    }

    public double getDiscountAmount(String code, List<CartItem> cartItems) {
        if (cartItems == null || cartItems.isEmpty()) return 0;
        double subtotal = cartItems.stream().mapToDouble(CartItem::subtotal).sum();
        return getDiscountAmount(code, subtotal, cartItems);
    }

    private double getDiscountAmount(String code, double subtotal, List<CartItem> cartItems) {
        if (code == null || code.isBlank()) return 0;
        String sql = "SELECT discount_type, discount_value, applies_to_product_id FROM coupons WHERE code = ? AND active = 1";
        try (Connection conn = databaseManager.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, code.trim().toUpperCase());
            try (ResultSet rs = stmt.executeQuery()) {
                if (!rs.next()) return 0;
                String type = rs.getString("discount_type");
                double value = rs.getDouble("discount_value");
                int appliesToProductId = rs.getInt("applies_to_product_id");

                // If the coupon targets one product, only count that product's subtotal
                double eligibleSubtotal = subtotal;
                if (!rs.wasNull() && cartItems != null && !cartItems.isEmpty()) {
                    eligibleSubtotal = cartItems.stream()
                            .filter(item -> item.getProduct().getId() == appliesToProductId)
                            .mapToDouble(CartItem::subtotal)
                            .sum();
                }

                // Calculate: PERCENT = percentage off, anything else = fixed flat discount
                double discount = "PERCENT".equalsIgnoreCase(type)
                        ? eligibleSubtotal * value / 100.0
                        : value;
                return Math.min(discount, eligibleSubtotal); // can't discount more than the total
            }
        } catch (SQLException exception) {
            throw new IllegalStateException("Failed to compute coupon discount", exception);
        }
    }

    public List<CouponRow> getAllCoupons() {
        String sql = "SELECT code, discount_type, discount_value, expiry_date, active, applies_to_product_id FROM coupons ORDER BY code";
        List<CouponRow> rows = new ArrayList<>();
        try (Connection conn = databaseManager.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql);
             ResultSet rs = stmt.executeQuery()) {
            while (rs.next()) {
                Integer productId = rs.getObject("applies_to_product_id") == null ? null : rs.getInt("applies_to_product_id");
                rows.add(new CouponRow(
                        rs.getString("code"),
                        rs.getString("discount_type"),
                        rs.getDouble("discount_value"),
                        rs.getString("expiry_date"),
                        rs.getInt("active") == 1,
                        productId
                ));
            }
            return rows;
        } catch (SQLException exception) {
            throw new IllegalStateException("Failed to load coupons", exception);
        }
    }

    public void upsertPercentCoupon(String code, double percent, Integer appliesToProductId, String expiryDate, boolean active) {
        String sql = """
                INSERT INTO coupons(code, discount_type, discount_value, expiry_date, active, applies_to_product_id)
                VALUES (?, 'PERCENT', ?, ?, ?, ?)
                ON CONFLICT(code) DO UPDATE SET
                    discount_type=excluded.discount_type,
                    discount_value=excluded.discount_value,
                    expiry_date=excluded.expiry_date,
                    active=excluded.active,
                    applies_to_product_id=excluded.applies_to_product_id
                """;
        try (Connection conn = databaseManager.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, code.trim().toUpperCase());
            stmt.setDouble(2, percent);
            stmt.setString(3, expiryDate == null || expiryDate.isBlank() ? null : expiryDate);
            stmt.setInt(4, active ? 1 : 0);
            if (appliesToProductId == null) {
                stmt.setNull(5, java.sql.Types.INTEGER);
            } else {
                stmt.setInt(5, appliesToProductId);
            }
            stmt.executeUpdate();
        } catch (SQLException exception) {
            throw new IllegalStateException("Failed to save coupon", exception);
        }
    }

    public void deleteCoupon(String code) {
        String sql = "DELETE FROM coupons WHERE code = ?";
        try (Connection conn = databaseManager.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, code.trim().toUpperCase());
            stmt.executeUpdate();
        } catch (SQLException exception) {
            throw new IllegalStateException("Failed to delete coupon", exception);
        }
    }

    public static class CouponRow {
        private final String code;
        private final String discountType;
        private final double discountValue;
        private final String expiryDate;
        private final boolean active;
        private final Integer appliesToProductId;

        public CouponRow(String code, String discountType, double discountValue, String expiryDate, boolean active, Integer appliesToProductId) {
            this.code = code;
            this.discountType = discountType;
            this.discountValue = discountValue;
            this.expiryDate = expiryDate;
            this.active = active;
            this.appliesToProductId = appliesToProductId;
        }

        public String getCode() { return code; }
        public String getDiscountType() { return discountType; }
        public double getDiscountValue() { return discountValue; }
        public String getExpiryDate() { return expiryDate; }
        public boolean isActive() { return active; }
        public Integer getAppliesToProductId() { return appliesToProductId; }
    }
}
