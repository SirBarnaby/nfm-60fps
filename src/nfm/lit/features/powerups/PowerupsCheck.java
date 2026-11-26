package nfm.lit.features.powerups;

import static nfm.lit.features.ExtrasCheck.check;
import static nfm.lit.features.ExtrasCheck.n;

import nfm.lit.features.CheckCanvas;
import nfm.lit.features.Explosions;
import nfm.lit.features.Features;
import nfm.lit.features.GameHooks;
import nfm.lit.features.RaceInts;
import nfm.lit.features.RaceTick;
import nfm.lit.features.RaceView;

/**
 * The powerups' checks ({@link nfm.lit.features.ExtrasCheck}): the race
 * half played on a made-up straight with checkpoints, through a
 * {@link RaceTick} of plain arrays, and the look half drawn into a canvas
 * that counts. Each requirement's situation: the rows of boxes and how rare
 * they are, a pickup, nitrous's burst, two nitrous at once, the grip nitrous
 * leaves, dynamite thrown two seconds after the pickup, its aim and its
 * blast denting the ground, the bomb dropped behind and driven onto, an
 * explosion's look and its smoke; and the state saved and restored plays on
 * the same.
 */
public final class PowerupsCheck {

    private static final String F = "powerups";

    private PowerupsCheck() {
    }

    public static void run() {
        boxes();
        rare();
        pickup();
        nitrous();
        twoNitrous();
        slippery();
        dynamite();
        aim();
        bomb();
        ownBomb();
        computerDrops();
        everyKind();
        explosion();
        smoke();
        rewind();
    }

    /** A row of three boxes over the road at one checkpoint in four. */
    private static void boxes() {
        Track t = new Track(2, 8);
        PowerupRace r = new PowerupRace();
        r.setUp(t, 1L);
        boolean over = true;
        for (int b = 0; b < r.boxes; b++) {
            over &= r.boxY[b] == 250F - PowerupRace.HOVER;
        }
        boolean apart = r.boxes == 6 && Math.abs(r.boxZ[3] - r.boxZ[0]) == 4 * 1500F;
        check(F, "rows of three boxes over the road, spread round the lap", apart && over,
                r.boxes + " boxes at 8 checkpoints, the rows " + n(Math.abs(r.boxZ[3] - r.boxZ[0])) + " apart");
    }

    /**
     * About a quarter as many powerups are picked up as in the first build
     * (a row at each checkpoint, every second one with more than 16): a car
     * drives every checkpoint of laps of several lengths, long enough apart
     * for every box to be back.
     */
    private static void rare() {
        int[] laps = {4, 6, 8, 12, 16, 22, 33};
        int now = 0;
        int first = 0;
        StringBuilder each = new StringBuilder();
        for (int gates : laps) {
            Track t = new Track(1, gates);
            PowerupRace r = new PowerupRace();
            r.setUp(t, 7L + gates);
            int got = 0;
            for (int lap = 0; lap < 3; lap++) {
                for (int p = 0; p < t.points(); p++) {
                    if (t.pointKind(p) <= 0) {
                        continue;
                    }
                    t.x[0] = t.pointX(p);
                    t.z[0] = t.pointZ(p);
                    r.tick(t);
                    if (r.held[0] != PowerupRace.NONE) {
                        got++;
                        r.held[0] = PowerupRace.NONE;
                    }
                    t.x[0] = -5000F;
                    for (int k = 0; k <= PowerupRace.RESPAWN; k++) {
                        r.tick(t);
                    }
                }
            }
            now += got;
            first += 3 * PowerupRace.firstBuildRows(gates);
            each.append(each.length() == 0 ? "" : ", ").append(got).append(" of ")
                    .append(3 * PowerupRace.firstBuildRows(gates));
        }
        float share = now / (float) first;
        check(F, "about a quarter as many powerups as in the first build", share > 0.2F && share < 0.32F,
                n(share) + " of the first build's pickups over three laps of 4 to 33 checkpoints (" + each + ")");
    }

