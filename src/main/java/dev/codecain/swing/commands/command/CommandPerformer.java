package dev.codecain.swing.commands.command;

import java.awt.event.ActionEvent;

/**
 * Host-provided behavior for a {@link CallbackCommand} or {@link BooleanCallbackCommand}.
 */
@FunctionalInterface
public interface CommandPerformer {

    /**
     * Performs the host application behavior for a command event.
     *
     * @param command command that received the event
     * @param event Swing event delivered by the command presenter
     */
    void performCommand(Command command, ActionEvent event);
}