package nfm.lit.features.weather;

import nfm.lit.features.Canvas;
import nfm.lit.features.DrawQueue;
import nfm.lit.features.Feature;
import nfm.lit.features.FeatureSettings;
import nfm.lit.features.Features;
import nfm.lit.features.GameHooks;
import nfm.lit.features.RaceView;
import nfm.lit.features.StageAir;
import nfm.lit.features.Weather;
import nfm.lit.features.Wind;
import nfm.lit.features.helmetcam.HelmetCam;

/**
 * The weather (weather, "Rain and snow"): rain, rain with fog, or snow, in
 * every race while its switch says so. It changes the look and the sound
 * only ("Weather never changes how the cars drive"): it reads the race
 * through its copy and never writes it.
 * <p>
 * As a stage loads it greys or pales the stage's sky, light, fog and ground
 * ({@link Air}). In the race rain or snow falls through the world around
 * each player's camera ({@link Fall}), rain splashes on the ground, drops
 * sit on the screen and more of them close behind another car
 * ({@link Lens}), the wet lies as a sheen on the roads and the cars, snow
 * lies on them and gets deeper to a cap, and the rain, the rain on the
 * roof and the snow's wind are heard ({@link WeatherSounds}).
 * <p>
 * The sky and the fog are the stage's: they change with the next race when
 * the switch is turned during one; what falls follows the switch at once.
 */
public final class WeatherFeature implements Feature {

    public static final String ID = "weather";
    /** The one instance, in the extras' list. */
    public static final WeatherFeature FEATURE = new WeatherFeature();

    static final int PLAYERS = 2;
    /** What it queues: the fall by distance, the splashes, each player's screen. */
    static final int FALL_ITEMS = 0;
    static final int SPLASH_ITEM = 100;
    static final int LENS_ITEM = 101;

    /** How deep the snow lies when a race starts (of its cap), and the ticks of snowfall that bring it to the cap: four minutes. */
    static final float SNOW_START = 0.3F;
    static final int SNOW_FULL_TICKS = 19 * 240;
    /** How much of a road's and a car's upward faces full snow hides, and how much the wet of rain shows on them. */
    static final float SNOW_ON_ROADS = 0.58F;
    static final float SNOW_ON_CARS = 0.38F;
    static final float WET_ON_ROADS = 0.2F;
    static final float WET_ON_CARS = 0.12F;
    /** How far behind another car its spray still reaches the screen, units. */
    static final float TAIL_REACH = 1200F;

    final Air air = new Air();
    final Fall fall = new Fall();
    final Lens[] lens = {new Lens(), new Lens()};

    /** The weather in the race being played. */
    int kind;
    private int players;
    private int tick;
    float depth;
    private int coveredAt = -1;
    private final float[] wind = new float[2];
    private final float[] camVX = new float[PLAYERS];
    private final float[] camVY = new float[PLAYERS];
    private final float[] camVZ = new float[PLAYERS];
    /** How close behind another car each player is, 0 to 1. */
    final float[] tail = new float[PLAYERS];
    /** Each player's last frame was drawn from the helmet of a closed car. */
    private final boolean[] inside = new boolean[PLAYERS];
    private int skyRgb;
    private float aspect = 16F / 9F;

    private WeatherFeature() {
    }

    /** A weather of its own, for the checks. */
    static WeatherFeature forCheck() {
        return new WeatherFeature();
    }

    @Override
    public String id() {
        return ID;
    }

    @Override
    public void air(int what, int[] c) {
        if (what == StageAir.BEGIN) {
            air.begin(FeatureSettings.get(ID));
            return;
        }
        air.grade(what, c);
    }

    @Override
    public void raceStarted(RaceView race) {
        kind = FeatureSettings.get(ID);
        if (kind == Weather.DRY) {
            Weather.set(Weather.DRY, 0F);
            return;
        }
        players = Math.max(1, Math.min(PLAYERS, Math.min(race.humans, race.cars)));
        tick = race.tick;
        skyRgb = race.skyColor;
        Wind.of(race.seed, wind);
        boolean snow = kind == Weather.SNOW;
        fall.start(race.seed, snow, wind[0], wind[1], race.skyColor, race.fogColor);
        for (int p = 0; p < PLAYERS; p++) {
            lens[p].reset(race.seed, p, snow);
            tail[p] = 0F;
            inside[p] = false;
        }
        WeatherSounds.make();
        coveredAt = -1;
        depth = snowAt(tick);
        Weather.set(kind, depth);
        cover(race);
    }

    /** How deep the snow lies {@code ticks} into a race: from {@link #SNOW_START} to 1 over {@link #SNOW_FULL_TICKS}, then no deeper. */
    static float snowAt(int ticks) {
        return Math.min(1F, SNOW_START + (1F - SNOW_START) * Math.max(0, ticks) / SNOW_FULL_TICKS);
    }

