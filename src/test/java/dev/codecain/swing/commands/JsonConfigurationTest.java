package dev.codecain.swing.commands;

import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.node.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import javax.swing.*;
import dev.codecain.swing.commands.config.*;
import dev.codecain.swing.commands.demo.Demo;

/** JSON contract, preference evolution, and validation regression checks. */
final class JsonConfigurationTest {
    private static final ObjectMapper JSON = new ObjectMapper();
    private static int checks;
    private static void check(boolean value, String message) {
        checks++; if (!value) throw new AssertionError(message);
    }
    private static Configuration install(JsonNode factory, Path prefs) throws IOException {
        return new ConfigurationManager().install(new ByteArrayInputStream(JSON.writeValueAsBytes(factory)),
            Demo.class.getClassLoader(), prefs);
    }
    private static void rejects(JsonNode factory, String expected) throws IOException {
        try (Configuration ignored = install(factory, null)) { throw new AssertionError("Accepted invalid defaults: " + expected); }
        catch (IOException ex) { check(ex.getMessage().contains(expected), "Useful error: " + ex.getMessage()); }
    }
    static void run() throws Exception {
        ObjectNode factory;
        try (InputStream in = Demo.class.getResourceAsStream("/demo.json")) { factory = (ObjectNode) JSON.readTree(in); }
        Path dir = Files.createTempDirectory("commands-json-"); Path prefs = dir.resolve("settings.json");
        try {
            ObjectNode selectedDefaults = factory.deepCopy();
            ((ObjectNode) selectedDefaults.path("commands").path("view.grid")).put("selected", false);
            ((ObjectNode) selectedDefaults.path("commands").path("theme.light")).put("selected", false);
            ((ObjectNode) selectedDefaults.path("commands").path("theme.dark")).put("selected", true);
            try (Configuration c = install(selectedDefaults, prefs)) {
                check(!c.getCommand(Demo.Grid.class).isSelected(), "Explicit false overrides Java true for callback command");
                check(c.getCommand(Demo.Dark.class).isSelected() && !c.getCommand(Demo.Light.class).isSelected(), "Exclusive group honors selected defaults");
                check(!((JCheckBoxMenuItem)c.getMenuBar().getMenu(0).getItem(1)).isSelected(), "Menu reflects false default");
                c.getCommand(Demo.Grid.class).setSelected(true);
                c.saveUserLayout();
                check(!JSON.readTree(prefs.toFile()).toString().contains("selected"), "Runtime selection is not persisted as a preference");
            }
            try (Configuration c = install(selectedDefaults, prefs)) {
                check(!c.getCommand(Demo.Grid.class).isSelected(), "Reload reestablishes explicit default");
            }
            Files.delete(prefs);
            ((ObjectNode) selectedDefaults.path("commands").path("view.grid")).put("selected", true);
            try (Configuration c = install(selectedDefaults, null)) { check(c.getCommand(Demo.Grid.class).isSelected(), "Explicit true for callback command"); }
            ((ObjectNode) selectedDefaults.path("commands").path("view.grid")).remove("selected");
            try (Configuration c = install(selectedDefaults, null)) { check(c.getCommand(Demo.Grid.class).isSelected(), "Omitted selected inherits Java default"); }
            ObjectNode invalidSelected = factory.deepCopy();
            ((ObjectNode) invalidSelected.path("commands").path("app.hello")).put("selected", true);
            rejects(invalidSelected, "requires a BooleanCommand");
            invalidSelected = factory.deepCopy();
            ((ObjectNode) invalidSelected.path("commands").path("view.grid")).put("selected", "true");
            rejects(invalidSelected, "selected");
            ((ObjectNode) invalidSelected.path("commands").path("view.grid")).putNull("selected");
            rejects(invalidSelected, "selected");
            try (Configuration c = install(factory, prefs)) {
                c.saveUserLayout();
                JsonNode saved = JSON.readTree(prefs.toFile());
                check(saved.path("commands").isEmpty(), "Unmodified presentation not saved");
                check(!saved.has("menus") && !saved.has("toolbars"), "Unmodified layouts inherit defaults");
                c.getCommand(Demo.Hello.class).setName("User label");
                c.setKeyBinding(c.getCommand(Demo.Hello.class), null, false);
                c.saveUserLayout();
                saved = JSON.readTree(prefs.toFile());
                check(saved.path("commands").path("app.hello").path("shortcut").isNull(), "Null explicitly removes shortcut");
                check(!saved.toString().contains("class"), "Preferences contain no class names");
            }
            ObjectNode updated = factory.deepCopy();
            ((ObjectNode) updated.path("commands").path("app.hello")).put("tooltip", "New factory tooltip");
            ((ArrayNode) updated.get("menus")).addObject().put("label", "New menu").putArray("items");
            try (Configuration c = install(updated, prefs)) {
                check(c.getCommand(Demo.Hello.class).getName().equals("User label"), "User label wins");
                check(c.getCommand(Demo.Hello.class).getKeyBinding() == null, "Removed shortcut stays removed");
                check(c.getCommand(Demo.Hello.class).getToolTip().equals("New factory tooltip"), "Untouched metadata follows new defaults");
                check(c.getMenuBar().getMenuCount() == 3, "Untouched layout follows new defaults");
                c.resetToDefaults(); c.saveUserLayout();
                check(JSON.readTree(prefs.toFile()).path("commands").isEmpty(), "Saved reset removes presentation overrides");
            }
            try (Configuration c = install(factory, null)) {
                check(c.getCommand(Demo.Hello.class).getName().equals("Say Hello"), "Reinstall restores Java fallback name");
                check(!c.getCommand(Demo.Hello.class).getToolTip().equals("New factory tooltip"), "Removed JSON override restores Java fallback tooltip");
            }
            ObjectNode bad = factory.deepCopy(); bad.put("version", 2); rejects(bad, "version");
            bad = factory.deepCopy(); bad.put("menuss", true); rejects(bad, "menuss");
            bad = factory.deepCopy(); ((ObjectNode) bad.path("commands").path("app.hello")).put("lable", "typo"); rejects(bad, "lable");
            bad = factory.deepCopy(); ((ObjectNode) bad.path("menus").get(0)).put("mnemonic", "long"); rejects(bad, "mnemonic");
            bad = factory.deepCopy(); ((ObjectNode) bad.path("toolbars").get(0)).put("showLabels", "yes"); rejects(bad, "showLabels");
            bad = factory.deepCopy(); ((ObjectNode) bad.path("menus").get(0).path("items").get(0)).put("command", "missing"); rejects(bad, "unknown command ID");
            bad = factory.deepCopy(); ((ObjectNode) bad.path("commands").path("view.grid")).put("shortcut", "ctrl pressed H"); rejects(bad, "duplicate shortcut");
            bad = factory.deepCopy(); ((ObjectNode) bad.path("commands").path("view.grid")).put("shortcut", "not a shortcut"); rejects(bad, "pressed shortcut");
            bad = factory.deepCopy(); ((ObjectNode) bad.path("commands").path("view.grid")).put("class", Demo.Hello.class.getName()); rejects(bad, "already registered");
            bad = factory.deepCopy(); ((ObjectNode) bad.path("toolbars").get(1).path("items").get(1)).put("defaultCommand", "view.grid"); rejects(bad, "defaultCommand");
            bad = factory.deepCopy(); ((ObjectNode) bad.path("menus").get(1).path("items").get(0).path("items").get(0)).put("command", "app.hello"); rejects(bad, "boolean commands");
            bad = factory.deepCopy(); ((ObjectNode) bad.path("menus").get(1)).put("id", "commands"); rejects(bad, "duplicate container ID");
            String[] invalidPrefs = {
                "{\"version\":2}", "{\"version\":1,\"commands\":{\"missing\":{\"label\":\"X\"}}}",
                "{\"version\":1,\"commands\":{\"app.hello\":{\"class\":\"java.lang.Runtime\"}}}",
                "{\"version\":1,\"commands\":{\"app.hello\":{\"label\":\"Must not apply\"},\"view.grid\":{\"shortcut\":\"ctrl pressed H\"}}}",
                "{\"version\":1} {}", "{\"version\":1,\"version\":1}", "{\"version\":1, // comment\n}",
                "{\"version\":1,\"menus\":[{\"label\":\"Bad\",\"items\":[{\"command\":\"missing\"}]}]}"
            };
            for (String text : invalidPrefs) {
                Files.writeString(prefs, text);
                try (Configuration c = install(factory, prefs)) {
                    check(c.getLoadWarning().isPresent(), "Bad preferences report warning");
                    check(c.getCommand(Demo.Hello.class).getName().equals("Say Hello"), "No partial metadata applied");
                    check(c.getMenuBar().getMenu(0).getText().equals("Commands"), "No partial layout applied");
                    check(Files.readString(prefs).equals(text), "Bad preferences left intact");
                }
            }
            Files.writeString(prefs, "{\"$schema\":\"https://invalid.invalid/never-fetch\",\"version\":1,\"menus\":[],\"toolbars\":[]}");
            try (Configuration c = install(factory, prefs)) {
                check(c.getLoadWarning().isEmpty(), "Runtime uses bundled schema, not document URL");
                check(c.getMenuBar().getMenuCount() == 0 && c.getToolbarPanel().getComponentCount() == 0, "Explicit empty layouts respected");
                c.saveUserLayout();
            }
            try (Configuration c = install(factory, prefs)) { check(c.getMenuBar().getMenuCount() == 0, "Empty layout survives save/reload"); }
            Files.delete(prefs);
            try (Configuration c = install(factory, prefs)) {
                // Nested menu, separator and dropdown defaults survive model serialization.
                var model = c.getMenuModel(); var nested = dev.codecain.swing.commands.resource.ResourceNode.group("Nested");
                model.add(model.getRoot().getChildAt(0), nested);
                model.add(nested, dev.codecain.swing.commands.resource.ResourceNode.command(c.getCommand(Demo.Grid.class)));
                var dropdown = c.getToolbarModel().getRoot().getChildAt(1).getChildAt(1);
                dropdown.getChildAt(0).getAttributes().put("default", "false");
                dropdown.getChildAt(1).getAttributes().put("default", "true");
                c.saveUserLayout();
            }
            try (Configuration c = install(factory, prefs)) {
                check(c.getLoadWarning().isEmpty(), "Edited nested models reload");
                check(c.getMenuModel().getRoot().getChildAt(0).getChildAt(4).getName().equals("Nested"), "Nested menu roundtrip");
                check(c.getToolbarModel().getRoot().getChildAt(1).getChildAt(1).getChildAt(1).attribute("default", "false").equals("true"), "Dropdown default roundtrip");
            }
        } finally {
            Files.deleteIfExists(prefs); Files.deleteIfExists(dir.resolve("settings.json.bak")); Files.deleteIfExists(dir);
        }
        System.out.println("Passed " + checks + " JSON configuration checks");
    }
}

