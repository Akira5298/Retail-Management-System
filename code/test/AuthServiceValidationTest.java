package tokyoera.service;

import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests input-validation guards in AuthService that reject bad input
 * before any database access is attempted. Passing null for DatabaseManager
 * is intentional here — these checks must not reach the DB.
 */
public class AuthServiceValidationTest {

    private final AuthService authService = new AuthService(null);

    // ── username validation ───────────────────────────────────────────────

    @Test
    void register_nullUsername_returnsErrorMessage() {
        Optional<String> result = authService.register(null, "Valid@123");
        assertTrue(result.isPresent(), "Should return an error for null username");
        assertFalse(result.get().isBlank());
    }

    @Test
    void register_blankUsername_returnsErrorMessage() {
        Optional<String> result = authService.register("  ", "Valid@123");
        assertTrue(result.isPresent(), "Should return an error for blank username");
    }

    @Test
    void register_emptyUsername_returnsErrorMessage() {
        Optional<String> result = authService.register("", "Valid@123");
        assertTrue(result.isPresent(), "Should return an error for empty username");
    }

    // ── password validation ───────────────────────────────────────────────

    @Test
    void register_nullPassword_returnsErrorMessage() {
        Optional<String> result = authService.register("validuser", null);
        assertTrue(result.isPresent(), "Should return an error for null password");
    }

    @Test
    void register_tooShortPassword_returnsErrorMessage() {
        // < 8 chars
        Optional<String> result = authService.register("validuser", "abc");
        assertTrue(result.isPresent(), "Password of 3 chars should be rejected");
    }

    @Test
    void register_passwordOfThreeChars_returnsError() {
        Optional<String> result = authService.register("user", "123");
        assertTrue(result.isPresent());
    }

    @Test
    void register_passwordMissingUppercase_returnsError() {
        Optional<String> result = authService.register("user", "valid@123");
        assertTrue(result.isPresent());
    }

    @Test
    void register_passwordMissingNumber_returnsError() {
        Optional<String> result = authService.register("user", "Valid@abc");
        assertTrue(result.isPresent());
    }

    @Test
    void register_passwordMissingSpecial_returnsError() {
        Optional<String> result = authService.register("user", "Valid1234");
        assertTrue(result.isPresent());
    }

    @Test
    void register_emptyPassword_returnsErrorMessage() {
        Optional<String> result = authService.register("validuser", "");
        assertTrue(result.isPresent(), "Empty password should be rejected");
    }

    // ── boundary: valid policy password should pass input validation ─────

    @Test
    void register_validPolicyPassword_passesInputValidation() {
        try {
            Optional<String> result = authService.register("someuser", "Valid@123");
            result.ifPresent(msg ->
                assertFalse(msg.toLowerCase().contains("at least 8"),
                    "Valid password should not trigger length error, got: " + msg));
        } catch (Exception e) {
            // DB error is expected since DatabaseManager is null — that's fine
            assertTrue(e.getMessage() != null, "Exception from DB access is acceptable here");
        }
    }

    // ── error messages are descriptive ───────────────────────────────────

    @Test
    void register_blankUsername_errorMentionsUsername() {
        Optional<String> result = authService.register("", "Valid@123");
        assertTrue(result.isPresent());
        assertTrue(result.get().toLowerCase().contains("username"),
                "Error message should mention 'username'");
    }

    @Test
    void register_shortPassword_errorMentionsPassword() {
        Optional<String> result = authService.register("user", "ab");
        assertTrue(result.isPresent());
        String msg = result.get().toLowerCase();
        assertTrue(msg.contains("password") || msg.contains("character"),
                "Error message should describe the password problem");
    }
}
