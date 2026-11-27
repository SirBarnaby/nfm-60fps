package nfm.lit.features.customgame;

import nfm.lit.features.Feature;
import nfm.lit.features.FeatureSettings;

/**
 * The custom game's place in the extras: its switch, which puts Custom Game
 * on the main menu. Off by default until 1.0, as every extra that changes
 * the races is.
 * <p>
 * The custom game is a way into a race rather than something added to one,
 * so it has nothing to do in the race's own calls: its races are set up
 * before they start ({@link CustomGame}), and their staggered start is part
 * of the race state ({@link StaggeredStart}).
 */
public final class CustomGameFeature implements Feature {

    /** The switch's id, and this folder's name. */
    public static final String ID = "customgame";

    @Override
    public String id() {
        return ID;
    }

    @Override
    public boolean changesRaces() {
        return true;
    }

    @Override
    public boolean playsInRaces() {
        return false; // nothing to do in a race, so a race with only this switch on runs as one with none
    }

    /** True if the main menu shows Custom Game: its switch is on. */
    public static boolean onMenu() {
        return FeatureSettings.on(ID);
    }
}
