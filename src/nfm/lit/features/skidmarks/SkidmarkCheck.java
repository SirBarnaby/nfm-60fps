package nfm.lit.features.skidmarks;

import static nfm.lit.features.ExtrasCheck.check;
import static nfm.lit.features.ExtrasCheck.n;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.stream.Stream;
import nfm.lit.features.CheckCanvas;
import nfm.lit.features.FeatureSettings;
import nfm.lit.features.Features;
import nfm.lit.features.RaceView;

/**
 * The skidmarks' checks: every maneuver in the owner's table played on a
 * made-up car (the Radical One's wheel places), and what the marks, the mud
 * and the particles then show measured against the requirement's words
 * (effects, "Skidmarks are per wheel", "show each maneuver", "carry the
 * surface's colour", "Skidding leaves particles", "Last race's skid zones").
 */
public final class SkidmarkCheck {

    private static final String F = "skidmarks";
    private static final float DEG = (float) (Math.PI / 180D);
    /* the Radical One's wheels: front left, front right, rear left, rear right */
    private static final int[] KEY_X = {-42, 42, -42, 42};
    private static final int[] KEY_Z = {72, 72, -84, -84};
    private static final int DROP = 20;
    private static final float GROUND = 250F;
    private static final int FL = 0;
    private static final int FR = 1;
    private static final int RL = 2;
    private static final int RR = 3;

    private RaceView v;
    private SkidmarksFeature marks;
    private final float[] p = new float[3];

    private SkidmarkCheck() {
    }

    public static void run() {
        SkidmarkCheck c = new SkidmarkCheck();
        c.oneWheelLocks();
        c.hardBraking();
        c.understeer();
        c.powerSlide();
        c.handbrake180();
        c.burnout();
        c.tBone();
        c.landingWhileRotating();
        c.landingBackwards();
        c.twoWheels();
        c.mudOntoTarmac();
        c.particlesMove();
        c.draws();
        c.zones();
    }

    /* ------------------------------------------------------------ the race */

    private void start() {
        v = new RaceView();
        v.fit(1);
        v.cars = 1;
        v.humans = 1;
        v.seed = 7L;
        v.stage = 3;
        v.stageName = "Check";
        v.groundColor = 0x8c6a3c;
        v.keepsFiles = false;
        v.tick = 0;
        v.model[0] = 0;
        v.topSpeed[0] = 60F;
        v.radius[0] = 110;
        v.wheelDrop[0] = DROP;
        for (int w = 0; w < RaceView.WHEELS; w++) {
            v.keyX[w] = KEY_X[w];
            v.keyZ[w] = KEY_Z[w];
        }
        marks = new SkidmarksFeature();
        marks.raceStarted(v);
    }

    /**
     * The car at {@code (x, z)} on the flat ground heading {@code xz}
     * degrees, moving {@code (vx, vz)} a tick with its engine at {@code sp},
     * front wheels steered {@code steer}: every wheel down on the road.
     */
    private void place(float x, float z, float xz, float vx, float vz, float sp, int steer) {
        v.x[0] = x;
        v.z[0] = z;
        v.y[0] = GROUND - DROP;
        v.xz[0] = Math.round(xz);
        v.xy[0] = 0;
        v.zy[0] = 0;
        v.steer[0] = steer;
        v.speed[0] = sp;
        v.vx[0] = vx;
        v.vy[0] = 0F;
        v.vz[0] = vz;
        v.capsized[0] = false;
        v.handbrake[0] = false;
        v.down[0] = false;
        float yaw = v.xz[0] * DEG;
        float rx = (float) Math.cos(yaw);
        float rz = (float) Math.sin(yaw);
        float fx = -(float) Math.sin(yaw);
        float fz = (float) Math.cos(yaw);
        for (int w = 0; w < RaceView.WHEELS; w++) {
            v.toWorld(0, KEY_X[w], DROP, KEY_Z[w], p);
            v.wheelX[w] = p[0];
            v.wheelY[w] = p[1];
            v.wheelZ[w] = p[2];
            v.contact[w] = true;
            v.onRamp[w] = false;
            v.surface[w] = 0;
            v.groundRgb[w] = v.groundColor;
            v.wheelVX[w] = vx;
            v.wheelVZ[w] = vz;
            v.slipSide[w] = vx * rx + vz * rz;
            v.slipLong[w] = sp - (vx * fx + vz * fz);
        }
    }

