package nfm.lit.features.helmetcam;

import nfm.lit.features.Canvas;

/**
 * The inside of the car as the helmet cam sees it (first person, "The
 * helmet cam shows a car interior"): a basic cockpit that fits any car. A
 * dashboard with a lit top, an instrument hood with two dials whose needles
 * follow the speed and the power, a steering wheel that turns with the
 * steering, a centre console, door sills at the sides and, in a car with
 * glass, the windscreen's pillars and the roof's edge. An open car (no
 * glass) has no pillars and no roof.
 * <p>
 * The game draws nothing nearer than 50 units to the eyes properly, and an
 * extra's polygon with one corner behind the eyes is left out whole. So the
 * cockpit is not built at its true size around the head: every part is
 * drawn on a plane across the car at least {@link #NEAREST} units ahead of
 * the eyes (the head comes 16 forward under braking), as large as it has to
 * be to look right from there, and the parts are on planes of their own, the
 * wheel nearest, so they shift against each other as the head moves on its
 * springs. Places are given as slopes from the eyes: {@code x} to the right
 * and {@code y} down per unit of distance ahead. The parts at the far sides
 * are cut into strips narrow enough that one whose outer end is behind the
 * eyes, when the head turns away, is out of the picture altogether.
 * <p>
 * Drawn on the car, after it, so it covers what the eyes would see of the
 * car's own inside. Nothing is allocated.
 */
final class Interior {

    /**
     * No part is nearer than this ahead of the eyes at rest: the game's 50,
     * the head's 16 of travel, and what turning the head and the view takes
     * off a part's depth.
     */
    static final float NEAREST = 86F;
    /** How far ahead of the eyes the dashboard is at least, and how much nearer the instruments and the wheel are. */
    static final float DASH_AHEAD = 102F;
    static final float INSTRUMENTS_NEARER = 6F;
    static final float WHEEL_NEARER = 16F;
    /** The top of the dashboard is this much below level at least and at most, as a slope. */
    static final float COWL_LEAST = 0.10F;
    static final float COWL_MOST = 0.34F;
    /**
     * The scuttle, a dark strip standing this much above the cowl: the
     * dashboard is drawn farther off than the bonnet's foot, so when the
     * head drops (a landing) the bonnet's foot rises against it, and the
     * scuttle is what shows there.
     */
    static final float SCUTTLE_HIGH = 0.05F;
    /** Down from the cowl: the front edge of the dashboard's top, the foot of its face. */
    static final float TOP_DEEP = 0.07F;
    static final float FACE_DEEP = 0.38F;
    /** The footwell is cut across here, and this is the lowest any part reaches: slopes down from level. */
    static final float FLOOR_CUT = 1F;
    static final float FLOOR = 1.9F;
    /**
     * Where the strips across the car are cut, as slopes to the side. The
     * outermost reach 83 degrees to the side: past the edge of a 21:9
     * picture with the head turned all the way.
     */
    static final float[] ACROSS = {-8F, -3.4F, -2.4F, -1.6F, -1.05F, -0.55F, 0.55F, 1.05F, 1.6F, 2.4F, 3.4F, 8F};
    /** The steering wheel: its middle below the cowl, its outer and inner radius, as slopes; how far it turns, degrees. */
    static final float WHEEL_DOWN = 0.33F;
    static final float WHEEL_OUT = 0.27F;
    static final float WHEEL_IN = 0.225F;
    static final float WHEEL_TURN = 100F;
    static final int WHEEL_PARTS = 12;
    /** The mark at the top of the wheel's rim is this many degrees wide each side of the top. */
    static final float MARK_HALF = 4F;
    /** The two dials: to each side of the middle, below the cowl, their radius; a needle sweeps this each side of up. */
    static final float DIAL_ASIDE = 0.115F;
    static final float DIAL_DOWN = 0.22F;
    static final float DIAL_ROUND = 0.062F;
    static final float NEEDLE_SWEEP = 125F;
    /** The pillars' and the roof edge's places: where a pillar stands on the cowl, where it meets the roof, the roof's edge. */
    static final float PILLAR_FOOT = 1.05F;
    static final float PILLAR_HEAD = 0.80F;
    static final float PILLAR_WIDE = 0.11F;
    static final float ROOF_EDGE = -0.61F;
    static final float ROOF_CUT = -0.9F;
    static final float ROOF_TOP = -1.5F;

