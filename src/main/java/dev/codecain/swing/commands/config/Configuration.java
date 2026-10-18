/*
 * Copyright (c) 2026 Code Cain
 * https://codecain.dev
 * SPDX-License-Identifier: Apache-2.0
 */

package dev.codecain.swing.commands.config;

import java.awt.*;
import java.beans.PropertyChangeListener;
import java.io.IOException;
import java.nio.file.*;
import java.util.*;
import java.util.List;
import javax.swing.*;
import javax.swing.event.*;

import dev.codecain.swing.commands.command.*;
import dev.codecain.swing.commands.resource.*;
import dev.codecain.swing.commands.resource.ResourceNode.Kind;

/**
 * Three tree models and their live Swing presenters. Create/edit/close on the EDT.
 */
public final class Configuration implements AutoCloseable {

    private final String name;

    // Resource models
    private final ResourceModel commands;
    private final ResourceModel menus;
    private final ResourceModel toolbars;

    // Resource nodes
    private final ResourceNode defaultCommands;
    private final ResourceNode defaultMenus;
    private final ResourceNode defaultToolbars;

    private final Map<String, Command> commandsById;
    private final Path userFile;

    private final Map<Command, Defaults> defaults = new LinkedHashMap<>();
    private final List<AbstractButton> presenters = new ArrayList<>();
    private final List<SelectionGroup> selectionGroups = new ArrayList<>();
    private final KeyBindingManager keys;

    private JMenuBar menuBar;
    private JPanel toolbarPanel;

    private String loadWarning;
    private boolean changing;
    private boolean closed;

    private record Defaults(
            String name,
            String tooltip,
            Icon icon,
            String iconResource,
            KeyStroke key) {

        static Defaults of(Command command) {

            return new Defaults(
                    command.getName(),
                    command.getToolTip(),
                    command.getIcon(),
                    command.getIconResource(),
                    command.getKeyBinding());
        }

        void apply(Command command) {

            command.setName(name);
            command.setToolTip(tooltip);
            command.setIcon(icon);
            command.putValue(Command.ICON_RESOURCE, iconResource);
            command.setKeyBinding(key);
        }
    }

    private final TreeModelListener layoutListener = new TreeModelListener() {

        @Override
        public void treeNodesChanged(TreeModelEvent event) {
            rebuild();
        }

        @Override
        public void treeNodesInserted(TreeModelEvent event) {
            rebuild();
        }

        @Override
        public void treeNodesRemoved(TreeModelEvent event) {
            rebuild();
        }

        @Override
        public void treeStructureChanged(TreeModelEvent event) {
            rebuild();
        }
    };

    private final PropertyChangeListener commandListener = this::commandChanged;

    private void commandChanged(java.beans.PropertyChangeEvent event) {

        if (Action.NAME.equals(event.getPropertyName())
                || Action.SMALL_ICON.equals(event.getPropertyName())) {

            Command command = (Command) event.getSource();

            notifyCommandNodes(commands, command);
            notifyCommandNodes(menus, command);
            notifyCommandNodes(toolbars, command);
        }
    }

    Configuration(
            String name,
            ResourceModel commands,
            ResourceModel menus,
            ResourceModel toolbars,
            Map<String, Command> commandsById,
            Path userFile) {

        this.name = name;
        this.commands = commands;
        this.menus = menus;
        this.toolbars = toolbars;
        this.commandsById = Collections.unmodifiableMap(new LinkedHashMap<>(commandsById));
        this.userFile = userFile;

        defaultCommands = commands.getRoot().copy();
        defaultMenus = menus.getRoot().copy();
        defaultToolbars = toolbars.getRoot().copy();
        keys = new KeyBindingManager(this.commandsById.values());

        for (Command command : this.commandsById.values()) {
            defaults.put(command, Defaults.of(command));
            command.addPropertyChangeListener(commandListener);
        }

        menus.addTreeModelListener(layoutListener);
        toolbars.addTreeModelListener(layoutListener);

        initializeUI();
    }

    private void initializeUI() {

        menuBar = new JMenuBar();
        toolbarPanel = new JPanel(new FlowLayout(FlowLayout.LEADING, 0, 0));

        rebuild();
    }

