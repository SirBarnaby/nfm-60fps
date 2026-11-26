package nfm.lit.audio;

/**
 * The layers heard over a car's engine with the rebuilt engine sound, each
 * a real recording that loops and follows the car: its turbo's whistle or
 * its supercharger's whine, the gear whine of reverse (and a truck's
 * beeper), its tyres rolling on a hard road or on loose ground, and its
 * radiator fan when it stands. This is only the reckoning: how loud each is
 * and how it is bent, from what the game publishes of the car after each
 * tick and the car's kit. {@link Sound3D} plays them; nothing here touches
 * OpenAL, so the extras' checks can run it.
 */
public final class EngineLayers {

    public static final int CHARGER = 0;
    public static final int WHINE = 1;
    public static final int BEEP = 2;
    public static final int ROAD = 3;
    public static final int GRAVEL = 4;
    public static final int FAN = 5;
    public static final int COUNT = 6;

    /* the 3D sound's clips for them (the enginesound extra gives the recordings under these names) */
    public static final String TURBO_CLIP = "eng-turbo";
    public static final String BLOWER_CLIP = "eng-blower";
    public static final String BLOW_OFF_CLIP = "eng-blowoff";
    public static final String BACKFIRE_CLIP = "eng-backfire";
    public static final String[] POP_CLIPS = {"eng-pop1", "eng-pop2", "eng-pop3"};
    private static final String[] CLIPS = {null, "eng-whine", "eng-beep", "eng-road", "eng-gravel", "eng-fan"};

    /** What forces air into the engine. */
    public static final int NO_CHARGER = 0;
    public static final int TURBO = 1;
    public static final int BLOWER = 2;

    /** What a car is fitted with, as its sheet has it; the same for the whole race. */
    public static final class Kit {
        public int charger = NO_CHARGER;
        /** A truck: it beeps while it reverses. */
        public boolean beeps;
        /** A radiator fan at the front, heard when the car stands. */
        public boolean fan;
        /** How loud its engine is, 1 for most: a strong engine carries further, a quiet car is harder to place. */
        public float loud = 1F;
        /** Semitones its recordings bend across a third of a speed band: a wide rev range bends further. */
        public float revs = 4F;
        /** Its skids' pitch, 1 for a wheel of the usual size: a big wheel skids lower. */
        public float skidPitch = 1F;
    }

    /** A kit with nothing fitted, for a car the extra has not described. */
    public static final Kit PLAIN = new Kit();

    /* how fast things follow, seconds */
    static final float SPOOL_UP = 0.45F;
    static final float SPOOL_DOWN = 0.18F;
    static final float FOLLOW = 0.07F;
    static final float AIR_UP = 0.35F;
    static final float AIR_DOWN = 0.12F;
    /** A turbo spinning faster than this when the throttle lifts blows off. */
    static final float BLOW_OFF_FROM = 0.55F;
    /* levels, against the engine's own (the charger, whine and fan) or the game's effects (road, beeper) */
    static final float TURBO_LEVEL = 0.5F;
    static final float BLOWER_LEVEL = 0.45F;
    static final float WHINE_LEVEL = 0.55F;
    static final float BEEP_LEVEL = 0.5F;
    static final float ROAD_LEVEL = 0.4F;
    static final float FAN_LEVEL = 0.2F;

    /** Each layer's level now (0 silent) and how its recording is bent (1 as recorded). */
    public final float[] gain = new float[COUNT];
    public final float[] pitch = {1F, 1F, 1F, 1F, 1F, 1F};
    /** The turbo's speed, 0 to 1. */
    float spool;
    /** How far the revs have climbed with the wheels off the ground and the throttle held, 0 to 1 (the limiter). */
    float air;
    private boolean wasOn;
    private float blowOff;
    private float blower;
    private float roll;

    /** The clip layer {@code layer} loops for a car with kit {@code kit}; null for none. */
    public static String clip(int layer, Kit kit) {
        if (layer == CHARGER) {
            return kit.charger == TURBO ? TURBO_CLIP : kit.charger == BLOWER ? BLOWER_CLIP : null;
        }
        return CLIPS[layer];
    }

