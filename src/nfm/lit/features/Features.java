package nfm.lit.features;

import fallk.logmaster.HLogger;

/**
 * The extras the game knows, which are on in the race being played, and the
 * calls the game makes into them. A new feature is one line in {@link #ALL}.
 * <p>
 * A look or sound feature follows its switch at once, even during a race;
 * a race feature keeps what its switch said when the race started. With
 * every feature off each call here is one check of a field. A feature that
 * throws is switched off for the rest of the race and logged, so a bug in an
 * extra never stops a race.
 * <p>
 * Called on the game thread only.
 */
public final class Features {

    /** Every feature, one line each. */
    private static final Feature[] ALL = {
        new nfm.lit.features.skidmarks.SkidmarksFeature(),
        new nfm.lit.features.skidmarks.SkidZonesFeature(),
        new nfm.lit.features.wingtrail.WingTrailFeature(),
        nfm.lit.features.realism.RealismFeature.FEATURE,
        nfm.lit.features.helmetcam.HelmetCam.FEATURE,
        nfm.lit.features.enginesound.EngineSound.FEATURE,
        nfm.lit.features.powerups.PowerupsFeature.FEATURE,
        nfm.lit.features.handling.HandlingFeature.FEATURE,
        nfm.lit.features.wrecks.WrecksFeature.FEATURE,
        nfm.lit.features.tailwhip.TailwhipFeature.FEATURE,
        nfm.lit.features.personality.PersonalityFeature.FEATURE,
        // last: what it lays on the ground is drawn over the marks and craters of those before it
        nfm.lit.features.weather.WeatherFeature.FEATURE,
    };

    private static final boolean[] active = new boolean[ALL.length];
    /** Thrown during this race: off until the next. */
    private static final boolean[] broken = new boolean[ALL.length];
    private static int activeCount;
    private static boolean racing;
    private static int seenVersion = -1;
    private static RaceView lastView;
    /**
     * The features on now that recolour and reshape the cars' polygons and
     * draw on them, in {@link #ALL}'s order (the damage, a car's own quirk,
     * then the weather over both), and each one's place in {@link #ALL}.
     */
    private static final CarLook[] looks = new CarLook[ALL.length];
    private static final int[] lookOwners = new int[ALL.length];
    private static int lookCount;
    /** The feature that draws the cars' bodies elsewhere (on their springs), and its place; null and -1 if none. */
    private static CarBody body;
    private static int bodyOwner = -1;
    /** A second feature that moves parts of the cars' bodies, after {@link #body} (a car's own quirk on its springs), and its place. */
    private static CarBody body2;
    private static int body2Owner = -1;
    /** A polygon's corners between two features that both move it. */
    private static int[] chainX = new int[64];
    private static int[] chainY = new int[64];
    private static int[] chainZ = new int[64];

    /*
     * The race extras (features that change races) playing in this race,
     * decided when its stage loads and kept to its end: they are part of
     * the race from then on, so a switch turned meanwhile waits for the next.
     */
    private static final boolean[] racePlays = new boolean[ALL.length];
    private static final boolean[] raceFailed = new boolean[ALL.length];
    private static boolean raceAny;
    /** While a recording plays back, the race extras it was recorded with (ids, comma-separated); null otherwise. */
    private static String recorded;
    private static volatile GameHooks hooks = new GameHooks() {
    };

    private Features() {
    }

    /** True if feature {@code id} exists (its switch is shown). */
    public static boolean registered(String id) {
        for (Feature f : ALL) {
            if (f.id().equals(id)) {
                return true;
            }
        }
        return false;
    }

    /** True if some feature is on in the race being played: the game then fills the draw queue. */
    public static boolean anyActive() {
        return activeCount > 0;
    }

    private static int switchedVersion = -1;
    private static boolean switchedOn;

    /** True if some feature's switch is on (it runs in the next race, or now if it only changes the look), or a race extra plays. */
    public static boolean anySwitchedOn() {
        if (raceAny) {
            return true;
        }
        int v = FeatureSettings.version();
        if (v != switchedVersion) {
            switchedVersion = v;
            switchedOn = false;
            for (Feature f : ALL) {
                switchedOn |= FeatureSettings.on(f.id());
            }
        }
        return switchedOn;
    }

    /** True if feature {@code id} is on in the race being played. */
    public static boolean active(String id) {
        for (int f = 0; f < ALL.length; f++) {
            if (active[f] && ALL[f].id().equals(id)) {
                return true;
            }
        }
        return false;
    }

    /** The game's own things an extra may use ({@link GameHooks}); each does nothing until the game hands them in. */
    public static GameHooks hooks() {
        return hooks;
    }

