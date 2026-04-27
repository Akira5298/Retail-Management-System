package tokyoera.service;

import tokyoera.model.ClothingType;
import tokyoera.model.Product;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

// This service handles all product data operations — create, read, update, delete.
// It also manages per-size stock so each size (XS/S/M/...) has its own stock count.
public class ProductService {
    private final DatabaseManager databaseManager;

    public ProductService(DatabaseManager databaseManager) {
        this.databaseManager = databaseManager;
    }

    // Returns every product in the catalogue sorted by ID
    public List<Product> getAll() {
        String sql = "SELECT * FROM products ORDER BY id";
        List<Product> products = new ArrayList<>();
        try (Connection connection = databaseManager.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql);
             ResultSet resultSet = statement.executeQuery()) {
            while (resultSet.next()) {
                products.add(mapProduct(resultSet));
            }
            return products;
        } catch (SQLException exception) {
            throw new IllegalStateException("Failed to get products", exception);
        }
    }

    // Looks up one product by its ID — returns empty Optional if not found
    public Optional<Product> findById(int productId) {
        String sql = "SELECT * FROM products WHERE id = ?";
        try (Connection connection = databaseManager.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setInt(1, productId);
            try (ResultSet resultSet = statement.executeQuery()) {
                if (resultSet.next()) {
                    return Optional.of(mapProduct(resultSet));
                }
                return Optional.empty();
            }
        } catch (SQLException exception) {
            throw new IllegalStateException("Failed to find product", exception);
        }
    }

    // Inserts a brand new product into the database
    public void create(Product product) {
        String sql = """
                INSERT INTO products(name, description, price, stock, clothing_type, color,
                default_english_message, default_japanese_message, customization_allowed,
                customization_fee, sizes, material, includes_info, image_prefix)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """;
        try (Connection connection = databaseManager.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            bindProduct(statement, product);
            statement.executeUpdate();
        } catch (SQLException exception) {
            throw new IllegalStateException("Failed to create product", exception);
        }
    }

    // Overwrites all fields of an existing product (admin edit)
    public void update(Product product) {
        String sql = """
                UPDATE products SET name=?, description=?, price=?, stock=?, clothing_type=?, color=?,
                default_english_message=?, default_japanese_message=?, customization_allowed=?,
                customization_fee=?, sizes=?, material=?, includes_info=?, image_prefix=?
                WHERE id=?
                """;
        try (Connection connection = databaseManager.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            bindProduct(statement, product);
            statement.setInt(15, product.getId());
            statement.executeUpdate();
        } catch (SQLException exception) {
            throw new IllegalStateException("Failed to update product", exception);
        }
    }

    // Removes a product permanently from the database
    public void delete(int productId) {
        String sql = "DELETE FROM products WHERE id = ?";
        try (Connection connection = databaseManager.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setInt(1, productId);
            statement.executeUpdate();
        } catch (SQLException exception) {
            throw new IllegalStateException("Failed to delete product", exception);
        }
    }

    // Quick check: does this product have at least `quantity` units of total stock?
    public boolean hasEnoughStock(int productId, int quantity) {
        return findById(productId).map(product -> product.getStock() >= quantity).orElse(false);
    }

    // Reduce the total stock by `quantity` (used when there are no per-size rows)
    public void reduceStock(int productId, int quantity) {
        String sql = "UPDATE products SET stock = stock - ? WHERE id = ? AND stock >= ?";
        try (Connection connection = databaseManager.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setInt(1, quantity);
            statement.setInt(2, productId);
            statement.setInt(3, quantity);
            int updated = statement.executeUpdate();
            if (updated == 0) {
                throw new IllegalStateException("Insufficient stock for product " + productId);
            }
        } catch (SQLException exception) {
            throw new IllegalStateException("Failed to reduce stock", exception);
        }
    }

    // Directly overwrite total stock and clear any per-size rows (e.g. full reset)
    public void updateStock(int productId, int newStock) {
        if (newStock < 0) {
            throw new IllegalArgumentException("Stock cannot be negative");
        }
        String sql = "UPDATE products SET stock = ? WHERE id = ?";
        String clearSizeSql = "DELETE FROM product_size_stock WHERE product_id = ?";
        try (Connection connection = databaseManager.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql);
             PreparedStatement clearSizeStatement = connection.prepareStatement(clearSizeSql)) {
            connection.setAutoCommit(false);
                try {
                    statement.setInt(1, newStock);
                    statement.setInt(2, productId);
                    statement.executeUpdate();

                    clearSizeStatement.setInt(1, productId);
                    clearSizeStatement.executeUpdate();

                    connection.commit();
                } catch (SQLException exception) {
                    connection.rollback();
                    throw exception;
                } finally {
                    connection.setAutoCommit(true);
                }
        } catch (SQLException exception) {
            throw new IllegalStateException("Failed to update stock", exception);
        }
    }

    // Returns how many units of this product are available for a specific size.
    // If per-size rows exist, use those; otherwise fall back to the product's total stock.
    public int getAvailableStockForSize(int productId, String size) {
        if (size == null || size.isBlank()) {
            return 0;
        }

        String countSql = "SELECT COUNT(*) AS cnt FROM product_size_stock WHERE product_id = ?";
        String sizeSql = "SELECT stock FROM product_size_stock WHERE product_id = ? AND size = ?";
        String totalSql = "SELECT stock FROM products WHERE id = ?";

        try (Connection connection = databaseManager.getConnection();
             PreparedStatement countStatement = connection.prepareStatement(countSql);
             PreparedStatement sizeStatement = connection.prepareStatement(sizeSql);
             PreparedStatement totalStatement = connection.prepareStatement(totalSql)) {
            countStatement.setInt(1, productId);
            int sizeRows = 0;
            try (ResultSet resultSet = countStatement.executeQuery()) {
                if (resultSet.next()) {
                    sizeRows = resultSet.getInt("cnt");
                }
            }

            if (sizeRows > 0) {
                sizeStatement.setInt(1, productId);
                sizeStatement.setString(2, size.trim());
                try (ResultSet resultSet = sizeStatement.executeQuery()) {
                    if (resultSet.next()) {
                        return Math.max(0, resultSet.getInt("stock"));
                    }
                }
                return 0;
            }

            totalStatement.setInt(1, productId);
            try (ResultSet resultSet = totalStatement.executeQuery()) {
                if (resultSet.next()) {
                    return Math.max(0, resultSet.getInt("stock"));
                }
            }
            return 0;
        } catch (SQLException exception) {
            throw new IllegalStateException("Failed to get available stock", exception);
        }
    }

    // Convenience wrapper: true if the requested quantity is available for this size
    public boolean hasEnoughStockForSize(int productId, String size, int quantity) {
        if (quantity <= 0) {
            return false;
        }
        return getAvailableStockForSize(productId, size) >= quantity;
    }

    // Deducts stock for a specific size inside an existing DB transaction.
    // If per-size rows exist, decrement that size row and sync the total.
    // Otherwise fall back to decrementing the product's total stock column.
    public void consumeStockForSize(Connection connection, int productId, String size, int quantity) throws SQLException {
        if (quantity <= 0) {
            throw new IllegalArgumentException("Quantity must be at least 1");
        }
        if (size == null || size.isBlank()) {
            throw new IllegalArgumentException("Size is required");
        }

        String countSql = "SELECT COUNT(*) AS cnt FROM product_size_stock WHERE product_id = ?";
        String reduceSizeSql = "UPDATE product_size_stock SET stock = stock - ? WHERE product_id = ? AND size = ? AND stock >= ?";
        String reduceTotalSql = "UPDATE products SET stock = stock - ? WHERE id = ? AND stock >= ?";

        try (PreparedStatement countStatement = connection.prepareStatement(countSql);
             PreparedStatement reduceSizeStatement = connection.prepareStatement(reduceSizeSql);
             PreparedStatement reduceTotalStatement = connection.prepareStatement(reduceTotalSql)) {
            countStatement.setInt(1, productId);
            int sizeRows = 0;
            try (ResultSet resultSet = countStatement.executeQuery()) {
                if (resultSet.next()) {
                    sizeRows = resultSet.getInt("cnt");
                }
            }

            if (sizeRows > 0) {
                reduceSizeStatement.setInt(1, quantity);
                reduceSizeStatement.setInt(2, productId);
                reduceSizeStatement.setString(3, size.trim());
                reduceSizeStatement.setInt(4, quantity);
                int updated = reduceSizeStatement.executeUpdate();
                if (updated == 0) {
                    throw new IllegalStateException("Insufficient stock for " + size + " size");
                }
                syncTotalStockFromSizes(connection, productId);
                return;
            }

            reduceTotalStatement.setInt(1, quantity);
            reduceTotalStatement.setInt(2, productId);
            reduceTotalStatement.setInt(3, quantity);
            int updated = reduceTotalStatement.executeUpdate();
            if (updated == 0) {
                throw new IllegalStateException("Insufficient stock for product " + productId);
            }
        }
    }

    // Returns a map of size → stock for a product.
    // Reads from product_size_stock; if empty, distributes total stock evenly across sizes.
    public Map<String, Integer> getSizeStocks(int productId, List<String> sizes, int totalStock) {
        Map<String, Integer> stocks = new LinkedHashMap<>();
        if (sizes == null || sizes.isEmpty()) {
            return stocks;
        }

        String sql = "SELECT size, stock FROM product_size_stock WHERE product_id = ?";
        try (Connection connection = databaseManager.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setInt(1, productId);
            try (ResultSet resultSet = statement.executeQuery()) {
                while (resultSet.next()) {
                    String size = resultSet.getString("size");
                    int stock = resultSet.getInt("stock");
                    if (size != null && sizes.contains(size)) {
                        stocks.put(size, Math.max(stock, 0));
                    }
                }
            }
        } catch (SQLException exception) {
            throw new IllegalStateException("Failed to get size stocks", exception);
        }

        if (stocks.isEmpty()) {
            int sizeCount = sizes.size();
            int base = sizeCount == 0 ? 0 : totalStock / sizeCount;
            int remainder = sizeCount == 0 ? 0 : totalStock % sizeCount;
            for (int index = 0; index < sizes.size(); index++) {
                int value = base + (index < remainder ? 1 : 0);
                stocks.put(sizes.get(index), Math.max(value, 0));
            }
        } else {
            for (String size : sizes) {
                stocks.putIfAbsent(size, 0);
            }
        }

        return stocks;
    }

    // Admin: update the stock for one specific size and recalculate the total
    public void updateSizeStock(int productId, String size, int newStock) {
        if (size == null || size.isBlank()) {
            throw new IllegalArgumentException("Size is required");
        }
        if (newStock < 0) {
            throw new IllegalArgumentException("Stock cannot be negative");
        }

        String upsertSql = """
                INSERT INTO product_size_stock(product_id, size, stock)
                VALUES (?, ?, ?)
                ON CONFLICT(product_id, size)
                DO UPDATE SET stock = excluded.stock
                """;
        String sumSql = "SELECT COALESCE(SUM(stock), 0) AS total FROM product_size_stock WHERE product_id = ?";
        String updateProductSql = "UPDATE products SET stock = ? WHERE id = ?";

        try (Connection connection = databaseManager.getConnection()) {
            connection.setAutoCommit(false);
            try (PreparedStatement upsert = connection.prepareStatement(upsertSql);
                 PreparedStatement sum = connection.prepareStatement(sumSql);
                 PreparedStatement updateTotal = connection.prepareStatement(updateProductSql)) {
                upsert.setInt(1, productId);
                upsert.setString(2, size.trim());
                upsert.setInt(3, newStock);
                upsert.executeUpdate();

                sum.setInt(1, productId);
                int total = 0;
                try (ResultSet resultSet = sum.executeQuery()) {
                    if (resultSet.next()) {
                        total = resultSet.getInt("total");
                    }
                }

                updateTotal.setInt(1, total);
                updateTotal.setInt(2, productId);
                updateTotal.executeUpdate();

                connection.commit();
            } catch (SQLException exception) {
                connection.rollback();
                throw exception;
            } finally {
                connection.setAutoCommit(true);
            }
        } catch (SQLException exception) {
            throw new IllegalStateException("Failed to update size stock", exception);
        }
    }

    private void syncTotalStockFromSizes(Connection connection, int productId) throws SQLException {
        String sumSql = "SELECT COALESCE(SUM(stock), 0) AS total FROM product_size_stock WHERE product_id = ?";
        String updateProductSql = "UPDATE products SET stock = ? WHERE id = ?";
        try (PreparedStatement sumStatement = connection.prepareStatement(sumSql);
             PreparedStatement updateStatement = connection.prepareStatement(updateProductSql)) {
            sumStatement.setInt(1, productId);
            int total = 0;
            try (ResultSet resultSet = sumStatement.executeQuery()) {
                if (resultSet.next()) {
                    total = resultSet.getInt("total");
                }
            }
            updateStatement.setInt(1, total);
            updateStatement.setInt(2, productId);
            updateStatement.executeUpdate();
        }
    }

    private void bindProduct(PreparedStatement statement, Product product) throws SQLException {
        statement.setString(1, product.getName());
        statement.setString(2, product.getDescription());
        statement.setDouble(3, product.getPrice());
        statement.setInt(4, product.getStock());
        statement.setString(5, product.getClothingType().name());
        statement.setString(6, product.getColor());
        statement.setString(7, product.getDefaultEnglishMessage());
        statement.setString(8, product.getDefaultJapaneseMessage());
        statement.setInt(9, product.isCustomizationAllowed() ? 1 : 0);
        statement.setDouble(10, product.getCustomizationFee());
        statement.setString(11, product.getSizes());
        statement.setString(12, product.getMaterial());
        statement.setString(13, product.getIncludesInfo());
        statement.setString(14, product.getImagePrefix());
    }

    private Product mapProduct(ResultSet resultSet) throws SQLException {
        return new Product(
                resultSet.getInt("id"),
                resultSet.getString("name"),
                resultSet.getString("description"),
                resultSet.getDouble("price"),
                resultSet.getInt("stock"),
                ClothingType.fromString(resultSet.getString("clothing_type")),
                resultSet.getString("color"),
                resultSet.getString("default_english_message"),
                resultSet.getString("default_japanese_message"),
                resultSet.getInt("customization_allowed") == 1,
                resultSet.getDouble("customization_fee"),
                resultSet.getString("sizes"),
                resultSet.getString("material"),
                resultSet.getString("includes_info"),
                resultSet.getString("image_prefix")
        );
    }
}
