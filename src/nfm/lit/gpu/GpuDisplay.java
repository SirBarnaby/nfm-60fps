package nfm.lit.gpu;

import static org.lwjgl.bgfx.BGFX.*;

import fallk.logmaster.HLogger;
import java.awt.Dimension;
import java.awt.Rectangle;
import java.awt.image.BufferedImage;
import java.awt.image.DataBufferInt;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;
import java.nio.IntBuffer;
import org.lwjgl.bgfx.BGFXInit;
import org.lwjgl.bgfx.BGFXPlatform;
import org.lwjgl.bgfx.BGFXStats;
import org.lwjgl.bgfx.BGFXTransientVertexBuffer;
import org.lwjgl.bgfx.BGFXVertexLayout;
import org.lwjgl.system.MemoryUtil;

/**
 * Puts finished frames on screen through the graphics card with bgfx on
 * Direct3D 11, instead of Java2D's software blit, and can draw the 3D scene
 * there too.
 * <p>
 * A frame is either a picture the processor drew (uploaded as a texture) or
 * a scene drawn here from a {@link SceneList} into a picture-sized target,
 * with the HUD on top. Either way it is drawn into the window as one
 * rectangle, so scaling it costs the processor nothing.
 * <p>
 * bgfx runs single-threaded: every call, from {@link #open} to
 * {@link #close}, must come from the same thread, the game thread.
 */
public final class GpuDisplay {

    /** Views run in this order within a frame. */
    private static final int SCENE_VIEW = 0;
    private static final int SCREEN_VIEW = 1;
    private static final int CAPTURE_VIEW = 2;
    private static final int COPY_VIEW = 3;

    private static final int BLACK = 0x000000ff;
    private static final long CLAMP = BGFX_SAMPLER_U_CLAMP | BGFX_SAMPLER_V_CLAMP;
    private static final long POINT = BGFX_SAMPLER_MIN_POINT | BGFX_SAMPLER_MAG_POINT;
    /** Frames to wait for a read-back before giving up; bgfx needs two. */
    private static final int READ_BACK_FRAMES = 8;
    private static final long OPAQUE = BGFX_STATE_WRITE_RGB | BGFX_STATE_WRITE_A;

    /*
     * The scene's alpha holds how much each pixel glows (see SceneList.GLOW),
     * which only the HDR present reads. Its colours blend as always; its alpha
     * is the glow of the draw that covered the pixel last (the blend factor,
     * set per draw), and the HUD takes it away wherever it covers the scene.
     */
    private static final long SCENE_BLEND = BGFX_STATE_BLEND_FUNC_SEPARATE(BGFX_STATE_BLEND_SRC_ALPHA,
            BGFX_STATE_BLEND_INV_SRC_ALPHA, BGFX_STATE_BLEND_FACTOR, BGFX_STATE_BLEND_ZERO);
    private static final long COVER_BLEND = BGFX_STATE_BLEND_FUNC_SEPARATE(BGFX_STATE_BLEND_ONE,
            BGFX_STATE_BLEND_ZERO, BGFX_STATE_BLEND_FACTOR, BGFX_STATE_BLEND_ZERO);
    private static final long HUD_BLEND = BGFX_STATE_BLEND_FUNC_SEPARATE(BGFX_STATE_BLEND_SRC_ALPHA,
            BGFX_STATE_BLEND_INV_SRC_ALPHA, BGFX_STATE_BLEND_ZERO, BGFX_STATE_BLEND_INV_SRC_ALPHA);

    /** HDR off, whatever the monitor. */
    public static final int HDR_OFF = 0;
    /** HDR when the monitor the window is on has it turned on in Windows. */
    public static final int HDR_AUTO = 1;
    /** HDR output even on an SDR monitor, where Windows shows it as SDR again: for tests. */
    public static final int HDR_FORCE = 2;
    /** How bright the brightest glow is, as a multiple of SDR white. */
    private static final float GLOW_BOOST = 3F;
    /** How often to ask Windows whether HDR was switched, or the window moved to another monitor. */
    private static final long COLOR_CHECK_NANOS = 2_000_000_000L;

    /** The picture's size in the game's pixels: scenes are placed in it, then scaled to the target. */
    private int pictureWidth;
    private int pictureHeight;
    private final long window;

    private boolean started;
    /** bgfx's renderer: Direct3D 11, or 12 with -Dnfm.backend=d3d12. */
    private final int backend = "d3d12".equalsIgnoreCase(System.getProperty("nfm.backend"))
            ? BGFX_RENDERER_TYPE_DIRECT3D12 : BGFX_RENDERER_TYPE_DIRECT3D11;
    private String adapterName = "";
    private BGFXVertexLayout pictureLayout;
    private BGFXVertexLayout colorLayout;
    private short pictureProgram = BGFX_INVALID_HANDLE;
    private short overlayProgram = BGFX_INVALID_HANDLE;
    private short colorProgram = BGFX_INVALID_HANDLE;
    private short hdrProgram = BGFX_INVALID_HANDLE;
    /* placing kept scenes on the card: see Shaders.PROJECTED_VS and SceneGeometry#buildProjected */
    private final boolean projectionWanted = !"off".equalsIgnoreCase(System.getProperty("nfm.projection"));
    private short projectedProgram = BGFX_INVALID_HANDLE;
    private short cameraUniform = BGFX_INVALID_HANDLE;
    private short carUniform = BGFX_INVALID_HANDLE;
    private short pointsSampler = BGFX_INVALID_HANDLE;
    private BGFXVertexLayout projectedLayout;
    /** The kept scene's points, three RGBA32F texels each, ProjectedScene.POINTS_WIDTH texels a row. */
    private short pointsTexture = BGFX_INVALID_HANDLE;
    private int pointsRows;
    private short projectedBuffer = BGFX_INVALID_HANDLE;
    /** The kept scene the card holds the triangles and points of, -1 for none. */
    private long projectedVersion = -1L;
    /** The geometry the last scene was drawn from, for the statistics. */
    private SceneGeometry lastGeometry;
    /* points and bytes sent to the card for kept scenes since the last statistics */
    private int projectedUploads;
    private long projectedBytes;
    private boolean projectedLast;
    private short sampler = BGFX_INVALID_HANDLE;
    private short hdrUniform = BGFX_INVALID_HANDLE;
    private final float[] hdrValues = new float[4];
    /** The present shaders' area-average downscale, see {@link Shaders}: u_down[0] and u_down[1]. */
    private short downUniform = BGFX_INVALID_HANDLE;
    private final float[] downValues = new float[8];
    private short picture = BGFX_INVALID_HANDLE;
    /* the picture's and the HUD's textures' sizes: the UI resolution's, a multiple of the picture's own or not */
    private int pictureTextureWidth;
    private int pictureTextureHeight;
    private int hudTextureWidth;
    private int hudTextureHeight;
    private IntBuffer upload;
    private BGFXTransientVertexBuffer transientBuffer;
    private int width;
    private int height;
    private int resetFlags = BGFX_RESET_NONE;
    private boolean vsync;

    /* how long the card took over the last frame, and what changed on it just before, for the frame log */
    private final StringBuilder changes = new StringBuilder();
    private boolean anyShown;
    private long lastFrameNanos;
    private String lastFrameChanges = "";
    private boolean lastShownPicture;

    /* HDR: what was asked for, what the monitor does, and whether the swap chain is scRGB */
    private int hdrMode = HDR_OFF;
    private boolean hdrBackBuffer;
    private DisplayColor displayColor;
    private boolean monitorHdr;
    private long colorCheckedAt;
    private boolean hdr;
    private String hdrStatus = "off";
    private int swapFormat = BGFX_TEXTURE_FORMAT_RGBA8;

    /* the scene target, made on first use and remade when its size changes */
    private short sceneBuffer = BGFX_INVALID_HANDLE;
    /* the scene target's size, and the part of it the last scene was drawn in (smaller with dynamic resolution) */
    private int targetWidth;
    private int targetHeight;
    private int sceneWidth;
    private int sceneHeight;
    private short sceneColor = BGFX_INVALID_HANDLE;
    /* samples per pixel asked for, and those the scene target has */
    private int wantedSamples = 1;
    private int samples = 1;
    /* the most samples the card does up to wantedSamples, and the wantedSamples that was checked for */
    private int supported = 1;
    private int supportedFor = 1;
    /** Pixels times samples of the smallest scene target the card could not make, so it is not tried again. */
    private long failedSampleArea = Long.MAX_VALUE;
    private short hud = BGFX_INVALID_HANDLE;
    private IntBuffer hudUpload;
    private SceneGeometry geometry;
    /** The texture the next present shows: the picture, or the scene drawn by drawScene. */
    private short source;

