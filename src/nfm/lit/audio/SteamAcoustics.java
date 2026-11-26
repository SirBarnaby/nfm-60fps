package nfm.lit.audio;

import static java.lang.foreign.ValueLayout.ADDRESS;
import static java.lang.foreign.ValueLayout.JAVA_FLOAT;
import static java.lang.foreign.ValueLayout.JAVA_INT;
import static java.lang.foreign.ValueLayout.JAVA_LONG;

import fallk.logmaster.HLogger;
import java.lang.foreign.Arena;
import java.lang.foreign.FunctionDescriptor;
import java.lang.foreign.Linker;
import java.lang.foreign.MemoryLayout;
import java.lang.foreign.MemoryLayout.PathElement;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.StructLayout;
import java.lang.foreign.SymbolLookup;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.FileTime;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.List;
import java.util.concurrent.locks.LockSupport;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.stream.Stream;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

/**
 * Sound against the shape of the track, worked out by Steam Audio (Valve,
 * Apache 2.0, {@code lib\dlls\win64\phonon.dll}) through Java's foreign
 * function API: how much of each car's sound gets through the stage to the
 * listener (occlusion, and what passes through a piece, by frequency), and
 * how long the track makes sound ring on where the listener is (reverb
 * times, by frequency), from rays traced against the stage's own pieces.
 * <p>
 * The stage is built into a Steam Audio scene on this class's own thread,
 * which also traces the reflections a few times a second; the sound thread
 * asks for occlusion every few updates, which costs microseconds. Steam
 * Audio's world has y up and is in metres; the game's has y down and is in
 * its own units, so points are turned half a turn about x (keeping the
 * world right-handed) and scaled.
 * <p>
 * Sound also bends over a crest and around the end of a wall. Steam Audio
 * finds those paths between probes it places over the stage, a few metres
 * above the ground: which probes see each other is baked once per stage, on
 * this thread while the race starts, and kept in {@code cache\sound-paths}
 * so a stage is baked only the first time; then every tenth of a second the
 * shortest path from each car to the listener gives how much of the car gets
 * round (by frequency) and the direction it arrives from.
 * <p>
 * Only the parts of the API this needs are bound; each structure's layout
 * is spelled out below as in {@code phonon.h} 4.8 and its size checked, so a
 * library that does not match is refused rather than misread.
 */
final class SteamAcoustics {

    /** A stage's pieces as triangles, in the game's units, each of a kind such as {@link Sound3D#SURFACE_HARD}. */
    record Stage(float[] vertices, int[] triangles, byte[] kinds) {
    }

    /*
     * Absorption (low, mid, high), scattering, transmission (low, mid, high),
     * in the order of Sound3D's SURFACE kinds: Steam Audio's presets for
     * concrete, gravel and glass; foliage has none,
     * so it is soft, scattering and lets most sound through.
     */
    private static final float[][] MATERIALS = {
            {0.05F, 0.07F, 0.08F, 0.05F, 0.015F, 0.002F, 0.001F},
            {0.60F, 0.70F, 0.80F, 0.05F, 0.031F, 0.012F, 0.008F},
            {0.20F, 0.35F, 0.50F, 0.70F, 0.60F, 0.40F, 0.25F},
            {0.06F, 0.03F, 0.02F, 0.05F, 0.060F, 0.044F, 0.011F},
    };

    private static final float UNITS_PER_METRE = 60F;
    private static final int VERSION = (4 << 16) | (8 << 8) | 1;
    private static final int SIMD_AVX2 = 3;
    private static final int SCENE_DEFAULT = 0;
    private static final int REFLECTIONS_PARAMETRIC = 1;
    private static final int SIMULATE_DIRECT = 1;
    private static final int SIMULATE_REFLECTIONS = 2;
    private static final int DIRECT_OCCLUSION = 1 << 3;
    private static final int DIRECT_TRANSMISSION = 1 << 4;
    private static final int OCCLUSION_VOLUMETRIC = 1;
    /** A car is about this wide, so it is not cut off by the edge of a rock. */
    private static final float SOURCE_RADIUS = 1.5F;
    private static final int OCCLUSION_SAMPLES = 16;
    private static final int TRANSMISSION_RAYS = 3;
    private static final int RAYS = 4096;
    private static final int OCCLUSION_RAYCAST = 0;
    /*
     * Reverb times alone do not say how much of the sound comes back: a few
     * rays caught between far walls ring long even in the open. So rays also
     * go out from the listener in sixteen directions, level and up, to see
     * how much of the sky around is closed in by the track within this far.
     */
    private static final float PROBE_DISTANCE = 40F;
    private static final float[][] PROBES = probeDirections();

    private static float[][] probeDirections() {
        float[][] d = new float[16][];
        for (int i = 0; i < 16; i++) {
            double around = Math.PI * 2 * (i % 8) / 8 + (i < 8 ? 0 : Math.PI / 8);
            double up = i < 8 ? 0 : Math.toRadians(35);
            d[i] = new float[] {(float) (Math.cos(around) * Math.cos(up)), (float) Math.sin(up),
                    (float) (Math.sin(around) * Math.cos(up))};
        }
        return d;
    }
    private static final int BOUNCES = 8;
    private static final float DURATION = 2F;
    private static final long REFLECTIONS_EVERY_NANOS = 250_000_000L;

    /*
     * pathing: probes every 16 m, 2 m over the ground, which see each other within 60 m; paths up to 150 m.
     * A 1 x 1.5 km stage has about 4,300 probes, bakes in 5 to 11 s on a quarter of the cores (so the race
     * starting meanwhile keeps its frame rate) and keeps in 2 MB; at 12 m it took 17 s and 10 MB for little a
     * driver would hear.
     */
    private static final int SIMULATE_PATHING = 4;
    private static final int PROBES_UNIFORM_FLOOR = 1;
    private static final int BAKED_PATHING = 1;
    private static final int VARIATION_DYNAMIC = 3;
    private static final float PROBE_SPACING = 16F;
    private static final float PROBE_HEIGHT = 2F;
    private static final float VIS_RADIUS = 1F;
    private static final float VIS_THRESHOLD = 0.1F;
    private static final float VIS_RANGE = 60F;
    private static final float PATH_RANGE = 150F;
    private static final int VIS_SAMPLES = 4;
    private static final long PATHS_EVERY_NANOS = 100_000_000L;
    /** Changes with anything that changes what is baked, so an old cache file is not used. */
    private static final String PATHS_BAKED_WITH = "phonon 4.8 paths v1 16/2/1/0.1/60/150 4";
    /** Where baked paths are kept, one gzipped file per stage named by a hash of its shape. */
    static final Path PATH_CACHE = Path.of("cache", "sound-paths");
    /** The most stages kept; the ones used longest ago go first. */
    private static final int PATH_CACHE_STAGES = 100;

    /* ------------------------------------------------------- layouts */

    private static final MemoryLayout PAD4 = MemoryLayout.paddingLayout(4);
    private static final StructLayout VEC3 = MemoryLayout.structLayout(
            JAVA_FLOAT.withName("x"), JAVA_FLOAT.withName("y"), JAVA_FLOAT.withName("z"));
    private static final StructLayout COORDS = MemoryLayout.structLayout(
            VEC3.withName("right"), VEC3.withName("up"), VEC3.withName("ahead"), VEC3.withName("origin"));
    private static final MemoryLayout BANDS = MemoryLayout.sequenceLayout(3, JAVA_FLOAT);