    /** Driving through a box: the car holds a powerup, the box is gone four seconds, a pickup event. */
    private static void pickup() {
        Track t = new Track(2, 4);
        PowerupRace r = new PowerupRace();
        r.setUp(t, 2L);
        t.x[0] = r.boxX[1];
        t.z[0] = r.boxZ[1];
        r.tick(t);
        boolean held = r.held[0] != PowerupRace.NONE;
        int kind = r.eventKind[0];
        check(F, "driving through a box gives a powerup and a pickup sound's event",
                held && kind == PowerupRace.PICKUP && r.boxWait[1] == PowerupRace.RESPAWN,
                "holds " + r.held[0] + ", event " + kind + ", box back in " + r.boxWait[1] + " ticks");
    }

    /** Nitrous on a press: more boost for two seconds, fading to none a second later. */
    private static void nitrous() {
        Track t = new Track(2, 4);
        PowerupRace r = new PowerupRace();
        r.setUp(t, 3L);
        r.held[0] = PowerupRace.NITROUS;
        t.key[0] = true;
        r.tick(t);
        float start = r.boost(0);
        t.key[0] = false;
        int ticks = 0;
        while (r.boost(0) > 0F && ticks < 200) {
            r.tick(t);
            ticks++;
        }
        check(F, "nitrous gives a burst of speed for about three seconds", start == PowerupRace.NITROUS_BOOST
                && ticks == PowerupRace.NITROUS_FULL + PowerupRace.NITROUS_FADE,
                "boost " + n(start) + ", for " + ticks + " more ticks");
    }

    /**
     * A nitrous fired while the last still burns: the flame is red and a
     * bit bigger for as long as the two overlap, and blue again when only
     * one burns.
     */
    private static void twoNitrous() {
        PowerupsFeature f = PowerupsFeature.FEATURE;
        PowerupRace r = f.race;
        Track t = new Track(2, 4);
        r.setUp(t, 8L);
        RaceView v = view(t);
        f.raceStarted(v);
        CheckCanvas canvas = new CheckCanvas();
        r.held[0] = PowerupRace.NITROUS;
        t.key[0] = true;
        tick(f, t, v);
        t.key[0] = false;
        for (int k = 0; k < 12; k++) {
            tick(f, t, v);
        }
        canvas.frame(f, 0F, 100F, -4000F, v.tick);
        int blueAlone = canvas.drawn(0x3866FF);
        int redAlone = canvas.drawn(0xFF3A22);
        int left = r.nitrous[0];
        // the second, picked up meanwhile, fired while the first still burns
        r.held[0] = PowerupRace.NITROUS;
        t.key[0] = true;
        tick(f, t, v);
        t.key[0] = false;
        canvas.frame(f, 0F, 100F, -4000F, v.tick);
        int red = canvas.drawn(0xFF3A22);
        int blue = canvas.drawn(0x3866FF);
        float redWide = canvas.span(0xFF3A22);
        // the same frame as one nitrous would show it (the flame flickers from tick to tick)
        f.burning[0] = 1;
        canvas.frame(f, 0F, 100F, -4000F, v.tick);
        float blueWide = canvas.span(0x3866FF);
        f.burning[0] = 2;
        float boost = r.boost(0);
        int overlap = 1;
        while (r.doubled[0] > 0 && overlap < 200) {
            tick(f, t, v);
            overlap++;
        }
        canvas.frame(f, 0F, 100F, -4000F, v.tick);
        int blueAfter = canvas.drawn(0x3866FF);
        int redAfter = canvas.drawn(0xFF3A22);
        boolean ok = blueAlone > 0 && redAlone == 0 && red > 0 && blue == 0 && redWide > blueWide * 1.1F
                && redWide < blueWide * 1.6F && overlap == left && blueAfter > 0 && redAfter == 0 && r.nitrous[0] > 0
                && boost == PowerupRace.NITROUS_BOOST;
        check(F, "two nitrous at once burn red and a bit bigger, blue again when one is left", ok,
                "one: " + blueAlone + " blue polygons; two: " + red + " red, " + n(redWide) + " long where one is "
                        + n(blueWide) + ", for " + overlap + " ticks (the first had " + left + " left), boost " + n(boost)
                        + "; then " + blueAfter + " blue, " + redAfter + " red");
    }

