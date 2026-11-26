package nfm.lit.features.realism;

import static nfm.lit.features.ExtrasCheck.check;
import static nfm.lit.features.ExtrasCheck.n;
import static nfm.lit.features.ExtrasCheck.waiting;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import nfm.lit.features.CarLooks;
import nfm.lit.features.CarShape;
import nfm.lit.features.CheckCanvas;
import nfm.lit.features.RaceView;

/**
 * Damage and heat's checks (realism, every requirement): a made-up car,
 * a box with a bonnet, a windscreen, side windows, lights and four wheels,
 * driven, braked, hit, scraped and dropped as each requirement's check
 * says, measuring what the extra would show and play.
 */
public final class RealismCheck {

    private static final String F = "damage and heat";
    private static final float GROUND = 250F;
    private static final int DROP = 10;
    private static final int[] KEY_X = {-18, 18, -18, 18};
    private static final int[] KEY_Z = {32, 32, -32, -32};
    private static final int BODY_RGB = 0xd0d0d0;
    private static final int BONNET_RGB = 0x3060c0;
    private static final int GLASS_RGB = 0x9ab8c8;
    private static final int REAR_LIGHT_RGB = 0x802020;
    private static final int FRONT_LIGHT_RGB = 0xd8d8b0;
    private static final int TYRE_RGB = 0x262626;
    private static final int BUMPER_RGB = 0x404248;
    /* polygons of the made-up car */
    private static final int BONNET = 0;
    private static final int ROOF = 2;
    private static final int LEFT_SIDE = 3;
    private static final int LEFT_WINDOW = 5;
    private static final int REAR_LIGHT = 10;
    private static final int FRONT_LIGHT = 12;
    private static final int FIRST_WHEEL = 14;
    /** Each wheel's face: six triangles from the rim to the hub, as the game's wheels have. */
    private static final int FIRST_RIM = FIRST_WHEEL + RaceView.WHEELS;
    private static final int RIM_RGB = 0x9098a0;
    /**
     * The game's cars' wheels, from their model files: the model's size and
     * width, its rims' size and depth, then each wheel's line (x, y, z,
     * steering, width, height). {@link #wheelsOf} builds them as the game
     * does.
     */
    private static final float[][] CAR_WHEELS = {
        {2.05F, 1F, 20, 20, -32, 9, 27, 11, 13, 10, 32, 9, 27, 11, -13, 10, -32, 9, -72, 0, 13, 10, 32, 9, -72, 0, -13, 10},
        {1.70F, 1.05F, 15, 30, -28, -8, 67, 11, 19, 10, 28, -8, 67, 11, -19, 10, -35, -8, -70, 0, 22, 12, 35, -8, -70, 0, -22, 12},
        {2.47F, 1F, 20, 30, -38, 6, 75, 11, 15, 13, 38, 6, 75, 11, -15, 13, -38, 6, -38, 2, 15, 13, 38, 6, -38, 2, -15, 13},
        {1.70F, 1F, 18, 20, -45, 3, 58, 11, 15, 11, 45, 3, 58, 11, -14, 11, -45, 3, -74, 3, 15, 11, 45, 3, -74, 3, -14, 11},
        {1.90F, 1F, 15, 50, -29, 8, 41, 11, 14, 9, 29, 8, 41, 11, -14, 9, -29, 8, -41, 0, 14, 9, 29, 8, -41, 0, -14, 9},
        {1.47F, 1F, 20, 0, -48, 6, 50, 11, 18, 15, 48, 6, 50, 11, -18, 15, -48, 6, -92, 0, 18, 15, 48, 6, -92, 0, -18, 15},
        {1.70F, 1F, 20, 30, -36, 18, 108, 11, 19, 13, 36, 18, 108, 11, -19, 13, -36, 18, -48, 0, 19, 13, 36, 18, -48, 0, -19, 13},
        {1.80F, 1F, 19, 50, -37, 16, 84, 11, 14, 13, 37, 16, 84, 11, -14, 13, -35, 16, -40, 0, 14, 13, 35, 16, -40, 0, -14, 13},
        {1.57F, 1F, 21, 0, -46, -7, 64, 11, 19, 14, 46, -7, 64, 11, -19, 14, -46, -7, -60, 0, 19, 14, 46, -7, -60, 0, -19, 14},
        {1.70F, 1F, 12, 160, -41, 16, 60, 11, 19, 15, 41, 16, 60, 11, -19, 15, -41, 16, -50, 0, 19, 15, 41, 16, -50, 0, -19, 15},
        {1.39F, 1F, 20, 17, -47, 2, 64, 11, 17, 15, 47, 2, 64, 11, -17, 15, -47, 2, -86, 0, 17, 15, 47, 2, -86, 0, -17, 15},
        {1.90F, 1F, 25, 60, -52, 48, 80, 11, 22, 16, 52, 48, 80, 11, -22, 16, -52, 48, -97, 0, 22, 16, 52, 48, -97, 0, -22, 16},
        {1.60F, 1F, 24, 10, -48, 20, 67, 11, 19, 14, 48, 20, 67, 11, -19, 14, -48, 20, -67, 0, 19, 14, 48, 20, -67, 0, -19, 14},
        {2.95F, 1.17F, 27, 30, -27, 34, 66, 11, 20, 15, 27, 34, 66, 11, -20, 15, -23, 34, -24, 0, 20, 15, 23, 34, -24, 0, -20, 15},
        {1.60F, 1F, 19, -20, -42, 2, 72, 11, 17, 12, 42, 2, 72, 11, -17, 12, -42, 2, -84, 0, 17, 12, 42, 2, -84, 0, -17, 12},
        {1.82F, 1F, 21, 10, -41, 0, 50, 11, 35, 20, 41, 0, 50, 11, -35, 20, -41, 0, -71, 0, 35, 20, 41, 0, -71, 0, -35, 20},
    };
    /** The game's cars, as in its car list (GameSparker.carModels). */
    private static final String[] CARS = {"2000tornados", "formula7", "canyenaro", "lescrab", "nimi", "maxrevenge",
        "leadoxide", "koolkat", "drifter", "policecops", "mustang", "king", "audir8", "masheen", "radicalone",
        "drmonster"};

    private RaceView v;
    private RealismFeature real;
    private CarState s;
    private float carZ;
    private final List<String> heard = new ArrayList<>();
    private final CheckCanvas canvas = new CheckCanvas();
    private final float[] p = new float[3];

    private RealismCheck() {
    }

    public static void run() {
        RealismCheck c = new RealismCheck();
        c.cosmeticOnly();
        c.discs();
        c.discInside();
        c.radiator();
        c.sidePods();
        c.smoke();
        c.exhaust();
        c.pops();
        c.ticking();
        c.noBonnetNoBumper();
        c.repair();
        c.spikes();
        c.glass();
        c.wheels();
        c.dirt();
        c.scratches();
        c.landing();
        c.debris();
        c.lights();
        c.haze();
        c.rattle();
        c.sheets();
        c.parts();
        CarLooks.keepDebris(null);
        CarLooks.clear(0);
    }

    /* ------------------------------------------------------------ the car */

