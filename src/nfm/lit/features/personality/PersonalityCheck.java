package nfm.lit.features.personality;

import static nfm.lit.features.ExtrasCheck.check;
import static nfm.lit.features.ExtrasCheck.n;

import nfm.lit.features.CarLooks;
import nfm.lit.features.CarShape;
import nfm.lit.features.CheckCanvas;
import nfm.lit.features.RaceView;

/**
 * The car personalities' checks: each one played on a made-up car of that
 * model's name beside a car of another model, and what is then seen and
 * heard measured against the requirement's words (car-personality).
 */
public final class PersonalityCheck {

    private static final String F = "personality";
    private static final float GROUND = 250F;
    private static final float DEG = (float) (Math.PI / 180D);
    private static final int DROP = 20;

    private RaceView v;
    private PersonalityFeature f;
    private final float[] p = new float[3];
    private final int[] ix = new int[16];
    private final int[] iy = new int[16];
    private final int[] iz = new int[16];
    private final int[] ox = new int[16];
    private final int[] oy = new int[16];
    private final int[] oz = new int[16];

    private PersonalityCheck() {
    }

    public static void run() {
        PersonalityCheck c = new PersonalityCheck();
        c.shark();
        c.oxide();
        c.kat();
        c.eight();
        c.revenge();
        c.masheen();
        c.monstaa();
        c.caninaro();
        c.king();
    }

    /* ------------------------------------------------------- made-up cars */

    /* a body 40 wide, 100 long, from 14 above the car's middle to 2 above it: polygons 0 to 4 */
    private static final float[][] BODY = {
        {-18, -12, 16, 18, -12, 16, 18, -12, 50, -18, -12, 50}, // the bonnet
        {-16, -12, 15, 16, -12, 15, 13, -24, -2, -13, -24, -2}, // the windscreen
        {-13, -24, -2, 13, -24, -2, 13, -24, -30, -13, -24, -30}, // the roof
        {-20, -12, -50, -20, -12, 50, -20, -2, 50, -20, -2, -50}, // the sides
        {20, -12, -50, 20, -12, 50, 20, -2, 50, 20, -2, -50},
    };
    /* MAX Revenge's blower on the bonnet, and the hole it stands in (the last) */
    private static final float[][] BLOWER = {
        {-3, -19, 20, 3, -19, 20, 3, -19, 28, -3, -19, 28},
        {-3, -19, 28, 3, -19, 28, 3, -13, 28, -3, -13, 28},
        {-3, -19, 20, -3, -19, 28, -3, -13, 28, -3, -13, 20},
        {-3, -12, 19, 3, -12, 19, 3, -12, 29, -3, -12, 29},
    };
    /* a truck: a chassis' deck and side, and a cab over its front half */
    private static final float[][] TRUCK = {
        {-20, 0, -60, 20, 0, -60, 20, 0, 0, -20, 0, 0}, // the deck behind the cab
        {-20, 0, -60, -20, 0, 60, -20, 10, 60, -20, 10, -60}, // the chassis' side
        {-18, -40, 0, 18, -40, 0, 18, 0, 0, -18, 0, 0}, // the cab's back wall
        {-18, -40, 0, 18, -40, 0, 18, -40, 30, -18, -40, 30}, // its roof
        {-18, -40, 30, 18, -40, 30, 18, -18, 60, -18, -18, 60}, // windscreen and bonnet
        {-18, -18, 60, 18, -18, 60, 18, 4, 60, -18, 4, 60}, // the grille
    };
    /* a plow at the nose: its back wall, its floor and its top lip */
    private static final float[][] PLOW = {
        {-22, -8, 42, 22, -8, 42, 22, 6, 41, -22, 6, 41},
        {-22, 6, 41, 22, 6, 41, 22, 8, 50, -22, 8, 50},
        {-22, -8, 42, 22, -8, 42, 22, -10, 49, -22, -10, 49},
    };

