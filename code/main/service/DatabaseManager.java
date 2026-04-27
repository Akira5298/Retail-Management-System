package tokyoera.service;

import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;

// This class handles database connections.
// It figures out the absolute path to the SQLite file and keeps the URL ready.
public class DatabaseManager {
    private final String jdbcUrl;

    // Work out where "tokyoera.db" lives on disk and build the JDBC URL from it
    public DatabaseManager() {
        Path dbPath = Path.of("tokyoera.db").toAbsolutePath();
        this.jdbcUrl = "jdbc:sqlite:" + dbPath;
    }

    // Opens and returns a fresh database connection — caller must close it when done
    public Connection getConnection() throws SQLException {
        return DriverManager.getConnection(jdbcUrl);
    }
}
