package nfm.lit.features.helmetcam;

import static nfm.lit.features.ExtrasCheck.check;
import static nfm.lit.features.ExtrasCheck.n;
import static nfm.lit.features.ExtrasCheck.waiting;

import java.util.List;
import nfm.lit.features.CarShape;
import nfm.lit.features.CheckCanvas;
import nfm.lit.features.RaceView;

/**
 * The helmet cam's checks (first-person, "Helmet-cam first-person view",
 * "G-forces are visible in first person"): a made-up car driven straight,
 * braked, accelerated, turned, flown and landed, and what the head, the eyes
 * and the edges of the view do; the eyes and the polygons drawn from them on
 * a made-up car; the camera placed for the game lined up with the car.
 */
public final class HelmetCheck {

    private static final String F = "helmet cam";
    /** The game's camera centre (Medium.cx, cy, cz) and its focus, at 1280x720. */
    private static final int CX = 640;
    private static final int CY = 360;
    private static final int CZ = 50;
    private static final float FOCUS = 400F;
    /** In a race the game's focus is 500: the picture's bottom edge is this slope below the middle of the view. */
    private static final float HALF_HIGH = 360F / 500F;
    /** The game leaves an extra's polygon out whole when a corner is nearer than this, and draws a nearer point as if at 50. */
    private static final float DROPPED = 10F;
    private static final float SQUEEZED = 50F;

    private final HelmetCam cam = HelmetCam.FEATURE;
    private RaceView v;
    private float x;
    private float y;
    private float z;
    private float vx;
    private float vy;
    private float vz;
    private int yaw;

    private HelmetCheck() {
    }

    public static void run() {
        HelmetCheck c = new HelmetCheck();
        try {
            c.braking();
            c.accelerating();
            c.corner();
            c.landing();
            c.vignette();
            c.cockpit();
            c.camera();
            c.interiorParts();
            c.interiorCovers();
            c.interiorNeverClips();
            c.interiorMoves();
            waiting(F, "a lap in first person", "the owner drives a lap with hard braking, a fast corner, a jump"
                    + " and a landing in the helmet cam (Extras tab, then the view key) and signs it off");
        } finally {
            c.cam.raceEnded(c.v);
        }
    }

    /** A race with one car, at rest on the ground, heading along z. */
    private void start() {
        v = new RaceView();
        v.fit(1);
        v.cars = 1;
        v.humans = 1;
        v.seed = 5L;
        v.modelName[0] = "box";
        CarShape shape = box();
        copy(shape, v.shape[0]);
        cam.raceStarted(v);
        x = 0F;
        y = 250F;
        z = 0F;
        vx = vy = vz = 0F;
        yaw = 0;
    }

    /** One tick: the car moves by its speed, which then changes by {@code (ax, ay, az)}. */
    private void tick(float ax, float ay, float az) {
        vx += ax;
        vy += ay;
        vz += az;
        x += vx;
        y += vy;
        z += vz;
        v.tick++;
        v.x[0] = x;
        v.y[0] = y;
        v.z[0] = z;
        v.vx[0] = vx;
        v.vy[0] = vy;
        v.vz[0] = vz;
        v.xz[0] = yaw;
        v.zy[0] = 0;
        v.xy[0] = 0;
        cam.afterTick(v);
    }

    private Head head() {
        return cam.head(0);
    }

    /** Straight on at {@code speed} units a tick until the head has settled. */
    private void cruise(float speed, int ticks) {
        vz = speed;
        for (int t = 0; t < ticks; t++) {
            tick(0F, 0F, 0F);
        }
    }

