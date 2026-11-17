package nfm.lit.gpu;

import java.lang.foreign.Arena;
import java.lang.foreign.FunctionDescriptor;
import java.lang.foreign.Linker;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.SymbolLookup;
import java.lang.foreign.ValueLayout;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.lang.invoke.MethodHandle;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;

/**
 * Compiles HLSL to Direct3D bytecode with Windows' own shader compiler,
 * d3dcompiler_47.dll, which every Windows 10 and 11 installation has.
 * <p>
 * bgfx expects shaders compiled ahead of time by its shaderc tool, which is
 * not part of LWJGL. So the game build compiles the renderer's shaders with
 * this class ({@link ShaderBaker}) and packs the bytecode into madness.jar,
 * each copy headed by a hash of the source and profile it came from; at the
 * start the game takes a baked copy whose hash matches and compiles the rest
 * (none, after a full build). Loading the compiler and its first compile
 * take about half a second, which baking saves. The bytecode is wrapped in
 * bgfx's container by {@link BgfxShaderBinary}.
 */
final class HlslCompiler {

    /** Where baked shaders are, next to this class. */
    static final String BAKED_FOLDER = "shaders/";
    private static final byte[] MAGIC = {'N', 'F', 'M', 'S'};
    private static final int HASH_BYTES = 32;

    private static final int D3DCOMPILE_OPTIMIZATION_LEVEL3 = 1 << 15;
    private static final int D3DCOMPILE_ENABLE_STRICTNESS = 1 << 11;

    private MethodHandle compile;
    private final Linker linker = Linker.nativeLinker();
    /** Shaders compiled here rather than taken baked, for the log. */
    private int compiled;

    /** Binds D3DCompile the first time a shader has to be compiled. */
    private MethodHandle compiler() {
        if (compile == null) {
            SymbolLookup library = SymbolLookup.libraryLookup("d3dcompiler_47", Arena.global());
            compile = linker.downcallHandle(library.findOrThrow("D3DCompile"),
                    FunctionDescriptor.of(ValueLayout.JAVA_INT,
                            ValueLayout.ADDRESS, ValueLayout.JAVA_LONG, ValueLayout.ADDRESS, ValueLayout.ADDRESS,
                            ValueLayout.ADDRESS, ValueLayout.ADDRESS, ValueLayout.ADDRESS,
                            ValueLayout.JAVA_INT, ValueLayout.JAVA_INT, ValueLayout.ADDRESS, ValueLayout.ADDRESS));
        }
        return compile;
    }

    /** The shader's bytecode: baked into the jar when it was built from this source, else compiled now. */
    byte[] bytecode(Shaders.Source shader) {
        byte[] baked = baked(shader);
        if (baked != null) {
            return baked;
        }
        compiled++;
        return compile(shader.name(), shader.hlsl(), shader.target());
    }

    /** How many shaders {@link #bytecode} had to compile. */
    int compiled() {
        return compiled;
    }

    /** The baked copy of {@code shader} if there is one and it came from the same source and profile. */
    private static byte[] baked(Shaders.Source shader) {
        try (InputStream in = HlslCompiler.class.getResourceAsStream(BAKED_FOLDER + shader.name() + ".dxbc")) {
            if (in == null) {
                return null;
            }
            byte[] file = in.readAllBytes();
            int header = MAGIC.length + HASH_BYTES;
            if (file.length <= header || !Arrays.equals(file, 0, MAGIC.length, MAGIC, 0, MAGIC.length)
                    || !Arrays.equals(file, MAGIC.length, header, hash(shader), 0, HASH_BYTES)) {
                return null;
            }
            return Arrays.copyOfRange(file, header, file.length);
        } catch (IOException ex) {
            return null;
        }
    }

    /** Compiles {@code shader} and writes it, headed by its hash, into {@code folder}. */
    void bake(Shaders.Source shader, Path folder) throws IOException {
        byte[] code = compile(shader.name(), shader.hlsl(), shader.target());
        Files.createDirectories(folder);
        try (OutputStream out = Files.newOutputStream(folder.resolve(shader.name() + ".dxbc"))) {
            out.write(MAGIC);
            out.write(hash(shader));
            out.write(code);
        }
    }

