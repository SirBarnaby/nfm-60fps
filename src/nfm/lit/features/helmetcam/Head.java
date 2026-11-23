package nfm.lit.features.helmetcam;

/**
 * The driver's head on the neck: a damped spring on each axis of the car's
 * own frame (0 side to side, x to the right; 1 up and down, y down; 2
 * forward and back, z forward), driven by the car's acceleration, as a
 * racing simulator's head physics are (stiffness and damping per axis, with
 * limits; the car moves the head, never the other way). Braking throws the
 * head forward, acceleration back, a corner to its outside, a landing down,
 * the air up a little. The head also looks into the corner: toward where the
 * car turns and where it slides.
 * <p>
 * Worked out once a race tick (19 a second), in the game's units (60 to a
 * metre); each frame shows it between {@link #before} and {@link #offset}.
 * Drawing only: it reads the race, never writes it.
 */
final class Head {

    /** The game's gravity, units a tick a tick: 1 g (Madness adds it to each wheel's fall every tick). */
    static final float G = 7F;
    /** How far one g moves the settled head on each axis, units. */
    static final float[] PER_G = {7F, 5F, 9F};
    /** The farthest the head goes on each axis, units. */
    static final float[] LIMIT = {12F, 9F, 16F};
    /** More than this on each axis is a crash: felt as this. Up and down takes more, for a landing's jolt. */
    static final float[] MAX_G = {3F, 5F, 3F};
    /** The neck's natural frequency, radians a tick (about 1.6 Hz), and its damping ratio: a little overshoot. */
    static final float OMEGA = 0.53F;
    static final float DAMPING = 0.45F;
    /** Steps a tick, for a spring this stiff to stay steady. */
    static final int STEPS = 4;
    /** A car moved farther than this in one tick was put back on the road: the head starts again. */
    static final float JUMP = 400F;
    /** Degrees the head looks into a corner for each degree a tick the car turns, and for each degree it slides. */
    static final float LOOK_TURN = 2.6F;
    static final float LOOK_SLIDE = 0.5F;
    /** The farthest the head turns, degrees, and the share of the way it turns each tick. */
    static final float LOOK_MAX = 20F;
    static final float LOOK_FOLLOW = 0.3F;
    /** Below this speed over the ground (units a tick) a car is not sliding anywhere. */
    static final float SLIDE_SPEED = 6F;
    /** Degrees the head nods down for each unit it is thrown forward (up for each unit back). */
    static final float NOD = 0.3F;

    /** Where the head is on each axis now (after the last tick) and at the tick before, units. */
    final float[] offset = new float[3];
    final float[] before = new float[3];
    private final float[] speed = new float[3];
    /** How far the head looks into the corner now and at the tick before, degrees (more turns it left). */
    float look;
    float lookBefore;
    /** The g-force the head feels now and at the tick before. */
    float felt;
    float feltBefore;

    private final float[] local = new float[3];
    private boolean known;
    private float lastX;
    private float lastY;
    private float lastZ;
    private float lastVx;
    private float lastVy;
    private float lastVz;
    private int lastYaw;

    /** Back at rest, as when a race starts. */
    void reset() {
        java.util.Arrays.fill(offset, 0F);
        java.util.Arrays.fill(before, 0F);
        java.util.Arrays.fill(speed, 0F);
        look = lookBefore = 0F;
        felt = feltBefore = 0F;
        known = false;
    }

    /**
     * One race tick: the car is at {@code (x, y, z)}, moving {@code (vx,
     * vy, vz)} units a tick, turned {@code yaw}, {@code pitch} and
     * {@code roll} degrees (ContO's xz, zy and xy).
     */
    void tick(float x, float y, float z, float vx, float vy, float vz, int yaw, int pitch, int roll) {
        System.arraycopy(offset, 0, before, 0, 3);
        lookBefore = look;
        feltBefore = felt;
        float dx = x - lastX;
        float dy = y - lastY;
        float dz = z - lastZ;
        if (!known || dx * dx + dy * dy + dz * dz > JUMP * JUMP) {
            reset();
            known = true;
            remember(x, y, z, vx, vy, vz, yaw);
            return;
        }
        // what the car's acceleration does to the head, in the car's frame
        toCar(vx - lastVx, vy - lastVy, vz - lastVz, yaw, pitch, roll, local);
        float h = 1F / STEPS;
        for (int a = 0; a < 3; a++) {
            float g = Math.max(-MAX_G[a], Math.min(MAX_G[a], -local[a] / G));
            float target = g * PER_G[a];
            float at = offset[a];
            float v = speed[a];
            for (int s = 0; s < STEPS; s++) {
                v += (OMEGA * OMEGA * (target - at) - 2F * DAMPING * OMEGA * v) * h;
                at += v * h;
            }
            if (Math.abs(at) > LIMIT[a]) {
                at = Math.copySign(LIMIT[a], at);
                v = 0F;
            }
            offset[a] = at;
            speed[a] = v;
        }
        // into the corner: where the car turns (more yaw turns it left) and where it slides
        int turn = Math.floorMod(yaw - lastYaw + 180, 360) - 180;
        toCar(vx, vy, vz, yaw, pitch, roll, local);
        float slide = 0F;
        if (local[2] > 0F && local[0] * local[0] + local[2] * local[2] > SLIDE_SPEED * SLIDE_SPEED) {
            // degrees to the right of the nose it moves; none going backwards
            slide = Math.max(-45F, Math.min(45F, (float) Math.toDegrees(Math.atan2(local[0], local[2]))));
        }
        float lookTarget = Math.max(-LOOK_MAX, Math.min(LOOK_MAX, turn * LOOK_TURN - slide * LOOK_SLIDE));
        look += (lookTarget - look) * LOOK_FOLLOW;
        felt = felt();
        remember(x, y, z, vx, vy, vz, yaw);
    }

    /**
     * The g-force the head feels now: how far it is thrown on each axis, in
     * g. Up and down only counts pressed down (a landing, the bottom of a
     * ramp): floating in the air is the lack of a force, not one.
     */
    private float felt() {
        float sum = 0F;
        for (int a = 0; a < 3; a++) {
            float g = (a == 1 ? Math.max(0F, offset[a]) : offset[a]) / PER_G[a];
            sum += g * g;
        }
        return (float) Math.sqrt(sum);
    }

    private void remember(float x, float y, float z, float vx, float vy, float vz, int yaw) {
        lastX = x;
        lastY = y;
        lastZ = z;
        lastVx = vx;
        lastVy = vy;
        lastVz = vz;
        lastYaw = yaw;
    }

    /**
     * World vector {@code (wx, wy, wz)} in the frame of a car turned
     * {@code yaw}, {@code pitch} and {@code roll} degrees, into
     * {@code out}: the inverse of how the game turns a car's points
     * (roll, then pitch, then yaw; see RaceView#toWorld).
     */
    static void toCar(float wx, float wy, float wz, int yaw, int pitch, int roll, float[] out) {
        double y = Math.toRadians(yaw);
        double p = Math.toRadians(pitch);
        double r = Math.toRadians(roll);
        float sy = (float) Math.sin(y);
        float cy = (float) Math.cos(y);
        float sp = (float) Math.sin(p);
        float cp = (float) Math.cos(p);
        float sr = (float) Math.sin(r);
        float cr = (float) Math.cos(r);
        float x1 = wx * cy + wz * sy;
        float z2 = -wx * sy + wz * cy;
        float y1 = wy * cp + z2 * sp;
        out[2] = -wy * sp + z2 * cp;
        out[0] = x1 * cr + y1 * sr;
        out[1] = -x1 * sr + y1 * cr;
    }
}
