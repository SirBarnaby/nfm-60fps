package nfm.lit;

import fallk.logmaster.HLogger;
import org.lwjgl.sdl.SDL_Event;

import java.nio.IntBuffer;
import java.util.ArrayList;
import java.util.List;

import static org.lwjgl.sdl.SDLError.SDL_GetError;
import static org.lwjgl.sdl.SDLEvents.SDL_EVENT_GAMEPAD_ADDED;
import static org.lwjgl.sdl.SDLEvents.SDL_EVENT_GAMEPAD_REMOVED;
import static org.lwjgl.sdl.SDLEvents.SDL_PollEvent;
import static org.lwjgl.sdl.SDLEvents.SDL_PumpEvents;
import static org.lwjgl.sdl.SDLGamepad.*;
import static org.lwjgl.sdl.SDLHints.SDL_HINT_JOYSTICK_ALLOW_BACKGROUND_EVENTS;
import static org.lwjgl.sdl.SDLHints.SDL_SetHint;
import static org.lwjgl.sdl.SDLInit.SDL_INIT_GAMEPAD;
import static org.lwjgl.sdl.SDLInit.SDL_Init;
import static org.lwjgl.sdl.SDLJoystick.SDL_AttachVirtualJoystick;
import static org.lwjgl.sdl.SDLJoystick.SDL_JOYSTICK_TYPE_GAMEPAD;
import static org.lwjgl.sdl.SDLJoystick.SDL_OpenJoystick;
import static org.lwjgl.sdl.SDLJoystick.SDL_SetJoystickVirtualAxis;

/**
 * Game controllers, through SDL3: any pad SDL knows (Xbox, PlayStation,
 * Switch and most others) presses the game's own keys, so a pad drives the
 * menus and the race exactly as the keyboard does, and races recorded with
 * one replay as if typed.
 * <p>
 * In a race: right trigger or A accelerates, left trigger or X brakes and
 * reverses, the left stick or the d-pad steers, B is the handbrake, LB and
 * RB look back, Y changes the view, Start or Back pauses. In the menus the
 * stick and d-pad are the arrow keys, held ones repeating as keys do, and A
 * or Start is Enter. Hard hits shake the pad.
 * <p>
 * The pads are read on a thread of their own, 250 times a second, and only
 * while the game's window has the focus.
 * <p>
 * In split screen each pad belongs to a player (see {@link Keys#playerOf}):
 * its keys drive that player's car, and only that player's hits shake it.
 */
final class Gamepads {

    /** What the pads drive: the game's key handling. */
    interface Keys {
        /** Presses or releases a key, as the keyboard would. */
        void key(int code, boolean down);

        /** Presses or releases a key for a split-screen player (0 or 1). */
        default void keyFor(int player, int code, boolean down) {
            key(code, down);
        }

        /**
         * Whose the pad at {@code index} of those connected ({@code names},
         * in order) is: -1 outside split screen (every pad drives the game),
         * 0 or 1 for a split-screen player, or -2 for no one.
         */
        default int playerOf(int index, List<String> names) {
            return -1;
        }

        /** True while a race is being driven (not its menus or replays). */
        boolean racing();

        /** True while the game's window has the focus. */
        boolean focused();
    }

    private static final int UP = 1004;
    private static final int DOWN = 1005;
    private static final int LEFT = 1006;
    private static final int RIGHT = 1007;
    private static final int ENTER = 10;
    private static final int ESCAPE = 27;
    private static final int SPACE = 32;
    private static final int LOOK_Z = 122;
    private static final int LOOK_X = 120;
    private static final int VIEW = 118;
    /** Every key a pad can press, one bit each in {@link #held}. */
    private static final int[] KEYS = {UP, DOWN, LEFT, RIGHT, ENTER, ESCAPE, SPACE, LOOK_Z, LOOK_X, VIEW};

    private static final int POLL_MILLIS = 4;
    /** How far a stick goes before it counts, of 32767: a worn stick rests well inside this. */
    private static final int STICK_DEAD_ZONE = 12000;
    /** How far a trigger goes before it counts, of 32767. */
    private static final int TRIGGER_DEAD_ZONE = 6000;
    /** Menu arrows held down repeat after this, then every REPEAT_MILLIS, as keys do. */
    private static final int REPEAT_DELAY_MILLIS = 400;
    private static final int REPEAT_MILLIS = 110;

