package nfm.lit;

import fallk.logmaster.HLogger;

import java.io.IOException;

/**
 * The player's fastest finish on a stage, driven again beside the race as a
 * see-through car: where the player's car was after every tick of that
 * race, from its replay (see {@link Replays}). It is only drawn: it is in
 * none of the race's lists, so nothing collides with it, counts it or reads
 * anything it does, and the race plays out exactly as without it.
 */
final class Ghost {

    /** How much of the ghost covers what is behind it. */
    static final float OPACITY = 0.45F;

    /** The ghost's own car, posed for each frame; never one of the race's objects. */
    final ContO car;
    /** The race time of the run it shows, as m:ss.cc. */
    final String time;
    private final int[] poses;
    private final int count;

    private Ghost(ContO car, int[] poses, int count, String time) {
        this.car = car;
        this.poses = poses;
        this.count = count;
        this.time = time;
    }

    /**
     * The ghost of the fastest finish kept for {@code stage}, in the car it
     * was driven in, or null if there is none or it cannot be read. Called
     * while the stage loads, so reading the replay holds nothing up.
     */
    static Ghost load(int stage, ContO[] carModels, int carCount) {
        Replays.Entry best = Replays.best(stage);
        if (best == null) {
            return null;
        }
        try {
            RaceLog log = RaceLog.read(best.file());
            int carId = log.carsAfter.length > 0 ? log.carsAfter[0] : -1;
            if (log.stage != stage || log.poseCount() == 0 || carId < 0 || carId >= carCount) {
                return null;
            }
            int n = log.poseCount();
            int[] poses = new int[n * RaceLog.POSE];
            for (int t = 0; t < n; t++) {
                for (int k = 0; k < RaceLog.POSE; k++) {
                    poses[t * RaceLog.POSE + k] = log.pose(t, k);
                }
            }
            ContO car = new ContO(carModels[carId], poses[0], poses[1], poses[2], 0);
            return new Ghost(car, poses, n, Replays.seconds(best.finishTick()));
        } catch (IOException | RuntimeException ex) {
            HLogger.warn("Could not read the ghost's replay " + best.file() + ": " + ex);
            return null;
        }
    }

    /** True if the run has a pose for race tick {@code tick} (counted from the countdown, as recorded). */
    boolean has(int tick) {
        return tick >= 0 && tick < count;
    }

    /** Copies the pose after race tick {@code tick} into {@code out}: x, y, z, xz, xy, zy, wxz, wzy. */
    void pose(int tick, int[] out) {
        System.arraycopy(poses, tick * RaceLog.POSE, out, 0, RaceLog.POSE);
    }
}
