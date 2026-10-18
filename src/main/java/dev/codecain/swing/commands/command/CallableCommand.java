/*
 * Copyright (c) 2026 Code Cain
 * https://codecain.dev
 * SPDX-License-Identifier: Apache-2.0
 */

package dev.codecain.swing.commands.command;

import java.awt.event.ActionEvent;

/**
 * Base for commands whose behavior is implemented by the command class.
 *
 * <p>Enabled events are forwarded to {@link #performCommand(ActionEvent)}. Events are ignored
 * while the command is disabled.
 */
public abstract class CallableCommand extends Command {

    /**
     * Forwards the event to {@link #performCommand(ActionEvent)} when this command is enabled.
     *
     * @param event Swing action event received by this command
     */
    @Override
    public final void actionPerformed(ActionEvent event) {

        if (isEnabled()) {
            performCommand(event);
        }
    }

    /**
     * Performs this command's application behavior.
     *
     * @param event Swing action event that triggered the command
     */
    public abstract void performCommand(ActionEvent event);
}