    private static volatile boolean on = true;
    private static volatile boolean rumbleOn = true;
    private static volatile Gamepads running;

    private final Keys keys;
    private final List<Long> pads = new ArrayList<>();
    private final List<String> names = new ArrayList<>();
    /** The keys held, the arrow repeating and when it repeats next: for all pads, then for each split-screen player. */
    private final int[] held = new int[3];
    private final int[] repeatKey = new int[3];
    private final long[] repeatAtNanos = new long[3];
    /** Each pad's player as it was last read (see {@link Keys#playerOf}), for the shakes. */
    private volatile int[] padPlayers = new int[0];
    /* the next shake for all pads and for each split-screen player, set by any thread, played by the pads' own */
    private final float[] rumbleStrength = new float[3];
    private volatile boolean rumbleWanted;

    private Gamepads(Keys keys) {
        this.keys = keys;
    }

    /**
     * Starts reading the pads, unless they are turned off. Nothing happens
     * if SDL cannot start; the game is played with the keyboard as before.
     */
    static void start(Keys keys) {
        if (running != null) {
            return;
        }
        Gamepads g = new Gamepads(keys);
        running = g;
        Thread t = new Thread(g::run, "nfm-gamepads");
        t.setDaemon(true);
        t.start();
    }

    static void setOn(boolean value) {
        on = value;
    }

    static boolean isOn() {
        return on;
    }

    static void setRumble(boolean value) {
        rumbleOn = value;
    }

    static boolean isRumble() {
        return rumbleOn;
    }

    /** The pads connected now, by name; empty if none or they are not being read. */
    static List<String> connected() {
        Gamepads g = running;
        if (g == null) {
            return List.of();
        }
        synchronized (g.names) {
            return List.copyOf(g.names);
        }
    }

    /**
     * The player's car was hit with {@code force} (see
     * {@link RaceEvents#crash}): the pads shake, harder for harder hits.
     */
    static void hit(float force) {
        hit(-1, force);
    }

    /** A split-screen player's car was hit (see {@link #hit(float)}): that player's pads shake; -1 for every pad. */
    static void hit(int player, float force) {
        Gamepads g = running;
        if (g == null || !rumbleOn || !on) {
            return;
        }
        synchronized (g.rumbleStrength) {
            g.rumbleStrength[player + 1] = Math.max(g.rumbleStrength[player + 1], Math.min(1F, Math.abs(force) / 250F));
        }
        g.rumbleWanted = true;
    }

    private void run() {
        try {
            // the game's window is not SDL's, so SDL would otherwise think it never has the focus
            SDL_SetHint(SDL_HINT_JOYSTICK_ALLOW_BACKGROUND_EVENTS, "1");
            if (!SDL_Init(SDL_INIT_GAMEPAD)) {
                HLogger.warn("Game controllers are not available: " + SDL_GetError());
                return;
            }
        } catch (Throwable ex) { // a missing or unloadable SDL3.dll
            HLogger.warn("Game controllers are not available: " + ex);
            return;
        }
        long testPad = Boolean.getBoolean("nfm.gamepad.test") ? attachTestPad() : 0L;
        openPresent();
        try (SDL_Event event = SDL_Event.calloc()) {
            while (true) {
                SDL_PumpEvents();
                while (SDL_PollEvent(event)) {
                    if (event.type() == SDL_EVENT_GAMEPAD_ADDED) {
                        open(event.gdevice().which());
                    } else if (event.type() == SDL_EVENT_GAMEPAD_REMOVED) {
                        close(event.gdevice().which());
                    }
                }
                if (testPad != 0L) {
                    driveTestPad(testPad);
                }
                SDL_UpdateGamepads();
                readPads(on && keys.focused());
                shake();
                Thread.sleep(POLL_MILLIS);
            }
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
        }
    }