    /** A car of {@link #BODY} and {@code more}, with an eight-cornered wheel at each corner (the last four polygons). */
    private static CarShape car(float[][] base, float[][] more, boolean glassSecond) {
        int polys = base.length + more.length + RaceView.WHEELS;
        CarShape s = new CarShape();
        s.fit(polys, polys * 8);
        s.minX = s.minY = s.minZ = Float.MAX_VALUE;
        s.maxX = s.maxY = s.maxZ = -Float.MAX_VALUE;
        int at = 0;
        for (int i = 0; i < base.length + more.length; i++) {
            float[] poly = i < base.length ? base[i] : more[i - base.length];
            s.first[i] = at;
            s.count[i] = poly.length / 3;
            s.rgb[i] = 0xc03020;
            s.glass[i] = glassSecond && i == 1;
            s.wheel[i] = -1;
            for (int k = 0; k < poly.length / 3; k++, at++) {
                s.x[at] = poly[k * 3];
                s.y[at] = poly[k * 3 + 1];
                s.z[at] = poly[k * 3 + 2];
                s.minX = Math.min(s.minX, s.x[at]);
                s.maxX = Math.max(s.maxX, s.x[at]);
                s.minY = Math.min(s.minY, s.y[at]);
                s.maxY = Math.max(s.maxY, s.y[at]);
                s.minZ = Math.min(s.minZ, s.z[at]);
                s.maxZ = Math.max(s.maxZ, s.z[at]);
            }
        }
        for (int w = 0; w < RaceView.WHEELS; w++) {
            int i = base.length + more.length + w;
            s.hubX[w] = (w & 1) == 0 ? -22F : 22F;
            s.hubY[w] = DROP - 12F;
            s.hubZ[w] = w < 2 ? 35F : -35F;
            s.wheelRadius[w] = 12F;
            s.wheelHalfWidth[w] = 4F;
            s.steerX[w] = 0;
            s.first[i] = at;
            s.count[i] = 8;
            s.rgb[i] = 0x202020;
            s.wheel[i] = w;
            for (int k = 0; k < 8; k++, at++) {
                // a corner straight down, one straight up
                double a = k * Math.PI / 4D;
                s.x[at] = s.hubX[w];
                s.y[at] = s.hubY[w] + 12F * (float) Math.cos(a);
                s.z[at] = s.hubZ[w] + 12F * (float) Math.sin(a);
            }
        }
        return s;
    }

    /** A race of two cars on the road at rest: car 0 of model {@code model}, car 1 a Nimi, both shaped {@code s}. */
    private void start(String model, CarShape s) {
        v = new RaceView();
        v.fit(2);
        v.cars = 2;
        v.humans = 1;
        v.seed = 11L;
        v.stage = 3;
        v.stageName = "Check";
        v.groundColor = 0x8c6a3c;
        v.keepsFiles = false;
        v.tick = 0;
        for (int c = 0; c < 2; c++) {
            v.model[c] = c;
            v.modelName[c] = c == 0 ? model : "nimi";
            v.shape[c] = s;
            v.topSpeed[c] = 60F;
            v.radius[c] = 110;
            v.wheelDrop[c] = DROP;
            place(c, c * 400F, 0F, 0F, 0F);
        }
        f = PersonalityFeature.forCheck();
        f.raceStarted(v);
    }

    /** Car {@code c} on the road at {@code (x, z)} heading {@code xz} degrees, going {@code speed} a tick that way, every wheel down. */
    private void place(int c, float x, float z, float xz, float speed) {
        v.x[c] = x;
        v.y[c] = GROUND - DROP;
        v.z[c] = z;
        v.xz[c] = Math.round(xz);
        v.xy[c] = 0;
        v.zy[c] = 0;
        v.speed[c] = speed;
        float yaw = v.xz[c] * DEG;
        v.vx[c] = -speed * (float) Math.sin(yaw);
        v.vy[c] = 0F;
        v.vz[c] = speed * (float) Math.cos(yaw);
        v.grounded[c] = true;
        v.airTicks[c] = 0;
        for (int w = 0; w < RaceView.WHEELS; w++) {
            int at = c * RaceView.WHEELS + w;
            v.contact[at] = true;
            v.surface[at] = 0;
            v.groundRgb[at] = 0x808078;
        }
    }

    /** Car {@code c} in the air, falling {@code fall} a tick, pitched {@code zy} degrees. */
    private void air(int c, float fall, int zy) {
        v.y[c] = GROUND - DROP - 80F;
        v.vy[c] = fall;
        v.zy[c] = zy;
        v.grounded[c] = false;
        v.airTicks[c]++;
        for (int w = 0; w < RaceView.WHEELS; w++) {
            v.contact[c * RaceView.WHEELS + w] = false;
        }
    }

    private void tick() {
        v.tick++;
        f.afterTick(v);
    }

