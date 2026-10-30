/*
 * Copyright (c) 2026 Code Cain
 * https://codecain.dev
 * SPDX-License-Identifier: Apache-2.0
 */

package dev.codecain.swing.commands;

import dev.codecain.swing.commands.command.Command;
import dev.codecain.swing.commands.config.Configuration;
import dev.codecain.swing.commands.config.ConfigurationManager;
import dev.codecain.swing.commands.demo.Demo;
import java.awt.event.ActionEvent;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import javax.swing.AbstractButton;
import javax.swing.InputMap;
import javax.swing.JComponent;
import javax.swing.JMenuItem;
import javax.swing.JRootPane;
import javax.swing.JTree;
import javax.swing.KeyStroke;
import javax.swing.SwingUtilities;

/** Dependency-free integration checks run on the Swing event thread. */
public final class IntegrationTest {

    private static int checks;

    private static void check(boolean condition, String message) {

        checks++;
        if (!condition) {
            throw new AssertionError(message);
        }
    }

    private static void rejects(Runnable operation) {

        try {
            operation.run();
            throw new AssertionError("Expected rejection");
        } catch (IllegalArgumentException expected) {
            checks++;
        }
    }

    private static Configuration install(Path applicationSettingsDirectory) throws IOException {

        return new ConfigurationManager().install(Demo.class, applicationSettingsDirectory);
    }

    public static void main(String[] args) throws Exception {

        SwingUtilities.invokeAndWait(() -> {
            try {
                exercise();
                JsonConfigurationTest.run();
                EnabledStateTest.run();
            } catch (Exception exception) {
                throw new RuntimeException(exception);
            }
        });

        System.out.println("Passed " + checks + " integration checks");
    }

    private static void exercise() throws Exception {

        Path directory = Files.createTempDirectory("swing-commands-test-");
        Path userFile = directory.resolve(ConfigurationManager.CONFIGURATION_FILE_NAME);
        var run = Command.get(Demo.Hello.class);
        var flag = Command.get(Demo.Grid.class);
        int initialListeners = run.getPropertyChangeListeners().length;

        try {
            try (Configuration configuration = install(directory)) {
                check(run == configuration.getCommand(Demo.Hello.class), "Shared identity");
                JTree tree = new JTree(configuration.getMenuModel());
                check(tree.getModel() == configuration.getMenuModel(), "Direct JTree model");

                int[] calls = {0};
                run.setCommandPerformer((command, event) -> calls[0]++);
                JMenuItem helloItem = configuration.getMenuBar().getMenu(0).getItem(0);
                helloItem.doClick(0);
                check(calls[0] == 1, "Menu callback");

                JRootPane root = new JRootPane();
                InputMap previous = root.getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW);
                configuration.installKeyBindings(root);
                var menu = configuration.getMenuModel().getRoot().getChildAt(0);
                configuration.getMenuModel().removeNodeFromParent(menu.getChildAt(0));
                Object key = root.getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW)
                        .get(run.getKeyBinding());
                root.getActionMap().get(key)
                        .actionPerformed(new ActionEvent(root, 0, "key"));
                check(calls[0] == 2, "Shortcut without menu item");

                rejects(() -> configuration.setKeyBinding(
                        flag,
                        run.getKeyBinding(),
                        false));
                configuration.setKeyBinding(flag, run.getKeyBinding(), true);
                check(run.getKeyBinding() == null, "Reassign clears previous shortcut");
                configuration.resetKeyBindings();
                check(run.getKeyBinding().equals(KeyStroke.getKeyStroke("ctrl pressed H")),
                        "Shortcut reset");
                configuration.uninstallKeyBindings(root);
                check(root.getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW) == previous,
                        "Restores window bindings");

                boolean[] seen = {false};
                flag.setCommandPerformer((command, event) -> seen[0] = flag.isSelected());
                flag.setSelected(false);
                configuration.getMenuBar().getMenu(0).getItem(0).doClick(0);
                check(flag.isSelected() && seen[0], "Toggle before callback");