    /** The made-up car: a box 40 wide, 100 long, 20 high, a bonnet ahead of a sloped windscreen, wheels of radius 8. */
    static CarShape box(boolean rearLights) {
        List<float[]> polys = new ArrayList<>();
        List<int[]> kinds = new ArrayList<>(); // colour, glass, light, wheel
        add(polys, kinds, BONNET_RGB, 0, 0, -1, -18, -12, 16, 18, -12, 16, 18, -12, 50, -18, -12, 50);
        add(polys, kinds, GLASS_RGB, 1, 0, -1, -16, -12, 15, 16, -12, 15, 14, -22, 0, -14, -22, 0);
        add(polys, kinds, BODY_RGB, 0, 0, -1, -16, -22, 0, 16, -22, 0, 16, -22, -30, -16, -22, -30);
        add(polys, kinds, BODY_RGB, 0, 0, -1, -20, -12, -50, -20, -12, 50, -20, -2, 50, -20, -2, -50);
        add(polys, kinds, BODY_RGB, 0, 0, -1, 20, -12, -50, 20, -12, 50, 20, -2, 50, 20, -2, -50);
        add(polys, kinds, GLASS_RGB, 1, 0, -1, -19, -21, -2, -19, -21, -28, -19, -13, -28, -19, -13, -2);
        add(polys, kinds, GLASS_RGB, 1, 0, -1, 19, -21, -2, 19, -21, -28, 19, -13, -28, 19, -13, -2);
        add(polys, kinds, BUMPER_RGB, 0, 0, -1, -20, -12, 50, 20, -12, 50, 20, -2, 50, -20, -2, 50);
        add(polys, kinds, BUMPER_RGB, 0, 0, -1, -20, -12, -50, 20, -12, -50, 20, -2, -50, -20, -2, -50);
        add(polys, kinds, BODY_RGB, 0, 0, -1, -18, -12, -30, 18, -12, -30, 18, -12, -50, -18, -12, -50);
        int rear = rearLights ? 2 : 0;
        add(polys, kinds, REAR_LIGHT_RGB, 0, rear, -1, -17, -10, -51, -11, -10, -51, -11, -7, -51, -17, -7, -51);
        add(polys, kinds, REAR_LIGHT_RGB, 0, rear, -1, 11, -10, -51, 17, -10, -51, 17, -7, -51, 11, -7, -51);
        add(polys, kinds, FRONT_LIGHT_RGB, 0, 1, -1, -17, -10, 51, -11, -10, 51, -11, -7, 51, -17, -7, 51);
        add(polys, kinds, FRONT_LIGHT_RGB, 0, 1, -1, 11, -10, 51, 17, -10, 51, 17, -7, 51, 11, -7, 51);
        for (int w = 0; w < RaceView.WHEELS; w++) {
            float x = KEY_X[w] + (KEY_X[w] < 0 ? -2F : 2F);
            float[] c = new float[24];
            for (int k = 0; k < 8; k++) {
                double a = k * Math.PI / 4D;
                c[k * 3] = x;
                c[k * 3 + 1] = 2F + (float) Math.sin(a) * 8F;
                c[k * 3 + 2] = KEY_Z[w] + (float) Math.cos(a) * 8F;
            }
            polys.add(c);
            kinds.add(new int[] {TYRE_RGB, 0, 0, w});
        }
        for (int w = 0; w < RaceView.WHEELS; w++) {
            // the wheel's face, a touch dished: six triangles from the rim's corners to the hub
            float out = KEY_X[w] < 0 ? -1F : 1F;
            float face = KEY_X[w] + out * 2F;
            for (int k = 0; k < 6; k++) {
                double a = k * Math.PI / 3D;
                double b = (k + 1) * Math.PI / 3D;
                add(polys, kinds, RIM_RGB, 0, 0, w,
                        face, 2F + (float) Math.sin(a) * 6F, KEY_Z[w] + (float) Math.cos(a) * 6F,
                        face, 2F + (float) Math.sin(b) * 6F, KEY_Z[w] + (float) Math.cos(b) * 6F,
                        face - out, 2F, KEY_Z[w]);
            }
        }
        CarShape shape = new CarShape();
        fill(shape, polys, kinds);
        for (int w = 0; w < RaceView.WHEELS; w++) {
            shape.hubX[w] = KEY_X[w] + (KEY_X[w] < 0 ? -2F : 2F);
            shape.hubY[w] = 2F;
            shape.hubZ[w] = KEY_Z[w];
            shape.wheelRadius[w] = 8F;
            shape.wheelHalfWidth[w] = 2F;
            shape.steerX[w] = w < 2 ? KEY_X[w] : 0;
        }
        return shape;
    }

    /** The polygons into {@code shape}: corners, colours, what each is, the body's extent. */
    private static void fill(CarShape shape, List<float[]> polys, List<int[]> kinds) {
        int corners = 0;
        for (float[] c : polys) {
            corners += c.length / 3;
        }
        shape.fit(polys.size(), corners);
        shape.minX = shape.minY = shape.minZ = Float.MAX_VALUE;
        shape.maxX = shape.maxY = shape.maxZ = -Float.MAX_VALUE;
        int at = 0;
        for (int i = 0; i < polys.size(); i++) {
            float[] c = polys.get(i);
            int[] k = kinds.get(i);
            shape.first[i] = at;
            shape.count[i] = c.length / 3;
            shape.rgb[i] = k[0];
            shape.glass[i] = k[1] != 0;
            shape.light[i] = k[2];
            shape.wheel[i] = k[3];
            for (int j = 0; j < c.length / 3; j++, at++) {
                shape.x[at] = c[j * 3];
                shape.y[at] = c[j * 3 + 1];
                shape.z[at] = c[j * 3 + 2];
                if (k[3] < 0) {
                    shape.minX = Math.min(shape.minX, shape.x[at]);
                    shape.maxX = Math.max(shape.maxX, shape.x[at]);
                    shape.minY = Math.min(shape.minY, shape.y[at]);
                    shape.maxY = Math.max(shape.maxY, shape.y[at]);
                    shape.minZ = Math.min(shape.minZ, shape.z[at]);
                    shape.maxZ = Math.max(shape.maxZ, shape.z[at]);
                }
            }
        }
    }

    private static void add(List<float[]> polys, List<int[]> kinds, int rgb, int glass, int light, int wheel,
            float... corners) {
        polys.add(corners);
        kinds.add(new int[] {rgb, glass, light, wheel});
    }

    private void start() {
        start(CarSheet.DEFAULT, true);
    }

    private void start(CarSheet sheet, boolean rearLights) {
        v = new RaceView();
        v.fit(1);
        v.cars = 1;
        v.humans = 1;
        v.seed = 11L;
        v.groundColor = 0x7a8a50;
        v.topSpeed[0] = 100F;
        v.wheelDrop[0] = DROP;
        v.radius[0] = 60;
        v.shape[0] = box(rearLights);
        for (int w = 0; w < RaceView.WHEELS; w++) {
            v.keyX[w] = KEY_X[w];
            v.keyZ[w] = KEY_Z[w];
        }
        carZ = 0F;
        place(0F, 0F, true);
        real = new RealismFeature();
        heard.clear();
        real.ears = (car, clip, gain, pitch) -> heard.add(clip);
        real.raceStarted(v);
        s = real.states[0];
        s.parts.build(v.shape[0], sheet, DROP);
    }

    /** The car on the flat ground at length {@code carZ}, heading along z, its engine at {@code sp}, moving {@code vz} a tick. */
    private void place(float sp, float vz, boolean onGround) {
        v.x[0] = 0F;
        v.y[0] = GROUND - DROP;
        v.z[0] = carZ;
        v.xz[0] = 0;
        v.xy[0] = 0;
        v.zy[0] = 0;
        v.speed[0] = sp;
        v.vx[0] = 0F;
        v.vy[0] = 0F;
        v.vz[0] = vz;
        for (int w = 0; w < RaceView.WHEELS; w++) {
            v.toWorld(0, KEY_X[w], DROP, KEY_Z[w], p);
            v.wheelX[w] = p[0];
            v.wheelY[w] = p[1];
            v.wheelZ[w] = p[2];
            v.contact[w] = onGround;
            v.surface[w] = 0;
            v.groundRgb[w] = v.groundColor;
        }
    }

