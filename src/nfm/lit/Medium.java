package nfm.lit;
import java.awt.*;
import java.util.Random;

/**
 * Medium manages most of the graphics you see during games, such as clouds, ground, and dust.
 * This class was edited and re-edited again by Chaotic for (mostly) full NFMM Graphics (excluding sparks and extra dust).
 *
 * @author Kaffeinated, Chaotic, Omar Wally
 */
public class Medium {
    /**
     * The race camera's stream. The camera decides which cars are on screen, and
     * the race reads that back (a car out of view finishes a repair hoop at
     * once), so the camera's dice must be as reproducible as the race's.
     */
    public static final RaceRandom cameraRandom = new RaceRandom();
    /**
     * Everything that only looks or sounds different: dust, sparks, flames,
     * clouds, lightning, captions, sound choices and menus. It never touches the
     * race, so drawing more frames or playing sounds cannot change a result.
     */
    public static final RaceRandom fxRandom = new RaceRandom();

    private static int nmt = 0;
    private static int[] mrd = null;
    private static int[] nmv = null;
    private static int[][] mtx = null;
    private static int[][] mty = null;
    private static int[][] mtz = null;
    private static int[][][] mtc = null;
    public static int mgen = (int) (fxRandom.nextDouble() * 100000D);
    public static int focus_point = 400;
    public static int ground = 250;
    private static int skyline = -300;
    public static final int[] fade = {
            3000, 4500, 6000, 7500, 9000, 10500, 12000, 13500, 15000, 16500, 18000, 19500, 21000, 22500, 24000, 25500
    };
    private static final int[] cldd = {
            210, 210, 210, 1, -1000
    };
    private static final int[] clds = {
            210, 210, 210
    };
    private static final int[] osky = {
            170, 220, 255
    };
    public static final int[] csky = {
            170, 220, 255
    };
    private static final int[] ogrnd = {
            205, 200, 200
    };
    public static final int[] cgrnd = {
            205, 200, 200
    };
    private static final int[] texture = {
            0, 0, 0, 50
    };
    private static final int[] cpol = {
            215, 210, 210
    };
    private static final int[] crgrnd = {
            205, 200, 200
    };
    public static final int[] cfade = {
            255, 220, 220
    };
    public static int snap[] = {
            0, 0, 0
    };
    public static int origfade = 3000;
    public static int fogd = 7;
    public static boolean lightson = false;
    private static int noelec = 0;
    public static int lightn = -1;
    public static boolean lton = false;
    private static int lilo = 217;
    public static int flex = 0;
    public static boolean trk = false;
    public static boolean crs = false;
    /*
     * The projection: the picture's centre (cx, cy) and size (w, h). They are
     * the game's own view's (GameFacts.GAME_WIDTH x GAME_HEIGHT) but while a
     * picture of another shape is drawn, see showView().
     */
    public static int cx = GameFacts.GAME_WIDTH / 2;
    public static int cy = GameFacts.GAME_HEIGHT / 2;
    public static int cz = 50;
    public static int xz = 0;
    public static int zy = 0;
    public static int x = 0;
    public static int y = 0;
    public static int z = 0;
    public static int w = GameFacts.GAME_WIDTH;
    public static int h = GameFacts.GAME_HEIGHT;
    public static int nsp = 0;
    public static int spx[] = new int[7];
    public static int spz[] = new int[7];
    public static int sprad[] = new int[7];
    private static boolean td = false;
    private static int bcxz = 0;
    public static int vxz = 180;
    public static int adv = 500;
    public static boolean vert = false;
    private static int trns = 1;
    private static int ogpx[][] = new int[9500][8];
    private static int ogpz[][] = new int[9500][8];
    private static int cgpx[] = new int[9500];
    private static int cgpz[] = new int[9500];
    private static int sgpx = 0;
    private static int sgpz = 0;
    private static int nrw = 0;
    private static int ncl = 0;
    public static int lastmaf = 0;
    public static int checkpoint = -1;
    public static boolean lastcheck = false;
    public static float elecr = 0.0F;
    public static boolean cpflik = false;
    public static boolean nochekflk = false;
    public static int hit = 45000;
    public static int ptr = 0;
    public static int ptcnt = -10;
    public static int nrnd = 0;
    public static long trx = 0L;
    public static long trz = 0L;
    private static long atrx = 0L;
    private static long atrz = 0L;
    public static int fallen = 0;
    private static float fo = 1.0F;
    private static float gofo = (float) (0.33000001311302185D + fxRandom.nextDouble() * 1.3400000000000001D);
    private static float[][] pvr;
    private static int[] pmx;
    private static float[] pcv;
    private static int resdown;
    private static int noc;
    private static int[] clx;
    private static int[] clz;
    private static int[] cmx;
    private static int[][][] clax;
    private static int[][][] clay;
    private static int[][][] claz;
    private static int[][][][] clc;
    static int iw;
    private static int nst;
    private static int[] stx;
    private static int[] stz;
    private static int[][][] stc;
    private static boolean[] bst;
    private static int[] twn;
    static int ih;
    private static int fvect;

    public static int detailtype = 2;

    public static boolean noclouds = false;

    /** One value from the cosmetic stream. */
    public static float fxRandom() {
        return fxRandom.next();
    }

    /** The seed of the race being set up or run, see {@link #seedRace(long)}. */
    private static long raceSeed;

    /**
     * Starts the camera's and the scenery's random streams from the race's
     * seed (the race's own is {@link World#seed}). Called when a stage is set
     * up, before anything in the race or its scenery draws a number.
     */
    public static void seedRace(long seed) {
        raceSeed = seed;
        fxRandom.reseed(RaceRandom.derive(seed, RaceRandom.STREAM_EFFECTS));
        cameraRandom.reseed(RaceRandom.derive(seed, RaceRandom.STREAM_CAMERA));
        Plane.seedShapes(seed);
    }

    public static long raceSeed() {
        return raceSeed;
    }

    /**
     * Restarts the camera and cosmetic streams when the countdown begins. The
     * intro fly-by before it lasts as long as the player lets it, so these
     * streams start the race itself from a known state either way.
     */
    public static void startCountdown() {
        cameraRandom.reseed(RaceRandom.derive(raceSeed, RaceRandom.STREAM_CAMERA));
        fxRandom.reseed(RaceRandom.derive(raceSeed, RaceRandom.STREAM_RACE_EFFECTS));
    }

    /*
     * Drawing the world is not free of side effects: d() and the scenery it draws
     * advance the lightning, the checkpoint flicker and the star twinkle, and
     * they draw from the cosmetic random stream. The legacy loop drew exactly
     * once per tick. The game still draws one step per tick, and takes back the
     * side effects of every presented frame with this snapshot.
     */
    private static final int[] savedFx = new int[RaceRandom.STATE_SIZE];
    private static int savedLilo;
    private static int savedLightn;
    private static int savedNoelec;
    private static boolean savedCpflik;
    private static float savedElecr;
    private static int savedFlex;
    private static int savedLastmaf;
    private static int[] savedTwn;
    private static int[][][] savedStc;

    /**
     * Projects into the race's picture as it is shown, GameFacts.sceneWidth x
     * sceneHeight, from the same place: the camera's x and y are counted from
     * the picture's corner, so they move by as much as its centre does.
     * Drawing a frame does this and {@link #gameView()} after; the camera is
     * placed, and what is on screen is read back for the race, in the game's
     * own view, so a race plays out the same in a window of any shape.
     */
    public static void showView() {
        setView(GameFacts.sceneWidth, GameFacts.sceneHeight);
    }

    /** Projects into the game's own view again, the view everything but drawing works in. */
    public static void gameView() {
        setView(GameFacts.GAME_WIDTH, GameFacts.GAME_HEIGHT);
    }

    private static void setView(int width, int height) {
        int centreX = width / 2;
        int centreY = height / 2;
        int down = cy - centreY;
        x += cx - centreX;
        y += down;
        // the ground is measured from the camera, as the picture is: it moves with it, or the 3D drawn in a menu
        // (the car select's car) would have its shadow drawn on a floor that stayed behind
        ground -= down;
        cx = centreX;
        cy = centreY;
        w = width;
        h = height;
    }

    /**
     * As {@link #shownView()}, for one split-screen player's pane: a picture
     * {@code width} x {@code height} in the game's units, from the same place.
     */
    public static ShownView paneView(int width, int height) {
        setView(width, height);
        return SHOWN;
    }

    /**
     * What {@link #d} does to the camera before it draws: the clamp, no
     * spots yet, and the ground's height under it. For a look at the world
     * from a second camera that draws the objects but not the ground.
     */
    public static void prepareLook() {
        nsp = 0;
        clampView();
        ground = 250 - y;
    }

    /**
     * A race camera of its own: where it is, how it turns and moves, which
     * checkpoint it highlights and its random stream. Split screen's Player 2
     * keeps one, and {@link #swap swaps} it in to place, move and draw from
     * it; Player 1's camera is the statics, as in the classic game.
     */
    public static final class Camera {
        int x;
        int y;
        int z;
        int xz;
        int zy;
        int bcxz;
        int vxz = 180;
        int adv = 500;
        boolean vert;
        int trns = 1;
        boolean td;
        int flex;
        int checkpoint = -1;
        boolean lastcheck;
        final int[] random = new int[RaceRandom.STATE_SIZE];

        public Camera() {
            new RaceRandom().exportState(random);
        }

        /** Starts this camera as the statics' camera is now, with its random stream from {@code seed}. */
        public void copyFromStatics(long seed) {
            x = Medium.x;
            y = Medium.y;
            z = Medium.z;
            xz = Medium.xz;
            zy = Medium.zy;
            bcxz = Medium.bcxz;
            vxz = Medium.vxz;
            adv = Medium.adv;
            vert = Medium.vert;
            trns = Medium.trns;
            td = Medium.td;
            flex = Medium.flex;
            checkpoint = Medium.checkpoint;
            lastcheck = Medium.lastcheck;
            RaceRandom r = new RaceRandom();
            r.reseed(seed);
            r.exportState(random);
        }

        public int x() {
            return x;
        }

        public int y() {
            return y;
        }

        public int z() {
            return z;
        }

        public int xz() {
            return xz;
        }

        public int zy() {
            return zy;
        }

        /** Puts this camera where the statics' camera is, as it moves (its random stream stays its own). */
        public void followStatics() {
            x = Medium.x;
            y = Medium.y;
            z = Medium.z;
            xz = Medium.xz;
            zy = Medium.zy;
            bcxz = Medium.bcxz;
            vxz = Medium.vxz;
            adv = Medium.adv;
            vert = Medium.vert;
            trns = Medium.trns;
            td = Medium.td;
            flex = Medium.flex;
        }

        /** The checkpoint this camera's player is heading for. */
        public void showCheckpoint(int next) {
            checkpoint = next;
        }

        public void showLastCheckpoint(boolean last) {
            lastcheck = last;
        }
    }

    private static final int[] swapRandom = new int[RaceRandom.STATE_SIZE];

    /**
     * Exchanges the camera in the statics with {@code c}: called once puts
     * {@code c}'s camera in place (and the statics' in {@code c}), called
     * again puts both back. Always in pairs, in a try/finally.
     */
    public static void swap(Camera c) {
        int i = x; x = c.x; c.x = i;
        i = y; y = c.y; c.y = i;
        i = z; z = c.z; c.z = i;
        i = xz; xz = c.xz; c.xz = i;
        i = zy; zy = c.zy; c.zy = i;
        i = bcxz; bcxz = c.bcxz; c.bcxz = i;
        i = vxz; vxz = c.vxz; c.vxz = i;
        i = adv; adv = c.adv; c.adv = i;
        i = trns; trns = c.trns; c.trns = i;
        i = flex; flex = c.flex; c.flex = i;
        i = checkpoint; checkpoint = c.checkpoint; c.checkpoint = i;
        boolean b = vert; vert = c.vert; c.vert = b;
        b = td; td = c.td; c.td = b;
        b = lastcheck; lastcheck = c.lastcheck; c.lastcheck = b;
        cameraRandom.exportState(swapRandom);
        cameraRandom.importState(c.random);
        System.arraycopy(swapRandom, 0, c.random, 0, swapRandom.length);
    }

