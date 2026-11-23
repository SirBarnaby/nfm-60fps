package nfm.lit.features;

import java.util.Locale;
import java.util.function.BiConsumer;
import java.util.function.ToIntFunction;

/**
 * One switch per extra, read and saved as every other setting is: for one
 * run {@code -Dnfm.<id>}, else {@code -Dnfm.features} ({@code looks},
 * {@code races}, {@code all}, {@code none} or a comma-separated list of
 * ids), else the saved choice (never in hidden test runs: the game's cookie
 * reader gives nothing there), else the default. Until 1.0 every default is
 * off, because each changes the look, the sound or the races; only the
 * explosions' screen flash, part of the powerups, defaults on.
 * <p>
 * The cookies' names all start with {@code xtra}, which no other cookie
 * contains, and none of them contains another (the game finds a cookie by
 * a part of its name).
 */
public final class FeatureSettings {

    /** What a switch changes. */
    public enum Kind {
        /** Only the look: reads the race, never writes it. */
        LOOK,
        /** Only the sound, through the 3D sound: reads the race, never writes it. */
        SOUND,
        /** The races themselves: its state is race state. */
        RACE
    }

    /** One switch. */
    public static final class Switch {
        public final String id;
        public final String label;
        public final String help;
        public final Kind kind;
        /** The feature this switch belongs to: its own id, or the feature it sets a detail of. */
        public final String owner;
        final String cookie;
        private final int[] values;
        private final String[] names;
        private final int fallback;
        private volatile int value;
        private volatile boolean changed;

        Switch(String id, String label, String help, String cookie, Kind kind, int fallback, String... names) {
            this(id, id, label, help, cookie, kind, fallback, names);
        }

        Switch(String id, String owner, String label, String help, String cookie, Kind kind, int fallback,
                String... names) {
            this.id = id;
            this.owner = owner;
            this.label = label;
            this.help = help;
            this.cookie = cookie;
            this.kind = kind;
            this.names = names;
            this.values = new int[names.length];
            for (int v = 0; v < names.length; v++) {
                values[v] = v;
            }
            this.fallback = fallback;
            this.value = fallback;
        }

        public int[] values() {
            return values.clone();
        }

        public String name(int v) {
            return v >= 0 && v < names.length ? names[v] : String.valueOf(v);
        }

        public int value() {
            return value;
        }

        public boolean on() {
            return value != 0;
        }

        /** The value {@code text} names: a number, a name, or on/off. */
        int parse(String text) {
            String t = text.trim().toLowerCase(Locale.ROOT);
            for (int v = 0; v < names.length; v++) {
                if (names[v].toLowerCase(Locale.ROOT).equals(t)) {
                    return v;
                }
            }
            switch (t) {
                case "on", "true", "yes" -> {
                    return names.length - 1;
                }
                case "off", "false", "no" -> {
                    return 0;
                }
                default -> {
                    try {
                        int v = Integer.parseInt(t);
                        return v >= 0 && v < names.length ? v : -1;
                    } catch (NumberFormatException e) {
                        return -1;
                    }
                }
            }
        }
    }

    private static final String LOOK_1_0 = " Until 1.0 it is off by default, because it changes the look.";
    private static final String SOUND_1_0 = " Until 1.0 it is off by default, because it changes the sound.";
    private static final String RACE_1_0 = " Until 1.0 it is off by default, because it changes the races.";

