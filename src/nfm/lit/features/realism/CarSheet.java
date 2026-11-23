package nfm.lit.features.realism;

import java.util.Locale;

/**
 * A car's short sheet (realism, "Each car has a short sheet"): its engine's
 * layout, where the engine sits, its cooling, turbo or not, its brakes and
 * exhausts. The effects read it: where steam and smoke come from, how hot
 * the brakes run, how the exhaust behaves. Kept in
 * {@code data\extras\car-sheets.txt} ({@link CarSheets}).
 */
final class CarSheet {

    /* where the engine sits */
    static final int FRONT = 0;
    static final int MID = 1;
    static final int REAR = 2;
    /* its cooling */
    static final int RADIATOR_FRONT = 0;
    static final int SIDE_PODS = 1;
    static final int AIR = 2;
    /* its brakes */
    static final int DRUM = 0;
    static final int DISC = 1;
    static final int CARBON = 2;
    /** Exhausts: upright pipes behind the cab rather than pipes at the back. */
    static final int STACKS = 3;

    /** For a car with no line in the sheets: a front-engined V8 with a radiator, discs and two pipes. */
    static final CarSheet DEFAULT = new CarSheet("", "V8", FRONT, RADIATOR_FRONT, false, DISC, 2);

    final String car;
    final String engine;
    final int sits;
    final int cooling;
    final boolean turbo;
    final int brakes;
    /** 1 or 2 pipes at the back, or {@link #STACKS}. */
    final int exhausts;

    CarSheet(String car, String engine, int sits, int cooling, boolean turbo, int brakes, int exhausts) {
        this.car = car;
        this.engine = engine;
        this.sits = sits;
        this.cooling = cooling;
        this.turbo = turbo;
        this.brakes = brakes;
        this.exhausts = exhausts;
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
                exhausts == STACKS ? "stacks" : Integer.toString(exhausts));
    }
}
