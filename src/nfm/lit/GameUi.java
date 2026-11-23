package nfm.lit;

import fallk.logmaster.HLogger;
import nfm.lit.audio.RadicalMod;
import nfm.lit.audio.RadicalMusic;
import nfm.lit.audio.Sound3D;
import nfm.lit.audio.SoundClipThreaded;
import nfm.lit.audio.SoundClipUnthreaded;
import nfm.lit.audio.SoundManager;
import nfm.lit.audio.TrackZipLoader;

import java.io.*;
import java.net.*;
import java.awt.*;
import java.awt.color.ColorSpace;
import java.awt.image.*;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Random;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

// Original resolution: 670x400

/**
 * The menus, the HUD, the loading screens, and the sounds and music that go
 * with them: everything drawn in 2D. It was called xtGraphics in the
 * original source.
 * 
 * Layout overhauls by Jacherr
 * 
 * @author Omar Waly
 * @author Jacherr
 */
public class GameUi extends Panel implements Runnable, RaceEvents {
    public Metrics gameMetrics = new Metrics();

    /**
     *
     */
    private static final long serialVersionUID = -6463312620664057856L;

    SoundManager sm = new SoundManager();
    /**
     * starting colors for the special screen in the credits
     */
    private final int[] credColors = {
            25, 50, 100
    };
    /**
     * test image for the network load feature
     */
    private Image aimLogo;

    private Graphics2D rd;
    private ImageObserver ob;
    private final GameSparker app;
    public Phase fase;
    private Phase oldfase;
    public int starcnt;
    public int unlocked;
    private int lockcnt;
    public int opselect;
    private boolean shaded;
    public int flipo;
    private boolean nextc;
    private int gatey;
    public int looped;
    public final int[] sc;
    public boolean setnumber;
    private boolean holdit;
    private int holdcnt;
    public boolean winner;
    private int[] flexpix;
    private Image fleximg;
    private int flatrstart;
    private Thread runner;
    private volatile int runtyp;
    private Image odmg;
    private Image opwr;
    private Image opos;
    private Image owas;
    private Image olap;
    private Image oyourwasted;
    private Image oyoulost;
    private Image oyouwon;
    private Image oyouwastedem;
    private Image ogameh;
    private Image oloadingmusic;
    private Image oflaot;
    private Image dmg;
    private Image pwr;
    private Image pos;
    private Image was;
    private Image lap;
    private Image select;
    private Image loadingmusic;
    private Image yourwasted;
    private Image youlost;
    private Image youwon;
    private Image youwastedem;
    private Image gameh;
    private Image congrd;
    private Image gameov;
    private Image carsbg;
    private Image pgate;
    Image selectcar;
    private Image statb;
    private Image statbo;
    public Image mdness;
    private Image paused;
    private Image radicalplay;
    private Image logocars;
    private Image logomadnes;
    private Image logomadbg;
    private Image byrd;
    private Image opback;
    private Image nfmcoms;
    private Image opti;
    private Image bgmain;
    /* GameSparker.catchlink uses this */
    public Image rpro;
    private Image nfmcom;
    private Image flaot;
    private Image fixhoop;
    private Image sarrow;
    private Image stunts;
    private Image racing;
    private Image wasting;
    private Image plus;
    private Image space;
    private Image arrows;
    private Image chil;
    private Image ory;
    private Image kz;
    private Image kx;
    private Image kv;
    private Image kp;
    private Image km;
    private Image kn;
    private Image kenter;
    private Image nfm;
    private final Image[][] trackbg;
    public final Image[] dude;
    private final Image[] dudeb;
    private int duds;
    private int dudo;
    final Image[] next;
    final Image[] back;
    private final Image[] contin;
    private final Image[] ostar;
    private final Image[] star;
    private int pcontin;
    private int pnext;
    private int pback;
    private int pstar;

    private final Image[] ocntdn;
    private final Image[] cntdn;
    private int gocnt;
    private final boolean[] pengs;
    private boolean aird;
    private boolean grrd;
    private boolean pwastd;
    public boolean mutes;
    /** The menus' click, which the second speaker also plays where the original menus were silent. */
    static final String CLICK_CLIP = "tick";
    /** Wrecking a car: the original had no sound for it, so it plays only on the second speaker. */
    static final String WRECK_CLIP = "tliu";
    /** The menu and the highlighted item as the last frame left them: a move clicks on the second speaker. */
    private int clickedMenu = -1;
    private long clickedMenuAt;

    // private RadicalMusic stages;
    // private RadicalMusic cars;
    // RadicalMusic tracks[];

    // public final RadicalMod[] stracks;
    public static RadicalMusic strack;
    public static RadicalMusic intertrack = new RadicalMod();
    //public static RadicalMusic intercar = new RadicalMod();
    public static boolean loadedt = false;

    private int lastload;
    private boolean mutem;
    private boolean macn;
    private boolean arrace;
    private int ana;
    private int cntan;
    private int cntovn;
    private boolean flk;
    private int tcnt;
    private boolean tflk;
    private String say;
    private boolean wasay;
    private int clear;
    private int posit;
    private int wasted;
    private int laps;
    private final int[] dested;
    public final String[] names = {
            "Tornado Shark", "Formula 7", "Wow Caninaro", "La Vite Crab", "Nimi", "MAX Revenge", "Lead Oxide",
            "Kool Kat", "Drifter X", "Sword of Justice", "High Rider", "EL KING", "Mighty Eight", "M A S H E E N",
            "Radical One", "DR Monstaa"
    };
    private int dmcnt;
    private boolean dmflk;
    private int pwcnt;
    private boolean pwflk;
    private final String[][] adj = {
            {
                    "Cool", "Alright", "Nice"
            },
            {
                    "Wicked", "Amazing", "Super"
            },
            {
                    "Awesome", "Ripping", "Radical"
            },
            {
                    "What the...?", "Your a super star!!!!", "Who are you again...?"
            },
            {
                    "surf style", "off the lip", "bounce back"
            }
    };
    private final String[] exlm = {
            "!", "!!", "!!!"
    };
    private String loop;
    private String spin;
    private String asay;
    private int auscnt;
    private boolean aflk;
    static final int[] sndsize = { // unused
            106, 76, 56, 116, 92, 208, 70, 80, 152, 102, 27, 65, 52, 30, 151, 129, 70, 0
    };
    private final Image hello;
    private final Image sign;
    private final Image loadbar;
    private int kbload;
    /** How much is loaded, for the loading bar; images, sounds and models load on threads of their own. */
    public volatile int dnload;
    private float shload;
    private int radpx;
    private int pin;
    /** How far the menus' grey backdrop has scrolled up, and the stage pictures along. */
    private int bgScroll;
    private int trackScroll;
    /* the full-screen art, fitted to the picture's shape; null until drawn at it (see fitScreen) */
    private Image backdropShown;
    private Image carsbgShown;
    private Image[][] trackbgShown;
    private int trkl;
    private int trklim;
    private final float hipno = 1.0F;
    private int flkat;
    private int movly;

    private int press_enter_to_continue_height; // see fitScreen

    /* TODO: define each page's constants in their own files */
    /* main menu */
    private int main_menu_height_origin; // see fitScreen

    private int main_menu_button_height = 22;
    /** The main menu's items, in order: the first three are in its picture, the rest are lettered under it. */
    static final int MENU_PLAY = 0;
    static final int MENU_INSTRUCTIONS = 1;
    static final int MENU_CREDITS = 2;
    static final int MENU_CUSTOM = 3;
    static final int MENU_SPLIT = 4;
    static final int MENU_SETTINGS = 5;
    static final int MENU_EXIT = 6;
    private static final int MENU_ITEMS = 7;
    /** Each item's box: how wide, and where it is at the top (see fitScreen), 31 pixels apart give or take. */
    private static final int[] MENU_WIDTH = {110, 196, 85, 150, 140, 100, 110};
    private static final int[] MENU_TOP = {246, 275, 306, 337, 368, 399, 430};
    /** The colour each item's box flickers to while chosen. */
    private static final Color[] MENU_FLICKER = {new Color(200, 255, 0), new Color(200, 128, 0), new Color(200, 0, 0),
        new Color(255, 200, 0), new Color(0, 160, 220), new Color(200, 0, 128), new Color(128, 0, 200)};
    private final int[] menuY = new int[MENU_ITEMS]; // see fitScreen
    /** Where the menu's brown backdrop is opened up for the items under its picture, and by how much (its rows stay whole). */
    private static final int OPBACK_GAP_AT = 90;
    private static final int OPBACK_GAP = 100;

    /** The settings screen, made when first opened (the menu font is registered by then). */
    private SettingsScreen settingsScreen;

    /** Split screen's car select and results. */
    final SplitScreens splitScreens = new SplitScreens(this);

    /** The custom game's page (see {@link SettingsScreen#customGame}), made when first opened. */
    private SettingsScreen customGameScreen;
    /** Race! was chosen on the custom game's page: off to the car select once its frame is done. */
    private boolean customRaceChosen;

    /** Race Again after a split-screen race: the stage select goes on with the same stage at once. */
    boolean raceAgain;

    private int insano_mainmenu_x;
    private int insano_mainmenu_y;
    /* these exist to reset the head to origin */
    private int insano_mainmenu_x_original;
    private int insano_mainmenu_y_original;

    private int insano_mainmenu_target_x;
    private int insano_mainmenu_target_y;

    /* car select */
    private int car_select_next_back_button_y; // see fitScreen
    private int car_select_back_button_x = 30;
    private int car_select_next_button_x; // see fitScreen
    private int car_select_continue_button_y; // see fitScreen

    /* stage select */
    private int stage_select_next_back_button_y; // see fitScreen
    private int stage_select_back_button_x = car_select_back_button_x;
    private int stage_select_next_button_x; // see fitScreen
    private int stage_select_continue_button_y; // see fitScreen
    private int stage_select_text_y = 25;

    /* post-game continue */
    private int post_game_continue_button_y; // see fitScreen

    /* pause */
    /* set in loadimages */
    private int pausebox_y = 0;
    private int pausebox_option_gap = 26;
    private int pausebox_option_start_off = 37;
    /* Resume Game, Instant Replay, Game Instructions, Settings, Quit Game */
    private final int[] pausebox_op_widths = {137, 155, 190, 104, 109};
    /* Settings is lettered into a gap opened in paused.gif above Quit Game */
    private static final int PAUSE_SETTINGS = 3;
    private static final int pausebox_gap_at = 115;
    private int pausebox_op_height = 22;
    /* the options arent quite centered so correct for it */
    private int pausebox_op_x_correction = -3;

    /* credits */
    private int credits_next_button_y; // see fitScreen

    /* instructions */
    private int instructions_next_back_button_y; // see fitScreen
    private int instructions_next_button_x; // see fitScreen
    private int instructions_back_button_x = stage_select_back_button_x;
    private int instructions_continue_button_x; // see fitScreen

    private final int[] pgatx = {
            146, 175, 215, 267, 334, 401, 452, 493, 521
    };
    private final int[] pgaty = {
            168, 188, 201, 212, 219, 214, 203, 189, 171
    };
    private final int[] pgady;
    private final boolean[] pgas;
    private int lxm;
    private int lym;
    private int pwait;
    private int stopcnt;
    private int cntwis;
    private int crshturn;
    private int bfcrash;
    private int bfskid;
    private boolean crashup;
    private boolean skidup;
    private int skflg;
    private int dskflg;
    private int flatr;
    private int flyr;
    private int flyrdest;
    private int flang;
    private int flangados;
    private float blackn;
    private float blacknados;


    static int practicemode = 0;

    private int dev_up = 0;
    private int dev_down = 0;
    private int dev_left = 0;
    private int dev_right = 0;
    public boolean devtriggered = false;

    public boolean fixedsort = false;

    public boolean nplayers_debug = false;
    public int nplayers_override = 7;

    public boolean debugmode = false;

    public int spectate = 0;

    public int nfmmode = 2;

    public boolean arrowDisabled = false;
    public boolean opstatusDisabled = false;

    private int car_select_delay = 3;   // this is the most optimal value, anything less is shit and causes tick sound to go nuts

    // server test
    public String serverIP = "127.0.0.1";
    public String authServerIP = "127.0.0.1";
    public int serverPort = 6900;
    public int authServerport = 6930;
    Socket socket = null;
    BufferedReader serverResponse = null;
    public String serverMessage = "Connecting to authentication server...";

    /**
     * Filter images
     *
     * @param img  Image to filter
     * @param type Integer of what filter to apply
     * @author Kaffeinated
     */
    public void filterImage(Image img, int type) {
        BufferedImage buff_img = new BufferedImage(img.getWidth(null), img.getHeight(null),
                BufferedImage.TYPE_INT_ARGB);
        Graphics2D rd_sep = buff_img.createGraphics();
        rd_sep.drawImage(img, 0, 0, null);
        rd_sep.dispose();
        // now buff_img = BufferedImage img
        if (type == 0) { //////// grayscale
            BufferedImage gray = new BufferedImage(buff_img.getWidth(), buff_img.getHeight(),
                    BufferedImage.TYPE_INT_ARGB);
            ColorConvertOp op = new ColorConvertOp(ColorSpace.getInstance(ColorSpace.CS_GRAY), null);
            op.filter(buff_img, gray);
            rd.drawImage(gray, 0, 0, null);
        }
        if (type == 1) { ///////// sepia tone
            BufferedImage sepia = new BufferedImage(buff_img.getWidth(), buff_img.getHeight(),
                    BufferedImage.TYPE_INT_ARGB);
            int sepiaDepth = 20;
            int w = buff_img.getWidth();
            int h = buff_img.getHeight();
            int[] pixels = new int[w * h * 4];
            buff_img.getRaster().getPixels(0, 0, w, h, pixels);
            for (int x = 0; x < buff_img.getWidth(); x++) {
                for (int y = 0; y < buff_img.getHeight(); y++) {
                    int rgb = buff_img.getRGB(x, y);
                    Color color = new Color(rgb, true);
                    int r = color.getRed();
                    int g = color.getGreen();
                    int b = color.getBlue();
                    int gry = (r + g + b) / 3;
                    r = g = b = gry;
                    r = r + (sepiaDepth * 2);
                    g = g + sepiaDepth;
                    if (r > 255) {
                        r = 255;
                    }
                    if (g > 255) {
                        g = 255;
                    }
                    if (b > 255) {
                        b = 255;
                    }
                    b -= 20;
                    if (b < 0) {
                        b = 0;
                    }
                    if (b > 255) {
                        b = 255;
                    }
                    color = new Color(r, g, b, color.getAlpha());
                    sepia.setRGB(x, y, color.getRGB());
                }
            }
            rd.drawImage(sepia, 0, 0, null);
        }
        if (type == 2) { /////////// inverts colors
            BufferedImage invert = new BufferedImage(buff_img.getWidth(), buff_img.getHeight(),
                    BufferedImage.TYPE_INT_ARGB);
            int w = buff_img.getWidth();
            int h = buff_img.getHeight();
            int[] pixels = new int[w * h * 4];
            buff_img.getRaster().getPixels(0, 0, w, h, pixels);
            for (int x = 0; x < buff_img.getWidth(); x++) {
                for (int y = 0; y < buff_img.getHeight(); y++) {
                    int rgb = buff_img.getRGB(x, y);
                    Color color = new Color(rgb, true);
                    int r = color.getRed();
                    int g = color.getGreen();
                    int b = color.getBlue();
                    r = 255 - r;
                    g = 255 - g;
                    b = 255 - b;
                    if (r > 255) {
                        r = 255;
                    }
                    if (r < 0) {
                        r = 0;
                    }
                    if (g > 255) {
                        g = 255;
                    }
                    if (g < 0) {
                        g = 0;
                    }
                    if (b > 255) {
                        b = 255;
                    }
                    if (b < 0) {
                        b = 0;
                    }
                    color = new Color(r, g, b, color.getAlpha());
                    invert.setRGB(x, y, color.getRGB());
                }
            }
            rd.drawImage(invert, 0, 0, null);
        }
        if (type == 3) { /////// alternate invert
            BufferedImage altinvert = new BufferedImage(buff_img.getWidth(), buff_img.getHeight(),
                    BufferedImage.TYPE_INT_ARGB);
            int w = buff_img.getWidth();
            int h = buff_img.getHeight();
            int[] pixels = new int[w * h * 4];
            buff_img.getRaster().getPixels(0, 0, w, h, pixels);
            for (int x = 0; x < buff_img.getWidth(); x++) {
                for (int y = 0; y < buff_img.getHeight(); y++) {
                    int rgb = buff_img.getRGB(x, y);
                    Color color = new Color(rgb, true);
                    int r = color.getRed();
                    int g = color.getGreen();
                    int b = color.getBlue();
                    r = Medium.csky[0] - r;
                    g = Medium.csky[1] - g;
                    b = Medium.csky[2] - b;
                    if (r > 255) {
                        r = 255;
                    }
                    if (r < 0) {
                        r = 0;
                    }
                    if (g > 255) {
                        g = 255;
                    }
                    if (g < 0) {
                        g = 0;
                    }
                    if (b > 255) {
                        b = 255;
                    }
                    if (b < 0) {
                        b = 0;
                    }
                    color = new Color(r, g, b, color.getAlpha());
                    altinvert.setRGB(x, y, color.getRGB());
                }
            }
            rd.drawImage(altinvert, 0, 0, null);
        }
        if (type == 4) { /////// washout type filter
            BufferedImage washout = new BufferedImage(buff_img.getWidth(), buff_img.getHeight(),
                    BufferedImage.TYPE_INT_ARGB);
            int w = buff_img.getWidth();
            int h = buff_img.getHeight();
            int[] pixels = new int[w * h * 4];
            buff_img.getRaster().getPixels(0, 0, w, h, pixels);
            for (int x = 0; x < buff_img.getWidth(); x++) {
                for (int y = 0; y < buff_img.getHeight(); y++) {
                    int rgb = buff_img.getRGB(x, y);
                    Color color = new Color(rgb, true);
                    int r = color.getRed();
                    int g = color.getGreen();
                    int b = color.getBlue();
                    int nr = r / 2;
                    int ng = g / 2;
                    r = nr + r;
                    g = ng + g;
                    b = b + (b / 2);
                    if (r > 255) {
                        r = 255;
                    }
                    if (r < 0) {
                        r = 0;
                    }
                    if (g > 255) {
                        g = 255;
                    }
                    if (g < 0) {
                        g = 0;
                    }
                    if (b > 255) {
                        b = 255;
                    }
                    if (b < 0) {
                        b = 0;
                    }
                    color = new Color(r, g, b, color.getAlpha());
                    washout.setRGB(x, y, color.getRGB());
                }
            }
            rd.drawImage(washout, 0, 0, null);
        }
    }

    /**
     * Special color effect in credits
     *
     * @param image the image
     * @return image the same image but filtered with color
     * @author Kaffeinated
     */
    public Image credsnap(Image image) {
        int height = GameFacts.screenHeight; // image.getHeight(ob);
        int width = image.getWidth(ob);
        int pixels[] = new int[width * height];

        if (credColors[0] < 200) {
            credColors[0] += 5;
        } else {
            do {
                credColors[0] -= 5;
            } while (credColors[0] > 25);
        }
        if (credColors[1] < 100) {
            credColors[1] += 5;
        } else {
            do {
                credColors[1] -= 10;
            } while (credColors[1] > 50);
        }
        if (credColors[2] < 30) {
            credColors[2] += 5;
        } else {
            do {
                credColors[2] -= 5;
            } while (credColors[2] > 100);
        }
        PixelGrabber grabber = new PixelGrabber(image, 0, 0, width, height, pixels, 0, width);
        try {
            grabber.grabPixels();
        } catch (InterruptedException ignored) {
        }
        for (int k = 0; k < width * height; k++) {
            Color color = new Color(pixels[k]);
            int red = (int) (color.getRed() * (credColors[0] / 100F));
            if (red > 225) {
                red = 225;
            }
            if (red < 0) {
                red = 0;
            }
            int green = (int) (color.getGreen() * (credColors[1] / 100F));
            if (green > 225) {
                green = 225;
            }
            if (green < 0) {
                green = 0;
            }
            int blue = (int) (color.getBlue() * (credColors[2] / 100F));
            if (blue > 225) {
                blue = 225;
            }
            if (blue < 0) {
                blue = 0;
            }
            Color tinted = new Color(red, green, blue, 50); /// last is alpha
            pixels[k] = tinted.getRGB();
        }
        return createImage(new MemoryImageSource(width, height, pixels, 0, width));
    }

    public int colorinvert(int r, int g, int b) {
        int hex = (0xff << 24) | ((r & 0xff) << 16) | ((g & 0xff) << 8) | (b & 0xff);
        return 0xFFFFFF - hex;
    }

    /**
     * drawcs for images
     *
     * @param img image to draw
     * @param y   y value
     * @author Kaffeinated
     */
    public void paintcs(Image img, int y) {
        rd.drawImage(img, (img.getWidth(null) / 2) - GameFacts.screenWidth, y, null);
    }

    /**
     * draw text over conto
     *
     * @param text   text to draw
     * @param contos conto to overlay
     * @author Rafa
     * @author Kaffeinated
     */
    public void drawOver(String text, ContO contos) {
        int x = Medium.cx + (contos.x - Medium.x - Medium.cx);
        int y = Medium.cy + (contos.y - Medium.y - Medium.cy);
        int z = Medium.cz + (contos.z - Medium.z - Medium.cz);

        x = Utility.rotSingle(x, z, Medium.cx, Medium.cz, Medium.xz, RadicalMath.sin(Medium.xz),
                RadicalMath.cos(Medium.xz))[0];
        y = Utility.rotSingle(y, z, Medium.cy, Medium.cz, Medium.zy, RadicalMath.sin(Medium.zy),
                RadicalMath.cos(Medium.zy))[0];
        z = Utility.rotSingle(y, z, Medium.cy, Medium.cz, Medium.zy, RadicalMath.sin(Medium.zy),
                RadicalMath.cos(Medium.zy))[1];

        final int xScreenCoor = Utility.xs(x, z);
        final int yScreenCoor = Utility.cYs(y, z);

        rd.drawString("" + text, xScreenCoor, yScreenCoor);
    }

    public void carspergame(CheckPoints checkpoints) {
        if (!setnumber) {
            setnumber = true;

            if (SplitRace.on) {
                GameFacts.numberOfPlayers = 2 + SplitRace.rivals; // both players and the computer cars chosen
            } else if (CustomGame.on) {
                GameFacts.numberOfPlayers = 1 + CustomGame.rivalsNow(); // the player and the computer cars chosen
            } else if (!nplayers_debug) {
                switch (checkpoints.stage) {
                    // case 2:
                    // GameFacts.numberOfPlayers = 10;
                    // break;
                    // case 5:
                    // GameFacts.numberOfPlayers = 13;
                    // break;
                    default:
                        GameFacts.numberOfPlayers = 7;
                        break;
                }
            } else {
                GameFacts.numberOfPlayers = nplayers_override;
            }

            if (GameFacts.numberOfPlayers == 1) {
                practicemode = 1;
            } else {
                practicemode = 0;
            }
        } else {
            fase = Phase.STAGESELECTTRIGGER;
        }
    }

    private boolean over(Image image, int mouseX, int mouseY, int x, int y) {
        int height = image.getHeight(ob);
        int width = image.getWidth(ob);
        return mouseX > x - 5 && mouseX < x + width + 5 && mouseY > y - 5 && mouseY < y + height + 5;
    }

    public void cantgo(Control control) {
        pnext = 0;
        trackbg(true);

        rd.setFont(new Font("SansSerif", 1, 13));
        FontHandler.fMetrics = rd.getFontMetrics();

        drawcs(400, "This stage will be unlocked when stage " + unlocked + " is complete!", 177, 177, 177, 3);

        int gate = 0;
        int pgate_gap = 30;
        int pgate_amt = 9;
        int pgate_start = Utility.centeredWidthX((pgate_amt - 1) * pgate_gap);
        do {
            rd.drawImage(pgate, pgate_start + gate * pgate_gap, 190, null);
        } while (++gate < pgate_amt);

        rd.setFont(new Font("SansSerif", 1, 11));
        FontHandler.fMetrics = rd.getFontMetrics();
        if (aflk) {
            drawcs(300, "[ Stage " + (unlocked + 1) + " Locked ]", 255, 128, 0, 3);
            aflk = false;
        } else {
            drawcs(300, "[ Stage " + (unlocked + 1) + " Locked ]", 255, 0, 0, 3);
            aflk = true;
        }

        rd.drawImage(select, Utility.centeredImageX(select), stage_select_text_y, null);
        rd.drawImage(back[pback], stage_select_back_button_x, stage_select_next_back_button_y, null);

        rd.setFont(new Font("SansSerif", 1, 11));
        FontHandler.fMetrics = rd.getFontMetrics();
        drawcs(GameFacts.screenWidth - 5, "You can also use Keyboard Arrows and Enter to navigate.", 82, 90, 0, 3);
        lockcnt--;
        if (lockcnt == 0 || control.enter || control.handb || control.left) {
            sm.play("tick");
            control.left = false;
            control.handb = false;
            control.enter = false;
            fase = Phase.STAGESELECT;
        }
    }

    public void loadingstage(int stage) {
        /*
         * 
         * looks better without idc fight me
         * 
         */
        trackbg(false);
        rd.setColor(new Color(177, 177, 177));
        rd.fillRoundRect(200, 150, 270, 52, 20, 40);
        rd.setColor(new Color(120, 120, 120));
        rd.drawRoundRect(200, 150, 270, 52, 20, 40);

        rd.setFont(new Font("SansSerif", 1, 13));
        FontHandler.fMetrics = rd.getFontMetrics();
        drawcs(180, "Loading Stage " + stage + ", please wait...", 0, 0, 0, 3);
        rd.drawImage(select, Utility.centeredImageX(select), stage_select_text_y, null);

        // rd.drawImage(br, 0, 0, null);
        rd.setFont(new Font("SansSerif", 1, 11));
        FontHandler.fMetrics = rd.getFontMetrics();
        drawcs(GameFacts.screenHeight - 5, "You can also use Keyboard Arrows and Enter to navigate.", 82, 90, 0, 3);

        // app.repaint();

        // if (lastload != -22) {
        // lastload = -22;
        // } else {
        // stages.stop();
        // }
    }

    public void drmcheck() {        // this is very barebones and not sustainable
        app.repaint();
        trackbg(false);

        // rd.drawImage(br, 0, 0, null);
        rd.setFont(new Font("SansSerif", 1, 40));
        FontHandler.fMetrics = rd.getFontMetrics();

        drawcs(GameFacts.screenHeight/2, serverMessage, 255, 255, 255, 3);
    }

    public void drmchecksuccess() {
        app.repaint();
        fase = Phase.AWAITLOADDISMISSAL;
    }

    /** Opens the settings screen; Back returns to {@code from}, the main menu or the pause menu. */
    private void openSettings(Phase from) {
        openSettings(from, -1);
    }