    /** One tick driving at engine speed {@code sp} (moving 0.4 of it a tick), the keys as given. */
    private void drive(float sp, boolean up, boolean down) {
        float vz = sp * 0.4F;
        carZ += vz;
        place(sp, vz, true);
        v.up[0] = up;
        v.down[0] = down;
        tick();
    }

    private void tick() {
        v.tick++;
        boolean any = false;
        for (int w = 0; w < RaceView.WHEELS; w++) {
            any |= v.contact[w];
        }
        v.grounded[0] = any;
        v.airTicks[0] = any ? 0 : v.airTicks[0] + 1;
        real.afterTick(v);
        v.hits = 0;
        v.rams = 0;
        Arrays.fill(v.touchX, 0F);
        Arrays.fill(v.touchZ, 0F);
    }

    /** A hit on corner {@code wheel} along world axis {@code axis} (0 x, 2 z), as the game reports it. */
    private void hit(int wheel, int axis, float force) {
        v.hitCar[v.hits] = 0;
        v.hitWheel[v.hits] = wheel;
        v.hitAxis[v.hits] = axis;
        v.hitForce[v.hits++] = force;
    }

    private int frame(float x, float y, float z) {
        return canvas.frame(real, x, y, z, v.tick);
    }

    /** Live particles of colour {@code rgb}. */
    private int particles(int rgb) {
        int n = 0;
        for (int i = 0; i < real.particles.capacity(); i++) {
            if (real.particles.color(i) == rgb) {
                n++;
            }
        }
        return n;
    }

    /** Live sparks within {@code reach} of world point {@code (x, z)} across the ground. */
    private int sparksNear(float x, float z, float reach) {
        int n = 0;
        for (int i = 0; i < real.particles.capacity(); i++) {
            if (real.particles.isSpark(i) && real.particles.position(i, p)
                    && Math.hypot(p[0] - x, p[2] - z) < reach) {
                n++;
            }
        }
        return n;
    }

    /** The mean place of the live particles of colour {@code rgb} in the car's own frame (the car heads along z). */
    private float[] meanLocal(int rgb) {
        float[] m = new float[4];
        for (int i = 0; i < real.particles.capacity(); i++) {
            if (real.particles.color(i) == rgb && real.particles.position(i, p)) {
                m[0] += p[0] - v.x[0];
                m[1] += p[1] - v.y[0];
                m[2] += p[2] - v.z[0];
                m[3]++;
            }
        }
        if (m[3] > 0F) {
            m[0] /= m[3];
            m[1] /= m[3];
            m[2] /= m[3];
        }
        return m;
    }

    private int heardOf(String clip) {
        int n = 0;
        for (String h : heard) {
            if (h.equals(clip)) {
                n++;
            }
        }
        return n;
    }

    /* ------------------------------------------------------------ the checks */

    /** Damage and heat are cosmetic only: the race it is handed is the same after it has read it. */
    private void cosmeticOnly() {
        start();
        drive(60F, true, false);
        hit(0, 2, -200F);
        v.touchX[0] = 30F;
        float[] before = {v.x[0], v.y[0], v.z[0], v.speed[0], v.vz[0], v.damage[0], v.hitForce[0], v.touchX[0]};
        real.afterTick(v);
        float[] after = {v.x[0], v.y[0], v.z[0], v.speed[0], v.vz[0], v.damage[0], v.hitForce[0], v.touchX[0]};
        check(F, "cosmetic only", Arrays.equals(before, after) && s.stars > 0,
                "the race view is the same after a tick with a heavy hit; in the game, test\\verify-races.ps1 "
                        + "-Features realism plays every recorded race with it on");
    }

    /** Brake discs glow: hard stops from top speed heat the fronts more than the rears; it fades in seconds. */
    private void discs() {
        start();
        heatBrakes();
        float front = Math.max(s.brake[0], s.brake[1]);
        float rear = Math.max(s.brake[2], s.brake[3]);
        int drawn = wheelFaces(HeatEffects.discColor(s.brake[0]));
        float glow = canvas.maxGlow;
        int atSpeed = 0;
        while (Math.max(s.brake[0], s.brake[1]) > HeatEffects.VISIBLE && atSpeed < 2000) {
            drive(100F, true, false);
            atSpeed++;
        }
        start();
        heatBrakes();
        int standing = 0;
        while (Math.max(s.brake[0], s.brake[1]) > HeatEffects.VISIBLE && standing < 2000) {
            drive(0F, false, false);
            standing++;
        }
        boolean ok = front > 0.8F && front > rear * 1.4F && rear > 0.3F && drawn > 0 && glow > 0.3F
                && atSpeed >= 19 && atSpeed <= 6 * 19 && standing > atSpeed && standing <= 12 * 19;
        check(F, "brake discs glow", ok, "after four hard stops: fronts " + n(front) + ", rears " + n(rear)
                + " (shows from " + n(HeatEffects.VISIBLE) + "), a front disc glows in " + drawn
                + " openings of its wheel, glow " + n(glow)
                + "; gone in " + n(atSpeed / 19F) + " s at speed, " + n(standing / 19F) + " s standing");
    }

    /**
     * Draws what the extra draws on every face triangle of the car's wheels,
     * as the game asks for it after drawing each; returns the polygons drawn
     * in colour {@code rgb}.
     */
    private int wheelFaces(int rgb) {
        canvas.reset();
        CarShape shape = v.shape[0];
        for (int i = 0; i < shape.planes; i++) {
            if (shape.wheel[i] >= 0) {
                real.onPlane(0, i, shape.rgb[i], canvas);
            }
        }
        return canvas.drawn(rgb);
    }

