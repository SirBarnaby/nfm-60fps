package nfm.lit.features.weather;

import static nfm.lit.features.ExtrasCheck.check;
import static nfm.lit.features.ExtrasCheck.n;

import java.util.HashSet;
import java.util.Set;

import nfm.lit.features.CheckCanvas;
import nfm.lit.features.DrawQueue;
import nfm.lit.features.FeatureSettings;
import nfm.lit.features.Features;
import nfm.lit.features.GameHooks;
import nfm.lit.features.RaceView;
import nfm.lit.features.StageAir;
import nfm.lit.features.Weather;

/**
 * The weather's checks (weather, "Rain and snow", "Weather never changes
 * how the cars drive", "Wipers and droplets in the rain", "Snow gets
 * deeper during the race"): a made-up race in each weather, looked at
 * through a canvas that counts what is drawn, with the game's hooks
 * replaced by ones that note what the weather asks of them.
 * <p>
 * That a recorded race ends the same in every weather is proven by the
 * recorded races themselves, replayed with the weather on
 * ({@code cloud/check.sh looks}, which plays them in snow, and again with
 * {@code -Dnfm.weather=rain}).
 */
public final class WeatherCheck {

    private static final String F = "weather";

    /** What the weather asked of the game. */
    private static final class Asked implements GameHooks {
        final String[] bed = new String[6];
        final float[] bedGain = new float[6];
        int beds;
        int ground = -1;
        int grounds;
        int coverRgb;
        float coverRoads;
        float coverCars;
        boolean threeD = true;

        @Override
        public boolean sound3d() {
            return threeD;
        }

        @Override
        public void bed(int slot, String clip, float gain, float pitch) {
            bed[slot] = gain > 0F ? clip : null;
            bedGain[slot] = clip == null ? 0F : gain;
            beds++;
        }

        @Override
        public void ground(int rgb, int patches) {
            ground = rgb;
            grounds++;
        }

        @Override
        public void cover(int rgb, float roads, float cars) {
            coverRgb = rgb;
            coverRoads = roads;
            coverCars = cars;
        }
    }

    private RaceView v;
    private WeatherFeature weather;
    private Asked asked;
    private final CheckCanvas canvas = new CheckCanvas();

    private WeatherCheck() {
    }

    public static void run() {
        int saved = FeatureSettings.get(WeatherFeature.ID);
        WeatherCheck c = new WeatherCheck();
        try {
            c.eachWeather();
            c.theAir();
            c.throughTheWorld();
            c.neverAtTheEye();
            c.splashes();
            c.dropsOnTheScreen();
            c.snowGetsDeeper();
            c.heard();
            c.onlyALook();
        } finally {
            FeatureSettings.set(WeatherFeature.ID, saved);
            Features.setHooks(new GameHooks() {
            });
        }
    }

    /** A race of two cars on flat ground in weather {@code kind}, loaded as a stage of a blue day would be. */
    private void start(int kind) {
        FeatureSettings.set(WeatherFeature.ID, kind);
        asked = new Asked();
        Features.setHooks(asked);
        weather = WeatherFeature.forCheck();
        load(weather, 0, 0, 0);
        v = new RaceView();
        v.fit(2);
        v.cars = 2;
        v.humans = 1;
        v.seed = 11L;
        v.skyColor = 0x9fb4c8;
        v.fogColor = 0xb0bcc8;
        v.groundColor = 0xd6caae;
        for (int c = 0; c < 2; c++) {
            v.x[c] = 0F;
            v.y[c] = 220F;
            v.z[c] = c * 6000F; // the other car far ahead: clear air
            v.topSpeed[c] = 200F;
            v.grounded[c] = true;
        }
        weather.raceStarted(v);
    }

    /* what the stage's file says of its air, and what the weather made of it */
    private final int[] snap = new int[5];
    private final int[] sky = new int[5];
    private final int[] ground = new int[5];
    private final int[] fog = new int[5];
    private final int[] fade = new int[5];
    private final int[] density = new int[5];

    /** A stage like the game's first loads: its air goes through {@code w} as its file is read. */
    private void load(WeatherFeature w, int snapRed, int snapGreen, int snapBlue) {
        w.air(StageAir.BEGIN, new int[5]);
        set(snap, snapRed, snapGreen, snapBlue);
        w.air(StageAir.SNAP, snap);
        set(sky, 205, 230, 255);
        w.air(StageAir.SKY, sky);
        set(fog, 185, 214, 247);
        w.air(StageAir.FOG, fog);
        set(ground, 214, 202, 174);
        w.air(StageAir.GROUND, ground);
        set(fade, 8000, 0, 0);
        w.air(StageAir.FADE, fade);
        set(density, 8, 0, 0);
        w.air(StageAir.DENSITY, density);
        w.air(StageAir.END, new int[5]);
    }

