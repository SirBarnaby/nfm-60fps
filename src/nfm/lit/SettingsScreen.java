package nfm.lit;

import java.awt.BasicStroke;
import java.awt.Dimension;
import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.Image;
import java.awt.Polygon;
import java.awt.LinearGradientPaint;
import java.awt.RenderingHints;
import java.awt.Shape;
import java.awt.Stroke;
import java.awt.event.KeyEvent;
import java.awt.font.TextAttribute;
import java.awt.geom.AffineTransform;
import java.awt.geom.Rectangle2D;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BooleanSupplier;
import java.util.function.IntConsumer;
import java.util.function.IntFunction;
import java.util.function.IntSupplier;
import java.util.function.Supplier;

import nfm.lit.audio.Sound3D;
import nfm.lit.audio.StreamMusic;
import nfm.lit.gpu.GpuDisplay;

import javax.imageio.ImageIO;

/**
 * The settings screen, reached from the main menu and the pause menu: every
 * setting the developer console has, in the game's own menu look. The box is
 * the pause box's frame around the main menu's brown, the lettering is the
 * menus' own (Adventure). Tabs along the top (Display, Audio, Controls,
 * Advanced) each hold a list of settings; beside the list, the chosen
 * setting's picture and what it does. The keys are the menus' keys: up and
 * down choose, left and right change, Enter changes or goes back, Escape
 * goes back; Page Up, Page Down and Tab change the tab, and the mouse wheel
 * scrolls a long list. Each change is saved at once, by the same setters the
 * console uses. A setting's picture is data/settings/(its name).png, where
 * there is one; otherwise a placeholder stands in.
 */
final class SettingsScreen {

    /* the box, in the game's own 1280x720 view; a picture of another shape has it in the middle */
    private static final int BOX_W = 940;
    private static final int BOX_H = 660;
    private static final int BOX_X = (GameFacts.GAME_WIDTH - BOX_W) / 2;
    private static final int BOX_Y = (GameFacts.GAME_HEIGHT - BOX_H) / 2;
    /** The tabs' lettering stands on this line. */
    private static final int TABS_Y = BOX_Y + 78;
    private static final int TAB_SPACING = 200;
    /** The list of the tab's settings. */
    private static final int LIST_X = BOX_X + 30;
    private static final int LIST_W = 500;
    private static final int LIST_TOP = BOX_Y + 104;
    private static final int LIST_BOTTOM = BOX_Y + BOX_H - 96;
    /** Beside the list: the chosen setting's picture and what it does. */
    private static final int PANE_X = LIST_X + LIST_W + 34;
    private static final int PANE_W = BOX_X + BOX_W - 30 - PANE_X;
    private static final int PICTURE_H = PANE_W * 9 / 16;
    private static final int ROW_H = 25;
    private static final int HEADING_H = 32;
    private static final int GROUP_GAP = 10;
    private static final int VALUE_W = 190;
    private static final int BACK_Y = BOX_Y + BOX_H - 72;
    /** {@link #selected} while the tabs along the top are chosen. */
    private static final int TABS = -1;

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
    /** Page Up, Page Down and Tab: tabs to move by, since the last frame. */
    private static final AtomicInteger TAB_KEYS = new AtomicInteger();
    /** A key is being chosen for an action: the next key is the screen's (see {@link #captureKey}). */
    private static volatile boolean choosingKey;
    private static final ConcurrentLinkedQueue<Integer> CHOSEN = new ConcurrentLinkedQueue<>();
    /** Backspace and Delete since the last frame: they clear a key. */
    private static final AtomicInteger CLEAR_KEYS = new AtomicInteger();
    /** The mouse wheel's notches since the last frame (down is more). */
    private static final AtomicInteger WHEEL = new AtomicInteger();
    /**
     * The settings that show a picture beside them: the ones that change what
     * is seen or heard, which a picture can show. The rest (keys, controllers,
     * readouts, volumes) and any setting added later show only their words.
     */
    private static final java.util.Set<String> PICTURED = java.util.Set.of(
            "Fullscreen", "Aspect ratio", "Resolution", "UI scale", "VSync", "Renderer",
            "MSAA", "Supersampling", "Smooth particles", "HDR", "Paper white", "Peak brightness",
            "Sound system", "Output", "Occlusion", "Sound paths", "Reverb", "HDR audio",
            "Dynamic resolution", "Performance overlay");

    /** The settings' pictures by name; null where there is none. Game thread only. */
    private static final Map<String, Image> PICTURES = new HashMap<>();
    /** Help lines already wrapped to the pane. Game thread only. */
    private static final Map<String, List<String>> WRAPPED = new HashMap<>();

    private final Image[] back;
    private final List<Row> rows = new ArrayList<>();
    private final List<String> tabNames = new ArrayList<>();
    private final List<String> tabHelp = new ArrayList<>();
    /** The rows of each tab, top to bottom. */
    private int[][] tabRows;
    /** How tall each tab's list is, headings and all. */
    private int[] tabHeight;
    /** Where each row stands in its tab's list, before scrolling. */
    private int[] rowY;
    /** How far each tab's list is scrolled. */
    private int[] scroll;
    /** The tab shown. */
    private int tab;
    /** Lettering already turned into shapes; game thread only. */
    private static final Map<String, Shape> SHAPES = new HashMap<>();
    private final Font font;
    private final Font smallFont;
    private final Font titleFont;

