package nfm.lit;

import java.awt.BasicStroke;
import java.awt.Dimension;
import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.Image;
import java.awt.LinearGradientPaint;
import java.awt.RenderingHints;
import java.awt.Shape;
import java.awt.Stroke;
import java.awt.font.TextAttribute;
import java.awt.geom.AffineTransform;
import java.awt.geom.Rectangle2D;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.function.BooleanSupplier;
import java.util.function.IntConsumer;
import java.util.function.IntFunction;
import java.util.function.IntSupplier;
import java.util.function.Supplier;

import nfm.lit.audio.Sound3D;
import nfm.lit.audio.StreamMusic;
import nfm.lit.gpu.GpuDisplay;

/**
 * The settings screen, reached from the main menu and the pause menu: every
 * setting the developer console has, in the game's own menu look. The box is
 * the pause box's frame around the main menu's brown, the lettering is the
 * menus' own (Adventure), and the keys are the menus' keys: up and down
 * choose, left and right change, Enter changes or goes back, Escape goes
 * back. Each change is saved at once, by the same setters the console uses.
 */
final class SettingsScreen {

    /* the box, in the 1280x720 picture */
    private static final int BOX_W = 900;
    private static final int BOX_H = 620;
    private static final int BOX_X = (GameFacts.screenWidth - BOX_W) / 2;
    private static final int BOX_Y = (GameFacts.screenHeight - BOX_H) / 2;
    private static final int COLUMN_W = 400;
    private static final int[] COLUMN_X = {BOX_X + 35, BOX_X + BOX_W - 35 - COLUMN_W};
    private static final int ROW_H = 25;
    private static final int SECTION_GAP = 44;
    private static final int VALUE_W = 170;
    private static final int BACK_Y = BOX_Y + BOX_H - 62;

    /* the menus' colours */
    private static final Color BROWN = new Color(0x3a, 0x1e, 0x08, 238);
    private static final Color[] FRAME = {new Color(0x311808), new Color(0xde8439), Color.WHITE,
            new Color(0xefb584), new Color(0xde8439), Color.BLACK};
    private static final Color LETTERS = new Color(0xffe7c1);
    private static final Color DIMMED = new Color(0x8a6c50);
    private static final Color ARROWS = new Color(0xde8439);
    private static final Color HELP = new Color(0xefb584);
    private static final Color SHADED = new Color(140, 70, 0);
    private static final Color FLASH_A = new Color(200, 255, 0);
    private static final Color FLASH_B = new Color(255, 128, 0);
    private static final Color[] PAUSE_LETTERS = {Color.WHITE, Color.WHITE, new Color(0x94c0ef), new Color(0x428cde)};
    private static final Color[] PAUSE_TITLE = {new Color(0xb2d2f4), new Color(0x94c0ef), new Color(0x7eb1ec),
            new Color(0x428cde)};
    private static final float[] GRADIENT_STOPS = {0F, 0.5F, 0.8F, 1F};
    private static final Color PAUSE_EDGE = new Color(0x0c1c3c);

    /** A key that went down which only this screen tells apart from Enter. */
    private static volatile boolean escapePressed;
    /** A size is being typed: every key goes to it (see {@link #typeKey}). */
    private static volatile boolean typing;
    private static final ConcurrentLinkedQueue<Integer> TYPED = new ConcurrentLinkedQueue<>();

    private final Image[] back;
    private final List<Row> rows = new ArrayList<>();
    /** Where each row is drawn: column, and y. */
    private final int[] rowColumn;
    private final int[] rowY;
    private final List<Section> sections = new ArrayList<>();
    /** Lettering already turned into shapes; game thread only. */
    private static final Map<String, Shape> SHAPES = new HashMap<>();
    private final Font font;
    private final Font smallFont;
    private final Font titleFont;

    /** The selected row, or rows.size() for Back. */
    private int selected;
    private boolean flash;
    private boolean pressed;
    private int lastMouseX = -1;
    private int lastMouseY = -1;
    private int backPressed;

    SettingsScreen(Image[] back) {
        this.back = back;
        Map<TextAttribute, Object> letters = Map.of(TextAttribute.TRACKING, 0.06F);
        font = new Font("Adventure", Font.PLAIN, 18).deriveFont(letters);
        smallFont = new Font("Adventure", Font.PLAIN, 15).deriveFont(letters);
        titleFont = new Font("Adventure", Font.PLAIN, 21).deriveFont(Map.of(TextAttribute.TRACKING, 0.04F));
        build();
        rowColumn = new int[rows.size()];
        rowY = new int[rows.size()];
        for (Section section : sections) {
            for (int i = 0; i < section.count; i++) {
                rowColumn[section.first + i] = section.column;
                rowY[section.first + i] = section.y + 14 + i * ROW_H;
            }
        }
    }

    /** Escape went down: on this screen it goes back, where elsewhere it is Enter. */
    static void noteEscape() {
        escapePressed = true;
    }

    /**
     * A key went down (on the window's thread): while a size is being typed
     * it is the screen's, and the game does not see it.
     *
     * @return true if the key was taken
     */
    static boolean typeKey(int key) {
        if (!typing || key == GameInput.F11) {
            return false;
        }
        TYPED.add(key);
        return true;
    }

    /** Called as the screen opens. */
    void open() {
        selected = 0;
        pressed = false;
        backPressed = 0;
        escapePressed = false;
        windowSize.stopTyping();
    }

