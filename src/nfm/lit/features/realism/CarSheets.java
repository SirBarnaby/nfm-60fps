package nfm.lit.features.realism;

import fallk.logmaster.HLogger;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * The cars' sheets, {@code car-sheets.txt} next to this class: a class-path
 * resource that goes into madness.jar with the extra (not one of the game's
 * data files, so no mod replaces it). Read once, the first time a race asks.
 * A line that cannot be read is logged and left out; a car with no line gets
 * {@link CarSheet#DEFAULT}.
 */
public final class CarSheets {

    /** The resource's name, next to this class. */
    static final String RESOURCE = "car-sheets.txt";
    /** Where it is kept in the source, for messages. */
    static final String SOURCE = "src\\nfm\\lit\\features\\realism\\" + RESOURCE;

    private static Map<String, CarSheet> sheets;

    private CarSheets() {
    }

    /** The sheet of car model {@code model} (its file name, such as {@code radicalone}). */
    public static synchronized CarSheet of(String model) {
        if (sheets == null) {
            List<String> lines = lines();
            sheets = lines == null ? Map.of() : parse(lines, SOURCE);
        }
        CarSheet sheet = sheets.get(model.toLowerCase(Locale.ROOT));
        return sheet != null ? sheet : CarSheet.DEFAULT;
    }

    /** A broken exhaust's pitches are at least this far apart from car to car: a third of a semitone. */
    static final float RASP_APART = 0.02F;

    private static Map<String, Float> rasps;

    /**
     * The pitch a car with sheet {@code sheet} blats at once its exhaust is
     * broken: by how its engine revs and its cylinders (a truck's low, a
     * screamer's high) and a little by its name, and then every car with a
     * sheet set at least {@link #RASP_APART} from the next, so no two sound
     * the same.
     */
    static synchronized float raspPitch(CarSheet sheet) {
        if (rasps == null) {
            of(""); // reads the sheets
            List<CarSheet> all = new java.util.ArrayList<>(sheets.values());
            all.sort(java.util.Comparator.comparingDouble((CarSheet c) -> rawRasp(c)).thenComparing(c -> c.car));
            Map<String, Float> out = new java.util.HashMap<>();
            float last = -1F;
            for (CarSheet c : all) {
                last = Math.max(rawRasp(c), last + RASP_APART);
                out.put(c.car, last);
            }
            rasps = out;
        }
        Float known = rasps.get(sheet.car);
        return known != null ? known : rawRasp(sheet);
    }

    private static float rawRasp(CarSheet sheet) {
        int cylinders = sheet.cylinders();
        float own = ((sheet.car.hashCode() * 0x9E3779B1) >>> 24) / 255F - 0.5F;
        return 0.62F + 0.11F * sheet.number("revs", 4F) + (cylinders <= 4 ? 0.12F : cylinders >= 10 ? 0.1F : 0F)
                + 0.1F * own;
    }

    /** The resource's lines, or null (logged) if it is missing or cannot be read. */
    static List<String> lines() {
        try (InputStream in = CarSheets.class.getResourceAsStream(RESOURCE)) {
            if (in == null) {
                HLogger.warn("[extras] realism: no " + RESOURCE + " next to the extra's classes; every car gets"
                        + " the default sheet");
                return null;
            }
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {
                return reader.lines().toList();
            }
        } catch (IOException | UncheckedIOException e) {
            HLogger.warn("[extras] realism: cannot read " + RESOURCE + ": " + e);
            return null;
        }
    }

    /** True if the sheets in {@code lines} say the owner has looked them over ({@code # Looked over by the owner: yes}). */
    static boolean lookedOver(List<String> lines) {
        for (String line : lines) {
            String t = line.trim().toLowerCase(Locale.ROOT);
            if (t.startsWith("#") && t.contains("looked over by the owner:")) {
                return t.substring(t.indexOf(':') + 1).trim().startsWith("yes");
            }
        }
        return false;
    }

    /** Every sheet in {@code lines} (from {@code where}, for the log). */
    static Map<String, CarSheet> parse(List<String> lines, String where) {
        Map<String, CarSheet> out = new HashMap<>();
        for (int n = 0; n < lines.size(); n++) {
            String line = lines.get(n).trim();
            if (line.isEmpty() || line.startsWith("#")) {
                continue;
            }
            CarSheet sheet = parseLine(line);
            if (sheet == null) {
                HLogger.warn("[extras] realism: " + where + " line " + (n + 1) + " is not a car sheet: " + line);
            } else {
                out.put(sheet.car, sheet);
            }
        }
        return out;
    }

    /** One line: car, engine, sits, cooling, turbo, brakes, exhausts, then any {@code name=value} traits; null if it is not one. */
    public static CarSheet parseLine(String line) {
        String[] t = line.toLowerCase(Locale.ROOT).split("\\s+");
        if (t.length < 7) {
            return null;
        }
        int sits = indexOf(t[2], "front", "mid", "rear");
        int cooling = indexOf(t[3], "front", "sides", "air");
        int turbo = indexOf(t[4], "no", "yes");
        int brakes = indexOf(t[5], "drum", "disc", "carbon");
        int exhausts = t[6].equals("stacks") ? CarSheet.STACKS : t[6].equals("1") ? 1 : t[6].equals("2") ? 2 : -1;
        if (sits < 0 || cooling < 0 || turbo < 0 || brakes < 0 || exhausts < 0) {
            return null;
        }
        Map<String, String> traits = new HashMap<>();
        for (int k = 7; k < t.length; k++) {
            int eq = t[k].indexOf('=');
            if (eq <= 0 || eq == t[k].length() - 1) {
                return null;
            }
            traits.put(t[k].substring(0, eq), t[k].substring(eq + 1));
        }
        return new CarSheet(t[0], line.trim().split("\\s+")[1], sits, cooling, turbo == 1, brakes, exhausts,
                Map.copyOf(traits));
    }

    private static int indexOf(String word, String... words) {
        for (int i = 0; i < words.length; i++) {
            if (words[i].equals(word)) {
                return i;
            }
        }
        return -1;
    }
}