    /** What lies on the ground, the roads and the cars now: told to the game once a second, and when the weather changes. */
    private void cover(RaceView race) {
        GameHooks hooks = Features.hooks();
        if (kind == Weather.SNOW) {
            int second = tick / 19;
            if (second == coveredAt) {
                return;
            }
            coveredAt = second;
            // the stage's ground only if it was graded for snow as it loaded: its file's colour is known then
            int ground = air.kind == Weather.SNOW ? air.groundUnder(depth) : -1;
            if (ground >= 0) {
                hooks.ground(ground, air.patchesUnder(depth));
            }
            float lies = 0.45F + 0.55F * depth;
            hooks.cover(Air.SNOW_WHITE, SNOW_ON_ROADS * lies, SNOW_ON_CARS * lies);
        } else if (coveredAt != -2) {
            coveredAt = -2;
            // the wet: a sheen of the sky on every upward face
            hooks.cover(Air.mix(race.skyColor, 0x000000, 0.2F), WET_ON_ROADS, WET_ON_CARS);
        }
    }

    @Override
    public void afterTick(RaceView race) {
        int now = FeatureSettings.get(ID);
        if (now != kind && now != Weather.DRY) {
            raceStarted(race); // the switch went from one weather to another during the race
        }
        if (kind == Weather.DRY) {
            return;
        }
        tick = race.tick;
        depth = snowAt(tick);
        Weather.set(kind, depth);
        cover(race);
        boolean snow = kind == Weather.SNOW;
        for (int p = 0; p < players && p < race.cars; p++) {
            camVX[p] = race.vx[p];
            camVY[p] = race.vy[p];
            camVZ[p] = race.vz[p];
            tail[p] = tailing(race, p);
            lens[p].tick(speed(race, p), tail[p]);
        }
        WeatherSounds.around(Features.hooks(), snow, speed(race, 0), inside[0]);
    }

    private static float speed(RaceView race, int car) {
        return Math.min(1F, Math.abs(race.speed[car]) / Math.max(1F, race.topSpeed[car]));
    }

    /**
     * How close behind another moving car player {@code p} drives: 0 with
     * none ahead within {@link #TAIL_REACH}, towards 1 right behind one.
     */
    static float tailing(RaceView race, int p) {
        double yaw = Math.toRadians(race.xz[p]);
        float fx = (float) -Math.sin(yaw);
        float fz = (float) Math.cos(yaw);
        float most = 0F;
        for (int c = 0; c < race.cars; c++) {
            if (c == p || !race.grounded[c] || race.wasted[c]) {
                continue;
            }
            float dx = race.x[c] - race.x[p];
            float dz = race.z[c] - race.z[p];
            float ahead = dx * fx + dz * fz;
            float aside = Math.abs(dx * fz - dz * fx);
            if (ahead < 60F || ahead > TAIL_REACH || aside > 0.4F * ahead + 90F) {
                continue;
            }
            float moving = Math.min(1F, race.groundSpeed(c) / 30F);
            most = Math.max(most, (1F - ahead / TAIL_REACH) * moving);
        }
        return most;
    }

    @Override
    public void raceEnded(RaceView race) {
        GameHooks hooks = Features.hooks();
        WeatherSounds.silence(hooks);
        hooks.cover(0, 0F, 0F);
        Weather.set(Weather.DRY, 0F);
        kind = Weather.DRY;
    }

    /* ------------------------------------------------------------ drawing */

    @Override
    public void queue(DrawQueue queue) {
        if (kind == Weather.DRY) {
            return;
        }
        int p = Math.max(0, Math.min(PLAYERS - 1, queue.player()));
        aspect = queue.aspect();
        boolean helmet = HelmetCam.FEATURE.framing() == p;
        inside[p] = helmet && HelmetCam.FEATURE.closed(p);
        fall.queue(queue, tick, camVX[p], camVY[p], camVZ[p], FALL_ITEMS);
        if (kind != Weather.SNOW) {
            queue.ground(SPLASH_ITEM);
        }
        if (lens[p].count > 0) {
            if (helmet) {
                // on the windscreen: over the player's own bonnet, under the car's inside drawn from the helmet
                queue.afterFirst(p, LENS_ITEM + p);
            } else {
                queue.at(queue.eyeX(), queue.eyeY(), queue.eyeZ(), LENS_ITEM + p); // nearest of all: over everything
            }
        }
    }

    @Override
    public void draw(Canvas canvas, int item) {
        if (item >= FALL_ITEMS && item < FALL_ITEMS + Fall.SHELLS) {
            fall.draw(canvas, item - FALL_ITEMS);
        } else if (item == SPLASH_ITEM) {
            fall.splashes(canvas);
        } else if (item >= LENS_ITEM && item < LENS_ITEM + PLAYERS) {
            lens[item - LENS_ITEM].draw(canvas, aspect, Air.mix(skyRgb, 0xffffff, 0.55F),
                    Air.mix(skyRgb, 0xffffff, 0.92F), Air.mix(skyRgb, 0x000000, 0.6F));
        }
    }
}