    /** For the smoke test: chooses the row with this label. */
    void select(String label) {
        for (int i = 0; i < rows.size(); i++) {
            if (rows.get(i).label.equals(label)) {
                selected = i;
            }
        }
    }

    /* ------------------------------------------------------------- the rows */

    private void build() {
        int[] fpsLimits = {GameSparker.FPS_MATCH_DISPLAY, GameSparker.FPS_VRR, 30, 60, 75, 90, 120, 144, 165, 240,
                360, GameSparker.FPS_UNLIMITED};
        BooleanSupplier gpu = GameSparker::isGpuRenderer;
        BooleanSupplier hdr = () -> GameSparker.isGpuRenderer() && GameSparker.getHdr() != GpuDisplay.HDR_OFF;
        BooleanSupplier sound3d = GameSparker::isNewSound;
        String gpuOnly = "Only when the graphics card draws (Renderer).";
        String sound3dOnly = "Only with the 3D sound.";

        BooleanSupplier windowed = () -> !RunApp.isFullscreen();
        String windowOnly = "Only in a window: in fullscreen the game fills the monitor.";

        section("Picture", 0);
        rows.add(new Choice("Renderer",
                "Graphics card: the GPU draws, with the smoothing below. Software: the game's own drawing.",
                () -> GameSparker.describeRenderer().contains("not available")
                        ? "The graphics card cannot draw here, so the game draws in software." : null,
                new int[] {1, 0}, v -> v == 1 ? "Graphics card" : "Software",
                () -> GameSparker.isGpuRenderer() ? 1 : 0, v -> GameSparker.setGpuRenderer(v == 1)));
        rows.add(new Choice("Fullscreen", "Borderless fullscreen on this monitor. F11 or Alt+Enter switch it too.",
                null, new int[] {0, 1}, SettingsScreen::onOff, () -> RunApp.isFullscreen() ? 1 : 0,
                v -> RunApp.setFullscreen(v == 1)));
        rows.add(new Choice("Aspect ratio",
                "The window's shape. The race and the menus are drawn 16:9, so other shapes show them with bars.",
                () -> "Now: " + describeWindow() + ".", aspectChoices(), SettingsScreen::aspectName,
                SettingsScreen::currentAspect, SettingsScreen::setAspect).when(windowed, windowOnly));
        rows.add(windowSize.when(windowed, windowOnly));
        rows.add(new Choice("Frame rate",
                "Frames a second. Variable refresh stays just under the monitor's rate, for G-Sync and FreeSync.",
                null, fpsLimits, SettingsScreen::fpsName, GameSparker::getFpsLimit, GameSparker::setFpsLimit));
        rows.add(new Choice("Laptop battery FPS",
                "Holds the frame rate down while the laptop runs on its battery, to make it last.",
                () -> "Now: " + GameSparker.describeBatteryFps() + ".",
                new int[] {0, 30, 40, 60, 90, 120}, v -> v == 0 ? "No cap" : v + " fps",
                GameSparker::getBatteryFps, GameSparker::setBatteryFps));
        rows.add(new Choice("Fill the window",
                "Scales the picture to the window, keeping its shape. Off shows it pixel for pixel.",
                null, new int[] {0, 1}, SettingsScreen::onOff, () -> GameSparker.isScaleToWindow() ? 1 : 0,
                v -> GameSparker.setScaleToWindow(v == 1)));
        rows.add(new Choice("MSAA",
                "Multisampling: smooths the edges of the cars and the track. Off keeps the game's hard edges.",
                () -> "Now: " + GameSparker.describeMultisample() + ".",
                new int[] {1, 2, 4, 8, 16}, v -> v <= 1 ? "Off" : v + "x",
                GameSparker::getMultisample, GameSparker::setMultisample).when(gpu, gpuOnly));
        rows.add(new Choice("Supersampling",
                "Draws races with 2, 4 or 8 times the pixels and averages them down. 4x on a 1080p screen is 4K.",
                () -> "Now: " + GameSparker.describeSupersample() + ".",
                new int[] {1, 2, 4, 8}, v -> v <= 1 ? "Off" : v + "x",
                GameSparker::getSupersample, GameSparker::setSupersample).when(gpu, gpuOnly));
        rows.add(new Choice("Dynamic resolution",
                "Draws races smaller while the graphics card cannot keep up, to hold the frame rate.",
                () -> "Now: " + GameSparker.describeDynamicResolution() + ".",
                new int[] {0, 90, 75, 50, 25}, v -> v == 0 ? "Off" : "Down to " + v + "%",
                GameSparker::getDynamicResolution, GameSparker::setDynamicResolution).when(gpu, gpuOnly));
        rows.add(new Choice("Smooth particles",
                "Dust and flying debris move every frame. Off: once a game tick, as the original drew them.",
                null, new int[] {0, 1}, SettingsScreen::onOff, () -> GameSparker.isSmoothEffects() ? 1 : 0,
                v -> GameSparker.setSmoothEffects(v == 1)));

        section("HDR", 0);
        rows.add(new Choice("HDR", "Brighter highlights on an HDR monitor, when Windows has HDR on.",
                () -> "Now: " + GameSparker.describeHdr() + ".",
                new int[] {GpuDisplay.HDR_OFF, GpuDisplay.HDR_AUTO},
                v -> v == GpuDisplay.HDR_OFF ? "Off" : v == GpuDisplay.HDR_FORCE ? "Forced" : "Auto",
                GameSparker::getHdr, GameSparker::setHdr).when(gpu, gpuOnly));
        rows.add(new Choice("Paper white",
                "How bright the game's white is in HDR. Windows: as its SDR content brightness slider.", null,
                new int[] {0, 80, 100, 120, 160, 200, 240, 280, 320, 400, 500},
                v -> v == 0 ? "Windows" : v + " nits", GameSparker::getPaperWhite, GameSparker::setPaperWhite)
                .when(hdr, "Only with HDR on."));
        rows.add(new Choice("Peak brightness", "The brightest the monitor can show. Monitor: what it tells Windows.", null,
                new int[] {0, 400, 600, 800, 1000, 1400, 2000, 4000, 10000},
                v -> v == 0 ? "Monitor" : v + " nits", GameSparker::getPeak, GameSparker::setPeak)
                .when(hdr, "Only with HDR on."));

        section("Sound", 1);
        rows.add(new Choice("Sound system",
                "Steam Audio: sounds come from where they are, mixed and spread. Classic: as the game always sounded.",
                () -> "Now: " + GameSparker.describeSound() + ".",
                new int[] {1, 0}, v -> v == 1 ? "Steam Audio" : "Classic",
                () -> GameSparker.isNewSound() ? 1 : 0, v -> GameSparker.setNewSound(v == 1)));
        rows.add(new Choice("Output", "Mono plays everything on both speakers, for one ear or one speaker.", null,
                new int[] {0, 1}, v -> v == 1 ? "Mono" : "Stereo", () -> Sound3D.isMono() ? 1 : 0,
                v -> GameSparker.setMono(v == 1)).when(sound3d, sound3dOnly));
        String[] busLabels = {"Master volume", "Engines", "Effects", "Music", "Interface"};
        String[] busHelp = {"Everything the game plays.", "The cars' engines and skids.",
                "Crashes, scrapes, power-ups and the rest of the race.", "The stage's music.",
                "The menus' and the countdown's sounds."};
        for (int b = 0; b < busLabels.length; b++) {
            final int bus = b;
            rows.add(new Slider(busLabels[b], busHelp[b], 0, 100, 5, v -> v + "%",
                    () -> Math.round(Sound3D.busLevel(bus) * 100F), v -> GameSparker.setVolume(bus, v))
                    .when(sound3d, sound3dOnly));
        }
        rows.add(new Slider("Music width",
                "How far apart the music's left and right are: 0 is mono, " + StreamMusic.DEFAULT_SEPARATION
                        + " the game's own, 200 the widest.",
                0, 200, 10, v -> v + "%", StreamMusic::separation, GameSparker::setMusicSeparation)
                .when(() -> GameSparker.isNewSound() && StreamMusic.available(), sound3dOnly));

        section("Controls", 1);
        rows.add(new Choice("Game controllers", "Plays with an Xbox or other game controller.",
                GameSparker::describeGamepads, new int[] {0, 1}, SettingsScreen::onOff,
                () -> Gamepads.isOn() ? 1 : 0, v -> GameSparker.setGamepad(v == 1)));
        rows.add(new Choice("Shaking on hits", "The controller shakes when the car hits hard.", null,
                new int[] {0, 1}, SettingsScreen::onOff, () -> Gamepads.isRumble() ? 1 : 0,
                v -> GameSparker.setRumble(v == 1)).when(Gamepads::isOn, "Only with game controllers on."));
        rows.add(new Choice("Run-ahead",
                "Shows the race a tick ahead with the keys held now: 53 ms less lag between key and screen.",
                null, new int[] {0, 1}, SettingsScreen::onOff, () -> GameSparker.isRunAhead() ? 1 : 0,
                v -> GameSparker.setRunAhead(v == 1)));
        sectionEnd();
    }

