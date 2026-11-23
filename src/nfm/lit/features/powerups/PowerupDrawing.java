package nfm.lit.features.powerups;

import nfm.lit.features.CarShape;
import nfm.lit.features.Canvas;
import nfm.lit.features.RaceView;

/**
 * The powerups' shapes, in flat shaded polygons as the game draws its own:
 * the boxes (spinning, bobbing cubes, each face with a darker diamond), the
 * dynamite stick tumbling, the powerup a car holds floating over its roof,
 * the nitrous flame, the magnet's arc and the craters. Things that move are
 * drawn with each corner's move to the next tick ({@link Canvas#moving}),
 * so they are smooth between the ticks; a solid's faces turned away from
 * the eye are left out. Nothing is allocated while drawing.
 */
final class PowerupDrawing {

    /* the boxes */
    private static final float BOX_HALF = 30F;
    private static final float BOX_TURN = 0.16F;
    private static final float BOX_BOB = 8F;
    private static final float BOX_BOB_RATE = 0.33F;
    private static final int BOX_SIDE = 0xFFB020;
    private static final int BOX_SIDE_DARK = 0xF08A10;
    private static final int BOX_TOP = 0xFFE070;
    private static final int BOX_MARK = 0x7A3A08;

    /* the held powerups' colours: outside and inside */
    private static final int[] ICON_OUT = {0, 0x2F7BFF, 0xD8281C, 0xB048F0};
    private static final int[] ICON_IN = {0, 0xBFE0FF, 0xFFD27A, 0xF0C8FF};

    /* the craters: a scorched patch, a bowl this deep and a raised lip, in rings of this many sides */
    private static final int SIDES = 12;
    private static final float DEPTH = 18F;
    private static final float LIP = 7F;
    private static final int SOOT = 0x2A241E;

    /* light from above, a little from the side (y grows downwards) */
    private static final float LIGHT_X = 0.36F;
    private static final float LIGHT_Y = -0.84F;
    private static final float LIGHT_Z = 0.40F;

    /* a cube's corners (unit) and faces (corner indices, outward normals) */
    private static final float[] CX = {-1, 1, 1, -1, -1, 1, 1, -1};
    private static final float[] CY = {-1, -1, -1, -1, 1, 1, 1, 1};
    private static final float[] CZ = {-1, -1, 1, 1, -1, -1, 1, 1};
    private static final int[][] FACES = {
        {0, 1, 2, 3}, {7, 6, 5, 4}, {3, 2, 6, 7}, {1, 0, 4, 5}, {2, 1, 5, 6}, {0, 3, 7, 4},
    };
    private static final float[][] NORMALS = {
        {0, -1, 0}, {0, 1, 0}, {0, 0, 1}, {0, 0, -1}, {1, 0, 0}, {-1, 0, 0},
    };

    private final PowerupsFeature f;

    /* the corners of the solid being drawn: now and at the next tick */
    private final float[] nowX = new float[8];
    private final float[] nowY = new float[8];
    private final float[] nowZ = new float[8];
    private final float[] nextX = new float[8];
    private final float[] nextY = new float[8];
    private final float[] nextZ = new float[8];

    /* one polygon */
    private final float[] px = new float[Canvas.MAX_CORNERS];
    private final float[] py = new float[Canvas.MAX_CORNERS];
    private final float[] pz = new float[Canvas.MAX_CORNERS];
    private final float[] mx = new float[Canvas.MAX_CORNERS];
    private final float[] my = new float[Canvas.MAX_CORNERS];
    private final float[] mz = new float[Canvas.MAX_CORNERS];
    private final float[] p = new float[3];
    private final float[] q = new float[3];

    PowerupDrawing(PowerupsFeature feature) {
        f = feature;
    }

    /* ------------------------------------------------------------ boxes */

