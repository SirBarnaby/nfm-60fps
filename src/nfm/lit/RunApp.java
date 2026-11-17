package nfm.lit;
import fallk.logmaster.HLogger;

import java.awt.*;
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
        applet.setPreferredSize(startSize());
        frame.add("Center", applet);
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

    /** The window's inside at start: 1280x720, or -Dnfm.window=<width>x<height>. */
    private static Dimension startSize() {
        String property = System.getProperty("nfm.window");
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
        if (frame == null || !frame.isVisible()) {
            return;
        }
        EventQueue.invokeLater(() -> {
            boolean toFullscreen = windowedBounds == null;
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