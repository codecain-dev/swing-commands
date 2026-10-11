package dev.codecain.swing.commands.resource;

import java.util.EnumSet;
import java.util.Set;
import javax.swing.tree.DefaultTreeModel;
import javax.swing.tree.MutableTreeNode;
import javax.swing.tree.TreePath;

import dev.codecain.swing.commands.command.BooleanCommand;
import dev.codecain.swing.commands.resource.ResourceNode.Kind;

/**
 * The same model is edited by a JTree and observed by menu and toolbar presenters.
 */
public final class ResourceModel extends DefaultTreeModel {

    private static final Set<Kind> COMMAND_CHILDREN =
            EnumSet.of(Kind.GROUP, Kind.COMMAND);

    private static final Set<Kind> MENU_GROUP_CHILDREN =
            EnumSet.of(Kind.GROUP, Kind.COMMAND, Kind.SEPARATOR, Kind.TOGGLE_GROUP);

    private static final Set<Kind> TOOLBAR_GROUP_CHILDREN =
            EnumSet.of(Kind.COMMAND, Kind.SEPARATOR, Kind.TOGGLE_GROUP, Kind.DROPDOWN);

    public enum Type {
        COMMAND,
        MENU,
        TOOLBAR
    }

    private final Type type;

    public ResourceModel(Type type, ResourceNode root) {
        super(root);

        this.type = type;
    }

    public Type getType() {
        return type;
    }

    @Override
    public ResourceNode getRoot() {
        return (ResourceNode) super.getRoot();
    }

    public void add(ResourceNode parent, ResourceNode child) {
        insertNodeInto(child, parent, parent.getChildCount());
    }

    @Override
    public void insertNodeInto(MutableTreeNode child, MutableTreeNode parent, int index) {

        if (!(child instanceof ResourceNode resourceChild)
                || !(parent instanceof ResourceNode resourceParent)) {
            throw new IllegalArgumentException("Resource nodes required");
        }

        requireMember(resourceParent);

        if (resourceChild.getParent() != null) {
            throw new IllegalArgumentException("Use move() for an existing node");
        }

        validate(resourceParent, resourceChild);

        try {
            super.insertNodeInto(resourceChild, resourceParent, index);
        } catch (RuntimeException exception) {
            if (resourceChild.getParent() == resourceParent) {
                resourceParent.remove(resourceChild);
            }

            nodeStructureChanged(getRoot());
            throw exception;
        }
    }

    private void validate(ResourceNode parent, ResourceNode child) {

        Kind parentKind = parent.getKind();
        Kind childKind = child.getKind();

        boolean valid = switch (type) {
            case COMMAND -> isValidCommand(parentKind, childKind);
            case MENU -> isValidMenu(parentKind, childKind);
            case TOOLBAR -> isValidToolbar(parentKind, childKind);
        };

        if (!valid) {
            throw new IllegalArgumentException(
                    "Cannot place " + childKind + " inside " + parentKind + " in " + type);
        }

        if (parentKind == Kind.TOGGLE_GROUP
                && !(child.getCommand() instanceof BooleanCommand)) {
            throw new IllegalArgumentException("Toggle groups require boolean commands");
        }

        for (int index = 0; index < child.getChildCount(); index++) {
            validate(child, child.getChildAt(index));
        }
    }

    private boolean isValidCommand(Kind parent, Kind child) {

        switch (parent) {
            case ROOT:
            case GROUP:
                return COMMAND_CHILDREN.contains(child);
            default:
                return false;
        }
    }

    private boolean isValidMenu(Kind parent, Kind child) {

        switch (parent) {
            case ROOT:
                return child == Kind.GROUP;
            case GROUP:
                return MENU_GROUP_CHILDREN.contains(child);
            case TOGGLE_GROUP:
                return child == Kind.COMMAND;
            default:
                return false;
        }
    }

    private boolean isValidToolbar(Kind parent, Kind child) {

        switch (parent) {
            case ROOT:
                return child == Kind.GROUP;
            case GROUP:
                return TOOLBAR_GROUP_CHILDREN.contains(child);
            case TOGGLE_GROUP:
            case DROPDOWN:
                return child == Kind.COMMAND;
            default:
                return false;
        }
    }

    public void move(ResourceNode node, ResourceNode parent, int index) {

        requireMember(node);
        requireMember(parent);

        if (node == getRoot() || node.isNodeDescendant(parent)) {
            throw new IllegalArgumentException("Cannot move root or create a cycle");
        }

        validate(parent, node);

        if (index < 0 || index > parent.getChildCount()) {
            throw new IndexOutOfBoundsException(index);
        }

        ResourceNode oldParent = node.getParent();
        int previousIndex = oldParent.getIndex(node);

        if (oldParent == parent && index > previousIndex) {
            index--;
        }

        // Notify once so observers never see a temporarily removed node.
        oldParent.remove(node);
        parent.insert(node, index);

        try {
            nodeStructureChanged(getRoot());
        } catch (RuntimeException exception) {
            parent.remove(node);
            oldParent.insert(node, previousIndex);
            nodeStructureChanged(getRoot());
            throw exception;
        }
    }

    @Override
    public void removeNodeFromParent(MutableTreeNode node) {

        requireMember((ResourceNode) node);

        if (node == getRoot()) {
            throw new IllegalArgumentException("Cannot remove root");
        }

        ResourceNode parent = ((ResourceNode) node).getParent();
        int index = parent.getIndex(node);

        try {
            super.removeNodeFromParent(node);
        } catch (RuntimeException exception) {
            if (node.getParent() == null) {
                parent.insert(node, index);
            }

            nodeStructureChanged(getRoot());
            throw exception;
        }
    }

    @Override
    public void valueForPathChanged(TreePath path, Object value) {
        rename((ResourceNode) path.getLastPathComponent(), String.valueOf(value));
    }

    public void rename(ResourceNode node, String name) {

        requireMember(node);

        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("A name is required");
        }

        if (node.getCommand() != null) {
            node.getCommand().setName(name);
        } else {
            node.setUserObject(name);
        }

        nodeChanged(node);
    }

    public void replaceWith(ResourceNode root) {

        if (root.getKind() != Kind.ROOT) {
            throw new IllegalArgumentException("Root required");
        }

        for (int index = 0; index < root.getChildCount(); index++) {
            validate(root, root.getChildAt(index));
        }

        setRoot(root);
    }

    private void requireMember(ResourceNode node) {

        if (node.getRoot() != getRoot()) {
            throw new IllegalArgumentException("Node belongs to another model");
        }
    }
}