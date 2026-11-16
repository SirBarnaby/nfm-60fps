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
 * Direct3D 11, instead of Java2D's software blit.
 * <p>
 * The picture is uploaded as a texture and drawn as one rectangle, so
 * scaling it to the window costs the processor nothing, and the frame reaches
 * the screen through a Direct3D swap chain rather than GDI.
 * <p>
 * bgfx runs single-threaded: every call, from {@link #open} to
 * {@link #close}, must come from the same thread, the game thread.
 */
public final class GpuDisplay {

    private static final int SCREEN_VIEW = 0;
    private static final int CAPTURE_VIEW = 1;
    private static final int COPY_VIEW = 2;
    private static final int BLACK = 0x000000ff;
    private static final long CLAMP = BGFX_SAMPLER_U_CLAMP | BGFX_SAMPLER_V_CLAMP;
    /** Frames to wait for a read-back before giving up; bgfx needs two. */
    private static final int READ_BACK_FRAMES = 8;

    private final int pictureWidth;
    private final int pictureHeight;
    private final long window;

    private boolean started;
    private BGFXVertexLayout layout;
    private short program = BGFX_INVALID_HANDLE;
    private short sampler = BGFX_INVALID_HANDLE;
    private short picture = BGFX_INVALID_HANDLE;
    private IntBuffer upload;
    private BGFXTransientVertexBuffer quad;
    private int width;
    private int height;
    private int resetFlags = BGFX_RESET_NONE;
    private boolean vsync;

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

    private void start(int width, int height) {
        GpuLibraries.prepare();
        HlslCompiler hlsl = new HlslCompiler();
        byte[] vertex = hlsl.compile("picture_vs", """
                struct In { float2 pos : POSITION; float2 uv : TEXCOORD0; };
                struct Out { float4 pos : SV_POSITION; float2 uv : TEXCOORD0; };
                Out main(In i) { Out o; o.pos = float4(i.pos, 0.0, 1.0); o.uv = i.uv; return o; }
                """, "vs_5_0");
        byte[] fragment = hlsl.compile("picture_fs", """
                Texture2D s_pictureTexture : register(t0);
                SamplerState s_pictureSampler : register(s0);
                float4 main(float4 pos : SV_POSITION, float2 uv : TEXCOORD0) : SV_TARGET
                {
                    return float4(s_pictureTexture.Sample(s_pictureSampler, uv).rgb, 1.0);
                }
                """, "ps_5_0");

        BGFXPlatform.bgfx_render_frame(-1); // no render thread: bgfx works on the calling thread
        try (BGFXInit init = BGFXInit.calloc()) {
            bgfx_init_ctor(init);
            init.type(BGFX_RENDERER_TYPE_DIRECT3D11);
            init.resolution(r -> r.width(width).height(height).reset(resetFlags));
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

        short vs = createShader(BgfxShaderBinary.vertex(vertex,
                BgfxShaderBinary.ATTRIB_POSITION, BgfxShaderBinary.ATTRIB_TEXCOORD0));
        short fs = createShader(BgfxShaderBinary.fragment(fragment, "s_picture"));
        program = bgfx_create_program(vs, fs, true);
        sampler = bgfx_create_uniform("s_picture", BGFX_UNIFORM_TYPE_SAMPLER, 1);
        layout = BGFXVertexLayout.calloc();
        bgfx_vertex_layout_begin(layout, BGFX_RENDERER_TYPE_DIRECT3D11);
        bgfx_vertex_layout_add(layout, BGFX_ATTRIB_POSITION, 2, BGFX_ATTRIB_TYPE_FLOAT, false, false);
        bgfx_vertex_layout_add(layout, BGFX_ATTRIB_TEXCOORD0, 2, BGFX_ATTRIB_TYPE_FLOAT, false, false);
        bgfx_vertex_layout_end(layout);
        picture = bgfx_create_texture_2d(pictureWidth, pictureHeight, false, 1, BGFX_TEXTURE_FORMAT_BGRA8,
                CLAMP, null, 0);
        if (!isValid(program) || !isValid(sampler) || !isValid(picture)) {
            throw new IllegalStateException("creating GPU resources failed");
        }
        upload = MemoryUtil.memAllocInt(pictureWidth * pictureHeight);
        quad = BGFXTransientVertexBuffer.calloc();
        bgfx_set_view_clear(SCREEN_VIEW, BGFX_CLEAR_COLOR, BLACK, 1F, 0);
        bgfx_set_view_clear(CAPTURE_VIEW, BGFX_CLEAR_COLOR, BLACK, 1F, 0);
    }

    private static boolean isValid(short handle) {
        return handle != BGFX_INVALID_HANDLE;
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

    /**
     * Shows {@code frame}, a packed-int RGB image of the picture's size, in a
     * window of the given size with the picture at {@code place}; the rest is
     * black. {@code smooth} filters the picture when it is scaled.
     *
     * @return false if the frame could not be shown (the caller should fall back)
     */
    public boolean present(BufferedImage frame, Dimension windowSize, Rectangle place, boolean smooth) {
        if (!upload(frame)) {
            return false;
        }
        fitSwapChain(windowSize);
        if (!drawPicture(SCREEN_VIEW, place, smooth)) {
            return false;
        }
        bgfx_frame(BGFX_FRAME_NONE);
        return true;
    }

    /**
     * Presents like {@link #present} and also reads the window's picture back
     * from the graphics card, exactly as it went to the screen. For tests.
     *
     * @return the window's picture, or null if it could not be read back
     */
    public BufferedImage presentAndCapture(BufferedImage frame, Dimension windowSize, Rectangle place,
            boolean smooth) {
        if (!upload(frame)) {
            return null;
        }
        fitSwapChain(windowSize);
        short target = bgfx_create_frame_buffer(width, height, BGFX_TEXTURE_FORMAT_BGRA8, BGFX_TEXTURE_RT | CLAMP);
        short copy = bgfx_create_texture_2d(width, height, false, 1, BGFX_TEXTURE_FORMAT_BGRA8,
                BGFX_TEXTURE_BLIT_DST | BGFX_TEXTURE_READ_BACK, null, 0);
        IntBuffer pixels = MemoryUtil.memAllocInt(width * height);
        try {
            bgfx_set_view_frame_buffer(CAPTURE_VIEW, target);
            if (!drawPicture(CAPTURE_VIEW, place, smooth) || !drawPicture(SCREEN_VIEW, place, smooth)) {
                return null;
            }
            bgfx_blit(COPY_VIEW, copy, 0, 0, 0, 0, bgfx_get_texture(target, 0), 0, 0, 0, 0, width, height, 1);
            int ready = bgfx_read_texture(copy, pixels, 0, 0);
            int done = bgfx_frame(BGFX_FRAME_NONE);
            for (int i = 0; done < ready && i < READ_BACK_FRAMES; i++) {
                drawPicture(SCREEN_VIEW, place, smooth); // keep the window showing the picture meanwhile
                done = bgfx_frame(BGFX_FRAME_NONE);
            }
            if (done < ready) {
                return null;
            }
            BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
            pixels.get(((DataBufferInt) image.getRaster().getDataBuffer()).getData()); // BGRA bytes = 0xAARRGGBB
            return image;
        } finally {
            bgfx_set_view_frame_buffer(CAPTURE_VIEW, BGFX_INVALID_HANDLE);
            bgfx_destroy_frame_buffer(target);
            bgfx_destroy_texture(copy);
            MemoryUtil.memFree(pixels);
        }
    }

    /** Copies the frame into the picture texture; false if it is not a packed-int image of the right size. */
    private boolean upload(BufferedImage frame) {
        if (!(frame.getRaster().getDataBuffer() instanceof DataBufferInt pixels)
                || frame.getWidth() != pictureWidth || frame.getHeight() != pictureHeight
                || frame.getRaster().getSampleModel().getWidth() != pictureWidth) {
            return false;
        }
        upload.clear();
        upload.put(pixels.getData(), pixels.getOffset(), pictureWidth * pictureHeight).flip();
        // a reference, not a copy: the buffer is only reused after bgfx_frame() has consumed it
        bgfx_update_texture_2d(picture, 0, 0, 0, 0, pictureWidth, pictureHeight,
                bgfx_make_ref(MemoryUtil.memByteBuffer(upload)), pictureWidth * 4);
        return true;
    }

    /** Resizes the swap chain to the window, and switches vsync, when either changed. */
    private void fitSwapChain(Dimension windowSize) {
        int newWidth = Math.max(1, windowSize.width);
        int newHeight = Math.max(1, windowSize.height);
        int newFlags = vsync ? BGFX_RESET_VSYNC : BGFX_RESET_NONE;
        if (newWidth != width || newHeight != height || newFlags != resetFlags) {
            width = newWidth;
            height = newHeight;
            resetFlags = newFlags;
            bgfx_reset(width, height, resetFlags, BGFX_TEXTURE_FORMAT_COUNT);
        }
    }

    /** Submits the picture rectangle to a view covering the whole window. */
    private boolean drawPicture(int view, Rectangle place, boolean smooth) {
        bgfx_set_view_rect(view, 0, 0, width, height);
        if (bgfx_get_avail_transient_vertex_buffer(4, layout) < 4) {
            return false;
        }
        bgfx_alloc_transient_vertex_buffer(quad, 4, layout);
        ByteBuffer vertices = quad.data().order(ByteOrder.LITTLE_ENDIAN);
        float left = 2F * place.x / width - 1F;
        float right = 2F * (place.x + place.width) / width - 1F;
        float top = 1F - 2F * place.y / height;
        float bottom = 1F - 2F * (place.y + place.height) / height;
        vertices.putFloat(left).putFloat(top).putFloat(0F).putFloat(0F);
        vertices.putFloat(right).putFloat(top).putFloat(1F).putFloat(0F);
        vertices.putFloat(left).putFloat(bottom).putFloat(0F).putFloat(1F);
        vertices.putFloat(right).putFloat(bottom).putFloat(1F).putFloat(1F);
        bgfx_set_transient_vertex_buffer(0, quad, 0, 4);
        long filter = smooth ? 0L : BGFX_SAMPLER_MIN_POINT | BGFX_SAMPLER_MAG_POINT;
        bgfx_set_texture(0, sampler, picture, (int) (CLAMP | filter));
        bgfx_set_state(BGFX_STATE_WRITE_RGB | BGFX_STATE_WRITE_A | BGFX_STATE_PT_TRISTRIP, 0);
        bgfx_submit(view, program, 0, BGFX_DISCARD_ALL);
        return true;
    }

    /** Stops bgfx and frees everything. Safe to call more than once. */
    public void close() {
        if (started) {
            if (isValid(picture)) {
                bgfx_destroy_texture(picture);
            }
            if (isValid(sampler)) {
                bgfx_destroy_uniform(sampler);
            }
            if (isValid(program)) {
                bgfx_destroy_program(program);
            }
            bgfx_shutdown();
            started = false;
        }
        picture = BGFX_INVALID_HANDLE;
        sampler = BGFX_INVALID_HANDLE;
        program = BGFX_INVALID_HANDLE;
        if (layout != null) {
            layout.free();
            layout = null;
        }
        if (quad != null) {
            quad.free();
            quad = null;
        }
        if (upload != null) {
            MemoryUtil.memFree(upload);
            upload = null;
        }
    }
}
