package tokyoera.service;

import tokyoera.model.CartItem;
import tokyoera.model.Product;

import javafx.collections.FXCollections;
import javafx.collections.ObservableList;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

// This service manages the shopping cart.
// Items are stored in memory as an ObservableList so the UI can react to changes,
// and can also be saved/loaded from the database so the cart survives between sessions.
public class CartService {
    // The live in-memory cart — UI elements observe this list for changes
    private final ObservableList<CartItem> items = FXCollections.observableArrayList();
    private final DatabaseManager databaseManager;
    private final ProductService productService;

    /** No-arg constructor for tests — persistence disabled. */
    public CartService() {
        this.databaseManager = null;
        this.productService = null;
    }

    /** Full constructor for production — enables SQLite cart persistence. */
    public CartService(DatabaseManager databaseManager, ProductService productService) {
        this.databaseManager = databaseManager;
        this.productService = productService;
    }

    public ObservableList<CartItem> getItems() {
        return items;
    }

    public void addToCart(Product product, String size, int quantity, boolean customized, String customEn, String customJp) {
        // If the same product+size+customization combo is already in the cart, just increase the qty
        for (CartItem existing : items) {
            if (existing.getProduct().getId() == product.getId()
                    && existing.getSize().equals(size)
                    && existing.isCustomized() == customized
                    && Objects.equals(existing.getCustomEnglish(), customEn)
                    && Objects.equals(existing.getCustomJapanese(), customJp)) {
                existing.setQuantity(existing.getQuantity() + quantity);
                return;
            }
        }
        // Otherwise add it as a brand new cart entry
        items.add(new CartItem(product, size, quantity, customized, customEn, customJp));
    }

    public void remove(CartItem item) {
        items.remove(item);
    }

    public void clear() {
        items.clear();
    }

    public int itemCount() {
        return items.stream().mapToInt(CartItem::getQuantity).sum();
    }

    public double total() {
        return items.stream().mapToDouble(CartItem::subtotal).sum();
    }

    public List<CartItem> snapshot() {
        return List.copyOf(items);
    }

    // ── Persistence ───────────────────────────────────────────────────────

    /**
     * Loads the persisted cart for the given user from the database.
     * Replaces the current in-memory cart contents.
     */
    public void loadCart(int userId) {
        if (databaseManager == null || productService == null) return;
        items.clear();
        String sql = "SELECT product_id, size, quantity, customized, custom_english, custom_japanese FROM cart WHERE user_id = ?";
        try (Connection conn = databaseManager.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setInt(1, userId);
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    int productId = rs.getInt("product_id");
                    Optional<Product> product = productService.findById(productId);
                    if (product.isEmpty()) continue;
                    String size = rs.getString("size");
                    int quantity = rs.getInt("quantity");
                    boolean customized = rs.getInt("customized") == 1;
                    String customEn = rs.getString("custom_english");
                    String customJp = rs.getString("custom_japanese");
                    items.add(new CartItem(product.get(), size, quantity, customized, customEn, customJp));
                }
            }
        } catch (SQLException exception) {
            // Non-fatal — start with empty cart
        }
    }

    /**
     * Persists the current in-memory cart to the database for the given user,
     * replacing any previously saved cart.
     */
    public void saveCart(int userId) {
        if (databaseManager == null) return;
        String deleteSql = "DELETE FROM cart WHERE user_id = ?";
        String insertSql = "INSERT INTO cart(user_id, product_id, size, quantity, customized, custom_english, custom_japanese) VALUES (?,?,?,?,?,?,?)";
        try (Connection conn = databaseManager.getConnection()) {
            conn.setAutoCommit(false);
            try (PreparedStatement del = conn.prepareStatement(deleteSql);
                 PreparedStatement ins = conn.prepareStatement(insertSql)) {
                del.setInt(1, userId);
                del.executeUpdate();
                for (CartItem item : items) {
                    ins.setInt(1, userId);
                    ins.setInt(2, item.getProduct().getId());
                    ins.setString(3, item.getSize());
                    ins.setInt(4, item.getQuantity());
                    ins.setInt(5, item.isCustomized() ? 1 : 0);
                    ins.setString(6, item.getCustomEnglish());
                    ins.setString(7, item.getCustomJapanese());
                    ins.addBatch();
                }
                ins.executeBatch();
                conn.commit();
            } catch (Exception ex) {
                conn.rollback();
                throw ex;
            } finally {
                conn.setAutoCommit(true);
            }
        } catch (SQLException exception) {
            // Non-fatal — cart save failed silently
        }
    }

    /** Removes the persisted cart for the user (e.g. after checkout or cancel). */
    public void clearSavedCart(int userId) {
        if (databaseManager == null) return;
        String sql = "DELETE FROM cart WHERE user_id = ?";
        try (Connection conn = databaseManager.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setInt(1, userId);
            stmt.executeUpdate();
        } catch (SQLException exception) {
            // Non-fatal
        }
    }
}
