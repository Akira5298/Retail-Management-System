package tokyoera.service;

import tokyoera.model.CartItem;
import tokyoera.model.ClothingType;
import tokyoera.model.Product;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

public class PricingCalculationTest {

    @Test
    void subtotalIncludesCustomizationFee() {
        Product product = new Product(
                1,
                "Sample",
                "Sample",
                199,
                3,
                ClothingType.HOODIE,
                "black",
                "Keep moving forward",
                "前進あるのみ",
                true,
                20,
                "XS,S,M,L,XL,2X,3X,4X,5X",
                "cotton",
                "Designed by Akira Fukutomi",
                "Product1"
        );

        CartItem customized = new CartItem(product, "M", 2, true, "A", "B");
        CartItem normal = new CartItem(product, "M", 2, false, "", "");

        Assertions.assertEquals(438.0, customized.subtotal(), 0.001);
        Assertions.assertEquals(398.0, normal.subtotal(), 0.001);
        assert customized.subtotal() > normal.subtotal();
    }
}
