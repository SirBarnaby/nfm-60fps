package nfm.lit.features.wrecks;

import nfm.lit.features.Canvas;
import nfm.lit.features.Dice;
import nfm.lit.features.DrawQueue;
import nfm.lit.features.Explosions;
import nfm.lit.features.Feature;
import nfm.lit.features.FeatureSettings;
import nfm.lit.features.RaceInts;
import nfm.lit.features.RaceTick;
import nfm.lit.features.RaceView;

/**
 * Exploding wrecks: a wasted car burns for two seconds, then explodes. The
 * explosion dents the ground and throws and damages the cars close to the
 * wreck, like a small dynamite, so it changes races: the race half
 * ({@link WreckRace}) plays inside the race tick. This half is the look and
 * the sound, made after real ticks only: sparks spitting from the wreck
 * while it waits, then the shared explosion ({@link Explosions}) with its
 * fireball, crater, flash and smoke. With the switch off a wasted car burns
 * as it always did.
 */
public final class WrecksFeature implements Feature {

    public static final String ID = "wrecks";
    public static final WrecksFeature FEATURE = new WrecksFeature();

    /** A wreck's explosion beside a dynamite stick's: its fireball, smoke, crater and loudness. */
    static final float SIZE = 0.75F;
    /** The powerups' switch of the white flash, which an exploding wreck follows too. */
    private static final String FLASH = "flash";

    final WreckRace race = new WreckRace();
    final Explosions explosions = new Explosions(ID);
    private final Dice dice = new Dice(0L, ID);
    /** The wrecks whose explosion has been shown. */
    private boolean[] shown = new boolean[0];

    private WrecksFeature() {
    }

    @Override
    public String id() {
        return ID;
    }

    @Override
    public boolean changesRaces() {
        return true;
    }

    /* ----------------------------------------------------------- the race */

    @Override
    public void raceSetUp(RaceTick r, long seed) {
        race.setUp(r);
        Explosions.sounds(); // made while the stage loads, not when the first wreck blows
    }

    @Override
    public void raceTick(RaceTick r) {
        race.tick(r);
    }

    @Override
    public void sync(RaceInts state) {
        race.sync(state);
    }

    /* ----------------------------------------------------------- the look */

    @Override
    public void raceStarted(RaceView r) {
        explosions.start(r);
        dice.reset(r.seed, ID);
        shown = new boolean[race.cars];
        for (int c = 0; c < race.cars; c++) {
            shown[c] = race.burned[c] == WreckRace.BLOWN; // switched on in a race: what blew up before is not shown again
        }
    }

    @Override
    public void afterTick(RaceView r) {
        if (shown.length != race.cars) {
            shown = new boolean[race.cars];
        }
        for (int c = 0; c < race.cars && c < r.cars; c++) {
            int burned = race.burned[c];
            if (burned == WreckRace.BLOWN) {
                if (!shown[c]) {
                    shown[c] = true;
                    explosions.explode(r, race.blastX[c], race.blastY[c], race.blastZ[c], race.blastGround[c],
                            race.dented[c], SIZE, Explosions.BLAST, FeatureSettings.on(FLASH));
                }
                continue;
            }
            shown[c] = false;
            if (burned > 0) {
                // about to go: sparks spit from it, more of them as the two seconds run out
                int sparks = 1 + burned * 3 / WreckRace.FUSE;
                for (int k = 0; k < sparks; k++) {
                    explosions.particles.add(r.x[c] + dice.between(-30F, 30F), r.y[c] - 20F,
                            r.z[c] + dice.between(-30F, 30F), dice.between(-9F, 9F), dice.between(-20F, -8F),
                            dice.between(-9F, 9F), dice.between(8F, 14F), 3F, 2F, 0xFFC860, 1F, 0.04F, -1.1F, true);
                }
            }
        }
        explosions.tick(r);
    }

    @Override
    public void queue(DrawQueue queue) {
        explosions.queue(queue, 0);
    }

    @Override
    public void draw(Canvas canvas, int item) {
        explosions.draw(canvas, item);
    }
}
