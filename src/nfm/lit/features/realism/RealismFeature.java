package nfm.lit.features.realism;

import nfm.lit.audio.Sound3D;
import nfm.lit.features.CarLook;
import nfm.lit.features.CarLooks;
import nfm.lit.features.Canvas;
import nfm.lit.features.Debris;
import nfm.lit.features.Dice;
import nfm.lit.features.DrawQueue;
import nfm.lit.features.Feature;
import nfm.lit.features.Particles;
import nfm.lit.features.RaceView;

/**
 * Damage and heat (realism, every requirement): cars that heat up, get
 * damaged and show it, in a cosmetic way only. It reads the race after each
 * real tick (speed, braking, which corner was hit and how hard), keeps a
 * small state per car ({@link CarState}) and shows it: as particles (steam,
 * smoke, haze, sparks, glass), as debris that stays, as polygons on the car
 * ({@link RealismDrawing}), and by recolouring the car's own polygons (dirt,
 * lights, broken glass) and tilting a bent wheel's
 * ({@link CarLook}). Its sounds play through the 3D sound only.
 * <p>
 * Each car's sheet ({@link CarSheets}) says where its engine, radiators and
 * exhausts are and how its brakes behave.
 */
public final class RealismFeature implements Feature, CarLook {

    public static final String ID = "realism";

    /** Items: particles, then debris, then each car's parts. */
    static final int PARTICLE = 0;
    static final int PARTICLES = 900;
    static final int DEBRIS = 1000;
    static final int DEBRIS_KEPT = 400;
    static final int CAR = 2000;
    static final int PER_CAR = 16;
    static final int DISC = 0;
    static final int FLAME = 4;
    static final int CRACKS = 9;
    static final int SCRATCH = 10;
    static final int LAMPS = 12;
    /** Farther than this from the eye a car's parts are not drawn. */
    private static final float FAR = 12000F;

    private static final int EMPTY_FRAME = 0x1c1e22;
    private static final int BRAKE_LIGHT = 0xff3020;
    private static final int SPARK = 0xffc860;
    private static final int SPARK_HOT = 0xffe8b0;
    private static final float GROUND = 250F;

    /** Where the sounds go: the 3D sound, or a check's ears. */
    interface Ears {
        void play(int car, String clip, float gain, float pitch);
    }

    final Particles particles = new Particles(PARTICLES);
    final Debris debris = new Debris(DEBRIS_KEPT);
    final Dice dice = new Dice(0L, ID);
    Ears ears = RealismFeature::toSound;

    /** A sound to the 3D sound: an exhaust pop from the car's exhaust, anything else at the car. */
    private static void toSound(int car, String clip, float gain, float pitch) {
        if (RealismSounds.POP.equals(clip) || RealismSounds.BACKFIRE.equals(clip)) {
            Sound3D.carPop(car, gain, RealismSounds.BACKFIRE.equals(clip));
        } else {
            Sound3D.carSound(car, clip, gain, pitch);
        }
    }
    CarState[] states = new CarState[0];
    int cars;
    private boolean[] nitrous = new boolean[0];
    private final HeatEffects heat = new HeatEffects(this);
    private final DamageEffects damage = new DamageEffects(this);
    private final RealismDrawing drawing = new RealismDrawing();
    private final float[] p = new float[3];
    private float eyeX;
    private float eyeY;
    private float eyeZ;
    private int lamp = -1;

    @Override
    public String id() {
        return ID;
    }

    @Override
    public void raceStarted(RaceView race) {
        cars = race.cars;
        if (states.length < cars) {
            CarState[] grown = new CarState[cars];
            for (int c = 0; c < cars; c++) {
                grown[c] = c < states.length ? states[c] : new CarState();
            }
            states = grown;
            nitrous = new boolean[cars];
        }
        dice.reset(race.seed, ID);
        particles.clear();
        debris.clear();
        CarLooks.clear(cars);
        CarLooks.keepDebris(debris);
        for (int c = 0; c < cars; c++) {
            CarState s = states[c];
            s.parts.build(race.shape[c], CarSheets.of(race.modelName[c]), race.wheelDrop[c]);
            s.reset(race.groundColor);
            s.pose(race, c, p);
        }
        RealismSounds.make();
    }

    @Override
    public void afterTick(RaceView race) {
        if (race.cars != cars) {
            raceStarted(race);
        }
        for (int c = 0; c < cars; c++) {
            CarState s = states[c];
            s.pose(race, c, p);
            nitrous[c] = race.nitrous[c] && !race.wasted[c];
            heat.tick(race, c, s);
            damage.tick(race, c, s);
        }
        particles.tick(0F, 0F);
    }

    @Override
    public void raceEnded(RaceView race) {
        CarLooks.keepDebris(null);
        CarLooks.clear(cars);
        particles.clear();
        debris.clear();
    }

    /* ------------------------------------------------------------ helpers */

    /** A car's speed as a share of its top speed, 0 to 1. */
    static float speedShare(RaceView race, int c) {
        return CarParts.clamp(Math.abs(race.speed[c]) / Math.max(1F, race.topSpeed[c]));
    }