    /**
     * The brake disc sits inside the wheel, on every car: the wheels of the
     * game's sixteen cars, built as the game builds them, each with hot
     * brakes. Every wheel shows its disc in six openings; every corner of an
     * opening and of a disc is on the wheel's face or inside it, within the
     * rim, and within its own triangle of the face, so from the side and
     * from behind none of it is outside the wheel. A hub that stood out of
     * the wheel (Sword of Justice's spike) is drawn flat, its point on the
     * hub.
     */
    private void discInside() {
        int wheels = 0;
        int discs = 0;
        int flat = 0;
        int proud = 0;
        float farthestOut = -Float.MAX_VALUE;
        float beyondRim = -Float.MAX_VALUE;
        float offTriangle = 0F;
        float proudest = 0F;
        StringBuilder wrong = new StringBuilder();
        int[] x = new int[3];
        int[] y = new int[3];
        int[] z = new int[3];
        int[] ox = new int[3];
        int[] oy = new int[3];
        int[] oz = new int[3];
        for (int car = 0; car < CAR_WHEELS.length; car++) {
            start();
            CarShape shape = wheelsOf(CAR_WHEELS[car]);
            v.shape[0] = shape;
            s.parts.build(shape, CarSheet.DEFAULT, DROP);
            Arrays.fill(s.brake, 1F);
            int hot = HeatEffects.discColor(1F);
            for (int w = 0; w < RaceView.WHEELS; w++) {
                wheels++;
                float out = s.parts.faceOut[w];
                float face = s.parts.faceX[w];
                proudest = Math.max(proudest, s.parts.hubProud[w]);
                proud += s.parts.hubProud[w] > 0F ? 1 : 0;
                int seen = 0;
                for (int i = 0; i < shape.planes; i++) {
                    if (shape.wheel[i] != w || s.parts.rimHub[i] < 0) {
                        continue;
                    }
                    int f = shape.first[i];
                    int hub = s.parts.rimHub[i];
                    corners(shape, i, x, y, z);
                    boolean bent = real.bend(0, i, x, y, z, 3, ox, oy, oz);
                    if (s.parts.hubProud[w] > 0F) {
                        flat += bent && (ox[hub] - face) * out <= 0F ? 1 : 0;
                    } else if (bent) {
                        wrong.append(' ').append(CARS[car]).append(" bent a true wheel");
                    }
                    canvas.reset();
                    canvas.keeping = true;
                    real.onPlane(0, i, RIM_RGB, canvas);
                    canvas.keeping = false;
                    float rim = (float) Math.hypot(shape.y[f + (hub + 1) % 3] - shape.hubY[w],
                            shape.z[f + (hub + 1) % 3] - shape.hubZ[w]);
                    for (int k = 0; k < canvas.kept.size(); k++) {
                        if (canvas.keptColor.get(k) == RIM_RGB) {
                            continue; // the hub's point, put back: the wheel's own, not the brake
                        }
                        if (canvas.keptColor.get(k) == hot && canvas.keptGlow.get(k) > 0.3F) {
                            seen++;
                        }
                        float[] c = canvas.kept.get(k);
                        for (int j = 0; j < c.length; j += 3) {
                            farthestOut = Math.max(farthestOut, (c[j] - face) * out);
                            beyondRim = Math.max(beyondRim, (float) Math.hypot(c[j + 1] - shape.hubY[w],
                                    c[j + 2] - shape.hubZ[w]) - rim);
                            offTriangle = Math.max(offTriangle, outside(shape, f, hub, c[j + 1], c[j + 2]));
                        }
                    }
                }
                discs += seen;
                if (seen != 6) {
                    wrong.append(' ').append(CARS[car]).append(" wheel ").append(w).append(": ").append(seen);
                }
            }
        }
        boolean ok = wheels == 64 && discs == 6 * wheels && wrong.isEmpty() && farthestOut <= 0.01F
                && beyondRim < 0F && offTriangle <= 0.01F && proud > 0 && flat == 6 * proud;
        check(F, "the brake disc sits inside the wheel", ok, CAR_WHEELS.length + " cars, " + wheels
                + " wheels, each disc seen in 6 openings (" + discs + "); the farthest any opening or disc reaches"
                + " out of its wheel's face " + n(farthestOut) + " (0 is on the face, less is inside), past its rim "
                + n(beyondRim) + ", out of its own triangle of the face " + n(offTriangle) + "; " + proud
                + " wheels whose hub stood out (by up to " + n(proudest) + ") drawn flat with the point on the hub, "
                + flat + " of their " + 6 * proud + " triangles"
                + wrong);
    }

    /**
     * How far point {@code (py, pz)} is outside face triangle {@code f}
     * (its hub corner {@code hub}) seen along the axle: 0 inside it.
     */
    private static float outside(CarShape shape, int f, int hub, float py, float pz) {
        float worst = 0F;
        for (int k = 0; k < 3; k++) {
            int a = f + (hub + k) % 3;
            int b = f + (hub + k + 1) % 3;
            int c = f + (hub + k + 2) % 3;
            float ey = shape.y[b] - shape.y[a];
            float ez = shape.z[b] - shape.z[a];
            float len = (float) Math.hypot(ey, ez);
            // which side of edge a-b the third corner is on is inside
            float side = ey * (shape.z[c] - shape.z[a]) - ez * (shape.y[c] - shape.y[a]);
            float d = (ey * (pz - shape.z[a]) - ez * (py - shape.y[a])) / Math.max(1e-3F, len);
            worst = Math.max(worst, side >= 0F ? -d : d);
        }
        return worst;
    }

    /**
     * The four wheels of a car as the game builds them from its model's
     * lines ({@code Wheels.make}: whole numbers, the face's six triangles
     * from the rim's corners to the hub, the tread across the tyre), as a
     * car's shape with no body.
     */
    static CarShape wheelsOf(float[] car) {
        List<float[]> polys = new ArrayList<>();
        List<int[]> kinds = new ArrayList<>();
        float size = car[0];
        float width = car[1];
        float rim = car[2] / 10F;
        float depth = car[3] / 10F;
        CarShape shape = new CarShape();
        for (int w = 0; w < RaceView.WHEELS; w++) {
            int at = 4 + w * 6;
            int x = (int) (car[at] * size * width);
            int y = (int) (car[at + 1] * size);
            int z = (int) (car[at + 2] * size);
            float widthScale = (int) (car[at + 4] * size * width) / 10F;
            float sizeScale = (int) (car[at + 5] * size) / 10F;
            int face = (int) (x - 4F * widthScale);
            int inner = (int) (x + 4F * widthScale);
            int hub = (int) (x - depth * widthScale);
            int[] ry = {y, (int) (y + 8.66D * rim), (int) (y + 8.66D * rim), y, (int) (y - 8.66D * rim),
                (int) (y - 8.66D * rim)};
            int[] rz = {(int) (z + 10F * rim), (int) (z + 5F * rim), (int) (z - 5F * rim), (int) (z - 10F * rim),
                (int) (z - 5F * rim), (int) (z + 5F * rim)};
            for (int k = 0; k < 6; k++) {
                int n = (k + 1) % 6;
                add(polys, kinds, RIM_RGB, 0, 0, w, face, ry[k], rz[k], face, ry[n], rz[n], hub, y, z);
            }
            int top = (int) (y - 12F * sizeScale);
            int bottom = (int) (y + 12F * sizeScale);
            int ahead = (int) (z + 5F * sizeScale);
            int behind = (int) (z - 5F * sizeScale);
            add(polys, kinds, TYRE_RGB, 0, 0, w, face, top, ahead, face, top, behind, inner, top, behind, inner, top, ahead);
            add(polys, kinds, TYRE_RGB, 0, 0, w, face, bottom, behind, face, bottom, ahead, inner, bottom, ahead, inner,
                    bottom, behind);
            shape.hubX[w] = x;
            shape.hubY[w] = y;
            shape.hubZ[w] = z;
            shape.wheelRadius[w] = 12F * sizeScale;
            shape.wheelHalfWidth[w] = Math.abs(4F * widthScale);
            shape.steerX[w] = car[at + 3] == 11F ? inner : 0;
        }
        fill(shape, polys, kinds);
        return shape;
    }

    private void heatBrakes() {
        for (int stop = 0; stop < 4; stop++) {
            for (int i = 0; i < 45; i++) {
                drive(100F * (1F - i / 45F), false, true);
            }
            if (stop < 3) {
                for (int i = 0; i < 70; i++) {
                    drive(100F * i / 70F, true, false);
                }
            }
        }
    }