    /** The shown view while open, see {@link #shownView()}; closing it goes back to the game's own. */
    public interface ShownView extends AutoCloseable {
        @Override
        void close();
    }

    private static final ShownView SHOWN = Medium::gameView;

    /**
     * {@link #showView()} for a try-with-resources block, so the game's own
     * view is back however the block ends: a race placed in the shown view
     * would no longer play out as recorded.
     */
    public static ShownView shownView() {
        showView();
        return SHOWN;
    }

    /**
     * As {@link #shownView()}, for the 3D drawn in a menu (the car select's
     * car, the stage select's stage): the menus' picture, GameFacts.screenWidth
     * x screenHeight, in which they keep their own size.
     */
    public static ShownView menuView() {
        setView(GameFacts.screenWidth, GameFacts.screenHeight);
        return SHOWN;
    }

    public static void saveDrawState() {
        fxRandom.exportState(savedFx);
        savedLilo = lilo;
        savedLightn = lightn;
        savedNoelec = noelec;
        savedCpflik = cpflik;
        savedElecr = elecr;
        savedFlex = flex;
        savedLastmaf = lastmaf;
        if (twn == null) {
            savedTwn = null;
            savedStc = null;
            return;
        }
        if (savedTwn == null || savedTwn.length != twn.length) {
            savedTwn = new int[twn.length];
            savedStc = new int[stc.length][2][3];
        }
        System.arraycopy(twn, 0, savedTwn, 0, twn.length);
        for (int i = 0; i < stc.length; i++) {
            System.arraycopy(stc[i][0], 0, savedStc[i][0], 0, 3);
            System.arraycopy(stc[i][1], 0, savedStc[i][1], 0, 3);
        }
    }

    public static void restoreDrawState() {
        fxRandom.importState(savedFx);
        lilo = savedLilo;
        lightn = savedLightn;
        noelec = savedNoelec;
        cpflik = savedCpflik;
        elecr = savedElecr;
        flex = savedFlex;
        lastmaf = savedLastmaf;
        if (savedTwn == null || twn == null || twn.length != savedTwn.length) {
            return;
        }
        System.arraycopy(savedTwn, 0, twn, 0, twn.length);
        for (int i = 0; i < stc.length; i++) {
            System.arraycopy(savedStc[i][0], 0, stc[i][0], 0, 3);
            System.arraycopy(savedStc[i][1], 0, stc[i][1], 0, 3);
        }
    }

    /**
     * Keeps the camera inside the range the renderer supports. d() applies this
     * before drawing, and the camera code (around(), watch()) depends on it
     * having been applied between ticks: their pitch easing can run far past
     * 90 degrees and is only pulled back by this clamp. The presentation pass
     * restores the camera it drew from, so each scene tick calls this itself.
     */
    public static void clampView() {
        if (zy > 90) {
            zy = 90;
        }
        if (zy < -90) {
            zy = -90;
        }
        if (xz > 360) {
            xz -= 360;
        }
        if (xz < 0) {
            xz += 360;
        }
        if (y > 0) {
            y = 0;
        }
    }

    public static void newpolys(int minX, int spanX, int minZ, int spanZ, Trackers trackers, int seed) {
        if (detailtype == 0)
            return;
        Random patchRandom = new Random((seed + cgrnd[0] + cgrnd[1] + cgrnd[2]) * 1671);
        nrw = spanX / 1200 + 9;
        ncl = spanZ / 1200 + 9;
        sgpx = minX - 4800;
        sgpz = minZ - 4800;
        ogpx = null;
        ogpz = null;
        pvr = null;
        cgpx = null;
        cgpz = null;
        pmx = null;
        pcv = null;
        ogpx = new int[nrw * ncl][8];
        ogpz = new int[nrw * ncl][8];
        pvr = new float[nrw * ncl][8];
        cgpx = new int[nrw * ncl];
        cgpz = new int[nrw * ncl];
        pmx = new int[nrw * ncl];
        pcv = new float[nrw * ncl];
        int column = 0;
        int row = 0;
        for (int patch = 0; patch < nrw * ncl; patch++) {
            cgpx[patch] = sgpx + column * 1200 + (int) (patchRandom.nextDouble() * 1000D - 500D);
            cgpz[patch] = sgpz + row * 1200 + (int) (patchRandom.nextDouble() * 1000D - 500D);
            for (int piece = 0; piece < trackers.nt; piece++) {
                if (trackers.zy[piece] != 0 || trackers.xy[piece] != 0) {
                    continue;
                }
                if (trackers.radx[piece] < trackers.radz[piece] && Math.abs(cgpz[patch] - trackers.z[piece]) < trackers.radz[piece]) {
                    for (; Math.abs(cgpx[patch] - trackers.x[piece]) < trackers.radx[piece]; cgpx[patch] += patchRandom.nextDouble()
                            * trackers.radx[piece] * 2D - trackers.radx[piece]) {
                    }
                }
                if (trackers.radz[piece] >= trackers.radx[piece]
                        || Math.abs(cgpx[patch] - trackers.x[piece]) >= trackers.radx[piece]) {
                    continue;
                }
                for (; Math.abs(cgpz[patch] - trackers.z[piece]) < trackers.radz[piece]; cgpz[patch] += patchRandom.nextDouble()
                        * trackers.radz[piece] * 2D - trackers.radz[piece]) {
                }
            }

            if (++column == nrw) {
                column = 0;
                row++;
            }
        }

        for (int patch = 0; patch < nrw * ncl; patch++) {
            float size = (float) (0.29999999999999999D + 1.6000000000000001D * patchRandom.nextDouble());
            ogpx[patch][0] = 0;
            ogpz[patch][0] = (int) ((100D + patchRandom.nextDouble() * 760D) * size);
            ogpx[patch][1] = (int) ((100D + patchRandom.nextDouble() * 760D) * 0.70709999999999995D * size);
            ogpz[patch][1] = ogpx[patch][1];
            ogpx[patch][2] = (int) ((100D + patchRandom.nextDouble() * 760D) * size);
            ogpz[patch][2] = 0;
            ogpx[patch][3] = (int) ((100D + patchRandom.nextDouble() * 760D) * 0.70709999999999995D * size);
            ogpz[patch][3] = -ogpx[patch][3];
            ogpx[patch][4] = 0;
            ogpz[patch][4] = -(int) ((100D + patchRandom.nextDouble() * 760D) * size);
            ogpx[patch][5] = -(int) ((100D + patchRandom.nextDouble() * 760D) * 0.70709999999999995D * size);
            ogpz[patch][5] = ogpx[patch][5];
            ogpx[patch][6] = -(int) ((100D + patchRandom.nextDouble() * 760D) * size);
            ogpz[patch][6] = 0;
            ogpx[patch][7] = -(int) ((100D + patchRandom.nextDouble() * 760D) * 0.70709999999999995D * size);
            ogpz[patch][7] = -ogpx[patch][7];
            for (int corner = 0; corner < 8; corner++) {
                int before = corner - 1;
                if (before == -1) {
                    before = 7;
                }
                int after = corner + 1;
                if (after == 8) {
                    after = 0;
                }
                ogpx[patch][corner] = ((ogpx[patch][before] + ogpx[patch][after]) / 2 + ogpx[patch][corner]) / 2;
                ogpz[patch][corner] = ((ogpz[patch][before] + ogpz[patch][after]) / 2 + ogpz[patch][corner]) / 2;
                pvr[patch][corner] = (float) (1.1000000000000001D + patchRandom.nextDouble() * 0.80000000000000004D);
                int reach = (int) Math.sqrt((int) (ogpx[patch][corner] * ogpx[patch][corner] * pvr[patch][corner] * pvr[patch][corner]
                        + ogpz[patch][corner] * ogpz[patch][corner] * pvr[patch][corner] * pvr[patch][corner]));
                if (reach > pmx[patch]) {
                    pmx[patch] = reach;
                }
            }

            pcv[patch] = (float) (0.96999999999999997D + patchRandom.nextDouble() * 0.029999999999999999D);
            if (pcv[patch] > 1.0F) {
                pcv[patch] = 1.0F;
            }
            if (patchRandom.nextDouble() > patchRandom.nextDouble()) {
                pcv[patch] = 1.0F;
            }
        }

    }