    boolean isClosed() {
        return closed;
    }

    ResourceNode defaultMenus() {
        return defaultMenus;
    }

    ResourceNode defaultToolbars() {
        return defaultToolbars;
    }

    String commandId(Command command) {

        for (Map.Entry<String, Command> entry : commandsById.entrySet()) {
            if (entry.getValue() == command) {
                return entry.getKey();
            }
        }

        throw new IllegalArgumentException("Unregistered command");
    }

    Map<String, String> presentationOverrides(Command command) {

        Defaults state = defaults.get(command);
        Map<String, String> result = new LinkedHashMap<>();

        if (!Objects.equals(state.name, command.getName())) {
            result.put("label", command.getName());
        }

        if (!Objects.equals(state.tooltip, command.getToolTip())) {
            result.put("tooltip", Objects.toString(command.getToolTip(), ""));
        }

        if (!Objects.equals(state.key, command.getKeyBinding())) {
            String shortcut = command.getKeyBinding() == null
                    ? null
                    : command.getKeyBinding().toString();

            result.put("shortcut", shortcut);
        }

        if (!Objects.equals(state.iconResource, command.getIconResource())
                || (state.icon != null && command.getIcon() == null)) {
            result.put("icon", command.getIconResource());
        }

        return result;
    }

    /**
     * Returns the application name defined by the configuration.
     *
     * @return configured application name
     */
    public String getName() {
        return name;
    }

    /**
     * Returns the tree model containing registered commands and command groups.
     *
     * @return live command model
     */
    public ResourceModel getCommandModel() {
        return commands;
    }

    /**
     * Returns the live tree model that defines the menu layout.
     *
     * @return live menu model
     */
    public ResourceModel getMenuModel() {
        return menus;
    }

    /**
     * Returns the live tree model that defines the toolbar layout.
     *
     * @return live toolbar model
     */
    public ResourceModel getToolbarModel() {
        return toolbars;
    }

    /**
     * Returns the commands registered by this configuration.
     *
     * @return unmodifiable collection of command instances
     */
    public Collection<Command> getCommands() {
        return commandsById.values();
    }

    /**
     * Finds the registered command instance assignable to the requested type.
     *
     * @param <T> command type
     * @param type class or superclass of the command to find
     * @return matching registered command
     * @throws IllegalArgumentException if no registered command matches the type
     */
    public <T extends Command> T getCommand(Class<T> type) {

        for (Command command : commandsById.values()) {
            if (type.isInstance(command)) {
                return type.cast(command);
            }
        }

        throw new IllegalArgumentException("Unregistered command " + type.getName());
    }

    /**
     * Returns the live menu bar built from the menu model.
     *
     * @return configuration menu bar
     */
    public JMenuBar getMenuBar() {
        return menuBar;
    }

    /**
     * Returns the live panel containing the configured toolbars.
     *
     * @return toolbar panel
     */
    public JPanel getToolbarPanel() {
        return toolbarPanel;
    }

    /**
     * Returns a warning recorded while loading user preferences, if any.
     *
     * @return warning message, or an empty optional when preferences loaded cleanly
     */
    public Optional<String> getLoadWarning() {
        return Optional.ofNullable(loadWarning);
    }

    void setLoadWarning(String message) {
        loadWarning = message;
    }

    /**
     * Installs this configuration's keyboard accelerators on a window root pane.
     *
     * @param window root pane that should receive the bindings
     */
    public void installKeyBindings(JRootPane window) {
        keys.install(window);
    }

    /**
     * Removes this configuration's keyboard accelerators from a window root pane.
     *
     * @param window root pane from which to remove the bindings
     */
    public void uninstallKeyBindings(JRootPane window) {
        keys.uninstall(window);
    }

    /**
     * Finds the command assigned to a keyboard accelerator.
     *
     * @param stroke accelerator to search for
     * @return matching command, or {@code null} when unassigned
     */
    public Command findKeyBinding(KeyStroke stroke) {

        if (stroke == null) {
            return null;
        }

        for (Command command : commandsById.values()) {
            if (stroke.equals(command.getKeyBinding())) {
                return command;
            }
        }

        return null;
    }