    /** Box {@code b}: a cube spinning about its upright and bobbing over the road. */
    void box(Canvas canvas, int b) {
        PowerupRace race = f.race;
        float x = race.boxX[b];
        float y = race.boxY[b];
        float z = race.boxZ[b];
        float turn = f.tick * BOX_TURN + b * 0.7F;
        float bob = (float) Math.sin((f.tick + b * 5) * BOX_BOB_RATE) * BOX_BOB;
        float bobNext = (float) Math.sin((f.tick + 1 + b * 5) * BOX_BOB_RATE) * BOX_BOB;
        cube(x, y + bob, z, turn, nowX, nowY, nowZ);
        cube(x, y + bobNext, z, turn + BOX_TURN, nextX, nextY, nextZ);
        float c = (float) Math.cos(turn);
        float s = (float) Math.sin(turn);
        canvas.glow(0.35F);
        for (int face = 0; face < FACES.length; face++) {
            float[] n = NORMALS[face];
            float nx = n[0] * c - n[2] * s;
            float nz = n[0] * s + n[2] * c;
            float ny = n[1];
            if (!facing(nx, ny, nz, x, y + bob, z)) {
                continue;
            }
            int rgb = ny != 0F ? BOX_TOP : (face & 1) == 0 ? BOX_SIDE : BOX_SIDE_DARK;
            canvas.color(canvas.fog(lit(rgb, nx, ny, nz), x, y, z));
            int[] corners = FACES[face];
            for (int k = 0; k < 4; k++) {
                corner(k, corners[k]);
            }
            canvas.moving(px, py, pz, mx, my, mz, 4);
            if (ny == 0F) {
                mark(canvas, corners, nx, nz, x, y, z);
            }
        }
    }

    /** A darker diamond in the middle of a side face, a hair out from it, so the spin is seen. */
    private void mark(Canvas canvas, int[] corners, float nx, float nz, float x, float y, float z) {
        float ax = 0F;
        float ay = 0F;
        float az = 0F;
        float bx = 0F;
        float by = 0F;
        float bz = 0F;
        for (int k = 0; k < 4; k++) {
            ax += nowX[corners[k]] / 4F;
            ay += nowY[corners[k]] / 4F;
            az += nowZ[corners[k]] / 4F;
            bx += nextX[corners[k]] / 4F;
            by += nextY[corners[k]] / 4F;
            bz += nextZ[corners[k]] / 4F;
        }
        float out = 0.6F;
        for (int k = 0; k < 4; k++) {
            // halfway from the face's middle to the middle of each edge: a diamond
            int i = corners[k];
            int j = corners[(k + 1) % 4];
            float ex = (nowX[i] + nowX[j]) / 2F;
            float ey = (nowY[i] + nowY[j]) / 2F;
            float ez = (nowZ[i] + nowZ[j]) / 2F;
            float fx = (nextX[i] + nextX[j]) / 2F;
            float fy = (nextY[i] + nextY[j]) / 2F;
            float fz = (nextZ[i] + nextZ[j]) / 2F;
            px[k] = ax + (ex - ax) * 0.55F + nx * out;
            py[k] = ay + (ey - ay) * 0.55F;
            pz[k] = az + (ez - az) * 0.55F + nz * out;
            mx[k] = bx + (fx - bx) * 0.55F + nx * out - px[k];
            my[k] = by + (fy - by) * 0.55F - py[k];
            mz[k] = bz + (fz - bz) * 0.55F + nz * out - pz[k];
        }
        canvas.color(canvas.fog(BOX_MARK, x, y, z));
        canvas.moving(px, py, pz, mx, my, mz, 4);
    }

    /** A cube's corners at {@code (x, y, z)}, turned {@code turn} radians about its upright. */
    private static void cube(float x, float y, float z, float turn, float[] ox, float[] oy, float[] oz) {
        float c = (float) Math.cos(turn);
        float s = (float) Math.sin(turn);
        for (int i = 0; i < 8; i++) {
            float lx = CX[i] * BOX_HALF;
            float lz = CZ[i] * BOX_HALF;
            ox[i] = x + lx * c - lz * s;
            oy[i] = y + CY[i] * BOX_HALF;
            oz[i] = z + lx * s + lz * c;
        }
    }

    /* ------------------------------------------------------------ the stick */

