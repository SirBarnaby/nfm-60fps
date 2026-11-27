package nfm.lit.features.personality;

import nfm.lit.features.CarShape;
import nfm.lit.features.RaceView;
import nfm.lit.features.realism.CarSheet;

/**
 * One car's personality in a race: which it is, where its part is on the
 * model, and how far along it is. Made when a race starts; looks and sound
 * only, never part of the race.
 */
final class Quirk {

    int kind;
    CarShape shape;
    CarSheet sheet = CarSheet.DEFAULT;

    /* what every kind keeps of the tick before */
    int lastXz;
    float lastSpeed;
    float lastVy;
    int lastAir;
    /** For the checks: how often its personality showed (a landing, a tipping, a hard stop's tick), and the puffs it threw. */
    int events;
    int puffs;

    /* the part: MAX Revenge's blower, EL KING's cab, M A S H E E N's plow */
    boolean hasPart;
    boolean[] part = new boolean[0];
    /** The part's extent in the car's own numbers: half its width, its top and bottom (y down), its back and front. */
    float half;
    float partTop;
    float partBottom;
    float partBack;
    float partFront;
    /** What EL KING's cab rocks about: its foot at the nose. */
    float pivotY;
    float pivotZ;

    /* Tornado Shark */
    boolean started;
    boolean away;
    float dust;
    int turns;
    int straight;
    int shedTicks;
    float shedEach;

    /* Lead Oxide */
    float heat;
    int gap;
    int showTicks;
    int shown;

    /* Kool Kat: how far its body is down on its springs now and a tick ago, and how far it may go */
    int window;
    float fell;
    float dip;
    float dipWas;
    float dipSpeed;
    float room = 6F;

    /* Mighty Eight */
    int held;
    int blowTicks;
    boolean burns;
    final float[] flame = new float[2];

    /* MAX Revenge */
    float shake;
    float shakeWas;

    /* M A S H E E N */
    float dirt;
    int dirtRgb = 0x6b5a3c;

    /* DR Monstaa: how flat each tyre is, 0 to 1, and which way is down in the car's own numbers */
    final float[] squash = new float[RaceView.WHEELS];
    float downX;
    float downY = 1F;
    float downZ;

    /* Wow Caninaro: where the loose thing is across the luggage area, -1 to 1 */
    float slid;
    float slideSpeed;
    boolean sliding;

    /* EL KING: how far the cab is rocked forward, degrees */
    float rock;
    float rockWas;
    float rockSpeed;
    int breath;
    boolean wasBrake;

    /**
     * Finds the car's part on its model {@code s}:
     * <ul>
     * <li>MAX Revenge's blower: the narrow box standing on the bonnet
     * between the nose and the windscreen, without the hole it stands in.
     * <li>EL KING's cab: everything above the chassis' deck from the cab's
     * back wall to the nose (the bonnet, the grille and the stacks with it).
     * <li>M A S H E E N's plow: the polygons right at the nose.
     * </ul>
     * and how far Kool Kat's body may come down before it touches the road.
     */
    void find(CarShape s, int wheelDrop) {
        shape = s;
        hasPart = false;
        if (s == null || s.planes == 0) {
            return;
        }
        if (part.length < s.planes) {
            part = new boolean[s.planes];
        }
        java.util.Arrays.fill(part, false);
        float high = s.maxY - s.minY;
        room = Math.max(1.5F, Math.min(7F, (wheelDrop - s.maxY) * 0.7F));
        if (kind == PersonalityFeature.REVENGE) {
            // first what stands in the place, then of that all but its floor
            float top = Float.MAX_VALUE;
            float bottom = -Float.MAX_VALUE;
            for (int pass = 0; pass < 2; pass++) {
                for (int i = 0; i < s.planes; i++) {
                    if (s.wheel[i] >= 0 || s.glass[i] || s.count[i] < 3) {
                        continue;
                    }
                    boolean in = true;
                    float y0 = Float.MAX_VALUE;
                    float y1 = -Float.MAX_VALUE;
                    for (int v = s.first[i]; v < s.first[i] + s.count[i]; v++) {
                        in &= Math.abs(s.x[v]) <= 0.2F * s.maxX && s.z[v] >= 0.25F * s.maxZ && s.z[v] <= 0.62F * s.maxZ;
                        y0 = Math.min(y0, s.y[v]);
                        y1 = Math.max(y1, s.y[v]);
                    }
                    if (!in) {
                        continue;
                    }
                    if (pass == 0) {
                        top = Math.min(top, y0);
                        bottom = Math.max(bottom, y1);
                    } else if (y0 < top + 0.6F * (bottom - top)) {
                        part[i] = true;
                        hasPart = true;
                    }
                }
            }
        } else if (kind == PersonalityFeature.KING) {
            for (int i = 0; i < s.planes; i++) {
                if (s.wheel[i] >= 0 || s.count[i] < 3) {
                    continue;
                }
                float mean = 0F;
                float back = Float.MAX_VALUE;
                for (int v = s.first[i]; v < s.first[i] + s.count[i]; v++) {
                    mean += s.y[v];
                    back = Math.min(back, s.z[v]);
                }
                mean /= s.count[i];
                if (mean < s.minY + 0.74F * high && back >= -0.05F * s.maxZ) {
                    part[i] = true;
                    hasPart = true;
                }
            }
            pivotY = s.minY + 0.8F * high;
            pivotZ = s.maxZ;
        } else if (kind == PersonalityFeature.MASHEEN) {
            for (int i = 0; i < s.planes; i++) {
                if (s.wheel[i] >= 0 || s.count[i] < 3) {
                    continue;
                }
                boolean in = true;
                for (int v = s.first[i]; v < s.first[i] + s.count[i]; v++) {
                    in &= s.z[v] >= 0.78F * s.maxZ;
                }
                if (in) {
                    part[i] = true;
                    hasPart = true;
                }
            }
        }
        if (!hasPart) {
            return;
        }
        half = 0F;
        partTop = Float.MAX_VALUE;
        partBottom = -Float.MAX_VALUE;
        partBack = Float.MAX_VALUE;
        partFront = -Float.MAX_VALUE;
        for (int i = 0; i < s.planes; i++) {
            if (!part[i]) {
                continue;
            }
            for (int v = s.first[i]; v < s.first[i] + s.count[i]; v++) {
                half = Math.max(half, Math.abs(s.x[v]));
                partTop = Math.min(partTop, s.y[v]);
                partBottom = Math.max(partBottom, s.y[v]);
                partBack = Math.min(partBack, s.z[v]);
                partFront = Math.max(partFront, s.z[v]);
            }
        }
    }
}