    /**
     * Brings the layers on by {@code dt} seconds.
     *
     * @param share where the car is in its speed band, 0 to 1 (its revs)
     * @param reverseTop the speed its reverse whine is highest at (its first band's end)
     * @param top its top speed (its last band's end)
     */
    public void update(Sound3D.Car car, Kit kit, float share, float reverseTop, float top, float dt, boolean muted) {
        boolean running = car.running && !muted;
        boolean on = running && car.throttle;
        float speed = Math.abs(car.speed);
        // the wheels in the air with the throttle held: nothing holds the engine back, the revs run up to the limiter
        boolean free = on && !car.grounded;
        air += ((free ? 1F : 0F) - air) * ease(dt, free ? AIR_UP : AIR_DOWN);
        float revs = Math.max(share, air);

        if (kit.charger == TURBO) {
            float target = on ? 0.3F + 0.7F * revs : running ? 0.05F * revs : 0F;
            spool += (target - spool) * ease(dt, target > spool ? SPOOL_UP : SPOOL_DOWN);
            if (wasOn && !on && running && spool > BLOW_OFF_FROM) {
                blowOff = spool; // the throttle shut on a spinning turbo: the air it packed is let go
                spool *= 0.35F;
            }
            gain[CHARGER] = (float) Math.pow(spool, 1.5D) * TURBO_LEVEL;
            pitch[CHARGER] = 0.75F + 0.65F * spool;
        } else if (kit.charger == BLOWER) {
            // belt-driven: it follows the revs at once, and howls under load
            float target = running ? (on ? 1F : 0.3F) * (0.25F + 0.75F * revs) : 0F;
            blower += (target - blower) * ease(dt, FOLLOW);
            gain[CHARGER] = blower * BLOWER_LEVEL;
            pitch[CHARGER] = 0.7F + 0.8F * revs;
        } else {
            gain[CHARGER] = 0F;
        }
        wasOn = on;

        boolean backing = running && car.grounded && car.speed < -1F;
        float back = backing ? Math.min(1F, speed / Math.max(1F, reverseTop)) : 0F;
        gain[WHINE] += (Math.min(1F, back * 3F) * WHINE_LEVEL - gain[WHINE]) * ease(dt, FOLLOW);
        pitch[WHINE] = 0.7F + 0.9F * back;
        gain[BEEP] += ((kit.beeps && backing ? BEEP_LEVEL : 0F) - gain[BEEP]) * ease(dt, 0.03F);

        float rolling = running && car.grounded ? Math.min(1F, speed / Math.max(1F, top)) : 0F;
        roll += (rolling - roll) * ease(dt, FOLLOW);
        float level = (float) Math.pow(roll, 0.8D) * ROAD_LEVEL;
        float loose = Integer.bitCount(car.loose & 15) / 4F;
        gain[ROAD] = level * (1F - loose);
        gain[GRAVEL] = level * loose;
        pitch[ROAD] = pitch[GRAVEL] = 0.75F + 0.6F * roll;

        float standing = running && kit.fan ? Math.max(0F, 1F - speed / Math.max(1F, 0.25F * top)) : 0F;
        gain[FAN] += (standing * FAN_LEVEL - gain[FAN]) * ease(dt, 0.5F);

        for (int i = 0; i < COUNT; i++) {
            if (gain[i] < 0.001F) {
                gain[i] = 0F;
            }
        }
    }

    /** How far the revs have run up in the air, 0 to 1: the engine's mix plays its top recording by this much. */
    public float air() {
        return air;
    }

    /** How hard the turbo just blew off (0: it did not); asked once, it is forgotten. */
    public float takeBlowOff() {
        float b = blowOff;
        blowOff = 0F;
        return b;
    }

    /** Everything falls silent at once: the race ended or the car is gone. */
    public void clear() {
        java.util.Arrays.fill(gain, 0F);
        spool = 0F;
        air = 0F;
        blower = 0F;
        roll = 0F;
        blowOff = 0F;
        wasOn = false;
    }

    private static float ease(float dt, float time) {
        return 1F - (float) Math.exp(-dt / time);
    }
}
