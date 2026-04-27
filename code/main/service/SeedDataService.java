package tokyoera.service;

import tokyoera.model.ClothingType;
import tokyoera.model.Product;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.UUID;

// This service fills the database with demo data on first launch.
// It creates default users, products, coupons, and historical orders so the app
// looks populated straight away without needing real customer data.
public class SeedDataService {
    private static final double PRODUCT_4_CORRECT_PRICE = 149.0;
    private static final String PRODUCT_4_IMAGE_PREFIX = "Product4";
    private static final String PRODUCT_4_NAME = "PREMIUM Japanese Embroidered T-Shirt – Black Heather – Japanese Characters / Break the Limit";
    private static final String DEFAULT_PRODUCT_MATERIAL = "100% combed and ring-spun cotton, 4.2 oz./yd², pre-shrunk, side-seamed, shoulder taping";

    private static final String PRODUCT_BASE_DESCRIPTION = """
        Tokyo Era is more than streetwear—it’s a statement. Designed to move with you, built to express who you are. Stand out. Move forward. Wear your spirit.
        Design Details:
        Front: Embroidered Tokyo Era / 東京時代 logo
        """;

    private final DatabaseManager databaseManager;

    public SeedDataService(DatabaseManager databaseManager) {
        this.databaseManager = databaseManager;
    }

    // Main entry point — called once at app startup to make sure all demo data is present
    public void seed() {
        seedUsers();
        seedProducts();
        resetExistingAccountPasswords(); // make sure demo passwords are always correct
        seedCoupons();
        normalizeEmptyProductMaterial();
        normalizeProduct4PriceAndHistory(); // fix price across all historical order records
        normalizeLegacyStatuses();          // rename old COMPLETED/PROCESSING statuses
        normalizeLegacyTimestamps();        // convert unix timestamps to readable datetime
        seedHistoricalDemoOrders();         // add some realistic past orders for the charts
    }

    /** Resets seeded/demo account passwords to strong plaintext defaults. */
    // Resets the known demo account passwords to standard values
    // so the login credentials in the project docs always work
    private void resetExistingAccountPasswords() {
        String updateAdminSql = "UPDATE users SET password = ? WHERE username = ?";
        String updatePatternSql = "UPDATE users SET password = ? WHERE username LIKE ?";
        try (Connection conn = databaseManager.getConnection();
             PreparedStatement adminStmt = conn.prepareStatement(updateAdminSql);
             PreparedStatement patternStmt = conn.prepareStatement(updatePatternSql)) {
            adminStmt.setString(1, "Admin@123!");
            adminStmt.setString(2, "admin");
            adminStmt.executeUpdate();

            adminStmt.setString(1, "User@123!");
            adminStmt.setString(2, "user");
            adminStmt.executeUpdate();

            patternStmt.setString(1, "Demo@123!");
            patternStmt.setString(2, "demo_user_%");
            patternStmt.executeUpdate();
        } catch (SQLException exception) {
            throw new IllegalStateException("Failed to reset seeded account passwords", exception);
        }
    }

    /** Seeds demo coupon codes if they do not already exist. */
    // Inserts demo coupon codes if they don't already exist
    private void seedCoupons() {
        String insertSql = "INSERT OR IGNORE INTO coupons(code, discount_type, discount_value, expiry_date, active, applies_to_product_id) VALUES (?,?,?,?,1,?)";
        Object[][] coupons = {
            {"TOKYO10",   "PERCENT", 10.0, null, null},
            {"WELCOME20", "FIXED",   20.0, null, null},
            {"SUMMER15",  "PERCENT", 15.0, null, null}
        };
        try (Connection conn = databaseManager.getConnection();
             PreparedStatement stmt = conn.prepareStatement(insertSql)) {
            for (Object[] c : coupons) {
                stmt.setString(1, (String) c[0]);
                stmt.setString(2, (String) c[1]);
                stmt.setDouble(3, (double) c[2]);
                stmt.setString(4, (String) c[3]);
                if (c[4] == null) {
                    stmt.setNull(5, java.sql.Types.INTEGER);
                } else {
                    stmt.setInt(5, (int) c[4]);
                }
                stmt.addBatch();
            }
            stmt.executeBatch();
        } catch (SQLException exception) {
            throw new IllegalStateException("Failed to seed coupons", exception);
        }
    }

