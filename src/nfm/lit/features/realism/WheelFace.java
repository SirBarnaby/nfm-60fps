package nfm.lit.features.realism;

import nfm.lit.features.Canvas;
import nfm.lit.features.CarShape;

/**
 * The cars' wheels with the brake inside (realism, "The brake disc sits
 * inside the wheel"). The game's wheel has a closed face: six triangles from
 * the rim's corners to the hub. Each is drawn as the game draws it and
 * then, right on it, an opening and the part of the brake disc seen through
 * it, so the face becomes a hub, six spokes and a rim's lip around six
 * openings, and the disc sits behind the spokes.
 * <p>
 * On some cars the game's face is not flat or dished but stands out of the
 * wheel: a pointed cap, and on Sword of Justice a long spike. An opening
 * painted on that would be out in front of the wheel, so such a face is
 * drawn flat, a touch dished ({@link #flat}, through the feature's
 * {@code bend}), and its point is put back on the hub: six triangles from
 * the hub cap's edge to where the game's point was.
 * <p>
 * Every corner drawn is a point of the game's own triangle, so nothing of
 * the opening or the disc is outside the wheel's face from any side, and
 * whatever of the car covers the face covers them too: they are drawn at
 * the triangle's place in the car's back-to-front order
 * ({@link nfm.lit.features.CarLook#onPlane}). A bent wheel's are tilted
 * with it. Nothing is allocated.
 */
final class WheelFace {

    /** A face triangle smaller than this from hub to rim has no room for an opening. */
    static final float SMALLEST = 4F;
    /** From the hub (0) to the rim's corner (1): where the hub cap ends and the rim's lip begins. */
    static final float HUB = 0.32F;
    /** The widest hub cap, under a point that stands far out (a spike's root). */
    static final float HUB_WIDEST = 0.5F;
    /** A face made flat is dished this far in at the hub. */
    static final float DISH = 1F;
    /** The disc starts this far outside the hub cap, of the way to the rim. */
    static final float DISC_GAP = 0.13F;
    static final float LIP = 0.88F;
    /** From one edge of the triangle (0) to the other (1): half a spoke is left at each. */
    static final float SPOKE = 0.18F;
    /** Where the disc seen in the opening ends, of the way to the rim: a dark gap is left inside and outside it. */
    static final float DISC_OUT = 0.76F;
    /** The dark inside of the wheel, and a cold disc's steel. */
    static final int WELL = 0x121214;
    static final int STEEL = 0x74777b;
    /** A drum brake is closed in: it shows as dark iron, and glows less. */
    static final int DRUM_IRON = 0x4a4c50;
    static final float DRUM_GLOW = 0.5F;

    private final float[] x = new float[4];
    private final float[] y = new float[4];
    private final float[] z = new float[4];