    /** A crushed radiator steams: back over the bonnet while driving, up when stopped, thinning as it empties. */
    private void radiator() {
        start();
        drive(60F, true, false);
        hit(0, 2, -150F);
        drive(60F, true, false);
        boolean crushed = s.crush > 0F && s.leaking[CarParts.LEFT] && !s.leaking[CarParts.RIGHT];
        for (int t = 0; t < 20; t++) {
            drive(60F, true, false);
        }
        float[] moving = meanLocal(0xe9edf0);
        float behind = s.parts.radiatorZ[CarParts.LEFT] - moving[2];
        for (int t = 0; t < 40; t++) {
            drive(0F, false, false);
        }
        float[] stopped = meanLocal(0xe9edf0);
        float rise = s.parts.radiatorY[CarParts.LEFT] - stopped[1];
        float drift = Math.abs(stopped[2] - s.parts.radiatorZ[CarParts.LEFT])
                + Math.abs(stopped[0] - s.parts.radiatorX[CarParts.LEFT]);
        int early = particles(0xe9edf0);
        for (int t = 0; t < 1500; t++) {
            drive(0F, false, false);
        }
        int late = particles(0xe9edf0);
        boolean ok = crushed && moving[3] > 5 && behind > 8F && moving[0] < 0F && stopped[3] > 5 && rise > 3F * drift
                && s.coolant < 0.5F && late < early;
        check(F, "a crushed radiator steams", ok, "front-left hit: left leaks " + crushed + "; driving, the steam is "
                + n(behind) + " behind the corner on the left (x " + n(moving[0]) + "); stopped, it rises " + n(rise)
                + " and drifts " + n(drift) + "; puffs " + early + " then " + late + " as the coolant falls to "
                + n(s.coolant));
    }

    /** A mid-engined car's radiators are in the side pods: a side hit makes them steam, a front hit does not. */
    private void sidePods() {
        start(new CarSheet("mid", "V8", CarSheet.MID, CarSheet.SIDE_PODS, false, CarSheet.DISC, 2), true);
        drive(60F, true, false);
        hit(0, 2, -150F);
        drive(60F, true, false);
        float afterFront = s.crush;
        hit(1, 0, -150F);
        drive(60F, true, false);
        boolean ok = afterFront == 0F && s.crush > 0F && s.leaking[CarParts.RIGHT]
                && s.parts.radiatorZ[CarParts.RIGHT] < 0F;
        check(F, "side pods steam on a side hit", ok, "after a front hit crush " + n(afterFront)
                + ", after a right side hit " + n(s.crush) + ", from z " + n(s.parts.radiatorZ[CarParts.RIGHT]));
    }

    /** A damaged engine smokes: wisps, then blue-grey on throttle, then dark puffs, from where the engine is. */
    private void smoke() {
        start();
        int[] counts = new int[3];
        float[] damage = {0.58F, 0.78F, 0.95F};
        int[] colors = {0xbdbdbd, 0x7b8696, 0x2c2c2e};
        float[] where = null;
        for (int level = 0; level < 3; level++) {
            real.particles.clear();
            v.damage[0] = damage[level];
            for (int t = 0; t < 30; t++) {
                drive(0F, level > 0, false);
            }
            for (int k = 0; k < 3; k++) {
                counts[k] += k == level ? particles(colors[k]) : -particles(colors[k]) * 100;
            }
            if (level == 0) {
                where = meanLocal(colors[0]);
            }
        }
        float off = Math.abs(where[2] - s.parts.engine[2]);
        start(new CarSheet("mid", "V8", CarSheet.MID, CarSheet.SIDE_PODS, false, CarSheet.DISC, 2), true);
        v.damage[0] = 0.58F;
        for (int t = 0; t < 30; t++) {
            drive(0F, false, false);
        }
        float[] mid = meanLocal(colors[0]);
        // it takes some effort: a third of the car's damage and two hard rams on the engine's end smoke nothing
        start();
        v.damage[0] = 0.35F;
        hit(0, 2, -100F);
        drive(30F, true, false);
        hit(1, 2, -100F);
        for (int t = 0; t < 60; t++) {
            drive(30F, true, false);
        }
        int early = particles(colors[0]) + particles(colors[1]) + particles(colors[2]);
        check(F, "light damage does not smoke", early == 0, early + " puffs of smoke at 35% damage after two hard"
                + " rams on the engine's end (the engine hurt " + n(s.ram) + ", smoke from "
                + n(DamageEffects.WISPS_FROM) + ")");
        boolean ok = counts[0] > 0 && counts[1] > 0 && counts[2] > 0 && off < 6F && mid[2] < 0F;
        check(F, "a damaged engine smokes", ok, "wisps " + counts[0] + ", blue-grey on throttle " + counts[1]
                + ", dark puffs " + counts[2] + " (each alone at its level); " + n(off)
                + " from a front engine; a mid engine's at z " + n(mid[2]));
    }

    /** The exhaust: flames on lift-off at top speed, a blue flame with nitrous (red with two at once), a shimmer at idle. */
    private void exhaust() {
        start();
        for (int t = 0; t < 10; t++) {
            drive(100F, true, false);
        }
        drive(95F, false, false);
        boolean flames = s.flameReach[0] > 0F;
        real.particles.clear();
        frame(0F, GROUND - 20F, carZ - 400F);
        int orange = canvas.drawn(0xff8a22);
        for (int t = 0; t < 8; t++) {
            drive(95F, false, false);
        }
        boolean ends = s.flameReach[0] == 0F;
        v.nitrous[0] = true;
        drive(100F, true, false);
        real.particles.clear();
        frame(0F, GROUND - 20F, carZ - 400F);
        int blue = canvas.drawn(0x5f9cff);
        float blueLong = canvas.span(0x5f9cff);
        v.nitrousDouble[0] = true;
        drive(100F, true, false);
        real.particles.clear();
        frame(0F, GROUND - 20F, carZ - 400F);
        int red = canvas.drawn(0xff3a22);
        float redLong = canvas.span(0xff3a22);
        v.nitrousDouble[0] = false;
        v.nitrous[0] = false;
        start();
        for (int t = 0; t < 20; t++) {
            drive(0F, false, false);
        }
        float[] idle = meanLocal(0xdfe3e6);
        boolean ok = flames && orange > 0 && ends && blue > 0 && red > 0 && redLong > blueLong && idle[3] > 0
                && idle[2] < s.parts.rear + 6F;
        check(F, "the exhaust shows the engine's state", ok, "lift-off flames " + flames + " (" + orange
                + " polygons), gone after " + ends + "; nitrous flame " + blue + " blue polygons " + n(blueLong) + " long, two at once " + red
                + " red " + n(redLong) + " long; idle shimmer "
                + (int) idle[3] + " puffs at z " + n(idle[2]));
    }

    /**
     * Exhaust pops: a car whose sheet says it pops bangs as it lifts off at
     * high revs and pops while its flames last, each pop heard and seen as
     * a flash at a pipe in the same tick; a car that does not pop throws
     * its flames in silence, with no flash.
     */
    private void pops() {
        int[] heardPops = new int[2];
        int[] flashes = new int[2];
        int[] together = new int[2];
        int[] flashPolygons = new int[2];
        boolean[] first = new boolean[2];
        boolean[] late = new boolean[2];
        for (int kind = 0; kind < 2; kind++) {
            start(CarSheets.parseLine(kind == 0 ? "popper V8 front front no disc 2 pops=yes"
                    : "silent V8 front front no disc 2"), true);
            // several lift-offs, so the dice have their say
            for (int lift = 0; lift < 6; lift++) {
                for (int t = 0; t < 10; t++) {
                    drive(100F, true, false);
                }
                for (int t = 0; t < 14; t++) {
                    heard.clear();
                    drive(95F, false, false);
                    boolean pop = heard.contains(RealismSounds.POP) || heard.contains(RealismSounds.BACKFIRE);
                    boolean flash = s.popFlash[0] > 0F || s.popFlash[1] > 0F;
                    heardPops[kind] += pop ? 1 : 0;
                    flashes[kind] += flash ? 1 : 0;
                    together[kind] += pop && flash && s.flameReach[0] > 0F ? 1 : 0;
                    first[kind] |= t == 0 && pop;
                    late[kind] |= t > 8 && (pop || flash);
                    if (flash && flashPolygons[kind] == 0) {
                        real.particles.clear();
                        frame(0F, GROUND - 20F, carZ - 400F);
                        flashPolygons[kind] = canvas.drawn(RealismDrawing.POP_FLASH);
                    }
                }
            }
        }
        boolean popper = first[0] && heardPops[0] > 6 && heardPops[0] == flashes[0] && together[0] == heardPops[0]
                && flashPolygons[0] > 0 && !late[0];
        boolean silent = heardPops[1] == 0 && flashes[1] == 0;
        check(F, "exhaust pops are heard and seen", popper && silent,
                "a car that pops, six lift-offs: " + heardPops[0] + " pops heard, " + flashes[0] + " flashes, "
                        + together[0] + " in the same tick with a flame (the flash " + flashPolygons[0]
                        + " polygons), a bang with every first flame " + first[0] + ", none once the flames are out "
                        + !late[0] + "; a car that does not: " + heardPops[1] + " pops, " + flashes[1] + " flashes");
    }