    /** Dynamite stick {@code s}: a red stick tumbling end over end along its flight. */
    void stick(Canvas canvas, int s) {
        float x = f.stickX[s];
        float y = f.stickY[s];
        float z = f.stickZ[s];
        float vx = f.stickVX[s];
        float vy = f.stickVY[s];
        float vz = f.stickVZ[s];
        float h = (float) Math.sqrt(vx * vx + vz * vz);
        float hx = h > 0.01F ? vx / h : 1F;
        float hz = h > 0.01F ? vz / h : 0F;
        float tumble = f.stickAge[s] * 0.6F;
        stickCorners(x, y, z, hx, hz, tumble, nowX, nowY, nowZ);
        stickCorners(x + vx, y + vy, z + vz, hx, hz, tumble + 0.6F, nextX, nextY, nextZ);
        drawSolid(canvas, 0xC8281E, x, y, z, 0.1F);
    }

    /** The stick's corners: 32 long along its tumbling axis, 8 across. */
    private static void stickCorners(float x, float y, float z, float hx, float hz, float tumble, float[] ox,
            float[] oy, float[] oz) {
        float c = (float) Math.cos(tumble);
        float s = (float) Math.sin(tumble);
        // along: in the upright plane of the flight; side: level, square to it; up: square to both
        float ux = hx * c;
        float uy = -s;
        float uz = hz * c;
        float sx = -hz;
        float sz = hx;
        float wx = uy * sz;
        float wy = uz * sx - ux * sz;
        float wz = -uy * sx;
        for (int i = 0; i < 8; i++) {
            float a = CZ[i] * 16F;
            float b = CX[i] * 4F;
            float d = CY[i] * 4F;
            ox[i] = x + ux * a + sx * b + wx * d;
            oy[i] = y + uy * a + wy * d;
            oz[i] = z + uz * a + sz * b + wz * d;
        }
    }

    /** The eight corners in now/next as a solid of colour {@code rgb}: the faces turned toward the eye. */
    private void drawSolid(Canvas canvas, int rgb, float x, float y, float z, float glow) {
        canvas.glow(glow);
        for (int[] face : FACES) {
            // the face's normal from its corners
            float ax = nowX[face[1]] - nowX[face[0]];
            float ay = nowY[face[1]] - nowY[face[0]];
            float az = nowZ[face[1]] - nowZ[face[0]];
            float bx = nowX[face[3]] - nowX[face[0]];
            float by = nowY[face[3]] - nowY[face[0]];
            float bz = nowZ[face[3]] - nowZ[face[0]];
            float nx = ay * bz - az * by;
            float ny = az * bx - ax * bz;
            float nz = ax * by - ay * bx;
            float len = (float) Math.sqrt(nx * nx + ny * ny + nz * nz);
            if (len < 1e-4F) {
                continue;
            }
            nx /= len;
            ny /= len;
            nz /= len;
            float cx = (nowX[face[0]] + nowX[face[2]]) / 2F;
            float cy = (nowY[face[0]] + nowY[face[2]]) / 2F;
            float cz = (nowZ[face[0]] + nowZ[face[2]]) / 2F;
            // outward: away from the solid's middle
            if (nx * (cx - x) + ny * (cy - y) + nz * (cz - z) < 0F) {
                nx = -nx;
                ny = -ny;
                nz = -nz;
            }
            if (!facing(nx, ny, nz, cx, cy, cz)) {
                continue;
            }
            canvas.color(canvas.fog(lit(rgb, nx, ny, nz), x, y, z));
            for (int k = 0; k < 4; k++) {
                corner(k, face[k]);
            }
            canvas.moving(px, py, pz, mx, my, mz, 4);
        }
    }

    /* ------------------------------------------------- what a car holds */

