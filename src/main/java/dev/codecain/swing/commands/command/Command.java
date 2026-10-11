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

/** A shared command instance for each command class. */
public abstract class Command extends AbstractAction implements Presenter.Menu, Presenter.Popup,
        Presenter.Toolbar, Presenter.Panel {

    public static final String ICON_RESOURCE = "iconResource";

    private static final Map<Class<? extends Command>, Command> INSTANCES =
            new ConcurrentHashMap<>();

    private boolean customizable = true;

    public static <T extends Command> T get(Class<T> type) {

        Command command = INSTANCES.computeIfAbsent(type, Command::create);
        return type.cast(command);
    }

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

    protected void initialize() {

    }

    public String getName() {

        return (String) getValue(NAME);
    }

    public void setName(String name) {

        putValue(NAME, name);
    }

    public String getToolTip() {

        return (String) getValue(SHORT_DESCRIPTION);
    }

    public void setToolTip(String text) {

        putValue(SHORT_DESCRIPTION, text);
    }

    public KeyStroke getKeyBinding() {

        return (KeyStroke) getValue(ACCELERATOR_KEY);
    }

    /** Use Configuration.setKeyBinding to check for shortcut conflicts. */
    public void setKeyBinding(KeyStroke key) {

        putValue(ACCELERATOR_KEY, key);
    }

    public boolean isCustomizable() {

        return customizable;
    }

    public void setCustomizable(boolean value) {

        customizable = value;
    }

    public String getIconResource() {

        return (String) getValue(ICON_RESOURCE);
    }

    public Icon getIcon() {

        return (Icon) getValue(SMALL_ICON);
    }

    public void setIcon(Icon icon) {

        putValue(SMALL_ICON, icon);
        putValue(ICON_RESOURCE, null);
    }

    /** Loads a classpath resource or local image file. */
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

    public void setIconResource(String resource) {

        Icon icon = loadIcon(getClass(), resource);
        putValue(SMALL_ICON, icon);

        String storedResource = resource == null || resource.isBlank() ? null : resource;
        putValue(ICON_RESOURCE, storedResource);
    }

    @Override
    public JMenuItem getMenuPresenter() {

        return new JMenuItem(this);
    }

    @Override
    public JMenuItem getPopupPresenter() {

        return getMenuPresenter();
    }

    public JComponent getToolbarPresenter() {

        return getToolbarPresenter(false);
    }

    @Override
    public JComponent getToolbarPresenter(boolean showLabel) {

        AbstractButton button = getButtonPresenter();
        button.setHideActionText(!showLabel && getIcon() != null);
        button.setFocusable(false);

        return button;
    }

    @Override
    public AbstractButton getButtonPresenter() {

        return new JButton(this);
    }

    @Override
    public JCheckBox getCheckBoxPresenter() {

        return new JCheckBox(this);
    }

    @Override
    public JPanel getPanelPresenter() {

        JPanel panel = new JPanel();
        panel.add(getButtonPresenter());

        return panel;
    }

    @Override
    public abstract void actionPerformed(ActionEvent event);
}

