/*
 * Copyright (c) 2026 Jon Kent
 * https://codecain.dev
 * SPDX-License-Identifier: Apache-2.0
 */

package dev.codecain.swing.commands.command;

import java.awt.event.ActionEvent;
import java.lang.reflect.Constructor;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import javax.swing.AbstractAction;
import javax.swing.AbstractButton;
import javax.swing.Icon;
import javax.swing.ImageIcon;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComponent;
import javax.swing.JMenuItem;
import javax.swing.JPanel;
import javax.swing.KeyStroke;

/**
 * Base Swing action for application commands.
 *
 * <p>Each concrete command class has one shared instance, retrieved with {@link #get(Class)}.
 * The class must provide a no-argument constructor. {@link #initialize()} runs once when the
 * shared instance is created.
 *
 * <p>Commands can provide fresh Swing components for menus, popups, toolbars, and panels.
 */
public abstract class Command extends AbstractAction implements Presenter.Menu, Presenter.Popup,
        Presenter.Toolbar, Presenter.Panel {

    /** Action property key that stores the command's icon resource location. */
    public static final String ICON_RESOURCE = "iconResource";

    private static final Map<Class<? extends Command>, Command> INSTANCES =
            new ConcurrentHashMap<>();

    private boolean customizable = true;

    /**
     * Returns the shared instance of the requested command class, creating it on first use.
     *
     * @param <T> concrete command type
     * @param type command class to retrieve
     * @return the shared command instance
     * @throws IllegalArgumentException if the command cannot be constructed
     */
    public static <T extends Command> T get(Class<T> type) {

        Command command = INSTANCES.computeIfAbsent(type, Command::create);
        return type.cast(command);
    }

    /**
     * Reflectively constructs a command, initializes it, and supplies its default name.
     *
     * @param type concrete command class to instantiate
     * @return the initialized command instance
     * @throws IllegalArgumentException if construction fails or the constructor is inaccessible
     */
    private static Command create(Class<? extends Command> type) {

        try {
            Constructor<? extends Command> constructor = type.getDeclaredConstructor();

            if (!constructor.trySetAccessible()) {
                throw new IllegalArgumentException(
                        "Command constructor is inaccessible: " + type.getName());
            }

            Command command = constructor.newInstance();
            command.initialize();

            if (command.getName() == null) {
                command.setName(type.getSimpleName());
            }

            return command;
        } catch (ReflectiveOperationException ex) {
            throw new IllegalArgumentException("Cannot construct command " + type.getName(), ex);
        }
    }

    /**
     * Performs one-time setup after the shared command instance is constructed.
     *
     * <p>Override this method to initialize command-specific state.
     */
    protected void initialize() {

    }

    /**
     * Returns the command's display name.
     *
     * @return command name, or {@code null} when unset
     */
    public String getName() {

        return (String) getValue(NAME);
    }

    /**
     * Sets the command's display name.
     *
     * @param name display name shown by command presenters
     */
    public void setName(String name) {

        putValue(NAME, name);
    }

    /**
     * Returns the command's short description used as its tooltip.
     *
     * @return tooltip text, or {@code null} when unset
     */
    public String getToolTip() {

        return (String) getValue(SHORT_DESCRIPTION);
    }

    /**
     * Sets the short description used as the command tooltip.
     *
     * @param text tooltip text
     */
    public void setToolTip(String text) {

        putValue(SHORT_DESCRIPTION, text);
    }

    /**
     * Returns the keyboard accelerator configured for this command.
     *
     * @return command accelerator, or {@code null} when unset
     */
    public KeyStroke getKeyBinding() {

        return (KeyStroke) getValue(ACCELERATOR_KEY);
    }

    /**
     * Sets this command's keyboard accelerator.
     *
     * <p>Use {@code Configuration.setKeyBinding} when assigning shortcuts so conflicts can be checked.
     *
     * @param key accelerator to assign, or {@code null} to clear it
     */
    public void setKeyBinding(KeyStroke key) {

        putValue(ACCELERATOR_KEY, key);
    }

    /**
     * Returns whether users may customize this command's properties.
     *
     * @return {@code true} when customization is allowed
     */
    public boolean isCustomizable() {

        return customizable;
    }

    /**
     * Sets whether users may customize this command's properties.
     *
     * @param value {@code true} to allow customization
     */
    public void setCustomizable(boolean value) {

        customizable = value;
    }

    /**
     * Returns the resource path or file path used to load the command icon.
     *
     * @return icon location, or {@code null} when no resource is configured
     */
    public String getIconResource() {

        return (String) getValue(ICON_RESOURCE);
    }

    /**
     * Returns the icon currently assigned to this command.
     *
     * @return command icon, or {@code null} when none is assigned
     */
    public Icon getIcon() {

        return (Icon) getValue(SMALL_ICON);
    }

    /**
     * Sets the command icon and clears any previously stored icon location.
     *
     * @param icon icon to assign, or {@code null} to clear the icon
     */
    public void setIcon(Icon icon) {

        putValue(SMALL_ICON, icon);
        putValue(ICON_RESOURCE, null);
    }

    /**
     * Loads an icon from a classpath resource or local image file.
     *
     * @param anchor class whose package is used to resolve the resource path
     * @param resource classpath resource or file path to load
     * @return loaded icon, or {@code null} when the path is {@code null} or blank
     * @throws IllegalArgumentException if the file is missing or the image is invalid
     */
    public static Icon loadIcon(Class<?> anchor, String resource) {

        if (resource == null || resource.isBlank()) {
            return null;
        }

        URL url = anchor.getResource(resource);
        ImageIcon icon;

        if (url != null) {
            icon = new ImageIcon(url);
        } else {
            Path path = Path.of(resource);

            if (!Files.isRegularFile(path)) {
                throw new IllegalArgumentException("Icon not found: " + resource);
            }

            icon = new ImageIcon(path.toAbsolutePath().toString());
        }

        if (icon.getIconWidth() < 0) {
            throw new IllegalArgumentException("Invalid image: " + resource);
        }

        return icon;
    }

    /**
     * Loads and assigns an icon from a classpath resource or local image file.
     *
     * @param resource classpath resource or file path, or {@code null} to clear the icon
     * @throws IllegalArgumentException if the specified image cannot be loaded
     */
    public void setIconResource(String resource) {

        Icon icon = loadIcon(getClass(), resource);
        putValue(SMALL_ICON, icon);

        String storedResource = resource == null || resource.isBlank() ? null : resource;
        putValue(ICON_RESOURCE, storedResource);
    }

    /**
     * Creates a menu item bound to this command.
     *
     * @return a new menu item presenter
     */
    @Override
    public JMenuItem getMenuPresenter() {

        return new JMenuItem(this);
    }

    /**
     * Creates a popup menu item bound to this command.
     *
     * @return a new popup menu item presenter
     */
    @Override
    public JMenuItem getPopupPresenter() {

        return getMenuPresenter();
    }

    /**
     * Creates a toolbar presenter with its command label hidden when an icon is available.
     *
     * @return a new toolbar presenter
     */
    public JComponent getToolbarPresenter() {

        return getToolbarPresenter(false);
    }

    /**
     * Creates a toolbar presenter with optional command text.
     *
     * @param showLabel {@code true} to show the command label beside its icon
     * @return a new toolbar presenter
     */
    @Override
    public JComponent getToolbarPresenter(boolean showLabel) {

        AbstractButton button = getButtonPresenter();
        button.setHideActionText(!showLabel && getIcon() != null);
        button.setFocusable(false);

        return button;
    }

    /**
     * Creates a button bound to this command.
     *
     * @return a new button presenter
     */
    @Override
    public AbstractButton getButtonPresenter() {

        return new JButton(this);
    }

    /**
     * Creates a checkbox bound to this command.
     *
     * @return a new checkbox presenter
     */
    @Override
    public JCheckBox getCheckBoxPresenter() {

        return new JCheckBox(this);
    }

    /**
     * Creates a panel containing a button presenter for this command.
     *
     * @return a new panel presenter
     */
    @Override
    public JPanel getPanelPresenter() {

        JPanel panel = new JPanel();
        panel.add(getButtonPresenter());

        return panel;
    }

    /**
     * Handles an action event delivered by a Swing presenter.
     *
     * @param event Swing action event received by this command
     */
    @Override
    public abstract void actionPerformed(ActionEvent event);
}