    /**
     * Assigns an accelerator to a registered command, optionally clearing an existing assignment.
     *
     * @param command registered command to update
     * @param stroke accelerator to assign, or {@code null} to clear the assignment
     * @param reassign {@code true} to remove the same accelerator from another command
     * @throws IllegalArgumentException if the command is unregistered, the accelerator is invalid,
     *         or a conflict exists while {@code reassign} is {@code false}
     */
    public void setKeyBinding(Command command, KeyStroke stroke, boolean reassign) {

        requireCommand(command);

        if (stroke != null) {
            ConfigurationManager.parseKey(stroke.toString());
        }

        Command existing = findKeyBinding(stroke);

        if (existing != null && existing != command) {
            if (!reassign) {
                throw new IllegalArgumentException(
                        "Shortcut is assigned to " + existing.getName());
            }

            existing.setKeyBinding(null);
        }

        command.setKeyBinding(stroke);
    }

    /**
     * Restores a command's Java-defined presentation and shortcut defaults.
     *
     * @param command registered command to reset
     * @throws IllegalArgumentException if the command is unregistered or its default shortcut
     *         conflicts with another command
     */
    public void resetCommand(Command command) {

        requireCommand(command);

        Defaults state = defaults.get(command);
        Command conflict = findKeyBinding(state.key);

        if (conflict != null && conflict != command) {
            throw new IllegalArgumentException(
                    "Default shortcut is assigned to " + conflict.getName());
        }

        state.apply(command);
    }

    /** Restores every command's default keyboard accelerator. */
    public void resetKeyBindings() {

        for (Command command : commandsById.values()) {
            command.setKeyBinding(null);
        }

        for (Map.Entry<Command, Defaults> entry : defaults.entrySet()) {
            entry.getKey().setKeyBinding(entry.getValue().key);
        }
    }

    /**
     * Saves the current menu, toolbar, and command preferences to the configured user file.
     *
     * @throws IOException if the user configuration cannot be written
     * @throws IllegalStateException if no user configuration path was supplied
     * @throws IllegalArgumentException if the layout or shortcuts are invalid
     */
    public void saveUserLayout() throws IOException {

        if (userFile == null) {
            throw new IllegalStateException("No user configuration path supplied");
        }

        validateGroups(menus, toolbars);

        Set<KeyStroke> used = new HashSet<>();

        for (Command command : commandsById.values()) {
            KeyStroke stroke = command.getKeyBinding();

            if (stroke != null && !used.add(stroke)) {
                throw new IllegalArgumentException("Duplicate shortcut: " + stroke);
            }
        }

        ConfigurationManager.save(this, userFile);
        loadWarning = null;
    }

    /**
     * Changes live state; Save persists it. Closing the customizer is not Cancel.
     */
    /**
     * Restores the live command presentation and layouts to their application defaults.
     *
     * <p>This changes the current session; call {@link #saveUserLayout()} to persist the reset.
     */
    public void resetToDefaults() {

        changing = true;

        try {
            for (Map.Entry<Command, Defaults> entry : defaults.entrySet()) {
                entry.getValue().apply(entry.getKey());
            }

            commands.replaceWith(defaultCommands.copy());
            menus.replaceWith(defaultMenus.copy());
            toolbars.replaceWith(defaultToolbars.copy());
        } finally {
            changing = false;
        }

        rebuild();
    }

    void applyUserConfiguration(ConfigurationManager.Parsed parsed) {

        changing = true;

        try {
            ConfigurationManager.apply(parsed);
            commands.replaceWith(parsed.commands.getRoot());
            menus.replaceWith(parsed.menus.getRoot());
            toolbars.replaceWith(parsed.toolbars.getRoot());
        } finally {
            changing = false;
        }

        rebuild();
    }

    private void requireCommand(Command command) {

        if (!commandsById.containsValue(command)) {
            throw new IllegalArgumentException("Unregistered command");
        }
    }