    /** The game hands in its own things, once. */
    public static void setHooks(GameHooks h) {
        hooks = h;
    }

    /* ------------------------------------------------------- race extras */

    /**
     * While a recording plays back, the race extras it was recorded with
     * ({@link #raceIds}): its races play with those, whatever the switches.
     * Null goes back to the switches.
     */
    public static void playRecorded(String ids) {
        recorded = ids;
    }

    /**
     * A stage has loaded for a race started from {@code seed}: the race
     * extras whose switches are on (or the recording's) are set up and play
     * in it to its end.
     */
    public static void raceStarting(RaceTick race, long seed) {
        raceAny = false;
        for (int f = 0; f < ALL.length; f++) {
            Feature feature = ALL[f];
            raceFailed[f] = false;
            racePlays[f] = feature.changesRaces()
                    && (recorded != null ? listed(recorded, feature.id()) : FeatureSettings.on(feature.id()));
            if (racePlays[f]) {
                raceAny = true;
                try {
                    feature.raceSetUp(race, seed);
                } catch (RuntimeException e) {
                    raceFail(f, e);
                }
            }
        }
    }

    /** True if a race extra plays in this race: its state is part of the race's. */
    public static boolean raceExtras() {
        return raceAny;
    }

    /** True if race extra {@code feature} plays in this race. */
    public static boolean racePlays(Feature feature) {
        if (!raceAny) {
            return false; // none plays (raceStarting sets the two together)
        }
        for (int f = 0; f < ALL.length; f++) {
            if (ALL[f] == feature) {
                return racePlays[f];
            }
        }
        return false;
    }

    /** The race extras playing in this race, ids comma-separated ("" for none), as a recording keeps them. */
    public static String raceIds() {
        StringBuilder b = new StringBuilder();
        for (int f = 0; f < ALL.length; f++) {
            if (racePlays[f]) {
                b.append(b.length() == 0 ? "" : ",").append(ALL[f].id());
            }
        }
        return b.toString();
    }

    /** One tick of the race proper, after every car has driven: inside the race tick, run-ahead's too. */
    public static void raceTick(RaceTick race) {
        if (!raceAny) {
            return;
        }
        for (int f = 0; f < ALL.length; f++) {
            if (racePlays[f]) {
                try {
                    ALL[f].raceTick(race);
                } catch (RuntimeException e) {
                    raceFail(f, e);
                }
            }
        }
    }

    /** Saves, restores or visits every race extra's state, in a fixed order; nothing without one. */
    public static void syncRace(RaceInts state) {
        if (!raceAny) {
            return;
        }
        for (int f = 0; f < ALL.length; f++) {
            if (racePlays[f]) {
                ALL[f].sync(state);
            }
        }
    }

    /** What the race extras add to car {@code car}'s boost: 0 without one. */
    public static float raceBoost(int car) {
        if (!raceAny) {
            return 0F;
        }
        float add = 0F;
        for (int f = 0; f < ALL.length; f++) {
            if (racePlays[f]) {
                add += ALL[f].boost(car);
            }
        }
        return add;
    }

    /** Car {@code car}'s grip as the race extras leave it: {@code grip} itself, untouched, without one. */
    public static float raceGrip(int car, float grip) {
        if (!raceAny) {
            return grip;
        }
        for (int f = 0; f < ALL.length; f++) {
            if (racePlays[f]) {
                grip *= ALL[f].grip(car);
            }
        }
        return grip;
    }

    /** The degrees more the race extras turn car {@code car} this tick ({@link Feature#turn}): 0 without one. */
    public static int raceTurn(int car, int steer, int steered, boolean bothBrakes, float along, float moving) {
        if (!raceAny) {
            return 0;
        }
        int add = 0;
        for (int f = 0; f < ALL.length; f++) {
            if (racePlays[f]) {
                add += ALL[f].turn(car, steer, steered, bothBrakes, along, moving);
            }
        }
        return add;
    }

    /** Two cars' corners touch this tick ({@link Feature#touch}); nothing without a race extra. */
    public static void raceTouch(int car, int wheel, int other, int otherWheel) {
        if (!raceAny) {
            return;
        }
        for (int f = 0; f < ALL.length; f++) {
            if (racePlays[f]) {
                ALL[f].touch(car, wheel, other, otherWheel);
            }
        }
    }

    /*
     * A race extra that throws keeps playing: switching it off would change
     * the race and the shape of its state in the middle of it. It is logged
     * once a race.
     */
    private static void raceFail(int f, RuntimeException e) {
        if (!raceFailed[f]) {
            raceFailed[f] = true;
            HLogger.error("[extras] " + ALL[f].id() + " failed in the race: " + e);
            e.printStackTrace();
        }
    }