    /* read-backs for tests, finished by the next frame */
    private ReadBack sceneRead;
    private BufferedImage sceneImage;
    private BufferedImage glowImage;

    private GpuDisplay(long window, int pictureWidth, int pictureHeight) {
        this.window = window;
        this.pictureWidth = pictureWidth;
        this.pictureHeight = pictureHeight;
    }

    /**
     * Loads bgfx's library and reads the shaders, so that {@link #open} has
     * only the card itself left to start. Any thread, any time before; a
     * failure here is left for {@link #open} to report.
     */
    public static void prepare() {
        try {
            GpuLibraries.prepare();
            org.lwjgl.bgfx.BGFX.getLibrary();
            HlslCompiler hlsl = new HlslCompiler();
            for (Shaders.Source source : Shaders.ALL) {
                hlsl.bytecode(source);
            }
        } catch (Throwable ex) {
            // open() tries again and says what is wrong
        }
    }

    /**
     * Starts bgfx on the window (an HWND), or returns null and logs why when
     * it cannot: the caller then keeps drawing in software.
     */
    public static GpuDisplay open(long window, Dimension size, int pictureWidth, int pictureHeight) {
        long started = System.nanoTime();
        GpuDisplay display = new GpuDisplay(window, pictureWidth, pictureHeight);
        try {
            display.start(Math.max(1, size.width), Math.max(1, size.height));
            HLogger.info("Drawing through the graphics card (" + backendName(display.backend) + display.adapterName()
                    + String.format(", started in %d ms)", (System.nanoTime() - started) / 1_000_000L));
            return display;
        } catch (Throwable ex) {
            HLogger.warn("Cannot draw through the graphics card, drawing in software: " + ex);
            display.close();
            return null;
        }
    }

    /** ", " and the name of the card bgfx drew with, or "" if it cannot say. */
    private String adapterName() {
        String name = adapterName;
        return name.isEmpty() ? "" : ", " + name;
    }

    private static String backendName(int backend) {
        return backend == BGFX_RENDERER_TYPE_DIRECT3D12 ? "Direct3D 12" : "Direct3D 11";
    }

    /** The window this display draws into. */
    public long window() {
        return window;
    }

    /**
     * Waits for the display's refresh before showing each frame, so a frame is
     * never shown torn; takes effect with the next frame.
     */
    public void setVsync(boolean on) {
        vsync = on;
    }

    /**
     * HDR_OFF, HDR_AUTO or HDR_FORCE; takes effect with the next frame. In
     * HDR the game looks as it does in SDR, at the brightness Windows shows
     * SDR white at, except that what glows (flames, lamps, sparks, stars,
     * lightning) is brighter than white.
     */
    public void setHdr(int mode) {
        if (mode != hdrMode) {
            hdrMode = mode;
            colorCheckedAt = 0L; // look again now
        }
    }

    /** Lowest and highest paper white, in nits. */
    public static final int PAPER_WHITE_MIN = 80;
    public static final int PAPER_WHITE_MAX = 500;
    /** Lowest and highest peak brightness, in nits. */
    public static final int PEAK_MIN = 200;
    public static final int PEAK_MAX = 10000;

    private int paperWhiteNits;
    private int peakNits;

    /**
     * In HDR, how bright the game's white is, in nits: the original palette
     * and the HUD are drawn at it; 0 follows Windows' SDR content brightness,
     * so the game matches the desktop.
     */
    public void setPaperWhite(int nits) {
        if (nits != paperWhiteNits) {
            paperWhiteNits = nits;
            hdrStatus = describeHdr();
        }
    }

    /**
     * In HDR, the brightest the display shows, in nits, which the glow rolls
     * off into; 0 takes what the monitor tells Windows.
     */
    public void setPeak(int nits) {
        if (nits != peakNits) {
            peakNits = nits;
            hdrStatus = describeHdr();
        }
    }

    /** Paper white in nits: the setting, or Windows' SDR white. */
    private float paperWhite() {
        return paperWhiteNits > 0 ? paperWhiteNits : displayColor.whiteNits;
    }

    /** The display's peak in nits, never below paper white; 0 if neither the setting nor the monitor says. */
    private float peak() {
        float peak = peakNits > 0 ? peakNits : displayColor.peakNits;
        return peak > 0F ? Math.max(peak, paperWhite()) : 0F;
    }

    /** Whether frames go out in HDR, and at what SDR white, for the console. */
    public String hdrStatus() {
        return hdrStatus;
    }

    private String describeHdr() {
        if (hdr) {
            float peak = peak();
            float brightest = paperWhite() * (1F + GLOW_BOOST);
            String highlights = peak <= 0F ? ", peak unknown: highlights up to " + Math.round(brightest) + " nits"
                    : ", peak " + Math.round(peak) + " nits" + (peakNits > 0 ? " (set)" : " (the monitor's)")
                    + (brightest > peak ? ", highlights roll off into it" : "");
            return "on, paper white " + Math.round(paperWhite()) + " nits"
                    + (paperWhiteNits > 0 ? " (set)" : " (Windows' SDR white)") + highlights
                    + (monitorHdr ? "" : " (forced on an SDR monitor)");
        }
        if (hdrMode == HDR_OFF) {
            return "off";
        }
        return hdrBackBuffer ? "off: the monitor is SDR" : "off: the graphics card cannot";
    }

    private void start(int width, int height) {
        GpuLibraries.prepare();
        HlslCompiler hlsl = new HlslCompiler();
        byte[] pictureVs = hlsl.bytecode(Shaders.PICTURE_VERTEX);
        byte[] pictureFs = hlsl.bytecode(Shaders.PICTURE_FRAGMENT);
        byte[] overlayFs = hlsl.bytecode(Shaders.OVERLAY_FRAGMENT);
        byte[] colorVs = hlsl.bytecode(Shaders.COLOR_VERTEX);
        byte[] colorFs = hlsl.bytecode(Shaders.COLOR_FRAGMENT);
        byte[] hdrFs = hlsl.bytecode(Shaders.HDR_FRAGMENT);
        byte[] projectedVs = null;
        if (projectionWanted) {
            try {
                projectedVs = hlsl.bytecode(Shaders.PROJECTED_VERTEX);
            } catch (RuntimeException ex) {
                HLogger.warn("Placing scenes on the graphics card is off: its shader did not compile: " + ex);
            }
        }
        if (hlsl.compiled() > 0) {
            HLogger.info("Compiled " + hlsl.compiled() + " shaders at the start: madness.jar has no baked copy of them");
        }

        // Windows' choice of card (its graphics settings can set one per program), unless asked for the fast one
        GpuAdapter adapter = "fast".equalsIgnoreCase(System.getProperty("nfm.adapter"))
                ? GpuAdapter.highPerformance() : null;
        BGFXPlatform.bgfx_render_frame(-1); // no render thread: bgfx works on the calling thread
        try (BGFXInit init = BGFXInit.calloc()) {
            bgfx_init_ctor(init);
            init.type(backend);
            if (adapter != null) {
                init.vendorId(adapter.vendor).deviceId(adapter.device);
            }
            // at most one frame queued for the display: what is drawn is shown as soon as it can be
            init.resolution(r -> r.width(width).height(height).reset(resetFlags).formatColor(swapFormat)
                    .maxFrameLatency((byte) 1));
            init.platformData(p -> p.nwh(window));
            if (!bgfx_init(init)) {
                throw new IllegalStateException("bgfx_init failed");
            }
        }
        started = true;
        if (adapter != null && adapter.vendor == bgfx_get_caps().vendorId()
                && adapter.device == bgfx_get_caps().deviceId()) {
            adapterName = adapter.name;
        }
        this.width = width;
        this.height = height;
        if (bgfx_get_renderer_type() != backend) {
            throw new IllegalStateException(backendName(backend) + " is not available");
        }

        int[] pictureAttributes = {BgfxShaderBinary.ATTRIB_POSITION, BgfxShaderBinary.ATTRIB_TEXCOORD0};
        BgfxShaderBinary.Vec4 down = new BgfxShaderBinary.Vec4("u_down", 2);
        // each uniform before the shaders that read it
        downUniform = bgfx_create_uniform("u_down", BGFX_UNIFORM_TYPE_VEC4, 2);
        hdrUniform = bgfx_create_uniform("u_hdr", BGFX_UNIFORM_TYPE_VEC4, 1);
        pictureProgram = program(BgfxShaderBinary.vertex(pictureVs, pictureAttributes),
                BgfxShaderBinary.fragment(pictureFs, "s_picture", down));
        overlayProgram = program(BgfxShaderBinary.vertex(pictureVs, pictureAttributes),
                BgfxShaderBinary.fragment(overlayFs, "s_picture"));
        colorProgram = program(
                BgfxShaderBinary.vertex(colorVs, BgfxShaderBinary.ATTRIB_POSITION, BgfxShaderBinary.ATTRIB_COLOR0,
                        BgfxShaderBinary.ATTRIB_TEXCOORD0),
                BgfxShaderBinary.fragment(colorFs, null));
        sampler = bgfx_create_uniform("s_picture", BGFX_UNIFORM_TYPE_SAMPLER, 1);
        if (projectedVs != null) {
            startProjection(projectedVs, colorFs);
        }
        hdrProgram = program(BgfxShaderBinary.vertex(pictureVs, pictureAttributes),
                BgfxShaderBinary.fragment(hdrFs, "s_picture", new BgfxShaderBinary.Vec4("u_hdr", 1), down));
        hdrBackBuffer = (bgfx_get_caps().formats(BGFX_TEXTURE_FORMAT_RGBA16F)
                & BGFX_CAPS_FORMAT_TEXTURE_BACKBUFFER) != 0;
        pictureLayout = BGFXVertexLayout.calloc();
        bgfx_vertex_layout_begin(pictureLayout, backend);
        bgfx_vertex_layout_add(pictureLayout, BGFX_ATTRIB_POSITION, 2, BGFX_ATTRIB_TYPE_FLOAT, false, false);
        bgfx_vertex_layout_add(pictureLayout, BGFX_ATTRIB_TEXCOORD0, 2, BGFX_ATTRIB_TYPE_FLOAT, false, false);
        bgfx_vertex_layout_end(pictureLayout);
        colorLayout = BGFXVertexLayout.calloc();
        bgfx_vertex_layout_begin(colorLayout, backend);
        bgfx_vertex_layout_add(colorLayout, BGFX_ATTRIB_POSITION, 2, BGFX_ATTRIB_TYPE_FLOAT, false, false);
        bgfx_vertex_layout_add(colorLayout, BGFX_ATTRIB_COLOR0, 4, BGFX_ATTRIB_TYPE_UINT8, true, false);
        bgfx_vertex_layout_add(colorLayout, BGFX_ATTRIB_TEXCOORD0, 2, BGFX_ATTRIB_TYPE_FLOAT, false, false);
        bgfx_vertex_layout_end(colorLayout);
        picture = bgfx_create_texture_2d(pictureWidth, pictureHeight, false, 1, BGFX_TEXTURE_FORMAT_BGRA8,
                CLAMP, null, 0);
        if (!isValid(sampler) || !isValid(hdrUniform) || !isValid(downUniform) || !isValid(picture)) {
            throw new IllegalStateException("creating GPU resources failed");
        }
        source = picture;
        pictureTextureWidth = pictureWidth;
        pictureTextureHeight = pictureHeight;
        upload = MemoryUtil.memAllocInt(pictureWidth * pictureHeight);
        transientBuffer = BGFXTransientVertexBuffer.calloc();
        bgfx_set_view_clear(SCREEN_VIEW, BGFX_CLEAR_COLOR, BLACK, 1F, 0);
        bgfx_set_view_clear(CAPTURE_VIEW, BGFX_CLEAR_COLOR, BLACK, 1F, 0);
    }

