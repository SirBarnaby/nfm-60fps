package nfm.lit.gpu;

import java.util.List;

/**
 * The renderer's HLSL shaders. They are compiled to Direct3D bytecode when
 * the game is built ({@link ShaderBaker}) and packed into madness.jar; a
 * shader whose baked copy is missing or was compiled from other source is
 * compiled when the game starts instead ({@link HlslCompiler}). This class
 * holds only strings, so baking needs neither bgfx nor a graphics card.
 */
final class Shaders {

    private Shaders() {
    }

    /** A shader: its name, its HLSL (entry point main) and the profile it is compiled for. */
    record Source(String name, String hlsl, String target) {
    }

    static final String PICTURE_VS = """
            struct In { float2 pos : POSITION; float2 uv : TEXCOORD0; };
            struct Out { float4 pos : SV_POSITION; float2 uv : TEXCOORD0; };
            Out main(In i) { Out o; o.pos = float4(i.pos, 0.0, 1.0); o.uv = i.uv; return o; }
            """;
    static final String PICTURE_FS = """
            Texture2D s_pictureTexture : register(t0);
            SamplerState s_pictureSampler : register(s0);
            float4 main(float4 pos : SV_POSITION, float2 uv : TEXCOORD0) : SV_TARGET
            {
                return float4(s_pictureTexture.Sample(s_pictureSampler, uv).rgb, 1.0);
            }
            """;
    /**
     * Shows the picture on an scRGB swap chain, whose 1.0 is 80 nits: its
     * sRGB colours made linear and scaled to paper white (u_hdr.x), and the
     * glow in its alpha brightened by up to u_hdr.y more. When the brightest
     * glow would pass the display's peak (u_hdr.z nits, 0 for unknown), the
     * highlights roll off into it along the BT.2390 curve, in PQ, on the
     * brightest of the three channels so the hue stays; nothing at or below
     * paper white changes.
     */
    static final String HDR_FS = """
            Texture2D s_pictureTexture : register(t0);
            SamplerState s_pictureSampler : register(s0);
            cbuffer Uniforms : register(b0) { float4 u_hdr; };
            float3 toLinear(float3 c)
            {
                return c <= 0.04045 ? c / 12.92 : pow(max((c + 0.055) / 1.055, 0.0), 2.4);
            }
            float pq(float nits)
            {
                float y = pow(max(nits, 0.0) / 10000.0, 0.1593017578125);
                return pow((0.8359375 + 18.8515625 * y) / (1.0 + 18.6875 * y), 78.84375);
            }
            float pqNits(float e)
            {
                float p = pow(max(e, 0.0), 1.0 / 78.84375);
                return 10000.0 * pow(max(p - 0.8359375, 0.0) / (18.8515625 - 18.6875 * p), 1.0 / 0.1593017578125);
            }
            float4 main(float4 pos : SV_POSITION, float2 uv : TEXCOORD0) : SV_TARGET
            {
                float4 c = s_pictureTexture.Sample(s_pictureSampler, uv);
                float3 rgb = toLinear(c.rgb) * u_hdr.x * (1.0 + c.a * u_hdr.y);
                float brightest = u_hdr.x * 80.0 * (1.0 + u_hdr.y);
                float m = max(rgb.r, max(rgb.g, rgb.b)) * 80.0;
                if (u_hdr.z > 0.0 && brightest > u_hdr.z && m > u_hdr.x * 80.0)
                {
                    float source = pq(brightest);
                    float e = pq(m) / source;
                    float top = pq(u_hdr.z) / source;
                    float knee = clamp(1.5 * top - 0.5, pq(u_hdr.x * 80.0) / source, top);
                    if (e > knee)
                    {
                        float t = (e - knee) / (1.0 - knee);
                        float t2 = t * t;
                        float t3 = t2 * t;
                        float p = (2.0 * t3 - 3.0 * t2 + 1.0) * knee + (t3 - 2.0 * t2 + t) * (1.0 - knee)
                                + (3.0 * t2 - 2.0 * t3) * top;
                        rgb *= pqNits(min(p, top) * source) / m;
                    }
                }
                return float4(rgb, 1.0);
            }
            """;
    static final String OVERLAY_FS = """
            Texture2D s_pictureTexture : register(t0);
            SamplerState s_pictureSampler : register(s0);
            float4 main(float4 pos : SV_POSITION, float2 uv : TEXCOORD0) : SV_TARGET
            {
                return s_pictureTexture.Sample(s_pictureSampler, uv);
            }
            """;
    static final String COLOR_VS = """
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
    static final String COLOR_FS = """
            float4 main(float4 pos : SV_POSITION, float4 col : COLOR0, float2 edge : TEXCOORD0) : SV_TARGET
            {
                return float4(col.rgb, col.a * saturate(edge.y * (1.0 - abs(edge.x))));
            }
            """;

    static final Source PICTURE_VERTEX = new Source("picture_vs", PICTURE_VS, "vs_5_0");
    static final Source PICTURE_FRAGMENT = new Source("picture_fs", PICTURE_FS, "ps_5_0");
    static final Source OVERLAY_FRAGMENT = new Source("overlay_fs", OVERLAY_FS, "ps_5_0");
    static final Source COLOR_VERTEX = new Source("color_vs", COLOR_VS, "vs_5_0");
    static final Source COLOR_FRAGMENT = new Source("color_fs", COLOR_FS, "ps_5_0");
    static final Source HDR_FRAGMENT = new Source("hdr_fs", HDR_FS, "ps_5_0");

    /** Every shader, for baking. */
    static final List<Source> ALL = List.of(PICTURE_VERTEX, PICTURE_FRAGMENT, OVERLAY_FRAGMENT, COLOR_VERTEX,
            COLOR_FRAGMENT, HDR_FRAGMENT);
}