    private static final StructLayout CONTEXT_SETTINGS = MemoryLayout.structLayout(
            JAVA_INT.withName("version"), PAD4, ADDRESS.withName("log"), ADDRESS.withName("allocate"),
            ADDRESS.withName("free"), JAVA_INT.withName("simdLevel"), JAVA_INT.withName("flags"));
    private static final StructLayout SCENE_SETTINGS = MemoryLayout.structLayout(
            JAVA_INT.withName("type"), PAD4, ADDRESS, ADDRESS, ADDRESS, ADDRESS, ADDRESS, ADDRESS, ADDRESS);
    private static final StructLayout MESH_SETTINGS = MemoryLayout.structLayout(
            JAVA_INT.withName("numVertices"), JAVA_INT.withName("numTriangles"), JAVA_INT.withName("numMaterials"),
            PAD4, ADDRESS.withName("vertices"), ADDRESS.withName("triangles"), ADDRESS.withName("materialIndices"),
            ADDRESS.withName("materials"));
    private static final StructLayout SIMULATION_SETTINGS = MemoryLayout.structLayout(
            JAVA_INT.withName("flags"), JAVA_INT.withName("sceneType"), JAVA_INT.withName("reflectionType"),
            JAVA_INT.withName("maxNumOcclusionSamples"), JAVA_INT.withName("maxNumRays"),
            JAVA_INT.withName("numDiffuseSamples"), JAVA_FLOAT.withName("maxDuration"), JAVA_INT.withName("maxOrder"),
            JAVA_INT.withName("maxNumSources"), JAVA_INT.withName("numThreads"), JAVA_INT.withName("rayBatchSize"),
            JAVA_INT.withName("numVisSamples"), JAVA_INT.withName("samplingRate"), JAVA_INT.withName("frameSize"),
            ADDRESS, ADDRESS, ADDRESS);
    private static final StructLayout INPUTS = MemoryLayout.structLayout(
            JAVA_INT.withName("flags"), JAVA_INT.withName("directFlags"), COORDS.withName("source"),
            MemoryLayout.structLayout(JAVA_INT.withName("type"), JAVA_FLOAT, ADDRESS, ADDRESS, JAVA_INT, PAD4)
                    .withName("distanceAttenuationModel"),
            MemoryLayout.structLayout(JAVA_INT.withName("type"), BANDS, ADDRESS, ADDRESS, JAVA_INT, PAD4)
                    .withName("airAbsorptionModel"),
            MemoryLayout.structLayout(JAVA_FLOAT, JAVA_FLOAT, ADDRESS, ADDRESS).withName("directivity"),
            JAVA_INT.withName("occlusionType"), JAVA_FLOAT.withName("occlusionRadius"),
            JAVA_INT.withName("numOcclusionSamples"), BANDS.withName("reverbScale"),
            JAVA_FLOAT.withName("hybridReverbTransitionTime"), JAVA_FLOAT.withName("hybridReverbOverlapPercent"),
            JAVA_INT.withName("baked"),
            MemoryLayout.structLayout(JAVA_INT, JAVA_INT, VEC3, JAVA_FLOAT).withName("bakedDataIdentifier"),
            PAD4, ADDRESS.withName("pathingProbes"), JAVA_FLOAT.withName("visRadius"),
            JAVA_FLOAT.withName("visThreshold"), JAVA_FLOAT.withName("visRange"), JAVA_INT.withName("pathingOrder"),
            JAVA_INT.withName("enableValidation"), JAVA_INT.withName("findAlternatePaths"),
            JAVA_INT.withName("numTransmissionRays"), PAD4, ADDRESS.withName("deviationModel"));
    private static final StructLayout SHARED_INPUTS = MemoryLayout.structLayout(
            COORDS.withName("listener"), JAVA_INT.withName("numRays"), JAVA_INT.withName("numBounces"),
            JAVA_FLOAT.withName("duration"), JAVA_INT.withName("order"), JAVA_FLOAT.withName("irradianceMinDistance"),
            PAD4, ADDRESS, ADDRESS);
    private static final StructLayout OUTPUTS = MemoryLayout.structLayout(
            MemoryLayout.structLayout(JAVA_INT, JAVA_INT, JAVA_FLOAT, BANDS, JAVA_FLOAT,
                    JAVA_FLOAT.withName("occlusion"), BANDS.withName("transmission")).withName("direct"),
            PAD4,
            MemoryLayout.structLayout(JAVA_INT, PAD4, ADDRESS, BANDS.withName("reverbTimes"), BANDS, JAVA_INT,
                    JAVA_INT, JAVA_INT, PAD4, ADDRESS, JAVA_INT, PAD4).withName("reflections"),
            MemoryLayout.structLayout(BANDS, PAD4, ADDRESS, JAVA_INT, JAVA_INT, ADDRESS, COORDS, JAVA_INT, PAD4)
                    .withName("pathing"));

    private static final long OCCLUSION = OUTPUTS.byteOffset(PathElement.groupElement("direct"),
            PathElement.groupElement("occlusion"));
    private static final long TRANSMISSION = OUTPUTS.byteOffset(PathElement.groupElement("direct"),
            PathElement.groupElement("transmission"));
    private static final long REVERB_TIMES = OUTPUTS.byteOffset(PathElement.groupElement("reflections"),
            PathElement.groupElement("reverbTimes"));
    private static final long PATH_EQ = OUTPUTS.byteOffset(PathElement.groupElement("pathing"));
    private static final long PATH_SH = PATH_EQ + 16;

    private static final StructLayout SERIALIZED_SETTINGS = MemoryLayout.structLayout(
            ADDRESS.withName("data"), JAVA_LONG.withName("size"));
    private static final StructLayout PROBE_GENERATION = MemoryLayout.structLayout(
            JAVA_INT.withName("type"), JAVA_FLOAT.withName("spacing"), JAVA_FLOAT.withName("height"),
            MemoryLayout.sequenceLayout(16, JAVA_FLOAT).withName("transform"));
    private static final StructLayout IDENTIFIER = MemoryLayout.structLayout(
            JAVA_INT.withName("type"), JAVA_INT.withName("variation"), VEC3.withName("center"),
            JAVA_FLOAT.withName("radius"));
    private static final StructLayout PATH_BAKE = MemoryLayout.structLayout(
            ADDRESS.withName("scene"), ADDRESS.withName("probeBatch"), IDENTIFIER.withName("identifier"),
            JAVA_INT.withName("numSamples"), JAVA_FLOAT.withName("radius"), JAVA_FLOAT.withName("threshold"),
            JAVA_FLOAT.withName("visRange"), JAVA_FLOAT.withName("pathRange"), JAVA_INT.withName("numThreads"));
    private static final StructLayout DEVIATION = MemoryLayout.structLayout(
            JAVA_INT.withName("type"), PAD4, ADDRESS.withName("callback"), ADDRESS.withName("userData"));

    private static long at(StructLayout layout, String field) {
        return layout.byteOffset(PathElement.groupElement(field));
    }

    /* ---------------------------------------------------- functions */

    private final MethodHandle contextRelease;
    private final MethodHandle sceneCreate;
    private final MethodHandle sceneRelease;
    private final MethodHandle sceneCommit;
    private final MethodHandle meshCreate;
    private final MethodHandle meshAdd;
    private final MethodHandle meshRelease;
    private final MethodHandle simulatorRelease;
    private final MethodHandle simulatorSetScene;
    private final MethodHandle simulatorCommit;
    private final MethodHandle simulatorSetShared;
    private final MethodHandle simulatorRunDirect;
    private final MethodHandle simulatorRunReflections;
    private final MethodHandle sourceRelease;
    private final MethodHandle sourceSetInputs;
    private final MethodHandle sourceGetOutputs;
    private final MethodHandle probeArrayCreate;
    private final MethodHandle probeArrayRelease;
    private final MethodHandle probeArrayGenerate;
    private final MethodHandle probeBatchCreate;
    private final MethodHandle probeBatchRelease;
    private final MethodHandle probeBatchAddArray;
    private final MethodHandle probeBatchCommit;
    private final MethodHandle probeBatchSave;
    private final MethodHandle probeBatchLoad;
    private final MethodHandle probeBatchCount;
    private final MethodHandle serializedCreate;
    private final MethodHandle serializedRelease;
    private final MethodHandle serializedSize;
    private final MethodHandle serializedData;
    private final MethodHandle pathBake;
    /* the path baker calls its progress callback without checking it is there */
    private final MemorySegment bakeProgress;
    private final MethodHandle simulatorAddProbes;
    private final MethodHandle simulatorRemoveProbes;
    private final MethodHandle simulatorRunPathing;

