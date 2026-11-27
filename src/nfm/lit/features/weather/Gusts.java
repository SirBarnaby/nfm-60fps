package nfm.lit.features.weather;

import nfm.lit.features.Dice;

/**
 * The weather's gusts (weather, "Rain and snow have more oomph"): the wind
 * rises and falls through a race, and with it how hard the rain or the snow
 * comes down, how far it slants and how loud it is. A squall comes every
 * half minute or so and lasts a few seconds.
 * <p>
 * It is a formula of the tick alone, the race's seed setting its two slow
 * waves, so what falls stays a pattern that follows from the tick
 * ({@link Fall}) and a race replayed gusts the same. {@link #sum} is the
 * formula added up from the race's start: how much farther the gusts have
 * carried a drop than a steady wind would have.
 */
final class Gusts {

    private double w1;
    private double p1;
    private double w2;
    private double p2;
    private double sum0;

    /** The gusts of the race started from {@code seed}. */
    void start(long seed) {
        Dice dice = new Dice(seed, "weather gusts");
        // one wave of 9 to 14 seconds, one of 23 to 37: together a squall now and then
        w1 = 2D * Math.PI / (19D * dice.between(9F, 14F));
        w2 = 2D * Math.PI / (19D * dice.between(23F, 37F));
        p1 = dice.between(0F, 6.2832F);
        p2 = dice.between(0F, 6.2832F);
        sum0 = 0D;
        sum0 = sum(0D);
    }

    /** How hard it gusts {@code tick} ticks into the race: 0 (the calm between) to 1 (a squall's peak), 0.25 on average. */
    float at(double tick) {
        return (float) (0.25D * (1D + Math.sin(w1 * tick + p1)) * (1D + Math.sin(w2 * tick + p2)));
    }

    /** {@link #at} added up from the race's start to {@code tick}. */
    double sum(double tick) {
        double a = w1 * tick + p1;
        double b = w2 * tick + p2;
        // (1 + sin a)(1 + sin b) = 1 + sin a + sin b + (cos(a - b) - cos(a + b)) / 2
        double whole = tick - Math.cos(a) / w1 - Math.cos(b) / w2
                + 0.5D * (Math.sin(a - b) / (w1 - w2) - Math.sin(a + b) / (w1 + w2));
        return 0.25D * whole - sum0;
    }
}
