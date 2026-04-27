package tokyoera.service;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public final class AssetPathResolver {
    private AssetPathResolver() {
    }

    public static Optional<Path> resolveExisting(String filename) {
        // First pass: try known fast paths.
        for (Path candidate : candidates(filename)) {
            if (Files.exists(candidate)) {
                return Optional.of(candidate.toAbsolutePath().normalize());
            }
        }
        // Fallback: recursive search within pics/ subdirectories
        Path picsDir = detectProjectRoot().resolve("pics");
        if (Files.isDirectory(picsDir)) {
            try {
                return Files.walk(picsDir, 4)
                        .filter(p -> p.getFileName().toString().equals(filename))
                        .findFirst()
                        .map(p -> p.toAbsolutePath().normalize());
            } catch (IOException ignored) {
                // If scan fails, we just return empty below.
            }
        }
        return Optional.empty();
    }

    public static List<Path> candidates(String filename) {
        Path projectRoot = detectProjectRoot();
        List<Path> candidates = new ArrayList<>();
        candidates.add(Path.of(filename));
        candidates.add(Path.of(System.getProperty("user.dir"), filename));
        candidates.add(projectRoot.resolve(filename));
        candidates.add(projectRoot.resolve("pics").resolve(filename));
        candidates.add(Path.of(System.getProperty("user.dir"), "pics", filename));
        candidates.add(projectRoot.resolve("assets").resolve(filename));
        candidates.add(Path.of(System.getProperty("user.dir"), "assets", filename));
        return candidates;
    }

    private static Path detectProjectRoot() {
        try {
            URI location = AssetPathResolver.class.getProtectionDomain().getCodeSource().getLocation().toURI();
            Path codePath = Path.of(location).toAbsolutePath().normalize();
            if (codePath.endsWith(Path.of("target", "classes"))) {
                return codePath.getParent().getParent();
            }
            if (Files.isDirectory(codePath) && Files.exists(codePath.resolve("pom.xml"))) {
                return codePath;
            }
        } catch (URISyntaxException | RuntimeException ignored) {
            // Fall back to current working directory when runtime path detection is unavailable.
        }
        return Path.of(System.getProperty("user.dir")).toAbsolutePath().normalize();
    }
}