    /** Moves the car up or down so that wheel {@code w}'s lowest point is on the ground. */
    private void settle(int w) {
        v.toWorld(0, KEY_X[w], DROP, KEY_Z[w], p);
        v.y[0] += GROUND - p[1];
        for (int k = 0; k < RaceView.WHEELS; k++) {
            v.toWorld(0, KEY_X[k], DROP, KEY_Z[k], p);
            v.wheelX[k] = p[0];
            v.wheelY[k] = p[1];
            v.wheelZ[k] = p[2];
        }
    }

    /** One real tick ends: the view goes to the feature as the game hands it over. */
    private void tick() {
        v.tick++;
        boolean any = false;
        for (int w = 0; w < RaceView.WHEELS; w++) {
            any |= v.contact[w];
        }
        v.grounded[0] = any;
        v.airTicks[0] = any ? 0 : v.airTicks[0] + 1;
        marks.afterTick(v);
        v.hits = 0;
    }

    /* ------------------------------------------------------- measurements */

    private MarkRing ring() {
        return marks.ring;
    }

    private int pieces(int wheel) {
        int n = 0;
        for (int i = 0; i < ring().count; i++) {
            if (ring().wheel[ring().slot(i)] == wheel) {
                n++;
            }
        }
        return n;
    }

    private boolean hasTick(int wheel, int tick) {
        for (int i = 0; i < ring().count; i++) {
            int q = ring().slot(i);
            if (ring().wheel[q] == wheel && ring().tick[q] == tick) {
                return true;
            }
        }
        return false;
    }

    private int firstTick(int wheel) {
        int t = Integer.MAX_VALUE;
        for (int i = 0; i < ring().count; i++) {
            int q = ring().slot(i);
            if (ring().wheel[q] == wheel) {
                t = Math.min(t, ring().tick[q]);
            }
        }
        return t;
    }

    private int lastTick(int wheel) {
        int t = Integer.MIN_VALUE;
        for (int i = 0; i < ring().count; i++) {
            int q = ring().slot(i);
            if (ring().wheel[q] == wheel) {
                t = Math.max(t, ring().tick[q]);
            }
        }
        return t;
    }

    /** The length of wheel {@code wheel}'s marks: the sum of its pieces' lengths. */
    private float length(int wheel) {
        float sum = 0F;
        for (int i = 0; i < ring().count; i++) {
            int q = ring().slot(i);
            if (ring().wheel[q] == wheel) {
                float sx = (ring().cornerX[q * 4] + ring().cornerX[q * 4 + 1]) / 2F;
                float sz = (ring().cornerZ[q * 4] + ring().cornerZ[q * 4 + 1]) / 2F;
                float ex = (ring().cornerX[q * 4 + 2] + ring().cornerX[q * 4 + 3]) / 2F;
                float ez = (ring().cornerZ[q * 4 + 2] + ring().cornerZ[q * 4 + 3]) / 2F;
                sum += (float) Math.hypot(ex - sx, ez - sz);
            }
        }
        return sum;
    }

    private float meanCover(int wheel) {
        float sum = 0F;
        int n = 0;
        for (int i = 0; i < ring().count; i++) {
            int q = ring().slot(i);
            if (ring().wheel[q] == wheel) {
                sum += ring().cover[q];
                n++;
            }
        }
        return n == 0 ? 0F : sum / n;
    }

