package dev.codecain.swing.commands.shortcut;

import dev.codecain.swing.commands.config.ConfigurationManager;
import java.awt.Component;
import java.awt.Container;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import javax.swing.JButton;
import javax.swing.JTextField;
import javax.swing.KeyStroke;
import javax.swing.SwingUtilities;

public final class KeyStrokeRecorderTest {

    private static int checks;

    public static void main(String[] args) throws Exception {

        SwingUtilities.invokeAndWait(KeyStrokeRecorderTest::exercise);
        System.out.println("Passed " + checks + " shortcut-recorder checks");
    }

    private static void exercise() {

        KeyStrokeRecorder recorder = new KeyStrokeRecorder();
        JTextField display = find(recorder, JTextField.class);
        JButton clear = find(recorder, JButton.class);
        check(display != null && clear != null, "Recorder provides a focus field and Clear button");

        KeyEvent controlE = keyPress(
                display,
                InputEvent.CTRL_DOWN_MASK,
                KeyEvent.VK_E,
                'E');
        fireKeyPress(display, controlE);

        KeyStroke stroke = KeyStroke.getKeyStroke("ctrl pressed E");
        check(controlE.isConsumed(), "Recorded key presses are consumed");
        check(stroke.equals(recorder.getKeyStroke()), "Ctrl+E becomes a Swing KeyStroke");
        check("Ctrl+E".equals(display.getText()), "Captured shortcuts use human-readable text");
        check(stroke.equals(ConfigurationManager.parseKey(stroke.toString())),
                "Captured shortcuts retain the existing configuration format");

        KeyEvent controlOnly = keyPress(
                display,
                InputEvent.CTRL_DOWN_MASK,
                KeyEvent.VK_CONTROL,
                KeyEvent.CHAR_UNDEFINED);
        fireKeyPress(display, controlOnly);
        check(controlOnly.isConsumed(), "Modifier-only presses do not escape the recorder");
        check(stroke.equals(recorder.getKeyStroke()),
                "Modifier-only presses leave the captured shortcut unchanged");

        KeyEvent altShiftF4 = keyPress(
                display,
                InputEvent.ALT_DOWN_MASK | InputEvent.SHIFT_DOWN_MASK,
                KeyEvent.VK_F4,
                KeyEvent.CHAR_UNDEFINED);
        fireKeyPress(display, altShiftF4);
        check("Alt+Shift+F4".equals(display.getText()),
                "Multiple modifiers and function keys display in order");

        clear.doClick();
        check(recorder.getKeyStroke() == null, "Clear removes the captured shortcut");
        check(display.getText().isEmpty(), "Clear empties the displayed shortcut");
    }

    private static KeyEvent keyPress(
            Component source,
            int modifiers,
            int keyCode,
            char keyChar) {

        return new KeyEvent(
                source,
                KeyEvent.KEY_PRESSED,
                System.currentTimeMillis(),
                modifiers,
                keyCode,
                keyChar);
    }

    private static void fireKeyPress(JTextField field, KeyEvent event) {

        for (var listener : field.getKeyListeners()) {
            listener.keyPressed(event);
        }
    }

    private static <T extends Component> T find(Container parent, Class<T> type) {

        for (Component child : parent.getComponents()) {
            if (type.isInstance(child)) {
                return type.cast(child);
            }

            if (child instanceof Container container) {
                T match = find(container, type);
                if (match != null) {
                    return match;
                }
            }
        }

        return null;
    }

    private static void check(boolean condition, String message) {

        checks++;
        if (!condition) {
            throw new AssertionError(message);
        }
    }
}

