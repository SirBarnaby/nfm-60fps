package nfm.lit;

import java.awt.event.KeyEvent;
import java.util.Locale;

/**
 * Each split-screen player's keys: the nine driving actions of
 * {@link KeyBindings}, two keys each, for Player 1 and Player 2. Pause,
 * Music and Sounds stay the keys of the Controls tab, and Enter and
 * Escape pause as always.
 *
 * <p>Two players share one keyboard, so a key here is told apart by where
 * it is as well as what it is: the right Shift, Ctrl and Alt are keys of
 * their own, and so is every key of the number pad, Num Lock on or off. A
 * key's number is its {@link KeyEvent} code, with {@link #RIGHT} or
 * {@link #NUMPAD} added.
 *
 * <p>Changed on the game thread and read on the window's thread as keys go
 * down, so the table is replaced whole, never changed in place.
 */
public final class SplitKeys {

    public static final int PLAYERS = 2;
    /** The actions each player has, as {@link KeyBindings} numbers them. */
    static final int[] ACTIONS = {KeyBindings.ACCELERATE, KeyBindings.BRAKE, KeyBindings.STEER_LEFT,
        KeyBindings.STEER_RIGHT, KeyBindings.HANDBRAKE, KeyBindings.LOOK_BACK, KeyBindings.LOOK_BACK_OTHER,
        KeyBindings.CHANGE_VIEW, KeyBindings.ARROW};
    public static final int SLOTS = KeyBindings.SLOTS;
    /** Added to a key's code for the right one of Shift, Ctrl or Alt. */
    public static final int RIGHT = 1 << 16;
    /** Added to a key's code for a key of the number pad. */
    public static final int NUMPAD = 1 << 17;
    private static final int CODE = 0xFFFF;

    /** The game's own split keys, {@code [player][action][slot]}: WASD for Player 1, the arrows for Player 2. */
    private static final int[][][] DEFAULTS = {
        {{KeyEvent.VK_W}, {KeyEvent.VK_S}, {KeyEvent.VK_A}, {KeyEvent.VK_D}, {KeyEvent.VK_SPACE, KeyEvent.VK_SHIFT},
            {KeyEvent.VK_Q}, {KeyEvent.VK_E}, {KeyEvent.VK_C}, {KeyEvent.VK_F}},
        {{KeyEvent.VK_UP}, {KeyEvent.VK_DOWN}, {KeyEvent.VK_LEFT}, {KeyEvent.VK_RIGHT},
            {KeyEvent.VK_CONTROL | RIGHT, KeyEvent.VK_NUMPAD0 | NUMPAD},
            {KeyEvent.VK_SHIFT | RIGHT, KeyEvent.VK_NUMPAD1 | NUMPAD},
            {KeyEvent.VK_SLASH, KeyEvent.VK_NUMPAD3 | NUMPAD},
            {KeyEvent.VK_PERIOD, KeyEvent.VK_DECIMAL | NUMPAD},
            {KeyEvent.VK_COMMA, KeyEvent.VK_ADD | NUMPAD}},
    };

    /** Each player's keys, {@code [(player * ACTIONS.length + action) * SLOTS + slot]}, 0 for none. */
    private static volatile int[] keys = defaults();
    private static volatile boolean changed;

    private SplitKeys() {
    }

    /** How many numbers the keys are saved as. */
    static int count() {
        return PLAYERS * ACTIONS.length * SLOTS;
    }

    private static int at(int player, int action, int slot) {
        return (player * ACTIONS.length + action) * SLOTS + slot;
    }

    private static int[] defaults() {
        int[] k = new int[count()];
        for (int p = 0; p < PLAYERS; p++) {
            for (int a = 0; a < ACTIONS.length; a++) {
                for (int s = 0; s < DEFAULTS[p][a].length; s++) {
                    k[at(p, a, s)] = DEFAULTS[p][a][s];
                }
            }
        }
        return k;
    }

