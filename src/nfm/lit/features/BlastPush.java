package nfm.lit.features;

/**
 * The race half of an explosion, shared by the race extras that make one
 * (dynamite, the bomb, an exploding wreck): every car near it is thrown
 * away and up, its nearer wheels more, so it tips; damaged and shaken, by
 * how near it is. It runs inside the race tick, so it reads and moves the
 * race only through {@link RaceTick} and uses {@link StrictMath}.
 */
public final class BlastPush {

    private BlastPush() {
    }

    /**
     * An explosion at {@code (x, y, z)} reaching {@code radius}: each car in
     * reach that is not wasted is thrown {@code push} away and {@code lift}
     * up a tick (its nearest wheels {@code tip} more) and takes
     * {@code damage} of what wastes it, all by its share of the reach (1 at
     * the middle, 0 at the edge). Car {@code full} (-1: none) gets all of it
     * however near it is: the car that set it off. Car {@code spared}
     * (-1: none) gets nothing. Returns how many cars it reached.
     */
    public static int throwCars(RaceTick race, float x, float y, float z, float radius, float push, float lift,
            float tip, float damage, int full, int spared) {
        int reached = 0;
        int cars = race.cars();
        for (int c = 0; c < cars; c++) {
            if (c == spared || race.wasted(c)) {
                continue;
            }
            float dx = race.x(c) - x;
            float dy = race.y(c) - y;
            float dz = race.z(c) - z;
            float d = (float) StrictMath.sqrt(dx * dx + dy * dy + dz * dz);
            if (c != full && d >= radius) {
                continue;
            }
            float share = c == full ? 1F : 1F - d / radius;
            float nearest = Float.MAX_VALUE;
            float farthest = 0F;
            int nearWheel = 0;
            for (int w = 0; w < 4; w++) {
                float wd = across(race.wheelX(c, w) - x, race.wheelZ(c, w) - z);
                if (wd < nearest) {
                    nearest = wd;
                    nearWheel = w;
                }
                farthest = Math.max(farthest, wd);
            }
            for (int w = 0; w < 4; w++) {
                float wx = race.wheelX(c, w) - x;
                float wz = race.wheelZ(c, w) - z;
                float wd = across(wx, wz);
                float ax = wd > 1F ? wx / wd : 0F;
                float az = wd > 1F ? wz / wd : 0F;
                float near = farthest > nearest ? (farthest - wd) / (farthest - nearest) : 1F;
                race.push(c, w, ax * push * share, -(lift + tip * near) * share, az * push * share);
            }
            race.damage(c, nearWheel, damage * share);
            reached++;
        }
        return reached;
    }

    private static float across(float dx, float dz) {
        return (float) StrictMath.sqrt(dx * dx + dz * dz);
    }
}