    /** The height of the ground under car {@code c}: its wheels' where they touch, else below its middle. */
    static float groundUnder(RaceView race, int c, CarState s) {
        float sum = 0F;
        int n = 0;
        for (int w = 0; w < RaceView.WHEELS; w++) {
            int at = c * RaceView.WHEELS + w;
            if (race.contact[at]) {
                sum += race.wheelY[at];
                n++;
            }
        }
        return n > 0 ? sum / n : Math.min(GROUND, s.at[1] + s.parts.ground);
    }

    /** A puff (steam, smoke, haze) from car point {@code (lx, ly, lz)}, moving {@code (vx, vy, vz)} a tick. */
    void puff(CarState s, float lx, float ly, float lz, float vx, float vy, float vz, float life, float from,
            float to, int rgb, float cover, float drag, float rise) {
        s.world(lx, ly, lz, p);
        particles.add(p[0], p[1], p[2], vx, vy, vz, life, from, to, rgb, cover, drag, rise, false);
    }

    /** A spark from world point {@code (x, y, z)} under car {@code c}, thrown back and up. */
    void spark(RaceView race, int c, float x, float y, float z) {
        particles.add(x, y - 0.5F, z, race.vx[c] * 0.4F + dice.between(-1.2F, 1.2F), -dice.between(0.5F, 2.2F),
                race.vz[c] * 0.4F + dice.between(-1.2F, 1.2F), dice.between(5F, 9F), dice.between(0.35F, 0.7F),
                0.3F, dice.next() < 0.5F ? SPARK : SPARK_HOT, 1F, 0.08F, -0.5F, true);
    }

    /** Plays clip {@code clip} at car {@code c}. */
    void sound(int c, String clip, float gain, float pitch) {
        ears.play(c, clip, gain, pitch);
    }

    /** {@code a} mixed toward {@code b} by {@code t} (0 to 1), each 0xRRGGBB. */
    static int mix(int a, int b, float t) {
        float k = CarParts.clamp(t);
        int r = Math.round((a >> 16 & 0xff) + ((b >> 16 & 0xff) - (a >> 16 & 0xff)) * k);
        int g = Math.round((a >> 8 & 0xff) + ((b >> 8 & 0xff) - (a >> 8 & 0xff)) * k);
        int bl = Math.round((a & 0xff) + ((b & 0xff) - (a & 0xff)) * k);
        return r << 16 | g << 8 | bl;
    }

    /** {@code rgb} brightened or darkened by {@code k}. */
    static int scale(int rgb, float k) {
        int r = Math.min(255, Math.round((rgb >> 16 & 0xff) * k));
        int g = Math.min(255, Math.round((rgb >> 8 & 0xff) * k));
        int b = Math.min(255, Math.round((rgb & 0xff) * k));
        return r << 16 | g << 8 | b;
    }

    private static float light(int rgb) {
        return 0.3F * (rgb >> 16 & 0xff) + 0.59F * (rgb >> 8 & 0xff) + 0.11F * (rgb & 0xff);
    }

    /** {@code target} lit as the game lit {@code base} into {@code shown}: the shading kept. */
    private static int shaded(int target, int shown, int base) {
        float lit = light(shown) / Math.max(8F, light(base));
        return scale(target, Math.min(1.6F, lit));
    }

    /* ------------------------------------------------- the car's own polygons */

    @Override
    public int color(int car, int plane, int rgb) {
        lamp = -1;
        if (car < 0 || car >= cars) {
            return rgb;
        }
        CarState s = states[car];
        CarParts parts = s.parts;
        if (parts.shape == null || plane < 0 || plane >= parts.shape.planes) {
            return rgb;
        }
        int base = parts.shape.rgb[plane];
        int out = rgb;
        switch (parts.role[plane]) {
            case CarParts.LIGHT_REAR -> {
                if (s.rearBroken) {
                    lamp = 0;
                    return scale(rgb, 0.35F);
                }
                if (s.braking) {
                    lamp = 1;
                    return mix(scale(rgb, 1.5F), BRAKE_LIGHT, 0.6F);
                }
            }
            case CarParts.LIGHT_FRONT -> {
                if (s.frontBroken) {
                    lamp = 0;
                    return scale(rgb, 0.35F);
                }
            }
            case CarParts.WINDOW_LEFT, CarParts.WINDOW_RIGHT -> {
                int side = parts.role[plane] == CarParts.WINDOW_LEFT ? CarParts.LEFT : CarParts.RIGHT;
                if (s.windowBroken[side]) {
                    return shaded(EMPTY_FRAME, rgb, base);
                }
            }
            default -> {
                // the body and the wheels: only dirt
            }
        }
        float d = s.dirt * parts.low[plane] * 0.8F;
        return d > 0.01F ? mix(out, shaded(s.dirtRgb, rgb, base), d) : out;
    }

    @Override
    public int lamp() {
        return lamp;
    }