    private float maxCover(int wheel) {
        float m = 0F;
        for (int i = 0; i < ring().count; i++) {
            int q = ring().slot(i);
            if (ring().wheel[q] == wheel) {
                m = Math.max(m, ring().cover[q]);
            }
        }
        return m;
    }

    /** The cover of wheel {@code wheel}'s oldest piece. */
    private float firstCover(int wheel) {
        for (int i = 0; i < ring().count; i++) {
            int q = ring().slot(i);
            if (ring().wheel[q] == wheel) {
                return ring().cover[q];
            }
        }
        return 0F;
    }

    /** The mean distance of wheel {@code wheel}'s pieces from {@code (cx, cz)}. */
    private float radius(int wheel, float cx, float cz) {
        float sum = 0F;
        int n = 0;
        for (int i = 0; i < ring().count; i++) {
            int q = ring().slot(i);
            if (ring().wheel[q] == wheel) {
                sum += (float) Math.hypot(midX(q) - cx, midZ(q) - cz);
                n++;
            }
        }
        return n == 0 ? 0F : sum / n;
    }

    /** The mean angle (degrees) the tyre pointed at along wheel {@code wheel}'s pieces. */
    private float headAngle(int wheel) {
        double sx = 0;
        double sz = 0;
        for (int i = 0; i < ring().count; i++) {
            int q = ring().slot(i);
            if (ring().wheel[q] == wheel) {
                sx += ring().headX[q];
                sz += ring().headZ[q];
            }
        }
        return (float) Math.toDegrees(Math.atan2(sx, sz));
    }

    private float midX(int q) {
        return (ring().cornerX[q * 4] + ring().cornerX[q * 4 + 2]) / 2F;
    }

    private float midZ(int q) {
        return (ring().cornerZ[q * 4] + ring().cornerZ[q * 4 + 2]) / 2F;
    }

    /* ---------------------------------------------------------- maneuvers */

    /** One wheel locks while the other three roll: only it marks, and its mark ends where it rolls cleanly. */
    private void oneWheelLocks() {
        start();
        int clean = -1;
        for (int t = 0; t < 30; t++) {
            place(0F, 30F * t, 0F, 0F, 30F, 30F, 0);
            if (t >= 5 && t <= 12) {
                v.slipLong[FL] = -24F; // dragged: the tread stands still
            } else if (t == 13) {
                clean = v.tick + 1;
            }
            tick();
        }
        int others = pieces(FR) + pieces(RL) + pieces(RR);
        boolean ok = pieces(FL) > 0 && others == 0 && lastTick(FL) <= clean + 1 && !ring().isOpen(FL);
        check(F, "one wheel locks", ok, "locked wheel " + pieces(FL) + " pieces, last at tick " + lastTick(FL)
                + ", rolls cleanly from tick " + clean + "; the other wheels " + others + " pieces");
    }

    /** Hard braking: a faint onset deepens as the wheels lock, and each mark ends when its wheel rolls again, still braking. */
    private void hardBraking() {
        start();
        float sp = 40F;
        float speed = 40F;
        float z = 0F;
        int clean = -1;
        for (int t = 0; t < 30; t++) {
            if (t >= 5) {
                sp = Math.max(10F, sp - 8F);
                speed = Math.max(sp, speed - 3F); // grip lets the car slow 3 a tick: the wheels lock
            }
            z += speed;
            place(0F, z, 0F, 0F, speed, sp, 0);
            v.down[0] = t >= 5;
            if (clean < 0 && t > 5 && speed == sp) {
                clean = v.tick + 1;
            }
            tick();
        }
        boolean ok = true;
        StringBuilder m = new StringBuilder();
        for (int w = 0; w < RaceView.WHEELS; w++) {
            boolean deepens = firstCover(w) < 0.5F * maxCover(w);
            boolean ends = lastTick(w) < clean + 2;
            ok &= pieces(w) > 0 && deepens && ends;
            m.append("wheel ").append(w).append(": onset ").append(n(firstCover(w))).append(" to ")
                    .append(n(maxCover(w))).append(", last at ").append(lastTick(w)).append("; ");
        }
        m.append("rolls cleanly from tick ").append(clean).append(", still braking");
        check(F, "hard braking", ok, m.toString());
    }

