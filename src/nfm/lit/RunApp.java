package nfm.lit;
import fallk.logmaster.HLogger;

import java.awt.*;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.ArrayList;

class RunApp extends Panel {
    /**
     *
     */
    private static final long serialVersionUID = -8590687589434803725L;

    private static Frame frame;
    private static GameSparker applet;
    private static ArrayList<Image> icons;

    /**
     * Fetches icons of 16, 32 and 48 pixels from the 'data' folder.
     *
     * @return icons - ArrayList of icon locations
     */
    private static ArrayList<Image> getIcons() {
        if (icons == null) {
            icons = new ArrayList<>();
            int[] resols = {
                    16, 32, 48
            };
            for (int res : resols) {
                icons.add(Toolkit.getDefaultToolkit().createImage("data/misc/ico_" + res + ".png"));
            }
        }
        return icons;
    }

    public static void main(String[] strings) {
        //System.runFinalizersOnExit(true);
        HLogger.info("Need For Madness: LIT"); // Change this to the message of your preference
        /*
         * Every frame covers the whole window, so Windows clearing it first only
         * flashes the background, and through the graphics card it would flicker.
         */
        System.setProperty("sun.awt.noerasebackground", "true");
        /*
         * madness64.exe has no console, so an exception that ends the game thread
         * used to leave a frozen window and no trace. Put it in the log file.
         */
        Thread.setDefaultUncaughtExceptionHandler((thread, ex) -> {
            StringWriter trace = new StringWriter();
            ex.printStackTrace(new PrintWriter(trace));
            HLogger.severe("Uncaught exception in thread \"" + thread.getName() + "\": " + trace);
            ex.printStackTrace();
        });
        startup();
    }