    /**
     * Nitrous leaves less grip, so the same corner slides more: the grip
     * the race is handed while it burns, and a corner taken by the game's
     * own rule (each tick a wheel's velocity nears the way the car points
     * by the grip at most) with and without it.
     */
    private static void slippery() {
        Track t = new Track(2, 4);
        PowerupRace r = new PowerupRace();
        r.setUp(t, 9L);
        float before = r.grip(0);
        r.held[0] = PowerupRace.NITROUS;
        t.key[0] = true;
        r.tick(t);
        t.key[0] = false;
        float burning = r.grip(0);
        float gripped = corner(null);
        float slid = corner(r);
        for (int k = 0; k < 200; k++) {
            r.tick(t);
        }
        float after = r.grip(0);
        check(F, "nitrous leaves less grip: the same corner slides more", before == 1F && after == 1F
                && burning == PowerupRace.NITROUS_GRIP && slid > gripped * 1.3F,
                "grip " + n(before) + " before, " + n(burning) + " burning, " + n(after) + " after; a 90 degree"
                        + " corner at 100 a tick slides " + n(gripped) + " units without, " + n(slid) + " with");
    }

    /**
     * How far a wheel ends up off its line through a 90 degree corner at
     * 100 units a tick, turning 15 degrees a tick, with the game's rule and a
     * grip of 20 (times what {@code r} leaves of it, if given).
     */
    private static float corner(PowerupRace r) {
        float grip = 20F * (r == null ? 1F : r.grip(0));
        float vx = 0F;
        float vz = 100F;
        float x = 0F;
        float z = 0F;
        float lineX = 0F;
        float lineZ = 0F;
        for (int k = 0; k < 40; k++) {
            double heading = Math.toRadians(Math.min(90, k * 15));
            float wantX = (float) (Math.sin(heading) * 100D);
            float wantZ = (float) (Math.cos(heading) * 100D);
            vx += Math.max(-grip, Math.min(grip, wantX - vx));
            vz += Math.max(-grip, Math.min(grip, wantZ - vz));
            x += vx;
            z += vz;
            lineX += wantX;
            lineZ += wantZ;
        }
        return (float) Math.sqrt((x - lineX) * (x - lineX) + (z - lineZ) * (z - lineZ));
    }

    /** Dynamite: thrown forwards 2 s after the pickup; its blast throws a car near it and dents the ground. */
    private static void dynamite() {
        Track t = new Track(2, 4);
        PowerupRace r = new PowerupRace();
        r.setUp(t, 4L);
        r.held[0] = PowerupRace.DYNAMITE;
        r.heldTicks[0] = 0;
        int thrownAt = -1;
        for (int k = 0; k < 60 && thrownAt < 0; k++) {
            r.tick(t);
            for (int s = 0; s < PowerupRace.STICKS; s++) {
                if (r.stickOwner[s] == 0) {
                    thrownAt = k + 1;
                }
            }
        }
        float forward = r.stickVZ[0];
        // a car parked where the stick comes down
        t.x[1] = t.x[0];
        t.z[1] = t.z[0] + 1500F;
        int blastAt = blastAfter(r, t, 80);
        check(F, "dynamite leaves the car forwards two seconds after the pickup",
                thrownAt == PowerupRace.FUSE && forward > 0F, "thrown after " + thrownAt + " ticks, "
                        + n(forward) + " a tick forwards");
        boolean dents = blastAt >= 0 && r.eventWhat[blastAt] == 1;
        check(F, "the blast dents the ground and throws a car near it", dents && t.pushed[1] > 0F
                && t.damaged[1] > 0F, "dent " + dents + ", car 1 pushed " + n(t.pushed[1]) + ", damaged "
                        + n(t.damaged[1]));
    }

    /** Ticks until a blast's event, at most {@code most}: the event's place in the ring, or -1. */
    private static int blastAfter(PowerupRace r, Track t, int most) {
        for (int k = 0; k < most; k++) {
            int before = r.events;
            r.tick(t);
            for (int e = before; e < r.events; e++) {
                if (r.eventKind[e % PowerupRace.EVENTS] == PowerupRace.BLAST) {
                    return e % PowerupRace.EVENTS;
                }
            }
        }
        return -1;
    }