    /** The powerup car {@code c} holds: a two-tone diamond floating over its roof, facing the eye. */
    void icon(Canvas canvas, int c) {
        RaceView v = f.view;
        int kind = f.held[c];
        if (v == null || c >= v.cars || kind <= 0 || kind >= ICON_OUT.length) {
            return;
        }
        CarShape shape = v.shape[c];
        float bob = (float) Math.sin(f.tick * 0.3F) * 6F;
        float bobNext = (float) Math.sin((f.tick + 1) * 0.3F) * 6F;
        float top = shape.minY - 46F;
        float mid = (shape.minZ + shape.maxZ) / 2F;
        v.toWorld(c, 0F, top, mid, p);
        float x = p[0];
        float y = p[1];
        float z = p[2];
        diamond(canvas, x, y + bob, z, 22F, bobNext - bob, v.vx[c], v.vy[c], v.vz[c], ICON_OUT[kind], 0.5F);
        diamond(canvas, x, y + bob, z, 11F, bobNext - bob, v.vx[c], v.vy[c], v.vz[c], ICON_IN[kind], 0.8F);
    }

    /** A diamond {@code size} high facing the eye at {@code (x, y, z)}, moving {@code (vx, vy + rise, vz)} a tick. */
    private void diamond(Canvas canvas, float x, float y, float z, float size, float rise, float vx, float vy,
            float vz, int rgb, float glow) {
        float dx = x - f.eyeX;
        float dz = z - f.eyeZ;
        float len = (float) Math.sqrt(dx * dx + dz * dz);
        float ax = len > 1F ? dz / len : 1F;
        float az = len > 1F ? -dx / len : 0F;
        float w = size * 0.7F;
        px[0] = x;
        py[0] = y - size;
        pz[0] = z;
        px[1] = x + ax * w;
        py[1] = y;
        pz[1] = z + az * w;
        px[2] = x;
        py[2] = y + size;
        pz[2] = z;
        px[3] = x - ax * w;
        py[3] = y;
        pz[3] = z - az * w;
        for (int k = 0; k < 4; k++) {
            mx[k] = vx;
            my[k] = vy + rise;
            mz[k] = vz;
        }
        canvas.glow(glow);
        canvas.color(canvas.fog(rgb, x, y, z));
        canvas.moving(px, py, pz, mx, my, mz, 4);
    }

    /** Nitrous burning in car {@code c}: a flickering blue flame from under its tail. */
    void flame(Canvas canvas, int c) {
        RaceView v = f.view;
        if (v == null || c >= v.cars) {
            return;
        }
        CarShape shape = v.shape[c];
        float height = shape.maxY - (shape.maxY - shape.minY) * 0.3F;
        float flicker = (float) Math.sin(f.tick * 2.3F + c) * 14F;
        float length = 80F + flicker;
        flameTriangle(canvas, v, c, height, shape.minZ, 14F, length, 0x3866FF, 0.75F);
        flameTriangle(canvas, v, c, height, shape.minZ, 6F, length * 0.55F, 0xC8E4FF, 1F);
    }

    private void flameTriangle(Canvas canvas, RaceView v, int c, float y, float back, float half, float length,
            int rgb, float coverage) {
        v.toWorld(c, -half, y, back, p);
        set(0, p);
        v.toWorld(c, half, y, back, p);
        set(1, p);
        v.toWorld(c, 0F, y, back - length, p);
        set(2, p);
        for (int k = 0; k < 3; k++) {
            mx[k] = v.vx[c];
            my[k] = v.vy[c];
            mz[k] = v.vz[c];
        }
        canvas.glow(1F);
        canvas.dither(coverage);
        canvas.color(rgb);
        canvas.moving(px, py, pz, mx, my, mz, 3);
        canvas.dither(1F);
    }

    /* ------------------------------------------------------- the magnet */

    /** Car {@code c}'s magnet: a crackling arc from its roof to the car it pulls toward. */
    void arc(Canvas canvas, int c) {
        RaceView v = f.view;
        int t = f.pulling[c];
        if (v == null || t < 0 || c >= v.cars || t >= v.cars) {
            return;
        }
        v.toWorld(c, 0F, v.shape[c].minY - 10F, 0F, p);
        v.toWorld(t, 0F, v.shape[t].minY - 10F, 0F, q);
        int segments = 5;
        canvas.glow(0.8F);
        canvas.dither(0.7F);
        canvas.color(0xA8E0FF);
        float lastX = p[0];
        float lastY = p[1];
        float lastZ = p[2];
        for (int k = 1; k <= segments; k++) {
            float share = k / (float) segments;
            float jitter = k == segments ? 0F : jitter(c, k) * 40F;
            float x = p[0] + (q[0] - p[0]) * share;
            float y = p[1] + (q[1] - p[1]) * share - (float) Math.sin(share * Math.PI) * 60F + jitter;
            float z = p[2] + (q[2] - p[2]) * share;
            segment(canvas, v, c, t, lastX, lastY, lastZ, (k - 1) / (float) segments, x, y, z, share);
            lastX = x;
            lastY = y;
            lastZ = z;
        }
        canvas.dither(1F);
    }

