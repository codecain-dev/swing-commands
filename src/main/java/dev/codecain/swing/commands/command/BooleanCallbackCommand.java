package dev.codecain.swing.commands.command;

import java.awt.event.ActionEvent;

/** A selected-state command whose behavior is supplied by its host application. */
public abstract class BooleanCallbackCommand extends BooleanCommand {

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
    protected void selectionPerformed(ActionEvent event) {

        if (performer != null) {
            performer.performCommand(this, event);
        }
    }
}