    /**
     * The throw turns toward the nearest enemy ahead by a couple of degrees
     * at most: all the way to one a degree off the nose, 2.5 degrees toward
     * one 6 degrees off, and one far to the side is still missed.
     */
    private static void aim() {
        float near = thrownToward(1F, 2000F, null);
        float few = thrownToward(6F, 2000F, null);
        float[] pushed = new float[1];
        float far = thrownToward(40F, 1500F, pushed);
        float left = thrownToward(-6F, 2000F, null);
        boolean ok = Math.abs(near - 1F) < 0.2F && Math.abs(few - PowerupRace.AIM_MOST) < 0.01F
                && Math.abs(far - PowerupRace.AIM_MOST) < 0.01F && Math.abs(left + PowerupRace.AIM_MOST) < 0.01F
                && pushed[0] == 0F;
        check(F, "dynamite aims a couple of degrees toward the nearest enemy, no more", ok,
                "an enemy 1 degree off: thrown " + n(near) + " degrees toward it; 6 off: " + n(few) + "; -6 off: "
                        + n(left) + "; 40 off: " + n(far) + ", and the blast pushes it " + n(pushed[0]));
    }

    /**
     * Car 0 throws with car 1 {@code degrees} off its nose, {@code far}
     * away: how many degrees off the nose the stick leaves; into
     * {@code pushed[0]}, if given, how hard its blast pushes car 1.
     */
    private static float thrownToward(float degrees, float far, float[] pushed) {
        Track t = new Track(2, 4);
        PowerupRace r = new PowerupRace();
        r.setUp(t, 10L);
        t.x[0] = 0F;
        t.z[0] = 0F;
        double a = Math.toRadians(degrees);
        t.x[1] = (float) (Math.sin(a) * far);
        t.z[1] = (float) (Math.cos(a) * far);
        r.held[0] = PowerupRace.DYNAMITE;
        r.heldTicks[0] = PowerupRace.FUSE - 1;
        r.tick(t);
        float leaves = (float) Math.toDegrees(Math.atan2(r.stickVX[0], r.stickVZ[0]));
        if (pushed != null) {
            blastAfter(r, t, 80);
            pushed[0] = t.pushed[1];
        }
        return leaves;
    }

    /**
     * The bomb: dropped on the road behind the car on a press of the key;
     * the next car that drives onto it (even between two ticks) gets a bang,
     * around 30% damage and is thrown slightly; a car close by gets its
     * share, one far away nothing. The look: a black ball with a fuse.
     */
    private static void bomb() {
        PowerupsFeature f = PowerupsFeature.FEATURE;
        PowerupRace r = f.race;
        Track t = new Track(4, 4);
        // the bomb will lie 180 behind car 0 (its radius and 60): one car waits beside that, one far away, one behind
        float lying = -(t.radius(0) + 60F);
        float[][] places = {{0F, 0F}, {0F, lying - 1100F}, {200F, lying}, {3000F, lying}};
        for (int c = 0; c < 4; c++) {
            t.x[c] = places[c][0];
            t.z[c] = places[c][1];
        }
        r.setUp(t, 11L);
        RaceView v = view(t);
        f.raceStarted(v);
        CheckCanvas canvas = new CheckCanvas();
        r.held[0] = PowerupRace.BOMB;
        tick(f, t, v);
        canvas.frame(f, 0F, 100F, -1500F, v.tick);
        int held = canvas.drawn(0xB89A5A);
        t.key[0] = true;
        tick(f, t, v);
        t.key[0] = false;
        boolean dropped = r.bombOwner[0] == 0 && r.held[0] == PowerupRace.NONE;
        float behind = t.z[0] - r.bombZ[0];
        float bombY = r.bombY[0];
        canvas.frame(f, 0F, 100F, -1500F, v.tick);
        int lies = canvas.drawn(0xB89A5A);
        // the car that dropped it drives on; car 1 comes up from behind
        t.z[0] = 4000F;
        int bangAt = -1;
        for (int k = 0; k < 20 && bangAt < 0; k++) {
            t.z[1] += 200F; // 200 a tick: more than the bomb's reach, so it is passed between two ticks
            int before = r.events;
            tick(f, t, v);
            for (int e = before; e < r.events; e++) {
                if (r.eventKind[e % PowerupRace.EVENTS] == PowerupRace.BANG) {
                    bangAt = e % PowerupRace.EVENTS;
                }
            }
        }
        boolean gone = r.bombOwner[0] < 0;
        boolean ok = dropped && behind == -lying && bombY == 250F && held > 0 && lies > 0 && bangAt >= 0
                && r.eventCar[bangAt] == 1 && gone && Math.abs(t.damaged[1] - PowerupRace.BANG_DAMAGE) < 0.001F
                && t.pushed[1] > 0F && t.damaged[2] > 0F && t.damaged[2] < t.damaged[1] && t.damaged[3] == 0F
                && t.damaged[0] == 0F && f.explosions.particles.count() > 0 && f.explosions.craters() == 0;
        check(F, "the bomb is dropped behind the car and bangs under the next car onto it", ok,
                "dropped " + dropped + " " + n(behind) + " behind at height " + n(bombY) + " (the road 250); drawn held "
                        + held + ", lying " + lies + "; car 1 onto it at 200 a tick: bang " + (bangAt >= 0)
                        + ", damage " + n(t.damaged[1]) + ", thrown " + n(t.pushed[1]) + "; a car 200 beside it damage "
                        + n(t.damaged[2]) + ", one 3000 away " + n(t.damaged[3]) + "; " + f.explosions.particles.count()
                        + " particles, " + f.explosions.craters() + " craters");
    }

