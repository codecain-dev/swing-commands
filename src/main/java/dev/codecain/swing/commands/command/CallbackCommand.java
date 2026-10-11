package dev.codecain.swing.commands.command;

import java.awt.event.ActionEvent;

/** A command whose behavior is supplied by its host application. */
public abstract class CallbackCommand extends CallableCommand {

    private CommandPerformer performer;

    public CommandPerformer getCommandPerformer() {

        return performer;
    }

    public void setCommandPerformer(CommandPerformer performer) {

        this.performer = performer;
    }

    public void removeCommandPerformer(CommandPerformer performer) {

        if (this.performer == performer) {
            setCommandPerformer(null);
        }
    }

    @Override
    public void performCommand(ActionEvent event) {

        if (performer != null) {
            performer.performCommand(this, event);
        }
    }
}