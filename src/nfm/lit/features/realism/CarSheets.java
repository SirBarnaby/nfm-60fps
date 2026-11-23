package nfm.lit.features.realism;

import fallk.logmaster.HLogger;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Function;

/**
 * The cars' sheets, read from {@code data\extras\car-sheets.txt} through the
 * game's files (so a mod's own file wins). Read when a race starts, again
 * only if the file changed. A line that cannot be read is logged and left
 * out; a car with no line gets {@link CarSheet#DEFAULT}.
 */
final class CarSheets {

    static final String PATH = "data/extras/car-sheets.txt";

    private static String readPath;
    private static long readStamp = Long.MIN_VALUE;
    private static Map<String, CarSheet> sheets = Map.of();

    private CarSheets() {
    }

    /** The sheet of car model {@code model} (its file name, such as {@code radicalone}). */
    static synchronized CarSheet of(Function<String, File> files, String model) {
        File file = files.apply(PATH);
        long stamp = file.isFile() ? file.lastModified() : -1L;
        if (!file.getPath().equals(readPath) || stamp != readStamp) {
            readPath = file.getPath();
            readStamp = stamp;
            sheets = read(file);
        }
        CarSheet sheet = sheets.get(model.toLowerCase(Locale.ROOT));
        return sheet != null ? sheet : CarSheet.DEFAULT;
    }

    /** Every sheet in {@code file}, by car. */
    static Map<String, CarSheet> read(File file) {
        if (!file.isFile()) {
            HLogger.warn("[extras] realism: no car sheets at " + file + "; every car gets the default sheet");
            return Map.of();
        }
        try {
            return parse(Files.readAllLines(file.toPath(), StandardCharsets.UTF_8), file.getPath());
        } catch (IOException e) {
            HLogger.warn("[extras] realism: cannot read the car sheets at " + file + ": " + e);
            return Map.of();
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

    /** One line: car, engine, sits, cooling, turbo, brakes, exhausts; null if it is not one. */
    static CarSheet parseLine(String line) {
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
        return new CarSheet(t[0], line.trim().split("\\s+")[1], sits, cooling, turbo == 1, brakes, exhausts);
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
