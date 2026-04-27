package tokyoera.service;

import tokyoera.model.ClothingType;
import tokyoera.model.Product;
import org.junit.jupiter.api.*;

import java.sql.SQLException;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class ProductServiceIntegrationTest {

    private static TestDatabaseManager db;
    private static ProductService productService;

    @BeforeAll
    static void setUpDatabase() throws SQLException {
        db = new TestDatabaseManager("product_test_db");
        new DatabaseInitializer(db).initialize();
        productService = new ProductService(db);
    }

    @AfterAll
    static void tearDown() throws SQLException {
        db.shutdown();
    }

    private Product buildProduct(String name, double price, int stock) {
        return new Product(0, name, "A test product", price, stock,
                ClothingType.HOODIE, "black",
                "Keep going", "前進あるのみ",
                true, 20.0,
                "XS,S,M,L,XL", "100% cotton", "",
                "TestPrefix");
    }

    // ── create / read ─────────────────────────────────────────────────────

    @Test
    @org.junit.jupiter.api.Order(1)
    void getAll_emptyDatabase_returnsEmptyList() {
        List<Product> products = productService.getAll();
        assertTrue(products.isEmpty());
    }

    @Test
    @org.junit.jupiter.api.Order(2)
    void create_thenGetAll_containsProduct() {
        productService.create(buildProduct("Test Hoodie", 149.0, 30));
        List<Product> products = productService.getAll();
        assertEquals(1, products.size());
        assertEquals("Test Hoodie", products.get(0).getName());
    }

    @Test
    @org.junit.jupiter.api.Order(3)
    void create_productFieldsPersistedCorrectly() {
        productService.create(buildProduct("Precision Hoodie", 200.0, 15));
        List<Product> all = productService.getAll();
        Product saved = all.stream()
                .filter(p -> p.getName().equals("Precision Hoodie"))
                .findFirst()
                .orElseThrow();
        assertEquals(200.0, saved.getPrice(), 0.001);
        assertEquals(15, saved.getStock());
        assertEquals(ClothingType.HOODIE, saved.getClothingType());
        assertEquals("black", saved.getColor());
        assertEquals("Keep going", saved.getDefaultEnglishMessage());
        assertEquals("前進あるのみ", saved.getDefaultJapaneseMessage());
        assertTrue(saved.isCustomizationAllowed());
        assertEquals(20.0, saved.getCustomizationFee(), 0.001);
    }

    @Test
    @org.junit.jupiter.api.Order(4)
    void findById_existingProduct_returnsIt() {
        List<Product> all = productService.getAll();
        assertFalse(all.isEmpty());
        int id = all.get(0).getId();
        Optional<Product> found = productService.findById(id);
        assertTrue(found.isPresent());
        assertEquals(id, found.get().getId());
    }

    @Test
    @org.junit.jupiter.api.Order(5)
    void findById_nonexistentId_returnsEmpty() {
        Optional<Product> found = productService.findById(99999);
        assertTrue(found.isEmpty());
    }

    // ── update ────────────────────────────────────────────────────────────

    @Test
    @org.junit.jupiter.api.Order(6)
    void update_changesProductFields() {
        productService.create(buildProduct("Update Me", 50.0, 5));
        Product toUpdate = productService.getAll().stream()
                .filter(p -> p.getName().equals("Update Me"))
                .findFirst()
                .orElseThrow();

        toUpdate.setName("Updated Name");
        toUpdate.setPrice(75.0);
        toUpdate.setStock(20);
        productService.update(toUpdate);

        Product updated = productService.findById(toUpdate.getId()).orElseThrow();
        assertEquals("Updated Name", updated.getName());
        assertEquals(75.0, updated.getPrice(), 0.001);
        assertEquals(20, updated.getStock());
    }

    // ── delete ────────────────────────────────────────────────────────────

    @Test
    @org.junit.jupiter.api.Order(7)
    void delete_removesProduct() {
        productService.create(buildProduct("Delete Me", 30.0, 2));
        Product toDelete = productService.getAll().stream()
                .filter(p -> p.getName().equals("Delete Me"))
                .findFirst()
                .orElseThrow();

        productService.delete(toDelete.getId());
        Optional<Product> gone = productService.findById(toDelete.getId());
        assertTrue(gone.isEmpty());
    }

    // ── stock management ──────────────────────────────────────────────────

    @Test
    @org.junit.jupiter.api.Order(8)
    void hasEnoughStock_sufficientStock_returnsTrue() {
        productService.create(buildProduct("Stock Test", 50.0, 10));
        Product p = productService.getAll().stream()
                .filter(pr -> pr.getName().equals("Stock Test"))
                .findFirst().orElseThrow();
        assertTrue(productService.hasEnoughStock(p.getId(), 5));
        assertTrue(productService.hasEnoughStock(p.getId(), 10));
    }

    @Test
    @org.junit.jupiter.api.Order(9)
    void hasEnoughStock_insufficientStock_returnsFalse() {
        Product p = productService.getAll().stream()
                .filter(pr -> pr.getName().equals("Stock Test"))
                .findFirst().orElseThrow();
        assertFalse(productService.hasEnoughStock(p.getId(), 11));
    }

    @Test
    @org.junit.jupiter.api.Order(10)
    void reduceStock_decreasesStockByAmount() {
        Product p = productService.getAll().stream()
                .filter(pr -> pr.getName().equals("Stock Test"))
                .findFirst().orElseThrow();
        productService.reduceStock(p.getId(), 3);
        assertEquals(7, productService.findById(p.getId()).orElseThrow().getStock());
    }

    @Test
    @org.junit.jupiter.api.Order(11)
    void reduceStock_insufficientStock_throwsException() {
        Product p = productService.getAll().stream()
                .filter(pr -> pr.getName().equals("Stock Test"))
                .findFirst().orElseThrow();
        // Current stock = 7, trying to reduce by 100
        assertThrows(IllegalStateException.class,
                () -> productService.reduceStock(p.getId(), 100));
    }

    @Test
    @org.junit.jupiter.api.Order(12)
    void updateStock_setsExactValue() {
        Product p = productService.getAll().stream()
                .filter(pr -> pr.getName().equals("Stock Test"))
                .findFirst().orElseThrow();
        productService.updateStock(p.getId(), 50);
        assertEquals(50, productService.findById(p.getId()).orElseThrow().getStock());
    }

    @Test
    @org.junit.jupiter.api.Order(13)
    void updateStock_negativeValue_throwsException() {
        Product p = productService.getAll().stream()
                .filter(pr -> pr.getName().equals("Stock Test"))
                .findFirst().orElseThrow();
        assertThrows(IllegalArgumentException.class,
                () -> productService.updateStock(p.getId(), -1));
    }
}
