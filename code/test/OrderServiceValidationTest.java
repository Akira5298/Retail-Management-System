package tokyoera.service;

import tokyoera.model.CartItem;
import tokyoera.model.ClothingType;
import tokyoera.model.Product;
import tokyoera.model.Role;
import tokyoera.model.User;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests validation guards in OrderService that throw before any DB access.
 * Passing null for DatabaseManager is intentional — these checks never reach DB.
 */
public class OrderServiceValidationTest {

    private final OrderService orderService = new OrderService(null, null);

    private User dummyUser() {
        return new User(1, "tester", "pass", Role.USER);
    }

    private Product dummyProduct() {
        return new Product(1, "Hoodie", "desc", 100.0, 50,
                ClothingType.HOODIE, "black", "EN", "JP",
                true, 25.0, "M", "cotton", "", "Product1");
    }

    // ── checkout – empty cart ─────────────────────────────────────────────

    @Test
    void checkout_emptyCart_throwsIllegalArgument() {
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> orderService.checkout(dummyUser(), List.of(), "CARD"));
        assertTrue(ex.getMessage().toLowerCase().contains("empty") ||
                   ex.getMessage().toLowerCase().contains("cart"),
                "Exception should mention empty cart");
    }

    // ── checkout – zero/negative quantity ─────────────────────────────────

    @Test
    void checkout_zeroQuantity_throwsIllegalArgument() {
        // CartItem validates quantity >= 1 in constructor now, so IllegalArgumentException
        // is thrown at CartItem construction time, not at checkout time
        assertThrows(IllegalArgumentException.class,
                () -> new CartItem(dummyProduct(), "M", 0, false, "", ""));
    }

    @Test
    void checkout_negativeQuantity_throwsIllegalArgument() {
        // CartItem validates quantity >= 1 in constructor now, so IllegalArgumentException
        // is thrown at CartItem construction time, not at checkout time
        assertThrows(IllegalArgumentException.class,
                () -> new CartItem(dummyProduct(), "M", -1, false, "", ""));
    }

    // ── updateOrderStatus – invalid statuses ──────────────────────────────

    @Test
    void updateOrderStatus_invalidStatus_throwsIllegalArgument() {
        assertThrows(IllegalArgumentException.class,
                () -> orderService.updateOrderStatus(1, "SHIPPED"));
    }

    @Test
    void updateOrderStatus_emptyStatus_throwsIllegalArgument() {
        assertThrows(IllegalArgumentException.class,
                () -> orderService.updateOrderStatus(1, ""));
    }

    @Test
    void updateOrderStatus_nullStatus_throwsIllegalArgument() {
        assertThrows(IllegalArgumentException.class,
                () -> orderService.updateOrderStatus(1, null));
    }

    @Test
    void updateOrderStatus_randomString_throwsIllegalArgument() {
        assertThrows(IllegalArgumentException.class,
                () -> orderService.updateOrderStatus(1, "DELIVERED"));
    }

    // ── updateOrderStatus – valid statuses should NOT throw ──────────────

    @Test
    void updateOrderStatus_paid_doesNotThrowValidationError() {
        // Will throw a DB error (null manager), but NOT an IllegalArgumentException
        // meaning the status itself is valid — the error is purely from DB being null
        Exception ex = assertThrows(Exception.class,
                () -> orderService.updateOrderStatus(1, "PAID"));
        assertFalse(ex instanceof IllegalArgumentException,
                "PAID is a valid status — should not cause IllegalArgumentException");
    }

    @Test
    void updateOrderStatus_pending_doesNotThrowValidationError() {
        Exception ex = assertThrows(Exception.class,
                () -> orderService.updateOrderStatus(1, "PENDING"));
        assertFalse(ex instanceof IllegalArgumentException,
                "PENDING is a valid status — should not cause IllegalArgumentException");
    }

    @Test
    void updateOrderStatus_cancelled_doesNotThrowValidationError() {
        Exception ex = assertThrows(Exception.class,
                () -> orderService.updateOrderStatus(1, "CANCELLED"));
        assertFalse(ex instanceof IllegalArgumentException,
                "CANCELLED is a valid status — should not cause IllegalArgumentException");
    }

    // ── saveCartAsOrder – invalid statuses ───────────────────────────────

    @Test
    void saveCartAsOrder_invalidStatus_throwsIllegalArgument() {
        CartItem item = new CartItem(dummyProduct(), "M", 1, false, "", "");
        assertThrows(IllegalArgumentException.class,
                () -> orderService.saveCartAsOrder(dummyUser(), List.of(item), "PAID"));
    }

    @Test
    void saveCartAsOrder_paidStatus_throwsIllegalArgument() {
        CartItem item = new CartItem(dummyProduct(), "M", 1, false, "", "");
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> orderService.saveCartAsOrder(dummyUser(), List.of(item), "PAID"));
        assertTrue(ex.getMessage().toLowerCase().contains("status") ||
                   ex.getMessage().toLowerCase().contains("unsupported"),
                "Exception should mention unsupported status");
    }

    @Test
    void saveCartAsOrder_emptyCart_returnsWithoutError() {
        // Empty cart is silently ignored
        assertDoesNotThrow(() -> orderService.saveCartAsOrder(dummyUser(), List.of(), "PENDING"));
    }

    @Test
    void saveCartAsOrder_nullCart_returnsWithoutError() {
        assertDoesNotThrow(() -> orderService.saveCartAsOrder(dummyUser(), null, "PENDING"));
    }
}
