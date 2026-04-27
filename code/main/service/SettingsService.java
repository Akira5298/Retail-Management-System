package tokyoera.service;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

// This service reads and writes simple key-value settings stored in the database.
// Things like the low stock threshold are stored here so the admin can change them.
public class SettingsService {
    private final DatabaseManager databaseManager;

    public SettingsService(DatabaseManager databaseManager) {
        this.databaseManager = databaseManager;
    }

    // Look up a setting by key and parse it as an integer.
    // If the key doesn't exist or the value isn't a number, return the default.
    public int getInt(String key, int defaultValue) {
        String sql = "SELECT value FROM settings WHERE key = ?";
        try (Connection connection = databaseManager.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, key);
            try (ResultSet resultSet = statement.executeQuery()) {
                if (resultSet.next()) {
                    return Integer.parseInt(resultSet.getString("value"));
                }
            }
        } catch (SQLException | NumberFormatException ignored) {
            // fall through to default
        }
        return defaultValue;
    }

    // Look up a setting by key and return it as a String.
    // Returns the provided default if the key isn't found.
    public String get(String key, String defaultValue) {
        String sql = "SELECT value FROM settings WHERE key = ?";
        try (Connection connection = databaseManager.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, key);
            try (ResultSet resultSet = statement.executeQuery()) {
                if (resultSet.next()) {
                    return resultSet.getString("value");
                }
            }
        } catch (SQLException ignored) {
            // fall through to default
        }
        return defaultValue;
    }

    // Save or update a setting — uses "INSERT OR REPLACE" (upsert) so we don't need
    // to check if the key already exists before writing
    public void set(String key, String value) {
        String sql = "INSERT INTO settings(key, value) VALUES(?, ?) "
                + "ON CONFLICT(key) DO UPDATE SET value = excluded.value";
        try (Connection connection = databaseManager.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, key);
            statement.setString(2, value);
            statement.executeUpdate();
        } catch (SQLException exception) {
            throw new IllegalStateException("Failed to save setting: " + key, exception);
        }
    }
}