    private final Arena arena = Arena.ofShared();
    private MemorySegment context = MemorySegment.NULL;
    private MemorySegment simulator = MemorySegment.NULL;
    private MemorySegment scene = MemorySegment.NULL;
    private MemorySegment mesh = MemorySegment.NULL;
    private final MemorySegment[] carSources;
    private final MemorySegment[] probeSources = new MemorySegment[PROBES.length];
    private volatile float enclosure;
    /** How closed in each car is, as {@link #enclosure} is for the listener; one car is looked at each trace, in turn. */
    private final float[] carEnclosure;
    private int nextCar;
    private MemorySegment roomSource = MemorySegment.NULL;
    /* the stage's baked probes, in the simulator while pathsReady; written under the write lock */
    private MemorySegment probeBatch = MemorySegment.NULL;
    private volatile boolean pathsReady;
    private volatile String pathsDescribed = "";
    private volatile long pathNanos;

    /* writing the scene excludes simulating it; the two simulations may run together */
    private final ReentrantReadWriteLock lock = new ReentrantReadWriteLock();
    private volatile boolean ready;
    private volatile Stage pendingStage;
    private volatile boolean running = true;
    private final Thread thread;

    /* the sound thread's structures */
    private final MemorySegment directInputs;
    private final MemorySegment directShared;
    private final MemorySegment directOutputs;
    private final MemorySegment probeInputs;
    private final MemorySegment carShared;
    /* the acoustics thread's */
    private final MemorySegment roomInputs;
    private final MemorySegment roomShared;
    private final MemorySegment roomOutputs;
    private final MemorySegment pathInputs;
    private final MemorySegment pathShared;
    private final MemorySegment pathOutputs;
    /* the cars as the sound thread last traced them, for the paths; guarded by pathCars */
    private final float[] pathCars;
    private final boolean[] pathActive;
    private int pathCount;
    /** Per car: 1 if a path was found, its three bands, and the direction it arrives from in the game's world. */
    private volatile float[] paths;
    static final int PATH_FLOATS = 7;

    /** The listener as Steam Audio has it: right, up, ahead, origin; written by the sound thread. */
    private final float[] listener = new float[12];
    private volatile boolean listenerKnown;
    private final float[] reverb = new float[3];
    private volatile float[] reverbTimes = new float[3];
    private static final float[] IN_STEP_REVERB_TIMES = {0.8F, 0.8F, 0.8F};
    private volatile String described = "Steam Audio: no stage yet";

    /**
     * In step with a race check: no thread of its own and the stage built
     * when asked, so a render comes out the same every time. Occlusion is
     * traced as always, but reflections are not: Steam Audio draws their rays
     * at random, so the reverb times would differ from run to run in the last
     * bit; the reverb rings for a fixed time instead, still as loud as the
     * place is closed in.
     */
    private final boolean inStep;