    static final int SCUTTLE = 0x1a1c1f;
    static final int DASH_TOP = 0x3a3e45;
    static final int DASH_FACE = 0x24272c;
    static final int LOWER = 0x131417;
    static final int FLOOR_DARK = 0x0c0d0f;
    static final int CONSOLE = 0x2b2e34;
    static final int TRIM = 0x555b64;
    static final int VENT_FRAME = 0x3f444b;
    static final int VENT = 0x0a0b0c;
    static final int RADIO = 0x2e3238;
    static final int SCREEN = 0x1f5560;
    static final int KNOB = 0x5a6068;
    static final int HOOD = 0x2a2d33;
    static final int PANEL = 0x0c0d0f;
    static final int BEZEL = 0x9aa0a8;
    static final int DIAL = 0x060708;
    static final int NEEDLE = 0xff5a28;
    static final int DOOR = 0x23262a;
    static final int SILL = 0x3c4046;
    static final int PILLAR = 0x16181b;
    static final int ROOF = 0x1b1d21;
    static final int RIM = 0x08090a;
    static final int GRIP = 0x30343a;
    static final int SPOKE = 0x1a1c20;
    static final int HUB = 0x33373d;
    static final int MARK = 0xe0c040;

    private final float[] x = new float[Canvas.MAX_CORNERS];
    private final float[] y = new float[Canvas.MAX_CORNERS];
    private final float[] z = new float[Canvas.MAX_CORNERS];
    private final float[] sx = new float[Canvas.MAX_CORNERS];
    private final float[] sy = new float[Canvas.MAX_CORNERS];

    /* the car being drawn */
    private Canvas canvas;
    private int car;
    private float eyeX;
    private float eyeY;
    private float eyeZ;
    private float lightX;
    private float lightY;
    private float lightZ;

    /** The cowl of {@code c}'s car: how far below level the top of its dashboard is seen, as a slope. */
    static float cowl(Cockpit c) {
        float slope = (c.bonnetTop - c.eyeY) / Math.max(1F, c.dashZ - c.eyeZ);
        return Math.max(COWL_LEAST, Math.min(COWL_MOST, slope));
    }

    /** How far ahead of {@code c}'s eyes the dashboard is drawn. */
    static float ahead(Cockpit c) {
        return Math.max(DASH_AHEAD, c.dashZ - c.eyeZ);
    }

    /**
     * Draws car {@code car}'s inside from cockpit {@code c}: the wheel
     * turned {@code steer} (-1 full left to 1 full right), the needles at
     * {@code speed} and {@code power} (0 to 1), lit as the stage lights
     * world point {@code (wx, wy, wz)}, the car's place.
     */
    void draw(Canvas canvas, int car, Cockpit c, float steer, float speed, float power, float wx, float wy,
            float wz) {
        this.canvas = canvas;
        this.car = car;
        eyeX = c.eyeX;
        eyeY = c.eyeY;
        eyeZ = c.eyeZ;
        lightX = wx;
        lightY = wy;
        lightZ = wz;
        float cowl = cowl(c);
        float far = ahead(c);
        float near = far - INSTRUMENTS_NEARER;
        // the dashboard: its top, lit from the windscreen, its face, and below it the dark of the footwell
        band(far, cowl - SCUTTLE_HIGH, cowl, SCUTTLE);
        band(far, cowl, cowl + TOP_DEEP, DASH_TOP);
        band(far, cowl + TOP_DEEP, cowl + FACE_DEEP, DASH_FACE);
        band(far, cowl + FACE_DEEP, FLOOR_CUT, LOWER);
        band(far, FLOOR_CUT, FLOOR, FLOOR_DARK);
        fittings(far, cowl);
        // the console between the seats, widening towards the eyes
        color(CONSOLE);
        quad(far, -0.15F, cowl + FACE_DEEP, 0.15F, cowl + FACE_DEEP, 0.22F, FLOOR_CUT, -0.22F, FLOOR_CUT);
        quad(far, -0.22F, FLOOR_CUT, 0.22F, FLOOR_CUT, 0.40F, FLOOR, -0.40F, FLOOR);
        doors(far, cowl);
        if (c.closed) {
            roof(far, cowl);
        }
        instruments(near, cowl, speed, power);
        wheel(far - WHEEL_NEARER, cowl + WHEEL_DOWN, steer);
        this.canvas = null;
    }