    private static final Switch[] SWITCHES = {
        new Switch("skidmarks", "Skidmarks",
                "Each wheel leaves a mark where it slides, spins or locks, in the colour of what it last drove"
                        + " through, with smoke, dust or sparks." + LOOK_1_0,
                "xtramarks", Kind.LOOK, 0, "Off", "On"),
        new Switch("skiddetail", "skidmarks", "Skidmark detail",
                "Full: streaks along a locked wheel's mark, fine striations from a tyre sliding sideways, and"
                        + " every particle. Simple: plain marks and half the particles, for a slower computer.",
                "xtradetail", Kind.LOOK, 1, "Simple", "Full"),
        new Switch("skidzones", "Last race's skid zones",
                "The road is a little darker where the cars slid most the last time you raced this stage."
                        + LOOK_1_0,
                "xtrazones", Kind.LOOK, 0, "Off", "On"),
        new Switch("wingtrail", "Radical One's wing trail",
                "Thin white vapour from the Radical One's wing tips while you steer it in the air." + LOOK_1_0,
                "xtrawing", Kind.LOOK, 0, "Off", "On"),
        new Switch("realism", "Damage and heat",
                "Glowing brakes, steam, smoke, flames from the exhaust, a flapping bonnet, a dragging bumper, cracked"
                        + " glass, bent wheels, dirt and scratches. Only the look: the car drives as always."
                        + LOOK_1_0,
                "xtrareal", Kind.LOOK, 0, "Off", "On"),
        new Switch("helmetcam", "Helmet cam",
                "Adds a view from the driver's helmet to the views you cycle through, with the cockpit around you"
                        + " and the g-forces felt in the head." + LOOK_1_0,
                "xtrahelmet", Kind.LOOK, 0, "Off", "On"),
        new Switch("enginesound", "Rebuilt engine sound",
                "With the 3D sound: engines, exhausts and tyres rebuilt from the game's own recordings, grain by"
                        + " grain, so each car sounds fuller at every speed. Classic sound stays as it was."
                        + SOUND_1_0,
                "xtraengine", Kind.SOUND, 0, "Off", "On"),
        new Switch("powerups", "Powerups",
                "Boxes along the track give nitrous, dynamite or a magnet; the computer cars use them too."
                        + RACE_1_0,
                "xtraboxes", Kind.RACE, 0, "Off", "On"),
        new Switch("flash", "powerups", "Explosion flash",
                "With powerups: the screen flashes for two frames when dynamite goes off. Off for anyone the"
                        + " flash bothers.",
                "xtraflash", Kind.LOOK, 1, "Off", "On"),
        new Switch("handling", "Suspension handling",
                "Each wheel on its own spring, grip that runs out in a circle, weight moving under braking and in"
                        + " corners. A new feel, meant for after 1.0." + RACE_1_0,
                "xtragrip", Kind.RACE, 0, "Off", "On"),
    };

    /** Raised by every change, so the features can follow a switch turned during a race. */
    private static volatile int version;

    private FeatureSettings() {
    }

    /** Every switch, in the settings' order. */
    public static Switch[] all() {
        return SWITCHES.clone();
    }

    /** The switch {@code id}, or null. */
    public static Switch find(String id) {
        for (Switch s : SWITCHES) {
            if (s.id.equalsIgnoreCase(id)) {
                return s;
            }
        }
        return null;
    }

    /** Switch {@code id}'s value (0 for an unknown id). */
    public static int get(String id) {
        Switch s = find(id);
        return s == null ? 0 : s.value;
    }

    public static boolean on(String id) {
        return get(id) != 0;
    }

    /** Sets switch {@code id} and saves it with the next settings save; false for an unknown id or value. */
    public static boolean set(String id, int value) {
        Switch s = find(id);
        if (s == null || value < 0 || value >= s.names.length) {
            return false;
        }
        if (s.value != value) {
            s.value = value;
            s.changed = true;
            version++;
        }
        return true;
    }

    /** Sets switch {@code id} from text (a number, a name, on or off); false if it means nothing. */
    public static boolean set(String id, String text) {
        Switch s = find(id);
        return s != null && set(id, s.parse(text));
    }

    static int version() {
        return version;
    }

    /**
     * Reads every switch: its property, else {@code nfm.features}, else
     * {@code cookie} (the saved value, or -1 for none), else its default.
     */
    public static void load(ToIntFunction<String> cookie) {
        String groups = System.getProperty("nfm.features");
        for (Switch s : SWITCHES) {
            int value = -1;
            String own = System.getProperty("nfm." + s.id);
            if (own != null) {
                value = s.parse(own);
            }
            if (value < 0 && groups != null) {
                value = fromGroups(s, groups);
            }
            if (value < 0) {
                int saved = cookie.applyAsInt(s.cookie);
                value = saved >= 0 && saved < s.names.length ? saved : -1;
            }
            s.value = value < 0 ? s.fallback : value;
            s.changed = false;
        }
        version++;
    }

    /** What {@code nfm.features} says of switch {@code s}: on, off (for {@code none}), or -1 if nothing. */
    private static int fromGroups(Switch s, String groups) {
        int on = s.names.length - 1;
        for (String part : groups.toLowerCase(Locale.ROOT).split("[,;| ]+")) {
            boolean named = switch (part) {
                case "all" -> true;
                case "looks" -> s.kind != Kind.RACE;
                case "races" -> s.kind == Kind.RACE;
                default -> part.equals(s.id);
            };
            if (part.equals("none")) {
                return 0;
            }
            if (named) {
                return on;
            }
        }
        return -1;
    }

    /** Saves each switch changed since the last save, with {@code save} (cookie name, value). */
    public static void saveIfChanged(BiConsumer<String, String> save) {
        for (Switch s : SWITCHES) {
            if (s.changed) {
                s.changed = false;
                save.accept(s.cookie, String.valueOf(s.value));
            }
        }
    }

    /** A line per switch for the developer console. */
    public static String describe() {
        StringBuilder out = new StringBuilder();
        for (Switch s : SWITCHES) {
            out.append(s.id).append(" = ").append(s.name(s.value)).append(" (").append(s.label).append(")\n");
        }
        return out.toString();
    }
}
