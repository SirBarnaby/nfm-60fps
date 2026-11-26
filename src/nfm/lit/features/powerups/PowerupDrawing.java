package nfm.lit.features.powerups;

import nfm.lit.features.CarShape;
import nfm.lit.features.Canvas;
import nfm.lit.features.RaceView;

/**
 * The powerups' shapes, in flat shaded polygons as the game draws its own:
 * the boxes (spinning, bobbing cubes, each face with a darker diamond), the
 * dynamite stick tumbling, the powerup a car holds floating over its roof,
 * the bomb (a black ball with a sparking fuse, over the roof and on the
 * road) and the nitrous flame. Things that move are
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

    /* the held powerups' colours: outside and inside (the bomb is drawn as itself) */
    private static final int[] ICON_OUT = {0, 0x2F7BFF, 0xD8281C};
    private static final int[] ICON_IN = {0, 0xBFE0FF, 0xFFD27A};

    /* the nitrous flame: one burning, and two at once (red, a bit bigger) */
    private static final int FLAME_BLUE = 0x3866FF;
    private static final int FLAME_BLUE_CORE = 0xC8E4FF;
    private static final int FLAME_RED = 0xFF3A22;
    private static final int FLAME_RED_CORE = 0xFFD2A0;
    static final float FLAME_DOUBLE = 1.35F;

    /* the bomb: a black ball this big on the road and over a roof, lit from dark to a dull shine, with a fuse */
    static final float BOMB_RADIUS = 24F;
    private static final float BOMB_HELD = 15F;
    private static final int BOMB_DARK = 0x101014;
    private static final int BOMB_SHINE = 0x6E6E7C;
    private static final int BOMB_FUSE = 0xB89A5A;
    private static final int BOMB_SPARK = 0xFFC850;
    private static final int BOMB_SPARK_CORE = 0xFFF6D8;
    /** The ball: an icosahedron's twelve corners (unit) and twenty faces. */
    private static final float[][] BALL = new float[12][];
    private static final int[][] BALL_FACES = {
        {0, 11, 5}, {0, 5, 1}, {0, 1, 7}, {0, 7, 10}, {0, 10, 11}, {1, 5, 9}, {5, 11, 4}, {11, 10, 2}, {10, 7, 6},
        {7, 1, 8}, {3, 9, 4}, {3, 4, 2}, {3, 2, 6}, {3, 6, 8}, {3, 8, 9}, {4, 9, 5}, {2, 4, 11}, {6, 2, 10},
        {8, 6, 7}, {9, 8, 1},
    };

    static {
        float t = 1.618034F;
        float[][] corners = {
            {-1, t, 0}, {1, t, 0}, {-1, -t, 0}, {1, -t, 0}, {0, -1, t}, {0, 1, t}, {0, -1, -t}, {0, 1, -t},
            {t, 0, -1}, {t, 0, 1}, {-t, 0, -1}, {-t, 0, 1},
        };
        float len = (float) Math.sqrt(1F + t * t);
        for (int i = 0; i < 12; i++) {
            BALL[i] = new float[] {corners[i][0] / len, corners[i][1] / len, corners[i][2] / len};
        }
    }

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
        if (v == null || c >= v.cars || kind <= 0 || kind > PowerupRace.BOMB) {
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
        if (kind == PowerupRace.BOMB) {
            bombShape(canvas, x, y + bob, z, BOMB_HELD, v.vx[c], v.vy[c] + bobNext - bob, v.vz[c], c);
            return;
        }
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

    /**
     * Nitrous burning in car {@code c}: a flickering flame from under its
     * tail, blue for one, red and a bit bigger while two burn at once.
     */
    void flame(Canvas canvas, int c) {
        RaceView v = f.view;
        if (v == null || c >= v.cars) {
            return;
        }
        boolean two = f.burning[c] > 1;
        float size = two ? FLAME_DOUBLE : 1F;
        CarShape shape = v.shape[c];
        float height = shape.maxY - (shape.maxY - shape.minY) * 0.3F;
        float flicker = (float) Math.sin(f.tick * 2.3F + c) * 14F;
        float length = (80F + flicker) * size;
        flameTriangle(canvas, v, c, height, shape.minZ, 14F * size, length, two ? FLAME_RED : FLAME_BLUE, 0.75F);
        flameTriangle(canvas, v, c, height, shape.minZ, 6F * size, length * 0.55F, two ? FLAME_RED_CORE
                : FLAME_BLUE_CORE, 1F);
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

    /* --------------------------------------------------------- the bomb */

    /** Bomb {@code b} on the road (or falling to it): the ball resting on the ground, its fuse sparking. */
    void bomb(Canvas canvas, int b) {
        bombShape(canvas, f.bombX[b], f.bombY[b] - BOMB_RADIUS, f.bombZ[b], BOMB_RADIUS, 0F, f.bombVY[b], 0F, b);
    }

    /**
     * A bomb of radius {@code r} at {@code (x, y, z)} moving
     * {@code (vx, vy, vz)} a tick: a black ball of flat faces, a short fuse
     * out of its top and a flickering spark at the fuse's end.
     */
    private void bombShape(Canvas canvas, float x, float y, float z, float r, float vx, float vy, float vz,
            int which) {
        canvas.glow(0F);
        for (int[] face : BALL_FACES) {
            float[] a = BALL[face[0]];
            float[] b = BALL[face[1]];
            float[] c = BALL[face[2]];
            float nx = a[0] + b[0] + c[0];
            float ny = a[1] + b[1] + c[1];
            float nz = a[2] + b[2] + c[2];
            float len = (float) Math.sqrt(nx * nx + ny * ny + nz * nz);
            nx /= len;
            ny /= len;
            nz /= len;
            if (!facing(nx, ny, nz, x + nx * r, y + ny * r, z + nz * r)) {
                continue;
            }
            float light = Math.max(0F, Math.min(1F, -(nx * LIGHT_X + ny * LIGHT_Y + nz * LIGHT_Z)));
            canvas.color(canvas.fog(mix(BOMB_DARK, BOMB_SHINE, light * light), x, y, z));
            for (int k = 0; k < 3; k++) {
                float[] corner = BALL[face[k]];
                px[k] = x + corner[0] * r;
                py[k] = y + corner[1] * r;
                pz[k] = z + corner[2] * r;
            }
            canvas.moving(px, py, pz, vx, vy, vz, 3);
        }
        // the fuse: a short band from the top, leaning, turned to the eye
        float dx = x - f.eyeX;
        float dz = z - f.eyeZ;
        float len = (float) Math.sqrt(dx * dx + dz * dz);
        float ax = len > 1F ? dz / len : 1F;
        float az = len > 1F ? -dx / len : 0F;
        float w = r * 0.09F;
        float tipX = x + ax * r * 0.3F;
        float tipY = y - r * 1.55F;
        float tipZ = z + az * r * 0.3F;
        px[0] = x - ax * w;
        py[0] = y - r * 0.95F;
        pz[0] = z - az * w;
        px[1] = x + ax * w;
        py[1] = y - r * 0.95F;
        pz[1] = z + az * w;
        px[2] = tipX + ax * w;
        py[2] = tipY;
        pz[2] = tipZ + az * w;
        px[3] = tipX - ax * w;
        py[3] = tipY;
        pz[3] = tipZ - az * w;
        canvas.color(canvas.fog(BOMB_FUSE, x, y, z));
        canvas.moving(px, py, pz, vx, vy, vz, 4);
        // the spark: two diamonds, a new size every tick
        float flicker = 0.75F + 0.25F * hash(f.tick * 7 + which);
        spark(canvas, tipX, tipY, tipZ, ax, az, r * 0.42F * flicker, vx, vy, vz, BOMB_SPARK);
        spark(canvas, tipX, tipY, tipZ, ax, az, r * 0.2F * flicker, vx, vy, vz, BOMB_SPARK_CORE);
    }

    private void spark(Canvas canvas, float x, float y, float z, float ax, float az, float size, float vx, float vy,
            float vz, int rgb) {
        px[0] = x;
        py[0] = y - size;
        pz[0] = z;
        px[1] = x + ax * size;
        py[1] = y;
        pz[1] = z + az * size;
        px[2] = x;
        py[2] = y + size;
        pz[2] = z;
        px[3] = x - ax * size;
        py[3] = y;
        pz[3] = z - az * size;
        canvas.glow(1F);
        canvas.color(rgb);
        canvas.moving(px, py, pz, vx, vy, vz, 4);
        canvas.glow(0F);
    }

    /** A number from -1 to 1 from {@code n}, the same every run. */
    private static float hash(int n) {
        int h = n * 0x9E3779B1;
        h ^= h >>> 15;
        return (h & 0xFFFF) / 32767.5F - 1F;
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