    /** Makes what placing kept scenes on the card needs; leaves it off if the card will not have it. */
    private void startProjection(byte[] projectedVs, byte[] colorFs) {
        int floatFormats = bgfx_get_caps().formats(BGFX_TEXTURE_FORMAT_RGBA32F);
        if ((floatFormats & BGFX_CAPS_FORMAT_TEXTURE_VERTEX) == 0 && (floatFormats & BGFX_CAPS_FORMAT_TEXTURE_2D) == 0) {
            HLogger.warn("Placing scenes on the graphics card is off: it cannot read float textures");
            return;
        }
        cameraUniform = bgfx_create_uniform("u_cam", BGFX_UNIFORM_TYPE_VEC4, ScenePoints.CAMERA_VEC4S);
        carUniform = bgfx_create_uniform("u_car", BGFX_UNIFORM_TYPE_VEC4,
                ScenePoints.CAR_VEC4S * ScenePoints.CAR_SLOTS);
        pointsSampler = bgfx_create_uniform("s_points", BGFX_UNIFORM_TYPE_SAMPLER, 1);
        BgfxShaderBinary.Vec4[] constants = {
            new BgfxShaderBinary.Vec4("u_cam", ScenePoints.CAMERA_VEC4S),
            new BgfxShaderBinary.Vec4("u_car", ScenePoints.CAR_VEC4S * ScenePoints.CAR_SLOTS)};
        try {
            projectedProgram = program(
                    BgfxShaderBinary.vertex(projectedVs, "s_points", constants, BgfxShaderBinary.ATTRIB_POSITION,
                            BgfxShaderBinary.ATTRIB_COLOR0, BgfxShaderBinary.ATTRIB_TEXCOORD0,
                            BgfxShaderBinary.ATTRIB_TEXCOORD1),
                    BgfxShaderBinary.fragment(colorFs, null));
        } catch (IllegalStateException ex) {
            HLogger.warn("Placing scenes on the graphics card is off: " + ex.getMessage());
            return;
        }
        projectedLayout = BGFXVertexLayout.calloc();
        bgfx_vertex_layout_begin(projectedLayout, backend);
        bgfx_vertex_layout_add(projectedLayout, BGFX_ATTRIB_POSITION, 2, BGFX_ATTRIB_TYPE_FLOAT, false, false);
        bgfx_vertex_layout_add(projectedLayout, BGFX_ATTRIB_COLOR0, 4, BGFX_ATTRIB_TYPE_UINT8, true, false);
        bgfx_vertex_layout_add(projectedLayout, BGFX_ATTRIB_TEXCOORD0, 4, BGFX_ATTRIB_TYPE_FLOAT, false, false);
        bgfx_vertex_layout_add(projectedLayout, BGFX_ATTRIB_TEXCOORD1, 1, BGFX_ATTRIB_TYPE_FLOAT, false, false);
        bgfx_vertex_layout_end(projectedLayout);
    }

    /**
     * The picture took another shape (GameFacts.screenWidth x screenHeight):
     * scenes are placed in it from now on, and the HUD and kept scene are
     * sent again. The textures follow the frames given.
     */
    public void setPictureSize(int width, int height) {
        pictureWidth = width;
        pictureHeight = height;
        invalidateHud();
        projectedVersion = -1L;
    }

    /** True if kept scenes are placed on the card, not here. */
    public boolean projecting() {
        return isValid(projectedProgram);
    }

    private static boolean isValid(short handle) {
        return handle != BGFX_INVALID_HANDLE;
    }

    private static short program(byte[] vertex, byte[] fragment) {
        short program = bgfx_create_program(createShader(vertex), createShader(fragment), true);
        if (!isValid(program)) {
            throw new IllegalStateException("bgfx rejected a shader program");
        }
        return program;
    }

    private static short createShader(byte[] binary) {
        ByteBuffer buffer = MemoryUtil.memAlloc(binary.length).put(binary).flip();
        try {
            short shader = bgfx_create_shader(bgfx_copy(buffer));
            if (!isValid(shader)) {
                throw new IllegalStateException("bgfx rejected a shader");
            }
            return shader;
        } finally {
            MemoryUtil.memFree(buffer);
        }
    }

    /* ------------------------------------------------------------ scene */

    /**
     * Draws a scene into a target of {@code size}, the picture's size or the
     * size it will have in the window, then {@code hud} (a packed-int ARGB
     * image of the picture's size, may be null) over it, and a white flash
     * over everything when {@code flash}. The next {@link #present} with no
     * picture shows the result.
     * <p>
     * A target larger than the picture draws the scene at that resolution,
     * so it is sharp in a large window rather than scaled up; one-pixel
     * lines get as much thicker as the picture is larger.
     *
     * @param version names a scene kept for a tick: under the same version the HUD is the same picture,
     *                not uploaded again, and the scene holds the same polygons in the same order, only
     *                moved, so how concave ones were cut into triangles can be checked rather than worked
     *                out again; -1 for a scene and HUD drawn anew
     * @param capture also read the drawn picture back, see {@link #takeSceneCapture}
     * @param scale   with dynamic resolution, the share of {@code size} to draw the scene at, 1 for all of it;
     *                never below the picture's own size. The target keeps its size, so changing the scale
     *                costs nothing: only part of it is drawn in, and that part is shown filtered
     * @return false if it could not be drawn (the caller should fall back)
     */
    public boolean drawScene(SceneList scene, BufferedImage hudImage, long version, long hudVersion, boolean flash,
            boolean capture, Dimension size, float scale) {
        return drawScene(scene, null, hudImage, version, hudVersion, flash, capture, size, scale, null);
    }

