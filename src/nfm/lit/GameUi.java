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
    private final int[] flexpix;
    private final int[] smokey;
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
    private Image selectcar;
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
    private final Image[] next;
    private final Image[] back;
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
    private final int[] bgmy = {
            0, GameFacts.screenHeight
    };
    private final int[] trkx = {
            0, GameFacts.screenWidth
    };
    private int trkl;
    private int trklim;
    private final float hipno = 1.0F;
    private int flkat;
    private int movly;

    private int press_enter_to_continue_height = GameFacts.screenHeight - 100;

    /* TODO: define each page's constants in their own files */
    /* main menu */
    private int main_menu_height_origin = (int) (GameFacts.screenHeight * 0.2);

    private int main_menu_button_height = 22;
    private int main_menu_op_0_y = 246 + main_menu_height_origin;
    private int main_menu_op_0_width = 110;
    private int main_menu_op_1_y = 275 + main_menu_height_origin;
    private int main_menu_op_1_width = 196;
    private int main_menu_op_2_y = 306 + main_menu_height_origin;
    private int main_menu_op_2_width = 85;
    /* Settings, lettered under the menu's picture */
    private int main_menu_op_3_y = 337 + main_menu_height_origin;
    private int main_menu_op_3_width = 100;

    /** The settings screen, made when first opened (the menu font is registered by then). */
    private SettingsScreen settingsScreen;

    private int insano_mainmenu_x;
    private int insano_mainmenu_y;
    /* these exist to reset the head to origin */
    private int insano_mainmenu_x_original;
    private int insano_mainmenu_y_original;

    private int insano_mainmenu_target_x;
    private int insano_mainmenu_target_y;

    /* car select */
    private int car_select_next_back_button_y = (int) (GameFacts.screenHeight * 0.68);
    private int car_select_back_button_x = 30;
    private int car_select_next_button_x = GameFacts.screenWidth - 90;
    private int car_select_continue_button_y = GameFacts.screenHeight - 120;

    /* stage select */
    private int stage_select_next_back_button_y = (int) (GameFacts.screenHeight * 0.5);;
    private int stage_select_back_button_x = car_select_back_button_x;
    private int stage_select_next_button_x = car_select_next_button_x;
    private int stage_select_continue_button_y = GameFacts.screenHeight - 80;
    private int stage_select_text_y = 25;

    /* post-game continue */
    private int post_game_continue_button_y = stage_select_continue_button_y;

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
    private int credits_next_button_y = GameFacts.screenHeight - 100;

    /* instructions */
    private int instructions_next_back_button_y = stage_select_next_back_button_y;
    private int instructions_next_button_x = stage_select_next_button_x;
    private int instructions_back_button_x = stage_select_back_button_x;
    private int instructions_continue_button_x = instructions_next_button_x - 5;

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

            if (!nplayers_debug) {
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
        if (settingsScreen == null) {
            settingsScreen = new SettingsScreen(back);
        }
        settingsScreen.backTo = from;
        settingsScreen.open();
        fase = Phase.SETTINGS;
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
        } else {
            int i = 0;
            do {
                rd.drawImage(bgmain, 0, bgmy[i], null);
                bgmy[i] -= 20;
                if (bgmy[i] <= -GameFacts.screenHeight) {
                    bgmy[i] = GameFacts.screenHeight;
                }
            } while (++i < 2);
        }
        if (screen.frame(rd, control)) {
            fase = screen.backTo;
            opselect = screen.backTo == Phase.PAUSEMENU ? PAUSE_SETTINGS : 3;
        }
    }

    public void inst(Control control) {
        if (flipo == 0) {
            flipo = 1;
            bgmy[0] = 0;
            bgmy[1] = GameFacts.screenHeight;
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
        int i = 0;
        do {
            rd.drawImage(bgmain, 0, bgmy[i], null);
            bgmy[i] -= 2;
            if (bgmy[i] <= -GameFacts.screenHeight) {
                bgmy[i] = GameFacts.screenHeight;
            }
        } while (++i < 2);
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
            PixelGrabber grabber = new PixelGrabber(image, 0, 0, GameFacts.screenWidth, GameFacts.screenHeight,
                    flexpix, 0, GameFacts.screenWidth);
            try {
                grabber.grabPixels();
            } catch (InterruptedException ignored) {
            }
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
        } while (++pixel < 0xE1000);
        fleximg = createImage(new MemoryImageSource(GameFacts.screenWidth, GameFacts.screenHeight, flexpix, 0,
                GameFacts.screenWidth));
        rd.drawImage(fleximg, 0, 0, null);
    }

    private void arrow(int checkpoint, int missed, CheckPoints checkpoints, ContO cars[], boolean toRacers) {
        int xs[] = new int[7];
        int ys[] = new int[7];
        int zs[] = new int[7];
        /**
         * Base X position
         */
        int centerX = GameFacts.screenWidth / 2;
        byte arrowY = -90;
        /**
         * Base Y position
         */
        int centerZ = GameFacts.screenHeight; // 2BC
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
        int nearest = 0;
        if (!toRacers) {
            char flip = '\0';
            if (checkpoints.x[checkpoint] - checkpoints.opx[0] >= 0) {
                flip = '\264';
            }
            angle = (int) (90 + flip + StrictMath.atan(
                    (double) (checkpoints.z[checkpoint] - checkpoints.opz[0]) / (double) (checkpoints.x[checkpoint] - checkpoints.opx[0]))
                    / 0.017453292519943295D);
        } else {
            int nearestDistance = -1;
            boolean seenOnScreen = false;
            int other = 1;
            do {
                if ((Utility.py(checkpoints.opx[0] / 100, checkpoints.opx[other] / 100, checkpoints.opz[0] / 100,
                        checkpoints.opz[other] / 100) < nearestDistance || nearestDistance == -1) && (!seenOnScreen || checkpoints.onscreen[other] != 0)
                        && checkpoints.dested[other] == 0) {
                    nearest = other;
                    nearestDistance = Utility.py(checkpoints.opx[0] / 100, checkpoints.opx[other] / 100, checkpoints.opz[0] / 100,
                            checkpoints.opz[other] / 100);
                    if (checkpoints.onscreen[other] != 0) {
                        seenOnScreen = true;
                    }
                }
            } while (++other < GameFacts.numberOfPlayers);
            other = 0;
            if (checkpoints.opx[nearest] - checkpoints.opx[0] >= 0) {
                other = 180;
            }
            angle = (int) (90 + other + StrictMath.atan((double) (checkpoints.opz[nearest] - checkpoints.opz[0])
                    / (double) (checkpoints.opx[nearest] - checkpoints.opx[0])) / 0.017453292519943295D);
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
                    xs[red] = Utility.cXs(xs[red], zs[red]);
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
                xs[red] = Utility.cXs(xs[red], zs[red]);
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

    @Override
    public void crash(float force, int kind) {
        if (bfcrash == 0) {
            if (Math.abs(force) > 25F && !SoundManager.isDiscarding()) {
                Gamepads.hit(force);
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

    private void smokeypix(byte data[], MediaTracker tracker, Toolkit toolkit) {
        Image image = toolkit.createImage(data);
        tracker.addImage(image, 0);
        try {
            tracker.waitForID(0);
        } catch (Exception ignored) {
        }
        PixelGrabber grabber = new PixelGrabber(image, 0, 0, 466, 202, smokey, 0, 466);
        try {
            grabber.grabPixels();
        } catch (InterruptedException ignored) {
        }
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

    public void pauseimage(Image image) {
        PixelGrabber grabber = new PixelGrabber(image, 0, 0, GameFacts.screenWidth, GameFacts.screenHeight,
                flexpix, 0, GameFacts.screenWidth);
        try {
            grabber.grabPixels();
        } catch (InterruptedException ignored) {
        }
        int lastGray = 0;
        int column = 0;
        int px_index = 0;
        do {
            Color color = new Color(flexpix[px_index]);
            int gray = 0;
            if (column == 0) {
                gray = (color.getRed() + color.getGreen() + color.getBlue()) / 3;
                lastGray = gray;
            } else {
                gray = (color.getRed() + color.getGreen() + color.getBlue() + lastGray * 30) / 33;
                lastGray = gray;
            }
            if (++column == 900) {
                column = 0;
            }
            Color grayColor = new Color(gray, gray, gray);
            flexpix[px_index] = grayColor.getRGB();
        } while (++px_index < GameFacts.screenHeight * GameFacts.screenWidth);
        fleximg = createImage(new MemoryImageSource(GameFacts.screenWidth, GameFacts.screenHeight, flexpix, 0,
                GameFacts.screenWidth));
        rd.drawImage(fleximg, 0, 0, null);
        Medium.flex = 0;
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
                if ("smokey.gif".equals(name)) {
                    smokeypix(data, tracker, toolkit);
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
                    pausebox_y = Utility.centeredImageY(paused) / 4;
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
                    /* setup initial position since it's dynamic */
                    insano_mainmenu_x = ((GameFacts.screenWidth / 2) - dude[0].getWidth(null) / 2) + 10;
                    insano_mainmenu_x_original = insano_mainmenu_x;
                    insano_mainmenu_y = 2 + main_menu_height_origin;
                    insano_mainmenu_y_original = insano_mainmenu_y;
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
            bgmy[0] = 0;
            bgmy[1] = GameFacts.screenHeight;
        }
        if (flipo >= 1 && flipo <= 100) {
            rad(flipo);
            flipo++;
            if (flipo == 100) {
                flipo = 1;
            }
        }
        if (flipo == 101) {
            int i = 0;
            do {
                rd.drawImage(bgmain, 0, bgmy[i], null);
                bgmy[i] -= 10;
                if (bgmy[i] <= -GameFacts.screenHeight) {
                    bgmy[i] = GameFacts.screenHeight;
                }
            } while (++i < 2);
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
            int i = 0;
            do {
                rd.drawImage(bgmain, 0, bgmy[i], null);
                bgmy[i] -= 10;
                if (bgmy[i] <= -GameFacts.screenHeight) {
                    bgmy[i] = GameFacts.screenHeight;
                }
            } while (++i < 2);
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
            int j = 0;
            do {
                rd.drawImage(bgmain, 0, bgmy[j], null);
                bgmy[j] -= 16;
                if (bgmy[j] <= -GameFacts.screenHeight) {
                    bgmy[j] = GameFacts.screenHeight;
                }
            } while (++j < 2);
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
                        for (int array_one = 0; array_one < num_cars; array_one++) {
                            boolean flag_status = false;
                            for (int array_two = 0; array_two < num_cars; array_two++) {
                                if (checkpoints.pos[array_two] == array_one && checkpoints.dested[array_two] == 0
                                        && !flag_status) {
                                    int y_value = 30; // use to move status up or down
                                    int x_value = GameFacts.screenWidth - 700; // use to move status left or right

                                    rd.setColor(new Color(0, 0, 100));
                                    if (array_one == 0)
                                        rd.drawString("1st", 543 + x_value, 76 + 30 + y_value * array_one);
                                    if (array_one == 1)
                                        rd.drawString("2nd", 541 + x_value, 76 + 30 + y_value * array_one);
                                    if (array_one == 2)
                                        rd.drawString("3rd", 541 + x_value, 76 + 30 + y_value * array_one);
                                    if (array_one >= 3)
                                        rd.drawString((array_one + 1) + "th", 541 + x_value, 76 + y_value + 30 * array_one);
                                    rd.setColor(new Color(0, 0, 0));
                                    rd.drawString(names[sc[array_two]],
                                            600 - ((FontHandler.fMetrics.stringWidth(names[sc[array_two]])) / 2) + x_value,
                                            70 + y_value + 30 * array_one);
                                    if (drivers[0].im == array_two) {
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
                                        rd.drawRect(531 + x_value, 58 + y_value + 30 * array_one, 114, 25);
                                        rd.drawRect(532 + x_value, 59 + y_value + 30 * array_one, 112, 23);
                                    }
                                    if (arrace) {
                                        int dmg = (int) (60F * ((float) drivers[array_two].hitmag
                                                / (float) drivers[array_two].stat.maxmag));

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
                                        rd.fillRect(565 + x_value, 75 + y_value + 30 * array_one, dmg, 5);
                                    } else {
                                        int pwr = (int) (60F * (drivers[array_two].power / 98F));
                                        if (pwr > 98) {
                                            pwr = 98;
                                        }
                                        rd.setColor(new Color(45, 150, 255));
                                        rd.fillRect(565 + x_value, 75 + y_value + 30 * array_one, pwr, 5);
                                    }
                                    rd.setColor(new Color(0, 0, 0));
                                    rd.drawRect(565 + x_value, 75 + y_value + 30 * array_one, 60, 5);

                                    flag_status = true;
                                }
                            }
                        }
                    }
                    rd.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_OFF);
                    if (!arrace && auscnt == 45 && drivers[0].capcnt == 0) {
                        if (drivers[0].missedcp > 0) {
                            if (drivers[0].missedcp > 15 && drivers[0].missedcp < 50) {
                                if (flk) {
                                    drawcs(70, "Checkpoint Missed!", 255, 0, 0, 0);
                                } else {
                                    drawcs(70, "Checkpoint Missed!", 255, 150, 0, 2);
                                }
                            }
                            drivers[0].missedcp++;
                            if (drivers[0].missedcp == 70) {
                                drivers[0].missedcp = -2;
                            }
                        } else if (drivers[0].mtouch && cntovn < 70) {
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
                    rd.drawString("" + checkpoints.wasted + " / " + (GameFacts.numberOfPlayers - 1), 150, 18);
                    rd.drawImage(pos, 42, 27, null);
                    rd.setFont(new Font("SansSerif", 1, 15));
                    FontHandler.fMetrics = rd.getFontMetrics();
                    String suffix = "";
                    int position = checkpoints.pos[0] + 1;
                    if ((position - 1) % 10 == 0 && position != 11) {
                        suffix = "st";
                    }
                    if ((position - 2) % 10 == 0 && position != 12) {
                        suffix = "nd";
                    }
                    if ((position - 3) % 10 == 0 && position != 13) {
                        suffix = "rd";
                    }
                    if (position % 10 == 0 || position % 10 >= 4 || position == 11 || position == 12
                            || position == 13) {
                        suffix = "th";
                    }
                    rd.drawString("" + position + suffix + "", 110, 43);
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
                    if (starcnt == 35 && !mutes) {
                        sm.play("three");
                    }
                    if (starcnt == 24) {
                        gocnt = 2;
                        if (!mutes) {
                            sm.play("two");
                        }
                    }
                    if (starcnt == 13) {
                        gocnt = 1;
                        if (!mutes) {
                            sm.play("one");
                        }
                    }
                    if (starcnt == 2) {
                        gocnt = 0;
                        if (!mutes) {
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
                    if (dudo != -1) {
                        rd.drawImage(dudeb[duds], Utility.centeredImageX(dudeb[duds]) - 50, 0, null);
                    }
                    if (gocnt != 0) {
                        rd.drawImage(cntdn[gocnt], Utility.centeredImageX(dudeb[duds]) + 50, 50, null);
                    } else {
                        rd.drawImage(cntdn[gocnt], Utility.centeredImageX(dudeb[duds]) + 60, 50, null);
                    }
                }
                if (looped != 0 && drivers[0].loop == 2) {
                    looped = 0;
                }
                if (drivers[0].power < 45F) {
                    if (tcnt == 30 && auscnt == 45 && drivers[0].mtouch && drivers[0].capcnt == 0) {
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
                if (drivers[0].capcnt == 0) {
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
                if (drivers[0].trcnt == 10) {
                    loop = "";
                    spin = "";
                    asay = "";
                    int turns;
                    for (turns = 0; drivers[0].travzy > 225; turns++) {
                        drivers[0].travzy -= 360;
                    }

                    while (drivers[0].travzy < -225) {
                        drivers[0].travzy += 360;
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
                        if (drivers[0].ftab && drivers[0].btab) {
                            loop = "Tabletop and reversed Tabletop";
                        } else if (drivers[0].ftab || drivers[0].btab) {
                            loop = "Tabletop";
                        }
                    }
                    if (turns > 0 && drivers[0].btab) {
                        loop = "Hanged " + loop;
                    }
                    if (turns < 0 && drivers[0].ftab) {
                        loop = "Hanged " + loop;
                    }
                    if (!("").equals(loop)) {
                        asay += " " + loop;
                    }
                    turns = 0;
                    for (drivers[0].travxy = Math.abs(drivers[0].travxy); drivers[0].travxy > 270;) {
                        drivers[0].travxy -= 360;
                        turns++;
                    }

                    if (turns == 0 && drivers[0].rtab) {
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
                    for (drivers[0].travxz = Math.abs(drivers[0].travxz); drivers[0].travxz > 90;) {
                        drivers[0].travxz -= 180;
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
                        if (drivers[0].powerup > 20F) {
                            praise = 1;
                        }
                        if (drivers[0].powerup > 40F) {
                            praise = 2;
                        }
                        if (drivers[0].powerup > 150F) {
                            praise = 3;
                        }
                        if (drivers[0].surfer) {
                            asay = " " + adj[4][(int) (Medium.fxRandom() * 3F)] + asay;
                        }
                        if (praise != 3) {
                            asay = adj[praise][(int) (Medium.fxRandom() * 3F)] + asay + exlm[praise];
                        } else {
                            asay = adj[praise][(int) (Medium.fxRandom() * 3F)];
                        }
                        if (!wasay) {
                            tcnt = auscnt;
                            if (drivers[0].power != 98F) {
                                say = "Power Up " + (int) ((100F * drivers[0].powerup) / 98F) + "%";
                            } else {
                                say = "Power To The MAX";
                            }
                            skidup = !skidup;
                        }
                    }
                }
                if (drivers[0].newcar) {
                    if (!wasay) {
                        say = "Car Fixed";
                        tcnt = 0;
                    }
                    crashup = !crashup;
                }
                if (clear != drivers[0].clear && drivers[0].clear != 0) {
                    if (!wasay) {
                        say = "Checkpoint!";
                        tcnt = 15;
                    }
                    clear = drivers[0].clear;
                    if (!mutes) {
                        sm.play("checkpoint");
                    }
                    cntovn = 0;
                    if (cntan != 0) {
                        cntan = 0;
                    }
                }
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
                        }
                    }
                } while (++other < GameFacts.numberOfPlayers);
            }
        }
        if (Medium.lightn != -1 && checkpoints.stage == 16) {
            // supposed to pulse during sections of the music, w/e
            Medium.lton = true;
        }
    }

    public void finish(CheckPoints checkpoints, ContO models[], Control control) {
        rd.drawImage(fleximg, 0, 0, null);
        if (winner) {
            if (checkpoints.stage == unlocked) {
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
                        Medium.x = -(GameFacts.screenWidth / 2);
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
                        models[unlockedCar].d(rd);
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
            if (checkpoints.stage == unlocked && winner && unlocked != GameFacts.numberOfStages) {
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
    private void sortcars(int stage) {
        if (stage != 0) {
            int lastcar = GameFacts.numberOfPlayers;

            if (GameFacts.numberOfPlayers == 1) {
                return; // if there's only 1 car (the player), don't sort anything
            }

            int maxId = GameFacts.numberOfPlayers + (stage + 1) / 2;
            if (maxId > GameFacts.numberOfCars - 1) {
                maxId = GameFacts.numberOfCars - 1;
            }

            if (sc[0] != maxId) {
                if (stage != -1) {
                    sc[GameFacts.numberOfPlayers - 1] = maxId;
                    lastcar--; // boss car won't be randomized if stage isnt custom
                }
            }

            // DEBUG: Prints the range of possible cars to the console
            // HLogger.info("Minimum car: " + cd.names[(i - 1) / 2] + ", maximum car: " +
            // cd.names[nplayers + ((i - 1) / 2)] + ", therefore: " + (((i - 1) / 2) -
            // (nplayers + ((i - 1) / 2))) + " car difference");

            if (stage == -1 || unlocked == GameFacts.numberOfStages) {
                stage = GameFacts.numberOfStages;
                // double boss cars can appear if game is unlocked and player is playing on regular stages cba to account for this rn
            }

            // create a list of car ids, each item completely unique
            ArrayList<Integer> list = new ArrayList<>();
            for (int k = stage / 2; k < GameFacts.numberOfPlayers + (stage / 2); k++) {
                if (k == sc[0])
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

            for (int j = 1; j < lastcar; j++) {

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
        int j = 0;
        do {
            rd.drawImage(trackbg[frame][j], trkx[j], 0, null);
            trkx[j]--;
            if (trkx[j] <= -GameFacts.screenWidth) {
                trkx[j] = GameFacts.screenWidth;
            }
        } while (++j < 2);
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
        smokey = new int[0x16fb4];
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
    }

    public void maini(Control control, CheckPoints checkpoints, Madness drivers[], ContO models[], ContO scene[]) {
        // might redo the entire menu its horrible

        int menuItems = 4;

        if (GameSparker.DEBUG) {
            if (!devtriggered) {
                HLogger.info("Developer Console triggered");

                DevTool console = new DevTool(checkpoints, drivers, models, scene, this);
                console.showConsole();
                devtriggered = true;
            }
        }

        if (flipo == 0) {
            bgmy[0] = 0;
            bgmy[1] = GameFacts.screenHeight;
            app.setCursor(new Cursor(0));
        }

        /* handle the moving background in main menu */
        int i = 0;
        do {
            rd.drawImage(bgmain, 0, bgmy[i], null);
            bgmy[i] -= 20;
            if (bgmy[i] <= -GameFacts.screenHeight) {
                bgmy[i] = GameFacts.screenHeight;
            }
        } while (++i < 2);

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

        rd.drawImage(opback, Utility.centeredImageX(opback), 212 + main_menu_height_origin, null);
        rd.drawImage(nfmcoms, Utility.centeredImageX(nfmcoms), 195 + main_menu_height_origin, null);
        rd.drawImage(byrd, Utility.centeredImageX(byrd), 383 + main_menu_height_origin, null);

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
        if (opselect == 0) {
            if (shaded) {
                rd.setColor(new Color(140, 70, 0));
                rd.fillRect(Utility.centeredWidthX(main_menu_op_0_width), main_menu_op_0_y, main_menu_op_0_width,
                        main_menu_button_height);
                aflk = false;
            }
            if (aflk) {
                rd.setColor(new Color(200, 255, 0));
                aflk = false;
            } else {
                rd.setColor(new Color(255, 128, 0));
                aflk = true;
            }
            rd.drawRoundRect(Utility.centeredWidthX(main_menu_op_0_width), main_menu_op_0_y, main_menu_op_0_width,
                    main_menu_button_height, 7, 20);
        } else {
            rd.setColor(new Color(0, 0, 0));
            rd.drawRoundRect(Utility.centeredWidthX(main_menu_op_0_width), main_menu_op_0_y, main_menu_op_0_width,
                    main_menu_button_height, 7, 20);
        }
        if (opselect == 1) {
            if (shaded) {
                rd.setColor(new Color(140, 70, 0));
                rd.fillRect(Utility.centeredWidthX(main_menu_op_1_width), main_menu_op_1_y, main_menu_op_1_width,
                        main_menu_button_height);
                aflk = false;
            }
            if (aflk) {
                rd.setColor(new Color(200, 128, 0));
                aflk = false;
            } else {
                rd.setColor(new Color(255, 128, 0));
                aflk = true;
            }
            rd.drawRoundRect(Utility.centeredWidthX(main_menu_op_1_width), main_menu_op_1_y, main_menu_op_1_width,
                    main_menu_button_height, 7, 20);
        } else {
            rd.setColor(new Color(0, 0, 0));
            rd.drawRoundRect(Utility.centeredWidthX(main_menu_op_1_width), main_menu_op_1_y, main_menu_op_1_width,
                    main_menu_button_height, 7, 20);
        }
        if (opselect == 2) {
            if (shaded) {
                rd.setColor(new Color(140, 70, 0));
                rd.fillRect(Utility.centeredWidthX(main_menu_op_2_width), main_menu_op_2_y, main_menu_op_2_width,
                        main_menu_button_height);
                aflk = false;
            }
            if (aflk) {
                rd.setColor(new Color(200, 0, 0));
                aflk = false;
            } else {
                rd.setColor(new Color(255, 128, 0));
                aflk = true;
            }
            rd.drawRoundRect(Utility.centeredWidthX(main_menu_op_2_width), main_menu_op_2_y, main_menu_op_2_width,
                    main_menu_button_height, 7, 20);
        } else {
            rd.setColor(new Color(0, 0, 0));
            rd.drawRoundRect(Utility.centeredWidthX(main_menu_op_2_width), main_menu_op_2_y, main_menu_op_2_width,
                    main_menu_button_height, 7, 20);
        }
        if (opselect == 3) {
            if (shaded) {
                rd.setColor(new Color(140, 70, 0));
                rd.fillRect(Utility.centeredWidthX(main_menu_op_3_width), main_menu_op_3_y, main_menu_op_3_width,
                        main_menu_button_height);
                aflk = false;
            }
            if (aflk) {
                rd.setColor(new Color(200, 0, 128));
                aflk = false;
            } else {
                rd.setColor(new Color(255, 128, 0));
                aflk = true;
            }
            rd.drawRoundRect(Utility.centeredWidthX(main_menu_op_3_width), main_menu_op_3_y, main_menu_op_3_width,
                    main_menu_button_height, 7, 20);
        } else {
            rd.setColor(new Color(0, 0, 0));
            rd.drawRoundRect(Utility.centeredWidthX(main_menu_op_3_width), main_menu_op_3_y, main_menu_op_3_width,
                    main_menu_button_height, 7, 20);
        }
        rd.drawImage(opti, Utility.centeredImageX(opti), 250 + main_menu_height_origin, null);
        SettingsScreen.drawMenuItem(rd, "Settings", GameFacts.screenWidth / 2F, main_menu_op_3_y + 18);
        if (control.enter || control.handb) {
            if (opselect == 0) {
                if (unlocked == 1 && oldfase == Phase.INGAME) {
                    oldfase = Phase.CARSELECTTRIGGER;
                    fase = Phase.INSTRUCTIONS;
                } else {
                    fase = Phase.CARSELECTTRIGGER;
                }
            }
            if (opselect == 1) {
                oldfase = Phase.MAINMENU;
                fase = Phase.INSTRUCTIONS;
            }
            if (opselect == 2) {
                fase = Phase.CREDITS;
            }
            if (opselect == 3) {
                openSettings(Phase.MAINMENU);
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

    public void blendude(Image image) {
        if (!macn) {
            if (Math.random() > Math.random()) {
                dudo = 217;
            } else {
                dudo = 331;
            }
            int background[] = new int[19520];
            PixelGrabber grabber = new PixelGrabber(image, dudo, 0, 122, 160, background, 0, 122);
            try {
                grabber.grabPixels();
            } catch (InterruptedException ignored) {
                dudo = -1;
            }
            int j = 0;
            do {
                int dudePixels[] = new int[19520];
                PixelGrabber dudeGrabber = new PixelGrabber(dude[j], 0, 10, 122, 160, dudePixels, 0, 122);
                try {
                    dudeGrabber.grabPixels();
                } catch (InterruptedException ignored) {
                    dudo = -1;
                }
                if (dudo != -1) {
                    int k = 0;
                    do {
                        if (dudePixels[k] != dudePixels[0]) {
                            Color color = new Color(dudePixels[k]);
                            Color behind = new Color(background[k]);
                            int red = (color.getRed() + behind.getRed() * 3) / 4;
                            if (red > 255) {
                                red = 255;
                            }
                            if (red < 0) {
                                red = 0;
                            }
                            int green = (color.getGreen() + behind.getGreen() * 3) / 4;
                            if (green > 255) {
                                green = 255;
                            }
                            if (green < 0) {
                                green = 0;
                            }
                            int blue = (color.getBlue() + behind.getBlue() * 3) / 4;
                            if (blue > 255) {
                                blue = 255;
                            }
                            if (blue < 0) {
                                blue = 0;
                            }
                            Color blended = new Color(red, green, blue);
                            dudePixels[k] = blended.getRGB();
                        }
                    } while (++k < 19520);
                    dudeb[j] = createImage(new MemoryImageSource(122, 160, dudePixels, 0, 122));
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

    public void musicomp(int stage, Control control) {
        hipnoload(stage, true);
        if (control.handb || control.enter) {
            System.gc();
            fase = Phase.INGAME;
            control.handb = false;
            control.enter = false;
        }
    }

    private void drawSmokeCarsbg() {
        if (Math.abs(flyr - flyrdest) > 20) {
            if (flyr > flyrdest) {
                flyr -= 20;
            } else {
                flyr += 20;
            }
        } else {
            flyr = flyrdest;
            flyrdest = (int) ((flyr + Medium.fxRandom() * 160F) - 80F);
        }
        if (flyr > 160) {
            flyr = 160;
        }
        if (flatr > 170) {
            flatrstart++;
            flatr = flatrstart * 3;
            flyr = (int) (Medium.fxRandom() * 160F - 80F);
            flyrdest = (int) ((flyr + Medium.fxRandom() * 160F) - 80F);
            flang = 1;
            flangados = (int) (Medium.fxRandom() * 6F + 2.0F);
            blackn = 0.0F;
            blacknados = Medium.fxRandom() * 0.4F;
        }
        int i = 0;
        do {
            int j = 0;
            do {
                if (smokey[i + j * 466] != smokey[0]) {
                    float distance = Utility.pys(i, 233, j, flyr);
                    int dx = (int) (((i - 233) / distance) * flatr);
                    int dy = (int) (((j - flyr) / distance) * flatr);
                    int target = i + dx + 100 + (j + dy + 110) * GameFacts.screenWidth;
                    if (i + dx + 100 < GameFacts.screenWidth && i + dx + 100 > 0 && j + dy + 110 < GameFacts.screenHeight
                            && j + dy + 110 > 0 && target < 0xE1000
                            && target >= 0) {
                        Color color = new Color(flexpix[target]);
                        Color smoke = new Color(smokey[i + j * 466]);
                        float density = (255F - smoke.getRed()) / 255F;
                        int gray = (int) ((color.getRed() * (flang * density)
                                + smoke.getRed() * (1.0F - density)) / (flang * density + (1.0F - density) + blackn));
                        if (gray > 255) {
                            gray = 255;
                        }
                        if (gray < 0) {
                            gray = 0;
                        }
                        Color grayColor = new Color(gray, gray, gray);
                        flexpix[target] = grayColor.getRGB();
                    }
                }
            } while (++j < 202);
        } while (++i < 466);
        blackn += blacknados;
        flang += flangados;
        flatr += 10 + flatrstart * 2;
        Image image = createImage(new MemoryImageSource(GameFacts.screenWidth, GameFacts.screenHeight, flexpix, 0,
                GameFacts.screenWidth));
        rd.drawImage(image, 0, 0, null);
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

    public void carselect(Control control, ContO models[], Madness car) {
        /* this is a mess... */

        if (flatrstart == 6) {
            rd.drawImage(carsbg, 0, 0, null);
            // } else if (flatrstart <= 1) {
            // drawSmokeCarsbg();
        } else {
            rd.setColor(new Color(255, 255, 255, 50));
            rd.fillRect(0, 0, GameFacts.screenWidth, GameFacts.screenHeight);
            carsbginflex();
            flatrstart = 6;
        }
        intertrack.play();
        rd.drawImage(selectcar, Utility.centeredImageX(selectcar), 12, null);
        Medium.crs = true;
        Medium.x = -GameFacts.screenWidth/2;
        Medium.y = -(int)(GameFacts.screenHeight*0.83);
        Medium.z = 200;
        Medium.xz = 0;
        Medium.zy = 10;
        Medium.ground = 570;
        models[sc[0]].d(rd);

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
        if (fase == Phase.MAINMENU) {
            if (mouseState == 1) {
                if (overon(Utility.centeredWidthX(main_menu_op_0_width), main_menu_op_0_y, main_menu_op_0_width,
                        main_menu_button_height, mouseX, mouseY)) {
                    opselect = 0;
                    shaded = true;
                }
                if (overon(Utility.centeredWidthX(main_menu_op_1_width), main_menu_op_1_y, main_menu_op_1_width,
                        main_menu_button_height, mouseX, mouseY)) {
                    opselect = 1;
                    shaded = true;
                }
                if (overon(Utility.centeredWidthX(main_menu_op_2_width), main_menu_op_2_y, main_menu_op_2_width,
                        main_menu_button_height, mouseX, mouseY)) {
                    opselect = 2;
                    shaded = true;
                }
                if (overon(Utility.centeredWidthX(main_menu_op_3_width), main_menu_op_3_y, main_menu_op_3_width,
                        main_menu_button_height, mouseX, mouseY)) {
                    opselect = 3;
                    shaded = true;
                }
            }
            if (mouseState == 2 && shaded) {
                control.enter = true;
                shaded = false;
            }
            if (mouseState == 0 && (mouseX != lxm || mouseY != lym)) {
                if (overon(Utility.centeredWidthX(main_menu_op_0_width), main_menu_op_0_y, main_menu_op_0_width,
                        main_menu_button_height, mouseX, mouseY)) {
                    opselect = 0;
                }
                if (overon(Utility.centeredWidthX(main_menu_op_1_width), main_menu_op_1_y, main_menu_op_1_width,
                        main_menu_button_height, mouseX, mouseY)) {
                    opselect = 1;
                }
                if (overon(Utility.centeredWidthX(main_menu_op_2_width), main_menu_op_2_y, main_menu_op_2_width,
                        main_menu_button_height, mouseX, mouseY)) {
                    opselect = 2;
                }
                if (overon(Utility.centeredWidthX(main_menu_op_3_width), main_menu_op_3_y, main_menu_op_3_width,
                        main_menu_button_height, mouseX, mouseY)) {
                    opselect = 3;
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
        PixelGrabber grabber = new PixelGrabber(carsbg, 0, 0, GameFacts.screenWidth, GameFacts.screenHeight,
                flexpix, 0, GameFacts.screenWidth);
        try {
            grabber.grabPixels();
        } catch (InterruptedException ignored) {
        }
    }
}