    /** The car that dropped a bomb can back onto it in the first second; after that it gets the bang as anyone. */
    private static void ownBomb() {
        Track t = new Track(2, 4);
        PowerupRace r = new PowerupRace();
        r.setUp(t, 12L);
        t.x[0] = 0F;
        t.z[0] = 0F;
        r.held[0] = PowerupRace.BOMB;
        t.key[0] = true;
        r.tick(t);
        t.key[0] = false;
        t.z[0] = r.bombZ[0];
        int safe = 0;
        while (r.bombOwner[0] == 0 && safe < 100) {
            r.tick(t);
            safe++;
        }
        check(F, "the car that dropped a bomb sets it off only after a second",
                safe == PowerupRace.BOMB_OWN_AFTER - 1 && t.damaged[0] > 0F,
                "on its own bomb for " + safe + " ticks before the bang, damage " + n(t.damaged[0]));
    }

    /** A computer car drops its bomb with a car close behind it in line, and keeps it with nobody behind. */
    private static void computerDrops() {
        Track t = new Track(2, 4);
        PowerupRace r = new PowerupRace();
        r.setUp(t, 13L);
        t.x[1] = 0F;
        t.z[1] = 0F;
        t.x[0] = 0F;
        t.z[0] = 3000F; // ahead of the computer car: nobody behind it
        r.held[1] = PowerupRace.BOMB;
        for (int k = 0; k < 5 * PowerupRace.SECOND; k++) {
            r.tick(t);
        }
        boolean kept = r.held[1] == PowerupRace.BOMB;
        t.z[0] = -600F; // close behind it, in line
        int ticks = 0;
        while (r.held[1] == PowerupRace.BOMB && ticks < 200) {
            r.tick(t);
            ticks++;
        }
        check(F, "a computer car drops its bomb in front of a car close behind", kept && ticks < 3 * PowerupRace.SECOND
                && r.bombOwner[0] == 1, "kept 5 s with nobody behind: " + kept + "; dropped " + ticks
                        + " ticks after a car came up behind");
    }

