package nfm.lit.features.physx;

import nfm.lit.features.CheckCanvas;
import nfm.lit.features.DrawQueue;
import nfm.lit.features.ExtrasCheck;
import nfm.lit.features.RaceView;
import nfm.lit.features.Weather;

/**
 * Checks of the PhysX extra (physx, "PhysX to try out", "PhysX is only
 * visual") on a made-up race: PhysX loads, a hard hit's panels fall, tumble
 * and come to rest on the ground, a car driven into them shoves them away,
 * a piece comes to rest on a raised floor and slides down a ramp, wrecks,
 * explosions and snow throw pieces, they are drawn, and the race's copy is
 * left as it was. That a recorded race replays with the extra on is
 * {@code cloud/check.sh looks} (test\verify-looks.ps1), which runs every
 * recorded race with every look extra on.
 */
public final class PhysxCheck {

    private static final String F = "physx";
    private static final float GROUND = 250F;

    private PhysxFeature physx;
    private RaceView v;
    private final float[] at = new float[6];

    private PhysxCheck() {
    }

    public static void run() {
        PhysxCheck c = new PhysxCheck();
        c.start(false);
        if (!c.physx.running()) {
            ExtrasCheck.check(F, "PhysX runs in the race", false, "it could not be started: " + PhysxFeature.unavailable());
            return;
        }
        ExtrasCheck.check(F, "PhysX runs in the race", true, "PhysX " + PhysWorld.version() + ", "
                + c.physx.world().pieces + " of the stage's " + c.v.pieces + " pieces in its scene");
        c.hitFallsAndRests();
        c.carShoves();
        c.stagePieces();
        c.wrecksBlastsAndSnow();
        c.drawn();
        c.onlyALook();
        c.physx.raceEnded(c.v);
    }

    /**
     * Two cars on flat ground, car 1 far off, on a stage with a raised
     * floor 100 above the ground about (3000, 0), a ramp rising along z
     * about (-3000, 0) and a wall at (0, 3000).
     */
    private void start(boolean again) {
        if (!again) {
            physx = PhysxFeature.forCheck();
        }
        v = new RaceView();
        v.fit(2);
        v.cars = 2;
        v.humans = 1;
        v.seed = 11L;
        v.groundColor = 0xd6caae;
        for (int c = 0; c < 2; c++) {
            v.x[c] = 0F;
            v.y[c] = 220F;
            v.z[c] = c * 9000F;
            v.grounded[c] = true;
            for (int w = 0; w < RaceView.WHEELS; w++) {
                v.keyX[c * RaceView.WHEELS + w] = w % 2 == 0 ? -36 : 36;
                v.keyZ[c * RaceView.WHEELS + w] = w < 2 ? 70 : -70;
            }
        }
        v.pieces = 3;
        v.pieceX = new float[] {3000F, -3000F, 0F};
        v.pieceY = new float[] {150F, 190F, 150F};
        v.pieceZ = new float[] {0F, 0F, 3000F};
        v.pieceRadX = new float[] {300F, 200F, 300F};
        v.pieceRadY = new float[] {10F, 60F, 100F};
        v.pieceRadZ = new float[] {300F, 200F, 20F};
        v.pieceXY = new int[] {0, 0, 0};
        v.pieceZY = new int[] {0, 17, 90};
        Weather.raceStarts();
        physx.raceStarted(v);
    }

    private void ticks(int count) {
        for (int t = 0; t < count; t++) {
            v.tick++;
            physx.afterTick(v);
            v.hits = 0;
        }
    }

    /** A hard hit knocks panels off; they fly up, turn over, fall and lie still on the ground. */
    private void hitFallsAndRests() {
        v.hits = 1;
        v.hitCar[0] = 0;
        v.hitWheel[0] = 0;
        v.hitForce[0] = 160F;
        ticks(1);
        int thrown = physx.panels;
        float highest = GROUND;
        float turned = 1F;
        for (int t = 0; t < 19 * 2; t++) {
            ticks(1);
            for (int n = 0; physx.pieceAt(n, at) != null; n++) {
                highest = Math.min(highest, at[1]);
                turned = Math.min(turned, at[5]);
            }
        }
        ticks(19 * 10);
        int lying = 0;
        int resting = 0;
        float off = 0F;
        Boolean rests;
        for (int n = 0; (rests = physx.pieceAt(n, at)) != null; n++) {
            float under = GROUND - at[1];
            // a box lying on the ground: its middle between its smallest and its largest half size above it
            lying += under > at[4] - 1.5F && under < at[3] + 1.5F ? 1 : 0;
            resting += rests ? 1 : 0;
            off = Math.max(off, Math.abs(under));
        }
        ExtrasCheck.check(F, "a hard hit knocks panels off the car", thrown == 3 && physx.count == 3,
                "a hit of 160: " + thrown + " panels, " + physx.count + " loose pieces (one from "
                        + (int) PhysxFeature.HIT_FROM + ", one more for every " + (int) PhysxFeature.HIT_MORE + ")");
        ExtrasCheck.check(F, "the pieces fly, tumble, fall and come to rest on the ground",
                highest < GROUND - 40F && turned < 0.97F && lying == thrown && resting == thrown,
                "highest " + ExtrasCheck.n(GROUND - highest) + " above the ground, turned to |w| "
                        + ExtrasCheck.n(turned) + "; 12 s later " + lying + " of " + thrown + " lie on the ground, "
                        + resting + " at rest");
    }