    private SteamAcoustics(SymbolLookup lib, int cars, boolean inStep) throws Throwable {
        this.inStep = inStep;
        Linker linker = Linker.nativeLinker();
        FunctionDescriptor create = FunctionDescriptor.of(JAVA_INT, ADDRESS, ADDRESS, ADDRESS);
        FunctionDescriptor oneHandle = FunctionDescriptor.ofVoid(ADDRESS);
        FunctionDescriptor twoHandles = FunctionDescriptor.ofVoid(ADDRESS, ADDRESS);
        FunctionDescriptor flagged = FunctionDescriptor.ofVoid(ADDRESS, JAVA_INT, ADDRESS);
        MethodHandle contextCreate = handle(linker, lib, "iplContextCreate",
                FunctionDescriptor.of(JAVA_INT, ADDRESS, ADDRESS));
        contextRelease = handle(linker, lib, "iplContextRelease", oneHandle);
        sceneCreate = handle(linker, lib, "iplSceneCreate", create);
        sceneRelease = handle(linker, lib, "iplSceneRelease", oneHandle);
        sceneCommit = handle(linker, lib, "iplSceneCommit", oneHandle);
        meshCreate = handle(linker, lib, "iplStaticMeshCreate", create);
        meshAdd = handle(linker, lib, "iplStaticMeshAdd", twoHandles);
        meshRelease = handle(linker, lib, "iplStaticMeshRelease", oneHandle);
        MethodHandle simulatorCreate = handle(linker, lib, "iplSimulatorCreate", create);
        simulatorRelease = handle(linker, lib, "iplSimulatorRelease", oneHandle);
        simulatorSetScene = handle(linker, lib, "iplSimulatorSetScene", twoHandles);
        simulatorCommit = handle(linker, lib, "iplSimulatorCommit", oneHandle);
        simulatorSetShared = handle(linker, lib, "iplSimulatorSetSharedInputs", flagged);
        simulatorRunDirect = handle(linker, lib, "iplSimulatorRunDirect", oneHandle);
        simulatorRunReflections = handle(linker, lib, "iplSimulatorRunReflections", oneHandle);
        MethodHandle sourceCreate = handle(linker, lib, "iplSourceCreate", create);
        sourceRelease = handle(linker, lib, "iplSourceRelease", oneHandle);
        MethodHandle sourceAdd = handle(linker, lib, "iplSourceAdd", twoHandles);
        sourceSetInputs = handle(linker, lib, "iplSourceSetInputs", flagged);
        sourceGetOutputs = handle(linker, lib, "iplSourceGetOutputs", flagged);
        probeArrayCreate = handle(linker, lib, "iplProbeArrayCreate", FunctionDescriptor.of(JAVA_INT, ADDRESS, ADDRESS));
        probeArrayRelease = handle(linker, lib, "iplProbeArrayRelease", oneHandle);
        probeArrayGenerate = handle(linker, lib, "iplProbeArrayGenerateProbes",
                FunctionDescriptor.ofVoid(ADDRESS, ADDRESS, ADDRESS));
        probeBatchCreate = handle(linker, lib, "iplProbeBatchCreate", FunctionDescriptor.of(JAVA_INT, ADDRESS, ADDRESS));
        probeBatchRelease = handle(linker, lib, "iplProbeBatchRelease", oneHandle);
        probeBatchAddArray = handle(linker, lib, "iplProbeBatchAddProbeArray", twoHandles);
        probeBatchCommit = handle(linker, lib, "iplProbeBatchCommit", oneHandle);
        probeBatchSave = handle(linker, lib, "iplProbeBatchSave", twoHandles);
        probeBatchLoad = handle(linker, lib, "iplProbeBatchLoad", create);
        probeBatchCount = handle(linker, lib, "iplProbeBatchGetNumProbes", FunctionDescriptor.of(JAVA_INT, ADDRESS));
        serializedCreate = handle(linker, lib, "iplSerializedObjectCreate", create);
        serializedRelease = handle(linker, lib, "iplSerializedObjectRelease", oneHandle);
        serializedSize = handle(linker, lib, "iplSerializedObjectGetSize", FunctionDescriptor.of(JAVA_LONG, ADDRESS));
        serializedData = handle(linker, lib, "iplSerializedObjectGetData", FunctionDescriptor.of(ADDRESS, ADDRESS));
        pathBake = handle(linker, lib, "iplPathBakerBake", FunctionDescriptor.ofVoid(ADDRESS, ADDRESS, ADDRESS, ADDRESS));
        bakeProgress = linker.upcallStub(MethodHandles.lookup().findStatic(SteamAcoustics.class, "bakeProgress",
                MethodType.methodType(void.class, float.class, MemorySegment.class)),
                FunctionDescriptor.ofVoid(JAVA_FLOAT, ADDRESS), arena);
        simulatorAddProbes = handle(linker, lib, "iplSimulatorAddProbeBatch", twoHandles);
        simulatorRemoveProbes = handle(linker, lib, "iplSimulatorRemoveProbeBatch", twoHandles);
        simulatorRunPathing = handle(linker, lib, "iplSimulatorRunPathing", oneHandle);

        MemorySegment out = arena.allocate(ADDRESS);
        MemorySegment settings = arena.allocate(CONTEXT_SETTINGS);
        settings.set(JAVA_INT, at(CONTEXT_SETTINGS, "version"), VERSION);
        settings.set(JAVA_INT, at(CONTEXT_SETTINGS, "simdLevel"), SIMD_AVX2);
        check((int) contextCreate.invokeExact(settings, out), "iplContextCreate");
        context = out.get(ADDRESS, 0);

        MemorySegment sim = arena.allocate(SIMULATION_SETTINGS);
        // in step, no paths: they are found by rays drawn at random, like the reflections
        sim.set(JAVA_INT, at(SIMULATION_SETTINGS, "flags"),
                SIMULATE_DIRECT | SIMULATE_REFLECTIONS | (inStep ? 0 : SIMULATE_PATHING));
        sim.set(JAVA_INT, at(SIMULATION_SETTINGS, "sceneType"), SCENE_DEFAULT);
        sim.set(JAVA_INT, at(SIMULATION_SETTINGS, "reflectionType"), REFLECTIONS_PARAMETRIC);
        sim.set(JAVA_INT, at(SIMULATION_SETTINGS, "maxNumOcclusionSamples"), OCCLUSION_SAMPLES);
        sim.set(JAVA_INT, at(SIMULATION_SETTINGS, "maxNumRays"), RAYS);
        sim.set(JAVA_INT, at(SIMULATION_SETTINGS, "numDiffuseSamples"), 32);
        sim.set(JAVA_FLOAT, at(SIMULATION_SETTINGS, "maxDuration"), DURATION);
        sim.set(JAVA_INT, at(SIMULATION_SETTINGS, "maxOrder"), inStep ? 0 : 1); // paths come with a first-order direction
        sim.set(JAVA_INT, at(SIMULATION_SETTINGS, "maxNumSources"), cars + PROBES.length + 1);
        sim.set(JAVA_INT, at(SIMULATION_SETTINGS, "numThreads"), inStep ? 1 : Math.max(1,
                Math.min(2, Runtime.getRuntime().availableProcessors() / 4)));
        sim.set(JAVA_INT, at(SIMULATION_SETTINGS, "rayBatchSize"), 1);
        sim.set(JAVA_INT, at(SIMULATION_SETTINGS, "numVisSamples"), VIS_SAMPLES);
        sim.set(JAVA_INT, at(SIMULATION_SETTINGS, "samplingRate"), 48_000);
        sim.set(JAVA_INT, at(SIMULATION_SETTINGS, "frameSize"), 1024);
        check((int) simulatorCreate.invokeExact(context, sim, out), "iplSimulatorCreate");
        simulator = out.get(ADDRESS, 0);

        MemorySegment sourceSettings = arena.allocate(JAVA_INT);
        carSources = new MemorySegment[cars];
        sourceSettings.set(JAVA_INT, 0, SIMULATE_DIRECT | (inStep ? 0 : SIMULATE_PATHING));
        for (int i = 0; i < cars; i++) {
            check((int) sourceCreate.invokeExact(simulator, sourceSettings, out), "iplSourceCreate");
            carSources[i] = out.get(ADDRESS, 0);
            sourceAdd.invokeExact(carSources[i], simulator);
        }
        sourceSettings.set(JAVA_INT, 0, SIMULATE_DIRECT);
        for (int i = 0; i < PROBES.length; i++) {
            check((int) sourceCreate.invokeExact(simulator, sourceSettings, out), "iplSourceCreate");
            probeSources[i] = out.get(ADDRESS, 0);
            sourceAdd.invokeExact(probeSources[i], simulator);
        }
        sourceSettings.set(JAVA_INT, 0, SIMULATE_REFLECTIONS);
        check((int) sourceCreate.invokeExact(simulator, sourceSettings, out), "iplSourceCreate");
        roomSource = out.get(ADDRESS, 0);
        sourceAdd.invokeExact(roomSource, simulator);
        simulatorCommit.invokeExact(simulator);

        directInputs = inputs(SIMULATE_DIRECT, DIRECT_OCCLUSION | DIRECT_TRANSMISSION);
        probeInputs = inputs(SIMULATE_DIRECT, DIRECT_OCCLUSION);
        probeInputs.set(JAVA_INT, at(INPUTS, "occlusionType"), OCCLUSION_RAYCAST);
        directShared = arena.allocate(SHARED_INPUTS);
        carShared = arena.allocate(SHARED_INPUTS);
        carEnclosure = new float[cars];
        directOutputs = arena.allocate(OUTPUTS);
        roomInputs = inputs(SIMULATE_REFLECTIONS, 0);
        roomShared = arena.allocate(SHARED_INPUTS);
        roomOutputs = arena.allocate(OUTPUTS);
        pathInputs = inputs(SIMULATE_PATHING, 0);
        identityAxes(pathInputs);
        long id = at(INPUTS, "bakedDataIdentifier");
        pathInputs.set(JAVA_INT, id, BAKED_PATHING);
        pathInputs.set(JAVA_INT, id + 4, VARIATION_DYNAMIC);
        pathInputs.set(JAVA_FLOAT, at(INPUTS, "visRadius"), VIS_RADIUS);
        pathInputs.set(JAVA_FLOAT, at(INPUTS, "visThreshold"), VIS_THRESHOLD);
        pathInputs.set(JAVA_FLOAT, at(INPUTS, "visRange"), VIS_RANGE);
        pathInputs.set(JAVA_INT, at(INPUTS, "pathingOrder"), 1);
        // the stage does not move, so the baked visibility holds and the paths need no checking again
        // (and Steam Audio 4.8 crashes checking them)
        pathInputs.set(JAVA_INT, at(INPUTS, "enableValidation"), 0);
        pathInputs.set(JAVA_INT, at(INPUTS, "findAlternatePaths"), 0);
        MemorySegment deviation = arena.allocate(DEVIATION); // the default: Uniform Theory of Diffraction
        pathInputs.set(ADDRESS, at(INPUTS, "deviationModel"), deviation);
        pathShared = arena.allocate(SHARED_INPUTS);
        pathOutputs = arena.allocate(OUTPUTS);
        pathCars = new float[3 * cars];
        pathActive = new boolean[cars];
        paths = new float[PATH_FLOATS * cars];
        for (MemorySegment shared : new MemorySegment[] {directShared, carShared, roomShared}) {
            shared.set(JAVA_INT, at(SHARED_INPUTS, "numRays"), RAYS);
            shared.set(JAVA_INT, at(SHARED_INPUTS, "numBounces"), BOUNCES);
            shared.set(JAVA_FLOAT, at(SHARED_INPUTS, "duration"), DURATION);
            shared.set(JAVA_FLOAT, at(SHARED_INPUTS, "irradianceMinDistance"), 1F);
        }

        thread = new Thread(this::work, "nfm-acoustics");
        thread.setDaemon(true);
        thread.setPriority(Thread.MIN_PRIORITY + 1);
        if (!inStep) {
            thread.start();
        }
    }

    private MemorySegment inputs(int flags, int directFlags) {
        MemorySegment in = arena.allocate(INPUTS);
        in.set(JAVA_INT, at(INPUTS, "flags"), flags);
        in.set(JAVA_INT, at(INPUTS, "directFlags"), directFlags);
        in.set(JAVA_INT, at(INPUTS, "occlusionType"), OCCLUSION_VOLUMETRIC);
        in.set(JAVA_FLOAT, at(INPUTS, "occlusionRadius"), SOURCE_RADIUS);
        in.set(JAVA_INT, at(INPUTS, "numOcclusionSamples"), OCCLUSION_SAMPLES);
        long scale = at(INPUTS, "reverbScale");
        for (int b = 0; b < 3; b++) {
            in.set(JAVA_FLOAT, scale + 4L * b, 1F);
        }
        in.set(JAVA_INT, at(INPUTS, "numTransmissionRays"), TRANSMISSION_RAYS);
        return in;
    }

    private static MethodHandle handle(Linker linker, SymbolLookup lib, String name, FunctionDescriptor fd) {
        return linker.downcallHandle(lib.find(name).orElseThrow(
                () -> new IllegalStateException(name + " is missing")), fd);
    }

    private static void check(int error, String what) {
        if (error != 0) {
            throw new IllegalStateException(what + " failed (" + error + ")");
        }
    }