    /** Hard braking throws the head forward; when it ends the head comes back past the middle a little, then settles. */
    private void braking() {
        start();
        cruise(60F, 40);
        float rest = head().offset[2];
        float forward = 0F;
        for (int t = 0; t < 14; t++) {
            tick(0F, 0F, -0.9F * Head.G); // 0.9 g
            forward = Math.max(forward, head().offset[2]);
        }
        float back = 0F;
        float settled = 0F;
        for (int t = 0; t < 60; t++) {
            tick(0F, 0F, 0F);
            back = Math.min(back, head().offset[2]);
            settled = head().offset[2];
        }
        float overshoot = -back / Math.max(0.001F, forward);
        check(F, "hard braking: the view moves forward, settles back with a little overshoot", Math.abs(rest) < 0.01F
                && forward > 5F && overshoot > 0.05F && overshoot < 0.35F && Math.abs(settled) < 0.2F,
                "at speed " + n(rest) + "; braking 0.9 g " + n(forward) + " forward; back past the middle "
                        + n(-back) + " (" + Math.round(overshoot * 100F) + "%); a second on " + n(settled));
    }

    /** Accelerating throws the head back. */
    private void accelerating() {
        start();
        cruise(10F, 10);
        float back = 0F;
        for (int t = 0; t < 14; t++) {
            tick(0F, 0F, 0.5F * Head.G);
            back = Math.min(back, head().offset[2]);
        }
        check(F, "accelerating: the view moves back", back < -2.5F, "0.5 g ahead: " + n(-back) + " back");
    }

    /** A left corner throws the head to the right, its outside, and turns it left, into the corner. */
    private void corner() {
        start();
        float speed = 50F;
        cruise(speed, 10);
        int rate = 5; // degrees a tick, left: 0.6 g at this speed
        for (int t = 0; t < 30; t++) {
            yaw += rate;
            double r = Math.toRadians(yaw);
            float nx = (float) (-Math.sin(r) * speed);
            float nz = (float) (Math.cos(r) * speed);
            tick(nx - vx, 0F, nz - vz);
        }
        Head h = head();
        check(F, "a corner: the view moves to its outside and looks into it", h.offset[0] > 2.5F && h.look > 8F,
                "a left corner at 0.6 g: " + n(h.offset[0]) + " to the right, looking " + n(h.look) + " degrees left");
        // and the other way round
        start();
        cruise(speed, 10);
        for (int t = 0; t < 30; t++) {
            yaw -= rate;
            double r = Math.toRadians(yaw);
            tick((float) (-Math.sin(r) * speed) - vx, 0F, (float) (Math.cos(r) * speed) - vz);
        }
        check(F, "a corner the other way: to its outside, looking into it", h.offset[0] < -2.5F && h.look < -8F,
                "a right corner: " + n(-h.offset[0]) + " to the left, looking " + n(-h.look) + " degrees right");
    }

    /** In the air the head floats up a little; the landing jolts it down hard, and it settles. */
    private void landing() {
        start();
        cruise(40F, 20);
        vy = -42F; // off a ramp, up
        float up = 0F;
        for (int t = 0; t < 12; t++) {
            tick(0F, Head.G, 0F); // falling as the game falls
            up = Math.min(up, head().offset[1]);
        }
        vy = 0F; // the wheels meet the road
        float down = 0F;
        for (int t = 0; t < 4; t++) {
            tick(0F, 0F, 0F);
            down = Math.max(down, head().offset[1]);
        }
        for (int t = 0; t < 40; t++) {
            tick(0F, 0F, 0F);
        }
        float settled = head().offset[1];
        check(F, "a landing jolts the view down; in the air it floats up a little", up < -1F && up > -Head.LIMIT[1]
                && down > 6F && Math.abs(settled) < 0.3F, "in the air " + n(-up) + " up; the landing " + n(down)
                        + " down; two seconds on " + n(settled));
    }