    private static void groundpolys(Graphics2D rd) {
        if (detailtype == 0)
            return;
        int firstColumn = (x - sgpx) / 1200 - 12;
        if (firstColumn < 0) {
            firstColumn = 0;
        }
        int endColumn = firstColumn + 25;
        if (endColumn > nrw) {
            endColumn = nrw;
        }
        if (endColumn < firstColumn) {
            endColumn = firstColumn;
        }
        int firstRow = (z - sgpz) / 1200 - 12;
        if (firstRow < 0) {
            firstRow = 0;
        }
        int endRow = firstRow + 25;
        if (endRow > ncl) {
            endRow = ncl;
        }
        if (endRow < firstRow) {
            endRow = firstRow;
        }
        int patchDepth[][] = new int[endColumn - firstColumn][endRow - firstRow];
        for (int column = firstColumn; column < endColumn; column++) {
            for (int row = firstRow; row < endRow; row++) {
                patchDepth[column - firstColumn][row - firstRow] = 0;
                int patch = column + row * nrw;
                if (resdown >= 2 && patch % 2 != 0) {
                    continue;
                }
                int viewX = cx + (int) ((cgpx[patch] - x - cx) * RadicalMath.cos(xz) - (cgpz[patch] - z - cz) * RadicalMath.sin(xz));
                int viewZ = cz + (int) ((cgpx[patch] - x - cx) * RadicalMath.sin(xz) + (cgpz[patch] - z - cz) * RadicalMath.cos(xz));
                int depth = cz + (int) ((250 - y - cy) * RadicalMath.sin(zy) + (viewZ - cz) * RadicalMath.cos(zy));
                if (Utility.xs(viewX + pmx[patch], depth) <= -cullX || Utility.xs(viewX - pmx[patch], depth) >= w + cullX || depth <= -pmx[patch] || depth >= fade[2]) {
                    continue;
                }
                patchDepth[column - firstColumn][row - firstRow] = depth;
                int cornerX[] = new int[8];
                int cornerZ[] = new int[8];
                int cornerY[] = new int[8];
                for (int corner = 0; corner < 8; corner++) {
                    cornerX[corner] = (int) ((ogpx[patch][corner] * pvr[patch][corner] + cgpx[patch]) - x);
                    cornerZ[corner] = (int) ((ogpz[patch][corner] * pvr[patch][corner] + cgpz[patch]) - z);
                    cornerY[corner] = ground;
                }
                if (Precise.on) {
                    Precise.camera(cornerX, cornerY, cornerZ, 8, 1, 10, EXACT_X, EXACT_Y);
                }

                Utility.rot(cornerX, cornerZ, cx, cz, xz, 8);
                Utility.rot(cornerY, cornerZ, cy, cz, zy, 8);
                int screenX[] = new int[8];
                int screenY[] = new int[8];
                int above = 0;
                int below = 0;
                int leftOf = 0;
                int rightOf = 0;
                boolean onScreen = true;
                for (int corner = 0; corner < 8; corner++) {
                    screenX[corner] = Utility.xs(cornerX[corner], cornerZ[corner]);
                    screenY[corner] = Utility.ys(cornerY[corner], cornerZ[corner], 1);
                    if (screenY[corner] < -cullY || cornerZ[corner] < 10) {
                        above++;
                    }
                    if (screenY[corner] > h + cullY || cornerZ[corner] < 10) {
                        below++;
                    }
                    if (screenX[corner] < -cullX || cornerZ[corner] < 10) {
                        leftOf++;
                    }
                    if (screenX[corner] > w + cullX || cornerZ[corner] < 10) {
                        rightOf++;
                    }
                }

                if (leftOf == 8 || above == 8 || below == 8 || rightOf == 8) {
                    onScreen = false;
                }
                if (!onScreen) {
                    continue;
                }
                int red = (int) ((cpol[0] * pcv[patch] + cgrnd[0]) / 2.0F);
                int green = (int) ((cpol[1] * pcv[patch] + cgrnd[1]) / 2.0F);
                int blue = (int) ((cpol[2] * pcv[patch] + cgrnd[2]) / 2.0F);
                if (depth - pmx[patch] > fade[0]) {
                    red = (red * 7 + cfade[0]) / 8;
                    green = (green * 7 + cfade[1]) / 8;
                    blue = (blue * 7 + cfade[2]) / 8;
                }
                if (depth - pmx[patch] > fade[1]) {
                    red = (red * 7 + cfade[0]) / 8;
                    green = (green * 7 + cfade[1]) / 8;
                    blue = (blue * 7 + cfade[2]) / 8;
                }
                rd.setColor(Colors.of(red, green, blue));
                if (Precise.on) {
                    Precise.bind(screenX, screenY, EXACT_X, EXACT_Y, 8, 10_000_000 + patch);
                }
                rd.fillPolygon(screenX, screenY, 8);
            }

        }

        for (int column = firstColumn; column < endColumn; column++) {
            for (int row = firstRow; row < endRow; row++) {
                if (patchDepth[column - firstColumn][row - firstRow] == 0) {
                    continue;
                }
                int patch = column + row * nrw;
                int cornerX[] = new int[8];
                int cornerZ[] = new int[8];
                int cornerY[] = new int[8];
                for (int corner = 0; corner < 8; corner++) {
                    cornerX[corner] = (ogpx[patch][corner] + cgpx[patch]) - x;
                    cornerZ[corner] = (ogpz[patch][corner] + cgpz[patch]) - z;
                    cornerY[corner] = ground;
                }
                if (Precise.on) {
                    Precise.camera(cornerX, cornerY, cornerZ, 8, 1, 10, EXACT_X, EXACT_Y);
                }

                Utility.rot(cornerX, cornerZ, cx, cz, xz, 8);
                Utility.rot(cornerY, cornerZ, cy, cz, zy, 8);
                int screenX[] = new int[8];
                int screenY[] = new int[8];
                int above = 0;
                int below = 0;
                int leftOf = 0;
                int rightOf = 0;
                boolean onScreen = true;
                for (int corner = 0; corner < 8; corner++) {
                    screenX[corner] = Utility.xs(cornerX[corner], cornerZ[corner]);
                    screenY[corner] = Utility.ys(cornerY[corner], cornerZ[corner], 1);
                    if (screenY[corner] < -cullY || cornerZ[corner] < 10) {
                        above++;
                    }
                    if (screenY[corner] > h + cullY || cornerZ[corner] < 10) {
                        below++;
                    }
                    if (screenX[corner] < -cullX || cornerZ[corner] < 10) {
                        leftOf++;
                    }
                    if (screenX[corner] > w + cullX || cornerZ[corner] < 10) {
                        rightOf++;
                    }
                }

                if (leftOf == 8 || above == 8 || below == 8 || rightOf == 8) {
                    onScreen = false;
                }
                if (!onScreen) {
                    continue;
                }
                int red = (int) (cpol[0] * pcv[patch]);
                int green = (int) (cpol[1] * pcv[patch]);
                int blue = (int) (cpol[2] * pcv[patch]);
                if (patchDepth[column - firstColumn][row - firstRow] - pmx[patch] > fade[0]) {
                    red = (red * 7 + cfade[0]) / 8;
                    green = (green * 7 + cfade[1]) / 8;
                    blue = (blue * 7 + cfade[2]) / 8;
                }
                if (patchDepth[column - firstColumn][row - firstRow] - pmx[patch] > fade[1]) {
                    red = (red * 7 + cfade[0]) / 8;
                    green = (green * 7 + cfade[1]) / 8;
                    blue = (blue * 7 + cfade[2]) / 8;
                }
                rd.setColor(Colors.of(red, green, blue));
                if (Precise.on) {
                    Precise.bind(screenX, screenY, EXACT_X, EXACT_Y, 8, 20_000_000 + patch);
                }
                rd.fillPolygon(screenX, screenY, 8);
            }
        }
    }

    public Medium() {
        focus_point = 500;
        ground = 250;
        skyline = -300;
        fogd = 7;
        mgen = (int) (fxRandom.nextDouble() * 100000D);
        snap = new int[3];
        origfade = 3000;
        fogd = 3;
        lightson = false;
        lightn = -1;
        lilo = 217;
        flex = 0;
        trk = false;
        crs = false;
        noelec = 0;
        cx = GameFacts.GAME_WIDTH / 2;
        cy = GameFacts.GAME_HEIGHT / 2;
        cz = 50;
        xz = 0;
        zy = 0;
        x = 0;
        y = 0;
        z = 0;
        w = GameFacts.GAME_WIDTH;
        h = GameFacts.GAME_HEIGHT;
        nsp = 0;
        spx = new int[7];
        spz = new int[7];
        sprad = new int[7];
        td = false;
        bcxz = 0;
        vxz = 180;
        adv = 500;
        vert = false;
        trns = 1;
        ogpx = new int[9500][8];
        ogpz = new int[9500][8];
        cgpx = new int[9500];
        cgpz = new int[9500];
        sgpx = 0;
        sgpz = 0;
        nrw = 0;
        ncl = 0;
        lastmaf = 0;
        checkpoint = -1;
        lastcheck = false;
        elecr = 0.0F;
        cpflik = false;
        nochekflk = false;
        seedRace(RaceRandom.newSeed());
        hit = 45000;
        ptr = 0;
        ptcnt = -10;
        nrnd = 0;
        trx = 0L;
        trz = 0L;
        atrx = 0L;
        atrz = 0L;
        fallen = 0;
        fo = 1.0F;
        gofo = (float) (0.33000001311302185D + fxRandom.nextDouble() * 1.3400000000000001D);
        detailtype = 2;
        noclouds = false;
    }

    public static void newclouds(int minX, int maxX, int minZ, int maxZ) {
        if (detailtype == 0 || detailtype == 1 || noclouds)
            return;
        clx = null;
        clz = null;
        cmx = null;
        clax = null;
        clay = null;
        claz = null;
        clc = null;
        minX = minX / 20 - 10000;
        maxX = maxX / 20 + 10000;
        minZ = minZ / 20 - 10000;
        maxZ = maxZ / 20 + 10000;
        noc = ((maxX - minX) * (maxZ - minZ)) / 0xfe502b;
        clx = new int[noc];
        clz = new int[noc];
        cmx = new int[noc];
        clax = new int[noc][3][12];
        clay = new int[noc][3][12];
        claz = new int[noc][3][12];
        clc = new int[noc][2][6][3];
        for (int cloud = 0; cloud < noc; cloud++) {
            clx[cloud] = (int) (minX + (maxX - minX) * fxRandom.nextDouble());
            clz[cloud] = (int) (minZ + (maxZ - minZ) * fxRandom.nextDouble());
            float size = (float) (0.25D + fxRandom.nextDouble() * 1.25D);
            float reach = (float) ((200D + fxRandom.nextDouble() * 700D) * size);
            clax[cloud][0][0] = (int) (reach * 0.3826D);
            claz[cloud][0][0] = (int) (reach * 0.92379999999999995D);
            clay[cloud][0][0] = (int) ((25D - fxRandom.nextDouble() * 50D) * size);
            reach = (float) ((200D + fxRandom.nextDouble() * 700D) * size);
            clax[cloud][0][1] = (int) (reach * 0.70709999999999995D);
            claz[cloud][0][1] = (int) (reach * 0.70709999999999995D);
            clay[cloud][0][1] = (int) ((25D - fxRandom.nextDouble() * 50D) * size);
            reach = (float) ((200D + fxRandom.nextDouble() * 700D) * size);
            clax[cloud][0][2] = (int) (reach * 0.92379999999999995D);
            claz[cloud][0][2] = (int) (reach * 0.3826D);
            clay[cloud][0][2] = (int) ((25D - fxRandom.nextDouble() * 50D) * size);
            reach = (float) ((200D + fxRandom.nextDouble() * 700D) * size);
            clax[cloud][0][3] = (int) (reach * 0.92379999999999995D);
            claz[cloud][0][3] = -(int) (reach * 0.3826D);
            clay[cloud][0][3] = (int) ((25D - fxRandom.nextDouble() * 50D) * size);
            reach = (float) ((200D + fxRandom.nextDouble() * 700D) * size);
            clax[cloud][0][4] = (int) (reach * 0.70709999999999995D);
            claz[cloud][0][4] = -(int) (reach * 0.70709999999999995D);
            clay[cloud][0][4] = (int) ((25D - fxRandom.nextDouble() * 50D) * size);
            reach = (float) ((200D + fxRandom.nextDouble() * 700D) * size);
            clax[cloud][0][5] = (int) (reach * 0.3826D);
            claz[cloud][0][5] = -(int) (reach * 0.92379999999999995D);
            clay[cloud][0][5] = (int) ((25D - fxRandom.nextDouble() * 50D) * size);
            reach = (float) ((200D + fxRandom.nextDouble() * 700D) * size);
            clax[cloud][0][6] = -(int) (reach * 0.3826D);
            claz[cloud][0][6] = -(int) (reach * 0.92379999999999995D);
            clay[cloud][0][6] = (int) ((25D - fxRandom.nextDouble() * 50D) * size);
            reach = (float) ((200D + fxRandom.nextDouble() * 700D) * size);
            clax[cloud][0][7] = -(int) (reach * 0.70709999999999995D);
            claz[cloud][0][7] = -(int) (reach * 0.70709999999999995D);
            clay[cloud][0][7] = (int) ((25D - fxRandom.nextDouble() * 50D) * size);
            reach = (float) ((200D + fxRandom.nextDouble() * 700D) * size);
            clax[cloud][0][8] = -(int) (reach * 0.92379999999999995D);
            claz[cloud][0][8] = -(int) (reach * 0.3826D);
            clay[cloud][0][8] = (int) ((25D - fxRandom.nextDouble() * 50D) * size);
            reach = (float) ((200D + fxRandom.nextDouble() * 700D) * size);
            clax[cloud][0][9] = -(int) (reach * 0.92379999999999995D);
            claz[cloud][0][9] = (int) (reach * 0.3826D);
            clay[cloud][0][9] = (int) ((25D - fxRandom.nextDouble() * 50D) * size);
            reach = (float) ((200D + fxRandom.nextDouble() * 700D) * size);
            clax[cloud][0][10] = -(int) (reach * 0.70709999999999995D);
            claz[cloud][0][10] = (int) (reach * 0.70709999999999995D);
            clay[cloud][0][10] = (int) ((25D - fxRandom.nextDouble() * 50D) * size);
            reach = (float) ((200D + fxRandom.nextDouble() * 700D) * size);
            clax[cloud][0][11] = -(int) (reach * 0.3826D);
            claz[cloud][0][11] = (int) (reach * 0.92379999999999995D);
            clay[cloud][0][11] = (int) ((25D - fxRandom.nextDouble() * 50D) * size);
            for (int corner = 0; corner < 12; corner++) {
                int before = corner - 1;
                if (before == -1) {
                    before = 11;
                }
                int after = corner + 1;
                if (after == 12) {
                    after = 0;
                }
                clax[cloud][0][corner] = ((clax[cloud][0][before] + clax[cloud][0][after]) / 2 + clax[cloud][0][corner]) / 2;
                clay[cloud][0][corner] = ((clay[cloud][0][before] + clay[cloud][0][after]) / 2 + clay[cloud][0][corner]) / 2;
                claz[cloud][0][corner] = ((claz[cloud][0][before] + claz[cloud][0][after]) / 2 + claz[cloud][0][corner]) / 2;
            }

            for (int corner = 0; corner < 12; corner++) {
                float spread = (float) (1.2D + 0.59999999999999998D * fxRandom.nextDouble());
                clax[cloud][1][corner] = (int) (clax[cloud][0][corner] * spread);
                claz[cloud][1][corner] = (int) (claz[cloud][0][corner] * spread);
                clay[cloud][1][corner] = (int) (clay[cloud][0][corner] - 100D * fxRandom.nextDouble());
                spread = (float) (1.1000000000000001D + 0.29999999999999999D * fxRandom.nextDouble());
                clax[cloud][2][corner] = (int) (clax[cloud][1][corner] * spread);
                claz[cloud][2][corner] = (int) (claz[cloud][1][corner] * spread);
                clay[cloud][2][corner] = (int) (clay[cloud][1][corner] - 240D * fxRandom.nextDouble());
            }

            cmx[cloud] = 0;
            for (int corner = 0; corner < 12; corner++) {
                int before = corner - 1;
                if (before == -1) {
                    before = 11;
                }
                int after = corner + 1;
                if (after == 12) {
                    after = 0;
                }
                clay[cloud][1][corner] = ((clay[cloud][1][before] + clay[cloud][1][after]) / 2 + clay[cloud][1][corner]) / 2;
                clay[cloud][2][corner] = ((clay[cloud][2][before] + clay[cloud][2][after]) / 2 + clay[cloud][2][corner]) / 2;
                int radius = (int) Math.sqrt(clax[cloud][2][corner] * clax[cloud][2][corner] + claz[cloud][2][corner] * claz[cloud][2][corner]);
                if (radius > cmx[cloud]) {
                    cmx[cloud] = radius;
                }
            }

            for (int shade = 0; shade < 6; shade++) {
                double lowShade = fxRandom.nextDouble();
                double highShade = fxRandom.nextDouble();
                for (int channel = 0; channel < 3; channel++) {
                    float range = clds[channel] * 1.05F - clds[channel];
                    clc[cloud][0][shade][channel] = (int) (clds[channel] + range * lowShade);
                    if (clc[cloud][0][shade][channel] > 255) {
                        clc[cloud][0][shade][channel] = 255;
                    }
                    if (clc[cloud][0][shade][channel] < 0) {
                        clc[cloud][0][shade][channel] = 0;
                    }
                    clc[cloud][1][shade][channel] = (int) (clds[channel] * 1.05F + range * highShade);
                    if (clc[cloud][1][shade][channel] > 255) {
                        clc[cloud][1][shade][channel] = 255;
                    }
                    if (clc[cloud][1][shade][channel] < 0) {
                        clc[cloud][1][shade][channel] = 0;
                    }
                }

            }

        }

    }

