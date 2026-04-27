package tokyoera.service;

import tokyoera.model.CartItem;
import tokyoera.model.ClothingType;
import tokyoera.model.Product;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

public class CartServiceTest {

    private CartService cartService;
    private Product productA;
    private Product productB;

    @BeforeEach
    void setUp() {
        cartService = new CartService();
        productA = new Product(1, "Hoodie", "desc", 100.0, 50,
                ClothingType.HOODIE, "black", "Forward", "前進",
                true, 25.0, "S,M,L", "cotton", "", "Product1");
        productB = new Product(2, "T-Shirt", "desc", 60.0, 20,
                ClothingType.T_SHIRT, "white", "Rise", "上昇",
                false, 0.0, "S,M,L,XL", "polyester", "", "Product2");
    }

    // ── addToCart – new items ─────────────────────────────────────────────

    @Test
    void addToCart_newProduct_addsToList() {
        cartService.addToCart(productA, "M", 1, false, "", "");
        assertEquals(1, cartService.getItems().size());
    }

    @Test
    void addToCart_twoDifferentProducts_addsBoth() {
        cartService.addToCart(productA, "M", 1, false, "", "");
        cartService.addToCart(productB, "L", 2, false, "", "");
        assertEquals(2, cartService.getItems().size());
    }

    @Test
    void addToCart_differentSizeSameProduct_addsSeparateEntry() {
        cartService.addToCart(productA, "M", 1, false, "", "");
        cartService.addToCart(productA, "L", 1, false, "", "");
        assertEquals(2, cartService.getItems().size());
    }

    @Test
    void addToCart_differentCustomization_addsSeparateEntry() {
        cartService.addToCart(productA, "M", 1, false, "", "");
        cartService.addToCart(productA, "M", 1, true, "EN", "JP");
        assertEquals(2, cartService.getItems().size());
    }

    @Test
    void addToCart_differentCustomEnglish_addsSeparateEntry() {
        cartService.addToCart(productA, "M", 1, true, "Text A", "JP");
        cartService.addToCart(productA, "M", 1, true, "Text B", "JP");
        assertEquals(2, cartService.getItems().size());
    }

    // ── addToCart – merging duplicates ────────────────────────────────────

    @Test
    void addToCart_duplicateItem_mergesQuantity() {
        cartService.addToCart(productA, "M", 2, false, "", "");
        cartService.addToCart(productA, "M", 3, false, "", "");
        assertEquals(1, cartService.getItems().size());
        assertEquals(5, cartService.getItems().get(0).getQuantity());
    }

    @Test
    void addToCart_duplicateCustomizedItem_mergesQuantity() {
        cartService.addToCart(productA, "M", 1, true, "EN", "JP");
        cartService.addToCart(productA, "M", 2, true, "EN", "JP");
        assertEquals(1, cartService.getItems().size());
        assertEquals(3, cartService.getItems().get(0).getQuantity());
    }

    // ── remove ────────────────────────────────────────────────────────────

    @Test
    void remove_existingItem_removesIt() {
        cartService.addToCart(productA, "M", 1, false, "", "");
        CartItem item = cartService.getItems().get(0);
        cartService.remove(item);
        assertTrue(cartService.getItems().isEmpty());
    }

    @Test
    void remove_oneOfTwoItems_leavesOther() {
        cartService.addToCart(productA, "M", 1, false, "", "");
        cartService.addToCart(productB, "L", 1, false, "", "");
        CartItem first = cartService.getItems().get(0);
        cartService.remove(first);
        assertEquals(1, cartService.getItems().size());
    }

    // ── clear ─────────────────────────────────────────────────────────────

    @Test
    void clear_removesAllItems() {
        cartService.addToCart(productA, "M", 2, false, "", "");
        cartService.addToCart(productB, "L", 1, false, "", "");
        cartService.clear();
        assertTrue(cartService.getItems().isEmpty());
    }

    @Test
    void clear_emptyCart_staysEmpty() {
        cartService.clear();
        assertTrue(cartService.getItems().isEmpty());
    }

    // ── itemCount ─────────────────────────────────────────────────────────

    @Test
    void itemCount_sumsTotalQuantities() {
        cartService.addToCart(productA, "M", 2, false, "", "");
        cartService.addToCart(productB, "L", 3, false, "", "");
        assertEquals(5, cartService.itemCount());
    }

    @Test
    void itemCount_emptyCart_isZero() {
        assertEquals(0, cartService.itemCount());
    }

    // ── total ─────────────────────────────────────────────────────────────

    @Test
    void total_sumsTotalSubtotals() {
        // productA 100 * 2 = 200, productB 60 * 1 = 60 → 260
        cartService.addToCart(productA, "M", 2, false, "", "");
        cartService.addToCart(productB, "L", 1, false, "", "");
        assertEquals(260.0, cartService.total(), 0.001);
    }

    @Test
    void total_withCustomization_includesFees() {
        // (100 + 25) * 1 = 125
        cartService.addToCart(productA, "M", 1, true, "EN", "JP");
        assertEquals(125.0, cartService.total(), 0.001);
    }

    @Test
    void total_emptyCart_isZero() {
        assertEquals(0.0, cartService.total(), 0.001);
    }

    // ── snapshot ──────────────────────────────────────────────────────────

    @Test
    void snapshot_returnsImmutableCopy() {
        cartService.addToCart(productA, "M", 1, false, "", "");
        List<CartItem> snap = cartService.snapshot();
        assertEquals(1, snap.size());
        assertThrows(UnsupportedOperationException.class,
                () -> snap.add(new CartItem(productB, "S", 1, false, "", "")));
    }

    @Test
    void snapshot_doesNotReflectSubsequentChanges() {
        cartService.addToCart(productA, "M", 1, false, "", "");
        List<CartItem> snap = cartService.snapshot();
        cartService.clear();
        assertEquals(1, snap.size(), "Snapshot should be independent of later cart changes");
    }
}
