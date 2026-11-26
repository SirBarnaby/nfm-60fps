package nfm.lit.features;

/**
 * The few things of the game's own an extra's look and sound may use, after
 * real ticks only: a player's message line, the white flash, a controller's
 * rumble, the name of a key, and Classic sound. The game hands its own in
 * ({@link Features#setHooks}); until then (and in the checks) each does
 * nothing.
 */
public interface GameHooks {

    /** Classic sound commands. */
    int PLAY = 0;
    int LOOP = 1;
    int STOP = 2;

    /** Shows {@code text} in player {@code player}'s message line, as the game shows "Checkpoint!". */
    default void say(int player, String text) {
    }

    /** The game's white flash, for one drawn frame. */
    default void flash() {
    }

    /** Shakes player {@code player}'s controller as a crash of {@code force} would. */
    default void rumble(int player, float force) {
    }

    /** The name of player {@code player}'s Use powerup key, such as "Ctrl". */
    default String useKey(int player) {
        return "Ctrl";
    }

    /** True if the 3D sound plays the race; false for Classic. */
    default boolean sound3d() {
        return false;
    }

    /** Classic sound: plays, loops or stops clip {@code clip} for player {@code player}, unless their sounds are off. */
    default void classic(int player, String clip, int command) {
    }

    /** Gives Classic sound clip {@code name}: 16-bit mono samples at {@code rate} a second. */
    default void classicClip(String name, short[] pcm, int rate) {
    }

    /**
     * The stage's ground from now on: its colour and its patches' colour
     * (0xRRGGBB as a stage file gives them, before the stage's light; -1
     * leaves the patches as the ground makes them). Snow falling whitens it.
     */
    default void ground(int rgb, int patches) {
    }

    /**
     * What lies on the world's upward faces from now on (snow, the wet of
     * rain): colour {@code rgb} as shown, mixed into the stage's pieces'
     * colours by {@code roads} and the cars' by {@code cars}, 0 (none) to 1.
     */
    default void cover(int rgb, float roads, float cars) {
    }

    /**
     * The 3D sound's bed {@code slot} (0 to 5): clip {@code clip} looping
     * around the listener at {@code gain}, as rain is all around; the gain
     * follows over a third of a second, and 0 or a null clip ends it.
     */
    default void bed(int slot, String clip, float gain, float pitch) {
    }
}
