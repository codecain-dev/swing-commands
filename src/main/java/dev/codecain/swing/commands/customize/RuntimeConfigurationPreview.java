/*
 * Copyright (c) 2026 Jon Kent
 * https://codecain.dev
 * SPDX-License-Identifier: Apache-2.0
 */

package dev.codecain.swing.commands.customize;

import java.awt.BorderLayout;
import java.awt.Dialog;
import javax.swing.*;

import dev.codecain.swing.commands.command.*;
import dev.codecain.swing.commands.resource.*;

/**
 * Builds a non-executing preview of the current menu and toolbar layouts.
 */
final class RuntimeConfigurationPreview {

    private final ResourceModel menuModel;
    private final ResourceModel toolbarModel;

    RuntimeConfigurationPreview(ResourceModel menuModel, ResourceModel toolbarModel) {

        this.menuModel = menuModel;
        this.toolbarModel = toolbarModel;
    }

    void show(Dialog owner) {

        JDialog dialog = new JDialog(owner, "Layout Preview", Dialog.ModalityType.APPLICATION_MODAL);
        JMenuBar menuBar = new JMenuBar();
        ResourceNode menus = menuModel.getRoot();

        for (int i = 0; i < menus.getChildCount(); i++) {
            menuBar.add(previewMenu(menus.getChildAt(i)));
        }

        dialog.setJMenuBar(menuBar);

        JPanel toolbarRows = new JPanel();
        toolbarRows.setLayout(new BoxLayout(toolbarRows, BoxLayout.Y_AXIS));

        ResourceNode toolbars = toolbarModel.getRoot();

        for (int i = 0; i < toolbars.getChildCount(); i++) {

            ResourceNode toolbarNode = toolbars.getChildAt(i);
            JToolBar toolbar = new JToolBar(toolbarNode.getName());
            toolbar.setFloatable(false);
            previewButtons(
                    toolbar,
                    toolbarNode,
                    Boolean.parseBoolean(toolbarNode.attribute("showLabels", "false")));
            toolbarRows.add(toolbar);
        }

        dialog.add(toolbarRows, BorderLayout.NORTH);
        dialog.add(new JLabel(
                " Preview only — commands do not execute.",
                SwingConstants.CENTER));
        dialog.setSize(760, 300);
        dialog.setLocationRelativeTo(owner);
        dialog.setVisible(true);
        dialog.dispose();
    }

    private JMenu previewMenu(ResourceNode node) {

        JMenu menu = new JMenu(node.getName());
        addMenuItems(menu, node);

        return menu;
    }

    private void addMenuItems(JMenu menu, ResourceNode parent) {

        for (int i = 0; i < parent.getChildCount(); i++) {
            ResourceNode node = parent.getChildAt(i);

            if (node.getKind() == ResourceNode.Kind.SEPARATOR) {
                menu.addSeparator();
            } else if (node.getKind() == ResourceNode.Kind.TOGGLE_GROUP) {
                addMenuItems(menu, node);
            } else if (node.getCommand() == null) {
                menu.add(previewMenu(node));
            } else {
                Command command = node.getCommand();
                JMenuItem item = command instanceof BooleanCommand booleanCommand
                        ? new JCheckBoxMenuItem(command.getName(), booleanCommand.isSelected())
                        : new JMenuItem(command.getName());

                item.setIcon(command.getIcon());
                item.setAccelerator(command.getKeyBinding());
                menu.add(item);
            }
        }
    }

    private void previewButtons(JToolBar toolbar, ResourceNode parent, boolean showLabels) {

        for (int i = 0; i < parent.getChildCount(); i++) {
            ResourceNode node = parent.getChildAt(i);

            if (node.getKind() == ResourceNode.Kind.SEPARATOR) {
                toolbar.addSeparator();
            } else if (node.getKind() == ResourceNode.Kind.TOGGLE_GROUP) {
                previewButtons(toolbar, node, showLabels);
            } else if (node.getKind() == ResourceNode.Kind.DROPDOWN) {
                addDropdown(toolbar, node);
            } else if (node.getCommand() != null) {
                addCommandButton(toolbar, node.getCommand(), showLabels);
            }
        }
    }

    private void addDropdown(JToolBar toolbar, ResourceNode node) {

        JButton button = new JButton(node.getName() + " ▾");
        JPopupMenu popup = new JPopupMenu();

        for (int i = 0; i < node.getChildCount(); i++) {
            popup.add(new JMenuItem(node.getChildAt(i).getName()));
        }

        button.addActionListener(event -> popup.show(button, 0, button.getHeight()));
        toolbar.add(button);
    }

    private void addCommandButton(JToolBar toolbar, Command command, boolean showLabels) {

        AbstractButton button = command instanceof BooleanCommand booleanCommand
                ? new JToggleButton(command.getName(), booleanCommand.isSelected())
                : new JButton(command.getName());

        button.setIcon(command.getIcon());

        if (!showLabels && command.getIcon() != null) {
            button.setText("");
        }

        button.setToolTipText(command.getToolTip());
        toolbar.add(button);
    }
}