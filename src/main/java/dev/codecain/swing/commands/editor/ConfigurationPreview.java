/*
 * Copyright (c) 2026 Jon Kent
 * https://codecain.dev
 * SPDX-License-Identifier: Apache-2.0
 */

package dev.codecain.swing.commands.editor;

import com.fasterxml.jackson.databind.JsonNode;

import java.awt.BorderLayout;
import java.awt.Component;
import java.util.Objects;
import java.util.function.Supplier;

import javax.swing.*;

final class ConfigurationPreview extends JPanel {

    private final Supplier<ConfigurationDocument> documentSupplier;

    ConfigurationPreview(Supplier<ConfigurationDocument> documentSupplier) {
        this.documentSupplier = Objects.requireNonNull(documentSupplier);
        initializeUI();
    }

    private void initializeUI() {
        setLayout(new BorderLayout(8, 8));
    }

    void refresh() {
        removeAll();

        JPanel north = new JPanel();
        north.setLayout(new BoxLayout(north, BoxLayout.Y_AXIS));

        JMenuBar bar = new JMenuBar();
        for (JsonNode menu : document().root.path("menus")) {
            bar.add(previewMenu(menu));
        }
        bar.setAlignmentX(Component.LEFT_ALIGNMENT);
        north.add(bar);

        for (JsonNode toolbar : document().root.path("toolbars")) {
            JToolBar row = new JToolBar(document().label(toolbar));
            row.setFloatable(false);
            for (JsonNode item : toolbar.path("items")) {
                previewToolbar(row, item);
            }
            row.setAlignmentX(Component.LEFT_ALIGNMENT);
            north.add(row);
        }

        add(north, BorderLayout.NORTH);

        JTextArea note = new JTextArea(
                "Layout preview — commands here never execute application code.\n\n"
                        + "Java defaults and icons need the host application to resolve them.\n"
                        + "Commands without label overrides display their stable IDs here.\n"
                        + "Toggle groups require BooleanCommand instances when loaded by the host.");
        note.setEditable(false);
        note.setLineWrap(true);
        note.setWrapStyleWord(true);
        note.setOpaque(false);
        add(note);

        revalidate();
        repaint();
    }

    private ConfigurationDocument document() {
        return documentSupplier.get();
    }

    private JMenu previewMenu(JsonNode node) {
        JMenu menu = new JMenu(document().label(node));
        fillMenu(menu, node.path("items"));
        return menu;
    }

    private void fillMenu(JMenu menu, JsonNode items) {
        for (JsonNode item : items) {
            if (item.has("separator")) {
                menu.addSeparator();
            } else if (item.has("toggleGroup")) {
                for (JsonNode command : item.path("items")) {
                    menu.add(new JCheckBoxMenuItem(document().label(command)));
                }
            } else if (item.has("items")) {
                menu.add(previewMenu(item));
            } else {
                menu.add(new JMenuItem(document().label(item)));
            }
        }
    }

    private void previewToolbar(JToolBar bar, JsonNode node) {
        if (node.has("separator")) {
            bar.addSeparator();
        } else if (node.has("toggleGroup")) {
            for (JsonNode item : node.path("items")) {
                bar.add(new JToggleButton(document().label(item)));
            }
        } else if (node.has("dropdown")) {
            JButton button = new JButton(document().label(node) + " ▾");
            JPopupMenu popup = new JPopupMenu();
            for (JsonNode item : node.path("items")) {
                popup.add(new JMenuItem(document().label(item)));
            }
            button.addActionListener(event -> popup.show(button, 0, button.getHeight()));
            bar.add(button);
        } else {
            bar.add(new JButton(document().label(node)));
        }
    }
}