    /** The edges darken under g-force: not at a steady speed nor floating in the air, more the harder the braking. */
    private void vignette() {
        start();
        cruise(60F, 40);
        float steady = cam.vignette(0, 1F);
        float light = 0F;
        for (int t = 0; t < 14; t++) {
            tick(0F, 0F, -0.4F * Head.G);
            light = Math.max(light, cam.vignette(0, 1F));
        }
        start();
        cruise(60F, 40);
        float hard = 0F;
        float between = 0F;
        for (int t = 0; t < 14; t++) {
            tick(0F, 0F, -1.2F * Head.G);
            hard = Math.max(hard, cam.vignette(0, 1F));
            between = Math.max(between, cam.vignette(0, 0.5F));
        }
        // up a ramp over four ticks (which presses the head down), then flying: floating is no force
        start();
        cruise(40F, 20);
        for (int t = 0; t < 4; t++) {
            tick(0F, -10F, 0F);
        }
        float air = 0F;
        for (int t = 0; t < 20; t++) {
            tick(0F, Head.G, 0F);
            if (t >= 8) {
                air = Math.max(air, cam.vignette(0, 1F));
            }
        }
        check(F, "g-forces darken the edges of the view", steady == 0F && air < 0.02F && light < hard && hard > 0.2F
                && hard <= HelmetCam.VIGNETTE_MOST && between > 0F, "at a steady speed " + n(steady)
                        + "; in the air " + n(air) + "; braking 0.4 g " + n(light) + "; braking 1.2 g " + n(hard)
                        + " (at most " + n(HelmetCam.VIGNETTE_MOST) + ")");
    }

    /** The eyes in the glasshouse; the bonnet drawn from them, the glass, roof, sides and back not. */
    private void cockpit() {
        start();
        Cockpit c = cam.cockpit(0);
        CarShape s = v.shape[0];
        boolean inside = c.eyeY > -22F && c.eyeY < -12F && c.eyeZ > -28F && c.eyeZ < 15F && c.eyeX == 0F;
        boolean bonnet = c.shows(0);
        boolean hidden = !c.shows(1) && !c.shows(2) && !c.shows(3) && !c.shows(4) && !c.shows(5) && !c.shows(8);
        boolean frontWheels = c.shows(s.planes - 4) && c.shows(s.planes - 3);
        boolean rearWheels = !c.shows(s.planes - 2) && !c.shows(s.planes - 1);
        check(F, "the eyes, and what of the car they see", inside && bonnet && hidden && frontWheels && rearWheels,
                "eyes at " + n(c.eyeX) + ", " + n(c.eyeY) + ", " + n(c.eyeZ) + " (glass from y -22 to -12, z -28 to 15);"
                        + " bonnet " + (bonnet ? "drawn" : "not drawn") + "; glass, roof, sides, back "
                        + (hidden ? "left out" : "drawn") + "; front wheels " + (frontWheels ? "drawn" : "not drawn")
                        + ", rear " + (rearWheels ? "left out" : "drawn") + "; " + c.drawn() + " of " + c.polygons
                        + " drawn");
    }

    /** The camera the game is given looks from the eyes along the car: a far point ahead lands in the middle. */
    private void camera() {
        start();
        cruise(30F, 30);
        Cockpit c = cam.cockpit(0);
        int carYaw = 30;
        int carX = 1000;
        int carY = 230;
        int carZ = -500;
        double[] place = new double[5];
        cam.camera(0, false, carX, carY, carZ, carYaw, 0, 0, 0, CX, CY, CZ, place);
        double yawR = Math.toRadians(carYaw);
        // the eyes in the world (the head at rest), and a point far ahead at their height
        double eyeX = carX + c.eyeX * Math.cos(yawR) - c.eyeZ * Math.sin(yawR);
        double eyeY = carY + c.eyeY;
        double eyeZ = carZ + c.eyeX * Math.sin(yawR) + c.eyeZ * Math.cos(yawR);
        double[] eye = view(place, eyeX, eyeY, eyeZ);
        double[] far = view(place, eyeX - Math.sin(yawR) * 5000D, eyeY, eyeZ + Math.cos(yawR) * 5000D);
        double farX = CX + FOCUS * (far[0] - CX) / far[2];
        double farY = CY + FOCUS * (far[1] - CY) / far[2];
        double downBy = CY - FOCUS * Math.tan(Math.toRadians(HelmetCam.LOOK_DOWN));
        // looking back turns it round: a far point behind lands in the middle
        cam.camera(0, false, carX, carY, carZ, carYaw, 0, 0, 1, CX, CY, CZ, place);
        double[] behind = view(place, eyeX + Math.sin(yawR) * 5000D, eyeY, eyeZ - Math.cos(yawR) * 5000D);
        double behindX = CX + FOCUS * (behind[0] - CX) / behind[2];
        check(F, "the camera looks from the eyes along the car", Math.abs(eye[2]) < 0.01D && Math.abs(eye[0] - CX) < 0.01D
                && Math.abs(farX - CX) < 0.5D && Math.abs(farY - downBy) < 1.5D && behind[2] > 0D
                && Math.abs(behindX - CX) < 0.5D, "the eyes " + n(eye[2]) + " from the camera's eye; a far point"
                        + " ahead at " + n(farX) + ", " + n(farY) + " (the middle " + CX + ", " + n(downBy) + " looking "
                        + n(HelmetCam.LOOK_DOWN) + " degrees down); looking back, one behind at " + n(behindX));
    }