    private static String onOff(int v) {
        return v == 1 ? "On" : "Off";
    }

    private static String fpsName(int limit) {
        if (limit == GameSparker.FPS_MATCH_DISPLAY) {
            return "Match display";
        }
        if (limit == GameSparker.FPS_VRR) {
            return "Variable refresh";
        }
        return limit == GameSparker.FPS_UNLIMITED ? "Unlimited" : limit + " fps";
    }

    /* ------------------------------------------------------- window size */

    /** The window shapes on offer: width and height of each. */
    private static final int[][] ASPECTS = {{16, 9}, {16, 10}, {4, 3}, {5, 4}, {21, 9}, {32, 9}};
    /** The common sizes of each shape, smallest first. */
    private static final int[][][] SIZES = {
        {{1280, 720}, {1366, 768}, {1600, 900}, {1920, 1080}, {2560, 1440}, {3200, 1800}, {3840, 2160},
            {5120, 2880}, {7680, 4320}},
        {{1280, 800}, {1440, 900}, {1680, 1050}, {1920, 1200}, {2560, 1600}, {2880, 1800}, {3840, 2400}},
        {{1024, 768}, {1280, 960}, {1400, 1050}, {1600, 1200}, {2048, 1536}, {2880, 2160}},
        {{1280, 1024}, {1800, 1440}, {2560, 2048}},
        {{2560, 1080}, {3440, 1440}, {3840, 1600}, {5120, 2160}},
        {{3840, 1080}, {5120, 1440}, {7680, 2160}},
    };
    /** "Other" for the shape of a window sized by hand. */
    private static final int OTHER_ASPECT = -1;