    private static void drawclouds(Graphics2D rd) {
        if (detailtype == 0 || detailtype == 1 || noclouds)
            return;
        for (int cloud = 0; cloud < noc; cloud++) {
            int viewX = cx + (int) ((clx[cloud] - x / 20 - cx) * RadicalMath.cos(xz) - (clz[cloud] - z / 20 - cz) * RadicalMath.sin(xz));
            int viewZ = cz + (int) ((clx[cloud] - x / 20 - cx) * RadicalMath.sin(xz) + (clz[cloud] - z / 20 - cz) * RadicalMath.cos(xz));
            int depth = cz + (int) ((cldd[4] - y / 20 - cy) * RadicalMath.sin(zy) + (viewZ - cz) * RadicalMath.cos(zy));
            int rightEdge = Utility.xs(viewX + cmx[cloud], depth);
            int leftEdge = Utility.xs(viewX - cmx[cloud], depth);
            if (rightEdge <= -cullX || leftEdge >= w + cullX || depth <= -cmx[cloud] || rightEdge - leftEdge <= 20) {
                continue;
            }
            int cloudX[][] = new int[3][12];
            int cloudY[][] = new int[3][12];
            int cloudZ[][] = new int[3][12];
            int screenX[] = new int[12];
            int screenY[] = new int[12];
            int above;
            int below;
            int leftOf;
            int rightOf;
            boolean onScreen;
            int midY;
            int midX;
            int midZ;
            for (int ring = 0; ring < 3; ring++) {
                for (int point = 0; point < 12; point++) {
                    cloudX[ring][point] = (clax[cloud][ring][point] + clx[cloud]) - x / 20;
                    cloudZ[ring][point] = (claz[cloud][ring][point] + clz[cloud]) - z / 20;
                    cloudY[ring][point] = (clay[cloud][ring][point] + cldd[4]) - y / 20;
                }

                if (Precise.on) {
                    Precise.camera(cloudX[ring], cloudY[ring], cloudZ[ring], 12, 20, 10, CLOUD_X[ring], CLOUD_Y[ring]);
                }
                Utility.rot(cloudX[ring], cloudZ[ring], cx, cz, xz, 12);
                Utility.rot(cloudY[ring], cloudZ[ring], cy, cz, zy, 12);
            }

            for (int side = 0; side < 12; side += 2) {
                above = 0;
                below = 0;
                leftOf = 0;
                rightOf = 0;
                onScreen = true;
                midY = 0;
                midX = 0;
                midZ = 0;
                for (int vertex = 0; vertex < 6; vertex++) {
                    int point = 0;
                    byte ring = 1;
                    if (vertex == 0) {
                        point = side;
                    }
                    if (vertex == 1) {
                        point = side + 1;
                        if (point >= 12) {
                            point -= 12;
                        }
                    }
                    if (vertex == 2) {
                        point = side + 2;
                        if (point >= 12) {
                            point -= 12;
                        }
                    }
                    if (vertex == 3) {
                        point = side + 2;
                        if (point >= 12) {
                            point -= 12;
                        }
                        ring = 2;
                    }
                    if (vertex == 4) {
                        point = side + 1;
                        if (point >= 12) {
                            point -= 12;
                        }
                        ring = 2;
                    }
                    if (vertex == 5) {
                        point = side;
                        ring = 2;
                    }
                    screenX[vertex] = Utility.xs(cloudX[ring][point], cloudZ[ring][point]);
                    screenY[vertex] = Utility.ys(cloudY[ring][point], cloudZ[ring][point], 1);
                    EXACT_X[vertex] = CLOUD_X[ring][point];
                    EXACT_Y[vertex] = CLOUD_Y[ring][point];
                    midX += cloudX[ring][point];
                    midY += cloudY[ring][point];
                    midZ += cloudZ[ring][point];
                    if (screenY[vertex] < -cullY || cloudZ[0][vertex] < 10) {
                        above++;
                    }
                    if (screenY[vertex] > h + cullY || cloudZ[0][vertex] < 10) {
                        below++;
                    }
                    if (screenX[vertex] < -cullX || cloudZ[0][vertex] < 10) {
                        leftOf++;
                    }
                    if (screenX[vertex] > w + cullX || cloudZ[0][vertex] < 10) {
                        rightOf++;
                    }
                }

                if (leftOf == 6 || above == 6 || below == 6 || rightOf == 6) {
                    onScreen = false;
                }
                if (!onScreen) {
                    continue;
                }
                midX /= 6;
                midY /= 6;
                midZ /= 6;
                int distance = (int) Math.sqrt((cy - midY) * (cy - midY) + (cx - midX) * (cx - midX) + midZ * midZ);
                if (distance >= fade[7]) {
                    continue;
                }
                int red = clc[cloud][1][side / 2][0];
                int green = clc[cloud][1][side / 2][1];
                int blue = clc[cloud][1][side / 2][2];
                for (int band = 0; band < 16; band++) {
                    if (distance > fade[band]) {
                        red = (red * fogd + cfade[0]) / (fogd + 1);
                        green = (green * fogd + cfade[1]) / (fogd + 1);
                        blue = (blue * fogd + cfade[2]) / (fogd + 1);
                    }
                }

                rd.setColor(Colors.of(red, green, blue));
                if (Precise.on) {
                    Precise.bind(screenX, screenY, EXACT_X, EXACT_Y, 6, 30_000_000 + cloud * 100 + side);
                }
                rd.fillPolygon(screenX, screenY, 6);
            }

            for (int side = 0; side < 12; side += 2) {
                above = 0;
                below = 0;
                leftOf = 0;
                rightOf = 0;
                onScreen = true;
                midY = 0;
                midX = 0;
                midZ = 0;
                for (int vertex = 0; vertex < 6; vertex++) {
                    int point = 0;
                    int ring = 0;
                    if (vertex == 0) {
                        point = side;
                    }
                    if (vertex == 1) {
                        point = side + 1;
                        if (point >= 12) {
                            point -= 12;
                        }
                    }
                    if (vertex == 2) {
                        point = side + 2;
                        if (point >= 12) {
                            point -= 12;
                        }
                    }
                    if (vertex == 3) {
                        point = side + 2;
                        if (point >= 12) {
                            point -= 12;
                        }
                        ring = 1;
                    }
                    if (vertex == 4) {
                        point = side + 1;
                        if (point >= 12) {
                            point -= 12;
                        }
                        ring = 1;
                    }
                    if (vertex == 5) {
                        point = side;
                        ring = 1;
                    }
                    screenX[vertex] = Utility.xs(cloudX[ring][point], cloudZ[ring][point]);
                    screenY[vertex] = Utility.ys(cloudY[ring][point], cloudZ[ring][point], 1);
                    EXACT_X[vertex] = CLOUD_X[ring][point];
                    EXACT_Y[vertex] = CLOUD_Y[ring][point];
                    midX += cloudX[ring][point];
                    midY += cloudY[ring][point];
                    midZ += cloudZ[ring][point];
                    if (screenY[vertex] < -cullY || cloudZ[0][vertex] < 10) {
                        above++;
                    }
                    if (screenY[vertex] > h + cullY || cloudZ[0][vertex] < 10) {
                        below++;
                    }
                    if (screenX[vertex] < -cullX || cloudZ[0][vertex] < 10) {
                        leftOf++;
                    }
                    if (screenX[vertex] > w + cullX || cloudZ[0][vertex] < 10) {
                        rightOf++;
                    }
                }

                if (leftOf == 6 || above == 6 || below == 6 || rightOf == 6) {
                    onScreen = false;
                }
                if (!onScreen) {
                    continue;
                }
                midX /= 6;
                midY /= 6;
                midZ /= 6;
                int distance = (int) Math.sqrt((cy - midY) * (cy - midY) + (cx - midX) * (cx - midX) + midZ * midZ);
                if (distance >= fade[7]) {
                    continue;
                }
                int red = clc[cloud][0][side / 2][0];
                int green = clc[cloud][0][side / 2][1];
                int blue = clc[cloud][0][side / 2][2];
                for (int band = 0; band < 16; band++) {
                    if (distance > fade[band]) {
                        red = (red * fogd + cfade[0]) / (fogd + 1);
                        green = (green * fogd + cfade[1]) / (fogd + 1);
                        blue = (blue * fogd + cfade[2]) / (fogd + 1);
                    }
                }

                rd.setColor(Colors.of(red, green, blue));
                if (Precise.on) {
                    Precise.bind(screenX, screenY, EXACT_X, EXACT_Y, 6, 40_000_000 + cloud * 100 + side);
                }
                rd.fillPolygon(screenX, screenY, 6);
            }

            above = 0;
            below = 0;
            leftOf = 0;
            rightOf = 0;
            onScreen = true;
            midY = 0;
            midX = 0;
            midZ = 0;
            for (int point = 0; point < 12; point++) {
                screenX[point] = Utility.xs(cloudX[0][point], cloudZ[0][point]);
                screenY[point] = Utility.ys(cloudY[0][point], cloudZ[0][point], 1);
                midX += cloudX[0][point];
                midY += cloudY[0][point];
                midZ += cloudZ[0][point];
                if (screenY[point] < -cullY || cloudZ[0][point] < 10) {
                    above++;
                }
                if (screenY[point] > h + cullY || cloudZ[0][point] < 10) {
                    below++;
                }
                if (screenX[point] < -cullX || cloudZ[0][point] < 10) {
                    leftOf++;
                }
                if (screenX[point] > w + cullX || cloudZ[0][point] < 10) {
                    rightOf++;
                }
            }

            if (leftOf == 12 || above == 12 || below == 12 || rightOf == 12) {
                onScreen = false;
            }
            if (!onScreen) {
                continue;
            }
            midX /= 12;
            midY /= 12;
            midZ /= 12;
            int distance = (int) Math.sqrt((cy - midY) * (cy - midY) + (cx - midX) * (cx - midX) + midZ * midZ);
            if (distance >= fade[7]) {
                continue;
            }
            int red = clds[0];
            int green = clds[1];
            int blue = clds[2];
            for (int band = 0; band < 16; band++) {
                if (distance > fade[band]) {
                    red = (red * fogd + cfade[0]) / (fogd + 1);
                    green = (green * fogd + cfade[1]) / (fogd + 1);
                    blue = (blue * fogd + cfade[2]) / (fogd + 1);
                }
            }

            rd.setColor(Colors.of(red, green, blue));
            if (Precise.on) {
                Precise.bind(screenX, screenY, CLOUD_X[0], CLOUD_Y[0], 12, 50_000_000 + cloud);
            }
            rd.fillPolygon(screenX, screenY, 12);
        }

    }

