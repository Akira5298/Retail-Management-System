package tokyoera.model;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

public class ProductModelTest {

    private Product makeProduct(String sizes) {
        return new Product(1, "Hoodie", "desc", 99.0, 10,
                ClothingType.HOODIE, "black",
                "Keep moving", "前進あるのみ",
                true, 20.0, sizes, "cotton", "", "Product1");
    }

    // ── getSizeList ────────────────────────────────────────────────────────

    @Test
    void getSizeList_normalCommaSeparated_parsesAll() {
        List<String> sizes = makeProduct("XS,S,M,L,XL").getSizeList();
        assertEquals(List.of("XS", "S", "M", "L", "XL"), sizes);
    }

    @Test
    void getSizeList_singleSize_returnsSingleEntry() {
        assertEquals(List.of("M"), makeProduct("M").getSizeList());
    }

    @Test
    void getSizeList_nullSizes_returnsEmptyList() {
        Product p = new Product();
        p.setSizes(null);
        assertEquals(List.of(), p.getSizeList());
    }

    @Test
    void getSizeList_blankSizes_returnsEmptyList() {
        assertEquals(List.of(), makeProduct("   ").getSizeList());
    }

    @Test
    void getSizeList_trailingComma_doesNotProduceBlankEntry() {
        // "XS,S,M," should give [XS, S, M] not [XS, S, M, ""]
        List<String> sizes = makeProduct("XS,S,M,").getSizeList();
        assertFalse(sizes.contains(""), "getSizeList should not contain empty strings");
        assertEquals(3, sizes.size());
    }

    @Test
    void getSizeList_doubleComma_doesNotProduceBlankEntry() {
        List<String> sizes = makeProduct("XS,,M").getSizeList();
        assertFalse(sizes.contains(""), "getSizeList should not contain empty strings from double comma");
        assertEquals(2, sizes.size());
    }

    @Test
    void getSizeList_spacesAroundEntries_areTrimmed() {
        List<String> sizes = makeProduct(" XS , S , M ").getSizeList();
        assertEquals(List.of("XS", "S", "M"), sizes);
    }

    // ── getImageNames ──────────────────────────────────────────────────────

    @Test
    void getImageNames_returnsTenEntries() {
        assertEquals(10, makeProduct("M").getImageNames().size());
    }

    @Test
    void getImageNames_suffixesAToJ() {
        List<String> names = makeProduct("M").getImageNames();
        assertEquals("Product1a", names.get(0));
        assertEquals("Product1b", names.get(1));
        assertEquals("Product1j", names.get(9));
    }

    @Test
    void getImageNames_differentPrefix_usesCorrectPrefix() {
        Product p = makeProduct("M");
        p.setImagePrefix("Tee2");
        List<String> names = p.getImageNames();
        assertTrue(names.stream().allMatch(n -> n.startsWith("Tee2")));
    }

    // ── getters / setters ─────────────────────────────────────────────────

    @Test
    void settersAndGetters_workCorrectly() {
        Product p = new Product();
        p.setId(5);
        p.setName("Test");
        p.setDescription("A description");
        p.setPrice(299.0);
        p.setStock(100);
        p.setClothingType(ClothingType.T_SHIRT);
        p.setColor("white");
        p.setDefaultEnglishMessage("Forward");
        p.setDefaultJapaneseMessage("前進");
        p.setCustomizationAllowed(true);
        p.setCustomizationFee(15.0);
        p.setSizes("S,M,L");
        p.setMaterial("polyester");
        p.setIncludesInfo("none");
        p.setImagePrefix("TestPfx");

        assertEquals(5, p.getId());
        assertEquals("Test", p.getName());
        assertEquals("A description", p.getDescription());
        assertEquals(299.0, p.getPrice(), 0.001);
        assertEquals(100, p.getStock());
        assertEquals(ClothingType.T_SHIRT, p.getClothingType());
        assertEquals("white", p.getColor());
        assertEquals("Forward", p.getDefaultEnglishMessage());
        assertEquals("前進", p.getDefaultJapaneseMessage());
        assertTrue(p.isCustomizationAllowed());
        assertEquals(15.0, p.getCustomizationFee(), 0.001);
        assertEquals("S,M,L", p.getSizes());
        assertEquals("polyester", p.getMaterial());
        assertEquals("none", p.getIncludesInfo());
        assertEquals("TestPfx", p.getImagePrefix());
    }

    // ── stock ─────────────────────────────────────────────────────────────

    @Test
    void setStock_updatesStock() {
        Product p = makeProduct("M");
        p.setStock(0);
        assertEquals(0, p.getStock());
    }
}