    /**
     * A key's number, from where it is on the keyboard: the right Shift,
     * Ctrl and Alt, and the number pad's keys, are told apart; the number
     * pad's keys are the same with Num Lock on or off.
     */
    static int id(KeyEvent e) {
        return id(e.getKeyCode(), e.getKeyLocation());
    }

    static int id(int code, int location) {
        if (location == KeyEvent.KEY_LOCATION_RIGHT) {
            return code | RIGHT;
        }
        if (location != KeyEvent.KEY_LOCATION_NUMPAD) {
            return code;
        }
        int pad = switch (code) {
            case KeyEvent.VK_INSERT -> KeyEvent.VK_NUMPAD0;
            case KeyEvent.VK_END -> KeyEvent.VK_NUMPAD1;
            case KeyEvent.VK_DOWN, KeyEvent.VK_KP_DOWN -> KeyEvent.VK_NUMPAD2;
            case KeyEvent.VK_PAGE_DOWN -> KeyEvent.VK_NUMPAD3;
            case KeyEvent.VK_LEFT, KeyEvent.VK_KP_LEFT -> KeyEvent.VK_NUMPAD4;
            case KeyEvent.VK_CLEAR, KeyEvent.VK_BEGIN -> KeyEvent.VK_NUMPAD5;
            case KeyEvent.VK_RIGHT, KeyEvent.VK_KP_RIGHT -> KeyEvent.VK_NUMPAD6;
            case KeyEvent.VK_HOME -> KeyEvent.VK_NUMPAD7;
            case KeyEvent.VK_UP, KeyEvent.VK_KP_UP -> KeyEvent.VK_NUMPAD8;
            case KeyEvent.VK_PAGE_UP -> KeyEvent.VK_NUMPAD9;
            case KeyEvent.VK_DELETE, KeyEvent.VK_SEPARATOR -> KeyEvent.VK_DECIMAL;
            default -> code;
        };
        return pad | NUMPAD;
    }

    /** The player and the action ({@code player << 8 | action}, as KeyBindings numbers it) a key does, or -1. */
    static int find(int id) {
        if (id == 0) {
            return -1;
        }
        int[] k = keys;
        for (int i = 0; i < k.length; i++) {
            if (k[i] == id) {
                int player = i / SLOTS / ACTIONS.length;
                return player << 8 | ACTIONS[i / SLOTS % ACTIONS.length];
            }
        }
        return -1;
    }

    /** A player's key for an action (of {@link #ACTIONS}, by place) in a slot, or 0. */
    public static int key(int player, int action, int slot) {
        return keys[at(player, action, slot)];
    }

    /** The place in {@link #ACTIONS} of a KeyBindings action, or -1. */
    static int indexOf(int bindingAction) {
        for (int a = 0; a < ACTIONS.length; a++) {
            if (ACTIONS[a] == bindingAction) {
                return a;
            }
        }
        return -1;
    }

    /** Escape, Enter and the other keys not the player's (see {@link KeyBindings#reserved}). */
    public static boolean reserved(int id) {
        return KeyBindings.reserved(id & CODE);
    }

    /**
     * Gives a key to a player's action, taking it from wherever it was in
     * either player's keys. 0 clears the slot.
     *
     * @return where the key was taken from, {@code player << 8 | action}
     *         (action by place in {@link #ACTIONS}), or -1
     */
    public static synchronized int bind(int player, int action, int slot, int id) {
        if (id != 0 && reserved(id)) {
            return -1;
        }
        int[] k = keys.clone();
        int from = -1;
        int here = at(player, action, slot);
        if (id != 0) {
            for (int i = 0; i < k.length; i++) {
                if (k[i] == id && i != here) {
                    k[i] = 0;
                    from = (i / SLOTS / ACTIONS.length) << 8 | i / SLOTS % ACTIONS.length;
                }
            }
        }
        k[here] = id;
        keys = k;
        changed = true;
        return from == (player << 8 | action) ? -1 : from;
    }

    /** Back to the game's own split keys. */
    public static synchronized void reset() {
        keys = defaults();
        changed = true;
    }

