package nfm.lit.features.skidmarks;

import fallk.logmaster.HLogger;
import java.util.Arrays;
import nfm.lit.features.Canvas;
import nfm.lit.features.CarLooks;
import nfm.lit.features.Dice;
import nfm.lit.features.DrawQueue;
import nfm.lit.features.Feature;
import nfm.lit.features.FeatureSettings;
import nfm.lit.features.Particles;
import nfm.lit.features.RaceView;

/**
 * Skidmarks, made per wheel (effects, "Skidmarks are per wheel" and "show
 * each maneuver"): each wheel that touches the ground and slides leaves a
 * mark whose kind (locked, sideways, both, spinning) sets its shade, and
 * that ends where the wheel rolls cleanly again. A tyre that has been on
 * loose ground carries its colour onto the road for a while ("carry the
 * surface's colour"); sliding wheels throw smoke on the road, dust and
 * clods on loose ground, and a body scraping the road sparks and leaves a
 * metal scrape ("Skidding leaves particles"). What a slide sheds stays on
 * the track and is stirred by the cars driving past ({@link Litter}, "The
 * track is littered by the end of a race").
 * <p>
 * It reads the race and throws its own dice, seeded from the race, so a race
 * replayed looks the same; it never changes the race.
 */
public final class SkidmarksFeature implements Feature {

    public static final String ID = "skidmarks";
    /** The detail switch: 0 simple, 1 full (streaks and striations). */
    static final String DETAIL = "skiddetail";
    /** How much wider than the tread's own swing a bent wheel's wavy mark is drawn, so it reads at a distance. */
    private static final float WAVE = 2.5F;
    /**
     * How many times longer the wave is than one turn of the wheel: marks are
     * laid once a tick, a wheel turns nearly half a turn in one at speed, and
     * a wave that short would show as a zigzag.
     */
    private static final float WAVE_LENGTH = 2.5F;

    /** Item: every piece of mark on the road. Below it, the slot of a piece on a ramp; from {@link #PARTICLE}, a particle. */
    static final int GROUND_ITEM = MarkRing.CAPACITY;
    static final int PARTICLE = 2 * MarkRing.CAPACITY;
    static final int PARTICLES = 640;
    /** Items: every bit of litter on the road; from {@link #LITTER}, the slot of a bit on a ramp. */
    static final int LITTER_GROUND = MarkRing.CAPACITY + 1;
    static final int LITTER = PARTICLE + PARTICLES;

    private static final float DEG = (float) (Math.PI / 180D);
    /** Pieces farther than this from the eye are not drawn: the fog has hidden them. */
    private static final float FAR = 12000F;
    /** A tyre's width, world units (about 28 cm). */
    private static final float TYRE = 16F;
    /** A body scrape's width. */
    private static final float SCRAPE = 7F;
    /** How far a tyre rolls on the road before most of the mud on it is gone (e-fold, units: 10 m). */
    static final float MUD_FADE = 600F;
    /** Ticks of sideways scrub after a hard shove from the side. */
    static final int SCRUB_TICKS = 4;
    /** A hit at least this hard, across the car, shoves it sideways. */
    private static final float SHOVE = 30F;
    /** The flat ground, and how near it a wheel's lowest point counts as on it. */
    private static final float GROUND = 250F;
    private static final float NEAR_GROUND = 8F;

    /** Rubber, by kind of mark (SkidSlip's kinds, then MarkRing.METAL). */
    static final int[] RUBBER = {0x2a2a2c, 0x1e1e20, 0x3a3a3c, 0x2a2a2c, 0x18181a, 0xafafb4};

    final SkidSlip slip = new SkidSlip();
    final MarkRing ring = new MarkRing();
    final Particles particles = new Particles(PARTICLES);
    final Litter litter = new Litter();
    private final Dice dice = new Dice(0L, ID);
    private float windX;
    private float windZ;
    private int cars;
    private int tick;

