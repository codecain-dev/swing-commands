/*
 * Copyright (c) 2026 Code Cain
 * https://codecain.dev
 * SPDX-License-Identifier: Apache-2.0
 */

package dev.codecain.swing.commands.config;

import dev.codecain.swing.commands.command.Command;
import java.beans.PropertyChangeListener;
import java.util.ArrayList;
import java.util.Collection;
import java.util.IdentityHashMap;
import java.util.Map;
import javax.swing.Action;
import javax.swing.ActionMap;
import javax.swing.ComponentInputMap;
import javax.swing.InputMap;
import javax.swing.JComponent;
import javax.swing.JRootPane;

/**
 * Window-scoped bindings, independent of menu placement. Focused Swing editor
 * bindings take precedence.
 */
public final class KeyBindingManager implements AutoCloseable {

    private final Collection<Command> commands;
    private final Map<JRootPane, Binding> windows = new IdentityHashMap<>();

    private final PropertyChangeListener changes = event -> {

        if (Action.ACCELERATOR_KEY.equals(event.getPropertyName())) {
            refresh();
        }
    };

    private record Binding(
            InputMap previousInput,
            ActionMap previousActionMap,
            ComponentInputMap input,
            ActionMap map) {
    }

    /**
     * Creates a manager that observes accelerator changes for the supplied commands.
     *
     * @param commands commands whose shortcuts should be managed
     */
    public KeyBindingManager(Collection<Command> commands) {

        this.commands = commands;

        for (Command command : commands) {
            command.addPropertyChangeListener(changes);
        }
    }

    /**
     * Installs the current command accelerators on a window root pane.
     *
     * @param root root pane that should receive the bindings
     */
    public void install(JRootPane root) {

        if (windows.containsKey(root)) {
            return;
        }

        ComponentInputMap input = new ComponentInputMap(root);
        ActionMap map = new ActionMap();
        InputMap previousInput = root.getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW);
        ActionMap previousActionMap = root.getActionMap();

        input.setParent(previousInput);
        map.setParent(previousActionMap);
        windows.put(root, new Binding(previousInput, previousActionMap, input, map));
        root.setInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW, input);
        root.setActionMap(map);

        refresh();
    }

    /**
     * Removes the bindings installed on a root pane and restores its previous maps.
     *
     * @param root root pane from which to remove the bindings
     */
    public void uninstall(JRootPane root) {

        Binding binding = windows.remove(root);
        if (binding == null) {
            return;
        }

        if (root.getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW) == binding.input) {
            root.setInputMap(
                    JComponent.WHEN_IN_FOCUSED_WINDOW,
                    binding.previousInput);
        }

        if (root.getActionMap() == binding.map) {
            root.setActionMap(binding.previousActionMap);
        }
    }

    private void refresh() {

        for (Binding binding : windows.values()) {
            binding.input.clear();
            binding.map.clear();

            for (Command command : commands) {
                if (command.getKeyBinding() == null) {
                    continue;
                }

                String key = "swing.commands:" + command.getClass().getName();
                binding.input.put(command.getKeyBinding(), key);
                binding.map.put(key, command);
            }
        }
    }

    /** Removes bindings from all windows and stops observing command changes. */
    @Override
    public void close() {

        for (JRootPane root : new ArrayList<>(windows.keySet())) {
            uninstall(root);
        }

        for (Command command : commands) {
            command.removePropertyChangeListener(changes);
        }
    }
}
