/*
 * Copyright (c) 2026 Code Cain
 * https://codecain.dev
 * SPDX-License-Identifier: Apache-2.0
 */

package dev.codecain.swing.commands.shortcut;

import java.awt.BorderLayout;
import java.awt.event.InputEvent;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;

import javax.swing.JButton;
import javax.swing.JPanel;
import javax.swing.JTextField;
import javax.swing.KeyStroke;

/**
 * Captures one key combination and exposes it as a Swing KeyStroke.
 *
 * This component only handles input and display. Callers decide how to validate,
 * persist, or resolve conflicts for the captured shortcut.
 */
public final class KeyStrokeRecorder extends JPanel {

    private JTextField display;
    private JButton clearButton;

    private KeyStroke keyStroke;

    /** Creates an empty recorder ready to capture a keyboard shortcut. */
    public KeyStrokeRecorder() {
        super(new BorderLayout(4, 0));

        initializeUI();
    }

    private void initializeUI() {

        display = new JTextField();
        clearButton = new JButton("Clear");

        display.setEditable(false);
        display.setToolTipText("Focus this field and press the shortcut you want.");
        display.getAccessibleContext().setAccessibleName("Shortcut key combination");
        display.addKeyListener(new KeyAdapter() {
            @Override
            public void keyPressed(KeyEvent event) {

                capture(event);
            }
        });

        clearButton.addActionListener(event -> setKeyStroke(null));

        add(display, BorderLayout.CENTER);
        add(clearButton, BorderLayout.EAST);
    }

    /**
     * Returns the captured key combination, or {@code null} when the field is clear.
     *
     * @return recorded Swing keystroke, or {@code null} when no shortcut is set
     */
    public KeyStroke getKeyStroke() {

        return keyStroke;
    }

    /**
     * Sets the key combination shown by the recorder.
     *
     * @param keyStroke keystroke to display, or {@code null} to clear the field
     */
    public void setKeyStroke(KeyStroke keyStroke) {

        this.keyStroke = keyStroke;
        display.setText(keyStroke == null ? "" : format(keyStroke));
    }

    /**
     * Enables or disables the recorder and its child controls.
     *
     * @param enabled {@code true} to enable shortcut capture
     */
    @Override
    public void setEnabled(boolean enabled) {

        super.setEnabled(enabled);

        if (display != null) {
            display.setEnabled(enabled);
        }
        if (clearButton != null) {
            clearButton.setEnabled(enabled);
        }
    }

    private void capture(KeyEvent event) {

        event.consume();

        if (isModifierKey(event.getKeyCode())) {
            return;
        }

        setKeyStroke(KeyStroke.getKeyStrokeForEvent(event));
    }

    private static boolean isModifierKey(int keyCode) {

        return keyCode == KeyEvent.VK_CONTROL
                || keyCode == KeyEvent.VK_ALT
                || keyCode == KeyEvent.VK_SHIFT
                || keyCode == KeyEvent.VK_META
                || keyCode == KeyEvent.VK_ALT_GRAPH;
    }

    private static String format(KeyStroke keyStroke) {

        StringBuilder label = new StringBuilder();
        int modifiers = keyStroke.getModifiers();

        appendModifier(label, modifiers, InputEvent.CTRL_DOWN_MASK, "Ctrl");
        appendModifier(label, modifiers, InputEvent.ALT_DOWN_MASK, "Alt");
        appendModifier(label, modifiers, InputEvent.SHIFT_DOWN_MASK, "Shift");
        appendModifier(label, modifiers, InputEvent.META_DOWN_MASK, "Meta");

        if (label.length() > 0) {
            label.append('+');
        }
        label.append(KeyEvent.getKeyText(keyStroke.getKeyCode()));

        return label.toString();
    }

    private static void appendModifier(
            StringBuilder label,
            int modifiers,
            int modifierMask,
            String name) {

        if ((modifiers & modifierMask) == 0) {
            return;
        }

        if (label.length() > 0) {
            label.append('+');
        }
        label.append(name);
    }
}