    /**
     * Draws a scene as {@link #drawScene(SceneList, BufferedImage, long, long, boolean, boolean, Dimension, float)}
     * does; a kept scene {@code projected} for the card (not null, and
     * built) is placed for the frame on the card instead, with the camera
     * and cars in its points, and {@code scene}'s corners are not read.
     * {@code hudArea} is where the HUD goes, as shares of the scene's width
     * and height (left, top, width, height); null for all of it. Shown a
     * whole number of times its size it is not filtered, so it stays sharp.
     */
    public boolean drawScene(SceneList scene, ProjectedScene projected, BufferedImage hudImage, long version,
            long hudVersion, boolean flash, boolean capture, Dimension size, float scale, float[] hudArea) {
        fitSceneTarget(Math.max(1, size.width), Math.max(1, size.height));
        sceneWidth = Math.min(targetWidth, Math.max(pictureWidth, Math.round(targetWidth * scale)));
        sceneHeight = Math.min(targetHeight, Math.max(pictureHeight, Math.round(targetHeight * scale)));
        if (hudImage != null && (hudVersion < 0L || hudVersion != uploadedHud)) {
            fitHudTexture(hudImage.getWidth(), hudImage.getHeight());
            if (!uploadHud(hudImage)) {
                return false;
            }
            uploadedHud = hudVersion;
            hudUploads++;
        }
        bgfx_set_view_rect(SCENE_VIEW, 0, 0, sceneWidth, sceneHeight);
        bgfx_touch(SCENE_VIEW);
        projectedLast = projected != null && projected.built() && version >= 0L && projecting();
        if (projectedLast) {
            if (version != projectedVersion) {
                uploadProjected(projected);
                projectedVersion = version;
            }
            submitProjected(projected);
        } else {
            long buildStart = System.nanoTime();
            geometry.build(scene, pictureWidth, pictureHeight, sceneWidth / (float) pictureWidth, version);
            buildNanos += System.nanoTime() - buildStart;
            builds++;
            lastGeometry = geometry;
            int vertexCount = geometry.vertexCount();
            if (vertexCount > 0) {
                if (bgfx_get_avail_transient_vertex_buffer(vertexCount, colorLayout) < vertexCount) {
                    return false;
                }
                bgfx_alloc_transient_vertex_buffer(transientBuffer, vertexCount, colorLayout);
                geometry.copyVertices(transientBuffer.data());
                submitScene(geometry, false);
            }
        }
        if (hudImage != null) {
            Rectangle area = hudArea == null ? new Rectangle(0, 0, sceneWidth, sceneHeight)
                    : new Rectangle(Math.round(sceneWidth * hudArea[0]), Math.round(sceneHeight * hudArea[1]),
                            Math.round(sceneWidth * hudArea[2]), Math.round(sceneHeight * hudArea[3]));
            boolean scaled = area.width % hudTextureWidth != 0 || area.height % hudTextureHeight != 0
                    || area.width / hudTextureWidth != area.height / hudTextureHeight;
            drawRectangle(SCENE_VIEW, overlayProgram, hud, sceneWidth, sceneHeight, area, scaled,
                    OPAQUE | HUD_BLEND, WHOLE);
        }
        if (flash) {
            fillWhite();
        }
        if (capture) {
            sceneRead = new ReadBack(sceneColor, sceneWidth, sceneHeight, 0F, hdr);
        }
        source = sceneColor;
        return true;
    }

    /** Sends a kept scene to the card once, for all its frames: its triangles and its points. */
    private void uploadProjected(ProjectedScene projected) {
        SceneGeometry g = projected.geometry;
        lastGeometry = g;
        ByteBuffer vertices = projected.vertices();
        int vertexCount = g.vertexCount();
        if (vertexCount > 0) {
            if (!isValid(projectedBuffer)) {
                projectedBuffer = bgfx_create_dynamic_vertex_buffer(Math.max(vertexCount, 65536), projectedLayout,
                        BGFX_BUFFER_ALLOW_RESIZE);
            }
            bgfx_update_dynamic_vertex_buffer(projectedBuffer, 0, bgfx_copy(vertices));
        }
        int rows = projected.rows();
        if (rows > pointsRows || !isValid(pointsTexture)) {
            destroyTexture(pointsTexture);
            pointsRows = Math.min(Math.max(16, Integer.highestOneBit(rows - 1) << 1), maxTextureSize());
            pointsTexture = bgfx_create_texture_2d(ProjectedScene.POINTS_WIDTH, pointsRows, false, 1,
                    BGFX_TEXTURE_FORMAT_RGBA32F, CLAMP | BGFX_SAMPLER_POINT, null, 0);
            if (!isValid(pointsTexture) || rows > pointsRows) {
                throw new IllegalStateException("the card cannot hold the scene's " + projected.pointCount() + " points");
            }
        }
        FloatBuffer texels = projected.texels();
        bgfx_update_texture_2d(pointsTexture, 0, 0, 0, 0, ProjectedScene.POINTS_WIDTH, rows, bgfx_copy(texels),
                ProjectedScene.POINTS_WIDTH * 16);
        projectedUploads++;
        projectedBytes += vertices.remaining() + (long) texels.remaining() * 4;
        // built on the game thread, counted here with the scenes it made
        buildNanos += projected.buildNanos();
        builds++;
    }

    /** Draws the kept scene on the card, placed for the frame whose camera and cars its points hold. */
    private void submitProjected(ProjectedScene projected) {
        ScenePoints points = projected.points();
        float[] camera = points.camera;
        camera[14] = 2F / pictureWidth;
        camera[15] = 2F / pictureHeight;
        camera[16] = sceneWidth / (float) pictureWidth;
        bgfx_set_uniform(cameraUniform, camera, ScenePoints.CAMERA_VEC4S);
        bgfx_set_uniform(carUniform, points.cars, Math.max(1, points.carCount) * ScenePoints.CAR_VEC4S);
        lastGeometry = projected.geometry;
        if (projected.geometry.vertexCount() > 0) {
            submitScene(projected.geometry, true);
        }
    }

    /** The scene read back by the last {@link #drawScene} that asked for it, once presented. */
    public BufferedImage takeSceneCapture() {
        BufferedImage image = sceneImage;
        sceneImage = null;
        return image;
    }

    /**
     * How much each pixel of the scene read back with it glows, as grey
     * (white glows most), when HDR was on; null otherwise. Take it after
     * {@link #takeSceneCapture}.
     */
    public BufferedImage takeGlowCapture() {
        BufferedImage image = glowImage;
        glowImage = null;
        return image;
    }

    /** Draw calls, vertices, stencil-filled polygons and antialiased outlines of the last scene, for the profile. */
    public String sceneStatistics() {
        SceneGeometry g = lastGeometry;
        if (g == null) {
            return "";
        }
        String text = g.draws() + " draws, " + g.vertexCount() + " vertices, "
                + g.evenOddPolygons() + " even-odd, " + g.antialiased() + " antialiased"
                + String.format(", triangles made in %.2f ms", builds == 0 ? 0D : buildNanos / 1e6D / builds)
                + ", " + hudUploads + " HUD uploads of " + hudTextureWidth + "x" + hudTextureHeight
                + String.format(" (%.1f MB)", hudBytes / 1e6D)
                + (projectedLast ? String.format(", placed on the card: %d scenes sent, %.1f MB",
                        projectedUploads, projectedBytes / 1e6D) : "");
        buildNanos = 0L;
        builds = 0;
        hudUploads = 0;
        hudBytes = 0L;
        projectedUploads = 0;
        projectedBytes = 0L;
        return text;
    }

    /**
     * How long the graphics card itself took over the last frame it finished,
     * from its own clock, in nanoseconds; 0 if it cannot say. Time the card
     * waits for the processor or the display is not in it.
     */
    public long gpuFrameNanos() {
        BGFXStats stats = bgfx_get_stats();
        if (stats == null) {
            return 0L;
        }
        long frequency = stats.gpuTimerFreq();
        long ticks = stats.gpuTimeEnd() - stats.gpuTimeBegin();
        if (frequency <= 0L || ticks <= 0L) {
            return 0L;
        }
        return (long) (ticks * (1e9D / frequency));
    }

    /**
     * The card's memory after its last frame, in bytes: used, the most it
     * has, textures and render targets; negative where the card does not say.
     */
    public long[] videoMemory() {
        BGFXStats stats = bgfx_get_stats();
        if (stats == null) {
            return new long[] {-1L, -1L, -1L, -1L};
        }
        return new long[] {stats.gpuMemoryUsed(), stats.gpuMemoryMax(), stats.textureMemoryUsed(),
                stats.rtMemoryUsed()};
    }