    public static void newmountains(int minX, int maxX, int minZ, int maxZ) {
        if (detailtype == 0 || detailtype == 1)
            return;
        Random random = new Random(mgen);
        nmt = (int) (20.0 + 10.0 * random.nextDouble());
        int centerX = (minX + maxX) / 60;
        int centerZ = (minZ + maxZ) / 60;
        int span = Math.max(maxX - minX, maxZ - minZ) / 60;
        mrd = null;
        nmv = null;
        mtx = null;
        mty = null;
        mtz = null;
        mtc = null;
        mrd = new int[nmt];
        nmv = new int[nmt];
        mtx = new int[nmt][];
        mty = new int[nmt][];
        mtz = new int[nmt][];
        mtc = new int[nmt][][];
        int[] distance = new int[nmt];
        int[] rank = new int[nmt];
        for (int mountain = 0; mountain < nmt; mountain++) {
            int green;
            float width;
            float height;
            distance[mountain] = (int) (10000.0 + random.nextDouble() * 10000.0);
            int turn = (int) (random.nextDouble() * 360.0);
            if (random.nextDouble() > random.nextDouble()) {
                width = (float) (0.2 + random.nextDouble() * 0.35);
                height = (float) (0.2 + random.nextDouble() * 0.35);
                nmv[mountain] = (int) (width * (24.0 + 16.0 * random.nextDouble()));
                green = (int) (85.0 + 10.0 * random.nextDouble());
            } else {
                width = (float) (0.3 + random.nextDouble() * 1.1);
                height = (float) (0.2 + random.nextDouble() * 0.35);
                nmv[mountain] = (int) (width * (12.0 + 8.0 * random.nextDouble()));
                green = (int) (104.0 - 10.0 * random.nextDouble());
            }
            mtx[mountain] = new int[nmv[mountain] * 2];
            mty[mountain] = new int[nmv[mountain] * 2];
            mtz[mountain] = new int[nmv[mountain] * 2];
            mtc[mountain] = new int[nmv[mountain]][3];
            for (int peak = 0; peak < nmv[mountain]; peak++) {
                mtx[mountain][peak] = (int) ((peak * 500 + (random.nextDouble() * 800.0 - 400.0)
                        - 250 * (nmv[mountain] - 1)) * width);
                mtx[mountain][peak + nmv[mountain]] = (int) ((peak * 500
                        + (random.nextDouble() * 800.0 - 400.0) - 250 * (nmv[mountain] - 1)) * width);
                mtx[mountain][nmv[mountain]] = (int) (mtx[mountain][0]
                        - (100.0 + random.nextDouble() * 600.0) * width);
                mtx[mountain][nmv[mountain] * 2 - 1] = (int) (mtx[mountain][nmv[mountain] - 1]
                        + (100.0 + random.nextDouble() * 600.0) * width);
                if (peak == 0 || peak == nmv[mountain] - 1)
                    mty[mountain][peak] = (int) ((-400.0 - 1200.0 * random.nextDouble()) * height
                            + ground);
                if (peak == 1 || peak == nmv[mountain] - 2)
                    mty[mountain][peak] = (int) ((-1000.0 - 1450.0 * random.nextDouble()) * height
                            + ground);
                if (peak > 1 && peak < nmv[mountain] - 2)
                    mty[mountain][peak] = (int) ((-1600.0 - 1700.0 * random.nextDouble()) * height
                            + ground);
                mty[mountain][peak + nmv[mountain]] = ground - 70;
                mtz[mountain][peak] = centerZ + span + distance[mountain];
                mtz[mountain][peak + nmv[mountain]] = centerZ + span + distance[mountain];
                float shade = (float) (0.5 + random.nextDouble() * 0.5);
                mtc[mountain][peak][0] = (int) (170.0F * shade + 170.0F * shade * (snap[0] / 100.0F));
                if (mtc[mountain][peak][0] > 255)
                    mtc[mountain][peak][0] = 255;
                if (mtc[mountain][peak][0] < 0)
                    mtc[mountain][peak][0] = 0;
                mtc[mountain][peak][1] = (int) (green * shade + 85.0F * shade * (snap[1] / 100.0F));
                if (mtc[mountain][peak][1] > 255)
                    mtc[mountain][peak][1] = 255;
                if (mtc[mountain][peak][1] < 1)
                    mtc[mountain][peak][1] = 0;
                mtc[mountain][peak][2] = 0;
            }
            for (int peak = 1; peak < nmv[mountain] - 1; peak++) {
                int before = peak - 1;
                int after = peak + 1;
                mty[mountain][peak] = ((mty[mountain][before] + mty[mountain][after]) / 2 + mty[mountain][peak]) / 2;
            }
            Utility.rot(mtx[mountain], mtz[mountain], centerX, centerZ, turn, nmv[mountain] * 2);
            rank[mountain] = 0;
        }
        for (int mountain = 0; mountain < nmt; mountain++) {
            for (int other = mountain + 1; other < nmt; other++) {
                if (distance[mountain] < distance[other])
                    rank[mountain]++;
                else
                    rank[other]++;
            }
            mrd[rank[mountain]] = mountain;
        }
    }

    private static void drawmountains(Graphics2D rd) {
        if (detailtype == 0 || detailtype == 1)
            return;
        for (int order = 0; order < nmt; order++) {
            int mountain = mrd[order];
            int leftX = cx + (int) ((mtx[mountain][0] - x / 30 - cx) * RadicalMath.cos(xz)
                    - (mtz[mountain][0] - z / 30 - cz) * RadicalMath.sin(xz));
            int leftViewZ = cz + (int) ((mtx[mountain][0] - x / 30 - cx) * RadicalMath.sin(xz)
                    + (mtz[mountain][0] - z / 30 - cz) * RadicalMath.cos(xz));
            int leftDepth = cz
                    + (int) ((mty[mountain][0] - y / 30 - cy) * RadicalMath.sin(zy) + (leftViewZ - cz) * RadicalMath.cos(zy));
            int rightX = cx + (int) ((mtx[mountain][nmv[mountain] - 1] - x / 30 - cx) * RadicalMath.cos(xz)
                    - (mtz[mountain][nmv[mountain] - 1] - z / 30 - cz) * RadicalMath.sin(xz));
            int rightViewZ = cz + (int) ((mtx[mountain][nmv[mountain] - 1] - x / 30 - cx) * RadicalMath.sin(xz)
                    + (mtz[mountain][nmv[mountain] - 1] - z / 30 - cz) * RadicalMath.cos(xz));
            int rightDepth = cz + (int) ((mty[mountain][nmv[mountain] - 1] - y / 30 - cy) * RadicalMath.sin(zy)
                    + (rightViewZ - cz) * RadicalMath.cos(zy));
            if (Utility.xs(rightX, rightDepth) > -cullX && Utility.xs(leftX, leftDepth) < w + cullX) {
                int[] pointX = new int[nmv[mountain] * 2];
                int[] pointY = new int[nmv[mountain] * 2];
                int[] pointZ = new int[nmv[mountain] * 2];
                for (int point = 0; point < nmv[mountain] * 2; point++) {
                    pointX[point] = mtx[mountain][point] - x / 30;
                    pointY[point] = mty[mountain][point] - y / 30;
                    pointZ[point] = mtz[mountain][point] - z / 30;
                }
                int distance = (int) Math.sqrt(pointX[nmv[mountain] / 4] * pointX[nmv[mountain] / 4]
                        + pointZ[nmv[mountain] / 4] * pointZ[nmv[mountain] / 4]);
                float[] mountainX = null;
                float[] mountainY = null;
                if (Precise.on) {
                    mountainX = new float[nmv[mountain] * 2];
                    mountainY = new float[nmv[mountain] * 2];
                    Precise.camera(pointX, pointY, pointZ, nmv[mountain] * 2, 30, 10, mountainX, mountainY);
                }
                Utility.rot(pointX, pointZ, cx, cz, xz, nmv[mountain] * 2);
                Utility.rot(pointY, pointZ, cy, cz, zy, nmv[mountain] * 2);
                int[] screenX = new int[4];
                int[] screenY = new int[4];
                boolean onScreen;
                for (int face = 0; face < nmv[mountain] - 1; face++) {
                    int above = 0;
                    int below = 0;
                    int leftOf = 0;
                    int rightOf = 0;
                    onScreen = true;
                    for (int corner = 0; corner < 4; corner++) {
                        int point = corner + face;
                        if (corner == 2)
                            point = face + nmv[mountain] + 1;
                        if (corner == 3)
                            point = face + nmv[mountain];
                        screenX[corner] = Utility.xs(pointX[point], pointZ[point]);
                        screenY[corner] = Utility.ys(pointY[point], pointZ[point], 1);
                        if (mountainX != null) {
                            EXACT_X[corner] = mountainX[point];
                            EXACT_Y[corner] = mountainY[point];
                        }
                        if (screenY[corner] < -cullY || pointZ[point] < 10)
                            above++;
                        if (screenY[corner] > h + cullY || pointZ[point] < 10)
                            below++;
                        if (screenX[corner] < -cullX || pointZ[point] < 10)
                            leftOf++;
                        if (screenX[corner] > w + cullX || pointZ[point] < 10)
                            rightOf++;
                    }
                    if (leftOf == 4 || above == 4 || below == 4 || rightOf == 4)
                        onScreen = false;
                    if (onScreen) {
                        float haze = distance / 2500.0F + (8000.0F - fade[0]) / 1000.0F - 2.0F
                                - (Math.abs(y) - 250.0F) / 5000.0F;
                        if (haze > 0.0F && haze < 10.0F) {
                            if (haze < 3.5)
                                haze = 3.5F;
                            int red = (int) ((mtc[mountain][face][0] + cgrnd[0] + csky[0] * haze
                                    + cfade[0] * haze) / (2.0F + haze * 2.0F));
                            int green = (int) ((mtc[mountain][face][1] + cgrnd[1] + csky[1] * haze
                                    + cfade[1] * haze) / (2.0F + haze * 2.0F));
                            int blue = (int) ((mtc[mountain][face][2] + cgrnd[2] + csky[2] * haze
                                    + cfade[2] * haze) / (2.0F + haze * 2.0F));
                            rd.setColor(Colors.of(red, green, blue)); // Mountain Color, should make this editable in the stage code
                            if (mountainX != null) {
                                Precise.bind(screenX, screenY, EXACT_X, EXACT_Y, 4, 60_000_000 + mountain * 1000 + face);
                            }
                            rd.fillPolygon(screenX, screenY, 4);
                        }
                    }
                }
            }
        }
    }