    private static void set(int[] c, int a, int b, int d) {
        c[0] = a;
        c[1] = b;
        c[2] = d;
    }

    private void ticks(int count) {
        for (int t = 0; t < count; t++) {
            v.tick++;
            weather.afterTick(v);
        }
    }

    /** Everything the weather draws in a frame of player 0's, the eye at {@code (x, y, z)} turned {@code yaw}, tilted {@code pitch}. */
    private int frame(float x, float y, float z, float yaw, float pitch) {
        DrawQueue queue = new DrawQueue();
        queue.begin(x, y, z, v.tick);
        queue.view(0, yaw, pitch, 16F / 9F);
        queue.owner(0);
        weather.queue(queue);
        queue.sort();
        canvas.reset();
        DrawQueue.Drawer drawer = (f, item) -> weather.draw(canvas, item);
        queue.before(Integer.MAX_VALUE, false, drawer);
        queue.end(drawer);
        return canvas.polygons;
    }

    /** Rain falls in rain, with nearer, thicker fog when set; snow in snow; nothing in the dry. */
    private void eachWeather() {
        start(Weather.RAIN);
        ticks(5);
        frame(0F, 100F, -400F, 0F, 0F);
        int rain = weather.fall.shown;
        int rainFade = fade[0];
        int rainDensity = density[0];
        check(F, "rain falls in a race in the rain", rain >= 150 && canvas.moving >= rain,
                rain + " drops in the picture, " + canvas.moving + " moving polygons");
        start(Weather.RAIN_FOG);
        ticks(5);
        frame(0F, 100F, -400F, 0F, 0F);
        check(F, "with fog set the rain's fog starts nearer and is thicker",
                weather.fall.shown >= 150 && fade[0] < rainFade / 2 && density[0] < rainDensity
                        && rainFade < 8000,
                "fog from " + fade[0] + " (rain alone " + rainFade + ", the stage 8000), density " + density[0]
                        + " (rain alone " + rainDensity + ", the stage 8)");
        start(Weather.SNOW);
        ticks(5);
        frame(0F, 100F, -400F, 0F, 0F);
        int snow = weather.fall.shown;
        check(F, "snow falls in a race in the snow", snow >= 150 && Weather.snowing() && !Weather.raining(),
                snow + " flakes in the picture");
        start(Weather.DRY);
        ticks(5);
        int dry = frame(0F, 100F, -400F, 0F, 0F);
        check(F, "nothing falls in the dry", dry == 0 && Weather.kind() == Weather.DRY && asked.beds == 0,
                dry + " polygons, " + asked.beds + " sounds asked for");
    }

    private static float lum(int[] c) {
        return (c[0] * 3 + c[1] * 6 + c[2]) / 10F;
    }

    private static int spread(int[] c) {
        return Math.max(c[0], Math.max(c[1], c[2])) - Math.min(c[0], Math.min(c[1], c[2]));
    }

    /** Rain greys and darkens the stage's sky and light; snow pales it and whitens the ground; a night stays a night. */
    private void theAir() {
        start(Weather.RAIN);
        boolean grey = spread(sky) < 50 / 2 && lum(sky) < 228F * 0.85F && snap[0] < 0 && lum(ground) < 201F;
        float rainSky = lum(sky);
        check(F, "rain: a grey, darker sky, duller light, darker ground", grey,
                "sky " + sky[0] + "," + sky[1] + "," + sky[2] + " (the stage 205,230,255), light " + snap[0] + ","
                        + snap[1] + "," + snap[2] + ", ground " + ground[0] + "," + ground[1] + "," + ground[2]);
        start(Weather.SNOW);
        boolean pale = spread(sky) < 50 / 2 && lum(sky) > rainSky + 30F && lum(ground) > 201F + 15F
                && spread(ground) < 40 - 10;
        check(F, "snow: a pale sky and ground part snow already", pale,
                "sky " + sky[0] + "," + sky[1] + "," + sky[2] + ", ground " + ground[0] + "," + ground[1] + ","
                        + ground[2] + " (the stage 214,202,174)");
        // a night stage: its light stays as dark, less the rain's dulling
        FeatureSettings.set(WeatherFeature.ID, Weather.RAIN);
        WeatherFeature night = WeatherFeature.forCheck();
        load(night, -50, -65, 0);
        check(F, "a night stays a night in the rain", snap[0] <= -50 && snap[1] <= -65 && snap[2] <= 0,
                "light " + snap[0] + "," + snap[1] + "," + snap[2] + " (the stage -50,-65,0)");
    }