    /* per wheel */
    float[] intensity = new float[0];
    /** How much loose ground the tyre carries, 0 to 1, and its colour. */
    float[] mud = new float[0];
    int[] mudRgb = new int[0];
    /** Ticks of sideways scrub left after a shove. */
    int[] scrub = new int[0];
    /** How far each wheel has turned, radians: where a bent wheel's wobble is (realism, "A bent wheel wobbles"). */
    float[] rolled = new float[0];

    private final float[] point = new float[3];
    private final float[] other = new float[3];
    /** How many particles a slide throws: all of them with the full detail, half with the simple one. */
    private float throwing = 1F;

    /* the frame being drawn */
    private boolean detail;
    private float eyeX;
    private float eyeY;
    private float eyeZ;

    @Override
    public String id() {
        return ID;
    }

    @Override
    public void raceStarted(RaceView race) {
        cars = race.cars;
        int wheels = cars * RaceView.WHEELS;
        if (intensity.length < wheels) {
            intensity = new float[wheels];
            mud = new float[wheels];
            mudRgb = new int[wheels];
            scrub = new int[wheels];
            rolled = new float[wheels];
        }
        Arrays.fill(intensity, 0F);
        Arrays.fill(mud, 0F);
        Arrays.fill(scrub, 0);
        Arrays.fill(rolled, 0F);
        // each car's four wheels, then a body scrape per car
        ring.fit(wheels + cars);
        ring.clear();
        particles.clear();
        litter.start(race);
        slip.reset();
        dice.reset(race.seed, ID);
        windX = dice.between(-0.6F, 0.6F);
        windZ = dice.between(-0.6F, 0.6F);
    }

    @Override
    public void afterTick(RaceView race) {
        if (race.cars != cars) {
            raceStarted(race); // cannot happen within a race; start afresh rather than read past the arrays
        }
        tick = race.tick;
        throwing = FeatureSettings.get(DETAIL) != 0 ? 1F : 0.5F;
        slip.update(race);
        shoves(race);
        for (int c = 0; c < cars; c++) {
            for (int w = 0; w < RaceView.WHEELS; w++) {
                wheel(race, c, c * RaceView.WHEELS + w);
            }
            body(race, c);
        }
        particles.tick(windX, windZ);
        litter.tick(race);
    }

    @Override
    public void raceEnded(RaceView race) {
        HLogger.info("[extras] skidmarks: " + ring.made + " pieces of mark in " + tick + " ticks, " + ring.count
                + " kept; " + litter.dropped + " bits of litter, " + litter.count + " lying");
    }

    /** A hard hit across a car shoves it sideways: each of its wheels on the ground scrubs a short mark from there. */
    private void shoves(RaceView race) {
        for (int h = 0; h < race.hits; h++) {
            int c = race.hitCar[h];
            int axis = race.hitAxis[h];
            if (c < 0 || c >= cars || axis == 1 || Math.abs(race.hitForce[h]) < SHOVE) {
                continue; // up and down is a landing or a bump, not a shove
            }
            float yaw = race.xz[c] * DEG;
            // the hit pushes along a world axis: how much of that is across the car
            float across = axis == 0 ? (float) Math.cos(yaw) : (float) Math.sin(yaw);
            if (Math.abs(across) < 0.5F) {
                continue;
            }
            for (int w = 0; w < RaceView.WHEELS; w++) {
                scrub[c * RaceView.WHEELS + w] = SCRUB_TICKS;
            }
        }
    }