    /**
     * For -Dnfm.gamepad.test: a pad made up inside SDL, which the game sees
     * as any other. In races it holds the right trigger and the stick to the
     * right, so a hidden smoke test shows the car driven by a pad.
     */
    private static long attachTestPad() {
        try (org.lwjgl.sdl.SDL_VirtualJoystickDesc desc = org.lwjgl.sdl.SDL_VirtualJoystickDesc.calloc()) {
            desc.version(desc.sizeof())
                    .name(TEST_PAD_NAME)
                    .type((short) SDL_JOYSTICK_TYPE_GAMEPAD)
                    .naxes((short) SDL_GAMEPAD_AXIS_COUNT)
                    .nbuttons((short) SDL_GAMEPAD_BUTTON_COUNT);
            int id = SDL_AttachVirtualJoystick(desc);
            long stick = id != 0 ? SDL_OpenJoystick(id) : 0L;
            HLogger.info("Test game controller " + (stick != 0L ? "attached" : "failed: " + SDL_GetError()));
            return stick;
        } catch (RuntimeException ex) {
            HLogger.warn("Test game controller failed: " + ex);
            return 0L;
        }
    }

    /* the test pad's name, which SDL keeps pointing at while the game runs */
    private static final java.nio.ByteBuffer TEST_PAD_NAME = org.lwjgl.system.MemoryUtil.memUTF8("NFM test pad");

    private void driveTestPad(long stick) {
        boolean racing = keys.racing();
        SDL_SetJoystickVirtualAxis(stick, SDL_GAMEPAD_AXIS_RIGHT_TRIGGER, racing ? Short.MAX_VALUE : 0);
        SDL_SetJoystickVirtualAxis(stick, SDL_GAMEPAD_AXIS_LEFTX, (short) (racing ? 20000 : 0));
    }

    private void openPresent() {
        IntBuffer ids = SDL_GetGamepads();
        if (ids != null) {
            for (int i = 0; i < ids.remaining(); i++) {
                open(ids.get(i));
            }
        }
    }

    private void open(int id) {
        for (long pad : pads) {
            if (SDL_GetGamepadID(pad) == id) {
                return;
            }
        }
        long pad = SDL_OpenGamepad(id);
        if (pad == 0L) {
            HLogger.warn("Could not open a game controller: " + SDL_GetError());
            return;
        }
        String name = SDL_GetGamepadName(pad);
        name = name != null ? name : "Game controller";
        pads.add(pad);
        synchronized (names) {
            names.add(name);
        }
        HLogger.info("Game controller connected: " + name);
    }

    private void close(int id) {
        for (int i = 0; i < pads.size(); i++) {
            long pad = pads.get(i);
            if (SDL_GetGamepadID(pad) == id) {
                String name;
                synchronized (names) {
                    name = names.remove(i);
                }
                pads.remove(i);
                SDL_CloseGamepad(pad);
                HLogger.info("Game controller disconnected: " + name);
                return;
            }
        }
    }

    /**
     * Presses and releases what the pads hold now: every pad's keys together
     * outside split screen, each player's pads' keys for that player in it.
     */
    private void readPads(boolean focused) {
        int[] want = new int[3];
        int[] players = new int[pads.size()];
        boolean racing = keys.racing();
        List<String> connected;
        synchronized (names) {
            connected = List.copyOf(names);
        }
        for (int i = 0; i < pads.size(); i++) {
            int player = i < connected.size() ? keys.playerOf(i, connected) : -1;
            players[i] = player;
            if (focused && player >= -1 && player <= 1) {
                want[player + 1] |= wanted(pads.get(i), racing);
            }
        }
        padPlayers = players;
        for (int slot = 0; slot < want.length; slot++) {
            press(slot, want[slot]);
        }
    }

