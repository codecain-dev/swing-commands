package dev.codecain.swing.commands.resource;

import java.util.LinkedHashMap;
import java.util.Map;
import javax.swing.tree.DefaultMutableTreeNode;

import dev.codecain.swing.commands.command.Command;

/**
 * A layout node that can be displayed directly by a {@link javax.swing.JTree}.
 * Multiple placements can refer to the same command instance.
 */
public final class ResourceNode extends DefaultMutableTreeNode {

    /** Identifies the role and allowed children of a resource node. */
    public enum Kind {
        /** Top-level node of a resource model. */
        ROOT,
        /** Named container for other nodes. */
        GROUP,
        /** Placement of a registered command. */
        COMMAND,
        /** Visual separator between neighboring menu or toolbar items. */
        SEPARATOR,
        /** Group whose BooleanCommands are mutually exclusive. */
        TOGGLE_GROUP,
        /** Toolbar control that presents a default command and alternate choices. */
        DROPDOWN
    }

    private final Kind kind;
    private final Map<String, String> attributes = new LinkedHashMap<>();

    /**
     * Creates a layout node of the specified kind.
     *
     * @param kind node role in the resource model
     * @param value command for a command node, or display value for other node kinds
     * @throws IllegalArgumentException if a command node does not contain a {@link Command}
     */
    public ResourceNode(Kind kind, Object value) {
        super(value, kind != Kind.COMMAND && kind != Kind.SEPARATOR);

        this.kind = kind;

        if (kind == Kind.COMMAND && !(value instanceof Command)) {
            throw new IllegalArgumentException("Command node needs a Command");
        }
    }

    /**
     * Creates a node that places the supplied command in a layout.
     *
     * @param command command to place
     * @return command placement node
     */
    public static ResourceNode command(Command command) {
        return new ResourceNode(Kind.COMMAND, command);
    }

    /**
     * Creates a named group node.
     *
     * @param name group display name
     * @return group node
     */
    public static ResourceNode group(String name) {
        return new ResourceNode(Kind.GROUP, name);
    }

    /**
     * Returns this node's role in its resource model.
     *
     * @return node kind
     */
    public Kind getKind() {
        return kind;
    }

    /**
     * Returns the command placed by this node.
     *
     * @return command for a command node, otherwise {@code null}
     */
    public Command getCommand() {
        return kind == Kind.COMMAND ? (Command) getUserObject() : null;
    }

    /**
     * Returns the display name for this node.
     *
     * @return command name or node display value
     */
    public String getName() {
        return getCommand() == null
                ? String.valueOf(getUserObject())
                : getCommand().getName();
    }

    /**
     * Returns the mutable presentation attributes associated with this node.
     *
     * @return node attributes, such as a toolbar's {@code showLabels} setting
     */
    public Map<String, String> getAttributes() {
        return attributes;
    }

    /**
     * Returns an attribute value, or the supplied fallback when it is absent.
     *
     * @param name attribute key
     * @param fallback value to return when the key is absent
     * @return attribute value or fallback
     */
    public String attribute(String name, String fallback) {
        return attributes.getOrDefault(name, fallback);
    }

    /**
     * Copies this node and its descendants while retaining referenced command instances.
     *
     * @return independent copy of the node tree
     */
    public ResourceNode copy() {

        ResourceNode result = new ResourceNode(kind, getUserObject());
        result.attributes.putAll(attributes);

        for (int index = 0; index < getChildCount(); index++) {
            result.add(getChildAt(index).copy());
        }

        return result;
    }

    /**
     * Returns a child node using the resource-specific type.
     *
     * @param index child index
     * @return child at the requested index
     */
    @Override
    public ResourceNode getChildAt(int index) {
        return (ResourceNode) super.getChildAt(index);
    }

    /**
     * Returns the parent node using the resource-specific type.
     *
     * @return parent node, or {@code null} when this node has no parent
     */
    @Override
    public ResourceNode getParent() {
        return (ResourceNode) super.getParent();
    }

    /** Returns a display string suitable for tree rendering. */
    @Override
    public String toString() {
        return kind == Kind.SEPARATOR ? "────────" : getName();
    }
}
