package tokyoera;

import tokyoera.model.User;
import tokyoera.service.AuthService;
import tokyoera.service.CartService;
import tokyoera.service.CouponService;
import tokyoera.service.DatabaseManager;
import tokyoera.service.OrderService;
import tokyoera.service.PdfService;
import tokyoera.service.ProductService;
import tokyoera.service.ReportService;
import tokyoera.service.MusicService;
import tokyoera.service.SettingsService;

// Acts like a shared toolbox for the whole application.
// Creates every service exactly once and passes them to UI classes that need them.
// Also keeps track of who is currently logged in via currentUser.
public class AppContext {
    private final DatabaseManager databaseManager;
    private final AuthService authService;
    private final ProductService productService;
    private final CartService cartService;
    private final OrderService orderService;
    private final ReportService reportService;
    private final PdfService pdfService;
    private final MusicService musicService;
    private final SettingsService settingsService;
    private final CouponService couponService;
    private User currentUser; // null when nobody is logged in

    public AppContext() {
        // Create services in dependency order: DatabaseManager first, then everything that needs it
        this.databaseManager = new DatabaseManager();
        this.authService = new AuthService(databaseManager);
        this.productService = new ProductService(databaseManager);
        this.cartService = new CartService(databaseManager, productService);
        this.orderService = new OrderService(databaseManager, productService);
        this.reportService = new ReportService(databaseManager);
        this.pdfService = new PdfService();
        this.musicService = new MusicService();
        this.settingsService = new SettingsService(databaseManager);
        this.couponService = new CouponService(databaseManager);
    }

    /** Returns the shared SQLite database connection manager. */
    public DatabaseManager databaseManager() {
        return databaseManager;
    }

    /** Handles login / password verification. */
    public AuthService authService() {
        return authService;
    }

    /** CRUD operations for products in the catalogue. */
    public ProductService productService() {
        return productService;
    }

    /** Manages the in-memory shopping cart and persists it to the DB. */
    public CartService cartService() {
        return cartService;
    }

    /** Creates orders and updates stock on checkout. */
    public OrderService orderService() {
        return orderService;
    }

    /** Queries aggregated sales data for the admin reports tab. */
    public ReportService reportService() {
        return reportService;
    }

    /** Generates PDF receipts using OpenPDF. */
    public PdfService pdfService() {
        return pdfService;
    }

    /** Plays (and stops) the background music track. */
    public MusicService musicService() {
        return musicService;
    }

    /** Reads / writes global app settings stored in the DB. */
    public SettingsService settingsService() {
        return settingsService;
    }

    /** Validates and applies discount coupon codes at checkout. */
    public CouponService couponService() {
        return couponService;
    }

    /** Returns the currently logged-in user, or null if nobody is logged in. */
    public User currentUser() {
        return currentUser;
    }

    /** Set when a user logs in; cleared to null on logout. */
    public void setCurrentUser(User currentUser) {
        this.currentUser = currentUser;
    }
}
