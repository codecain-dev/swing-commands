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

    public enum Kind {
        ROOT,
        GROUP,
        COMMAND,
        SEPARATOR,
        TOGGLE_GROUP,
        DROPDOWN
    }

    private final Kind kind;
    private final Map<String, String> attributes = new LinkedHashMap<>();

    public ResourceNode(Kind kind, Object value) {
        super(value, kind != Kind.COMMAND && kind != Kind.SEPARATOR);

        this.kind = kind;

        if (kind == Kind.COMMAND && !(value instanceof Command)) {
            throw new IllegalArgumentException("Command node needs a Command");
        }
    }

    public static ResourceNode command(Command command) {
        return new ResourceNode(Kind.COMMAND, command);
    }

    public static ResourceNode group(String name) {
        return new ResourceNode(Kind.GROUP, name);
    }

    public Kind getKind() {
        return kind;
    }

    public Command getCommand() {
        return kind == Kind.COMMAND ? (Command) getUserObject() : null;
    }

    public String getName() {
        return getCommand() == null
                ? String.valueOf(getUserObject())
                : getCommand().getName();
    }

    public Map<String, String> getAttributes() {
        return attributes;
    }

    public String attribute(String name, String fallback) {
        return attributes.getOrDefault(name, fallback);
    }

    public ResourceNode copy() {

        ResourceNode result = new ResourceNode(kind, getUserObject());
        result.attributes.putAll(attributes);

        for (int index = 0; index < getChildCount(); index++) {
            result.add(getChildAt(index).copy());
        }

        return result;
    }

    @Override
    public ResourceNode getChildAt(int index) {
        return (ResourceNode) super.getChildAt(index);
    }

    @Override
    public ResourceNode getParent() {
        return (ResourceNode) super.getParent();
    }

    @Override
    public String toString() {
        return kind == Kind.SEPARATOR ? "────────" : getName();
    }
}