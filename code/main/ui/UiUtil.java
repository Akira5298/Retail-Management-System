package tokyoera.ui;

import tokyoera.service.AssetPathResolver;
import java.text.DecimalFormat;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;
import javafx.animation.Animation;
import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.scene.control.Alert;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Label;
import javafx.scene.image.Image;
import javafx.util.Duration;

// A collection of static helper methods used across all the UI screens.
// Keeps common UI tasks (image loading, formatting, alerts) in one place.
public final class UiUtil {
    private static final DecimalFormat RM = new DecimalFormat("0.00");
    // Image cache so the same file doesn't get loaded from disk multiple times
    private static final Map<String, Image> IMAGE_CACHE = new ConcurrentHashMap<>();

    // Utility class — no instances needed
    private UiUtil() {
    }

    // Formats a double as a Malaysian Ringgit string, e.g. 12.5 -> "RM 12.50"
    public static String rm(double value) {
        return "RM " + RM.format(value);
    }

    // Shows a blocking error popup with the given message
    public static void error(String message) {
        Alert alert = new Alert(Alert.AlertType.ERROR, message, ButtonType.OK);
        alert.setHeaderText("Validation Error");
        alert.showAndWait();
    }

    // Shows a blocking info popup with a custom title and message
    public static void info(String title, String message) {
        Alert alert = new Alert(Alert.AlertType.INFORMATION, message, ButtonType.OK);
        alert.setHeaderText(title);
        alert.showAndWait();
    }

    // Tells the user a specific size is out of stock and asks them to pick another
    public static void sizeUnavailable(String size) {
        ButtonType exitBtn = new ButtonType("Exit", ButtonBar.ButtonData.OK_DONE);
        // Friendly popup used in storefront/cart when selected size cannot be bought right now.
        String message = "This size is temporarily out of stock. Please choose another size.";
        if (size != null && !size.isBlank()) {
            message = "Size " + size + " is temporarily out of stock. Please choose another size.";
        }
        Alert alert = new Alert(Alert.AlertType.INFORMATION, message, exitBtn);
        alert.setHeaderText("Size Unavailable");
        alert.showAndWait();
    }

    // Prompts the user with a Yes/No dialog; returns true if they clicked Yes
    public static boolean confirm(String title, String message) {
        Alert alert = new Alert(Alert.AlertType.CONFIRMATION, message, ButtonType.YES, ButtonType.NO);
        alert.setHeaderText(title);
        Optional<ButtonType> result = alert.showAndWait();
        return result.isPresent() && result.get() == ButtonType.YES;
    }

    // Loads an image by its base name, trying common extensions in order.
    // Results are cached so large gallery views don't hammer the filesystem.
    // Falls back to a placeholder image URL if no file is found.
    public static Image loadImageByBaseName(String baseName) {
        String[] extensions = new String[]{".jpg", ".jpeg", ".png", ".webp", ".avif"};
        for (String ext : extensions) {
            String filename = baseName + ext;
            Optional<java.nio.file.Path> path = AssetPathResolver.resolveExisting(filename);
            if (path.isPresent()) {
                String uri = path.get().toUri().toString();
                return IMAGE_CACHE.computeIfAbsent(uri, key -> new Image(key, false));
            }
        }
        String fallback = "https://dummyimage.com/300x300/111/00e6ff&text=TokyoEra";
        return IMAGE_CACHE.computeIfAbsent(fallback, key -> new Image(key, false));
    }

    // Creates the glitch animation used for the motto text on dashboards.
    // Every 85ms it randomly shifts/rotates/fades the label to look like a digital glitch.
    public static Timeline createMottoGlitchAnimation(Label label) {
        Timeline timeline = new Timeline(new KeyFrame(Duration.millis(85), event -> {
            ThreadLocalRandom random = ThreadLocalRandom.current();
            double baseX = random.nextDouble(-2.6, 2.6);
            double baseY = random.nextDouble(-3.2, 3.2);
            double baseRotate = random.nextDouble(-1.4, 1.4);
            double opacity = random.nextDouble(0.82, 1.0);

            if (random.nextDouble() < 0.14) {
                baseX += random.nextDouble(-6.0, 6.0);
                baseY += random.nextDouble(-4.0, 4.0);
                baseRotate += random.nextDouble(-3.0, 3.0);
                opacity = random.nextDouble(0.7, 0.94);
            }

            label.setTranslateX(baseX);
            label.setTranslateY(baseY);
            label.setRotate(baseRotate);
            label.setOpacity(opacity);
        }));
        timeline.setCycleCount(Animation.INDEFINITE);
        return timeline;
    }
}