    /** Opens the settings screen on a tab (-1 the one last shown); Back returns to {@code from}. */
    void openSettings(Phase from, int tab) {
        if (settingsScreen == null) {
            settingsScreen = new SettingsScreen(back);
        }
        settingsScreen.backTo = from;
        settingsScreen.open(tab);
        fase = Phase.SETTINGS;
    }

    /**
     * Opens the custom game's page: from the main menu, which turns the
     * custom game on, and again after each of its races.
     */
    void openCustomGame() {
        if (customGameScreen == null) {
            customGameScreen = SettingsScreen.customGame(back, names, () -> customRaceChosen = true);
        }
        CustomGame.on = true;
        customRaceChosen = false;
        customGameScreen.open(-1);
        fase = Phase.CUSTOMGAME;
    }

    /**
     * One tick of the custom game's page, over the main menu's moving
     * background: Race! goes on to the car select, Back or Escape to the
     * main menu.
     */
    void customGame(Control control) {
        scrollBackdrop(20);
        boolean leave = customGameScreen.frame(rd, control);
        if (customRaceChosen) {
            customRaceChosen = false;
            fase = Phase.CARSELECTTRIGGER;
        } else if (leave) {
            opselect = MENU_CUSTOM;
            fase = Phase.MAINMENU;
        }
    }

    /** The race is over (won, lost or wasted), its banner up until Enter: for the hidden test run. */
    boolean raceOver() {
        return holdit;
    }

    /** For the hidden test run: chooses a row of the custom game's page by its name. */
    void selectCustomGameRow(String label) {
        if (customGameScreen != null) {
            customGameScreen.select(label);
        }
    }

    /** For the smoke test: chooses a setting on the open settings screen by its name. */
    void selectSetting(String label) {
        if (settingsScreen != null) {
            settingsScreen.select(label);
        }
    }

    /** The settings screen is open over a paused race, which is then drawn live behind it. */
    boolean settingsOverRace() {
        return fase == Phase.SETTINGS && settingsScreen != null && settingsScreen.backTo == Phase.PAUSEMENU;
    }

    /**
     * The settings screen, over the main menu's moving background or the
     * paused race: drawn live behind it ({@code raceBehind}), so a change to
     * the picture shows at once, or else its frozen picture.
     */
    public void settings(Control control, boolean raceBehind) {
        SettingsScreen screen = settingsScreen;
        if (raceBehind) {
            // the race is drawn underneath, as it is now
        } else if (screen.backTo == Phase.PAUSEMENU) {
            rd.drawImage(fleximg, 0, 0, null);
        } else if (screen.backTo == Phase.SPLITCARSELECT) {
            rd.drawImage(carsBackdrop(), 0, 0, null);
        } else {
            scrollBackdrop(20);
        }
        if (screen.frame(rd, control)) {
            fase = screen.backTo;
            opselect = screen.backTo == Phase.PAUSEMENU ? PAUSE_SETTINGS : MENU_SETTINGS;
        }
    }

    public void inst(Control control) {
        if (flipo == 0) {
            flipo = 1;
            bgScroll = 0;
        }
        if (flipo == 2) {
            flipo = 3;
            dudo = 200;
        }
        if (flipo == 4) {
            flipo = 5;
            dudo = 250;
        }
        if (flipo == 6) {
            flipo = 7;
            dudo = 200;
        }
        if (flipo == 8) {
            flipo = 9;
            dudo = 250;
        }
        if (flipo == 10) {
            flipo = 11;
            dudo = 200;
        }
        if (flipo == 12) {
            flipo = 13;
            dudo = 200;
        }
        if (flipo == 14) {
            flipo = 15;
            dudo = 100;
        }
        scrollBackdrop(2);
        aflk = !aflk;
        int hint_all_width = dude[duds].getWidth(null) + oflaot.getWidth(null);
        int start_x = Utility.centeredWidthX(hint_all_width);
        int flaot_x = start_x + dude[duds].getWidth(null);
        int text_x = flaot_x + 70;
        if (flipo != 1) {
            if (dudo > 0) {
                if (aflk) {
                    if (Math.random() > Math.random()) {
                        duds = (int) (Math.random() * 3D);
                    } else {
                        duds = (int) (Math.random() * 2D);
                    }
                }
                dudo--;
            } else {
                duds = 0;
            }
            rd.drawImage(dude[duds], start_x, 10, null);
            rd.drawImage(oflaot, flaot_x, 42, null);
        }
        rd.setColor(new Color(0, 0, 0));
        rd.setFont(new Font("SansSerif", 1, 13));

        int dudetext_base_y = 65;
        int dudetext_line_gap = 20;

        if (flipo == 3 || flipo == 5) {// 2-3 page
            if (flipo == 3) {
                rd.drawString("Hello!  This will be changed eventually.", text_x, dudetext_base_y);

                //
                // rd.drawImage(nfm, 519, 30, null);
                rd.drawString("In this game there are two ways to complete a stage.", text_x,
                        dudetext_base_y + dudetext_line_gap * 2);
                rd.drawString("One is by racing and finishing in first place, the other is by", text_x,
                        dudetext_base_y + dudetext_line_gap * 3);
                rd.drawString("wasting and crashing all the other cars in the stage!", text_x,
                        dudetext_base_y + dudetext_line_gap * 4);
            } else {
                rd.setColor(new Color(100, 100, 100));
                rd.drawString("While racing, you will need to focus on going fast and passing", text_x,
                        dudetext_base_y);
                rd.drawString("through all the checkpoints in the track. To complete a lap, you", text_x,
                        dudetext_base_y + dudetext_line_gap);
                rd.drawString("must not miss a checkpoint.", text_x, dudetext_base_y + dudetext_line_gap * 2);
                rd.drawString("While wasting, you will just need to chase the other cars and", text_x,
                        dudetext_base_y + dudetext_line_gap * 3);
                rd.drawString("crash into them (without worrying about track and checkpoints).", text_x,
                        dudetext_base_y + dudetext_line_gap * 4);
                rd.setColor(new Color(0, 0, 0));
            }

            int middle = Utility.centeredImageX(ory);
            rd.drawImage(racing, middle - racing.getWidth(null) - 30, 200, null);
            rd.drawImage(ory, middle, 250, null);
            rd.drawImage(wasting, middle + ory.getWidth(null) + 30, 200, null);
            rd.setFont(new Font("SansSerif", 1, 11));
            rd.drawString("Checkpoint", middle - ory.getWidth(null) - 15, 200);
            rd.setFont(new Font("SansSerif", 1, 13));
            rd.drawString("Drive your car using the Arrow Keys and Spacebar :", start_x, 330);
            rd.drawImage(space, start_x + 50, 370, null);
            rd.drawImage(arrows, middle + 120, 340, null);
            rd.setFont(new Font("SansSerif", 1, 11));
            rd.drawString("(When your car is on the ground Spacebar is for Handbrake)", start_x, 350);
            rd.drawString("Accelerate", middle + 130, 335);
            rd.drawString("Brake/Reverse", middle + 120, 415);
            rd.drawString("Turn left", middle + 70, 390);
            rd.drawString("Turn right", middle + 210, 390);

        }
        if (flipo == 7 || flipo == 9) { // 4-5 page
            if (flipo == 7) {
                rd.drawString("Whether you are racing or wasting the other cars you will need", text_x,
                        dudetext_base_y);
                rd.drawString("to power up your car.", text_x, dudetext_base_y + dudetext_line_gap);
                rd.drawString("=> More 'Power' makes your car become faster and stronger!", text_x,
                        dudetext_base_y + dudetext_line_gap * 2);
                rd.drawString("To power up your car (and keep it powered up) you will need to", text_x,
                        dudetext_base_y + dudetext_line_gap * 3);
                rd.drawString("perform stunts!", text_x, dudetext_base_y + dudetext_line_gap * 4);

            } else {
                rd.drawString("The better the stunt the more power you get!", text_x, dudetext_base_y);
                rd.setColor(new Color(100, 100, 100));
                rd.drawString("Forward looping pushes your car forwards in the air and helps", text_x,
                        dudetext_base_y + dudetext_line_gap);
                rd.drawString("when racing. Backward looping pushes your car upwards giving it", text_x,
                        dudetext_base_y + dudetext_line_gap * 2);
                rd.drawString("more hang time in the air making it easier to control its landing.", text_x,
                        dudetext_base_y + dudetext_line_gap * 3);
                rd.drawString("Left and right rolls shift your car in the air left and right slightly.", text_x,
                        dudetext_base_y + dudetext_line_gap * 4);

            }
            rd.drawImage(stunts, Utility.centeredImageX(stunts), 200, null);

            rd.setFont(new Font("SansSerif", 1, 13));
            rd.drawString("To perform stunts. When your car is in the AIR;", start_x, 330);
            rd.drawString("Press combo Spacebar + Arrow Keys :", start_x, 350);
            rd.drawImage(space, start_x + 50, 370, null);

            /* use same middle as pervious page to keep alignments the same */
            int middle = Utility.centeredImageX(ory);
            rd.drawImage(arrows, middle + 120, 340, null);
            rd.setFont(new Font("SansSerif", 1, 11));
            rd.setColor(new Color(0, 0, 0));
            rd.drawString("Forward Loop", middle + 125, 335);
            rd.drawString("Backward Loop", middle + 120, 415);
            rd.drawString("Left Roll", middle + 70, 390);
            rd.drawString("Right Roll", middle + 210, 390);

        }
        if (flipo == 11 || flipo == 13) { // 6-7 page
            if (flipo == 11) {
                rd.drawString("When wasting cars, to help you find the other cars in the stage,", text_x,
                        dudetext_base_y);
                rd.drawString("press [ A ] to toggle the guidance arrow from pointing to the track", text_x,
                        dudetext_base_y + dudetext_line_gap);
                rd.drawString("to pointing to the cars.", text_x, dudetext_base_y + dudetext_line_gap * 2);
                rd.drawString("When your car is damaged. You fix it (and reset its 'Damage') by", text_x,
                        dudetext_base_y + dudetext_line_gap * 3);
                rd.drawString("jumping through the electrified hoop.", text_x, dudetext_base_y + dudetext_line_gap * 4);
            } else {
                rd.setColor(new Color(100, 100, 100));
                rd.drawString("You will find that in some stages it's easier to waste the other cars", text_x,
                        dudetext_base_y);
                rd.drawString("and in some others it's easier to race and finish in first place.", text_x,
                        dudetext_base_y + dudetext_line_gap);
                rd.drawString("It is up to you to decide when to waste and when to race.", text_x,
                        dudetext_base_y + dudetext_line_gap * 2);
                rd.drawString("And remember, 'Power' is an important factor in the game. You", text_x,
                        dudetext_base_y + dudetext_line_gap * 3);
                rd.drawString("will need it whether you are racing or wasting!", text_x,
                        dudetext_base_y + dudetext_line_gap * 4);
                rd.setColor(new Color(0, 0, 0));
            }

            int middle = GameFacts.screenWidth / 2;
            int fixhoop_x = middle - fixhoop.getWidth(null) - 100;
            int guidance_x = middle + 100;

            rd.drawImage(fixhoop, fixhoop_x, 193, null);
            rd.drawImage(sarrow, guidance_x, 203, null);
            rd.setFont(new Font("SansSerif", 1, 11));
            rd.drawString("The Electrified Hoop", fixhoop_x + 5, 191);
            rd.drawString("Jumping through it fixes your car.", fixhoop_x - 30, 313);
            rd.drawString("Make guidance arrow point to cars.", guidance_x, 191);
        }
        if (flipo == 15) { // 8
            rd.drawString("There is a total of 17 stages!", text_x, dudetext_base_y);
            rd.drawString("Every two stages completed a new car will be unlocked!", text_x,
                    dudetext_base_y + dudetext_line_gap);
            rd.drawString("I am Coach Insano by the way.", text_x, dudetext_base_y + dudetext_line_gap * 3);
            rd.drawString("I am your coach and narrator in this game!  Good Luck!", text_x,
                    dudetext_base_y + dudetext_line_gap * 4);

            int middle = GameFacts.screenWidth / 2;
            int col1_base_x = middle - 300;
            int col2_base_x = middle + 50;

            drawcs(185, "Other Controls :", 0, 0, 0, 0);

            rd.setFont(new Font("SansSerif", 1, 11));
            rd.drawImage(kz, col1_base_x, 200, null);
            rd.drawString("OR", col1_base_x + kz.getWidth(null) + 10, 226);
            rd.drawImage(kx, col1_base_x + 70, 200, null);
            rd.drawString("=> To look behind you while driving.", col1_base_x + 110, 226);
            rd.drawImage(kv, col1_base_x, 250, null);
            rd.drawString("Change Views", col1_base_x + kv.getWidth(null) + 10, 276);
            rd.drawImage(kp, col1_base_x, 300, null);
            rd.drawString("OR", col1_base_x + kp.getWidth(null) + 10, 326);
            rd.drawImage(kenter, col1_base_x + 70, 300, null);
            rd.drawString("Pause Game", col1_base_x + 200, 326);
            rd.drawImage(km, col2_base_x, 200, null);
            rd.drawString("Mute Music", col2_base_x + km.getWidth(null) + 10, 226);
            rd.drawImage(kn, col2_base_x, 250, null);
            rd.drawString("Mute Sound Effects", col2_base_x + kn.getWidth(null) + 10, 276);
        }
        if (flipo == 1) {// first papge
            rd.setFont(new Font("SansSerif", 1, 13));
            FontHandler.fMetrics = rd.getFontMetrics();
            drawcs(20, "Main Game Controls", 0, 0, 0, 3);

            String arrkey_txt = "Drive your car using the Arrow Keys:";
            int arrkey_txt_width = FontHandler.fMetrics.stringWidth(arrkey_txt);
            /* relative X for column 1 page 1 */
            int col_1_x_start = Utility.centeredWidthX(arrkey_txt_width) - 150;
            /* same for column 2 */
            int col_2_x_start = Utility.centeredImageX(arrows) + 150;

            rd.drawString(arrkey_txt, col_1_x_start + 20, 55);
            rd.drawString("On the GROUND Spacebar is for Handbrake", col_1_x_start, 76);
            rd.drawImage(space, col_1_x_start + 30, 90, null);
            rd.drawImage(arrows, col_2_x_start, 58, null);
            rd.setFont(new Font("SansSerif", 1, 11));
            FontHandler.fMetrics = rd.getFontMetrics();
            rd.drawString("Accelerate", col_2_x_start + 15, 54);
            rd.drawString("Brake/Reverse", col_2_x_start + 5, 132);
            rd.drawString("Turn left", col_2_x_start - 50, 110);
            rd.drawString("Turn right", col_2_x_start + 85, 110);

            drawcs(150, "--------------------------------------------------------------------------------"
                    + "--------------------------------------------------------------------", 0, 0, 0, 3);
            rd.setFont(new Font("SansSerif", 1, 13));
            FontHandler.fMetrics = rd.getFontMetrics();
            rd.drawString("To perform stunts:", col_1_x_start, 175);
            rd.drawString("In the AIR press combo Spacebar + Arrow Keys :", col_1_x_start, 195);
            rd.drawImage(space, col_1_x_start + 30, 220, null);

            rd.drawImage(arrows, col_2_x_start, 188, null);
            rd.setFont(new Font("SansSerif", 1, 11));
            FontHandler.fMetrics = rd.getFontMetrics();
            rd.setColor(new Color(0, 0, 0));
            rd.drawString("Forward Loop", col_2_x_start + 3, 184);
            rd.drawString("Backward Loop", col_2_x_start + 3, 262);
            rd.drawString("Left Roll", col_2_x_start - 50, 240);
            rd.drawString("Right Roll", col_2_x_start + 85, 240);

            rd.drawImage(stunts, Utility.centeredImageX(stunts), 290, null);
        }
        if (flipo >= 1 && flipo <= 13) {
            rd.drawImage(next[pnext], instructions_next_button_x, instructions_next_back_button_y, null);
        }
        if (flipo >= 3 && flipo <= 15) {
            rd.drawImage(back[pback], instructions_back_button_x, instructions_next_back_button_y, null);
        }
        if (flipo == 15) {
            rd.drawImage(contin[pcontin], instructions_continue_button_x, instructions_next_back_button_y, null);
        }
        if (control.enter || control.right) {
            if (flipo >= 1 && flipo <= 13) {
                flipo++;
            }
            if (control.enter && flipo == 15) {
                flipo = 0;
                fase = oldfase;
                rd.setFont(new Font("SansSerif", 1, 11));
                FontHandler.fMetrics = rd.getFontMetrics();
            }
            control.enter = false;
            control.right = false;
        }
        if (control.left) {
            if (flipo >= 3 && flipo <= 15) {
                flipo -= 3;
            }
            control.left = false;
        }
    }

    public void fleximage(Image image, int frame, int stage) {
        if (frame == 0) {
            grabScreen(image);
        }
        int column = 0;
        int lastRed = 0;
        int lastGreen = 0;
        int lastBlue = 0;
        int noise = (int) (Math.random() * 128D);
        int noiseLeft = (int) (5D + Math.random() * 15D);
        int pixel = 0;
        do {
            Color color = new Color(flexpix[pixel]);
            int red = 0;
            int green = 0;
            int blue = 0;
            if (column == 0) {
                red = color.getRed();
                lastRed = red;
                green = color.getGreen();
                lastGreen = green;
                blue = color.getBlue();
                lastBlue = blue;
            } else {
                red = (int) ((color.getRed() + lastRed * 0.38F * frame) / (1.0F + 0.38F * frame));
                lastRed = red;
                green = (int) ((color.getGreen() + lastGreen * 0.38F * frame) / (1.0F + 0.38F * frame));
                lastGreen = green;
                blue = (int) ((color.getBlue() + lastBlue * 0.38F * frame) / (1.0F + 0.38F * frame));
                lastBlue = blue;
            }
            if (++column == GameFacts.screenWidth) {
                column = 0;
            }
            int outRed = (red * 17 + green + blue + noise) / 22;
            int outGreen = (green * 17 + red + blue + noise) / 22;
            int outBlue = (blue * 17 + red + green + noise) / 22;
            if (stage == 17) {
                outRed = (red * 17 + green + blue + noise) / 22;
                outGreen = (green * 17 + red + blue + noise) / 21;
                outBlue = (blue * 17 + red + green + noise) / 20;
            }
            if (--noiseLeft == 0) {
                noise = (int) (Math.random() * 128D);
                noiseLeft = (int) (5D + Math.random() * 15D);
            }
            Color blurred = new Color(outRed, outGreen, outBlue);
            flexpix[pixel] = blurred.getRGB();
        } while (++pixel < flexpix.length);
        fleximg = createImage(new MemoryImageSource(GameFacts.screenWidth, GameFacts.screenHeight, flexpix, 0,
                GameFacts.screenWidth));
        rd.drawImage(fleximg, 0, 0, null);
    }

    /** The car whose HUD is being drawn: car 0, or car 1 for split screen's Player 2. */
    private int hudCar;

    private void arrow(int checkpoint, int missed, CheckPoints checkpoints, ContO cars[], boolean toRacers) {
        int xs[] = new int[7];
        int ys[] = new int[7];
        int zs[] = new int[7];
        /**
         * Base X position
         */
        int centerX = GameFacts.GAME_WIDTH / 2; // the HUD projects in the game's own view; marginX moves it into the HUD
        byte arrowY = -90;
        /**
         * Base Y position
         */
        int centerZ = GameFacts.GAME_HEIGHT; // 2BC
        int angle = 0;
        do {
            ys[angle] = arrowY;
        } while (++angle < 7);
        xs[0] = centerX;
        zs[0] = centerZ + 110;
        xs[1] = centerX - 35;
        zs[1] = centerZ + 50;
        xs[2] = centerX - 15;
        zs[2] = centerZ + 50;
        xs[3] = centerX - 15;
        zs[3] = centerZ - 50;
        xs[4] = centerX + 15;
        zs[4] = centerZ - 50;
        xs[5] = centerX + 15;
        zs[5] = centerZ + 50;
        xs[6] = centerX + 35;
        zs[6] = centerZ + 50;
        angle = 0;
        int nearest = hudCar;
        if (!toRacers) {
            char flip = '\0';
            if (checkpoints.x[checkpoint] - checkpoints.opx[hudCar] >= 0) {
                flip = '\264';
            }
            angle = (int) (90 + flip + StrictMath.atan(
                    (double) (checkpoints.z[checkpoint] - checkpoints.opz[hudCar]) / (double) (checkpoints.x[checkpoint] - checkpoints.opx[hudCar]))
                    / 0.017453292519943295D);
        } else {
            int nearestDistance = -1;
            boolean seenOnScreen = false;
            int other = hudCar == 0 ? 1 : 0;
            do {
                if (other == hudCar) {
                    continue; // split screen's Player 2 points at Player 1 too, never at itself
                }
                if ((Utility.py(checkpoints.opx[hudCar] / 100, checkpoints.opx[other] / 100, checkpoints.opz[hudCar] / 100,
                        checkpoints.opz[other] / 100) < nearestDistance || nearestDistance == -1) && (!seenOnScreen || checkpoints.onscreen[other] != 0)
                        && checkpoints.dested[other] == 0) {
                    nearest = other;
                    nearestDistance = Utility.py(checkpoints.opx[hudCar] / 100, checkpoints.opx[other] / 100, checkpoints.opz[hudCar] / 100,
                            checkpoints.opz[other] / 100);
                    if (checkpoints.onscreen[other] != 0) {
                        seenOnScreen = true;
                    }
                }
            } while (++other < GameFacts.numberOfPlayers);
            other = 0;
            if (checkpoints.opx[nearest] - checkpoints.opx[hudCar] >= 0) {
                other = 180;
            }
            angle = (int) (90 + other + StrictMath.atan((double) (checkpoints.opz[nearest] - checkpoints.opz[hudCar])
                    / (double) (checkpoints.opx[nearest] - checkpoints.opx[hudCar])) / 0.017453292519943295D);
            drawcs(13, "[ " + names[sc[nearest]] + " ]", 76, 67, 240, 0);
            drawcs(13, names[sc[nearest]], 0, 0, 0, 0);
            /*
             * example use of drawOver
             */
            // drawOver(names[sc[l]], conto[l]);
        }
        for (angle += Medium.xz; angle < 0; angle += 360) {
        }
        for (; angle > 180; angle -= 360) {
        }
        if (!toRacers) {
            if (angle > 130) {
                angle = 130;
            }
            if (angle < -130) {
                angle = -130;
            }
        } else {
            if (angle > 100) {
                angle = 100;
            }
            if (angle < -100) {
                angle = -100;
            }
        }
        if (Math.abs(ana - angle) < 180) {
            if (Math.abs(ana - angle) < 10) {
                ana = angle;
            } else if (ana < angle) {
                ana += 10;
            } else {
                ana -= 10;
            }
        } else {
            if (angle < 0) {
                ana += 15;
                if (ana > 180) {
                    ana -= 360;
                }
            }
            if (angle > 0) {
                ana -= 15;
                if (ana < -180) {
                    ana += 360;
                }
            }
        }
        Utility.rot(xs, zs, centerX, centerZ, ana, 7);
        angle = Math.abs(ana);
        if (!toRacers) {
            if (angle > 7 || missed > 0 || missed == -2 || cntan != 0) {
                int red = 0;
                do {
                    xs[red] = Utility.cXs(xs[red], zs[red]) + GameFacts.marginX();
                    ys[red] = Utility.cYs(ys[red], zs[red]);
                } while (++red < 7);
                red = (int) (190F + 190F * (Medium.snap[0] / 100F));
                if (red > 255) {
                    red = 255;
                }
                if (red < 0) {
                    red = 0;
                }
                int green = (int) (255F + 255F * (Medium.snap[1] / 100F));
                if (green > 255) {
                    green = 255;
                }
                if (green < 0) {
                    green = 0;
                }
                int blue = 0;
                if (missed <= 0) {
                    if (angle <= 45 && missed != -2 && cntan == 0) {
                        red = (red * angle + Medium.csky[0] * (45 - angle)) / 45;
                        green = (green * angle + Medium.csky[1] * (45 - angle)) / 45;
                        blue = (blue * angle + Medium.csky[2] * (45 - angle)) / 45;
                    }
                    if (angle >= 90) {
                        int brightRed = (int) (255F + 255F * (Medium.snap[0] / 100F));
                        if (brightRed > 255) {
                            brightRed = 255;
                        }
                        if (brightRed < 0) {
                            brightRed = 0;
                        }
                        red = (red * (140 - angle) + brightRed * (angle - 90)) / 50;
                        if (red > 255) {
                            red = 255;
                        }
                    }
                } else if (flk) {
                    red = (int) (255F + 255F * (Medium.snap[0] / 100F));
                    if (red > 255) {
                        red = 255;
                    }
                    if (red < 0) {
                        red = 0;
                    }
                    flk = false;
                } else {
                    red = (int) (255F + 255F * (Medium.snap[0] / 100F));
                    if (red > 255) {
                        red = 255;
                    }
                    if (red < 0) {
                        red = 0;
                    }
                    green = (int) (220F + 220F * (Medium.snap[1] / 100F));
                    if (green > 255) {
                        green = 255;
                    }
                    if (green < 0) {
                        green = 0;
                    }
                    flk = true;
                }
                rd.setColor(new Color(red, green, blue));
                rd.fillPolygon(xs, ys, 7);
                red = (int) (115F + 115F * (Medium.snap[0] / 100F));
                if (red > 255) {
                    red = 255;
                }
                if (red < 0) {
                    red = 0;
                }
                green = (int) (170F + 170F * (Medium.snap[1] / 100F));
                if (green > 255) {
                    green = 255;
                }
                if (green < 0) {
                    green = 0;
                }
                blue = 0;
                if (missed <= 0) {
                    if (angle <= 45 && missed != -2 && cntan == 0) {
                        red = (red * angle + Medium.csky[0] * (45 - angle)) / 45;
                        green = (green * angle + Medium.csky[1] * (45 - angle)) / 45;
                        blue = (blue * angle + Medium.csky[2] * (45 - angle)) / 45;
                    }
                } else if (flk) {
                    red = (int) (255F + 255F * (Medium.snap[0] / 100F));
                    if (red > 255) {
                        red = 255;
                    }
                    if (red < 0) {
                        red = 0;
                    }
                    green = 0;
                }
                rd.setColor(new Color(red, green, blue));
                rd.drawPolygon(xs, ys, 7);
            }
        } else {
            int red = 0;
            do {
                xs[red] = Utility.cXs(xs[red], zs[red]) + GameFacts.marginX();
                ys[red] = Utility.cYs(ys[red], zs[red]);
            } while (++red < 7);
            red = (int) (159F + 159F * (Medium.snap[0] / 100F));
            if (red > 255) {
                red = 255;
            }
            if (red < 0) {
                red = 0;
            }
            int green = (int) (207F + 207F * (Medium.snap[1] / 100F));
            if (green > 255) {
                green = 255;
            }
            if (green < 0) {
                green = 0;
            }
            int blue = (int) (255F + 255F * (Medium.snap[2] / 100F));
            if (blue > 255) {
                blue = 255;
            }
            if (blue < 0) {
                blue = 0;
            }
            rd.setColor(new Color(red, green, blue));
            rd.fillPolygon(xs, ys, 7);
            red = (int) (120F + 120F * (Medium.snap[0] / 100F));
            if (red > 255) {
                red = 255;
            }
            if (red < 0) {
                red = 0;
            }
            green = (int) (114F + 114F * (Medium.snap[1] / 100F));
            if (green > 255) {
                green = 255;
            }
            if (green < 0) {
                green = 0;
            }
            blue = (int) (255F + 255F * (Medium.snap[2] / 100F));
            if (blue > 255) {
                blue = 255;
            }
            if (blue < 0) {
                blue = 0;
            }
            rd.setColor(new Color(red, green, blue));
            rd.drawPolygon(xs, ys, 7);
        }
    }