    /** The selected row, TABS for the tabs, or rows.size() for Back. */
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
        layout();
    }

    /** Stacks each tab's rows, with room for the headings, and notes how tall each list is. */
    private void layout() {
        tabRows = new int[tabNames.size()][];
        tabHeight = new int[tabNames.size()];
        scroll = new int[tabNames.size()];
        rowY = new int[rows.size()];
        for (int t = 0; t < tabNames.size(); t++) {
            List<Integer> in = new ArrayList<>();
            int y = 0;
            for (int i = 0; i < rows.size(); i++) {
                Row row = rows.get(i);
                if (row.tab == t) {
                    if (row.heading != null) {
                        y += (in.isEmpty() ? 0 : GROUP_GAP) + HEADING_H;
                    }
                    rowY[i] = y;
                    y += ROW_H;
                    in.add(i);
                }
            }
            tabRows[t] = in.stream().mapToInt(Integer::intValue).toArray();
            tabHeight[t] = y;
        }
    }

    /** Escape went down: on this screen it goes back, where elsewhere it is Enter. */
    static void noteEscape() {
        escapePressed = true;
    }

    /** Page Up (-1), Page Down or Tab (1) went down: the next or the previous tab. */
    static void noteTabKey(int direction) {
        TAB_KEYS.addAndGet(direction);
    }

    /** The mouse wheel turned by {@code notches} (down is more): scrolls the list. */
    static void noteWheel(int notches) {
        WHEEL.addAndGet(notches);
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

    /**
     * A key went down on the keyboard (on the window's thread), by its
     * place: while a key is being chosen it is the screen's, and the game
     * does not see it.
     *
     * @return true if the key was taken
     */
    static boolean captureKey(int keyCode) {
        if (choosingKey && keyCode != KeyEvent.VK_F11) {
            CHOSEN.add(keyCode);
            return true;
        }
        if (keyCode == KeyEvent.VK_BACK_SPACE || keyCode == KeyEvent.VK_DELETE) {
            CLEAR_KEYS.incrementAndGet();
        }
        return false;
    }

    /** The key row a key is being chosen for, or null. Game thread. */
    private KeyRow choosing;

    /** Typing a size or choosing a key: the mouse's hovering leaves the selection be. */
    private boolean busy() {
        return windowSize.typing() || choosing != null;
    }

    /** Called as the screen opens. */
    void open() {
        selected = tabRows[tab].length > 0 ? tabRows[tab][0] : TABS;
        scroll[tab] = 0;
        pressed = false;
        backPressed = 0;
        escapePressed = false;
        TAB_KEYS.set(0);
        WHEEL.set(0);
        CLEAR_KEYS.set(0);
        windowSize.stopTyping();
        stopChoosing();
    }

    private void stopChoosing() {
        if (choosing != null) {
            choosing.stopListening();
        }
    }

    /** For the smoke test: chooses the row with this label, on its tab. */
    void select(String label) {
        for (int i = 0; i < rows.size(); i++) {
            if (rows.get(i).label.equals(label)) {
                tab = rows.get(i).tab;
                selected = i;
                showSelected();
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
        String sound3dOnly = "Only with Steam Audio (Sound system).";


        tab("Display", "The window, the frame rate, and how the races are drawn.");
        group("Screen");
        add(new Choice("Fullscreen", "Borderless fullscreen on this monitor. F11 or Alt+Enter switch it too.",
                null, new int[] {0, 1}, SettingsScreen::onOff, () -> RunApp.isFullscreen() ? 1 : 0,
                v -> RunApp.setFullscreen(v == 1)));
        add(new Choice("Aspect ratio",
                "The window's shape, or in fullscreen the resolution's (a shape not the monitor's is cut to fill it)."
                        + " The race and the menus take it, with no bars.",
                () -> "Now: " + describeWindow() + ".", aspectChoices(), SettingsScreen::aspectName,
                SettingsScreen::currentAspect, SettingsScreen::setAspect));
        add(windowSize);
        add(new Choice("UI scale",
                "How big the menus, lettering and HUD are: 1, 2 or 4 times their own size in pixels, so they stay"
                        + " sharp, with more of the backdrop around them on a larger screen. Auto: the largest that"
                        + " fits, 1x on a 1080p screen, 2x on 1440p and 4K. A scale needs room for 1280x720 at it,"
                        + " so a smaller window takes the next one down, and one under 1280x720 shows the game"
                        + " shrunk to fit.",
                () -> "Now: " + GameSparker.describePicture() + ".", new int[] {0, 1, 2, 4},
                v -> v == 0 ? "Auto" : v + "x", GameSparker::getUiZoom, GameSparker::setUiZoom)
                .when(GameSparker::isGpuRenderer,
                        "Only with the Graphics card renderer: in software the menus are scaled with the race."));
        add(new Choice("Frame rate",
                "Frames a second. Variable refresh stays just under the monitor's rate, for G-Sync and FreeSync.",
                null, fpsLimits, SettingsScreen::fpsName, GameSparker::getFpsLimit, GameSparker::setFpsLimit));
        add(new Choice("VSync",
                "On: frames wait for the display, so the picture never tears. Off: each frame goes out at once,"
                        + " the least lag. Auto: on with Match display and Variable refresh.",
                () -> "Now: " + GameSparker.describeVsync() + ".",
                new int[] {GameSparker.VSYNC_AUTO, GameSparker.VSYNC_ON, GameSparker.VSYNC_OFF},
                v -> v == GameSparker.VSYNC_ON ? "On" : v == GameSparker.VSYNC_OFF ? "Off" : "Auto",
                GameSparker::getVsync, GameSparker::setVsync).when(gpu, gpuOnly));
        add(new Choice("Laptop battery FPS",
                "Holds the frame rate down while the laptop runs on its battery, to make it last.",
                () -> "Now: " + GameSparker.describeBatteryFps() + ".",
                new int[] {0, 30, 40, 60, 90, 120}, v -> v == 0 ? "No cap" : v + " fps",
                GameSparker::getBatteryFps, GameSparker::setBatteryFps));
        group("Picture");
        add(new Choice("Renderer",
                "Graphics card: the GPU draws, with the smoothing below. Software: the game's own drawing.",
                () -> GameSparker.describeRenderer().contains("not available")
                        ? "The graphics card cannot draw here, so the game draws in software." : null,
                new int[] {1, 0}, v -> v == 1 ? "Graphics card" : "Software",
                () -> GameSparker.isGpuRenderer() ? 1 : 0, v -> GameSparker.setGpuRenderer(v == 1)));
        add(new Choice("MSAA",
                "Multisampling: smooths the edges of the cars and the track. Off keeps the game's hard edges.",
                () -> "Now: " + GameSparker.describeMultisample() + ".",
                new int[] {1, 2, 4, 8, 16}, v -> v <= 1 ? "Off" : v + "x",
                GameSparker::getMultisample, GameSparker::setMultisample).when(gpu, gpuOnly));
        add(new Choice("Supersampling",
                "Draws races with 2, 4 or 8 times the pixels and averages them down. 4x on a 1080p screen is 4K.",
                () -> "Now: " + GameSparker.describeSupersample() + ".",
                new int[] {1, 2, 4, 8}, v -> v <= 1 ? "Off" : v + "x",
                GameSparker::getSupersample, GameSparker::setSupersample).when(gpu, gpuOnly));
        add(new Choice("Smooth particles",
                "Dust and flying debris move every frame. Off: once a game tick, as the original drew them.",
                null, new int[] {0, 1}, SettingsScreen::onOff, () -> GameSparker.isSmoothEffects() ? 1 : 0,
                v -> GameSparker.setSmoothEffects(v == 1)));
        group("HDR");
        add(new Choice("HDR", "Brighter highlights on an HDR monitor, when Windows has HDR on.",
                () -> "Now: " + GameSparker.describeHdr() + ".",
                new int[] {GpuDisplay.HDR_OFF, GpuDisplay.HDR_AUTO},
                v -> v == GpuDisplay.HDR_OFF ? "Off" : v == GpuDisplay.HDR_FORCE ? "Forced" : "Auto",
                GameSparker::getHdr, GameSparker::setHdr).when(gpu, gpuOnly));
        add(new Choice("Paper white",
                "How bright the game's white is in HDR. Windows: as its SDR content brightness slider.", null,
                new int[] {0, 80, 100, 120, 160, 200, 240, 280, 320, 400, 500},
                v -> v == 0 ? "Windows" : v + " nits", GameSparker::getPaperWhite, GameSparker::setPaperWhite)
                .when(hdr, "Only with HDR on."));
        add(new Choice("Peak brightness", "The brightest the monitor can show. Monitor: what it tells Windows.", null,
                new int[] {0, 400, 600, 800, 1000, 1400, 2000, 4000, 10000},
                v -> v == 0 ? "Monitor" : v + " nits", GameSparker::getPeak, GameSparker::setPeak)
                .when(hdr, "Only with HDR on."));

        tab("Audio", "The sound system, its effects, and the volumes.");
        group("Sound");
        add(new Choice("Sound system",
                "Steam Audio: sounds come from where they are, mixed and spread. Classic: as the game always sounded.",
                () -> "Now: " + GameSparker.describeSound() + ".",
                new int[] {1, 0}, v -> v == 1 ? "Steam Audio" : "Classic",
                () -> GameSparker.isNewSound() ? 1 : 0, v -> GameSparker.setNewSound(v == 1)));
        add(new Choice("Output", "Mono plays everything on both speakers, for one ear or one speaker.", null,
                new int[] {0, 1}, v -> v == 1 ? "Mono" : "Stereo", () -> Sound3D.isMono() ? 1 : 0,
                v -> GameSparker.setMono(v == 1)).when(sound3d, sound3dOnly));
        group("Effects");
        add(new Choice("Occlusion",
                "A car behind a hill or a wall is muffled, as it would be: quieter and duller, by how much of"
                        + " the track is in the way.",
                null, new int[] {0, 1}, SettingsScreen::onOff, () -> Sound3D.isOcclusion() ? 1 : 0,
                v -> GameSparker.setOcclusion(v == 1)).when(sound3d, sound3dOnly));
        add(new Choice("Sound paths",
                "Sound bends over a crest and round the end of a wall: a hidden car is heard from the way its"
                        + " sound comes round.",
                null, new int[] {0, 1}, SettingsScreen::onOff, () -> Sound3D.isPaths() ? 1 : 0,
                v -> GameSparker.setSoundPaths(v == 1))
                .when(() -> GameSparker.isNewSound() && Sound3D.isOcclusion(), "Only with occlusion on."));
        add(new Choice("Reverb",
                "Half-pipes, walls and canyons make the engines, skids and crashes ring on; open ground stays"
                        + " dry.",
                null, new int[] {0, 1}, SettingsScreen::onOff, () -> Sound3D.isReverb() ? 1 : 0,
                v -> GameSparker.setReverb(v == 1)).when(sound3d, sound3dOnly));
        add(new Choice("HDR audio",
                "The engines, skids, music and menus play this far below full scale, and crashes and a wreck"
                        + " use the room above: they stand out as they would. Raise the master volume to match.",
                null, new int[] {0, 6, 12, 18}, v -> v == 0 ? "Off" : new String[] {"", "Narrow", "Medium", "Wide"}[v / 6]
                        + " (" + v + " dB)",
                Sound3D::hdrRange, GameSparker::setHdrAudio).when(sound3d, sound3dOnly));
        group("Volume");
        String[] busLabels = {"Master volume", "Engines", "Effects", "Music", "Interface"};
        String[] busHelp = {"Everything the game plays.", "The cars' engines and skids.",
                "Crashes, scrapes, power-ups and the rest of the race.", "The stage's music.",
                "The menus' and the countdown's sounds."};
        for (int b = 0; b < busLabels.length; b++) {
            final int bus = b;
            add(new Slider(busLabels[b], busHelp[b], 0, 100, 5, v -> v + "%",
                    () -> Math.round(Sound3D.busLevel(bus) * 100F), v -> GameSparker.setVolume(bus, v))
                    .when(sound3d, sound3dOnly));
        }
        add(new Slider("Music width",
                "How far apart the music's left and right are: 0 is mono, " + StreamMusic.DEFAULT_SEPARATION
                        + " the game's own, 200 the widest.",
                0, 200, 10, v -> v + "%", StreamMusic::separation, GameSparker::setMusicSeparation)
                .when(() -> GameSparker.isNewSound() && StreamMusic.available(), sound3dOnly));

        tab("Controls", "Game controllers, how quickly the car answers, and the keys.");
        group("Game controllers");
        add(new Choice("Game controllers", "Plays with an Xbox or other game controller.",
                GameSparker::describeGamepads, new int[] {0, 1}, SettingsScreen::onOff,
                () -> Gamepads.isOn() ? 1 : 0, v -> GameSparker.setGamepad(v == 1)));
        add(new Choice("Shaking on hits", "The controller shakes when the car hits hard.", null,
                new int[] {0, 1}, SettingsScreen::onOff, () -> Gamepads.isRumble() ? 1 : 0,
                v -> GameSparker.setRumble(v == 1)).when(Gamepads::isOn, "Only with game controllers on."));
        group("Response");
        add(new Choice("Run-ahead",
                "Shows the race a tick ahead with the keys held now: 53 ms less lag between key and screen.",
                null, new int[] {0, 1}, SettingsScreen::onOff, () -> GameSparker.isRunAhead() ? 1 : 0,
                v -> GameSparker.setRunAhead(v == 1)));

        group("Keys");
        for (int a = 0; a < KeyBindings.COUNT; a++) {
            add(new KeyRow(a));
        }
        add(new Button("Restore the game's keys", "Restore",
                "Puts every key back as the game had it. Out of a race the arrows, Enter and Escape always move"
                        + " through the menus, whatever the keys.",
                KeyBindings::reset).when(() -> !KeyBindings.isDefault(), "The keys are the game's own."));

        tab("Advanced", "For tinkering: how the game draws, and readouts of how fast it runs.");
        group("Drawing");
        add(new Choice("Dynamic resolution",
                "Draws races smaller while the graphics card cannot keep up, to hold the frame rate.",
                () -> "Now: " + GameSparker.describeDynamicResolution() + ".",
                new int[] {0, 90, 75, 50, 25}, v -> v == 0 ? "Off" : "Down to " + v + "%",
                GameSparker::getDynamicResolution, GameSparker::setDynamicResolution).when(gpu, gpuOnly));
        group("Performance overlay");
        add(new Choice("Performance overlay",
                "A panel in a corner with how fast the game runs: the readouts below. F3 shows and hides it.",
                null, new int[] {PerfOverlay.OFF, PerfOverlay.TOP_LEFT, PerfOverlay.TOP_RIGHT,
                        PerfOverlay.BOTTOM_LEFT, PerfOverlay.BOTTOM_RIGHT},
                v -> new String[] {"Off", "Top left", "Top right", "Bottom left", "Bottom right"}[v],
                PerfOverlay::corner, PerfOverlay::setCorner));
        BooleanSupplier overlay = () -> PerfOverlay.corner() != PerfOverlay.OFF;
        String overlayOnly = "Only with the performance overlay on.";
        String[][] readouts = {
            {"Frames a second", "Frames put on the screen in the last second."},
            {"Frame time", "The average time between frames over the last second, and the graphics card's own"
                    + " share of it."},
            {"90% frame time", "The time 90% of frames beat, over the last two seconds: the slowest tenth are"
                    + " above it."},
            {"99% frame time", "The time 99% of frames beat: the slowest frames, the ones felt as stutter."},
            {"Memory", "The memory Windows has given the game alone, and the part of it Java's heap uses."},
            {"Video memory", "The graphics card's memory in use, of all it has, and how much of it is textures."},
            {"Cache", "The game's caches on disk: the compiled code it starts with, and the baked sound paths."},
            {"Frame time graph", "The last frames' times as bars: green within 60 fps, yellow within 30, red"
                    + " slower."},
        };
        for (int r = 0; r < readouts.length; r++) {
            final int bit = 1 << r;
            add(new Choice(readouts[r][0], readouts[r][1], null, new int[] {0, 1}, SettingsScreen::onOff,
                    () -> PerfOverlay.shows(bit) ? 1 : 0, v -> PerfOverlay.setShows(bit, v == 1))
                    .when(overlay, overlayOnly));
        }
    }

    /* the tab and the heading the next rows go under, while building */
    private int buildingTab = -1;
    private String nextHeading;

    private void tab(String name, String help) {
        tabNames.add(name);
        tabHelp.add(help);
        buildingTab = tabNames.size() - 1;
    }

    /** The next row starts a group, under this heading. */
    private void group(String heading) {
        nextHeading = heading;
    }

    private void add(Row row) {
        row.tab = buildingTab;
        row.heading = nextHeading;
        nextHeading = null;
        rows.add(row);
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
        Dimension now = RunApp.drawingPixels();
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

    /** Changes the window's (or fullscreen resolution's) shape, keeping its height as near as the sizes allow. */
    private static void setAspect(int aspect) {
        Dimension now = RunApp.drawingPixels();
        int[] best = null;
        for (int[] size : sizesFor(aspect)) {
            if (best == null || Math.abs(size[1] - now.height) < Math.abs(best[1] - now.height)) {
                best = size;
            }
        }
        RunApp.setDrawingPixels(best[0], best[1]);
    }

    private static String describeWindow() {
        Dimension now = RunApp.drawingPixels();
        Dimension screen = RunApp.screenPixels();
        if (RunApp.isFullscreen()) {
            return now.width == screen.width && now.height == screen.height
                    ? "fullscreen at the monitor's " + screen.width + "x" + screen.height
                    : "fullscreen at " + now.width + "x" + now.height + ", scaled to the " + screen.width + "x"
                            + screen.height + " monitor";
        }
        return now.width + "x" + now.height + " window, on a " + screen.width + "x" + screen.height + " monitor";
    }

    /* ---------------------------------------------------------- each frame */

    /**
     * Draws the screen over whatever is behind it and handles the keys.
     *
     * @return true when the player goes back
     */
    boolean frame(Graphics2D rd, Control control) {
        boolean leave = handleKeys(control);
        int dx = GameFacts.marginX();
        int dy = GameFacts.marginY();
        rd.translate(dx, dy);
        try {
            draw(rd);
        } finally {
            rd.translate(-dx, -dy);
        }
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
            TAB_KEYS.set(0);
            WHEEL.set(0);
            return false;
        }
        if (choosing != null) {
            for (Integer key; choosing != null && (key = CHOSEN.poll()) != null;) {
                choosing.chosen(key);
            }
            control.up = control.down = control.left = control.right = false;
            control.enter = control.handb = false;
            escapePressed = false;
            TAB_KEYS.set(0);
            CLEAR_KEYS.set(0);
            return false;
        }
        if (CLEAR_KEYS.getAndSet(0) > 0 && isRow(selected) && rows.get(selected) instanceof KeyRow key) {
            key.clear();
        }
        int tabMove = TAB_KEYS.getAndSet(0);
        if (tabMove != 0) {
            switchTab(tab + tabMove);
        }
        int notches = WHEEL.getAndSet(0);
        if (notches != 0) {
            scroll[tab] += notches * ROW_H * 3;
            clampScroll();
        }
        int[] list = tabRows[tab];
        int at = position();
        if (control.up || control.down) {
            at = control.up ? (at <= -1 ? list.length : at - 1) : (at >= list.length ? -1 : at + 1);
            selected = at < 0 ? TABS : at >= list.length ? rows.size() : list[at];
            showSelected();
            control.up = false;
            control.down = false;
        }
        if (!isRow(selected) || rows.get(selected) != windowSize) {
            windowSize.leave();
        }
        if (!isRow(selected) || !(rows.get(selected) instanceof KeyRow)) {
            KeyRow.unchosen(rows);
        }
        if (selected == TABS) {
            if (control.left || control.right) {
                switchTab(tab + (control.left ? -1 : 1));
            }
        } else if (isRow(selected)) {
            if (control.left) {
                rows.get(selected).change(-1, false);
            }
            if (control.right) {
                rows.get(selected).change(1, false);
            }
        }
        control.left = false;
        control.right = false;
        boolean leave = false;
        if (escapePressed) {
            escapePressed = false;
            leave = true;
        } else if (control.enter || control.handb) {
            if (selected == TABS) {
                if (list.length > 0) {
                    selected = list[0];
                    showSelected();
                }
            } else if (isRow(selected) && rows.get(selected) == windowSize) {
                windowSize.enter();
            } else if (isRow(selected)) {
                rows.get(selected).change(1, true);
            } else {
                leave = true;
            }
        }
        control.enter = false;
        control.handb = false;
        return leave;
    }

    private boolean isRow(int i) {
        return i >= 0 && i < rows.size();
    }

    /** Where the selection is in the tab: -1 on the tabs, the list's length on Back. */
    private int position() {
        int[] list = tabRows[tab];
        if (selected == TABS) {
            return -1;
        }
        for (int p = 0; p < list.length; p++) {
            if (list[p] == selected) {
                return p;
            }
        }
        return list.length;
    }

    /** Shows another tab. The tabs or Back stay chosen; a row gives way to the new tab's first. */
    private void switchTab(int to) {
        tab = Math.floorMod(to, tabNames.size());
        if (isRow(selected)) {
            selected = tabRows[tab].length > 0 ? tabRows[tab][0] : TABS;
            scroll[tab] = 0;
        }
        windowSize.leave();
        stopChoosing();
        pressed = false;
    }

    /** Scrolls the list so the chosen row, and its heading, are in sight. */
    private void showSelected() {
        if (!isRow(selected)) {
            return;
        }
        Row row = rows.get(selected);
        int top = rowY[selected] - (row.heading != null ? HEADING_H : 0);
        int bottom = rowY[selected] + ROW_H;
        int shown = LIST_BOTTOM - LIST_TOP;
        if (top < scroll[tab]) {
            scroll[tab] = top;
        } else if (bottom > scroll[tab] + shown) {
            scroll[tab] = bottom - shown;
        }
        clampScroll();
    }

    private void clampScroll() {
        int most = Math.max(0, tabHeight[tab] - (LIST_BOTTOM - LIST_TOP));
        scroll[tab] = Math.max(0, Math.min(most, scroll[tab]));
    }

    /**
     * The mouse, in picture coordinates: hovering chooses a row, a click on
     * its arrows (or on a volume's bar) changes it, a click on a tab shows
     * it, a click on Back goes back. {@code state} is 1 on the tick a button
     * went down.
     */
    void mouse(int pictureX, int pictureY, int state, Control control) {
        int x = pictureX - GameFacts.marginX();
        int y = pictureY - GameFacts.marginY();
        boolean moved = x != lastMouseX || y != lastMouseY;
        lastMouseX = x;
        lastMouseY = y;
        int row = rowAt(x, y);
        int tabHit = tabAt(x, y);
        boolean overBack = overBack(x, y);
        if (moved && state == 0 && !busy()) {
            if (row >= 0) {
                selected = row;
            } else if (tabHit >= 0) {
                selected = TABS;
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
        if (state == 1 && choosing != null) {
            if (row >= 0 && rows.get(row) == choosing) {
                choosing.slot = x < valueLeft() + VALUE_W / 2 ? 0 : 1; // the other key of the same action
                return;
            }
            stopChoosing();
        }
        if (moved && state == 0 && busy()) {
            return; // the typing, or the key being chosen, keeps its row
        }
        if (state == 1) {
            if (row >= 0) {
                selected = row;
                Row r = rows.get(row);
                int left = valueLeft();
                if (r instanceof KeyRow key && x >= left - 4 && x < left + VALUE_W + 4) {
                    key.slot = x < left + VALUE_W / 2 ? 0 : 1;
                    key.change(1, true);
                } else if (r instanceof Button) {
                    r.change(1, true);
                } else if (r == windowSize && windowSize.custom() && x >= left + 16 && x <= left + VALUE_W - 16) {
                    windowSize.enter();
                } else if (r instanceof Slider slider && x >= left + 16 && x <= left + VALUE_W - 16) {
                    slider.setAt((x - (left + 20)) / (float) (VALUE_W - 40));
                } else if (x >= left - 4 && x < left + 16) {
                    r.change(-1, false);
                } else {
                    r.change(1, !(x >= left + VALUE_W - 16));
                }
                pressed = true;
            } else if (tabHit >= 0) {
                switchTab(tabHit);
                selected = TABS;
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
        if (x < LIST_X || x >= LIST_X + LIST_W || y < LIST_TOP || y >= LIST_BOTTOM) {
            return -1;
        }
        for (int i : tabRows[tab]) {
            int top = LIST_TOP + rowY[i] - scroll[tab];
            if (y >= top && y < top + ROW_H - 2) {
                return i;
            }
        }
        return -1;
    }

    private float tabCentre(int t) {
        return GameFacts.GAME_WIDTH / 2F + (t - (tabNames.size() - 1) / 2F) * TAB_SPACING;
    }

    private int tabAt(int x, int y) {
        if (y < TABS_Y - 24 || y > TABS_Y + 10) {
            return -1;
        }
        for (int t = 0; t < tabNames.size(); t++) {
            if (Math.abs(x - tabCentre(t)) < TAB_SPACING / 2F - 8) {
                return t;
            }
        }
        return -1;
    }

    private boolean overBack(int x, int y) {
        Image b = back[0];
        if (b == null) {
            return false;
        }
        int bx = (GameFacts.GAME_WIDTH - b.getWidth(null)) / 2;
        return x > bx - 5 && x < bx + b.getWidth(null) + 5 && y > BACK_Y - 5 && y < BACK_Y + b.getHeight(null) + 5;
    }

    /** Where a row's value starts, its "<". */
    private static int valueLeft() {
        return LIST_X + LIST_W - VALUE_W - 14;
    }

    /* ------------------------------------------------------------- drawing */

    private void draw(Graphics2D rd) {
        Object antialias = rd.getRenderingHint(RenderingHints.KEY_ANTIALIASING);
        Object textAntialias = rd.getRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING);
        Stroke stroke = rd.getStroke();
        java.awt.Paint paint = rd.getPaint();
        Shape clip = rd.getClip();
        drawBox(rd);
        rd.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        drawTitle(rd, "Settings", GameFacts.GAME_WIDTH / 2F, BOX_Y + 38);
        flash = !flash;
        drawTabs(rd);
        rd.clipRect(LIST_X - 2, LIST_TOP - 2, LIST_W + 4, LIST_BOTTOM - LIST_TOP + 4);
        for (int i : tabRows[tab]) {
            drawRow(rd, i);
        }
        rd.setClip(clip);
        drawScrollbar(rd);
        drawPane(rd);
        drawFooter(rd);
        rd.setPaint(paint);
        rd.setStroke(stroke);
        rd.setRenderingHint(RenderingHints.KEY_ANTIALIASING, antialias);
        rd.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, textAntialias);
    }

    /** The pause box's frame, around the main menu's brown, with lines under the tabs and beside the list. */
    private static void drawBox(Graphics2D rd) {
        rd.setColor(new Color(0, 0, 0, 90));
        rd.fillRect(BOX_X + 6, BOX_Y + 8, BOX_W, BOX_H);
        rd.setColor(BROWN);
        rd.fillRect(BOX_X + FRAME.length, BOX_Y + FRAME.length, BOX_W - 2 * FRAME.length, BOX_H - 2 * FRAME.length);
        for (int i = 0; i < FRAME.length; i++) {
            rd.setColor(FRAME[i]);
            rd.drawRect(BOX_X + i, BOX_Y + i, BOX_W - 1 - 2 * i, BOX_H - 1 - 2 * i);
        }
        rd.setColor(new Color(0xde, 0x84, 0x39, 90));
        rd.drawLine(BOX_X + 24, TABS_Y + 12, BOX_X + BOX_W - 25, TABS_Y + 12);
        int middle = LIST_X + LIST_W + 17;
        rd.drawLine(middle, LIST_TOP, middle, LIST_BOTTOM);
    }

    /** The tabs: the shown one in the pause menu's lettering and underlined, the rest in the menu's tan. */
    private void drawTabs(Graphics2D rd) {
        for (int t = 0; t < tabNames.size(); t++) {
            String name = tabNames.get(t);
            float cx = tabCentre(t);
            Rectangle2D b = place(shape(titleFont, name), cx, TABS_Y, null).getBounds2D();
            if (t == tab) {
                pauseLetters(rd, titleFont, name, cx, TABS_Y, true);
                rd.setColor(ARROWS);
                rd.fillRect((int) b.getMinX() - 4, TABS_Y + 6, (int) b.getWidth() + 8, 3);
                if (selected == TABS) {
                    highlight(rd, (int) b.getMinX() - 14, TABS_Y - 24, (int) b.getWidth() + 28, 36);
                }
            } else {
                menuLetters(rd, titleFont, name, cx, TABS_Y, HELP, null);
            }
        }
    }

    private void drawRow(Graphics2D rd, int i) {
        Row row = rows.get(i);
        int y = LIST_TOP + rowY[i] - scroll[tab];
        if (y + ROW_H < LIST_TOP - HEADING_H || y > LIST_BOTTOM + HEADING_H) {
            return;
        }
        if (row.heading != null) {
            pauseLetters(rd, titleFont, row.heading, LIST_X + 8, y - 9, false);
        }
        boolean enabled = row.enabled();
        int width = LIST_W - (tabHeight[tab] > LIST_BOTTOM - LIST_TOP ? 12 : 0);
        if (i == selected) {
            highlight(rd, LIST_X, y, width, ROW_H - 3);
        }
        Color letters = enabled ? LETTERS : DIMMED;
        menuLetters(rd, font, row.label, LIST_X + 12, y + 17, letters, false);
        int left = valueLeft();
        if (row instanceof KeyRow key) {
            drawKeys(rd, key, left, y, i == selected);
            return;
        }
        if (enabled && !(row instanceof Button)) {
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

    /** An action's two keys, side by side: the chosen one framed, the one being chosen asking for a key. */
    private void drawKeys(Graphics2D rd, KeyRow key, int left, int y, boolean selectedRow) {
        int w = (VALUE_W - 6) / 2;
        for (int slot = 0; slot < KeyBindings.SLOTS; slot++) {
            int x = left + slot * (w + 6);
            boolean chosen = selectedRow && key.slot == slot;
            boolean asking = key == choosing && key.slot == slot;
            rd.setColor(new Color(0, 0, 0, asking ? 150 : 110));
            rd.fillRect(x, y + 2, w, ROW_H - 7);
            rd.setColor(chosen ? ARROWS : new Color(0xde, 0x84, 0x39, 80));
            rd.drawRect(x, y + 2, w - 1, ROW_H - 8);
            if (chosen) {
                rd.drawRect(x + 1, y + 3, w - 3, ROW_H - 10);
            }
            int code = KeyBindings.key(key.action, slot);
            String text = asking ? (System.currentTimeMillis() / 400L % 2L == 0L ? "Press a key" : "")
                    : KeyBindings.name(code);
            Font f = shape(smallFont, text).getBounds2D().getWidth() > w - 8 ? smallFont.deriveFont(12F) : smallFont;
            Color color = asking ? Color.WHITE : code == 0 ? DIMMED : LETTERS;
            menuLetters(rd, f, text, x + w / 2F, y + 16, color, null);
        }
    }

    /** A thin bar beside a list too long for the box, showing where the list is scrolled to. */
    private void drawScrollbar(Graphics2D rd) {
        int shown = LIST_BOTTOM - LIST_TOP;
        int whole = tabHeight[tab];
        if (whole <= shown) {
            return;
        }
        int x = LIST_X + LIST_W - 5;
        rd.setColor(new Color(0, 0, 0, 100));
        rd.fillRoundRect(x, LIST_TOP, 5, shown, 5, 5);
        int thumb = Math.max(24, shown * shown / whole);
        int top = LIST_TOP + Math.round(scroll[tab] * (shown - thumb) / (float) (whole - shown));
        rd.setColor(ARROWS);
        rd.fillRoundRect(x, top, 5, thumb, 5, 5);
    }

    /** Beside the list: the chosen setting's picture, its name, what it does, and how it is now. */
    private void drawPane(Graphics2D rd) {
        String title;
        String help;
        String status = null;
        String picture = null;
        boolean pictured = false;
        if (selected == TABS) {
            title = tabNames.get(tab);
            help = tabHelp.get(tab) + " Left and right, or Page Up and Page Down, change the tab.";
        } else if (isRow(selected)) {
            Row row = rows.get(selected);
            title = row.label;
            help = row.help();
            status = row.enabled() ? row.status() : row.disabledWhy;
            pictured = PICTURED.contains(row.label);
            picture = pictureName(row.label);
        } else {
            title = "Back";
            help = "Back to the " + (backTo == Phase.PAUSEMENU ? "pause menu." : "main menu.");
        }
        int top = LIST_TOP + 2;
        float y = top + 22;
        if (pictured) {
            drawPicture(rd, picture, title, PANE_X, top, PANE_W, PICTURE_H);
            y += PICTURE_H + 12;
        }
        pauseLetters(rd, titleFont, title, PANE_X, y, false);
        y += 28;
        for (String line : wrap(smallFont, help, PANE_W)) {
            menuLetters(rd, smallFont, line, PANE_X, y, HELP, false);
            y += 21;
        }
        if (status != null) {
            y += 6;
            for (String line : wrap(smallFont, status, PANE_W)) {
                menuLetters(rd, smallFont, line, PANE_X, y, LETTERS, false);
                y += 21;
            }
        }
    }

    /** The file name a setting's picture has: its label in lower case, with dashes, as "laptop-battery-fps". */
    static String pictureName(String label) {
        return label.toLowerCase(java.util.Locale.ROOT).replaceAll("[^a-z0-9]+", "-").replaceAll("^-|-$", "");
    }

    /** The picture of a setting, from data/settings/(name).png; null where there is none. */
    private static Image picture(String name) {
        if (!PICTURES.containsKey(name)) {
            Image image = null;
            File file = new File("data/settings/" + name + ".png");
            if (file.isFile()) {
                try {
                    image = ImageIO.read(file);
                } catch (IOException e) {
                    System.err.println("Cannot read " + file + ": " + e.getMessage());
                }
            }
            PICTURES.put(name, image);
        }
        return PICTURES.get(name);
    }

    /** The setting's picture, or until it has one, a frame with a little landscape and its name. */
    private void drawPicture(Graphics2D rd, String name, String title, int x, int y, int w, int h) {
        Image image = name == null ? null : picture(name);
        if (image != null) {
            rd.drawImage(image, x, y, w, h, null);
            rd.setColor(new Color(0xde8439));
            rd.drawRect(x, y, w - 1, h - 1);
            return;
        }
        rd.setColor(new Color(0, 0, 0, 110));
        rd.fillRect(x, y, w, h);
        rd.setColor(new Color(0xde, 0x84, 0x39, 140));
        rd.setStroke(new BasicStroke(1.5F, BasicStroke.CAP_BUTT, BasicStroke.JOIN_MITER, 10F, new float[] {6F, 5F}, 0F));
        rd.drawRect(x + 1, y + 1, w - 3, h - 3);
        int iw = 66;
        int ih = 48;
        int ix = x + (w - iw) / 2;
        int iy = y + h / 2 - ih + 2;
        rd.setColor(DIMMED);
        rd.setStroke(new BasicStroke(2.5F, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        rd.drawRoundRect(ix, iy, iw, ih, 8, 8);
        Polygon hills = new Polygon(
                new int[] {ix + 6, ix + 25, ix + 36, ix + 45, ix + iw - 6},
                new int[] {iy + ih - 6, iy + 19, iy + 31, iy + 23, iy + ih - 6}, 5);
        rd.fill(hills);
        rd.fillOval(ix + iw - 22, iy + 8, 11, 11);
        menuLetters(rd, smallFont, title, x + w / 2F, iy + ih + 30, DIMMED, null);
    }

    /** The text broken into lines no wider than {@code width}, at the spaces. */
    private static List<String> wrap(Font f, String text, int width) {
        String key = f.getSize2D() + "|" + width + "|" + text;
        List<String> lines = WRAPPED.get(key);
        if (lines == null) {
            lines = new ArrayList<>();
            StringBuilder line = new StringBuilder();
            for (String word : text.split(" ")) {
                String longer = line.length() == 0 ? word : line + " " + word;
                if (line.length() > 0 && outline(f, longer).getBounds2D().getWidth() > width) {
                    lines.add(line.toString());
                    line.setLength(0);
                    line.append(word);
                } else {
                    line.setLength(0);
                    line.append(longer);
                }
            }
            if (line.length() > 0) {
                lines.add(line.toString());
            }
            if (WRAPPED.size() > 128) {
                WRAPPED.clear();
            }
            WRAPPED.put(key, lines);
        }
        return lines;
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
        Image b = back[selected == rows.size() && backPressed == 1 ? 1 : 0];
        if (b != null) {
            int bx = (GameFacts.GAME_WIDTH - b.getWidth(null)) / 2;
            if (selected == rows.size()) {
                highlight(rd, bx - 8, BACK_Y - 3, b.getWidth(null) + 16, b.getHeight(null) + 6);
            }
            rd.drawImage(b, bx, BACK_Y, null);
        }
        menuLetters(rd, smallFont, windowSize.typing()
                ? "Type the width, then x and the height. Enter sets it, Escape keeps the size as it is."
                : "Arrows choose and change a setting. Page Up and Page Down change the tab. Escape goes back.",
                GameFacts.GAME_WIDTH / 2F, BOX_Y + BOX_H - 16, DIMMED, null);
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

    /** The text's outline, not kept: for measuring. */
    private static Shape outline(Font f, String text) {
        return f.createGlyphVector(new java.awt.font.FontRenderContext(null, true, true), text).getOutline();
    }

    private static Shape place(Shape s, float x, float y, Boolean alignRight) {
        Rectangle2D b = s.getBounds2D();
        double dx = alignRight == null ? x - b.getCenterX() : alignRight ? x - b.getMaxX() : x - b.getMinX();
        return AffineTransform.getTranslateInstance(dx, y).createTransformedShape(s);
    }

    /* ---------------------------------------------------------------- model */

    /** One setting: its name, a line on what it does, and how it changes. */
    private abstract static class Row {
        final String label;
        final String help;
        private final Supplier<String> status;
        private BooleanSupplier enabled = () -> true;
        String disabledWhy;
        /** The tab it is on, and the heading it starts a group under (or null); set as it is added. */
        int tab;
        String heading;

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
            super("Resolution", "The size of the window's inside, in the monitor's pixels. In fullscreen, the"
                    + " resolution the game draws at, scaled to fill the monitor: lower is faster.",
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
            Dimension now = RunApp.drawingPixels();
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
            Dimension now = RunApp.drawingPixels();
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
                RunApp.setDrawingPixels(sizes.get(next)[0], sizes.get(next)[1]);
            }
        }

        /** Enter: on Custom it starts typing, otherwise it steps on. */
        void enter() {
            if (!enabled()) {
                return;
            }
            if (custom) {
                Dimension now = RunApp.drawingPixels();
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
            int minW = GameFacts.GAME_WIDTH / 2;
            int minH = GameFacts.GAME_HEIGHT / 2;
            if (w < minW || h < minH) {
                problem = "At least " + minW + "x" + minH + ".";
            } else if (w > screen.width || h > screen.height) {
                problem = "At most the monitor's " + screen.width + "x" + screen.height + ".";
            } else {
                stopTyping();
                RunApp.setDrawingPixels(w, h);
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
            Dimension now = RunApp.drawingPixels();
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

    /**
     * One action's keys: left and right choose between its two, Enter (or a
     * click) asks for a new one, Backspace or Delete clears it.
     */
    private final class KeyRow extends Row {
        final int action;
        /** The key chosen of the two. */
        int slot;
        /** What the last key chosen did, until the row is left. */
        private String note;

        KeyRow(int action) {
            super(KeyBindings.label(action), KeyBindings.help(action), null);
            this.action = action;
        }

        /** Notes are forgotten on every key row but the selected one. */
        static void unchosen(List<Row> rows) {
            for (Row row : rows) {
                if (row instanceof KeyRow key) {
                    key.note = null;
                }
            }
        }

        @Override
        void step(int direction, boolean wrap) {
            if (wrap) {
                listen();
            } else {
                slot = Math.max(0, Math.min(KeyBindings.SLOTS - 1, slot + direction));
            }
        }

        private void listen() {
            stopChoosing();
            note = null;
            CHOSEN.clear();
            choosing = this;
            choosingKey = true;
        }

        void stopListening() {
            choosingKey = false;
            CHOSEN.clear();
            if (choosing == this) {
                choosing = null;
            }
        }

        /** The key pressed while one was asked for. */
        void chosen(int keyCode) {
            if (keyCode == KeyEvent.VK_ESCAPE) {
                stopListening();
            } else if (keyCode == KeyEvent.VK_BACK_SPACE || keyCode == KeyEvent.VK_DELETE) {
                stopListening();
                clear();
            } else if (KeyBindings.reserved(keyCode)) {
                note = KeyBindings.name(keyCode) + " is the game's own: choose another key.";
            } else {
                stopListening();
                int from = KeyBindings.bind(action, slot, keyCode);
                note = from == KeyBindings.NONE ? null
                        : KeyBindings.name(keyCode) + " was on " + KeyBindings.label(from) + ": now it is here.";
            }
        }

        void clear() {
            if (KeyBindings.key(action, slot) != 0) {
                KeyBindings.bind(action, slot, 0);
                note = (slot == 0 ? "The first" : "The second") + " key is cleared.";
            }
        }

        @Override
        String value() {
            return KeyBindings.name(KeyBindings.key(action, 0)) + ", " + KeyBindings.name(KeyBindings.key(action, 1));
        }

        @Override
        String help() {
            return choosing == this
                    ? "Press the key for " + label + ". Escape keeps the key it has; Backspace clears it."
                    : super.help();
        }

        @Override
        String status() {
            if (note != null) {
                return note;
            }
            return choosing == this ? null : "Left and right choose a key, Enter changes it, Backspace clears it.";
        }
    }

    /** Something to do rather than a setting: Enter or a click does it. */
    private static final class Button extends Row {
        private final String text;
        private final Runnable action;

        Button(String label, String text, String help, Runnable action) {
            super(label, help, null);
            this.text = text;
            this.action = action;
        }

        @Override
        void step(int direction, boolean wrap) {
            if (wrap) {
                action.run();
            }
        }

        @Override
        String value() {
            return text;
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