    /**
     * Loads Steam Audio for this many cars; null if the library is not there
     * or does not start, and then the sound goes on without it.
     */
    static SteamAcoustics open(int cars, boolean inStep) {
        Path dll = Path.of(System.getProperty("nfm.steamaudio", "lib/dlls/win64/phonon.dll"));
        if (!Files.isRegularFile(dll)) {
            HLogger.info("3D sound: no Steam Audio library (" + dll + "), so no muffling or reverb from the track");
            return null;
        }
        try {
            if (CONTEXT_SETTINGS.byteSize() != 40 || SCENE_SETTINGS.byteSize() != 64
                    || MESH_SETTINGS.byteSize() != 48 || SIMULATION_SETTINGS.byteSize() != 80
                    || INPUTS.byteSize() != 264 || SHARED_INPUTS.byteSize() != 88 || OUTPUTS.byteSize() != 216
                || PROBE_GENERATION.byteSize() != 76 || PATH_BAKE.byteSize() != 64 || IDENTIFIER.byteSize() != 24
                || SERIALIZED_SETTINGS.byteSize() != 16) {
                throw new IllegalStateException("structure sizes do not match phonon.h 4.8");
            }
            return new SteamAcoustics(SymbolLookup.libraryLookup(dll, Arena.global()), cars, inStep);
        } catch (Throwable ex) {
            HLogger.warn("3D sound: Steam Audio would not start, so no muffling or reverb from the track: " + ex);
            return null;
        }
    }

    /** Builds this stage on the acoustics thread; the newest one asked for wins. */
    void stage(Stage stage) {
        if (inStep) {
            build(stage);
            return;
        }
        pendingStage = stage;
        LockSupport.unpark(thread);
    }

    String describe() {
        return described + pathsDescribed + String.format(", reflections %.1f ms, occlusion %.2f ms, paths %.2f ms",
                roomNanos / 1e6, directNanos / 1e6, pathNanos / 1e6);
    }

    /**
     * The paths from the cars to the listener, {@link #PATH_FLOATS} per car:
     * 1 if one was found (else 0 and the rest is to be ignored), how much of
     * the car gets round by band (low, mid, high), and the direction the
     * sound arrives from, a unit vector in the game's world. Null when there
     * are no paths (no probes yet, or in step).
     */
    float[] paths() {
        return pathsReady ? paths : null;
    }

    private volatile long roomNanos;
    private volatile long directNanos;

    /** The reverb times (low, mid, high, in seconds) where the listener is, from the last reflections. */
    float[] reverbTimes() {
        return inStep ? IN_STEP_REVERB_TIMES : reverbTimes;
    }

    /**
     * Sets the listener from the game's camera: where it is and how it is
     * turned (the same turns as the renderer's, yaw then pitch, in radians'
     * sines and cosines).
     */
    void listener(float x, float y, float z, float yawSin, float yawCos, float pitchSin, float pitchCos) {
        synchronized (listener) {
            // the camera's right, up (the game's y is down) and ahead in the game's world, into Steam Audio's
            put(0, yawCos, 0F, -yawSin);
            put(3, yawSin * pitchSin, -pitchCos, yawCos * pitchSin);
            put(6, yawSin * pitchCos, pitchSin, yawCos * pitchCos);
            listener[9] = x / UNITS_PER_METRE;
            listener[10] = -y / UNITS_PER_METRE;
            listener[11] = -z / UNITS_PER_METRE;
        }
        listenerKnown = true;
    }

    /** A direction in the game's world, into Steam Audio's (y and z flipped). */
    private void put(int at, float x, float y, float z) {
        listener[at] = x;
        listener[at + 1] = -y;
        listener[at + 2] = -z;
    }

    /**
     * How much of each car's sound reaches the listener through the stage,
     * by band (low, mid, high: 1 clear, 0 cut off), into {@code through}
     * (three per car). False while there is no stage to trace against, or
     * it is being rebuilt; {@code through} is then left as it was.
     *
     * @param cars x, y, z of each car in the game's world; {@code active[i]} false skips car i
     * @param around the cars whose surroundings are looked at ({@link #enclosure(int)}), one each call
     */
    boolean direct(float[] cars, boolean[] active, boolean[] around, int count, float[] through) {
        if (!ready || !listenerKnown || !lock.readLock().tryLock()) {
            return false;
        }
        try {
            if (!ready) {
                return false;
            }
            long started = System.nanoTime();
            int n = Math.min(count, carSources.length);
            writeListener(directShared);
            long origin = at(INPUTS, "source") + COORDS.byteOffset(PathElement.groupElement("origin"));
            identityAxes(directInputs);
            for (int i = 0; i < n; i++) {
                if (!active[i]) {
                    continue;
                }
                // half a metre above the car's middle, clear of the road it sits on
                directInputs.set(JAVA_FLOAT, origin, cars[3 * i] / UNITS_PER_METRE);
                directInputs.set(JAVA_FLOAT, origin + 4, -(cars[3 * i + 1] - 30F) / UNITS_PER_METRE);
                directInputs.set(JAVA_FLOAT, origin + 8, -cars[3 * i + 2] / UNITS_PER_METRE);
                sourceSetInputs.invokeExact(carSources[i], SIMULATE_DIRECT, directInputs);
            }
            setProbes(origin);
            synchronized (pathCars) {
                System.arraycopy(cars, 0, pathCars, 0, 3 * n);
                System.arraycopy(active, 0, pathActive, 0, n);
                pathCount = n;
            }
            simulatorSetShared.invokeExact(simulator, SIMULATE_DIRECT, directShared);
            simulatorRunDirect.invokeExact(simulator);
            float closed = 0F;
            for (MemorySegment probe : probeSources) {
                sourceGetOutputs.invokeExact(probe, SIMULATE_DIRECT, directOutputs);
                closed += 1F - directOutputs.get(JAVA_FLOAT, OCCLUSION);
            }
            enclosure = closed / probeSources.length;

            for (int i = 0; i < n; i++) {
                if (!active[i]) {
                    continue;
                }
                sourceGetOutputs.invokeExact(carSources[i], SIMULATE_DIRECT, directOutputs);
                float occlusion = directOutputs.get(JAVA_FLOAT, OCCLUSION);
                for (int b = 0; b < 3; b++) {
                    float t = directOutputs.get(JAVA_FLOAT, TRANSMISSION + 4L * b);
                    through[3 * i + b] = occlusion + (1F - occlusion) * t;
                }
            }
            around(cars, around, n, origin);
            directNanos = System.nanoTime() - started;
            return true;
        } catch (Throwable ex) {
            fail(ex);
            return false;
        } finally {
            lock.readLock().unlock();
        }
    }

    /**
     * How closed in one car is by the track, the next in turn each trace:
     * the same sixteen rays as the listener's, from the car, by tracing once
     * more with the car as the listener and the probes around it. The cars'
     * own sources are traced again too and not read.
     */
    private void around(float[] cars, boolean[] active, int n, long origin) throws Throwable {
        for (int tried = 0; tried < n; tried++) {
            int c = nextCar;
            nextCar = (nextCar + 1) % n;
            if (!active[c]) {
                carEnclosure[c] = 0F;
                continue;
            }
            float x = cars[3 * c] / UNITS_PER_METRE;
            float y = -(cars[3 * c + 1] - 30F) / UNITS_PER_METRE;
            float z = -cars[3 * c + 2] / UNITS_PER_METRE;
            writeListener(carShared);
            long at = at(SHARED_INPUTS, "listener") + 4L * 9;
            carShared.set(JAVA_FLOAT, at, x);
            carShared.set(JAVA_FLOAT, at + 4, y);
            carShared.set(JAVA_FLOAT, at + 8, z);
            setProbes(origin, x, y, z);
            simulatorSetShared.invokeExact(simulator, SIMULATE_DIRECT, carShared);
            simulatorRunDirect.invokeExact(simulator);
            float closed = 0F;
            for (MemorySegment probe : probeSources) {
                sourceGetOutputs.invokeExact(probe, SIMULATE_DIRECT, directOutputs);
                closed += 1F - directOutputs.get(JAVA_FLOAT, OCCLUSION);
            }
            carEnclosure[c] = closed / probeSources.length;
            return;
        }
    }

