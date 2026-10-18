/*
 * Copyright (c) 2026 Code Cain
 * https://codecain.dev
 * SPDX-License-Identifier: Apache-2.0
 */

package dev.codecain.swing.commands.editor;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.awt.BorderLayout;
import java.awt.GridLayout;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.Supplier;

import javax.swing.*;
import javax.swing.tree.*;

final class LayoutEditor extends JPanel {

    enum Type {
        MENUS("menus", "Menu Bar", "Add Menu…"),
        TOOLBARS("toolbars", "Toolbars", "Add Toolbar…");

        final String section;
        final String rootLabel;
        final String addLabel;

        Type(String section, String rootLabel, String addLabel) {
            this.section = section;
            this.rootLabel = rootLabel;
            this.addLabel = addLabel;
        }
    }

    private final Type type;
    private final Supplier<ConfigurationDocument> documentSupplier;
    private final Runnable refreshEditor;
    private final Consumer<String> statusMessage;
    private JTree tree;

    LayoutEditor(Type type, Supplier<ConfigurationDocument> documentSupplier,
                 Runnable refreshEditor, Consumer<String> statusMessage) {
        this.type = type;
        this.documentSupplier = documentSupplier;
        this.refreshEditor = refreshEditor;
        this.statusMessage = statusMessage;
        initializeUI();
    }

    private void initializeUI() {
        setLayout(new BorderLayout(8, 8));
        tree = new JTree();
        tree.setRootVisible(true);
        tree.setShowsRootHandles(true);
        tree.getSelectionModel().setSelectionMode(TreeSelectionModel.SINGLE_TREE_SELECTION);
        add(new JScrollPane(tree));

        JPanel controls = new JPanel(new GridLayout(0, 4, 4, 4));
        button(controls, type.addLabel, () -> addContainer("label"));
        button(controls, "Add Command…", this::addCommand);
        button(controls, "Separator",
                () -> addItem(JsonNodeFactory.instance.objectNode().put("separator", true)));
        button(controls, "Toggle Group…", () -> addContainer("toggleGroup"));
        if (type == Type.TOOLBARS) {
            button(controls, "Dropdown…", () -> addContainer("dropdown"));
        }
        button(controls, "Properties…", this::properties);
        button(controls, "Up", () -> move(-1));
        button(controls, "Down", () -> move(1));
        button(controls, "Move To…", this::moveTo);
        button(controls, "Remove", this::removeSelected);
        add(controls, BorderLayout.SOUTH);
    }

    void refresh() {
        ObjectNode root = JsonNodeFactory.instance.objectNode();
        root.set("items", document().section(type.section));
        tree.setModel(new DefaultTreeModel(branch(root, type.rootLabel)));
        expand(tree);
        tree.setSelectionRow(0);
    }

    private ConfigurationDocument document() {
        return documentSupplier.get();
    }

    private void addContainer(String field) {
        String label = JOptionPane.showInputDialog(this, "Name:");
        if (label == null || label.isBlank()) {
            return;
        }

        ObjectNode node = JsonNodeFactory.instance.objectNode().put(field, label);
        node.putArray("items");
        if (type == Type.TOOLBARS && field.equals("label")) {
            document().section(type.section).add(node);
            refreshEditor.run();
            selectObject(node);
        } else {
            addItem(node);
        }
    }

    private void addCommand() {
        if (document().commands().isEmpty()) {
            statusMessage.accept("Register a command first.");
            return;
        }
        JComboBox<String> choice = new JComboBox<>();
        document().commands().fieldNames().forEachRemaining(choice::addItem);
        if (JOptionPane.showConfirmDialog(this, choice, "Choose Command",
                JOptionPane.OK_CANCEL_OPTION) == JOptionPane.OK_OPTION) {
            addItem(JsonNodeFactory.instance.objectNode()
                    .put("command", Objects.toString(choice.getSelectedItem())));
        }
    }

    private boolean allowed(DefaultMutableTreeNode parent, ObjectNode child) {
        Entry entry = entry(parent);
        if (!entry.node.has("items")) {
            return false;
        }
        if (entry.rootLabel != null) {
            return child.has("label");
        }
        if (entry.node.has("toggleGroup") || entry.node.has("dropdown")) {
            return child.has("command");
        }
        return type == Type.MENUS ? !child.has("dropdown") : !child.has("label");
    }

    private void addItem(ObjectNode child) {
        DefaultMutableTreeNode parent = container();
        if (parent == null || !allowed(parent, child)) {
            statusMessage.accept("Select a suitable menu, toolbar, or command group first.");
            return;
        }
        children(parent).add(child);
        refreshEditor.run();
        selectObject(child);
    }

    private void removeSelected() {
        DefaultMutableTreeNode selected = selected();
        if (selected != null && selected.getParent() != null) {
            remove(selected);
            refreshEditor.run();
        }
    }

    private void remove(DefaultMutableTreeNode node) {
        DefaultMutableTreeNode parent = (DefaultMutableTreeNode) node.getParent();
        ObjectNode object = entry(node).node;
        ObjectNode container = entry(parent).node;

        // A dropdown cannot keep a default that points to an item removed from its choices.
        if (object.has("command") && container.path("defaultCommand").equals(object.get("command"))) {
            container.remove("defaultCommand");
        }
        children(parent).remove(parent.getIndex(node));
    }

    private void move(int delta) {
        DefaultMutableTreeNode node = selected();
        if (node == null || node.getParent() == null) {
            return;
        }
        DefaultMutableTreeNode parent = (DefaultMutableTreeNode) node.getParent();
        int index = parent.getIndex(node), target = index + delta;
        ArrayNode items = children(parent);
        if (target < 0 || target >= items.size()) {
            return;
        }
        JsonNode value = items.remove(index);
        items.insert(target, value);
        refreshEditor.run();
        selectObject(value);
    }