    /** Hot metal ticks: a car stopped after a long fast run ticks for a while, less and less. */
    private void ticking() {
        start();
        for (int t = 0; t < 600; t++) {
            drive(100F, true, false);
        }
        float hot = s.exhaust;
        int[] windows = new int[8];
        for (int k = 0; k < windows.length; k++) {
            int before = heardOf(RealismSounds.TICK);
            for (int t = 0; t < 100; t++) {
                drive(0F, false, false);
            }
            windows[k] = heardOf(RealismSounds.TICK) - before;
        }
        boolean ok = windows[0] > 3 && windows[0] > windows[3] && windows[windows.length - 1] == 0;
        check(F, "hot metal ticks", ok, "exhaust " + n(hot) + " after 30 s flat out; ticks per 5 s stopped "
                + Arrays.toString(windows));
    }

    /** No bonnets or bumpers: a heavy hit on either end stands nothing up, hangs nothing and drags no sparks. */
    private void noBonnetNoBumper() {
        start();
        drive(30F, true, false);
        hit(0, 2, -200F);
        drive(60F, true, false);
        hit(2, 2, 200F);
        int sparks = 0;
        for (int t = 0; t < 20; t++) {
            drive(60F, true, false);
            sparks += sparksNear(0F, carZ + s.parts.rear, 30F) + sparksNear(0F, carZ + s.parts.front, 30F);
        }
        real.particles.clear();
        frame(0F, GROUND - 60F, carZ + 400F);
        int moving = canvas.moving;
        int front = canvas.drawn(BUMPER_RGB) + canvas.drawn(BONNET_RGB);
        frame(0F, GROUND - 20F, carZ - 400F);
        int rear = canvas.drawn(BUMPER_RGB) + canvas.drawn(BONNET_RGB);
        int bonnet = real.color(0, BONNET, BONNET_RGB);
        boolean ok = moving == 0 && front == 0 && rear == 0 && sparks == 0 && bonnet == BONNET_RGB;
        check(F, "no bonnets or bumpers", ok, "after heavy hits on both ends: " + moving + " flapping panels, "
                + (front + rear) + " bonnet or bumper polygons drawn, " + sparks + " sparks at the ends, the bonnet's"
                + " colour " + (bonnet == BONNET_RGB ? "as it was" : "changed"));
    }

    /** A repair hoop fixes all damage: the radiator, the engine, the glass, the lights, the wheels, the scratches. */
    private void repair() {
        start();
        drive(60F, true, false);
        hit(0, 2, -230F); // a heavy front hit: radiator, engine, front lights, a bent flat wheel, cracks
        drive(60F, true, false);
        hit(2, 2, 120F); // the rear lights
        drive(60F, true, false);
        hit(1, 0, -200F); // the right window
        drive(60F, true, false);
        v.touchX[0] = 30F; // a scrape along the left side
        drive(60F, true, false);
        v.damage[0] = 0.95F;
        for (int t = 0; t < 20; t++) {
            drive(60F, true, false);
        }
        String before = damaged();
        v.damage[0] = 0F;
        v.repaired[0] = true;
        drive(60F, true, false);
        v.repaired[0] = false;
        real.particles.clear();
        for (int t = 0; t < 60; t++) {
            drive(60F, true, false);
        }
        String after = damaged();
        int smoke = particles(0xbdbdbd) + particles(0x7b8696) + particles(0x2c2c2e) + particles(0xe9edf0);
        boolean ok = !before.equals("nothing") && after.equals("nothing") && smoke == 0
                && CarLooks.bent(0, 0) == 0F;
        check(F, "repair hoops fix all damage", ok, "before the hoop: " + before + "; after it: " + after + ", "
                + smoke + " puffs of smoke or steam in the next 3 s");
    }

    /** A car with spikes glints and sparks whenever it rams another car; a car without them never does. */
    private void spikes() {
        int[] bursts = new int[2];
        int[] glints = new int[2];
        for (int kind = 0; kind < 2; kind++) {
            CarSheet sheet = kind == 0 ? CarSheets.parseLine("spiky V8 front front no disc 2 spikes=yes")
                    : CarSheet.DEFAULT;
            v = new RaceView();
            v.fit(2);
            v.cars = 2;
            v.humans = 1;
            v.seed = 11L;
            v.groundColor = 0x7a8a50;
            for (int c = 0; c < 2; c++) {
                v.topSpeed[c] = 100F;
                v.wheelDrop[c] = DROP;
                v.radius[c] = 60;
                v.shape[c] = box(true);
                v.y[c] = GROUND - DROP;
                v.z[c] = c * 110F; // the second car right ahead of the first
                for (int w = 0; w < RaceView.WHEELS; w++) {
                    int at = c * RaceView.WHEELS + w;
                    v.keyX[at] = KEY_X[w];
                    v.keyZ[at] = KEY_Z[w];
                    v.wheelX[at] = KEY_X[w];
                    v.wheelY[at] = GROUND;
                    v.wheelZ[at] = v.z[c] + KEY_Z[w];
                    v.contact[at] = true;
                }
                v.grounded[c] = true;
            }
            real = new RealismFeature();
            heard.clear();
            real.ears = (car, clip, gain, pitch) -> heard.add(clip);
            real.raceStarted(v);
            real.states[0].parts.build(v.shape[0], sheet, DROP);
            for (int ram = 0; ram < 3; ram++) {
                v.tick++;
                v.rams = 1;
                v.ramCar[0] = 0;
                v.ramWheel[0] = 0; // its front left corner into the other's rear left
                v.ramOther[0] = 1;
                v.ramOtherWheel[0] = 2;
                v.ramPush[0] = 12F;
                real.afterTick(v);
                v.rams = 0;
                int sparks = 0;
                for (int i = 0; i < real.particles.capacity(); i++) {
                    if (real.particles.isSpark(i) && real.particles.position(i, p)
                            && Math.hypot(p[0] - KEY_X[0], p[2] - 55F) < 40F) {
                        sparks++;
                    }
                }
                bursts[kind] += sparks > 4 ? 1 : 0;
                for (int t = 0; t < 12; t++) {
                    v.tick++;
                    real.afterTick(v); // the sparks die away before the next ram
                }
            }
            glints[kind] = heardOf(RealismSounds.GLINT);
        }
        boolean ok = bursts[0] == 3 && glints[0] == 3 && bursts[1] == 0 && glints[1] == 0;
        check(F, "Sword of Justice sparks when it rams", ok, "three rams by a car with spikes: " + bursts[0]
                + " bursts of sparks where they touched, " + glints[0] + " glints heard; by a car without: "
                + bursts[1] + " and " + glints[1]);
    }