    /** Understeer: the nose pushes wide; the front tyres scrub two arcs, the outside one darker, the rear none. */
    private void understeer() {
        start();
        float r = 2400F;
        float speed = 60F;
        float beta = 6F;
        float psi = 0F;
        for (int t = 0; t < 40; t++) {
            // the path turns left round (0, 0); the car's nose points beta further left than its path
            float yaw = psi * DEG;
            float x = r * (float) Math.cos(yaw);
            float z = r * (float) Math.sin(yaw);
            float vx = -speed * (float) Math.sin(yaw);
            float vz = speed * (float) Math.cos(yaw);
            place(x, z, psi + beta, vx, vz, speed, 30);
            tick();
            psi += speed / r / DEG;
        }
        boolean fronts = pieces(FL) > 10 && pieces(FR) > 10;
        boolean rears = pieces(RL) == 0 && pieces(RR) == 0;
        boolean darker = meanCover(FR) > meanCover(FL);
        check(F, "understeer", fronts && rears && darker, "front left " + pieces(FL) + " pieces at " + n(meanCover(FL))
                + ", front right (outside) " + pieces(FR) + " at " + n(meanCover(FR)) + "; rear "
                + (pieces(RL) + pieces(RR)) + " pieces");
    }

    /** A power slide: the rear's arc is wider than the front's, the loaded outside wheel stronger; countersteering turns the front traces alone. */
    private void powerSlide() {
        float[] into = slide(15);
        float rearRadius = radius(RR, 0F, 0F);
        float frontRadius = radius(FR, 0F, 0F);
        boolean wider = rearRadius > frontRadius && pieces(RR) > 10 && pieces(FR) > 10;
        boolean loaded = meanCover(RR) > meanCover(RL) && pieces(RL) > 0;
        float[] counter = slide(-8);
        float frontTurn = Math.abs(SkidSlip.wrap(into[0] - counter[0]));
        float rearTurn = Math.abs(SkidSlip.wrap(into[1] - counter[1]));
        boolean own = frontTurn > 15F && rearTurn < 1F && pieces(FR) > 0;
        check(F, "power slide: the rear's arc wider", wider, "rear outside " + n(rearRadius) + ", front outside "
                + n(frontRadius) + " from the turn's centre");
        check(F, "power slide: the loaded outside wheel stronger", loaded, "rear outside cover " + n(meanCover(RR))
                + ", inside " + n(meanCover(RL)));
        check(F, "power slide: countersteering turns the front traces alone", own, "front traces turned "
                + n(frontTurn) + " degrees, rear " + n(rearTurn));
    }

    /** A left power slide round (0, 0), steering {@code steer}; returns the front and rear outside traces' angles. */
    private float[] slide(int steer) {
        start();
        float r = 1500F;
        float speed = 40F;
        float beta = 25F;
        float psi = 0F;
        for (int t = 0; t < 40; t++) {
            float yaw = psi * DEG;
            float x = r * (float) Math.cos(yaw);
            float z = r * (float) Math.sin(yaw);
            float vx = -speed * (float) Math.sin(yaw);
            float vz = speed * (float) Math.cos(yaw);
            place(x, z, psi + beta, vx, vz, speed, steer);
            tick();
            psi += speed / r / DEG;
        }
        return new float[] {headAngle(FR), headAngle(RR)};
    }

    /** A handbrake 180: the rear draws two hooks, the fronts roll fairly cleanly (their marks under 30% of the rear's). */
    private void handbrake180() {
        start();
        float speed = 40F;
        float z = 0F;
        for (int t = 0; t < 30; t++) {
            float xz = Math.min(23, t) * 8F;
            z += speed;
            place(0F, z, xz, 0F, speed, 40F, 36);
            v.handbrake[0] = true;
            tick();
            speed *= 0.97F;
        }
        float rear = length(RL) + length(RR);
        float front = length(FL) + length(FR);
        check(F, "handbrake 180", rear > 0F && front < 0.3F * rear, "rear marks " + n(rear) + " long, front "
                + n(front) + " (" + n(rear > 0F ? 100F * front / rear : 0F) + "%)");
    }

