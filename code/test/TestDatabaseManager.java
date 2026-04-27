package tokyoera.service;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;

/**
 * A DatabaseManager that stores data in a named shared-cache in-memory SQLite database.
 * One keep-alive connection is held open so the in-memory DB is not destroyed between calls.
 * Call {@link #shutdown()} in {@code @AfterAll} to release it.
 */
public class TestDatabaseManager extends DatabaseManager {

    private final String url;
    private Connection keepAlive;

    public TestDatabaseManager(String uniqueName) throws SQLException {
        // Parent constructor sets an unused private field. We override getConnection().
        this.url = "jdbc:sqlite:file:" + uniqueName + "?mode=memory&cache=shared";
        this.keepAlive = DriverManager.getConnection(url);
    }

    @Override
    public Connection getConnection() throws SQLException {
        return DriverManager.getConnection(url);
    }

    public void shutdown() throws SQLException {
        if (keepAlive != null && !keepAlive.isClosed()) {
            keepAlive.close();
        }
    }
}
