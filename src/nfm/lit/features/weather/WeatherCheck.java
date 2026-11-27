package nfm.lit.features.weather;

import static nfm.lit.features.ExtrasCheck.check;
import static nfm.lit.features.ExtrasCheck.n;

import nfm.lit.features.ExtrasCheck;

import java.util.HashSet;
import java.util.Set;

import nfm.lit.features.helmetcam.HelmetCam;
import nfm.lit.features.Wipers;
import nfm.lit.features.CarShape;
import nfm.lit.features.CheckCanvas;
import nfm.lit.features.DrawQueue;
import nfm.lit.features.Explosions;
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
    /** The next races' cars have a windscreen (and so wipers); else they are empty shapes. */
    private boolean glassCars;
    /** The next races' cars have an open back behind the cab and wheels, and this model's name; null for the usual. */
    private String backCars;
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
            c.smokeInRain();
            c.spray();
            c.puddles();
            c.theGrid();
            c.wipers();
            c.wipersFromTheHelmet();
            c.snowTrailsAndHeaps();
            c.snowUnderARoof();
            c.snowOnTheCars();
            c.snowCleared();
            c.gusts();
            c.snowAtSpeed();
            c.storm();
            c.wetRoad();
            c.bedAnywhere();
            c.carShapes();
            c.bedDeepens();
            c.drifts();
            c.snowAboutTheCars();
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
            if (glassCars) {
                v.shape[c] = glassCar();
                v.modelName[c] = "check";
            }
            if (backCars != null) {
                v.shape[c] = backCar();
                v.modelName[c] = backCars;
            }
        }
        Weather.raceStarts(); // as the game does: no crater of another race is left to fill
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

    /**
     * A closed car for the wipers: 40 wide, 100 long, a bonnet ahead of a
     * sloped windscreen that narrows towards the roof, side windows, a roof.
     */
    private static CarShape glassCar() {
        float[][] polys = {
            {-18, -12, 16, 18, -12, 16, 18, -12, 50, -18, -12, 50}, // the bonnet
            {-16, -12, 15, 16, -12, 15, 13, -24, -2, -13, -24, -2}, // the windscreen
            {-13, -24, -2, 13, -24, -2, 13, -24, -30, -13, -24, -30}, // the roof
            {-19, -22, -4, -19, -22, -28, -19, -13, -28, -19, -13, -4}, // the side windows
            {19, -22, -4, 19, -22, -28, 19, -13, -28, 19, -13, -4},
            {-20, -12, -50, -20, -12, 50, -20, -2, 50, -20, -2, -50}, // the sides
            {20, -12, -50, 20, -12, 50, 20, -2, 50, 20, -2, -50},
        };
        boolean[] glass = {false, true, false, true, true, false, false};
        CarShape s = new CarShape();
        s.fit(polys.length, polys.length * 4);
        s.minX = s.minY = s.minZ = Float.MAX_VALUE;
        s.maxX = s.maxY = s.maxZ = -Float.MAX_VALUE;
        int at = 0;
        for (int i = 0; i < polys.length; i++) {
            s.first[i] = at;
            s.count[i] = 4;
            s.rgb[i] = glass[i] ? 0x9fb4c8 : 0xc03020;
            s.glass[i] = glass[i];
            s.wheel[i] = -1;
            for (int k = 0; k < 4; k++, at++) {
                s.x[at] = polys[i][k * 3];
                s.y[at] = polys[i][k * 3 + 1];
                s.z[at] = polys[i][k * 3 + 2];
                s.minX = Math.min(s.minX, s.x[at]);
                s.maxX = Math.max(s.maxX, s.x[at]);
                s.minY = Math.min(s.minY, s.y[at]);
                s.maxY = Math.max(s.maxY, s.y[at]);
                s.minZ = Math.min(s.minZ, s.z[at]);
                s.maxZ = Math.max(s.maxZ, s.z[at]);
            }
        }
        return s;
    }

    /** The closed car with a level panel behind its cab (an open back) and four wheels of radius 8. */
    private static CarShape backCar() {
        CarShape g = glassCar();
        CarShape s = new CarShape();
        s.fit(g.planes + 1, (g.planes + 1) * 4);
        System.arraycopy(g.first, 0, s.first, 0, g.planes);
        System.arraycopy(g.count, 0, s.count, 0, g.planes);
        System.arraycopy(g.rgb, 0, s.rgb, 0, g.planes);
        System.arraycopy(g.glass, 0, s.glass, 0, g.planes);
        System.arraycopy(g.x, 0, s.x, 0, g.planes * 4);
        System.arraycopy(g.y, 0, s.y, 0, g.planes * 4);
        System.arraycopy(g.z, 0, s.z, 0, g.planes * 4);
        java.util.Arrays.fill(s.wheel, -1);
        int i = g.planes;
        float[] bed = {-15, -14, -30, 15, -14, -30, 15, -14, -48, -15, -14, -48};
        s.first[i] = i * 4;
        s.count[i] = 4;
        s.rgb[i] = 0x969696;
        for (int k = 0; k < 4; k++) {
            s.x[i * 4 + k] = bed[k * 3];
            s.y[i * 4 + k] = bed[k * 3 + 1];
            s.z[i * 4 + k] = bed[k * 3 + 2];
        }
        s.minX = g.minX;
        s.maxX = g.maxX;
        s.minY = g.minY;
        s.maxY = g.maxY;
        s.minZ = g.minZ;
        s.maxZ = g.maxZ;
        for (int w = 0; w < RaceView.WHEELS; w++) {
            s.hubX[w] = (w & 1) == 0 ? -20F : 20F;
            s.hubY[w] = 0F;
            s.hubZ[w] = w < 2 ? 35F : -35F;
            s.wheelRadius[w] = 8F;
            s.wheelHalfWidth[w] = 2F;
        }
        return s;
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
        for (int c = 0; c < v.cars; c++) {
            queue.under(c, drawer);
            queue.after(c, drawer);
        }
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

    /** In the rain every car with a windscreen wipes it, the blades staying on the glass; not in snow, not in the dry. */
    private void wipers() {
        glassCars = true;
        start(Weather.RAIN);
        int fitted = weather.wiping.fitted;
        Wipers w = weather.wiping.of(0);
        float least = 1F;
        float most = 0F;
        boolean onGlass = w.has;
        float[] uv = new float[2];
        for (int t = 0; t < Wipers.CYCLE; t++) {
            ticks(1);
            float sweep = weather.wiping.sweep(0);
            least = Math.min(least, sweep);
            most = Math.max(most, sweep);
            for (int k = 0; k < 2 && w.has; k++) {
                w.end(k, sweep, uv);
                onGlass &= w.covers(uv[0], uv[1]);
            }
        }
        // from ahead of the first car, looking back at its windscreen; the other car's faces away
        frame(0F, 150F, 400F, 180F, 0F);
        int rain = canvas.onCar;
        float apart = Math.abs(weather.wiping.sweep(0) - weather.wiping.sweep(1));
        start(Weather.SNOW);
        ticks(3);
        frame(0F, 150F, 400F, 180F, 0F);
        int snow = canvas.onCar;
        start(Weather.DRY);
        ticks(3);
        frame(0F, 150F, 400F, 180F, 0F);
        int dry = canvas.onCar;
        glassCars = false;
        start(Weather.RAIN);
        int open = weather.wiping.fitted;
        check(F, "the wipers work in the rain", fitted == 2 && rain == 4 && least < 0.05F && most > 0.95F && onGlass
                && apart > 0.05F && snow == 0 && dry == 0 && open == 0,
                fitted + " of 2 closed cars have wipers, " + rain + " polygons on the car seen from ahead (two blades,"
                        + " two arms); through a wipe the blades go from " + n(least) + " to " + n(most)
                        + " of the way up, their ends " + (onGlass ? "on" : "off") + " the glass; the two cars "
                        + n(apart) + " apart; " + snow + " polygons in snow, " + dry + " in the dry; " + open
                        + " cars without glass have wipers");
    }

    /** From the helmet of a closed car the player's wipers sweep the windscreen beyond the dashboard and wipe the drops off the screen. */
    private void wipersFromTheHelmet() {
        glassCars = true;
        // from the chase view first: the same rain on the screen, nothing wiping it
        start(Weather.RAIN);
        int outside = 0;
        for (int t = 0; t < 120; t++) {
            ticks(1);
            frame(0F, 100F, -400F, 0F, 0F);
            outside += weather.lens[0].count;
        }
        start(Weather.RAIN);
        HelmetCam helmet = HelmetCam.FEATURE;
        helmet.raceStarted(v);
        helmet.drawingFor(0);
        float[] screen = new float[5];
        boolean closed = helmet.windscreen(0, screen);
        int inside = 0;
        int polygons = 0;
        float nearest = Float.MAX_VALUE;
        float highest = Float.MAX_VALUE;
        for (int t = 0; t < 120; t++) {
            ticks(1);
            canvas.keeping = true;
            frame(v.x[0], v.y[0] - 20F, v.z[0], 0F, 0F);
            canvas.keeping = false;
            inside += weather.lens[0].count;
            polygons = Math.max(polygons, canvas.onCar);
            for (int j = 0; j < canvas.kept.size(); j++) {
                float[] c = canvas.kept.get(j);
                if (canvas.keptColor.get(j) != Wipers.RGB) {
                    continue; // not a blade
                }
                for (int i = 0; i + 2 < c.length; i += 3) {
                    nearest = Math.min(nearest, c[i + 2] - screen[2]);
                    highest = Math.min(highest, (c[i + 1] - screen[1]) / Math.max(1F, c[i + 2] - screen[2]));
                }
            }
        }
        int wiped = weather.wiping.wiped;
        int heard = weather.wiping.heard;
        helmet.drawingFor(-1);
        helmet.raceEnded(v);
        glassCars = false;
        check(F, "from the helmet the wipers clear the screen", closed && polygons == 4 && nearest >= 86F
                && highest >= HelmetCam.GLASS_TOP && wiped >= 3 && inside < outside * 0.95F && heard >= 4,
                polygons + " polygons under the car's inside, the nearest " + n(nearest) + " ahead of the eyes (the"
                        + " inside's parts are at least 86), reaching up to slope " + n(highest) + " (the roof's edge "
                        + n(HelmetCam.GLASS_TOP) + "); in 120 ticks " + wiped + " drops wiped off, " + inside
                        + " drop-ticks on the screen against " + outside + " from outside; " + heard
                        + " wipes heard");
    }

    /** The wheels leave trails in the snow that the snowfall fills in; a drift piles snow at the outside of the slide, on the ground only. */
    private void snowTrailsAndHeaps() {
        start(Weather.SNOW);
        Snow snow = weather.snow;
        for (int t = 0; t < 30; t++) {
            drive(40F, 0F, true);
        }
        int straightRuts = snow.ruts;
        int straightHeaps = snow.heaps;
        frame(0F, 100F, v.z[0] - 500F, 0F, 0F);
        int seen = snow.drawnRuts;
        // a slide to the right: the path from here on, and each heap's distance to its right
        float x0 = v.x[0];
        float z0 = v.z[0];
        for (int t = 0; t < 60; t++) {
            v.vx[0] = 14F;
            v.x[0] += 14F;
            drive(40F, 12F, true);
        }
        int heaps = snow.heaps;
        float nearest = Float.MAX_VALUE;
        float highest = 0F;
        float[] h = new float[5];
        float norm = (float) Math.sqrt(14F * 14F + 40F * 40F);
        for (int n = 0; snow.heapAt(n, h); n++) {
            nearest = Math.min(nearest, ((h[0] - x0) * 40F - (h[2] - z0) * 14F) / norm);
            highest = Math.max(highest, h[4]);
        }
        frame(v.x[0] - 400F, 60F, v.z[0] - 900F, 0F, 0F);
        int drawnHeaps = snow.drawnHeaps;
        int sides = snow.drawnSides;
        v.vx[0] = 0F;
        ticks(19 * 10);
        frame(0F, 100F, z0 - 500F, 0F, 0F);
        int later = snow.drawnRuts;
        ticks(Snow.RUT_FILLS);
        frame(0F, 100F, z0 - 500F, 0F, 0F);
        int filled = snow.drawnRuts;
        // the same slide off the ground, and in the rain
        start(Weather.SNOW);
        for (int t = 0; t < 60; t++) {
            v.vx[0] = 14F;
            v.x[0] += 14F;
            drive(40F, 12F, false);
        }
        int airHeaps = weather.snow.heaps;
        int airRuts = weather.snow.ruts;
        start(Weather.RAIN);
        for (int t = 0; t < 60; t++) {
            v.vx[0] = 14F;
            v.x[0] += 14F;
            drive(40F, 12F, true);
        }
        int rainHeaps = weather.snow.heaps + weather.snow.ruts;
        v.vx[0] = 0F;
        check(F, "wheels leave trails in the snow that stay", straightRuts >= 20 && seen >= 20 && straightHeaps == 0
                && later >= 20 && filled == 0 && airRuts == 0 && rainHeaps == 0,
                straightRuts + " pieces of trail after 30 ticks' driving, " + seen + " drawn, no heaps ("
                        + straightHeaps + "); " + later + " still drawn ten seconds later, " + filled
                        + " once the snowfall has filled them in (" + Snow.RUT_FILLS / 19 + " s); " + airRuts
                        + " from a car in the air, " + rainHeaps + " trails and heaps in the rain");
        check(F, "a drift piles snow at the side of its path", heaps >= 3 && nearest >= 60F && highest >= 8F
                && drawnHeaps >= 3 && sides >= drawnHeaps * 3 && airHeaps == 0,
                heaps + " heaps after a slide of 60 ticks, the nearest " + n(nearest) + " to the outside of the path"
                        + " (the car is 40 to each side), the highest " + n(highest) + " high; " + drawnHeaps
                        + " drawn, as " + sides + " sides and tops turned to the eye (shapes, not marks); " + airHeaps
                        + " from the same slide in the air");
    }

    /** A car on its roof for a tick, sliding {@code speed} along z. */
    private void roofSlide(float speed) {
        v.capsized[0] = true;
        v.xy[0] = 180;
        v.vz[0] = speed;
        v.z[0] += speed;
        v.y[0] = 230F;
        v.grounded[0] = false;
        for (int w = 0; w < RaceView.WHEELS; w++) {
            v.contact[w] = false;
        }
        ticks(1);
    }

    /** A car sliding on its roof pushes snow along and leaves a heap where it stops. */
    private void snowUnderARoof() {
        start(Weather.SNOW);
        Snow snow = weather.snow;
        for (int t = 0; t < 40; t++) {
            roofSlide(30F);
        }
        float pushed = snow.pushed[0];
        int during = snow.heaps;
        frame(-300F, 100F, v.z[0] - 300F, 45F, 0F);
        int moving = snow.drawnPushed;
        roofSlide(0F);
        int left = snow.heaps;
        float[] h = new float[5];
        boolean there = snow.heapAt(0, h) && Math.abs(h[0] - v.x[0]) < 60F && Math.abs(h[2] - v.z[0]) < 90F;
        frame(-300F, 100F, v.z[0] - 300F, 45F, 0F);
        int still = snow.drawnHeaps;
        v.capsized[0] = false;
        v.xy[0] = 0;
        // on its wheels the same way: nothing pushed
        start(Weather.SNOW);
        for (int t = 0; t < 40; t++) {
            drive(30F, 0F, true);
        }
        float upright = weather.snow.pushed[0];
        check(F, "a car on its roof pushes snow along and leaves a heap", pushed > 0.5F && during == 0 && moving >= 4
                && left == 1 && there && h[3] >= 40F && still == 1 && snow.heard >= 1 && upright == 0F,
                "after 40 ticks sliding on its roof it has pushed up " + n(pushed) + " of a full heap, drawn moving"
                        + " with it (" + moving + " polygons); where it stops " + left + " heap is left, "
                        + n(h[3]) + " wide and " + n(h[4]) + " high, " + (there ? "at" : "away from")
                        + " the car, and is heard; on its wheels it pushes " + n(upright));
    }

    /** Snow packs into the wheel arches and into an open back; a ram shakes it off, a hard landing knocks it out. */
    private void snowOnTheCars() {
        backCars = "drmonster";
        start(Weather.SNOW);
        Snow snow = weather.snow;
        float atStart = snow.trunk[0];
        for (int t = 0; t < 19 * 35; t++) {
            drive(40F, 0F, true);
        }
        float arch = snow.arch[0];
        float trunk = snow.trunk[0];
        // from behind the car, to its left
        frame(v.x[0] - 300F, 100F, v.z[0] - 300F, 45F, 0F);
        int onCar = canvas.onCar;
        int flewBefore = snow.thrown;
        v.rams = 1;
        v.ramCar[0] = 1;
        v.ramOther[0] = 0;
        v.ramPush[0] = 8F;
        ticks(1);
        v.rams = 0;
        float archRammed = snow.arch[0];
        float trunkRammed = snow.trunk[0];
        int flew = snow.thrown - flewBefore;
        for (int t = 0; t < 20; t++) {
            v.airTicks[0] = t + 1;
            drive(40F, 0F, false);
        }
        int heapsBefore = snow.heaps;
        v.airTicks[0] = 0;
        drive(40F, 0F, true);
        float trunkLanded = snow.trunk[0];
        float archLanded = snow.arch[0];
        int lumps = snow.lumps;
        int heapsAfter = snow.heaps;
        int heard = snow.heard;
        // any other car has no back that fills
        backCars = "check";
        start(Weather.SNOW);
        float other = weather.snow.trunk[0];
        backCars = null;
        check(F, "snow sits in the wheel arches", arch > 0.95F && onCar >= 2,
                "after 35 s of driving the arches are " + n(arch) + " full; " + onCar
                        + " polygons of snow on the car seen from behind its left (two arches and the back's heap)");
        check(F, "a hard landing knocks the snow out of an open back, a ram shakes it off", atStart > 0.3F
                && trunk > 0.95F && onCar >= 5 && archRammed < arch * 0.5F && trunkRammed < trunk * 0.7F && flew >= 3
                && trunkLanded < 0.05F && archLanded < archRammed && lumps >= 2 && heapsAfter > heapsBefore
                && heard >= 1 && other < 0F,
                "DR Monstaa's back " + n(atStart) + " full at the start, " + n(trunk) + " after 35 s; a ram leaves "
                        + n(trunkRammed) + " in it and " + n(archRammed) + " in the arches, " + flew
                        + " puffs fly off; a landing after 20 ticks in the air leaves " + n(trunkLanded) + ", "
                        + lumps + " lumps on the ground (" + heapsBefore + " heaps before, " + heapsAfter
                        + " after), heard; a car of another model " + (other < 0F ? "has no such back" : "fills too"));
    }

    /** An explosion bares the ground around it, and the snowfall covers it again; the snow melts back around a burning car. */
    private void snowCleared() {
        start(Weather.SNOW);
        Snow snow = weather.snow;
        for (int t = 0; t < 40; t++) {
            v.vx[0] = 14F;
            v.x[0] += 14F;
            drive(40F, 12F, true);
        }
        v.vx[0] = 0F;
        int heapsBefore = snow.heaps;
        int rutsBefore = snow.ruts;
        float bx = v.x[0] - 100F;
        float bz = v.z[0] - 300F;
        int flewBefore = snow.thrown;
        Weather.blast(bx, 250F, bz, 1F, true, v.tick);
        ticks(1);
        int heapsAfter = snow.heaps;
        int rutsAfter = snow.ruts;
        int flew = snow.thrown - flewBefore;
        snow.heap(bx + 50F, 250F, bz, 20F, 7F);
        int refused = snow.heaps;
        frame(bx, 60F, bz - 900F, 0F, 0F);
        int bare = snow.drawnBare;
        float wide = canvas.span(Snow.BARE);
        ticks(DrySpots.SNOWED_IN + 2);
        frame(bx, 60F, bz - 900F, 0F, 0F);
        int covered = snow.drawnBare;
        // the other car burns
        v.wasted[1] = true;
        ticks(19);
        frame(0F, 60F, v.z[1] - 900F, 0F, 0F);
        float early = snow.drawnBare == 1 ? canvas.span(Snow.BARE) : 0F;
        ticks(Snow.MELTS_IN);
        frame(0F, 60F, v.z[1] - 900F, 0F, 0F);
        float melted = snow.drawnBare == 1 ? canvas.span(Snow.BARE) : 0F;
        ticks(DrySpots.SNOWED_IN);
        frame(0F, 60F, v.z[1] - 900F, 0F, 0F);
        int kept = snow.drawnBare;
        v.wasted[1] = false;
        check(F, "an explosion bares the ground and a burning car melts the snow", heapsBefore >= 2
                && heapsAfter < heapsBefore && rutsAfter < rutsBefore && refused == heapsAfter && flew >= 10
                && bare == 1 && wide > Snow.BLAST_CLEARS * 1.5F && covered == 0 && early > 0F && melted > early * 3F
                && melted > Snow.FIRE_MELTS * 1.5F && kept == 1,
                "a blast on the trail: " + heapsBefore + " heaps and " + rutsBefore + " trail pieces before, "
                        + heapsAfter + " and " + rutsAfter + " after, " + flew + " puffs thrown out, bare ground "
                        + n(wide) + " across, no snow heaped on it; covered again after "
                        + DrySpots.SNOWED_IN / 19 + " s (" + covered + " drawn); around a burning car bare "
                        + n(early) + " across after a second, " + n(melted) + " after " + Snow.MELTS_IN / 19
                        + " s, and still there " + DrySpots.SNOWED_IN / 19 + " s later (" + kept + ")");
    }

    /** Nothing falls so near the eye that the game would leave it out, whichever way the camera looks and moves. */
    private void neverAtTheEye() {
        float nearest = Float.MAX_VALUE;
        int frames = 0;
        float[] look = new float[9];
        for (int kind = Weather.RAIN; kind <= Weather.SNOW; kind += 2) {
            start(kind);
            // what falls, not the bed: that lies on the ground, where the game leaves out what reaches the eye
            weather.snow.bed.stop();
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
        // a flake sways up to 9 units each way along x and z about its line: 12.7 at most towards the eye
        check(F, "nothing falls at the eye", nearest >= Fall.NEAR - 13F,
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
                        && asked.coverRoads <= 0.95F,
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
        WeatherSounds.around(asked, false, 0F, true, 0F, 0F);
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

    /** The same explosion in the dry and in the rain: its smoke is gone sooner and hangs lower in the rain. */
    private void smokeInRain() {
        float[] dryAt = smokeAfter(Weather.DRY, 100);
        float[] rainAt = smokeAfter(Weather.RAIN, 100);
        start(Weather.DRY);
        check(F, "smoke is gone sooner and hangs lower in the rain",
                dryAt[0] >= 10F && rainAt[0] < dryAt[0] * 0.8F && rainAt[1] < dryAt[1] * 0.7F,
                n(rainAt[0]) + " puffs left 100 ticks after an explosion in the rain, " + n(rainAt[1])
                        + " above the ground on average; in the dry " + n(dryAt[0]) + ", " + n(dryAt[1]) + " above");
    }

    /** The smoke of a dynamite stick's explosion {@code ticks} later in weather {@code kind}: puffs left, and their mean height over the ground. */
    private float[] smokeAfter(int kind, int ticks) {
        start(kind);
        Explosions blast = new Explosions("weather check");
        blast.start(v);
        blast.explode(v, 2000F, 240F, 2000F, 250F, false, 1F, Explosions.BLAST, false);
        for (int t = 0; t < ticks; t++) {
            v.tick++;
            blast.tick(v);
        }
        float[] at = new float[3];
        int puffs = 0;
        float height = 0F;
        for (int i = 0; i < blast.particles.capacity(); i++) {
            if (blast.particles.isPuff(i) && blast.particles.position(i, at)) {
                puffs++;
                height += 250F - at[1];
            }
        }
        return new float[] {puffs, puffs == 0 ? 0F : height / puffs};
    }

    /** Car 0 for a tick: its wheels on the ground or not, moving {@code speed} along z, its rear wheels sliding {@code slide} sideways. */
    private void drive(float speed, float slide, boolean onGround) {
        v.vz[0] = speed;
        v.z[0] += speed;
        v.grounded[0] = onGround;
        v.y[0] = onGround ? 220F : 60F;
        for (int w = 0; w < RaceView.WHEELS; w++) {
            v.contact[w] = onGround;
            v.wheelX[w] = v.x[0] + ((w & 1) == 0 ? -40F : 40F);
            v.wheelY[w] = onGround ? 250F : 90F;
            v.wheelZ[w] = v.z[0] + (w < 2 ? 70F : -70F);
            v.slipSide[w] = w >= 2 ? slide : 0F;
        }
        ticks(1);
    }

    /** A drift on the wet road throws water and a car at speed raises a mist; in the air neither, and none in the dry. */
    private void spray() {
        start(Weather.RAIN);
        int thrown = 0;
        int mist = 0;
        for (int t = 0; t < 20; t++) {
            drive(30F, 12F, true);
            thrown += weather.wet.thrown;
            mist += weather.wet.misted;
        }
        int inSpray = weather.wet.spray.count();
        frame(0F, 100F, v.z[0] - 500F, 0F, 0F);
        int drawn = canvas.polygons;
        // the same slide, off the ground
        start(Weather.RAIN);
        int airThrown = 0;
        int airMist = 0;
        for (int t = 0; t < 20; t++) {
            drive(30F, 12F, false);
            airThrown += weather.wet.thrown;
            airMist += weather.wet.misted;
        }
        int airSpray = weather.wet.spray.count();
        // rolling straight and slowly: no water thrown
        start(Weather.RAIN);
        int slow = 0;
        for (int t = 0; t < 20; t++) {
            drive(6F, 0F, true);
            slow += weather.wet.thrown + weather.wet.misted;
        }
        check(F, "a drift in the rain throws water, a car at speed raises a mist",
                thrown >= 20 && mist >= 10 && inSpray >= 20 && drawn > inSpray && slow == 0,
                thrown + " drops and " + mist + " puffs of mist in 20 ticks of a slide at speed, " + inSpray
                        + " in the air at once; " + slow + " rolling slowly");
        check(F, "no water comes off a car in the air", airThrown == 0 && airMist == 0 && airSpray == 0,
                airThrown + " drops, " + airMist + " puffs from the same slide off the ground");
    }

    /** Puddles lie along the track, a wheel through one splashes, and a crater fills with a small one. */
    private void puddles() {
        start(Weather.RAIN);
        // a track of eight points on the flat ground, 3000 apart
        v.points = 8;
        v.pointX = new float[8];
        v.pointY = new float[8];
        v.pointZ = new float[8];
        for (int p = 0; p < 8; p++) {
            v.pointX[p] = p < 4 ? 0F : 6000F;
            v.pointZ[p] = (p < 4 ? p : 7 - p) * 3000F;
        }
        weather.raceStarted(v);
        int laid = weather.wet.puddles;
        frame(3000F, -2500F, 4500F, 0F, 60F);
        int seen = weather.wet.drawnPuddles;
        // drive through the first puddle
        v.x[0] = weather.wet.puddleX[0];
        v.z[0] = weather.wet.puddleZ[0] - 200F;
        int before = weather.lens[0].count;
        int splashes = 0;
        for (int t = 0; t < 12; t++) {
            drive(30F, 0F, true);
            for (int w = 0; w < RaceView.WHEELS; w++) {
                v.wheelX[w] = v.x[0]; // all four along the middle, through the puddle
            }
            splashes += weather.wet.splashed;
        }
        int onScreen = weather.lens[0].count - before;
        check(F, "puddles lie along the track and splash under a wheel",
                laid >= 12 && seen == laid && splashes >= 2 && onScreen >= 2,
                laid + " puddles along 8 track points, " + seen + " drawn from above; " + splashes
                        + " wheels splashed driving through one, " + onScreen + " more drops on the screen");
        // an explosion's crater, and one that left none
        Weather.raceStarts();
        Weather.blast(500F, 250F, 500F, 1F, true, v.tick);
        Weather.blast(900F, 250F, 500F, 1F, false, v.tick);
        ticks(2);
        frame(500F, -400F, 0F, 0F, 50F);
        int fresh = weather.wet.drawnCraters;
        ticks(Wet.CRATER_FILLS);
        canvas.keeping = true;
        frame(500F, -400F, 0F, 0F, 50F);
        canvas.keeping = false;
        int filled = weather.wet.drawnCraters;
        float deepest = -Float.MAX_VALUE;
        float widest = 0F;
        for (float[] c : canvas.kept) {
            if (c.length != 30) {
                continue;
            }
            for (int i = 0; i + 2 < c.length; i += 3) {
                float dx = c[i] - 500F;
                float dz = c[i + 2] - 500F;
                if (dx * dx + dz * dz < 150F * 150F && c[i + 1] > 250.5F) {
                    deepest = Math.max(deepest, c[i + 1]);
                    widest = Math.max(widest, (float) Math.sqrt(dx * dx + dz * dz));
                }
            }
        }
        Weather.raceStarts();
        check(F, "a small puddle fills the crater an explosion left",
                fresh == 0 && filled == 1 && deepest > 250.5F && deepest < 250F + Explosions.DEPTH
                        && widest < Explosions.MOUTH,
                fresh + " right after the blast, " + filled + " after " + Wet.CRATER_FILLS / 19 + " s of rain: water "
                        + n(deepest - 250F) + " below the ground in a bowl " + n(Explosions.DEPTH) + " deep, "
                        + n(widest) + " from its middle (the bowl's mouth " + n(Explosions.MOUTH) + ")");
    }

    /** A dry spot shows where each car stood when the race started in rain or snow, for a while after it drove off. */
    private void theGrid() {
        String[] names = {"", "rain", "", "snow"};
        for (int kind = Weather.RAIN; kind <= Weather.SNOW; kind += 2) {
            start(kind);
            v.z[1] = 400F; // both on the grid
            weather.raceStarted(v);
            for (int w = 0; w < 2 * RaceView.WHEELS; w++) {
                v.contact[w] = true;
                v.wheelY[w] = 250F;
                v.groundRgb[w] = 0x808080;
            }
            ticks(19);
            frame(0F, -300F, -600F, 0F, 30F);
            int standing = weather.dry.drawn;
            // both drive off
            v.z[0] += 2000F;
            v.z[1] += 2000F;
            ticks(19);
            frame(0F, -300F, -600F, 0F, 30F);
            int justLeft = weather.dry.drawn;
            float showing = weather.dry.shows(0);
            ticks(kind == Weather.SNOW ? DrySpots.SNOWED_IN : DrySpots.WET_IN);
            frame(0F, -300F, -600F, 0F, 30F);
            int later = weather.dry.any() ? weather.dry.drawn : 0;
            check(F, "a dry spot shows where each car stood on the grid in " + names[kind],
                    standing == 2 && justLeft == 2 && showing > 0.5F && showing < 1F && later == 0,
                    standing + " spots under 2 cars, " + justLeft + " a second after they drove off (" + n(showing)
                            + " of each left), " + later + " after "
                            + (kind == Weather.SNOW ? DrySpots.SNOWED_IN : DrySpots.WET_IN) / 19 + " s more");
        }
        // a weather switched on in the middle of a race lays no spots
        start(Weather.RAIN);
        v.tick = 19 * 60;
        weather.raceStarted(v);
        ticks(5);
        frame(0F, -300F, -600F, 0F, 30F);
        check(F, "no dry spots when the rain joins a race under way", weather.dry.drawn == 0,
                weather.dry.drawn + " spots");
    }

    /* ------------------------------------------------ more oomph (2026-11-27) */

    /** The tick of the next minutes at which the race's gusts are calmest, or at their hardest. */
    private int gustTick(boolean hardest) {
        int best = 0;
        float bestAt = hardest ? -1F : 2F;
        for (int t = 0; t < 19 * 600; t++) {
            float g = weather.gusts.at(t);
            if (hardest ? g > bestAt : g < bestAt) {
                bestAt = g;
                best = t;
            }
        }
        return best;
    }

    /** What falls in a frame at tick {@code tick}: drops shown, and their slant. */
    private float[] fallAt(int tick) {
        v.tick = tick - 1;
        ticks(1);
        frame(0F, 100F, -400F, 0F, 0F);
        return new float[] {weather.fall.shown, weather.fall.slant, asked.bedGain[WeatherSounds.RAIN_BED],
            asked.bedGain[WeatherSounds.WIND_BED]};
    }

    /** The wind gusts: a squall brings more rain or snow, slanted further and louder; the formula adds up. */
    private void gusts() {
        start(Weather.RAIN);
        Gusts g = weather.gusts;
        // the closed form added up against the formula itself, tick by tick
        double summed = 0D;
        double worst = 0D;
        float low = 1F;
        float high = 0F;
        for (int t = 0; t < 19 * 300; t++) {
            summed += (g.at(t) + g.at(t + 1)) / 2D;
            worst = Math.max(worst, Math.abs(g.sum(t + 1) - summed));
            low = Math.min(low, g.at(t));
            high = Math.max(high, g.at(t));
        }
        int calmTick = gustTick(false);
        int squallTick = gustTick(true);
        float[] calm = fallAt(calmTick);
        float[] squall = fallAt(squallTick);
        start(Weather.SNOW);
        float[] snowCalm = fallAt(gustTick(false));
        float[] snowSquall = fallAt(gustTick(true));
        check(F, "the wind gusts by a formula of the tick", worst < 0.5D && low >= 0F && low < 0.05F && high > 0.7F
                && high <= 1F,
                "from " + n(low) + " in the calm to " + n(high) + " in a squall over five minutes; its closed sum is"
                        + " within " + n(worst) + " ticks of the formula added up");
        check(F, "a squall brings more rain, slanted further and louder", squall[0] >= calm[0] * 1.2F
                && squall[1] >= calm[1] * 2F && squall[2] > calm[2] + 0.1F && calm[3] == 0F && squall[3] > 0.1F,
                n(squall[0]) + " drops in the picture at a squall's peak, " + n(calm[0]) + " in the calm; slant "
                        + n(squall[1]) + " against " + n(calm[1]) + "; the rain's sound at " + n(squall[2])
                        + " against " + n(calm[2]) + ", the wind heard at " + n(squall[3]) + " against " + n(calm[3]));
        check(F, "a squall drives the snow nearly level", snowSquall[0] >= snowCalm[0] * 1.2F
                && snowSquall[1] >= snowCalm[1] * 3F && snowSquall[1] > 1F && snowSquall[3] > snowCalm[3] + 0.2F,
                n(snowSquall[0]) + " flakes in the picture at the peak, " + n(snowCalm[0]) + " in the calm; they"
                        + " drift " + n(snowSquall[1]) + " for every unit they fall, against " + n(snowCalm[1])
                        + "; the wind at " + n(snowSquall[3]) + " against " + n(snowCalm[3]));
        ExtrasCheck.waiting(F, "the owner signs off the stronger rain and snow", "the owner drives a race in rain and"
                + " one in snow and says whether they have the oomph asked for: squalls, lightning and thunder,"
                + " rooster tails and the wet road's reflections in rain; the bed, drifts, plumes and spindrift in"
                + " snow");
    }

    /** Lightning in the rain: a bolt far off, the picture pales (unless the flash is off), thunder follows; none in snow. */
    private void storm() {
        int flashWas = FeatureSettings.get("flash");
        start(Weather.RAIN);
        Storm storm = weather.storm;
        int strikeAt = -1;
        for (int t = 0; t < 19 * 40 && strikeAt < 0; t++) {
            ticks(1);
            if (storm.struck == 1) {
                strikeAt = v.tick;
            }
        }
        float dx = storm.boltX() - v.x[0];
        float dz = storm.boltZ() - v.z[0];
        float yaw = (float) Math.toDegrees(Math.atan2(dx, dz));
        FeatureSettings.set("flash", 1);
        frame(v.x[0], 100F, v.z[0], yaw, -10F);
        int bolt = storm.drawnBolt;
        int wash = storm.drawnWash;
        float glow = canvas.maxGlow;
        frame(v.x[0], 100F, v.z[0], yaw + 180F, -10F);
        int behind = storm.drawnBolt;
        FeatureSettings.set("flash", 0);
        frame(v.x[0], 100F, v.z[0], yaw, -10F);
        int washOff = storm.drawnWash;
        int boltOff = storm.drawnBolt;
        FeatureSettings.set("flash", flashWas);
        int thunderWith = storm.thundered;
        int rolled = -1;
        for (int t = 0; t < 19 * 4 && rolled < 0; t++) {
            ticks(1);
            if (storm.thundered == 1) {
                rolled = v.tick;
            }
        }
        frame(v.x[0], 100F, v.z[0], yaw, -10F);
        int gone = storm.drawnBolt;
        ticks(19 * 240);
        int struck = storm.struck;
        int thundered = storm.thundered;
        start(Weather.SNOW);
        ticks(19 * 120);
        int inSnow = weather.storm.struck;
        check(F, "lightning strikes far off in the rain", strikeAt > 19 * 5 && bolt >= 8 && glow == 1F && behind == 0
                && storm.boltFar() >= Storm.NEAREST && gone == 0 && struck >= 6 && struck <= 30 && inSnow == 0,
                "the first bolt " + n(strikeAt / 19F) + " s into the race, " + n(storm.boltFar()) + " units off, drawn"
                        + " as " + bolt + " glowing pieces (" + behind + " looking the other way), gone " + gone
                        + " once it is over; " + struck + " in four minutes more, " + inSnow + " in snow");
        check(F, "the picture pales with the lightning unless the flash is off", wash == 1 && washOff == 0
                && boltOff == bolt,
                wash + " wash over the picture with the flash on, " + washOff + " with it off (the bolt still"
                        + " drawn: " + boltOff + " pieces)");
        check(F, "thunder follows the lightning", thunderWith == 0 && rolled > strikeAt + 5 && thundered >= struck - 1,
                "none with the bolt, the first " + (rolled - strikeAt) + " ticks after it (a tick for every "
                        + n(Storm.THUNDER_PACE) + " units); " + thundered + " for " + struck + " bolts");
    }

    /** The wet road mirrors the cars and their lights; fast cars throw rooster tails and bow spray. */
    private void wetRoad() {
        start(Weather.RAIN);
        for (int t = 0; t < 5; t++) {
            drive(8F, 0F, true);
        }
        int slowTails = weather.wet.tails + weather.wet.bows;
        frame(v.x[0], 120F, v.z[0] - 500F, 0F, 10F);
        int mirrored = weather.reflections.drawnCars;
        int tail = weather.reflections.drawnLights;
        boolean coasting = weather.reflections.glowed;
        v.down[0] = true;
        drive(8F, 0F, true);
        frame(v.x[0], 120F, v.z[0] - 500F, 0F, 10F);
        boolean braking = weather.reflections.glowed;
        v.down[0] = false;
        frame(v.x[0], 120F, v.z[0] + 600F, 180F, 10F);
        int head = weather.reflections.drawnLights;
        int tails = 0;
        int bows = 0;
        for (int t = 0; t < 40; t++) {
            drive(60F, 0F, true);
            tails += weather.wet.tails;
            bows += weather.wet.bows;
        }
        drive(60F, 0F, false);
        frame(v.x[0], 120F, v.z[0] - 500F, 0F, 10F);
        int inAir = weather.reflections.drawnCars;
        start(Weather.SNOW);
        drive(8F, 0F, true);
        frame(v.x[0], 120F, v.z[0] - 500F, 0F, 10F);
        int inSnow = weather.reflections.drawnCars;
        check(F, "the wet road mirrors the cars and their lights", mirrored == 1 && tail == 2 && !coasting && braking
                && head == 2 && inAir == 0 && inSnow == 0,
                mirrored + " car mirrored under itself with " + tail + " streaks from its tail lights seen from"
                        + " behind (glowing while it brakes: " + braking + ", coasting: " + coasting + "), " + head
                        + " from its headlights seen from ahead; " + inAir + " under a car in the air, " + inSnow
                        + " in snow");
        check(F, "a fast car throws a rooster tail and bow spray", tails >= 20 && bows >= 8 && slowTails == 0,
                tails + " puffs of rooster tail and " + bows + " of bow spray in 40 ticks at 60 a tick; "
                        + slowTails + " at 8 a tick");
    }

    /** The bed lies everywhere: far from the track a car leaves the same marks, cut in relief. */
    private void bedAnywhere() {
        backCars = "check";
        start(Weather.SNOW);
        backCars = null;
        Snow snow = weather.snow;
        // far from anything the stage has: no track point, no piece
        v.x[0] = 61000F;
        v.z[0] = -47000F;
        for (int t = 0; t < 30; t++) {
            drive(40F, 0F, true);
        }
        int ruts = snow.ruts;
        int channels = snow.channels;
        frame(v.x[0], 100F, v.z[0] - 500F, 0F, 0F);
        int relief = snow.drawnRelief;
        int polygons = canvas.polygons;
        frame(v.x[0], 100F, v.z[0] - 4500F, 0F, 0F);
        int far = snow.drawnRelief;
        int farRuts = snow.drawnRuts;
        check(F, "a car leaves its marks in the bed anywhere on the map", ruts >= 20 && channels >= 5 && relief >= 20
                && polygons >= relief * 3 && far == 0 && farRuts >= 20,
                "61000 across and 47000 back from the start: " + ruts + " pieces of trail after 30 ticks, " + channels
                        + " of them the channel its body cuts (it stands " + n(snow.clearance(0)) + " high in a bed "
                        + n(SnowBed.deep(weather.depth)) + " deep); " + relief + " drawn as cuts with walls near"
                        + " the eye, flat strips from 4500 away (" + farRuts + ")");
    }

    /** A landing presses the car's own outline into the bed, turned as the car is; a car at rest on its roof leaves its shape too. */
    private void carShapes() {
        backCars = "check";
        start(Weather.SNOW);
        backCars = null;
        SnowBed bed = weather.snow.bed;
        v.xz[0] = 30;
        drive(20F, 0F, true);
        int before = bed.imprints;
        v.airTicks[0] = 14;
        drive(20F, 0F, false);
        v.airTicks[0] = 0;
        drive(20F, 0F, true);
        int landed = bed.imprints;
        float[] xs = new float[SnowBed.CORNERS];
        float[] zs = new float[SnowBed.CORNERS];
        float[] cut = new float[1];
        int corners = bed.imprintAt(0, xs, zs, cut);
        // its size along and across the car's heading (30 degrees round)
        double yaw = Math.toRadians(30D);
        float along0 = Float.MAX_VALUE;
        float along1 = -Float.MAX_VALUE;
        float across0 = Float.MAX_VALUE;
        float across1 = -Float.MAX_VALUE;
        for (int k = 0; k < corners; k++) {
            float along = (float) (-(xs[k] - v.x[0]) * Math.sin(yaw) + (zs[k] - v.z[0]) * Math.cos(yaw));
            float across = (float) ((xs[k] - v.x[0]) * Math.cos(yaw) + (zs[k] - v.z[0]) * Math.sin(yaw));
            along0 = Math.min(along0, along);
            along1 = Math.max(along1, along);
            across0 = Math.min(across0, across);
            across1 = Math.max(across1, across);
        }
        frame(v.x[0] - 300F, 80F, v.z[0] - 500F, 30F, 5F);
        int prints = bed.drawnPrints;
        int walls = bed.drawnWalls;
        // on its roof, at rest
        v.xz[0] = 0;
        for (int t = 0; t < 10; t++) {
            roofSlide(30F);
        }
        int sliding = bed.imprints;
        roofSlide(0F);
        roofSlide(0F);
        int rested = bed.imprints;
        v.capsized[0] = false;
        v.xy[0] = 0;
        ticks(SnowBed.IMPRINT_FILLS + 1);
        frame(v.x[0] - 300F, 80F, v.z[0] - 500F, 30F, 5F);
        int filled = bed.drawnPrints;
        check(F, "a landing presses the car's own shape into the bed", before == 0 && landed == 1 && corners >= 4
                && Math.abs(along1 - along0 - 100F) < 8F && Math.abs(across1 - across0 - 40F) < 6F && cut[0] > 5F
                && prints == 1 && walls >= 4 && filled == 0,
                landed + " imprint after a landing (" + before + " before), " + corners + " corners, "
                        + n(along1 - along0) + " along the car and " + n(across1 - across0) + " across it (the car"
                        + " is 100 by 40, turned 30 degrees), " + n(cut[0]) + " deep; drawn as a hollow with "
                        + walls + " walls; " + filled + " left once the snowfall has filled it in ("
                        + SnowBed.IMPRINT_FILLS / 19 + " s)");
        check(F, "a car at rest on its roof leaves its shape", sliding == landed && rested == landed + 1,
                (rested - sliding) + " more imprint where it came to rest, " + (sliding - landed)
                        + " while it still slid");
    }

    /** The bed gets deeper with the snowfall, and so do the marks pressed into it. */
    private void bedDeepens() {
        backCars = "check";
        start(Weather.SNOW);
        backCars = null;
        SnowBed bed = weather.snow.bed;
        float[] xs = new float[SnowBed.CORNERS];
        float[] zs = new float[SnowBed.CORNERS];
        float[] cut = new float[1];
        ticks(1);
        float thin = SnowBed.deep(weather.depth);
        v.airTicks[0] = 30;
        drive(20F, 0F, false);
        v.airTicks[0] = 0;
        drive(20F, 0F, true);
        bed.imprintAt(0, xs, zs, cut);
        float first = cut[0];
        ticks(WeatherFeature.SNOW_FULL_TICKS);
        float deep = SnowBed.deep(weather.depth);
        v.x[0] += 2000F;
        v.airTicks[0] = 30;
        drive(20F, 0F, false);
        v.airTicks[0] = 0;
        drive(20F, 0F, true);
        float last = 0F;
        for (int n = 0; bed.imprintAt(n, xs, zs, cut) > 0; n++) {
            last = Math.max(last, cut[0]);
        }
        ticks(WeatherFeature.SNOW_FULL_TICKS);
        float capped = SnowBed.deep(weather.depth);
        check(F, "the bed gets deeper, and the marks in it with it", thin > 10F && deep > thin * 2F && capped == deep
                && last > first * 2F && SnowBed.driftHigh(1F, deep) > SnowBed.driftHigh(1F, thin) * 1.8F,
                "the bed " + n(thin) + " units deep when a race starts, " + n(deep) + " after four minutes and "
                        + n(capped) + " after eight; the same landing presses " + n(first) + " deep at the start and "
                        + n(last) + " at the cap; a drift stands " + n(SnowBed.driftHigh(1F, thin)) + " then "
                        + n(SnowBed.driftHigh(1F, deep)) + " high");
    }

    /** Drifts lie all over the map; a car through one flattens it, and the snowfall builds it up again. */
    private void drifts() {
        start(Weather.SNOW);
        SnowBed bed = weather.snow.bed;
        // a drift far from the track
        float[] d = new float[6];
        int ix = 70;
        int iz = -52;
        while (!SnowBed.drift(ix, iz, d)) {
            ix++;
        }
        frame(d[0], 60F, d[1] - 900F, 0F, 5F);
        int seen = bed.drawnDrifts;
        int faces = bed.drawnFaces;
        frame(0F, 60F, -900F, 0F, 5F);
        int atStart = bed.drawnDrifts;
        // the car through it
        v.x[0] = d[0];
        v.z[0] = d[1] - 400F;
        int lumps = Weather.lumps();
        for (int t = 0; t < 12; t++) {
            drive(40F, 0F, true);
        }
        int flattened = bed.flattened;
        int thrown = Weather.lumps() - lumps;
        v.x[0] += 5000F;
        ticks(1);
        frame(d[0], 60F, d[1] - 900F, 0F, 5F);
        int after = bed.drawnDrifts;
        ticks(SnowBed.REGROWS);
        frame(d[0], 60F, d[1] - 900F, 0F, 5F);
        int regrown = bed.drawnDrifts;
        check(F, "drifts lie all over the map and a car flattens them", seen >= 3 && faces >= seen * 3 && atStart >= 3
                && flattened == 1 && after == seen - 1 && regrown == seen && thrown >= 2,
                seen + " drifts in the picture " + n(Math.hypot(d[0], d[1])) + " from the start (" + faces
                        + " faces turned to the eye), " + atStart + " at the start; a car through one flattens it ("
                        + flattened + ", " + after + " left in the picture, " + thrown + " lumps thrown); "
                        + regrown + " again after " + SnowBed.REGROWS / 19 + " s of snowfall");
    }

    /**
     * Driving does not turn the snowfall into streaks ("Snow stays light
     * while driving"): a flake is as short from a car at 110 a tick as from
     * one standing, and fewer are shown.
     */
    private void snowAtSpeed() {
        start(Weather.SNOW);
        ticks(3);
        frame(0F, 100F, -400F, 0F, 0F);
        float stillLong = weather.fall.longest;
        int stillFalling = weather.fall.fallingNow;
        int stillShown = weather.fall.shown;
        for (int t = 0; t < 10; t++) {
            drive(110F, 0F, true);
        }
        frame(v.x[0], 100F, v.z[0] - 400F, 0F, 0F);
        float fastLong = weather.fall.longest;
        int fastFalling = weather.fall.fallingNow;
        check(F, "snow stays light while driving", stillShown > 50 && fastLong <= stillLong * 1.1F + 0.5F
                && fastFalling <= stillFalling * 0.6F,
                "the longest flake (a far one, as wide as it is long) from a standing car " + n(stillLong)
                        + " units, from one at 110 a tick " + n(fastLong) + "; of the pattern " + stillFalling
                        + " flakes fall around a standing car, " + fastFalling + " around the fast one");
    }

    /** The cars stand in the bed: snow about each wheel on the ground, a wave at the nose of one that ploughs, powder behind a fast one, spindrift over it all. */
    private void snowAboutTheCars() {
        backCars = "check";
        start(Weather.SNOW);
        backCars = null;
        Snow snow = weather.snow;
        SnowBed bed = snow.bed;
        for (int t = 0; t < 5; t++) {
            drive(10F, 0F, true);
        }
        int slowPlumes = snow.plumes;
        frame(v.x[0] - 200F, 120F, v.z[0] - 500F, 20F, 10F);
        int wheels = bed.drawnWheels;
        int noses = bed.drawnNoses;
        int streamers = 0;
        for (int t = 0; t < 40; t++) {
            drive(60F, 0F, true);
            frame(v.x[0] - 200F, 120F, v.z[0] - 500F, 20F, 10F);
            streamers = Math.max(streamers, bed.drawnStreamers);
        }
        int cruising = snow.plumes;
        for (int t = 0; t < 40; t++) {
            drive(120F, 0F, true);
        }
        int plumes = snow.plumes - cruising;
        float ploughs = bed.plough[0];
        drive(60F, 0F, false);
        frame(v.x[0] - 200F, 120F, v.z[0] - 500F, 20F, 10F);
        int inAir = bed.drawnWheels + bed.drawnNoses;
        check(F, "the cars stand in the bed and plough it", wheels == 4 && noses == 1 && ploughs > 1F && inAir == 0,
                "snow banked about " + wheels + " wheels on the ground and " + noses + " wave at the nose of a car"
                        + " cutting " + n(ploughs) + " deep; " + inAir + " about a car in the air");
        check(F, "powder behind a fast car, spindrift over the bed", plumes >= 20 && slowPlumes == 0
                && cruising < plumes && streamers >= 3,
                plumes + " puffs of powder in 40 ticks at 120 a tick, " + cruising + " at 60, " + slowPlumes
                        + " at 10; up to " + streamers
                        + " streamers of spindrift in the picture");
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
