package nfm.lit;
import fallk.logmaster.HLogger;

import java.awt.*;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
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
        try {
            javax.swing.UIManager.setLookAndFeel(javax.swing.UIManager.getSystemLookAndFeelClassName());
        } catch (Exception ex) {
            HLogger.warn("Could not setup System Look&Feel: " + ex.toString());
        }
        startup();
    }

    private static void startup() {
        /*
         * The scripted smoke test renders into its own back buffer and never
         * needs the window on screen. Keep it undecorated, off-screen and
         * unfocusable so it cannot steal the mouse or keyboard from the user.
         */
        boolean autoTest = System.getProperty("nfm.autotest") != null;

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
        applet.setPreferredSize(new Dimension(GameFacts.screenWidth, GameFacts.screenHeight));// The resolution of your game goes here
        frame.add("Center", applet);
        frame.setResizable(true);// If you plan to make you game support changes in resolution, you can comment out this line.
        frame.pack();
        frame.setMinimumSize(frame.getSize());
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