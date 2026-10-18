/*
 * Copyright (c) 2026 Code Cain
 * https://codecain.dev
 * SPDX-License-Identifier: Apache-2.0
 */

package dev.codecain.swing.commands.command;

import javax.swing.*;

/**
 * Defines the Swing components a command can create for different UI placements.
 *
 * <p>Each presenter request should return a fresh component so the same command can appear in
 * more than one menu, toolbar, or panel.
 */
public interface Presenter {

    /** Supplies a menu item for a command. */
    interface Menu {
        /**
         * Creates a fresh menu item for the command.
         *
         * @return new menu item presenter
         */
        JMenuItem getMenuPresenter();
    }

    /** Supplies a menu item for a popup menu. */
    interface Popup {
        /**
         * Creates a fresh popup menu item for the command.
         *
         * @return new popup menu item presenter
         */
        JMenuItem getPopupPresenter();
    }

    /** Supplies a component for a toolbar, optionally showing the command label. */
    interface Toolbar {
        /**
         * Creates a fresh toolbar component.
         *
         * @param showLabel {@code true} to show the command label
         * @return new toolbar presenter
         */
        JComponent getToolbarPresenter(boolean showLabel);
    }

    /** Supplies button-based components for a command panel. */
    interface Panel {
        /**
         * Creates a fresh button presenter.
         *
         * @return new button presenter
         */
        AbstractButton getButtonPresenter();

        /**
         * Creates a fresh checkbox presenter.
         *
         * @return new checkbox presenter
         */
        JCheckBox getCheckBoxPresenter();

        /**
         * Creates a fresh panel presenter.
         *
         * @return new panel presenter
         */
        JPanel getPanelPresenter();
    }
}
