package tokyoera.service;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import tokyoera.model.SalesRecord;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class ReportServiceIntegrationTest {

    private static TestDatabaseManager db;
    private static ReportService reportService;

    @BeforeAll
    static void setUpDatabase() throws SQLException {
        db = new TestDatabaseManager("report_test_db");
        new DatabaseInitializer(db).initialize();
        reportService = new ReportService(db);
        seedReportData();
    }

    @AfterAll
    static void tearDown() throws SQLException {
        db.shutdown();
    }

    private static void seedReportData() throws SQLException {
        try (Connection connection = db.getConnection()) {
            insertUser(connection, 1, "report_user", "pass1234", "USER");
            insertProduct(connection, 1, "Tokyo Hoodie", "HOODIE", 10);
            insertProduct(connection, 2, "Tokyo Tee", "T_SHIRT", 0);

            insertOrder(connection, 1, 1, 120.0, "PAID", "2026-04-20 10:00:00");
            insertOrderItem(connection, 1, 1, "Tokyo Hoodie", "Hoodie", 2, 50.0, 10.0, 120.0);

            insertOrder(connection, 2, 1, 75.0, "PAID", "2026-04-21 11:00:00");
            insertOrderItem(connection, 2, 2, "Tokyo Tee", "T-shirt", 1, 75.0, 0.0, 75.0);

            insertOrder(connection, 3, 1, 40.0, "PENDING", "2026-04-21 12:00:00");
            insertOrderItem(connection, 3, 1, "Tokyo Hoodie", "Hoodie", 1, 40.0, 0.0, 40.0);
        }
    }

    private static void insertUser(Connection connection, int id, String username, String password, String role) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "INSERT INTO users(id, username, password, role) VALUES (?, ?, ?, ?)")) {
            statement.setInt(1, id);
            statement.setString(2, username);
            statement.setString(3, password);
            statement.setString(4, role);
            statement.executeUpdate();
        }
    }

    private static void insertProduct(Connection connection, int id, String name, String category, int stock) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "INSERT INTO products(id, name, description, price, stock, clothing_type, color, default_english_message, default_japanese_message, customization_allowed, customization_fee, sizes, material, includes_info, image_prefix) "
                        + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)")) {
            statement.setInt(1, id);
            statement.setString(2, name);
            statement.setString(3, "report seed");
            statement.setDouble(4, 50.0);
            statement.setInt(5, stock);
            statement.setString(6, category);
            statement.setString(7, "black");
            statement.setString(8, "Left");
            statement.setString(9, "Right");
            statement.setInt(10, 1);
            statement.setDouble(11, 10.0);
            statement.setString(12, "M,L");
            statement.setString(13, "Cotton");
            statement.setString(14, "");
            statement.setString(15, "ReportPrefix" + id);
            statement.executeUpdate();
        }
    }

    private static void insertOrder(Connection connection, int id, int userId, double total, String status, String createdAt) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "INSERT INTO orders(id, user_id, total_amount, status, created_at) VALUES (?, ?, ?, ?, ?)")) {
            statement.setInt(1, id);
            statement.setInt(2, userId);
            statement.setDouble(3, total);
            statement.setString(4, status);
            statement.setString(5, createdAt);
            statement.executeUpdate();
        }
    }

    private static void insertOrderItem(Connection connection, int orderId, int productId, String productName,
                                        String category, int quantity, double basePrice, double customizationFee,
                                        double subtotal) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "INSERT INTO order_items(order_id, product_id, product_name, category, size, quantity, base_price, customization_fee, customized, custom_english, custom_japanese, subtotal) "
                        + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)")) {
            statement.setInt(1, orderId);
            statement.setInt(2, productId);
            statement.setString(3, productName);
            statement.setString(4, category);
            statement.setString(5, "M");
            statement.setInt(6, quantity);
            statement.setDouble(7, basePrice);
            statement.setDouble(8, customizationFee);
            statement.setInt(9, customizationFee > 0 ? 1 : 0);
            statement.setString(10, customizationFee > 0 ? "Custom EN" : "Left");
            statement.setString(11, customizationFee > 0 ? "Custom JP" : "Right");
            statement.setDouble(12, subtotal);
            statement.executeUpdate();
        }
    }

    @Test
    void getSalesRecords_returnsOnlyPaidOrdersWithinFilter() {
        List<SalesRecord> records = reportService.getSalesRecords("2026-04-20", "2026-04-21", "All");

        assertEquals(2, records.size());
        assertEquals("2026-04-21", records.get(0).getDate());
        assertEquals("Tokyo Tee", records.get(0).getProductName());
        assertEquals("2026-04-20", records.get(1).getDate());
    }

    @Test
    void getSalesRecords_categoryFilter_returnsMatchingCategoryOnly() {
        List<SalesRecord> records = reportService.getSalesRecords("2026-04-20", "2026-04-21", "Hoodie");

        assertEquals(1, records.size());
        assertEquals("Hoodie", records.get(0).getCategory());
        assertEquals(120.0, records.get(0).getSalesAmount(), 0.001);
    }

    @Test
    void paidDateRangeAndDailyTotals_matchSeededPaidOrders() {
        assertArrayEquals(new String[] {"2026-04-20", "2026-04-21"}, reportService.getPaidSalesDateRange());

        Map<String, Double> dailyTotals = reportService.getDailyPaidTotals("2026-04-20", "2026-04-21");
        assertEquals(2, dailyTotals.size());
        assertEquals(120.0, dailyTotals.get("2026-04-20"), 0.001);
        assertEquals(75.0, dailyTotals.get("2026-04-21"), 0.001);
    }

    @Test
    void aggregatesAndDashboardStats_ignorePendingOrders() {
        Map<String, Object> aggregates = reportService.getOrderAggregatesByType("2026-04-20", "2026-04-21", "All");
        Map<String, Object> stats = reportService.getDashboardStatsByDate("2026-04-20", "2026-04-21", 2);

        assertEquals(195.0, (Double) aggregates.get("totalRevenue"), 0.001);
        assertEquals(2, aggregates.get("totalOrders"));
        assertEquals(120.0, (Double) aggregates.get("maxSale"), 0.001);
        assertEquals(75.0, (Double) aggregates.get("minSale"), 0.001);

        assertEquals(195.0, (Double) stats.get("totalRevenue"), 0.001);
        assertEquals(2, stats.get("totalOrders"));
        assertEquals(3, stats.get("qtySold"));
        assertEquals(0, stats.get("lowStockCount"));
        assertEquals(1, stats.get("outOfStockCount"));
    }

    @Test
    void exportToCsv_writesHeaderAndRows() throws IOException {
        Path csvFile = Files.createTempFile("tokyoera-report-", ".csv");
        try {
            List<SalesRecord> records = reportService.getSalesRecords("2026-04-20", "2026-04-21", "All");

            reportService.exportToCsv(records, csvFile.toString());

            List<String> lines = Files.readAllLines(csvFile);
            assertFalse(lines.isEmpty());
            assertEquals("Date,Product,Category,Quantity,Sales (RM)", lines.get(0));
            assertTrue(lines.stream().anyMatch(line -> line.contains("Tokyo Hoodie") && line.contains("120.00")));
            assertTrue(lines.stream().anyMatch(line -> line.contains("Tokyo Tee") && line.contains("75.00")));
        } finally {
            Files.deleteIfExists(csvFile);
        }
    }
}