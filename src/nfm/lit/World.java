package nfm.lit;

/**
 * The state of a race that belongs to no single car. The cars' physics
 * ({@link Madness}) and their drivers ({@link Control}) draw their dice from
 * here instead of from a global, so the race is one object graph that can be
 * captured, put back or run ahead without touching what the screen shows.
 *
 * <p>
 * One world lives for the whole session: some race values carry over from
 * one race to the next, as in the original, and are recorded at the countdown
 * (see {@link RaceLog}).
 */
public final class World {

    /**
     * The race's own random stream: the AI, crash deformation and the dust
     * that decides skids draw from it. It is seeded once per race (see
     * {@link #seed(long)}), so a race plays again exactly from its seed and
     * inputs. The algorithm is the original {@code Medium.random()}.
     */
    public final RaceRandom random = new RaceRandom();

    /** One value from the race stream. */
    public float random() {
        return random.next();
    }

    /** Starts the race stream for a new race, before the stage draws from it. */
    public void seed(long seed) {
        random.reseed(seed);
    }
}
