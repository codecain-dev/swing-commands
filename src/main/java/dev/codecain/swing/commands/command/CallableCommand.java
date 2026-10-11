package dev.codecain.swing.commands.command;

import java.awt.event.ActionEvent;

/** A command whose behavior is implemented directly by its subclass. */
public abstract class CallableCommand extends Command {

    @Override
    public final void actionPerformed(ActionEvent event) {

        if (isEnabled()) {
            performCommand(event);
        }
    }

    public abstract void performCommand(ActionEvent event);
}