    /** A car driven into the pieces lying there shoves them away. */
    private void carShoves() {
        physx.pieceAt(0, at);
        float fromX = at[0];
        float fromZ = at[2];
        // car 1 comes from 600 behind the piece, along z, 30 units a tick, and drives on past it
        v.x[1] = fromX;
        v.z[1] = fromZ - 600F;
        ticks(1);
        v.vz[1] = 30F;
        for (int t = 0; t < 40; t++) {
            v.z[1] += 30F;
            ticks(1);
        }
        v.vz[1] = 0F;
        ticks(19 * 3);
        physx.pieceAt(0, at);
        float moved = (float) Math.hypot(at[0] - fromX, at[2] - fromZ);
        ExtrasCheck.check(F, "a car driving into a piece shoves it away", moved > 60F && at[1] <= GROUND,
                "a car at 30 a tick drove through a piece lying still: it ended " + ExtrasCheck.n(moved)
                        + " from where it lay, " + ExtrasCheck.n(GROUND - at[1]) + " above the ground");
        v.z[1] = 9000F;
        v.x[1] = 0F;
    }

    /** A piece dropped over the raised floor rests on it; one dropped on the ramp slides or rolls down it; one thrown at the wall stops before it. */
    private void stagePieces() {
        start(true);
        physx.piece(3000F, 60F, 0F, 10F, 6F, 8F, 0F, 0F, 0F, 0x808080, 19 * 60, false);
        physx.piece(-3000F, 100F, 0F, 9F, 8F, 9F, 0F, 0F, 0F, 0x808080, 19 * 60, false);
        physx.piece(0F, 200F, 2700F, 8F, 8F, 8F, 0F, -4F, 20F, 0x808080, 19 * 60, false);
        float rampFirst = Float.NaN;
        for (int t = 0; t < 19 * 14; t++) {
            ticks(1);
            physx.pieceAt(1, at);
            if (Float.isNaN(rampFirst) && t == 19) {
                rampFirst = at[1];
            }
        }
        physx.pieceAt(0, at);
        float onFloor = 150F - at[1];
        boolean floorOk = onFloor > 4F && onFloor < 12F && Math.abs(at[0] - 3000F) < 300F;
        physx.pieceAt(1, at);
        // the ramp's plane passes through (-3000, 190, 0): y - 190 = z tan 17; the piece was dropped on its middle
        float rampY = at[1];
        float rampZ = at[2];
        physx.pieceAt(2, at);
        float wallZ = at[2];
        ExtrasCheck.check(F, "a piece rests on a raised floor", floorOk,
                "dropped over a floor 100 above the ground: its middle came to rest " + ExtrasCheck.n(onFloor)
                        + " above the floor (a box 6 high lying, 10 on its end)");
        ExtrasCheck.check(F, "a piece meets a ramp above the ground", rampFirst < GROUND - 30F,
                "dropped on a ramp whose middle is 60 above the ground: a second later its middle was "
                        + ExtrasCheck.n(GROUND - rampFirst) + " above the ground, in the end "
                        + ExtrasCheck.n(GROUND - rampY) + " at z " + ExtrasCheck.n(rampZ));
        ExtrasCheck.check(F, "a wall stops a piece", wallZ < 2980F,
                "thrown at 20 a tick at a wall whose near face is at z 2980: it lies at z " + ExtrasCheck.n(wallZ));
    }