    /** A stationary burnout: the driven wheels darken two spots, which stretch into streaks as the car hooks up. */
    private void burnout() {
        start();
        float speed = 0F;
        float z = 0F;
        float spot = 0F;
        int spotPieces = 0;
        for (int t = 0; t < 24; t++) {
            if (t >= 10) {
                speed = Math.min(25F, speed + 3F);
            }
            z += speed;
            place(0F, z, 0F, 0F, speed, 25F, 0);
            tick();
            if (t == 9) {
                spot = maxCover(RL);
                spotPieces = pieces(RL);
            }
        }
        boolean spots = spotPieces == 1 && spot >= 0.8F;
        boolean streaks = length(RL) >= 60F && length(RR) >= 60F;
        boolean fronts = pieces(FL) + pieces(FR) == 0;
        check(F, "stationary burnout", spots && streaks && fronts, "standing: " + spotPieces + " spot darkened to "
                + n(spot) + "; moving: streaks " + n(length(RL)) + " and " + n(length(RR)) + " long; fronts "
                + (pieces(FL) + pieces(FR)) + " pieces");
    }

    /** T-boned: never braking, the car leaves four short sideways scrubs that start at the impact. */
    private void tBone() {
        start();
        float x = 0F;
        float z = 0F;
        int impact = -1;
        float shove = 0F;
        for (int t = 0; t < 25; t++) {
            if (t == 10) {
                shove = 8F;
                impact = v.tick + 1;
            }
            x += shove;
            z += 20F;
            place(x, z, 0F, shove, 20F, 20F, 0);
            if (t == 10) {
                v.hits = 1;
                v.hitCar[0] = 0;
                v.hitWheel[0] = FR;
                v.hitAxis[0] = 0;
                v.hitForce[0] = 120F;
            }
            tick();
            shove = Math.max(0F, shove - 2F);
        }
        boolean ok = true;
        StringBuilder m = new StringBuilder("impact at tick " + impact + ": ");
        for (int w = 0; w < RaceView.WHEELS; w++) {
            ok &= firstTick(w) == impact && length(w) < 250F;
            m.append("wheel ").append(w).append(" from ").append(firstTick(w)).append(", ").append(n(length(w)))
                    .append(" long; ");
        }
        check(F, "T-boned", ok, m.toString());
    }

    /** Landing turning and crooked: the first wheel down marks first, and a bounce leaves a gap as long as it is off. */
    private void landingWhileRotating() {
        start();
        float z = 0F;
        int landed = -1;
        boolean closedInBounce = true;
        for (int t = 0; t < 16; t++) {
            float xz = Math.min(t, 9) * 6F;
            z += 30F;
            place(0F, z, xz, 0F, 30F, 30F, 0);
            boolean air = t < 6;
            for (int w = 0; w < RaceView.WHEELS; w++) {
                v.contact[w] = !air;
            }
            if (t == 6) {
                landed = v.tick + 1;
                v.contact[FR] = false;
                v.contact[RL] = false;
                v.contact[RR] = false; // only the front left is down
            }
            if (t == 8 || t == 9) {
                v.contact[FL] = false; // it bounces
            }
            tick();
            if (t == 8 || t == 9) {
                closedInBounce &= !ring().isOpen(FL);
            }
        }
        boolean first = firstTick(FL) == landed && firstTick(FR) > landed && firstTick(RL) > landed
                && firstTick(RR) > landed;
        boolean gap = hasTick(FL, landed) && !hasTick(FL, landed + 2) && !hasTick(FL, landed + 3)
                && hasTick(FL, landed + 4) && closedInBounce;
        check(F, "landing: the first wheel down marks first", first, "landed at tick " + landed + "; first marks at "
                + firstTick(FL) + ", " + firstTick(FR) + ", " + firstTick(RL) + ", " + firstTick(RR));
        check(F, "landing: a bounce leaves a gap", gap, "front left off at ticks " + (landed + 2) + " and "
                + (landed + 3) + ": marks then " + hasTick(FL, landed + 2) + ", " + hasTick(FL, landed + 3)
                + ", again at " + (landed + 4) + ": " + hasTick(FL, landed + 4));
    }