    /** What is on the dashboard's face: a trim line, an air vent each side, switches on the left, a radio on the right. */
    private void fittings(float ahead, float cowl) {
        for (int side = -1; side <= 1; side += 2) {
            color(TRIM);
            quad(ahead, side * 0.33F, cowl + 0.215F, side * PILLAR_FOOT, cowl + 0.215F, side * PILLAR_FOOT,
                    cowl + 0.228F, side * 0.33F, cowl + 0.228F);
            color(VENT_FRAME);
            quad(ahead, side * 0.38F, cowl + 0.105F, side * 0.66F, cowl + 0.105F, side * 0.66F, cowl + 0.175F,
                    side * 0.38F, cowl + 0.175F);
            color(VENT);
            quad(ahead, side * 0.395F, cowl + 0.115F, side * 0.645F, cowl + 0.115F, side * 0.645F, cowl + 0.136F,
                    side * 0.395F, cowl + 0.136F);
            quad(ahead, side * 0.395F, cowl + 0.144F, side * 0.645F, cowl + 0.144F, side * 0.645F, cowl + 0.165F,
                    side * 0.395F, cowl + 0.165F);
        }
        color(KNOB);
        for (int k = 0; k < 3; k++) {
            disc(ahead, -0.44F - k * 0.085F, cowl + 0.30F, 0.022F, 6);
        }
        color(RADIO);
        quad(ahead, 0.40F, cowl + 0.255F, 0.70F, cowl + 0.255F, 0.70F, cowl + 0.345F, 0.40F, cowl + 0.345F);
        color(SCREEN);
        quad(ahead, 0.42F, cowl + 0.27F, 0.58F, cowl + 0.27F, 0.58F, cowl + 0.33F, 0.42F, cowl + 0.33F);
        color(KNOB);
        disc(ahead, 0.64F, cowl + 0.30F, 0.024F, 6);
    }

    /** The door on each side: a sill with a lit top edge, from where the dashboard ends out past the picture's edge. */
    private void doors(float ahead, float cowl) {
        float top = cowl + 0.03F;
        for (int side = -1; side <= 1; side += 2) {
            // from the pillar's foot outwards, the sill falling a little towards the eyes
            float from = side * PILLAR_FOOT;
            float sill = top;
            for (int k = ACROSS.length / 2 + 1; k < ACROSS.length; k++) {
                if (ACROSS[k] <= PILLAR_FOOT) {
                    continue;
                }
                float to = side * ACROSS[k];
                float next = top + 0.04F * (ACROSS[k] - PILLAR_FOOT);
                color(SILL);
                quad(ahead, from, sill, to, next, to, next + 0.07F, from, sill + 0.07F);
                color(DOOR);
                quad(ahead, from, sill + 0.07F, to, next + 0.07F, to, FLOOR_CUT, from, FLOOR_CUT);
                from = to;
                sill = next;
            }
        }
    }

    /** A car with glass: the windscreen's pillars, leaning in, and the roof's edge over it and over the side windows. */
    private void roof(float ahead, float cowl) {
        color(PILLAR);
        for (int side = -1; side <= 1; side += 2) {
            float foot = side * PILLAR_FOOT;
            float head = side * PILLAR_HEAD;
            float wide = side * PILLAR_WIDE;
            float middle = (cowl + ROOF_EDGE) / 2F;
            // in two parts, so neither spans too much of the view
            quad(ahead, (foot + head) / 2F, middle, (foot + head) / 2F + wide, middle, foot + wide, cowl + 0.04F, foot,
                    cowl + 0.04F);
            quad(ahead, head, ROOF_EDGE, head + wide, ROOF_EDGE, (foot + head) / 2F + wide, middle,
                    (foot + head) / 2F, middle);
        }
        band(ahead, ROOF_CUT, ROOF_EDGE, ROOF);
        band(ahead, ROOF_TOP, ROOF_CUT, ROOF);
    }

    /** The instruments: a hood standing on the dashboard, a dark panel under it, two dials with their needles. */
    private void instruments(float ahead, float cowl, float speed, float power) {
        color(HOOD);
        quad(ahead, -0.22F, cowl + 0.035F, 0.22F, cowl + 0.035F, 0.27F, cowl + 0.115F, -0.27F, cowl + 0.115F);
        color(PANEL);
        quad(ahead, -0.26F, cowl + 0.115F, 0.26F, cowl + 0.115F, 0.24F, cowl + 0.31F, -0.24F, cowl + 0.31F);
        dial(ahead, -DIAL_ASIDE, cowl + DIAL_DOWN, speed);
        dial(ahead, DIAL_ASIDE, cowl + DIAL_DOWN, power);
    }

    /** One dial at slopes {@code (cx, cy)}: a pale ring, a dark face, a needle at {@code value} of its sweep. */
    private void dial(float ahead, float cx, float cy, float value) {
        color(BEZEL);
        disc(ahead, cx, cy, DIAL_ROUND, 10);
        color(DIAL);
        disc(ahead, cx, cy, DIAL_ROUND * 0.84F, 10);
        double a = Math.toRadians(-NEEDLE_SWEEP + 2F * NEEDLE_SWEEP * Math.max(0F, Math.min(1F, value)));
        float ux = (float) Math.sin(a);
        float uy = (float) -Math.cos(a);
        float tip = DIAL_ROUND * 0.76F;
        float foot = DIAL_ROUND * 0.13F;
        color(NEEDLE);
        sx[0] = cx + ux * tip;
        sy[0] = cy + uy * tip;
        sx[1] = cx - uy * foot - ux * foot;
        sy[1] = cy + ux * foot - uy * foot;
        sx[2] = cx + uy * foot - ux * foot;
        sy[2] = cy - ux * foot - uy * foot;
        put(ahead, 3);
    }

