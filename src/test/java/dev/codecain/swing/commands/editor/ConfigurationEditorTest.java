/*
 * Copyright (c) 2026 Jon Kent
 * https://codecain.dev
 * SPDX-License-Identifier: Apache-2.0
 */

package dev.codecain.swing.commands.editor;

import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import dev.codecain.swing.commands.config.Configuration;
import dev.codecain.swing.commands.config.ConfigurationManager;
import dev.codecain.swing.commands.demo.Demo;
import java.awt.Component;
import java.awt.Container;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import javax.imageio.ImageIO;
import javax.swing.JButton;
import javax.swing.JTabbedPane;
import javax.swing.JTable;
import javax.swing.SwingUtilities;

public final class ConfigurationEditorTest {

    private static int checks;

    @FunctionalInterface
    private interface Work {

        void run() throws Exception;
    }

    private static void check(boolean value, String message) {

        checks++;
        if (!value) {
            throw new AssertionError(message);
        }
    }

    private static void fails(Work work) throws Exception {

        try {
            work.run();
            throw new AssertionError("Expected validation failure");
        } catch (IOException expected) {
            checks++;
        }
    }

    public static void main(String[] args) throws Exception {

        SwingUtilities.invokeAndWait(() -> {
            try {
                exercise();
            } catch (Exception exception) {
                throw new RuntimeException(exception);
            }
        });

        System.out.println("Passed " + checks + " editor checks");
    }

