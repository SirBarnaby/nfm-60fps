package nfm.lit.features.physx;

import nfm.lit.features.CarShape;
import nfm.lit.features.RaceView;
import physx.PxTopLevelFunctions;
import physx.common.PxDefaultAllocator;
import physx.common.PxDefaultCpuDispatcher;
import physx.common.PxDefaultErrorCallback;
import physx.common.PxFoundation;
import physx.common.PxIDENTITYEnum;
import physx.common.PxQuat;
import physx.common.PxTolerancesScale;
import physx.common.PxTransform;
import physx.common.PxVec3;
import physx.extensions.PxRigidBodyExt;
import physx.geometry.PxBoxGeometry;
import physx.physics.PxFilterData;
import physx.physics.PxMaterial;
import physx.physics.PxPhysics;
import physx.physics.PxRigidBodyFlagEnum;
import physx.physics.PxRigidDynamic;
import physx.physics.PxRigidStatic;
import physx.physics.PxScene;
import physx.physics.PxSceneDesc;
import physx.physics.PxShape;
import physx.physics.PxShapeFlagEnum;
import physx.physics.PxShapeFlags;

/**
 * The PhysX scene of a race (physx, "PhysX to try out"): the only class
 * that touches PhysX (through physx-jni, {@code lib\LICENSES-physx.txt}),
 * so the game runs without it where its library is missing.
 * <p>
 * The scene is in the game's own units and time: a unit is a world unit, a
 * step is a race tick (in two halves), and down is the way y grows. In it
 * stand the flat ground, the stage's pieces (floors, walls and ramps, as
 * boxes), and a box for each car that is moved to where the car is every
 * tick, so it shoves what lies in its way. The loose pieces are boxes PhysX
 * moves. Nothing here reaches the race: it reads the race's copy.
 */
final class PhysWorld {

    /** Loose pieces at most; the oldest makes way. */
    static final int BODIES = 160;
    /** Gravity, units a tick each tick: as the earth's at the cars' size (a car is about 350 units long). */
    static final float GRAVITY = 2.2F;
    /** A piece moves no faster than this, units a tick: a fast car's shove stays in sight. */
    private static final float FASTEST = 110F;
    private static final float GROUND = 250F;
    /** A car set down farther than this from where it was is put there, not swept there. */
    private static final float JUMP = 500F;

    private static PxFoundation foundation;
    private static PxPhysics physics;
    private static PxDefaultCpuDispatcher dispatcher;
    private static PxTolerancesScale scale;
    private static PxMaterial hard;
    private static PxMaterial soft;
    private static PxShapeFlags flags;
    private static PxFilterData filter;

    private PxScene scene;
    private final PxVec3 v = new PxVec3(0F, 0F, 0F);
    private final PxQuat q = new PxQuat(0F, 0F, 0F, 1F);
    private final PxTransform pose = new PxTransform(PxIDENTITYEnum.PxIdentity);
    private final PxRigidDynamic[] body = new PxRigidDynamic[BODIES];
    private final PxShape[] bodyShape = new PxShape[BODIES];
    private PxRigidStatic[] fixed = new PxRigidStatic[0];
    private PxShape[] fixedShape = new PxShape[0];
    private int fixedCount;
    private PxRigidDynamic[] car = new PxRigidDynamic[0];
    private PxShape[] carShape = new PxShape[0];
    private float[] carAt = new float[0];
    private float[] carMiddle = new float[0];
    private int cars;
    private final float[] p = new float[3];
    private final float[] a = new float[3];
    private final float[] m = new float[9];

    /** Stage pieces standing in the scene, of those the race has. */
    int pieces;

    /** Starts PhysX, once for the game. Throws whatever goes wrong: the library missing, its natives not loading. */
    static synchronized void load() {
        if (physics != null) {
            return;
        }
        int version = PxTopLevelFunctions.getPHYSICS_VERSION();
        foundation = PxTopLevelFunctions.CreateFoundation(version, new PxDefaultAllocator(),
                new PxDefaultErrorCallback());
        scale = new PxTolerancesScale();
        scale.setLength(50F);
        scale.setSpeed(50F);
        physics = PxTopLevelFunctions.CreatePhysics(version, foundation, scale);
        // no threads of its own: a step runs on the game thread, after the tick
        dispatcher = PxTopLevelFunctions.DefaultCpuDispatcherCreate(0);
        hard = physics.createMaterial(0.6F, 0.5F, 0.25F);
        soft = physics.createMaterial(0.9F, 0.8F, 0.02F);
        flags = new PxShapeFlags(
                (byte) (PxShapeFlagEnum.eSCENE_QUERY_SHAPE.value | PxShapeFlagEnum.eSIMULATION_SHAPE.value));
        filter = new PxFilterData(1, 1, 0, 0);
    }