    /**
     * World point {@code (px, py, pz)} turned into the game's camera at
     * {@code place} (Medium.x, y, z, xz, zy), as ContO.d turns it: x and y
     * before the perspective, and the depth.
     */
    private static double[] view(double[] place, double px, double py, double pz) {
        double xz = Math.toRadians(place[3]);
        double zy = Math.toRadians(place[4]);
        double rx = CX + (px - place[0] - CX) * Math.cos(xz) - (pz - place[2] - CZ) * Math.sin(xz);
        double rz = CZ + (px - place[0] - CX) * Math.sin(xz) + (pz - place[2] - CZ) * Math.cos(xz);
        double ry = CY + (py - place[1] - CY) * Math.cos(zy) - (rz - CZ) * Math.sin(zy);
        double depth = CZ + (py - place[1] - CY) * Math.sin(zy) + (rz - CZ) * Math.cos(zy);
        return new double[] {rx, ry, depth};
    }

    /* -------------------------------------------------------- the inside */

    /** The parts of the inside as drawn for the made-up car, the wheel turned {@code steer}. */
    private CheckCanvas inside(Cockpit c, float steer, float speed, float power) {
        CheckCanvas canvas = new CheckCanvas();
        canvas.keeping = true;
        new Interior().draw(canvas, 0, c, steer, speed, power, 0F, 0F, 0F);
        return canvas;
    }

    /** The inside is many parts in many colours, none nearer the eyes than the game can draw; an open car has no roof. */
    private void interiorParts() {
        start();
        Cockpit c = cam.cockpit(0);
        CheckCanvas canvas = inside(c, 0F, 0.5F, 0.5F);
        float nearest = Float.MAX_VALUE;
        for (float[] p : canvas.kept) {
            for (int k = 2; k < p.length; k += 3) {
                nearest = Math.min(nearest, p[k] - c.eyeZ);
            }
        }
        int roofed = canvas.drawn(Interior.PILLAR) + canvas.drawn(Interior.ROOF);
        // the same car with no glass: an open car
        CarShape open = box();
        java.util.Arrays.fill(open.glass, false);
        Cockpit o = new Cockpit();
        o.build(open);
        CheckCanvas openCanvas = inside(o, 0F, 0.5F, 0.5F);
        int openRoofed = openCanvas.drawn(Interior.PILLAR) + openCanvas.drawn(Interior.ROOF);
        check(F, "the inside of the car: a dashboard, instruments, a steering wheel, doors; a roof only over glass",
                canvas.polygons >= 60 && canvas.colors.size() >= 15 && nearest >= Interior.NEAREST && c.closed
                        && roofed > 0 && !o.closed && openRoofed == 0 && openCanvas.polygons >= 60,
                canvas.polygons + " parts in " + canvas.colors.size() + " colours, the nearest " + n(nearest)
                        + " ahead of the eyes (the game draws from 50, the head comes 16 forward); " + roofed
                        + " parts of pillars and roof with glass, " + openRoofed + " in an open car ("
                        + openCanvas.polygons + " parts)");
    }