    private void notifyCommandNodes(ResourceModel model, Command command) {

        Enumeration<?> nodes = model.getRoot().depthFirstEnumeration();

        while (nodes.hasMoreElements()) {
            ResourceNode node = (ResourceNode) nodes.nextElement();

            if (node.getCommand() == command) {
                model.nodeChanged(node);
            }
        }
    }

    private void rebuild() {

        if (changing || closed) {
            return;
        }

        releasePresenters();

        for (SelectionGroup group : selectionGroups) {
            group.close();
        }

        selectionGroups.clear();

        for (Set<Command> group : validateGroups(menus, toolbars)) {
            selectionGroups.add(new SelectionGroup(group));
        }

        menuBar.removeAll();
        toolbarPanel.removeAll();

        ResourceNode menuRoot = menus.getRoot();

        for (int i = 0; i < menuRoot.getChildCount(); i++) {
            menuBar.add(createMenu(menuRoot.getChildAt(i)));
        }

        ResourceNode toolbarRoot = toolbars.getRoot();

        for (int i = 0; i < toolbarRoot.getChildCount(); i++) {
            ResourceNode node = toolbarRoot.getChildAt(i);
            JToolBar bar = new JToolBar(node.getName());
            bar.setName(node.getName());
            bar.setFloatable(Boolean.parseBoolean(node.attribute("floatable", "false")));
            appendToolbar(bar, node, Boolean.parseBoolean(node.attribute("showLabels", "false")));
            toolbarPanel.add(bar);
        }

        menuBar.revalidate();
        menuBar.repaint();
        toolbarPanel.revalidate();
        toolbarPanel.repaint();
    }

    private JMenu createMenu(ResourceNode node) {

        JMenu menu = new JMenu(node.getName());
        String mnemonic = node.attribute("mnemonic", "");

        if (!mnemonic.isEmpty()) {
            menu.setMnemonic(
                    mnemonic.length() == 1
                            ? mnemonic.charAt(0)
                            : Integer.parseInt(mnemonic));
        }

        appendMenu(menu, node);

        return menu;
    }

    private void appendMenu(JMenu menu, ResourceNode parent) {

        for (int i = 0; i < parent.getChildCount(); i++) {
            ResourceNode child = parent.getChildAt(i);

            switch (child.getKind()) {
                case COMMAND -> {
                    JMenuItem item = child.getCommand().getMenuPresenter();
                    presenters.add(item);
                    menu.add(item);
                }
                case SEPARATOR -> menu.addSeparator();
                case TOGGLE_GROUP -> appendMenu(menu, child);
                case GROUP -> menu.add(createMenu(child));
                default -> throw new IllegalArgumentException("Invalid menu placement");
            }
        }
    }

    private void appendToolbar(JToolBar bar, ResourceNode parent, boolean labels) {

        for (int i = 0; i < parent.getChildCount(); i++) {
            ResourceNode child = parent.getChildAt(i);

            switch (child.getKind()) {
                case COMMAND -> {
                    JComponent component = child.getCommand().getToolbarPresenter(labels);
                    track(component);
                    bar.add(component);
                }
                case SEPARATOR -> bar.addSeparator();
                case TOGGLE_GROUP -> appendToolbar(bar, child, labels);
                case DROPDOWN -> bar.add(createDropdown(child, labels));
                default -> throw new IllegalArgumentException("Invalid toolbar placement");
            }
        }
    }

    private JComponent createDropdown(ResourceNode node, boolean labels) {

        JPanel panel = new JPanel(new BorderLayout());
        JButton main = new JButton();
        main.setFocusable(false);
        presenters.add(main);

        JPopupMenu choices = new JPopupMenu();

        for (int i = 0; i < node.getChildCount(); i++) {
            ResourceNode child = node.getChildAt(i);
            Command command = child.getCommand();

            if (i == 0 || Boolean.parseBoolean(child.attribute("default", "false"))) {
                main.setAction(command);
            }

            JMenuItem item = command.getMenuPresenter();
            presenters.add(item);
            item.addActionListener(event -> {
                // Keep the selected choice in the layout model so saving preserves it.
                for (int j = 0; j < node.getChildCount(); j++) {
                    ResourceNode choice = node.getChildAt(j);
                    boolean selected = choice == child;
                    choice.getAttributes().put("default", Boolean.toString(selected));
                }

                main.setAction(command);
                main.setHideActionText(!labels && command.getIcon() != null);
            });
            choices.add(item);
        }

        main.setEnabled(main.getAction() != null && main.getAction().isEnabled());
        main.setHideActionText(!labels && main.getIcon() != null);

        JButton arrow = new JButton("▾");
        arrow.setToolTipText(node.getName());
        arrow.setFocusable(false);
        arrow.addActionListener(event -> choices.show(arrow, 0, arrow.getHeight()));

        panel.add(main, BorderLayout.CENTER);
        panel.add(arrow, BorderLayout.EAST);

        return panel;
    }