    /** The low ends of the drops kept by the canvas, rounded to whole units. */
    private Set<Long> lowEnds() {
        Set<Long> ends = new HashSet<>();
        for (float[] c : canvas.kept) {
            if (c.length != 12) {
                continue;
            }
            long x = Math.round((c[0] + c[3]) / 2F);
            long y = Math.round((c[1] + c[4]) / 2F);
            long z = Math.round((c[2] + c[5]) / 2F);
            ends.add(x * 73856093L ^ y * 19349663L ^ z * 83492791L);
        }
        return ends;
    }

    /** The drops have places in the world: seen from a step aside they are the same drops; a replay rains the same. */
    private void throughTheWorld() {
        start(Weather.RAIN);
        ticks(40);
        canvas.keeping = true;
        frame(0F, 100F, -400F, 0F, 0F);
        Set<Long> here = lowEnds();
        frame(60F, 100F, -380F, 0F, 0F);
        Set<Long> aside = lowEnds();
        int same = 0;
        for (Long end : here) {
            same += aside.contains(end) ? 1 : 0;
        }
        check(F, "the rain is in the world: from a step aside most drops are where they were",
                here.size() >= 150 && same >= here.size() * 0.6F,
                same + " of " + here.size() + " drops seen again from 60 aside");
        // the same race again, and the next tick
        start(Weather.RAIN);
        ticks(40);
        frame(0F, 100F, -400F, 0F, 0F);
        Set<Long> again = lowEnds();
        ticks(1);
        frame(0F, 100F, -400F, 0F, 0F);
        Set<Long> later = lowEnds();
        int kept = 0;
        for (Long end : again) {
            kept += later.contains(end) ? 1 : 0;
        }
        canvas.keeping = false;
        check(F, "a race replayed rains the same, and the rain falls", again.equals(here) && kept < again.size() / 10,
                (again.equals(here) ? "the same drops" : "other drops") + " in the same race again; " + kept + " of "
                        + again.size() + " still where they were a tick later");
    }

    /** Nothing falls so near the eye that the game would leave it out, whichever way the camera looks and moves. */
    private void neverAtTheEye() {
        float nearest = Float.MAX_VALUE;
        int frames = 0;
        float[] look = new float[9];
        for (int kind = Weather.RAIN; kind <= Weather.SNOW; kind += 2) {
            start(kind);
            canvas.keeping = true;
            for (int k = 0; k < 60; k++) {
                // the player's car, which the camera follows, at every speed and heading, climbing and falling
                v.vx[0] = (k % 7 - 3) * 25F;
                v.vz[0] = (k % 5 - 2) * 40F;
                v.vy[0] = (k % 3 - 1) * 30F;
                ticks(1);
                float yaw = k * 37F;
                float pitch = (k % 9 - 4) * 12F;
                float ex = k * 130F;
                float ey = 240F - (k % 6) * 150F;
                float ez = -k * 90F;
                frame(ex, ey, ez, yaw, pitch);
                DrawQueue q = new DrawQueue();
                q.view(0, yaw, pitch, 16F / 9F);
                q.look(look);
                for (float[] c : canvas.kept) {
                    for (int i = 0; i + 2 < c.length; i += 3) {
                        float d = (c[i] - ex) * look[0] + (c[i + 1] - ey) * look[1] + (c[i + 2] - ez) * look[2];
                        nearest = Math.min(nearest, d);
                    }
                }
                frames++;
            }
            canvas.keeping = false;
        }
        check(F, "nothing falls at the eye", nearest >= Fall.NEAR - 12F,
                "the nearest corner " + n(nearest) + " ahead in " + frames + " frames (the game leaves out a polygon"
                        + " nearer than 10)");
    }

    /** Rain splashes on the flat ground; snow does not. */
    private void splashes() {
        start(Weather.RAIN);
        ticks(30);
        canvas.keeping = true;
        frame(0F, 100F, -400F, 0F, 10F);
        int rain = weather.fall.splashed;
        float lowest = -Float.MAX_VALUE;
        for (float[] c : canvas.kept) {
            if (c.length == 18) { // a splash's ring
                for (int i = 1; i < c.length; i += 3) {
                    lowest = Math.max(lowest, c[i]);
                }
            }
        }
        canvas.keeping = false;
        start(Weather.SNOW);
        ticks(30);
        frame(0F, 100F, -400F, 0F, 10F);
        check(F, "rain splashes on the ground", rain >= 15 && Math.abs(lowest - Fall.GROUND) < 2F
                && weather.fall.splashed == 0,
                rain + " splashes in the picture, at height " + n(lowest) + " (the ground 250); "
                        + weather.fall.splashed + " in snow");
    }

