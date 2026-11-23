package nfm.lit;

import java.awt.Graphics2D;
import nfm.lit.audio.SoundManager;
import nfm.lit.features.CarShape;
import nfm.lit.features.DrawQueue;
import nfm.lit.features.Features;
import nfm.lit.features.RaceView;

/**
 * Where the game meets its extras ({@link Features}): the race copied into a
 * {@link RaceView} after each real tick, the hits of the tick, and the
 * extras' drawing merged into the scene's back-to-front order. Each hook in
 * the game is one line; with every extra off each costs a check.
 * <p>
 * The race's classes are package-private here, so the extras, in their own
 * packages, only ever see the copy. Game thread only.
 */
final class FeatureBridge {

    /** How near the ground a wheel's lowest point is when it touches it. */
    private static final float CONTACT = 8F;
    private static final float DEGREES = (float) (Math.PI / 180D);

    private static final RaceView view = new RaceView();
    private static final DrawQueue queue = new DrawQueue();
    private static final FeatureCanvas canvas = new FeatureCanvas();
    private static final DrawQueue.Drawer DRAWER = FeatureBridge::drawItem;

    /** A stage was loaded: the race starts with its first tick. */
    private static boolean starting;
    /** True while a scene's objects are drawn with the extras in them. */
    static boolean drawing;
    /** True while run-ahead's tick ahead is drawn (renderAheadScene): there the last real tick is at share 0. */
    static boolean ahead;
    private static int cars;

    /*
     * The cars' own polygons, recoloured and reshaped by an extra while one
     * is on (Features.carLook): ContO says which race car and polygon it
     * draws, Plane asks for the colour and, for a bent wheel, the corners.
     */
    /** The race car whose polygons are being drawn ({@link ContO#carSlot}), or -1. */
    static int lookCar = -1;
    /** Which of its polygons. */
    static int lookPlane;
    /** After {@link #look}: -1 lit as the game would, 0 dark, 1 lit as a lamp. */
    static int lookLamp = -1;
    /** A bent polygon's corners after {@link #bend}. */
    static final int[] bentX = new int[64];
    static final int[] bentY = new int[64];
    static final int[] bentZ = new int[64];

    /* hits of the tick being played, handed over with the view */
    private static int hits;
    private static final int[] hitCar = new int[RaceView.MAX_HITS];
    private static final int[] hitWheel = new int[RaceView.MAX_HITS];
    private static final int[] hitAxis = new int[RaceView.MAX_HITS];
    private static final float[] hitForce = new float[RaceView.MAX_HITS];

    /* every touch of the tick (a wall, another car), the strongest push along each world axis per corner */
    private static final float[] touchX = new float[Precise.MAX_CARS * RaceView.WHEELS];
    private static final float[] touchZ = new float[Precise.MAX_CARS * RaceView.WHEELS];

    private FeatureBridge() {
    }

    /* ------------------------------------------------------------ the race */

    /**
     * A stage has been loaded for a race started from {@code seed};
     * {@code keepsFiles} if the extras may write their files (never in test
     * runs, recording or verifying).
     */
    static void stageLoaded(long seed, CheckPoints checkpoints, boolean keepsFiles) {
        Features.leftRace();
        view.keepsFiles = keepsFiles;
        starting = true;
        hits = 0;
        view.seed = seed;
        view.tick = 0;
        view.stage = checkpoints.stage;
        view.stageName = checkpoints.name == null ? "" : checkpoints.name;
        view.groundColor = Medium.cgrnd[0] << 16 | Medium.cgrnd[1] << 8 | Medium.cgrnd[2];
    }

    /** The game is in {@code phase}: out of the race (results, menus), the race's extras end. */
    static void phase(Phase phase) {
        switch (phase) {
            case INGAME, PAUSEMENU, PAUSETRIGGER, INSTANTREPLAY, NOTENOUGHREPLAYDATA, LOADSTAGE, LOADING, PREGAME,
                    PREGAMEMUSIC, AWAITLOADDISMISSAL, DRAWENVIRONMENT -> {
            }
            default -> Features.leftRace();
        }
    }

    /** A hit of {@code force} on car {@code car}'s corner {@code wheel}, along {@code axis} (0 x, 1 y, 2 z). */
    static void hit(int car, int wheel, int axis, float force) {
        if (hits == RaceView.MAX_HITS || SoundManager.isDiscarding()) {
            return; // a run-ahead tick is played again for real, and its hits with it
        }
        hitCar[hits] = car;
        hitWheel[hits] = wheel;
        hitAxis[hits] = axis;
        hitForce[hits++] = force;
    }

