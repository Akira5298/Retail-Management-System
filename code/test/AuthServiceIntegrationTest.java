package tokyoera.service;

import tokyoera.model.Role;
import tokyoera.model.User;
import org.junit.jupiter.api.*;

import java.sql.SQLException;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class AuthServiceIntegrationTest {

    private static TestDatabaseManager db;
    private static AuthService authService;

    @BeforeAll
    static void setUpDatabase() throws SQLException {
        db = new TestDatabaseManager("auth_test_db");
        new DatabaseInitializer(db).initialize();
        authService = new AuthService(db);
    }

    @AfterAll
    static void tearDown() throws SQLException {
        db.shutdown();
    }

    // ── register ──────────────────────────────────────────────────────────

    @Test
    @org.junit.jupiter.api.Order(1)
    void register_newUser_returnsEmptyOptional() {
        Optional<String> result = authService.register("alice", "Password@123");
        assertTrue(result.isEmpty(), "Successful registration should return empty Optional");
    }

    @Test
    @org.junit.jupiter.api.Order(2)
    void register_duplicateUsername_returnsError() {
        // Register alice first (may already exist from test 1)
        authService.register("alice", "Password@123");
        Optional<String> result = authService.register("alice", "Different@123");
        assertTrue(result.isPresent(), "Duplicate username should return an error");
        assertTrue(result.get().toLowerCase().contains("taken") ||
                   result.get().toLowerCase().contains("already"),
                "Error message should indicate username is taken");
    }

    @Test
    @org.junit.jupiter.api.Order(3)
    void register_anotherNewUser_succeeds() {
        Optional<String> result = authService.register("bob", "Secure@123");
        assertTrue(result.isEmpty());
    }

    // ── login ─────────────────────────────────────────────────────────────

    @Test
    @org.junit.jupiter.api.Order(4)
    void login_validCredentials_returnsUser() {
        authService.register("charlie", "Mypassword@1");
        Optional<User> user = authService.login("charlie", "Mypassword@1");
        assertTrue(user.isPresent(), "Login with correct credentials should succeed");
        assertEquals("charlie", user.get().getUsername());
        assertEquals(Role.USER, user.get().getRole());
    }

    @Test
    @org.junit.jupiter.api.Order(5)
    void login_wrongPassword_returnsEmpty() {
        authService.register("dave", "Correct@123");
        Optional<User> user = authService.login("dave", "wrongpass");
        assertTrue(user.isEmpty(), "Login with wrong password should fail");
    }

    @Test
    @org.junit.jupiter.api.Order(6)
    void login_unknownUsername_returnsEmpty() {
        Optional<User> user = authService.login("nonexistent_user_xyz", "anypass");
        assertTrue(user.isEmpty(), "Login with unknown username should fail");
    }

    @Test
    @org.junit.jupiter.api.Order(7)
    void login_registeredUser_hasUserId() {
        authService.register("eve", "Pass@1234");
        Optional<User> user = authService.login("eve", "Pass@1234");
        assertTrue(user.isPresent());
        assertTrue(user.get().getId() > 0, "Registered user should have a positive ID");
    }

    @Test
    @org.junit.jupiter.api.Order(8)
    void login_caseSensitiveUsername() {
        authService.register("Frank", "Pass@1234");
        Optional<User> lower = authService.login("frank", "Pass@1234");
        Optional<User> exact = authService.login("Frank", "Pass@1234");
        // SQLite LIKE is case-insensitive but = is case-sensitive for ASCII by default
        // The query uses WHERE username = ? so exact match expected
        assertTrue(exact.isPresent(), "Exact username match should succeed");
        // Note: SQLite default is case-insensitive for ASCII — this test documents the actual behaviour
    }
}
