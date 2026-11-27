package nfm.lit.features;

import static nfm.lit.features.Synth.decay;
import static nfm.lit.features.Synth.pcm;

import nfm.lit.audio.Sound3D;
import nfm.lit.features.Synth.Noise;
import nfm.lit.features.sounds.Recordings;

/**
 * The look and the sound of an explosion, shared by the extras that make
 * one (dynamite, the bomb, an exploding wreck): a fireball of dithered
 * puffs, clods of earth and sparks, a crater left in the road, the bang,
 * the game's white flash and the controller's rumble, and the smoke.
 * <p>
 * The smoke is dynamic: a column that rises from the blast and a low ring
 * that rolls out along the ground. From then on it lives with what is
 * around it: it drifts with the race's wind ({@link Wind}), stays on top of
 * the ground it was made over, is shoved aside and dragged along by every
 * car that drives through it, and a later explosion blows the smoke that is
 * still there outwards.
 * <p>
 * It is a look only, made after real ticks from a copy of the race
 * ({@link RaceView}); the race half is {@link BlastPush}. Each extra keeps
 * one of its own and gives it a share of its draw items: craters at
 * {@code base + 0..15}, particles from {@code base + }{@link #PARTICLES_AT}.
 */
public final class Explosions {

    /** The clips: a big explosion (dynamite, a wreck) and the bomb's bang. */
    public static final String BLAST = "xpl-blast";
    public static final String BANG = "xpl-bang";

    /** Craters kept for the race; the oldest makes way. */
    public static final int CRATERS = 16;
    /** Where the particles' draw items start, after the craters'. */
    public static final int PARTICLES_AT = 100;
    /** Draw items one of these needs. */
    public static final int ITEMS = PARTICLES_AT + 640;

    /** The fireball's colours, from its white-hot middle out. */
    private static final int[] FIRE = {0xFFE48A, 0xFFB43C, 0xFF7A1E, 0xE8501A};
    /** An explosion of size 1 this near a player's car flashes their screen and is heard in Classic sound. */
    private static final float NEAR_PLAYER = 6000F;
    /** It shakes a controller within this far, by how near. */
    private static final float RUMBLE_REACH = 830F;
    /** It blows the smoke already there outwards within this far, this fast at its middle. */
    private static final float WAVE_REACH = 900F;
    private static final float WAVE_PUSH = 14F;
    /** A car shoves smoke within this much more than its radius aside, and drags it along within twice that. */
    private static final float CAR_REACH = 80F;
    private static final float CAR_ASIDE = 3F;
    private static final float CAR_ALONG = 0.05F;
    private static final float WAKE_ALONG = 0.015F;
    private static final float CAR_UP = 200F;

    /* the craters: a scorched patch, a bowl this deep and a raised lip, in rings of this many sides */
    private static final int SIDES = 12;
    /** A crater of size 1: how deep its bowl is, and how wide its floor and its mouth are (radii). Rain fills it. */
    public static final float DEPTH = 18F;
    public static final float FLOOR = 34F;
    public static final float MOUTH = 92F;
    private static final float LIP = 7F;
    private static final int SOOT = 0x2A241E;
    private static final float[] RING_COS = new float[SIDES + 1];
    private static final float[] RING_SIN = new float[SIDES + 1];

    static {
        for (int k = 0; k <= SIDES; k++) {
            double a = k * 2D * Math.PI / SIDES;
            RING_COS[k] = (float) Math.cos(a);
            RING_SIN[k] = (float) Math.sin(a);
        }
    }

    /* light from above, a little from the side (y grows downwards) */
    private static final float LIGHT_X = 0.36F;
    private static final float LIGHT_Y = -0.84F;
    private static final float LIGHT_Z = 0.40F;

    private static boolean soundsMade;

    /** The particles: the owner may add its own (a throw's sparks). */
    public final Particles particles = new Particles(ITEMS - PARTICLES_AT);
    private final String owner;
    private final Dice dice;
    private final float[] wind = new float[2];
    private final float[] at = new float[3];

    private final float[] craterX = new float[CRATERS];
    private final float[] craterY = new float[CRATERS];
    private final float[] craterZ = new float[CRATERS];
    private final float[] craterSize = new float[CRATERS];
    private int craters;