    /** Wheel {@code at} of car {@code c}: its mark goes on, starts or ends, and it throws what it throws. */
    private void wheel(RaceView race, int c, int at) {
        boolean touching = race.contact[at];
        boolean loose = race.surface[at] != 0;
        float speed = race.groundSpeed(c);
        if (touching) {
            if (loose) {
                mud[at] = Math.min(1F, mud[at] + 0.2F);
                mudRgb[at] = race.groundRgb[at];
            } else {
                mud[at] *= (float) Math.exp(-speed / MUD_FADE);
            }
        }
        float radius = CarLooks.radius(c, at % RaceView.WHEELS);
        if (radius > 0F) {
            rolled[at] = (rolled[at] + speed / (radius * WAVE_LENGTH)) % (float) (2D * Math.PI);
        }
        float s = slip.slide[at];
        int kind = slip.kind[at];
        boolean shoved = scrub[at] > 0;
        if (shoved) {
            scrub[at]--;
        }
        boolean open = ring.isOpen(at);
        boolean go;
        if (!touching) {
            go = false;
        } else if (shoved) {
            go = speed > 1F || s > SkidSlip.OFF;
        } else if (open) {
            go = s >= SkidSlip.OFF && slip.ratio[at] >= SkidSlip.CLEAN;
        } else {
            float on = slip.landing(c) ? SkidSlip.OFF : SkidSlip.ON;
            go = s > on && slip.ratio[at] > 0.12F;
        }
        if (!go) {
            if (open) {
                ring.close(at);
            }
            intensity[at] = 0F;
            return;
        }
        if (kind == SkidSlip.NONE) {
            kind = SkidSlip.SIDEWAYS;
        }
        float target = Math.max(0.15F, Math.min(1F, (s - SkidSlip.OFF) / 14F));
        if (shoved) {
            target = Math.max(target, 0.55F);
        }
        float now = intensity[at];
        // a faint onset that deepens as the slide holds
        intensity[at] = !open ? 0.2F : now < target ? Math.min(target, now + 0.3F) : target;

        int color;
        float cover;
        if (loose) {
            color = MarkRing.darker(race.groundRgb[at], 0.55F); // a rut in the ground itself
            cover = intensity[at] * 0.6F;
        } else {
            color = mix(RUBBER[kind], MarkRing.darker(mudRgb[at], 0.7F), mud[at] * 0.75F);
            cover = intensity[at] * 0.85F;
        }
        // where the tyre points: the fronts as they are steered
        float heading = (race.xz[c] + (race.keyZ[at] > 0 ? race.steer[c] : 0)) * DEG;
        float hx = -(float) Math.sin(heading);
        float hz = (float) Math.cos(heading);
        float y = race.wheelY[at];
        if (!race.onRamp[at] && Math.abs(y - GROUND) < NEAR_GROUND) {
            y = GROUND;
        }
        // a bent wheel's tread swings from side to side once a turn: its mark is wavy
        float sway = wobble(c, at);
        ring.mark(at, race.wheelX[at] + hz * sway, y, race.wheelZ[at] - hx * sway, hx, hz, TYRE, color, cover, kind,
                race.onRamp[at], tick);
        throwFrom(race, c, at, s, loose, y);
        litter.shed(race.wheelX[at], y, race.wheelZ[at], race.wheelVX[at], race.wheelVZ[at], intensity[at], loose,
                race.groundRgb[at], race.onRamp[at]);
    }

    /** How far wheel {@code at}'s tread is to the side of its hub now, from how bent it is and how far it has turned. */
    float wobble(int c, int at) {
        float bent = CarLooks.bent(c, at % RaceView.WHEELS);
        if (bent == 0F) {
            return 0F;
        }
        float reach = CarLooks.radius(c, at % RaceView.WHEELS) * (float) Math.sin(Math.toRadians(bent)) * WAVE;
        return reach * (float) Math.sin(rolled[at]);
    }