    /**
     * Car {@code car}'s corner {@code wheel} touched a wall or a car, pushed
     * {@code force} along world axis {@code axis} (0 x, 2 z): every touch,
     * however light, as the game measures it before deciding on a hit.
     */
    static void touch(int car, int wheel, int axis, float force) {
        int at = car * RaceView.WHEELS + wheel;
        if (at < 0 || at >= touchX.length || SoundManager.isDiscarding()) {
            return;
        }
        float[] pushes = axis == 0 ? touchX : touchZ;
        if (Math.abs(force) > Math.abs(pushes[at])) {
            pushes[at] = force;
        }
    }

    /** A real race tick has ended: the view is filled and handed to the extras. */
    static void afterTick(Madness[] racers, Control[] controls, ContO[] scene, Trackers trackers, int ticks) {
        if (!starting && !Features.anyActive() && !Features.anySwitchedOn()) {
            hits = 0;
            java.util.Arrays.fill(touchX, 0F);
            java.util.Arrays.fill(touchZ, 0F);
            return;
        }
        fill(racers, controls, scene, trackers, ticks);
        if (starting) {
            starting = false;
            view.dataFile = GameFiles::file;
            for (int c = 0; c < cars; c++) {
                shape(view.shape[c], scene[c]);
            }
            Features.raceStarted(view);
        }
        boolean looking = Features.carLook() != null;
        for (int c = 0; c < cars; c++) {
            scene[c].carSlot = looking ? c : -1;
        }
        Features.afterTick(view);
        hits = 0;
        java.util.Arrays.fill(touchX, 0F);
        java.util.Arrays.fill(touchZ, 0F);
    }

    private static void fill(Madness[] racers, Control[] controls, ContO[] scene, Trackers trackers, int ticks) {
        int n = Math.min(GameFacts.numberOfPlayers, racers.length);
        cars = n;
        view.fit(n);
        view.cars = n;
        view.humans = GameFacts.humans;
        view.tick = ticks;
        for (int c = 0; c < n; c++) {
            Madness m = racers[c];
            ContO o = scene[c];
            Control k = controls[c];
            view.model[c] = m.cn;
            view.modelName[c] = GameSparker.carModel(m.cn);
            view.x[c] = o.x;
            view.y[c] = o.y;
            view.z[c] = o.z;
            view.xz[c] = o.xz;
            view.xy[c] = o.xy;
            view.zy[c] = o.zy;
            view.steer[c] = o.wxz;
            view.speed[c] = m.speed;
            view.topSpeed[c] = m.stat.swits[2];
            view.vx[c] = (m.scx[0] + m.scx[1] + m.scx[2] + m.scx[3]) / 4F;
            view.vy[c] = (m.scy[0] + m.scy[1] + m.scy[2] + m.scy[3]) / 4F;
            view.vz[c] = (m.scz[0] + m.scz[1] + m.scz[2] + m.scz[3]) / 4F;
            view.damage[c] = m.stat.maxmag > 0 ? Math.min(1F, Math.max(0F, m.hitmag / (float) m.stat.maxmag)) : 0F;
            view.wasted[c] = m.dest;
            view.capsized[c] = m.capsized;
            view.up[c] = k.up;
            view.down[c] = k.down;
            view.left[c] = k.left;
            view.right[c] = k.right;
            view.handbrake[c] = k.handb;
            view.power[c] = m.power;
            view.radius[c] = o.maxR;
            view.wheelDrop[c] = o.grat;
            wheels(c, m, o, trackers);
            if (ticks <= 1) {
                view.airTicks[c] = 0;
            }
            view.airTicks[c] = view.grounded[c] ? 0 : view.airTicks[c] + 1;
        }
        int touched = Math.min(n * RaceView.WHEELS, touchX.length);
        java.util.Arrays.fill(view.touchX, 0F);
        java.util.Arrays.fill(view.touchZ, 0F);
        System.arraycopy(touchX, 0, view.touchX, 0, touched);
        System.arraycopy(touchZ, 0, view.touchZ, 0, touched);
        view.hits = hits;
        System.arraycopy(hitCar, 0, view.hitCar, 0, hits);
        System.arraycopy(hitWheel, 0, view.hitWheel, 0, hits);
        System.arraycopy(hitAxis, 0, view.hitAxis, 0, hits);
        System.arraycopy(hitForce, 0, view.hitForce, 0, hits);
    }