    /** Landing backwards with the tyres spinning forwards: a dramatic scrub, smoking. */
    private void landingBackwards() {
        int landed = backwardsLanding();
        boolean ok = maxCover(RL) >= 0.8F && maxCover(RR) >= 0.8F && lastTick(RL) >= landed + 3
                && marks.particles.count() > 0 && kindOf(RL) == SkidSlip.SPIN;
        check(F, "landing backwards, tyres spinning", ok, "rear marks to cover " + n(maxCover(RL)) + " and "
                + n(maxCover(RR)) + ", kind " + kindOf(RL) + ", " + marks.particles.count() + " particles of smoke");
    }

    /** The car comes down facing backwards, its tyres spinning forwards; returns the tick it lands. */
    private int backwardsLanding() {
        start();
        float z = 0F;
        int landed = -1;
        for (int t = 0; t < 10; t++) {
            z += 30F;
            place(0F, z, 180F, 0F, 30F, 30F, 0);
            boolean air = t < 5;
            for (int w = 0; w < RaceView.WHEELS; w++) {
                v.contact[w] = !air;
            }
            if (t == 5) {
                landed = v.tick + 1;
            }
            tick();
        }
        return landed;
    }

    private int kindOf(int wheel) {
        for (int i = ring().count - 1; i >= 0; i--) {
            int q = ring().slot(i);
            if (ring().wheel[q] == wheel) {
                return ring().kind[q];
            }
        }
        return -1;
    }

    /** On two wheels only the loaded side marks; rolled further, a metal scrape appears beside the rubber, sparking. */
    private void twoWheels() {
        start();
        float z = 0F;
        int metal = 1 * RaceView.WHEELS; // the body's scrape (cars * 4 + car)
        int rolledAt = -1;
        for (int t = 0; t < 20; t++) {
            z += 30F;
            place(0F, z, 0F, 10F, 30F, 30F, 0);
            v.xy[0] = t < 12 ? 35 : 60; // rolled onto the right wheels
            settle(FR);
            v.contact[FL] = false;
            v.contact[RL] = false;
            if (t == 12) {
                rolledAt = v.tick + 1;
            }
            tick();
        }
        boolean loaded = pieces(FR) > 0 && pieces(RR) > 0 && pieces(FL) == 0 && pieces(RL) == 0;
        int scrape = pieces(metal);
        boolean sparks = false;
        for (int i = 0; i < marks.particles.capacity(); i++) {
            sparks |= marks.particles.isSpark(i);
        }
        float beside = Float.MAX_VALUE;
        for (int i = 0; i < ring().count; i++) {
            int q = ring().slot(i);
            if (ring().wheel[q] != metal) {
                continue;
            }
            for (int k = 0; k < ring().count; k++) {
                int r = ring().slot(k);
                if (ring().wheel[r] == FR || ring().wheel[r] == RR) {
                    beside = Math.min(beside, (float) Math.hypot(midX(q) - midX(r), midZ(q) - midZ(r)));
                }
            }
        }
        boolean beyond = firstTick(metal) >= rolledAt;
        check(F, "two wheels: only the loaded side marks", loaded, "right " + pieces(FR) + " and " + pieces(RR)
                + " pieces, left " + pieces(FL) + " and " + pieces(RL));
        check(F, "two wheels: rolled further, a metal scrape beside", scrape > 0 && beyond && beside >= 4F && sparks,
                scrape + " scrape pieces from tick " + firstTick(metal) + " (rolled at " + rolledAt + "), "
                        + n(beside) + " from the rubber, sparks " + sparks);
    }