    /**
     * From the eyes at rest and thrown as far as the springs let them,
     * looking as far aside as the head turns: every point of the picture
     * below the top of the dashboard is on a part, in a 16:9 and a 21:9
     * picture, and no one part is much of the picture's lower half.
     */
    private void interiorCovers() {
        start();
        Cockpit c = cam.cockpit(0);
        List<float[]> parts = inside(c, 0F, 0.5F, 0.5F).kept;
        float far = Interior.ahead(c);
        float cowl = Interior.cowl(c);
        int holes = 0;
        int looked = 0;
        float most = 0F;
        int seenParts = 0;
        float[] aspects = {16F / 9F, 21F / 9F};
        float[] px = new float[parts.size() * 12];
        float[] py = new float[parts.size() * 12];
        boolean[] dropped = new boolean[parts.size()];
        for (float aspect : aspects) {
            float halfWide = HALF_HIGH * aspect;
            for (int state = 0; state < 3 * 3 * 3 * 3 * 2; state++) {
                float ox = (state % 3 - 1) * Head.LIMIT[0];
                float oy = (state / 3 % 3 - 1) * Head.LIMIT[1];
                float oz = (state / 9 % 3 - 1) * Head.LIMIT[2];
                float look = (state / 27 % 3 - 1) * Head.LOOK_MAX;
                float pitch = state / 81 == 0 ? HelmetCam.LOOK_DOWN : HelmetCam.LOOK_DOWN + Head.NOD * Head.LIMIT[2];
                boolean rest = ox == 0F && oy == 0F && oz == 0F && look == 0F && state / 81 == 0;
                project(parts, c, ox, oy, oz, look, pitch, px, py, dropped);
                int[] top = new int[parts.size()];
                int lower = 0;
                double yaw = Math.toRadians(look);
                double down = Math.toRadians(pitch);
                for (int row = 0; row < 36; row++) {
                    for (int col = 0; col < 64; col++) {
                        float sx = ((col + 0.5F) / 32F - 1F) * halfWide;
                        float sy = ((row + 0.5F) / 18F - 1F) * HALF_HIGH;
                        // the ray through this point of the picture, in the car's frame: the camera's turns undone
                        double ry = sy * Math.cos(down) + Math.sin(down);
                        double rz1 = -sy * Math.sin(down) + Math.cos(down);
                        double rx = sx * Math.cos(yaw) + rz1 * Math.sin(yaw);
                        double rz = -sx * Math.sin(yaw) + rz1 * Math.cos(yaw);
                        int on = -1;
                        for (int i = 0; i < parts.size(); i++) {
                            if (!dropped[i] && within(px, py, i * 12, parts.get(i).length / 3, sx, sy)) {
                                on = i;
                            }
                        }
                        if (rest && sy > 0F) {
                            lower++;
                            if (on >= 0) {
                                top[on]++;
                            }
                        }
                        if (rz <= 0.001D) {
                            continue;
                        }
                        // where the ray meets the dashboard's plane, as slopes from the eyes at rest
                        double t = (far - oz) / rz;
                        double hx = (ox + rx * t) / far;
                        double hy = (oy + ry * t) / far;
                        if (hy > cowl + 0.01D && hy < Interior.FLOOR && Math.abs(hx) < 8D) {
                            looked++;
                            holes += on < 0 ? 1 : 0;
                        }
                    }
                }
                if (rest) {
                    for (int count : top) {
                        most = Math.max(most, count / (float) lower);
                        seenParts += count > 0 ? 1 : 0;
                    }
                }
            }
        }
        check(F, "below the dashboard's top the picture is covered, by many parts, however the head is thrown",
                holes == 0 && looked > 20000 && most < 0.3F && seenParts >= 40,
                holes + " of " + looked + " points open (16:9 and 21:9; the head at rest and at the ends of its"
                        + " travel, turned up to " + n(Head.LOOK_MAX) + " degrees); at rest the largest part is "
                        + Math.round(most * 100F) + "% of the picture's lower half, which shows " + seenParts / 2
                        + " parts");
    }