    /** A box gives nitrous, dynamite or a bomb, to the leader, the last car and those between. */
    private static void everyKind() {
        int[][] got = new int[3][4];
        for (int place = 0; place < 3; place++) {
            for (long seed = 1; seed <= 400; seed++) {
                Track t = new Track(3, 4);
                PowerupRace r = new PowerupRace();
                r.setUp(t, seed);
                t.place[0] = place;
                t.place[place] = 0;
                t.x[0] = r.boxX[0];
                t.z[0] = r.boxZ[0];
                r.tick(t);
                got[place][r.held[0]]++;
            }
        }
        boolean ok = true;
        StringBuilder all = new StringBuilder();
        String[] who = {"the leader", "the middle", "the last"};
        for (int place = 0; place < 3; place++) {
            ok &= got[place][0] == 0 && got[place][1] > 0 && got[place][2] > 0 && got[place][3] > 0;
            all.append(place == 0 ? "" : "; ").append(who[place]).append(" ").append(got[place][1]).append(" nitrous, ")
                    .append(got[place][2]).append(" dynamite, ").append(got[place][3]).append(" bombs");
        }
        check(F, "a box gives nitrous, dynamite or a bomb, whatever the car's place", ok, "of 400 boxes: " + all);
    }

    /**
     * An explosion: the screen flashed once for a player near it, the
     * fireball and the smoke drawn dithered, a crater left, and the smoke
     * still there seconds later.
     */
    private static void explosion() {
        PowerupsFeature f = PowerupsFeature.FEATURE;
        PowerupRace r = f.race;
        Track t = new Track(2, 4);
        r.setUp(t, 14L);
        t.x[0] = 0F;
        t.z[0] = 0F;
        RaceView v = view(t);
        int[] flashes = new int[1];
        Features.setHooks(new GameHooks() {
            @Override
            public void flash() {
                flashes[0]++;
            }
        });
        f.raceStarted(v);
        r.held[0] = PowerupRace.DYNAMITE;
        r.heldTicks[0] = PowerupRace.FUSE - 1;
        int blast = -1;
        for (int k = 0; k < 80 && blast < 0; k++) {
            int before = r.events;
            tick(f, t, v);
            for (int e = before; e < r.events; e++) {
                blast = r.eventKind[e % PowerupRace.EVENTS] == PowerupRace.BLAST ? k : blast;
            }
        }
        CheckCanvas canvas = new CheckCanvas();
        tick(f, t, v);
        canvas.frame(f, 0F, 100F, -1500F, v.tick);
        int fire = canvas.drawn(0xFFB43C);
        float dithered = canvas.lastCoverage;
        int atOnce = f.explosions.particles.count();
        for (int k = 0; k < 5 * PowerupRace.SECOND; k++) {
            tick(f, t, v);
        }
        int later = f.explosions.particles.count();
        Features.setHooks(new GameHooks() {
        });
        boolean ok = blast >= 0 && flashes[0] == 1 && fire > 0 && atOnce > 40 && later > 20
                && f.explosions.craters() == 1;
        check(F, "an explosion flashes once, its fireball and smoke drawn dithered, and the smoke stays", ok,
                "flashes " + flashes[0] + ", fireball polygons " + fire + ", " + atOnce + " particles at once, " + later
                        + " five seconds later, craters " + f.explosions.craters());
    }