    /** Drives car 0 on for {@code ticks} at {@code speed}, its heading changing {@code turn} degrees a tick. */
    private void drive(int ticks, float speed, float turn) {
        float xz = v.xz[0];
        float x = v.x[0];
        float z = v.z[0];
        for (int t = 0; t < ticks; t++) {
            xz += turn;
            float yaw = xz * DEG;
            x += -speed * (float) Math.sin(yaw);
            z += speed * (float) Math.cos(yaw);
            boolean up = v.up[0];
            boolean down = v.down[0];
            place(0, x, z, xz, speed);
            v.up[0] = up;
            v.down[0] = down;
            tick();
        }
    }

    private Quirk q() {
        return f.quirks[0];
    }

    /** Polygon {@code plane} of car 0's shape, as the game hands its corners to an extra. */
    private int corners(int plane) {
        CarShape s = v.shape[0];
        int count = s.count[plane];
        for (int k = 0; k < count; k++) {
            ix[k] = Math.round(s.x[s.first[plane] + k]);
            iy[k] = Math.round(s.y[s.first[plane] + k]);
            iz[k] = Math.round(s.z[s.first[plane] + k]);
        }
        return count;
    }

    /* ------------------------------------------------------------ the nine */

    /** Tornado Shark: dust at the takeoff and in the first two turns, none in the third. */
    private void shark() {
        start("2000tornados", car(BODY, new float[0][], true));
        for (int t = 0; t < 5; t++) {
            tick();
        }
        int standing = q().puffs;
        int tinted = f.color(0, 0, 0xc03020);
        int glass = f.color(0, 1, 0x9fb4c8);
        drive(40, 30F, 0F);
        int takeoff = q().puffs - standing;
        float afterTakeoff = q().dust;
        drive(30, 30F, 0F);
        int cruising = q().puffs - standing - takeoff;
        int before = q().puffs;
        drive(20, 30F, 4F);
        int first = q().puffs - before;
        drive(15, 30F, 0F);
        before = q().puffs;
        drive(20, 30F, -4F);
        int second = q().puffs - before;
        float left = q().dust;
        drive(15, 30F, 0F);
        before = q().puffs;
        drive(20, 30F, 4F);
        int third = q().puffs - before;
        int clean = f.color(0, 0, 0xc03020);
        boolean ok = standing == 0 && takeoff > 0 && cruising == 0 && first > 0 && second > 0 && third == 0
                && tinted != 0xc03020 && glass == 0x9fb4c8 && clean == 0xc03020 && left == 0F
                && f.quirks[1].puffs == 0 && f.color(1, 0, 0xc03020) == 0xc03020;
        check(F, "Tornado Shark starts dusty", ok, standing + " puffs of dust standing on the grid, its paint "
                + (tinted != 0xc03020 ? "dusted" : "clean") + "; " + takeoff + " at the takeoff ("
                + n(afterTakeoff) + " of the dust left), " + cruising + " driving straight on, " + first
                + " in the first turn, " + second + " in the second (" + n(left) + " left, its paint "
                + (clean == 0xc03020 ? "clean" : "dusted") + "), " + third + " in the third; "
                + f.quirks[1].puffs + " from the car of another model");
    }

    /** Lead Oxide: driven hard, heat shows over its nose now and then; driven gently, never. */
    private void oxide() {
        start("leadoxide", car(BODY, new float[0][], true));
        v.up[0] = true;
        float ahead = Float.MAX_VALUE;
        int firstAt = -1;
        int lastShown = 0;
        int gaps = 0;
        int longest = 0;
        int since = 0;
        for (int t = 0; t < 19 * 40; t++) {
            int puffs = q().puffs;
            drive(1, 54F, 0F);
            v.up[0] = true;
            if (q().puffs > puffs && firstAt < 0) {
                firstAt = t;
                // the newest puff: how far ahead of the car's middle it started
                for (int i = 0; i < f.particles.capacity(); i++) {
                    if (f.particles.position(i, p)) {
                        ahead = Math.min(ahead, p[2] - v.z[0]);
                    }
                }
            }
            if (q().shown > lastShown) {
                lastShown = q().shown;
                if (lastShown > 1) {
                    gaps++;
                    longest = Math.max(longest, since);
                }
                since = 0;
            }
            since++;
        }
        int hard = q().shown;
        start("leadoxide", car(BODY, new float[0][], true));
        v.up[0] = true;
        for (int t = 0; t < 19 * 40; t++) {
            drive(1, 18F, 0F);
            v.up[0] = true;
        }
        int gentle = q().shown;
        boolean ok = hard >= 3 && firstAt > 19 * 3 && ahead > 0F && gentle == 0 && longest <= 19 * 12;
        check(F, "Lead Oxide shows heat at the front", ok, "driven hard for 40 s its heat showed " + hard
                + " times, first after " + n(firstAt / 19F) + " s, at most " + n(longest / 19F)
                + " s apart, starting " + n(ahead) + " ahead of the car's middle; driven gently " + gentle
                + " times");
    }