    /** The keys a pad holds down now, one bit per entry of {@link #KEYS}. */
    private int wanted(long pad, boolean racing) {
        int want = 0;
        int x = SDL_GetGamepadAxis(pad, SDL_GAMEPAD_AXIS_LEFTX);
        int y = SDL_GetGamepadAxis(pad, SDL_GAMEPAD_AXIS_LEFTY);
        boolean left = x < -STICK_DEAD_ZONE || button(pad, SDL_GAMEPAD_BUTTON_DPAD_LEFT);
        boolean right = x > STICK_DEAD_ZONE || button(pad, SDL_GAMEPAD_BUTTON_DPAD_RIGHT);
        boolean dpadUp = button(pad, SDL_GAMEPAD_BUTTON_DPAD_UP);
        boolean dpadDown = button(pad, SDL_GAMEPAD_BUTTON_DPAD_DOWN);
        boolean a = button(pad, SDL_GAMEPAD_BUTTON_SOUTH);
        boolean start = button(pad, SDL_GAMEPAD_BUTTON_START);
        want |= bit(LEFT, left) | bit(RIGHT, right);
        if (racing) {
            boolean gas = SDL_GetGamepadAxis(pad, SDL_GAMEPAD_AXIS_RIGHT_TRIGGER) > TRIGGER_DEAD_ZONE;
            boolean brake = SDL_GetGamepadAxis(pad, SDL_GAMEPAD_AXIS_LEFT_TRIGGER) > TRIGGER_DEAD_ZONE;
            want |= bit(UP, gas || a || dpadUp)
                    | bit(DOWN, brake || button(pad, SDL_GAMEPAD_BUTTON_WEST) || dpadDown)
                    | bit(SPACE, button(pad, SDL_GAMEPAD_BUTTON_EAST))
                    | bit(LOOK_Z, button(pad, SDL_GAMEPAD_BUTTON_LEFT_SHOULDER))
                    | bit(LOOK_X, button(pad, SDL_GAMEPAD_BUTTON_RIGHT_SHOULDER))
                    | bit(VIEW, button(pad, SDL_GAMEPAD_BUTTON_NORTH))
                    | bit(ENTER, start)
                    | bit(ESCAPE, button(pad, SDL_GAMEPAD_BUTTON_BACK));
        } else {
            want |= bit(UP, y < -STICK_DEAD_ZONE || dpadUp)
                    | bit(DOWN, y > STICK_DEAD_ZONE || dpadDown)
                    | bit(ENTER, a || start);
        }
        return want;
    }

    private static boolean button(long pad, int button) {
        return SDL_GetGamepadButton(pad, button);
    }

    private static int bit(int key, boolean down) {
        if (!down) {
            return 0;
        }
        for (int i = 0; i < KEYS.length; i++) {
            if (KEYS[i] == key) {
                return 1 << i;
            }
        }
        throw new IllegalArgumentException("key " + key);
    }

    /** A key for all pads (slot 0) or a split-screen player's (slot 1 or 2). */
    private void key(int slot, int code, boolean down) {
        if (slot == 0) {
            keys.key(code, down);
        } else {
            keys.keyFor(slot - 1, code, down);
        }
    }

    /** Releases the keys no longer held and presses the new ones; repeats a held menu arrow. */
    private void press(int slot, int want) {
        int changed = held[slot] ^ want;
        for (int i = 0; i < KEYS.length; i++) {
            int b = 1 << i;
            if ((changed & b) != 0 && (want & b) == 0) {
                key(slot, KEYS[i], false);
            }
        }
        long now = System.nanoTime();
        for (int i = 0; i < KEYS.length; i++) {
            int b = 1 << i;
            if ((changed & b) != 0 && (want & b) != 0) {
                key(slot, KEYS[i], true);
                if (KEYS[i] >= UP && KEYS[i] <= RIGHT) {
                    repeatKey[slot] = KEYS[i];
                    repeatAtNanos[slot] = now + REPEAT_DELAY_MILLIS * 1_000_000L;
                }
            }
        }
        held[slot] = want;
        if (repeatKey[slot] != 0 && (want & bit(repeatKey[slot], true)) == 0) {
            repeatKey[slot] = 0;
        }
        if (repeatKey[slot] != 0 && !keys.racing() && now - repeatAtNanos[slot] >= 0) {
            key(slot, repeatKey[slot], true);
            repeatAtNanos[slot] = now + REPEAT_MILLIS * 1_000_000L;
        }
    }

    private void shake() {
        if (!rumbleWanted) {
            return;
        }
        rumbleWanted = false;
        float[] strength = new float[3];
        synchronized (rumbleStrength) {
            System.arraycopy(rumbleStrength, 0, strength, 0, 3);
            java.util.Arrays.fill(rumbleStrength, 0F);
        }
        int[] players = padPlayers;
        for (int i = 0; i < pads.size(); i++) {
            int player = i < players.length ? players[i] : -1;
            float s = strength[0];
            if (player == 0 || player == 1) {
                s = Math.max(s, strength[player + 1]);
            }
            if (s <= 0F) {
                continue;
            }
            short low = (short) Math.round(0xffff * s);
            short high = (short) Math.round(0xffff * s * 0.6F);
            int millis = 90 + Math.round(160 * s);
            SDL_RumbleGamepad(pads.get(i), low, high, millis);
        }
    }
}