    /** What a sliding wheel throws: smoke from the road, dust and clods from loose ground. */
    private void throwFrom(RaceView race, int c, int at, float s, boolean loose, float y) {
        float vx = race.wheelVX[at];
        float vz = race.wheelVZ[at];
        float x = race.wheelX[at];
        float z = race.wheelZ[at];
        float level = intensity[at] * throwing;
        if (!loose) {
            if (s > 7F && dice.next() < level) {
                particles.add(x, y - 6F, z, vx * 0.3F + dice.between(-1F, 1F), -dice.between(0.5F, 1.5F),
                        vz * 0.3F + dice.between(-1F, 1F), dice.between(22F, 34F), 10F, 45F, 0xcdcdcd,
                        0.45F * Math.min(1F, s / 14F), 0.08F, 0.05F, false);
            }
            return;
        }
        if (dice.next() < level * 0.7F) {
            int dust = brighter(race.groundRgb[at], 1.25F);
            particles.add(x, y - 4F, z, vx * 0.35F + dice.between(-1.5F, 1.5F), -dice.between(0.8F, 2F),
                    vz * 0.35F + dice.between(-1.5F, 1.5F), dice.between(18F, 28F), 8F, 38F, dust, 0.4F, 0.1F,
                    0.03F, false);
        }
        if (dice.next() < level * 0.5F) {
            int clod = MarkRing.darker(race.groundRgb[at], 0.6F);
            int slot = particles.add(x, y - 3F, z, vx * 0.5F + dice.between(-3F, 3F), -dice.between(4F, 9F),
                    vz * 0.5F + dice.between(-3F, 3F), dice.between(12F, 20F), 2.5F, 2.5F, clod, 1F, 0.02F, -0.9F,
                    false);
            particles.floor(slot, y);
            particles.keep(slot); // with the damage extra on, a clod stays where it falls
        }
    }

    /**
     * Car {@code c}'s body on the road: rolled far enough that its side edge
     * touches, or on its roof, it scrapes a metal mark and sparks.
     */
    private void body(RaceView race, int c) {
        int key = cars * RaceView.WHEELS + c;
        float speed = race.groundSpeed(c);
        boolean touch = false;
        if (speed > 2F) {
            float ground = groundUnder(race, c);
            int roll = Math.round(SkidSlip.wrap(race.xy[c]));
            float half = halfTrack(race, c) + 6F;
            float low = race.wheelDrop[c] * 0.5F;
            if (race.capsized[c] || Math.abs(roll) >= 130) {
                race.toWorld(c, 0F, -race.wheelDrop[c], 0F, point);
                touch = point[1] > ground - NEAR_GROUND;
            } else if (Math.abs(roll) > 50) {
                race.toWorld(c, -half, low, 0F, point);
                race.toWorld(c, half, low, 0F, other);
                if (other[1] > point[1]) {
                    System.arraycopy(other, 0, point, 0, 3);
                }
                touch = point[1] > ground - NEAR_GROUND;
            }
            if (touch) {
                float heading = race.xz[c] * DEG;
                float gy = Math.abs(point[1] - GROUND) < NEAR_GROUND ? GROUND : Math.min(point[1], ground);
                ring.mark(key, point[0], gy, point[2], -(float) Math.sin(heading), (float) Math.cos(heading),
                        SCRAPE, RUBBER[MarkRing.METAL], 0.7F, MarkRing.METAL, false, tick);
                sparks(race, c, point[0], gy, point[2]);
            }
        }
        if (!touch && ring.isOpen(key)) {
            ring.close(key);
        }
    }

    private void sparks(RaceView race, int c, float x, float y, float z) {
        int count = throwing < 1F ? 1 : 2;
        for (int k = 0; k < count; k++) {
            particles.add(x, y - 2F, z, race.vx[c] * 0.6F + dice.between(-4F, 4F), -dice.between(2F, 6F),
                    race.vz[c] * 0.6F + dice.between(-4F, 4F), dice.between(6F, 10F), 1.6F, 0.8F, 0xffdc8c, 1F,
                    0.05F, -0.6F, true);
        }
    }