    /** SHA-256 of the profile and the source. */
    private static byte[] hash(Shaders.Source shader) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            digest.update(shader.target().getBytes(StandardCharsets.UTF_8));
            digest.update((byte) 0);
            digest.update(shader.hlsl().getBytes(StandardCharsets.UTF_8));
            return digest.digest();
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException(ex); // every Java has SHA-256
        }
    }

    /**
     * Compiles {@code source}'s {@code main} for {@code target} (for example
     * vs_5_0 or ps_5_0) and returns the bytecode.
     *
     * @throws IllegalStateException with the compiler's messages when it fails
     */
    byte[] compile(String name, String source, String target) {
        try (Arena arena = Arena.ofConfined()) {
            byte[] text = source.getBytes(StandardCharsets.UTF_8);
            MemorySegment sourceSegment = arena.allocate(text.length);
            sourceSegment.copyFrom(MemorySegment.ofArray(text));
            MemorySegment code = arena.allocate(ValueLayout.ADDRESS);
            MemorySegment errors = arena.allocate(ValueLayout.ADDRESS);
            int result = (int) compiler().invokeExact(sourceSegment, (long) text.length, arena.allocateFrom(name),
                    MemorySegment.NULL, MemorySegment.NULL, arena.allocateFrom("main"), arena.allocateFrom(target),
                    D3DCOMPILE_OPTIMIZATION_LEVEL3 | D3DCOMPILE_ENABLE_STRICTNESS, 0, code, errors);
            MemorySegment codeBlob = code.get(ValueLayout.ADDRESS, 0L);
            MemorySegment errorBlob = errors.get(ValueLayout.ADDRESS, 0L);
            try {
                if (result < 0 || codeBlob.equals(MemorySegment.NULL)) {
                    String message = errorBlob.equals(MemorySegment.NULL) ? ""
                            : new String(blobBytes(errorBlob), StandardCharsets.UTF_8).trim();
                    throw new IllegalStateException("Compiling shader " + name + " failed (0x"
                            + Integer.toHexString(result) + "): " + message);
                }
                return blobBytes(codeBlob);
            } finally {
                release(codeBlob);
                release(errorBlob);
            }
        } catch (IllegalStateException ex) {
            throw ex;
        } catch (Throwable ex) {
            throw new IllegalStateException("Compiling shader " + name + " failed", ex);
        }
    }

    // ID3DBlob's methods, by their place in its COM function table
    private static final int RELEASE = 2;
    private static final int GET_BUFFER_POINTER = 3;
    private static final int GET_BUFFER_SIZE = 4;

    private byte[] blobBytes(MemorySegment blob) throws Throwable {
        MemorySegment pointer = (MemorySegment) method(blob, GET_BUFFER_POINTER,
                FunctionDescriptor.of(ValueLayout.ADDRESS, ValueLayout.ADDRESS)).invokeExact(blob);
        long size = (long) method(blob, GET_BUFFER_SIZE,
                FunctionDescriptor.of(ValueLayout.JAVA_LONG, ValueLayout.ADDRESS)).invokeExact(blob);
        return pointer.reinterpret(size).toArray(ValueLayout.JAVA_BYTE);
    }

    private void release(MemorySegment blob) {
        if (blob.equals(MemorySegment.NULL)) {
            return;
        }
        try {
            int ignored = (int) method(blob, RELEASE,
                    FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.ADDRESS)).invokeExact(blob);
        } catch (Throwable ex) {
            // a blob that cannot be released only leaks a few bytes
        }
    }

    private MethodHandle method(MemorySegment object, int slot, FunctionDescriptor descriptor) {
        MemorySegment table = object.reinterpret(ValueLayout.ADDRESS.byteSize()).get(ValueLayout.ADDRESS, 0L);
        MemorySegment function = table.reinterpret((slot + 1L) * ValueLayout.ADDRESS.byteSize())
                .getAtIndex(ValueLayout.ADDRESS, slot);
        return linker.downcallHandle(function, descriptor);
    }
}