    /** From mud onto tarmac: the first marks on the tarmac carry the mud's colour, fading to plain rubber within 30 m. */
    private void mudOntoTarmac() {
        start();
        int mud = 0x8c6a3c;
        float x = 0F;
        float z = 0F;
        float edgeX = 0F;
        float edgeZ = 0F;
        int onTarmac = -1;
        for (int t = 0; t < 90; t++) {
            x += 10F;
            z += 30F;
            place(x, z, 0F, 10F, 30F, 30F, 0);
            boolean loose = t < 15;
            for (int w = 0; w < RaceView.WHEELS; w++) {
                v.surface[w] = loose ? 1 : 0;
                v.groundRgb[w] = loose ? mud : 0x707070;
            }
            if (t == 15) {
                onTarmac = v.tick + 1;
                edgeX = v.wheelX[RR];
                edgeZ = v.wheelZ[RR];
            }
            tick();
        }
        int firstOff = 0;
        int farOff = 0;
        boolean anyFar = false;
        for (int i = 0; i < ring().count; i++) {
            int q = ring().slot(i);
            if (ring().wheel[q] != RR || ring().tick[q] < onTarmac) {
                continue;
            }
            int off = channelGap(ring().rgb[q], SkidmarksFeature.RUBBER[ring().kind[q]]);
            float d = (float) Math.hypot(midX(q) - edgeX, midZ(q) - edgeZ);
            if (ring().tick[q] == onTarmac) {
                firstOff = off;
            }
            if (d >= 1800F) {
                anyFar = true;
                farOff = Math.max(farOff, off);
            }
        }
        check(F, "mud onto tarmac", firstOff >= 12 && anyFar && farOff <= 3, "first tarmac mark " + firstOff
                + " levels from rubber, 30 m on at most " + farOff);
    }

    private static int channelGap(int a, int b) {
        int r = Math.abs((a >> 16 & 0xff) - (b >> 16 & 0xff));
        int g = Math.abs((a >> 8 & 0xff) - (b >> 8 & 0xff));
        int bl = Math.abs((a & 0xff) - (b & 0xff));
        return Math.max(r, Math.max(g, bl));
    }

    /** Particles come off the sliding wheels and move on their own. */
    private void particlesMove() {
        backwardsLanding();
        int live = -1;
        for (int i = 0; i < marks.particles.capacity() && live < 0; i++) {
            if (marks.particles.position(i, p)) {
                live = i;
            }
        }
        float moved = 0F;
        if (live >= 0) {
            float x = p[0];
            float y = p[1];
            float z = p[2];
            marks.particles.tick(0F, 0F);
            if (marks.particles.position(live, p)) {
                moved = (float) Math.sqrt((p[0] - x) * (p[0] - x) + (p[1] - y) * (p[1] - y) + (p[2] - z) * (p[2] - z));
            }
        }
        check(F, "particles", live >= 0 && moved > 0.1F, marks.particles.count() + " particles; one moved "
                + n(moved) + " in a tick with no car near");
    }

    /** The marks are drawn: the full detail adds streaks and striations to the simple strips. */
    private void draws() {
        slide(15);
        CheckCanvas canvas = new CheckCanvas();
        float ex = v.wheelX[RR];
        float ez = v.wheelZ[RR] - 600F;
        FeatureSettings.set(SkidmarksFeature.DETAIL, 0);
        int simple = canvas.frame(marks, ex, GROUND - 300F, ez, v.tick);
        FeatureSettings.set(SkidmarksFeature.DETAIL, 1);
        int full = canvas.frame(marks, ex, GROUND - 300F, ez, v.tick);
        check(F, "drawn", simple > 0 && full > simple, "simple " + simple + " polygons, full " + full);
    }