    public void levelhigh(int wasted, int whenWasted, int closeFinish, int frame, int stage) {
        rd.drawImage(gameh, Utility.centeredImageX(gameh), 20, null);
        byte red = 16; ///////////// change this to 1 to relock the game
        char green = '0';
        char blue = '`';
        if (frame < 50) {
            if (aflk) {
                red = 106;
                green = '\260';
                blue = '\377';
                aflk = false;
            } else {
                aflk = true;
            }
        }
        if (wasted != 0) {
            if (closeFinish == 0) {
                drawcs(60, "You Wasted 'em!", red, green, blue, 0);
            } else if (closeFinish == 1) {
                drawcs(60, "Close Finish!", red, green, blue, 0);
            } else {
                drawcs(60, "Close Finish!  Almost got it!", red, green, blue, 0);
            }
        } else if (whenWasted == 229) {
            drawcs(60, "Wasted!", red, green, blue, 0);
        } else if (stage > 2) {
            drawcs(60, "Stunts!", red, green, blue, 0);
        } else {
            drawcs(60, "Best Stunt!", red, green, blue, 0);
        }
        drawcs(press_enter_to_continue_height, "Press  [ Enter ]  to continue", 0, 0, 0, 0);
    }

    public void playsounds(Madness car, Control control, int stage) {
        if (fase == Phase.INGAME && starcnt < 35 && cntwis != 8 && !mutes) {
            boolean throttle = control.up && car.speed > 0.0F || control.down && car.speed < 10F;
            boolean wheelspin = car.skid == 1 && control.handb
                    || Math.abs(car.scz[0]
                            - (car.scz[1] + car.scz[0] + car.scz[2] + car.scz[3]) / 4F) > 1.0F
                    || Math.abs(car.scx[0]
                            - (car.scx[1] + car.scx[0] + car.scx[2] + car.scx[3]) / 4F) > 1.0F;
            boolean launching = false;
            if (control.up && car.speed < 10F) {
                wheelspin = true;
                throttle = true;
                launching = true;
            }
            if (throttle && car.mtouch) {
                if (!car.capsized) {
                    if (!wheelspin) {
                        if (car.power != 98F) {
                            if (Math.abs(car.speed) > 0.0F
                                    && Math.abs(car.speed) <= car.stat.swits[0]) {
                                int gear = (int) ((3F * Math.abs(car.speed)) / car.stat.swits[0]);
                                if (gear == 2) {
                                    if (pwait == 0) {
                                        gear = 0;
                                    } else {
                                        pwait--;
                                    }
                                } else {
                                    pwait = 7;
                                }
                                sparkeng(car.stat, gear);
                            }
                            if (Math.abs(car.speed) > car.stat.swits[0]
                                    && Math.abs(car.speed) <= car.stat.swits[1]) {
                                int gear = (int) ((3F * (Math.abs(car.speed) - car.stat.swits[0]))
                                        / (car.stat.swits[1] - car.stat.swits[0]));
                                if (gear == 2) {
                                    if (pwait == 0) {
                                        gear = 0;
                                    } else {
                                        pwait--;
                                    }
                                } else {
                                    pwait = 7;
                                }
                                sparkeng(car.stat, gear);
                            }
                            if (Math.abs(car.speed) > car.stat.swits[1]
                                    && Math.abs(car.speed) <= car.stat.swits[2]) {
                                int gear = (int) ((3F * (Math.abs(car.speed) - car.stat.swits[1]))
                                        / (car.stat.swits[2] - car.stat.swits[1]));
                                sparkeng(car.stat, gear);
                            }
                        } else {
                            byte gear = 2;
                            if (pwait == 0) {
                                if (Math.abs(car.speed) > car.stat.swits[1]) {
                                    gear = 3;
                                }
                            } else {
                                pwait--;
                            }
                            sparkeng(car.stat, gear);
                        }
                    } else {
                        sparkeng(car.stat, -1);
                        if (launching) {
                            if (stopcnt <= 0) {
                                sm.loop("air" + 5);
                                stopcnt = 10;
                            }
                        } else if (stopcnt <= -2) {
                            sm.loop("air" + (2 + (int) (Medium.fxRandom() * 3F)));
                            stopcnt = 7;
                        }
                    }
                } else {
                    sparkeng(car.stat, 3);
                }
                grrd = false;
                aird = false;
            } else {
                pwait = 15;
                if (!car.mtouch && !grrd && Medium.fxRandom() > 0.50000000000000002D) {
                    sm.loop("air" + (int) (Medium.fxRandom() * 4F));
                    stopcnt = 5;
                    grrd = true;
                }
                if (!car.wtouch && !aird) {
                    stopairs();
                    sm.loop("air" + (int) (Medium.fxRandom() * 4F));
                    stopcnt = 10;
                    aird = true;
                }
                sparkeng(car.stat, -1);
            }
            if (car.cntdest != 0 && cntwis < 7) {
                if (!pwastd) {
                    sm.loop("wasted");
                    pwastd = true;
                }
            } else {
                if (pwastd) {
                    sm.play("wasted");
                    pwastd = false;
                }
                if (cntwis == 7 && !mutes) {
                    sm.stop("wasted");
                    sm.play("firewasted");
                }
            }
        } else {
            sparkeng(car.stat, -2);
            if (pwastd) {
                sm.stop("wasted");
                pwastd = false;
            }
        }
        if (stopcnt != -20) {
            if (stopcnt == 1) {
                stopairs();
            }
            stopcnt--;
        }
        if (bfcrash != 0) {
            bfcrash--;
        }
        if (bfskid != 0) {
            bfskid--;
        }
        if (car.newcar) {
            cntwis = 0;
        }
        if (fase == Phase.INGAME || fase == Phase.PREGAME || fase == Phase.INSTANTREPLAY
                || fase == Phase.CAUGHTHIGHLIGHT || fase == Phase.GAMEHIGHLIGHT || fase == Phase.POSTGAMEFADEOUT
                || fase == Phase.POSTGAME) {
            if (mutes != Control.mutes) {
                mutes = Control.mutes;
            }
            if (Control.mutem != mutem) {
                mutem = Control.mutem;
                if (mutem) {
                    if (loadedt) {
                        strack.setPaused(true);
                    }
                } else if (loadedt) {
                    strack.setPaused(false);
                }
            }
        }
        if (car.cntdest != 0 && cntwis < 7) {
            if (car.dest) {
                cntwis++;
            }
        } else {
            if (car.cntdest == 0) {
                cntwis = 0;
            }
            if (cntwis == 7) {
                cntwis = 8;
            }
        }
    }

    /*
     * A split-screen race's events for a car: car 1's are Player 2's, heard
     * and shown with Player 2's state swapped in (see PlayerHud). The classic
     * game has only car 0's, which go straight on.
     */

    @Override
    public void crash(int car, float force, int kind) {
        if (SplitRace.active == null) {
            crash(force, kind);
        } else {
            asPlayer(car, () -> crash(force, kind));
        }
    }

    @Override
    public void skid(int car, int surface, float speed) {
        if (SplitRace.active == null) {
            skid(surface, speed);
        } else {
            asPlayer(car, () -> skid(surface, speed));
        }
    }

    @Override
    public void carFixed(int car) {
        if (SplitRace.active == null) {
            carFixed();
        } else {
            asPlayer(car, this::carFixed);
        }
    }

    @Override
    public void showCheckpoint(int car, int next) {
        SplitRace split = SplitRace.active;
        if (split != null && car == 1) {
            split.cam2.showCheckpoint(next);
        } else {
            showCheckpoint(next);
        }
    }

    @Override
    public void showLastCheckpoint(int car, boolean last) {
        SplitRace split = SplitRace.active;
        if (split != null && car == 1) {
            split.cam2.showLastCheckpoint(last);
        } else {
            showLastCheckpoint(last);
        }
    }

    /** Runs {@code event} as split-screen player {@code car} (0 or 1), that player's HUD state swapped in. */
    void asPlayer(int car, Runnable event) {
        SplitRace split = SplitRace.active;
        if (split == null || car == SplitRace.drawing) {
            event.run();
            return;
        }
        int was = SplitRace.drawing;
        split.hud2.exchange(this);
        SplitRace.drawing = car;
        try {
            event.run();
        } finally {
            SplitRace.drawing = was;
            split.hud2.exchange(this);
        }
    }

    @Override
    public void crash(float force, int kind) {
        if (bfcrash == 0) {
            if (Math.abs(force) > 25F && !SoundManager.isDiscarding()) {
                Gamepads.hit(SplitRace.active == null ? -1 : SplitRace.drawing, force);
            }
            if (kind == 0) {
                if (Math.abs(force) > 25F && Math.abs(force) < 170F) {
                    if (!mutes) {
                        sm.play("lowcrash" + crshturn);
                    }
                    bfcrash = 2;
                }
                if (Math.abs(force) >= 170F) {
                    if (!mutes) {
                        sm.play("crash" + crshturn);
                    }
                    bfcrash = 2;
                }
                if (Math.abs(force) > 25F) {
                    if (crashup) {
                        crshturn--;
                    } else {
                        crshturn++;
                    }
                    if (crshturn == -1) {
                        crshturn = 2;
                    }
                    if (crshturn == 3) {
                        crshturn = 0;
                    }
                }
            }
            if (kind == -1) {
                if (Math.abs(force) > 25F && Math.abs(force) < 170F) {
                    if (!mutes) {
                        sm.play("lowcrash" + 2);
                    }
                    bfcrash = 2;
                }
                if (Math.abs(force) > 170F) {
                    if (!mutes) {
                        sm.play("crash" + 2);
                    }
                    bfcrash = 2;
                }
            }
            if (kind == 1) {
                if (!mutes) {
                    sm.play("tires");
                }
                bfcrash = 3;
            }
        }
    }

    @Override
    public void carFixed() {
        if (!mutes) {
            sm.play("carfixed");
        }
    }

    @Override
    public void showCheckpoint(int next) {
        Medium.checkpoint = next;
    }

    @Override
    public void showLastCheckpoint(boolean last) {
        Medium.lastcheck = last;
    }

    public void replyn() {
        if (aflk) {
            drawcs(30, "Replay  > ", 0, 0, 0, 0);
            aflk = false;
        } else {
            drawcs(30, "Replay  >>", 0, 128, 255, 0);
            aflk = true;
        }
    }

    private Image pressed(Image image) {
        int height = image.getHeight(ob);
        int width = image.getWidth(ob);
        int pixels[] = new int[width * height];
        PixelGrabber grabber = new PixelGrabber(image, 0, 0, width, height, pixels, 0, width);
        try {
            grabber.grabPixels();
        } catch (InterruptedException ignored) {
        }
        for (int k = 0; k < width * height; k++) {
            if (pixels[k] != pixels[width * height - 1]) {
                pixels[k] = 0xff000000;
            }
        }

        return createImage(new MemoryImageSource(width, height, pixels, 0, width));
    }

    private Image dodgen(Image image) {
        int height = image.getHeight(ob);
        int width = image.getWidth(ob);
        int pixels[] = new int[width * height];
        PixelGrabber grabber = new PixelGrabber(image, 0, 0, width, height, pixels, 0, width);
        try {
            grabber.grabPixels();
        } catch (InterruptedException ignored) {
        }
        for (int k = 0; k < width * height; k++) {
            Color color = new Color(pixels[k]);
            int red = color.getRed() * 3 + 90;
            if (red > 255) {
                red = 255;
            }
            if (red < 0) {
                red = 0;
            }
            int green = color.getGreen() * 3 + 90;
            if (green > 255) {
                green = 255;
            }
            if (green < 0) {
                green = 0;
            }
            int blue = color.getBlue() * 3 + 90;
            if (blue > 255) {
                blue = 255;
            }
            if (blue < 0) {
                blue = 0;
            }
            Color brightened = new Color(red, green, blue);
            pixels[k] = brightened.getRGB();
        }

        return createImage(new MemoryImageSource(width, height, pixels, 0, width));
    }

    public void stoploading() {
        // Thread.stop() throws on Java 20+, so end the loader loop cooperatively
        Thread loader = runner;
        runtyp = 0;
        runner = null;
        if (loader != null) {
            loader.interrupt();
            try {
                loader.join();
            } catch (InterruptedException ignored) {
                Thread.currentThread().interrupt();
            }
        }
        loading();
        app.repaint();
    }

    public void nofocus() {
        rd.setColor(new Color(255, 255, 255));
        rd.fillRect(0, 0, GameFacts.screenWidth, 20);
        rd.fillRect(0, 0, 20, GameFacts.screenHeight);
        rd.fillRect(0, GameFacts.screenHeight - 20, GameFacts.screenWidth, 20);
        rd.fillRect(GameFacts.screenWidth - 20, 0, 20, GameFacts.screenHeight);
        rd.setColor(new Color(192, 192, 192));
        rd.drawRect(20, 20, GameFacts.screenWidth - 40, GameFacts.screenHeight - 40);
        rd.setColor(new Color(0, 0, 0));
        rd.drawRect(22, 22, GameFacts.screenWidth - 44, GameFacts.screenHeight - 44);
        rd.setFont(new Font("SansSerif", 1, 11));
        FontHandler.fMetrics = rd.getFontMetrics();
        drawcs(14, "Game lost its focus.   Click screen with mouse to continue.", 100, 100, 100, 3);
        drawcs(GameFacts.screenHeight - 5, "Game lost its focus.   Click screen with mouse to continue.", 100, 100, 100,
                3);
    }

    private boolean overon(int x, int y, int width, int height, int pointX, int pointY) {
        return pointX > x && pointX < x + width && pointY > y && pointY < y + height;
    }

    /** The race as it was when it stopped, greyed by {@link #pauseimage}, behind a menu over it. */
    void drawPauseBackdrop() {
        rd.drawImage(fleximg, 0, 0, null);
    }

    /**
     * Greys the race as it stopped, for behind the pause menu or split
     * screen's results: each row smeared to the right as the original did,
     * with whole numbers, as reading each pixel through {@link Color} gave.
     */
    public void pauseimage(Image image) {
        grabScreen(image);
        int width = GameFacts.screenWidth;
        int pixels = GameFacts.screenHeight * width;
        int lastGray = 0;
        int column = 0;
        for (int i = 0; i < pixels; i++) {
            int rgb = flexpix[i];
            int sum = (rgb >> 16 & 0xFF) + (rgb >> 8 & 0xFF) + (rgb & 0xFF);
            int gray = column == 0 ? sum / 3 : (sum + lastGray * 30) / 33;
            lastGray = gray;
            if (++column == width) { // each row greyed on its own
                column = 0;
            }
            flexpix[i] = 0xFF000000 | gray << 16 | gray << 8 | gray;
        }
        fleximg = createImage(new MemoryImageSource(GameFacts.screenWidth, GameFacts.screenHeight, flexpix, 0,
                GameFacts.screenWidth));
        rd.drawImage(fleximg, 0, 0, null);
        Medium.flex = 0;
    }

    /**
     * The screen-sized top left of {@code image} into {@link #flexpix}, as a
     * {@link PixelGrabber} gives it, but straight from the picture's buffer
     * when it is one: the grabber took some 30 ms of a tick at 1920x1080, in
     * the pause menu's first and split screen's results' first.
     */
    private void grabScreen(Image image) {
        int width = GameFacts.screenWidth;
        int height = GameFacts.screenHeight;
        if (image instanceof BufferedImage picture && picture.getWidth() >= width && picture.getHeight() >= height) {
            if (picture.getType() == BufferedImage.TYPE_INT_RGB) {
                // as stored, 0xRRGGBB, in one go: what reads these takes only red, green and blue (see blendude)
                picture.getRaster().getDataElements(0, 0, width, height, flexpix);
            } else {
                picture.getRGB(0, 0, width, height, flexpix, 0, width);
            }
            return;
        }
        PixelGrabber grabber = new PixelGrabber(image, 0, 0, width, height, flexpix, 0, width);
        try {
            grabber.grabPixels();
        } catch (InterruptedException ignored) {
        }
    }

    public static void loadstrack(final int stage) {
        // ACV: Large music files should not be in zip files, so why
        // not load/read them directly? Checks if the specific file
        // format exists, then load the music according to stage number.
        // I haven't applied these changes to all other tracks yet.

        // thank you for your service acv
        String path = "data/music/" + GameSparker.stageSubDir + "stage" + stage;
        boolean customMusic = false;
        String fileFormat = CheckPoints.trackformat;

        
        // the first of these that is there, a mod's before the game's
        String found = GameFiles.find(path, ".radq", ".zip", ".zipo", ".ogg", ".mp3", ".wav", ".mid");

        // if (i < 1) {
        if (CheckPoints.customTrack) {
            customMusic = true;
            path = "data/music/custom/" + CheckPoints.trackname + "." + CheckPoints.trackformat;
            HLogger.info(path);
            if (customMusic) {
                File customMFile = new File(path);
                HLogger.info(fileFormat);
                try {
                    if (customMFile.exists()) {
                        if (CheckPoints.trackformat.equals("zip") || CheckPoints.trackformat.equals("zipo") || CheckPoints.trackformat.equals("radq")) {
                            strack = TrackZipLoader.loadZip(path, false);
                        } else {
                            strack = TrackZipLoader.loadMusic(path);
                        }
                    }
                } catch (final IOException ex) {
                    System.out.println("Error loading custom music file: " + path);
                    ex.printStackTrace();
                    loadedt = false;
                }
                loadedt = true;
                return;
            }
        }
        // }

        try {
            if (found != null) {
                strack = isZip(found) ? TrackZipLoader.loadZip(found, false) : TrackZipLoader.loadMusic(found);
            }
        } catch (final IOException ex) {
            System.out.println("Error loading music file music/stage" + stage);
            ex.printStackTrace();
            loadedt = false;
        }
        loadedt = true;
    }

    public void loadmusic(int stage, int unused) {
        hipnoload(stage, false);
        app.setCursor(new Cursor(3));
        app.repaint();

        boolean animated = false;

        if (stage == 1 || stage == 2 || stage == 3 || stage == 4 || stage == 5 || stage == 7 || stage == 8 || stage == 9 || stage == 10 || stage == 12
                || stage == 13 || stage == 16) {
            animated = true;
        }
        if (animated) {
            runtyp = stage;
            runner = new Thread(this);
            runner.start();
        }
        loadstrack(stage);
        /// aaaaaaaa

        if (animated) {
            runner.interrupt();
            runner = null;
            runtyp = 0;
        }
        System.gc();

        strack.play();
        /*
         * if (loadedt && mutem)
         * strack.setPaused(true);
         */

        app.setCursor(new Cursor(0));
        fase = Phase.PREGAME;
        pcontin = 0;
        mutem = false;
        mutes = false;
    }

    /**
     * Loads images from images.radq
     *
     * @author Omar Wally
     */
    private void loadimages() {
        Toolkit toolkit = Toolkit.getDefaultToolkit();
        MediaTracker tracker = new MediaTracker(app);
        addLoaded(12);
        int howManyImages = 0;

        try {
            for (GameFiles.Entry zipentry : GameFiles.archive("images")) {
                String name = zipentry.name();
                byte data[] = zipentry.data();
                if ("cars.gif".equals(name)) {
                    carsbg = loadimage(data, tracker, toolkit);
                }
                if ("gameh.gif".equals(name)) {
                    ogameh = loadimage(data, tracker, toolkit);
                }
                if ("gameov.gif".equals(name)) {
                    gameov = loadimage(data, tracker, toolkit);
                }
                if ("lap.gif".equals(name)) {
                    olap = loadimage(data, tracker, toolkit);
                }
                if ("paused.gif".equals(name)) {
                    paused = loadimage(data, tracker, toolkit);
                    pausebox_y = pauseBoxY();
                }
                if ("select.gif".equals(name)) {
                    select = loadimage(data, tracker, toolkit);
                }
                if ("yourwasted.gif".equals(name)) {
                    oyourwasted = loadimage(data, tracker, toolkit);
                }
                if ("youwastedem.gif".equals(name)) {
                    oyouwastedem = loadimage(data, tracker, toolkit);
                }
                if ("d1.gif".equals(name)) {
                    dude[0] = loadimage(data, tracker, toolkit);
                    placeInsano();
                }
                if ("d2.gif".equals(name)) {
                    dude[1] = loadimage(data, tracker, toolkit);
                }
                if ("d3.gif".equals(name)) {
                    dude[2] = loadimage(data, tracker, toolkit);
                }
                if ("float.gif".equals(name)) {
                    oflaot = loadimage(data, tracker, toolkit);
                }
                if ("1c.gif".equals(name)) {
                    ocntdn[1] = loadimage(data, tracker, toolkit);
                }
                if ("2c.gif".equals(name)) {
                    ocntdn[2] = loadimage(data, tracker, toolkit);
                }
                if ("3c.gif".equals(name)) {
                    ocntdn[3] = loadimage(data, tracker, toolkit);
                }
                if ("bgmain.jpg".equals(name)) {
                    bgmain = loadimage(data, tracker, toolkit);
                }
                if ("loadingmusic.gif".equals(name)) {
                    oloadingmusic = loadimage(data, tracker, toolkit);
                }
                if ("radicalplay.gif".equals(name)) {
                    radicalplay = loadimage(data, tracker, toolkit);
                }
                if ("back.gif".equals(name)) {
                    back[0] = loadimage(data, tracker, toolkit);
                    back[1] = bressed(back[0]);
                }
                if ("continue2.gif".equals(name)) {
                    contin[0] = loadimage(data, tracker, toolkit);
                    contin[1] = bressed(contin[0]);
                }
                if ("next.gif".equals(name)) {
                    next[0] = loadimage(data, tracker, toolkit);
                    next[1] = bressed(next[0]);
                }
                if ("pgate.gif".equals(name)) {
                    pgate = loadimage(data, tracker, toolkit);
                }
                if ("rpro.gif".equals(name)) {
                    rpro = loadimage(data, tracker, toolkit);
                }
                if ("selectcar.gif".equals(name)) {
                    selectcar = loadimage(data, tracker, toolkit);
                }
                if ("track1.jpg".equals(name)) {
                    trackbg[0][0] = loadimage(data, tracker, toolkit);
                    trackbg[1][0] = dodgen(trackbg[0][0]);
                }
                if ("track2.jpg".equals(name)) {
                    trackbg[0][1] = loadimage(data, tracker, toolkit);
                    trackbg[1][1] = dodgen(trackbg[0][1]);
                }
                if ("youlost.gif".equals(name)) {
                    oyoulost = loadimage(data, tracker, toolkit);
                }
                if ("youwon.gif".equals(name)) {
                    oyouwon = loadimage(data, tracker, toolkit);
                }
                if ("0c.gif".equals(name)) {
                    ocntdn[0] = loadimage(data, tracker, toolkit);
                }
                if ("damage.gif".equals(name)) {
                    odmg = loadimage(data, tracker, toolkit);
                }
                if ("power.gif".equals(name)) {
                    opwr = loadimage(data, tracker, toolkit);
                }
                if ("position.gif".equals(name)) {
                    opos = loadimage(data, tracker, toolkit);
                }
                if ("wasted.gif".equals(name)) {
                    owas = loadimage(data, tracker, toolkit);
                }
                if ("start1.gif".equals(name)) {
                    ostar[0] = loadimage(data, tracker, toolkit);
                }
                if ("start2.gif".equals(name)) {
                    ostar[1] = loadimage(data, tracker, toolkit);
                    star[2] = pressed(ostar[1]);
                }
                if ("congrad.gif".equals(name)) {
                    congrd = loadimage(data, tracker, toolkit);
                }
                if ("statb.gif".equals(name)) {
                    statb = loadimage(data, tracker, toolkit);
                }
                if ("statbo.gif".equals(name)) {
                    statbo = loadimage(data, tracker, toolkit);
                }
                if ("madness.gif".equals(name)) {
                    mdness = loadimage(data, tracker, toolkit);
                }
                if ("fixhoop.gif".equals(name)) {
                    fixhoop = loadimage(data, tracker, toolkit);
                }
                if ("arrow.gif".equals(name)) {
                    sarrow = loadimage(data, tracker, toolkit);
                }
                if ("stunts.gif".equals(name)) {
                    stunts = loadimage(data, tracker, toolkit);
                }
                if ("racing.gif".equals(name)) {
                    racing = loadimage(data, tracker, toolkit);
                }
                if ("wasting.gif".equals(name)) {
                    wasting = loadimage(data, tracker, toolkit);
                }
                if ("plus.gif".equals(name)) {
                    plus = loadimage(data, tracker, toolkit);
                }
                if ("space.gif".equals(name)) {
                    space = loadimage(data, tracker, toolkit);
                }
                if ("arrows.gif".equals(name)) {
                    arrows = loadimage(data, tracker, toolkit);
                }
                if ("chil.gif".equals(name)) {
                    chil = loadimage(data, tracker, toolkit);
                }
                if ("ory.gif".equals(name)) {
                    ory = loadimage(data, tracker, toolkit);
                }
                if ("kz.gif".equals(name)) {
                    kz = loadimage(data, tracker, toolkit);
                }
                if ("kx.gif".equals(name)) {
                    kx = loadimage(data, tracker, toolkit);
                }
                if ("kv.gif".equals(name)) {
                    kv = loadimage(data, tracker, toolkit);
                }
                if ("kp.gif".equals(name)) {
                    kp = loadimage(data, tracker, toolkit);
                }
                if ("km.gif".equals(name)) {
                    km = loadimage(data, tracker, toolkit);
                }
                if ("kn.gif".equals(name)) {
                    kn = loadimage(data, tracker, toolkit);
                }
                if ("kenter.gif".equals(name)) {
                    kenter = loadimage(data, tracker, toolkit);
                }
                if ("nfm.gif".equals(name)) {
                    nfm = loadimage(data, tracker, toolkit);
                }
                if ("options.gif".equals(name)) {
                    opti = loadimage(data, tracker, toolkit);
                }
                if ("opback.gif".equals(name)) {
                    opback = loadimage(data, tracker, toolkit);
                }
                if ("logocars.gif".equals(name)) {
                    logocars = loadimage(data, tracker, toolkit);
                }
                if ("logomadmess.gif".equals(name)) {
                    logomadnes = loadimage(data, tracker, toolkit);
                }
                if ("logomadbg.gif".equals(name)) {
                    logomadbg = loadimage(data, tracker, toolkit);
                }
                if ("byrd.gif".equals(name)) {
                    byrd = loadimage(data, tracker, toolkit);
                }
                if ("nfmcoms.gif".equals(name)) {
                    nfmcoms = loadimage(data, tracker, toolkit);
                }
                if ("nfmcom.gif".equals(name)) {
                    nfmcom = loadimage(data, tracker, toolkit);
                }
                howManyImages++;
                addLoaded(3);
            }
            HLogger.info("Images loaded: " + howManyImages);
        } catch (IOException e) {
            HLogger.error("Error Reading Images: " + e);
            e.printStackTrace();
        }
        System.gc();
    }