    /** Drops sit on the screen in the rain, more of them close behind another car. */
    private void dropsOnTheScreen() {
        start(Weather.RAIN);
        v.speed[0] = 120F;
        ticks(200);
        int clear = weather.lens[0].count;
        frame(0F, 100F, -400F, 0F, 0F);
        int drawn = canvas.onScreen;
        float covered = canvas.screenArea;
        // now right behind the other car, both moving
        v.z[1] = 350F;
        v.vz[1] = 40F;
        ticks(200);
        int behind = weather.lens[0].count;
        float tail = weather.tail[0];
        // and beside it: no spray reaches the screen
        v.x[1] = 900F;
        ticks(1);
        float beside = weather.tail[0];
        check(F, "drops sit on the screen in the rain", clear >= 8 && drawn >= clear && covered > 0.001F
                && covered < 0.12F,
                clear + " drops, " + drawn + " polygons covering " + n(covered * 100F) + "% of the picture");
        check(F, "more drops close behind another car", behind >= clear * 1.5F && tail > 0.5F && beside == 0F,
                behind + " drops behind a car 350 ahead, " + clear + " in clear air; beside it none of its spray");
        start(Weather.DRY);
        ticks(100);
        check(F, "no drops in the dry", weather.lens[0].count == 0, weather.lens[0].count + " drops");
    }

    /** Snow gets deeper as the race goes on, to a cap: on the ground and on the roads. */
    private void snowGetsDeeper() {
        start(Weather.SNOW);
        ticks(1);
        float first = weather.depth;
        int groundFirst = asked.ground;
        float roadsFirst = asked.coverRoads;
        ticks(19 * 120);
        float middle = weather.depth;
        ticks(19 * 120);
        float full = weather.depth;
        int groundFull = asked.ground;
        float roadsFull = asked.coverRoads;
        ticks(19 * 240);
        float after = weather.depth;
        boolean whiter = lumOf(groundFull) > lumOf(groundFirst) + 8F && roadsFull > roadsFirst + 0.1F;
        check(F, "snow gets deeper during the race and stops at its cap",
                first < middle && middle < full && full == 1F && after == 1F && asked.ground == groundFull && whiter
                        && asked.coverRoads < 0.7F,
                "depth " + n(first) + " at the start, " + n(middle) + " after 2 min, " + n(full) + " after 4, "
                        + n(after) + " after 8; the ground " + hex(groundFirst) + " then " + hex(groundFull)
                        + ", snow over " + n(roadsFirst * 100F) + "% then " + n(roadsFull * 100F) + "% of a road");
    }

    private static float lumOf(int rgb) {
        return ((rgb >> 16 & 0xff) * 3 + (rgb >> 8 & 0xff) * 6 + (rgb & 0xff)) / 10F;
    }

    private static String hex(int rgb) {
        return String.format("%06x", rgb);
    }

    /** The rain is heard all around through the 3D sound, the roof from inside, the wind in snow; Classic hears none. */
    private void heard() {
        start(Weather.RAIN);
        ticks(3);
        boolean rain = WeatherSounds.RAIN.equals(asked.bed[WeatherSounds.RAIN_BED])
                && asked.bed[WeatherSounds.ROOF_BED] == null && asked.bed[WeatherSounds.WIND_BED] == null;
        float open = asked.bedGain[WeatherSounds.RAIN_BED];
        WeatherSounds.around(asked, false, 0F, true);
        boolean roof = WeatherSounds.ROOF.equals(asked.bed[WeatherSounds.ROOF_BED])
                && asked.bedGain[WeatherSounds.RAIN_BED] < open;
        weather.raceEnded(v);
        boolean ended = asked.bed[WeatherSounds.RAIN_BED] == null && asked.bed[WeatherSounds.ROOF_BED] == null
                && asked.coverRoads == 0F;
        start(Weather.SNOW);
        ticks(3);
        boolean wind = WeatherSounds.WIND.equals(asked.bed[WeatherSounds.WIND_BED])
                && asked.bed[WeatherSounds.RAIN_BED] == null;
        start(Weather.RAIN);
        asked.threeD = false;
        asked.beds = 0;
        ticks(3);
        check(F, "the weather is heard through the 3D sound only", rain && roof && ended && wind && asked.beds == 0,
                "rain all around " + rain + ", on the roof from inside " + roof + ", the wind in snow " + wind
                        + ", silent after the race " + ended + ", " + asked.beds + " asked of Classic sound");
    }

    /** It is a look: it does not change races, and the race's copy is as it was after the weather read it. */
    private void onlyALook() {
        start(Weather.SNOW);
        v.x[0] = 12F;
        v.speed[0] = 77F;
        v.vz[0] = 33F;
        ticks(50);
        frame(0F, 100F, -400F, 20F, 5F);
        boolean untouched = v.x[0] == 12F && v.speed[0] == 77F && v.vz[0] == 33F && v.y[0] == 220F
                && v.grounded[0];
        check(F, "the weather only reads the race", !weather.changesRaces() && untouched,
                "changes races: " + weather.changesRaces() + "; the race's copy untouched: " + untouched
                        + " (the recorded races replayed in rain and snow prove the rest)");
    }
}
