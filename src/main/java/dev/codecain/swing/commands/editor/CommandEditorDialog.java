/*
 * Copyright (c) 2026 Jon Kent
 * https://codecain.dev
 * SPDX-License-Identifier: Apache-2.0
 */

package dev.codecain.swing.commands.editor;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.awt.Component;
import java.awt.GridLayout;
import java.nio.file.Path;

import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JTextField;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;

import dev.codecain.swing.commands.config.ConfigurationManager;
import dev.codecain.swing.commands.shortcut.KeyStrokeRecorder;

/**
 * Modal editor for a single command definition.
 *
 * This class owns the Swing controls used to edit a command, but deliberately
 * does not mutate ConfigurationDocument. The caller remains responsible for
 * applying and validating the returned result.
 */
final class CommandEditorDialog {

    private static final String[] STATE_OPTIONS = {
            "Inherit Java default",
            "Selected (true)",
            "Unselected (false)"
    };

    private static final String[] ENABLED_OPTIONS = {
            "Inherit Java default",
            "Enabled (true)",
            "Disabled (false)"
    };

    private final Component parent;
    private final boolean copy;
    private final boolean registering;
    private final Path resourceRoot;

    private JPanel form;

    private JTextField commandIdField;
    private JTextField classNameField;
    private JTextField categoryField;
    private JTextField labelField;
    private JTextField tooltipField;
    private KeyStrokeRecorder shortcutRecorder;
    private JTextField iconField;

    private JCheckBox removeShortcutCheckBox;
    private JCheckBox removeIconCheckBox;

    private JComboBox<String> selectedDefaultComboBox;
    private JComboBox<String> enabledDefaultComboBox;

    CommandEditorDialog(
            Component parent,
            JsonNode definition,
            String initialCommandId,
            boolean copy,
            Path resourceRoot) {

        this.parent = parent;
        this.copy = copy;
        this.registering = !copy && initialCommandId.isEmpty();
        this.resourceRoot = resourceRoot;

        initializeUI(definition, initialCommandId);
    }

    private void initializeUI(JsonNode definition, String initialCommandId) {
        commandIdField = new JTextField(initialCommandId, 32);
        classNameField = new JTextField(definition.path("class").asText(""), 32);
        categoryField = new JTextField(definition.path("category").asText(""), 32);
        labelField = new JTextField(definition.path("label").asText(""), 32);
        tooltipField = new JTextField(definition.path("tooltip").asText(""), 32);
        shortcutRecorder = new KeyStrokeRecorder();
        shortcutRecorder.setKeyStroke(
                ConfigurationManager.parseKey(definition.path("shortcut").asText("")));
        iconField = new JTextField(definition.path("icon").asText(""), 32);

        removeShortcutCheckBox = new JCheckBox(
                "Remove default shortcut",
                definition.has("shortcut") && definition.get("shortcut").isNull());

        removeIconCheckBox = new JCheckBox(
                "Remove default icon",
                definition.has("icon") && definition.get("icon").isNull());

        selectedDefaultComboBox = new JComboBox<>(STATE_OPTIONS);
        selectedDefaultComboBox.setSelectedIndex(
                definition.has("selected")
                        ? (definition.path("selected").asBoolean() ? 1 : 2)
                        : 0);

        enabledDefaultComboBox = new JComboBox<>(ENABLED_OPTIONS);
        enabledDefaultComboBox.setSelectedIndex(
                definition.has("enabled")
                        ? (definition.path("enabled").asBoolean() ? 1 : 2)
                        : 0);

        form = new JPanel(new GridLayout(0, 2, 8, 8));

        addField("Stable command ID", commandIdField);
        addField("Java class", classNameField);
        addField("Category", categoryField);
        addField("Label override", labelField);
        addField("Tooltip override", tooltipField);
        form.add(new JLabel("Shortcut override"));
        form.add(shortcutRecorder);

        form.add(new JLabel("Icon resource / file"));

        IconPreviewer iconPreview = new IconPreviewer(iconField, () -> {
            boolean removed = removeIconCheckBox.isSelected();
            String className = classNameField.getText().trim();

            return path -> removed
                    ? null
                    : EditorIconLoader.load(path, className, resourceRoot);
        });

        form.add(iconPreview);

        removeIconCheckBox.addActionListener(event -> iconPreview.refresh());
        classNameField.getDocument().addDocumentListener(
                new RefreshDocumentListener(iconPreview::refresh));

        form.add(removeShortcutCheckBox);
        form.add(removeIconCheckBox);

        form.add(new JLabel("Initial selection (boolean commands only)"));
        form.add(selectedDefaultComboBox);

        form.add(new JLabel("Initial enabled state"));
        form.add(enabledDefaultComboBox);

        form.add(new JLabel("Blank overrides inherit Java defaults."));
        form.add(new JLabel("Focus the shortcut field and press a key combination."));

        if (copy) {
            form.add(new JLabel("Copy requirements"));
            form.add(new JLabel(
                    "Change the Java class; command classes and explicit shortcuts must be unique."));
        }
    }

    Result showDialog() {
        int answer = JOptionPane.showConfirmDialog(
                parent,
                form,
                dialogTitle(),
                JOptionPane.OK_CANCEL_OPTION,
                JOptionPane.PLAIN_MESSAGE);

        if (answer != JOptionPane.OK_OPTION) {
            return null;
        }

        return new Result(commandIdField.getText().trim(), createDefinition());
    }

    private String dialogTitle() {
        if (copy) {
            return "Copy Command";
        }

        return registering ? "Register Command" : "Edit Command";
    }

    private ObjectNode createDefinition() {
        ObjectNode definition = JsonNodeFactory.instance.objectNode();

        definition.put("class", classNameField.getText().trim());

        putIfPresent(definition, "category", categoryField);
        putIfPresent(definition, "label", labelField);
        putIfPresent(definition, "tooltip", tooltipField);
        if (shortcutRecorder.getKeyStroke() != null) {
            definition.put("shortcut", shortcutRecorder.getKeyStroke().toString());
        }
        putIfPresent(definition, "icon", iconField);

        if (removeShortcutCheckBox.isSelected()) {
            definition.putNull("shortcut");
        }

        if (removeIconCheckBox.isSelected()) {
            definition.putNull("icon");
        }

        if (selectedDefaultComboBox.getSelectedIndex() != 0) {
            definition.put(
                    "selected",
                    selectedDefaultComboBox.getSelectedIndex() == 1);
        }

        if (enabledDefaultComboBox.getSelectedIndex() != 0) {
            definition.put(
                    "enabled",
                    enabledDefaultComboBox.getSelectedIndex() == 1);
        }

        return definition;
    }

    private void addField(String label, JTextField field) {
        form.add(new JLabel(label));
        form.add(field);
    }

    private static void putIfPresent(
            ObjectNode definition,
            String key,
            JTextField field) {

        if (!field.getText().isBlank()) {
            definition.put(key, field.getText().trim());
        }
    }

    record Result(String commandId, ObjectNode definition) {
    }

    private static final class RefreshDocumentListener implements DocumentListener {

        private final Runnable refresh;

        private RefreshDocumentListener(Runnable refresh) {
            this.refresh = refresh;
        }

        @Override
        public void insertUpdate(DocumentEvent event) {
            refresh.run();
        }

        @Override
        public void removeUpdate(DocumentEvent event) {
            refresh.run();
        }

        @Override
        public void changedUpdate(DocumentEvent event) {
            refresh.run();
        }
    }
}

