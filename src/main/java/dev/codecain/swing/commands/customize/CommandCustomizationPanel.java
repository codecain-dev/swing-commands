package dev.codecain.swing.commands.customize;

import java.awt.BorderLayout;
import java.awt.FlowLayout;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Window;
import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;
import javax.swing.*;

import dev.codecain.swing.commands.command.*;
import dev.codecain.swing.commands.config.*;
import dev.codecain.swing.commands.editor.IconPreviewer;
import dev.codecain.swing.commands.resource.ResourceNode;
import dev.codecain.swing.commands.shortcut.KeyStrokeRecorder;

/**
 * Edits command names, tooltips, icons and keyboard shortcuts.
 */
final class CommandCustomizationPanel extends JPanel {

    private final Configuration config;
    private final Window owner;
    private final Consumer<String> status;

    private JTree commandTree;
    private JTextField nameField;
    private JTextField tooltipField;
    private JTextField iconField;
    private KeyStrokeRecorder shortcutRecorder;

    private JButton applyButton;
    private JButton resetButton;
    private JButton resetShortcutsButton;

    CommandCustomizationPanel(Configuration config, Window owner, Consumer<String> status) {

        this.config = config;
        this.owner = owner;
        this.status = status;

        initializeUI();
    }

    private void initializeUI() {

        commandTree = CustomizerUi.createTree(config.getCommandModel());
        nameField = new JTextField();
        tooltipField = new JTextField();
        iconField = new JTextField();
        shortcutRecorder = new KeyStrokeRecorder();

        applyButton = new JButton("Apply Command");
        resetButton = new JButton("Reset Command");
        resetShortcutsButton = new JButton("Reset All Shortcuts");

        buildPanel();
        connectCommands();
        loadSelectedCommand();
    }

    private void buildPanel() {

        JPanel fields = createFields();
        JSplitPane split = new JSplitPane(
                JSplitPane.HORIZONTAL_SPLIT,
                new JScrollPane(commandTree),
                fields);
        split.setResizeWeight(0.35);

        setLayout(new BorderLayout());
        add(split, BorderLayout.CENTER);
    }

    private JPanel createFields() {

        JPanel fields = new JPanel(new GridBagLayout());
        IconPreviewer iconPreview = new IconPreviewer(iconField, this::iconLoader);

        CustomizerUi.addField(fields, 0, "Name", nameField);
        CustomizerUi.addField(fields, 1, "Tooltip", tooltipField);
        CustomizerUi.addField(fields, 2, "Icon resource / file", iconPreview);
        CustomizerUi.addField(fields, 3, "Shortcut", shortcutRecorder);
        fields.add(createCommandButtons(), buttonConstraints());

        GridBagConstraints spacerConstraints = new GridBagConstraints();
        spacerConstraints.gridy = 5;
        spacerConstraints.gridwidth = 2;
        spacerConstraints.weightx = 1;
        spacerConstraints.weighty = 1;
        spacerConstraints.fill = GridBagConstraints.HORIZONTAL;
        fields.add(Box.createGlue(), spacerConstraints);

        return fields;
    }

    private JPanel createCommandButtons() {

        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.LEFT));
        buttons.add(applyButton);
        buttons.add(resetButton);
        buttons.add(resetShortcutsButton);

        return buttons;
    }

    private GridBagConstraints buttonConstraints() {

        GridBagConstraints constraints = new GridBagConstraints();
        constraints.gridy = 4;
        constraints.gridwidth = 2;
        constraints.weightx = 1;
        constraints.fill = GridBagConstraints.HORIZONTAL;

        return constraints;
    }

    private IconPreviewer.Resolver iconLoader() {

        ResourceNode node = CustomizerUi.selectedNode(commandTree);
        Command command = node == null ? null : node.getCommand();
        Class<?> anchor = command == null ? ConfigurationCustomizerDialog.class : command.getClass();
        Icon currentIcon = command == null ? null : command.getIcon();
        String currentPath = command == null ? null : command.getIconResource();

        return path -> path.isBlank() && currentPath == null
                ? currentIcon
                : Command.loadIcon(anchor, path);
    }

    private void connectCommands() {

        commandTree.addTreeSelectionListener(event -> loadSelectedCommand());
        applyButton.addActionListener(event -> applySelectedCommand());
        resetButton.addActionListener(event -> resetSelectedCommand());
        resetShortcutsButton.addActionListener(event -> resetAllShortcuts());
    }

    private void loadSelectedCommand() {

        ResourceNode node = CustomizerUi.selectedNode(commandTree);
        Command command = node == null ? null : node.getCommand();
        boolean enabled = command != null && command.isCustomizable();

        for (JComponent component : List.of(
                nameField,
                tooltipField,
                iconField,
                shortcutRecorder,
                applyButton,
                resetButton)) {
            component.setEnabled(enabled);
        }

        nameField.setText(command == null ? "" : command.getName());
        tooltipField.setText(command == null ? "" : Objects.toString(command.getToolTip(), ""));
        iconField.setText(command == null ? "" : Objects.toString(command.getIconResource(), ""));
        shortcutRecorder.setKeyStroke(command == null ? null : command.getKeyBinding());
    }

    private void applySelectedCommand() {

        ResourceNode node = CustomizerUi.selectedNode(commandTree);

        if (node == null || node.getCommand() == null) {
            return;
        }

        Command command = node.getCommand();

        try {
            if (nameField.getText().isBlank()) {
                throw new IllegalArgumentException("A name is required");
            }

            KeyStroke stroke = shortcutRecorder.getKeyStroke();

            if (stroke != null) {
                stroke = ConfigurationManager.parseKey(stroke.toString());
            }

            boolean iconChanged = !Objects.toString(command.getIconResource(), "")
                    .equals(iconField.getText());

            if (iconChanged) {
                Command.loadIcon(command.getClass(), iconField.getText());
            }

            Command conflict = config.findKeyBinding(stroke);

            if (conflict != null
                    && conflict != command
                    && JOptionPane.showConfirmDialog(
                            owner,
                            "This shortcut belongs to " + conflict.getName() + ". Reassign it?",
                            "Shortcut Conflict",
                            JOptionPane.YES_NO_OPTION) != JOptionPane.YES_OPTION) {
                return;
            }

            config.setKeyBinding(command, stroke, true);
            command.setName(nameField.getText());
            command.setToolTip(tooltipField.getText());

            if (iconChanged) {
                command.setIconResource(iconField.getText());
            }

            status.accept("Command updated. Save to keep the change.");
        } catch (RuntimeException ex) {
            showError(ex);
        }
    }

    private void resetSelectedCommand() {

        try {
            ResourceNode node = CustomizerUi.selectedNode(commandTree);

            if (node != null && node.getCommand() != null) {
                config.resetCommand(node.getCommand());
                loadSelectedCommand();
            }
        } catch (RuntimeException ex) {
            showError(ex);
        }
    }

    private void resetAllShortcuts() {

        config.resetKeyBindings();
        loadSelectedCommand();
    }

    private void showError(Exception exception) {

        JOptionPane.showMessageDialog(
                owner,
                exception.getMessage(),
                "Configuration",
                JOptionPane.ERROR_MESSAGE);
    }
}