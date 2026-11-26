package nfm.lit;

import java.awt.event.KeyEvent;
import java.util.Locale;

/**
 * Which keys do what: each of the game's actions has two keys, by the
 * key's place on the keyboard ({@link KeyEvent}'s key code), so Shift and
 * Caps Lock change nothing and Shift, Ctrl and Alt can be keys of their
 * own. The first keys are the game's own: the arrows, Space, Z, X, V, P,
 * A, M and N. The second are free.
 *
 * <p>Some keys are not the player's to give away: Escape and Enter (they
 * pause and go back), F3 (the performance overlay), F11 (fullscreen), Page
 * Up, Page Down and Tab (the settings' tabs). The arrows, Enter and Escape
 * also always move through the menus, so no choice of keys locks the
 * player out.
 *
 * <p>Changed on the game thread (the settings screen, the console) and read
 * on the window's thread as keys go down, so the table is replaced whole,
 * never changed in place.
 */
public final class KeyBindings {

    public static final int NONE = -1;
    public static final int ACCELERATE = 0;
    public static final int BRAKE = 1;
    public static final int STEER_LEFT = 2;
    public static final int STEER_RIGHT = 3;
    public static final int HANDBRAKE = 4;
    public static final int LOOK_BACK = 5;
    public static final int LOOK_BACK_OTHER = 6;
    public static final int CHANGE_VIEW = 7;
    public static final int PAUSE = 8;
    public static final int ARROW = 9;
    public static final int MUSIC = 10;
    public static final int SOUNDS = 11;
    /** Uses the powerup the car holds (the powerups extra); added after the game's own actions, so theirs keep their numbers. */
    public static final int USE_POWERUP = 12;
    public static final int COUNT = 13;
    /** Keys each action can have. */
    public static final int SLOTS = 2;

    private static final String[] LABELS = {"Accelerate", "Brake and reverse", "Steer left", "Steer right",
        "Handbrake", "Look back", "Look back, other side", "Change view", "Pause", "Arrow: track or cars",
        "Music on or off", "Sounds on or off", "Use powerup"};
    private static final String[] HELP = {
        "Speeds the car up. In the air, the four driving keys turn the car for stunts.",
        "Brakes, and once the car has stopped, reverses.",
        "Steers left.",
        "Steers right.",
        "Locks the back wheels, for sharp turns.",
        "The camera swings round to look behind, for as long as it is held.",
        "As Look back, swinging round the other way.",
        "Steps through the three cameras.",
        "Pauses the race. Enter and Escape always pause too.",
        "Points the arrow at the top along the track, or at the nearest car.",
        "Switches the music off and on again.",
        "Switches the race's sounds off and on again.",
        "With Powerups on (Extras tab): fires the nitrous the car holds, or drops its bomb behind it. Dynamite throws itself.",
    };
    /** The game's own keys: the first of each action's. */
    private static final int[] DEFAULTS = {KeyEvent.VK_UP, KeyEvent.VK_DOWN, KeyEvent.VK_LEFT, KeyEvent.VK_RIGHT,
        KeyEvent.VK_SPACE, KeyEvent.VK_Z, KeyEvent.VK_X, KeyEvent.VK_V, KeyEvent.VK_P, KeyEvent.VK_A,
        KeyEvent.VK_M, KeyEvent.VK_N, KeyEvent.VK_CONTROL};
    /** The keys that are not the player's to give away. */
    private static final int[] RESERVED = {KeyEvent.VK_ESCAPE, KeyEvent.VK_ENTER, KeyEvent.VK_F3, KeyEvent.VK_F11,
        KeyEvent.VK_PAGE_UP, KeyEvent.VK_PAGE_DOWN, KeyEvent.VK_TAB, KeyEvent.VK_WINDOWS, KeyEvent.VK_CONTEXT_MENU,
        KeyEvent.VK_UNDEFINED};

    /** Each action's keys, {@code [action * SLOTS + slot]}, 0 for none. */
    private static volatile int[] keys = defaults();
    private static volatile boolean changed;

    private KeyBindings() {
    }

    private static int[] defaults() {
        int[] k = new int[COUNT * SLOTS];
        for (int a = 0; a < COUNT; a++) {
            k[a * SLOTS] = DEFAULTS[a];
        }
        return k;
    }

    public static String label(int action) {
        return LABELS[action];
    }

    public static String help(int action) {
        return HELP[action];
    }

    /** The action a key does, or NONE. */
    public static int action(int keyCode) {
        if (keyCode == 0) {
            return NONE;
        }
        int[] k = keys;
        for (int i = 0; i < k.length; i++) {
            if (k[i] == keyCode) {
                return i / SLOTS;
            }
        }
        return NONE;
    }

    /** An action's key in a slot, or 0. */
    public static int key(int action, int slot) {
        return keys[action * SLOTS + slot];
    }

    public static boolean reserved(int keyCode) {
        for (int r : RESERVED) {
            if (r == keyCode) {
                return true;
            }
        }
        return false;
    }

    /**
     * Gives a key to an action's slot, taking it from wherever it was. 0
     * clears the slot.
     *
     * @return the action the key was taken from, or NONE
     */
    public static synchronized int bind(int action, int slot, int keyCode) {
        if (keyCode != 0 && reserved(keyCode)) {
            return NONE;
        }
        int[] k = keys.clone();
        int from = NONE;
        if (keyCode != 0) {
            for (int i = 0; i < k.length; i++) {
                if (k[i] == keyCode && i != action * SLOTS + slot) {
                    k[i] = 0;
                    from = i / SLOTS;
                }
            }
        }
        k[action * SLOTS + slot] = keyCode;
        keys = k;
        changed = true;
        return from == action ? NONE : from;
    }