    /** Kool Kat: a landing on all four dips the body and is heard; a landing on two does neither. */
    private void kat() {
        start("koolkat", car(BODY, new float[0][], true));
        drive(5, 30F, 0F);
        for (int t = 0; t < 10; t++) {
            air(0, 12F, 0);
            tick();
        }
        float deepest = 0F;
        int bodyDown = 0;
        boolean wheelMoved = false;
        for (int t = 0; t < 45; t++) {
            drive(1, 30F, 0F);
            if (q().dip > deepest) {
                deepest = q().dip;
                int count = corners(0);
                f.queue(new nfm.lit.features.DrawQueue());
                bodyDown = f.body(0, 0, ix, iy, iz, count, ox, oy, oz) ? oy[0] - iy[0] : 0;
                int wheel = v.shape[0].planes - 1;
                wheelMoved = f.body(0, wheel, ix, iy, iz, corners(wheel), ox, oy, oz);
            }
        }
        int thonks = f.thonks;
        float after = q().dip;
        // on two wheels: the others come down four ticks later
        for (int t = 0; t < 10; t++) {
            air(0, 12F, 0);
            tick();
        }
        for (int t = 0; t < 4; t++) {
            place(0, v.x[0], v.z[0] + 30F, 0F, 30F);
            v.contact[2] = false;
            v.contact[3] = false;
            tick();
        }
        drive(10, 30F, 0F);
        boolean ok = thonks == 1 && deepest > 1F && bodyDown >= 1 && !wheelMoved && after == 0F && f.thonks == 1
                && f.quirks[1].events == 0;
        check(F, "Kool Kat lands heavily", ok, "landing on all four: " + thonks + " thonk, the body dips "
                + n(deepest) + " (drawn " + bodyDown + " lower, the wheels " + (wheelMoved ? "moved" : "where they were")
                + ") and is back up after two seconds (" + n(after) + "); landing on two wheels: "
                + (f.thonks - thonks) + " thonks");
    }

    /** Mighty Eight: a lift after a second of throttle blows the radiators; its nitro's flame is far longer than another car's. */
    private void eight() {
        start("audir8", car(BODY, new float[0][], true));
        v.up[0] = true;
        drive(30, 40F, 0F);
        int before = q().puffs;
        v.up[0] = false;
        drive(12, 40F, 0F);
        int lift = q().puffs - before;
        v.up[0] = true;
        drive(5, 40F, 0F);
        before = q().puffs;
        v.up[0] = false;
        drive(12, 40F, 0F);
        int blip = q().puffs - before;
        CheckCanvas canvas = new CheckCanvas();
        canvas.frame(f, v.x[0], GROUND - 200F, v.z[0] - 500F, v.tick);
        int cold = f.drawnFlames;
        v.nitrous[0] = true;
        v.nitrous[1] = true;
        drive(2, 40F, 0F);
        canvas.frame(f, v.x[0], GROUND - 200F, v.z[0] - 500F, v.tick);
        int burning = f.drawnFlames;
        float reach = Math.min(q().flame[0], q().flame[1]);
        boolean ok = lift > 0 && blip == 0 && cold == 0 && burning == 8 && reach > 15F * 1.5F
                && !f.quirks[1].burns && f.quirks[1].puffs == 0;
        check(F, "Mighty Eight breathes and burns big", ok, lift + " light puffs from its radiators on lifting off"
                + " after a second and a half of throttle, " + blip + " after a blip; burning nitro " + burning
                + " flame polygons (" + cold + " without), the flames at least " + n(reach)
                + " long (another car's is 15); the car of another model burning nitro: none of these");
    }

