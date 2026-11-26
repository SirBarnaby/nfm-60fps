package nfm.lit.features.realism;

import nfm.lit.features.CarShape;
import nfm.lit.features.Canvas;

/**
 * Draws what damage and heat put on a car: glowing discs on the wheels,
 * flames at the exhaust tips, the windscreen's cracks, the scratches, drawn
 * brake lights. Each is a few flat polygons on the car ({@link Canvas#onCar},
 * so it moves with it) or on a wheel ({@link Canvas#onWheel}). Nothing is
 * allocated.
 */
final class RealismDrawing {

    private static final float TAU = 6.2831855F;
    private static final int HUB = 0x3a3a3c;
    private static final int CRACK = 0xe6eef2;
    private static final int STREAK = 0xd8dadc;
    private static final int BARE_METAL = 0xb4b8bc;
    private static final int LAMP = 0xff2a18;
    private static final int FLAME = 0xff8a22;
    private static final int FLAME_CORE = 0xffe08a;
    /** An exhaust pop's flash, and how long and how wide (at its root) one is. */
    static final int POP_FLASH = 0xfff4d6;
    private static final float FLASH_LONG = 4.5F;
    private static final float FLASH_WIDE = 3.2F;
    private static final int NITROUS = 0x5f9cff;
    private static final int NITROUS_CORE = 0xdcefff;
    /** How far the cracks and scratches stand off their glass and panel, so the panel never covers them. */
    private static final float STAND_OFF = 0.35F;
    /** A flame's teardrop: how far along and how wide each of its five corners is. */
    private static final float[] DROP_ALONG = {0F, 0.45F, 1F, 0.45F, 0F};
    private static final float[] DROP_ACROSS = {-1F, -1.3F, 0F, 1.3F, 1F};

    private final float[] x = new float[Canvas.MAX_CORNERS];
    private final float[] y = new float[Canvas.MAX_CORNERS];
    private final float[] z = new float[Canvas.MAX_CORNERS];

    /** Car {@code car}'s brake disc on wheel {@code w}, glowing with its heat, seen through the wheel. */
    void disc(Canvas canvas, int car, CarState s, int w) {
        CarShape shape = s.parts.shape;
        float r = shape.wheelRadius[w];
        if (r <= 0F) {
            return;
        }
        float t = s.brake[w];
        float out = shape.hubX[w] < 0F ? -1F : 1F;
        float face = shape.hubX[w] + out * (shape.wheelHalfWidth[w] + 0.3F);
        int hubY = Math.round(shape.hubY[w]);
        int hubZ = Math.round(shape.hubZ[w]);
        canvas.color(canvas.fog(HeatEffects.discColor(t), s.at[0], s.at[1], s.at[2]));
        canvas.glow(HeatEffects.discGlow(t));
        octagon(face, shape.hubY[w], shape.hubZ[w], r * 0.62F);
        canvas.onWheel(car, x, y, z, 8, shape.steerX[w], hubY, hubZ);
        canvas.glow(0F);
        canvas.color(canvas.fog(HUB, s.at[0], s.at[1], s.at[2]));
        octagon(face + out * 0.1F, shape.hubY[w], shape.hubZ[w], r * 0.28F);
        canvas.onWheel(car, x, y, z, 8, shape.steerX[w], hubY, hubZ);
    }

    private void octagon(float px, float py, float pz, float r) {
        for (int k = 0; k < 8; k++) {
            float a = k * (TAU / 8F);
            x[k] = px;
            y[k] = py + (float) Math.sin(a) * r;
            z[k] = pz + (float) Math.cos(a) * r;
        }
    }