    private final WindowSize windowSize = new WindowSize();

    private static int[] aspectChoices() {
        int[] choices = new int[ASPECTS.length];
        for (int i = 0; i < choices.length; i++) {
            choices[i] = i;
        }
        return choices;
    }

    private static String aspectName(int aspect) {
        return aspect == OTHER_ASPECT ? "Other" : ASPECTS[aspect][0] + ":" + ASPECTS[aspect][1];
    }

    /** The shape the window has, within a hundredth, or OTHER_ASPECT. */
    private static int aspectOf(int width, int height) {
        for (int i = 0; i < ASPECTS.length; i++) {
            float ratio = ASPECTS[i][0] / (float) ASPECTS[i][1];
            if (Math.abs(width / (float) height - ratio) < ratio * 0.01F) {
                return i;
            }
        }
        return OTHER_ASPECT;
    }

    private static int currentAspect() {
        Dimension now = RunApp.windowPixels();
        return aspectOf(now.width, now.height);
    }

    /** The common sizes of a shape that fit on the monitor, or the largest of that shape that does. */
    private static List<int[]> sizesFor(int aspect) {
        Dimension screen = RunApp.screenPixels();
        List<int[]> sizes = new ArrayList<>();
        for (int[] size : SIZES[aspect]) {
            if (size[0] <= screen.width && size[1] <= screen.height) {
                sizes.add(size);
            }
        }
        if (sizes.isEmpty()) {
            int w = ASPECTS[aspect][0];
            int h = ASPECTS[aspect][1];
            int width = screen.width;
            int height = Math.round(width * h / (float) w);
            if (height > screen.height) {
                height = screen.height;
                width = Math.round(height * w / (float) h);
            }
            sizes.add(new int[] {width, height});
        }
        return sizes;
    }

    /** Changes the window's shape, keeping its height as near as the shape's sizes allow. */
    private static void setAspect(int aspect) {
        Dimension now = RunApp.windowPixels();
        int[] best = null;
        for (int[] size : sizesFor(aspect)) {
            if (best == null || Math.abs(size[1] - now.height) < Math.abs(best[1] - now.height)) {
                best = size;
            }
        }
        RunApp.setWindowPixels(best[0], best[1]);
    }

    private static String describeWindow() {
        Dimension now = RunApp.windowPixels();
        Dimension screen = RunApp.screenPixels();
        return now.width + "x" + now.height + " window, on a " + screen.width + "x" + screen.height + " monitor";
    }

    private Section open;

    private void section(String title, int column) {
        sectionEnd();
        int y = BOX_Y + 70;
        for (Section s : sections) {
            if (s.column == column) {
                y = s.y + 14 + s.count * ROW_H + SECTION_GAP - 14;
            }
        }
        open = new Section(title, column, y, rows.size());
        sections.add(open);
    }

    private void sectionEnd() {
        if (open != null) {
            open.count = rows.size() - open.first;
            open = null;
        }
    }

    /* ---------------------------------------------------------- each frame */

    /**
     * Draws the screen over whatever is behind it and handles the keys.
     *
     * @return true when the player goes back
     */
    boolean frame(Graphics2D rd, Control control) {
        boolean leave = handleKeys(control);
        draw(rd);
        return leave;
    }

    private boolean handleKeys(Control control) {
        if (windowSize.typing()) {
            for (Integer key; (key = TYPED.poll()) != null;) {
                windowSize.type(key);
            }
            control.up = control.down = control.left = control.right = false;
            control.enter = control.handb = false;
            escapePressed = false;
            return false;
        }
        int count = rows.size() + 1;
        if (control.up) {
            selected = (selected + count - 1) % count;
            control.up = false;
        }
        if (control.down) {
            selected = (selected + 1) % count;
            control.down = false;
        }
        if (selected >= rows.size() || rows.get(selected) != windowSize) {
            windowSize.leave();
        }
        if (selected < rows.size()) {
            if (control.left) {
                rows.get(selected).change(-1, false);
                control.left = false;
            }
            if (control.right) {
                rows.get(selected).change(1, false);
                control.right = false;
            }
        } else {
            control.left = false;
            control.right = false;
        }
        boolean leave = false;
        if (escapePressed) {
            escapePressed = false;
            leave = true;
        } else if (control.enter || control.handb) {
            if (selected < rows.size() && rows.get(selected) == windowSize) {
                windowSize.enter();
            } else if (selected < rows.size()) {
                rows.get(selected).change(1, true);
            } else {
                leave = true;
            }
        }
        control.enter = false;
        control.handb = false;
        return leave;
    }