    /** What is damaged on the car now, in words ("nothing" for a whole car). */
    private String damaged() {
        StringBuilder out = new StringBuilder();
        if (s.crush > 0F || s.leaking[0] || s.leaking[1] || s.coolant < 1F) {
            out.append("radiator ");
        }
        if (s.ram > 0F) {
            out.append("engine ");
        }
        if (s.stars > 0) {
            out.append("cracks ");
        }
        if (s.windowBroken[0] || s.windowBroken[1]) {
            out.append("window ");
        }
        if (s.frontBroken || s.rearBroken) {
            out.append("lights ");
        }
        boolean wheels = false;
        for (int w = 0; w < RaceView.WHEELS; w++) {
            wheels |= s.bent[w] > 0F || s.flat[w];
        }
        if (wheels) {
            out.append("wheels ");
        }
        if (s.scratches[0] + s.scratches[1] > 0) {
            out.append("scratches ");
        }
        return out.length() == 0 ? "nothing" : out.toString().trim();
    }

    /** Glass: the windscreen cracks after a hit; a heavy side hit shatters that side's window into bits on the road. */
    private void glass() {
        start();
        drive(40F, true, false);
        hit(0, 2, -120F);
        drive(40F, true, false);
        int stars = s.stars;
        real.particles.clear();
        frame(0F, GROUND - 120F, carZ + 400F);
        int cracks = canvas.drawn(0xe6eef2);
        int lying = real.debris.count();
        hit(0, 0, 200F);
        drive(40F, true, false);
        int bits = particles(0xcfe4ea) + particles(0xa9c8d2);
        for (int t = 0; t < 80; t++) {
            drive(40F, true, false);
        }
        int fallen = real.debris.count() - lying;
        int frameColor = real.color(0, LEFT_WINDOW, GLASS_RGB);
        boolean ok = stars >= 1 && cracks > 0 && s.windowBroken[CarParts.LEFT] && !s.windowBroken[CarParts.RIGHT]
                && bits >= 10 && fallen >= 10 && light(frameColor) < light(GLASS_RGB) * 0.5F;
        check(F, "glass cracks and shatters", ok, stars + " crack star, " + cracks + " polygons; left window "
                + "shattered " + s.windowBroken[CarParts.LEFT] + ", right " + s.windowBroken[CarParts.RIGHT] + "; "
                + bits + " bits, " + fallen + " lying on the road");
    }

    /** A bent wheel wobbles, the others run true; a flat tyre's rim throws sparks. */
    private void wheels() {
        start();
        drive(40F, true, false);
        hit(1, 2, -140F);
        drive(40F, true, false);
        CarShape shape = v.shape[0];
        int wheel1 = FIRST_WHEEL + 1;
        int wheel0 = FIRST_WHEEL;
        int[] x = new int[8];
        int[] y = new int[8];
        int[] z = new int[8];
        int[] ox = new int[8];
        int[] oy = new int[8];
        int[] oz = new int[8];
        corners(shape, wheel1, x, y, z);
        boolean tilted = real.bend(0, wheel1, x, y, z, 8, ox, oy, oz) && !Arrays.equals(x, ox);
        corners(shape, wheel0, x, y, z);
        boolean truer = !real.bend(0, wheel0, x, y, z, 8, ox, oy, oz);
        boolean shared = CarLooks.bent(0, 1) > 0F && CarLooks.bent(0, 0) == 0F;
        hit(3, 2, 240F);
        drive(40F, true, false);
        int sparks = 0;
        for (int t = 0; t < 10; t++) {
            drive(40F, true, false);
            sparks += sparksNear(v.wheelX[3], v.wheelZ[3], 10F);
        }
        boolean ok = s.bent[1] > 0F && s.bent[0] == 0F && tilted && truer && shared && s.flat[3] && !s.flat[1]
                && sparks > 3;
        check(F, "a bent wheel wobbles", ok, "front-right bent " + n(s.bent[1]) + " degrees, tilted as drawn "
                + tilted + ", the others true " + truer + ", shared with the skidmarks " + shared + " (their wavy"
                + " marks are checked with them); rear-right flat " + s.flat[3] + ", " + sparks + " rim sparks");
    }

    private static void corners(CarShape shape, int plane, int[] x, int[] y, int[] z) {
        for (int k = 0; k < shape.count[plane]; k++) {
            x[k] = Math.round(shape.x[shape.first[plane] + k]);
            y[k] = Math.round(shape.y[shape.first[plane] + k]);
            z[k] = Math.round(shape.z[shape.first[plane] + k]);
        }
    }

    /** Dirt builds up on soft ground in its colour, on the lower body and wheels, and slowly wears off on tarmac. */
    private void dirt() {
        start();
        int mud = 0x8a6a40;
        int cleanSide = real.color(0, LEFT_SIDE, BODY_RGB);
        for (int t = 0; t < 300; t++) {
            carZ += 20F;
            place(50F, 20F, true);
            for (int w = 0; w < RaceView.WHEELS; w++) {
                v.surface[w] = 1;
                v.groundRgb[w] = mud;
            }
            tick();
        }
        float dirty = s.dirt;
        int side = real.color(0, LEFT_SIDE, BODY_RGB);
        int roof = real.color(0, ROOF, BODY_RGB);
        int tyre = real.color(0, FIRST_WHEEL, TYRE_RGB);
        for (int t = 0; t < 600; t++) {
            drive(50F, true, false);
        }
        float worn = s.dirt;
        boolean ok = dirty > 0.5F && distance(side, mud) < distance(cleanSide, mud) * 0.6F && roof == BODY_RGB
                && distance(tyre, mud) < distance(TYRE_RGB, mud) && worn < dirty && worn > 0.1F;
        check(F, "dirt builds up", ok, "after 16 s on soft ground dirt " + n(dirty) + ": the lower side "
                + hex(side) + " (clean " + hex(cleanSide) + ", ground " + hex(mud) + "), the roof " + hex(roof)
                + ", a tyre " + hex(tyre) + "; after 32 s on tarmac " + n(worn));
    }

    /** A wall scraped along the left side scratches the left side only. */
    private void scratches() {
        start();
        for (int t = 0; t < 10; t++) {
            carZ += 20F;
            place(50F, 20F, true);
            v.touchX[0] = 25F; // the wall on the left pushes the left corners right
            v.touchX[2] = 25F;
            tick();
        }
        real.particles.clear();
        frame(-400F, GROUND - 20F, carZ);
        int left = canvas.drawn(0xd8dadc) + canvas.drawn(0xb4b8bc);
        frame(400F, GROUND - 20F, carZ);
        int right = canvas.drawn(0xd8dadc) + canvas.drawn(0xb4b8bc);
        boolean ok = s.scratches[CarParts.LEFT] > 0 && s.scratches[CarParts.RIGHT] == 0 && left > 0 && right == 0;
        check(F, "scrapes leave scratches", ok, s.scratches[CarParts.LEFT] + " scratches on the left, "
                + s.scratches[CarParts.RIGHT] + " on the right; seen from the left " + left
                + " polygons, from the right " + right);
    }