    /** True if {@code id} is one of the comma-separated {@code ids}. */
    private static boolean listed(String ids, String id) {
        for (String one : ids.split(",")) {
            if (one.trim().equalsIgnoreCase(id)) {
                return true;
            }
        }
        return false;
    }

    /* -------------------------------------------------------- the stage's air */

    /**
     * While a stage loads: what its file says of its air ({@link StageAir}),
     * {@code what} with the values {@code c}, as the extras whose switches
     * are on change it (the weather greys the sky, brings the fog nearer).
     */
    public static void air(int what, int[] c) {
        for (Feature f : ALL) {
            if (FeatureSettings.on(f.id())) {
                try {
                    f.air(what, c);
                } catch (RuntimeException e) {
                    HLogger.error("[extras] " + f.id() + " failed on the stage's air: " + e);
                }
            }
        }
    }

    /* ------------------------------------------------------ the race's look */

    /** A race starts: the features whose switches are on start with it (a race extra if it plays in it). */
    public static void raceStarted(RaceView race) {
        if (racing) {
            raceEnded(race);
        }
        racing = true;
        lastView = race;
        Weather.raceStarts();
        java.util.Arrays.fill(broken, false);
        seenVersion = FeatureSettings.version();
        activeCount = 0;
        for (int f = 0; f < ALL.length; f++) {
            active[f] = ALL[f].changesRaces() ? racePlays[f] : FeatureSettings.on(ALL[f].id());
            if (active[f]) {
                activeCount++;
                start(f, race);
            }
        }
        relook();
    }

    /** A real race tick has ended. */
    public static void afterTick(RaceView race) {
        if (!racing) {
            return;
        }
        lastView = race;
        if (seenVersion != FeatureSettings.version()) {
            follow(race);
        }
        if (activeCount == 0) {
            return;
        }
        for (int f = 0; f < ALL.length; f++) {
            if (active[f]) {
                try {
                    ALL[f].afterTick(race);
                } catch (RuntimeException e) {
                    fail(f, e);
                }
            }
        }
    }

    /** A look or sound switch turned during the race: start or stop that feature now. */
    private static void follow(RaceView race) {
        seenVersion = FeatureSettings.version();
        for (int f = 0; f < ALL.length; f++) {
            Feature feature = ALL[f];
            if (feature.changesRaces() || broken[f]) {
                continue;
            }
            boolean on = FeatureSettings.on(feature.id());
            if (on && !active[f]) {
                active[f] = true;
                activeCount++;
                start(f, race);
            } else if (!on && active[f]) {
                stop(f, race);
            }
        }
        relook();
    }

    /** The race has ended or been left. */
    public static void raceEnded(RaceView race) {
        if (!racing) {
            return;
        }
        racing = false;
        for (int f = 0; f < ALL.length; f++) {
            if (active[f]) {
                stop(f, race != null ? race : lastView);
            }
        }
        activeCount = 0;
        relook();
    }

    /** The race in play ended (the game left it), if one was. */
    public static void leftRace() {
        raceEnded(lastView);
    }

    /** Asks every feature that is on what it draws in this frame. */
    public static void queue(DrawQueue queue) {
        for (int f = 0; f < ALL.length; f++) {
            if (active[f]) {
                queue.owner(f);
                try {
                    ALL[f].queue(queue);
                } catch (RuntimeException e) {
                    fail(f, e);
                }
            }
        }
    }

    /** Draws item {@code item} of feature {@code feature}. */
    public static void draw(int feature, int item, Canvas canvas) {
        if (!active[feature]) {
            return;
        }
        try {
            ALL[feature].draw(canvas, item);
        } catch (RuntimeException e) {
            fail(feature, e);
        }
    }

    /** The first feature that recolours the cars' polygons in the race being played, or null: the game asks them for each. */
    public static CarLook carLook() {
        return lookCount > 0 ? looks[0] : null;
    }

    /** {@link CarLook#color} of each feature that recolours the cars, one after the other; a feature that throws is switched off. */
    public static int carColor(int car, int plane, int rgb) {
        lamp = -1;
        for (int k = 0; k < lookCount; k++) {
            CarLook l = looks[k];
            try {
                rgb = l.color(car, plane, rgb);
                if (lamp < 0) {
                    lamp = l.lamp();
                }
            } catch (RuntimeException e) {
                fail(lookOwners[k], e);
                return rgb;
            }
        }
        return rgb;
    }