    /**
     * The smoke an explosion leaves lives with what is around it: left
     * alone it drifts with the wind and stays on top of the ground; a car
     * driving through it drags it along and shoves it aside; a second
     * explosion blows it outwards.
     */
    private static void smoke() {
        RaceView v = new RaceView();
        v.fit(1);
        v.cars = 1;
        v.humans = 1;
        v.seed = 15L;
        v.radius[0] = 120;
        v.x[0] = 50000F;
        v.y[0] = 220F;
        Explosions alone = new Explosions("check");
        Explosions driven = new Explosions("check");
        Explosions twice = new Explosions("check");
        alone.start(v);
        driven.start(v);
        twice.start(v);
        alone.explode(v, 0F, 250F, 0F, 250F, true, 1F, Explosions.BLAST, false);
        driven.explode(v, 0F, 250F, 0F, 250F, true, 1F, Explosions.BLAST, false);
        twice.explode(v, 0F, 250F, 0F, 250F, true, 1F, Explosions.BLAST, false);
        for (int k = 0; k < 30; k++) {
            alone.tick(v);
            driven.tick(v);
            twice.tick(v);
        }
        float[] early = cloud(alone);
        // a car through the middle of one cloud, along z at 80 a tick; a second blast beside another
        twice.explode(v, 300F, 250F, 0F, 250F, false, 1F, Explosions.BLAST, false);
        for (int k = 0; k < 30; k++) {
            alone.tick(v);
            twice.tick(v);
            v.x[0] = 0F;
            v.z[0] = -1200F + k * 80F;
            v.vz[0] = 80F;
            driven.tick(v);
            v.x[0] = 50000F;
        }
        float[] late = cloud(alone);
        float[] car = cloud(driven);
        float[] blown = cloudOld(twice, 0xFFFFFF);
        float[] calm = cloudOld(alone, 0xFFFFFF);
        float downwind = (late[0] - early[0]) * alone.windX() + (late[1] - early[1]) * alone.windZ();
        boolean ok = downwind > 0F && late[3] <= 250F && car[1] > late[1] + 30F && car[2] > late[2] * 1.05F
                && blown[0] < calm[0] - 20F;
        check(F, "smoke drifts with the wind, stays on the ground, and cars and blasts move it", ok,
                "left alone it moved " + n(downwind) + " downwind in 30 ticks, its lowest puff at " + n(late[3])
                        + " (the ground 250); a car through it carried it " + n(car[1] - late[1])
                        + " along and spread it from " + n(late[2]) + " to " + n(car[2])
                        + " across; a second blast 300 beside it moved it " + n(calm[0] - blown[0]) + " away");
    }

    /** A cloud's puffs: their mean x and z, their mean distance from x = 0 across, and the lowest one's height. */
    private static float[] cloud(Explosions e) {
        return cloudOld(e, 0);
    }

    /**
     * As {@link #cloud}, of the puffs only: with {@code skip} not 0, the
     * count is limited to the first explosion's (the pool's first puffs),
     * so two clouds of different sizes compare like with like.
     */
    private static float[] cloudOld(Explosions e, int skip) {
        float[] p = new float[3];
        float sx = 0F;
        float sz = 0F;
        float across = 0F;
        float lowest = -Float.MAX_VALUE;
        int count = 0;
        int most = skip != 0 ? 110 : e.particles.capacity();
        for (int i = 0; i < most; i++) {
            if (!e.particles.isPuff(i) || !e.particles.position(i, p)) {
                continue;
            }
            sx += p[0];
            sz += p[2];
            across += Math.abs(p[0]);
            lowest = Math.max(lowest, p[1]);
            count++;
        }
        count = Math.max(1, count);
        return new float[] {sx / count, sz / count, across / count, lowest};
    }

    /** Saved, played on and restored, the state plays on exactly as before (run-ahead and rollback). */
    private static void rewind() {
        Track t = new Track(3, 4);
        PowerupRace r = new PowerupRace();
        r.setUp(t, 6L);
        r.held[0] = PowerupRace.DYNAMITE;
        r.held[1] = PowerupRace.NITROUS;
        r.held[2] = PowerupRace.BOMB;
        t.x[0] = -600F; // close behind the computer car with the bomb, so it drops it
        t.z[0] = -3600F;
        t.x[2] = -600F;
        RaceInts saved = new RaceInts();
        saved.startSaving();
        r.sync(saved);
        saved.endSaving();
        Track copy = t.copy();
        for (int k = 0; k < 70; k++) {
            r.tick(t);
        }
        boolean played = r.events > 2;
        RaceInts first = new RaceInts();
        first.startSaving();
        r.sync(first);
        first.endSaving();
        saved.startRestoring();
        r.sync(saved);
        saved.endRestoring();
        for (int k = 0; k < 70; k++) {
            r.tick(copy);
        }
        RaceInts again = new RaceInts();
        again.startSaving();
        r.sync(again);
        again.endSaving();
        check(F, "restored, the race extras play on the same", first.sameAs(again) && first.size() > 0 && played,
                first.size() + " values, " + r.events + " events played twice");
    }

    /* ------------------------------------------------------------ helpers */