    /** MAX Revenge: the blower, and only the blower, shakes under throttle. */
    private void revenge() {
        CarShape s = car(BODY, BLOWER, true);
        start("maxrevenge", s);
        int found = 0;
        for (int i = 0; i < s.planes; i++) {
            found += q().part[i] ? 1 : 0;
        }
        int blower = BODY.length;
        int hole = BODY.length + BLOWER.length - 1;
        v.up[0] = true;
        int moved = 0;
        int farthest = 0;
        boolean bonnetMoved = false;
        for (int t = 0; t < 20; t++) {
            drive(1, 40F, 0F);
            v.up[0] = true;
            int count = corners(blower);
            if (f.body(0, blower, ix, iy, iz, count, ox, oy, oz)) {
                int d = Math.max(Math.abs(ox[0] - ix[0]), Math.abs(oy[0] - iy[0]));
                moved += d > 0 ? 1 : 0;
                farthest = Math.max(farthest, d);
            }
            bonnetMoved |= f.body(0, 0, ix, iy, iz, corners(0), ox, oy, oz);
            bonnetMoved |= f.body(0, hole, ix, iy, iz, corners(hole), ox, oy, oz);
        }
        v.up[0] = false;
        drive(25, 40F, 0F);
        boolean still = !f.body(0, blower, ix, iy, iz, corners(blower), ox, oy, oz);
        boolean ok = found == BLOWER.length - 1 && q().part[blower] && !q().part[hole] && moved >= 8
                && farthest >= 1 && farthest <= 3 && !bonnetMoved && still;
        check(F, "MAX Revenge's turbocharger moves", ok, "the blower found on the model: " + found
                + " polygons (not the hole it stands in); at full throttle it is drawn off its place in " + moved
                + " of 20 ticks, at most " + farthest + " (it is " + n(2F * q().half) + " wide), the bonnet "
                + (bonnetMoved ? "moved" : "still") + "; off the throttle it is " + (still ? "still" : "moving"));
    }

    /** M A S H E E N: dirt from off-road sits in the plow, rides along on the road and tumbles out when the nose goes down in the air. */
    private void masheen() {
        CarShape s = car(BODY, PLOW, true);
        start("masheen", s);
        int found = 0;
        for (int i = 0; i < s.planes; i++) {
            found += q().part[i] ? 1 : 0;
        }
        CheckCanvas canvas = new CheckCanvas();
        canvas.frame(f, v.x[0], GROUND - 200F, v.z[0] + 400F, v.tick);
        int empty = f.drawnDirt;
        for (int t = 0; t < 19 * 5; t++) {
            place(0, 0F, v.z[0] + 20F, 0F, 20F);
            for (int w = 0; w < RaceView.WHEELS; w++) {
                v.surface[w] = 1;
                v.groundRgb[w] = 0x8c6a3c;
            }
            tick();
        }
        float filled = q().dirt;
        drive(19 * 10, 30F, 0F);
        float carried = q().dirt;
        canvas.frame(f, v.x[0], GROUND - 200F, v.z[0] + 400F, v.tick);
        int ahead = f.drawnDirt;
        canvas.frame(f, v.x[0], GROUND - 100F, v.z[0] - 400F, v.tick);
        int behind = f.drawnDirt;
        // which way is nose down: the nose's own height says
        v.zy[0] = -20;
        v.toWorld(0, 0F, 0F, 100F, p);
        int down = p[1] > v.y[0] ? -20 : 20;
        for (int t = 0; t < 8; t++) {
            air(0, 2F, -down);
            v.z[0] += 30F;
            tick();
        }
        int noseUp = f.tipped;
        v.airTicks[0] = 0;
        for (int t = 0; t < 8; t++) {
            air(0, 2F, down);
            v.z[0] += 30F;
            tick();
        }
        int tipped = f.tipped;
        float left = q().dirt;
        drive(30, 30F, 0F);
        int landed = f.landed;
        int lying = f.lying.count();
        drive(19 * 10, 30F, 0F);
        int still = f.lying.count();
        f.lying.position(f.lying.slot(0), p);
        canvas.frame(f, p[0], GROUND - 200F, p[2] - 300F, v.tick);
        boolean drawn = canvas.polygons > 0;
        boolean ok = found == PLOW.length && empty == 0 && filled > 0.95F && carried == filled && ahead == 3
                && behind == 0 && noseUp == 0 && tipped == 3 && left == 0F && landed == 3 && lying == 3 && still == 3
                && drawn;
        check(F, "M A S H E E N's plow carries dirt", ok, "the plow found on the model: " + found + " polygons; "
                + empty + " polygons of dirt in it at the start, after 5 s off-road it is " + n(filled)
                + " full and still " + n(carried) + " after 10 s on the road: " + ahead
                + " polygons seen from ahead, " + behind + " from behind; in the air nose up " + noseUp
                + " chunks, nose down " + tipped + " tumble out (" + n(left) + " left in the plow), " + landed
                + " land, " + lying + " lie on the road and " + still + " are still there 10 s later");
    }