    private int groundColor = SOOT;
    private float eyeX;
    private float eyeZ;

    private final float[] px = new float[Canvas.MAX_CORNERS];
    private final float[] py = new float[Canvas.MAX_CORNERS];
    private final float[] pz = new float[Canvas.MAX_CORNERS];

    /** The explosions of extra {@code owner} (its id: its dice are its own). */
    public Explosions(String owner) {
        this.owner = owner;
        dice = new Dice(0L, owner);
    }

    /** Makes the explosions' sounds, once for the game: recordings, with a synthesised boom if one is missing. */
    public static synchronized void sounds() {
        if (soundsMade) {
            return;
        }
        soundsMade = true;
        if (!Recordings.give(BLAST, "blast", true)) {
            give(BLAST, boom(2.4F, 35F));
        }
        if (!Recordings.give(BANG, "bang", true)) {
            give(BANG, boom(1.2F, 60F));
        }
    }

    private static void give(String name, short[] samples) {
        Sound3D.extraClip(name, samples, 22050);
        Features.hooks().classicClip(name, samples, 22050);
    }

    /** A race starts: no smoke, no craters, the race's wind. */
    public void start(RaceView r) {
        particles.clear();
        craters = 0;
        dice.reset(r.seed, owner);
        Wind.of(r.seed, wind);
        groundColor = r.groundColor;
    }

    /** The wind the smoke drifts with, along x and z, units a tick. */
    public float windX() {
        return wind[0];
    }

    public float windZ() {
        return wind[1];
    }

    /** Craters left so far in this race (more than {@link #CRATERS}: the oldest are gone). */
    public int craters() {
        return craters;
    }

    /**
     * An explosion at {@code (x, y, z)} over ground at {@code ground}:
     * {@code size} 1 for a dynamite stick, smaller for less. It leaves a
     * crater if {@code crater}, plays clip {@code clip} there, and flashes
     * the screen of a player near it if {@code flash}.
     */
    public void explode(RaceView r, float x, float y, float z, float ground, boolean crater, float size,
            String clip, boolean flash) {
        GameHooks hooks = Features.hooks();
        groundColor = r.groundColor;
        float nearest = Float.MAX_VALUE;
        int nearestPlayer = 0;
        float rumble = RUMBLE_REACH * size;
        for (int p = 0; p < r.humans && p < r.cars; p++) {
            float dx = r.x[p] - x;
            float dy = r.y[p] - y;
            float dz = r.z[p] - z;
            float d = (float) Math.sqrt(dx * dx + dy * dy + dz * dz);
            if (d < nearest) {
                nearest = d;
                nearestPlayer = p;
            }
            if (d < rumble) {
                hooks.rumble(p, 250F * size * (1F - d / rumble));
            }
        }
        float near = NEAR_PLAYER * Math.min(1F, size + 0.25F);
        if (hooks.sound3d()) {
            Sound3D.pointSound(x, y, z, clip, 1.4F * (float) Math.sqrt(size), dice.between(0.94F, 1.06F));
        } else if (nearest < near) {
            hooks.classic(nearestPlayer, clip, GameHooks.PLAY);
        }
        if (flash && nearest < near) {
            hooks.flash();
        }
        wave(x, y, z, size);
        fireball(x, y - 30F * size, z, size);
        smoke(x, y - 20F * size, z, ground, size);
        debris(x, y - 10F, z, ground, size);
        float cratered = size;
        if (crater) {
            int i = craters % CRATERS;
            craterX[i] = x;
            craterY[i] = ground;
            craterZ[i] = z;
            craterSize[i] = size * dice.between(0.85F, 1.15F);
            cratered = craterSize[i];
            craters++;
        }
        Weather.blast(x, ground, z, cratered, crater, r.tick);
    }