    /** PhysX's version, such as 5.4.1. */
    static String version() {
        int n = PxTopLevelFunctions.getPHYSICS_VERSION();
        return (n >> 24) + "." + (n >> 16 & 0xff) + "." + (n >> 8 & 0xff);
    }

    /** A scene for the race {@code r} shows: the ground, the stage's pieces, the cars. */
    PhysWorld(RaceView r) {
        load();
        PxSceneDesc desc = new PxSceneDesc(scale);
        v.setX(0F);
        v.setY(GRAVITY);
        v.setZ(0F);
        desc.setGravity(v);
        desc.setCpuDispatcher(dispatcher);
        desc.setFilterShader(PxTopLevelFunctions.DefaultFilterShader());
        scene = physics.createScene(desc);
        desc.destroy();
        fixed = new PxRigidStatic[r.pieces + 1];
        fixedShape = new PxShape[r.pieces + 1];
        // the flat ground: a slab whose top is the ground
        identity();
        box(0F, GROUND + 500F, 0F, 1e6F, 500F, 1e6F);
        pieces = 0;
        for (int i = 0; i < r.pieces; i++) {
            pieces += piece(r, i) ? 1 : 0;
        }
        cars = r.cars;
        car = new PxRigidDynamic[cars];
        carShape = new PxShape[cars];
        carAt = new float[cars * 3];
        carMiddle = new float[cars * 3];
        for (int c = 0; c < cars; c++) {
            CarShape s = r.shape[c];
            boolean sized = s != null && s.maxX > s.minX && s.maxZ > s.minZ && s.maxY > s.minY;
            float hx = sized ? (s.maxX - s.minX) / 2F : 40F;
            // down to just above where its wheels stand, so what lies flat on the ground is shoved too
            float top = sized ? s.minY : -40F;
            float low = sized ? s.maxY : 28F;
            for (int w = 0; s != null && w < RaceView.WHEELS; w++) {
                if (s.wheelRadius[w] > 0F) {
                    low = Math.max(low, s.hubY[w] + s.wheelRadius[w] - 1.5F);
                }
            }
            float hy = (low - top) / 2F;
            float hz = sized ? (s.maxZ - s.minZ) / 2F : 80F;
            carMiddle[c * 3] = sized ? (s.minX + s.maxX) / 2F : 0F;
            carMiddle[c * 3 + 1] = (top + low) / 2F;
            carMiddle[c * 3 + 2] = sized ? (s.minZ + s.maxZ) / 2F : 0F;
            carPose(r, c);
            PxBoxGeometry geometry = new PxBoxGeometry(hx, hy, hz);
            carShape[c] = physics.createShape(geometry, hard, true, flags);
            geometry.destroy();
            carShape[c].setSimulationFilterData(filter);
            car[c] = physics.createRigidDynamic(pose);
            car[c].setRigidBodyFlag(PxRigidBodyFlagEnum.eKINEMATIC, true);
            car[c].attachShape(carShape[c]);
            scene.addActor(car[c]);
            carAt[c * 3] = p[0];
            carAt[c * 3 + 1] = p[1];
            carAt[c * 3 + 2] = p[2];
        }
    }

    private void identity() {
        q.setX(0F);
        q.setY(0F);
        q.setZ(0F);
        q.setW(1F);
    }

    /** A box that never moves, its middle at {@code (x, y, z)}, turned as {@link #q} says. */
    private void box(float x, float y, float z, float hx, float hy, float hz) {
        v.setX(x);
        v.setY(y);
        v.setZ(z);
        pose.setP(v);
        pose.setQ(q);
        PxBoxGeometry geometry = new PxBoxGeometry(Math.max(1F, hx), Math.max(1F, hy), Math.max(1F, hz));
        PxShape shape = physics.createShape(geometry, hard, true, flags);
        geometry.destroy();
        shape.setSimulationFilterData(filter);
        PxRigidStatic actor = physics.createRigidStatic(pose);
        actor.attachShape(shape);
        scene.addActor(actor);
        fixed[fixedCount] = actor;
        fixedShape[fixedCount++] = shape;
    }