    private void track(Component component) {

        if (component instanceof AbstractButton button) {
            presenters.add(button);
        }

        if (component instanceof Container parent) {
            for (Component child : parent.getComponents()) {
                track(child);
            }
        }
    }

    private void releasePresenters() {

        // Detach old actions before rebuilding so commands do not retain obsolete components.
        for (AbstractButton presenter : presenters) {
            presenter.setAction(null);
        }

        presenters.clear();
    }

    static List<Set<Command>> validateGroups(ResourceModel... models) {

        List<Set<Command>> groups = new ArrayList<>();

        for (ResourceModel model : models) {
            Enumeration<?> nodes = model.getRoot().depthFirstEnumeration();

            while (nodes.hasMoreElements()) {
                ResourceNode node = (ResourceNode) nodes.nextElement();

                if (node.getKind() != Kind.TOGGLE_GROUP) {
                    continue;
                }

                Set<Command> members = new LinkedHashSet<>();

                for (int i = 0; i < node.getChildCount(); i++) {
                    Command command = node.getChildAt(i).getCommand();

                    if (!(command instanceof BooleanCommand) || !members.add(command)) {
                        throw new IllegalArgumentException(
                                "Toggle groups need distinct boolean commands");
                    }
                }

                for (Set<Command> previous : groups) {
                    boolean overlaps = !Collections.disjoint(previous, members);
                    boolean sameGroup = previous.equals(members);

                    if (overlaps && !sameGroup) {
                        throw new IllegalArgumentException(
                                "A command cannot belong to different exclusive groups");
                    }
                }

                if (!members.isEmpty() && !groups.contains(members)) {
                    groups.add(members);
                }
            }
        }

        return groups;
    }

    private static final class SelectionGroup implements AutoCloseable {

        private final List<BooleanCommand> commands = new ArrayList<>();
        private boolean updating;

        private final PropertyChangeListener listener = event -> {
            boolean selectionChanged = Action.SELECTED_KEY.equals(event.getPropertyName());
            boolean selected = Boolean.TRUE.equals(event.getNewValue());

            if (selectionChanged && selected) {
                select((BooleanCommand) event.getSource());
            }
        };

        SelectionGroup(Set<Command> members) {

            for (Command member : members) {
                commands.add((BooleanCommand) member);
            }

            for (BooleanCommand command : commands) {
                command.addPropertyChangeListener(listener);
            }

            for (BooleanCommand command : commands) {
                if (command.isSelected()) {
                    select(command);
                    break;
                }
            }
        }

        private void select(BooleanCommand selected) {

            if (updating) {
                return;
            }

            updating = true;

            try {
                // Clearing peers fires selection events; the guard prevents recursive updates.
                for (BooleanCommand command : commands) {
                    if (command != selected) {
                        command.setSelected(false);
                    }
                }
            } finally {
                updating = false;
            }
        }

        @Override
        public void close() {

            for (BooleanCommand command : commands) {
                command.removePropertyChangeListener(listener);
            }
        }
    }

    /** Releases presenter and key-binding resources owned by this configuration. */
    @Override
    public void close() {

        closed = true;
        keys.close();

        for (SelectionGroup group : selectionGroups) {
            group.close();
        }

        selectionGroups.clear();
        menus.removeTreeModelListener(layoutListener);
        toolbars.removeTreeModelListener(layoutListener);

        for (Command command : commandsById.values()) {
            command.removePropertyChangeListener(commandListener);
        }

        releasePresenters();
    }
}