    public static void newstars() {
        if (detailtype == 0 || detailtype == 1)
            return;
        stx = null;
        stz = null;
        stc = null;
        bst = null;
        twn = null;
        nst = 0;
        if (lightson) {
            Random starRandom = new Random((long) (fxRandom.nextDouble() * 100000D));
            nst = 40;
            stx = new int[nst];
            stz = new int[nst];
            stc = new int[nst][2][3];
            bst = new boolean[nst];
            twn = new int[nst];
            for (int star = 0; star < nst; star++) {
                stx[star] = (int) (2000D * starRandom.nextDouble() - 1000D);
                stz[star] = (int) (2000D * starRandom.nextDouble() - 1000D);
                int strong = (int) (3D * starRandom.nextDouble());
                if (strong >= 3) {
                    strong = 0;
                }
                if (strong <= -1) {
                    strong = 2;
                }
                int tint = strong + 1;
                if (starRandom.nextDouble() > starRandom.nextDouble()) {
                    tint = strong - 1;
                }
                if (tint == 3) {
                    tint = 0;
                }
                if (tint == -1) {
                    tint = 2;
                }
                for (int channel = 0; channel < 3; channel++) {
                    stc[star][0][channel] = 200;
                    if (strong == channel) {
                        stc[star][0][channel] += (int) (55D * starRandom.nextDouble());
                    }
                    if (tint == channel) {
                        stc[star][0][channel] += 55;
                    }
                    stc[star][0][channel] = (stc[star][0][channel] * 2 + csky[channel]) / 3;
                    stc[star][1][channel] = (stc[star][0][channel] + csky[channel]) / 2;
                }

                twn[star] = (int) (4D * starRandom.nextDouble());
                bst[star] = starRandom.nextDouble() > 0.80000000000000004D;
            }

        }
    }

    private static void drawstars(Graphics2D rd) {
        if (detailtype == 0 || detailtype == 1)
            return;
        for (int star = 0; star < nst; star++) {
            int starX = cx + (int) (stx[star] * RadicalMath.cos(xz) - stz[star] * RadicalMath.sin(xz));
            int viewZ = cz + (int) (stx[star] * RadicalMath.sin(xz) + stz[star] * RadicalMath.cos(xz));
            int starY = cy + (int) (-200F * RadicalMath.cos(zy) - viewZ * RadicalMath.sin(zy));
            int depth = cz + (int) (-200F * RadicalMath.sin(zy) + viewZ * RadicalMath.cos(zy));
            starX = Utility.xs(starX, depth);
            starY = Utility.ys(starY, depth, 1);
            if (starX - 1 <= iw || starX + 3 >= w || starY - 1 <= ih || starY + 3 >= h) {
                continue;
            }
            if (twn[star] == 0) {
                int strong = (int) (3D * fxRandom.nextDouble());
                if (strong >= 3) {
                    strong = 0;
                }
                if (strong <= -1) {
                    strong = 2;
                }
                int tint = strong + 1;
                if (fxRandom.nextDouble() > fxRandom.nextDouble()) {
                    tint = strong - 1;
                }
                if (tint == 3) {
                    tint = 0;
                }
                if (tint == -1) {
                    tint = 2;
                }
                for (int channel = 0; channel < 3; channel++) {
                    stc[star][0][channel] = 200;
                    if (strong == channel) {
                        stc[star][0][channel] += (int) (55D * fxRandom.nextDouble());
                    }
                    if (tint == channel) {
                        stc[star][0][channel] += 55;
                    }
                    stc[star][0][channel] = (stc[star][0][channel] * 2 + csky[channel]) / 3;
                    stc[star][1][channel] = (stc[star][0][channel] + csky[channel]) / 2;
                }

                twn[star] = 3;
            } else {
                twn[star]--;
            }
            int big = 0;
            if (bst[star]) {
                big = 1;
            }
            rd.setColor(Colors.of(stc[star][1][0], stc[star][1][1], stc[star][1][2]));
            rd.fillRect(starX - 1, starY, 3 + big, 1 + big);
            rd.fillRect(starX, starY - 1, 1 + big, 3 + big);
            rd.setColor(Colors.of(stc[star][0][0], stc[star][0][1], stc[star][0][2]));
            rd.fillRect(starX, starY, 1 + big, 1 + big);
        }

    }

    /*
     * How far past the picture's edges (in 1280x720 pixels) the scene is
     * still drawn, and whether every sky and ground band is, even one out of
     * view: set while a scene is recorded once for a whole tick (see
     * RetainedScene), which the graphics card then turns and moves for each
     * frame, so what comes into view during the tick is already there. 0 and
     * false otherwise, and always for the race's own drawing step.
     */
    static int cullX;
    static int cullY;
    static boolean allBands;

    /* exact corners of scenery polygons, for the graphics card (see Precise); game thread only */
    private static final float[] EXACT_X = new float[16];
    private static final float[] EXACT_Y = new float[16];
    private static final float[][] CLOUD_X = new float[3][12];
    private static final float[][] CLOUD_Y = new float[3][12];

    /**
     * Gives a full-width band of the sky or ground ({@code bandX/bandY}: corner 0
     * at the edge just worked out, 1 and 2 at the edge before, 3 back at the
     * first) its exact edges {@code edge} and {@code before}.
     */
    private static void band(int[] bandX, int[] bandY, float edge, float before, int id) {
        EXACT_X[0] = iw;
        EXACT_Y[0] = edge;
        EXACT_X[1] = iw;
        EXACT_Y[1] = before;
        EXACT_X[2] = w;
        EXACT_Y[2] = before;
        EXACT_X[3] = w;
        EXACT_Y[3] = edge;
        Precise.bind(bandX, bandY, EXACT_X, EXACT_Y, 4, id);
    }

    public static void d(Graphics2D rd) {
        nsp = 0;
        clampView();
        if (Precise.on) {
            Precise.cameraAngles(); // after any clamp of the camera
        }

        float t_cos = RadicalMath.cos(zy);
        float t_sin = RadicalMath.sin(zy);

        ground = 250 - y;
        int bandX[] = new int[4];
        int bandY[] = new int[4];
        int red = cgrnd[0];
        int green = cgrnd[1];
        int blue = cgrnd[2];
        int farRed = crgrnd[0];
        int farGreen = crgrnd[1];
        int farBlue = crgrnd[2];
        int edgeBelow = h;
        float exactBelow = h; // the exact edge of the band drawn last, for the graphics card
        for (int fadeBand = 0; fadeBand < 16; fadeBand++) {
            int depth = fade[fadeBand];
            int viewY = ground;
            if (zy != 0) {
                viewY = cy + (int) ((ground - cy) * t_cos - (fade[fadeBand] - cz) * t_sin);
                depth = cz + (int) ((ground - cy) * t_sin + (fade[fadeBand] - cz) * t_cos);
            }
            bandX[0] = iw;
            bandY[0] = Utility.ys(viewY, depth, 1);
            if (bandY[0] < ih) {
                bandY[0] = ih;
            }
            if (bandY[0] > h) {
                bandY[0] = h;
            }
            bandX[1] = iw;
            bandY[1] = edgeBelow;
            bandX[2] = w;
            bandY[2] = edgeBelow;
            bandX[3] = w;
            bandY[3] = bandY[0];
            edgeBelow = bandY[0];
            float exactEdge = Precise.on ? Precise.horizon(ground, fade[fadeBand], true) : 0F;
            if (fadeBand > 0) {
                farRed = (farRed * 7 + cfade[0]) / 8;
                farGreen = (farGreen * 7 + cfade[1]) / 8;
                farBlue = (farBlue * 7 + cfade[2]) / 8;
                if (fadeBand < 3) {
                    red = (red * 7 + cfade[0]) / 8;
                    green = (green * 7 + cfade[1]) / 8;
                    blue = (blue * 7 + cfade[2]) / 8;
                } else {
                    red = farRed;
                    green = farGreen;
                    blue = farBlue;
                }
            }
            if (allBands || bandY[0] < h && bandY[1] > ih) {
                rd.setColor(Colors.of(red, green, blue));
                if (Precise.on) {
                    band(bandX, bandY, exactEdge, exactBelow, 1000 + fadeBand);
                }
                rd.fillPolygon(bandX, bandY, 4);
            }
            exactBelow = exactEdge;
        }

        if (lightn != -1 && lton) {
            if (lightn < 16) {
                if (lilo > lightn + 217) {
                    lilo -= 3;
                } else {
                    lightn = (int) (16F + 16F * fxRandom());
                }
            } else if (lilo < lightn + 217) {
                lilo += 7;
            } else {
                lightn = (int) (16F * fxRandom());
            }
            csky[0] = (int) (lilo + lilo * (snap[0] / 100F));
            if (csky[0] > 255) {
                csky[0] = 255;
            }
            if (csky[0] < 0) {
                csky[0] = 0;
            }
            csky[1] = (int) (lilo + lilo * (snap[1] / 100F));
            if (csky[1] > 255) {
                csky[1] = 255;
            }
            if (csky[1] < 0) {
                csky[1] = 0;
            }
            csky[2] = (int) (lilo + lilo * (snap[2] / 100F));
            if (csky[2] > 255) {
                csky[2] = 255;
            }
            if (csky[2] < 0) {
                csky[2] = 0;
            }
        }
        if (lightn != -1 && lton) {
            Glow.set((lilo - 217) * Glow.LIGHTNING_STEP); // the sky lit by the storm
        }
        red = csky[0];
        green = csky[1];
        blue = csky[2];
        int gradientRed = red;
        int gradientGreen = green;
        int gradientBlue = blue;
        int gradientEdge = cy + (int) ((skyline - 700 - cy) * t_cos - (7000 - cz) * t_sin);
        int gradientDepth = cz + (int) ((skyline - 700 - cy) * t_sin + (7000 - cz) * t_cos);
        gradientEdge = Utility.ys(gradientEdge, gradientDepth, 1);
        float exactGradient = Precise.on ? Precise.horizon(skyline - 700, 7000, false) : 0F;
        int edgeAbove = ih;
        float exactAbove = ih;
        for (int fadeBand = 0; fadeBand < 16; fadeBand++) {
            int depth = fade[fadeBand];
            int viewY = skyline;
            if (zy != 0) {
                viewY = cy + (int) ((skyline - cy) * t_cos - (fade[fadeBand] - cz) * t_sin);
                depth = cz + (int) ((skyline - cy) * t_sin + (fade[fadeBand] - cz) * t_cos);
            }
            bandX[0] = iw;
            bandY[0] = Utility.ys(viewY, depth, 1);
            if (bandY[0] > h) {
                bandY[0] = h;
            }
            if (bandY[0] < ih) {
                bandY[0] = ih;
            }
            bandX[1] = iw;
            bandY[1] = edgeAbove;
            bandX[2] = w;
            bandY[2] = edgeAbove;
            bandX[3] = w;
            bandY[3] = bandY[0];
            edgeAbove = bandY[0];
            float exactSky = Precise.on ? Precise.horizon(skyline, fade[fadeBand], false) : 0F;
            if (fadeBand > 0) {
                red = (red * 7 + cfade[0]) / 8;
                green = (green * 7 + cfade[1]) / 8;
                blue = (blue * 7 + cfade[2]) / 8;
            }
            if (bandY[1] < gradientEdge) {
                gradientRed = red;
                gradientGreen = green;
                gradientBlue = blue;
            }
            if (allBands || bandY[0] > ih && bandY[1] < h) {
                rd.setColor(Colors.of(red, green, blue));
                if (Precise.on) {
                    band(bandX, bandY, exactSky, exactAbove, 2000 + fadeBand);
                }
                rd.fillPolygon(bandX, bandY, 4);
            }
            exactAbove = exactSky;
        }

        bandX[0] = iw;
        bandY[0] = edgeAbove;
        bandX[1] = iw;
        bandY[1] = edgeBelow;
        bandX[2] = w;
        bandY[2] = edgeBelow;
        bandX[3] = w;
        bandY[3] = edgeAbove;
        if (allBands || bandY[0] < h && bandY[1] > ih) {
            float blend = (Math.abs(y) - 250F) / (fade[0] * 2);
            if (blend < 0.0F) {
                blend = 0.0F;
            }
            if (blend > 1.0F) {
                blend = 1.0F;
            }
            red = (int) ((red * (1.0F - blend) + farRed * (1.0F + blend)) / 2.0F);
            green = (int) ((green * (1.0F - blend) + farGreen * (1.0F + blend)) / 2.0F);
            blue = (int) ((blue * (1.0F - blend) + farBlue * (1.0F + blend)) / 2.0F);
            rd.setColor(Colors.of(red, green, blue));
            if (Precise.on) {
                band(bandX, bandY, exactAbove, exactBelow, 3000);
            }
            rd.fillPolygon(bandX, bandY, 4);
        }
        if (resdown != 2) {
            for (int step = 1; step < 20; step++) {
                int depth = 7000;
                int viewY = skyline - 700 - step * 70;
                if (zy != 0 && step != 19) {
                    viewY = cy + (int) ((skyline - 700 - step * 70 - cy) * t_cos - (7000 - cz) * t_sin);
                    depth = cz + (int) ((skyline - 700 - step * 70 - cy) * t_sin + (7000 - cz) * t_cos);
                }
                bandX[0] = iw;
                if (step != 19) {
                    bandY[0] = Utility.ys(viewY, depth, 1);
                    if (bandY[0] > h) {
                        bandY[0] = h;
                    }
                    if (bandY[0] < ih) {
                        bandY[0] = ih;
                    }
                } else {
                    bandY[0] = ih;
                }
                float exactStep = 0F;
                if (Precise.on) {
                    exactStep = step != 19 ? Precise.horizon(skyline - 700 - step * 70, 7000, false) : ih;
                }
                bandX[1] = iw;
                bandY[1] = gradientEdge;
                bandX[2] = w;
                bandY[2] = gradientEdge;
                bandX[3] = w;
                bandY[3] = bandY[0];
                gradientEdge = bandY[0];
                gradientRed = (int) (gradientRed * 0.99099999999999999D);
                gradientGreen = (int) (gradientGreen * 0.99099999999999999D);
                gradientBlue = (int) (gradientBlue * 0.998D);
                if (allBands || bandY[1] > ih && bandY[0] < h) {
                    rd.setColor(Colors.of(gradientRed, gradientGreen, gradientBlue));
                    if (Precise.on) {
                        band(bandX, bandY, exactStep, exactGradient, 4000 + step);
                    }
                    rd.fillPolygon(bandX, bandY, 4);
                }
                exactGradient = exactStep;
            }

            Glow.off();
            if (lightson) {
                Glow.set(Glow.STAR);
                drawstars(rd);
                Glow.off();
            }
            drawmountains(rd);
            drawclouds(rd);
        }
        Glow.off();
        groundpolys(rd);
        if (noelec != 0) {
            noelec--;
        }
        if (cpflik) {
            cpflik = false;
        } else {
            cpflik = true;
            elecr = fxRandom() * 15F - 6F;
        }
    }