    /** How closed in car {@code car} is by the track, 0 in the open to 1 all round; each car's is looked at in turn. */
    float enclosure(int car) {
        return car >= 0 && car < carEnclosure.length ? carEnclosure[car] : 0F;
    }

    /** Puts the probes around the listener. */
    private void setProbes(long origin) throws Throwable {
        float x;
        float y;
        float z;
        synchronized (listener) {
            x = listener[9];
            y = listener[10];
            z = listener[11];
        }
        setProbes(origin, x, y, z);
    }

    /** Puts the probes around a place (Steam Audio's coordinates). */
    private void setProbes(long origin, float x, float y, float z) throws Throwable {
        identityAxes(probeInputs);
        for (int i = 0; i < PROBES.length; i++) {
            probeInputs.set(JAVA_FLOAT, origin, x + PROBES[i][0] * PROBE_DISTANCE);
            probeInputs.set(JAVA_FLOAT, origin + 4, y + PROBES[i][1] * PROBE_DISTANCE);
            probeInputs.set(JAVA_FLOAT, origin + 8, z + PROBES[i][2] * PROBE_DISTANCE);
            sourceSetInputs.invokeExact(probeSources[i], SIMULATE_DIRECT, probeInputs);
        }
    }

    /** How closed in the listener is by the track, 0 in the open to 1 all round. */
    float enclosure() {
        return enclosure;
    }

    private void writeListener(MemorySegment shared) {
        long base = at(SHARED_INPUTS, "listener");
        synchronized (listener) {
            for (int i = 0; i < 12; i++) {
                shared.set(JAVA_FLOAT, base + 4L * i, listener[i]);
            }
        }
    }

    /** Sources face nowhere in particular: their axes are the world's. */
    private static void identityAxes(MemorySegment in) {
        long base = at(INPUTS, "source");
        float[] axes = {1F, 0F, 0F, 0F, 1F, 0F, 0F, 0F, -1F};
        for (int i = 0; i < axes.length; i++) {
            in.set(JAVA_FLOAT, base + 4L * i, axes[i]);
        }
    }

    private void fail(Throwable ex) {
        HLogger.warn("3D sound: Steam Audio stopped: " + ex);
        ready = false;
        running = false;
        described = "Steam Audio stopped";
    }

    /* ------------------------------------------- the acoustics thread */

    private void work() {
        long nextRoom = 0L;
        long nextPaths = 0L;
        while (running) {
            Stage stage = pendingStage;
            if (stage != null) {
                pendingStage = null;
                build(stage);
                if (ready && pendingStage == null) {
                    preparePaths(stage);
                }
                nextRoom = 0L;
            }
            long now = System.nanoTime();
            if (ready && listenerKnown && now >= nextRoom) {
                room();
                nextRoom = now + REFLECTIONS_EVERY_NANOS;
            }
            if (pathsReady && listenerKnown && now >= nextPaths) {
                findPaths();
                nextPaths = now + PATHS_EVERY_NANOS;
            }
            long next = pathsReady ? Math.min(nextRoom, nextPaths) : nextRoom;
            LockSupport.parkNanos(this, Math.max(1_000_000L, next - System.nanoTime()));
        }
        releaseAll();
    }

    private void releaseAll() {
        lock.writeLock().lock();
        try {
            ready = false;
            dropPaths();
            releaseScene();
            for (MemorySegment s : carSources) {
                release(sourceRelease, s);
            }
            for (MemorySegment s : probeSources) {
                release(sourceRelease, s);
            }
            release(sourceRelease, roomSource);
            release(simulatorRelease, simulator);
            release(contextRelease, context);
        } finally {
            lock.writeLock().unlock();
        }
        arena.close();
    }

    /** Turns the stage into a Steam Audio scene and hands it to the simulator. */
    private void build(Stage stage) {
        long started = System.nanoTime();
        int vertices = stage.vertices().length / 3;
        int triangles = stage.triangles().length / 3;
        try (Arena a = Arena.ofConfined()) {
            MemorySegment v = a.allocate(VEC3, vertices);
            for (int i = 0; i < vertices; i++) {
                v.set(JAVA_FLOAT, 12L * i, stage.vertices()[3 * i] / UNITS_PER_METRE);
                v.set(JAVA_FLOAT, 12L * i + 4, -stage.vertices()[3 * i + 1] / UNITS_PER_METRE);
                v.set(JAVA_FLOAT, 12L * i + 8, -stage.vertices()[3 * i + 2] / UNITS_PER_METRE);
            }
            MemorySegment t = a.allocateFrom(JAVA_INT, stage.triangles());
            MemorySegment kinds = a.allocate(JAVA_INT, triangles);
            for (int i = 0; i < triangles; i++) {
                kinds.setAtIndex(JAVA_INT, i, stage.kinds()[i]);
            }
            MemorySegment materials = a.allocate(28L * MATERIALS.length, 4);
            for (int m = 0; m < MATERIALS.length; m++) {
                for (int k = 0; k < 7; k++) {
                    materials.set(JAVA_FLOAT, 28L * m + 4L * k, MATERIALS[m][k]);
                }
            }
            MemorySegment settings = a.allocate(MESH_SETTINGS);
            settings.set(JAVA_INT, at(MESH_SETTINGS, "numVertices"), vertices);
            settings.set(JAVA_INT, at(MESH_SETTINGS, "numTriangles"), triangles);
            settings.set(JAVA_INT, at(MESH_SETTINGS, "numMaterials"), MATERIALS.length);
            settings.set(ADDRESS, at(MESH_SETTINGS, "vertices"), v);
            settings.set(ADDRESS, at(MESH_SETTINGS, "triangles"), t);
            settings.set(ADDRESS, at(MESH_SETTINGS, "materialIndices"), kinds);
            settings.set(ADDRESS, at(MESH_SETTINGS, "materials"), materials);
            MemorySegment sceneSettings = a.allocate(SCENE_SETTINGS);
            sceneSettings.set(JAVA_INT, at(SCENE_SETTINGS, "type"), SCENE_DEFAULT);
            MemorySegment out = a.allocate(ADDRESS);

            check((int) sceneCreate.invokeExact(context, sceneSettings, out), "iplSceneCreate");
            MemorySegment newScene = out.get(ADDRESS, 0);
            check((int) meshCreate.invokeExact(newScene, settings, out), "iplStaticMeshCreate");
            MemorySegment newMesh = out.get(ADDRESS, 0);
            meshAdd.invokeExact(newMesh, newScene);
            sceneCommit.invokeExact(newScene);

            lock.writeLock().lock();
            try {
                dropPaths();
                releaseScene();
                scene = newScene;
                mesh = newMesh;
                simulatorSetScene.invokeExact(simulator, scene);
                simulatorCommit.invokeExact(simulator);
                ready = true;
            } finally {
                lock.writeLock().unlock();
            }
            described = String.format("Steam Audio: %d triangles, built in %d ms", triangles,
                    (System.nanoTime() - started) / 1_000_000L);
        } catch (Throwable ex) {
            fail(ex);
        }
    }

    /** Traces the reflections around the listener and keeps the reverb times they give. */
    private void room() {
        lock.readLock().lock();
        try {
            if (!ready) {
                return;
            }
            long started = System.nanoTime();
            writeListener(roomShared);
            long source = at(INPUTS, "source");
            long from = at(SHARED_INPUTS, "listener");
            for (int i = 0; i < 12; i++) {
                roomInputs.set(JAVA_FLOAT, source + 4L * i, roomShared.get(JAVA_FLOAT, from + 4L * i));
            }
            sourceSetInputs.invokeExact(roomSource, SIMULATE_REFLECTIONS, roomInputs);
            simulatorSetShared.invokeExact(simulator, SIMULATE_REFLECTIONS, roomShared);
            simulatorRunReflections.invokeExact(simulator);
            sourceGetOutputs.invokeExact(roomSource, SIMULATE_REFLECTIONS, roomOutputs);
            for (int b = 0; b < 3; b++) {
                float rt = roomOutputs.get(JAVA_FLOAT, REVERB_TIMES + 4L * b);
                reverb[b] = Float.isFinite(rt) ? Math.max(0F, rt) : 0F;
            }
            reverbTimes = reverb.clone();
            roomNanos = System.nanoTime() - started;
        } catch (Throwable ex) {
            fail(ex);
        } finally {
            lock.readLock().unlock();
        }
    }