                flag.actionPerformed(new ActionEvent(root, 0, "key"));
                check(!flag.isSelected() && !seen[0], "Keyboard toggles once");

                var light = configuration.getCommand(Demo.Light.class);
                var dark = configuration.getCommand(Demo.Dark.class);
                light.setSelected(true);
                dark.setSelected(true);
                check(!light.isSelected() && dark.isSelected(), "Exclusive group selection");

                var exclusive = configuration.getMenuModel()
                        .getRoot().getChildAt(1).getChildAt(0);
                rejects(() -> configuration.getMenuModel()
                        .removeNodeFromParent(exclusive.getChildAt(0)));
                check(exclusive.getChildCount() == 2,
                        "Conflicting shared group edit rolls back");

                int listeners = run.getPropertyChangeListeners().length;
                for (int index = 0; index < 10; index++) {
                    configuration.getMenuModel().rename(menu, "Menu " + index);
                }
                check(run.getPropertyChangeListeners().length == listeners,
                        "No rebuild listener leak");

                rejects(() -> configuration.getMenuModel().move(menu, menu, 0));
                check(menu.getParent() == configuration.getMenuModel().getRoot(),
                        "Rejected move preserves tree");

                run.setName("Run & <inspect>");
                configuration.setKeyBinding(
                        run,
                        KeyStroke.getKeyStroke("ctrl pressed T"),
                        false);
                configuration.saveUserConfiguration();
                configuration.saveUserConfiguration();
                check(Files.exists(directory.resolve(
                        ConfigurationManager.CONFIGURATION_FILE_NAME + ".bak")),
                        "Backup exists");

                var model = configuration.getMenuModel();
                configuration.resetToDefaults();
                check(configuration.getMenuModel() == model
                                && model.getRoot().getChildAt(0).getName().equals("Commands"),
                        "Reset retains model identity");
                check(run.getName().equals("Say Hello"), "Reset metadata");
            }

            try (Configuration configuration = install(directory)) {
                check(configuration.getLoadWarning().isEmpty(), "Saved JSON loads");
                check(run.getName().equals("Run & <inspect>"), "Escaped metadata roundtrip");
                check(run.getKeyBinding().equals(KeyStroke.getKeyStroke("ctrl pressed T")),
                        "Shortcut roundtrip");
                check(configuration.getMenuModel().getRoot().getChildAt(0).getName()
                                .equals("Menu 9"),
                        "Layout roundtrip");
                configuration.resetToDefaults();
            }

            String invalid = "{\"version\":1,\"menus\":[{\"label\":\"Bad\","
                    + "\"mnemonic\":\"invalid\",\"items\":[]}]}";
            Files.writeString(userFile, invalid);
            try (Configuration configuration = install(directory)) {
                check(configuration.getLoadWarning().isPresent(), "Invalid mnemonic rejected");
                check(configuration.getMenuBar().getMenu(0).getText().equals("Commands"),
                        "Factory fallback");
                check(Files.readString(userFile).equals(invalid), "Invalid file preserved");
            }

            Files.writeString(userFile,
                    "{\"version\":1,\"commands\":{\"app.hello\":{\"class\":"
                            + "\"java.lang.Runtime\"}}}");
            try (Configuration configuration = install(directory)) {
                check(configuration.getLoadWarning().isPresent(),
                        "User class injection rejected");
            }

            Files.writeString(userFile, "{\"version\":1,\"version\":2}");
            try (Configuration configuration = install(directory)) {
                check(configuration.getLoadWarning().isPresent(), "Duplicate JSON keys rejected");
            }

            check(run.getPropertyChangeListeners().length == initialListeners,
                    "Close releases listeners");
        } finally {
            run.setCommandPerformer(null);
            flag.setCommandPerformer(null);
            Files.deleteIfExists(userFile);
            Files.deleteIfExists(directory.resolve(
                    ConfigurationManager.CONFIGURATION_FILE_NAME + ".bak"));
            Files.deleteIfExists(directory);
        }
    }
}