    /** DR Monstaa: the one tyre it lands on lies flat on the ground for a moment; the others stay round. */
    private void monstaa() {
        CarShape s = car(BODY, new float[0][], true);
        start("drmonster", s);
        drive(5, 30F, 0F);
        for (int t = 0; t < 8; t++) {
            air(0, 10F, 0);
            tick();
        }
        // it comes down on its rear right wheel alone, rolled over to that side
        place(0, v.x[0], v.z[0] + 30F, 0F, 30F);
        for (int w = 0; w < 3; w++) {
            v.contact[w] = false;
        }
        v.xy[0] = 0;
        tick();
        int wheels = s.planes - RaceView.WHEELS;
        float hubY = s.hubY[3];
        CarLooks.spin = 0;
        CarLooks.turn = 0;
        CarLooks.baked = false;
        boolean bent = f.bend(0, wheels + 3, ix, iy, iz, corners(wheels + 3), ox, oy, oz);
        boolean baked = CarLooks.baked;
        int lowest = Integer.MIN_VALUE;
        int flat = 0;
        int widest = 0;
        for (int k = 0; k < 8 && bent; k++) {
            lowest = Math.max(lowest, oy[k]);
        }
        for (int k = 0; k < 8 && bent; k++) {
            if (oy[k] == lowest) {
                flat++;
                widest = Math.max(widest, Math.abs(oz[k] - Math.round(s.hubZ[3])));
            }
        }
        int top = Integer.MAX_VALUE;
        for (int k = 0; k < 8 && bent; k++) {
            top = Math.min(top, oy[k]);
        }
        // spun an eighth of a turn and a bit, the flat is still on the ground
        CarLooks.spin = 60;
        boolean spun = f.bend(0, wheels + 3, ix, iy, iz, corners(wheels + 3), ox, oy, oz);
        int lowestSpun = Integer.MIN_VALUE;
        for (int k = 0; k < 8 && spun; k++) {
            lowestSpun = Math.max(lowestSpun, oy[k]);
        }
        boolean othersRound = true;
        for (int w = 0; w < 3; w++) {
            othersRound &= !f.bend(0, wheels + w, ix, iy, iz, corners(wheels + w), ox, oy, oz);
        }
        drive(10, 30F, 0F);
        boolean back = !f.bend(0, wheels + 3, ix, iy, iz, corners(wheels + 3), ox, oy, oz);
        // a landing on all four squashes none
        for (int t = 0; t < 8; t++) {
            air(0, 10F, 0);
            tick();
        }
        drive(1, 30F, 0F);
        boolean four = false;
        for (int w = 0; w < RaceView.WHEELS; w++) {
            four |= f.bend(0, wheels + w, ix, iy, iz, corners(wheels + w), ox, oy, oz);
        }
        int ground = Math.round(hubY + s.wheelRadius[3]);
        boolean ok = bent && baked && flat >= 3 && lowest == ground && widest > 8 && top > Math.round(hubY) - 12
                && lowestSpun == ground
                && othersRound && back && !four;
        check(F, "DR Monstaa's tyres squash", ok, "landing on one wheel: that tyre is drawn with " + flat
                + " of its 8 corners flat on the ground (its lowest at " + lowest + ", the ground at " + ground
                + "), the flat reaching " + widest + " from under its hub (8 where it is round), its top "
                + (top - (Math.round(hubY) - 12)) + " lower; with the wheel spun on, its lowest is at "
                + lowestSpun + "; the other three " + (othersRound ? "stay round" : "are bent") + "; half a second later it is "
                + (back ? "round again" : "still flat") + "; landing on all four squashes " + (four ? "some" : "none"));
    }

