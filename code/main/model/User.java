package tokyoera.model;

// Represents a logged-in account — could be a regular user or an admin.
// All fields are immutable after creation; the Role controls what the user can see.
public class User {
    private final int id;         // primary key from the users table
    private final String username;
    private final String password; // stored as BCrypt hash, never plaintext
    private final Role role;       // USER or ADMIN

    public User(int id, String username, String password, Role role) {
        // Guard clauses: reject obviously invalid data early
        if (username == null || username.isBlank()) {
            throw new IllegalArgumentException("Username cannot be null or blank");
        }
        if (password == null || password.isBlank()) {
            throw new IllegalArgumentException("Password cannot be null or blank");
        }
        if (role == null) {
            throw new IllegalArgumentException("Role cannot be null");
        }
        this.id = id;
        this.username = username;
        this.password = password;
        this.role = role;
    }

    public int getId() {
        return id;
    }

    public String getUsername() {
        return username;
    }

    public String getPassword() {
        return password;
    }

    public Role getRole() {
        return role;
    }
}