    /** Car {@code c}'s wheels: where each meets the ground, whether it touches, what it is on and how it slides. */
    private static void wheels(int c, Madness m, ContO o, Trackers t) {
        float sinR = (float) Math.sin(o.xy * DEGREES);
        float cosR = (float) Math.cos(o.xy * DEGREES);
        float sinP = (float) Math.sin(o.zy * DEGREES);
        float cosP = (float) Math.cos(o.zy * DEGREES);
        float sinY = (float) Math.sin(o.xz * DEGREES);
        float cosY = (float) Math.cos(o.xz * DEGREES);
        // the heading, as the game drives the car: (-sin, cos) of its yaw, tilted by its pitch
        float hx = -sinY * cosP;
        float hy = -sinP;
        float hz = cosY * cosP;
        float rx = cosY;
        float rz = sinY;
        boolean any = false;
        for (int w = 0; w < RaceView.WHEELS; w++) {
            int at = c * RaceView.WHEELS + w;
            float x = o.keyx[w];
            float y = o.grat;
            float z = o.keyz[w];
            float x1 = x * cosR - y * sinR; // roll
            float y1 = x * sinR + y * cosR;
            float y2 = y1 * cosP - z * sinP; // pitch
            float z2 = y1 * sinP + z * cosP;
            float x3 = x1 * cosY - z2 * sinY; // yaw
            float z3 = x1 * sinY + z2 * cosY;
            float wx = o.x + x3;
            float wy = o.y + y2;
            float wz = o.z + z3;
            view.keyX[at] = o.keyx[w];
            view.keyZ[at] = o.keyz[w];
            view.wheelX[at] = wx;
            view.wheelY[at] = wy;
            view.wheelZ[at] = wz;
            float ground = 250F;
            boolean sloped = false;
            int surface = 1; // off every piece: the stage's own ground, as the game has it
            int rgb = view.groundColor;
            for (int p = 0; p < t.nt; p++) {
                if (Math.abs(wx - t.x[p]) >= t.radx[p] || Math.abs(wz - t.z[p]) >= t.radz[p]
                        || Math.abs(t.zy[p]) == 90 || Math.abs(t.xy[p]) == 90) {
                    continue;
                }
                surface = t.skd[p];
                rgb = t.c[p][0] << 16 | t.c[p][1] << 8 | t.c[p][2];
                if (wy < t.y[p] - t.rady[p] - CONTACT || wy > t.y[p] + t.rady[p]) {
                    continue;
                }
                if (t.xy[p] == 0 && t.zy[p] == 0) {
                    if (t.y[p] != 250) {
                        ground = Math.min(ground, t.y[p]);
                    }
                } else {
                    sloped = true;
                }
            }
            boolean touching = m.wtouch && (sloped || wy > ground - CONTACT);
            view.contact[at] = touching;
            view.onRamp[at] = touching && sloped;
            view.surface[at] = surface;
            view.groundRgb[at] = rgb & 0xffffff;
            any |= touching;
            float vx = m.scx[w];
            float vy = m.scy[w];
            float vz = m.scz[w];
            view.wheelVX[at] = vx;
            view.wheelVZ[at] = vz;
            view.slipSide[at] = vx * rx + vz * rz;
            view.slipLong[at] = m.speed - (vx * hx + vy * hy + vz * hz);
        }
        view.grounded[c] = any;
    }

