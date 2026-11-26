package nfm.lit.features.helmetcam;

import fallk.logmaster.HLogger;
import java.util.Locale;
import nfm.lit.features.Canvas;
import nfm.lit.features.DrawQueue;
import nfm.lit.features.Feature;
import nfm.lit.features.RaceView;

/**
 * The helmet cam (switch {@code helmetcam}): a first-person view from the
 * driver's head, which sits on springs ({@link Head}): braking throws it
 * forward, acceleration back, corners to the side, landings jolt it; it looks
 * into the corner; the edges of the view darken under g-force. Around the
 * eyes is the car's inside ({@link Interior}).
 * <p>
 * Drawing only. The view key adds the helmet after the game's three views;
 * while it shows, the race's own view is the chase view (placed, recorded and
 * read by the race as always) and only the frames are drawn from here. Each
 * human player (two in split screen) has a head. The game asks, when it
 * places a frame's camera, for the helmet's camera on the car's pose at the
 * tick before or now ({@link #camera}), whether a polygon of the player's
 * own car is drawn from the eyes ({@link #shows}) and how dark the edges are
 * ({@link #vignette}).
 */
public final class HelmetCam implements Feature {

    public static final String ID = "helmetcam";
    /** Human players: one, or two in split screen. */
    static final int PLAYERS = 2;
    /** The car's pitch the view follows, through a sine, at most this many degrees: never upside down. */
    static final float PITCH_FOLLOW = 55F;
    /** The eyes look this many degrees down from level, at the road ahead. */
    static final float LOOK_DOWN = 3F;
    /** The vignette starts at this g, is strongest at {@link #VIGNETTE_FULL} g, and darkens the edges at most this much. */
    static final float VIGNETTE_FROM = 0.35F;
    static final float VIGNETTE_FULL = 1.8F;
    static final float VIGNETTE_MOST = 0.55F;

    /** The one instance, in the extras' list; the game's hooks reach it here. */
    public static final HelmetCam FEATURE = new HelmetCam();

    /** How much of the way to where the keys hold it the steering wheel turns in a tick. */
    static final float STEER_FOLLOW = 0.3F;

    private final Head[] heads = {new Head(), new Head()};
    private final Cockpit[] cockpits = {new Cockpit(), new Cockpit()};
    private int players;
    private boolean running;
    /** The player whose frame is being drawn from the helmet, or -1 (see {@link #drawingFor}). */
    private int framing = -1;
    /* each player's car after the last tick, for the stage's light on the dashboard */
    private final float[] carX = new float[PLAYERS];
    private final float[] carY = new float[PLAYERS];
    private final float[] carZ = new float[PLAYERS];
    /* what the inside shows: the steering wheel's turn (-1 to 1), the speed and the power (0 to 1) */
    private final float[] steer = new float[PLAYERS];
    private final float[] speed = new float[PLAYERS];
    private final float[] power = new float[PLAYERS];
    private final Interior interior = new Interior();

    private HelmetCam() {
    }

    @Override
    public String id() {
        return ID;
    }

    @Override
    public void raceStarted(RaceView race) {
        players = Math.min(PLAYERS, Math.min(race.humans, race.cars));
        for (int p = 0; p < players; p++) {
            heads[p].reset();
            steer[p] = 0F;
            speed[p] = 0F;
            power[p] = 0F;
            Cockpit c = cockpits[p];
            c.build(race.shape[p]);
            HLogger.info(String.format(Locale.ROOT,
                    "[extras] helmet cam: %s, eyes at %.0f, %.0f, %.0f; %d of %d polygons drawn from them, the nearest"
                            + " %.0f ahead", race.modelName[p], c.eyeX, c.eyeY, c.eyeZ, c.drawn(), c.polygons,
                    c.nearest(race.shape[p])));
        }
        running = true;
    }

    @Override
    public void afterTick(RaceView race) {
        for (int p = 0; p < players && p < race.cars; p++) {
            heads[p].tick(race.x[p], race.y[p], race.z[p], race.vx[p], race.vy[p], race.vz[p], race.xz[p],
                    race.zy[p], race.xy[p]);
            carX[p] = race.x[p];
            carY[p] = race.y[p];
            carZ[p] = race.z[p];
            float held = (race.right[p] ? 1F : 0F) - (race.left[p] ? 1F : 0F);
            steer[p] += (held - steer[p]) * STEER_FOLLOW;
            speed[p] = Math.min(1F, Math.abs(race.speed[p]) / Math.max(1F, race.topSpeed[p]));
            power[p] = Math.max(0F, Math.min(1F, race.power[p] / 98F));
        }
    }

    @Override
    public void raceEnded(RaceView race) {
        running = false;
        framing = -1;
    }

    /** The next frame is drawn from {@code player}'s helmet, or with -1 from the race's own camera. */
    public void drawingFor(int player) {
        framing = ready(player) ? player : -1;
    }