    /** The height of the ground under car {@code c}: its lowest wheel that touches, else the flat ground. */
    private static float groundUnder(RaceView race, int c) {
        float g = Float.NaN;
        for (int w = 0; w < RaceView.WHEELS; w++) {
            int at = c * RaceView.WHEELS + w;
            if (race.contact[at] && (Float.isNaN(g) || race.wheelY[at] > g)) {
                g = race.wheelY[at];
            }
        }
        return Float.isNaN(g) ? GROUND : g;
    }

    private static float halfTrack(RaceView race, int c) {
        float half = 0F;
        for (int w = 0; w < RaceView.WHEELS; w++) {
            half = Math.max(half, Math.abs(race.keyX[c * RaceView.WHEELS + w]));
        }
        return half;
    }

    /* ------------------------------------------------------------ drawing */

    @Override
    public void queue(DrawQueue queue) {
        detail = FeatureSettings.get(DETAIL) != 0;
        eyeX = queue.eyeX();
        eyeY = queue.eyeY();
        eyeZ = queue.eyeZ();
        boolean road = false;
        for (int i = 0; i < ring.count; i++) {
            int q = ring.slot(i);
            if (!ring.raised[q]) {
                road = true;
            } else if (distance(q) < FAR) {
                // pulled a little towards the eye, so the ramp under it, measured from its middle, is drawn first
                float mx = mid(ring.cornerX, q);
                float mz = mid(ring.cornerZ, q);
                float my = (ring.startY[q] + ring.endY[q]) / 2F;
                queue.at(eyeX + (mx - eyeX) * 0.85F, eyeY + (my - eyeY) * 0.85F, eyeZ + (mz - eyeZ) * 0.85F, q);
            }
        }
        if (road) {
            queue.ground(GROUND_ITEM);
        }
        litter.queue(queue, LITTER_GROUND, LITTER);
        particles.queue(queue, PARTICLE);
    }

    @Override
    public void draw(Canvas canvas, int item) {
        if (item >= LITTER) {
            litter.draw(canvas, item - LITTER);
        } else if (item >= PARTICLE) {
            particles.draw(canvas, item - PARTICLE);
        } else if (item == LITTER_GROUND) {
            litter.drawGround(canvas);
        } else if (item == GROUND_ITEM) {
            for (int i = 0; i < ring.count; i++) {
                int q = ring.slot(i);
                if (!ring.raised[q]) {
                    float d = distance(q);
                    if (d < FAR) {
                        ring.draw(canvas, q, detail, d);
                    }
                }
            }
        } else if (item >= 0 && item < MarkRing.CAPACITY) {
            ring.draw(canvas, item, detail, distance(item));
        }
    }

    private float distance(int q) {
        float dx = mid(ring.cornerX, q) - eyeX;
        float dz = mid(ring.cornerZ, q) - eyeZ;
        float dy = ring.endY[q] - eyeY;
        return (float) Math.sqrt(dx * dx + dy * dy + dz * dz);
    }

    private static float mid(float[] corners, int q) {
        return (corners[q * 4] + corners[q * 4 + 2]) / 2F;
    }

    /** {@code a} moved {@code share} of the way to {@code b}. */
    static int mix(int a, int b, float share) {
        float t = Math.max(0F, Math.min(1F, share));
        int r = Math.round((a >> 16 & 0xff) + ((b >> 16 & 0xff) - (a >> 16 & 0xff)) * t);
        int g = Math.round((a >> 8 & 0xff) + ((b >> 8 & 0xff) - (a >> 8 & 0xff)) * t);
        int bl = Math.round((a & 0xff) + ((b & 0xff) - (a & 0xff)) * t);
        return r << 16 | g << 8 | bl;
    }

    static int brighter(int color, float f) {
        int r = Math.min(255, (int) ((color >> 16 & 0xff) * f));
        int g = Math.min(255, (int) ((color >> 8 & 0xff) * f));
        int b = Math.min(255, (int) ((color & 0xff) * f));
        return r << 16 | g << 8 | b;
    }
}