    /* ------------------------------------------------------- pathing */

    /** Takes the probes out of the simulator and lets go of them; under the write lock. */
    private void dropPaths() {
        pathsReady = false;
        pathsDescribed = "";
        if (probeBatch.equals(MemorySegment.NULL)) {
            return;
        }
        try {
            simulatorRemoveProbes.invokeExact(probeBatch, simulator);
            simulatorCommit.invokeExact(simulator);
        } catch (Throwable ex) {
            HLogger.warn("3D sound: Steam Audio could not take the sound paths out: " + ex);
        }
        release(probeBatchRelease, probeBatch);
        probeBatch = MemorySegment.NULL;
    }

    /**
     * Gives the simulator the stage's probes, with the paths between them:
     * from the cache when this stage was baked before, else baked now and
     * kept. On the acoustics thread, after {@link #build}.
     */
    private void preparePaths(Stage stage) {
        long started = System.nanoTime();
        String key = cacheKey(stage);
        Path file = PATH_CACHE.resolve(key + ".probes.gz");
        try (Arena a = Arena.ofConfined()) {
            MemorySegment batch = MemorySegment.NULL;
            boolean cached = false;
            if (Files.isRegularFile(file)) {
                batch = loadProbes(a, readKept(file));
                cached = !batch.equals(MemorySegment.NULL);
            }
            if (!cached) {
                batch = bakeProbes(a, stage);
                if (pendingStage != null) { // another stage is wanted: this one is no use now
                    release(probeBatchRelease, batch);
                    return;
                }
                saveProbes(a, batch, file);
            }
            int probes = (int) probeBatchCount.invokeExact(batch);
            lock.writeLock().lock();
            try {
                if (pendingStage != null || !ready) {
                    release(probeBatchRelease, batch);
                    return;
                }
                probeBatch = batch;
                simulatorAddProbes.invokeExact(probeBatch, simulator);
                simulatorCommit.invokeExact(simulator);
                pathInputs.set(ADDRESS, at(INPUTS, "pathingProbes"), probeBatch);
                pathsReady = probes > 0;
            } finally {
                lock.writeLock().unlock();
            }
            pathsDescribed = String.format(", %d path probes %s in %.1f s", probes, cached ? "loaded" : "baked",
                    (System.nanoTime() - started) / 1e9);
            HLogger.info("3D sound: " + probes + " probes for sound paths " + (cached ? "loaded from " + file
                    : "baked and kept in " + file) + String.format(" in %.1f s", (System.nanoTime() - started) / 1e9));
        } catch (Throwable ex) {
            HLogger.warn("3D sound: no sound paths for this stage, cars behind hills are only muffled: " + ex);
            pathsDescribed = ", no paths";
        }
    }

    /** Places probes over the stage's ground and bakes which of them see each other. */
    private MemorySegment bakeProbes(Arena a, Stage stage) throws Throwable {
        MemorySegment out = a.allocate(ADDRESS);
        check((int) probeArrayCreate.invokeExact(context, out), "iplProbeArrayCreate");
        MemorySegment array = out.get(ADDRESS, 0);
        try {
            MemorySegment params = a.allocate(PROBE_GENERATION);
            params.set(JAVA_INT, at(PROBE_GENERATION, "type"), PROBES_UNIFORM_FLOOR);
            params.set(JAVA_FLOAT, at(PROBE_GENERATION, "spacing"), PROBE_SPACING);
            params.set(JAVA_FLOAT, at(PROBE_GENERATION, "height"), PROBE_HEIGHT);
            float[] box = bounds(stage);
            // row-major: the unit cube scaled to the stage's box and moved to its lowest corner
            long m = at(PROBE_GENERATION, "transform");
            for (int axis = 0; axis < 3; axis++) {
                params.set(JAVA_FLOAT, m + 4L * (5 * axis), box[3 + axis] - box[axis]);
                params.set(JAVA_FLOAT, m + 4L * (12 + axis), box[axis]);
            }
            params.set(JAVA_FLOAT, m + 4L * 15, 1F);
            probeArrayGenerate.invokeExact(array, scene, params);
            check((int) probeBatchCreate.invokeExact(context, out), "iplProbeBatchCreate");
            MemorySegment batch = out.get(ADDRESS, 0);
            probeBatchAddArray.invokeExact(batch, array);
            probeBatchCommit.invokeExact(batch);
            MemorySegment bake = a.allocate(PATH_BAKE);
            bake.set(ADDRESS, at(PATH_BAKE, "scene"), scene);
            bake.set(ADDRESS, at(PATH_BAKE, "probeBatch"), batch);
            long id = at(PATH_BAKE, "identifier");
            bake.set(JAVA_INT, id, BAKED_PATHING);
            bake.set(JAVA_INT, id + 4, VARIATION_DYNAMIC);
            bake.set(JAVA_INT, at(PATH_BAKE, "numSamples"), VIS_SAMPLES);
            bake.set(JAVA_FLOAT, at(PATH_BAKE, "radius"), VIS_RADIUS);
            bake.set(JAVA_FLOAT, at(PATH_BAKE, "threshold"), VIS_THRESHOLD);
            bake.set(JAVA_FLOAT, at(PATH_BAKE, "visRange"), VIS_RANGE);
            bake.set(JAVA_FLOAT, at(PATH_BAKE, "pathRange"), PATH_RANGE);
            bake.set(JAVA_INT, at(PATH_BAKE, "numThreads"),
                    Math.max(1, Runtime.getRuntime().availableProcessors() / 4));
            pathBake.invokeExact(context, bake, bakeProgress, MemorySegment.NULL);
            probeBatchCommit.invokeExact(batch);
            return batch;
        } finally {
            release(probeArrayRelease, array);
        }
    }

    private static void bakeProgress(float progress, MemorySegment userData) {
        // nothing to show: the bake runs while the race starts
    }

    /** The stage's box in Steam Audio's world: lowest x, y, z, then highest, a little above its top. */
    private static float[] bounds(Stage stage) {
        float[] box = {Float.MAX_VALUE, Float.MAX_VALUE, Float.MAX_VALUE, -Float.MAX_VALUE, -Float.MAX_VALUE,
                -Float.MAX_VALUE};
        float[] v = stage.vertices();
        for (int i = 0; i + 2 < v.length; i += 3) {
            float[] p = {v[i] / UNITS_PER_METRE, -v[i + 1] / UNITS_PER_METRE, -v[i + 2] / UNITS_PER_METRE};
            for (int axis = 0; axis < 3; axis++) {
                box[axis] = Math.min(box[axis], p[axis]);
                box[3 + axis] = Math.max(box[3 + axis], p[axis]);
            }
        }
        box[4] += PROBE_HEIGHT * 4F;
        return box;
    }

    private MemorySegment loadProbes(Arena a, byte[] bytes) {
        try {
            MemorySegment data = a.allocateFrom(java.lang.foreign.ValueLayout.JAVA_BYTE, bytes);
            MemorySegment settings = a.allocate(SERIALIZED_SETTINGS);
            settings.set(ADDRESS, at(SERIALIZED_SETTINGS, "data"), data);
            settings.set(JAVA_LONG, at(SERIALIZED_SETTINGS, "size"), bytes.length);
            MemorySegment out = a.allocate(ADDRESS);
            check((int) serializedCreate.invokeExact(context, settings, out), "iplSerializedObjectCreate");
            MemorySegment serialized = out.get(ADDRESS, 0);
            try {
                check((int) probeBatchLoad.invokeExact(context, serialized, out), "iplProbeBatchLoad");
                MemorySegment batch = out.get(ADDRESS, 0);
                probeBatchCommit.invokeExact(batch);
                return batch;
            } finally {
                release(serializedRelease, serialized);
            }
        } catch (Throwable ex) {
            HLogger.warn("3D sound: the kept sound paths could not be read, baking them again: " + ex);
            return MemorySegment.NULL;
        }
    }