    public static void addSp(int x, int z, int radius) {
        if (nsp != 7) {
            spx[nsp] = x;
            spz[nsp] = z;
            sprad[nsp] = radius;
            nsp++;
        }
    }

    public static void setSnap(int red, int green, int blue) {
        snap[0] = red;
        snap[1] = green;
        snap[2] = blue;
    }

    public static void adjustFade(float tickMillis) {
        if (tickMillis < 15F) {
            fade[0] = (int) (origfade - 1000F * (15F - tickMillis));
            if (fade[0] < 3000) {
                fade[0] = 3000;
            }
            fadeFrom(fade[0]);
        } else if (fade[0] != origfade) {
            fade[0] += 500;
            if (fade[0] > origfade) {
                fade[0] = origfade;
            }
            fadeFrom(fade[0]);
        }
    }

    public static void setFade(int red, int green, int blue) {
        cfade[0] = (int) (red + red * (snap[0] / 100F));
        if (cfade[0] > 255) {
            cfade[0] = 255;
        }
        if (cfade[0] < 0) {
            cfade[0] = 0;
        }
        cfade[1] = (int) (green + green * (snap[1] / 100F));
        if (cfade[1] > 255) {
            cfade[1] = 255;
        }
        if (cfade[1] < 0) {
            cfade[1] = 0;
        }
        cfade[2] = (int) (blue + blue * (snap[2] / 100F));
        if (cfade[2] > 255) {
            cfade[2] = 255;
        }
        if (cfade[2] < 0) {
            cfade[2] = 0;
        }
    }

    public static void setSky(int red, int green, int blue) {
        osky[0] = red;
        osky[1] = green;
        osky[2] = blue;
        for (int channel = 0; channel < 3; channel++) {
            clds[channel] = (osky[channel] * cldd[3] + cldd[channel]) / (cldd[3] + 1);
            clds[channel] = (int) (clds[channel] + clds[channel] * (snap[channel] / 100F));
            if (clds[channel] > 255) {
                clds[channel] = 255;
            }
            if (clds[channel] < 0) {
                clds[channel] = 0;
            }
        }

        csky[0] = (int) (red + red * (snap[0] / 100F));
        if (csky[0] > 255) {
            csky[0] = 255;
        }
        if (csky[0] < 0) {
            csky[0] = 0;
        }
        csky[1] = (int) (green + green * (snap[1] / 100F));
        if (csky[1] > 255) {
            csky[1] = 255;
        }
        if (csky[1] < 0) {
            csky[1] = 0;
        }
        csky[2] = (int) (blue + blue * (snap[2] / 100F));
        if (csky[2] > 255) {
            csky[2] = 255;
        }
        if (csky[2] < 0) {
            csky[2] = 0;
        }
        float hsb[] = new float[3];
        Color.RGBtoHSB(csky[0], csky[1], csky[2], hsb);
    }

    public static void setClouds(int red, int green, int blue, int skyMix, int height) {
        if (noclouds)
            return;
        if (skyMix < 0) {
            skyMix = 0;
        }
        if (skyMix > 10) {
            skyMix = 10;
        }
        if (height < -1500) {
            height = -1500;
        }
        if (height > -500) {
            height = -500;
        }
        cldd[0] = red;
        cldd[1] = green;
        cldd[2] = blue;
        cldd[3] = skyMix;
        cldd[4] = height;
        for (int channel = 0; channel < 3; channel++) {
            clds[channel] = (osky[channel] * cldd[3] + cldd[channel]) / (cldd[3] + 1);
            clds[channel] = (int) (clds[channel] + clds[channel] * (snap[channel] / 100F));
            if (clds[channel] > 255) {
                clds[channel] = 255;
            }
            if (clds[channel] < 0) {
                clds[channel] = 0;
            }
        }
    }

    public static void setGround(int red, int green, int blue) {
        ogrnd[0] = red;
        ogrnd[1] = green;
        ogrnd[2] = blue;
        for (int channel = 0; channel < 3; channel++) {
            cpol[channel] = (ogrnd[channel] * texture[3] + texture[channel]) / (1 + texture[3]);
            cpol[channel] = (int) (cpol[channel] + cpol[channel] * (snap[channel] / 100F));
            if (cpol[channel] > 255) {
                cpol[channel] = 255;
            }
            if (cpol[channel] < 0) {
                cpol[channel] = 0;
            }
        }

        cgrnd[0] = (int) (red + red * (snap[0] / 100F));
        if (cgrnd[0] > 255) {
            cgrnd[0] = 255;
        }
        if (cgrnd[0] < 0) {
            cgrnd[0] = 0;
        }
        cgrnd[1] = (int) (green + green * (snap[1] / 100F));
        if (cgrnd[1] > 255) {
            cgrnd[1] = 255;
        }
        if (cgrnd[1] < 0) {
            cgrnd[1] = 0;
        }
        cgrnd[2] = (int) (blue + blue * (snap[2] / 100F));
        if (cgrnd[2] > 255) {
            cgrnd[2] = 255;
        }
        if (cgrnd[2] < 0) {
            cgrnd[2] = 0;
        }
        for (int channel = 0; channel < 3; channel++) {
            crgrnd[channel] = (int) ((cpol[channel] * 0.98999999999999999D + cgrnd[channel]) / 2D);
        }

    }

    public static void setTexture(int red, int green, int blue, int mix) {
        if (mix < 20) {
            mix = 20;
        }
        if (mix > 60) {
            mix = 60;
        }
        texture[0] = red;
        texture[1] = green;
        texture[2] = blue;
        texture[3] = mix;
        red = (ogrnd[0] * mix + red) / (1 + mix);
        green = (ogrnd[1] * mix + green) / (1 + mix);
        blue = (ogrnd[2] * mix + blue) / (1 + mix);
        cpol[0] = (int) (red + red * (snap[0] / 100F));
        if (cpol[0] > 255) {
            cpol[0] = 255;
        }
        if (cpol[0] < 0) {
            cpol[0] = 0;
        }
        cpol[1] = (int) (green + green * (snap[1] / 100F));
        if (cpol[1] > 255) {
            cpol[1] = 255;
        }
        if (cpol[1] < 0) {
            cpol[1] = 0;
        }
        cpol[2] = (int) (blue + blue * (snap[2] / 100F));
        if (cpol[2] > 255) {
            cpol[2] = 255;
        }
        if (cpol[2] < 0) {
            cpol[2] = 0;
        }
        for (int channel = 0; channel < 3; channel++) {
            crgrnd[channel] = (int) ((cpol[channel] * 0.98999999999999999D + cgrnd[channel]) / 2D);
        }

    }

    public static void setPolys(int red, int green, int blue) {
        cpol[0] = (int) (red + red * (snap[0] / 100F));
        if (cpol[0] > 255) {
            cpol[0] = 255;
        }
        if (cpol[0] < 0) {
            cpol[0] = 0;
        }
        cpol[1] = (int) (green + green * (snap[1] / 100F));
        if (cpol[1] > 255) {
            cpol[1] = 255;
        }
        if (cpol[1] < 0) {
            cpol[1] = 0;
        }
        cpol[2] = (int) (blue + blue * (snap[2] / 100F));
        if (cpol[2] > 255) {
            cpol[2] = 255;
        }
        if (cpol[2] < 0) {
            cpol[2] = 0;
        }
        for (int channel = 0; channel < 3; channel++) {
            crgrnd[channel] = (int) ((cpol[channel] * 0.98999999999999999D + cgrnd[channel]) / 2D);
        }

    }

    public static void fadeFrom(int distance) {
        if (distance > 8000) {
            distance = 8000;
        }
        for (int band = 1; band < 17; band++) {
            fade[band - 1] = (distance / 2) * (band + 1);
        }
    }

