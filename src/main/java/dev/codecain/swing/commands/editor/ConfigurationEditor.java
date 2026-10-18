/*
 * Copyright (c) 2026 Jon Kent
 * https://codecain.dev
 * SPDX-License-Identifier: Apache-2.0
 */

package dev.codecain.swing.commands.editor;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.*;

import java.awt.*;
import java.awt.event.*;
import java.nio.file.*;
import java.util.*;
import java.util.List;
import javax.swing.*;
import javax.swing.table.AbstractTableModel;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.event.*;
import javax.swing.filechooser.FileNameExtensionFilter;
import javax.swing.tree.*;


/**
 * Standalone application-default editor. Application classes are never loaded or executed.
 */
public final class ConfigurationEditor extends JPanel {

    private ConfigurationDocument document = new ConfigurationDocument();

    private JTextField applicationName;
    private CommandTableModel commandTableModel;
    private JTable commandTable;
    private LayoutEditor menuEditor;
    private LayoutEditor toolbarEditor;
    private ConfigurationPreview preview;
    private JLabel status;

    private boolean refreshing;

    /** Creates an editor for application-default configuration files. */
    public ConfigurationEditor() {
        initializeUI();
        refresh();
    }

    /**
     * Constructs and wires the Swing UI.
     *
     * Keeping component construction here makes the editor lifecycle explicit and
     * keeps field declarations focused on state rather than UI initialization.
     */
    private void initializeUI() {
        setLayout(new BorderLayout(8, 8));
        setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));

        applicationName = new JTextField();
        commandTableModel = new CommandTableModel();
        commandTable = createCommandTable();
        preview = new ConfigurationPreview(() -> document);
        status = new JLabel("Create a configuration or open an application's defaults.");
        menuEditor = new LayoutEditor(
                LayoutEditor.Type.MENUS, () -> document, this::refresh, status::setText);
        toolbarEditor = new LayoutEditor(
                LayoutEditor.Type.TOOLBARS, () -> document, this::refresh, status::setText);

        JPanel header = new JPanel(new BorderLayout(8, 8));
        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 0));

        button(buttons, "New", () -> {
            if (canDiscard()) {
                document = new ConfigurationDocument();
                refresh();
            }
        });
        button(buttons, "Open…", this::open);
        button(buttons, "Save", () -> save(false));
        button(buttons, "Save As…", () -> save(true));
        button(buttons, "Validate", () -> attempt(() -> {
            document.validate();
            status.setText(
                    "Valid configuration. Application class compatibility is checked when the host loads it.");
        }));

        header.add(buttons, BorderLayout.NORTH);

        JPanel title = new JPanel(new BorderLayout(8, 0));
        title.add(new JLabel("Application name"), BorderLayout.WEST);
        title.add(applicationName);
        header.add(title, BorderLayout.SOUTH);

        add(header, BorderLayout.NORTH);

        applicationName.getDocument().addDocumentListener(new DocumentListener() {
            @Override
            public void insertUpdate(DocumentEvent event) {
                applicationNameChanged();
            }

            @Override
            public void removeUpdate(DocumentEvent event) {
                applicationNameChanged();
            }

            @Override
            public void changedUpdate(DocumentEvent event) {
                applicationNameChanged();
            }
        });

        JTabbedPane tabs = new JTabbedPane();
        tabs.addTab("Registered Commands", commandPanel());
        tabs.addTab("Menus", menuEditor);
        tabs.addTab("Toolbars", toolbarEditor);
        tabs.addTab("Preview", preview);
        tabs.addChangeListener(event -> preview.refresh());
        add(tabs, BorderLayout.CENTER);

        status.setBorder(BorderFactory.createEmptyBorder(5, 0, 0, 0));
        add(status, BorderLayout.SOUTH);
    }

    private JTable createCommandTable() {
        return new JTable(commandTableModel) {
            @Override
            public String getToolTipText(MouseEvent event) {
                int row = rowAtPoint(event.getPoint());
                int column = columnAtPoint(event.getPoint());

                if (row < 0 || column < 0) {
                    return null;
                }

                Object value = getValueAt(row, column);
                return Objects.toString(value, null);
            }
        };
    }

    private void applicationNameChanged() {
        if (refreshing) {
            return;
        }

        document.root.put("name", applicationName.getText());
        markChanged();
    }

    private JPanel commandPanel() {

        JPanel panel = new JPanel(new BorderLayout(8, 8));
        JLabel note = new JLabel(
                "Registered command classes and the values each configuration overrides.");
        panel.add(note, BorderLayout.NORTH);

        configureCommandTable();
        JScrollPane commandScrollPane = new JScrollPane(commandTable);
        commandScrollPane.setColumnHeaderView(commandTable.getTableHeader());
        panel.add(commandScrollPane, BorderLayout.CENTER);

        JPanel controls = new JPanel(new FlowLayout(FlowLayout.LEFT));
        button(controls, "Register Command…", () -> editCommand(null));
        button(controls, "Edit…", () -> {
            String commandId = selectedCommandId();
            if (commandId != null) {
                editCommand(commandId);
            }
        });
        button(controls, "Copy…", () -> {
            String commandId = selectedCommandId();
            if (commandId != null) {
                editCommand(commandId, true);
            }
        });
        button(controls, "Remove", () -> attempt(() -> {
            String commandId = selectedCommandId();
            if (commandId != null) {
                document.removeCommand(commandId);
                refresh();
            }
        }));
        panel.add(controls, BorderLayout.SOUTH);

        return panel;
    }

    private void configureCommandTable() {

        commandTable.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        commandTable.setAutoCreateRowSorter(true);
        commandTable.setAutoResizeMode(JTable.AUTO_RESIZE_OFF);
        commandTable.setFillsViewportHeight(true);
        commandTable.setRowHeight(26);
        commandTable.setShowVerticalLines(false);
        commandTable.setIntercellSpacing(new Dimension(8, 0));
        commandTable.getTableHeader().setReorderingAllowed(false);
        commandTable.setDefaultRenderer(Object.class, new DefaultTableCellRenderer() {
            @Override
            public Component getTableCellRendererComponent(
                    JTable table,
                    Object value,
                    boolean selected,
                    boolean focused,
                    int row,
                    int column) {

                Component cell = super.getTableCellRendererComponent(
                        table,
                        value,
                        selected,
                        focused,
                        row,
                        column);

                if (selected) {
                    cell.setForeground(table.getSelectionForeground());
                } else if (isInheritedValue(value)) {
                    Color muted = UIManager.getColor("Label.disabledForeground");
                    cell.setForeground(muted == null ? table.getForeground() : muted);
                } else {
                    cell.setForeground(table.getForeground());
                }

                return cell;
            }

            private boolean isInheritedValue(Object value) {

                return "Inherit".equals(value)
                        || "None".equals(value)
                        || "—".equals(value);
            }
        });
        commandTable.setToolTipText("");
        commandTable.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent event) {

                if (event.getClickCount() == 2 && selectedCommandId() != null) {
                    editCommand(selectedCommandId());
                }
            }
        });

        int[] preferredWidths = {120, 210, 90, 110, 105, 120, 85, 85};
        for (int column = 0; column < preferredWidths.length; column++) {
            commandTable.getColumnModel().getColumn(column)
                    .setPreferredWidth(preferredWidths[column]);
        }
    }

    private String selectedCommandId() {

        int selectedRow = commandTable.getSelectedRow();
        if (selectedRow < 0) {
            return null;
        }

        int modelRow = commandTable.convertRowIndexToModel(selectedRow);
        return commandTableModel.commandIdAt(modelRow);
    }

    private void selectCommand(String commandId) {

        int modelRow = commandTableModel.rowOf(commandId);
        if (modelRow < 0) {
            return;
        }

        int viewRow = commandTable.convertRowIndexToView(modelRow);
        if (viewRow < 0) {
            return;
        }

        commandTable.setRowSelectionInterval(viewRow, viewRow);
        commandTable.scrollRectToVisible(commandTable.getCellRect(viewRow, 0, true));
    }

    private final class CommandTableModel extends AbstractTableModel {

        private final String[] columns = {
                "Stable ID",
                "Java class",
                "Category",
                "Label",
                "Shortcut",
                "Icon",
                "Selected",
                "Enabled"
        };

        private final List<String> commandIds = new ArrayList<>();

        @Override
        public int getRowCount() {

            return commandIds.size();
        }

        @Override
        public int getColumnCount() {

            return columns.length;
        }

        @Override
        public String getColumnName(int column) {

            return columns[column];
        }

        @Override
        public Object getValueAt(int row, int column) {

            String commandId = commandIds.get(row);
            JsonNode definition = document.commands().path(commandId);

            return switch (column) {
                case 0 -> commandId;
                case 1 -> definition.path("class").asText("");
                case 2 -> valueOrDash(definition, "category");
                case 3 -> overrideValue(definition, "label");
                case 4 -> shortcutValue(definition);
                case 5 -> iconValue(definition);
                case 6 -> booleanValue(definition, "selected");
                case 7 -> booleanValue(definition, "enabled");
                default -> throw new IndexOutOfBoundsException("Unknown column: " + column);
            };
        }

        private String valueOrDash(JsonNode definition, String key) {

            String value = definition.path(key).asText("");
            return value.isBlank() ? "—" : value;
        }

        private String overrideValue(JsonNode definition, String key) {

            if (!definition.has(key)) {
                return "Inherit";
            }

            String value = definition.path(key).asText("");
            return value.isBlank() ? "Inherit" : value;
        }

        private String shortcutValue(JsonNode definition) {

            if (!definition.has("shortcut")) {
                return "Inherit";
            }

            String shortcut = definition.path("shortcut").asText("");
            return shortcut.isBlank() ? "None" : shortcut;
        }

        private String iconValue(JsonNode definition) {

            if (!definition.has("icon")) {
                return "Inherit";
            }

            String icon = definition.path("icon").asText("");
            return icon.isBlank() ? "None" : icon;
        }

        private String booleanValue(JsonNode definition, String key) {

            if (!definition.has(key)) {
                return "Inherit";
            }

            return definition.path(key).asBoolean() ? "Yes" : "No";
        }

        private String commandIdAt(int row) {

            return commandIds.get(row);
        }

        private int rowOf(String commandId) {

            return commandIds.indexOf(commandId);
        }

        private void reload() {

            commandIds.clear();
            document.commands().fieldNames().forEachRemaining(commandIds::add);
            fireTableDataChanged();
        }
    }

    private void editCommand(String oldId) {
        editCommand(oldId, false);
    }

    private void editCommand(String oldId, boolean copy) {
        JsonNode value = oldId == null
                ? JsonNodeFactory.instance.objectNode()
                : document.commands().path(oldId);

        String initialId = copy ? copiedId(oldId) : Objects.toString(oldId, "");
        Path resourceRoot = document.path() == null ? null : document.path().getParent();

        CommandEditorDialog dialog = new CommandEditorDialog(
                this,
                value,
                initialId,
                copy,
                resourceRoot);

        while (true) {
            CommandEditorDialog.Result result = dialog.showDialog();
            if (result == null) {
                return;
            }

            try {
                document.putCommand(
                        copy ? null : oldId,
                        result.commandId(),
                        result.definition());

                refresh();
                selectCommand(result.commandId());
                return;
            } catch (Exception ex) {
                refresh();
                error(ex);
            }
        }
    }

    private String copiedId(String sourceId) {
        String base = sourceId + ".copy";
        String candidate = base;
        int suffix = 2;
        while (document.commands().has(candidate)) candidate = base + suffix++;
        return candidate;
    }

    private void refresh() {
        refreshing = true;
        try {
            applicationName.setText(document.root.path("name").asText("Application"));
            commandTableModel.reload();
            menuEditor.refresh();
            toolbarEditor.refresh();
            preview.refresh();
        } finally {
            refreshing = false;
        }
        markChanged();
    }

    private void markChanged() {

        status.setText(
                (document.dirty() ? "Unsaved changes — " : "")
                        + (document.path() == null
                                ? "New application configuration"
                                : document.path().toString()));
        Window window = SwingUtilities.getWindowAncestor(this);
        if (window instanceof JFrame frame) {
            frame.setTitle("Command Configuration Editor" + (document.dirty() ? " *" : ""));
        }
    }

    private void open() {

        if (!canDiscard()) {
            return;
        }
        JFileChooser chooser = chooser();
        if (chooser.showOpenDialog(this) == JFileChooser.APPROVE_OPTION) {
            attempt(() -> openFile(chooser.getSelectedFile().toPath()));
        }
    }

    void openFile(Path file) throws Exception {

        ConfigurationDocument next = ConfigurationDocument.open(file);
        document = next;
        refresh();
        if (document.repairedVersion()) {
            status.setText("Missing format version repaired. Save to write a valid application configuration.");
        }
    }

    private JFileChooser chooser() {

        JFileChooser chooser = new JFileChooser();
        chooser.setFileFilter(new FileNameExtensionFilter("Command configuration (*.json)", "json"));
        if (document.path() != null) {
            chooser.setSelectedFile(document.path().toFile());
        }
        return chooser;
    }

    private boolean save(boolean choose) {

        Path target = document.path();
        if (choose || target == null) {
            JFileChooser chooser = chooser();
            if (chooser.showSaveDialog(this) != JFileChooser.APPROVE_OPTION) {
                return false;
            }
            target = chooser.getSelectedFile().toPath();
            if (!target.getFileName().toString().endsWith(".json")) {
                target = target.resolveSibling(target.getFileName() + ".json");
            }
            if (Files.exists(target)
                    && JOptionPane.showConfirmDialog(
                            this,
                            "Replace " + target + "? A backup will be kept.",
                            "Save Configuration",
                            JOptionPane.YES_NO_OPTION) != JOptionPane.YES_OPTION) {
                return false;
            }
        }
        try {
            document.save(target);
            markChanged();
            status.setText("Saved " + target);
            return true;
        } catch (Exception ex) {
            error(ex);
            return false;
        }
    }

    private boolean canDiscard() {

        if (!document.dirty()) {
            return true;
        }
        int answer = JOptionPane.showConfirmDialog(this, "Save your changes before continuing?", "Unsaved Configuration", JOptionPane.YES_NO_CANCEL_OPTION);
        return answer == JOptionPane.NO_OPTION || (answer == JOptionPane.YES_OPTION && save(false));
    }

    @FunctionalInterface
    private interface Work {
        void run() throws Exception;
    }

    private void attempt(Work work) {
        try {
            work.run();
        } catch (Exception ex) {
            error(ex);
        }
    }

    private void error(Exception ex) {
        JTextArea text = new JTextArea(Objects.toString(ex.getMessage(), ex.toString()), 10, 65);
        text.setLineWrap(true);
        text.setWrapStyleWord(true);
        text.setEditable(false);
        JOptionPane.showMessageDialog(this, new JScrollPane(text), "Configuration needs attention", JOptionPane.ERROR_MESSAGE);
    }

    private static void button(JPanel panel, String label, Runnable work) {
        JButton button = new JButton(label);
        button.addActionListener(e -> work.run());
        panel.add(button);
    }

    /**
     * Opens the standalone configuration editor.
     *
     * @param args optional configuration file path to open
     */
    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            try {
                UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
            } catch (Exception ignored) {
            }
            ConfigurationEditor editor = new ConfigurationEditor();
            JFrame frame = new JFrame("Command Configuration Editor");
            frame.setContentPane(editor);
            frame.setDefaultCloseOperation(WindowConstants.DO_NOTHING_ON_CLOSE);
            frame.addWindowListener(new WindowAdapter() {
                public void windowClosing(WindowEvent e) {
                    if (editor.canDiscard()) {
                        frame.dispose();
                    }
                }
            });
            frame.setSize(1000, 700);
            frame.setMinimumSize(new Dimension(760, 540));
            frame.setLocationRelativeTo(null);
            frame.setVisible(true);
            if (args.length > 0) {
                editor.attempt(() -> editor.openFile(Path.of(args[0])));
            }
        });
    }
}







