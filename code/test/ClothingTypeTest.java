package tokyoera.model;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

public class ClothingTypeTest {

    // ── getLabel ──────────────────────────────────────────────────────────

    @Test
    void getLabel_hoodie_returnsHoodie() {
        assertEquals("Hoodie", ClothingType.HOODIE.getLabel());
    }

    @Test
    void getLabel_sweatshirt_returnsSweatshirt() {
        assertEquals("Sweatshirt", ClothingType.SWEATSHIRT.getLabel());
    }

    @Test
    void getLabel_tShirt_returnsTShirt() {
        assertEquals("T-shirt", ClothingType.T_SHIRT.getLabel());
    }

    // ── fromString – by enum name ─────────────────────────────────────────

    @Test
    void fromString_enumNameLowercase_resolves() {
        assertEquals(ClothingType.HOODIE, ClothingType.fromString("hoodie"));
    }

    @Test
    void fromString_enumNameUppercase_resolves() {
        assertEquals(ClothingType.SWEATSHIRT, ClothingType.fromString("SWEATSHIRT"));
    }

    @Test
    void fromString_tShirtEnumName_resolves() {
        assertEquals(ClothingType.T_SHIRT, ClothingType.fromString("T_SHIRT"));
    }

    // ── fromString – by label ─────────────────────────────────────────────

    @Test
    void fromString_label_hoodie() {
        assertEquals(ClothingType.HOODIE, ClothingType.fromString("Hoodie"));
    }

    @Test
    void fromString_label_sweatshirt() {
        assertEquals(ClothingType.SWEATSHIRT, ClothingType.fromString("Sweatshirt"));
    }

    @Test
    void fromString_label_tShirt() {
        assertEquals(ClothingType.T_SHIRT, ClothingType.fromString("T-shirt"));
    }

    @Test
    void fromString_label_caseInsensitive() {
        assertEquals(ClothingType.T_SHIRT, ClothingType.fromString("t-shirt"));
    }

    // ── fromString – unknown / null fallback ─────────────────────────────

    @Test
    void fromString_unknownInput_defaultsToHoodie() {
        assertEquals(ClothingType.HOODIE, ClothingType.fromString("unknownType"));
    }

    @Test
    void fromString_null_defaultsToHoodie() {
        // equalsIgnoreCase(null) returns false in Java — should fall through to HOODIE
        assertEquals(ClothingType.HOODIE, ClothingType.fromString(null));
    }

    @Test
    void fromString_emptyString_defaultsToHoodie() {
        assertEquals(ClothingType.HOODIE, ClothingType.fromString(""));
    }

    // ── enum completeness ─────────────────────────────────────────────────

    @Test
    void values_containsExactlyThreeTypes() {
        assertEquals(3, ClothingType.values().length);
    }

    @Test
    void allValues_haveNonBlankLabel() {
        for (ClothingType type : ClothingType.values()) {
            assertFalse(type.getLabel().isBlank(),
                    type + " should have a non-blank label");
        }
    }
}
