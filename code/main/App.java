package tokyoera;

import tokyoera.model.Role;
import tokyoera.model.User;
import tokyoera.service.AssetValidationService;
import tokyoera.service.DatabaseInitializer;
import tokyoera.service.SeedDataService;
import tokyoera.ui.AdminDashboardView;
import tokyoera.ui.LoginView;
import tokyoera.ui.UiUtil;
import tokyoera.ui.UserDashboardView;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.scene.Scene;
import javafx.stage.Stage;

// This is the main JavaFX Application class — the entry point of the whole app.
// It wires up everything on startup: asset validation, DB init, music, and login screen.
public class App extends Application {
    private final AppContext context = new AppContext(); // shared services container

    @Override
    public void start(Stage stage) {
        // First make sure all required image/music assets are present on disk
        // Using try and catch
        try {
            AssetValidationService.validateRequiredAssets();
        } catch (IllegalStateException exception) {
            UiUtil.error(exception.getMessage());
            Platform.exit();
            return;
        }

        // Set up the SQLite database and fill it with sample/seed data
        new DatabaseInitializer(context.databaseManager()).initialize();
        new SeedDataService(context.databaseManager()).seed();

        // Start background music immediately
        context.musicService().start();

        stage.setTitle("~Tokyo Era~ by Akira Fukutomi");
        showLogin(stage);         // show the login screen first
        stage.setMaximized(true); // open in full window
        stage.show();
    }

    // Called when the window is closed — cleans up background music
    @Override
    public void stop() {
        context.musicService().stop();
    }

    // Builds the login scene and listens for a successful login.
    // Once logged in, routes to admin or user dashboard based on role.
    private void showLogin(Stage stage) {
        Scene scene = new LoginView(context, user -> {
            context.setCurrentUser(user);
            if (user.getRole() == Role.ADMIN) {
                showAdmin(stage, user);
            } else {
                showUser(stage, user);
            }
        }).build();
        stage.setScene(scene);
    }

    // Loads the user's saved cart, then shows the storefront dashboard.
    // On logout: saves the cart, turns it into a pending order if not empty, then goes back to login.
    // Using try and catch
    private void showUser(Stage stage, User user) {
        context.cartService().loadCart(user.getId());
        Scene scene = new UserDashboardView(context, user, () -> {
            context.cartService().saveCart(user.getId());
            if (!context.cartService().snapshot().isEmpty()) {
                try {
                    // Persist the unpaid cart as a PENDING order so nothing is lost
                    context.orderService().saveCartAsOrder(user, context.cartService().snapshot(), "PENDING");
                } catch (Exception exception) {
                    UiUtil.error("Failed to save pending cart before logout: " + exception.getMessage());
                }
            }
            context.cartService().clear();
            context.setCurrentUser(null);
            showLogin(stage);
        }).build();
        stage.setScene(scene);
    }

    // Shows the admin management dashboard (products, orders, reports, etc.).
    // On logout: clears the current user and returns to the login screen.
    private void showAdmin(Stage stage, User user) {
        Scene scene = new AdminDashboardView(context, user, () -> {
            context.setCurrentUser(null);
            showLogin(stage);
        }).build();
        stage.setScene(scene);
    }

    // Standard JavaFX entry point — just calls launch() which triggers start()
    public static void main(String[] args) {
        launch(args);
    }
}
