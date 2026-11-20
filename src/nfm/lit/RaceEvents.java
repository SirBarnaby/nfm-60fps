package nfm.lit;

/**
 * What the race tells the rest of the game while it runs. The cars' physics
 * calls these instead of the HUD or the sound code, so the race itself never
 * draws or plays anything; {@link GameUi} listens and decides, exactly as
 * the original did, which clip plays and what the checkpoints look like.
 * Calls happen inside the race tick, in the original order.
 */
public interface RaceEvents {

    /**
     * The player's car hit something, or was hit while the camera followed it.
     *
     * @param force how hard
     * @param kind  0 for a hit to the body, -1 or 1 for a roll onto the roof
     *              or the wheels
     */
    void crash(float force, int kind);

    /**
     * The player's car skids hard enough to kick up dust.
     *
     * @param surface 0 on road, otherwise grass or dirt
     * @param speed   how fast the wheel slides
     */
    void skid(int surface, float speed);

    /** The player's damaged car drove through a repair hoop. */
    void carFixed();

    /** The checkpoint the player is heading for, -1 before the race; it is highlighted. */
    void showCheckpoint(int next);

    /** Whether the player is heading for the finish, which flickers. */
    void showLastCheckpoint(boolean last);

    /*
     * The same for a given car: a human player's own (car 0, or car 1 in
     * split screen), or the car a human hit. Car 0's are the calls above.
     */

    /** {@link #crash(float, int)} for {@code car}. */
    default void crash(int car, float force, int kind) {
        crash(force, kind);
    }

    /** {@link #skid(int, float)} for {@code car}. */
    default void skid(int car, int surface, float speed) {
        skid(surface, speed);
    }

    /** {@link #carFixed()} for {@code car}. */
    default void carFixed(int car) {
        carFixed();
    }

    /** {@link #showCheckpoint(int)} for {@code car}. */
    default void showCheckpoint(int car, int next) {
        showCheckpoint(next);
    }

    /** {@link #showLastCheckpoint(boolean)} for {@code car}. */
    default void showLastCheckpoint(int car, boolean last) {
        showLastCheckpoint(last);
    }
}
