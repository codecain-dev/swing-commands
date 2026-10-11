package dev.codecain.swing.commands.customize;

import java.awt.*;
import java.io.IOException;
import javax.swing.*;

import dev.codecain.swing.commands.config.*;

/**
 * Plain Swing editor. Changes are live; Save persists, Close keeps unsaved session changes.
 */
public final class ConfigurationCustomizerDialog extends JDialog {

    private final Configuration config;
    private final RuntimeConfigurationPreview preview;

    private JLabel status;

    /**
     * Creates a modal editor for the live configuration.
     *
     * @param owner parent window for this dialog
     * @param config configuration whose command and layout settings will be edited
     */
    public ConfigurationCustomizerDialog(Window owner, Configuration config) {

        super(owner, "Customize Commands, Menus and Toolbars", ModalityType.APPLICATION_MODAL);
        this.config = config;
        this.preview = new RuntimeConfigurationPreview(
                config.getMenuModel(),
                config.getToolbarModel());

        initializeUI(owner);
    }

    private void initializeUI(Window owner) {

        status = new JLabel("Changes apply immediately. Save keeps them for the next session.");

        JTabbedPane tabs = new JTabbedPane();
        tabs.addTab(
                "Commands & Shortcuts",
                new CommandCustomizationPanel(config, this, status::setText));
        tabs.addTab(
                "Menus",
                new LayoutCustomizationPanel(config, config.getMenuModel(), this));
        tabs.addTab(
                "Toolbars",
                new LayoutCustomizationPanel(config, config.getToolbarModel(), this));
        add(tabs);

        JPanel bottom = new JPanel(new BorderLayout());
        bottom.setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));
        bottom.add(status);

        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        addButton(buttons, "Reset All", () -> {
            int result = JOptionPane.showConfirmDialog(
                    this,
                    "Restore factory command properties, shortcuts and layouts? Save to keep this reset.",
                    "Restore Defaults",
                    JOptionPane.OK_CANCEL_OPTION);

            if (result == JOptionPane.OK_OPTION) {
                config.resetToDefaults();
                status.setText("Defaults restored for this session. Save to keep them.");
            }
        });
        addButton(buttons, "Save", () -> {
            try {
                config.saveUserLayout();
                status.setText("Configuration saved.");
            } catch (IOException | RuntimeException ex) {
                error(ex);
            }
        });
        addButton(buttons, "Preview", () -> preview.show(this));
        addButton(buttons, "Close", this::dispose);

        bottom.add(buttons, BorderLayout.SOUTH);
        add(bottom, BorderLayout.SOUTH);

        setDefaultCloseOperation(DISPOSE_ON_CLOSE);
        setSize(850, 540);
        setLocationRelativeTo(owner);
    }

    private static void addButton(JPanel panel, String text, Runnable command) {

        JButton button = new JButton(text);
        button.addActionListener(event -> command.run());
        panel.add(button);
    }

    private void error(Exception exception) {

        JOptionPane.showMessageDialog(
                this,
                exception.getMessage(),
                "Configuration",
                JOptionPane.ERROR_MESSAGE);
    }
}
