package tokyoera.ui;

import tokyoera.AppContext;
import tokyoera.model.User;
import tokyoera.service.AuthService;
import javafx.animation.Timeline;
import javafx.concurrent.Task;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.PasswordField;
import javafx.scene.control.Separator;
import javafx.scene.control.TextField;
import javafx.scene.layout.VBox;

import java.util.function.Consumer;

// This view shows the login screen with a glitchy animated title.
// It contains both the login form and a sign-up form that can be toggled into view.
public class LoginView {
    private final AppContext context;
    private final Consumer<User> onSuccess;

    public LoginView(AppContext context, Consumer<User> onSuccess) {
        this.context = context;
        this.onSuccess = onSuccess;
    }

    public Scene build() {
        // ── Shared heading ── glitch animation plays in a loop on the store title
        Label title = new Label("~Tokyo Era~ by Akira Fukutomi");
        title.getStyleClass().addAll("heading", "motto-heading");
        Timeline titleGlitch = UiUtil.createMottoGlitchAnimation(title);
        titleGlitch.play();

        // ── Login form ───────────────────────────────────────────────────
        TextField loginUsernameField = new TextField();
        loginUsernameField.setPromptText("Username");

        PasswordField loginPasswordField = new PasswordField();
        loginPasswordField.setPromptText("Password");

        Label loginErrorLabel = new Label();
        loginErrorLabel.setStyle("-fx-text-fill: #ff3b30; -fx-font-weight: bold;");

        Button loginButton = new Button("Login");
        loginButton.getStyleClass().add("neon-btn");
        loginButton.setMaxWidth(Double.MAX_VALUE);

        loginButton.setOnAction(e -> {
            String username = loginUsernameField.getText() == null ? "" : loginUsernameField.getText().trim();
            String password = loginPasswordField.getText() == null ? "" : loginPasswordField.getText().trim();
            if (username.isBlank() || password.isBlank()) {
                loginErrorLabel.setText("Username and password are required.");
                return;
            }

            loginErrorLabel.setStyle("-fx-text-fill: #39ff88; -fx-font-weight: bold;");
            loginErrorLabel.setText("Loading...");
            loginButton.setDisable(true);

            // Run the login on a background thread so the UI doesn't freeze
            Task<java.util.Optional<User>> loginTask = new Task<>() {
                @Override
                protected java.util.Optional<User> call() {
                    return context.authService().login(username, password);
                }
            };

            // If succeeded
            loginTask.setOnSucceeded(ev -> {
                loginButton.setDisable(false);
                loginTask.getValue().ifPresentOrElse(onSuccess, () ->
                {
                    loginErrorLabel.setStyle("-fx-text-fill: #ff3b30; -fx-font-weight: bold;");
                    loginErrorLabel.setText("Wrong username or password.");
                });
            });

            // If failed
            loginTask.setOnFailed(ev -> {
                loginButton.setDisable(false);
                Throwable exception = loginTask.getException();
                // AuthService throws AccountLockedException when too many failed attempts
                if (exception instanceof AuthService.AccountLockedException locked) {
                    loginErrorLabel.setStyle("-fx-text-fill: #ff3b30; -fx-font-weight: bold;");
                    loginErrorLabel.setText(locked.getMessage());
                } else {
                    loginErrorLabel.setStyle("-fx-text-fill: #ff3b30; -fx-font-weight: bold;");
                    loginErrorLabel.setText("Login failed. Please try again.");
                }
            });

            Thread loginThread = new Thread(loginTask, "login-worker");
            loginThread.setDaemon(true);
            loginThread.start();
        });

        // ── Sign-up form ─────────────────────────────────────────────────
        TextField signUpUsernameField = new TextField();
        signUpUsernameField.setPromptText("Choose a username");

        PasswordField signUpPasswordField = new PasswordField();
        signUpPasswordField.setPromptText("Password: 8 + chars, include uppercase, number, special char");

        PasswordField signUpConfirmField = new PasswordField();
        signUpConfirmField.setPromptText("Confirm password");

        Label signUpStatusLabel = new Label();
        signUpStatusLabel.setStyle("-fx-text-fill: #ff6b6b;");
        signUpStatusLabel.setWrapText(true);

        Button signUpButton = new Button("Create Account");
        signUpButton.getStyleClass().add("neon-btn");
        signUpButton.setMaxWidth(Double.MAX_VALUE);

        signUpButton.setOnAction(e -> {
            // Basic front-end validation before calling the service
            String username = signUpUsernameField.getText() == null ? "" : signUpUsernameField.getText().trim();
            String password = signUpPasswordField.getText() == null ? "" : signUpPasswordField.getText();
            String confirm  = signUpConfirmField.getText()  == null ? "" : signUpConfirmField.getText();

            if (username.isBlank()) {
                signUpStatusLabel.setStyle("-fx-text-fill: #ff6b6b;");
                signUpStatusLabel.setText("Username cannot be empty.");
                return;
            }
            if (!password.equals(confirm)) {
                signUpStatusLabel.setStyle("-fx-text-fill: #ff6b6b;");
                signUpStatusLabel.setText("Passwords do not match.");
                return;
            }

            context.authService().register(username, password).ifPresentOrElse(
                    error -> {
                        signUpStatusLabel.setStyle("-fx-text-fill: #ff6b6b;");
                        signUpStatusLabel.setText(error);
                    },
                    () -> {
                        signUpStatusLabel.setStyle("-fx-text-fill: #39ff88;");
                        signUpStatusLabel.setText("Account created! You can now log in.");
                        signUpUsernameField.clear();
                        signUpPasswordField.clear();
                        signUpConfirmField.clear();
                    }
            );
        });

        VBox signUpBox = new VBox(8,
                new Label("New here? Create an account:"),
                signUpUsernameField,
                signUpPasswordField,
                signUpConfirmField,
                signUpButton,
                signUpStatusLabel
        );
        signUpBox.setVisible(false);
        signUpBox.setManaged(false);

        // ── Toggle link ──────────────────────────────────────────────────
        Button toggleButton = new Button("Don't have an account? Sign up");
        toggleButton.setStyle("-fx-background-color: transparent; -fx-text-fill: #7FF8FF; " +
                "-fx-border-color: transparent; -fx-cursor: hand; -fx-underline: true;");

            // Show or hide the sign-up form and change the toggle button text accordingly
            toggleButton.setOnAction(e -> {
            boolean showing = signUpBox.isVisible();
            signUpBox.setVisible(!showing);
            signUpBox.setManaged(!showing);
            toggleButton.setText(showing
                    ? "Don't have an account? Sign up"
                    : "Already have an account? Log in");
            loginErrorLabel.setText("");
            signUpStatusLabel.setText("");
        });

        // ── Assemble card ────────────────────────────────────────────────
        VBox card = new VBox(10,
                title,
                new Separator(),
                loginUsernameField,
                loginPasswordField,
                loginButton,
                loginErrorLabel,
                toggleButton,
                signUpBox
        );
        card.setPadding(new Insets(24));
        card.setAlignment(Pos.CENTER_LEFT);
        card.getStyleClass().add("panel");
        card.setMaxWidth(440);

        VBox root = new VBox(card);
        root.setAlignment(Pos.CENTER);
        root.setPadding(new Insets(40));

        Scene scene = new Scene(root, 1280, 800);
        scene.getStylesheets().add(getClass().getResource("/tokyoera/styles.css").toExternalForm());
        scene.windowProperty().addListener((obs, oldWindow, newWindow) -> {
            if (newWindow != null) {
                newWindow.setOnHidden(event -> titleGlitch.stop());
            }
        });
        return scene;
    }
}

