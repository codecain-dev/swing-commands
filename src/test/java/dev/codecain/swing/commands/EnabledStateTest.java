/*
 * Copyright (c) 2026 Code Cain
 * https://codecain.dev
 * SPDX-License-Identifier: Apache-2.0
 */

package dev.codecain.swing.commands;

import dev.codecain.swing.commands.command.BooleanCallbackCommand;
import dev.codecain.swing.commands.command.BooleanCommand;
import dev.codecain.swing.commands.command.CallbackCommand;
import dev.codecain.swing.commands.command.CallableCommand;
import dev.codecain.swing.commands.command.Command;
import dev.codecain.swing.commands.command.CommandPerformer;
import dev.codecain.swing.commands.config.Configuration;
import dev.codecain.swing.commands.config.ConfigurationManager;
import dev.codecain.swing.commands.config.ConfigurationManagerTestSupport;
import java.awt.event.ActionEvent;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import javax.swing.AbstractButton;
import javax.swing.JMenuItem;
import javax.swing.JToolBar;

final class EnabledStateTest {

    public static class Callback extends CallbackCommand {
    }

    public static class Toggle extends BooleanCallbackCommand {
    }

    public static class DisabledDefault extends CallableCommand {

        @Override
        protected void initialize() {

            setEnabled(false);
        }

        @Override
        public void performCommand(ActionEvent event) {

        }
    }

    private static int checks;

    private static void check(boolean condition, String message) {

        checks++;
        if (!condition) {
            throw new AssertionError(message);
        }
    }

    private static Configuration load(
            Class<?> type,
            String enabled,
            Path applicationSettingsDirectory) throws IOException {

        String setting = enabled == null ? "" : ",\"enabled\":" + enabled;
        String json = "{\"version\":1,\"commands\":{\"test\":{\"class\":\""
                + type.getName() + "\"" + setting
                + "}},\"menus\":[{\"label\":\"Menu\",\"items\":[{\"command\":\"test\"}]}],"
                + "\"toolbars\":[{\"label\":\"Main\",\"items\":[{\"command\":\"test\"}]}]}";
        byte[] bytes = json.getBytes(StandardCharsets.UTF_8);
        ByteArrayInputStream input = new ByteArrayInputStream(bytes);
        Files.deleteIfExists(applicationSettingsDirectory.resolve(
                ConfigurationManager.CONFIGURATION_FILE_NAME));

        return ConfigurationManagerTestSupport.install(
                input,
                EnabledStateTest.class.getClassLoader(),
                applicationSettingsDirectory);
    }

    static void run() throws Exception {

        Path settingsDirectory = Files.createTempDirectory("enabled-state-");

        try {

        for (Class<? extends Command> type : List.of(Callback.class, Toggle.class)) {
            try (Configuration configuration = load(type, "false", settingsDirectory)) {
                Command command = configuration.getCommand(type);
                JMenuItem menuItem = configuration.getMenuBar().getMenu(0).getItem(0);
                JToolBar toolbar = (JToolBar) configuration.getToolbarPanel().getComponent(0);
                AbstractButton button = (AbstractButton) toolbar.getComponent(0);
                int[] calls = {0};
                CommandPerformer performer = (registeredCommand, event) -> calls[0]++;

                if (command instanceof CallbackCommand callback) {
                    callback.setCommandPerformer(performer);
                } else {
                    ((BooleanCallbackCommand) command).setCommandPerformer(performer);
                }

                check(!command.isEnabled(), "Performer registration preserves disabled state");
                check(!menuItem.isEnabled() && !button.isEnabled(),
                        "Presenters reflect configured disabled state");

                command.actionPerformed(new ActionEvent(button, 0, "test"));
                check(calls[0] == 0, "Disabled command does not execute");

                if (command instanceof BooleanCommand toggle) {
                    check(!toggle.isSelected(), "Disabled boolean command does not toggle");
                }

                command.setEnabled(true);
                check(menuItem.isEnabled() && button.isEnabled(),
                        "Presenter state follows setEnabled");

                command.actionPerformed(new ActionEvent(new Object(), 0, "test"));
                check(calls[0] == 1, "Enabled command executes");

                if (command instanceof CallbackCommand callback) {
                    callback.removeCommandPerformer(performer);
                } else {
                    ((BooleanCallbackCommand) command).removeCommandPerformer(performer);
                }

                check(command.isEnabled(), "Removing performer preserves enabled state");
                command.actionPerformed(new ActionEvent(new Object(), 0, "test"));
                check(calls[0] == 1,
                        "No performer is a safe no-op for callback behavior");
            }

            try (Configuration configuration = load(type, "true", settingsDirectory)) {
                check(configuration.getCommand(type).isEnabled(),
                        "Explicit true independent of performer");
            }

            try (Configuration configuration = load(type, null, settingsDirectory)) {
                check(configuration.getCommand(type).isEnabled(),
                        "Callback Java default is enabled without performer");
            }
        }

        try (Configuration configuration = load(
                DisabledDefault.class, null, settingsDirectory)) {
            check(!configuration.getCommand(DisabledDefault.class).isEnabled(),
                    "Omission inherits Java false");
        }

        try (Configuration configuration = load(
                DisabledDefault.class, "true", settingsDirectory)) {
            check(configuration.getCommand(DisabledDefault.class).isEnabled(),
                    "Config overrides Java false");
        }

        try (Configuration configuration = load(
                DisabledDefault.class, null, settingsDirectory)) {
            check(!configuration.getCommand(DisabledDefault.class).isEnabled(),
                    "Reload restores inherited Java default");
        }

        for (String invalid : List.of("\"false\"", "null", "1")) {
            try (Configuration configuration = load(
                    Callback.class, invalid, settingsDirectory)) {
                throw new AssertionError("Accepted invalid enabled value");
            } catch (IOException expected) {
                check(expected.getMessage().contains("enabled"),
                        "Invalid enabled value identifies setting");
            }
        }

        System.out.println("Passed " + checks + " enabled-state checks");
        } finally {
            Files.deleteIfExists(settingsDirectory.resolve(
                    ConfigurationManager.CONFIGURATION_FILE_NAME));
            Files.deleteIfExists(settingsDirectory);
        }
    }
}