    /** The flame at car {@code car}'s exhaust tip {@code pipe}: orange on lift-off, blue with nitrous. */
    void flame(Canvas canvas, int car, CarState s, int pipe, boolean nitrous) {
        float reach = s.flameReach[pipe];
        if (reach <= 0F) {
            return;
        }
        CarParts p = s.parts;
        canvas.glow(1F);
        for (int layer = 0; layer < 2; layer++) {
            boolean core = layer == 1;
            int rgb = nitrous ? (core ? NITROUS_CORE : NITROUS) : core ? FLAME_CORE : FLAME;
            canvas.color(canvas.fog(rgb, s.at[0], s.at[1], s.at[2]));
            float length = reach * (core ? 0.6F : 1F);
            float half = (p.stacks ? 1.6F : 1.4F) * (core ? 0.55F : 1F);
            for (int plane = 0; plane < 2; plane++) {
                teardrop(p, pipe, length, half, plane);
                canvas.onCar(car, x, y, z, 5);
            }
        }
        float flash = s.popFlash[pipe];
        if (flash > 0F && !nitrous) {
            // a pop: for this tick a short burst at the pipe's mouth, wider than the flame and near white
            canvas.color(canvas.fog(POP_FLASH, s.at[0], s.at[1], s.at[2]));
            for (int plane = 0; plane < 2; plane++) {
                teardrop(p, pipe, FLASH_LONG * flash, FLASH_WIDE * flash, plane);
                canvas.onCar(car, x, y, z, 5);
            }
        }
    }

    /**
     * A flame's teardrop from tip {@code pipe}, {@code length} long and
     * {@code half} wide at its root, in one of two planes square to each
     * other (so it shows from every side): back from a pipe, up from a stack.
     */
    private void teardrop(CarParts p, int pipe, float length, float half, int plane) {
        float tx = p.pipeX[pipe];
        float ty = p.pipeY[pipe];
        float tz = p.pipeZ[pipe];
        // along: back (-z) for pipes, up (-y) for stacks; across: one of the two others
        float alongY = p.stacks ? -1F : 0F;
        float alongZ = p.stacks ? 0F : -1F;
        float acrossX = plane == 0 ? 1F : 0F;
        float acrossY = plane == 0 || p.stacks ? 0F : 1F;
        float acrossZ = plane == 1 && p.stacks ? 1F : 0F;
        for (int k = 0; k < 5; k++) {
            float along = DROP_ALONG[k] * length;
            float across = DROP_ACROSS[k] * half;
            x[k] = tx + acrossX * across;
            y[k] = ty + alongY * along + acrossY * across;
            z[k] = tz + alongZ * along + acrossZ * across;
        }
    }

    /** The cracks on car {@code car}'s windscreen: each star's rays and a ring across them. */
    void cracks(Canvas canvas, int car, CarState s) {
        CarParts p = s.parts;
        canvas.color(canvas.fog(CRACK, s.at[0], s.at[1], s.at[2]));
        canvas.dither(0.8F);
        float w = Math.max(0.15F, Math.min(p.screenReachU, p.screenReachV) * 0.03F);
        for (int k = 0; k < s.stars; k++) {
            float u0 = s.starU[k];
            float v0 = s.starV[k];
            for (int r = 0; r < CarState.RAYS; r++) {
                int ray = k * CarState.RAYS + r;
                float du = s.rayU[ray] - u0;
                float dv = s.rayV[ray] - v0;
                float len = (float) Math.sqrt(du * du + dv * dv);
                if (len < 1e-3F) {
                    continue;
                }
                float pu = -dv / len * w;
                float pv = du / len * w;
                onScreen(p, 0, u0 + pu, v0 + pv);
                onScreen(p, 1, s.rayU[ray], s.rayV[ray]);
                onScreen(p, 2, u0 - pu, v0 - pv);
                canvas.onCar(car, x, y, z, 3);
                // the ring: from this ray to the next, at four tenths of the way out
                int next = k * CarState.RAYS + (r + 1) % CarState.RAYS;
                float au = u0 + du * 0.4F;
                float av = v0 + dv * 0.4F;
                float bu = u0 + (s.rayU[next] - u0) * 0.4F;
                float bv = v0 + (s.rayV[next] - v0) * 0.4F;
                float su = bu - au;
                float sv = bv - av;
                float sl = (float) Math.sqrt(su * su + sv * sv);
                if (sl < 1e-3F) {
                    continue;
                }
                float qu = -sv / sl * w * 0.7F;
                float qv = su / sl * w * 0.7F;
                onScreen(p, 0, au + qu, av + qv);
                onScreen(p, 1, bu + qu, bv + qv);
                onScreen(p, 2, bu - qu, bv - qv);
                onScreen(p, 3, au - qu, av - qv);
                canvas.onCar(car, x, y, z, 4);
            }
        }
        canvas.dither(1F);
    }

