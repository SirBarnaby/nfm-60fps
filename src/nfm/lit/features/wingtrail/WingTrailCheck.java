package nfm.lit.features.wingtrail;

import static nfm.lit.features.ExtrasCheck.check;
import static nfm.lit.features.ExtrasCheck.n;

import nfm.lit.features.CheckCanvas;
import nfm.lit.features.RaceView;

/**
 * The wing trail's checks (effects, "The Radical One's wing trail", "The
 * wing trail looks like vapour"): a made-up Radical One flown with and
 * without steering, on the ground, and another car flown the same way.
 */
public final class WingTrailCheck {

    private static final String F = "wing trail";
    private static final int SCALE_WHEEL_X = 67; // the game's Radical One: 42 in the file, scaled by 1.6

    private RaceView v;
    private WingTrailFeature trail;
    private final float[] p = new float[3];

    private WingTrailCheck() {
    }

    public static void run() {
        WingTrailCheck c = new WingTrailCheck();
        c.steeringInTheAir();
        c.look();
        c.fades();
    }

    private void start(int model) {
        v = new RaceView();
        v.fit(1);
        v.cars = 1;
        v.humans = 1;
        v.seed = 3L;
        v.model[0] = model;
        int[] kx = {-SCALE_WHEEL_X, SCALE_WHEEL_X, -SCALE_WHEEL_X, SCALE_WHEEL_X};
        int[] kz = {115, 115, -134, -134};
        for (int w = 0; w < RaceView.WHEELS; w++) {
            v.keyX[w] = kx[w];
            v.keyZ[w] = kz[w];
        }
        trail = new WingTrailFeature();
        trail.raceStarted(v);
    }

    /** One tick: the car at height {@code y} moving forward 40 a tick, in the air or not, the keys as given. */
    private void tick(float z, float y, boolean air, boolean left, boolean up) {
        v.tick++;
        v.x[0] = 0F;
        v.y[0] = y;
        v.z[0] = z;
        v.xz[0] = 0;
        v.vx[0] = 0F;
        v.vz[0] = 40F;
        v.left[0] = left;
        v.up[0] = up;
        v.grounded[0] = !air;
        v.airTicks[0] = air ? v.airTicks[0] + 1 : 0;
        for (int w = 0; w < RaceView.WHEELS; w++) {
            v.contact[w] = !air;
        }
        trail.afterTick(v);
    }

    private int points() {
        return trail.count[0] + trail.count[1];
    }

    /** Only steering in the air: on the ground, or in the air without steering, or another car, nothing. */
    private void steeringInTheAir() {
        start(WingTips.RADICAL_ONE);
        for (int t = 0; t < 8; t++) {
            tick(40F * t, 150F, true, true, false);
        }
        int steering = points();
        boolean both = trail.count[0] > 0 && trail.count[1] > 0;
        start(WingTips.RADICAL_ONE);
        for (int t = 0; t < 8; t++) {
            tick(40F * t, 230F, false, true, true);
        }
        int ground = points();
        start(WingTips.RADICAL_ONE);
        for (int t = 0; t < 8; t++) {
            tick(40F * t, 150F, true, false, false);
        }
        int still = points();
        start(WingTips.RADICAL_ONE - 1);
        for (int t = 0; t < 8; t++) {
            tick(40F * t, 150F, true, true, false);
        }
        int other = points();
        // only after 0.15 s in the air
        start(WingTips.RADICAL_ONE);
        tick(0F, 150F, true, true, false);
        tick(40F, 150F, true, true, false);
        int early = points();
        check(F, "steering in the air", steering > 0 && both && ground == 0 && still == 0 && other == 0
                && early == 0, "steering in the air " + steering + " points; on the ground " + ground
                        + "; in the air not steering " + still + "; another car " + other + "; before 0.15 s "
                        + early);
    }