    /** The smoke already there is blown outwards, the nearest the most. */
    private void wave(float x, float y, float z, float size) {
        if (particles.count() == 0) {
            return;
        }
        float reach = WAVE_REACH * size;
        float[] p = at;
        for (int i = 0; i < particles.capacity(); i++) {
            if (!particles.isPuff(i) || !particles.position(i, p)) {
                continue;
            }
            float dx = p[0] - x;
            float dy = p[1] - y;
            float dz = p[2] - z;
            float d = (float) Math.sqrt(dx * dx + dy * dy + dz * dz);
            if (d >= reach || d < 1F) {
                continue;
            }
            float push = WAVE_PUSH * size * (1F - d / reach) / d;
            particles.push(i, dx * push, dy * push * 0.5F, dz * push);
        }
    }

    /** Glowing orange and yellow puffs that swell fast and are gone in about 0.6 s. */
    private void fireball(float x, float y, float z, float size) {
        int n = Math.max(6, (int) (16F * size));
        for (int k = 0; k < n; k++) {
            float a = dice.between(0F, 6.2832F);
            float out = dice.between(3F, 11F) * size;
            particles.add(x + dice.between(-30F, 30F) * size, y + dice.between(-30F, 20F) * size,
                    z + dice.between(-30F, 30F) * size, (float) Math.cos(a) * out, dice.between(-9F, -2F) * size,
                    (float) Math.sin(a) * out, dice.between(9F, 13F), dice.between(30F, 55F) * size,
                    dice.between(130F, 190F) * size, FIRE[k % FIRE.length], 0.95F, 0.22F, 0.5F, false);
        }
    }

    /**
     * The smoke: a column that rises and spreads and stays about eight
     * seconds, and a low ring of smoke and dust rolling out along the
     * ground. None of it sinks below the ground it was made over.
     */
    private void smoke(float x, float y, float z, float ground, float size) {
        int column = Math.max(8, (int) (28F * size));
        for (int k = 0; k < column; k++) {
            float a = dice.between(0F, 6.2832F);
            float out = dice.between(1.5F, 9F) * size;
            int grey = (int) dice.between(52F, 96F);
            int rgb = grey << 16 | (grey - 3) << 8 | (grey - 6);
            int puff = particles.add(x + dice.between(-60F, 60F) * size, y + dice.between(-40F, 30F) * size,
                    z + dice.between(-60F, 60F) * size, (float) Math.cos(a) * out, dice.between(-4F, -0.5F),
                    (float) Math.sin(a) * out, dice.between(120F, 170F), dice.between(50F, 90F) * size,
                    dice.between(220F, 330F) * size, rgb, 0.8F, 0.07F, 0.025F, false);
            particles.floor(puff, ground - 20F);
        }
        int ring = Math.max(6, (int) (14F * size));
        float first = dice.between(0F, 6.2832F);
        for (int k = 0; k < ring; k++) {
            float a = first + k * 6.2832F / ring + dice.between(-0.2F, 0.2F);
            float out = dice.between(7F, 15F) * size;
            int grey = (int) dice.between(70F, 110F);
            int rgb = mix(grey << 16 | grey << 8 | grey, groundColor, 0.35F);
            int puff = particles.add(x + (float) Math.cos(a) * 40F * size, ground - 25F,
                    z + (float) Math.sin(a) * 40F * size, (float) Math.cos(a) * out, dice.between(-0.6F, 0F),
                    (float) Math.sin(a) * out, dice.between(110F, 170F), dice.between(40F, 60F) * size,
                    dice.between(170F, 240F) * size, rgb, 0.6F, 0.09F, 0F, false);
            particles.floor(puff, ground - 15F);
        }
    }

    /** Clods of earth thrown up and falling back, and sparks. */
    private void debris(float x, float y, float z, float ground, float size) {
        int clods = Math.max(4, (int) (16F * size));
        for (int k = 0; k < clods; k++) {
            float a = dice.between(0F, 6.2832F);
            float out = dice.between(5F, 15F) * size;
            int clod = particles.add(x, y, z, (float) Math.cos(a) * out, dice.between(-26F, -11F) * size,
                    (float) Math.sin(a) * out, dice.between(30F, 45F), dice.between(6F, 11F), 6F, k % 3 == 0
                            ? 0x3A2E22 : 0x5C4A34, 1F, 0.02F, -1.3F, false);
            particles.floor(clod, ground);
        }
        int sparks = Math.max(4, (int) (12F * size));
        for (int k = 0; k < sparks; k++) {
            float a = dice.between(0F, 6.2832F);
            float out = dice.between(18F, 36F) * size;
            int spark = particles.add(x, y, z, (float) Math.cos(a) * out, dice.between(-24F, -6F),
                    (float) Math.sin(a) * out, dice.between(7F, 12F), 3F, 2F, 0xFFD27A, 1F, 0.05F, -0.9F, true);
            particles.floor(spark, ground);
        }
    }

