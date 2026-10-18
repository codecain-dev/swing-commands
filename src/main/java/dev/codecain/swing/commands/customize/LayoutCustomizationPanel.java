/*
 * Copyright (c) 2026 Code Cain
 * https://codecain.dev
 * SPDX-License-Identifier: Apache-2.0
 */

package dev.codecain.swing.commands.customize;

import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.GridLayout;
import java.awt.Window;
import javax.swing.*;
import javax.swing.tree.TreePath;

import dev.codecain.swing.commands.command.Command;
import dev.codecain.swing.commands.config.Configuration;
import dev.codecain.swing.commands.resource.ResourceModel;
import dev.codecain.swing.commands.resource.ResourceNode;
import dev.codecain.swing.commands.resource.ResourceNode.Kind;

/**
 * Edits a menu or toolbar layout.
 */
final class LayoutCustomizationPanel extends JPanel {

    private final Configuration config;
    private final ResourceModel model;
    private final Window owner;

    private JTree layoutTree;

    LayoutCustomizationPanel(Configuration config, ResourceModel model, Window owner) {

        this.config = config;
        this.model = model;
        this.owner = owner;

        initializeUI();
    }

    private void initializeUI() {

        layoutTree = CustomizerUi.createTree(model);

        setLayout(new BorderLayout());
        add(new JScrollPane(layoutTree), BorderLayout.CENTER);
        add(createControls(), BorderLayout.SOUTH);
    }

    private JPanel createControls() {

        JPanel controls = new JPanel(new GridLayout(0, 4, 4, 4));

        addButton(controls, "Add Command", this::addCommand);
        addButton(controls, "Separator", this::addSeparator);
        addButton(
                controls,
                model.getType() == ResourceModel.Type.MENU ? "New Menu" : "New Toolbar",
                this::addContainer);
        addButton(controls, "Toggle Group", this::addToggleGroup);

        if (model.getType() == ResourceModel.Type.TOOLBAR) {
            addButton(controls, "Dropdown", this::addDropdown);
            addButton(controls, "Options", this::editToolbarOptions);
        }

        addButton(controls, "Rename", this::renameSelected);
        addButton(controls, "Up", () -> moveSelected(-1));
        addButton(controls, "Down", () -> moveSelected(1));
        addButton(controls, "Move To…", this::moveSelectedTo);
        addButton(controls, "Remove", this::removeSelected);

        return controls;
    }

    private void addCommand() {

        ResourceNode parent = CustomizerUi.selectedContainer(layoutTree);

        if (parent == null) {
            return;
        }

        Command[] choices = config.getCommands()
                .stream()
                .filter(Command::isCustomizable)
                .toArray(Command[]::new);
        JComboBox<Command> commandChoices = new JComboBox<>(choices);
        commandChoices.setRenderer(new DefaultListCellRenderer() {
            @Override
            public Component getListCellRendererComponent(
                    JList<?> list,
                    Object value,
                    int index,
                    boolean selected,
                    boolean focus) {

                Object label = value instanceof Command command ? command.getName() : value;

                return super.getListCellRendererComponent(
                        list,
                        label,
                        index,
                        selected,
                        focus);
            }
        });

        int result = JOptionPane.showConfirmDialog(
                owner,
                commandChoices,
                "Add Command",
                JOptionPane.OK_CANCEL_OPTION);

        if (result == JOptionPane.OK_OPTION && commandChoices.getSelectedItem() != null) {
            Command command = (Command) commandChoices.getSelectedItem();
            edit(() -> model.add(parent, ResourceNode.command(command)));
        }
    }

    private void addSeparator() {

        ResourceNode parent = CustomizerUi.selectedContainer(layoutTree);

        if (parent != null) {
            edit(() -> model.add(parent, new ResourceNode(Kind.SEPARATOR, "Separator")));
        }
    }

    private void addContainer() {

        ResourceNode parent = model.getType() == ResourceModel.Type.TOOLBAR
                ? model.getRoot()
                : CustomizerUi.selectedContainer(layoutTree);

        if (parent == null) {
            return;
        }

        String name = JOptionPane.showInputDialog(owner, "Name:");

        if (name != null && !name.isBlank()) {
            edit(() -> model.add(parent, ResourceNode.group(name)));
        }
    }

