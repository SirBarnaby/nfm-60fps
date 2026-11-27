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
                "Glowing brakes, steam, smoke, flames from the exhaust, cracked"
                        + " glass, bent wheels, dirt and scratches. Only the look: the car drives as always."
                        + LOOK_1_0,
                "xtrareal", Kind.LOOK, 0, "Off", "On"),
        new Switch("helmetcam", "Helmet cam",
                "Adds a view from the driver's helmet after the others on the view key: the road over the bonnet,"
                        + " the head thrown by braking, corners and landings, the edges darker under hard g-forces."
                        + LOOK_1_0,
                "xtrahelmet", Kind.LOOK, 0, "Off", "On"),
        new Switch("enginesound", "Rebuilt engine sound",
                "With the 3D sound: engines, exhausts and tyres rebuilt from the game's own recordings, grain by"
                        + " grain, so each car sounds fuller at every speed. Classic sound stays as it was."
                        + SOUND_1_0,
                "xtraengine", Kind.SOUND, 0, "Off", "On"),
        new Switch("powerups", "Powerups",
                "A few rows of boxes along the track give nitrous, dynamite or a bomb to drop behind you; the"
                        + " computer cars use them too."
                        + RACE_1_0,
                "xtraboxes", Kind.RACE, 0, "Off", "On"),
        new Switch("flash", "powerups", "Explosion flash",
                "The screen flashes white for a frame when something explodes near you. Off for"
                        + " anyone the flash bothers.",
                "xtraflash", Kind.LOOK, 1, "Off", "On"),
        new Switch("handling", "Suspension handling",
                "Each wheel on its own spring and damper, 120 times a second; one grip shared by braking and"
                        + " turning that lets go smoothly, so a slide can be held; weight forward under braking and"
                        + " outward in corners; a drift assist that keeps slides catchable. Stunts stay." + RACE_1_0,
                "xtragrip", Kind.RACE, 0, "Off", "On"),
        new Switch("wrecks", "Exploding wrecks",
                "A wasted car burns for two seconds, then explodes: the blast dents the ground and throws and"
                        + " damages the cars close to it, like a small dynamite. Off: it burns as it always did."
                        + RACE_1_0,
                "xtrawreck", Kind.RACE, 0, "Off", "On"),
        new Switch("tailwhip", "Tailwhip",
                "Hold the handbrake and the brake and steer while moving: the car rotates more easily and its"
                        + " tail swings round. A swinging tail that hits another car spins it, and everyone is"
                        + " told who tailwhipped whom. The computer cars' driving changes with it too." + RACE_1_0,
                "xtrawhip", Kind.RACE, 0, "Off", "On"),
        new Switch("personality", "Car personalities",
                "One small thing each that only that car does: Tornado Shark starts dusty, Lead Oxide shows heat"
                        + " at its front, Kool Kat lands heavily, Mighty Eight's radiators breathe and its nitro"
                        + " burns big, MAX Revenge's blower shakes, M A S H E E N's plow carries dirt, DR Monstaa's"
                        + " tyres squash, something slides in Wow Caninaro and EL KING's cab rocks. Seen and heard"
                        + " only; the sounds need the 3D sound." + LOOK_1_0,
                "xtraquirks", Kind.LOOK, 0, "Off", "On"),
        new Switch("weather", "Weather",
                "Rain, rain with fog, or snow in every race: a grey or pale sky, duller light, rain or snow"
                        + " falling around you, splashes, drops on the screen (more behind another car), snow that"
                        + " gets deeper, and its sound with the 3D sound. Only the look and the sound: the road"
                        + " grips as in the dry. The sky changes with the next race." + LOOK_1_0,
                "xtraweather", Kind.LOOK, 0, "Off", "Rain", "Rain and fog", "Snow"),
        new Switch("physx", "PhysX debris",
                "NVIDIA PhysX moves loose pieces through the race: panels a hard hit knocks off a car, chunks a"
                        + " wasted car and an explosion throw, and in snow the lumps the cars break out of it. They"
                        + " fall, bounce off the ground, walls and ramps, come to rest, and cars shove them about."
                        + " Only seen: the cars drive through them as if they were not there." + LOOK_1_0,
                "xtraphysx", Kind.LOOK, 0, "Off", "On"),
        new Switch("customgame", "Custom game",
                "Adds Custom Game to the main menu: up to 32 computer cars, whose cars they drive (Default, Novice,"
                        + " Pro, or picked one by one), and above 9 a start in groups of six, a second apart, with you"
                        + " last. Not in split screen." + RACE_1_0,
                "xtracustom", Kind.RACE, 0, "Off", "On"),
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