    /** Which HUD the HUD texture holds, see {@link #drawScene}. */
    private long uploadedHud = -1L;

    /** HUDs sent to the card since the last statistics. */
    private int hudUploads;

    /* time spent turning scenes into triangles since the last statistics */
    private long buildNanos;
    private int builds;

    /**
     * Samples per pixel the scene is drawn with: 1 draws each polygon's
     * edge pixels whole, as the game always did; 2, 4 or 8 smooth them
     * (multisampling). Takes effect with the next scene.
     */
    public void setMultisample(int wanted) {
        wantedSamples = wanted;
    }

    /** Samples per pixel the last scene was drawn with: what was asked for, or the most the card does below it. */
    public int multisample() {
        return samples;
    }

    /** The multisampling flag for {@code n} samples per pixel, 0 for none. */
    private static long msaaFlag(int n) {
        return switch (n) {
            case 2 -> BGFX_TEXTURE_RT_MSAA_X2;
            case 4 -> BGFX_TEXTURE_RT_MSAA_X4;
            case 8 -> BGFX_TEXTURE_RT_MSAA_X8;
            case 16 -> BGFX_TEXTURE_RT_MSAA_X16;
            default -> 0L;
        };
    }

    /** The most samples per pixel up to {@code wanted} the card can draw the scene with. */
    private static int supportedSamples(int wanted) {
        int n = Integer.highestOneBit(Math.max(1, Math.min(16, wanted)));
        for (; n > 1; n /= 2) {
            long msaa = msaaFlag(n);
            if (bgfx_is_texture_valid(0, false, 1, BGFX_TEXTURE_FORMAT_BGRA8, BGFX_TEXTURE_RT | CLAMP | msaa)
                    && bgfx_is_texture_valid(0, false, 1, BGFX_TEXTURE_FORMAT_D24S8,
                    BGFX_TEXTURE_RT_WRITE_ONLY | msaa)) {
                break;
            }
        }
        return n;
    }

    private void fitSceneTarget(int newWidth, int newHeight) {
        if (supportedFor != wantedSamples) {
            supportedFor = wantedSamples;
            supported = supportedSamples(wantedSamples);
        }
        int newSamples = supported;
        while (newSamples > 1 && (long) newWidth * newHeight * newSamples >= failedSampleArea) {
            newSamples /= 2; // as large as a target the card could not make
        }
        if (isValid(sceneBuffer) && newWidth == targetWidth && newHeight == targetHeight && newSamples == samples) {
            return;
        }
        if (isValid(sceneBuffer)) {
            bgfx_destroy_frame_buffer(sceneBuffer); // and its two textures
        }
        targetWidth = newWidth;
        targetHeight = newHeight;
        // a large multisampled target can be more than the card has room for: then fewer samples
        for (samples = newSamples;; samples /= 2) {
            sceneBuffer = createSceneBuffer(samples);
            if (isValid(sceneBuffer) || samples == 1) {
                break;
            }
            failedSampleArea = Math.min(failedSampleArea, (long) targetWidth * targetHeight * samples);
            HLogger.warn("The graphics card cannot draw " + targetWidth + "x" + targetHeight + " with " + samples
                    + " samples per pixel, trying " + (samples / 2));
        }
        if (!isValid(hud)) {
            hud = bgfx_create_texture_2d(pictureWidth, pictureHeight, false, 1, BGFX_TEXTURE_FORMAT_BGRA8,
                    CLAMP, null, 0);
            hudUpload = MemoryUtil.memAllocInt(pictureWidth * pictureHeight);
            hudHeld = false;
            hudTextureWidth = pictureWidth;
            hudTextureHeight = pictureHeight;
            geometry = new SceneGeometry();
        }
        if (!isValid(sceneBuffer) || !isValid(hud)) {
            throw new IllegalStateException("creating the scene target failed");
        }
        bgfx_set_view_frame_buffer(SCENE_VIEW, sceneBuffer);
        bgfx_set_view_mode(SCENE_VIEW, BGFX_VIEW_MODE_SEQUENTIAL);
        // the scene paints every pixel, as it does in software; only the stencil starts empty
        bgfx_set_view_clear(SCENE_VIEW, BGFX_CLEAR_STENCIL, 0, 1F, 0);
    }

    /**
     * Makes the scene target at the scene's size with {@code n} samples per
     * pixel, its colour in {@link #sceneColor}; an invalid handle, with
     * nothing left over, if the card cannot.
     */
    private short createSceneBuffer(int n) {
        // multisampled, the colour is resolved into a plain texture when the scene view is done
        long msaa = msaaFlag(n);
        sceneColor = bgfx_create_texture_2d(targetWidth, targetHeight, false, 1, BGFX_TEXTURE_FORMAT_BGRA8,
                BGFX_TEXTURE_RT | CLAMP | msaa, null, 0);
        short stencil = bgfx_create_texture_2d(targetWidth, targetHeight, false, 1, BGFX_TEXTURE_FORMAT_D24S8,
                BGFX_TEXTURE_RT_WRITE_ONLY | msaa, null, 0);
        if (!isValid(sceneColor) || !isValid(stencil)) {
            if (isValid(sceneColor)) {
                bgfx_destroy_texture(sceneColor);
            }
            if (isValid(stencil)) {
                bgfx_destroy_texture(stencil);
            }
            return BGFX_INVALID_HANDLE;
        }
        noteChange("a new scene target " + targetWidth + "x" + targetHeight + (n > 1 ? " with " + n + " samples" : ""));
        return bgfx_create_frame_buffer_from_handles(new short[] {sceneColor, stencil}, true);
    }

    /** Submits {@code g}'s draws: from the transient buffer, or with {@code points} placed on the card. */
    private void submitScene(SceneGeometry g, boolean onCard) {
        short program = onCard ? projectedProgram : colorProgram;
        int stencilFlip = BGFX_STENCIL_TEST_ALWAYS | BGFX_STENCIL_FUNC_REF(0) | BGFX_STENCIL_FUNC_RMASK(0xff)
                | BGFX_STENCIL_OP_FAIL_S_KEEP | BGFX_STENCIL_OP_FAIL_Z_KEEP | BGFX_STENCIL_OP_PASS_Z_INVERT;
        int stencilOdd = BGFX_STENCIL_TEST_NOTEQUAL | BGFX_STENCIL_FUNC_REF(0) | BGFX_STENCIL_FUNC_RMASK(0xff)
                | BGFX_STENCIL_OP_FAIL_S_KEEP | BGFX_STENCIL_OP_FAIL_Z_KEEP | BGFX_STENCIL_OP_PASS_Z_ZERO;
        clipCached = NO_CLIP; // bgfx keeps scissors for one frame
        for (int d = 0; d < g.draws(); d++) {
            int start = g.start(d);
            int count = g.count(d);
            if (count == 0) {
                continue;
            }
            int glow = g.glow(d); // the blend factor's alpha
            short clip = paneScissor(g, d);
            if (g.kind(d) == SceneGeometry.PLAIN) {
                bindScene(onCard, start, count, clip);
                bgfx_set_state(OPAQUE | SCENE_BLEND, glow); // only antialiased lines are not opaque
                bgfx_submit(SCENE_VIEW, program, 0, BGFX_DISCARD_ALL);
            } else {
                // flip the stencil where the fan covers, then colour where it is odd and clear it again
                bindScene(onCard, start, count, clip);
                bgfx_set_stencil(stencilFlip, BGFX_STENCIL_NONE);
                bgfx_set_state(0L, 0);
                bgfx_submit(SCENE_VIEW, program, 0, BGFX_DISCARD_ALL);
                bindScene(onCard, start, count, clip);
                bgfx_set_stencil(stencilOdd, BGFX_STENCIL_NONE);
                bgfx_set_state(OPAQUE | COVER_BLEND, glow);
                bgfx_submit(SCENE_VIEW, program, 0, BGFX_DISCARD_ALL);
            }
        }
    }

    private final int[] paneArea = new int[4];
    /* the last pane's scissor, kept by bgfx for this frame, and the pane it is for */
    private final int[] clipArea = new int[4];
    private short clipCached = NO_CLIP;

