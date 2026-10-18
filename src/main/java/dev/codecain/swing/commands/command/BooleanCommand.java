/*
 * Copyright (c) 2026 Jon Kent
 * https://codecain.dev
 * SPDX-License-Identifier: Apache-2.0
 */

package dev.codecain.swing.commands.command;

import java.awt.event.ActionEvent;
import javax.swing.AbstractButton;
import javax.swing.JCheckBoxMenuItem;
import javax.swing.JMenuItem;
import javax.swing.JToggleButton;

/**
 * A command with selected state shared across its toggle-style Swing presenters.
 *
 * <p>Presenter events update the shared state before {@link #selectionPerformed(ActionEvent)} is
 * called. For other events, the selected state is toggled.
 */
public abstract class BooleanCommand extends CallableCommand {

    /** Creates a command with its selected state initially cleared. */
    protected BooleanCommand() {

        setSelected(false);
    }

    /**
     * Returns whether this command is currently selected.
     *
     * @return {@code true} when selected
     */
    public boolean isSelected() {

        return Boolean.TRUE.equals(getValue(SELECTED_KEY));
    }

    /**
     * Sets the selection state shared by this command's toggle presenters.
     *
     * @param selected {@code true} to select the command; {@code false} to clear it
     */
    public void setSelected(boolean selected) {

        putValue(SELECTED_KEY, selected);
    }

    /**
     * Creates a checkbox menu item bound to this command's selected state.
     *
     * @return a new checkbox menu presenter
     */
    @Override
    public JMenuItem getMenuPresenter() {

        return new JCheckBoxMenuItem(this);
    }

    /**
     * Creates a toggle button bound to this command's selected state.
     *
     * @return a new toggle button presenter
     */
    @Override
    public AbstractButton getButtonPresenter() {

        return new JToggleButton(this);
    }

    /**
     * Updates selection from a toggle presenter, or toggles it for other event sources,
     * then invokes {@link #selectionPerformed(ActionEvent)}.
     *
     * @param event action event delivered by the presenter
     */
    @Override
    public final void performCommand(ActionEvent event) {

        if (event.getSource() instanceof AbstractButton button
                && button.getAction() == this
                && (button instanceof JToggleButton || button instanceof JCheckBoxMenuItem)) {
            // Swing toggle presenters update SELECTED_KEY before delivering the event.
            setSelected(button.isSelected());
        } else {
            setSelected(!isSelected());
        }

        selectionPerformed(event);
    }

    /**
     * Hook called after the selected state has been updated.
     *
     * @param event action event that triggered the selection change
     */
    protected void selectionPerformed(ActionEvent event) {

    }
}
