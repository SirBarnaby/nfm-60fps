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
    /**
     * The picture's colour at {@code uv}: sampled, or, while u_down[1].z is
     * on, the exact average of the texels under the window's pixel, for a
     * scene drawn larger than it is shown (supersampling). u_down[0] is the
     * texture's size in texels (xy) and how many texels across and down one
     * pixel covers (zw); u_down[1].xy is the part of the texture the scene
     * was drawn in. Each texel counts by how much of it the pixel covers, so
     * 1.41 or 2.83 texels a pixel are averaged as cleanly as 2.
     */
    private static final String FETCH = """
            float4 fetch(float2 uv)
            {
                if (u_down[1].z <= 0.0)
                {
                    return s_pictureTexture.Sample(s_pictureSampler, uv);
                }
                float2 centre = uv * u_down[0].xy;
                float2 lo = centre - 0.5 * u_down[0].zw;
                float2 hi = centre + 0.5 * u_down[0].zw;
                int2 first = max(int2(floor(lo)), int2(0, 0));
                int2 last = min(int2(ceil(hi)) - 1, int2(u_down[1].xy) - 1);
                float4 sum = 0.0;
                float total = 0.0;
                [loop] for (int y = first.y; y <= min(last.y, first.y + 7); y++)
                {
                    float wy = min(y + 1.0, hi.y) - max((float) y, lo.y);
                    [loop] for (int x = first.x; x <= min(last.x, first.x + 7); x++)
                    {
                        float w = wy * (min(x + 1.0, hi.x) - max((float) x, lo.x));
                        sum += s_pictureTexture.Load(int3(x, y, 0)) * w;
                        total += w;
                    }
                }
                return total > 0.0 ? sum / total : s_pictureTexture.Sample(s_pictureSampler, uv);
            }
            """;
    static final String PICTURE_FS = """
            Texture2D s_pictureTexture : register(t0);
            SamplerState s_pictureSampler : register(s0);
            cbuffer Uniforms : register(b0) { float4 u_down[2]; };
            """ + FETCH + """
            float4 main(float4 pos : SV_POSITION, float2 uv : TEXCOORD0) : SV_TARGET
            {
                return float4(fetch(uv).rgb, 1.0);
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
            cbuffer Uniforms : register(b0) { float4 u_hdr; float4 u_down[2]; };
            """ + FETCH + """
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
                float4 c = fetch(uv);
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
     * middle is covered by half width - d. The colour's alpha is how much of
     * the polygon's pixels it covers (SceneList.DITHER): below 1 only the
     * pixels whose place in a 4x4 ordered (Bayer) pattern is under it are
     * drawn, so an extra's see-through smoke keeps the scene opaque.
     */
    static final String COLOR_FS = """
            static const float BAYER[16] = { 0.0, 8.0, 2.0, 10.0, 12.0, 4.0, 14.0, 6.0,
                                             3.0, 11.0, 1.0, 9.0, 15.0, 7.0, 13.0, 5.0 };
            float4 main(float4 pos : SV_POSITION, float4 col : COLOR0, float2 edge : TEXCOORD0) : SV_TARGET
            {
                if (col.a < 0.999) {
                    uint2 cell = uint2(pos.xy) & 3u;
                    if (col.a * 16.0 <= BAYER[cell.y * 4u + cell.x] + 0.5) {
                        discard;
                    }
                }
                return float4(col.rgb, saturate(edge.y * (1.0 - abs(edge.x))));
            }
            """;

    /**
     * A kept scene's vertex, placed for the frame on the card (see
     * SceneGeometry#buildProjected): refs names the points its x and y come
     * from (refs.xy; refs.zw is a line's other end), role says what it is.
     * Each point is three texels of s_points (its kind and its recorded
     * values), placed as RetainedScene places it: turned and projected by
     * the camera in u_cam, or moved with car slot s by u_car[4s..4s+3].
     * u_cam: [cx, cy, cz, focus], [sin, cos yaw, sin, cos pitch], [camera
     * move, trail share], [top, bottom, 2 / width, 2 / height], [pixel scale,
     * where the scene's top left is in the picture: a split-screen pane's
     * corner, 0, 0 for the whole picture].
     */
    static final String PROJECTED_VS = """
            Texture2D<float4> s_pointsTexture : register(t0);
            cbuffer Uniforms : register(b0) { float4 u_cam[5]; float4 u_car[204]; };
            struct In { float2 pos : POSITION; float4 col : COLOR0; float4 refs : TEXCOORD0; float role : TEXCOORD1; };
            struct Out { float4 pos : SV_POSITION; float4 col : COLOR0; float2 edge : TEXCOORD0; };

            float4 texel(uint id, uint t)
            {
                uint i = id * 3u + t;
                return s_pointsTexture.Load(int3(i % 3072u, i / 3072u, 0));
            }

            float3 project(float3 p, float nearZ)
            {
                precise float cx = u_cam[0].x, cy = u_cam[0].y, cz = u_cam[0].z, focus = u_cam[0].w;
                precise float dx = p.x - cx;
                precise float dz = p.z - cz;
                precise float x = cx + dx * u_cam[1].y - dz * u_cam[1].x;
                precise float z = cz + dx * u_cam[1].x + dz * u_cam[1].y;
                precise float dy = p.y - cy;
                dz = z - cz;
                precise float y = cy + dy * u_cam[1].w - dz * u_cam[1].z;
                z = cz + dy * u_cam[1].z + dz * u_cam[1].w;
                precise float zx = max(z, cz);
                precise float zy = max(z, nearZ);
                return float3((zx - focus) * (cx - x) / zx + x, (zy - focus) * (cy - y) / zy + y, z);
            }

            float3 placeBasic(float4 a, float4 b)
            {
                int kind = (int) a.x;
                float cz = u_cam[0].z;
                if (kind == 0) {
                    return project(a.yzw + u_cam[2].xyz * b.x, b.y);
                }
                if (kind == 1 || kind == 2) {
                    uint s = (uint) a.y * 4u;
                    float4 t03 = u_car[s + 1u];
                    float4 t47 = u_car[s + 2u];
                    float4 t89 = u_car[s + 3u];
                    precise float x = a.z, y = a.w, z = b.x;
                    if (kind == 1) {
                        precise float wx = b.y, wy = b.z, wz = b.w;
                        if (wz != 0.0) {
                            precise float da = y - wy, db = z - wz;
                            y = wy + da * t89.y - db * t89.x;
                            z = wz + da * t89.x + db * t89.y;
                        }
                        if (wx != 0.0) {
                            precise float da = x - wx, db = z - wz;
                            x = wx + da * t47.w - db * t47.z;
                            z = wz + da * t47.z + db * t47.w;
                        }
                    }
                    precise float x1 = x * t03.y - y * t03.x;
                    precise float y1 = x * t03.x + y * t03.y;
                    precise float y2 = y1 * t03.w - z * t03.z;
                    precise float z2 = y1 * t03.z + z * t03.w;
                    precise float x3 = x1 * t47.y - z2 * t47.x;
                    precise float z3 = x1 * t47.x + z2 * t47.y;
                    float3 car = u_car[s].xyz;
                    if (kind == 1) {
                        return project(float3(x3, y2, z3) + car, cz);
                    }
                    return project(float3(x3 + car.x, b.y + u_cam[2].y, z3 + car.z), cz);
                }
                if (kind == 3) {
                    precise float cy = u_cam[0].y;
                    precise float height = a.y + (a.w != 0.0 ? u_cam[2].y : 0.0);
                    precise float dy = height - cy;
                    precise float dz = a.z - cz;
                    precise float y = cy + dy * u_cam[1].w - dz * u_cam[1].z;
                    precise float z = cz + dy * u_cam[1].z + dz * u_cam[1].w;
                    precise float zy = max(z, 10.0);
                    precise float screen = (zy - u_cam[0].w) * (cy - y) / zy + y;
                    return float3(0.0, clamp(screen, u_cam[3].x, u_cam[3].y), z);
                }
                if (kind == 7) {
                    // an extra's particle: where it is at the frame's moment, from the last real tick
                    precise float s = u_cam[2].w - b.w;
                    return project(a.yzw + b.xyz * s + u_cam[2].xyz, cz);
                }
                return float3(a.y, a.z, 0.0); // a value the game worked out itself
            }

            float3 place(uint id)
            {
                float4 a = texel(id, 0u);
                float4 b = texel(id, 1u);
                int kind = (int) a.x;
                if (kind != 4 && kind != 5) {
                    return placeBasic(a, b);
                }
                precise float x = a.z, y = a.w;
                if (kind == 5) {
                    float4 c = texel(id, 2u);
                    x = b.w + (x - b.w) * u_cam[2].w;
                    y = c.x + (y - c.x) * u_cam[2].w;
                }
                uint anchor = (uint) a.y;
                float3 at = placeBasic(texel(anchor, 0u), texel(anchor, 1u));
                float cz = u_cam[0].z;
                if (b.z < 10.0 || at.z < 10.0) {
                    return float3(x, y, at.z); // behind the camera: left where it was
                }
                precise float scale = max(b.z, cz) / max(at.z, cz);
                return float3(at.x + (x - b.x) * scale, at.y + (y - b.y) * scale, at.z);
            }

            float2 corner(float refX, float refY)
            {
                float3 p = place((uint) refX);
                return float2(p.x, refY == refX ? p.y : place((uint) refY).y);
            }

            Out main(In i)
            {
                int role = (int) (i.role + 0.5);
                float2 p = i.pos;
                float2 edge = float2(0.0, 1.0);
                if (role == 0) {
                    p = corner(i.refs.x, i.refs.y) + 0.25;
                } else if (role == 2) {
                    // a share of the way along the edge between two corners (a polygon cut in bands)
                    float2 a = corner(i.refs.x, i.refs.y);
                    float2 b = corner(i.refs.z, i.refs.w);
                    precise float2 d = b - a;
                    p = a + d * i.pos.x + 0.25;
                } else if (role == 3) {
                    // where edges a-b and c-d cross now (a polygon cut in bands): the bands either side meet there
                    float2 a = corner(i.refs.x, i.refs.x);
                    float2 b = corner(i.refs.y, i.refs.y);
                    float2 c = corner(i.refs.z, i.refs.z);
                    float2 d = corner(i.refs.w, i.refs.w);
                    precise float2 ab = b - a;
                    precise float2 cd = d - c;
                    precise float2 ac = c - a;
                    precise float den = ab.x * cd.y - ab.y * cd.x;
                    // kept between where a-b meets the cuts either side (pos), so the bands cannot fold
                    precise float share = den != 0.0 ? clamp((ac.x * cd.y - ac.y * cd.x) / den, i.pos.x, i.pos.y)
                            : (i.pos.x + i.pos.y) * 0.5;
                    p = a + ab * share + 0.25;
                } else if (role >= 10) {
                    int k = role % 10;
                    float2 a = corner(i.refs.x, i.refs.y);
                    float2 b = corner(i.refs.z, i.refs.w);
                    precise float2 d = b - a;
                    float len = sqrt(d.x * d.x + d.y * d.y);
                    if (role >= 20 && len != 0.0) {
                        // antialiased: half a pixel past each end, fading over a pixel either side
                        float2 u = d / len;
                        float2 s = a + 0.5 - u * 0.5;
                        float2 t = b + 0.5 + u * 0.5;
                        float ps = u_cam[4].x;
                        float halfWidth = ps / 2.0 + 0.5;
                        float2 n = float2(-u.y, u.x) * (halfWidth / ps);
                        p = (k == 0 ? s - n : k == 1 ? t - n : k == 2 ? t + n : s + n);
                        edge = float2(k < 2 ? -1.0 : 1.0, halfWidth);
                    } else if (d.x == 0.0 && d.y == 0.0) {
                        p = a + float2(k == 1 || k == 2 ? 1.0 : 0.0, k >= 2 ? 1.0 : 0.0);
                    } else {
                        bool wide = abs(d.x) >= abs(d.y);
                        precise float e = 0.5 / (wide ? abs(d.x) : abs(d.y));
                        float2 s = a + 0.5 - d * e;
                        float2 t = b + 0.5 + d * e;
                        float2 from = k == 0 || k == 3 ? s : t;
                        float side = k < 2 ? -0.5 : 0.5;
                        p = from + (wide ? float2(0.0, side) : float2(side, 0.0));
                    }
                }
                Out o;
                p += u_cam[4].yz;
                o.pos = float4(p.x * u_cam[3].z - 1.0, 1.0 - p.y * u_cam[3].w, 0.0, 1.0);
                o.col = i.col;
                o.edge = edge;
                return o;
            }
            """;

    static final Source PICTURE_VERTEX = new Source("picture_vs", PICTURE_VS, "vs_5_0");
    static final Source PICTURE_FRAGMENT = new Source("picture_fs", PICTURE_FS, "ps_5_0");
    static final Source OVERLAY_FRAGMENT = new Source("overlay_fs", OVERLAY_FS, "ps_5_0");
    static final Source COLOR_VERTEX = new Source("color_vs", COLOR_VS, "vs_5_0");
    static final Source COLOR_FRAGMENT = new Source("color_fs", COLOR_FS, "ps_5_0");
    static final Source HDR_FRAGMENT = new Source("hdr_fs", HDR_FS, "ps_5_0");
    static final Source PROJECTED_VERTEX = new Source("projected_vs", PROJECTED_VS, "vs_5_0");

    /** Every shader, for baking. */
    static final List<Source> ALL = List.of(PICTURE_VERTEX, PICTURE_FRAGMENT, OVERLAY_FRAGMENT, COLOR_VERTEX,
            COLOR_FRAGMENT, HDR_FRAGMENT, PROJECTED_VERTEX);
}
