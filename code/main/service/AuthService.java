package tokyoera.service;

import tokyoera.model.Role;
import tokyoera.model.User;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.time.format.DateTimeParseException;
import java.util.Optional;
import java.util.regex.Pattern;

// This service handles everything to do with logging in and registering accounts.
// It also enforces a lockout policy: too many wrong passwords temporarily blocks the account.
public class AuthService {
    private static final int MAX_FAILED_ATTEMPTS = 5;   // lock after this many bad passwords
    private static final int LOCKOUT_MINUTES = 5;       // how long the account stays locked
    private static final Pattern HAS_UPPERCASE = Pattern.compile(".*[A-Z].*");
    private static final Pattern HAS_NUMBER = Pattern.compile(".*\\d.*");
    private static final Pattern HAS_SPECIAL = Pattern.compile(".*[^A-Za-z0-9].*");

    /** Thrown when a login attempt is made on a locked account. */
    public static class AccountLockedException extends RuntimeException {
        public AccountLockedException(String message) {
            super(message);
        }
    }

    private final DatabaseManager databaseManager;

    public AuthService(DatabaseManager databaseManager) {
        this.databaseManager = databaseManager;
    }

    /**
     * Attempts to log in with the given credentials.
     *
     * @return the authenticated User, or empty on wrong password
     * @throws AccountLockedException if the account is currently locked
     */
    public Optional<User> login(String username, String password) {
        String sql = "SELECT id, username, password, role, failed_attempts, locked_until FROM users WHERE username = ?";
        try (Connection connection = databaseManager.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, username);
            try (ResultSet resultSet = statement.executeQuery()) {
                if (!resultSet.next()) {
                    // Username doesn't exist at all
                    return Optional.empty();
                }

                int userId = resultSet.getInt("id");
                String storedHash = resultSet.getString("password");
                String roleStr = resultSet.getString("role");
                int failedAttempts = resultSet.getInt("failed_attempts");
                String lockedUntil = resultSet.getString("locked_until");

                // ── Lockout check ────────────────────────────────────────────────
                // If the account has a locked_until time and it hasn't passed yet, reject login
                if (lockedUntil != null && !lockedUntil.isBlank()) {
                    try {
                        LocalDateTime lockExpiry = LocalDateTime.parse(lockedUntil);
                        if (LocalDateTime.now().isBefore(lockExpiry)) {
                            long secondsLeft = java.time.Duration.between(LocalDateTime.now(), lockExpiry).getSeconds();
                            long minutesLeft = (secondsLeft / 60) + 1;
                            throw new AccountLockedException(
                                    "Account locked due to too many failed attempts. Try again in " + minutesLeft + " minute(s).");
                        } else {
                            // Lock expired — reset the counter so the user can try again
                            resetFailedAttempts(userId);
                        }
                    } catch (DateTimeParseException ignored) {
                        resetFailedAttempts(userId);
                    }
                }

                // ── Password check ───────────────────────────────────────────────
                // Compare the entered password against the stored one (plaintext per coursework spec)
                boolean valid = password.equals(storedHash);

                if (!valid) {
                    // Wrong password — increment failure counter (may trigger lockout)
                    incrementFailedAttempts(userId, failedAttempts + 1);
                    return Optional.empty();
                }

                // Login succeeded — reset any previous failure count
                resetFailedAttempts(userId);
                return Optional.of(new User(userId, username, storedHash, Role.valueOf(roleStr)));
            }
        } catch (AccountLockedException e) {
            throw e;
        } catch (SQLException exception) {
            throw new IllegalStateException("Login failed", exception);
        }
    }

    /**
     * Registers a new USER account.
     *
     * @return empty Optional on success, or a non-empty Optional with an error message
     */
    public Optional<String> register(String username, String password) {
        // Basic validation first — don't even hit the database with bad input
        if (username == null || username.isBlank()) return Optional.of("Username cannot be empty.");
        Optional<String> passwordValidation = validatePasswordPolicy(password);
        if (passwordValidation.isPresent()) return passwordValidation;

        // Check that the username isn't already taken
        String checkSql = "SELECT id FROM users WHERE username = ?";
        try (Connection conn = databaseManager.getConnection();
             PreparedStatement check = conn.prepareStatement(checkSql)) {
            check.setString(1, username.trim());
            try (ResultSet rs = check.executeQuery()) {
                if (rs.next()) return Optional.of("Username already taken. Please choose another.");
            }
        } catch (SQLException e) {
            throw new IllegalStateException("Registration check failed", e);
        }

        // All good — insert the new user with role USER
        String insertSql = "INSERT INTO users (username, password, role) VALUES (?, ?, 'USER')";
        try (Connection conn = databaseManager.getConnection();
             PreparedStatement stmt = conn.prepareStatement(insertSql)) {
            stmt.setString(1, username.trim());
            stmt.setString(2, password);
            stmt.executeUpdate();
            return Optional.empty(); // empty = success
        } catch (SQLException e) {
            throw new IllegalStateException("Registration failed", e);
        }
    }

    // Checks the password against our security rules:
    // must be at least 8 chars, include uppercase, a digit, and a special character
    private Optional<String> validatePasswordPolicy(String password) {
        if (password == null || password.isBlank()) {
            return Optional.of("Password cannot be empty.");
        }
        if (password.length() < 8) {
            return Optional.of("Password must be at least 8 characters.");
        }
        if (!HAS_UPPERCASE.matcher(password).matches()) {
            return Optional.of("Password must include at least one uppercase letter.");
        }
        if (!HAS_NUMBER.matcher(password).matches()) {
            return Optional.of("Password must include at least one number.");
        }
        if (!HAS_SPECIAL.matcher(password).matches()) {
            return Optional.of("Password must include at least one special character.");
        }
        return Optional.empty();
    }

    // Increment the failure counter; if it hits the max, set a lockout timestamp
    private void incrementFailedAttempts(int userId, int newCount) {
        String lockUntil = null;
        if (newCount >= MAX_FAILED_ATTEMPTS) {
            lockUntil = LocalDateTime.now().plusMinutes(LOCKOUT_MINUTES).toString();
        }
        String sql = "UPDATE users SET failed_attempts = ?, locked_until = ? WHERE id = ?";
        try (Connection conn = databaseManager.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setInt(1, newCount);
            stmt.setString(2, lockUntil);
            stmt.setInt(3, userId);
            stmt.executeUpdate();
        } catch (SQLException e) {
            // Non-critical; log silently
        }
    }

    // Clear the failure counter and remove any lockout after a successful login
    private void resetFailedAttempts(int userId) {
        String sql = "UPDATE users SET failed_attempts = 0, locked_until = NULL WHERE id = ?";
        try (Connection conn = databaseManager.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setInt(1, userId);
            stmt.executeUpdate();
        } catch (SQLException e) {
            // Non-critical; log silently
        }
    }
}