    /**
     * The scissor of draw {@code d}'s split-screen pane, in the target's
     * pixels (its edges where the picture's land, so the panes meet without
     * a gap or an overlap), kept by bgfx for the frame; NO_CLIP when it has
     * none. A pane's draws come one after another, so each pane is worked
     * out once.
     */
    private short paneScissor(SceneGeometry g, int d) {
        if (!g.pane(d, paneArea)) {
            return NO_CLIP;
        }
        if (clipCached != NO_CLIP && java.util.Arrays.equals(paneArea, clipArea)) {
            return clipCached;
        }
        System.arraycopy(paneArea, 0, clipArea, 0, 4);
        float sx = sceneWidth / (float) pictureWidth;
        float sy = sceneHeight / (float) pictureHeight;
        int left = Math.round(paneArea[0] * sx);
        int top = Math.round(paneArea[1] * sy);
        int right = Math.round((paneArea[0] + paneArea[2]) * sx);
        int bottom = Math.round((paneArea[1] + paneArea[3]) * sy);
        clipCached = (short) bgfx_set_scissor(left, top, Math.max(0, right - left), Math.max(0, bottom - top));
        return clipCached;
    }

    /** No scissor: {@link #bindScene} draws over the whole target. */
    private static final short NO_CLIP = -1;

    private void bindScene(boolean onCard, int start, int count, short clip) {
        if (clip != NO_CLIP) {
            bgfx_set_scissor_cached(Short.toUnsignedInt(clip));
        }
        if (!onCard) {
            bgfx_set_transient_vertex_buffer(0, transientBuffer, start, count);
        } else {
            bgfx_set_dynamic_vertex_buffer(0, projectedBuffer, start, count);
            bgfx_set_texture(0, pointsSampler, pointsTexture, (int) (CLAMP | BGFX_SAMPLER_POINT));
        }
    }

    private void fillWhite() {
        if (bgfx_get_avail_transient_vertex_buffer(4, colorLayout) < 4) {
            return;
        }
        BGFXTransientVertexBuffer white = BGFXTransientVertexBuffer.calloc();
        try {
            bgfx_alloc_transient_vertex_buffer(white, 4, colorLayout);
            ByteBuffer v = white.data().order(ByteOrder.LITTLE_ENDIAN);
            float[][] corners = {{-1F, 1F}, {1F, 1F}, {-1F, -1F}, {1F, -1F}};
            for (float[] c : corners) {
                // white, and nothing glows under the flash: its alpha is 0
                v.putFloat(c[0]).putFloat(c[1]).putInt(0x00ffffff).putFloat(0F).putFloat(1F);
            }
            bgfx_set_transient_vertex_buffer(0, white, 0, 4);
            bgfx_set_state(OPAQUE | BGFX_STATE_PT_TRISTRIP, 0);
            bgfx_submit(SCENE_VIEW, colorProgram, 0, BGFX_DISCARD_ALL);
        } finally {
            white.free();
        }
    }

    /* -------------------------------------------------------- presenting */

    /**
     * Shows {@code frame}, a packed-int RGB image of the picture's size, or
     * with null the scene from {@link #drawScene}, in a window of the given
     * size with the picture at {@code place}; the rest is black.
     * {@code smooth} filters the picture when it is scaled.
     *
     * @return false if the frame could not be shown (the caller should fall back)
     */
    public boolean present(BufferedImage frame, Dimension windowSize, Rectangle place, boolean smooth) {
        return show(frame, windowSize, place, smooth, null);
    }

    /**
     * Presents like {@link #present} and also reads the window back from the
     * graphics card, exactly as it went to the screen. For tests.
     *
     * @return the window's picture, or null if it could not be read back
     */
    public BufferedImage presentAndCapture(BufferedImage frame, Dimension windowSize, Rectangle place,
            boolean smooth) {
        fitSwapChain(windowSize);
        // in HDR, what went out as scRGB, turned back into sRGB as it was before the glow brightened it
        short target = bgfx_create_frame_buffer(width, height, hdr ? BGFX_TEXTURE_FORMAT_RGBA16F
                : BGFX_TEXTURE_FORMAT_BGRA8, BGFX_TEXTURE_RT | CLAMP);
        try {
            ReadBack windowRead = new ReadBack(bgfx_get_texture(target, 0), width, height,
                    hdr ? whiteScale() : 0F, false);
            bgfx_set_view_frame_buffer(CAPTURE_VIEW, target);
            return show(frame, windowSize, place, smooth, windowRead) ? windowRead.image : null;
        } finally {
            bgfx_set_view_frame_buffer(CAPTURE_VIEW, BGFX_INVALID_HANDLE);
            bgfx_destroy_frame_buffer(target);
        }
    }

    private boolean show(BufferedImage frame, Dimension windowSize, Rectangle place, boolean smooth,
            ReadBack windowRead) {
        if (frame != null) {
            fitPictureTexture(frame.getWidth(), frame.getHeight());
            if (!uploadPicture(frame, picture, upload, pictureTextureWidth, pictureTextureHeight)) {
                return false;
            }
            source = picture;
        }
        boolean pictureFrame = frame != null;
        if (anyShown && pictureFrame != lastShownPicture) {
            noteChange(pictureFrame ? "a picture after the scene" : "the scene after a picture");
        }
        lastShownPicture = pictureFrame;
        fitSwapChain(windowSize);
        if (source == sceneColor) {
            // drawn at the size it is shown, or larger or smaller and filtered
            smooth = place.width != sceneWidth || place.height != sceneHeight;
        }
        float glowBoost = source == sceneColor ? GLOW_BOOST : 0F; // only the scene says what glows
        if (!drawFrame(SCREEN_VIEW, place, smooth, glowBoost)) {
            return false;
        }
        if (windowRead != null) {
            drawFrame(CAPTURE_VIEW, place, smooth, 0F);
        }
        ReadBack scene = sceneRead;
        sceneRead = null;
        int ready = 0;
        if (scene != null) {
            ready = Math.max(ready, scene.request());
        }
        if (windowRead != null) {
            ready = Math.max(ready, windowRead.request());
        }
        long frameStart = System.nanoTime();
        int done = bgfx_frame(BGFX_FRAME_NONE);
        noteFrameShown(System.nanoTime() - frameStart, windowRead != null);
        for (int i = 0; done < ready && i < READ_BACK_FRAMES; i++) {
            // keep the window showing the frame meanwhile
            drawFrame(SCREEN_VIEW, place, smooth, glowBoost);
            done = bgfx_frame(BGFX_FRAME_NONE);
        }
        if (scene != null) {
            sceneImage = scene.finish(done);
            glowImage = scene.glow;
        }
        if (windowRead != null) {
            windowRead.finish(done);
        }
        return true;
    }

    /** How long handing the last frame to the card took on this thread (0 for a test's screenshot). */
    public long lastFrameNanos() {
        return lastFrameNanos;
    }

    /** What changed on the card just before the last frame (a new swap chain, say), or empty for nothing. */
    public String lastFrameChanges() {
        return lastFrameChanges;
    }

    /** Something changed on the card since the last frame, to name if the next one is slow to show. */
    private void noteChange(String what) {
        if (!changes.isEmpty()) {
            changes.append(", ");
        }
        changes.append(what);
    }

    /**
     * A frame has gone to the card, which took {@code nanos} over it; the
     * frame log names a slow one with what changed before it (a
     * screenshot's read-back is a test's own, so it counts as none).
     */
    private void noteFrameShown(long nanos, boolean screenshot) {
        anyShown = true;
        lastFrameNanos = screenshot ? 0L : nanos;
        lastFrameChanges = changes.isEmpty() ? "" : changes.toString();
        changes.setLength(0);
    }

    /** Draws the frame into a view as it goes to the screen: as it is, or in HDR brightened by its glow. */
    private boolean drawFrame(int view, Rectangle place, boolean smooth, float glowBoost) {
        setDownscale(place);
        if (!hdr) {
            return drawRectangle(view, pictureProgram, source, width, height, place, smooth, OPAQUE, sourcePart());
        }
        hdrValues[0] = whiteScale();
        hdrValues[1] = glowBoost;
        hdrValues[2] = peak();
        bgfx_set_uniform(hdrUniform, hdrValues, 1);
        return drawRectangle(view, hdrProgram, source, width, height, place, smooth, OPAQUE, sourcePart());
    }

    /**
     * The scene is shown smaller than it was drawn (supersampling, or a
     * window smaller than the picture): the present shaders then average the
     * texels under each pixel instead of sampling once.
     */
    private boolean downscaling(Rectangle place) {
        return source == sceneColor && place.width > 0 && place.height > 0
                && (sceneWidth > place.width * 1.01F || sceneHeight > place.height * 1.01F);
    }

    private void setDownscale(Rectangle place) {
        boolean on = downscaling(place);
        java.util.Arrays.fill(downValues, 0F);
        if (on) {
            downValues[0] = targetWidth;
            downValues[1] = targetHeight;
            downValues[2] = sceneWidth / (float) place.width;
            downValues[3] = sceneHeight / (float) place.height;
            downValues[4] = sceneWidth;
            downValues[5] = sceneHeight;
            downValues[6] = 1F;
        }
        downNow = on;
        bgfx_set_uniform(downUniform, downValues, 2);
    }

