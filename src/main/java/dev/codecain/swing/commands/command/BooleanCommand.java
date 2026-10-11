package dev.codecain.swing.commands.command;

import java.awt.event.ActionEvent;
import javax.swing.AbstractButton;
import javax.swing.JCheckBoxMenuItem;
import javax.swing.JMenuItem;
import javax.swing.JToggleButton;

/** A command whose selected state is shared by its Swing presenters. */
public abstract class BooleanCommand extends CallableCommand {

    protected BooleanCommand() {

        setSelected(false);
    }

    public boolean isSelected() {

        return Boolean.TRUE.equals(getValue(SELECTED_KEY));
    }

    public void setSelected(boolean selected) {

        putValue(SELECTED_KEY, selected);
    }

    @Override
    public JMenuItem getMenuPresenter() {

        return new JCheckBoxMenuItem(this);
    }

    @Override
    public AbstractButton getButtonPresenter() {

        return new JToggleButton(this);
    }

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

    protected void selectionPerformed(ActionEvent event) {

    }
}