/*
 * Copyright (c) 2026 Code Cain
 * https://codecain.dev
 * SPDX-License-Identifier: Apache-2.0
 */

package dev.codecain.swing.commands.command;

import java.awt.event.ActionEvent;

/**
 * A command that delegates its behavior to a {@link CommandPerformer} supplied by the host.
 *
 * <p>If no performer is registered, the command has no callback behavior.
 */
public abstract class CallbackCommand extends CallableCommand {

    private CommandPerformer performer;

    /**
     * Returns the host callback registered for this command.
     *
     * @return the registered performer, or {@code null} when none is registered
     */
    public CommandPerformer getCommandPerformer() {

        return performer;
    }

    /**
     * Registers the host callback for this command.
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
     * Delegates the event to the registered performer, if present.
     *
     * @param event action event that triggered the command
     */
    @Override
    public void performCommand(ActionEvent event) {

        if (performer != null) {
            performer.performCommand(this, event);
        }
    }
}