    /**
     * The game leaves out, whole, a polygon with a corner behind the eyes.
     * However the head is thrown and turned and the car pitched against
     * the view, a part left out is one wholly outside the picture, so
     * nothing vanishes in view; and no corner in view is drawn nearer than
     * it is by more than a little.
     */
    private void interiorNeverClips() {
        start();
        Cockpit c = cam.cockpit(0);
        List<float[]> parts = inside(c, 1F, 0.5F, 0.5F).kept;
        int vanishing = 0;
        int leftOut = 0;
        int frames = 0;
        float squeeze = 1F;
        float[] v = new float[3];
        for (float aspect : new float[] {16F / 9F, 21F / 9F}) {
            float halfWide = HALF_HIGH * aspect;
            for (int state = 0; state < 27; state++) {
                float ox = (state % 3 - 1) * Head.LIMIT[0];
                float oy = (state / 3 % 3 - 1) * Head.LIMIT[1];
                float oz = (state / 9 % 3 - 1) * Head.LIMIT[2];
                for (float look = -Head.LOOK_MAX; look <= Head.LOOK_MAX; look += 5F) {
                    for (float pitch = -12F; pitch <= 18F; pitch += 3F) {
                        frames++;
                        for (float[] p : parts) {
                            int n = p.length / 3;
                            boolean out = false;
                            int sides = 15;
                            for (int k = 0; k < n; k++) {
                                turn(p, k, c, ox, oy, oz, look, pitch, v);
                                out |= v[2] < DROPPED;
                                int side;
                                if (v[2] > 0.5F) {
                                    side = (v[0] / v[2] > halfWide ? 1 : 0) | (v[0] / v[2] < -halfWide ? 2 : 0)
                                            | (v[1] / v[2] > HALF_HIGH ? 4 : 0) | (v[1] / v[2] < -HALF_HIGH ? 8 : 0);
                                    if (side == 0 && v[2] < SQUEEZED) {
                                        squeeze = Math.max(squeeze, SQUEEZED / v[2]);
                                    }
                                } else {
                                    side = (v[0] > 0F ? 1 : 2) | (v[1] > 0F ? 4 : 8);
                                }
                                sides &= side;
                            }
                            if (out) {
                                leftOut++;
                                vanishing += sides == 0 ? 1 : 0;
                            }
                        }
                    }
                }
            }
        }
        check(F, "the inside never clips: a part the game leaves out is always outside the picture",
                vanishing == 0 && leftOut > 0 && squeeze < 1.05F,
                vanishing + " of " + leftOut + " parts left out were in the picture, over " + frames
                        + " views (16:9 and 21:9; the head at the ends of its travel, turned up to "
                        + n(Head.LOOK_MAX) + " degrees, the car pitched 12 up to 18 down against the view); a"
                        + " corner in view is drawn at most " + Math.round((squeeze - 1F) * 100F) + "% out of place");
    }