    /** After {@link #carColor}: the lamp of the first look that lit or darkened one. */
    private static int lamp = -1;

    /** {@link CarLook#lamp} after {@link #carColor}; -1 when no feature recolours the cars. */
    public static int carLamp() {
        return lookCount == 0 ? -1 : lamp;
    }

    /**
     * {@link CarLook#bend} of each feature that reshapes the cars, each
     * given what the one before made of the corners; false when none does
     * (or one threw, and is off).
     */
    public static boolean carBend(int car, int plane, int[] x, int[] y, int[] z, int n, int[] outX, int[] outY,
            int[] outZ) {
        boolean bent = false;
        for (int k = 0; k < lookCount; k++) {
            try {
                if (!bent) {
                    bent = looks[k].bend(car, plane, x, y, z, n, outX, outY, outZ);
                } else {
                    chain(outX, outY, outZ, n);
                    looks[k].bend(car, plane, chainX, chainY, chainZ, n, outX, outY, outZ);
                }
            } catch (RuntimeException e) {
                fail(lookOwners[k], e);
                return bent;
            }
        }
        return bent;
    }

    /** The corners one feature moved, kept for the next to move on from. */
    private static void chain(int[] x, int[] y, int[] z, int n) {
        if (chainX.length < n) {
            chainX = new int[n];
            chainY = new int[n];
            chainZ = new int[n];
        }
        System.arraycopy(x, 0, chainX, 0, n);
        System.arraycopy(y, 0, chainY, 0, n);
        System.arraycopy(z, 0, chainZ, 0, n);
    }

    /** True while a feature changes how the cars' own polygons are drawn ({@link CarLook}). */
    public static boolean carLooks() {
        return lookCount > 0;
    }

    /** {@link CarLook#onPlane} of each; a feature that throws is switched off. */
    public static void carOnPlane(int car, int plane, int rgb, Canvas canvas) {
        for (int k = 0; k < lookCount; k++) {
            try {
                looks[k].onPlane(car, plane, rgb, canvas);
            } catch (RuntimeException e) {
                fail(lookOwners[k], e);
                return;
            }
        }
    }

    /** True while a feature draws the cars' bodies elsewhere ({@link CarBody}): the game asks it for their polygons. */
    public static boolean carBodies() {
        return body != null;
    }

    /** {@link CarBody#body}; false when no feature moves the cars' bodies (or it threw, and is off). */
    public static boolean carBody(int car, int plane, int[] x, int[] y, int[] z, int n, int[] outX, int[] outY,
            int[] outZ) {
        CarBody b = body;
        if (b == null) {
            return false;
        }
        boolean moved;
        try {
            moved = b.body(car, plane, x, y, z, n, outX, outY, outZ);
        } catch (RuntimeException e) {
            fail(bodyOwner, e);
            return false;
        }
        CarBody second = body2;
        if (second != null) {
            try {
                if (!moved) {
                    return second.body(car, plane, x, y, z, n, outX, outY, outZ);
                }
                chain(outX, outY, outZ, n);
                second.body(car, plane, chainX, chainY, chainZ, n, outX, outY, outZ);
            } catch (RuntimeException e) {
                fail(body2Owner, e);
            }
        }
        return moved;
    }

    /** Finds the features on now that recolour the cars and move their bodies, if any. */
    private static void relook() {
        lookCount = 0;
        body = null;
        bodyOwner = -1;
        body2 = null;
        body2Owner = -1;
        for (int f = 0; f < ALL.length; f++) {
            if (active[f] && ALL[f] instanceof CarLook l) {
                looks[lookCount] = l;
                lookOwners[lookCount++] = f;
            }
            if (active[f] && ALL[f] instanceof CarBody b) {
                if (body == null) {
                    body = b;
                    bodyOwner = f;
                } else if (body2 == null) {
                    body2 = b;
                    body2Owner = f;
                }
            }
        }
    }

    private static void start(int f, RaceView race) {
        try {
            ALL[f].raceStarted(race);
        } catch (RuntimeException e) {
            fail(f, e);
        }
    }

    private static void stop(int f, RaceView race) {
        active[f] = false;
        activeCount--;
        try {
            ALL[f].raceEnded(race);
        } catch (RuntimeException e) {
            HLogger.error("[extras] " + ALL[f].id() + " failed ending the race: " + e);
        }
    }

    private static void fail(int f, RuntimeException e) {
        HLogger.error("[extras] " + ALL[f].id() + " failed and is off for this race: " + e);
        e.printStackTrace();
        broken[f] = true;
        if (active[f]) {
            active[f] = false;
            activeCount--;
        }
        relook();
    }
}
