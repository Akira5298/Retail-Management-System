package tokyoera.service;

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;

// This class creates all the database tables on first run.
// It also runs migrations to add new columns to existing tables so old databases still work.
public class DatabaseInitializer {
    private final DatabaseManager databaseManager;

    public DatabaseInitializer(DatabaseManager databaseManager) {
        this.databaseManager = databaseManager;
    }

    public void initialize() {
        String usersTable = """
                CREATE TABLE IF NOT EXISTS users (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    username TEXT UNIQUE NOT NULL,
                    password TEXT NOT NULL,
                    role TEXT NOT NULL,
                    failed_attempts INTEGER DEFAULT 0,
                    locked_until TEXT DEFAULT NULL
                );
                """;

        String productsTable = """
                CREATE TABLE IF NOT EXISTS products (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    name TEXT NOT NULL,
                    description TEXT NOT NULL,
                    price REAL NOT NULL,
                    stock INTEGER NOT NULL,
                    clothing_type TEXT NOT NULL,
                    color TEXT NOT NULL,
                    default_english_message TEXT NOT NULL,
                    default_japanese_message TEXT NOT NULL,
                    customization_allowed INTEGER NOT NULL,
                    customization_fee REAL NOT NULL,
                    sizes TEXT NOT NULL,
                    material TEXT NOT NULL,
                    includes_info TEXT NOT NULL,
                    image_prefix TEXT NOT NULL,
                    created_at TEXT DEFAULT CURRENT_TIMESTAMP
                );
                """;

        String ordersTable = """
                CREATE TABLE IF NOT EXISTS orders (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    user_id INTEGER NOT NULL,
                    total_amount REAL NOT NULL,
                    status TEXT NOT NULL,
                    coupon_code TEXT DEFAULT NULL,
                    discount_amount REAL DEFAULT 0,
                    created_at TEXT DEFAULT CURRENT_TIMESTAMP,
                    FOREIGN KEY (user_id) REFERENCES users(id)
                );
                """;

        String orderItemsTable = """
                CREATE TABLE IF NOT EXISTS order_items (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    order_id INTEGER NOT NULL,
                    product_id INTEGER NOT NULL,
                    product_name TEXT NOT NULL,
                    category TEXT NOT NULL,
                    size TEXT NOT NULL,
                    quantity INTEGER NOT NULL,
                    base_price REAL NOT NULL,
                    customization_fee REAL NOT NULL,
                    customized INTEGER NOT NULL,
                    custom_english TEXT,
                    custom_japanese TEXT,
                    subtotal REAL NOT NULL,
                    FOREIGN KEY (order_id) REFERENCES orders(id),
                    FOREIGN KEY (product_id) REFERENCES products(id)
                );
                """;

        String transactionsTable = """
                CREATE TABLE IF NOT EXISTS transactions (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    order_id INTEGER NOT NULL,
                    amount REAL NOT NULL,
                    payment_method TEXT NOT NULL,
                    created_at TEXT DEFAULT CURRENT_TIMESTAMP,
                    FOREIGN KEY (order_id) REFERENCES orders(id)
                );
                """;

        String settingsTable = """
                CREATE TABLE IF NOT EXISTS settings (
                    key TEXT PRIMARY KEY NOT NULL,
                    value TEXT NOT NULL
                );
                """;

        String cartTable = """
                CREATE TABLE IF NOT EXISTS cart (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    user_id INTEGER NOT NULL,
                    product_id INTEGER NOT NULL,
                    size TEXT NOT NULL,
                    quantity INTEGER NOT NULL,
                    customized INTEGER NOT NULL DEFAULT 0,
                    custom_english TEXT,
                    custom_japanese TEXT,
                    FOREIGN KEY (user_id) REFERENCES users(id),
                    FOREIGN KEY (product_id) REFERENCES products(id)
                );
                """;

        String couponsTable = """
                CREATE TABLE IF NOT EXISTS coupons (
                    code TEXT PRIMARY KEY NOT NULL,
                    discount_type TEXT NOT NULL,
                    discount_value REAL NOT NULL,
                    expiry_date TEXT,
                    active INTEGER NOT NULL DEFAULT 1,
                    applies_to_product_id INTEGER DEFAULT NULL,
                    FOREIGN KEY (applies_to_product_id) REFERENCES products(id)
                );
                """;

        String productSizeStockTable = """
                CREATE TABLE IF NOT EXISTS product_size_stock (
                    product_id INTEGER NOT NULL,
                    size TEXT NOT NULL,
                    stock INTEGER NOT NULL DEFAULT 0,
                    PRIMARY KEY (product_id, size),
                    FOREIGN KEY (product_id) REFERENCES products(id) ON DELETE CASCADE
                );
                """;

        try (Connection connection = databaseManager.getConnection();
             Statement statement = connection.createStatement()) {
            // Create each table if it doesn't already exist
            statement.execute(usersTable);
            statement.execute(productsTable);
            statement.execute(ordersTable);
            statement.execute(orderItemsTable);
            statement.execute(transactionsTable);
            statement.execute(settingsTable);
            statement.execute(cartTable);
            statement.execute(couponsTable);
            statement.execute(productSizeStockTable);
            // Then run migrations for any columns added in newer versions
            migrateExistingSchema(connection);
        } catch (SQLException exception) {
            throw new IllegalStateException("Failed to initialize database schema", exception);
        }
    }

    /** Adds new columns to existing tables that were created before this schema version. */
    private void migrateExistingSchema(Connection connection) {
        // Try to add each column; if it already exists SQLite throws an error which we safely ignore
        alterTableIfMissing(connection, "ALTER TABLE users ADD COLUMN failed_attempts INTEGER DEFAULT 0");
        alterTableIfMissing(connection, "ALTER TABLE users ADD COLUMN locked_until TEXT DEFAULT NULL");
        alterTableIfMissing(connection, "ALTER TABLE orders ADD COLUMN coupon_code TEXT DEFAULT NULL");
        alterTableIfMissing(connection, "ALTER TABLE orders ADD COLUMN discount_amount REAL DEFAULT 0");
        alterTableIfMissing(connection, "ALTER TABLE coupons ADD COLUMN applies_to_product_id INTEGER DEFAULT NULL");
        // Clean up old size data that's no longer used
        removeLegacySizes(connection);
        removeLegacySizeStockRows(connection);
    }

    private void alterTableIfMissing(Connection connection, String sql) {
        try (Statement statement = connection.createStatement()) {
            statement.execute(sql);
        } catch (SQLException ignored) {
            // Column already exists — safe to ignore
        }
    }

    private void removeLegacySizes(Connection connection) {
        try (Statement st = connection.createStatement()) {
            st.execute("UPDATE products SET sizes = REPLACE(sizes, ',4X', '')");
            st.execute("UPDATE products SET sizes = REPLACE(sizes, ',5X', '')");
        } catch (SQLException ignored) { }
    }

    private void removeLegacySizeStockRows(Connection connection) {
        try (Statement st = connection.createStatement()) {
            st.execute("DELETE FROM product_size_stock WHERE size IN ('4X', '5X')");
        } catch (SQLException ignored) { }
    }
}