    private static void startup() {
        /*
         * The scripted smoke test, the race verifier and the corpus autopilot
         * render into their own back buffer and never need the window on screen. Keep it undecorated, off-screen and
         * unfocusable so it cannot steal the mouse or keyboard from the user.
         */
        boolean autoTest = System.getProperty("nfm.autotest") != null || RaceSession.runsHidden();
        RaceSession.prepareProcess();

        frame = new Frame("Need For Madness: LIT");// Change this to the name of your preference
        if (autoTest) {
            frame.setUndecorated(true);
        }
        frame.setBackground(new Color(0, 0, 0));
        frame.setIgnoreRepaint(true);
        frame.setIconImages(getIcons());

        applet = new GameSparker();
        frame.addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent windowevent) {
                exitSequence();
            }
        });
        applet.setPreferredSize(startSize(autoTest));
        frame.add("Center", applet);
        applet.addComponentListener(new ComponentAdapter() {
            @Override
            public void componentResized(ComponentEvent e) {
                if (!fullscreen && frame.isVisible() && windowedBounds == null) {
                    windowChangedAt = System.currentTimeMillis();
                }
            }
        });
        frame.setResizable(true);
        frame.pack();
        // the picture scales to the window, so it may be smaller than 1280x720
        Insets insets = frame.getInsets();
        frame.setMinimumSize(new Dimension(GameFacts.screenWidth / 2 + insets.left + insets.right,
                GameFacts.screenHeight / 2 + insets.top + insets.bottom));
        if (autoTest) {
            /*
             * Never show the window at all. pack() already created the peer, so
             * the game can render into its off-screen buffer and the harness can
             * capture it without a visible window or a taskbar entry.
             */
            frame.setFocusableWindowState(false);
            frame.setAutoRequestFocus(false);
            frame.setLocation(-20000, -20000);
        } else {
            frame.setLocationRelativeTo(null);
            frame.setVisible(true);
        }
        applet.init();
        applet.start();
    }

    /**
     * The window's inside at start: -Dnfm.window=<width>x<height>, else the
     * size the player last had (in the monitor's pixels, so Windows' display
     * scaling is taken out), else 1280x720.
     */
    private static Dimension startSize(boolean hidden) {
        String property = System.getProperty("nfm.window");
        if (property == null && !hidden) {
            Dimension saved = GameSparker.savedWindowSize();
            if (saved != null) {
                GraphicsConfiguration screen = GraphicsEnvironment.getLocalGraphicsEnvironment()
                        .getDefaultScreenDevice().getDefaultConfiguration();
                double scale = screen.getDefaultTransform().getScaleX();
                Rectangle area = screen.getBounds();
                return new Dimension(
                        Math.max(GameFacts.screenWidth / 2, Math.min(area.width, (int) Math.round(saved.width / scale))),
                        Math.max(GameFacts.screenHeight / 2,
                                Math.min(area.height, (int) Math.round(saved.height / scale))));
            }
        }
        if (property != null) {
            String[] parts = property.toLowerCase().split("x");
            try {
                if (parts.length == 2) {
                    return new Dimension(Math.max(GameFacts.screenWidth / 2, Integer.parseInt(parts[0].trim())),
                            Math.max(GameFacts.screenHeight / 2, Integer.parseInt(parts[1].trim())));
                }
            } catch (NumberFormatException ex) {
                // fall through
            }
            HLogger.warn("Ignoring invalid nfm.window value: " + property);
        }
        return new Dimension(GameFacts.screenWidth, GameFacts.screenHeight);
    }

    /** The window's place and size before fullscreen, to go back to. */
    private static Rectangle windowedBounds;

    /**
     * Switches between a normal window and borderless fullscreen on the
     * monitor the window is on (F11 or Alt+Enter). The picture scales to fit
     * either way. Not in hidden test runs, whose window stays off screen.
     */
    static void toggleFullscreen() {
        setFullscreen(!fullscreen);
    }

    /** Fullscreen as last asked for: the window follows a moment later, on the event thread. */
    private static volatile boolean fullscreen;
    /** Fullscreen was switched since the game last saved it. */
    private static volatile boolean fullscreenChanged;

    static boolean isFullscreen() {
        return fullscreen;
    }

    /** True once after each switch, for the game to save the choice. */
    static boolean takeFullscreenChanged() {
        boolean changed = fullscreenChanged;
        fullscreenChanged = false;
        return changed;
    }

    /** Borderless fullscreen on the window's monitor, or back to the window as it was. */
    static void setFullscreen(boolean on) {
        if (frame == null || !frame.isVisible() || on == fullscreen) {
            return;
        }
        fullscreen = on;
        fullscreenChanged = true;
        EventQueue.invokeLater(() -> {
            boolean toFullscreen = fullscreen;
            if (toFullscreen == (windowedBounds != null)) {
                return; // already so
            }
            GraphicsConfiguration screen = frame.getGraphicsConfiguration();
            frame.dispose(); // decorations can only change while the window is not displayable
            if (toFullscreen) {
                windowedBounds = frame.getBounds();
                frame.setUndecorated(true);
                frame.setBounds(screen.getBounds());
            } else {
                frame.setUndecorated(false);
                frame.setBounds(windowedBounds);
                windowedBounds = null;
            }
            frame.setVisible(true);
            applet.requestFocus();
        });
    }

    /** The resolution fullscreen draws at, scaled to the monitor; null for the monitor's own. */
    private static volatile Dimension fullscreenResolution;
    private static volatile boolean fullscreenResolutionChanged;

    /** The resolution chosen for fullscreen, in the monitor's pixels; null when it is the monitor's own. */
    static Dimension fullscreenResolution() {
        Dimension chosen = fullscreenResolution;
        if (chosen == null) {
            return null;
        }
        Dimension screen = screenPixels();
        return chosen.width >= screen.width && chosen.height >= screen.height ? null : chosen;
    }

    /**
     * Fullscreen draws at {@code width} x {@code height} from now on, scaled to
     * fill the monitor (with bars if its shape is not the monitor's); the
     * monitor's own size, or larger, goes back to drawing at that.
     */
    static void setFullscreenResolution(int width, int height) {
        Dimension screen = screenPixels();
        fullscreenResolution = width >= screen.width && height >= screen.height || width <= 0 || height <= 0 ? null
                : new Dimension(Math.min(width, screen.width), Math.min(height, screen.height));
        fullscreenResolutionChanged = true;
    }

    /** True once after each change, for the game to save it. */
    static boolean takeFullscreenResolutionChanged() {
        boolean changed = fullscreenResolutionChanged;
        fullscreenResolutionChanged = false;
        return changed;
    }

    /** The size the game draws at, in the monitor's pixels: the fullscreen resolution, else the window's inside. */
    static Dimension drawingPixels() {
        if (fullscreen) {
            Dimension chosen = fullscreenResolution();
            return chosen != null ? chosen : screenPixels();
        }
        return windowPixels();
    }

    /** Sets what {@link #drawingPixels} is: the fullscreen resolution in fullscreen, else the window's size. */
    static void setDrawingPixels(int width, int height) {
        if (fullscreen) {
            setFullscreenResolution(width, height);
        } else {
            setWindowPixels(width, height);
        }
    }

    /** When the window's size last changed (0 once saved), for saving it once the player lets go. */
    private static volatile long windowChangedAt;

    /**
     * True once when the window's size changed and has stayed for a second,
     * for the game to save it (not while a window edge is being dragged).
     */
    static boolean takeWindowSizeChanged() {
        long at = windowChangedAt;
        if (at != 0L && System.currentTimeMillis() - at > 1000L) {
            windowChangedAt = 0L;
            return true;
        }
        return false;
    }

    /** Windows' display scaling for the window's monitor: 1.5 at 150%. */
    private static double scale() {
        GraphicsConfiguration screen = frame != null ? frame.getGraphicsConfiguration() : null;
        if (screen == null) {
            screen = GraphicsEnvironment.getLocalGraphicsEnvironment().getDefaultScreenDevice()
                    .getDefaultConfiguration();
        }
        return screen.getDefaultTransform().getScaleX();
    }

    /** The monitor the window is on, in its own pixels. */
    static Dimension screenPixels() {
        GraphicsConfiguration screen = frame != null ? frame.getGraphicsConfiguration() : null;
        if (screen == null) {
            screen = GraphicsEnvironment.getLocalGraphicsEnvironment().getDefaultScreenDevice()
                    .getDefaultConfiguration();
        }
        double s = screen.getDefaultTransform().getScaleX();
        Rectangle area = screen.getBounds();
        return new Dimension((int) Math.round(area.width * s), (int) Math.round(area.height * s));
    }

    /** The inside of the window in the monitor's pixels (what the game draws into). */
    static Dimension windowPixels() {
        if (applet == null) {
            return new Dimension(GameFacts.screenWidth, GameFacts.screenHeight);
        }
        double s = scale();
        return new Dimension((int) Math.round(applet.getWidth() * s), (int) Math.round(applet.getHeight() * s));
    }

    /** Hidden test runs only: resizes the off-screen window's inside, as dragging an edge would. */
    static void resizeHiddenWindow(int width, int height) {
        if (frame == null || frame.isVisible()) {
            return;
        }
        EventQueue.invokeLater(() -> {
            Insets insets = frame.getInsets();
            frame.setSize(width + insets.left + insets.right, height + insets.top + insets.bottom);
            frame.validate();
        });
    }

    /**
     * Makes the inside of the window {@code width} by {@code height} of the
     * monitor's pixels, keeping its centre where it fits on the monitor.
     * Only for a window on screen: not in fullscreen, nor in hidden runs.
     */
    static void setWindowPixels(int width, int height) {
        if (frame == null || !frame.isVisible() || fullscreen) {
            return;
        }
        EventQueue.invokeLater(() -> {
            if (!frame.isVisible() || windowedBounds != null) {
                return;
            }
            if ((frame.getExtendedState() & Frame.MAXIMIZED_BOTH) != 0) {
                frame.setExtendedState(Frame.NORMAL);
            }
            double s = scale();
            Insets insets = frame.getInsets();
            int w = (int) Math.round(width / s) + insets.left + insets.right;
            int h = (int) Math.round(height / s) + insets.top + insets.bottom;
            Rectangle old = frame.getBounds();
            Rectangle area = frame.getGraphicsConfiguration().getBounds();
            int x = old.x + (old.width - w) / 2;
            int y = old.y + (old.height - h) / 2;
            x = Math.max(area.x, Math.min(x, area.x + area.width - w));
            y = Math.max(area.y, Math.min(y, area.y + area.height - h));
            frame.setBounds(x, y, w, h);
            frame.validate();
            windowChangedAt = System.currentTimeMillis();
        });
    }

    /**
     * Gives the game a fresh native window in the same place, for going back
     * from Direct3D to drawing in software: Windows does not show GDI drawing
     * in a window a flip-model swap chain has presented to.
     */
    static void recreateWindow() {
        if (frame == null || !frame.isVisible()) {
            return;
        }
        EventQueue.invokeLater(() -> {
            if (!frame.isVisible()) {
                return;
            }
            Rectangle bounds = frame.getBounds();
            boolean focused = frame.isFocused();
            frame.dispose();
            frame.setBounds(bounds);
            frame.setVisible(true);
            if (focused) {
                applet.requestFocus();
            }
        });
    }

    /** Closes the game as the window's close button does, from any thread (Exit Game on the main menu). */
    static void requestExit() {
        EventQueue.invokeLater(RunApp::exitSequence);
    }

    private static void exitSequence() {
        applet.stop();
        frame.removeAll();
        try {
            Thread.sleep(200L);
        } catch (Exception exception) {
        }
        applet = null;
        System.exit(0);
    }
}