package nfm.lit;

import fallk.logmaster.HLogger;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * The game's data files, with mods laid over them.
 * <p>
 * A mod is a folder in {@code mods/} that mirrors {@code data/}: each
 * archive is a folder of the same name ({@code models/} for
 * {@code models.radq}, {@code images/}, {@code sounds/}), and loose files
 * keep their paths ({@code stages/nfm2/3.txt}, {@code music/nfm2/stage3.ogg}).
 * A file in a mod replaces the game's file of the same name; files the game
 * has no use for are reported and left alone. Mods apply in the order
 * {@code mods/load-order.txt} lists them, one folder name per line, a later
 * one winning where two replace the same file; a name with a {@code -} in
 * front is turned off, {@code #} starts a comment, and folders it does not
 * list come after it in alphabetical order.
 * <p>
 * Hidden test runs use the game's own files only, unless
 * {@code -Dnfm.mods=on}; {@code -Dnfm.mods=off} turns mods off for a run.
 */
final class GameFiles {

    static final File MODS = new File("mods");
    static final String ORDER_FILE = "load-order.txt";

    /** One file of an archive, as the game reads it. */
    record Entry(String name, byte[] data) {
    }

    private static volatile boolean enabled = true;
    private static List<File> active;
    private static List<String> disabled = List.of();

    private GameFiles() {
    }

    /** Whether mods apply at all: off for hidden runs unless asked. Call before the game loads its data. */
    static void setUp(boolean hiddenRun) {
        String property = System.getProperty("nfm.mods");
        enabled = property == null ? !hiddenRun : !("off".equalsIgnoreCase(property.trim())
                || "false".equalsIgnoreCase(property.trim()));
        rescan();
    }

    /** Reads the mods folder and the load order again. */
    static synchronized void rescan() {
        List<File> mods = new ArrayList<>();
        List<String> off = new ArrayList<>();
        if (enabled && MODS.isDirectory()) {
            File[] folders = MODS.listFiles(File::isDirectory);
            Map<String, File> byName = new LinkedHashMap<>();
            if (folders != null) {
                Arrays.sort(folders, (a, b) -> a.getName().compareToIgnoreCase(b.getName()));
                for (File folder : folders) {
                    byName.put(folder.getName().toLowerCase(Locale.ROOT), folder);
                }
            }
            for (String line : orderLines()) {
                boolean turnedOff = line.startsWith("-");
                String name = (turnedOff ? line.substring(1) : line).trim();
                File folder = byName.remove(name.toLowerCase(Locale.ROOT));
                if (folder == null) {
                    HLogger.warn("mods/" + ORDER_FILE + " lists " + name + ", which is not a folder in mods");
                } else if (turnedOff) {
                    off.add(folder.getName());
                } else {
                    mods.add(folder);
                }
            }
            mods.addAll(byName.values());
        }
        active = Collections.unmodifiableList(mods);
        disabled = Collections.unmodifiableList(off);
        if (!mods.isEmpty()) {
            HLogger.info("Mods, in load order: " + names());
        }
    }

    private static List<String> orderLines() {
        File order = new File(MODS, ORDER_FILE);
        List<String> lines = new ArrayList<>();
        if (!order.isFile()) {
            return lines;
        }
        try {
            for (String line : Files.readAllLines(order.toPath(), StandardCharsets.UTF_8)) {
                int comment = line.indexOf('#');
                String t = (comment >= 0 ? line.substring(0, comment) : line).trim();
                if (!t.isEmpty()) {
                    lines.add(t);
                }
            }
        } catch (IOException ex) {
            HLogger.warn("Cannot read mods/" + ORDER_FILE + ": " + ex);
        }
        return lines;
    }

    private static synchronized List<File> mods() {
        if (active == null) {
            rescan();
        }
        return active;
    }

    /** The active mods' names, in load order, comma separated; "" for none. */
    static String names() {
        List<String> names = new ArrayList<>();
        for (File mod : mods()) {
            names.add(mod.getName());
        }
        return String.join(", ", names);
    }

    /** The mods in words, for the console. */
    static String describe() {
        if (!enabled) {
            return "Mods are off for this run.";
        }
        List<File> mods = mods();
        StringBuilder b = new StringBuilder();
        if (mods.isEmpty()) {
            b.append("No mods in ").append(MODS.getAbsolutePath()).append('.');
        } else {
            b.append("Mods, in load order (a later one wins): ").append(names()).append('.');
        }
        if (!disabled.isEmpty()) {
            b.append(" Turned off in ").append(ORDER_FILE).append(": ").append(String.join(", ", disabled)).append('.');
        }
        return b.toString();
    }

    /**
     * The files of the archive {@code data/<name>.radq}, in the archive's
     * order, each replaced by the last mod that has a file of its name in a
     * {@code <name>} folder.
     */
    static List<Entry> archive(String name) throws IOException {
        List<Entry> entries = new ArrayList<>();
        try (ZipInputStream zip = new ZipInputStream(new FileInputStream(new File("data", name + ".radq")))) {
            for (ZipEntry entry = zip.getNextEntry(); entry != null; entry = zip.getNextEntry()) {
                entries.add(new Entry(entry.getName(), zip.readAllBytes()));
            }
        }
        for (File mod : mods()) {
            File folder = new File(mod, name);
            File[] files = folder.listFiles(File::isFile);
            if (files == null) {
                continue;
            }
            for (File file : files) {
                int at = indexOf(entries, file.getName());
                if (at < 0) {
                    HLogger.warn("Mod " + mod.getName() + ": " + name + "/" + file.getName() + " is not in " + name
                            + ".radq, so the game does not read it");
                    continue;
                }
                try (InputStream in = new FileInputStream(file)) {
                    entries.set(at, new Entry(entries.get(at).name(), in.readAllBytes()));
                }
                HLogger.info("Mod " + mod.getName() + " replaces " + name + "/" + entries.get(at).name());
            }
        }
        return entries;
    }

    private static int indexOf(List<Entry> entries, String fileName) {
        for (int i = 0; i < entries.size(); i++) {
            if (entries.get(i).name().equalsIgnoreCase(fileName)) {
                return i;
            }
        }
        return -1;
    }

    /**
     * The file at {@code path} (under {@code data/}), or the last mod's file
     * at the same place under the mod's folder.
     */
    static File file(String path) {
        String inside = insideData(path);
        if (inside != null) {
            List<File> mods = mods();
            for (int i = mods.size() - 1; i >= 0; i--) {
                File file = new File(mods.get(i), inside);
                if (file.isFile()) {
                    return file;
                }
            }
        }
        return new File(path);
    }

    /**
     * The first of {@code base} + each extension that exists, the last mod
     * that has one winning over the game's own whatever the extension (so a
     * mod's .ogg replaces the game's .radq); null if none does.
     */
    static String find(String base, String... extensions) {
        String inside = insideData(base);
        if (inside != null) {
            List<File> mods = mods();
            for (int i = mods.size() - 1; i >= 0; i--) {
                for (String extension : extensions) {
                    File file = new File(mods.get(i), inside + extension);
                    if (file.isFile()) {
                        return file.getPath();
                    }
                }
            }
        }
        for (String extension : extensions) {
            if (new File(base + extension).isFile()) {
                return base + extension;
            }
        }
        return null;
    }

    /** {@code path} relative to data/, with / between folders; null if it is not under data/. */
    private static String insideData(String path) {
        String p = path.replace('\\', '/');
        if (p.startsWith("./")) {
            p = p.substring(2);
        }
        return p.regionMatches(true, 0, "data/", 0, 5) ? p.substring(5) : null;
    }
}