    /** A number from -1 to 1 for the arc's bend at point {@code k}, new every tick. */
    private float jitter(int c, int k) {
        int h = (f.tick * 73 + c * 31 + k * 17) * 0x9E3779B1;
        h ^= h >>> 15;
        return (h & 0xFFFF) / 32767.5F - 1F;
    }

    /** A thin band of the arc from {@code a} to {@code b}, its ends moving as the two cars do. */
    private void segment(Canvas canvas, RaceView v, int c, int t, float ax, float ay, float az, float shareA,
            float bx, float by, float bz, float shareB) {
        float dx = bx - ax;
        float dz = bz - az;
        float len = (float) Math.sqrt(dx * dx + dz * dz);
        float wx = len > 1F ? -dz / len * 4F : 4F;
        float wz = len > 1F ? dx / len * 4F : 0F;
        px[0] = ax - wx;
        py[0] = ay;
        pz[0] = az - wz;
        px[1] = bx - wx;
        py[1] = by;
        pz[1] = bz - wz;
        px[2] = bx + wx;
        py[2] = by + 3F;
        pz[2] = bz + wz;
        px[3] = ax + wx;
        py[3] = ay + 3F;
        pz[3] = az + wz;
        for (int k = 0; k < 4; k++) {
            float share = k == 0 || k == 3 ? shareA : shareB;
            mx[k] = v.vx[c] + (v.vx[t] - v.vx[c]) * share;
            my[k] = v.vy[c] + (v.vy[t] - v.vy[c]) * share;
            mz[k] = v.vz[c] + (v.vz[t] - v.vz[c]) * share;
        }
        canvas.moving(px, py, pz, mx, my, mz, 4);
    }

    /* ------------------------------------------------------- the craters */

    /** Crater {@code i}: a scorched patch, then a bowl inside a raised lip, its faces shaded by their slope. */
    void crater(Canvas canvas, int i) {
        float x = f.craterX[i];
        float y = f.craterY[i];
        float z = f.craterZ[i];
        float s = f.craterSize[i];
        int ground = f.view == null ? SOOT : f.view.groundColor;
        int burnt = mix(ground, SOOT, 0.7F);
        int earth = mix(ground, 0x5A4A38, 0.6F);
        // the scorched patch, see-through
        canvas.dither(0.55F);
        canvas.color(canvas.fog(mix(ground, 0x141210, 0.8F), x, y, z));
        for (int k = 0; k < SIDES; k++) {
            double a = k * 2D * Math.PI / SIDES;
            px[k] = x + (float) Math.cos(a) * 200F * s;
            py[k] = y - 0.5F;
            pz[k] = z + (float) Math.sin(a) * 200F * s;
        }
        canvas.world(px, py, pz, SIDES);
        canvas.dither(1F);
        // the bowl's floor
        canvas.color(canvas.fog(lit(burnt, 0F, -1F, 0F), x, y, z));
        for (int k = 0; k < SIDES; k++) {
            double a = k * 2D * Math.PI / SIDES;
            px[k] = x + (float) Math.cos(a) * 34F * s;
            py[k] = y + DEPTH * s;
            pz[k] = z + (float) Math.sin(a) * 34F * s;
        }
        canvas.world(px, py, pz, SIDES);
        // the rings, the far side first
        double eye = Math.atan2(f.eyeZ - z, f.eyeX - x);
        int start = (int) Math.round((eye + Math.PI) / (2D * Math.PI) * SIDES);
        for (int n = 0; n < SIDES; n++) {
            int k = Math.floorMod(start + (n % 2 == 0 ? n / 2 : -(n + 1) / 2), SIDES);
            ring(canvas, x, y, z, k, 34F * s, DEPTH * s, 92F * s, 0F, burnt);
            ring(canvas, x, y, z, k, 92F * s, 0F, 118F * s, -LIP * s, earth);
            ring(canvas, x, y, z, k, 118F * s, -LIP * s, 150F * s, 0F, earth);
        }
    }

