package tokyoera.service;

import tokyoera.model.SalesRecord;

import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

// This service provides all the sales analytics for the admin dashboard.
// It queries the orders and order_items tables and returns data ready to display in charts and tables.
public class ReportService {
    private final DatabaseManager databaseManager;

    public ReportService(DatabaseManager databaseManager) {
        this.databaseManager = databaseManager;
    }

    // Returns a list of sales records grouped by date and product.
    // Supports filtering by date range and clothing category.
    public List<SalesRecord> getSalesRecords(String startDate, String endDate, String category) {
        StringBuilder sql = new StringBuilder("""
                SELECT DATE(o.created_at) as sales_date, oi.product_name, oi.category,
                       SUM(oi.quantity) as qty, SUM(oi.subtotal) as amount
                FROM orders o
                JOIN order_items oi ON oi.order_id = o.id
                WHERE o.status = 'PAID'
                """);

        List<Object> params = new ArrayList<>();

        if (startDate != null && !startDate.isBlank()) {
            sql.append(" AND DATE(o.created_at) >= DATE(?)");
            params.add(startDate);
        }
        if (endDate != null && !endDate.isBlank()) {
            sql.append(" AND DATE(o.created_at) <= DATE(?)");
            params.add(endDate);
        }
        if (category != null && !category.isBlank() && !"All".equalsIgnoreCase(category)) {
            sql.append(" AND oi.category = ?");
            params.add(category);
        }

        sql.append(" GROUP BY DATE(o.created_at), oi.product_name, oi.category ORDER BY sales_date DESC");

        List<SalesRecord> records = new ArrayList<>();
        try (Connection connection = databaseManager.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql.toString())) {
            for (int index = 0; index < params.size(); index++) {
                statement.setObject(index + 1, params.get(index));
            }

            try (ResultSet resultSet = statement.executeQuery()) {
                while (resultSet.next()) {
                    records.add(new SalesRecord(
                            resultSet.getString("sales_date"),
                            resultSet.getString("product_name"),
                            resultSet.getString("category"),
                            resultSet.getInt("qty"),
                            resultSet.getDouble("amount")
                    ));
                }
            }
            return records;
        } catch (SQLException exception) {
            throw new IllegalStateException("Failed to build sales records", exception);
        }
    }

    // Returns daily total revenue for PAID orders, used to draw the sales trend chart.
    // The date handling in the subquery normalises timestamps stored in different formats.
    public Map<String, Double> getDailyPaidTotals(String startDate, String endDate) {
        StringBuilder sql = new StringBuilder("""
                SELECT day_key, COALESCE(SUM(total_amount), 0) as total
                FROM (
                    SELECT
                        CASE
                            WHEN DATE(created_at) IS NOT NULL THEN DATE(created_at)
                            WHEN created_at IS NOT NULL AND LENGTH(created_at) >= 10 THEN SUBSTR(created_at, 1, 10)
                            ELSE NULL
                        END as day_key,
                        total_amount
                    FROM orders
                    WHERE status = 'PAID'
                ) s
                WHERE day_key IS NOT NULL AND day_key <> ''
                """);

        List<Object> params = new ArrayList<>();
        if (startDate != null && !startDate.isBlank()) {
            sql.append(" AND day_key >= ?");
            params.add(startDate);
        }
        if (endDate != null && !endDate.isBlank()) {
            sql.append(" AND day_key <= ?");
            params.add(endDate);
        }
        sql.append(" GROUP BY day_key ORDER BY day_key ASC");

        Map<String, Double> result = new LinkedHashMap<>();
        try (Connection connection = databaseManager.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql.toString())) {
            for (int index = 0; index < params.size(); index++) {
                statement.setObject(index + 1, params.get(index));
            }
            try (ResultSet rs = statement.executeQuery()) {
                while (rs.next()) {
                    String dayKey = rs.getString("day_key");
                    if (dayKey != null && !dayKey.isBlank()) {
                        result.put(dayKey, rs.getDouble("total"));
                    }
                }
            }
            return result;
        } catch (SQLException exception) {
            throw new IllegalStateException("Failed to build daily paid totals", exception);
        }
    }

    // Returns the earliest and latest dates that have PAID orders —
    // used to set the default date range shown in the admin report filters
    public String[] getPaidSalesDateRange() {
        String sql = """
                SELECT
                    MIN(DATE(created_at)) as min_date,
                    MAX(DATE(created_at)) as max_date
                FROM orders
                WHERE status = 'PAID'
                  AND DATE(created_at) IS NOT NULL
                  AND DATE(created_at) > '1970-01-01'
                """;

        try (Connection connection = databaseManager.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql);
             ResultSet rs = statement.executeQuery()) {
            if (rs.next()) {
                return new String[] {rs.getString("min_date"), rs.getString("max_date")};
            }
            return new String[] {null, null};
        } catch (SQLException exception) {
            throw new IllegalStateException("Failed to load paid sales date range", exception);
        }
    }

    // Returns high-level aggregates (total revenue, order count, max/min sale) for the report page
    public Map<String, Object> getOrderAggregatesByType(String startDate, String endDate, String category) {
        StringBuilder sql = new StringBuilder("""
                SELECT
                    COALESCE(SUM(order_total), 0) as total_revenue,
                    COUNT(*) as total_orders,
                    COALESCE(MAX(order_total), 0) as max_sale,
                    COALESCE(MIN(CASE WHEN order_total > 0 THEN order_total END), 0) as min_sale
                FROM (
                    SELECT oi.order_id, SUM(oi.subtotal) as order_total
                    FROM orders o
                    JOIN order_items oi ON oi.order_id = o.id
                    WHERE o.status = 'PAID'
                """);

        List<Object> params = new ArrayList<>();
        if (startDate != null && !startDate.isBlank()) {
            sql.append(" AND DATE(o.created_at) >= DATE(?)");
            params.add(startDate);
        }
        if (endDate != null && !endDate.isBlank()) {
            sql.append(" AND DATE(o.created_at) <= DATE(?)");
            params.add(endDate);
        }
        if (category != null && !category.isBlank() && !"All".equalsIgnoreCase(category)) {
            sql.append(" AND oi.category = ?");
            params.add(category);
        }
        sql.append(" GROUP BY oi.order_id ) t");

        Map<String, Object> result = new HashMap<>();
        try (Connection connection = databaseManager.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql.toString())) {
            for (int i = 0; i < params.size(); i++) {
                statement.setObject(i + 1, params.get(i));
            }
            try (ResultSet rs = statement.executeQuery()) {
                if (rs.next()) {
                    result.put("totalRevenue", rs.getDouble("total_revenue"));
                    result.put("totalOrders", rs.getInt("total_orders"));
                    result.put("maxSale", rs.getDouble("max_sale"));
                    result.put("minSale", rs.getDouble("min_sale"));
                }
            }
        } catch (SQLException exception) {
            throw new IllegalStateException("Failed to load order aggregates by type", exception);
        }

        result.putIfAbsent("totalRevenue", 0.0);
        result.putIfAbsent("totalOrders", 0);
        result.putIfAbsent("maxSale", 0.0);
        result.putIfAbsent("minSale", 0.0);
        return result;
    }

    /**
     * Returns summary statistics for the admin dashboard cards.
     * Keys: totalRevenue, totalOrders, qtySold, lowStockCount
     */
    public Map<String, Object> getDashboardStats(int lowStockThreshold) {
        return getDashboardStatsByDate("", "", lowStockThreshold);
    }

    /**
     * Returns date-filtered dashboard statistics (PAID orders only for revenue).
     * Keys: totalRevenue, totalOrders, qtySold, maxSale, minSale, avgOrderValue, lowStockCount, outOfStockCount
     */
    public Map<String, Object> getDashboardStatsByDate(String startDate, String endDate, int lowStockThreshold) {
        Map<String, Object> stats = new HashMap<>();

        StringBuilder revenueSql = new StringBuilder(
                "SELECT COALESCE(SUM(total_amount),0) as rev, COUNT(*) as cnt FROM orders WHERE status = 'PAID'");
        List<Object> revParams = new ArrayList<>();
        if (startDate != null && !startDate.isBlank()) { revenueSql.append(" AND DATE(created_at) >= ?"); revParams.add(startDate); }
        if (endDate != null && !endDate.isBlank()) { revenueSql.append(" AND DATE(created_at) <= ?"); revParams.add(endDate); }

        StringBuilder qtySql = new StringBuilder(
                "SELECT COALESCE(SUM(oi.quantity),0) as qty FROM order_items oi JOIN orders o ON o.id = oi.order_id WHERE o.status = 'PAID'");
        List<Object> qtyParams = new ArrayList<>();
        if (startDate != null && !startDate.isBlank()) { qtySql.append(" AND DATE(o.created_at) >= ?"); qtyParams.add(startDate); }
        if (endDate != null && !endDate.isBlank()) { qtySql.append(" AND DATE(o.created_at) <= ?"); qtyParams.add(endDate); }

        StringBuilder maxMinSql = new StringBuilder(
                "SELECT MAX(total_amount) as max_s, MIN(total_amount) as min_s FROM orders WHERE status = 'PAID' AND total_amount > 0");
        List<Object> maxMinParams = new ArrayList<>();
        if (startDate != null && !startDate.isBlank()) { maxMinSql.append(" AND DATE(created_at) >= ?"); maxMinParams.add(startDate); }
        if (endDate != null && !endDate.isBlank()) { maxMinSql.append(" AND DATE(created_at) <= ?"); maxMinParams.add(endDate); }

    String lowStockSql = "SELECT COUNT(*) as cnt FROM products WHERE stock < ? AND stock > 0";
    String outOfStockSql = "SELECT COUNT(*) as cnt FROM products WHERE stock = 0";

        try (Connection connection = databaseManager.getConnection()) {
            try (PreparedStatement ps = connection.prepareStatement(revenueSql.toString())) {
                for (int i = 0; i < revParams.size(); i++) ps.setObject(i + 1, revParams.get(i));
                try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    stats.put("totalRevenue", rs.getDouble("rev"));
                    stats.put("totalOrders", rs.getInt("cnt"));
                }
                }
            }
            try (PreparedStatement ps = connection.prepareStatement(qtySql.toString())) {
                for (int i = 0; i < qtyParams.size(); i++) ps.setObject(i + 1, qtyParams.get(i));
                try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    stats.put("qtySold", rs.getInt("qty"));
                }
                }
            }
            try (PreparedStatement ps = connection.prepareStatement(maxMinSql.toString())) {
                for (int i = 0; i < maxMinParams.size(); i++) ps.setObject(i + 1, maxMinParams.get(i));
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        stats.put("maxSale", rs.getDouble("max_s"));
                        stats.put("minSale", rs.getDouble("min_s"));
                    }
                }
            }
            try (PreparedStatement ps = connection.prepareStatement(lowStockSql)) {
                ps.setInt(1, lowStockThreshold);
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        stats.put("lowStockCount", rs.getInt("cnt"));
                    }
                }
            }
            try (PreparedStatement ps = connection.prepareStatement(outOfStockSql);
                 ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        stats.put("outOfStockCount", rs.getInt("cnt"));
                    }
            }
        } catch (SQLException exception) {
            throw new IllegalStateException("Failed to load dashboard stats", exception);
        }
        stats.putIfAbsent("totalRevenue", 0.0);
        stats.putIfAbsent("totalOrders", 0);
        stats.putIfAbsent("qtySold", 0);
        stats.putIfAbsent("maxSale", 0.0);
        stats.putIfAbsent("minSale", 0.0);
        stats.putIfAbsent("lowStockCount", 0);
        stats.putIfAbsent("outOfStockCount", 0);
        return stats;
    }

    /**
     * Exports the given sales records as a CSV file to the specified path.
     */
    public void exportToCsv(List<SalesRecord> records, String filePath) {
        try (PrintWriter writer = new PrintWriter(new OutputStreamWriter(new FileOutputStream(filePath), StandardCharsets.UTF_8))) {
            writer.println("Date,Product,Category,Quantity,Sales (RM)");
            for (SalesRecord record : records) {
                writer.printf("\"%s\",\"%s\",\"%s\",%d,%.2f%n",
                        record.getDate(),
                        record.getProductName(),
                        record.getCategory(),
                        record.getQuantity(),
                        record.getSalesAmount());
            }
        } catch (IOException exception) {
            throw new IllegalStateException("Failed to export CSV: " + filePath, exception);
        }
    }

    /**
     * Calculates profit margin stats for paid orders in the given date range.
     * Cost prices are fixed per clothing type: Hoodie=230 RM, Sweatshirt=200 RM, T-shirt=160 RM.
     * Shipping is excluded from cost.
     *
     * @return map with keys: totalRevenue, totalCost, totalProfit, profitMarginPercent
     */
    public Map<String, Object> getProfitStats(String startDate, String endDate) {
        StringBuilder sql = new StringBuilder("""
                SELECT oi.category, SUM(oi.quantity) as qty, SUM(oi.subtotal) as revenue
                FROM orders o
                JOIN order_items oi ON oi.order_id = o.id
                WHERE o.status = 'PAID'
                """);

        List<Object> params = new ArrayList<>();
        if (startDate != null && !startDate.isBlank()) { sql.append(" AND DATE(o.created_at) >= DATE(?)"); params.add(startDate); }
        if (endDate != null && !endDate.isBlank()) { sql.append(" AND DATE(o.created_at) <= DATE(?)"); params.add(endDate); }
        sql.append(" GROUP BY oi.category");

        Map<String, Double> costByCategory = Map.of(
                "Hoodie",     230.0,
                "Sweatshirt", 200.0,
                "T-shirt",    160.0
        );

        double totalRevenue = 0;
        double totalCost = 0;

        try (Connection connection = databaseManager.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql.toString())) {
            for (int i = 0; i < params.size(); i++) statement.setObject(i + 1, params.get(i));
            try (ResultSet rs = statement.executeQuery()) {
                while (rs.next()) {
                    String category = rs.getString("category");
                    int qty = rs.getInt("qty");
                    double revenue = rs.getDouble("revenue");
                    double costPerUnit = costByCategory.getOrDefault(category, 0.0);
                    totalRevenue += revenue;
                    totalCost += costPerUnit * qty;
                }
            }
        } catch (SQLException exception) {
            throw new IllegalStateException("Failed to compute profit stats", exception);
        }

        double totalProfit = totalRevenue - totalCost;
        double marginPercent = totalRevenue == 0 ? 0 : (totalProfit / totalRevenue) * 100.0;

        Map<String, Object> result = new HashMap<>();
        result.put("totalRevenue", totalRevenue);
        result.put("totalCost", totalCost);
        result.put("totalProfit", totalProfit);
        result.put("profitMarginPercent", marginPercent);
        return result;
    }
}
