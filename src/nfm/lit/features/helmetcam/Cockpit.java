package nfm.lit.features.helmetcam;

import nfm.lit.features.CarShape;

/**
 * Where a car's driver has their eyes, and which of the car's own polygons
 * are drawn from there, worked out from its model when a race starts.
 * <p>
 * The eyes: with glass, in the middle of the glasshouse along the car, a
 * third of the way down from the roof line; with none (an open car), a
 * little above the body at its middle; either way over the top of the
 * bonnet. The polygons drawn: those wholly ahead of the eyes and below them
 * that are not glass, the bonnet, the front wings and wheels. The game does
 * not cut a polygon where it passes the eyes, it stretches it across the
 * picture, so the roof, the sides and the back (which reach past the eyes)
 * and the glass (which the eyes look through) are left out, and the car's
 * inside ({@link Interior}) hides what the eyes would see below the
 * bonnet's foot.
 */
final class Cockpit {

    /** How far ahead of the eyes and below them a polygon must lie, units. */
    static final float AHEAD = 4F;
    static final float BELOW = 2F;
    /** Over an open car's body (and its bonnet), how high the eyes are, units; with glass, a third of its height. */
    static final float OVER_OPEN_BODY = 6F;
    /** How far ahead of the eyes the foot of the windscreen is taken to be at least, units. */
    static final float DASH_AHEAD = 66F;

    /** The eyes, in the car's own numbers (x right, y down, z forward). */
    float eyeX;
    float eyeY;
    float eyeZ;
    /** The top of the bonnet (its least y), and how far along the car the foot of the windscreen is: where the dashboard's top is seen. */
    float bonnetTop;
    float dashZ;
    /** The car has glass: a roof and pillars around the eyes; false for an open car. */
    boolean closed;
    /** Per polygon (the game's numbers): drawn from the eyes. */
    boolean[] shown = new boolean[0];
    int polygons;

    /** Works the eyes and the polygons out from {@code s}. */
    void build(CarShape s) {
        polygons = s.planes;
        if (shown.length < polygons) {
            shown = new boolean[polygons];
        }
        eyeX = 0F;
        float glassTop = Float.MAX_VALUE;
        float glassBottom = -Float.MAX_VALUE;
        float glassBack = Float.MAX_VALUE;
        float glassFront = -Float.MAX_VALUE;
        for (int i = 0; i < polygons; i++) {
            if (!s.glass[i]) {
                continue;
            }
            for (int k = s.first[i]; k < s.first[i] + s.count[i]; k++) {
                glassTop = Math.min(glassTop, s.y[k]);
                glassBottom = Math.max(glassBottom, s.y[k]);
                glassBack = Math.min(glassBack, s.z[k]);
                glassFront = Math.max(glassFront, s.z[k]);
            }
        }
        float bonnetFrom;
        float over;
        closed = glassTop < glassBottom && glassBack < glassFront;
        if (closed) {
            over = (glassBottom - glassTop) / 3F;
            eyeY = glassTop + over;
            eyeZ = (glassBack + glassFront) / 2F;
            bonnetFrom = glassFront;
        } else {
            openCar(s);
            over = OVER_OPEN_BODY;
            bonnetFrom = eyeZ + AHEAD;
        }
        // over the bonnet, which the eyes see below them: a wedge's can be higher than a third down its glass
        bonnetTop = top(s, bonnetFrom);
        if (bonnetTop != Float.MAX_VALUE) {
            eyeY = Math.min(eyeY, bonnetTop - over);
        } else {
            bonnetTop = eyeY + over;
        }
        dash(s, bonnetFrom);
        for (int i = 0; i < polygons; i++) {
            boolean ahead = !s.glass[i] && s.count[i] > 0;
            for (int k = s.first[i]; ahead && k < s.first[i] + s.count[i]; k++) {
                ahead = s.z[k] > eyeZ + AHEAD && s.y[k] > eyeY + BELOW;
            }
            shown[i] = ahead;
        }
    }

    /**
     * The highest point of the body from {@code from} forward, of the
     * polygons that reach over its middle half (the bonnet and the scuttle,
     * not the wings or a mirror): the least y; Float.MAX_VALUE if none.
     */
    private static float top(CarShape s, float from) {
        float band = (s.maxX - s.minX) / 4F;
        float top = Float.MAX_VALUE;
        for (int i = 0; i < s.planes; i++) {
            if (s.glass[i] || s.wheel[i] >= 0 || s.count[i] == 0) {
                continue;
            }
            float left = Float.MAX_VALUE;
            float right = -Float.MAX_VALUE;
            float high = Float.MAX_VALUE;
            for (int k = s.first[i]; k < s.first[i] + s.count[i]; k++) {
                left = Math.min(left, s.x[k]);
                right = Math.max(right, s.x[k]);
                if (s.z[k] >= from) {
                    high = Math.min(high, s.y[k]);
                }
            }
            if (left <= band && right >= -band) {
                top = Math.min(top, high);
            }
        }
        return top;
    }

    /**
     * Where the dashboard's top is seen: at the foot of the windscreen (at
     * least {@link #DASH_AHEAD} from the eyes), level with the top of the
     * bonnet. {@link Interior} draws the car's inside from there down.
     */
    private void dash(CarShape s, float from) {
        dashZ = Math.max(from, eyeZ + DASH_AHEAD);
    }

    /** No glass: the eyes a little over the body, at its middle along the car. */
    private void openCar(CarShape s) {
        float middle = (s.minZ + s.maxZ) / 2F;
        float reach = Math.max(1F, (s.maxZ - s.minZ) * 0.15F);
        float top = Float.MAX_VALUE;
        for (int i = 0; i < s.planes; i++) {
            if (s.wheel[i] >= 0) {
                continue;
            }
            for (int k = s.first[i]; k < s.first[i] + s.count[i]; k++) {
                if (Math.abs(s.z[k] - middle) <= reach && Math.abs(s.x[k]) <= (s.maxX - s.minX) / 4F) {
                    top = Math.min(top, s.y[k]);
                }
            }
        }
        if (top == Float.MAX_VALUE) {
            top = s.minY;
        }
        eyeY = top - OVER_OPEN_BODY;
        eyeZ = middle;
    }

    /** True if polygon {@code plane} is drawn from the eyes. */
    boolean shows(int plane) {
        return plane >= 0 && plane < polygons && shown[plane];
    }

    /** How many polygons are drawn from the eyes. */
    int drawn() {
        int n = 0;
        for (int i = 0; i < polygons; i++) {
            n += shown[i] ? 1 : 0;
        }
        return n;
    }

    /** How far ahead of the eyes the nearest corner drawn from them is, in {@code s} (0 if none is drawn). */
    float nearest(CarShape s) {
        float near = Float.MAX_VALUE;
        for (int i = 0; i < polygons; i++) {
            for (int k = s.first[i]; shown[i] && k < s.first[i] + s.count[i]; k++) {
                near = Math.min(near, s.z[k] - eyeZ);
            }
        }
        return near == Float.MAX_VALUE ? 0F : near;
    }
}
