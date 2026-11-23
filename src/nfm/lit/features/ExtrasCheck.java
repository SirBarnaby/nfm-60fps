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
        System.out.println("[extras] checks: " + passed + " passed, " + failed + " failed");
        System.exit(failed == 0 && passed > 0 ? 0 : 1);
    }

    private static boolean wants(String only, String feature) {
        return only.equals("all") || only.equals(feature);
    }
}
