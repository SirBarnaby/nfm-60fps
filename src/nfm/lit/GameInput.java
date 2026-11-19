package nfm.lit;

import java.awt.event.FocusEvent;
import java.awt.event.FocusListener;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.awt.event.KeyListener;
import java.awt.event.MouseEvent;
import java.awt.event.MouseListener;
import java.awt.event.MouseMotionListener;
import java.awt.event.MouseWheelEvent;
import java.awt.event.MouseWheelListener;

/**
 * The game's keyboard, mouse and focus, through AWT's listeners. The game
 * was written for AWT 1.0's {@code keyDown(Event, int)} and friends,
 * deprecated since 1997, which AWT still converts each event into; this
 * listens to the events themselves instead, and hands the game the same
 * numbers AWT 1.0 did, so every key does what it always did. Each key also
 * comes with how long ago Windows saw it, for the profile's key-to-screen
 * time.
 */
final class GameInput implements KeyListener, MouseListener, MouseMotionListener, MouseWheelListener, FocusListener {

    /* AWT 1.0's numbers for the keys without a character (java.awt.Event.HOME and on) */
    private static final int[][] KEYS_WITHOUT_CHARACTER = {
        {KeyEvent.VK_HOME, 1000}, {KeyEvent.VK_END, 1001}, {KeyEvent.VK_PAGE_UP, 1002},
        {KeyEvent.VK_PAGE_DOWN, 1003}, {KeyEvent.VK_UP, 1004}, {KeyEvent.VK_DOWN, 1005},
        {KeyEvent.VK_LEFT, 1006}, {KeyEvent.VK_RIGHT, 1007},
        {KeyEvent.VK_F1, 1008}, {KeyEvent.VK_F2, 1009}, {KeyEvent.VK_F3, 1010}, {KeyEvent.VK_F4, 1011},
        {KeyEvent.VK_F5, 1012}, {KeyEvent.VK_F6, 1013}, {KeyEvent.VK_F7, 1014}, {KeyEvent.VK_F8, 1015},
        {KeyEvent.VK_F9, 1016}, {KeyEvent.VK_F10, 1017}, {KeyEvent.VK_F11, 1018}, {KeyEvent.VK_F12, 1019},
        {KeyEvent.VK_PRINTSCREEN, 1020}, {KeyEvent.VK_SCROLL_LOCK, 1021}, {KeyEvent.VK_CAPS_LOCK, 1022},
        {KeyEvent.VK_NUM_LOCK, 1023}, {KeyEvent.VK_PAUSE, 1024}, {KeyEvent.VK_INSERT, 1025},
    };

    /** AWT 1.0's number for F11, which (like Alt+Enter) switches fullscreen. */
    static final int F11 = 1018;

    private final GameSparker game;

    private GameInput(GameSparker game) {
        this.game = game;
    }

    /** Starts listening to the game's panel. */
    static void attach(GameSparker game) {
        GameInput input = new GameInput(game);
        game.addKeyListener(input);
        game.addMouseListener(input);
        game.addMouseMotionListener(input);
        game.addMouseWheelListener(input);
        game.addFocusListener(input);
        game.setFocusTraversalKeysEnabled(false); // Tab is the game's (the settings' tabs), not focus moving on
    }

    /**
     * The key as AWT 1.0 numbered it: 1000 and up for the keys in
     * {@link #KEYS_WITHOUT_CHARACTER}, otherwise the character it types (so
     * Shift+A is 65 and A is 97); -1 for Shift, Ctrl and Alt on their own,
     * which AWT 1.0 never reported.
     */
    static int legacyCode(KeyEvent e) {
        int code = e.getKeyCode();
        if (code == KeyEvent.VK_SHIFT || code == KeyEvent.VK_CONTROL || code == KeyEvent.VK_ALT) {
            return -1;
        }
        for (int[] key : KEYS_WITHOUT_CHARACTER) {
            if (key[0] == code) {
                return key[1];
            }
        }
        return e.getKeyChar();
    }

    /** How long ago Windows saw the event, in nanoseconds (its time has whole milliseconds). */
    private static long age(InputEvent e) {
        long millis = System.currentTimeMillis() - e.getWhen();
        return millis > 0L && millis < 1000L ? millis * 1_000_000L : 0L;
    }

    @Override
    public void keyPressed(KeyEvent e) {
        int keyCode = e.getKeyCode();
        if (SettingsScreen.captureKey(keyCode)) {
            e.consume();
            return; // a key being chosen on the settings screen
        }
        int code = legacyCode(e);
        if (code >= 0 || KeyBindings.action(keyCode) != KeyBindings.NONE) {
            game.pressKeyboard(keyCode, code, e.isAltDown(), age(e));
            if (code < 0) {
                e.consume(); // Alt on its own, given to an action, must not open the window's menu
            }
        }
    }

    @Override
    public void keyReleased(KeyEvent e) {
        game.releaseKeyboard(e.getKeyCode(), age(e));
        if (e.getKeyCode() == KeyEvent.VK_ALT && KeyBindings.action(KeyEvent.VK_ALT) != KeyBindings.NONE) {
            e.consume();
        }
    }

    @Override
    public void keyTyped(KeyEvent e) {
        // AWT 1.0 had no such event: a key is handled when it goes down
    }

    @Override
    public void mousePressed(MouseEvent e) {
        game.mousePressedAt(e.getX(), e.getY());
    }

    @Override
    public void mouseMoved(MouseEvent e) {
        game.mouseMovedTo(e.getX(), e.getY());
    }

    @Override
    public void mouseDragged(MouseEvent e) {
        // AWT 1.0's drag was never used: a held button does not move the pointer in the menus
    }

    @Override
    public void mouseWheelMoved(MouseWheelEvent e) {
        SettingsScreen.noteWheel(e.getWheelRotation()); // the wheel only scrolls the settings' list
    }

    @Override
    public void mouseReleased(MouseEvent e) {
        // the game acts on the press
    }

    @Override
    public void mouseClicked(MouseEvent e) {
        // the game acts on the press
    }

    @Override
    public void mouseEntered(MouseEvent e) {
        // nothing: AWT 1.0's MOUSE_ENTER was never used
    }

    @Override
    public void mouseExited(MouseEvent e) {
        // nothing: AWT 1.0's MOUSE_EXIT was never used
    }

    @Override
    public void focusGained(FocusEvent e) {
        game.focusGained();
    }

    @Override
    public void focusLost(FocusEvent e) {
        game.focusLost();
    }
}