    /**
     * Loads images from the web
     *
     * @author Kaffeinated
     */
    public void loadnetworkimages() {
        Utility.startTimer();
        addLoaded(12);
        try {
            aimLogo = Utility.webGet("http://i59.servimg.com/u/f59/14/03/33/42/logo10.png"); /// aim games logo

            /// URL goes here
            //
            addLoaded(3);
        } catch (Exception ex) {
            HLogger.error("Error Loading Network Images: " + ex);
        }
        System.gc();
        Utility.stopTimer();
    }

    public void pausedgame(int stage, Control control, Record record) {
        rd.drawImage(fleximg, 0, 0, null);
        int boxX = Utility.centeredImageX(paused);
        int boxW = paused.getWidth(null);
        int boxH = paused.getHeight(null) + pausebox_option_gap; // opened up for Settings
        /* add the blue lines behind the pause box */
        int current_line_y = pausebox_y + 5;
        rd.setColor(new Color(60, 135, 220, 128));
        for (; current_line_y < pausebox_y + boxH - 5; current_line_y += 4) {
            rd.drawLine(boxX, current_line_y, boxX + boxW, current_line_y);
        }

        int items = pausebox_op_widths.length;
        if (control.up) {
            opselect--;
            if (opselect < 0) {
                opselect = items - 1;
            }
            control.up = false;
        }
        if (control.down) {
            opselect++;
            if (opselect >= items) {
                opselect = 0;
            }
            control.down = false;
        }
        clickOnMove(1);
        if (opselect >= 0 && opselect < items) {
            int width = pausebox_op_widths[opselect];
            int y = pausebox_y + pausebox_option_start_off + pausebox_option_gap * opselect;
            rd.setColor(new Color(64, 143, 223));
            rd.fillRoundRect(Utility.centeredWidthX(width) + pausebox_op_x_correction, y, width, pausebox_op_height, 7,
                    20);
            if (shaded) {
                rd.setColor(new Color(225, 200, 255));
            } else {
                rd.setColor(new Color(0, 89, 223));
            }
            rd.drawRoundRect(Utility.centeredWidthX(width) + pausebox_op_x_correction, y, width, pausebox_op_height, 7,
                    20);
        }
        /*
         * The box's picture, opened up above Quit Game: the rows above the gap,
         * the empty row at the gap stretched over the new item's height, then
         * the rest. Settings is lettered into it in the box's own style.
         */
        int gap = pausebox_gap_at;
        int extra = pausebox_option_gap;
        rd.drawImage(paused, boxX, pausebox_y, boxX + boxW, pausebox_y + gap, 0, 0, boxW, gap, null);
        rd.drawImage(paused, boxX, pausebox_y + gap, boxX + boxW, pausebox_y + gap + extra, 0, gap, boxW, gap + 1,
                null);
        rd.drawImage(paused, boxX, pausebox_y + gap + extra, boxX + boxW, pausebox_y + boxH, 0, gap, boxW,
                paused.getHeight(null), null);
        SettingsScreen.drawPauseItem(rd, "Settings", GameFacts.screenWidth / 2F + pausebox_op_x_correction,
                pausebox_y + pausebox_option_start_off + pausebox_option_gap * PAUSE_SETTINGS + 23);
        if (control.enter || control.handb) {
            clickNow();
            if (opselect == 0) {
                if (!mutem) {
                    if (loadedt) {
                        strack.setPaused(false);
                    }
                }
                fase = Phase.INGAME;
            }
            if (opselect == 1) {
                if (record.caught >= 300) {
                    if (loadedt) {
                        strack.setPaused(false);
                    }
                    fase = Phase.INSTANTREPLAY;
                } else {
                    fase = Phase.NOTENOUGHREPLAYDATA;
                }
            }
            if (opselect == 2) {
                if (loadedt) {
                    strack.setPaused(true);
                }
                oldfase = Phase.PAUSEMENU;
                fase = Phase.INSTRUCTIONS;
            }
            if (opselect == PAUSE_SETTINGS) {
                openSettings(Phase.PAUSEMENU);
            }
            if (opselect == 4) {
                if (loadedt) {
                    strack.setPaused(true);
                    strack.unload();
                }
                fase = Phase.MAINMENU;
                opselect = 0;
            }
            control.enter = false;
            control.handb = false;
        }
    }

    public void credits(Control control) {
        if (flipo == 0) {
            sm.play("powerup");
            rad(0);
            flipo = 1;
            bgScroll = 0;
        }
        if (flipo >= 1 && flipo <= 100) {
            rad(flipo);
            flipo++;
            if (flipo == 100) {
                flipo = 1;
            }
        }
        if (flipo == 101) {
            scrollBackdrop(10);
            rd.drawImage(mdness, Utility.centeredImageX(mdness), 7, null);
            rd.setFont(new Font("SansSerif", 1, 13));
            FontHandler.fMetrics = rd.getFontMetrics();
            drawcs(65, "At Radicalplay.com", 0, 0, 0, 3);
            drawcs(100, "Cartoon 3D Engine, Game Programming, 3D Models, Graphics and Sound Effects", 0, 0, 0, 3);
            drawcs(120, "Everything By Omar Waly", 70, 70, 70, 3);
            rd.setFont(new Font("SansSerif", 1, 13));
            FontHandler.fMetrics = rd.getFontMetrics();
            drawcs(180, "Thanks for Game Testing", 0, 0, 0, 3);
            rd.setFont(new Font("SansSerif", 1, 11));
            FontHandler.fMetrics = rd.getFontMetrics();
            drawcs(200, "Soufy H Abutaleb, Sherif Abouzeid,", 90, 90, 90, 3);
            drawcs(215, "Kareem Mansour, Youssef Wahby,", 90, 90, 90, 3);
            drawcs(230, "Taymour Farid, Mahmoud Waly", 90, 90, 90, 3);
            drawcs(245, "and Mahmoud Ezzeldin (Turbo)", 90, 90, 90, 3);
            rd.setFont(new Font("SansSerif", 1, 13));
            FontHandler.fMetrics = rd.getFontMetrics();
            drawcs(340, "Music was obtained from the ModArchive.org", 0, 0, 0, 3);
            rd.setFont(new Font("SansSerif", 1, 11));
            FontHandler.fMetrics = rd.getFontMetrics();
            drawcs(360, "All tracks where remixed to fit game by Omar Waly", 90, 90, 90, 3);
            drawcs(380, "For more details about the music: http://www.radicalplay.com/madcars/music.html", 90, 90, 90,
                    3);
        }
        if (flipo == 102) {
            scrollBackdrop(10);
            rd.setFont(new Font("SansSerif", 1, 13));
            FontHandler.fMetrics = rd.getFontMetrics();
            //////////////////////////////////////////////////////////////////////
            drawcs(100, "These files have been edited and enhanced by Kaffeinated", 0, 0, 0, 3);

            drawcs(180, "Thanks for help and suggestions goes to", 0, 0, 0, 3);
            rd.setFont(new Font("SansSerif", 1, 11));
            FontHandler.fMetrics = rd.getFontMetrics();
            drawcs(200, "Chaotic for help with NFMM graphics", 90, 90, 90, 3);
            drawcs(215, "DragShot for his opponent status and desktop hack", 90, 90, 90, 3);
            drawcs(230, "Ten Graves, Phyrexian, and Hyde233 for bug fixes", 90, 90, 90, 3);

            //////////////////////////////////////////////////////////////////////
        }
        if (flipo == 103) {
            scrollBackdrop(16);
            rd.drawImage(nfmcom, Utility.centeredImageX(nfmcom), 170, null);
        }

        rd.drawImage(next[pnext], 600, credits_next_button_y, null);

        if (control.enter || control.handb || control.right) {
            if (flipo >= 1 && flipo <= 100) {
                flipo = 101;
                app.setCursor(new Cursor(0));
            } else {
                flipo++;
            }
            if (flipo == 104) {
                flipo = 0;
                fase = Phase.MAINMENU;
            }
            control.enter = false;
            control.handb = false;
            control.right = false;
        }
    }

    public void stat(Madness drivers[], CheckPoints checkpoints, Control control, ContO scene[], boolean chaseView) {
        statFlow(drivers, checkpoints, control);
        if (fase != Phase.CAUGHTHIGHLIGHT) {
            statPlayer(0, drivers, checkpoints, control, scene, chaseView);
        }
        statLightning(checkpoints);
    }

    /**
     * The race's flow, once a tick: pausing, the end of the race and its
     * banner, and the highlight after it. Split screen has its own, see
     * {@link #splitFlow}.
     */
    private void statFlow(Madness drivers[], CheckPoints checkpoints, Control control) {
        if (holdit) {
            holdcnt++;
            if (Medium.flex != 0) {
                Medium.flex = 0;
            }
            if (control.enter || holdcnt > 250) {
                fase = Phase.CAUGHTHIGHLIGHT;
                control.enter = false;
            }
        } else {
            if (holdcnt != 0) {
                holdcnt = 0;
            }
            if (control.enter) {
                if (loadedt) {
                    strack.setPaused(true);
                }
                fase = Phase.PAUSETRIGGER;
                control.enter = false;
            }
        }
        if (fase != Phase.CAUGHTHIGHLIGHT) {
            holdit = false;
            if (checkpoints.wasted == GameFacts.numberOfPlayers - 1 && practicemode == 0) {
                if (Medium.flex != 2) {
                    rd.setColor(new Color(Medium.csky[0], Medium.csky[1], Medium.csky[2]));
                    rd.fillRect(Utility.centeredImageX(youwastedem), 70, youwastedem.getWidth(ob),
                            youwastedem.getHeight(ob));
                    rd.setColor(new Color(Medium.cfade[0], Medium.cfade[1], Medium.cfade[2]));
                    rd.drawRect(Utility.centeredImageX(youwastedem), 70, youwastedem.getWidth(ob),
                            youwastedem.getHeight(ob));
                }
                rd.drawImage(youwastedem, Utility.centeredImageX(youwastedem), 70, null);
                if (aflk) {
                    drawcs(120, "You won, all cars have been wasted!", 0, 0, 0, 0);
                    aflk = false;
                } else {
                    drawcs(120, "You won, all cars have been wasted!", 0, 128, 255, 0);
                    aflk = true;
                }
                drawcs(press_enter_to_continue_height, "Press  [ Enter ]  to continue", 0, 0, 0, 0);
                checkpoints.haltall = true;
                holdit = true;
                winner = true;
            }
            if (!holdit && drivers[0].dest && cntwis == 8) {
                if (Medium.flex != 2) {
                    rd.setColor(new Color(Medium.csky[0], Medium.csky[1], Medium.csky[2]));
                    rd.fillRect(Utility.centeredImageX(yourwasted), 70, yourwasted.getWidth(ob),
                            yourwasted.getHeight(ob));
                    rd.setColor(new Color(Medium.cfade[0], Medium.cfade[1], Medium.cfade[2]));
                    rd.drawRect(Utility.centeredImageX(yourwasted), 70, yourwasted.getWidth(ob),
                            yourwasted.getHeight(ob));
                }
                rd.drawImage(yourwasted, Utility.centeredImageX(yourwasted), 70, null);
                drawcs(press_enter_to_continue_height, "Press  [ Enter ]  to continue", 0, 0, 0, 0);
                holdit = true;
                winner = false;
            }
            if (!holdit) {
                int racer = 0;
                do {
                    if (checkpoints.clear[racer] == checkpoints.nlaps * checkpoints.nsp && checkpoints.pos[racer] == 0) {
                        if (racer == 0) {
                            if (Medium.flex != 2) {
                                rd.setColor(new Color(Medium.csky[0], Medium.csky[1], Medium.csky[2]));
                                rd.fillRect(Utility.centeredImageX(youwon), 70, youwon.getWidth(ob),
                                        youwon.getHeight(ob));
                                rd.setColor(new Color(Medium.cfade[0], Medium.cfade[1], Medium.cfade[2]));
                                rd.drawRect(Utility.centeredImageX(youwon), 70, youwon.getWidth(ob),
                                        youwon.getHeight(ob));
                            }
                            rd.drawImage(youwon, Utility.centeredImageX(youwon), 70, null);
                            if (aflk) {
                                drawcs(120, "You finished first, nice job!", 0, 0, 0, 0);
                                aflk = false;
                            } else {
                                drawcs(120, "You finished first, nice job!", 0, 128, 255, 0);
                                aflk = true;
                            }
                            winner = true;
                        } else {
                            if (Medium.flex != 2) {
                                rd.setColor(new Color(Medium.csky[0], Medium.csky[1], Medium.csky[2]));
                                rd.fillRect(Utility.centeredImageX(youlost), 70, youlost.getWidth(ob),
                                        youlost.getHeight(ob));
                                rd.setColor(new Color(Medium.cfade[0], Medium.cfade[1], Medium.cfade[2]));
                                rd.drawRect(Utility.centeredImageX(youlost), 70, youlost.getWidth(ob),
                                        youlost.getHeight(ob));
                            }
                            rd.drawImage(youlost, Utility.centeredImageX(youlost), 70, null);
                            if (aflk) {
                                drawcs(120, "" + names[sc[racer]] + " finished first, race over!", 0, 0, 0, 0);
                                aflk = false;
                            } else {
                                drawcs(120, "" + names[sc[racer]] + " finished first, race over!", 0, 128, 255, 0);
                                aflk = true;
                            }
                            winner = false;
                        }
                        drawcs(press_enter_to_continue_height, "Press  [ Enter ]  to continue", 0, 0, 0, 0);
                        checkpoints.haltall = true;
                        holdit = true;
                    }
                } while (++racer < GameFacts.numberOfPlayers);
            }
        }
    }

    /** The most rows the standings show; a bigger race (a custom game's) shows a few of its cars, see {@link #compactStandings}. */
    static final int BOARD_ROWS = 10;
    /** The cars still in the race in standing order, and which of them the compact standings show. */
    private final int[] boardOrder = new int[51];
    private final boolean[] boardShown = new boolean[51];

    /**
     * One row of the standings, {@code row} rows down: the car's place
     * ({@code rank}, 0 for 1st), its name, its power or damage bar, and the
     * frame around the car the arrow points at. {@code mine} writes the name
     * in the places' blue: the player's own row on the compact standings.
     */
    private void standingRow(int row, int rank, int car, int me, Madness[] drivers, boolean mine) {
        int y_value = 30; // use to move status up or down
        int x_value = GameFacts.screenWidth - 700; // use to move status left or right

        rd.setColor(new Color(0, 0, 100));
        rd.drawString(ordinal(rank + 1), (rank == 0 ? 543 : 541) + x_value, 76 + y_value + 30 * row);
        rd.setColor(mine ? new Color(0, 0, 100) : new Color(0, 0, 0));
        rd.drawString(names[sc[car]], 600 - ((FontHandler.fMetrics.stringWidth(names[sc[car]])) / 2) + x_value,
                70 + y_value + 30 * row);
        if (drivers[me].im == car) {
            int red = (int) (159.0F + (159.0F * ((float) Medium.snap[0] / 100.0F)));
            if (red > 255)
                red = 255;
            if (red < 0)
                red = 0;
            int green = (int) (207.0F + (207.0F * ((float) Medium.snap[1] / 100.0F)));
            if (green > 255)
                green = 255;
            if (green < 0)
                green = 0;
            int blue = (int) (255.0F + (255.0F * ((float) Medium.snap[2] / 100.0F)));
            if (blue > 255)
                blue = 255;
            if (blue < 0)
                blue = 0;
            rd.setColor(new Color(red, green, blue));
            rd.drawRect(531 + x_value, 58 + y_value + 30 * row, 114, 25);
            rd.drawRect(532 + x_value, 59 + y_value + 30 * row, 112, 23);
        }
        if (arrace) {
            int dmg = (int) (60F * ((float) drivers[car].hitmag / (float) drivers[car].stat.maxmag));

            int red = 244;
            int green = 244;
            int blue = 11;

            if (dmg > 20)
                green = (int) (244F - 233F * ((float) (dmg - 20) / 40F));

            red = (int) ((float) red + (float) red * ((float) Medium.snap[0] / 100F));
            if (dmg > 60)
                dmg = 60;
            if (red > 255)
                red = 255;
            if (red < 0)
                red = 0;

            green = (int) ((float) green + (float) green * ((float) Medium.snap[1] / 100F));
            if (green > 255)
                green = 255;
            if (green < 0)
                green = 0;

            blue = (int) ((float) blue + (float) blue * ((float) Medium.snap[2] / 100F));
            if (blue > 255)
                blue = 255;
            if (blue < 0)
                blue = 0;

            rd.setColor(new Color(red, green, blue));
            rd.fillRect(565 + x_value, 75 + y_value + 30 * row, dmg, 5);
        } else {
            int pwr = (int) (60F * (drivers[car].power / 98F));
            if (pwr > 98) {
                pwr = 98;
            }
            rd.setColor(new Color(45, 150, 255));
            rd.fillRect(565 + x_value, 75 + y_value + 30 * row, pwr, 5);
        }
        rd.setColor(new Color(0, 0, 0));
        rd.drawRect(565 + x_value, 75 + y_value + 30 * row, 60, 5);
    }

    /**
     * The standings of a race with more cars than {@link #BOARD_ROWS}: the
     * top three, the car the arrow points at, and the player with the cars
     * nearest them, ahead first, in {@link #BOARD_ROWS} rows. Three dots
     * between two rows mark the cars left out there, and after the last row
     * the cars behind it.
     */
    private void compactStandings(int me, Madness[] drivers, CheckPoints checkpoints) {
        int cars = GameFacts.numberOfPlayers;
        int alive = 0;
        int mine = 0;
        int target = -1;
        for (int rank = 0; rank < cars; rank++) {
            for (int car = 0; car < cars; car++) {
                if (checkpoints.pos[car] == rank && checkpoints.dested[car] == 0) {
                    if (car == me) {
                        mine = alive;
                    }
                    if (car == drivers[me].im) {
                        target = alive;
                    }
                    boardOrder[alive++] = car;
                    break;
                }
            }
        }
        java.util.Arrays.fill(boardShown, 0, alive, false);
        int rows = Math.min(BOARD_ROWS, alive);
        int shown = 0;
        for (int i = 0; i < Math.min(3, alive); i++) {
            shown += showOnBoard(i);
        }
        if (target >= 0) {
            shown += showOnBoard(target);
        }
        for (int d = 0; shown < rows; d++) {
            if (mine - d >= 0) {
                shown += showOnBoard(mine - d);
            }
            if (shown < rows && mine + d < alive) {
                shown += showOnBoard(mine + d);
            }
        }
        int row = 0;
        int last = -1;
        for (int i = 0; i < alive; i++) {
            if (boardShown[i]) {
                if (i > last + 1 && last >= 0) {
                    boardGap(row - 1);
                }
                int car = boardOrder[i];
                standingRow(row++, checkpoints.pos[car], car, me, drivers, car == me);
                last = i;
            }
        }
        if (last < alive - 1 && row > 0) {
            boardGap(row - 1);
        }
    }

    /** Puts entry {@code i} of the standing order on the compact standings: 1 if it was not on yet. */
    private int showOnBoard(int i) {
        if (boardShown[i]) {
            return 0;
        }
        boardShown[i] = true;
        return 1;
    }

    /** Three dots under row {@code row} of the standings: cars are left out there. */
    private void boardGap(int row) {
        int x = GameFacts.screenWidth - 700 + 582;
        int y = 30 + 85 + 30 * row;
        rd.setColor(new Color(0, 0, 100));
        for (int dot = 0; dot < 3; dot++) {
            rd.fillRect(x + dot * 5, y, 2, 2);
        }
    }

    /** Where the staggered start's captions go: under the countdown. */
    private static final int LAUNCH_CAPTION_Y = 150;

    /**
     * A staggered start's own countdown (see {@link CustomGame#staggered}):
     * under the classic countdown, which starts the front group, a line on
     * how the start goes; then, while the player waits, which group is off
     * and the seconds left, and for the last three the classic countdown's
     * numbers, voice and Go! once more, for the player's own start.
     *
     * @param world the race, whose {@link World#ticksSinceGo} the start runs by
     */
    private void launchCountdown(World world, int me) {
        int cars = GameFacts.numberOfPlayers;
        int gap = CustomGame.LAUNCH_GAP_TICKS;
        int groups = world.startGroups;
        if (starcnt != 0) {
            if (starcnt <= 35) {
                drawcs(LAUNCH_CAPTION_Y, "Staggered start: " + groups + " groups, a second apart. You go last.", 0,
                        0, 100, 0);
            }
            return;
        }
        int tick = world.ticksSinceGo - 1; // the tick just run, 0 at Go!
        int wait = CustomGame.launchGroup(0, cars) * gap - tick;
        int number = wait <= 0 ? 0 : (wait + gap - 1) / gap; // the seconds left; 0 is Go!
        if (number > 3) {
            drawcs(LAUNCH_CAPTION_Y, "Group " + CustomGame.groupsGone(cars, tick) + " of " + groups
                    + " is off. You go in " + number, 0, 0, 100, 0);
            return;
        }
        if (wait < -LAUNCH_GO_TICKS) {
            return;
        }
        if (wait == number * gap && !mutes && me == 0) {
            sm.play(switch (number) {
                case 3 -> "three";
                case 2 -> "two";
                case 1 -> "one";
                default -> "go";
            });
        }
        drawCountdown(number, number == 0 ? 2 : wait > number * gap - 5 ? 1 : 0); // the portrait speaks each number
    }

    /** The countdown's portrait ({@code talking}: 0 still, 1 speaking, 2 at Go!) and its number, 0 for Go!. */
    private void drawCountdown(int number, int talking) {
        if (dudo != -1) {
            rd.drawImage(dudeb[talking], Utility.centeredImageX(dudeb[talking]) - 50, 0, null);
        }
        if (number != 0) {
            rd.drawImage(cntdn[number], Utility.centeredImageX(dudeb[talking]) + 50, 50, null);
        } else {
            rd.drawImage(cntdn[number], Utility.centeredImageX(dudeb[talking]) + 60, 50, null);
        }
    }

    /** How long the player's own Go! stays up: about half a second. */
    private static final int LAUNCH_GO_TICKS = 10;

    /** The places a race can have, written out once: {@link #ordinal}. */
    private static final String[] ORDINALS = new String[52];

    static {
        for (int n = 0; n < ORDINALS.length; n++) {
            ORDINALS[n] = spellOrdinal(n);
        }
    }

    /** 1st, 2nd, 3rd, 4th ... 11th, 12th, 13th ... 21st, 22nd. */
    static String ordinal(int n) {
        return n >= 0 && n < ORDINALS.length ? ORDINALS[n] : spellOrdinal(n);
    }

    private static String spellOrdinal(int n) {
        int tens = n % 100;
        if (tens >= 11 && tens <= 13) {
            return n + "th";
        }
        return switch (n % 10) {
            case 1 -> n + "st";
            case 2 -> n + "nd";
            case 3 -> n + "rd";
            default -> n + "th";
        };
    }

