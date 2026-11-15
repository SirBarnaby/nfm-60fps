package nfm.lit;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.util.Locale;

/**
 * Golden screenshots: frames of recorded races, rendered while the verifier
 * plays them again and compared pixel by pixel with the frames saved before.
 * A recorded race replays exactly, so its frame after a given tick only
 * changes when drawing changes. That proves rendering work leaves the picture
 * as it was, and measures how far a new renderer is from the original.
 * <ul>
 * <li>{@code -Dnfm.verify.golden=<folder>} compares the frames with the ones
 * saved in the folder, and saves any that are not there yet.</li>
 * <li>{@code -Dnfm.verify.golden.update=true} saves every frame again, after a
 * change that is meant to alter the picture.</li>
 * <li>{@code -Dnfm.verify.golden.tolerance=<n>} lets each colour channel be off
 * by up to {@code n} before a pixel counts as changed (default 0).</li>
 * </ul>
 * A changed frame is written to the temp folder next to a picture of the
 * golden frame with every changed pixel marked.
 */
final class GoldenFrames {

    /** The first frame: just after the start, with the cars still close together. */
    private static final int FIRST_TICK = 40;
    /** Frames per race, spread evenly from {@link #FIRST_TICK} to the last tick. */
    private static final int FRAMES = 4;
    /** Changed pixels in the marked-up picture. */
    private static final int MARK = 0xFF00FF;

    private final File folder;
    private final boolean update;
    private final int tolerance;
    private int same;
    private int changed;
    private int saved;

    private GoldenFrames(File folder, boolean update, int tolerance) {
        this.folder = folder;
        this.update = update;
        this.tolerance = tolerance;
    }

    /** The check the command line asks for, or null for none. */
    static GoldenFrames fromCommandLine() {
        String path = System.getProperty("nfm.verify.golden");
        if (path == null) {
            return null;
        }
        return new GoldenFrames(new File(path), Boolean.getBoolean("nfm.verify.golden.update"),
                Integer.getInteger("nfm.verify.golden.tolerance", 0));
    }

    /** True if the frame after this tick of a race {@code raceTicks} long is a golden one. */
    static boolean isGoldenTick(int tick, int raceTicks) {
        int last = raceTicks - 1;
        if (last <= FIRST_TICK) {
            return tick == last;
        }
        for (int i = 0; i < FRAMES; i++) {
            if (tick == FIRST_TICK + (last - FIRST_TICK) * i / (FRAMES - 1)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Compares one frame with its golden counterpart, or saves it, and returns
     * a line for the report.
     */
    String check(String race, int tick, BufferedImage frame) {
        String name = String.format(Locale.ROOT, "tick-%04d", tick);
        File file = new File(new File(folder, race), name + ".png");
        if (update || !file.isFile()) {
            return save(file, frame, name);
        }
        BufferedImage golden;
        try {
            golden = ImageIO.read(file);
        } catch (IOException ex) {
            golden = null;
        }
        if (golden == null) {
            changed++;
            return name + ": the golden frame " + file + " cannot be read";
        }
        if (golden.getWidth() != frame.getWidth() || golden.getHeight() != frame.getHeight()) {
            changed++;
            return name + ": the frame is " + frame.getWidth() + "x" + frame.getHeight() + ", the golden frame "
                    + golden.getWidth() + "x" + golden.getHeight();
        }
        return compare(race, name, golden, frame);
    }

    private String save(File file, BufferedImage frame, String name) {
        File dir = file.getParentFile();
        try {
            if (!dir.isDirectory() && !dir.mkdirs()) {
                throw new IOException("cannot create " + dir);
            }
            ImageIO.write(frame, "png", file);
        } catch (IOException ex) {
            changed++;
            return name + ": could not save the golden frame: " + ex.getMessage();
        }
        saved++;
        return name + ": saved as the golden frame";
    }

    private String compare(String race, String name, BufferedImage golden, BufferedImage frame) {
        int width = frame.getWidth();
        int height = frame.getHeight();
        int[] want = golden.getRGB(0, 0, width, height, null, 0, width);
        int[] got = frame.getRGB(0, 0, width, height, null, 0, width);
        int count = 0;
        int worst = 0;
        int minX = width;
        int minY = height;
        int maxX = -1;
        int maxY = -1;
        for (int i = 0; i < got.length; i++) {
            int delta = channelDelta(want[i], got[i]);
            if (delta > tolerance) {
                count++;
                worst = Math.max(worst, delta);
                int x = i % width;
                int y = i / width;
                minX = Math.min(minX, x);
                maxX = Math.max(maxX, x);
                minY = Math.min(minY, y);
                maxY = Math.max(maxY, y);
            }
        }
        if (count == 0) {
            same++;
            return name + ": same";
        }
        changed++;
        String where = writeDifference(race + "-" + name, frame, want, got, width, height);
        return String.format(Locale.ROOT,
                "%s: CHANGED: %d pixels differ (%.2f%%), by up to %d per channel, within x %d-%d, y %d-%d%s",
                name, count, 100D * count / got.length, worst, minX, maxX, minY, maxY, where);
    }

    /** The largest difference between two pixels in any one colour channel. */
    private static int channelDelta(int a, int b) {
        int r = Math.abs((a >> 16 & 0xFF) - (b >> 16 & 0xFF));
        int g = Math.abs((a >> 8 & 0xFF) - (b >> 8 & 0xFF));
        int bl = Math.abs((a & 0xFF) - (b & 0xFF));
        return Math.max(r, Math.max(g, bl));
    }

    /**
     * Writes the new frame, and the golden frame dimmed with every changed
     * pixel marked, to the temp folder. Returns where, for the report.
     */
    private String writeDifference(String stem, BufferedImage frame, int[] want, int[] got, int width,
                                   int height) {
        int[] marked = new int[want.length];
        for (int i = 0; i < want.length; i++) {
            marked[i] = channelDelta(want[i], got[i]) > tolerance ? MARK : (want[i] >> 2) & 0x3F3F3F;
        }
        BufferedImage diff = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        diff.setRGB(0, 0, width, height, marked, 0, width);
        File tmp = new File(System.getProperty("java.io.tmpdir"));
        File actual = new File(tmp, stem + "-new.png");
        File marks = new File(tmp, stem + "-changes.png");
        try {
            ImageIO.write(frame, "png", actual);
            ImageIO.write(diff, "png", marks);
            return "; see " + marks;
        } catch (IOException ex) {
            return "";
        }
    }

    boolean anyChanged() {
        return changed > 0;
    }

    String summary() {
        return "golden frames: " + same + " same, " + changed + " changed, " + saved + " saved";
    }
}
