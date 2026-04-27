package tokyoera.model;

// Simple enum to tell if someone is a regular user or an admin.
// We use this in App.java to route users to the correct dashboard after login.
public enum Role {
    USER,   // regular shopper
    ADMIN   // store manager with access to product/order management
}