    // Fills in missing material info for products that were seeded without it
    private void normalizeEmptyProductMaterial() {
        String sql = """
                UPDATE products
                SET material = ?
                WHERE TRIM(COALESCE(material, '')) = ''
                """;
        try (Connection connection = databaseManager.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, DEFAULT_PRODUCT_MATERIAL);
            statement.executeUpdate();
        } catch (SQLException exception) {
            throw new IllegalStateException("Failed to normalize empty product material", exception);
        }
    }

    // Corrects the price for Product 4 and propagates the fix to all historical order records
    private void normalizeProduct4PriceAndHistory() {
        String updateProductSql = "UPDATE products SET price = ? WHERE image_prefix = ?";
        String updateOrderItemsSql = """
                UPDATE order_items
                SET base_price = ?,
                    subtotal = (? + customization_fee) * quantity
                WHERE product_id IN (SELECT id FROM products WHERE image_prefix = ?)
                   OR product_name = ?
                """;
        String updateOrdersSql = """
                UPDATE orders
                SET total_amount = COALESCE((
                    SELECT SUM(oi.subtotal)
                    FROM order_items oi
                    WHERE oi.order_id = orders.id
                ), 0)
                WHERE id IN (
                    SELECT DISTINCT order_id
                    FROM order_items
                    WHERE product_id IN (SELECT id FROM products WHERE image_prefix = ?)
                       OR product_name = ?
                )
                """;
        String updateTransactionsSql = """
                UPDATE transactions
                SET amount = (SELECT o.total_amount FROM orders o WHERE o.id = transactions.order_id)
                WHERE order_id IN (
                    SELECT DISTINCT order_id
                    FROM order_items
                    WHERE product_id IN (SELECT id FROM products WHERE image_prefix = ?)
                       OR product_name = ?
                )
                """;

        try (Connection connection = databaseManager.getConnection()) {
            connection.setAutoCommit(false);
            try (PreparedStatement updateProduct = connection.prepareStatement(updateProductSql);
                 PreparedStatement updateOrderItems = connection.prepareStatement(updateOrderItemsSql);
                 PreparedStatement updateOrders = connection.prepareStatement(updateOrdersSql);
                 PreparedStatement updateTransactions = connection.prepareStatement(updateTransactionsSql)) {
                updateProduct.setDouble(1, PRODUCT_4_CORRECT_PRICE);
                updateProduct.setString(2, PRODUCT_4_IMAGE_PREFIX);
                updateProduct.executeUpdate();

                updateOrderItems.setDouble(1, PRODUCT_4_CORRECT_PRICE);
                updateOrderItems.setDouble(2, PRODUCT_4_CORRECT_PRICE);
                updateOrderItems.setString(3, PRODUCT_4_IMAGE_PREFIX);
                updateOrderItems.setString(4, PRODUCT_4_NAME);
                updateOrderItems.executeUpdate();

                updateOrders.setString(1, PRODUCT_4_IMAGE_PREFIX);
                updateOrders.setString(2, PRODUCT_4_NAME);
                updateOrders.executeUpdate();

                updateTransactions.setString(1, PRODUCT_4_IMAGE_PREFIX);
                updateTransactions.setString(2, PRODUCT_4_NAME);
                updateTransactions.executeUpdate();

                connection.commit();
            } catch (Exception exception) {
                connection.rollback();
                throw exception;
            } finally {
                connection.setAutoCommit(true);
            }
        } catch (SQLException exception) {
            throw new IllegalStateException("Failed to normalize product 4 price history", exception);
        }
    }

    // Normalize old status names to the current ones (COMPLETED→PAID, PROCESSING→PENDING)
    private void normalizeLegacyStatuses() {
        String completedToPaidSql = "UPDATE orders SET status = 'PAID' WHERE status = 'COMPLETED'";
        String processingToPendingSql = "UPDATE orders SET status = 'PENDING' WHERE status = 'PROCESSING'";
        try (Connection connection = databaseManager.getConnection();
             PreparedStatement ps1 = connection.prepareStatement(completedToPaidSql);
             PreparedStatement ps2 = connection.prepareStatement(processingToPendingSql)) {
            ps1.executeUpdate();
            ps2.executeUpdate();
        } catch (SQLException exception) {
            throw new IllegalStateException("Failed to normalize legacy statuses", exception);
        }
    }

    // Convert any old unix-epoch timestamps stored in the DB to proper datetime strings
    private void normalizeLegacyTimestamps() {
        String normalizeOrdersSql = """
                UPDATE orders
                SET created_at = datetime(CAST(SUBSTR(created_at, 1, 10) AS INTEGER), 'unixepoch')
                WHERE created_at GLOB '[0-9]*' AND LENGTH(created_at) >= 10
                """;
        String normalizeTransactionsSql = """
                UPDATE transactions
                SET created_at = datetime(CAST(SUBSTR(created_at, 1, 10) AS INTEGER), 'unixepoch')
                WHERE created_at GLOB '[0-9]*' AND LENGTH(created_at) >= 10
                """;

        try (Connection connection = databaseManager.getConnection();
             PreparedStatement psOrders = connection.prepareStatement(normalizeOrdersSql);
             PreparedStatement psTransactions = connection.prepareStatement(normalizeTransactionsSql)) {
            psOrders.executeUpdate();
            psTransactions.executeUpdate();
        } catch (SQLException exception) {
            throw new IllegalStateException("Failed to normalize legacy timestamps", exception);
        }
    }

    // Seeds users only if the users table is empty (i.e. first run)
    private void seedUsers() {
        String countSql = "SELECT COUNT(1) FROM users";
        String insertSql = "INSERT INTO users(username, password, role) VALUES (?, ?, ?)";

        try (Connection connection = databaseManager.getConnection();
             PreparedStatement countStatement = connection.prepareStatement(countSql);
             ResultSet resultSet = countStatement.executeQuery()) {
            if (resultSet.next() && resultSet.getInt(1) > 0) {
                return;
            }
            try (PreparedStatement insertStatement = connection.prepareStatement(insertSql)) {
                insertStatement.setString(1, "user");
                insertStatement.setString(2, "User@123!");
                insertStatement.setString(3, "USER");
                insertStatement.addBatch();

                insertStatement.setString(1, "admin");
                insertStatement.setString(2, "Admin@123!");
                insertStatement.setString(3, "ADMIN");
                insertStatement.addBatch();
                insertStatement.executeBatch();
            }
        } catch (SQLException exception) {
            throw new IllegalStateException("Failed to seed users", exception);
        }
    }

    // Seeds products only if the products table is empty (i.e. first run)
    private void seedProducts() {
        String countSql = "SELECT COUNT(1) FROM products";
        String insertSql = """
                INSERT INTO products(name, description, price, stock, clothing_type, color,
                default_english_message, default_japanese_message, customization_allowed,
                customization_fee, sizes, material, includes_info, image_prefix)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """;

        List<Product> products = List.of(
                new Product(0,
                        "PREMIUM Japanese Embroidered Hoodie – Black – Japanese Characters / Keep Moving Forward",
            PRODUCT_BASE_DESCRIPTION + """
                    Left Sleeve: Keep moving forward (upside down for wearer)
                    Right Sleeve: 前進あるのみ
                    Back: Vaporwave graphics with 東京時代 and 創造の彼方 (Beyond Imagination)
                """,
                        199, 3, ClothingType.HOODIE, "black", "Keep moving forward", "前進あるのみ", true,
                        20, "XS,S,M,L,XL,2X,3X", "100% combed and ring-spun cotton, 4.2 oz./yd², pre-shrunk, side-seamed, shoulder taping",
                        "Designed by Akira Fukutomi (Japanese designer & music producer)", "Product1"),
                new Product(0,
                        "PREMIUM Japanese Embroidered Sweatshirt – Black – Japanese Characters / Break the Limit",
            PRODUCT_BASE_DESCRIPTION + """
                    Left Sleeve: Break the limit
                    Right Sleeve: 限界を超えろ
                    Back: 東京時代 and 眠らない闘志 (Sleepless Spirit)
                    """,
                        179, 8, ClothingType.SWEATSHIRT, "black", "Break the limit", "限界を超えろ", true,
                        20, "XS,S,M,L,XL,2X,3X", "100% combed and ring-spun cotton, 4.2 oz./yd², pre-shrunk, side-seamed, shoulder taping",
                        "Designed by Akira Fukutomi (Japanese designer & music producer)", "Product2"),
                new Product(0,
                        "PREMIUM Japanese Embroidered Hoodie – Black – Japanese Characters / Break the Limit",
                PRODUCT_BASE_DESCRIPTION + """
                    Left Sleeve: Break the limit
                    Right Sleeve: 限界を超えろ
                    Back: Vaporwave graphics with 東京時代 and 創造の彼方 (Beyond Imagination)
                    """,
                        199, 6, ClothingType.HOODIE, "black", "Break the limit", "限界を超えろ", true,
                        20, "XS,S,M,L,XL,2X,3X", "100% combed and ring-spun cotton, 4.2 oz./yd², pre-shrunk, side-seamed, shoulder taping",
                        "Designed by Akira Fukutomi (Japanese designer & music producer)", "Product3"),
                new Product(0,
                        "PREMIUM Japanese Embroidered T-Shirt – Black Heather – Japanese Characters / Break the Limit",
                PRODUCT_BASE_DESCRIPTION + """
                    Left Sleeve: Break the limit
                    Right Sleeve: 限界を超えろ
                    Back: Vaporwave-inspired TokyoEra visual language
                    """,
                    149, 0, ClothingType.T_SHIRT, "black", "Break the limit", "限界を超えろ", true,
                        20, "XS,S,M,L,XL,2X,3X", "Cotton + polyester blend",
                        "Designed by Akira Fukutomi (Japanese designer & music producer)", "Product4")
        );

        try (Connection connection = databaseManager.getConnection();
             PreparedStatement countStatement = connection.prepareStatement(countSql);
             ResultSet resultSet = countStatement.executeQuery()) {
            if (resultSet.next() && resultSet.getInt(1) > 0) {
                return;
            }
            try (PreparedStatement insertStatement = connection.prepareStatement(insertSql)) {
                for (Product product : products) {
                    insertStatement.setString(1, product.getName());
                    insertStatement.setString(2, product.getDescription());
                    insertStatement.setDouble(3, product.getPrice());
                    insertStatement.setInt(4, product.getStock());
                    insertStatement.setString(5, product.getClothingType().name());
                    insertStatement.setString(6, product.getColor());
                    insertStatement.setString(7, product.getDefaultEnglishMessage());
                    insertStatement.setString(8, product.getDefaultJapaneseMessage());
                    insertStatement.setInt(9, product.isCustomizationAllowed() ? 1 : 0);
                    insertStatement.setDouble(10, product.getCustomizationFee());
                    insertStatement.setString(11, product.getSizes());
                    insertStatement.setString(12, product.getMaterial());
                    insertStatement.setString(13, product.getIncludesInfo());
                    insertStatement.setString(14, product.getImagePrefix());
                    insertStatement.addBatch();
                }
                insertStatement.executeBatch();
            }
        } catch (SQLException exception) {
            throw new IllegalStateException("Failed to seed products", exception);
        }
    }

    // Generates realistic-looking historical orders spread across the past 6 months,
    // so the sales charts and reports have meaningful data to display out of the box
    private void seedHistoricalDemoOrders() {
        LocalDate startDate = LocalDate.of(LocalDate.now().getYear(), 4, 1);
        LocalDate endDate = LocalDate.of(LocalDate.now().getYear(), 4, 27);

        String inRangeCountSql = "SELECT COUNT(1) FROM orders WHERE date(created_at) BETWEEN ? AND ?";
        try (Connection connection = databaseManager.getConnection();
             PreparedStatement inRangeCountStmt = connection.prepareStatement(inRangeCountSql)) {
            inRangeCountStmt.setString(1, startDate.toString());
            inRangeCountStmt.setString(2, endDate.toString());
            int existingInRange;
            try (ResultSet rs = inRangeCountStmt.executeQuery()) {
                existingInRange = rs.next() ? rs.getInt(1) : 0;
            }
            if (existingInRange >= 120) {
                return;
            }

            ensureExtraUsers(connection);

            List<Integer> userIds = new ArrayList<>();
            try (PreparedStatement ps = connection.prepareStatement("SELECT id FROM users WHERE role = 'USER' ORDER BY id");
                 ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    userIds.add(rs.getInt("id"));
                }
            }

            class ProductSeed {
                int id;
                String name;
                String category;
                double price;
            }

            List<ProductSeed> products = new ArrayList<>();
            try (PreparedStatement ps = connection.prepareStatement("SELECT id, name, clothing_type, price FROM products");
                 ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    ProductSeed p = new ProductSeed();
                    p.id = rs.getInt("id");
                    p.name = rs.getString("name");
                    p.category = toCategoryLabel(rs.getString("clothing_type"));
                    p.price = rs.getDouble("price");
                    products.add(p);
                }
            }

            if (userIds.isEmpty() || products.isEmpty()) {
                return;
            }

            String insertOrderSql = "INSERT INTO orders(user_id, total_amount, status, created_at) VALUES (?, ?, ?, ?)";
            String insertItemSql = """
                    INSERT INTO order_items(order_id, product_id, product_name, category, size, quantity, base_price,
                    customization_fee, customized, custom_english, custom_japanese, subtotal)
                    VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                    """;
            String insertTxSql = "INSERT INTO transactions(order_id, amount, payment_method, created_at) VALUES (?, ?, ?, ?)";

            Random random = new Random(240401);
            String[] sizes = {"S", "M", "L", "XL"};
            String[] paymentMethods = {"Card", "FPX", "E-Wallet"};
            String[] customEnglish = {
                    "Move with purpose",
                    "Never stand still",
                    "Forward always",
                    "Built to create",
                    "Edge of tomorrow"
            };
            String[] customJapanese = {
                    "信念を貫け",
                    "立ち止まるな",
                    "前へ進め",
                    "創造を続けろ",
                    "未来を切り開け"
            };

            connection.setAutoCommit(false);
            try (PreparedStatement orderStmt = connection.prepareStatement(insertOrderSql, PreparedStatement.RETURN_GENERATED_KEYS);
                 PreparedStatement itemStmt = connection.prepareStatement(insertItemSql);
                 PreparedStatement txStmt = connection.prepareStatement(insertTxSql)) {

                for (LocalDate day = startDate; !day.isAfter(endDate); day = day.plusDays(1)) {
                    int ordersForDay = 2 + random.nextInt(3); // 2-4 orders per day

                    for (int i = 0; i < ordersForDay; i++) {
                        int userId = userIds.get(random.nextInt(userIds.size()));
                        int hour = 9 + random.nextInt(13);
                        int minute = random.nextInt(60);
                        int second = random.nextInt(60);
                        String createdAt = String.format("%s %02d:%02d:%02d", day, hour, minute, second);

                        String statusRoll;
                        int statusSeed = random.nextInt(100);
                        if (statusSeed < 60) {
                            statusRoll = "PAID";
                        } else if (statusSeed < 82) {
                            statusRoll = "PENDING";
                        } else {
                            statusRoll = "CANCELLED";
                        }

                        int lineItems = 1 + random.nextInt(2);
                        double orderTotal = 0;

                        orderStmt.setInt(1, userId);
                        orderStmt.setDouble(2, 0);
                        orderStmt.setString(3, statusRoll);
                        orderStmt.setString(4, createdAt);
                        orderStmt.executeUpdate();

                        int orderId;
                        try (ResultSet keys = orderStmt.getGeneratedKeys()) {
                            if (!keys.next()) {
                                throw new IllegalStateException("Failed to generate seeded order id");
                            }
                            orderId = keys.getInt(1);
                        }

                        for (int line = 0; line < lineItems; line++) {
                            ProductSeed product = products.get(random.nextInt(products.size()));
                            int quantity = 1 + random.nextInt(3);
                            boolean customized = random.nextInt(100) < 40;
                            double customizationFee = customized ? 20.0 : 0.0;
                            String cEn = customized ? customEnglish[random.nextInt(customEnglish.length)] : "";
                            String cJp = customized ? customJapanese[random.nextInt(customJapanese.length)] : "";
                            double lineSubtotal = (product.price + customizationFee) * quantity;
                            orderTotal += lineSubtotal;

                            itemStmt.setInt(1, orderId);
                            itemStmt.setInt(2, product.id);
                            itemStmt.setString(3, product.name);
                            itemStmt.setString(4, product.category);
                            itemStmt.setString(5, sizes[random.nextInt(sizes.length)]);
                            itemStmt.setInt(6, quantity);
                            itemStmt.setDouble(7, product.price);
                            itemStmt.setDouble(8, customizationFee);
                            itemStmt.setInt(9, customized ? 1 : 0);
                            itemStmt.setString(10, cEn);
                            itemStmt.setString(11, cJp);
                            itemStmt.setDouble(12, lineSubtotal);
                            itemStmt.executeUpdate();
                        }

                        try (PreparedStatement updateOrderTotal = connection.prepareStatement(
                                "UPDATE orders SET total_amount = ? WHERE id = ?")) {
                            updateOrderTotal.setDouble(1, orderTotal);
                            updateOrderTotal.setInt(2, orderId);
                            updateOrderTotal.executeUpdate();
                        }

                        if ("PAID".equals(statusRoll)) {
                            txStmt.setInt(1, orderId);
                            txStmt.setDouble(2, orderTotal);
                            txStmt.setString(3, paymentMethods[random.nextInt(paymentMethods.length)]);
                            txStmt.setString(4, createdAt);
                            txStmt.executeUpdate();
                        }
                    }
                }
                connection.commit();
            } catch (Exception ex) {
                connection.rollback();
                throw ex;
            } finally {
                connection.setAutoCommit(true);
            }
        } catch (SQLException exception) {
            throw new IllegalStateException("Failed to seed historical demo orders", exception);
        }
    }

    private void ensureExtraUsers(Connection connection) throws SQLException {
        String checkSql = "SELECT COUNT(1) FROM users WHERE username = ?";
        String insertSql = "INSERT INTO users(username, password, role) VALUES (?, ?, 'USER')";

        try (PreparedStatement checkStmt = connection.prepareStatement(checkSql);
             PreparedStatement insertStmt = connection.prepareStatement(insertSql)) {
            for (int i = 1; i <= 20; i++) {
                String username = String.format("demo_user_%02d", i);
                checkStmt.setString(1, username);
                boolean exists;
                try (ResultSet rs = checkStmt.executeQuery()) {
                    exists = rs.next() && rs.getInt(1) > 0;
                }
                if (!exists) {
                    insertStmt.setString(1, username);
                    insertStmt.setString(2, "Demo@123!");
                    insertStmt.addBatch();
                }
            }
            insertStmt.executeBatch();
        }
    }

    private String toCategoryLabel(String clothingType) {
        if ("T_SHIRT".equalsIgnoreCase(clothingType)) {
            return "T-shirt";
        }
        if ("SWEATSHIRT".equalsIgnoreCase(clothingType)) {
            return "Sweatshirt";
        }
        return "Hoodie";
    }
}
