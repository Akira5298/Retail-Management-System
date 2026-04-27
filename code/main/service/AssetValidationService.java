package tokyoera.service;

import java.util.ArrayList;
import java.util.List;

// Checks that all required assets (product images + music) are on disk before the app starts.
// If anything is missing we throw early so the user gets a clear error, not a crash later.
public final class AssetValidationService {
    // Utility class — no instances needed
    private AssetValidationService() {
    }

    // Scans for every expected product image (Product1a.jpg … Product4j.jpg) and the music file.
    // Throws IllegalStateException listing every missing file if any are not found.
    public static void validateRequiredAssets() {
        List<String> missing = new ArrayList<>();

        // Each product has 10 images labelled 'a' through 'j'
        for (int product = 1; product <= 4; product++) {
            for (char suffix = 'a'; suffix <= 'j'; suffix++) {
                String imageName = "Product" + product + suffix + ".jpg";
                if (AssetPathResolver.resolveExisting(imageName).isEmpty()) {
                    missing.add(imageName);
                }
            }
        }

        // Also check the background music track
        String musicName = "Akira - From Dust, a Future.mp3";
        if (AssetPathResolver.resolveExisting(musicName).isEmpty()) {
            missing.add(musicName);
        }

        if (!missing.isEmpty()) {
            throw new IllegalStateException("Missing required asset files: " + String.join(", ", missing));
        }
    }
}