    /**
     * A real tick has ended: every car in the smoke shoves it aside and
     * drags it along, a little of it follows in the car's wake, and all of
     * it moves on with the wind.
     */
    public void tick(RaceView r) {
        if (particles.count() == 0) {
            return;
        }
        float[] p = at;
        for (int i = 0; i < particles.capacity(); i++) {
            if (!particles.isPuff(i) || !particles.position(i, p)) {
                continue;
            }
            for (int c = 0; c < r.cars; c++) {
                float dx = p[0] - r.x[c];
                float dy = p[1] - r.y[c];
                float dz = p[2] - r.z[c];
                float reach = r.radius[c] + CAR_REACH;
                float wake = reach * 2F;
                float d2 = dx * dx + dz * dz;
                if (d2 > wake * wake || Math.abs(dy) > CAR_UP) {
                    continue;
                }
                float d = (float) Math.sqrt(d2) + 1F;
                if (d < reach) {
                    float shove = CAR_ASIDE * (1F - d / reach);
                    particles.push(i, r.vx[c] * CAR_ALONG + dx / d * shove, -0.4F, r.vz[c] * CAR_ALONG + dz / d * shove);
                } else {
                    float share = WAKE_ALONG * (1F - d / wake);
                    particles.push(i, r.vx[c] * share, 0F, r.vz[c] * share);
                }
            }
        }
        particles.tick(wind[0], wind[1]);
    }

    /* ------------------------------------------------------------ drawing */

    /** Queues the craters and the particles: items {@code base + 0..}{@link #ITEMS}. */
    public void queue(DrawQueue queue, int base) {
        eyeX = queue.eyeX();
        eyeZ = queue.eyeZ();
        for (int i = 0; i < Math.min(craters, CRATERS); i++) {
            queue.ground(base + i);
        }
        particles.queue(queue, base + PARTICLES_AT);
    }

    /** Draws item {@code item} (less the base given to {@link #queue}). */
    public void draw(Canvas canvas, int item) {
        if (item >= PARTICLES_AT) {
            particles.draw(canvas, item - PARTICLES_AT);
        } else if (item >= 0 && item < CRATERS) {
            crater(canvas, item);
        }
    }

    /** Crater {@code i}: a scorched patch, then a bowl inside a raised lip, its faces shaded by their slope. */
    private void crater(Canvas canvas, int i) {
        float x = craterX[i];
        float y = craterY[i];
        float z = craterZ[i];
        float s = craterSize[i];
        int ground = groundColor;
        int burnt = mix(ground, SOOT, 0.7F);
        int earth = mix(ground, 0x5A4A38, 0.6F);
        // the scorched patch, see-through
        canvas.dither(0.55F);
        canvas.color(canvas.fog(mix(ground, 0x141210, 0.8F), x, y, z));
        for (int k = 0; k < SIDES; k++) {
            px[k] = x + RING_COS[k] * 200F * s;
            py[k] = y - 0.5F;
            pz[k] = z + RING_SIN[k] * 200F * s;
        }
        canvas.world(px, py, pz, SIDES);
        canvas.dither(1F);
        // the bowl's floor
        canvas.color(canvas.fog(lit(burnt, 0F, -1F, 0F), x, y, z));
        for (int k = 0; k < SIDES; k++) {
            px[k] = x + RING_COS[k] * FLOOR * s;
            py[k] = y + DEPTH * s;
            pz[k] = z + RING_SIN[k] * FLOOR * s;
        }
        canvas.world(px, py, pz, SIDES);
        // the rings, the far side first
        double eye = Math.atan2(eyeZ - z, eyeX - x);
        int start = (int) Math.round((eye + Math.PI) / (2D * Math.PI) * SIDES);
        for (int n = 0; n < SIDES; n++) {
            int k = Math.floorMod(start + (n % 2 == 0 ? n / 2 : -(n + 1) / 2), SIDES);
            ring(canvas, x, y, z, k, FLOOR * s, DEPTH * s, MOUTH * s, 0F, burnt);
            ring(canvas, x, y, z, k, MOUTH * s, 0F, 118F * s, -LIP * s, earth);
            ring(canvas, x, y, z, k, 118F * s, -LIP * s, 150F * s, 0F, earth);
        }
    }