    /** Wow Caninaro: left, left, right: one knock for the two lefts, then a slide and a knock on the right. */
    private void caninaro() {
        start("canyenaro", car(BODY, new float[0][], true));
        drive(10, 40F, 0F);
        drive(12, 40F, 1F);
        int gentleKnocks = f.knocks;
        int gentleSlides = f.slides;
        drive(15, 40F, 0F);
        drive(15, 40F, 5F);
        int firstKnocks = f.knocks;
        int firstSlides = f.slides;
        drive(20, 40F, 0F);
        drive(15, 40F, 5F);
        int secondKnocks = f.knocks;
        int secondSlides = f.slides;
        drive(20, 40F, 0F);
        int slideAt = -1;
        int knockAt = -1;
        for (int t = 0; t < 25; t++) {
            int s0 = f.slides;
            int k0 = f.knocks;
            drive(1, 40F, -5F);
            if (f.slides > s0 && slideAt < 0) {
                slideAt = t;
            }
            if (f.knocks > k0 && knockAt < 0) {
                knockAt = t;
            }
        }
        boolean ok = gentleKnocks == 0 && gentleSlides == 0 && firstKnocks == 1 && secondKnocks == 1
                && secondSlides == firstSlides && f.knocks == 2 && f.slides == firstSlides + 1 && slideAt >= 0
                && knockAt - slideAt >= 4;
        check(F, "something slides in Wow Caninaro", ok, "a gentle corner: " + gentleKnocks + " knocks; a hard left: "
                + firstKnocks + " knock (" + firstSlides + " slide heard); a second hard left: "
                + (secondKnocks - firstKnocks) + " more; then a hard right: a slide heard after " + slideAt
                + " ticks and the knock " + (knockAt - slideAt) + " ticks after it (" + f.knocks + " knocks, "
                + f.slides + " slides in all)");
    }

    /** EL KING: a hard stop rocks the cab forward, and letting the brake go lets the air out once. */
    private void king() {
        CarShape s = car(TRUCK, new float[0][], false);
        start("king", s);
        int cab = 0;
        for (int i = 0; i < s.planes; i++) {
            cab += q().part[i] ? 1 : 0;
        }
        drive(20, 60F, 0F);
        // a gentle stop first: no rocking to speak of, and no air on the release
        v.down[0] = true;
        float speed = 60F;
        float gentle = 0F;
        for (int t = 0; t < 40; t++) {
            speed = Math.max(40F, speed - 0.5F);
            drive(1, speed, 0F);
            gentle = Math.max(gentle, q().rock);
        }
        v.down[0] = false;
        drive(5, speed, 0F);
        int gentleBreaths = f.breaths;
        // then a hard one
        v.down[0] = true;
        float most = 0F;
        int roofForward = 0;
        int backUp = 0;
        boolean chassisMoved = false;
        for (int t = 0; t < 14; t++) {
            speed = Math.max(0F, speed - 3F);
            drive(1, speed, 0F);
            if (q().rock > most) {
                most = q().rock;
                f.queue(new nfm.lit.features.DrawQueue());
                // the cab's back wall: its top goes forward and up
                int count = corners(2);
                if (f.body(0, 2, ix, iy, iz, count, ox, oy, oz)) {
                    roofForward = oz[0] - iz[0];
                    backUp = iy[2] - oy[2];
                }
                chassisMoved = f.body(0, 0, ix, iy, iz, corners(0), ox, oy, oz)
                        || f.body(0, 1, ix, iy, iz, corners(1), ox, oy, oz);
            }
        }
        int during = f.breaths;
        drive(6, speed, 0F);
        v.down[0] = false;
        drive(1, speed, 0F);
        int released = f.breaths;
        drive(45, speed, 0F);
        float settled = q().rock;
        v.down[0] = true;
        drive(3, speed, 0F);
        v.down[0] = false;
        drive(3, speed, 0F);
        boolean ok = cab == 4 && gentle < 1F && gentleBreaths == 0 && most > 2F && most <= PersonalityFeature.KING_ROCK
                && roofForward >= 1 && backUp >= 0 && !chassisMoved && during == 0 && released == 1 && settled == 0F
                && f.breaths == 1;
        check(F, "EL KING's cab rocks", ok, "the cab found on the model: " + cab + " polygons (not the chassis); a"
                + " gentle stop rocks it " + n(gentle) + " degrees, " + gentleBreaths + " breaths of air; a hard stop "
                + n(most) + " degrees forward (its roof " + roofForward + " ahead, the chassis "
                + (chassisMoved ? "moved" : "still") + "), " + during + " breaths while braking, " + released
                + " on letting the brake go, the cab back at " + n(settled) + " two seconds later; braking again"
                + " without a hard stop: " + (f.breaths - released) + " more");
    }
}
