package dev.codecain.swing.commands.command;

import javax.swing.*;

/**
 * An command supplies a fresh component for each placement.
 */
public interface Presenter {

    interface Menu {
        JMenuItem getMenuPresenter();
    }

    interface Popup {
        JMenuItem getPopupPresenter();
    }

    interface Toolbar {
        JComponent getToolbarPresenter(boolean showLabel);
    }

    interface Panel {
        AbstractButton getButtonPresenter();
        JCheckBox getCheckBoxPresenter();
        JPanel getPanelPresenter();
    }
}