    /**
     * One player's HUD for a tick: the arrow, the standings, the bars, the
     * countdown, the captions and the stunts, and the sounds that go with
     * them. Car {@code me}'s: car 0 in the classic game; in split screen
     * each player's pane runs it with that player's state swapped in (see
     * {@link PlayerHud}).
     */
    void statPlayer(int me, Madness drivers[], CheckPoints checkpoints, Control control, ContO scene[],
            boolean chaseView) {
        hudCar = me;
        SplitRace split = SplitRace.active;
        if (split != null) {
            splitBanner(me, split, checkpoints);
        }
        if (chaseView) {
            if (!arrowDisabled) {
                if (checkpoints.stage != 110 && arrace != control.arrace) {
                    arrace = control.arrace;
                    if (arrace) {
                        wasay = true;
                        say = " Arrow now pointing at  Cars  <";
                        tcnt = -5;
                    }
                    if (!arrace) {
                        wasay = false;
                        say = " Arrow now pointing at  Track  <";
                        tcnt = -5;
                        cntan = 20;
                    }
                }
            }
            if (!holdit && fase != Phase.PAUSETRIGGER && starcnt == 0) {
                rd.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

                if (!arrowDisabled) {
                    arrow(drivers[spectate].point, drivers[spectate].missedcp, checkpoints, scene, arrace);
                }

                if (!opstatusDisabled) {
                    int num_cars = GameFacts.numberOfPlayers;
                    if (num_cars > BOARD_ROWS) {
                        compactStandings(me, drivers, checkpoints);
                    } else {
                        for (int array_one = 0; array_one < num_cars; array_one++) {
                            for (int array_two = 0; array_two < num_cars; array_two++) {
                                if (checkpoints.pos[array_two] == array_one && checkpoints.dested[array_two] == 0) {
                                    standingRow(array_one, array_one, array_two, me, drivers, false);
                                    break;
                                }
                            }
                        }
                    }
                }
                rd.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_OFF);
                if (!arrace && auscnt == 45 && drivers[me].capcnt == 0) {
                    if (drivers[me].missedcp > 0) {
                        if (drivers[me].missedcp > 15 && drivers[me].missedcp < 50) {
                            if (flk) {
                                drawcs(70, "Checkpoint Missed!", 255, 0, 0, 0);
                            } else {
                                drawcs(70, "Checkpoint Missed!", 255, 150, 0, 2);
                            }
                        }
                        drivers[me].missedcp++;
                        if (drivers[me].missedcp == 70) {
                            drivers[me].missedcp = -2;
                        }
                    } else if (drivers[me].mtouch && cntovn < 70) {
                        if (Math.abs(ana) > 100) {
                            cntan++;
                        } else if (cntan != 0) {
                            cntan--;
                        }
                        if (cntan > 40) {
                            cntovn++;
                            cntan = 40;
                            if (flk) {
                                drawcs(70, "Wrong Way!", 255, 150, 0, 0);
                                flk = false;
                            } else {
                                drawcs(70, "Wrong Way!", 255, 0, 0, 2);
                                flk = true;
                            }
                        }
                    }
                }
            }

            if (Medium.flex != 2 || Medium.flex == 2) {
                int bar_x = GameFacts.screenWidth - dmg.getWidth(null) - 50;

                rd.drawImage(dmg, bar_x, 7, null);
                rd.drawImage(pwr, bar_x, 27, null);
                rd.drawImage(lap, 19, 7, null);
                rd.setColor(new Color(0, 0, 100));
                rd.drawString("" + (drivers[spectate].nlaps + 1) + " / " + checkpoints.nlaps + "", 51, 18);
                rd.drawImage(was, 92, 7, null);
                rd.setColor(new Color(0, 0, 100));
                rd.drawString("" + wastedSeen(me, checkpoints) + " / " + (GameFacts.numberOfPlayers - 1), 150, 18);
                rd.drawImage(pos, 42, 27, null);
                rd.setFont(new Font("SansSerif", 1, 15));
                FontHandler.fMetrics = rd.getFontMetrics();
                rd.drawString(ordinal(checkpoints.pos[me] + 1), 110, 43);
                if (split != null) {
                    rd.setFont(new Font("SansSerif", 1, 11));
                    rd.setColor(new Color(0, 0, 100));
                    rd.drawString("Player " + (me + 1), 19, 58);
                }
                rd.setFont(new Font("SansSerif", 1, 11));
                FontHandler.fMetrics = rd.getFontMetrics();
                drawstat(drivers[spectate].stat.maxmag, drivers[spectate].hitmag, drivers[spectate].newcar,
                        drivers[spectate].power, bar_x);

                if (GameSparker.DEBUG) {
                    if (debugmode) {
                        rd.setColor(new Color(0, 0, 0));
                        rd.drawString("pcleared: " + drivers[spectate].pcleared, 20, 80);
                        rd.drawString("checkpoints.clear[" + spectate + "]: " + checkpoints.clear[spectate], 20,
                                95);
                        rd.drawString("X: " + scene[spectate].x, 20, 400);
                        rd.drawString("Y: " + scene[spectate].y, 20, 420);
                        rd.drawString("Z: " + scene[spectate].z, 20, 440);
                    }
                }
            }
        }
        if (!holdit) {
            if (starcnt != 0 && starcnt <= 35) {
                if (starcnt == 35 && !mutes && me == 0) {
                    sm.play("three");
                }
                if (starcnt == 24) {
                    gocnt = 2;
                    if (!mutes && me == 0) {
                        sm.play("two");
                    }
                }
                if (starcnt == 13) {
                    gocnt = 1;
                    if (!mutes && me == 0) {
                        sm.play("one");
                    }
                }
                if (starcnt == 2) {
                    gocnt = 0;
                    if (!mutes && me == 0) {
                        sm.play("go");
                    }
                }
                duds = 0;
                if (starcnt <= 37 && starcnt > 32) {
                    duds = 1;
                }
                if (starcnt <= 26 && starcnt > 21) {
                    duds = 1;
                }
                if (starcnt <= 15 && starcnt > 10) {
                    duds = 1;
                }
                if (starcnt <= 4) {
                    duds = 2;
                    Medium.flex = 0;
                }
                drawCountdown(gocnt, duds);
            }
            if (split == null && drivers[me].world().staggered()) {
                launchCountdown(drivers[me].world(), me);
            }
            if (looped != 0 && drivers[me].loop == 2) {
                looped = 0;
            }
            if (drivers[me].power < 45F) {
                if (tcnt == 30 && auscnt == 45 && drivers[me].mtouch && drivers[me].capcnt == 0) {
                    if (looped != 2) {
                        if (pwcnt < 70 || pwcnt < 160 && looped != 0) {
                            if (pwflk) {
                                drawcs(110, "Power low, perform stunt!", 0, 0, 200, 0);
                                pwflk = false;
                            } else {
                                drawcs(110, "Power low, perform stunt!", 255, 100, 0, 0);
                                pwflk = true;
                            }
                        }
                    } else if (pwcnt < 250) {
                        if (pwflk) {
                            drawcs(105, "> >  Press Enter for GAME INSTRUCTIONS!  < <", 0, 0, 200, 0);
                            drawcs(120, "To learn how to preform STUNTS!", 0, 0, 200, 0);
                            pwflk = false;
                        } else {
                            drawcs(105, "> >  Press Enter for GAME INSTRUCTIONS!  < <", 255, 100, 0, 0);
                            drawcs(120, "To learn how to preform STUNTS!", 255, 100, 0, 0);
                            pwflk = true;
                        }
                    }
                    pwcnt++;
                    if (pwcnt == 300) {
                        pwcnt = 0;
                        if (looped != 0) {
                            looped++;
                            if (looped == 3) {
                                looped = 1;
                            }
                        }
                    }
                }
            } else if (pwcnt != 0) {
                pwcnt = 0;
            }
            if (drivers[me].capcnt == 0) {
                if (tcnt < 30) {
                    if (tflk) {
                        if (!wasay) {
                            drawcs(105, say, 0, 0, 0, 0);
                        } else {
                            drawcs(105, say, 0, 0, 0, 0);
                        }
                        tflk = false;
                    } else {
                        if (!wasay) {
                            drawcs(105, say, 0, 128, 255, 0);
                        } else {
                            drawcs(105, say, 255, 128, 0, 0);
                        }
                        tflk = true;
                    }
                    tcnt++;
                } else if (wasay) {
                    wasay = false;
                }
                if (auscnt < 45) {
                    if (aflk) {
                        drawcs(85, asay, 98, 176, 255, 0);
                        aflk = false;
                    } else {
                        drawcs(85, asay, 0, 128, 255, 0);
                        aflk = true;
                    }
                    auscnt++;
                }
            } else if (tflk) {
                drawcs(110, "Bad Landing!", 0, 0, 200, 0);
                tflk = false;
            } else {
                drawcs(110, "Bad Landing!", 255, 100, 0, 0);
                tflk = true;
            }
            if (drivers[me].trcnt == 10) {
                loop = "";
                spin = "";
                asay = "";
                int turns;
                for (turns = 0; drivers[me].travzy > 225; turns++) {
                    drivers[me].travzy -= 360;
                }

                while (drivers[me].travzy < -225) {
                    drivers[me].travzy += 360;
                    turns--;
                }
                if (turns == 1) {
                    loop = "Forward loop";
                }
                if (turns == 2) {
                    loop = "double Forward";
                }
                if (turns == 3) {
                    loop = "triple Forward";
                }
                if (turns >= 4) {
                    loop = "massive Forward looping";
                }
                if (turns == -1) {
                    loop = "Backloop";
                }
                if (turns == -2) {
                    loop = "double Back";
                }
                if (turns == -3) {
                    loop = "triple Back";
                }
                if (turns <= -4) {
                    loop = "massive Back looping";
                }
                if (turns == 0) {
                    if (drivers[me].ftab && drivers[me].btab) {
                        loop = "Tabletop and reversed Tabletop";
                    } else if (drivers[me].ftab || drivers[me].btab) {
                        loop = "Tabletop";
                    }
                }
                if (turns > 0 && drivers[me].btab) {
                    loop = "Hanged " + loop;
                }
                if (turns < 0 && drivers[me].ftab) {
                    loop = "Hanged " + loop;
                }
                if (!("").equals(loop)) {
                    asay += " " + loop;
                }
                turns = 0;
                for (drivers[me].travxy = Math.abs(drivers[me].travxy); drivers[me].travxy > 270;) {
                    drivers[me].travxy -= 360;
                    turns++;
                }

                if (turns == 0 && drivers[me].rtab) {
                    if (("").equals(loop)) {
                        spin = "Tabletop";
                    } else {
                        spin = "Flipside";
                    }
                }
                if (turns == 1) {
                    spin = "Rollspin";
                }
                if (turns == 2) {
                    spin = "double Rollspin";
                }
                if (turns == 3) {
                    spin = "triple Rollspin";
                }
                if (turns >= 4) {
                    spin = "massive Roll spinning";
                }
                turns = 0;
                boolean maxed = false;
                for (drivers[me].travxz = Math.abs(drivers[me].travxz); drivers[me].travxz > 90;) {
                    drivers[me].travxz -= 180;
                    if ((turns += 180) > GameFacts.screenWidth) {
                        turns = GameFacts.screenWidth;
                        maxed = true;
                    }
                }

                if (turns != 0) {
                    if (("").equals(loop) && ("").equals(spin)) {
                        asay += " " + turns;
                        if (maxed) {
                            asay += " and beyond";
                        }
                    } else {
                        if (!("").equals(loop)) {
                            if (("").equals(loop)) {
                                asay += " " + spin;
                            } else {
                                asay += " with " + spin;
                            }
                        }
                        asay += " by " + turns;
                        if (maxed) {
                            asay += " and beyond";
                        }
                    }
                } else if (!("").equals(spin)) {
                    if (("").equals(loop)) {
                        asay += " " + spin;
                    } else {
                        asay += " by " + spin;
                    }
                }
                if (!("").equals(asay)) {
                    auscnt -= 15;
                }
                if (!("").equals(loop)) {
                    auscnt -= 25;
                }
                if (!("").equals(spin)) {
                    auscnt -= 25;
                }
                if (turns != 0) {
                    auscnt -= 25;
                }
                if (auscnt < 45) {
                    if (!mutes) {
                        sm.play("powerup");
                    }
                    if (auscnt < -20) {
                        auscnt = -20;
                    }
                    byte praise = 0;
                    if (drivers[me].powerup > 20F) {
                        praise = 1;
                    }
                    if (drivers[me].powerup > 40F) {
                        praise = 2;
                    }
                    if (drivers[me].powerup > 150F) {
                        praise = 3;
                    }
                    if (drivers[me].surfer) {
                        asay = " " + adj[4][(int) (Medium.fxRandom() * 3F)] + asay;
                    }
                    if (praise != 3) {
                        asay = adj[praise][(int) (Medium.fxRandom() * 3F)] + asay + exlm[praise];
                    } else {
                        asay = adj[praise][(int) (Medium.fxRandom() * 3F)];
                    }
                    if (!wasay) {
                        tcnt = auscnt;
                        if (drivers[me].power != 98F) {
                            say = "Power Up " + (int) ((100F * drivers[me].powerup) / 98F) + "%";
                        } else {
                            say = "Power To The MAX";
                        }
                        skidup = !skidup;
                    }
                }
            }
            if (drivers[me].newcar) {
                if (!wasay) {
                    say = "Car Fixed";
                    tcnt = 0;
                }
                crashup = !crashup;
            }
            if (clear != drivers[me].clear && drivers[me].clear != 0) {
                if (!wasay) {
                    say = "Checkpoint!";
                    tcnt = 15;
                }
                clear = drivers[me].clear;
                if (!mutes) {
                    sm.play("checkpoint");
                }
                cntovn = 0;
                if (cntan != 0) {
                    cntan = 0;
                }
            }
            if (split == null) {
                int other = 1;
                do {
                    if (dested[other] != checkpoints.dested[other]) {
                        dested[other] = checkpoints.dested[other];
                        if (dested[other] == 1) {
                            wasay = true;
                            say = "" + names[sc[other]] + " has been wasted!";
                            tcnt = -15;
                        }
                        if (dested[other] == 2) {
                            wasay = true;
                            say = "You wasted " + names[sc[other]] + "!";
                            tcnt = -15;
                            if (!mutes) {
                                Sound3D.event(WRECK_CLIP, false);
                            }
                        }
                    }
                } while (++other < GameFacts.numberOfPlayers);
            } else {
                splitWasted(me, split, checkpoints);
            }
        }
    }

    /** The cars wasted, as car {@code me}'s HUD counts them: every car but its own. */
    private int wastedSeen(int me, CheckPoints checkpoints) {
        if (SplitRace.active == null) {
            return checkpoints.wasted;
        }
        int seen = 0;
        for (int car = 0; car < GameFacts.numberOfPlayers; car++) {
            if (car != me && checkpoints.dested[car] != 0) {
                seen++;
            }
        }
        return seen;
    }

    /**
     * Split screen's "has been wasted" captions for player {@code me}: every
     * other car, Player 1 or 2 by name, and "You wasted" only for the cars
     * this player wrecked.
     */
    private void splitWasted(int me, SplitRace split, CheckPoints checkpoints) {
        for (int other = 0; other < GameFacts.numberOfPlayers; other++) {
            if (other == me || dested[other] == checkpoints.dested[other]) {
                continue;
            }
            dested[other] = checkpoints.dested[other];
            if (dested[other] == 0) {
                continue;
            }
            String name = SplitRace.nameOf(other, names[sc[other]]);
            wasay = true;
            tcnt = -15;
            if (dested[other] == 2 && split.hitBy[other] == me) {
                say = "You wasted " + name + "!";
                if (!mutes) {
                    Sound3D.event(WRECK_CLIP, false, me);
                }
            } else {
                say = name + " has been wasted!";
            }
        }
    }

    /**
     * Split screen's race flow, once a tick, in place of statFlow: pausing,
     * then whether the race is over and who won (see SplitRace#judge). Nothing
     * is drawn here: each pane shows its player how it ended, see splitBanner.
     */
    void splitFlow(Madness drivers[], CheckPoints checkpoints, Control control) {
        SplitRace split = SplitRace.active;
        winner = false; // a split-screen race never unlocks or saves anything
        if (holdit) {
            holdcnt++;
            if (control.enter || holdcnt > 250) {
                fase = Phase.SPLITRESULTS;
                control.enter = false;
            }
            return;
        }
        holdcnt = 0;
        if (control.enter) {
            if (loadedt) {
                strack.setPaused(true);
            }
            fase = Phase.PAUSETRIGGER;
            control.enter = false;
        }
        if (split.judge(drivers, checkpoints, GameFacts.numberOfPlayers)) {
            checkpoints.haltall = true;
            holdit = true;
        }
    }

    /**
     * How the race ended, or that this player is out of it, across the top
     * of player {@code me}'s pane, as the classic game shows it across the
     * screen.
     */
    private void splitBanner(int me, SplitRace split, CheckPoints checkpoints) {
        if (!split.over) {
            if (split.wasted[me] && cntwis == 8) {
                banner(yourwasted, split.wasted[1 - me] ? null : "Player " + (2 - me) + " races on");
            }
            return;
        }
        int won = split.winner;
        int other = 1 - me;
        if (won == me && split.finished) {
            banner(youwon, "You finished first, nice job!");
        } else if (won == me) {
            banner(youwastedem, split.rivalsNow > 0 ? "You won, all cars have been wasted!"
                    : "You won, Player " + (other + 1) + " has been wasted!");
        } else if (won == other && split.finished) {
            banner(youlost, "Player " + (other + 1) + " finished first, race over!");
        } else if (won == other) {
            banner(yourwasted, "Player " + (other + 1) + " wins!");
        } else if (won >= 2) {
            banner(youlost, "" + names[sc[won]] + " finished first, race over!");
        } else {
            banner(yourwasted, "Both players have been wasted, nobody wins!");
        }
        drawcs(GameFacts.screenHeight - 100, "Press  [ Enter ]  to continue", 0, 0, 0, 0);
    }

    /** One of the race's end pictures at the top of the screen (or pane), with a flickering caption under it. */
    private void banner(Image image, String caption) {
        if (Medium.flex != 2) {
            rd.setColor(new Color(Medium.csky[0], Medium.csky[1], Medium.csky[2]));
            rd.fillRect(Utility.centeredImageX(image), 70, image.getWidth(ob), image.getHeight(ob));
            rd.setColor(new Color(Medium.cfade[0], Medium.cfade[1], Medium.cfade[2]));
            rd.drawRect(Utility.centeredImageX(image), 70, image.getWidth(ob), image.getHeight(ob));
        }
        rd.drawImage(image, Utility.centeredImageX(image), 70, null);
        if (caption == null) {
            return;
        }
        if (aflk) {
            drawcs(120, caption, 0, 0, 0, 0);
            aflk = false;
        } else {
            drawcs(120, caption, 0, 128, 255, 0);
            aflk = true;
        }
    }

    /** The stage 16 lightning pulses with the music, once a tick. */
    void statLightning(CheckPoints checkpoints) {
        if (Medium.lightn != -1 && checkpoints.stage == 16) {
            // supposed to pulse during sections of the music, w/e
            Medium.lton = true;
        }
    }

    public void finish(CheckPoints checkpoints, ContO models[], Control control) {
        rd.drawImage(fleximg, 0, 0, null);
        if (winner) {
            if (checkpoints.stage == unlocked && !CustomGame.on) { // a custom game unlocks nothing
                if (checkpoints.stage != GameFacts.numberOfStages) {
                    rd.drawImage(congrd, Utility.centeredImageX(congrd), 30, null);
                    if (checkpoints.stage != -1) {
                        drawcs(80, "Stage " + checkpoints.stage + " Completed!", 170, 170, 170, 3);
                    } else {
                        drawcs(80, "Stage Completed!", 170, 170, 170, 3);
                    }
                } else {
                    rd.drawImage(congrd, Utility.centeredImageX(congrd) + (int) (Medium.fxRandom() * 10F), 30, null);
                }
                byte unlockedCar = 0;
                int carY = 0;
                pin = 60;
                if (checkpoints.stage == 2) {
                    unlockedCar = 8;
                    carY = 265;
                    pin = 0;
                    sc[0] = 8;
                }
                if (checkpoints.stage == 4) {
                    unlockedCar = 9;
                    carY = 240;
                    pin = 0;
                    sc[0] = 9;
                }
                if (checkpoints.stage == 6) {
                    unlockedCar = 10;
                    carY = 290;
                    pin = 0;
                    sc[0] = 10;
                }
                if (checkpoints.stage == 8) {
                    unlockedCar = 11;
                    carY = 226;
                    pin = 0;
                    sc[0] = 11;
                }
                if (checkpoints.stage == 10) {
                    unlockedCar = 12;
                    carY = 200;
                    pin = 0;
                    sc[0] = 12;
                }
                if (checkpoints.stage == 12) {
                    unlockedCar = 13;
                    carY = 200;
                    pin = 0;
                    sc[0] = 13;
                }
                if (checkpoints.stage == 14) {
                    unlockedCar = 14;
                    carY = 270;
                    pin = 0;
                    sc[0] = 14;
                }
                if (checkpoints.stage == 16) {
                    unlockedCar = 15;
                    carY = 290;
                    pin = 0;
                    sc[0] = 15;
                }
                if (checkpoints.stage != GameFacts.numberOfStages) {
                    rd.setFont(new Font("SansSerif", 1, 13));
                    FontHandler.fMetrics = rd.getFontMetrics();
                    int carUnlockedBoxHeight = 125;
                    if (aflk) {
                        drawcs(Utility.centeredHeightY(carUnlockedBoxHeight) - 40 + pin,
                                "Stage " + (checkpoints.stage + 1) + " is now unlocked!", 176, 196, 0, 3);
                    } else {
                        drawcs(Utility.centeredHeightY(carUnlockedBoxHeight) - 40 + pin,
                                "Stage " + (checkpoints.stage + 1) + " is now unlocked!", 247, 255, 165, 3);
                    }
                    if (unlockedCar != 0) {
                        if (aflk) {
                            drawcs(Utility.centeredHeightY(carUnlockedBoxHeight) - 20, "And:", 176, 196, 0, 3);
                        } else {
                            drawcs(Utility.centeredHeightY(carUnlockedBoxHeight) - 20, "And:", 247, 255, 165, 3);
                        }
                        rd.setColor(new Color(236, 226, 202));
                        float flicker = (float) Math.random();
                        if (flicker < 0.69999999999999996D) {
                            rd.drawRect(Utility.centeredWidthX(349), Utility.centeredHeightY(carUnlockedBoxHeight), 349,
                                    126);
                        } else {
                            rd.fillRect(Utility.centeredWidthX(350), Utility.centeredHeightY(carUnlockedBoxHeight), 350,
                                    127);
                        }
                        rd.setColor(new Color(255, 209, 89));
                        rd.fillRect(Utility.centeredWidthX(348), Utility.centeredHeightY(carUnlockedBoxHeight), 348, 4);
                        rd.fillRect(Utility.centeredWidthX(348), Utility.centeredHeightY(carUnlockedBoxHeight), 4, 125);
                        rd.fillRect(Utility.centeredWidthX(348),
                                Utility.centeredHeightY(carUnlockedBoxHeight) + carUnlockedBoxHeight, 348, 4);
                        rd.fillRect(Utility.centeredWidthX(348) + 345, Utility.centeredHeightY(carUnlockedBoxHeight), 4,
                                125);
                        models[unlockedCar].y = carY;
                        Medium.crs = true;
                        Medium.x = -(GameFacts.GAME_WIDTH / 2);
                        Medium.y = -130;
                        Medium.z = -50;
                        Medium.xz = 0;
                        Medium.zy = 0;
                        Medium.ground = 2470;
                        models[unlockedCar].blackout = false;
                        models[unlockedCar].z = 1000;
                        models[unlockedCar].x = 0;
                        models[unlockedCar].xz += 5;
                        models[unlockedCar].zy = 0;
                        models[unlockedCar].wzy -= 10;
                        try (var _ = Medium.menuView()) {
                            models[unlockedCar].d(rd);
                        }
                        if (flicker < 0.10000000000000001D) {
                            rd.setColor(new Color(236, 226, 202));
                            int row = 0;
                            do {
                                rd.drawLine(Utility.centeredWidthX(348),
                                        Utility.centeredHeightY(carUnlockedBoxHeight) + 4 * 2 + 4 * row,
                                        Utility.centeredWidthX(348) + 348,
                                        Utility.centeredHeightY(carUnlockedBoxHeight) + 4 * 2 + 4 * row);
                            } while (++row < 30);
                        }
                        String space = "";
                        if (unlockedCar == 13) {
                            space = " ";
                        }
                        if (aflk) {
                            drawcs(Utility.centeredHeightY(carUnlockedBoxHeight) + carUnlockedBoxHeight + 20,
                                    "" + names[unlockedCar] + "" + space + " has been unlocked!", 176, 196, 0, 3);
                        } else {
                            drawcs(Utility.centeredHeightY(carUnlockedBoxHeight) + carUnlockedBoxHeight + 20,
                                    "" + names[unlockedCar] + "" + space + " has been unlocked!", 247, 255, 165, 3);
                        }
                        pin = 180;
                    }
                    rd.setFont(new Font("SansSerif", 1, 11));
                    FontHandler.fMetrics = rd.getFontMetrics();
                    drawcs(Utility.centeredHeightY(carUnlockedBoxHeight) + carUnlockedBoxHeight + 40,
                            "GAME SAVED", 230, 167, 0, 3);
                    if (pin == 60) {
                        pin = 30;
                    } else {
                        pin = 0;
                    }
                } else {
                    rd.setFont(new Font("SansSerif", 1, 13));
                    FontHandler.fMetrics = rd.getFontMetrics();
                    if (aflk) {
                        drawcs(90, "Woohoooo you finished the game!!!", 144, 167, 255, 3);
                    } else {
                        drawcs(90, "Woohoooo you finished the game!!!", 228, 240, 255, 3);
                    }
                    if (aflk) {
                        drawcs(140, "Your Awesome!", 144, 167, 255, 3);
                    } else {
                        drawcs(142, "Your Awesome!", 228, 240, 255, 3);
                    }
                    if (aflk) {
                        drawcs(190, "You're truly a RADICAL GAMER!", 144, 167, 255, 3);
                    } else {
                        drawcs(190, "You're truly a RADICAL GAMER!", 255, 100, 100, 3);
                    }
                    rd.setColor(new Color(0, 0, 0));
                    rd.fillRect(0, 205, GameFacts.screenWidth, 62);
                    rd.drawImage(radicalplay, radpx + (int) (8D * Math.random() - 4D), 205, null);
                    if (radpx != 147) {
                        radpx += 40;
                        if (radpx > GameFacts.screenWidth) {
                            radpx = -453;
                        }
                    }
                    if (flipo == 40) {
                        radpx = 148;
                    }
                    flipo++;
                    if (flipo == 70) {
                        flipo = 0;
                    }
                    if (radpx == 147) {
                        rd.setFont(new Font("SansSerif", 1, 11));
                        FontHandler.fMetrics = rd.getFontMetrics();
                        if (aflk) {
                            drawcs(259, "A Game by Radicalplay.com", 144, 167, 255, 3);
                        } else {
                            drawcs(259, "A Game by Radicalplay.com", 228, 240, 255, 3);
                        }
                    }
                    if (aflk) {
                        drawcs(300, "Now get up and dance!", 144, 167, 255, 3);
                    } else {
                        drawcs(300, "Now get up and dance!", 228, 240, 255, 3);
                    }
                    pin = 0;
                }
                aflk = !aflk;
            } else {
                pin = 30;
                rd.drawImage(congrd, Utility.centeredImageX(congrd), 87, null);
                if (checkpoints.stage != -1) {
                    drawcs(137, "Stage " + checkpoints.stage + " Completed!", 170, 170, 170, 3);
                } else {
                    drawcs(137, "Stage Completed!", 170, 170, 170, 3);
                }
                drawcs(154, "" + checkpoints.name + "", 128, 128, 128, 3);
            }
        } else {
            pin = 30;
            rd.drawImage(gameov, Utility.centeredImageX(gameov), 117, null);
            drawcs(167, "Failed to Complete Stage " + checkpoints.stage + "!", 170, 170, 170, 3);
            drawcs(184, "" + checkpoints.name + "", 128, 128, 128, 3);
        }
        rd.drawImage(contin[pcontin], Utility.centeredImageX(contin[pcontin]), post_game_continue_button_y, null);
        if (control.enter || control.handb) {
            if (loadedt) {
                strack.setPaused(true);
                strack.unload();
            }
            if (checkpoints.stage == unlocked && winner && unlocked != GameFacts.numberOfStages && !CustomGame.on) {
                checkpoints.stage++;
                unlocked++;
                HLogger.info(unlocked);
            }
            fase = Phase.SAVEGAME;
            flipo = 0;
            control.enter = false;
            control.handb = false;
        }
    }

    /**
     * unique car sorter
     *
     * @param stage checkpoints.stage
     * @author rafa
     */
    /** The boss car a stage brings to a race of {@code players} cars: the computer car at the front. */
    static int stageBoss(int stage, int players) {
        return Math.min(players + (stage + 1) / 2, GameFacts.numberOfCars - 1);
    }

    /**
     * The stage whose span of the car list the computer cars come from
     * (half of it on, as many cars as the race has): the stage itself, or the
     * last one for a stage loaded from a file and once every stage is unlocked.
     */
    static int carSpanStage(int stage, int unlocked) {
        return stage == -1 || unlocked == GameFacts.numberOfStages ? GameFacts.numberOfStages : stage;
    }

    private void sortcars(int stage) {
        // split screen: both players' cars are theirs, the computer cars come after
        boolean split = SplitRace.on;
        int humans = split ? 2 : 1;
        if (split) {
            sc[0] = SplitRace.cars[0];
            sc[1] = SplitRace.cars[1];
        }
        if (CustomGame.on && !CustomGame.classicPicks() && GameFacts.numberOfPlayers > 1) {
            // a custom game's lineup, shuffled from the race seed as the classic cars are
            Random deck = new Random(RaceRandom.derive(Medium.raceSeed(), RaceRandom.STREAM_OPPONENTS));
            CustomGame.pickCars(sc, GameFacts.numberOfPlayers, stage, unlocked, deck);
            return;
        }
        if (stage != 0) {
            int lastcar = GameFacts.numberOfPlayers;

            if (GameFacts.numberOfPlayers == humans) {
                return; // if there's only 1 car (the player), don't sort anything
            }

            int maxId = stageBoss(stage, GameFacts.numberOfPlayers);

            if (sc[0] != maxId && (!split || sc[1] != maxId)) {
                if (stage != -1) {
                    sc[GameFacts.numberOfPlayers - 1] = maxId;
                    lastcar--; // boss car won't be randomized if stage isnt custom
                }
            }

            // DEBUG: Prints the range of possible cars to the console
            // HLogger.info("Minimum car: " + cd.names[(i - 1) / 2] + ", maximum car: " +
            // cd.names[nplayers + ((i - 1) / 2)] + ", therefore: " + (((i - 1) / 2) -
            // (nplayers + ((i - 1) / 2))) + " car difference");

            // double boss cars can appear if game is unlocked and player is playing on regular stages cba to account for this rn
            stage = carSpanStage(stage, unlocked);

            // create a list of car ids, each item completely unique
            ArrayList<Integer> list = new ArrayList<>();
            for (int k = stage / 2; k < GameFacts.numberOfPlayers + (stage / 2); k++) {
                if (k == sc[0] || split && k == sc[1])
                    continue;
                list.add(k);
            }
            /*
             * Randomize the order of this list (shuffle it like a deck of cards).
             * The deck comes from the race seed, so a recorded race gets the
             * same opponents when it is replayed.
             */
            Random deck = new Random(RaceRandom.derive(Medium.raceSeed(), RaceRandom.STREAM_OPPONENTS));
            Collections.shuffle(list, deck);

            // which item of the list should be picked
            int pick = 0;

            for (int j = humans; j < lastcar; j++) {

                // get an item from the "deck" - this can be any item as long as it's unique
                sc[j] = list.get(pick);
                pick++;

                // if there are more cars than tracks, reduce the car index number until it
                // fits.
                // unfortunately i have no idea how to make this work properly so we'll just
                // have to ignore the duplicates here
                while (sc[j] > GameFacts.numberOfCars - 1) {
                    HLogger.error("Car " + j + " is out of bounds");
                    sc[j] -= deck.nextDouble() * 5F;
                }
                // HLogger.info("sc of " + j + " is " + sc[j]);
            }
        }
        // this error will never be thrown in a deployment environment
        // it is only here for extra safety
        for (int j = 0; j < GameFacts.numberOfPlayers; j++) {
            if (sc[j] > GameFacts.numberOfCars - 1)
                throw new RuntimeException("there are too many tracks and not enough cars");
        }
    }

    private void sparkeng(final Stat stat, int gear) {
        gear++;
        int j = 0;
        do {
            if (gear == j) {
                if (!pengs[j]) {
                    sm.loop("engs" + stat.engine + j);
                    pengs[j] = true;
                }
            } else if (pengs[j]) {
                sm.stop("engs" + stat.engine + j);
                ;
                pengs[j] = false;
            }
        } while (++j < 5);
    }

    /*
     * Draws a centered string.
     */
    public void drawcs(int yPos, String text, int red, int green, int blue, int style) {
        if (style != 3 && style != 4) {
            red = (int) (red + red * (Medium.snap[0] / 100F));
            if (red > 255) {
                red = 255;
            }
            if (red < 0) {
                red = 0;
            }
            green = (int) (green + green * (Medium.snap[1] / 100F));
            if (green > 255) {
                green = 255;
            }
            if (green < 0) {
                green = 0;
            }
            blue = (int) (blue + blue * (Medium.snap[2] / 100F));
            if (blue > 255) {
                blue = 255;
            }
            if (blue < 0) {
                blue = 0;
            }
        }
        if (style == 4) {
            red = (int) (red - red * (Medium.snap[0] / 100F));
            if (red > 255) {
                red = 255;
            }
            if (red < 0) {
                red = 0;
            }
            green = (int) (green - green * (Medium.snap[1] / 100F));
            if (green > 255) {
                green = 255;
            }
            if (green < 0) {
                green = 0;
            }
            blue = (int) (blue - blue * (Medium.snap[2] / 100F));
            if (blue > 255) {
                blue = 255;
            }
            if (blue < 0) {
                blue = 0;
            }
        }
        if (style == 1) {
            rd.setColor(new Color(0, 0, 0));
            rd.drawString(text, (GameFacts.screenWidth / 2 - FontHandler.fMetrics.stringWidth(text) / 2) + 1, yPos + 1);
        }
        if (style == 2) {
            red = (red * 2 + Medium.csky[0] * 1) / 3;
            if (red > 255) {
                red = 255;
            }
            if (red < 0) {
                red = 0;
            }
            green = (green * 2 + Medium.csky[1] * 1) / 3;
            if (green > 255) {
                green = 255;
            }
            if (green < 0) {
                green = 0;
            }
            blue = (blue * 2 + Medium.csky[2] * 1) / 3;
            if (blue > 255) {
                blue = 255;
            }
            if (blue < 0) {
                blue = 0;
            }
        }
        rd.setColor(new Color(red, green, blue));
        rd.drawString(text, GameFacts.screenWidth / 2 - FontHandler.fMetrics.stringWidth(text) / 2, yPos);
    }

    public void trackbg(boolean steady) {
        int frame = 0;
        trkl++;
        if (trkl > trklim) {
            frame = 1;
            trklim = (int) (Math.random() * 40D);
            trkl = 0;
        }
        if (steady) {
            frame = 0;
        }
        // the two pictures one after the other, scrolling left a pixel a tick, as many as the picture is wide
        Image[][] shown = trackPictures();
        int tile = shown[frame][0].getWidth(null);
        int x = Math.floorMod(trackScroll, 2 * tile) - 2 * tile;
        for (int j = 0; x < GameFacts.screenWidth; j ^= 1, x += tile) {
            if (x > -tile) {
                rd.drawImage(shown[frame][j], x, 0, null);
            }
        }
        trackScroll = Math.floorMod(trackScroll - 1, 2 * tile);
    }

    /** The stage pictures, as tall as the picture is (they are made for 720). */
    private Image[][] trackPictures() {
        if (GameFacts.screenHeight == trackbg[0][0].getHeight(null)) {
            return trackbg;
        }
        if (trackbgShown == null) {
            trackbgShown = new Image[2][2];
            for (int f = 0; f < 2; f++) {
                for (int j = 0; j < 2; j++) {
                    Image art = trackbg[f][j];
                    trackbgShown[f][j] = Utility.extendArt(art, art.getWidth(null), GameFacts.screenHeight);
                }
            }
        }
        return trackbgShown;
    }

    /**
     * The menus' grey backdrop, drawn over the whole picture and then
     * scrolled up by {@code speed}: as many tiles as the picture is tall,
     * and across a wide one, its neighbours mirrored so they meet.
     */
    private void scrollBackdrop(int speed) {
        if (backdropShown == null) {
            backdropShown = GameFacts.screenWidth == bgmain.getWidth(null) ? bgmain
                    : Utility.mirrorAcross(bgmain, GameFacts.screenWidth);
        }
        int tile = bgmain.getHeight(null);
        for (int y = Math.floorMod(bgScroll, tile) - tile; y < GameFacts.screenHeight; y += tile) {
            rd.drawImage(backdropShown, 0, y, null);
        }
        bgScroll = Math.floorMod(bgScroll - speed, tile);
    }

    /**
     * Where the pause box stands: where it always has at 720 tall (the
     * original's formula, from the width, kept so it does not move), and
     * lower by half what a taller picture adds.
     */
    private int pauseBoxY() {
        return (GameFacts.GAME_WIDTH / 2 - paused.getHeight(null) / 2) / 4 + GameFacts.marginY();
    }

    /** The car select's picture, filling the picture whatever its shape. */
    Image carsBackdrop() {
        if (carsbgShown == null) {
            carsbgShown = carsbg.getWidth(null) == GameFacts.screenWidth
                    && carsbg.getHeight(null) == GameFacts.screenHeight ? carsbg
                    : Utility.extendArt(carsbg, GameFacts.screenWidth, GameFacts.screenHeight);
        }
        return carsbgShown;
    }

    /**
     * Lays the screens out again for the picture's new size,
     * GameFacts.screenWidth x screenHeight, after it took another shape; the
     * full-screen art is fitted to it again as it is next drawn.
     */
    public void fitScreen() {
        int width = GameFacts.screenWidth;
        int height = GameFacts.screenHeight;
        press_enter_to_continue_height = height - 100;
        main_menu_height_origin = (int) (height * 0.2);
        for (int item = 0; item < MENU_ITEMS; item++) {
            menuY[item] = MENU_TOP[item] + main_menu_height_origin;
        }
        car_select_next_back_button_y = (int) (height * 0.68);
        car_select_next_button_x = width - 90;
        car_select_continue_button_y = height - 120;
        stage_select_next_back_button_y = (int) (height * 0.5);
        stage_select_next_button_x = car_select_next_button_x;
        stage_select_continue_button_y = height - 80;
        post_game_continue_button_y = stage_select_continue_button_y;
        credits_next_button_y = height - 100;
        instructions_next_back_button_y = stage_select_next_back_button_y;
        instructions_next_button_x = stage_select_next_button_x;
        instructions_continue_button_x = instructions_next_button_x - 5;
        if (paused != null) {
            pausebox_y = pauseBoxY();
        }
        if (dude[0] != null) {
            placeInsano();
        }
        if (flexpix.length != width * height) {
            flexpix = new int[width * height];
        }
        if (fleximg != null) {
            // the paused race behind the pause menu, stretched until it is taken again
            BufferedImage stretched = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
            Graphics2D g = stretched.createGraphics();
            g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
            g.drawImage(fleximg, 0, 0, width, height, null);
            g.dispose();
            fleximg = stretched;
        }
        backdropShown = null;
        carsbgShown = null;
        trackbgShown = null;
    }

    /** Insano's place on the main menu, over the middle of the menu, where he goes back to. */
    private void placeInsano() {
        insano_mainmenu_x_original = ((GameFacts.screenWidth / 2) - dude[0].getWidth(null) / 2) + 10;
        insano_mainmenu_y_original = 2 + main_menu_height_origin;
        insano_mainmenu_x = insano_mainmenu_x_original;
        insano_mainmenu_y = insano_mainmenu_y_original;
    }

    public void stageselect(CheckPoints checkpoints, Control control) {
        intertrack.play();

        // rd.setFont(new Font("Adventure", 1, 30));
        // drawcs(40, "S E L E C T S T A G E", 255, 128, 0, 3);

        rd.setColor(new Color(20, 20, 20, 100));
        rd.fillRoundRect(Utility.centeredWidthX(145), 20, 145, 27, 23, 30);
        rd.drawImage(select, Utility.centeredImageX(select), stage_select_text_y, null);

        if (checkpoints.stage > 0) {
            if (checkpoints.stage != 1) {
                rd.drawImage(back[pback], stage_select_back_button_x, stage_select_next_back_button_y, null);
            }
            if (checkpoints.stage != GameFacts.numberOfStages) {
                rd.drawImage(next[pnext], stage_select_next_button_x, stage_select_next_back_button_y, null);
            }
        }

        // rectangle borrowed from g6
        rd.setColor(new Color(20, 20, 20, 100));
        rd.fillRoundRect(Utility.centeredWidthX(350), 60, 350, 50, 23, 30);

        rd.setFont(new Font("SansSerif", 1, 13));
        FontHandler.fMetrics = rd.getFontMetrics();

        if (checkpoints.stage != GameFacts.numberOfStages && checkpoints.stage > 0) {
            drawcs(80, "< Stage " + checkpoints.stage + " >", 255, 255, 255, 3);
        }
        if (checkpoints.stage == GameFacts.numberOfStages) {
            drawcs(80, "< Final Stage >", 255, 255, 255, 3);
        }
        if (checkpoints.stage < 0) {
            drawcs(80, "< Custom Stage >", 255, 255, 255, 3);
        }
        drawcs(100, "| " + checkpoints.name + " |", 210, 210, 210, 3);
        rd.drawImage(contin[pcontin], Utility.centeredImageX(contin[pcontin]), stage_select_continue_button_y, null);
        rd.setFont(new Font("SansSerif", 1, 11));
        FontHandler.fMetrics = rd.getFontMetrics();
        drawcs(GameFacts.screenHeight - 5, "You can also use Keyboard Arrows and Enter to navigate.", 82, 90, 0, 3);
        if (raceAgain) {
            raceAgain = false;
            control.enter = true;
        }
        if (control.handb || control.enter) {
            sm.play("tick");
            if (checkpoints.stage != -1) {
                asay = "Stage " + checkpoints.stage + ":  " + checkpoints.name;
            } else {
                asay = "Custom Stage:  " + checkpoints.name;
            }
            dudo = 150;
            Medium.trk = false;
            Medium.focus_point = 500;
            fase = Phase.SELECTEDCARSAVE;
            control.handb = false;
            control.enter = false;
            intertrack.setPaused(true);
            intertrack.unload();
        }
        if (control.right && checkpoints.stage < GameFacts.numberOfStages) {
            sm.play("tick");
            if (checkpoints.stage != unlocked) {
                checkpoints.stage++;
                fase = Phase.NPLAYERSCHECK;
                control.right = false;
            } else {
                fase = Phase.LOCKEDSTAGE;
                lockcnt = 100;
                control.right = false;
            }
        }
        if (control.left && checkpoints.stage > 1) {
            sm.play("tick");
            checkpoints.stage--;
            fase = Phase.NPLAYERSCHECK;
            control.left = false;
        }
    }

    public void snap(int stage) {
        dmg = loadsnap(odmg);
        pwr = loadsnap(opwr);
        was = loadsnap(owas);
        lap = loadsnap(olap);
        pos = loadsnap(opos);
        int j = 0;
        do {
            cntdn[j] = loadsnap(ocntdn[j]);
        } while (++j < 4);
        yourwasted = loadsnap(oyourwasted);
        youlost = loadsnap(oyoulost);
        youwon = loadsnap(oyouwon);
        youwastedem = loadsnap(oyouwastedem);
        gameh = loadsnap(ogameh);
        loadingmusic = loadopsnap(oloadingmusic, stage, 76);
        star[0] = loadopsnap(ostar[0], stage, 0);
        star[1] = loadopsnap(ostar[1], stage, 0);
        flaot = loadopsnap(oflaot, stage, 1);
    }

    private Image loadsnap(Image image) {
        int height = image.getHeight(ob);
        int width = image.getWidth(ob);
        int pixels[] = new int[width * height];
        PixelGrabber grabber = new PixelGrabber(image, 0, 0, width, height, pixels, 0, width);
        try {
            grabber.grabPixels();
        } catch (InterruptedException ignored) {
        }
        for (int k = 0; k < width * height; k++) {
            if (pixels[k] != 0xffc0c0c0 && pixels[k] != pixels[width * height - 1]) {
                Color color = new Color(pixels[k]);
                int red = (int) (color.getRed() + color.getRed() * (Medium.snap[0] / 100F));
                if (red > 225) {
                    red = 225;
                }
                if (red < 0) {
                    red = 0;
                }
                int green = (int) (color.getGreen() + color.getGreen() * (Medium.snap[1] / 100F));
                if (green > 225) {
                    green = 225;
                }
                if (green < 0) {
                    green = 0;
                }
                int blue = (int) (color.getBlue() + color.getBlue() * (Medium.snap[2] / 100F));
                if (blue > 225) {
                    blue = 225;
                }
                if (blue < 0) {
                    blue = 0;
                }
                Color tinted = new Color(red, green, blue);
                pixels[k] = tinted.getRGB();
            } else if (pixels[k] == 0xffc0c0c0) {
                Color sky = new Color(Medium.csky[0], Medium.csky[1], Medium.csky[2]);
                pixels[k] = sky.getRGB();
            }
        }

        return createImage(new MemoryImageSource(width, height, pixels, 0, width));
    }

    public void resetstat(int stage) {
        arrace = false;
        ana = 0;
        cntan = 0;
        cntovn = 0;
        tcnt = 30;
        wasay = false;
        clear = 0;
        dmcnt = 0;
        pwcnt = 0;
        auscnt = 45;
        pnext = 0;
        pback = 0;
        starcnt = 130;
        gocnt = 3;
        grrd = true;
        aird = true;
        bfcrash = 0;
        cntwis = 0;
        bfskid = 0;
        pwait = 7;
        holdcnt = 0;
        holdit = false;
        winner = false;
        setnumber = false;
        wasted = 0;
        int j = 0;
        do {
            dested[j] = 0;
        } while (++j < GameFacts.numberOfPlayers);
        sortcars(stage);
    }

    /**
     * x_origin defines the relative position horizontally at which to draw the bars
     */
    private void drawstat(int maxDamage, int damage, boolean redraw, float power, int x_origin) {
        int xs[] = new int[4];
        int ys[] = new int[4];
        int bar_x_origin = x_origin + 62;

        if (redraw) {
            xs[0] = bar_x_origin;
            ys[0] = 11;
            xs[1] = bar_x_origin;
            ys[1] = 19;
            xs[2] = 630;
            ys[2] = 19;
            xs[3] = 630;
            ys[3] = 11;
            rd.setColor(new Color(Medium.csky[0], Medium.csky[1], Medium.csky[2]));
            rd.fillPolygon(xs, ys, 4);
        }
        if (damage > maxDamage) {
            damage = maxDamage;
        }
        int damageBar = (int) (98F * ((float) damage / (float) maxDamage));
        xs[0] = bar_x_origin;
        ys[0] = 11;
        xs[1] = bar_x_origin;
        ys[1] = 20;
        xs[2] = bar_x_origin + damageBar;
        ys[2] = 20;
        xs[3] = bar_x_origin + damageBar;
        ys[3] = 11;
        int red = 244;
        int green = 244;
        int blue = 11;
        if (damageBar > 33) {
            green = (int) (244F - 233F * ((damageBar - 33) / 65F));
        }
        if (damageBar > 70) {
            if (dmcnt < 10) {
                if (dmflk) {
                    green = 170;
                    dmflk = false;
                } else {
                    dmflk = true;
                }
            }
            dmcnt++;
            if (dmcnt > 167D - damageBar * 1.5D) {
                dmcnt = 0;
            }
        }
        red = (int) (red + red * (Medium.snap[0] / 100F));
        if (red > 255) {
            red = 255;
        }
        if (red < 0) {
            red = 0;
        }
        green = (int) (green + green * (Medium.snap[1] / 100F));
        if (green > 255) {
            green = 255;
        }
        if (green < 0) {
            green = 0;
        }
        blue = (int) (blue + blue * (Medium.snap[2] / 100F));
        if (blue > 255) {
            blue = 255;
        }
        if (blue < 0) {
            blue = 0;
        }
        rd.setColor(new Color(red, green, blue));
        rd.fillPolygon(xs, ys, 4);
        xs[0] = bar_x_origin;
        ys[0] = 31;
        xs[1] = bar_x_origin;
        ys[1] = 40;
        xs[2] = (int) (bar_x_origin + power);
        ys[2] = 40;
        xs[3] = (int) (bar_x_origin + power);
        ys[3] = 31;
        red = 128;
        if (power == 98F) {
            red = 64;
        }
        green = (int) (190D + power * 0.37D);
        blue = 244;
        if (auscnt < 45 && aflk) {
            red = 128;
            green = 244;
            blue = 244;
        }
        red = (int) (red + red * (Medium.snap[0] / 100F));
        if (red > 255) {
            red = 255;
        }
        if (red < 0) {
            red = 0;
        }
        green = (int) (green + green * (Medium.snap[1] / 100F));
        if (green > 255) {
            green = 255;
        }
        if (green < 0) {
            green = 0;
        }
        blue = (int) (blue + blue * (Medium.snap[2] / 100F));
        if (blue > 255) {
            blue = 255;
        }
        if (blue < 0) {
            blue = 0;
        }
        rd.setColor(new Color(red, green, blue));
        rd.fillPolygon(xs, ys, 4);
        if (Medium.flex == 2 && power != 98F) {
            xs[0] = (int) (bar_x_origin + power);
            ys[0] = 31;
            xs[1] = (int) (bar_x_origin + power);
            ys[1] = 39;
            xs[2] = 630;
            ys[2] = 39;
            xs[3] = 630;
            ys[3] = 31;
            rd.setColor(new Color(Medium.csky[0], Medium.csky[1], Medium.csky[2]));
            rd.fillPolygon(xs, ys, 4);
        }
    }

    /*
     * "pressed" lol
     */
    private Image bressed(Image image) {
        int height = image.getHeight(ob);
        int width = image.getWidth(ob);
        int pixels[] = new int[width * height];
        PixelGrabber grabber = new PixelGrabber(image, 0, 0, width, height, pixels, 0, width);
        try {
            grabber.grabPixels();
        } catch (InterruptedException ignored) {
        }
        Color color = new Color(247, 255, 165);
        for (int k = 0; k < width * height; k++) {
            if (pixels[k] != pixels[width * height - 1]) {
                pixels[k] = color.getRGB();
            }
        }

        return createImage(new MemoryImageSource(width, height, pixels, 0, width));
    }

    private void loading() {
        rd.setColor(new Color(0, 0, 0));
        rd.fillRect(0, 0, GameFacts.screenWidth, GameFacts.screenHeight);
        rd.drawImage(sign, Utility.centeredImageX(sign), 10, this);
        rd.drawImage(hello, Utility.centeredImageX(hello), 80, this);
        rd.setColor(new Color(198, 214, 255));
        rd.fillRoundRect(Utility.centeredWidthX(300), 315, 300, 80, 30, 70);
        rd.setColor(new Color(128, 167, 255));
        rd.drawRoundRect(Utility.centeredWidthX(300), 315, 300, 80, 30, 70);
        rd.drawImage(loadbar, Utility.centeredImageX(loadbar), 340, this);
        rd.setFont(new Font("SansSerif", 1, 11));
        FontHandler.fMetrics = rd.getFontMetrics();
        drawcs(333, "Loading game, please wait.", 0, 0, 0, 3);
        shload += ((dnload + 10F) - shload) / 100F;
        if (shload > kbload) {
            shload = kbload;
        }
        if (dnload == kbload) {
            shload = kbload;
        }
        drawcs(385, "" + (int) (((26F + (shload / kbload) * 200F) / 226F) * 100F) + " % loaded    |    "
                + (kbload - (int) shload) + " KB remaining", 32, 64, 128, 3);
        rd.setColor(new Color(32, 64, 128));
        rd.fillRect(Utility.centeredImageX(loadbar) + 5, 346, 26 + (int) ((shload / kbload) * 200F), 10);
    }

    /**
     * Redirects every UI draw call to another surface.
     * <p>
     * The frame loop uses this to render the HUD into its own layer once per
     * simulation tick (so HUD timers and pause handling keep running at the legacy
     * cadence) while the 3D scene is redrawn at the display rate and composited
     * underneath it.
     */
    public void setTarget(Graphics2D target) {
        this.rd = target;
    }

    public Graphics2D getTarget() {
        return rd;
    }

    public GameUi(Graphics2D target, GameSparker applet) {
        fase = Phase.LOADING;
        oldfase = Phase.INGAME;
        starcnt = 0;
        unlocked = 1;
        lockcnt = 0;
        opselect = 1;
        shaded = false;
        flipo = 0;
        nextc = false;
        gatey = 0;
        looped = 1;
        sc = new int[51];
        holdit = false;
        holdcnt = 0;
        winner = false;
        flexpix = new int[GameFacts.screenHeight * GameFacts.screenWidth];
        flatrstart = 0;
        runtyp = 0;
        trackbg = new Image[2][2];
        dude = new Image[3];
        dudeb = new Image[3];
        duds = 0;
        dudo = 0;
        next = new Image[2];
        back = new Image[2];
        contin = new Image[2];
        ostar = new Image[2];
        star = new Image[3];
        pcontin = 0;
        pnext = 0;
        pback = 0;
        pstar = 0;
        ocntdn = new Image[4];
        cntdn = new Image[4];
        gocnt = 0;
        pengs = new boolean[5];
        aird = false;
        grrd = false;
        setnumber = false;
        pwastd = false;
        mutes = false;
        // tracks = new RadicalMusic[GameFacts.numberOfStages];
        // loadedt = new boolean[GameFacts.numberOfStages];
        lastload = -1;
        mutem = false;
        macn = false;
        arrace = false;
        ana = 0;
        cntan = 0;
        cntovn = 0;
        flk = false;
        tcnt = 30;
        tflk = false;
        say = "";
        wasay = false;
        clear = 0;
        posit = 0;
        wasted = 0;
        laps = 0;
        dested = new int[51];
        dmcnt = 0;
        dmflk = false;
        pwcnt = 0;
        pwflk = false;
        loop = "";
        spin = "";
        asay = "";
        auscnt = 45;
        aflk = false;
        kbload = 0;
        dnload = 0;
        shload = 0.0F;
        radpx = 147;
        pin = 60;
        trkl = 0;
        trklim = (int) (Math.random() * 40D);
        flkat = (int) (60D + 140D * Math.random());
        movly = (int) (100D + 100D * Math.random());
        /* this is later set properly in loadimages() */
        insano_mainmenu_x = GameFacts.screenWidth / 2;
        insano_mainmenu_y = 2 + main_menu_height_origin;
        insano_mainmenu_target_x = 0;
        insano_mainmenu_target_y = 0;
        pgady = new int[9];
        pgas = new boolean[9];
        lxm = -10;
        lym = -10;
        pwait = 7;
        stopcnt = 0;
        cntwis = 0;
        crshturn = 0;
        bfcrash = 0;
        bfskid = 0;
        crashup = false;
        skidup = false;
        skflg = 0;
        dskflg = 0;
        flatr = 0;
        flyr = 0;
        flyrdest = 0;
        flang = 0;
        flangados = 0;
        blackn = 0.0F;
        blacknados = 0.0F;
        app = applet;
        rd = target;
        MediaTracker tracker = new MediaTracker(app);
        hello = Toolkit.getDefaultToolkit().getImage(GameUi.class.getResource("aimg/hello.gif"));
        tracker.addImage(hello, 0);
        try {
            tracker.waitForID(0);
        } catch (Exception ignored) {
        }
        sign = Toolkit.getDefaultToolkit().getImage(GameUi.class.getResource("aimg/sign.gif"));
        tracker.addImage(sign, 0);
        try {
            tracker.waitForID(0);
        } catch (Exception ignored) {
        }
        loadbar = Toolkit.getDefaultToolkit().getImage(GameUi.class.getResource("aimg/loadbar.gif"));
        tracker.addImage(loadbar, 0);
        try {
            tracker.waitForID(0);
        } catch (Exception ignored) {
        }
        int i = 0;
        loadedt = false;
        fitScreen();
    }

    public void maini(Control control, CheckPoints checkpoints, Madness drivers[], ContO models[], ContO scene[]) {
        // might redo the entire menu its horrible

        int menuItems = MENU_ITEMS;

        if (GameSparker.DEBUG) {
            if (!devtriggered) {
                HLogger.info("Developer Console triggered");

                DevTool console = new DevTool(checkpoints, drivers, models, scene, this);
                console.showConsole();
                devtriggered = true;
            }
        }

        if (flipo == 0) {
            bgScroll = 0;
            app.setCursor(new Cursor(0));
        }

        /* handle the moving background in main menu */
        scrollBackdrop(20);

        /* handle the light red/ping background behind "need for madness!?" text */
        int logomadbg_xpos = Utility.centeredImageX(logomadbg);
        if (flipo > flkat) {
            rd.drawImage(logomadbg, logomadbg_xpos + (int) (4D - Math.random() * 8D),
                    143 + main_menu_height_origin + (int) (4D - Math.random() * 8D), null);
        } else {
            rd.drawImage(logomadbg, logomadbg_xpos, 143 + main_menu_height_origin, null);
        }

        /* handle initial insano position */
        rd.drawImage(dude[0], insano_mainmenu_x, insano_mainmenu_y, null);

        /* handle cars surrounding "need for madness!? text" */
        rd.drawImage(logocars, Utility.centeredImageX(logocars), 28 + main_menu_height_origin, null);

        if (flipo > flkat) {
            rd.drawImage(logomadnes, Utility.centeredImageX(logomadnes) + (int) (4D - Math.random() * 8D),
                    148 + main_menu_height_origin + (int) (4D - Math.random() * 8D), null);
        } else {
            rd.drawImage(logomadnes, Utility.centeredImageX(logomadnes), 148 + main_menu_height_origin, null);
        }

        flipo++;

        if (flipo > flkat + 36) {
            flipo = 1;
            flkat = (int) (60D + 140D * Math.random());
        }

        /* handles moving insano around in the menu */
        if (movly <= 10) {
            if (movly == 10 || movly == 8 || movly == 6 || movly == 4 || movly == 2) {
                insano_mainmenu_target_x = (int) (insano_mainmenu_x + 200 - 500D * Math.random());
                insano_mainmenu_target_y = (int) (insano_mainmenu_y + 200 - 500D * Math.random());
                if (movly == 2) {
                    insano_mainmenu_target_x = insano_mainmenu_x_original;
                    insano_mainmenu_target_y = insano_mainmenu_y_original;
                }
                movly--;
            }
            insano_mainmenu_x += (insano_mainmenu_target_x - insano_mainmenu_x) / 15;
            insano_mainmenu_y += (insano_mainmenu_target_y - insano_mainmenu_y) / 15;
            if (movly != 1) {
                if (Utility.pys(insano_mainmenu_x, insano_mainmenu_target_x, insano_mainmenu_y,
                        insano_mainmenu_target_y) < 20F) {
                    movly--;
                }
            } else {
                if (insano_mainmenu_x > insano_mainmenu_target_x) {
                    insano_mainmenu_x--;
                } else {
                    insano_mainmenu_x++;
                }
                if (insano_mainmenu_y > insano_mainmenu_target_y) {
                    insano_mainmenu_y--;
                } else {
                    insano_mainmenu_y++;
                }
                if (Utility.pys(insano_mainmenu_x, insano_mainmenu_target_x, insano_mainmenu_y,
                        insano_mainmenu_target_y) < 2.0F) {
                    movly--;
                }
            }
            if (movly == 0) {
                insano_mainmenu_x = insano_mainmenu_x_original;
                insano_mainmenu_y = insano_mainmenu_y_original;
                movly = (int) (100D + 100D * Math.random());
            }
        } else if (flipo >= movly) {
            movly = 10;
        }

        drawMenuBackdrop(212 + main_menu_height_origin);
        rd.drawImage(nfmcoms, Utility.centeredImageX(nfmcoms), 195 + main_menu_height_origin, null);
        rd.drawImage(byrd, Utility.centeredImageX(byrd), 383 + OPBACK_GAP + main_menu_height_origin, null);

        if (control.up) {
            opselect--;
            if (opselect == -1) {
                opselect = menuItems - 1;
            }
            control.up = false;
        }
        if (control.down) {
            opselect++;
            if (opselect >= menuItems) {
                opselect = 0;
            }
            control.down = false;
        }
        clickOnMove(0);
        for (int item = 0; item < menuItems; item++) {
            int x = Utility.centeredWidthX(MENU_WIDTH[item]);
            if (opselect == item) {
                if (shaded) {
                    rd.setColor(new Color(140, 70, 0));
                    rd.fillRect(x, menuY[item], MENU_WIDTH[item], main_menu_button_height);
                    aflk = false;
                }
                if (aflk) {
                    rd.setColor(MENU_FLICKER[item]);
                    aflk = false;
                } else {
                    rd.setColor(new Color(255, 128, 0));
                    aflk = true;
                }
            } else {
                rd.setColor(new Color(0, 0, 0));
            }
            rd.drawRoundRect(x, menuY[item], MENU_WIDTH[item], main_menu_button_height, 7, 20);
        }
        rd.drawImage(opti, Utility.centeredImageX(opti), 250 + main_menu_height_origin, null);
        SettingsScreen.drawMenuItem(rd, "Custom Game", GameFacts.screenWidth / 2F, menuY[MENU_CUSTOM] + 18);
        SettingsScreen.drawMenuItem(rd, "Split Screen", GameFacts.screenWidth / 2F, menuY[MENU_SPLIT] + 18);
        SettingsScreen.drawMenuItem(rd, "Settings", GameFacts.screenWidth / 2F, menuY[MENU_SETTINGS] + 18);
        SettingsScreen.drawMenuItem(rd, "Exit Game", GameFacts.screenWidth / 2F, menuY[MENU_EXIT] + 18);
        if (control.enter || control.handb) {
            clickNow();
            if (opselect == MENU_PLAY) {
                if (unlocked == 1 && oldfase == Phase.INGAME) {
                    oldfase = Phase.CARSELECTTRIGGER;
                    fase = Phase.INSTRUCTIONS;
                } else {
                    fase = Phase.CARSELECTTRIGGER;
                }
            }
            if (opselect == MENU_INSTRUCTIONS) {
                oldfase = Phase.MAINMENU;
                fase = Phase.INSTRUCTIONS;
            }
            if (opselect == MENU_CREDITS) {
                fase = Phase.CREDITS;
            }
            if (opselect == MENU_CUSTOM) {
                openCustomGame();
            }
            if (opselect == MENU_SPLIT) {
                SplitRace.on = true;
                GameFacts.humans = 2; // each player's keys and controller are theirs from the car select on
                fase = Phase.SPLITCARSELECT;
            }
            if (opselect == MENU_SETTINGS) {
                openSettings(Phase.MAINMENU);
            }
            if (opselect == MENU_EXIT) {
                RunApp.requestExit();
            }
            flipo = 0;
            control.enter = false;
            control.handb = false;
        }
        if (shaded) {
            app.repaint();
            try {
                Thread.sleep(100L);
            } catch (InterruptedException ignored) {
            }
        }
    }

    /**
     * The main menu's brown backdrop, taller than its picture by one item: the
     * picture's rows above {@link #OPBACK_GAP_AT}, that row stretched over
     * {@link #OPBACK_GAP} pixels, then the rest, so its round ends stay whole.
     */
    private void drawMenuBackdrop(int y) {
        int x = Utility.centeredImageX(opback);
        int w = opback.getWidth(null);
        int h = opback.getHeight(null);
        int at = OPBACK_GAP_AT;
        rd.drawImage(opback, x, y, x + w, y + at, 0, 0, w, at, null);
        rd.drawImage(opback, x, y + at, x + w, y + at + OPBACK_GAP, 0, at, w, at + 1, null);
        rd.drawImage(opback, x, y + at + OPBACK_GAP, x + w, y + h + OPBACK_GAP, 0, at, w, h, null);
    }

    /**
     * A click on the second speaker when the highlighted item of a menu
     * moves, by key or by mouse (the mouse moves it between frames); the
     * original menus were silent there. Coming into a menu does not click.
     */
    private void clickOnMove(int menu) {
        int at = menu * 100 + opselect;
        long now = System.nanoTime();
        // a menu shown again after a while is a menu come into, not a move
        if (clickedMenu / 100 == menu && at != clickedMenu && now - clickedMenuAt < 200_000_000L) {
            clickNow();
        }
        clickedMenu = at;
        clickedMenuAt = now;
    }

    /** A click on the second speaker only. */
    void clickNow() {
        if (!mutes) {
            Sound3D.event(CLICK_CLIP, true);
        }
    }

    /* the countdown portrait (not on a Mac): where it can stand, left or right, and its size */
    private static final int PORTRAIT_LEFT = 217;
    private static final int PORTRAIT_RIGHT = 331;
    private static final int PORTRAIT_WIDTH = 122;
    private static final int PORTRAIT_HEIGHT = 160;
    private static final int PORTRAIT_PIXELS = PORTRAIT_WIDTH * PORTRAIT_HEIGHT;

    /**
     * Where the countdown portrait can stand, in the picture's pixels: both
     * places {@link #blendude} picks from, side by side. It tints the portrait
     * with what this part of the picture shows, and reads nothing else.
     */
    static final Rectangle PORTRAIT_BEHIND = new Rectangle(PORTRAIT_LEFT, 0,
            PORTRAIT_RIGHT + PORTRAIT_WIDTH - PORTRAIT_LEFT, PORTRAIT_HEIGHT);

    /** The portrait's three pictures as {@link #blendude} reads them. */
    private final int[][] dudePixels = new int[3][];

    /**
     * Tints the countdown portrait with the picture behind it, as the
     * original did. The picture's pixels are read straight from its buffer
     * and the portrait's once, and blended with whole numbers: the same
     * colours as reading both through a {@link PixelGrabber} and blending
     * through {@link Color}, which took the game thread some 25 ms.
     */
    public void blendude(BufferedImage picture) {
        if (!macn) {
            if (Math.random() > Math.random()) {
                dudo = PORTRAIT_LEFT;
            } else {
                dudo = PORTRAIT_RIGHT;
            }
            int background[] = new int[PORTRAIT_PIXELS];
            if (picture.getType() == BufferedImage.TYPE_INT_RGB) {
                // the pixels as stored, 0xRRGGBB, in one go: the blend reads only red, green and blue
                picture.getRaster().getDataElements(dudo, 0, PORTRAIT_WIDTH, PORTRAIT_HEIGHT, background);
            } else {
                picture.getRGB(dudo, 0, PORTRAIT_WIDTH, PORTRAIT_HEIGHT, background, 0, PORTRAIT_WIDTH);
            }
            int j = 0;
            do {
                int portrait[] = portraitPixels(j);
                if (portrait == null) {
                    dudo = -1;
                }
                if (dudo != -1) {
                    int pixels[] = portrait.clone();
                    int k = 0;
                    do {
                        if (pixels[k] != pixels[0]) {
                            // a quarter the portrait, three quarters the picture behind it, rounded down as the original
                            pixels[k] = 0xFF000000 | FrameScaler.mix(pixels[k], background[k], 192);
                        }
                    } while (++k < PORTRAIT_PIXELS);
                    dudeb[j] = createImage(new MemoryImageSource(PORTRAIT_WIDTH, PORTRAIT_HEIGHT, pixels, 0, PORTRAIT_WIDTH));
                }
            } while (++j < 3);
        } else {
            if (Math.random() > Math.random()) {
                dudo = 176;
            } else {
                dudo = 372;
            }
            int i = 0;
            do {
                dudeb[i] = dude[i];
            } while (++i < 3);
        }
    }

    /**
     * Reads the portrait's pictures ahead of {@link #blendude}, which runs in
     * a countdown tick: reading them the first time decodes them, some 20 ms.
     */
    void readPortraits() {
        if (!macn) {
            for (int j = 0; j < 3; j++) {
                portraitPixels(j);
            }
        }
    }

    /**
     * Portrait picture {@code j}'s pixels from 10 down, read once (the
     * pictures are loaded once, at the start); null if reading it was
     * interrupted.
     */
    private int[] portraitPixels(int j) {
        if (dudePixels[j] == null) {
            int pixels[] = new int[PORTRAIT_PIXELS];
            PixelGrabber dudeGrabber = new PixelGrabber(dude[j], 0, 10, PORTRAIT_WIDTH, PORTRAIT_HEIGHT, pixels, 0,
                    PORTRAIT_WIDTH);
            try {
                dudeGrabber.grabPixels();
            } catch (InterruptedException ignored) {
                return null;
            }
            dudePixels[j] = pixels;
        }
        return dudePixels[j];
    }

    public void musicomp(int stage, Control control) {
        hipnoload(stage, true);
        if (control.handb || control.enter) {
            System.gc();
            fase = Phase.INGAME;
            control.handb = false;
            control.enter = false;
        }
    }

    public void loaddata() {
        kbload = 950;
        String osName = System.getProperty("os.name");
        if (!osName.startsWith("Win")) {
            macn = true;
        }
        runtyp = 176;

        runner = new Thread(this);
        runner.start();

        // the sounds decode on a thread of their own while the images load here
        Throwable[] soundsFailed = new Throwable[1];
        Thread sounds = new Thread(() -> {
            try {
                loadsounds();
            } catch (Throwable ex) {
                soundsFailed[0] = ex;
            }
        }, "nfm-load-sounds");
        sounds.start();

        loadimages();
        // loadnetworkimages();

        // cars = new RadicalMusic("music/cars.radq", 200, 7900, 125, macn);
        // dnload += 27;

        // cars = new RadicalMusic("music/cars.mp3");
        // dnload += 3718;

        /*
         * int j = 0;
         * do {
         * int k = 0;
         * do {
         * engs[k][j] = getSound("sounds/" + s + "" + k + "" + j + ".au");
         * dnload += 3;
         * } while (++k < 5);
         * pengs[j] = false;
         * } while (++j < 5);
         */
        // stages = new RadicalMusic("music/stages.radq", 135, 7800, 125, macn);
        // dnload += 91;

        /*
         * j = 0;
         * do {
         * air[j] = getSound("sounds/" + s + "air" + j + ".au");
         * dnload += 2;
         * } while (++j < 6);
         * j = 0;
         * do {
         * crash[j] = getSound("sounds/" + s + "crash" + (j + 1) + "." + s1);
         * if (i == 2) {
         * dnload += 10;
         * } else {
         * dnload += 7;
         * }
         * } while (++j < 3);
         * j = 0;
         * do {
         * lowcrash[j] = getSound("sounds/" + s + "lowcrash" + (j + 1) + "." + s1);
         * if (i == 2) {
         * dnload += 10;
         * } else {
         * dnload += 3;
         */
        try {
            sounds.join();
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
        }
        if (soundsFailed[0] != null) {
            HLogger.error("Loading the sounds failed: " + soundsFailed[0]);
        }
    }

    /** Counts {@code n} more of the game loaded, from any loader thread. */
    synchronized void addLoaded(int n) {
        dnload += n;
    }

    public void loadsounds() {
        addLoaded(3);

        try {
            for (GameFiles.Entry soundEntry : GameFiles.archive("sounds")) {
                String name = soundEntry.name();
                byte[] sound = soundEntry.data();

                int i;
                for (i = 0; i < 5; ++i) {
                    for (int v = 0; v < 5; ++v) {
                        if (name.equals(v + "" + i + ".wav")) {
                            sm.add("engs" + v + i, new SoundClipUnthreaded(sound));
                        }
                    }
                }

                for (i = 0; i < 6; ++i) {
                    if (name.equals("air" + i + ".wav")) {
                        sm.add("air" + i, new SoundClipUnthreaded(sound));
                    }
                }

                for (i = 0; i < 3; ++i) {
                    if (name.equals("crash" + (i + 1) + ".wav")) {
                        sm.add("crash" + i, new SoundClipUnthreaded(sound));
                    }
                }

                for (i = 0; i < 3; ++i) {
                    if (name.equals("lowcrash" + (i + 1) + ".wav")) {
                        sm.add("lowcrash" + i, new SoundClipUnthreaded(sound));
                    }
                }

                for (i = 0; i < 3; ++i) {
                    if (name.equals("skid" + (i + 1) + ".wav")) {
                        sm.add("skid" + i, new SoundClipUnthreaded(sound));
                    }
                }

                for (i = 0; i < 3; ++i) {
                    if (name.equals("dustskid" + (i + 1) + ".wav")) {
                        sm.add("dustskid" + i, new SoundClipUnthreaded(sound));
                    }
                }

                if (name.equals("powerup.wav")) {
                    sm.add("powerup", new SoundClipThreaded(sound));
                }

                if (name.equals("tires.wav")) {
                    sm.add("tires", new SoundClipUnthreaded(sound));
                }

                if (name.equals("checkpoint.wav")) {
                    sm.add("checkpoint", new SoundClipUnthreaded(sound));
                }

                if (name.equals("carfixed.wav")) {
                    sm.add("carfixed", new SoundClipThreaded(sound));
                }

                if (name.equals("three.wav")) {
                    sm.add("three", new SoundClipUnthreaded(sound));
                }

                if (name.equals("two.wav")) {
                    sm.add("two", new SoundClipUnthreaded(sound));
                }

                if (name.equals("one.wav")) {
                    sm.add("one", new SoundClipUnthreaded(sound));
                }

                if (name.equals("go.wav")) {
                    sm.add("go", new SoundClipUnthreaded(sound));
                }

                if (name.equals("wasted.wav")) {
                    sm.add("wasted", new SoundClipUnthreaded(sound));
                }

                if (name.equals("firewasted.wav")) {
                    sm.add("firewasted", new SoundClipUnthreaded(sound));
                }

                if (name.equals("tick.wav")) {
                    sm.add("tick", new SoundClipThreaded(sound));
                }

                if (name.equals("tliu.wav")) {
                    sm.add("tliu", new SoundClipThreaded(sound));
                }

                if (name.equals("woosh.wav")) {
                    sm.add("woosh", new SoundClipThreaded(sound));
                }

                if (name.equals("cosmic.wav")) {
                    sm.add("cosmic", new SoundClipThreaded(sound));
                }

                addLoaded(5);
            }

            /*
             * } while (++j < 3);
             * tires = getSound("sounds/" + s + "tires." + s1);
             * if (i == 2) {
             * dnload += 24;
             * } else {
             * dnload += 4;
             * }
             * checkpoint = getSound("sounds/" + s + "checkpoint." + s1);
             * if (i == 2) {
             * dnload += 24;
             * } else {
             * dnload += 6;
             * }
             * carfixed = getSound("sounds/" + s + "carfixed." + s1);
             * if (i == 2) {
             * dnload += 24;
             * } else {
             * dnload += 10;
             * }
             * powerup = getSound("sounds/" + s + "powerup." + s1);
             * if (i == 2) {
             * dnload += 42;
             * } else {
             * dnload += 8;
             * }
             * three = getSound("sounds/" + s + "three." + s1);
             * if (i == 2) {
             * dnload += 24;
             * } else {
             * dnload += 4;
             * }
             * two = getSound("sounds/" + s + "two." + s1);
             * if (i == 2) {
             * dnload += 24;
             * } else {
             * dnload += 2;
             * }
             * one = getSound("sounds/" + s + "one." + s1);
             * if (i == 2) {
             * dnload += 24;
             * } else {
             * dnload += 4;
             * }
             * go = getSound("sounds/" + s + "go." + s1);
             * if (i == 2) {
             * dnload += 24;
             * } else {
             * dnload += 4;
             * }
             * wastd = getSound("sounds/" + s + "wasted." + s1);
             * if (i == 2) {
             * dnload += 24;
             * } else {
             * dnload += 4;
             * }
             * firewasted = getSound("sounds/" + s + "firewasted." + s1);
             * if (i == 2) {
             * dnload += 24;
             * } else {
             * dnload += 10;
             */

        } catch (Exception ex) {
            HLogger.error("Error Loading Sounds: " + ex);

        }

        /*
         * j = 0;
         * do {
         * skid[j] = getSound("sounds/" + s + "skid" + (j + 1) + "." + s1);
         * if (i == 2) {
         * dnload += 22;
         * } else {
         * dnload += 6;
         * }
         * } while (++j < 3);
         * 
         * 
         * }
         * j = 0;
         * do {
         * dustskid[j] = getSound("sounds/" + s + "dustskid" + (j + 1) + "." + s1);
         * if (i == 2) {
         * dnload += 22;
         * } else {
         * dnload += 7;
         * }
         * } while (++j < 3);
         */

        System.gc();
    }

    public void clicknow() {
        rd.setColor(new Color(198, 214, 255));
        rd.fillRoundRect(GameFacts.screenWidth / 2 - 150, 315, 300, 80, 30, 70);
        rd.setColor(new Color(128, 167, 255));
        rd.drawRoundRect(GameFacts.screenWidth / 2 - 150, 315, 300, 80, 30, 70);
        if (aflk) {
            drawcs(355, "Click here to Start", 0, 0, 0, 3);
            aflk = false;
        } else {
            drawcs(355, "Click here to Start", 0, 67, 200, 3);
            aflk = true;
        }
    }

    private Image loadimage(byte data[], MediaTracker tracker, Toolkit toolkit) {
        Image image = toolkit.createImage(data);
        tracker.addImage(image, 0);
        try {
            tracker.waitForID(0);
        } catch (Exception ignored) {
        }
        return image;
    }

    public void rad(int frame) {
        if (frame == 0) {
            radpx = Utility.centeredImageX(radicalplay);
            pin = 0;
        }

        trackbg(false);
        rd.setColor(new Color(0, 0, 0));
        rd.fillRect(0, 110, GameFacts.screenWidth, 59);

        if (pin != 0) {
            rd.drawImage(radicalplay, radpx + (int) (8D * Math.random() - 4D), 110, null);
        } else {
            rd.drawImage(radicalplay, Utility.centeredImageX(radicalplay), 110, null);
        }

        if (pin == 1) {
            radpx += GameFacts.screenWidth / 20;
            if (radpx > GameFacts.screenWidth) {
                radpx = -453;
            }
        }

        if (Math.abs(radpx - Utility.centeredImageX(radicalplay)) < GameFacts.screenWidth / 20) {
            radpx = Utility.centeredImageX(radicalplay);
            pin = 0;
        }

        if (frame == 40) {
            radpx = Utility.centeredImageX(radicalplay);
            pin = 1;
        }

        if (radpx == Utility.centeredImageX(radicalplay)) {
            rd.setFont(new Font("SansSerif", 1, 11));
            FontHandler.fMetrics = rd.getFontMetrics();
            drawcs(160 + (int) (5F * Medium.fxRandom()), "Radicalplay.com", 112, 120, 143, 3);
        }

        rd.setFont(new Font("SansSerif", 1, 11));
        FontHandler.fMetrics = rd.getFontMetrics();
        if (aflk) {
            drawcs(190, "And we are never going to find the new unless we get a little crazy...", 112, 120, 143, 3);
            aflk = false;
        } else {
            drawcs(192, "And we are never going to find the new unless we get a little crazy...", 150, 150, 150, 3);
            aflk = true;
        }
        rd.drawImage(rpro, Utility.centeredImageX(rpro), 240, null);
    }

    @Override
    public void skid(int surface, float speed) {
        if (bfcrash == 0 && bfskid == 0 && speed > 150F) {
            if (surface == 0) {
                if (!mutes) {
                    sm.play("skid" + skflg);
                }
                if (skidup) {
                    skflg++;
                } else {
                    skflg--;
                }
                if (skflg == 3) {
                    skflg = 0;
                }
                if (skflg == -1) {
                    skflg = 2;
                }
            } else {
                if (!mutes) {
                    sm.play("dustskid" + dskflg);
                }
                if (skidup) {
                    dskflg++;
                } else {
                    dskflg--;
                }
                if (dskflg == 3) {
                    dskflg = 0;
                }
                if (dskflg == -1) {
                    dskflg = 2;
                }
            }
            bfskid = 35;
        }
    }

    public void cantreply() {
        rd.setColor(new Color(64, 143, 223));
        rd.fillRoundRect(Utility.centeredWidthX(500), 73, 500, 23, 7, 20);
        rd.setColor(new Color(0, 89, 223));
        rd.drawRoundRect(Utility.centeredWidthX(500), 73, 500, 23, 7, 20);
        drawcs(89, "Sorry not enough replay data to play available, please try again later.", 255, 255, 255, 1);
    }

    public void stopallnow() {
        Sound3D.stopAll();
        int i = 0;
        if (loadedt) {
            strack.unload();
            strack = null;
        }
        loadedt = false;
        i = 0;
        do {
            for (int x = 0; x < 5; x++) {
                sm.stop("engs" + x + i);
            }
        } while (++i < 5);
        i = 0;
        do {
            sm.stop("air" + i);
        } while (++i < 6);
        sm.stop("wasted");
        if (intertrack != null) {
            intertrack.unload();
        }
        intertrack = null;
        // if (intercar != null) {
        //     intercar.unload();
        // }
        // intercar = null;
    }

    /** The menus' music changes to another interface track, "cars" or "stages", as the classic screens' does. */
    void switchIntertrack(String track) {
        if (intertrack != null) {
            intertrack.setPaused(true);
            intertrack.unload();
            intertrack = null;
        }
        loadIntertrack(track);
    }

    /** The menus' music stops: split screen is back at the main menu, which has none. */
    void stopIntertrack() {
        if (intertrack != null) {
            intertrack.setPaused(true);
            intertrack.unload();
        }
        intertrack = new RadicalMod(); // silent, as at the start, for the car select to play until its own loads
    }

    void loadIntertrack(String track) {     //load interface track
        // the first of these that is there, a mod's before the game's
        String found = GameFiles.find("data/music/interface/" + track, ".mp3", ".ogg", ".mid", ".wav", ".zip", ".radq",
                ".zipo");
        try {
            if (found != null) {
                intertrack = isZip(found) ? TrackZipLoader.loadZip(found, false) : TrackZipLoader.loadMusic(found);
            }
        } catch (final IOException e) {
            e.printStackTrace();
        }
    }

    /** Whether a music file is a zip of a module (.radq, .zip or .zipo) rather than a recording. */
    private static boolean isZip(String path) {
        String p = path.toLowerCase(java.util.Locale.ROOT);
        return p.endsWith(".radq") || p.endsWith(".zip") || p.endsWith(".zipo");
    }

    public void inishcarselect() {
        carsbginflex();
        flatrstart = 0;
        Medium.lightson = false;
        /// aaaaaaaa
        (new Thread() {
            public void run() {
                loadIntertrack("cars");
            }
        }).start();
        pnext = 0;
        pback = 0;
    }

    /* Draws the text and bar for a single car selection stat. */
    private void draw_carselect_stat(String text, int bar_x, int y, float blocked_off_part_size) {
        int stringWidth = FontHandler.fMetrics.stringWidth(text);

        /* draw text and gradient bar */
        rd.setColor(new Color(181, 120, 40));
        rd.drawString(text, bar_x - stringWidth - 5, y + 6);
        rd.drawImage(statb, bar_x, y, null);

        /* replace some of the gradient bar with black */
        rd.setColor(new Color(0, 0, 0));
        rd.fillRect((int) ((float) bar_x + 156F * blocked_off_part_size), y,
                (int) (156F * (1.0F - blocked_off_part_size) + 1.0F), statbo.getHeight(null));

        /* draw the overlay */
        rd.drawImage(statbo, bar_x, y, null);
    }

    /**
     * The car select's car {@code model}, where it stands, from the car
     * select's camera, with its shadow on the floor: in the menus' picture,
     * at its own size in the middle of it (Medium.menuView). The verifier
     * checks it at several sizes (RaceSession.Verifier.checkCarSelect).
     */
    static void drawSelectCar(Graphics2D g, ContO model) {
        Medium.crs = true;
        Medium.x = -GameFacts.GAME_WIDTH / 2;
        Medium.y = -(int) (GameFacts.GAME_HEIGHT * 0.83);
        Medium.z = 200;
        Medium.xz = 0;
        Medium.zy = 10;
        Medium.ground = 570;
        try (var _ = Medium.menuView()) {
            model.d(g);
        }
    }

    public void carselect(Control control, ContO models[], Madness car) {
        /* this is a mess... */

        if (flatrstart == 6) {
            rd.drawImage(carsBackdrop(), 0, 0, null);
        } else {
            rd.setColor(new Color(255, 255, 255, 50));
            rd.fillRect(0, 0, GameFacts.screenWidth, GameFacts.screenHeight);
            carsbginflex();
            flatrstart = 6;
        }
        intertrack.play();
        rd.drawImage(selectcar, Utility.centeredImageX(selectcar), 12, null);
        drawSelectCar(rd, models[sc[0]]);

        if (GameSparker.DEBUG) {
            if (debugmode) {
                rd.setColor(new Color(255, 255, 255));
                rd.drawString("sc[" + sc[0] + "]", 33, 50);
            }
        }

        if (flipo == 0) {
            rd.setFont(new Font("SansSerif", 1, 13));
            FontHandler.fMetrics = rd.getFontMetrics();
            byte nameDrop = 0;
            if (flatrstart < 6) {
                nameDrop = 2;
            }
            models[sc[0]].z = 950;
            if (sc[0] == 13) {
                models[sc[0]].z = 1000;
            }
            models[sc[0]].y = -34 - models[sc[0]].grat;
            models[sc[0]].x = 0;
            models[sc[0]].xz += 5;
            models[sc[0]].zy = 0;
            models[sc[0]].wzy -= 10;
            if (models[sc[0]].wzy < -45) {
                models[sc[0]].wzy += 45;
            }

            car_select_delay = 0;

            if (sc[0] != 0) {
                rd.drawImage(back[pback], car_select_back_button_x, car_select_next_back_button_y, null);
            }
            if (sc[0] != GameFacts.numberOfCars - 1) {
                rd.drawImage(next[pnext], car_select_next_button_x, car_select_next_back_button_y, null);
            }
            if ((sc[0] - 7) * 2 >= unlocked) {
                // if (gatey == 300) {
                // int i = 0;
                // do {
                // pgas[i] = false;
                // pgady[i] = 0;
                // } while (++i < 9);
                // pgas[0] = true;
                // }
                // int j = 0;
                // do {
                // rd.drawImage(pgate, pgatx[j], (pgaty[j] + pgady[j]) - gatey, null);
                // if (flatrstart == 6) {
                // if (pgas[j]) {
                // pgady[j] -= ((80 + 100 / (j + 1)) - Math.abs(pgady[j])) / 3;
                // if (pgady[j] < -(70 + 100 / (j + 1))) {
                // pgas[j] = false;
                // if (j != 8) {
                // pgas[j + 1] = true;
                // }
                // }
                // } else {
                // pgady[j] += ((80 + 100 / (j + 1)) - Math.abs(pgady[j])) / 3;
                // if (pgady[j] > 0) {
                // pgady[j] = 0;
                // }
                // }
                // }
                // } while (++j < 9);
                // if (gatey != 0) {
                // gatey -= 100;
                // }
                models[sc[0]].blackout = true;
                if (flatrstart == 6) {
                    drawcs((int) (GameFacts.screenHeight * 0.91), "[ Car Locked ]", 210, 210, 210, 3);
                    drawcs((int) (GameFacts.screenHeight * 0.94),
                            "This car unlocks when stage " + (sc[0] - 7) * 2 + " is completed...", 181, 120, 40, 3);
                }
                if (aflk) {
                    drawcs((int) (GameFacts.screenHeight * 0.1) + nameDrop, "[ LOCKED ]", 240, 240, 240, 3);
                    aflk = false;
                } else {
                    drawcs((int) (GameFacts.screenHeight * 0.1), "[ LOCKED ]", 176, 176, 176, 3);
                    aflk = true;
                }
            } else {
                if (aflk) {
                    drawcs((int) (GameFacts.screenHeight * 0.1) + nameDrop, names[sc[0]], 240, 240, 240, 3);
                    aflk = false;
                } else {
                    drawcs((int) (GameFacts.screenHeight * 0.1), names[sc[0]], 176, 176, 176, 3);
                    aflk = true;
                }

                if (flatrstart == 6) {
                    int leftside_statbar_x = GameFacts.screenWidth / 4 - statb.getWidth(null) / 2;
                    int rightside_statbar_x = (GameFacts.screenWidth / 4) * 3 - statb.getWidth(null) / 2;
                    int statbar_initial_y = (int) (GameFacts.screenHeight * 0.85);
                    int statbar_y_gap = 15;

                    rd.setFont(new Font("SansSerif", 1, 11));
                    FontHandler.fMetrics = rd.getFontMetrics();

                    /*
                     * the `f` variable holds the current stat's actual level, to pass to
                     * draw_carselect_stat
                     */

                    float fill = (StatList.swits[sc[0]][2] - 220) / 90F;
                    if (fill < 0.20000000000000001D) {
                        fill = 0.2F;
                    }
                    draw_carselect_stat("Top Speed:", leftside_statbar_x, statbar_initial_y, fill);

                    fill = (StatList.acelf[sc[0]][1] * StatList.acelf[sc[0]][0] * StatList.acelf[sc[0]][2]
                            * StatList.grip[sc[0]]) / 7700F;
                    if (fill > 1.0F) {
                        fill = 1.0F;
                    }
                    draw_carselect_stat("Acceleration:", leftside_statbar_x, statbar_initial_y + statbar_y_gap, fill);

                    fill = StatList.dishandle[sc[0]];
                    draw_carselect_stat("Handling:", leftside_statbar_x, statbar_initial_y + statbar_y_gap * 2, fill);

                    fill = (StatList.airc[sc[0]] * StatList.airs[sc[0]] * StatList.bounce[sc[0]] + 28F) / 139F;
                    if (fill > 1.0F) {
                        fill = 1.0F;
                    }
                    draw_carselect_stat("Stunts:", rightside_statbar_x, statbar_initial_y, fill);

                    float momentBase = 0.5F;
                    if (sc[0] == 9) {
                        momentBase = 0.8F;
                    }
                    fill = (StatList.moment[sc[0]] + momentBase) / 2.6F;
                    if (fill > 1.0F) {
                        fill = 1.0F;
                    }
                    draw_carselect_stat("Strength:", rightside_statbar_x, statbar_initial_y + statbar_y_gap, fill);

                    fill = StatList.outdam[sc[0]];
                    draw_carselect_stat("Endurance:", rightside_statbar_x, statbar_initial_y + statbar_y_gap * 2, fill);
                }
                rd.drawImage(contin[pcontin], Utility.centeredImageX(contin[pcontin]), car_select_continue_button_y,
                        null);
            }
        } else {
            pback = 0;
            pnext = 0;
            gatey = 300;
            car_select_delay--;
            if (flipo > 10) {
                models[sc[0]].y -= 100;
                if (nextc) {
                    models[sc[0]].zy += 20;
                } else {
                    models[sc[0]].zy -= 20;
                }
            } else {
                if (flipo == 10) {
                    if (nextc) {
                        sc[0]++;
                        if (sc[0] == GameFacts.numberOfCars)
                            sc[0] = 0;
                    } else {
                        sc[0]--;
                        if (sc[0] == -1)
                            sc[0] = GameFacts.numberOfCars - 1;
                    }
                    models[sc[0]].z = 950;
                    models[sc[0]].y = -34 - models[sc[0]].grat - 1100;
                    models[sc[0]].x = 0;
                    models[sc[0]].zy = 0;
                }
                models[sc[0]].y += 100;
                System.out.println(flipo + ", " + models[sc[0]].y);
            }
            flipo--;
        }
        rd.setFont(new Font("SansSerif", 1, 11));
        FontHandler.fMetrics = rd.getFontMetrics();
        drawcs(GameFacts.screenHeight - 5, "You can also use Keyboard Arrows and Enter to navigate.", 82, 90, 0, 3);
        if (control.right && car_select_delay == 0) {
            sm.play("tick");
            control.right = false;
            if (flatrstart > 1) {
                flatrstart = 0;
            }
            nextc = true;
            flipo = 10;
            car_select_delay = 3;
            // sc[0]++;
        }
        if (control.left && car_select_delay == 0) {
            sm.play("tick");
            control.left = false;
            if (flatrstart > 1) {
                flatrstart = 0;
            }
            nextc = false;
            flipo = 10;
            car_select_delay = 3;
            // sc[0]--;
        }
        if (control.handb || control.enter) {
            sm.play("tick");
            if (flipo == 0 && (sc[0] - 7) * 2 < unlocked) {
                lastload = -11;
                intertrack.setPaused(true);
                intertrack.unload();
                loadIntertrack("stages");
                Medium.crs = false;
                fase = Phase.NPLAYERSCHECK;
            }
            control.handb = false;
            control.enter = false;
        }
    }

    public void ctachm(int mouseX, int mouseY, int mouseState, Control control) {
        if (fase == Phase.STAGESELECT) {
            if (mouseState == 1) {
                if (over(next[0], mouseX, mouseY, stage_select_next_button_x, stage_select_next_back_button_y)) {
                    pnext = 1;
                }
                if (over(back[0], mouseX, mouseY, stage_select_back_button_x, stage_select_next_back_button_y)) {
                    pback = 1;
                }
                if (over(contin[0], mouseX, mouseY, Utility.centeredImageX(contin[0]), stage_select_continue_button_y)) {
                    pcontin = 1;
                }
            }
            if (mouseState == 2) {
                if (pnext == 1) {
                    control.right = true;
                }
                if (pback == 1) {
                    control.left = true;
                }
                if (pcontin == 1) {
                    control.enter = true;
                }
            }
        }
        if (fase == Phase.ERRORLOADINGSTAGE) {
            if (mouseState == 1 && over(contin[0], mouseX, mouseY, 290, 325)) {
                pcontin = 1;
            }
            if (mouseState == 2 && pcontin == 1) {
                control.enter = true;
                pcontin = 0;
            }
        }
        if (fase == Phase.LOCKEDSTAGE) {
            if (mouseState == 1 && over(back[0], mouseX, mouseY, stage_select_back_button_x, stage_select_next_back_button_y)) {
                pback = 1;
            }
            if (mouseState == 2 && pback == 1) {
                control.enter = true;
                pback = 0;
            }
        }
        if (fase == Phase.PREGAME) {
            if (mouseState == 1 && (over(star[0], mouseX, mouseY, 294, 360) || over(star[0], mouseX, mouseY, 294, 270))) {
                pstar = 2;
            }
            if (mouseState == 2 && pstar == 2) {
                control.enter = true;
                pstar = 1;
            }
        }
        if (fase == Phase.CARSELECT) {
            if (mouseState == 1) {
                if (over(next[0], mouseX, mouseY, car_select_next_button_x, car_select_next_back_button_y)) {
                    pnext = 1;
                }
                if (over(back[0], mouseX, mouseY, car_select_back_button_x, car_select_next_back_button_y)) {
                    pback = 1;
                }
                if (over(contin[0], mouseX, mouseY, Utility.centeredImageX(contin[0]), car_select_continue_button_y)) {
                    pcontin = 1;
                }
            }
            if (mouseState == 2) {
                if (pnext == 1) {
                    control.right = true;
                }
                if (pback == 1) {
                    control.left = true;
                }
                if (pcontin == 1) {
                    control.enter = true;
                    pcontin = 0;
                }
            }
        }
        if (fase == Phase.POSTGAME) {
            lxm = mouseX;
            lym = mouseY;
            if (mouseState == 1 && over(contin[0], mouseX, mouseY, Utility.centeredImageX(contin[0]), post_game_continue_button_y)) {
                pcontin = 1;
            }
            if (mouseState == 2 && pcontin == 1) {
                control.enter = true;
                pcontin = 0;
            }
        }
        if (fase == Phase.PAUSEMENU) {
            int over = -1;
            for (int i = 0; i < pausebox_op_widths.length; i++) {
                if (overon(Utility.centeredWidthX(pausebox_op_widths[i]) + pausebox_op_x_correction,
                        pausebox_y + pausebox_option_start_off + pausebox_option_gap * i,
                        pausebox_op_widths[i], pausebox_op_height, mouseX, mouseY)) {
                    over = i;
                }
            }
            if (mouseState == 1 && over >= 0) {
                opselect = over;
                shaded = true;
            }
            if (mouseState == 2 && shaded) {
                control.enter = true;
                shaded = false;
            }
            if (mouseState == 0 && (mouseX != lxm || mouseY != lym)) {
                if (over >= 0) {
                    opselect = over;
                }
                lxm = mouseX;
                lym = mouseY;
            }
        }
        if (fase == Phase.SETTINGS && settingsScreen != null) {
            settingsScreen.mouse(mouseX, mouseY, mouseState, control);
        }
        if (fase == Phase.CUSTOMGAME && customGameScreen != null) {
            customGameScreen.mouse(mouseX, mouseY, mouseState, control);
        }
        if (fase == Phase.MAINMENU) {
            int over = -1;
            for (int item = 0; item < MENU_ITEMS; item++) {
                if (overon(Utility.centeredWidthX(MENU_WIDTH[item]), menuY[item], MENU_WIDTH[item],
                        main_menu_button_height, mouseX, mouseY)) {
                    over = item;
                }
            }
            if (mouseState == 1 && over >= 0) {
                opselect = over;
                shaded = true;
            }
            if (mouseState == 2 && shaded) {
                control.enter = true;
                shaded = false;
            }
            if (mouseState == 0 && (mouseX != lxm || mouseY != lym)) {
                if (over >= 0) {
                    opselect = over;
                }
                lxm = mouseX;
                lym = mouseY;
            }
        }
        if (fase == Phase.INSTRUCTIONS) {
            if (flipo >= 1 && flipo <= 13) {
                if (mouseState == 1 && over(next[0], mouseX, mouseY, instructions_next_button_x, instructions_next_back_button_y)) {
                    pnext = 1;
                }
                if (mouseState == 2 && pnext == 1) {
                    control.right = true;
                    pnext = 0;
                }
            }
            if (flipo >= 3 && flipo <= 15) {
                if (mouseState == 1 && over(back[0], mouseX, mouseY, instructions_back_button_x, instructions_next_back_button_y)) {
                    pback = 1;
                }
                if (mouseState == 2 && pback == 1) {
                    control.left = true;
                    pback = 0;
                }
            }
            if (flipo == 15) {
                if (mouseState == 1 && over(contin[0], mouseX, mouseY, instructions_continue_button_x, instructions_next_back_button_y)) {
                    pcontin = 1;
                }
                if (mouseState == 2 && pcontin == 1) {
                    control.enter = true;
                    pcontin = 0;
                }
            }
        }
        if (fase == Phase.CREDITS) {

            if (mouseState == 1 && over(next[0], mouseX, mouseY, Utility.centeredImageX(next[0]), credits_next_button_y)) {
                pnext = 1;
            }

            if (mouseState == 2 && pnext == 1) {
                control.enter = true;
                pnext = 0;
            }
        }
    }

    private void stopairs() {
        int i = 0;
        do {
            sm.stop("air" + i);
        } while (++i < 6);
    }

    @Override
    public void run() {
        while (runtyp != 0) {
            if (runtyp >= 1 && runtyp <= 17) {
                hipnoload(runtyp, false);
            }
            if (runtyp == 176) {
                loading();
            }
            app.repaint();
            try {
                Thread.sleep(20L);
            } catch (InterruptedException ignored) {
            }
        }
    }

    public void loadingfailed(CheckPoints checkpoints, Control control, String error) {
        trackbg(false);
        rd.drawImage(select, Utility.centeredImageX(select), stage_select_text_y, null);
        if (checkpoints.stage != 1) {
            rd.drawImage(back[pback], stage_select_back_button_x, stage_select_next_back_button_y, null);
        }
        if (checkpoints.stage != GameFacts.numberOfStages) {
            rd.drawImage(next[pnext], stage_select_next_button_x, stage_select_next_back_button_y, null);
        }
        rd.setFont(new Font("SansSerif", 1, 13));
        FontHandler.fMetrics = rd.getFontMetrics();
        drawcs(140, "Error Loading Stage " + checkpoints.stage, 200, 0, 0, 3);
        drawcs(170, error, 177, 177, 177, 3);
        drawcs(220, "Check console for more info.", 177, 177, 177, 3);
        rd.drawImage(contin[pcontin], Utility.centeredImageX(contin[pcontin]), stage_select_continue_button_y, null);
        rd.setFont(new Font("SansSerif", 1, 11));
        FontHandler.fMetrics = rd.getFontMetrics();
        drawcs(GameFacts.screenHeight - 5, "You can also use Keyboard Arrows and Enter to navigate.", 82, 90, 0, 3);
        if (control.handb || control.enter) {
            fase = Phase.NPLAYERSCHECK;
            control.handb = false;
            control.enter = false;
        }
        if (control.right && checkpoints.stage < 17) {
            if (checkpoints.stage != unlocked) {
                checkpoints.stage++;
                fase = Phase.NPLAYERSCHECK;
                control.right = false;
            } else {
                fase = Phase.LOCKEDSTAGE;
                lockcnt = 100;
                control.right = false;
            }
        }
        if (control.left && checkpoints.stage > 1) {
            checkpoints.stage--;
            fase = Phase.NPLAYERSCHECK;
            control.left = false;
        }
    }

    public void hipnoload(int stage, boolean loaded) {
        int red = (int) (230F - 230F * (Medium.snap[0] / (100F * hipno)));
        if (red > 255) {
            red = 255;
        }
        if (red < 0) {
            red = 0;
        }
        int green = (int) (230F - 230F * (Medium.snap[1] / (100F * hipno)));
        if (green > 255) {
            green = 255;
        }
        if (green < 0) {
            green = 0;
        }
        int blue = (int) (230F - 230F * (Medium.snap[2] / (100F * hipno)));
        if (blue > 255) {
            blue = 255;
        }
        if (blue < 0) {
            blue = 0;
        }
        if (stage == 1) {
            red = 230;
            green = 230;
            blue = 230;
        }
        rd.setColor(new Color(red, green, blue));
        rd.fillRect(0, 0, GameFacts.screenWidth, GameFacts.screenHeight);
        rd.setFont(new Font("SansSerif", 1, 13));
        FontHandler.fMetrics = rd.getFontMetrics();
        drawcs(25, asay, 0, 0, 0, 3);

        /* this is such a stupid way to accommodate for the stages with hints... */
        byte textDrop = -90;
        if (stage == 1 || stage == 2 || stage == 3 || stage == 4 || stage == 7 || stage == 8 || stage == 9 || stage == 10 || stage == 12
                || stage == 13 || stage == 16) {
            textDrop = 0;
        }
        if (textDrop == 0) {
            if (dudo > 0) {
                if (aflk) {
                    if (Math.random() > Math.random()) {
                        duds = (int) (Math.random() * 3D);
                    } else {
                        duds = (int) (Math.random() * 2D);
                    }
                    aflk = false;
                } else {
                    aflk = true;
                }
                dudo--;
            } else {
                duds = 0;
            }
            int hint_all_width = dude[duds].getWidth(null) + flaot.getWidth(null);
            int start_x = Utility.centeredWidthX(hint_all_width);
            int flaot_x = start_x + dude[duds].getWidth(null);
            int text_x = flaot_x + 70;
            rd.drawImage(dude[duds], start_x, 10, null);
            rd.drawImage(flaot, flaot_x, 42, null);
            int hintRed = (int) (80F - 80F * (Medium.snap[0] / (50F * hipno)));
            if (hintRed > 255) {
                hintRed = 255;
            }
            if (hintRed < 0) {
                hintRed = 0;
            }
            int hintGreen = (int) (80F - 80F * (Medium.snap[1] / (50F * hipno)));
            if (hintGreen > 255) {
                hintGreen = 255;
            }
            if (hintGreen < 0) {
                hintGreen = 0;
            }
            int hintBlue = (int) (80F - 80F * (Medium.snap[2] / (50F * hipno)));
            if (hintBlue > 255) {
                hintBlue = 255;
            }
            if (hintBlue < 0) {
                hintBlue = 0;
            }
            if (stage == 1) {
                hintRed = 80;
                hintGreen = 80;
                hintBlue = 80;
            }
            rd.setColor(new Color(hintRed, hintGreen, hintBlue));
            rd.setFont(new Font("SansSerif", 1, 13));
            if (stage == 1) {
                rd.drawString("Hey!  Don't forget, to complete a lap you must pass through", text_x, 67);
                rd.drawString("all checkpoints in the track!", text_x, 87);
            }
            if (stage == 2) {
                rd.drawString("Remember, the more power you have the faster your car will be!", text_x, 67);
            }
            if (stage == 3) {
                rd.drawString("Watch out!  Look out!  The policeman might be out to get you!", text_x, 67);
                rd.drawString("Don't upset him or you'll be arrested!", text_x, 87);
                rd.drawString("Better run, run, run.", text_x, 127);
            }
            if (stage == 4) {
                rd.drawString("Don't waste your time.  Waste them instead!", text_x, 67);
                rd.drawString("Try a taste of sweet revenge here (if you can)!", text_x, 87);
                rd.drawString("Press [ A ] to make the guidance arrow point to cars instead of to", text_x, 127);
                rd.drawString("the track.", text_x, 147);
            }
            if (stage == 7) {
                rd.drawString("Welcome to the realm of the king...", text_x, 67);
                rd.drawString("The key word here is 'POWER'.  The more you have of it the faster", text_x, 107);
                rd.drawString("and STRONGER you car will be!", text_x, 127);
            }
            if (stage == 8) {
                rd.drawString("Watch out, EL KING is out to get you now!", text_x, 67);
                rd.drawString("He seems to be seeking revenge?", text_x, 87);
                rd.drawString("(To fly longer distances in the air try drifting your car on the ramp", text_x, 127);
                rd.drawString("before take off).", text_x, 147);
            }
            if (stage == 9) {
                rd.drawString("It\u2019s good to be the king!", text_x, 67);
            }
            if (stage == 10) {
                rd.drawString("Remember, forward loops give your car a push forwards in the air", text_x, 67);
                rd.drawString("and help in racing.", text_x, 87);
                rd.drawString("(You may need to do more forward loops here.  Also try keeping", text_x, 127);
                rd.drawString("your power at maximum at all times.  Try not to miss a ramp).", text_x, 147);
            }
            if (stage == 12) {
                rd.drawString("Watch out!  Beware!  Take care!", text_x, 67);
                rd.drawString("MASHEEN is hiding out there some where, don't get mashed now!", text_x, 87);
            }
            if (stage == 13) {
                rd.drawString("Anyone for a game of Digger?!", text_x, 67);
                rd.drawString("You can have fun using MASHEEN here!", text_x, 87);
            }
            if (stage == 16) {
                rd.drawString("This is it!  This is the toughest stage in the game!", text_x, 67);
                rd.drawString("This track is actually a 4D object projected onto the 3D world.", text_x, 107);
                rd.drawString("It's been broken down, separated and, in many ways, it is also a", text_x, 127);
                rd.drawString("maze!  GOOD LUCK!", text_x, 147);
            }
        }
        rd.drawImage(loadingmusic, Utility.centeredImageX(loadingmusic), 180 + textDrop, null);
        rd.setFont(new Font("SansSerif", 1, 11));
        FontHandler.fMetrics = rd.getFontMetrics();
        if (!loaded) {
            // drawcs(315 + textDrop, "" + sndsize[stage - 1] + " KB", 0, 0, 0, 3);
            drawcs(350 + textDrop, " Please Wait...", 0, 0, 0, 3);
        } else {
            drawcs(340 + textDrop, "Loading complete!  Press Start to begin...", 0, 0, 0, 3);
            rd.drawImage(star[pstar], Utility.centeredImageX(star[pstar]), 360 + textDrop, null);
            if (pstar != 2) {
                if (pstar == 0) {
                    pstar = 1;
                } else {
                    pstar = 0;
                }
            }
        }
    }

    private Image loadopsnap(Image image, int stage, int keyPixel) {
        int height = image.getHeight(ob);
        int width = image.getWidth(ob);
        int pixels[] = new int[width * height];
        PixelGrabber grabber = new PixelGrabber(image, 0, 0, width, height, pixels, 0, width);
        try {
            grabber.grabPixels();
        } catch (InterruptedException ignored) {
        }
        int highlight = 0;
        if (keyPixel == 1) {
            highlight = pixels[61993];
        }
        for (int pixel = 0; pixel < width * height; pixel++) {
            if (pixels[pixel] != pixels[keyPixel]) {
                Color color = new Color(pixels[pixel]);
                int red = 0;
                int green = 0;
                int blue = 0;
                if (keyPixel == 1 && pixels[pixel] == highlight) {
                    red = (int) (237F - 237F * (Medium.snap[0] / (150F * hipno)));
                    if (red > 255) {
                        red = 255;
                    }
                    if (red < 0) {
                        red = 0;
                    }
                    green = (int) (237F - 237F * (Medium.snap[1] / (150F * hipno)));
                    if (green > 255) {
                        green = 255;
                    }
                    if (green < 0) {
                        green = 0;
                    }
                    blue = (int) (237F - 237F * (Medium.snap[2] / (150F * hipno)));
                    if (blue > 255) {
                        blue = 255;
                    }
                    if (blue < 0) {
                        blue = 0;
                    }
                    if (stage == 1) {
                        red = 250;
                        green = 250;
                        blue = 250;
                    }
                } else {
                    red = (int) (color.getRed()
                            - color.getRed() * (Medium.snap[0] / (50F * hipno)));
                    if (red > 255) {
                        red = 255;
                    }
                    if (red < 0) {
                        red = 0;
                    }
                    green = (int) (color.getGreen()
                            - color.getGreen() * (Medium.snap[1] / (50F * hipno)));
                    if (green > 255) {
                        green = 255;
                    }
                    if (green < 0) {
                        green = 0;
                    }
                    blue = (int) (color.getBlue()
                            - color.getBlue() * (Medium.snap[2] / (50F * hipno)));
                    if (blue > 255) {
                        blue = 255;
                    }
                    if (blue < 0) {
                        blue = 0;
                    }
                    if (stage == 1) {
                        red = color.getRed();
                        green = color.getGreen();
                        blue = color.getBlue();
                    }
                }
                Color tinted = new Color(red, green, blue);
                pixels[pixel] = tinted.getRGB();
            }
        }

        return createImage(new MemoryImageSource(width, height, pixels, 0, width));
    }

    private void carsbginflex() {
        flatr = 0;
        flyr = (int) (Medium.fxRandom() * 160F - 80F);
        flyrdest = (int) ((flyr + Medium.fxRandom() * 160F) - 80F);
        flang = 1;
        flangados = (int) (Medium.fxRandom() * 6F + 2.0F);
        blackn = 0.0F;
        blacknados = Medium.fxRandom() * 0.4F;
        grabScreen(carsBackdrop());
    }
}
