package dev.codecain.swing.commands.command;

import java.awt.event.ActionEvent;

@FunctionalInterface
public interface CommandPerformer {

    void performCommand(Command command, ActionEvent event);
}