    /**
     * The mouse, in picture coordinates: hovering chooses a row, a click on
     * its arrows (or on a volume's bar) changes it, a click on Back goes back.
     * {@code state} is 1 on the tick a button went down.
     */
    void mouse(int x, int y, int state, Control control) {
        boolean moved = x != lastMouseX || y != lastMouseY;
        lastMouseX = x;
        lastMouseY = y;
        int row = rowAt(x, y);
        boolean overBack = overBack(x, y);
        if (moved && state == 0 && !windowSize.typing()) {
            if (row >= 0) {
                selected = row;
            } else if (overBack) {
                selected = rows.size();
            }
        }
        if (state == 1 && windowSize.typing()) {
            if (row >= 0 && rows.get(row) == windowSize) {
                return; // a click on the size being typed
            }
            windowSize.stopTyping();
        }
        if (moved && state == 0 && windowSize.typing()) {
            return; // the typing keeps its row
        }
        if (state == 1) {
            if (row >= 0) {
                selected = row;
                Row r = rows.get(row);
                int left = valueLeft(rowColumn[row]);
                if (r == windowSize && windowSize.custom() && x >= left + 16 && x <= left + VALUE_W - 16) {
                    windowSize.enter();
                } else if (r instanceof Slider slider && x >= left + 16 && x <= left + VALUE_W - 16) {
                    slider.setAt((x - (left + 20)) / (float) (VALUE_W - 40));
                } else if (x >= left - 4 && x < left + 16) {
                    r.change(-1, false);
                } else {
                    r.change(1, !(x >= left + VALUE_W - 16));
                }
                pressed = true;
            } else if (overBack) {
                selected = rows.size();
                backPressed = 1;
            }
        }
        if (state == 2) {
            pressed = false;
            if (backPressed == 1) {
                backPressed = 0;
                control.enter = true; // goes back on the next frame
            }
        }
    }

    private int rowAt(int x, int y) {
        for (int i = 0; i < rows.size(); i++) {
            int cx = COLUMN_X[rowColumn[i]];
            if (x >= cx && x < cx + COLUMN_W && y >= rowY[i] && y < rowY[i] + ROW_H - 2) {
                return i;
            }
        }
        return -1;
    }

    private boolean overBack(int x, int y) {
        Image b = back[0];
        if (b == null) {
            return false;
        }
        int bx = (GameFacts.screenWidth - b.getWidth(null)) / 2;
        return x > bx - 5 && x < bx + b.getWidth(null) + 5 && y > BACK_Y - 5 && y < BACK_Y + b.getHeight(null) + 5;
    }

    private static int valueLeft(int column) {
        return COLUMN_X[column] + COLUMN_W - VALUE_W - 6;
    }

    /* ------------------------------------------------------------- drawing */