    public static boolean isDefault() {
        return java.util.Arrays.equals(keys, defaults());
    }

    /** True once after a change: the keys are to be saved. */
    static boolean takeChanged() {
        boolean was = changed;
        changed = false;
        return was;
    }

    /** The keys as saved: one number each, in the order of {@link #count}. */
    static int saved(int i) {
        return keys[i];
    }

    /**
     * The keys as saved (missing ones, below 0, keep the game's own); a key
     * on two actions, or one not the player's, puts back the game's own keys.
     */
    static synchronized void load(int[] saved) {
        int[] k = defaults();
        for (int i = 0; i < k.length && i < saved.length; i++) {
            if (saved[i] >= 0) {
                k[i] = saved[i];
            }
        }
        for (int i = 0; i < k.length; i++) {
            if (k[i] != 0 && reserved(k[i])) {
                k = defaults();
                break;
            }
            for (int j = i + 1; j < k.length; j++) {
                if (k[i] != 0 && k[i] == k[j]) {
                    k = defaults();
                    i = k.length;
                    break;
                }
            }
        }
        keys = k;
        changed = false;
    }

    /** A key's name for the screen: "W", "Right Ctrl", "Left Shift", "Num 0", or a dash for none. */
    public static String name(int id) {
        if (id == 0) {
            return "-";
        }
        int code = id & CODE;
        String base = code == KeyEvent.VK_SHIFT ? "Shift" : code == KeyEvent.VK_ALT ? "Alt" : KeyBindings.name(code);
        if ((id & RIGHT) != 0) {
            return "Right " + base;
        }
        if ((id & NUMPAD) != 0) {
            return base.startsWith("Num ") ? base : "Num " + base;
        }
        boolean modifier = code == KeyEvent.VK_SHIFT || code == KeyEvent.VK_CONTROL || code == KeyEvent.VK_ALT;
        return modifier ? "Left " + base : base;
    }

    /** The key a console word names, as {@link #name} gives it ("rctrl", "Right Shift", "num0"); -1 if none. */
    public static int parse(String word) {
        String w = word.trim().toLowerCase(Locale.ROOT).replace(" ", "");
        if (w.equals("-") || w.equals("none")) {
            return 0;
        }
        // rshift, lctrl and the like are short for Right Shift, Left Ctrl
        if (w.matches("[rl](shift|ctrl|alt)")) {
            w = (w.charAt(0) == 'r' ? "right" : "left") + w.substring(1);
        }
        for (int code = 1; code < 0x400; code++) {
            for (int bits : new int[] {0, RIGHT, NUMPAD}) {
                int id = id(code, bits == RIGHT ? KeyEvent.KEY_LOCATION_RIGHT
                        : bits == NUMPAD ? KeyEvent.KEY_LOCATION_NUMPAD : KeyEvent.KEY_LOCATION_STANDARD);
                String n = name(id);
                if (!n.contains("Key ") && n.toLowerCase(Locale.ROOT).replace(" ", "").equals(w)) {
                    return id;
                }
            }
        }
        int plain = KeyBindings.parse(word);
        return plain > 0 ? plain : -1;
    }

    /** The action a console word names (see {@link KeyBindings#parseAction}), by place in ACTIONS; -1 if none. */
    public static int parseAction(String word) {
        return indexOf(KeyBindings.parseAction(word));
    }

    /** A player's action's label. */
    public static String label(int action) {
        return KeyBindings.label(ACTIONS[action]);
    }

    /** Both players' keys, for the console. */
    public static String describe() {
        StringBuilder s = new StringBuilder();
        for (int p = 0; p < PLAYERS; p++) {
            s.append(p == 0 ? "" : "\n").append("Player ").append(p + 1).append(':');
            for (int a = 0; a < ACTIONS.length; a++) {
                s.append("\n  ").append(label(a)).append(": ").append(name(key(p, a, 0)));
                if (key(p, a, 1) != 0) {
                    s.append(", ").append(name(key(p, a, 1)));
                }
            }
        }
        return s.toString();
    }
}