    /** Side {@code k} of a ring from radius {@code r0} at depth {@code d0} out to {@code r1} at {@code d1}. */
    private void ring(Canvas canvas, float x, float y, float z, int k, float r0, float d0, float r1, float d1,
            int rgb) {
        double a0 = k * 2D * Math.PI / SIDES;
        double a1 = (k + 1) * 2D * Math.PI / SIDES;
        float c0 = (float) Math.cos(a0);
        float s0 = (float) Math.sin(a0);
        float c1 = (float) Math.cos(a1);
        float s1 = (float) Math.sin(a1);
        px[0] = x + c0 * r0;
        py[0] = y + d0;
        pz[0] = z + s0 * r0;
        px[1] = x + c1 * r0;
        py[1] = y + d0;
        pz[1] = z + s1 * r0;
        px[2] = x + c1 * r1;
        py[2] = y + d1;
        pz[2] = z + s1 * r1;
        px[3] = x + c0 * r1;
        py[3] = y + d1;
        pz[3] = z + s0 * r1;
        // the slope's normal: outward along the middle of the side, tilted by how the ring rises
        float cm = (c0 + c1) / 2F;
        float sm = (s0 + s1) / 2F;
        float run = r1 - r0;
        float rise = d1 - d0; // negative: rises outward (y grows downwards)
        float len = (float) Math.sqrt(run * run + rise * rise);
        float nx = -cm * (-rise) / len;
        float nz = -sm * (-rise) / len;
        float ny = -run / len;
        canvas.color(canvas.fog(lit(rgb, nx, ny, nz), x, y, z));
        canvas.world(px, py, pz, 4);
    }

    /* ------------------------------------------------------------ helpers */

    /** Corner {@code k} of the polygon from solid corner {@code i}: where it is, and its move to the next tick. */
    private void corner(int k, int i) {
        px[k] = nowX[i];
        py[k] = nowY[i];
        pz[k] = nowZ[i];
        mx[k] = nextX[i] - nowX[i];
        my[k] = nextY[i] - nowY[i];
        mz[k] = nextZ[i] - nowZ[i];
    }

    private void set(int k, float[] at) {
        px[k] = at[0];
        py[k] = at[1];
        pz[k] = at[2];
    }

    /** True if a face at {@code (x, y, z)} with normal {@code n} is turned toward the eye. */
    private boolean facing(float nx, float ny, float nz, float x, float y, float z) {
        return nx * (f.eyeX - x) + ny * (f.eyeY - y) + nz * (f.eyeZ - z) > 0F;
    }

    /** {@code rgb} lit by the light from above: a face turned to it full, one turned away darker. */
    private static int lit(int rgb, float nx, float ny, float nz) {
        float d = -(nx * LIGHT_X + ny * LIGHT_Y + nz * LIGHT_Z);
        float k = 0.62F + 0.38F * Math.max(-0.4F, Math.min(1F, d));
        int r = Math.min(255, (int) (((rgb >> 16) & 0xFF) * k));
        int g = Math.min(255, (int) (((rgb >> 8) & 0xFF) * k));
        int b = Math.min(255, (int) ((rgb & 0xFF) * k));
        return r << 16 | g << 8 | b;
    }

    /** {@code a} turned {@code share} of the way to {@code b}. */
    private static int mix(int a, int b, float share) {
        int r = (int) (((a >> 16) & 0xFF) + ((((b >> 16) & 0xFF) - ((a >> 16) & 0xFF)) * share));
        int g = (int) (((a >> 8) & 0xFF) + ((((b >> 8) & 0xFF) - ((a >> 8) & 0xFF)) * share));
        int bl = (int) ((a & 0xFF) + (((b & 0xFF) - (a & 0xFF)) * share));
        return r << 16 | g << 8 | bl;
    }
}