    /** Bottoming out: a hard landing from a big jump throws sparks from under the car, with a thud and a scrape. */
    private void landing() {
        start();
        for (int t = 0; t < 8; t++) {
            carZ += 20F;
            place(50F, 20F, false);
            v.vy[0] = 25F;
            tick();
        }
        carZ += 20F;
        place(50F, 20F, true);
        tick();
        int sparks = 0;
        for (int i = 0; i < real.particles.capacity(); i++) {
            sparks += real.particles.isSpark(i) ? 1 : 0;
        }
        boolean ok = sparks > 5 && heardOf(RealismSounds.THUD) == 1 && heardOf(RealismSounds.SCRAPE) == 1;
        check(F, "bottoming out sparks", ok, sparks + " sparks; heard " + heard);
    }

    /** Debris stays: still there a lap later; past the cap the oldest piece goes first. */
    private void debris() {
        start();
        drive(40F, true, false);
        hit(0, 0, 200F);
        for (int t = 0; t < 80; t++) {
            drive(40F, true, false);
        }
        int fallen = real.debris.count();
        for (int t = 0; t < 2000; t++) {
            drive(40F, true, false);
        }
        int lapLater = real.debris.count();
        int cap = real.debris.capacity();
        for (int k = 0; k < cap + 50; k++) {
            real.debris.drop(10000F + k, GROUND, 0F, 1F, 0xffffff, 4, 0F);
        }
        real.debris.position(real.debris.slot(0), p);
        boolean ok = fallen >= 10 && lapLater == fallen && real.debris.count() == cap && p[0] == 10050F;
        check(F, "debris stays", ok, fallen + " pieces after a crash, " + lapLater + " a lap later; past the cap of "
                + cap + " the oldest left is new piece " + (int) (p[0] - 10000F + 1));
    }

    /** Lights: the brake lights light while braking; a rear hit leaves them dark, a front hit the headlights. */
    private void lights() {
        start();
        drive(50F, false, true);
        int braking = real.color(0, REAR_LIGHT, REAR_LIGHT_RGB);
        int lit = real.lamp();
        drive(50F, true, false);
        int off = real.color(0, REAR_LIGHT, REAR_LIGHT_RGB);
        int gameLit = real.lamp();
        hit(2, 2, 120F);
        drive(50F, false, true);
        int broken = real.color(0, REAR_LIGHT, REAR_LIGHT_RGB);
        int dark = real.lamp();
        hit(0, 2, -120F);
        drive(50F, true, false);
        real.color(0, FRONT_LIGHT, FRONT_LIGHT_RGB);
        int headlight = real.lamp();
        start(CarSheet.DEFAULT, false);
        drive(50F, false, true);
        real.particles.clear();
        frame(0F, GROUND - 20F, carZ - 400F);
        int drawnLamps = canvas.drawn(0xff2a18);
        boolean ok = lit == 1 && light(braking) > light(REAR_LIGHT_RGB) && off == REAR_LIGHT_RGB && gameLit == -1
                && dark == 0 && light(broken) < light(REAR_LIGHT_RGB) && headlight == 0 && drawnLamps == 2;
        check(F, "lights work and break", ok, "braking: lit " + (lit == 1) + " " + hex(braking) + "; not braking "
                + hex(off) + "; after a rear hit dark " + (dark == 0) + " " + hex(broken) + "; headlights dark after a "
                + "front hit " + (headlight == 0) + "; a car with no rear lights of its own gets " + drawnLamps
                + " drawn lamps");
    }

    /** Heat haze: a car driven hard shimmers over its engine and behind its exhausts. */
    private void haze() {
        start();
        for (int t = 0; t < 400; t++) {
            drive(100F, true, false);
        }
        int over = 0;
        int behind = 0;
        for (int i = 0; i < real.particles.capacity(); i++) {
            if (real.particles.color(i) == 0xdfe3e6 && real.particles.position(i, p)) {
                float lz = p[2] - v.z[0];
                if (lz > 0F) {
                    over++;
                } else {
                    behind++;
                }
            }
        }
        boolean ok = s.engine > HeatEffects.HAZE && s.exhaust > 0.3F && over > 0 && behind > 0;
        check(F, "heat haze", ok, "engine " + n(s.engine) + ", exhaust " + n(s.exhaust) + "; shimmer over the engine "
                + over + ", behind " + behind);
    }

    /** Loose parts rattle on a damaged car over bumps, more often at speed; a whole car never rattles. */
    private void rattle() {
        int slow = rattles(0.4F, 20F);
        int fast = rattles(0.4F, 100F);
        int whole = rattles(0F, 100F);
        check(F, "loose parts rattle", slow > 0 && fast > slow && whole == 0, "damaged, 10 s over bumps: " + slow
                + " rattles slow, " + fast + " fast; undamaged " + whole);
    }

    private int rattles(float damage, float sp) {
        start();
        v.damage[0] = damage;
        for (int t = 0; t < 190; t++) {
            carZ += sp * 0.4F;
            place(sp, sp * 0.4F, true);
            for (int w = 0; w < RaceView.WHEELS; w++) {
                v.surface[w] = 3;
            }
            tick();
        }
        return heardOf(RealismSounds.RATTLE);
    }

    /** Each car has a short sheet; the owner looks the values over. */
    private void sheets() {
        String file = CarSheets.SOURCE;
        List<String> lines = CarSheets.lines();
        if (lines == null) {
            check(F, "each car has a short sheet", false, "no " + CarSheets.RESOURCE + " next to the extra's classes");
            return;
        }
        Map<String, CarSheet> sheets = CarSheets.parse(lines, file);
        List<String> missing = new ArrayList<>();
        for (String car : CARS) {
            if (!sheets.containsKey(car)) {
                missing.add(car);
            }
        }
        check(F, "each car has a short sheet", missing.isEmpty(), sheets.size() + " sheets in " + file
                + (missing.isEmpty() ? ", every car has one" : ", missing " + missing));
        if (CarSheets.lookedOver(lines)) {
            check(F, "the owner has looked the sheets over", true, "the file says so");
        } else {
            waiting(F, "the owner has looked the sheets over", "suggested from each car's look; " + file
                    + " says \"Looked over by the owner: no\" until the owner has put them right"
                    + " (docs\\features\\car-sheets.md lists them)");
        }
    }

    /** The made-up car's parts are found where they are: bonnet, windscreen, windows, lights, the engine. */
    private void parts() {
        start();
        CarParts parts = s.parts;
        boolean ok = parts.role[BONNET] == CarParts.BONNET && parts.screen == 1
                && parts.role[LEFT_WINDOW] == CarParts.WINDOW_LEFT && parts.role[REAR_LIGHT] == CarParts.LIGHT_REAR
                && parts.role[ROOF] == CarParts.BODY && parts.side[LEFT_SIDE] == CarParts.LEFT && parts.hasBonnet
                && parts.hingeZ <= 16F && parts.bonnetFront >= 50F && parts.engine[2] > 16F && !parts.drawnLamps
                && parts.low[LEFT_SIDE] > parts.low[ROOF];
        check(F, "car parts found", ok, "bonnet from z " + n(parts.hingeZ) + " to " + n(parts.bonnetFront)
                + ", windscreen polygon " + parts.screen + ", engine at z " + n(parts.engine[2]));
    }

    private static float light(int rgb) {
        return 0.3F * (rgb >> 16 & 0xff) + 0.59F * (rgb >> 8 & 0xff) + 0.11F * (rgb & 0xff);
    }

    private static float distance(int a, int b) {
        float r = (a >> 16 & 0xff) - (b >> 16 & 0xff);
        float g = (a >> 8 & 0xff) - (b >> 8 & 0xff);
        float bl = (a & 0xff) - (b & 0xff);
        return (float) Math.sqrt(r * r + g * g + bl * bl);
    }

    private static String hex(int rgb) {
        return String.format("%06x", rgb & 0xffffff);
    }
}
