package dev.codecain.swing.commands.customize;

import java.awt.Component;
import java.awt.GridBagConstraints;
import java.awt.Insets;
import javax.swing.*;
import javax.swing.tree.*;

import dev.codecain.swing.commands.command.Command;
import dev.codecain.swing.commands.resource.ResourceModel;
import dev.codecain.swing.commands.resource.ResourceNode;

/**
 * Shared Swing helpers for the command customization views.
 */
final class CustomizerUi {

    private CustomizerUi() {
    }

    static JTree createTree(ResourceModel model) {

        JTree tree = new JTree(model);
        tree.setRootVisible(true);
        tree.setShowsRootHandles(true);
        tree.getSelectionModel().setSelectionMode(TreeSelectionModel.SINGLE_TREE_SELECTION);
        tree.setCellRenderer(new DefaultTreeCellRenderer() {
            @Override
            public Component getTreeCellRendererComponent(
                    JTree tree,
                    Object value,
                    boolean selected,
                    boolean expanded,
                    boolean leaf,
                    int row,
                    boolean focus) {

                super.getTreeCellRendererComponent(tree, value, selected, expanded, leaf, row, focus);

                if (value instanceof ResourceNode node
                        && node.getCommand() != null
                        && node.getCommand().getIcon() != null) {
                    setIcon(node.getCommand().getIcon());
                }

                return this;
            }
        });

        for (int row = 0; row < tree.getRowCount(); row++) {
            tree.expandRow(row);
        }

        tree.setSelectionRow(0);

        return tree;
    }

    static ResourceNode selectedNode(JTree tree) {

        return (ResourceNode) tree.getLastSelectedPathComponent();
    }

    static ResourceNode selectedContainer(JTree tree) {

        ResourceNode node = selectedNode(tree);

        if (node != null && !node.getAllowsChildren()) {
            return node.getParent();
        }

        return node;
    }

    static void addField(JPanel panel, int row, String label, JComponent value) {

        GridBagConstraints constraints = new GridBagConstraints();
        constraints.gridy = row;
        constraints.insets = new Insets(6, 6, 6, 6);
        constraints.anchor = GridBagConstraints.WEST;
        panel.add(new JLabel(label), constraints);

        constraints.gridx = 1;
        constraints.weightx = 1;
        constraints.fill = GridBagConstraints.HORIZONTAL;
        panel.add(value, constraints);
    }
}