    /** A car wasted throws chunks, an explosion throws chunks, a lump of snow broken out of the bed becomes a block; old pieces make way. */
    private void wrecksBlastsAndSnow() {
        start(true);
        v.wasted[0] = true;
        ticks(1);
        int wreck = physx.chunks;
        Weather.blast(500F, 250F, 500F, 1F, false, v.tick);
        ticks(1);
        int blast = physx.chunks - wreck;
        Weather.lump(200F, 240F, 200F, 3F, -6F, 2F, 14F);
        Weather.lump(220F, 240F, 200F, 3F, -6F, 2F, 10F);
        ticks(1);
        int snow = physx.snowLumps;
        int had = physx.count;
        ticks(PhysxFeature.SNOW_LIES + 2);
        int afterSnow = physx.count;
        ticks(PhysxFeature.LIES - PhysxFeature.SNOW_LIES + 2);
        ExtrasCheck.check(F, "wrecks, explosions and broken snow throw pieces",
                wreck == PhysxFeature.WRECK_CHUNKS && blast == PhysxFeature.BLAST_CHUNKS && snow == 2,
                "a car wasted: " + wreck + " chunks; an explosion: " + blast + "; two lumps of snow: " + snow
                        + " blocks");
        ExtrasCheck.check(F, "pieces go after a while", had == wreck + blast + 2 && afterSnow == had - 2
                && physx.count == 0,
                had + " pieces; after " + PhysxFeature.SNOW_LIES / 19 + " s the snow is gone (" + afterSnow
                        + " left), after " + PhysxFeature.LIES / 19 + " s all (" + physx.count + " left)");
        // more than there is room for: the oldest make way, never more than the scene holds
        start(true);
        for (int n = 0; n < PhysWorld.BODIES + 40; n++) {
            physx.piece(n * 30F, 200F, -2000F, 6F, 4F, 6F, 0F, 0F, 0F, 0x808080, 19 * 60, false);
        }
        ticks(3);
        ExtrasCheck.check(F, "never more pieces than the scene holds", physx.count == PhysWorld.BODIES,
                (PhysWorld.BODIES + 40) + " thrown: " + physx.count + " loose (room for " + PhysWorld.BODIES + ")");
    }

    /** A piece ahead of the eye is drawn as a box of the faces turned to the eye; far off it is left out. */
    private void drawn() {
        start(true);
        physx.piece(0F, 200F, 600F, 12F, 6F, 9F, 0F, 0F, 0F, 0xc02020, 19 * 60, false);
        ticks(19 * 4);
        CheckCanvas canvas = new CheckCanvas();
        int near = frame(canvas, 0F, 150F, 0F);
        int nearFaces = physx.drawnFaces;
        int far = frame(canvas, 0F, 150F, -PhysxFeature.SEEN - 500F);
        ExtrasCheck.check(F, "a piece is drawn as a box", near >= 1 && near <= 3 && nearFaces == near && far == 0,
                "one piece 600 ahead: " + near + " of its 6 faces drawn (those turned to the eye); from "
                        + (int) (PhysxFeature.SEEN + 1100F) + " away: " + far);
    }

    private int frame(CheckCanvas canvas, float x, float y, float z) {
        DrawQueue queue = new DrawQueue();
        queue.begin(x, y, z, v.tick);
        queue.view(0, 0F, 0F, 16F / 9F);
        queue.owner(0);
        physx.queue(queue);
        queue.sort();
        canvas.reset();
        DrawQueue.Drawer drawer = (f, item) -> physx.draw(canvas, item);
        queue.before(Integer.MAX_VALUE, false, drawer);
        for (int c = 0; c < v.cars; c++) {
            queue.under(c, drawer);
            queue.after(c, drawer);
        }
        queue.end(drawer);
        return canvas.polygons;
    }

    /** With pieces flying about and cars among them, the race's copy is as it was: the extra only reads it. */
    private void onlyALook() {
        start(true);
        v.hits = 1;
        v.hitCar[0] = 0;
        v.hitForce[0] = 300F;
        v.wasted[1] = true;
        float[] x = v.x.clone();
        float[] y = v.y.clone();
        float[] z = v.z.clone();
        float[] vx = v.vx.clone();
        float[] vz = v.vz.clone();
        int[] xz = v.xz.clone();
        float[] damage = v.damage.clone();
        for (int t = 0; t < 60; t++) {
            v.tick++;
            physx.afterTick(v);
        }
        boolean same = java.util.Arrays.equals(x, v.x) && java.util.Arrays.equals(y, v.y)
                && java.util.Arrays.equals(z, v.z) && java.util.Arrays.equals(vx, v.vx)
                && java.util.Arrays.equals(vz, v.vz) && java.util.Arrays.equals(xz, v.xz)
                && java.util.Arrays.equals(damage, v.damage) && v.hits == 1 && v.hitForce[0] == 300F;
        ExtrasCheck.check(F, "only a look: the race's copy is left as it was", same && physx.count > 0,
                physx.count + " pieces about two cars for 60 ticks: the cars' places, speeds, turns and damage "
                        + (same ? "unchanged" : "CHANGED"));
    }
}