    /** A copy of the made-up race for the look half, as the game makes one after a real tick. */
    private static RaceView view(Track t) {
        RaceView v = new RaceView();
        v.fit(t.cars);
        v.cars = t.cars;
        v.humans = 1;
        v.seed = 5L;
        v.groundColor = 0x4A6A3A;
        look(t, v);
        return v;
    }

    private static void look(Track t, RaceView v) {
        for (int c = 0; c < t.cars; c++) {
            v.x[c] = t.x[c];
            v.y[c] = t.y[c];
            v.z[c] = t.z[c];
            v.radius[c] = t.radius(c);
        }
    }

    /** One real tick: the race half, then the look half from a fresh copy. */
    private static void tick(PowerupsFeature f, Track t, RaceView v) {
        f.race.tick(t);
        v.tick++;
        look(t, v);
        f.afterTick(v);
    }

    /** A straight along z with a checkpoint every 1500 units, the cars on it, as plain arrays. */
    private static final class Track implements RaceTick {
        final int cars;
        final int gates;
        final float[] x;
        final float[] y;
        final float[] z;
        final int[] place;
        final boolean[] key;
        final float[] pushed;
        final float[] damaged;

        Track(int cars, int gates) {
            this.cars = cars;
            this.gates = gates;
            x = new float[cars];
            y = new float[cars];
            z = new float[cars];
            place = new int[cars];
            key = new boolean[cars];
            pushed = new float[cars];
            damaged = new float[cars];
            for (int c = 0; c < cars; c++) {
                x[c] = -900F - 400F * c; // beside the boxes
                y[c] = 220F;
                z[c] = -3000F;
                place[c] = c;
            }
        }

        Track copy() {
            Track t = new Track(cars, gates);
            System.arraycopy(x, 0, t.x, 0, cars);
            System.arraycopy(y, 0, t.y, 0, cars);
            System.arraycopy(z, 0, t.z, 0, cars);
            System.arraycopy(place, 0, t.place, 0, cars);
            return t;
        }

        @Override
        public int cars() {
            return cars;
        }

        @Override
        public int humans() {
            return 1;
        }

        @Override
        public float x(int car) {
            return x[car];
        }

        @Override
        public float y(int car) {
            return y[car];
        }

        @Override
        public float z(int car) {
            return z[car];
        }

        @Override
        public int yaw(int car) {
            return 0;
        }

        @Override
        public float speed(int car) {
            return 0F;
        }

        @Override
        public float topSpeed(int car) {
            return 100F;
        }

        @Override
        public float vx(int car) {
            return 0F;
        }

        @Override
        public float vy(int car) {
            return 0F;
        }

        @Override
        public float vz(int car) {
            return 0F;
        }

        @Override
        public int radius(int car) {
            return 120;
        }

        @Override
        public float wheelX(int car, int wheel) {
            return x[car] + ((wheel & 1) == 0 ? -40F : 40F);
        }

        @Override
        public float wheelZ(int car, int wheel) {
            return z[car] + (wheel < 2 ? 70F : -70F);
        }

        @Override
        public boolean grounded(int car) {
            return true;
        }

        @Override
        public boolean wasted(int car) {
            return false;
        }

        @Override
        public boolean throttle(int car) {
            return false;
        }

        @Override
        public boolean steering(int car) {
            return false;
        }

        @Override
        public boolean useKey(int car) {
            return car == 0 && key[0];
        }

        @Override
        public int place(int car) {
            return place[car];
        }

        @Override
        public int wheelDrop(int car) {
            return 30;
        }

        @Override
        public int points() {
            return gates * 2;
        }

        @Override
        public float pointX(int point) {
            return 0F;
        }

        @Override
        public float pointY(int point) {
            return 250F;
        }

        @Override
        public float pointZ(int point) {
            return point * 750F;
        }

        @Override
        public int pointKind(int point) {
            return point % 2 == 1 ? 1 : 0;
        }

        @Override
        public float ground(float px, float py, float pz) {
            return 250F;
        }

        @Override
        public void push(int car, int wheel, float vx, float vy, float vz) {
            pushed[car] += (float) Math.sqrt(vx * vx + vy * vy + vz * vz);
        }

        @Override
        public void damage(int car, int wheel, float share) {
            damaged[car] += share;
        }
    }
}