    /** Corner {@code k} at {@code (u, v)} on the windscreen, standing off it. */
    private void onScreen(CarParts p, int k, float u, float v) {
        x[k] = p.screenMid[0] + p.screenU[0] * u + p.screenV[0] * v + p.screenN[0] * STAND_OFF;
        y[k] = p.screenMid[1] + p.screenU[1] * u + p.screenV[1] * v + p.screenN[1] * STAND_OFF;
        z[k] = p.screenMid[2] + p.screenU[2] * u + p.screenV[2] * v + p.screenN[2] * STAND_OFF;
    }

    /** The scratches on car {@code car}'s side {@code side}: pale streaks and bare-metal chips. */
    void scratches(Canvas canvas, int car, CarState s, int side) {
        CarParts p = s.parts;
        int base = side * CarState.SCRATCHES;
        canvas.color(canvas.fog(STREAK, s.at[0], s.at[1], s.at[2]));
        canvas.dither(0.85F);
        for (int j = 0; j < s.scratches[side]; j++) {
            int k = base + j;
            int plane = s.scratchPlane[k];
            float half = 0.3F;
            float sy = s.scratchY[k];
            onPanel(p, plane, 0, sy - half, s.scratchFrom[k]);
            onPanel(p, plane, 1, sy - half * 0.4F, s.scratchTo[k]);
            onPanel(p, plane, 2, sy + half * 0.4F, s.scratchTo[k]);
            onPanel(p, plane, 3, sy + half, s.scratchFrom[k]);
            canvas.onCar(car, x, y, z, 4);
        }
        canvas.dither(1F);
        canvas.color(canvas.fog(BARE_METAL, s.at[0], s.at[1], s.at[2]));
        for (int j = 0; j < s.scratches[side]; j++) {
            int k = base + j;
            int plane = s.scratchPlane[k];
            for (int c = 0; c < CarState.CHIPS; c++) {
                int chip = k * CarState.CHIPS + c;
                float cy = s.chipY[chip];
                float cz = s.chipZ[chip];
                float r = s.chipSize[chip];
                onPanel(p, plane, 0, cy - r, cz);
                onPanel(p, plane, 1, cy, cz + r * 1.3F);
                onPanel(p, plane, 2, cy + r * 0.8F, cz);
                onPanel(p, plane, 3, cy, cz - r);
                canvas.onCar(car, x, y, z, 4);
            }
        }
    }

    /** Corner {@code k} on side panel {@code plane} at height {@code py}, length {@code pz}, standing off it. */
    private void onPanel(CarParts p, int plane, int k, float py, float pz) {
        x[k] = p.xOn(plane, py, pz) + p.nx[plane] * STAND_OFF;
        y[k] = py + p.ny[plane] * STAND_OFF;
        z[k] = pz + p.nz[plane] * STAND_OFF;
    }

    /** Brake lights drawn on a car whose model has none: two small lamps at the back, lit. */
    void lamps(Canvas canvas, int car, CarState s) {
        CarParts p = s.parts;
        canvas.color(canvas.fog(LAMP, s.at[0], s.at[1], s.at[2]));
        canvas.glow(1F);
        float ly = p.top + p.height * 0.5F;
        float lz = p.rear - 0.3F;
        for (int side = -1; side <= 1; side += 2) {
            float lx = side * p.halfWidth * 0.6F;
            quad(lx - 1.6F, ly - 0.8F, lz, lx + 1.6F, ly - 0.8F, lz, lx + 1.6F, ly + 0.8F, lz, lx - 1.6F, ly + 0.8F, lz);
            canvas.onCar(car, x, y, z, 4);
        }
    }

    private void quad(float ax, float ay, float az, float bx, float by, float bz, float cx, float cy, float cz,
            float dx, float dy, float dz) {
        x[0] = ax;
        y[0] = ay;
        z[0] = az;
        x[1] = bx;
        y[1] = by;
        z[1] = bz;
        x[2] = cx;
        y[2] = cy;
        z[2] = cz;
        x[3] = dx;
        y[3] = dy;
        z[3] = dz;
    }
}
