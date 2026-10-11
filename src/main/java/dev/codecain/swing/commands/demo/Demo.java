package dev.codecain.swing.commands.demo;

import java.awt.*;
import java.awt.event.*;
import java.nio.file.Path;
import javax.swing.*;

import dev.codecain.swing.commands.command.*;
import dev.codecain.swing.commands.config.*;
import dev.codecain.swing.commands.customize.ConfigurationCustomizerDialog;

/**
 * Run with Gradle 'run'. User settings stay in the working directory.
 */
public final class Demo {

    /** Sample callback command used to demonstrate menu, toolbar, and shortcut invocation. */
    public static class Hello extends CallbackCommand {
        @Override
        protected void initialize() {
            setName("Say Hello");
            setToolTip("Works from the menu, toolbar, or Ctrl+H");
            setKeyBinding(KeyStroke.getKeyStroke("ctrl pressed H"));
        }
    }

    /** Sample callback command that demonstrates shared toggle state and shortcuts. */
    public static class Grid extends BooleanCallbackCommand {
        @Override
        protected void initialize() {
            setName("Show Grid");
            setSelected(true);
            setKeyBinding(KeyStroke.getKeyStroke("ctrl pressed G"));
        }
    }

    /** Sample boolean command used in the exclusive appearance selection. */
    public static class Light extends BooleanCommand {
        @Override
        protected void initialize() {
            setName("Light");
            setSelected(true);
        }
    }

    /** Sample boolean command used in the exclusive appearance selection. */
    public static class Dark extends BooleanCommand {
        @Override
        protected void initialize() {
            setName("Dark");
        }
    }

    /** Sample callback command that opens the runtime customizer. */
    public static class Customize extends CallbackCommand {
        @Override
        protected void initialize() {
            setName("Customize…");
        }
    }

    /**
     * Starts the sample application.
     *
     * @param args unused command-line arguments
     */
    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            try {
                Configuration config = ConfigurationManager.getDefault().install(Demo.class, "/demo.json", Path.of("demo.user.json"));
                JFrame frame = new JFrame("SwingCommands v2 — JSON → ResourceModel → JTree");
                frame.setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);
                JTextArea log = new JTextArea("The trees below are the actual configuration models.\nTry Ctrl+H, Ctrl+G, and Customize.\n");
                log.setEditable(false);
                config.getCommand(Hello.class).setCommandPerformer((command, event) -> log.append("Hello from " + event.getSource().getClass().getSimpleName() + "\n"));
                config.getCommand(Grid.class).setCommandPerformer((command, event) -> log.append("Grid: " + ((Grid) command).isSelected() + "\n"));
                config.getCommand(Customize.class).setCommandPerformer((command, event) -> new ConfigurationCustomizerDialog(frame, config).setVisible(true));
                frame.setJMenuBar(config.getMenuBar());
                frame.add(config.getToolbarPanel(), BorderLayout.NORTH);
                JTabbedPane trees = new JTabbedPane();
                trees.addTab("Commands", new JScrollPane(new JTree(config.getCommandModel())));
                trees.addTab("Menus", new JScrollPane(new JTree(config.getMenuModel())));
                trees.addTab("Toolbars", new JScrollPane(new JTree(config.getToolbarModel())));
                JSplitPane split = new JSplitPane(JSplitPane.VERTICAL_SPLIT, trees, new JScrollPane(log));
                split.setResizeWeight(0.65);
                frame.add(split);
                config.installKeyBindings(frame.getRootPane());
                config.getLoadWarning().ifPresent(message -> log.append(message + "\n"));
                frame.addWindowListener(new WindowAdapter() {
                    @Override
                    public void windowClosed(WindowEvent event) {
                        config.close();
                    }
                });
                frame.setSize(900, 650);
                frame.setLocationRelativeTo(null);
                frame.setVisible(true);
            } catch (Exception ex) {
                JOptionPane.showMessageDialog(null, ex.toString(), "Startup Error", JOptionPane.ERROR_MESSAGE);
            }
        });
    }
}