    /**
     * Stage piece {@code i} as a box: a raised floor as a thin slab whose
     * top is the floor, a wall as the box it is, a ramp as a slab tilted as
     * the ramp is, its top the ramp. A floor level with the ground is the
     * ground. False for a piece that adds nothing.
     */
    private boolean piece(RaceView r, int i) {
        float x = r.pieceX[i];
        float y = r.pieceY[i];
        float z = r.pieceZ[i];
        float rx = r.pieceRadX[i];
        float ry = r.pieceRadY[i];
        float rz = r.pieceRadZ[i];
        int xy = r.pieceXY[i];
        int zy = r.pieceZY[i];
        if (rx <= 0F || rz <= 0F) {
            return false;
        }
        identity();
        if (xy == 0 && zy == 0) {
            if (y >= GROUND - 1F) {
                return false;
            }
            box(x, y + 8F, z, rx, 8F, rz);
            return true;
        }
        if (Math.abs(xy) == 90 || Math.abs(zy) == 90) {
            box(x, y, z, rx, Math.max(ry, 20F), rz);
            return true;
        }
        float thick = 8F;
        if (zy != 0) {
            // it rises along z: y grows by tan(zy) for every unit of z
            double tilt = Math.toRadians(zy);
            float sin = (float) Math.sin(tilt);
            float cos = (float) Math.cos(tilt);
            float half = Math.min(rz / Math.max(0.05F, Math.abs(cos)), ry / Math.max(0.05F, Math.abs(sin)));
            q.setX((float) Math.sin(-tilt / 2D));
            q.setW((float) Math.cos(-tilt / 2D));
            box(x, y + cos * thick, z - sin * thick, rx, thick, half);
        } else {
            // along x: y grows by tan(xy) for every unit of x
            double tilt = Math.toRadians(xy);
            float sin = (float) Math.sin(tilt);
            float cos = (float) Math.cos(tilt);
            float half = Math.min(rx / Math.max(0.05F, Math.abs(cos)), ry / Math.max(0.05F, Math.abs(sin)));
            q.setZ((float) Math.sin(tilt / 2D));
            q.setW((float) Math.cos(tilt / 2D));
            box(x - sin * thick, y + cos * thick, z, half, thick, rz);
        }
        return true;
    }

    /** Car {@code c}'s box as the car is now, into {@link #pose}; its middle into {@link #p}. */
    private void carPose(RaceView r, int c) {
        float mx = carMiddle[c * 3];
        float my = carMiddle[c * 3 + 1];
        float mz = carMiddle[c * 3 + 2];
        r.toWorld(c, mx, my, mz, p);
        // its three axes in the world: the columns of its turn
        for (int axis = 0; axis < 3; axis++) {
            r.toWorld(c, mx + (axis == 0 ? 1F : 0F), my + (axis == 1 ? 1F : 0F), mz + (axis == 2 ? 1F : 0F), a);
            m[axis] = a[0] - p[0];
            m[3 + axis] = a[1] - p[1];
            m[6 + axis] = a[2] - p[2];
        }
        turn(m);
        v.setX(p[0]);
        v.setY(p[1]);
        v.setZ(p[2]);
        pose.setP(v);
        pose.setQ(q);
    }

    /** The turn of matrix {@code m} (rows first) into {@link #q}. */
    private void turn(float[] m) {
        float trace = m[0] + m[4] + m[8];
        float x;
        float y;
        float z;
        float w;
        if (trace > 0F) {
            float s = (float) Math.sqrt(trace + 1F) * 2F;
            w = 0.25F * s;
            x = (m[7] - m[5]) / s;
            y = (m[2] - m[6]) / s;
            z = (m[3] - m[1]) / s;
        } else if (m[0] > m[4] && m[0] > m[8]) {
            float s = (float) Math.sqrt(1F + m[0] - m[4] - m[8]) * 2F;
            w = (m[7] - m[5]) / s;
            x = 0.25F * s;
            y = (m[1] + m[3]) / s;
            z = (m[2] + m[6]) / s;
        } else if (m[4] > m[8]) {
            float s = (float) Math.sqrt(1F + m[4] - m[0] - m[8]) * 2F;
            w = (m[2] - m[6]) / s;
            x = (m[1] + m[3]) / s;
            y = 0.25F * s;
            z = (m[5] + m[7]) / s;
        } else {
            float s = (float) Math.sqrt(1F + m[8] - m[0] - m[4]) * 2F;
            w = (m[3] - m[1]) / s;
            x = (m[2] + m[6]) / s;
            y = (m[5] + m[7]) / s;
            z = 0.25F * s;
        }
        float len = (float) Math.sqrt(x * x + y * y + z * z + w * w);
        if (len < 1e-6F) {
            identity();
            return;
        }
        q.setX(x / len);
        q.setY(y / len);
        q.setZ(z / len);
        q.setW(w / len);
    }