    /** The frame being presented is averaged down, see {@link #setDownscale}. */
    private boolean downNow;

    /** Texture coordinates of all of a texture. */
    private static final float[] WHOLE = {0F, 0F, 1F, 1F};

    /**
     * The texture coordinates of what is shown: all of a picture, or the part
     * of the scene target the scene was drawn in. A part is taken from the
     * centre of its first pixels to the centre of its last, so filtering
     * never mixes in a pixel outside it, left over from a larger scene.
     */
    private float[] sourcePart() {
        if (source != sceneColor || sceneWidth == targetWidth && sceneHeight == targetHeight) {
            return WHOLE;
        }
        if (downNow) {
            // averaged down: each pixel covers its share of the part, edge to edge
            part[0] = 0F;
            part[1] = 0F;
            part[2] = sceneWidth / (float) targetWidth;
            part[3] = sceneHeight / (float) targetHeight;
            return part;
        }
        part[0] = 0.5F / targetWidth;
        part[1] = 0.5F / targetHeight;
        part[2] = (sceneWidth - 0.5F) / targetWidth;
        part[3] = (sceneHeight - 0.5F) / targetHeight;
        return part;
    }

    private final float[] part = new float[4];

    /** The widest or tallest texture the card can draw into. */
    public int maxTextureSize() {
        return bgfx_get_caps().limits().maxTextureSize();
    }

    /** The share of the scene target's width the last scene was drawn in, below 1 with dynamic resolution. */
    public float sceneScale() {
        return targetWidth == 0 ? 1F : sceneWidth / (float) targetWidth;
    }

    /** Paper white in scRGB, where 1.0 is 80 nits. */
    private float whiteScale() {
        return paperWhite() / DisplayColor.SDR_WHITE_NITS;
    }

    /**
     * True while {@link #hudUpload} holds what the HUD texture does, so only
     * the rows that changed need sending: most of a HUD is transparent and
     * the same from tick to tick, and the whole of it is 3.7 MB at 1280x720.
     */
    private boolean hudHeld;
    /** Unchanged rows between two changed ones that are sent along rather than starting another upload. */
    private static final int HUD_ROW_GAP = 8;

    /** The HUD is sent whole next time: its texture is new, or the picture changed. */
    private void invalidateHud() {
        uploadedHud = -1L;
        hudHeld = false;
    }

    /** Sends the rows of the HUD that differ from what the texture holds; false if it is not the texture's size. */
    private boolean uploadHud(BufferedImage image) {
        int width = hudTextureWidth;
        int height = hudTextureHeight;
        DataBufferInt pixels = packedPixels(image, width, height);
        if (pixels == null) {
            return false;
        }
        if (!hudHeld) {
            hudHeld = true;
            return uploadPicture(image, hud, hudUpload, width, height);
        }
        int[] from = pixels.getData();
        int offset = pixels.getOffset();
        IntBuffer held = hudUpload.clear();
        int bandStart = -1;
        int bandEnd = -1;
        for (int y = 0; y < height; y++) {
            int at = y * width;
            if (held.slice(at, width).mismatch(IntBuffer.wrap(from, offset + at, width)) < 0) {
                continue;
            }
            held.put(at, from, offset + at, width);
            if (bandStart >= 0 && y - bandEnd > HUD_ROW_GAP) {
                uploadHudRows(bandStart, bandEnd);
                bandStart = -1;
            }
            if (bandStart < 0) {
                bandStart = y;
            }
            bandEnd = y + 1;
        }
        if (bandStart >= 0) {
            uploadHudRows(bandStart, bandEnd);
        }
        return true;
    }

    /** Sends rows {@code [from, to)} of {@link #hudUpload}, which holds them already, to the HUD texture. */
    private void uploadHudRows(int from, int to) {
        int width = hudTextureWidth;
        int count = (to - from) * width;
        // a reference into the buffer, which bgfx reads at bgfx_frame(): bands never overlap
        IntBuffer rows = hudUpload.slice(from * width, count);
        bgfx_update_texture_2d(hud, 0, 0, 0, from, width, to - from,
                bgfx_make_ref(MemoryUtil.memByteBuffer(rows)), width * 4);
        hudBytes += count * 4L;
    }

    /** An image's pixels, if it is a packed-int image {@code width} x {@code height} with no padding; else null. */
    private static DataBufferInt packedPixels(BufferedImage image, int width, int height) {
        return image.getRaster().getDataBuffer() instanceof DataBufferInt pixels
                && image.getWidth() == width && image.getHeight() == height
                && image.getRaster().getSampleModel().getWidth() == width ? pixels : null;
    }

    /** HUD bytes sent to the card since the last statistics. */
    private long hudBytes;

    /** Copies an image into a texture; false if it is not a packed-int image of the texture's size. */
    private static boolean uploadPicture(BufferedImage frame, short texture, IntBuffer buffer, int width, int height) {
        DataBufferInt pixels = packedPixels(frame, width, height);
        if (pixels == null) {
            return false;
        }
        buffer.clear();
        buffer.put(pixels.getData(), pixels.getOffset(), width * height).flip();
        // a reference, not a copy: the buffer is only reused after bgfx_frame() has consumed it
        bgfx_update_texture_2d(texture, 0, 0, 0, 0, width, height,
                bgfx_make_ref(MemoryUtil.memByteBuffer(buffer)), width * 4);
        return true;
    }

    /**
     * Makes the picture's texture the size of the picture drawn now: the UI
     * resolution can draw the menus larger than the game's 1280x720, and
     * changes with the window. The old one has been drawn from by now, as
     * bgfx works on this thread.
     */
    private void fitPictureTexture(int width, int height) {
        if (width == pictureTextureWidth && height == pictureTextureHeight) {
            return;
        }
        short made = bgfx_create_texture_2d(width, height, false, 1, BGFX_TEXTURE_FORMAT_BGRA8, CLAMP, null, 0);
        if (!isValid(made)) {
            return; // the upload then fails, and the frame is drawn in software
        }
        boolean shown = source == picture;
        destroyTexture(picture);
        picture = made;
        if (shown) {
            source = picture;
        }
        pictureTextureWidth = width;
        pictureTextureHeight = height;
        noteChange("a new picture texture " + width + "x" + height);
        upload = free(upload);
        upload = MemoryUtil.memAllocInt(width * height);
    }

    /** As {@link #fitPictureTexture}, for the HUD drawn over races. */
    private void fitHudTexture(int width, int height) {
        if (width == hudTextureWidth && height == hudTextureHeight) {
            return;
        }
        short made = bgfx_create_texture_2d(width, height, false, 1, BGFX_TEXTURE_FORMAT_BGRA8, CLAMP, null, 0);
        if (!isValid(made)) {
            return;
        }
        destroyTexture(hud);
        hud = made;
        hudTextureWidth = width;
        hudTextureHeight = height;
        noteChange("a new HUD texture " + width + "x" + height);
        hudUpload = free(hudUpload);
        hudUpload = MemoryUtil.memAllocInt(width * height);
        invalidateHud();
    }

    /**
     * Resizes the swap chain to the window, switches vsync, and switches
     * between SDR and HDR (an scRGB swap chain, which bgfx gives the scRGB
     * colour space), when any of them changed.
     */
    private void fitSwapChain(Dimension windowSize) {
        checkDisplayColor();
        int newWidth = Math.max(1, windowSize.width);
        int newHeight = Math.max(1, windowSize.height);
        int newFlags = vsync ? BGFX_RESET_VSYNC : BGFX_RESET_NONE;
        int newFormat = hdr ? BGFX_TEXTURE_FORMAT_RGBA16F : BGFX_TEXTURE_FORMAT_RGBA8;
        if (newWidth != width || newHeight != height || newFlags != resetFlags || newFormat != swapFormat) {
            width = newWidth;
            height = newHeight;
            resetFlags = newFlags;
            swapFormat = newFormat;
            bgfx_reset(width, height, resetFlags, swapFormat);
            noteChange("the swap chain made " + width + "x" + height + (vsync ? " with vsync" : "")
                    + (hdr ? " in HDR" : ""));
        }
    }

    /**
     * Every few seconds, asks Windows whether HDR is on for the monitor the
     * window is on, and how bright it shows SDR white: either can change
     * while the game runs, from Windows' display settings or by moving the
     * window to another monitor.
     */
    private void checkDisplayColor() {
        long now = System.nanoTime();
        if (colorCheckedAt != 0L && now - colorCheckedAt < COLOR_CHECK_NANOS) {
            return;
        }
        colorCheckedAt = now;
        DisplayColor monitor = hdrMode == HDR_OFF ? null : DisplayColor.of(window);
        monitorHdr = monitor != null && monitor.hdr;
        DisplayColor wanted = hdrMode == HDR_FORCE ? DisplayColor.forced(monitor) : monitor;
        boolean on = wanted != null && wanted.hdr && hdrBackBuffer;
        if (on != hdr || on && !wanted.equals(displayColor)) {
            HLogger.info(on ? "HDR output on: " + wanted : "HDR output off");
        }
        hdr = on;
        displayColor = wanted;
        hdrStatus = describeHdr();
    }

