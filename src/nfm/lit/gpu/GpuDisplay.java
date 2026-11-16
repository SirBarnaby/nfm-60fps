package nfm.lit.gpu;

import static org.lwjgl.bgfx.BGFX.*;

import fallk.logmaster.HLogger;
import java.awt.Dimension;
import java.awt.Rectangle;
import java.awt.image.BufferedImage;
import java.awt.image.DataBufferInt;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.IntBuffer;
import org.lwjgl.bgfx.BGFXInit;
import org.lwjgl.bgfx.BGFXPlatform;
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

    private static final String PICTURE_VS = """
            struct In { float2 pos : POSITION; float2 uv : TEXCOORD0; };
            struct Out { float4 pos : SV_POSITION; float2 uv : TEXCOORD0; };
            Out main(In i) { Out o; o.pos = float4(i.pos, 0.0, 1.0); o.uv = i.uv; return o; }
            """;
    private static final String PICTURE_FS = """
            Texture2D s_pictureTexture : register(t0);
            SamplerState s_pictureSampler : register(s0);
            float4 main(float4 pos : SV_POSITION, float2 uv : TEXCOORD0) : SV_TARGET
            {
                return float4(s_pictureTexture.Sample(s_pictureSampler, uv).rgb, 1.0);
            }
            """;
    /**
     * Shows the picture on an scRGB swap chain, whose 1.0 is 80 nits: its
     * sRGB colours made linear and scaled to where Windows puts SDR white
     * (u_hdr.x), and the glow in its alpha brightened by up to u_hdr.y more.
     */
    private static final String HDR_FS = """
            Texture2D s_pictureTexture : register(t0);
            SamplerState s_pictureSampler : register(s0);
            cbuffer Uniforms : register(b0) { float4 u_hdr; };
            float3 toLinear(float3 c)
            {
                return c <= 0.04045 ? c / 12.92 : pow(max((c + 0.055) / 1.055, 0.0), 2.4);
            }
            float4 main(float4 pos : SV_POSITION, float2 uv : TEXCOORD0) : SV_TARGET
            {
                float4 c = s_pictureTexture.Sample(s_pictureSampler, uv);
                return float4(toLinear(c.rgb) * u_hdr.x * (1.0 + c.a * u_hdr.y), 1.0);
            }
            """;
    private static final String OVERLAY_FS = """
            Texture2D s_pictureTexture : register(t0);
            SamplerState s_pictureSampler : register(s0);
            float4 main(float4 pos : SV_POSITION, float2 uv : TEXCOORD0) : SV_TARGET
            {
                return s_pictureTexture.Sample(s_pictureSampler, uv);
            }
            """;
    private static final String COLOR_VS = """
            struct In { float2 pos : POSITION; float4 col : COLOR0; float2 edge : TEXCOORD0; };
            struct Out { float4 pos : SV_POSITION; float4 col : COLOR0; float2 edge : TEXCOORD0; };
            Out main(In i)
            {
                Out o; o.pos = float4(i.pos, 0.0, 1.0); o.col = i.col; o.edge = i.edge; return o;
            }
            """;
    /**
     * Solid colour; on an antialiased line, edge.x runs -1 to 1 across it and
     * edge.y is its half width in pixels, so a pixel at distance d from its
     * middle is covered by half width - d.
     */
    private static final String COLOR_FS = """
            float4 main(float4 pos : SV_POSITION, float4 col : COLOR0, float2 edge : TEXCOORD0) : SV_TARGET
            {
                return float4(col.rgb, col.a * saturate(edge.y * (1.0 - abs(edge.x))));
            }
            """;

    private final int pictureWidth;
    private final int pictureHeight;
    private final long window;

    private boolean started;
    private BGFXVertexLayout pictureLayout;
    private BGFXVertexLayout colorLayout;
    private short pictureProgram = BGFX_INVALID_HANDLE;
    private short overlayProgram = BGFX_INVALID_HANDLE;
    private short colorProgram = BGFX_INVALID_HANDLE;
    private short hdrProgram = BGFX_INVALID_HANDLE;
    private short sampler = BGFX_INVALID_HANDLE;
    private short hdrUniform = BGFX_INVALID_HANDLE;
    private final float[] hdrValues = new float[4];
    private short picture = BGFX_INVALID_HANDLE;
    private IntBuffer upload;
    private BGFXTransientVertexBuffer transientBuffer;
    private int width;
    private int height;
    private int resetFlags = BGFX_RESET_NONE;
    private boolean vsync;

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
    private int sceneWidth;
    private int sceneHeight;
    private short sceneColor = BGFX_INVALID_HANDLE;
    /* samples per pixel asked for, and those the scene target has */
    private int wantedSamples = 1;
    private int samples = 1;
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
     * Starts bgfx on the window (an HWND), or returns null and logs why when
     * it cannot: the caller then keeps drawing in software.
     */
    public static GpuDisplay open(long window, Dimension size, int pictureWidth, int pictureHeight) {
        GpuDisplay display = new GpuDisplay(window, pictureWidth, pictureHeight);
        try {
            display.start(Math.max(1, size.width), Math.max(1, size.height));
            HLogger.info("Drawing through the graphics card (Direct3D 11)");
            return display;
        } catch (Throwable ex) {
            HLogger.warn("Cannot draw through the graphics card, drawing in software: " + ex);
            display.close();
            return null;
        }
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

    /** Whether frames go out in HDR, and at what SDR white, for the console. */
    public String hdrStatus() {
        return hdrStatus;
    }

    private String describeHdr() {
        if (hdr) {
            return "on, SDR white at " + Math.round(displayColor.whiteNits) + " nits"
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
        byte[] pictureVs = hlsl.compile("picture_vs", PICTURE_VS, "vs_5_0");
        byte[] pictureFs = hlsl.compile("picture_fs", PICTURE_FS, "ps_5_0");
        byte[] overlayFs = hlsl.compile("overlay_fs", OVERLAY_FS, "ps_5_0");
        byte[] colorVs = hlsl.compile("color_vs", COLOR_VS, "vs_5_0");
        byte[] colorFs = hlsl.compile("color_fs", COLOR_FS, "ps_5_0");
        byte[] hdrFs = hlsl.compile("hdr_fs", HDR_FS, "ps_5_0");

        BGFXPlatform.bgfx_render_frame(-1); // no render thread: bgfx works on the calling thread
        try (BGFXInit init = BGFXInit.calloc()) {
            bgfx_init_ctor(init);
            init.type(BGFX_RENDERER_TYPE_DIRECT3D11);
            init.resolution(r -> r.width(width).height(height).reset(resetFlags).formatColor(swapFormat));
            init.platformData(p -> p.nwh(window));
            if (!bgfx_init(init)) {
                throw new IllegalStateException("bgfx_init failed");
            }
        }
        started = true;
        this.width = width;
        this.height = height;
        if (bgfx_get_renderer_type() != BGFX_RENDERER_TYPE_DIRECT3D11) {
            throw new IllegalStateException("Direct3D 11 is not available");
        }

        int[] pictureAttributes = {BgfxShaderBinary.ATTRIB_POSITION, BgfxShaderBinary.ATTRIB_TEXCOORD0};
        pictureProgram = program(BgfxShaderBinary.vertex(pictureVs, pictureAttributes),
                BgfxShaderBinary.fragment(pictureFs, "s_picture"));
        overlayProgram = program(BgfxShaderBinary.vertex(pictureVs, pictureAttributes),
                BgfxShaderBinary.fragment(overlayFs, "s_picture"));
        colorProgram = program(
                BgfxShaderBinary.vertex(colorVs, BgfxShaderBinary.ATTRIB_POSITION, BgfxShaderBinary.ATTRIB_COLOR0,
                        BgfxShaderBinary.ATTRIB_TEXCOORD0),
                BgfxShaderBinary.fragment(colorFs, null));
        sampler = bgfx_create_uniform("s_picture", BGFX_UNIFORM_TYPE_SAMPLER, 1);
        hdrUniform = bgfx_create_uniform("u_hdr", BGFX_UNIFORM_TYPE_VEC4, 1); // before the shader that reads it
        hdrProgram = program(BgfxShaderBinary.vertex(pictureVs, pictureAttributes),
                BgfxShaderBinary.fragment(hdrFs, "s_picture", "u_hdr"));
        hdrBackBuffer = (bgfx_get_caps().formats(BGFX_TEXTURE_FORMAT_RGBA16F)
                & BGFX_CAPS_FORMAT_TEXTURE_BACKBUFFER) != 0;
        pictureLayout = BGFXVertexLayout.calloc();
        bgfx_vertex_layout_begin(pictureLayout, BGFX_RENDERER_TYPE_DIRECT3D11);
        bgfx_vertex_layout_add(pictureLayout, BGFX_ATTRIB_POSITION, 2, BGFX_ATTRIB_TYPE_FLOAT, false, false);
        bgfx_vertex_layout_add(pictureLayout, BGFX_ATTRIB_TEXCOORD0, 2, BGFX_ATTRIB_TYPE_FLOAT, false, false);
        bgfx_vertex_layout_end(pictureLayout);
        colorLayout = BGFXVertexLayout.calloc();
        bgfx_vertex_layout_begin(colorLayout, BGFX_RENDERER_TYPE_DIRECT3D11);
        bgfx_vertex_layout_add(colorLayout, BGFX_ATTRIB_POSITION, 2, BGFX_ATTRIB_TYPE_FLOAT, false, false);
        bgfx_vertex_layout_add(colorLayout, BGFX_ATTRIB_COLOR0, 4, BGFX_ATTRIB_TYPE_UINT8, true, false);
        bgfx_vertex_layout_add(colorLayout, BGFX_ATTRIB_TEXCOORD0, 2, BGFX_ATTRIB_TYPE_FLOAT, false, false);
        bgfx_vertex_layout_end(colorLayout);
        picture = bgfx_create_texture_2d(pictureWidth, pictureHeight, false, 1, BGFX_TEXTURE_FORMAT_BGRA8,
                CLAMP, null, 0);
        if (!isValid(sampler) || !isValid(hdrUniform) || !isValid(picture)) {
            throw new IllegalStateException("creating GPU resources failed");
        }
        source = picture;
        upload = MemoryUtil.memAllocInt(pictureWidth * pictureHeight);
        transientBuffer = BGFXTransientVertexBuffer.calloc();
        bgfx_set_view_clear(SCREEN_VIEW, BGFX_CLEAR_COLOR, BLACK, 1F, 0);
        bgfx_set_view_clear(CAPTURE_VIEW, BGFX_CLEAR_COLOR, BLACK, 1F, 0);
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
     * @return false if it could not be drawn (the caller should fall back)
     */
    public boolean drawScene(SceneList scene, BufferedImage hudImage, long version, boolean flash,
            boolean capture, Dimension size) {
        fitSceneTarget(Math.max(1, size.width), Math.max(1, size.height));
        if (hudImage != null && (version < 0L || version != uploadedHud)) {
            if (!uploadPicture(hudImage, hud, hudUpload)) {
                return false;
            }
            uploadedHud = version;
        }
        long buildStart = System.nanoTime();
        geometry.build(scene, pictureWidth, pictureHeight, sceneWidth / (float) pictureWidth, version);
        buildNanos += System.nanoTime() - buildStart;
        builds++;
        int vertexCount = geometry.vertexCount();
        bgfx_set_view_rect(SCENE_VIEW, 0, 0, sceneWidth, sceneHeight);
        bgfx_touch(SCENE_VIEW);
        if (vertexCount > 0) {
            if (bgfx_get_avail_transient_vertex_buffer(vertexCount, colorLayout) < vertexCount) {
                return false;
            }
            bgfx_alloc_transient_vertex_buffer(transientBuffer, vertexCount, colorLayout);
            geometry.copyVertices(transientBuffer.data());
            submitScene();
        }
        if (hudImage != null) {
            boolean scaled = sceneWidth != pictureWidth || sceneHeight != pictureHeight;
            drawRectangle(SCENE_VIEW, overlayProgram, hud, sceneWidth, sceneHeight,
                    new Rectangle(0, 0, sceneWidth, sceneHeight), scaled, OPAQUE | HUD_BLEND);
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
        if (geometry == null) {
            return "";
        }
        String text = geometry.draws() + " draws, " + geometry.vertexCount() + " vertices, "
                + geometry.evenOddPolygons() + " even-odd, " + geometry.antialiased() + " antialiased"
                + String.format(", triangles made in %.2f ms", builds == 0 ? 0D : buildNanos / 1e6D / builds);
        buildNanos = 0L;
        builds = 0;
        return text;
    }

    /** Which HUD the HUD texture holds, see {@link #drawScene}. */
    private long uploadedHud = -1L;

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
            default -> 0L;
        };
    }

    /** The most samples per pixel up to {@code wanted} the card can draw the scene with. */
    private static int supportedSamples(int wanted) {
        int n = Integer.highestOneBit(Math.max(1, Math.min(8, wanted)));
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
        int newSamples = wantedSamples == samples ? samples : supportedSamples(wantedSamples);
        if (isValid(sceneBuffer) && newWidth == sceneWidth && newHeight == sceneHeight && newSamples == samples) {

            return;
        }
        if (isValid(sceneBuffer)) {
            bgfx_destroy_frame_buffer(sceneBuffer); // and its two textures
        }
        sceneWidth = newWidth;
        sceneHeight = newHeight;
        samples = newSamples;
        wantedSamples = newSamples;
        // multisampled, the colour is resolved into a plain texture when the scene view is done
        long msaa = msaaFlag(samples);
        sceneColor = bgfx_create_texture_2d(sceneWidth, sceneHeight, false, 1, BGFX_TEXTURE_FORMAT_BGRA8,
                BGFX_TEXTURE_RT | CLAMP | msaa, null, 0);
        short stencil = bgfx_create_texture_2d(sceneWidth, sceneHeight, false, 1, BGFX_TEXTURE_FORMAT_D24S8,
                BGFX_TEXTURE_RT_WRITE_ONLY | msaa, null, 0);
        sceneBuffer = bgfx_create_frame_buffer_from_handles(new short[] {sceneColor, stencil}, true);
        if (!isValid(hud)) {
            hud = bgfx_create_texture_2d(pictureWidth, pictureHeight, false, 1, BGFX_TEXTURE_FORMAT_BGRA8,
                    CLAMP, null, 0);
            hudUpload = MemoryUtil.memAllocInt(pictureWidth * pictureHeight);
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

    private void submitScene() {
        int stencilFlip = BGFX_STENCIL_TEST_ALWAYS | BGFX_STENCIL_FUNC_REF(0) | BGFX_STENCIL_FUNC_RMASK(0xff)
                | BGFX_STENCIL_OP_FAIL_S_KEEP | BGFX_STENCIL_OP_FAIL_Z_KEEP | BGFX_STENCIL_OP_PASS_Z_INVERT;
        int stencilOdd = BGFX_STENCIL_TEST_NOTEQUAL | BGFX_STENCIL_FUNC_REF(0) | BGFX_STENCIL_FUNC_RMASK(0xff)
                | BGFX_STENCIL_OP_FAIL_S_KEEP | BGFX_STENCIL_OP_FAIL_Z_KEEP | BGFX_STENCIL_OP_PASS_Z_ZERO;
        for (int d = 0; d < geometry.draws(); d++) {
            int start = geometry.start(d);
            int count = geometry.count(d);
            if (count == 0) {
                continue;
            }
            int glow = geometry.glow(d); // the blend factor's alpha
            if (geometry.kind(d) == SceneGeometry.PLAIN) {
                bgfx_set_transient_vertex_buffer(0, transientBuffer, start, count);
                bgfx_set_state(OPAQUE | SCENE_BLEND, glow); // only antialiased lines are not opaque
                bgfx_submit(SCENE_VIEW, colorProgram, 0, BGFX_DISCARD_ALL);
            } else {
                // flip the stencil where the fan covers, then colour where it is odd and clear it again
                bgfx_set_transient_vertex_buffer(0, transientBuffer, start, count);
                bgfx_set_stencil(stencilFlip, BGFX_STENCIL_NONE);
                bgfx_set_state(0L, 0);
                bgfx_submit(SCENE_VIEW, colorProgram, 0, BGFX_DISCARD_ALL);
                bgfx_set_transient_vertex_buffer(0, transientBuffer, start, count);
                bgfx_set_stencil(stencilOdd, BGFX_STENCIL_NONE);
                bgfx_set_state(OPAQUE | COVER_BLEND, glow);
                bgfx_submit(SCENE_VIEW, colorProgram, 0, BGFX_DISCARD_ALL);
            }
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
            if (!uploadPicture(frame, picture, upload)) {
                return false;
            }
            source = picture;
        }
        fitSwapChain(windowSize);
        if (source == sceneColor && place.width == sceneWidth && place.height == sceneHeight) {
            smooth = false; // drawn at the size it is shown
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
        int done = bgfx_frame(BGFX_FRAME_NONE);
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

    /** Draws the frame into a view as it goes to the screen: as it is, or in HDR brightened by its glow. */
    private boolean drawFrame(int view, Rectangle place, boolean smooth, float glowBoost) {
        if (!hdr) {
            return drawRectangle(view, pictureProgram, source, width, height, place, smooth, OPAQUE);
        }
        hdrValues[0] = whiteScale();
        hdrValues[1] = glowBoost;
        bgfx_set_uniform(hdrUniform, hdrValues, 1);
        return drawRectangle(view, hdrProgram, source, width, height, place, smooth, OPAQUE);
    }

    /** SDR white in scRGB, where 1.0 is 80 nits. */
    private float whiteScale() {
        return displayColor.whiteNits / DisplayColor.SDR_WHITE_NITS;
    }

    /** Copies an image into a texture; false if it is not a packed-int image of the picture's size. */
    private boolean uploadPicture(BufferedImage frame, short texture, IntBuffer buffer) {
        if (!(frame.getRaster().getDataBuffer() instanceof DataBufferInt pixels)
                || frame.getWidth() != pictureWidth || frame.getHeight() != pictureHeight
                || frame.getRaster().getSampleModel().getWidth() != pictureWidth) {
            return false;
        }
        buffer.clear();
        buffer.put(pixels.getData(), pixels.getOffset(), pictureWidth * pictureHeight).flip();
        // a reference, not a copy: the buffer is only reused after bgfx_frame() has consumed it
        bgfx_update_texture_2d(texture, 0, 0, 0, 0, pictureWidth, pictureHeight,
                bgfx_make_ref(MemoryUtil.memByteBuffer(buffer)), pictureWidth * 4);
        return true;
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
            HLogger.info(on ? "HDR output on, SDR white at " + Math.round(wanted.whiteNits) + " nits"
                    : "HDR output off");
        }
        hdr = on;
        displayColor = wanted;
        hdrStatus = describeHdr();
    }

    /** Submits a textured rectangle at {@code place} to a view of {@code viewWidth} x {@code viewHeight}. */
    private boolean drawRectangle(int view, short program, short texture, int viewWidth, int viewHeight,
            Rectangle place, boolean smooth, long state) {
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
            vertices.putFloat(left).putFloat(top).putFloat(0F).putFloat(0F);
            vertices.putFloat(right).putFloat(top).putFloat(1F).putFloat(0F);
            vertices.putFloat(left).putFloat(bottom).putFloat(0F).putFloat(1F);
            vertices.putFloat(right).putFloat(bottom).putFloat(1F).putFloat(1F);
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
            for (short uniform : new short[] {sampler, hdrUniform}) {
                if (isValid(uniform)) {
                    bgfx_destroy_uniform(uniform);
                }
            }
            for (short program : new short[] {pictureProgram, overlayProgram, colorProgram, hdrProgram}) {
                if (isValid(program)) {
                    bgfx_destroy_program(program);
                }
            }
            bgfx_shutdown();
            started = false;
        }
        picture = BGFX_INVALID_HANDLE;
        hud = BGFX_INVALID_HANDLE;
        sceneBuffer = BGFX_INVALID_HANDLE;
        sceneColor = BGFX_INVALID_HANDLE;
        sampler = BGFX_INVALID_HANDLE;
        hdrUniform = BGFX_INVALID_HANDLE;
        hdrProgram = BGFX_INVALID_HANDLE;
        pictureProgram = BGFX_INVALID_HANDLE;
        overlayProgram = BGFX_INVALID_HANDLE;
        colorProgram = BGFX_INVALID_HANDLE;
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