    /**
     * The steering wheel, its middle at slope {@code cy} below level,
     * turned {@code steer} of its lock: a rim in twelve parts with a mark
     * at its top, so the turn shows, three spokes and a hub.
     */
    private void wheel(float ahead, float cy, float steer) {
        double turn = Math.toRadians(WHEEL_TURN * Math.max(-1F, Math.min(1F, steer)));
        for (int k = 0; k < WHEEL_PARTS; k++) {
            double a = turn + (k - 0.5D) * 2D * Math.PI / WHEEL_PARTS;
            double b = turn + (k + 0.5D) * 2D * Math.PI / WHEEL_PARTS;
            color(k % 3 == 0 && k != 0 && k != 6 ? GRIP : RIM);
            ring(a, WHEEL_OUT, cy, 0);
            ring(b, WHEEL_OUT, cy, 1);
            ring(b, WHEEL_IN, cy, 2);
            ring(a, WHEEL_IN, cy, 3);
            put(ahead, 4);
        }
        color(MARK);
        ring(turn - Math.toRadians(MARK_HALF), WHEEL_OUT, cy, 0);
        ring(turn + Math.toRadians(MARK_HALF), WHEEL_OUT, cy, 1);
        ring(turn + Math.toRadians(MARK_HALF), WHEEL_IN, cy, 2);
        ring(turn - Math.toRadians(MARK_HALF), WHEEL_IN, cy, 3);
        put(ahead, 4);
        color(SPOKE);
        for (int k = 0; k < 3; k++) {
            // to the left, to the right and down
            double a = turn + Math.toRadians(k == 0 ? -90D : k == 1 ? 90D : 180D);
            float ux = (float) Math.sin(a);
            float uy = (float) -Math.cos(a);
            float half = 0.024F;
            float from = 0.045F;
            float to = WHEEL_IN + 0.008F;
            sx[0] = ux * from - uy * half;
            sy[0] = cy + uy * from + ux * half;
            sx[1] = ux * to - uy * half;
            sy[1] = cy + uy * to + ux * half;
            sx[2] = ux * to + uy * half;
            sy[2] = cy + uy * to - ux * half;
            sx[3] = ux * from + uy * half;
            sy[3] = cy + uy * from - ux * half;
            put(ahead, 4);
        }
        color(HUB);
        disc(ahead, 0F, cy, 0.055F, 8);
    }

    /** Corner {@code k}: the point of the wheel's ring at angle {@code a} (0 up, turning right) and radius {@code r}. */
    private void ring(double a, float r, float cy, int k) {
        sx[k] = (float) Math.sin(a) * r;
        sy[k] = cy - (float) Math.cos(a) * r;
    }

    /* ----------------------------------------------------------- drawing */

    private void color(int rgb) {
        canvas.color(canvas.fog(rgb, lightX, lightY, lightZ));
    }

    /** A strip across the whole car between slopes {@code top} and {@code bottom}, in its parts. */
    private void band(float ahead, float top, float bottom, int rgb) {
        color(rgb);
        for (int k = 0; k + 1 < ACROSS.length; k++) {
            quad(ahead, ACROSS[k], top, ACROSS[k + 1], top, ACROSS[k + 1], bottom, ACROSS[k], bottom);
        }
    }

    private void quad(float ahead, float x0, float y0, float x1, float y1, float x2, float y2, float x3, float y3) {
        sx[0] = x0;
        sy[0] = y0;
        sx[1] = x1;
        sy[1] = y1;
        sx[2] = x2;
        sy[2] = y2;
        sx[3] = x3;
        sy[3] = y3;
        put(ahead, 4);
    }

    /** A round part with {@code n} corners at slopes {@code (cx, cy)}, radius {@code r}. */
    private void disc(float ahead, float cx, float cy, float r, int n) {
        for (int k = 0; k < n; k++) {
            double a = k * 2D * Math.PI / n;
            sx[k] = cx + (float) Math.sin(a) * r;
            sy[k] = cy - (float) Math.cos(a) * r;
        }
        put(ahead, n);
    }

    /** The {@code n} corners at slopes {@link #sx}, {@link #sy}, {@code ahead} of the eyes, onto the car. */
    private void put(float ahead, int n) {
        float d = Math.max(NEAREST, ahead);
        for (int k = 0; k < n; k++) {
            x[k] = eyeX + sx[k] * d;
            y[k] = eyeY + sy[k] * d;
            z[k] = eyeZ + d;
        }
        canvas.onCar(car, x, y, z, n);
    }
}