    /** The steering wheel turns with the steering; the needles follow the speed and the power. */
    private void interiorMoves() {
        start();
        Cockpit c = cam.cockpit(0);
        float[] markX = new float[3];
        float[] needle = new float[2];
        for (int k = 0; k < 3; k++) {
            CheckCanvas canvas = inside(c, k - 1F, k == 0 ? 0F : 1F, k == 0 ? 0F : 1F);
            int needles = 0;
            for (int i = 0; i < canvas.kept.size(); i++) {
                float[] p = canvas.kept.get(i);
                if (canvas.keptColor.get(i) == Interior.MARK) {
                    markX[k] = (p[0] + p[3] + p[6] + p[9]) / 4F - c.eyeX;
                } else if (canvas.keptColor.get(i) == Interior.NEEDLE && needles++ == 0 && k != 1) {
                    // the first needle is the speed's, on the left dial: its tip against the dial's middle
                    float d = Math.max(Interior.NEAREST, Interior.ahead(c) - Interior.INSTRUMENTS_NEARER);
                    needle[k / 2] = (p[0] - c.eyeX) / d + Interior.DIAL_ASIDE;
                }
            }
        }
        // the wheel in the game follows the keys
        cruise(30F, 5);
        v.right[0] = true;
        for (int t = 0; t < 20; t++) {
            tick(0F, 0F, 0F);
        }
        float held = cam.steer(0);
        v.right[0] = false;
        v.left[0] = true;
        tick(0F, 0F, 0F);
        float turning = cam.steer(0);
        v.left[0] = false;
        check(F, "the steering wheel turns with the steering, the needles with the speed and the power",
                markX[0] < -8F && Math.abs(markX[1]) < 0.01F && markX[2] > 8F && needle[0] < -0.02F && needle[1] > 0.02F
                        && held > 0.95F && turning < held && turning > -0.5F,
                "the mark on the rim " + n(markX[0]) + ", " + n(markX[1]) + ", " + n(markX[2])
                        + " to the side at full left, straight, full right; the speed's needle points "
                        + (needle[0] < 0F ? "left" : "right") + " at rest and " + (needle[1] > 0F ? "right" : "left")
                        + " at top speed; right held a second turns the wheel " + Math.round(held * 100F)
                        + "% of its lock, and a tick of left brings it to " + Math.round(turning * 100F) + "%");
    }

    /**
     * Corner {@code k} of part {@code p} (the car's frame) as the eyes see
     * it, into {@code v}: across, down and ahead of them, the head moved
     * {@code (ox, oy, oz)} from rest and turned {@code look} degrees to
     * the right, the view {@code pitch} degrees down against the car.
     */
    private static void turn(float[] p, int k, Cockpit c, float ox, float oy, float oz, float look, float pitch,
            float[] v) {
        double dx = p[k * 3] - c.eyeX - ox;
        double dy = p[k * 3 + 1] - c.eyeY - oy;
        double dz = p[k * 3 + 2] - c.eyeZ - oz;
        double yaw = Math.toRadians(look);
        double down = Math.toRadians(pitch);
        double x1 = dx * Math.cos(yaw) - dz * Math.sin(yaw);
        double z1 = dx * Math.sin(yaw) + dz * Math.cos(yaw);
        v[0] = (float) x1;
        v[1] = (float) (dy * Math.cos(down) - z1 * Math.sin(down));
        v[2] = (float) (dy * Math.sin(down) + z1 * Math.cos(down));
    }

    /** Every part as the game would put it in the picture (slopes from its middle), 12 corners' room each; {@code dropped}: left out. */
    private static void project(List<float[]> parts, Cockpit c, float ox, float oy, float oz, float look, float pitch,
            float[] px, float[] py, boolean[] dropped) {
        float[] v = new float[3];
        for (int i = 0; i < parts.size(); i++) {
            float[] p = parts.get(i);
            dropped[i] = false;
            for (int k = 0; k < p.length / 3; k++) {
                turn(p, k, c, ox, oy, oz, look, pitch, v);
                dropped[i] |= v[2] < DROPPED;
                float depth = Math.max(v[2], SQUEEZED);
                px[i * 12 + k] = v[0] / depth;
                py[i * 12 + k] = v[1] / depth;
            }
        }
    }

    /** True if {@code (x, y)} is in the convex polygon of {@code n} corners from {@code at}. */
    private static boolean within(float[] px, float[] py, int at, int n, float x, float y) {
        boolean positive = false;
        boolean negative = false;
        for (int k = 0; k < n; k++) {
            int next = at + (k + 1) % n;
            float cross = (px[next] - px[at + k]) * (y - py[at + k]) - (py[next] - py[at + k]) * (x - px[at + k]);
            positive |= cross > 0F;
            negative |= cross < 0F;
            if (positive && negative) {
                return false;
            }
        }
        return true;
    }

    /* ------------------------------------------------------------ the car */