    /** Thin white streams from both tips, spreading and fading within about a second, the newest stretch moving with the car. */
    private void look() {
        start(WingTips.RADICAL_ONE);
        for (int t = 0; t < 6; t++) {
            tick(40F * t, 150F, true, true, false);
        }
        // the newest points are at the two tips, the widest points at the back
        float[] tip = WingTips.of(WingTips.RADICAL_ONE);
        float scale = WingTips.scale(v, 0);
        float worst = 0F;
        for (int side = 0; side < 2; side++) {
            v.toWorld(0, (side == 0 ? -1F : 1F) * tip[0] * scale, tip[1] * scale, tip[2] * scale, p);
            int i = side * WingTrailFeature.POINTS + trail.head[side];
            worst = Math.max(worst, (float) Math.hypot(Math.hypot(trail.px[i] - p[0], trail.py[i] - p[1]),
                    trail.pz[i] - p[2]));
        }
        boolean apart = Math.abs(trail.tipX[0] - trail.tipX[1]) > 150F;
        CheckCanvas canvas = new CheckCanvas();
        int drawn = canvas.frame(trail, 0F, 50F, -600F, v.tick);
        boolean white = (canvas.lastColor >> 16 & 0xff) > 230 && (canvas.lastColor & 0xff) > 230;
        boolean thin = WingTrailFeature.width(0) < 1.5F && WingTrailFeature.width(WingTrailFeature.LIFE)
                > 4F * WingTrailFeature.width(0);
        boolean fading = WingTrailFeature.shade(WingTrailFeature.LIFE, 1F) <= 0.001F
                && WingTrailFeature.shade(10, 1F) < 0.6F * WingTrailFeature.shade(0, 1F);
        boolean smooth = canvas.moving >= 2 && Math.abs(trail.tipVZ[0] - 40F) < 0.01F;
        check(F, "its look: thin white streams from both tips", worst < 1F && apart && drawn > 0 && white && thin,
                "newest points " + n(worst) + " from the tips, " + n(Math.abs(trail.tipX[0] - trail.tipX[1]))
                        + " apart; half width " + n(WingTrailFeature.width(0)) + " at the tip, "
                        + n(WingTrailFeature.width(WingTrailFeature.LIFE)) + " a second on; colour "
                        + Integer.toHexString(canvas.lastColor) + "; " + drawn + " stretches drawn");
        // a point lives a second: steer on, then keep flying without keys
        for (int t = 6; t < 6 + WingTrailFeature.LIFE + 6; t++) {
            tick(40F * t, 150F, true, false, false);
        }
        check(F, "its look: spreading and fading within a second", fading && points() == 0, "cover "
                + n(WingTrailFeature.shade(0, 1F)) + " new, " + n(WingTrailFeature.shade(10, 1F)) + " at half a second, "
                + n(WingTrailFeature.shade(WingTrailFeature.LIFE, 1F)) + " at a second; points left a second after "
                + points());
        check(F, "its look: no steps", smooth, canvas.moving + " stretches reach the tip as drawn, moving "
                + n(trail.tipVZ[0]) + " a tick with the car");
    }

    /** Letting go fades the trail; landing fades it quickly. */
    private void fades() {
        start(WingTips.RADICAL_ONE);
        for (int t = 0; t < 8; t++) {
            tick(40F * t, 150F, true, true, false);
        }
        float full = trail.level[0];
        tick(320F, 150F, true, false, false);
        float released = trail.level[0];
        int ticks = 1;
        while (trail.level[0] > 0F && ticks < 20) {
            tick(320F + 40F * ticks, 150F, true, false, false);
            ticks++;
        }
        start(WingTips.RADICAL_ONE);
        for (int t = 0; t < 8; t++) {
            tick(40F * t, 150F, true, true, false);
        }
        int landing = 0;
        do {
            tick(320F + 40F * landing, 230F, false, true, false);
            landing++;
        } while (trail.level[0] > 0F && landing < 20);
        check(F, "fades: letting go, landing", full == 1F && released > 0F && released < full && ticks >= 3
                && landing <= 2, "full " + n(full) + ", a tick after letting go " + n(released) + ", gone in "
                        + ticks + " ticks; landing, gone in " + landing);
    }
}