    private static void exercise() throws Exception {

        Path directory = Files.createTempDirectory("command-editor-");
        Path file = directory.resolve("defaults.json");

        try {
            ObjectNode booleanCommand = JsonNodeFactory.instance.objectNode()
                    .put("class", Demo.Grid.class.getName())
                    .put("selected", false);
            ConfigurationDocument booleanDocument = new ConfigurationDocument();
            booleanDocument.putCommand(null, "view.grid", booleanCommand);
            booleanDocument.save(file);

            check(ConfigurationDocument.open(file).commands()
                            .path("view.grid").path("selected").isBoolean(),
                    "Editor writes selected as a JSON boolean");

            try (Configuration installed = new ConfigurationManager().install(file, null)) {
                check(!installed.getCommand(Demo.Grid.class).isSelected(),
                        "Editor false selection loads in runtime");
            }

            booleanCommand.put("selected", true);
            booleanDocument.putCommand("view.grid", "view.grid", booleanCommand);
            booleanDocument.save(file);
            check(ConfigurationDocument.open(file).commands()
                            .path("view.grid").path("selected").asBoolean(),
                    "Editor selected true round trip");

            ConfigurationDocument document = new ConfigurationDocument();
            ObjectNode command = JsonNodeFactory.instance.objectNode()
                    .put("class", Demo.Hello.class.getName())
                    .put("label", "Hello from editor")
                    .put("shortcut", "ctrl pressed H");
            document.putCommand(null, "app.hello", command);

            ObjectNode menu = document.section("menus")
                    .addObject()
                    .put("label", "File");
            menu.putArray("items")
                    .addObject()
                    .put("command", "app.hello");

            ObjectNode toolbar = document.section("toolbars")
                    .addObject()
                    .put("label", "Main");
            ObjectNode dropdown = toolbar.putArray("items")
                    .addObject()
                    .put("dropdown", "Quick")
                    .put("defaultCommand", "app.hello");
            dropdown.putArray("items")
                    .addObject()
                    .put("command", "app.hello");

            document.save(file);
            check(Files.readString(file).contains("\"version\" : 1"), "Save adds version");
            check(!document.dirty(), "Save clears dirty state");

            try (Configuration installed = new ConfigurationManager().install(file, null)) {
                check(installed.getMenuBar().getMenu(0).getItem(0).getText()
                                .equals("Hello from editor"),
                        "Editor output loads in runtime");
            }

            document = ConfigurationDocument.open(file);
            check(!document.dirty(), "Open preserves clean state");
            document.putCommand("app.hello", "app.greeting", command);
            check(document.root.path("menus").get(0).path("items").get(0)
                            .path("command").asText().equals("app.greeting"),
                    "ID rename updates placements");
            check(document.root.path("toolbars").get(0).path("items").get(0)
                            .path("defaultCommand").asText().equals("app.greeting"),
                    "ID rename updates dropdown default");
            check(document.dirty(), "Edit marks dirty");

            ConfigurationDocument changed = document;
            fails(() -> changed.removeCommand("app.greeting"));
            fails(() -> changed.putCommand(null, "duplicate", command));
            check(!changed.commands().has("duplicate"), "Invalid registration rolled back");

            changed.save(file);
            check(Files.exists(directory.resolve("defaults.json.bak")),
                    "Save retains backup");
            String previous = Files.readString(file);
            changed.section("menus")
                    .addObject()
                    .put("label", "Bad")
                    .putArray("items")
                    .addObject()
                    .put("command", "unknown");
            fails(() -> changed.save(file));
            check(Files.readString(file).equals(previous), "Invalid save leaves file intact");

            Files.writeString(file,
                    "{\"commands\":{\"app.test\":{\"class\":"
                            + "\"not.on.editor.classpath.TestCommand\"}},\"menus\":[]}");
            ConfigurationDocument repair = ConfigurationDocument.open(file);
            check(repair.repairedVersion() && repair.dirty(),
                    "Missing version repair is visible and dirty");
            repair.save(file);
            check(ConfigurationDocument.open(file).root.path("version").asInt() == 1,
                    "Repair persisted");
            check(repair.commands().path("app.test").path("class").asText()
                            .equals("not.on.editor.classpath.TestCommand"),
                    "Editor needs no host classes");

            Files.writeString(file, "{\"version\":2,\"commands\":{}}");
            fails(() -> ConfigurationDocument.open(file));
            Files.writeString(file, "{\"version\":1,\"version\":1,\"commands\":{}}");
            fails(() -> ConfigurationDocument.open(file));
            Files.writeString(file,
                    "{\"version\":1,\"commands\":{\"app.test\":"
                            + "{\"label\":\"Preference override\"}}}");
            fails(() -> ConfigurationDocument.open(file));

            // Render the editor panel in headless mode for layout inspection.
            try (var input = Demo.class.getResourceAsStream("/demo.json")) {
                Files.copy(input, file, StandardCopyOption.REPLACE_EXISTING);
            }

            ConfigurationEditor editor = new ConfigurationEditor();
            editor.openFile(file);
            editor.setSize(1000, 650);
            layout(editor);

            BufferedImage image = new BufferedImage(1000, 650, BufferedImage.TYPE_INT_RGB);
            Graphics2D graphics = image.createGraphics();
            editor.paint(graphics);
            graphics.dispose();

            Path preview = Path.of("build/reports/editor-preview.png");
            Files.createDirectories(preview.getParent());
            ImageIO.write(image, "png", preview.toFile());
            check(editor.getComponentCount() > 0,
                    "Editor panel renders without application runtime");
            JTable commandTable = findCommandTable(editor);
            check(commandTable != null && commandTable.getColumnCount() == 8,
                    "Registered commands render in the metadata table");
            check(findLabel(editor, "Open…"), "UTF-8 UI labels compile correctly on Windows");

            for (Component child : editor.getComponents()) {
                if (child instanceof JTabbedPane tabs) {
                    tabs.setSelectedIndex(1);
                    layout(editor);
                    graphics = image.createGraphics();
                    editor.paint(graphics);
                    graphics.dispose();
                    ImageIO.write(image, "png", Path.of("build/reports/editor-menus.png").toFile());

                    tabs.setSelectedIndex(3);
                    layout(editor);
                    graphics = image.createGraphics();
                    editor.paint(graphics);
                    graphics.dispose();
                    ImageIO.write(image, "png", Path.of("build/reports/editor-layout.png").toFile());
                }
            }
        } finally {
            Files.deleteIfExists(file);
            Files.deleteIfExists(directory.resolve("defaults.json.bak"));
            Files.deleteIfExists(directory);
        }
    }

    private static JTable findCommandTable(Container parent) {

        for (Component child : parent.getComponents()) {
            if (child instanceof JTable table) {
                return table;
            }

            if (child instanceof Container container) {
                JTable table = findCommandTable(container);
                if (table != null) {
                    return table;
                }
            }
        }

        return null;
    }
    private static boolean findLabel(Container parent, String label) {

        for (Component child : parent.getComponents()) {
            if (child instanceof JButton button && button.getText().equals(label)) {
                return true;
            }

            if (child instanceof Container container && findLabel(container, label)) {
                return true;
            }
        }

        return false;
    }

    private static void layout(Container parent) {

        parent.doLayout();

        for (Component child : parent.getComponents()) {
            if (child instanceof Container container) {
                layout(container);
            }
        }
    }
}