    /**
     * The made-up car (x right, y down, z forward): 0 the bonnet, 1 the
     * windscreen, 2 the roof, 3 and 4 the sides, 5 the rear window, 6 and 7
     * the side windows, 8 the boot; then the four wheels, front first.
     */
    private static CarShape box() {
        float[][] polys = {
            {-18, -12, 16, 18, -12, 16, 18, -12, 50, -18, -12, 50},
            {-16, -12, 15, 16, -12, 15, 14, -22, 0, -14, -22, 0},
            {-16, -22, 0, 16, -22, 0, 16, -22, -30, -16, -22, -30},
            {-20, -12, -50, -20, -12, 50, -20, -2, 50, -20, -2, -50},
            {20, -12, -50, 20, -12, 50, 20, -2, 50, 20, -2, -50},
            {-14, -22, -30, 14, -22, -30, 16, -12, -32, -16, -12, -32},
            {-19, -21, -2, -19, -21, -28, -19, -13, -28, -19, -13, -2},
            {19, -21, -2, 19, -21, -28, 19, -13, -28, 19, -13, -2},
            {-18, -12, -32, 18, -12, -32, 18, -12, -50, -18, -12, -50},
            wheel(-22F, 34F), wheel(22F, 34F), wheel(-22F, -34F), wheel(22F, -34F)};
        boolean[] glass = {false, true, false, false, false, true, true, true, false, false, false, false, false};
        CarShape s = new CarShape();
        int corners = 0;
        for (float[] p : polys) {
            corners += p.length / 3;
        }
        s.fit(polys.length, corners);
        s.minX = s.minY = s.minZ = Float.MAX_VALUE;
        s.maxX = s.maxY = s.maxZ = -Float.MAX_VALUE;
        int at = 0;
        for (int i = 0; i < polys.length; i++) {
            s.first[i] = at;
            s.count[i] = polys[i].length / 3;
            s.glass[i] = glass[i];
            s.wheel[i] = i >= 9 ? i - 9 : -1;
            for (int k = 0; k < s.count[i]; k++, at++) {
                s.x[at] = polys[i][k * 3];
                s.y[at] = polys[i][k * 3 + 1];
                s.z[at] = polys[i][k * 3 + 2];
                if (i < 9) {
                    s.minX = Math.min(s.minX, s.x[at]);
                    s.maxX = Math.max(s.maxX, s.x[at]);
                    s.minY = Math.min(s.minY, s.y[at]);
                    s.maxY = Math.max(s.maxY, s.y[at]);
                    s.minZ = Math.min(s.minZ, s.z[at]);
                    s.maxZ = Math.max(s.maxZ, s.z[at]);
                }
            }
        }
        return s;
    }

    /** A wheel of radius 8 at {@code (x, 2, z)}, eight corners round. */
    private static float[] wheel(float x, float z) {
        float[] c = new float[24];
        for (int k = 0; k < 8; k++) {
            double a = k * Math.PI / 4D;
            c[k * 3] = x;
            c[k * 3 + 1] = 2F + (float) Math.sin(a) * 8F;
            c[k * 3 + 2] = z + (float) Math.cos(a) * 8F;
        }
        return c;
    }

    private static void copy(CarShape from, CarShape to) {
        int corners = 0;
        for (int i = 0; i < from.planes; i++) {
            corners += from.count[i];
        }
        to.fit(from.planes, corners);
        System.arraycopy(from.first, 0, to.first, 0, from.planes);
        System.arraycopy(from.count, 0, to.count, 0, from.planes);
        System.arraycopy(from.glass, 0, to.glass, 0, from.planes);
        System.arraycopy(from.wheel, 0, to.wheel, 0, from.planes);
        System.arraycopy(from.x, 0, to.x, 0, corners);
        System.arraycopy(from.y, 0, to.y, 0, corners);
        System.arraycopy(from.z, 0, to.z, 0, corners);
        to.minX = from.minX;
        to.maxX = from.maxX;
        to.minY = from.minY;
        to.maxY = from.maxY;
        to.minZ = from.minZ;
        to.maxZ = from.maxZ;
    }
}
