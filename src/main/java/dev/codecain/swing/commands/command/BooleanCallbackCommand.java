/*
 * Copyright (c) 2026 Code Cain
 * https://codecain.dev
 * SPDX-License-Identifier: Apache-2.0
 */

package dev.codecain.swing.commands.command;

import java.awt.event.ActionEvent;

/**
 * A {@link BooleanCommand} that delegates selection changes to a host-provided performer.
 *
 * <p>If no performer is registered, selection still changes but no callback is made.
 */
public abstract class BooleanCallbackCommand extends BooleanCommand {

    private CommandPerformer performer;

    /**
     * Returns the host callback that receives this command's selection events.
     *
     * @return the registered performer, or {@code null} when none is registered
     */
    public CommandPerformer getCommandPerformer() {

        return performer;
    }

    /**
     * Registers the host callback for selection events.
     *
     * @param performer callback to register, or {@code null} to clear the callback
     */
    public void setCommandPerformer(CommandPerformer performer) {

        this.performer = performer;
    }

    /**
     * Removes the callback if the supplied instance is currently registered.
     *
     * @param performer callback instance to remove
     */
    public void removeCommandPerformer(CommandPerformer performer) {

        if (this.performer == performer) {
            setCommandPerformer(null);
        }
    }

    /**
     * Notifies the registered performer after the selected state has been updated.
     *
     * @param event event that changed or toggled the command selection
     */
    @Override
    protected void selectionPerformed(ActionEvent event) {

        if (performer != null) {
            performer.performCommand(this, event);
        }
    }
}