    /**
     * On car {@code car}'s face triangle {@code plane} of wheel {@code w},
     * just drawn in {@code shown}: its opening, in it the disc, cold steel
     * or glowing with its heat, and the hub's point if the game's stood
     * out.
     */
    void draw(Canvas canvas, int car, CarState s, int plane, int w, int shown) {
        CarParts parts = s.parts;
        CarShape shape = parts.shape;
        int hub = parts.rimHub[plane];
        int f = shape.first[plane];
        int a = f + hub;
        int b = f + (hub + 1) % 3;
        int c = f + (hub + 2) % 3;
        int steerX = shape.steerX[w];
        int hubY = Math.round(shape.hubY[w]);
        int hubZ = Math.round(shape.hubZ[w]);
        float tilt = s.bent[w];
        float hubX = flat(parts, w, shape.x[a]);
        float in = parts.hubEnd[w];
        canvas.color(RealismFeature.shaded(WELL, shown, shape.rgb[plane]));
        quad(shape, hubX, a, b, c, in, LIP, w, tilt);
        canvas.onWheel(car, x, y, z, 4, steerX, hubY, hubZ);
        float heat = s.brake[w];
        boolean drum = parts.sheet.brakes == CarSheet.DRUM;
        if (heat > HeatEffects.VISIBLE) {
            canvas.color(canvas.fog(HeatEffects.discColor(heat), s.at[0], s.at[1], s.at[2]));
            canvas.glow(HeatEffects.discGlow(heat) * (drum ? DRUM_GLOW : 1F));
        } else {
            canvas.color(RealismFeature.shaded(drum ? DRUM_IRON : STEEL, shown, shape.rgb[plane]));
        }
        quad(shape, hubX, a, b, c, Math.min(in + DISC_GAP, DISC_OUT - 0.1F), DISC_OUT, w, tilt);
        canvas.onWheel(car, x, y, z, 4, steerX, hubY, hubZ);
        canvas.glow(0F);
        if (parts.hubProud[w] > 0F) {
            // the game's point, back on the hub: from the hub cap's edge on this triangle to where it was
            canvas.color(shown);
            point(shape, hubX, a, b, c, in, 0F, 0);
            point(shape, hubX, a, b, c, in, 1F, 1);
            x[2] = shape.x[a];
            y[2] = shape.y[a];
            z[2] = shape.z[a];
            if (tilt > 0F) {
                tilt(x, z, 3, shape.hubX[w], shape.hubZ[w], tilt);
            }
            canvas.onWheel(car, x, y, z, 3, steerX, hubY, hubZ);
        }
    }

    /**
     * Where along x the hub corner of wheel {@code w}'s face triangles is
     * drawn, given the game's {@code hubX}: there, or for a hub that stood
     * out, just inside the face.
     */
    static float flat(CarParts parts, int w, float hubX) {
        return parts.hubProud[w] > 0F ? parts.faceX[w] - parts.faceOut[w] * DISH : hubX;
    }

    /**
     * The part of triangle {@code a, b, c} ({@code a} at the hub, drawn at
     * {@code hubX}) from {@code in} to {@code out} of the way to the rim,
     * half a spoke left at each edge, into the corners; tilted with a wheel
     * bent {@code tilt} degrees.
     */
    private void quad(CarShape shape, float hubX, int a, int b, int c, float in, float out, int w, float tilt) {
        point(shape, hubX, a, b, c, in, SPOKE, 0);
        point(shape, hubX, a, b, c, out, SPOKE, 1);
        point(shape, hubX, a, b, c, out, 1F - SPOKE, 2);
        point(shape, hubX, a, b, c, in, 1F - SPOKE, 3);
        if (tilt > 0F) {
            tilt(x, z, 4, shape.hubX[w], shape.hubZ[w], tilt);
        }
    }

    /** The triangle's point {@code t} of the way from the hub to the rim, {@code u} of the way from edge to edge. */
    private void point(CarShape shape, float hubX, int a, int b, int c, float t, float u, int k) {
        x[k] = at(hubX, shape.x[b], shape.x[c], t, u);
        y[k] = at(shape.y[a], shape.y[b], shape.y[c], t, u);
        z[k] = at(shape.z[a], shape.z[b], shape.z[c], t, u);
    }

    private static float at(float a, float b, float c, float t, float u) {
        return a + t * ((1F - u) * (b - a) + u * (c - a));
    }

    /**
     * A bent wheel's tilt: {@code n} corners turned {@code degrees} about
     * the upright through the hub {@code (hubX, hubZ)}; spun with the wheel
     * the tilt goes round, a wobble.
     */
    static void tilt(float[] x, float[] z, int n, float hubX, float hubZ, float degrees) {
        double a = Math.toRadians(degrees);
        float sin = (float) Math.sin(a);
        float cos = (float) Math.cos(a);
        for (int k = 0; k < n; k++) {
            float dx = x[k] - hubX;
            float dz = z[k] - hubZ;
            x[k] = hubX + dx * cos - dz * sin;
            z[k] = hubZ + dx * sin + dz * cos;
        }
    }
}
