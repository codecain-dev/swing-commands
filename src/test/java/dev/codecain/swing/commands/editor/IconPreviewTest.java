/*
 * Copyright (c) 2026 Jon Kent
 * https://codecain.dev
 * SPDX-License-Identifier: Apache-2.0
 */

package dev.codecain.swing.commands.editor;

import java.awt.image.BufferedImage;
import java.nio.file.Files;
import java.nio.file.Path;
import javax.imageio.ImageIO;
import javax.swing.JLabel;
import javax.swing.JTextField;
import javax.swing.SwingUtilities;

public final class IconPreviewTest {

    public static void main(String[] args) throws Exception {

        Path root = Files.createTempDirectory("icon-preview-");
        Path icons = Files.createDirectories(root.resolve("icons"));
        Path png = icons.resolve("exit.png");

        try {
            BufferedImage image = new BufferedImage(16, 16, BufferedImage.TYPE_INT_ARGB);
            ImageIO.write(image, "png", png.toFile());

            check(EditorIconLoader.load("/icons/exit.png", "missing.Command", root)
                    .getIconWidth() == 16, "Resource-root lookup");
            check(EditorIconLoader.load(png.toString(), "missing.Command", null)
                    .getIconHeight() == 16, "Absolute file lookup");
            check(EditorIconLoader.load("exit.png", "icons.Command", root)
                    .getIconWidth() == 16, "Class-relative lookup without loading class");
            check(EditorIconLoader.load("", "missing.Command", root) == null, "Empty path");

            try {
                EditorIconLoader.load("/missing.png", "missing.Command", root);
                throw new AssertionError("Missing image accepted");
            } catch (IllegalArgumentException expected) {
                // Missing resources are expected to fail validation.
            }

            JTextField[] field = {null};
            IconPreviewer[] panel = {null};
            SwingUtilities.invokeAndWait(() -> {
                field[0] = new JTextField(png.toString());
                panel[0] = new IconPreviewer(
                        field[0],
                        () -> value -> EditorIconLoader.load(
                                value,
                                "missing.Command",
                                root));
            });

            await(panel[0], true, "");

            SwingUtilities.invokeAndWait(() -> field[0].setText("missing.png"));
            await(panel[0], false, "?");

            SwingUtilities.invokeAndWait(() -> field[0].setText(""));
            await(panel[0], false, "—");

            SwingUtilities.invokeAndWait(() -> panel[0].removeNotify());
            System.out.println(
                    "Icon preview lookup, live updates, invalid-image and clearing checks passed");
        } finally {
            Files.deleteIfExists(png);
            Files.deleteIfExists(icons);
            Files.deleteIfExists(root);
        }
    }

    private static void await(IconPreviewer panel, boolean hasIcon, String text)
            throws Exception {

        for (int attempt = 0; attempt < 100; attempt++) {
            boolean[] ready = {false};

            SwingUtilities.invokeAndWait(() -> {
                JLabel label = (JLabel) panel.getComponent(1);
                ready[0] = (label.getIcon() != null) == hasIcon
                        && text.equals(label.getText());
            });

            if (ready[0]) {
                return;
            }

            Thread.sleep(30);
        }

        throw new AssertionError("Preview did not update: " + text);
    }

    private static void check(boolean condition, String message) {

        if (!condition) {
            throw new AssertionError(message);
        }
    }
}
