/*
 * Copyright (c) 2026 Code Cain
 * https://codecain.dev
 * SPDX-License-Identifier: Apache-2.0
 */

package dev.codecain.swing.commands;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import dev.codecain.swing.commands.config.Configuration;
import dev.codecain.swing.commands.config.ConfigurationManager;
import dev.codecain.swing.commands.config.ConfigurationManagerTestSupport;
import dev.codecain.swing.commands.demo.Demo;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import javax.swing.JCheckBoxMenuItem;

/** Complete-configuration persistence and validation regression checks. */
final class JsonConfigurationTest {

    private static final ObjectMapper JSON = new ObjectMapper();

    private static int checks;

    private JsonConfigurationTest() {
    }

    private static void check(boolean value, String message) {

        checks++;

        if (!value) {
            throw new AssertionError(message);
        }
    }

    private static Configuration install(JsonNode factory, Path applicationSettingsDirectory)
            throws IOException {

        return ConfigurationManagerTestSupport.install(
                new ByteArrayInputStream(JSON.writeValueAsBytes(factory)),
                Demo.class.getClassLoader(),
                applicationSettingsDirectory);
    }

    private static void rejects(JsonNode factory, String expected) throws IOException {

        Path settingsDirectory = Files.createTempDirectory("commands-invalid-factory-");

        try (Configuration ignored = install(factory, settingsDirectory)) {
            throw new AssertionError("Accepted invalid factory configuration: " + expected);
        } catch (IOException exception) {
            check(exception.getMessage().contains(expected), "Useful error: " + exception.getMessage());
        } finally {
            Files.deleteIfExists(settingsDirectory);
        }
    }

    static void run() throws Exception {

        ObjectNode factory;

        try (InputStream input = Demo.class.getResourceAsStream(
                "/" + ConfigurationManager.CONFIGURATION_FILE_NAME)) {
            factory = (ObjectNode) JSON.readTree(input);
        }

        Path directory = Files.createTempDirectory("commands-json-");
        Path userFile = directory.resolve(ConfigurationManager.CONFIGURATION_FILE_NAME);

        try {
            testFactoryConfiguration(factory, directory, userFile);
            testSavedConfigurationIsAuthoritative(factory, directory, userFile);
            testInvalidSavedConfigurationFallsBack(factory, directory, userFile);
            testValidation(factory);
            testLayoutRoundTrip(factory, directory, userFile);
        } finally {
            Files.deleteIfExists(userFile);
            Files.deleteIfExists(directory.resolve(
                    ConfigurationManager.CONFIGURATION_FILE_NAME + ".bak"));
            Files.deleteIfExists(directory);
        }

        System.out.println("Passed " + checks + " JSON configuration checks");
    }

    private static void testFactoryConfiguration(
            ObjectNode factory,
            Path settingsDirectory,
            Path userFile) throws Exception {

        ObjectNode configuredFactory = factory.deepCopy();
        ((ObjectNode) configuredFactory.path("commands").path("view.grid")).put("selected", false);
        ((ObjectNode) configuredFactory.path("commands").path("theme.light")).put("selected", false);
        ((ObjectNode) configuredFactory.path("commands").path("theme.dark")).put("selected", true);

        try (Configuration configuration = install(configuredFactory, settingsDirectory)) {
            JsonNode copiedFactory = JSON.readTree(userFile.toFile());
            check(!copiedFactory.path("commands").path("view.grid")
                            .path("selected").asBoolean(),
                    "First install creates the stable application configuration file");
            check(!configuration.getCommand(Demo.Grid.class).isSelected(),
                    "Explicit false overrides the Java-selected state");
            check(configuration.getCommand(Demo.Dark.class).isSelected()
                            && !configuration.getCommand(Demo.Light.class).isSelected(),
                    "Factory selection state is applied");
            check(!((JCheckBoxMenuItem) configuration.getMenuBar().getMenu(0).getItem(1))
                            .isSelected(),
                    "Menu presenter reflects factory selection");

            configuration.getCommand(Demo.Grid.class).setSelected(true);
            configuration.saveUserConfiguration();
        }

        JsonNode saved = JSON.readTree(userFile.toFile());
        check(saved.path("commands").path("app.hello").has("class"),
                "Saved file contains command class registrations");
        check(saved.path("menus").isArray() && saved.path("toolbars").isArray(),
                "Saved file contains complete menu and toolbar layouts");
        check(saved.path("commands").path("view.grid").path("selected").asBoolean(),
                "Saved file captures the complete active command configuration");

        try (Configuration configuration = install(configuredFactory, settingsDirectory)) {
            check(configuration.getCommand(Demo.Grid.class).isSelected(),
                    "Saved configuration is loaded on the next install");
            configuration.resetToDefaults();
            check(!configuration.getCommand(Demo.Grid.class).isSelected(),
                    "Reset restores the factory selection state");
        }

        Files.delete(userFile);
    }