    private void addToggleGroup() {

        ResourceNode parent = CustomizerUi.selectedContainer(layoutTree);

        if (parent != null) {
            edit(() -> model.add(parent, new ResourceNode(Kind.TOGGLE_GROUP, "Selection")));
        }
    }

    private void addDropdown() {

        ResourceNode parent = CustomizerUi.selectedContainer(layoutTree);

        if (parent != null) {
            edit(() -> model.add(parent, new ResourceNode(Kind.DROPDOWN, "Commands")));
        }
    }

    private void editToolbarOptions() {

        ResourceNode node = CustomizerUi.selectedNode(layoutTree);

        if (node == null || node.getParent() != model.getRoot()) {
            return;
        }

        JCheckBox labels = new JCheckBox(
                "Show text",
                Boolean.parseBoolean(node.attribute("showLabels", "false")));
        JCheckBox floating = new JCheckBox(
                "Floatable",
                Boolean.parseBoolean(node.attribute("floatable", "false")));
        JPanel options = new JPanel(new GridLayout(0, 1));
        options.add(labels);
        options.add(floating);

        int result = JOptionPane.showConfirmDialog(
                owner,
                options,
                "Toolbar Options",
                JOptionPane.OK_CANCEL_OPTION);

        if (result == JOptionPane.OK_OPTION) {
            node.getAttributes().put("showLabels", Boolean.toString(labels.isSelected()));
            node.getAttributes().put("floatable", Boolean.toString(floating.isSelected()));
            model.nodeChanged(node);
        }
    }

    private void renameSelected() {

        ResourceNode node = CustomizerUi.selectedNode(layoutTree);

        if (node == null
                || node == model.getRoot()
                || node.getCommand() != null
                || node.getKind() == Kind.SEPARATOR) {
            return;
        }

        String name = JOptionPane.showInputDialog(owner, "Name:", node.getName());

        if (name != null) {
            edit(() -> model.rename(node, name));
        }
    }

    private void moveSelected(int direction) {

        ResourceNode node = CustomizerUi.selectedNode(layoutTree);

        if (node == null || node.getParent() == null) {
            return;
        }

        ResourceNode parent = node.getParent();
        int index = parent.getIndex(node);

        if (index + direction < 0 || index + direction >= parent.getChildCount()) {
            return;
        }

        edit(() -> {
            int destinationIndex = direction < 0 ? index - 1 : index + 2;
            model.move(node, parent, destinationIndex);
            layoutTree.setSelectionPath(new TreePath(node.getPath()));
        });
    }

    private void moveSelectedTo() {

        ResourceNode node = CustomizerUi.selectedNode(layoutTree);

        if (node == null || node == model.getRoot()) {
            return;
        }

        JTree destinations = CustomizerUi.createTree(model);
        int result = JOptionPane.showConfirmDialog(
                owner,
                new JScrollPane(destinations),
                "Choose Destination",
                JOptionPane.OK_CANCEL_OPTION);

        if (result == JOptionPane.OK_OPTION) {
            ResourceNode parent = CustomizerUi.selectedNode(destinations);

            if (parent != null) {
                edit(() -> model.move(node, parent, parent.getChildCount()));
            }
        }
    }

    private void removeSelected() {

        ResourceNode node = CustomizerUi.selectedNode(layoutTree);

        if (node == null || node == model.getRoot()) {
            return;
        }

        edit(() -> {
            ensureCustomizable(node);
            model.removeNodeFromParent(node);
        });
    }

    private void ensureCustomizable(ResourceNode node) {

        if (node.getCommand() != null && !node.getCommand().isCustomizable()) {
            throw new IllegalArgumentException("This command cannot be removed");
        }

        for (int i = 0; i < node.getChildCount(); i++) {
            ensureCustomizable(node.getChildAt(i));
        }
    }

    private void edit(Runnable command) {

        try {
            command.run();
        } catch (RuntimeException ex) {
            showError(ex);
        }
    }

    private void showError(Exception exception) {

        JOptionPane.showMessageDialog(
                owner,
                exception.getMessage(),
                "Configuration",
                JOptionPane.ERROR_MESSAGE);
    }

    private static void addButton(JPanel panel, String text, Runnable command) {

        JButton button = new JButton(text);
        button.addActionListener(event -> command.run());
        panel.add(button);
    }
}