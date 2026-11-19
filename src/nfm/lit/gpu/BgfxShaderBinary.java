package nfm.lit.gpu;

import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;

/**
 * Wraps Direct3D shader bytecode in the container bgfx loads, as its shaderc
 * tool would write it for the Direct3D 11 and 12 renderers.
 * <p>
 * The layout is bgfx's shader binary version 12 (bgfx API 156, the version in
 * LWJGL 3.4.3): a magic word, input and output hashes, raw-buffer binding
 * masks, the uniform table, the bytecode, the vertex attributes it reads and
 * the size of its constant buffer. Everything is little-endian.
 */
final class BgfxShaderBinary {

    private static final int VERSION = 12;
    /** Any value, as long as a vertex shader's output matches its fragment shader's input. */
    static final int STAGE_HASH = 0x4E464D32;

    /** bgfx's fixed attribute ids (vertexlayout.cpp). */
    static final int ATTRIB_POSITION = 0x0001;
    static final int ATTRIB_COLOR0 = 0x0005;
    static final int ATTRIB_TEXCOORD0 = 0x0010;
    static final int ATTRIB_TEXCOORD1 = 0x0011;

    private static final int UNIFORM_SAMPLER = 0;
    private static final int UNIFORM_VEC4 = 2;
    private static final int UNIFORM_SAMPLER_BIT = 0x20;
    private static final int TEXTURE_COMPONENT_FLOAT = 0x00;
    private static final int TEXTURE_DIMENSION_2D = 0x02;

    private BgfxShaderBinary() {
    }

    /** A vertex shader that reads the given attributes and uses no uniforms. */
    static byte[] vertex(byte[] bytecode, int... attributes) {
        return pack('V', 0, STAGE_HASH, bytecode, attributes, null, new Vec4[0]);
    }

    /**
     * A vertex shader reading the given attributes, a 2D texture at register
     * 0 (by Load, so no sampler state) and the given float4s, as
     * {@link #fragment(byte[], String, Vec4...)} lays them out.
     */
    static byte[] vertex(byte[] bytecode, String samplerName, Vec4[] vec4s, int... attributes) {
        return pack('V', 0, STAGE_HASH, bytecode, attributes, samplerName, vec4s);
    }

    /** A fragment shader using no uniforms, or one 2D texture sampler at register 0. */
    static byte[] fragment(byte[] bytecode, String samplerName) {
        return pack('F', STAGE_HASH, 0, bytecode, new int[0], samplerName, new Vec4[0]);
    }

    /**
     * A fragment shader using a 2D texture sampler at register 0 and the
     * given float4s (or arrays of them), in that order the whole of its
     * constant buffer at b0. bgfx finds each by name, so each must have been
     * made with bgfx_create_uniform under the same name and count before the
     * shader is.
     */
    static byte[] fragment(byte[] bytecode, String samplerName, Vec4... vec4s) {
        return pack('F', STAGE_HASH, 0, bytecode, new int[0], samplerName, vec4s);
    }

    /** A float4 uniform, or an array of {@code count} of them. */
    record Vec4(String name, int count) {
    }

    private static byte[] pack(char type, int hashIn, int hashOut, byte[] bytecode, int[] attributes,
            String samplerName, Vec4[] vec4s) {
        ByteArrayOutputStream out = new ByteArrayOutputStream(bytecode.length + 64);
        ByteBuffer word = ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN);
        out.writeBytes(new byte[] {(byte) type, 'S', 'H', VERSION});
        writeInt(out, word, hashIn);
        writeInt(out, word, hashOut);
        writeInt(out, word, 0); // raw shader resource views
        writeInt(out, word, 0); // raw unordered access views
        writeShort(out, word, (samplerName == null ? 0 : 1) + vec4s.length);
        if (samplerName != null) {
            // register s0 / t0, one register
            writeUniform(out, word, samplerName, UNIFORM_SAMPLER_BIT | UNIFORM_SAMPLER, 1, 0, 1,
                    TEXTURE_DIMENSION_2D);
        }
        int bytes = 0;
        for (Vec4 vec4 : vec4s) {
            // at this byte of the constant buffer, one register each
            writeUniform(out, word, vec4.name(), UNIFORM_VEC4, vec4.count(), bytes, vec4.count(), 0);
            bytes += 16 * vec4.count();
        }
        writeInt(out, word, bytecode.length);
        out.writeBytes(bytecode);
        out.write(0);
        out.write(attributes.length);
        for (int attribute : attributes) {
            writeShort(out, word, attribute);
        }
        writeShort(out, word, bytes); // constant buffer size
        return out.toByteArray();
    }

    /**
     * An entry of the uniform table: {@code count} of its kind at
     * {@code index} (a sampler's register, a constant's byte in the buffer).
     */
    private static void writeUniform(ByteArrayOutputStream out, ByteBuffer word, String uniformName, int kind,
            int count, int index, int registers, int textureDimension) {
        byte[] name = uniformName.getBytes(StandardCharsets.US_ASCII);
        out.write(name.length);
        out.writeBytes(name);
        out.write(kind);
        out.write(count);
        writeShort(out, word, index); // register index
        writeShort(out, word, registers); // registers used
        out.write(TEXTURE_COMPONENT_FLOAT);
        out.write(textureDimension);
        writeShort(out, word, 0); // texture format: any
    }

    private static void writeInt(ByteArrayOutputStream out, ByteBuffer word, int value) {
        out.write(word.clear().putInt(value).array(), 0, 4);
    }

    private static void writeShort(ByteArrayOutputStream out, ByteBuffer word, int value) {
        out.write(word.clear().putShort((short) value).array(), 0, 2);
    }
}