    private static void testSavedConfigurationIsAuthoritative(
            ObjectNode factory,
            Path settingsDirectory,
            Path userFile) throws Exception {

        try (Configuration configuration = install(factory, settingsDirectory)) {
            configuration.getCommand(Demo.Hello.class).setName("User label");
            configuration.setKeyBinding(configuration.getCommand(Demo.Hello.class), null, false);
            configuration.saveUserConfiguration();
        }

        ObjectNode updatedFactory = factory.deepCopy();
        ((ObjectNode) updatedFactory.path("commands").path("app.hello"))
                .put("tooltip", "Changed factory tooltip");
        ((ArrayNode) updatedFactory.get("menus")).addObject()
                .put("label", "New factory menu")
                .putArray("items");

        try (Configuration configuration = install(updatedFactory, settingsDirectory)) {
            check(configuration.getCommand(Demo.Hello.class).getName().equals("User label"),
                    "Saved user configuration supplies the active command properties");
            check(configuration.getCommand(Demo.Hello.class).getToolTip()
                            .equals("Works from the menu, toolbar, or Ctrl+H"),
                    "Saved configuration is loaded as a whole, not merged with new factory values");
            check(configuration.getCommand(Demo.Hello.class).getKeyBinding() == null,
                    "Saved null shortcut remains cleared");
            check(configuration.getMenuBar().getMenuCount() == 2,
                    "Saved layout is used instead of the updated factory layout");

            configuration.resetToDefaults();
            configuration.saveUserConfiguration();
        }

        try (Configuration configuration = install(factory, settingsDirectory)) {
            check(configuration.getCommand(Demo.Hello.class).getToolTip()
                            .equals("Changed factory tooltip"),
                    "Reset and save replaces the user file with the current factory configuration");
            check(configuration.getMenuBar().getMenuCount() == 3,
                    "Factory layout is restored and persisted by reset");
        }
    }

    private static void testInvalidSavedConfigurationFallsBack(
            ObjectNode factory,
            Path settingsDirectory,
            Path userFile) throws Exception {

        String invalid = "{\"version\": 2}";
        Files.writeString(userFile, invalid);

        try (Configuration configuration = install(factory, settingsDirectory)) {
            check(configuration.getLoadWarning().isPresent(),
                    "Invalid saved configuration reports a warning");
            check(configuration.getCommand(Demo.Hello.class).getName().equals("Say Hello"),
                    "Invalid user file does not partially change commands");
            check(configuration.getMenuBar().getMenu(0).getText().equals("Commands"),
                    "Factory layout is used when the saved file is invalid");
            check(Files.readString(userFile).equals(invalid),
                    "Invalid user file is preserved for recovery");
        }

        Files.delete(userFile);
    }

    private static void testValidation(ObjectNode factory) throws Exception {

        ObjectNode invalid = factory.deepCopy();
        invalid.put("version", 2);
        rejects(invalid, "version");

        invalid = factory.deepCopy();
        invalid.put("menuss", true);
        rejects(invalid, "menuss");

        invalid = factory.deepCopy();
        ((ObjectNode) invalid.path("commands").path("app.hello")).put("lable", "typo");
        rejects(invalid, "lable");

        invalid = factory.deepCopy();
        ((ObjectNode) invalid.path("commands").path("app.hello")).put("selected", true);
        rejects(invalid, "requires a BooleanCommand");

        invalid = factory.deepCopy();
        ((ObjectNode) invalid.path("commands").path("view.grid"))
                .put("shortcut", "ctrl pressed H");
        rejects(invalid, "duplicate shortcut");

        invalid = factory.deepCopy();
        ((ObjectNode) invalid.path("menus").get(0).path("items").get(0))
                .put("command", "missing");
        rejects(invalid, "unknown command ID");
    }

    private static void testLayoutRoundTrip(
            ObjectNode factory,
            Path settingsDirectory,
            Path userFile) throws Exception {

        try (Configuration configuration = install(factory, settingsDirectory)) {
            configuration.getMenuModel().getRoot().getChildAt(0)
                    .add(dev.codecain.swing.commands.resource.ResourceNode.group("Nested"));
            configuration.saveUserConfiguration();
        }

        try (Configuration configuration = install(factory, settingsDirectory)) {
            check(configuration.getLoadWarning().isEmpty(),
                    "Complete saved layout reloads without warning");
            check(configuration.getMenuModel().getRoot().getChildAt(0)
                            .getChildAt(4).getName().equals("Nested"),
                    "Nested menu layout survives serialization");
        }

        Files.delete(userFile);
    }
}