    /** Back to the game's own keys. */
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

    /** One action's keys, as saved: the first key and the second's code above it. */
    static int saved(int action) {
        return key(action, 0) | key(action, 1) << 16;
    }

    /**
     * The keys as saved, one number for each action (missing ones, below 0,
     * keep the game's own); a key on two actions, or one not the player's,
     * goes back to the game's own keys.
     */
    static synchronized void load(int[] saved) {
        int[] k = defaults();
        for (int a = 0; a < COUNT && a < saved.length; a++) {
            if (saved[a] >= 0) {
                k[a * SLOTS] = saved[a] & 0xFFFF;
                k[a * SLOTS + 1] = saved[a] >>> 16 & 0xFFFF;
            }
        }
        // an action newer than the save gives up its own key if the player has put it on another
        for (int a = 0; a < COUNT; a++) {
            if ((a >= saved.length || saved[a] < 0) && takenElsewhere(k, a)) {
                k[a * SLOTS] = 0;
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

    /** True if action {@code a}'s first key in {@code k} is one of another action's. */
    private static boolean takenElsewhere(int[] k, int a) {
        int key = k[a * SLOTS];
        for (int i = 0; i < k.length && key != 0; i++) {
            if (i / SLOTS != a && k[i] == key) {
                return true;
            }
        }
        return false;
    }

    /** A key's name for the screen: "Space", "W", "Up", "Num 8", or a dash for none. */
    public static String name(int keyCode) {
        if (keyCode == 0) {
            return "-";
        }
        return switch (keyCode) {
            case KeyEvent.VK_CONTROL -> "Ctrl";
            case KeyEvent.VK_BACK_SPACE -> "Backspace";
            case KeyEvent.VK_CAPS_LOCK -> "Caps Lock";
            case KeyEvent.VK_ALT_GRAPH -> "Alt Gr";
            default -> {
                String text = KeyEvent.getKeyText(keyCode);
                if (text.startsWith("NumPad-")) {
                    text = "Num " + text.substring(7);
                } else if (text.startsWith("Unknown")) {
                    text = "Key " + keyCode;
                }
                yield text;
            }
        };
    }

    /** The key a console word names: a letter or digit, or a key's name as {@link #name} gives it; 0 for none. */
    public static int parse(String word) {
        String w = word.trim();
        if (w.equals("-") || w.equalsIgnoreCase("none")) {
            return 0;
        }
        if (w.length() == 1) {
            int code = KeyEvent.getExtendedKeyCodeForChar(Character.toLowerCase(w.charAt(0)));
            return code == KeyEvent.VK_UNDEFINED ? -1 : code;
        }
        for (int code = 1; code < 0x400; code++) {
            String n = name(code);
            if (n.replace(" ", "").equalsIgnoreCase(w.replace(" ", "")) && !n.startsWith("Key ")) {
                return code;
            }
        }
        return -1;
    }

    /** The action a console word names: its label's first word or the whole label, any case; NONE if none. */
    public static int parseAction(String word) {
        String w = word.toLowerCase(Locale.ROOT).replace('-', ' ').replace('_', ' ');
        String[] shortNames = {"accelerate", "brake", "left", "right", "handbrake", "look", "lookother", "view",
            "pause", "arrow", "music", "sounds"};
        for (int a = 0; a < COUNT; a++) {
            if (shortNames[a].equals(w.replace(" ", "")) || LABELS[a].toLowerCase(Locale.ROOT).equals(w)) {
                return a;
            }
        }
        return NONE;
    }

    /** Every action and its keys, for the console. */
    public static String describe() {
        StringBuilder s = new StringBuilder();
        for (int a = 0; a < COUNT; a++) {
            s.append(a == 0 ? "" : "\n").append(LABELS[a]).append(": ").append(name(key(a, 0)));
            if (key(a, 1) != 0) {
                s.append(", ").append(name(key(a, 1)));
            }
        }
        return s.toString();
    }

    /**
     * The action of a key as the game has always numbered it (see
     * {@link GameSparker#pressKey}): what the autopilot, recorded races and
     * game controllers press. These keep the game's own keys, whatever the
     * keyboard's are.
     */
    static int legacyAction(int legacy) {
        return switch (legacy) {
            case 1004 -> ACCELERATE;
            case 1005 -> BRAKE;
            case 1006 -> STEER_LEFT;
            case 1007 -> STEER_RIGHT;
            case 32 -> HANDBRAKE;
            case 122, 90 -> LOOK_BACK;
            case 120, 88 -> LOOK_BACK_OTHER;
            case 118, 86 -> CHANGE_VIEW;
            case 112, 80 -> PAUSE;
            case 97, 65 -> ARROW;
            case 109, 77 -> MUSIC;
            case 110, 78 -> SOUNDS;
            case 99, 67 -> USE_POWERUP;
            default -> NONE;
        };
    }

    /** In the menus an arrow is always its own direction: the action it does there, or NONE. */
    static int menuArrow(int keyCode) {
        return switch (keyCode) {
            case KeyEvent.VK_UP, KeyEvent.VK_KP_UP -> ACCELERATE;
            case KeyEvent.VK_DOWN, KeyEvent.VK_KP_DOWN -> BRAKE;
            case KeyEvent.VK_LEFT, KeyEvent.VK_KP_LEFT -> STEER_LEFT;
            case KeyEvent.VK_RIGHT, KeyEvent.VK_KP_RIGHT -> STEER_RIGHT;
            default -> NONE;
        };
    }
}
