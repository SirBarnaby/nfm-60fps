package nfm.lit.features.realism;

import java.util.Locale;
import java.util.Map;

/**
 * A car's short sheet (realism, "Each car has a short sheet"): its engine's
 * layout, where the engine sits, its cooling, turbo or not, its brakes and
 * exhausts. The effects read it: where steam and smoke come from, how hot
 * the brakes run, how the exhaust behaves. Kept in
 * {@code car-sheets.txt} next to this class ({@link CarSheets}).
 */
public final class CarSheet {

    /* where the engine sits */
    public static final int FRONT = 0;
    public static final int MID = 1;
    public static final int REAR = 2;
    /* its cooling */
    public static final int RADIATOR_FRONT = 0;
    public static final int SIDE_PODS = 1;
    public static final int AIR = 2;
    /* its brakes */
    public static final int DRUM = 0;
    public static final int DISC = 1;
    public static final int CARBON = 2;
    /** Exhausts: upright pipes behind the cab rather than pipes at the back. */
    public static final int STACKS = 3;

    /** For a car with no line in the sheets: a front-engined V8 with a radiator, discs and two pipes. */
    public static final CarSheet DEFAULT = new CarSheet("", "V8", FRONT, RADIATOR_FRONT, false, DISC, 2);

    public final String car;
    public final String engine;
    public final int sits;
    public final int cooling;
    public final boolean turbo;
    public final int brakes;
    /** 1 or 2 pipes at the back, or {@link #STACKS}. */
    public final int exhausts;
    /** The car's own traits, written after the columns as {@code name=value} (such as {@code spikes=yes}). */
    public final Map<String, String> traits;

    CarSheet(String car, String engine, int sits, int cooling, boolean turbo, int brakes, int exhausts) {
        this(car, engine, sits, cooling, turbo, brakes, exhausts, Map.of());
    }

    CarSheet(String car, String engine, int sits, int cooling, boolean turbo, int brakes, int exhausts,
            Map<String, String> traits) {
        this.traits = traits;
        this.car = car;
        this.engine = engine;
        this.sits = sits;
        this.cooling = cooling;
        this.turbo = turbo;
        this.brakes = brakes;
        this.exhausts = exhausts;
    }

    /** True if the sheet gives trait {@code name} as yes. */
    public boolean is(String name) {
        return "yes".equals(traits.get(name));
    }

    /** Its exhaust pops when the throttle lifts at high revs: a charged engine's does, or one whose sheet says {@code pops=yes}. */
    public boolean pops() {
        return "yes".equals(traits.getOrDefault("pops", turbo ? "yes" : "no"));
    }

    /** Trait {@code name}, or {@code fallback} if the sheet does not give it. */
    public String trait(String name, String fallback) {
        return traits.getOrDefault(name, fallback);
    }

    /** Trait {@code name} as a number, or {@code fallback} if the sheet does not give one. */
    public float number(String name, float fallback) {
        try {
            String text = traits.get(name);
            return text == null ? fallback : Float.parseFloat(text);
        } catch (NumberFormatException e) {
            return fallback;
        }
    }

    /** How fast the brakes take heat: drums, closed in, the most; carbon the least. */
    float brakeHeating() {
        return brakes == DRUM ? 1.3F : brakes == CARBON ? 0.85F : 1F;
    }

    /** How fast they lose it: carbon the most, drums the least. */
    float brakeCooling() {
        return brakes == DRUM ? 0.7F : brakes == CARBON ? 1.3F : 1F;
    }

    /** The engine's cylinders, from its layout (a rotary counts as 2). */
    int cylinders() {
        String e = engine.toLowerCase(Locale.ROOT);
        if (e.startsWith("rotary")) {
            return 2;
        }
        int n = 0;
        for (int i = 0; i < e.length(); i++) {
            char ch = e.charAt(i);
            if (ch >= '0' && ch <= '9') {
                n = n * 10 + (ch - '0');
            }
        }
        return n > 0 ? n : 8;
    }

    /** The sheet as its line in the file. */
    String line() {
        return String.format(Locale.ROOT, "%-14s %-7s %-6s %-8s %-6s %-7s %s", car, engine,
                new String[] {"front", "mid", "rear"}[sits], new String[] {"front", "sides", "air"}[cooling],
                turbo ? "yes" : "no", new String[] {"drum", "disc", "carbon"}[brakes],
                exhausts == STACKS ? "stacks" : Integer.toString(exhausts)).trim();
    }
}