    /** Car model {@code o}'s polygons into {@code s}: corners, colours, glass, lights, wheels; the body's extent. */
    private static void shape(CarShape s, ContO o) {
        int corners = 0;
        for (int i = 0; i < o.npl; i++) {
            corners += o.p[i].n;
        }
        s.fit(o.npl, corners);
        float[] sumX = new float[RaceView.WHEELS];
        int[] pointsOf = new int[RaceView.WHEELS];
        float[] lowX = {Float.MAX_VALUE, Float.MAX_VALUE, Float.MAX_VALUE, Float.MAX_VALUE};
        float[] highX = {-Float.MAX_VALUE, -Float.MAX_VALUE, -Float.MAX_VALUE, -Float.MAX_VALUE};
        java.util.Arrays.fill(s.wheelRadius, 0F);
        java.util.Arrays.fill(s.steerX, 0);
        s.minX = s.minY = s.minZ = Float.MAX_VALUE;
        s.maxX = s.maxY = s.maxZ = -Float.MAX_VALUE;
        int at = 0;
        for (int i = 0; i < o.npl; i++) {
            Plane p = o.p[i];
            s.first[i] = at;
            s.count[i] = p.n;
            s.rgb[i] = (p.c[0] & 0xff) << 16 | (p.c[1] & 0xff) << 8 | p.c[2] & 0xff;
            s.glass[i] = p.glass;
            s.light[i] = p.light;
            int wheel = p.wz != 0 ? nearestWheel(o, p) : -1;
            s.wheel[i] = wheel;
            for (int k = 0; k < p.n; k++, at++) {
                s.x[at] = p.ox[k];
                s.y[at] = p.oy[k];
                s.z[at] = p.oz[k];
                if (wheel >= 0) {
                    sumX[wheel] += p.ox[k];
                    pointsOf[wheel]++;
                    lowX[wheel] = Math.min(lowX[wheel], p.ox[k]);
                    highX[wheel] = Math.max(highX[wheel], p.ox[k]);
                    s.hubY[wheel] = p.wy;
                    s.hubZ[wheel] = p.wz;
                    s.steerX[wheel] = p.wx;
                    s.wheelRadius[wheel] = Math.max(s.wheelRadius[wheel],
                            (float) Math.hypot(p.oy[k] - p.wy, p.oz[k] - p.wz));
                } else {
                    s.minX = Math.min(s.minX, p.ox[k]);
                    s.maxX = Math.max(s.maxX, p.ox[k]);
                    s.minY = Math.min(s.minY, p.oy[k]);
                    s.maxY = Math.max(s.maxY, p.oy[k]);
                    s.minZ = Math.min(s.minZ, p.oz[k]);
                    s.maxZ = Math.max(s.maxZ, p.oz[k]);
                }
            }
        }
        for (int w = 0; w < RaceView.WHEELS; w++) {
            s.hubX[w] = pointsOf[w] > 0 ? sumX[w] / pointsOf[w] : o.keyx[w];
            s.wheelHalfWidth[w] = pointsOf[w] > 0 ? (highX[w] - lowX[w]) / 2F : 0F;
        }
    }

    /** Which of {@code o}'s wheels wheel polygon {@code p} belongs to: the nearest to its hub. */
    private static int nearestWheel(ContO o, Plane p) {
        float mx = 0F;
        for (int k = 0; k < p.n; k++) {
            mx += p.ox[k];
        }
        mx /= Math.max(1, p.n);
        int best = 0;
        float bestDistance = Float.MAX_VALUE;
        for (int w = 0; w < RaceView.WHEELS; w++) {
            float d = Math.abs(o.keyx[w] - mx) + Math.abs(o.keyz[w] - p.wz);
            if (d < bestDistance) {
                bestDistance = d;
                best = w;
            }
        }
        return best;
    }

    /** The colour {@link #lookCar}'s polygon {@link #lookPlane} is drawn in (Plane.d, before the fog); sets {@link #lookLamp}. */
    static int look(int red, int green, int blue) {
        int rgb = Features.carColor(lookCar, lookPlane, red << 16 | green << 8 | blue);
        lookLamp = Features.carLamp();
        return rgb;
    }

    /** True if {@link #lookCar}'s polygon {@link #lookPlane} is drawn from other corners, then in {@link #bentX} etc. */
    static boolean bend(int[] x, int[] y, int[] z, int n) {
        return n <= bentX.length && Features.carBend(lookCar, lookPlane, x, y, z, n, bentX, bentY, bentZ);
    }

    /* ------------------------------------------------------------- drawing */

    /**
     * The scene's objects are about to be drawn into {@code target},
     * {@code share} of the way between the ticks shown: the extras say what
     * they draw in it.
     */
    static void beginScene(Graphics2D target, float share, ContO[] scene) {
        drawing = false;
        if (!Features.anyActive()) {
            return;
        }
        queue.begin(Medium.x + Medium.cx + Precise.camX, Medium.y + Medium.cy + Precise.camY,
                Medium.z + Precise.camZ, view.tick);
        Features.queue(queue);
        if (queue.isEmpty()) {
            return;
        }
        queue.sort();
        canvas.begin(target, share, ahead ? 0F : 1F, scene, cars);
        drawing = true;
    }

    /** Before the game draws {@code o}, last measured {@code dist} away. */
    static void beforeObject(int dist, ContO o) {
        queue.before(dist, o.flat(), DRAWER);
    }

    /** After the game drew object {@code index} ({@code o}). */
    static void afterObject(int index, ContO o) {
        if (index < cars && o.dist != 0) {
            queue.after(index, DRAWER);
        }
    }

    /** After the game's last object. */
    static void endObjects() {
        queue.end(DRAWER);
    }

    /** The scene is drawn. */
    static void endScene() {
        if (drawing) {
            drawing = false;
            canvas.end();
        }
    }

    private static void drawItem(int feature, int item) {
        canvas.startItem();
        Features.draw(feature, item, canvas);
        canvas.endItem();
    }
}