    private void moveTo() {
        DefaultMutableTreeNode node = selected();
        if (node == null || node.getParent() == null) {
            return;
        }
        JTree destinations = new JTree(tree.getModel());
        expand(destinations);
        if (JOptionPane.showConfirmDialog(this, new JScrollPane(destinations),
                "Choose Destination", JOptionPane.OK_CANCEL_OPTION) != JOptionPane.OK_OPTION) return;

        DefaultMutableTreeNode parent =
                (DefaultMutableTreeNode) destinations.getLastSelectedPathComponent();
        ObjectNode value = entry(node).node;
        if (parent == null || node.isNodeDescendant(parent) || !allowed(parent, value)) {
            statusMessage.accept("That destination cannot contain this item.");
            return;
        }
        remove(node);
        children(parent).add(value);
        refreshEditor.run();
        selectObject(value);
    }

    private void properties() {
        DefaultMutableTreeNode selected = selected();
        if (selected == null || selected.getParent() == null) {
            return;
        }
        ObjectNode value = entry(selected).node;
        if (value.has("command") || value.has("separator")) {
            statusMessage.accept("Edit command presentation in Registered Commands.");
            return;
        }

        String key = value.has("toggleGroup") ? "toggleGroup"
                : value.has("dropdown") ? "dropdown" : "label";
        JTextField label = new JTextField(value.path(key).asText());
        JTextField id = new JTextField(value.path("id").asText(""));
        JTextField mnemonic = new JTextField(value.path("mnemonic").asText(""));
        JCheckBox labels = new JCheckBox("Show labels", value.path("showLabels").asBoolean());
        JCheckBox floating = new JCheckBox("Floatable", value.path("floatable").asBoolean());

        JComboBox<String> defaultCommand = new JComboBox<>();
        defaultCommand.addItem("");
        for (JsonNode child : value.path("items"))
            if (child.has("command")) {
                defaultCommand.addItem(child.path("command").asText());
            }
        defaultCommand.setSelectedItem(value.path("defaultCommand").asText(""));

        JPanel form = new JPanel(new GridLayout(0, 2, 8, 8));
        form.add(new JLabel("Name"));
        form.add(label);
        if (key.equals("label")) {
            form.add(new JLabel("Container ID (optional)"));
            form.add(id);
            if (type == Type.MENUS) {
                form.add(new JLabel("Mnemonic (one character)"));
                form.add(mnemonic);
            } else {
                form.add(labels);
                form.add(floating);
            }
        }
        if (key.equals("dropdown")) {
            form.add(new JLabel("Default command"));
            form.add(defaultCommand);
        }

        if (JOptionPane.showConfirmDialog(this, form, "Properties",
                JOptionPane.OK_CANCEL_OPTION) != JOptionPane.OK_OPTION) return;
        if (label.getText().isBlank() || mnemonic.getText().length() > 1) {
            statusMessage.accept("A name is required; a mnemonic is at most one character.");
            return;
        }

        value.put(key, label.getText());
        if (key.equals("label")) {
            optional(value, "id", id.getText());
            if (type == Type.MENUS) {
                optional(value, "mnemonic", mnemonic.getText());
            } else {
                value.put("showLabels", labels.isSelected());
                value.put("floatable", floating.isSelected());
            }
        }
        if (key.equals("dropdown")) {
            optional(
                    value,
                    "defaultCommand",
                    Objects.toString(defaultCommand.getSelectedItem(), ""));
        }

        refreshEditor.run();
        selectObject(value);
    }

    private DefaultMutableTreeNode selected() {
        return (DefaultMutableTreeNode) tree.getLastSelectedPathComponent();
    }

    private ArrayNode children(DefaultMutableTreeNode node) {
        return (ArrayNode) entry(node).node.get("items");
    }

    private DefaultMutableTreeNode container() {
        DefaultMutableTreeNode node = selected();
        if (node == null) {
            node = (DefaultMutableTreeNode) tree.getModel().getRoot();
        }
        if (!entry(node).node.has("items")) {
            node = (DefaultMutableTreeNode) node.getParent();
        }
        return node;
    }

    private Entry entry(DefaultMutableTreeNode node) {
        return (Entry) node.getUserObject();
    }

    private DefaultMutableTreeNode branch(ObjectNode node, String label) {
        DefaultMutableTreeNode result = new DefaultMutableTreeNode(new Entry(node, label));
        for (JsonNode item : node.path("items")) result.add(branch((ObjectNode) item, null));
        return result;
    }

    private void selectObject(JsonNode value) {
        var nodes = ((DefaultMutableTreeNode) tree.getModel().getRoot()).depthFirstEnumeration();
        while (nodes.hasMoreElements()) {
            DefaultMutableTreeNode node = (DefaultMutableTreeNode) nodes.nextElement();
            if (entry(node).node == value) {
                tree.setSelectionPath(new TreePath(node.getPath()));
                break;
            }
        }
    }

    private static void expand(JTree tree) {
        for (int i = 0; i < tree.getRowCount(); i++) tree.expandRow(i);
    }

    private static void optional(ObjectNode node, String key, String value) {
        if (value.isBlank()) {
            node.remove(key);
        }
        else node.put(key, value);
    }

    private static void button(JPanel panel, String label, Runnable work) {
        JButton button = new JButton(label);
        button.addActionListener(e -> work.run());
        panel.add(button);
    }

    private final class Entry {
        final ObjectNode node;
        final String rootLabel;

        Entry(ObjectNode node, String rootLabel) {
            this.node = node;
            this.rootLabel = rootLabel;
        }

        @Override
        public String toString() {
            return rootLabel != null ? rootLabel : document().label(node);
        }
    }
}