    /** One race tick: the cars' boxes go to where the cars are, then PhysX moves the loose pieces. */
    void step(RaceView r) {
        for (int c = 0; c < cars && c < r.cars; c++) {
            carPose(r, c);
            float dx = p[0] - carAt[c * 3];
            float dy = p[1] - carAt[c * 3 + 1];
            float dz = p[2] - carAt[c * 3 + 2];
            if (dx * dx + dy * dy + dz * dz > JUMP * JUMP) {
                car[c].setGlobalPose(pose);
            } else {
                car[c].setKinematicTarget(pose);
            }
            carAt[c * 3] = p[0];
            carAt[c * 3 + 1] = p[1];
            carAt[c * 3 + 2] = p[2];
        }
        for (int half = 0; half < 2; half++) {
            scene.simulate(0.5F);
            scene.fetchResults(true);
        }
    }

    /**
     * Loose piece {@code slot} from now on: a box of half sizes
     * {@code (hx, hy, hz)} at {@code (x, y, z)}, moving {@code (vx, vy, vz)}
     * a tick and turning {@code (wx, wy, wz)} radians a tick, {@code snow}
     * if it is a lump of snow (it hardly bounces). Whatever the slot held is
     * gone.
     */
    void spawn(int slot, float x, float y, float z, float hx, float hy, float hz, float vx, float vy, float vz,
            float wx, float wy, float wz, boolean snow) {
        drop(slot);
        identity();
        v.setX(x);
        v.setY(Math.min(y, GROUND - Math.max(hx, Math.max(hy, hz)) - 1F));
        v.setZ(z);
        pose.setP(v);
        pose.setQ(q);
        PxBoxGeometry geometry = new PxBoxGeometry(hx, hy, hz);
        PxShape shape = physics.createShape(geometry, snow ? soft : hard, true, flags);
        geometry.destroy();
        shape.setSimulationFilterData(filter);
        PxRigidDynamic b = physics.createRigidDynamic(pose);
        b.attachShape(shape);
        PxRigidBodyExt.setMassAndUpdateInertia(b, Math.max(0.2F, hx * hy * hz / 400F));
        b.setMaxLinearVelocity(FASTEST);
        b.setMaxAngularVelocity(1.5F);
        // a piece born inside a car's box is eased out of it, not shot out
        b.setMaxDepenetrationVelocity(8F);
        b.setLinearDamping(0.004F);
        b.setAngularDamping(0.02F);
        v.setX(vx);
        v.setY(vy);
        v.setZ(vz);
        b.setLinearVelocity(v);
        v.setX(wx);
        v.setY(wy);
        v.setZ(wz);
        b.setAngularVelocity(v);
        scene.addActor(b);
        body[slot] = b;
        bodyShape[slot] = shape;
    }

    /** Loose piece {@code slot} is gone. */
    void drop(int slot) {
        if (body[slot] != null) {
            scene.removeActor(body[slot]);
            body[slot].release();
            bodyShape[slot].release();
            body[slot] = null;
            bodyShape[slot] = null;
        }
    }

    /** Loose piece {@code slot}'s place into {@code out[0..2]} and its turn (x, y, z, w) into {@code out[3..6]}; false if the slot is empty. */
    boolean pose(int slot, float[] out) {
        PxRigidDynamic b = body[slot];
        if (b == null) {
            return false;
        }
        PxTransform t = b.getGlobalPose();
        PxVec3 at = t.getP();
        PxQuat turn = t.getQ();
        out[0] = at.getX();
        out[1] = at.getY();
        out[2] = at.getZ();
        out[3] = turn.getX();
        out[4] = turn.getY();
        out[5] = turn.getZ();
        out[6] = turn.getW();
        return true;
    }

    /** True if loose piece {@code slot} has come to rest. */
    boolean resting(int slot) {
        return body[slot] != null && body[slot].isSleeping();
    }

    /** The race is over: the scene and all in it are given back. */
    void close() {
        if (scene == null) {
            return;
        }
        for (int i = 0; i < BODIES; i++) {
            drop(i);
        }
        for (int c = 0; c < cars; c++) {
            scene.removeActor(car[c]);
            car[c].release();
            carShape[c].release();
        }
        for (int i = 0; i < fixedCount; i++) {
            scene.removeActor(fixed[i]);
            fixed[i].release();
            fixedShape[i].release();
        }
        fixedCount = 0;
        cars = 0;
        scene.release();
        scene = null;
        v.destroy();
        q.destroy();
        pose.destroy();
    }
}