    /* -------------------------------------------------------------- zones */

    /** The second race on a stage shows last race's heaviest sliding as darker squares; with the toggle off, none. */
    private void zones() {
        Path dir = null;
        try {
            dir = Files.createTempDirectory("nfm-skid-heat");
            start();
            v.cacheDir = dir;
            v.keepsFiles = true;
            SkidZonesFeature first = new SkidZonesFeature();
            first.raceStarted(v);
            float r = 1500F;
            float psi = 0F;
            for (int t = 0; t < 40; t++) {
                float yaw = psi * DEG;
                place(r * (float) Math.cos(yaw), r * (float) Math.sin(yaw), psi + 25F, -40F * (float) Math.sin(yaw),
                        40F * (float) Math.cos(yaw), 40F, 15);
                v.tick++;
                v.grounded[0] = true;
                first.afterTick(v);
                psi += 40F / r / DEG;
            }
            int counted = first.cells();
            first.raceEnded(v);
            boolean saved = Files.isRegularFile(SkidZonesFeature.file(v));

            SkidZonesFeature second = new SkidZonesFeature();
            v.tick = 0;
            second.raceStarted(v);
            CheckCanvas canvas = new CheckCanvas();
            int drawn = canvas.frame(second, 1500F, GROUND - 400F, -800F, 1);
            // a square where the car slid (20 degrees round its arc) is among those shown
            float slidX = r * (float) Math.cos(20F * DEG);
            float slidZ = r * (float) Math.sin(20F * DEG);
            boolean found = false;
            for (int c = 0; c < second.shown; c++) {
                found |= Math.hypot(SkidZonesFeature.centreX(second.shownKey[c]) - slidX,
                        SkidZonesFeature.centreZ(second.shownKey[c]) - slidZ) <= 400F;
            }
            check(F, "skid zones: second race shows them", saved && second.shown > 0 && drawn > 0 && found,
                    counted + " squares slid in, file kept " + saved + ", " + second.shown + " shown, " + drawn
                            + " drawn");

            // the toggle: off, the zones are not a feature of the race at all
            FeatureSettings.set(SkidZonesFeature.ID, 0);
            Features.raceStarted(v);
            boolean offOk = !Features.active(SkidZonesFeature.ID);
            Features.leftRace();
            FeatureSettings.set(SkidZonesFeature.ID, 1);
            Features.raceStarted(v);
            boolean onOk = Features.active(SkidZonesFeature.ID);
            Features.leftRace();
            FeatureSettings.set(SkidZonesFeature.ID, 0);
            check(F, "skid zones: the toggle", offOk && onOk, "off: shown " + !offOk + "; on: shown " + onOk);

            // test runs keep no files
            Path other = Files.createTempDirectory("nfm-skid-heat");
            v.cacheDir = other;
            v.keepsFiles = false;
            SkidZonesFeature hidden = new SkidZonesFeature();
            hidden.raceStarted(v);
            place(0F, 0F, 25F, 0F, 40F, 40F, 0);
            for (int t = 0; t < 10; t++) {
                v.tick++;
                hidden.afterTick(v);
            }
            hidden.raceEnded(v);
            boolean none;
            try (Stream<Path> files = Files.walk(other)) {
                none = files.filter(Files::isRegularFile).findAny().isEmpty();
            }
            check(F, "skid zones: test runs keep no file", none && hidden.cells() > 0, "slid in " + hidden.cells()
                    + " squares, files left " + !none);
            delete(other);
        } catch (IOException e) {
            check(F, "skid zones", false, "could not use a temporary folder: " + e);
        } finally {
            if (dir != null) {
                delete(dir);
            }
        }
    }

    private static void delete(Path dir) {
        try (Stream<Path> files = Files.walk(dir)) {
            files.sorted(Comparator.reverseOrder()).forEach(f -> f.toFile().delete());
        } catch (IOException e) {
            // a temporary folder left behind; the system clears it
        }
    }
}