    /** In a frame from the helmet: the car's inside, right after the player's car, over it. */
    @Override
    public void queue(DrawQueue queue) {
        if (framing >= 0) {
            queue.after(framing, framing);
        }
    }

    @Override
    public void draw(Canvas canvas, int item) {
        if (item < 0 || item >= players) {
            return;
        }
        interior.draw(canvas, item, cockpits[item], steer[item], speed[item], power[item], carX[item], carY[item],
                carZ[item]);
    }

    /** The player whose frame is being drawn from the helmet now, or -1 for a frame from the race's own camera. */
    public int framing() {
        return framing;
    }

    /** True if {@code player}'s car has a roof over the eyes (glass around them): rain drums on it. */
    public boolean closed(int player) {
        return ready(player) && cockpits[player].closed;
    }

    /** True while the extra is on in a race and has a head for {@code player}. */
    public boolean ready(int player) {
        return running && player >= 0 && player < players;
    }

    /** True if polygon {@code plane} of {@code player}'s own car is drawn from the eyes. */
    public boolean shows(int player, int plane) {
        return cockpits[player].shows(plane);
    }

    /**
     * {@code player}'s camera as the game holds one ({@code Medium.x, y, z},
     * the yaw {@code xz} and the pitch {@code zy}, degrees), as real numbers
     * into {@code out[0..4]}: the eyes of the car posed {@code x, y, z,
     * xz, xy, zy}, with the head as it was after the tick before
     * ({@code before}) or after the last tick. {@code lookback} (the
     * look-back key, -1, 0 or 1) turns the head round. {@code cx, cy, cz}
     * are the camera's centre ({@code Medium.cx, cy, cz}): the game turns
     * the camera about a point {@code cz} ahead of the eyes, so the numbers
     * are worked out from the eyes accordingly.
     */
    public void camera(int player, boolean before, int x, int y, int z, int xz, int xy, int zy, int lookback,
            int cx, int cy, int cz, double[] out) {
        Head head = heads[player];
        Cockpit cockpit = cockpits[player];
        float[] offset = before ? head.before : head.offset;
        float look = before ? head.lookBefore : head.look;
        // the eyes in the world, through the car's whole pose (as RaceView.toWorld)
        double lx = cockpit.eyeX + offset[0];
        double ly = cockpit.eyeY + offset[1];
        double lz = cockpit.eyeZ + offset[2];
        double roll = Math.toRadians(xy);
        double pitch = Math.toRadians(zy);
        double yaw = Math.toRadians(xz);
        double x1 = lx * Math.cos(roll) - ly * Math.sin(roll);
        double y1 = lx * Math.sin(roll) + ly * Math.cos(roll);
        double y2 = y1 * Math.cos(pitch) - lz * Math.sin(pitch);
        double z2 = y1 * Math.sin(pitch) + lz * Math.cos(pitch);
        double eyeX = x + x1 * Math.cos(yaw) - z2 * Math.sin(yaw);
        double eyeY = y + y2;
        double eyeZ = z + x1 * Math.sin(yaw) + z2 * Math.cos(yaw);
        // where they look: the car's heading and the head's turn; down a little, nodding with the head
        double heading = xz + look + (lookback != 0 ? 180D : 0D);
        double cameraYaw = -heading;
        double cameraPitch = -PITCH_FOLLOW * Math.sin(pitch) + LOOK_DOWN + Head.NOD * offset[2];
        double cy0 = Math.toRadians(cameraYaw);
        double cp0 = Math.toRadians(cameraPitch);
        out[0] = eyeX - cx + cz * Math.cos(cp0) * Math.sin(cy0);
        out[1] = eyeY - cy + cz * Math.sin(cp0);
        out[2] = eyeZ - cz + cz * Math.cos(cp0) * Math.cos(cy0);
        out[3] = cameraYaw - 360D * Math.floor(cameraYaw / 360D);
        out[4] = cameraPitch;
    }

    /** How much {@code player}'s view darkens at its edges, 0 to 1, {@code share} of the way from the tick before to now. */
    public float vignette(int player, float share) {
        Head head = heads[player];
        float t = Math.max(0F, Math.min(1F, share));
        float felt = head.feltBefore + (head.felt - head.feltBefore) * t;
        float strength = (felt - VIGNETTE_FROM) / (VIGNETTE_FULL - VIGNETTE_FROM);
        return Math.max(0F, Math.min(1F, strength)) * VIGNETTE_MOST;
    }

    /** How far {@code player}'s steering wheel is turned, -1 to 1, for the check. */
    float steer(int player) {
        return steer[player];
    }

    /** {@code player}'s head, for the check. */
    Head head(int player) {
        return heads[player];
    }

    /** {@code player}'s cockpit, for the check. */
    Cockpit cockpit(int player) {
        return cockpits[player];
    }
}
