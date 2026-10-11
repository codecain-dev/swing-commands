package dev.codecain.swing.commands.editor;

import java.nio.file.*;
import java.net.URL;
import javax.imageio.ImageIO;
import javax.swing.*;

/**
 * Resolves previews without loading or initializing application command classes.
 */
final class EditorIconLoader {

    static Icon load(String value, String className, Path configurationDirectory) {
        if (value.isBlank()) {
            return null;
        }

        String resource = value.startsWith("/")
                ? value.substring(1)
                : (className.contains(".")
                ? className.substring(0, className.lastIndexOf('.')).replace('.', '/') + "/"
                : "") + value;

        try {
            URL url = ConfigurationEditor.class.getClassLoader().getResource(resource);

            if (url != null) {
                return decode(url);
            }

            Path file = Path.of(value);

            if (Files.isRegularFile(file)) {
                return decode(file.toUri().toURL());
            }

            if (configurationDirectory != null) {
                Path root = configurationDirectory;

                for (Path parent = configurationDirectory; parent != null; parent = parent.getParent()) {
                    if (parent.getFileName() != null && parent.getFileName().toString().equals("resources")) {
                        root = parent;
                        break;
                    }
                }

                Path candidate = root.resolve(resource).normalize();

                if (Files.isRegularFile(candidate)) {
                    return decode(candidate.toUri().toURL());
                }

                if (!file.isAbsolute()) {
                    candidate = configurationDirectory.resolve(file).normalize();

                    if (Files.isRegularFile(candidate)) {
                        return decode(candidate.toUri().toURL());
                    }
                }
            }

            throw new IllegalArgumentException(
                    "Image not found. Open the configuration from its resource folder, or use a local file path.");
        } catch (Exception ex) {
            throw new IllegalArgumentException(ex.getMessage(), ex);
        }
    }

    private static Icon decode(URL url) throws java.io.IOException {
        var image = ImageIO.read(url);

        if (image == null) {
            throw new java.io.IOException("Unsupported or invalid image");
        }

        return new ImageIcon(image);
    }
}