    @Override
    public boolean bend(int car, int plane, int[] x, int[] y, int[] z, int n, int[] outX, int[] outY, int[] outZ) {
        if (car < 0 || car >= cars) {
            return false;
        }
        CarState s = states[car];
        CarParts parts = s.parts;
        if (parts.shape == null || plane < 0 || plane >= parts.shape.planes) {
            return false;
        }
        int w = parts.shape.wheel[plane];
        if (w < 0 || s.bent[w] <= 0F) {
            return false;
        }
        // tilted about the upright through the hub: spun with the wheel, the tilt goes round, a wobble
        double a = Math.toRadians(s.bent[w]);
        float sin = (float) Math.sin(a);
        float cos = (float) Math.cos(a);
        float hx = parts.shape.hubX[w];
        float hz = parts.shape.hubZ[w];
        for (int k = 0; k < n; k++) {
            float dx = x[k] - hx;
            float dz = z[k] - hz;
            outX[k] = Math.round(hx + dx * cos - dz * sin);
            outY[k] = y[k];
            outZ[k] = Math.round(hz + dx * sin + dz * cos);
        }
        return true;
    }

    /* ------------------------------------------------------------ drawing */

    @Override
    public void queue(DrawQueue queue) {
        eyeX = queue.eyeX();
        eyeY = queue.eyeY();
        eyeZ = queue.eyeZ();
        particles.queue(queue, PARTICLE);
        for (int k = 0; k < debris.count(); k++) {
            int i = debris.slot(k);
            if (debris.raised(i)) {
                debris.position(i, p);
                // pulled a little towards the eye, so the ramp under it, measured from its middle, is drawn first
                queue.at(eyeX + (p[0] - eyeX) * 0.85F, eyeY + (p[1] - eyeY) * 0.85F, eyeZ + (p[2] - eyeZ) * 0.85F,
                        DEBRIS + i);
            } else {
                queue.ground(DEBRIS + i);
            }
        }
        for (int c = 0; c < cars; c++) {
            queueCar(queue, c, states[c]);
        }
    }

    private void queueCar(DrawQueue queue, int c, CarState s) {
        float dx = s.at[0] - eyeX;
        float dy = s.at[1] - eyeY;
        float dz = s.at[2] - eyeZ;
        if (dx * dx + dy * dy + dz * dz > FAR * FAR || s.parts.shape == null) {
            return;
        }
        CarParts parts = s.parts;
        int base = CAR + c * PER_CAR;
        for (int w = 0; w < RaceView.WHEELS; w++) {
            if (s.brake[w] > HeatEffects.VISIBLE) {
                float out = parts.shape.hubX[w] < 0F ? -1F : 1F;
                float face = parts.shape.hubX[w] + out * parts.shape.wheelHalfWidth[w];
                if (s.faces(face, parts.shape.hubY[w], parts.shape.hubZ[w], out, 0F, 0F, eyeX, eyeY, eyeZ)) {
                    queue.after(c, base + DISC + w);
                }
            }
        }
        for (int pipe = 0; pipe < parts.pipes; pipe++) {
            if (s.flameReach[pipe] > 0F) {
                s.world(parts.pipeX[pipe], parts.pipeY[pipe], parts.pipeZ[pipe], p);
                queue.at(p[0], p[1], p[2], base + FLAME + pipe);
            }
        }
        if (s.stars > 0 && s.faces(parts.screenMid[0], parts.screenMid[1], parts.screenMid[2], parts.screenN[0],
                parts.screenN[1], parts.screenN[2], eyeX, eyeY, eyeZ)) {
            queue.after(c, base + CRACKS);
        }
        for (int side = 0; side < 2; side++) {
            float out = side == CarParts.LEFT ? -1F : 1F;
            if (s.scratches[side] > 0 && s.faces(out * parts.halfWidth, (parts.top + parts.bottom) / 2F,
                    (parts.front + parts.rear) / 2F, out, 0F, 0F, eyeX, eyeY, eyeZ)) {
                queue.after(c, base + SCRATCH + side);
            }
        }
        if (parts.drawnLamps && s.braking && !s.rearBroken
                && s.faces(0F, parts.top, parts.rear, 0F, 0F, -1F, eyeX, eyeY, eyeZ)) {
            queue.after(c, base + LAMPS);
        }
    }

    @Override
    public void draw(Canvas canvas, int item) {
        if (item < DEBRIS) {
            particles.draw(canvas, item - PARTICLE);
            return;
        }
        if (item < CAR) {
            int i = item - DEBRIS;
            if (i >= 0 && i < debris.capacity()) {
                debris.draw(canvas, i);
            }
            return;
        }
        int c = (item - CAR) / PER_CAR;
        int part = (item - CAR) % PER_CAR;
        if (c < 0 || c >= cars) {
            return;
        }
        CarState s = states[c];
        if (part < FLAME) {
            drawing.disc(canvas, c, s, part - DISC);
        } else if (part < CRACKS) {
            drawing.flame(canvas, c, s, part - FLAME, nitrous[c]);
        } else if (part == CRACKS) {
            drawing.cracks(canvas, c, s);
        } else if (part < LAMPS) {
            drawing.scratches(canvas, c, s, part - SCRATCH);
        } else if (part == LAMPS) {
            drawing.lamps(canvas, c, s);
        }
    }
}
