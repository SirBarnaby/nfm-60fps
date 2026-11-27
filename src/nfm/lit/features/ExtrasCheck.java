package nfm.lit.features;

import java.util.Locale;

/**
 * The extras' own checks: each feature plays the situations its
 * requirements name (a wheel locking, a power slide, a landing...) on a
 * made-up race and checks what it would show. Run by
 * {@code test\extras-check.ps1}; needs no window, sound or race file.
 * <p>
 * {@code java -cp madness.jar nfm.lit.features.ExtrasCheck [feature]}
 * prints a line per check and exits 0 when every one passes.
 */
public final class ExtrasCheck {

    private static int passed;
    private static int failed;
    private static int waiting;

    private ExtrasCheck() {
    }

    /** Records one check of {@code feature}: {@code name} passed if {@code ok}, with what was measured. */
    public static void check(String feature, String name, boolean ok, String measured) {
        if (ok) {
            passed++;
        } else {
            failed++;
        }
        System.out.println("[extras] " + feature + ": " + name + ": " + (ok ? "ok" : "FAILED") + " (" + measured + ")");
    }

    /**
     * A check of {@code feature} only the owner can pass ({@code name}, such
     * as looking values over): reported on every run as waiting, with what
     * is measured, and counted apart. It is no pass: the requirement stays
     * unmet until the owner has done it.
     */
    public static void waiting(String feature, String name, String measured) {
        waiting++;
        System.out.println("[extras] " + feature + ": " + name + ": WAITING FOR THE OWNER (" + measured + ")");
    }

    /** Formats a number for a check's line. */
    public static String n(double v) {
        return String.format(Locale.ROOT, "%.3g", v);
    }

    public static void main(String[] args) {
        String only = args.length > 0 ? args[0].toLowerCase(Locale.ROOT) : "all";
        if (wants(only, "skidmarks")) {
            nfm.lit.features.skidmarks.SkidmarkCheck.run();
        }
        if (wants(only, "wingtrail")) {
            nfm.lit.features.wingtrail.WingTrailCheck.run();
        }
        if (wants(only, "realism")) {
            nfm.lit.features.realism.RealismCheck.run();
        }
        if (wants(only, "helmetcam")) {
            nfm.lit.features.helmetcam.HelmetCheck.run();
        }
        if (wants(only, "sounds")) {
            nfm.lit.features.sounds.SoundsCheck.run();
        }
        if (wants(only, "enginesound")) {
            nfm.lit.features.enginesound.EngineCheck.run();
        }
        if (wants(only, "powerups")) {
            nfm.lit.features.powerups.PowerupsCheck.run();
        }
        if (wants(only, "wrecks")) {
            nfm.lit.features.wrecks.WrecksCheck.run();
        }
        if (wants(only, "handling")) {
            nfm.lit.features.handling.HandlingCheck.run();
        }
        if (wants(only, "tailwhip")) {
            nfm.lit.features.tailwhip.TailwhipCheck.run();
        }
        if (wants(only, "weather")) {
            nfm.lit.features.weather.WeatherCheck.run();
        }
        if (wants(only, "personality")) {
            nfm.lit.features.personality.PersonalityCheck.run();
        }
        if (wants(only, "physx")) {
            nfm.lit.features.physx.PhysxCheck.run();
        }
        System.out.println("[extras] checks: " + passed + " passed, " + failed + " failed"
                + (waiting > 0 ? ", " + waiting + " waiting for the owner" : ""));
        System.exit(failed == 0 && passed > 0 ? 0 : 1);
    }

    private static boolean wants(String only, String feature) {
        return only.equals("all") || only.equals(feature);
    }
}