    /**
     * Submits a textured rectangle at {@code place} to a view of
     * {@code viewWidth} x {@code viewHeight}, showing the part {@code uv}
     * (left, top, right, bottom texture coordinates) of the texture.
     */
    private boolean drawRectangle(int view, short program, short texture, int viewWidth, int viewHeight,
            Rectangle place, boolean smooth, long state, float[] uv) {
        if (view != SCENE_VIEW) {
            bgfx_set_view_rect(view, 0, 0, viewWidth, viewHeight);
        }
        if (bgfx_get_avail_transient_vertex_buffer(4, pictureLayout) < 4) {
            return false;
        }
        BGFXTransientVertexBuffer quad = BGFXTransientVertexBuffer.calloc();
        try {
            bgfx_alloc_transient_vertex_buffer(quad, 4, pictureLayout);
            ByteBuffer vertices = quad.data().order(ByteOrder.LITTLE_ENDIAN);
            float left = 2F * place.x / viewWidth - 1F;
            float right = 2F * (place.x + place.width) / viewWidth - 1F;
            float top = 1F - 2F * place.y / viewHeight;
            float bottom = 1F - 2F * (place.y + place.height) / viewHeight;
            vertices.putFloat(left).putFloat(top).putFloat(uv[0]).putFloat(uv[1]);
            vertices.putFloat(right).putFloat(top).putFloat(uv[2]).putFloat(uv[1]);
            vertices.putFloat(left).putFloat(bottom).putFloat(uv[0]).putFloat(uv[3]);
            vertices.putFloat(right).putFloat(bottom).putFloat(uv[2]).putFloat(uv[3]);
            bgfx_set_transient_vertex_buffer(0, quad, 0, 4);
        } finally {
            quad.free();
        }
        long filter = smooth ? 0L : POINT;
        bgfx_set_texture(0, sampler, texture, (int) (CLAMP | filter));
        bgfx_set_state(state | BGFX_STATE_PT_TRISTRIP, 0);
        bgfx_submit(view, program, 0, BGFX_DISCARD_ALL);
        return true;
    }

    /** Stops bgfx and frees everything. Safe to call more than once. */
    public void close() {
        if (started) {
            destroyTexture(picture);
            destroyTexture(hud);
            if (isValid(sceneBuffer)) {
                bgfx_destroy_frame_buffer(sceneBuffer);
            }
            destroyTexture(pointsTexture);
            if (isValid(projectedBuffer)) {
                bgfx_destroy_dynamic_vertex_buffer(projectedBuffer);
            }
            for (short uniform : new short[] {sampler, hdrUniform, downUniform, cameraUniform, carUniform,
                    pointsSampler}) {
                if (isValid(uniform)) {
                    bgfx_destroy_uniform(uniform);
                }
            }
            for (short program : new short[] {pictureProgram, overlayProgram, colorProgram, hdrProgram,
                    projectedProgram}) {
                if (isValid(program)) {
                    bgfx_destroy_program(program);
                }
            }
            bgfx_shutdown();
            started = false;
        }
        picture = BGFX_INVALID_HANDLE;
        hud = BGFX_INVALID_HANDLE;
        hudHeld = false;
        sceneBuffer = BGFX_INVALID_HANDLE;
        sceneColor = BGFX_INVALID_HANDLE;
        sampler = BGFX_INVALID_HANDLE;
        hdrUniform = BGFX_INVALID_HANDLE;
        downUniform = BGFX_INVALID_HANDLE;
        hdrProgram = BGFX_INVALID_HANDLE;
        pictureProgram = BGFX_INVALID_HANDLE;
        overlayProgram = BGFX_INVALID_HANDLE;
        colorProgram = BGFX_INVALID_HANDLE;
        projectedProgram = BGFX_INVALID_HANDLE;
        cameraUniform = BGFX_INVALID_HANDLE;
        carUniform = BGFX_INVALID_HANDLE;
        pointsSampler = BGFX_INVALID_HANDLE;
        pointsTexture = BGFX_INVALID_HANDLE;
        pointsRows = 0;
        projectedBuffer = BGFX_INVALID_HANDLE;
        projectedVersion = -1L;
        if (projectedLayout != null) {
            projectedLayout.free();
            projectedLayout = null;
        }
        if (pictureLayout != null) {
            pictureLayout.free();
            pictureLayout = null;
        }
        if (colorLayout != null) {
            colorLayout.free();
            colorLayout = null;
        }
        if (transientBuffer != null) {
            transientBuffer.free();
            transientBuffer = null;
        }
        upload = free(upload);
        hudUpload = free(hudUpload);
        if (geometry != null) {
            geometry.free();
            geometry = null;
        }
    }

    private static void destroyTexture(short texture) {
        if (isValid(texture)) {
            bgfx_destroy_texture(texture);
        }
    }

    private static IntBuffer free(IntBuffer buffer) {
        if (buffer != null) {
            MemoryUtil.memFree(buffer);
        }
        return null;
    }

    /**
     * A texture copied back from the graphics card, for tests: an 8-bit BGRA
     * one, or with a white scale an scRGB one, turned back into sRGB.
     */
    private static final class ReadBack {
        private final short texture;
        private final int width;
        private final int height;
        /** For an scRGB texture, where SDR white is in it; 0 for an 8-bit one. */
        private final float white;
        private final boolean keepGlow;
        private short copy = BGFX_INVALID_HANDLE;
        private IntBuffer pixels;
        private int ready;
        private BufferedImage image;
        /** With keepGlow, the texture's alpha as grey. */
        private BufferedImage glow;

        ReadBack(short texture, int width, int height, float white, boolean keepGlow) {
            this.texture = texture;
            this.width = width;
            this.height = height;
            this.white = white;
            this.keepGlow = keepGlow;
        }

        /** Asks for the copy in the coming frame; returns the frame number it will be ready at. */
        int request() {
            boolean half = white > 0F;
            copy = bgfx_create_texture_2d(width, height, false, 1,
                    half ? BGFX_TEXTURE_FORMAT_RGBA16F : BGFX_TEXTURE_FORMAT_BGRA8,
                    BGFX_TEXTURE_BLIT_DST | BGFX_TEXTURE_READ_BACK, null, 0);
            pixels = MemoryUtil.memAllocInt(width * height * (half ? 2 : 1));
            bgfx_blit(COPY_VIEW, copy, 0, 0, 0, 0, texture, 0, 0, 0, 0, width, height, 1);
            ready = bgfx_read_texture(copy, pixels, 0, 0);
            return ready;
        }

        BufferedImage finish(int doneFrame) {
            try {
                if (doneFrame >= ready && pixels != null) {
                    image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
                    int[] rgb = ((DataBufferInt) image.getRaster().getDataBuffer()).getData();
                    if (white > 0F) {
                        fromScRgb(rgb);
                    } else {
                        pixels.get(rgb); // BGRA = 0xAARRGGBB
                    }
                    if (keepGlow) {
                        glow = new BufferedImage(width, height, BufferedImage.TYPE_BYTE_GRAY);
                        byte[] grey = ((java.awt.image.DataBufferByte) glow.getRaster().getDataBuffer()).getData();
                        for (int i = 0; i < rgb.length; i++) {
                            grey[i] = (byte) (rgb[i] >>> 24);
                        }
                    }
                }
                return image;
            } finally {
                if (isValid(copy)) {
                    bgfx_destroy_texture(copy);
                }
                pixels = free(pixels);
            }
        }

        /** Half-float RGBA, SDR white at {@link #white}, back to 8-bit sRGB. */
        private void fromScRgb(int[] rgb) {
            for (int i = 0; i < rgb.length; i++) {
                int rg = pixels.get(2 * i);
                int ba = pixels.get(2 * i + 1);
                rgb[i] = 0xff000000 | toSrgb(rg & 0xffff) << 16 | toSrgb(rg >>> 16) << 8 | toSrgb(ba & 0xffff);
            }
        }

        private int toSrgb(int half) {
            float v = Math.max(0F, Math.min(1F, Float.float16ToFloat((short) half) / white));
            double c = v <= 0.0031308F ? 12.92D * v : 1.055D * Math.pow(v, 1D / 2.4D) - 0.055D;
            return (int) Math.round(c * 255D);
        }
    }
}