    /** Side {@code k} of a ring from radius {@code r0} at depth {@code d0} out to {@code r1} at {@code d1}. */
    private void ring(Canvas canvas, float x, float y, float z, int k, float r0, float d0, float r1, float d1,
            int rgb) {
        float c0 = RING_COS[k];
        float s0 = RING_SIN[k];
        float c1 = RING_COS[k + 1];
        float s1 = RING_SIN[k + 1];
        px[0] = x + c0 * r0;
        py[0] = y + d0;
        pz[0] = z + s0 * r0;
        px[1] = x + c1 * r0;
        py[1] = y + d0;
        pz[1] = z + s1 * r0;
        px[2] = x + c1 * r1;
        py[2] = y + d1;
        pz[2] = z + s1 * r1;
        px[3] = x + c0 * r1;
        py[3] = y + d1;
        pz[3] = z + s0 * r1;
        // the slope's normal: outward along the middle of the side, tilted by how the ring rises
        float cm = (c0 + c1) / 2F;
        float sm = (s0 + s1) / 2F;
        float run = r1 - r0;
        float rise = d1 - d0; // negative: rises outward (y grows downwards)
        float len = (float) Math.sqrt(run * run + rise * rise);
        float nx = -cm * (-rise) / len;
        float nz = -sm * (-rise) / len;
        float ny = -run / len;
        canvas.color(canvas.fog(lit(rgb, nx, ny, nz), x, y, z));
        canvas.world(px, py, pz, 4);
    }

    /** {@code rgb} lit by the light from above: a face turned to it full, one turned away darker. */
    private static int lit(int rgb, float nx, float ny, float nz) {
        float d = -(nx * LIGHT_X + ny * LIGHT_Y + nz * LIGHT_Z);
        float k = 0.62F + 0.38F * Math.max(-0.4F, Math.min(1F, d));
        int r = Math.min(255, (int) (((rgb >> 16) & 0xFF) * k));
        int g = Math.min(255, (int) (((rgb >> 8) & 0xFF) * k));
        int b = Math.min(255, (int) ((rgb & 0xFF) * k));
        return r << 16 | g << 8 | b;
    }

    /** {@code a} turned {@code share} of the way to {@code b}. */
    private static int mix(int a, int b, float share) {
        int r = (int) (((a >> 16) & 0xFF) + ((((b >> 16) & 0xFF) - ((a >> 16) & 0xFF)) * share));
        int g = (int) (((a >> 8) & 0xFF) + ((((b >> 8) & 0xFF) - ((a >> 8) & 0xFF)) * share));
        int bl = (int) ((a & 0xFF) + (((b & 0xFF) - (a & 0xFF)) * share));
        return r << 16 | g << 8 | bl;
    }

    /** A falling boom, a roar of low noise dying away, and debris crackling: what plays if a recording is missing. */
    private static short[] boom(float seconds, float lowest) {
        int rate = 22050;
        float[] out = new float[(int) (rate * seconds)];
        Noise noise = new Noise(107);
        float lo = 0F;
        float lower = 0F;
        float phase = 0F;
        for (int i = 0; i < out.length; i++) {
            float t = i / (float) rate;
            float hz = lowest + 55F * decay(t, 0.25F);
            phase += hz / rate;
            float thump = (float) Math.sin(2D * Math.PI * phase) * decay(t, 0.45F);
            float n = noise.next();
            lo += (n - lo) * 0.12F;
            lower += (lo - lower) * 0.05F;
            float roar = (lo * 0.8F + lower * 4F) * decay(t, seconds / 4F);
            float crack = n * decay(t, 0.015F);
            out[i] = thump * 1.1F + roar + crack * 0.8F;
        }
        return pcm(out, 0.9F);
    }
}
