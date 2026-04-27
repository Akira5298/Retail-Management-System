package tokyoera.model;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

public class CartItemTest {

    private Product product;

    @BeforeEach
    void setUp() {
        product = new Product(1, "Test Hoodie", "desc", 100.0, 50,
                ClothingType.HOODIE, "black",
                "Keep going", "前進あるのみ",
                true, 25.0,
                "XS,S,M,L,XL", "cotton", "", "Product1");
    }

    // ── unitPrice ──────────────────────────────────────────────────────────

    @Test
    void unitPrice_notCustomized_returnsBasePrice() {
        CartItem item = new CartItem(product, "M", 1, false, "", "");
        assertEquals(100.0, item.unitPrice(), 0.001);
    }

    @Test
    void unitPrice_customized_addsCustomizationFee() {
        CartItem item = new CartItem(product, "M", 1, true, "Custom EN", "Custom JP");
        assertEquals(125.0, item.unitPrice(), 0.001);
    }

    @Test
    void unitPrice_zeroFee_customizedEqualsBase() {
        Product noFeeProduct = new Product(2, "Tee", "desc", 80.0, 10,
                ClothingType.T_SHIRT, "white", "msg", "msg", true, 0.0,
                "S,M,L", "cotton", "", "Tee1");
        CartItem item = new CartItem(noFeeProduct, "S", 1, true, "A", "B");
        assertEquals(80.0, item.unitPrice(), 0.001);
    }

    // ── subtotal ───────────────────────────────────────────────────────────

    @Test
    void subtotal_singleQuantity_equalsUnitPrice() {
        CartItem item = new CartItem(product, "M", 1, false, "", "");
        assertEquals(100.0, item.subtotal(), 0.001);
    }

    @Test
    void subtotal_multipleQuantity_multiplied() {
        CartItem item = new CartItem(product, "M", 3, false, "", "");
        assertEquals(300.0, item.subtotal(), 0.001);
    }

    @Test
    void subtotal_customizedMultipleQuantity() {
        CartItem item = new CartItem(product, "M", 2, true, "A", "B");
        // (100 + 25) * 2 = 250
        assertEquals(250.0, item.subtotal(), 0.001);
    }

    @Test
    void subtotal_customizedIsGreaterThanNormal() {
        CartItem customized = new CartItem(product, "M", 2, true, "A", "B");
        CartItem normal = new CartItem(product, "M", 2, false, "", "");
        assertTrue(customized.subtotal() > normal.subtotal());
    }

    // ── customizationLabel ────────────────────────────────────────────────

    @Test
    void customizationLabel_notCustomized_returnsDefaultSleeves() {
        CartItem item = new CartItem(product, "M", 1, false, "", "");
        assertEquals("Default sleeves", item.customizationLabel());
    }

    @Test
    void customizationLabel_customized_showsBothMessages() {
        CartItem item = new CartItem(product, "M", 1, true, "Forward", "前進");
        assertEquals("EN: Forward | JP: 前進", item.customizationLabel());
    }

    @Test
    void customizationLabel_customized_emptyMessages_showsBlanks() {
        CartItem item = new CartItem(product, "M", 1, true, "", "");
        assertEquals("EN:  | JP: ", item.customizationLabel());
    }

    // ── quantity mutation ─────────────────────────────────────────────────

    @Test
    void setQuantity_updatesQuantity() {
        CartItem item = new CartItem(product, "M", 1, false, "", "");
        item.setQuantity(5);
        assertEquals(5, item.getQuantity());
        assertEquals(500.0, item.subtotal(), 0.001);
    }

    // ── getters ───────────────────────────────────────────────────────────

    @Test
    void getters_returnConstructorValues() {
        CartItem item = new CartItem(product, "L", 3, true, "EN text", "JP text");
        assertSame(product, item.getProduct());
        assertEquals("L", item.getSize());
        assertEquals(3, item.getQuantity());
        assertTrue(item.isCustomized());
        assertEquals("EN text", item.getCustomEnglish());
        assertEquals("JP text", item.getCustomJapanese());
    }
}