    private void draw(Graphics2D rd) {
        Object antialias = rd.getRenderingHint(RenderingHints.KEY_ANTIALIASING);
        Object textAntialias = rd.getRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING);
        Stroke stroke = rd.getStroke();
        java.awt.Paint paint = rd.getPaint();
        drawBox(rd);
        rd.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        drawTitle(rd, "Settings", GameFacts.screenWidth / 2F, BOX_Y + 38);
        for (Section section : sections) {
            pauseLetters(rd, titleFont, section.title, COLUMN_X[section.column] + 8, section.y, false);
        }
        flash = !flash;
        for (int i = 0; i < rows.size(); i++) {
            drawRow(rd, i);
        }
        drawFooter(rd);
        rd.setPaint(paint);
        rd.setStroke(stroke);
        rd.setRenderingHint(RenderingHints.KEY_ANTIALIASING, antialias);
        rd.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, textAntialias);
    }

    /** The pause box's frame, around the main menu's brown. */
    private static void drawBox(Graphics2D rd) {
        rd.setColor(new Color(0, 0, 0, 90));
        rd.fillRect(BOX_X + 6, BOX_Y + 8, BOX_W, BOX_H);
        rd.setColor(BROWN);
        rd.fillRect(BOX_X + FRAME.length, BOX_Y + FRAME.length, BOX_W - 2 * FRAME.length, BOX_H - 2 * FRAME.length);
        for (int i = 0; i < FRAME.length; i++) {
            rd.setColor(FRAME[i]);
            rd.drawRect(BOX_X + i, BOX_Y + i, BOX_W - 1 - 2 * i, BOX_H - 1 - 2 * i);
        }
        // the line between the two columns
        rd.setColor(new Color(0xde, 0x84, 0x39, 90));
        int middle = GameFacts.screenWidth / 2;
        rd.drawLine(middle, BOX_Y + 66, middle, BOX_Y + 482);
    }

    private void drawRow(Graphics2D rd, int i) {
        Row row = rows.get(i);
        int cx = COLUMN_X[rowColumn[i]];
        int y = rowY[i];
        boolean enabled = row.enabled();
        if (i == selected) {
            highlight(rd, cx, y, COLUMN_W, ROW_H - 3);
        }
        Color letters = enabled ? LETTERS : DIMMED;
        menuLetters(rd, font, row.label, cx + 12, y + 17, letters, false);
        int left = valueLeft(rowColumn[i]);
        if (enabled) {
            menuLetters(rd, font, "<", left + 4, y + 17, ARROWS, false);
            menuLetters(rd, font, ">", left + VALUE_W - 4, y + 17, ARROWS, true);
        }
        if (row instanceof Slider slider) {
            int barX = left + 20;
            int barW = VALUE_W - 40;
            rd.setColor(new Color(0, 0, 0, 120));
            rd.fillRect(barX, y + 7, barW, 9);
            rd.setColor(enabled ? new Color(0xde8439) : new Color(0x6a4a2c));
            rd.fillRect(barX + 1, y + 8, Math.round((barW - 2) * slider.fraction()), 7);
            rd.setColor(enabled ? new Color(0xefb584) : new Color(0x7a5a3c));
            rd.drawRect(barX, y + 7, barW - 1, 8);
            menuLetters(rd, smallFont, row.value(), left - 6, y + 17, letters, true);
        } else {
            String value = row.value();
            Font f = shape(font, value).getBounds2D().getWidth() > VALUE_W - 40 ? smallFont : font;
            if (row == windowSize && windowSize.typing()) {
                rd.setColor(new Color(0, 0, 0, 120));
                rd.fillRect(left + 18, y + 2, VALUE_W - 36, ROW_H - 7);
                rd.setColor(ARROWS);
                rd.drawRect(left + 18, y + 2, VALUE_W - 37, ROW_H - 8);
                value = windowSize.typed(System.currentTimeMillis() / 400L % 2L == 0L);
                f = font;
                letters = Color.WHITE;
            }
            menuLetters(rd, f, value, left + VALUE_W / 2F, y + 17, letters, null);
        }
    }

    /** The main menu's selection: a blinking rounded frame, filled while the mouse holds it. */
    private void highlight(Graphics2D rd, int x, int y, int w, int h) {
        rd.setStroke(new BasicStroke(1F));
        if (pressed) {
            rd.setColor(SHADED);
            rd.fillRoundRect(x, y, w, h, 7, 20);
        }
        rd.setColor(flash ? FLASH_A : FLASH_B);
        rd.drawRoundRect(x, y, w, h, 7, 20);
    }

    private void drawFooter(Graphics2D rd) {
        int helpY = BOX_Y + BOX_H - 110;
        if (selected < rows.size()) {
            Row row = rows.get(selected);
            menuLetters(rd, smallFont, row.help(), GameFacts.screenWidth / 2F, helpY, HELP, null);
            String status = row.enabled() ? row.status() : row.disabledWhy;
            if (status != null) {
                menuLetters(rd, smallFont, status, GameFacts.screenWidth / 2F, helpY + 22, LETTERS, null);
            }
        } else {
            menuLetters(rd, smallFont, "Back to the " + (backTo == Phase.PAUSEMENU ? "pause menu." : "main menu."),
                    GameFacts.screenWidth / 2F, helpY, HELP, null);
        }
        Image b = back[selected == rows.size() && backPressed == 1 ? 1 : 0];
        if (b != null) {
            int bx = (GameFacts.screenWidth - b.getWidth(null)) / 2;
            if (selected == rows.size()) {
                highlight(rd, bx - 8, BACK_Y - 3, b.getWidth(null) + 16, b.getHeight(null) + 6);
            }
            rd.drawImage(b, bx, BACK_Y, null);
        }
        menuLetters(rd, smallFont, windowSize.typing()
                ? "Type the width, then x and the height. Enter sets it, Escape keeps the size as it is."
                : "Arrows choose and change a setting, Enter or Escape goes back.",
                GameFacts.screenWidth / 2F, BOX_Y + BOX_H - 14, DIMMED, null);
    }

    /** Where the screen goes back to; set by the menu that opens it. */
    Phase backTo = Phase.MAINMENU;

    /* ------------------------------------------------------------ lettering */

    /**
     * The main menu's lettering: Adventure in its cream, a touch bolder.
     * {@code alignRight}: true ends the text at x, false starts it there,
     * null centres it on x; y is where the letters stand.
     */
    private static void menuLetters(Graphics2D rd, Font f, String text, float x, float y, Color color,
            Boolean alignRight) {
        Shape s = place(shape(f, text), x, y, alignRight);
        rd.setColor(color);
        rd.fill(s);
        rd.setStroke(new BasicStroke(0.5F));
        rd.draw(s);
    }

    /** The pause menu's lettering: white running into blue, with a black edge. */
    private static void pauseLetters(Graphics2D rd, Font f, String text, float x, float y, boolean centred) {
        Shape s = place(shape(f, text), x, y, centred ? null : Boolean.FALSE);
        rd.setStroke(new BasicStroke(1.3F, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        rd.setColor(PAUSE_EDGE);
        rd.draw(s);
        fillDown(rd, s, PAUSE_LETTERS);
    }

    private static Font menuItemFont;
    private static Font pauseItemFont;

    /**
     * An item on the main menu, lettered as the menu's own picture is
     * (options.gif): centred on {@code cx}, standing on {@code y}.
     */
    static void drawMenuItem(Graphics2D rd, String text, float cx, float y) {
        if (menuItemFont == null) {
            menuItemFont = new Font("Adventure", Font.PLAIN, 20).deriveFont(Map.of(TextAttribute.TRACKING, 0.07F));
        }
        Object antialias = rd.getRenderingHint(RenderingHints.KEY_ANTIALIASING);
        Stroke stroke = rd.getStroke();
        rd.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        menuLetters(rd, menuItemFont, text, cx, y, LETTERS, null);
        rd.setStroke(stroke);
        rd.setRenderingHint(RenderingHints.KEY_ANTIALIASING, antialias);
    }

    /** An item on the pause menu, lettered as the pause box's picture is (paused.gif). */
    static void drawPauseItem(Graphics2D rd, String text, float cx, float y) {
        if (pauseItemFont == null) {
            pauseItemFont = new Font("Adventure", Font.PLAIN, 22).deriveFont(Map.of(TextAttribute.TRACKING, 0.08F));
        }
        Object antialias = rd.getRenderingHint(RenderingHints.KEY_ANTIALIASING);
        Stroke stroke = rd.getStroke();
        rd.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        java.awt.Paint paint = rd.getPaint();
        pauseLetters(rd, pauseItemFont, text, cx, y, true);
        rd.setPaint(paint);
        rd.setStroke(stroke);
        rd.setRenderingHint(RenderingHints.KEY_ANTIALIASING, antialias);
    }

    /** The pause box's "— Paused —" heading, here "— Settings —". */
    private void drawTitle(Graphics2D rd, String text, float cx, float y) {
        Shape s = place(shape(titleFont, text), cx, y, null);
        Rectangle2D b = s.getBounds2D();
        fillDown(rd, s, PAUSE_TITLE);
        rd.setColor(PAUSE_TITLE[1]);
        int dashY = (int) Math.round(b.getCenterY());
        rd.fillRect((int) b.getMinX() - 34, dashY, 16, 2);
        rd.fillRect((int) b.getMaxX() + 18, dashY, 16, 2);
    }

    private static void fillDown(Graphics2D rd, Shape s, Color[] colors) {
        Rectangle2D b = s.getBounds2D();
        rd.setPaint(new LinearGradientPaint((float) b.getX(), (float) b.getY(), (float) b.getX(),
                (float) b.getMaxY() + 0.01F, GRADIENT_STOPS, colors));
        rd.fill(s);
        rd.setStroke(new BasicStroke(0.4F));
        rd.draw(s);
    }

    private static Shape shape(Font f, String text) {
        String key = f.getSize2D() + "|" + f.getAttributes().get(TextAttribute.TRACKING) + "|" + text;
        Shape s = SHAPES.get(key);
        if (s == null) {
            if (SHAPES.size() > 256) {
                SHAPES.clear(); // values like "Now: ..." change; keep the cache small
            }
            s = f.createGlyphVector(new java.awt.font.FontRenderContext(null, true, true), text).getOutline();
            SHAPES.put(key, s);
        }
        return s;
    }

    private static Shape place(Shape s, float x, float y, Boolean alignRight) {
        Rectangle2D b = s.getBounds2D();
        double dx = alignRight == null ? x - b.getCenterX() : alignRight ? x - b.getMaxX() : x - b.getMinX();
        return AffineTransform.getTranslateInstance(dx, y).createTransformedShape(s);
    }

    /* ---------------------------------------------------------------- model */

    private static final class Section {
        final String title;
        final int column;
        final int y;
        final int first;
        int count;

        Section(String title, int column, int y, int first) {
            this.title = title;
            this.column = column;
            this.y = y;
            this.first = first;
        }
    }

    /** One setting: its name, a line on what it does, and how it changes. */
    private abstract static class Row {
        final String label;
        final String help;
        private final Supplier<String> status;
        private BooleanSupplier enabled = () -> true;
        String disabledWhy;

        Row(String label, String help, Supplier<String> status) {
            this.label = label;
            this.help = help;
            this.status = status;
        }

        Row when(BooleanSupplier condition, String why) {
            enabled = condition;
            disabledWhy = why;
            return this;
        }

        boolean enabled() {
            return enabled.getAsBoolean();
        }

        String status() {
            return status == null ? null : status.get();
        }

        String help() {
            return help;
        }

        void change(int direction, boolean wrap) {
            if (enabled()) {
                step(direction, wrap);
            }
        }

        abstract void step(int direction, boolean wrap);

        abstract String value();
    }

    /** A setting with a few values to step through. */
    private static final class Choice extends Row {
        private final int[] values;
        private final IntFunction<String> names;
        private final IntSupplier get;
        private final IntConsumer set;

        Choice(String label, String help, Supplier<String> status, int[] values, IntFunction<String> names,
                IntSupplier get, IntConsumer set) {
            super(label, help, status);
            this.values = values;
            this.names = names;
            this.get = get;
            this.set = set;
        }

        @Override
        void step(int direction, boolean wrap) {
            int now = get.getAsInt();
            int at = -1;
            for (int i = 0; i < values.length; i++) {
                if (values[i] == now) {
                    at = i;
                }
            }
            int next;
            if (at < 0) {
                next = direction > 0 ? 0 : values.length - 1; // a value set elsewhere: start from an end
            } else {
                next = at + direction;
                if (next < 0 || next >= values.length) {
                    if (!wrap) {
                        return;
                    }
                    next = (next + values.length) % values.length;
                }
            }
            set.accept(values[next]);
        }

        @Override
        String value() {
            return names.apply(get.getAsInt());
        }
    }

    /**
     * The window's size: the common sizes of its shape that fit on the
     * monitor, the size it has if that is none of them, and Custom, where
     * Enter (or a click) lets the player type a size of their own.
     */
    private final class WindowSize extends Row {
        /** Custom is chosen: the window keeps its size until one is typed. */
        private boolean custom;
        private boolean typingNow;
        private final StringBuilder width = new StringBuilder();
        private final StringBuilder height = new StringBuilder();
        private boolean onHeight;
        /** Why the last typed size was not used, until the next key. */
        private String problem;

        WindowSize() {
            super("Resolution", "The size of the window's inside, in the monitor's pixels.",
                    null);
        }

        boolean custom() {
            return custom;
        }

        boolean typing() {
            return typingNow;
        }

        /** The common sizes of the window's shape that fit, and its own size among them if it is not one. */
        private List<int[]> choices() {
            Dimension now = RunApp.windowPixels();
            int aspect = aspectOf(now.width, now.height);
            List<int[]> sizes = new ArrayList<>(sizesFor(aspect == OTHER_ASPECT ? 0 : aspect));
            int at = 0;
            boolean listed = false;
            for (int[] size : sizes) {
                if (size[0] == now.width && size[1] == now.height) {
                    listed = true;
                }
                if (size[0] * size[1] < now.width * now.height) {
                    at++;
                }
            }
            if (!listed) {
                sizes.add(Math.min(at, sizes.size()), new int[] {now.width, now.height});
            }
            return sizes;
        }

        @Override
        void step(int direction, boolean wrap) {
            List<int[]> sizes = choices();
            Dimension now = RunApp.windowPixels();
            int at = sizes.size(); // Custom, after the sizes
            if (!custom) {
                for (int i = 0; i < sizes.size(); i++) {
                    if (sizes.get(i)[0] == now.width && sizes.get(i)[1] == now.height) {
                        at = i;
                    }
                }
            }
            int next = at + direction;
            if (next < 0 || next > sizes.size()) {
                if (!wrap) {
                    return;
                }
                next = (next + sizes.size() + 1) % (sizes.size() + 1);
            }
            custom = next == sizes.size();
            if (!custom) {
                RunApp.setWindowPixels(sizes.get(next)[0], sizes.get(next)[1]);
            }
        }

        /** Enter: on Custom it starts typing, otherwise it steps on. */
        void enter() {
            if (!enabled()) {
                return;
            }
            if (custom) {
                Dimension now = RunApp.windowPixels();
                width.setLength(0);
                width.append(now.width);
                height.setLength(0);
                height.append(now.height);
                onHeight = false;
                problem = null;
                typingNow = true;
                typing = true;
                TYPED.clear();
            } else {
                step(1, true);
            }
        }

        /** The row is no longer chosen: Custom goes back to showing the size. */
        void leave() {
            if (!typingNow) {
                custom = false;
                problem = null;
            }
        }

        void stopTyping() {
            typingNow = false;
            typing = false;
            custom = false;
            TYPED.clear();
        }

        /** A key while typing: digits, Backspace, x (or Tab, Space, the arrows) between the two, Enter, Escape. */
        void type(int key) {
            problem = null;
            StringBuilder field = onHeight ? height : width;
            if (key >= '0' && key <= '9') {
                if (field.length() < 5 && !(field.length() == 0 && key == '0')) {
                    field.append((char) key);
                }
            } else if (key == 8 || key == 127) { // Backspace, Delete
                if (field.length() > 0) {
                    field.setLength(field.length() - 1);
                } else if (onHeight) {
                    onHeight = false;
                }
            } else if (key == 'x' || key == 'X' || key == '*' || key == 9 || key == ' ' || key == 1007) {
                onHeight = true;
            } else if (key == 1006) {
                onHeight = false;
            } else if (key == 10) {
                apply();
            } else if (key == 27) {
                stopTyping();
            }
        }

        private void apply() {
            int w = width.length() == 0 ? 0 : Integer.parseInt(width.toString());
            int h = height.length() == 0 ? 0 : Integer.parseInt(height.toString());
            Dimension screen = RunApp.screenPixels();
            int minW = GameFacts.screenWidth / 2;
            int minH = GameFacts.screenHeight / 2;
            if (w < minW || h < minH) {
                problem = "At least " + minW + "x" + minH + ".";
            } else if (w > screen.width || h > screen.height) {
                problem = "At most the monitor's " + screen.width + "x" + screen.height + ".";
            } else {
                stopTyping();
                RunApp.setWindowPixels(w, h);
            }
        }

        /** The size as typed so far, with a blinking mark on the number being typed. */
        String typed(boolean blink) {
            String mark = blink ? "|" : " ";
            return width + (onHeight ? "" : mark) + " x " + height + (onHeight ? mark : "");
        }

        @Override
        String value() {
            if (custom) {
                return "Custom...";
            }
            Dimension now = RunApp.windowPixels();
            return now.width + "x" + now.height;
        }

        @Override
        String help() {
            if (typingNow) {
                return problem != null ? problem : "Type a size of your own, up to the monitor's.";
            }
            return custom ? "Press Enter, or click, to type a size of your own." : super.help();
        }

        @Override
        String status() {
            return "Now: " + describeWindow() + ".";
        }
    }

    /** A setting from a range, drawn as a bar. */
    private static final class Slider extends Row {
        private final int min;
        private final int max;
        private final int step;
        private final IntFunction<String> format;
        private final IntSupplier get;
        private final IntConsumer set;

        Slider(String label, String help, int min, int max, int step, IntFunction<String> format, IntSupplier get,
                IntConsumer set) {
            super(label, help, null);
            this.min = min;
            this.max = max;
            this.step = step;
            this.format = format;
            this.get = get;
            this.set = set;
        }

        @Override
        void step(int direction, boolean wrap) {
            if (wrap) {
                return; // Enter does not move a slider
            }
            int now = get.getAsInt();
            int next = direction > 0 ? (now / step + 1) * step : ((now + step - 1) / step - 1) * step;
            next = Math.max(min, Math.min(max, next));
            if (next != now) {
                set.accept(next);
            }
        }

        void setAt(float fraction) {
            if (enabled()) {
                int v = Math.round((min + Math.max(0F, Math.min(1F, fraction)) * (max - min)) / step) * step;
                set.accept(Math.max(min, Math.min(max, v)));
            }
        }

        float fraction() {
            return (get.getAsInt() - min) / (float) (max - min);
        }

        @Override
        String value() {
            return format.apply(get.getAsInt());
        }
    }
}