    private void saveProbes(Arena a, MemorySegment batch, Path file) {
        try {
            MemorySegment settings = a.allocate(SERIALIZED_SETTINGS);
            MemorySegment out = a.allocate(ADDRESS);
            check((int) serializedCreate.invokeExact(context, settings, out), "iplSerializedObjectCreate");
            MemorySegment serialized = out.get(ADDRESS, 0);
            try {
                probeBatchSave.invokeExact(batch, serialized);
                long size = (long) serializedSize.invokeExact(serialized);
                MemorySegment data = ((MemorySegment) serializedData.invokeExact(serialized)).reinterpret(size);
                Files.createDirectories(file.getParent());
                Path part = file.resolveSibling(file.getFileName() + ".part");
                try (OutputStream zipped = new GZIPOutputStream(Files.newOutputStream(part), 1 << 16)) {
                    zipped.write(data.toArray(java.lang.foreign.ValueLayout.JAVA_BYTE));
                }
                Files.move(part, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
                forgetOldStages();
            } finally {
                release(serializedRelease, serialized);
            }
        } catch (Throwable ex) {
            HLogger.warn("3D sound: the sound paths could not be kept, they are baked again next time: " + ex);
        }
    }

    /** A kept stage's paths, unzipped; marks it as just used, so it is the last to be forgotten. */
    private static byte[] readKept(Path file) throws IOException {
        try (InputStream in = new GZIPInputStream(Files.newInputStream(file), 1 << 16)) {
            byte[] bytes = in.readAllBytes();
            Files.setLastModifiedTime(file, FileTime.fromMillis(System.currentTimeMillis()));
            return bytes;
        }
    }

    /** Keeps the cache to {@link #PATH_CACHE_STAGES} stages, forgetting those used longest ago. */
    private static void forgetOldStages() {
        try (Stream<Path> files = Files.list(PATH_CACHE)) {
            List<Path> kept = files.filter(p -> p.getFileName().toString().endsWith(".probes.gz"))
                    .sorted(Comparator.comparing((Path p) -> {
                        try {
                            return Files.getLastModifiedTime(p);
                        } catch (IOException ex) {
                            return FileTime.fromMillis(0L);
                        }
                    }).reversed())
                    .toList();
            for (Path old : kept.subList(Math.min(kept.size(), PATH_CACHE_STAGES), kept.size())) {
                Files.deleteIfExists(old);
            }
        } catch (IOException ex) {
            HLogger.warn("3D sound: could not tidy " + PATH_CACHE + ": " + ex);
        }
    }

    /** SHA-256 of the stage's shape and what the bake depends on, in hex. */
    static String cacheKey(Stage stage) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            digest.update(PATHS_BAKED_WITH.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            ByteBuffer b = ByteBuffer.allocate(4 * (stage.vertices().length + stage.triangles().length))
                    .order(ByteOrder.LITTLE_ENDIAN);
            for (float f : stage.vertices()) {
                b.putFloat(f);
            }
            for (int t : stage.triangles()) {
                b.putInt(t);
            }
            digest.update(b.array());
            digest.update(stage.kinds());
            return HexFormat.of().formatHex(digest.digest(), 0, 16);
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException(ex);
        }
    }

    /** Finds the path from each car to the listener; on the acoustics thread. */
    private void findPaths() {
        lock.readLock().lock();
        try {
            if (!pathsReady) {
                return;
            }
            long started = System.nanoTime();
            float[] cars;
            boolean[] active;
            int n;
            synchronized (pathCars) {
                n = pathCount;
                cars = pathCars.clone();
                active = pathActive.clone();
            }
            writeListener(pathShared);
            long origin = at(INPUTS, "source") + COORDS.byteOffset(PathElement.groupElement("origin"));
            for (int i = 0; i < n; i++) {
                if (!active[i]) {
                    continue;
                }
                pathInputs.set(JAVA_FLOAT, origin, cars[3 * i] / UNITS_PER_METRE);
                pathInputs.set(JAVA_FLOAT, origin + 4, -(cars[3 * i + 1] - 30F) / UNITS_PER_METRE);
                pathInputs.set(JAVA_FLOAT, origin + 8, -cars[3 * i + 2] / UNITS_PER_METRE);
                sourceSetInputs.invokeExact(carSources[i], SIMULATE_PATHING, pathInputs);
            }
            simulatorSetShared.invokeExact(simulator, SIMULATE_PATHING, pathShared);
            simulatorRunPathing.invokeExact(simulator);
            float[] found = new float[paths.length];
            for (int i = 0; i < n; i++) {
                if (active[i]) {
                    sourceGetOutputs.invokeExact(carSources[i], SIMULATE_PATHING, pathOutputs);
                    readPath(pathOutputs, found, PATH_FLOATS * i);
                }
            }
            paths = found;
            pathNanos = System.nanoTime() - started;
        } catch (Throwable ex) {
            fail(ex);
        } finally {
            lock.readLock().unlock();
        }
    }

    /**
     * One car's path from Steam Audio's outputs into {@code into} at
     * {@code at}: its bands, and the direction of the first-order
     * Ambisonic coefficients (ACN order W, Y, Z, X, in Steam Audio's
     * Ambisonic axes: x ahead = its -z, y left = its -x, z up = its y),
     * turned into the game's world.
     */
    static void readPath(MemorySegment outputs, float[] into, int at) {
        float low = outputs.get(JAVA_FLOAT, PATH_EQ);
        float mid = outputs.get(JAVA_FLOAT, PATH_EQ + 4);
        float high = outputs.get(JAVA_FLOAT, PATH_EQ + 8);
        MemorySegment sh = outputs.get(ADDRESS, PATH_SH);
        if (sh.equals(MemorySegment.NULL) || !(Float.isFinite(low) && Float.isFinite(mid) && Float.isFinite(high))) {
            return;
        }
        sh = sh.reinterpret(16);
        float w = sh.getAtIndex(JAVA_FLOAT, 0);
        float y = sh.getAtIndex(JAVA_FLOAT, 1);
        float z = sh.getAtIndex(JAVA_FLOAT, 2);
        float x = sh.getAtIndex(JAVA_FLOAT, 3);
        float length = (float) Math.sqrt(x * x + y * y + z * z);
        if (w <= 0F || length < 1e-6F || Math.max(low, Math.max(mid, high)) <= 0F) {
            return; // no path
        }
        into[at] = 1F;
        into[at + 1] = clamp(low);
        into[at + 2] = clamp(mid);
        into[at + 3] = clamp(high);
        // Ambisonic left, up and ahead into the game's x (right), y (down) and z (Steam Audio's -z)
        into[at + 4] = -y / length;
        into[at + 5] = -z / length;
        into[at + 6] = x / length;
    }

    private static float clamp(float v) {
        return Math.max(0F, Math.min(1F, v));
    }

    private void releaseScene() {
        release(meshRelease, mesh);
        release(sceneRelease, scene);
        mesh = MemorySegment.NULL;
        scene = MemorySegment.NULL;
    }

    /** Steam Audio's release functions take the handle's address and clear it. */
    private void release(MethodHandle release, MemorySegment handle) {
        if (handle.equals(MemorySegment.NULL)) {
            return;
        }
        try (Arena a = Arena.ofConfined()) {
            MemorySegment box = a.allocate(ADDRESS);
            box.set(ADDRESS, 0, handle);
            release.invokeExact(box);
        } catch (Throwable ex) {
            HLogger.warn("3D sound: Steam Audio could not let go of something: " + ex);
        }
    }

    /** Stops the acoustics thread, which lets go of everything. */
    void close() {
        running = false;
        if (inStep) {
            releaseAll();
            return;
        }
        LockSupport.unpark(thread);
        try {
            thread.join(2_000L);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
        }
    }
}