    public static void watch(ContO car, int angle) {
        if (flex != 0) {
            flex = 0;
        }
        if (td) {
            y = (int) (car.y - 300 - 1100F * cameraRandom.next());
            x = car.x + (int) (((car.x + 400) - car.x) * RadicalMath.cos(angle)
                    - ((car.z + 5000) - car.z) * RadicalMath.sin(angle));
            z = car.z + (int) (((car.x + 400) - car.x) * RadicalMath.sin(angle)
                    + ((car.z + 5000) - car.z) * RadicalMath.cos(angle));
            td = false;
        }
        int offset = 0;
        if (car.x - x - cx > 0) {
            offset = 180;
        }
        int targetYaw = -(int) (90 + offset
                + StrictMath.atan((double) (car.z - z) / (double) (car.x - x - cx)) / 0.017453292519943295D);
        offset = 0;
        if (car.y - y - cy < 0) {
            offset = -180;
        }
        int flatDistance = (int) Math.sqrt((car.z - z) * (car.z - z) + (car.x - x - cx) * (car.x - x - cx));
        int targetPitch = (int) (90 + offset - StrictMath.atan((double) flatDistance / (double) (car.y - y - cy)) / 0.017453292519943295D);
        xz += (targetYaw - xz) / trns;
        if (trns != 1) {
            trns--;
        }
        zy += (targetPitch - zy) / 5;
        if ((int) Math.sqrt((car.z - z) * (car.z - z) + (car.x - x - cx) * (car.x - x - cx)
                + (car.y - y - cy) * (car.y - y - cy)) > 6000) {
            td = true;
        }
    }

    public static void around(ContO car, boolean closingIn) {
        if (flex != 0) {
            flex = 0;
        }
        if (!closingIn) {
            if (!vert) {
                adv += 2;
            } else {
                adv -= 2;
            }
            if (adv > 900) {
                vert = true;
            }
            if (adv < 500) {
                vert = false;
            }
        } else {
            adv -= 14;
        }
        int reach = 500 + adv;
        if (closingIn && reach < 1300) {
            reach = 1300;
        }
        if (reach < 1000) {
            reach = 1000;
        }
        y = car.y - adv;
        if (y > 10) {
            vert = false;
        }
        x = car.x + (int) ((car.x - reach - car.x) * RadicalMath.cos(vxz));
        z = car.z + (int) ((car.x - reach - car.x) * RadicalMath.sin(vxz));
        if (!closingIn) {
            vxz += 2;
        } else {
            vxz += 4;
        }
        /*
         * offset is -180 when the camera is lower than the car, so the pitch turns
         * up toward it. The decompiler declared these offsets as char, which
         * turned -180 into 65356 and tipped the camera straight down instead;
         * watch(), getaround() and transaround() had the same bug.
         */
        int offset = 0;
        int eyeY = y;
        if (eyeY > 0) {
            eyeY = 0;
        }
        if (car.y - eyeY - cy < 0) {
            offset = -180;
        }
        int flatDistance = (int) Math.sqrt(((car.z - z) + cz) * ((car.z - z) + cz) + (car.x - x - cx) * (car.x - x - cx));
        int targetPitch = (int) (90 + offset - StrictMath.atan((double) flatDistance / (double) (car.y - eyeY - cy)) / 0.017453292519943295D);
        xz = -vxz + 90;
        if (closingIn) {
            targetPitch -= 15;
        }
        zy += (targetPitch - zy) / 10;
        if (trns != 5) {
            trns = 5;
        }
    }

    public static void aroundTrack(CheckPoints checkpoints) {
        if (flex != 0) {
            flex = 0;
        }
        y = -hit;
        x = cx + (int) trx + (int) (17000F * RadicalMath.cos(vxz));
        z = (int) trz + (int) (17000F * RadicalMath.sin(vxz));
        if (hit > 5000) {
            if (hit == 45000) {
                fo = 1.0F;
                zy = 67;
                atrx = (checkpoints.x[0] - trx) / 116L;
                atrz = (checkpoints.z[0] - trz) / 116L;
                focus_point = 400;
            }
            hit -= fallen;
            fallen += 7;
            trx += atrx;
            trz += atrz;
            if (hit < 17600) {
                zy -= 2;
            }
            if (fallen > 500) {
                fallen = 500;
            }
            if (hit <= 5000) {
                hit = 5000;
                fallen = 0;
            }
            vxz += 3;
        } else {
            focus_point = (int) (400F * fo);
            if (Math.abs(fo - gofo) > 0.0050000000000000001D) {
                if (fo < gofo) {
                    fo += 0.005F;
                } else {
                    fo -= 0.005F;
                }
            } else {
                gofo = (float) (0.34999999403953552D + fxRandom.nextDouble() * 1.3D);
            }
            vxz++;
            trx -= (trx - checkpoints.x[ptr]) / 10L;
            trz -= (trz - checkpoints.z[ptr]) / 10L;
            if (ptcnt == 7) {
                ptr++;
                if (ptr == checkpoints.n) {
                    ptr = 0;
                    nrnd++;
                }
                ptcnt = 0;
            } else {
                ptcnt++;
            }
        }
        if (vxz > 360) {
            vxz -= 360;
        }
        xz = -vxz - 90;
        Math.sqrt(((trz - z) + cz) * ((trz - z) + cz)
                + (trx - x - cx) * (trx - x - cx));
        cpflik = !cpflik;
    }

    public static void follow(ContO car, int heading, int look) {
        zy = 10;
        int turnStep = 2 + Math.abs(bcxz) / 4;
        if (turnStep > 20) {
            turnStep = 20;
        }
        if (look != 0) {
            if (look == 1) {
                if (bcxz < 180) {
                    bcxz += turnStep;
                }
                if (bcxz > 180) {
                    bcxz = 180;
                }
            }
            if (look == -1) {
                if (bcxz > -180) {
                    bcxz -= turnStep;
                }
                if (bcxz < -180) {
                    bcxz = -180;
                }
            }
        } else if (Math.abs(bcxz) > turnStep) {
            if (bcxz > 0) {
                bcxz -= turnStep;
            } else {
                bcxz += turnStep;
            }
        } else if (bcxz != 0) {
            bcxz = 0;
        }
        heading += bcxz;
        xz = -heading;
        x = (car.x - cx) + (int) ((-(car.z - 800 - car.z)) * RadicalMath.sin(heading));
        z = (car.z - cz) + (int) ((car.z - 800 - car.z) * RadicalMath.cos(heading));
        y = car.y - 250 - cy;
        if (trns != 1) {
            trns = 1;
        }
    }

    public static void getaround(ContO car) {
        if (!vert) {
            adv += 2;
        } else {
            adv -= 2;
        }
        if (adv > 1700) {
            vert = true;
        }
        if (adv < -500) {
            vert = false;
        }
        if (car.y - adv > 10) {
            vert = false;
        }
        int reach = 500 + adv;
        if (reach < 1000) {
            reach = 1000;
        }
        int goalY = car.y - adv;
        int goalX = car.x + (int) ((car.x - reach - car.x) * RadicalMath.cos(vxz));
        int goalZ = car.z + (int) ((car.x - reach - car.x) * RadicalMath.sin(vxz));
        int arrived = 0;
        if (Math.abs(goalY - y) > fvect) {
            if (y < goalY) {
                y += fvect;
            } else {
                y -= fvect;
            }
        } else {
            y = goalY;
            arrived++;
        }
        if (Math.abs(goalX - x) > fvect) {
            if (x < goalX) {
                x += fvect;
            } else {
                x -= fvect;
            }
        } else {
            x = goalX;
            arrived++;
        }
        if (Math.abs(goalZ - z) > fvect) {
            if (z < goalZ) {
                z += fvect;
            } else {
                z -= fvect;
            }
        } else {
            z = goalZ;
            arrived++;
        }
        if (arrived == 3) {
            fvect = 200;
        } else {
            fvect += 2;
        }
        for (vxz += 2; vxz > 360; vxz -= 360) {
        }
        int orbitYaw = -vxz + 90;
        int offset = 0;
        if (car.x - x - cx > 0) {
            offset = 180;
        }
        int lookYaw = -(int) (90 + offset
                + StrictMath.atan((double) (car.z - z) / (double) (car.x - x - cx)) / 0.017453292519943295D);
        int eyeY = y;
        offset = 0;
        if (eyeY > 0) {
            eyeY = 0;
        }
        if (car.y - eyeY - cy < 0) {
            offset = -180;
        }
        int flatDistance = (int) Math.sqrt(((car.z - z) + cz) * ((car.z - z) + cz) + (car.x - x - cx) * (car.x - x - cx));
        int targetPitch = 25;
        if (flatDistance != 0) {
            targetPitch = (int) (90 + offset
                    - StrictMath.atan((double) flatDistance / (double) (car.y - eyeY - cy)) / 0.017453292519943295D);
        }
        for (; orbitYaw < 0; orbitYaw += 360) {
        }
        for (; orbitYaw > 360; orbitYaw -= 360) {
        }
        for (; lookYaw < 0; lookYaw += 360) {
        }
        for (; lookYaw > 360; lookYaw -= 360) {
        }
        if ((Math.abs(orbitYaw - lookYaw) < 30 || Math.abs(orbitYaw - lookYaw) > 330) && arrived == 3) {
            if (Math.abs(orbitYaw - xz) > 7 && Math.abs(orbitYaw - xz) < 353) {
                if (Math.abs(orbitYaw - xz) > 180) {
                    if (xz > orbitYaw) {
                        xz += 7;
                    } else {
                        xz -= 7;
                    }
                } else if (xz < orbitYaw) {
                    xz += 7;
                } else {
                    xz -= 7;
                }
            } else {
                xz = orbitYaw;
            }
        } else if (Math.abs(lookYaw - xz) > 6 && Math.abs(lookYaw - xz) < 354) {
            if (Math.abs(lookYaw - xz) > 180) {
                if (xz > lookYaw) {
                    xz += 3;
                } else {
                    xz -= 3;
                }
            } else if (xz < lookYaw) {
                xz += 3;
            } else {
                xz -= 3;
            }
        } else {
            xz = lookYaw;
        }
        zy += (targetPitch - zy) / 10;
    }

    public static void transaround(ContO from, ContO to, int step) {
        if (flex != 0) {
            flex = 0;
        }
        int midX = (from.x * (20 - step) + to.x * step) / 20;
        int midY = (from.y * (20 - step) + to.y * step) / 20;
        int midZ = (from.z * (20 - step) + to.z * step) / 20;
        if (!vert) {
            adv += 2;
        } else {
            adv -= 2;
        }
        if (adv > 900) {
            vert = true;
        }
        if (adv < 500) {
            vert = false;
        }
        int reach = 500 + adv;
        if (reach < 1000) {
            reach = 1000;
        }
        y = midY - adv;
        if (y > 10) {
            vert = false;
        }
        x = midX + (int) ((midX - reach - midX) * RadicalMath.cos(vxz));
        z = midZ + (int) ((midX - reach - midX) * RadicalMath.sin(vxz));
        vxz += 2;
        int offset = 0;
        int eyeY = y;
        if (eyeY > 0) {
            eyeY = 0;
        }
        if (midY - eyeY - cy < 0) {
            offset = -180;
        }
        int flatDistance = (int) Math.sqrt(((midZ - z) + cz) * ((midZ - z) + cz) + (midX - x - cx) * (midX - x - cx));
        int targetPitch = (int) (90 + offset - StrictMath.atan((double) flatDistance / (double) (midY - eyeY - cy)) / 0.017453292519943295D);
        xz = -vxz + 90;
        zy += (targetPitch - zy) / 10;
        if (trns != 5) {
            trns = 5;
        }
    }
}
