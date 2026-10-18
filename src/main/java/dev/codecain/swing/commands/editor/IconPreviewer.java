/*
 * Copyright (c) 2026 Jon Kent
 * https://codecain.dev
 * SPDX-License-Identifier: Apache-2.0
 */

package dev.codecain.swing.commands.editor;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.util.function.Supplier;
import javax.swing.BorderFactory;
import javax.swing.Icon;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTextField;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
import javax.swing.SwingWorker;
import javax.swing.Timer;
import javax.swing.UIManager;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;

/** Shows a live icon preview without loading images on the Swing event thread. */
public final class IconPreviewer extends JPanel {

    /** Resolves icon paths using the caller's active configuration context. */
    @FunctionalInterface
    public interface Resolver {

        /**
         * Resolves an icon path in the context of the edited configuration.
         *
         * @param path resource or file path entered by the user
         * @return resolved icon
         */
        Icon resolve(String path);
    }

    private static final int PREVIEW_SIZE = 28;
    private static final int DEBOUNCE_MILLISECONDS = 200;

    private final JTextField field;
    private JLabel preview;
    private final Supplier<Resolver> resolverSupplier;
    private Timer delay;

    private SwingWorker<Icon, Void> worker;
    private int revision;

    /**
     * Creates a live preview attached to an icon-path field.
     *
     * @param field text field whose value determines the preview
     * @param resolverSupplier supplies the resolver for the current editing context
     */
    public IconPreviewer(JTextField field, Supplier<Resolver> resolverSupplier) {

        super(new BorderLayout(6, 0));

        this.field = field;
        this.resolverSupplier = resolverSupplier;

        initializeUI();
        refresh();
    }

    private void initializeUI() {

        preview = new JLabel("—", SwingConstants.CENTER);
        configurePreview();

        add(field, BorderLayout.CENTER);
        add(preview, BorderLayout.EAST);

        delay = new Timer(DEBOUNCE_MILLISECONDS, event -> loadPreview());
        delay.setRepeats(false);
        field.getDocument().addDocumentListener(new PreviewDocumentListener());
    }

    private void configurePreview() {

        preview.setPreferredSize(new Dimension(36, 32));

        Color borderColor = UIManager.getColor("Separator.foreground");
        if (borderColor == null) {
            borderColor = Color.GRAY;
        }

        preview.setBorder(BorderFactory.createLineBorder(borderColor));
        preview.getAccessibleContext().setAccessibleName("Icon preview");
    }

    /** Reloads the preview using the current field value and resolver. */
    public void refresh() {

        revision++;

        if (worker != null) {
            worker.cancel(true);
        }

        preview.setIcon(null);
        preview.setText("…");
        preview.setToolTipText("Loading preview");
        delay.restart();
    }

    private void loadPreview() {

        int requestRevision = revision;
        String path = field.getText();

        // Resolve the current selection on the EDT; only image loading runs in the worker.
        Resolver resolver = resolverSupplier.get();
        worker = new SwingWorker<>() {
            @Override
            protected Icon doInBackground() {

                return resolver.resolve(path);
            }

            @Override
            protected void done() {

                // A slower earlier load must not replace the preview for a newer field value.
                if (requestRevision != revision || isCancelled()) {
                    return;
                }

                try {
                    showIcon(get());
                } catch (Exception exception) {
                    showError(exception);
                }
            }
        };

        worker.execute();
    }

    private void showIcon(Icon icon) {

        if (icon == null) {
            preview.setText("—");
            preview.setToolTipText("No icon preview");
            return;
        }

        int width = icon.getIconWidth();
        int height = icon.getIconHeight();

        preview.setIcon(fit(icon, width, height));
        preview.setText("");
        preview.setToolTipText(width + " × " + height + " pixels");
    }

    private void showError(Exception exception) {

        preview.setIcon(null);
        preview.setText("?");

        Throwable cause = exception.getCause();
        if (cause == null) {
            cause = exception;
        }

        preview.setToolTipText("Preview unavailable: " + cause.getMessage());
    }

    private static Icon fit(Icon icon, int width, int height) {

        if (width <= PREVIEW_SIZE && height <= PREVIEW_SIZE) {
            return icon;
        }

        double scale = Math.min(
                (double) PREVIEW_SIZE / width,
                (double) PREVIEW_SIZE / height);

        return new Icon() {
            @Override
            public int getIconWidth() {

                return Math.max(1, (int) Math.round(width * scale));
            }

            @Override
            public int getIconHeight() {

                return Math.max(1, (int) Math.round(height * scale));
            }

            @Override
            public void paintIcon(java.awt.Component component, Graphics graphics, int x, int y) {

                Graphics2D scaledGraphics = (Graphics2D) graphics.create();
                scaledGraphics.translate(x, y);
                scaledGraphics.scale(scale, scale);
                icon.paintIcon(component, scaledGraphics, 0, 0);
                scaledGraphics.dispose();
            }
        };
    }

    @Override
    public void addNotify() {

        super.addNotify();
        refresh();
    }

    @Override
    public void removeNotify() {

        delay.stop();
        revision++;

        if (worker != null) {
            worker.cancel(true);
        }

        super.removeNotify();
    }

    private final class PreviewDocumentListener implements DocumentListener {

        @Override
        public void insertUpdate(DocumentEvent event) {

            refresh();
        }

        @Override
        public void removeUpdate(DocumentEvent event) {

            refresh();
        }

        @Override
        public void changedUpdate(DocumentEvent event) {

            refresh();
        }
    }
}
