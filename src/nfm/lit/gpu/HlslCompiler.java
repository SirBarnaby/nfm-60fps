package nfm.lit.gpu;

import java.lang.foreign.Arena;
import java.lang.foreign.FunctionDescriptor;
import java.lang.foreign.Linker;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.SymbolLookup;
import java.lang.foreign.ValueLayout;
import java.lang.invoke.MethodHandle;
import java.nio.charset.StandardCharsets;

/**
 * Compiles HLSL to Direct3D bytecode with Windows' own shader compiler,
 * d3dcompiler_47.dll, which every Windows 10 and 11 installation has.
 * <p>
 * bgfx expects shaders compiled ahead of time by its shaderc tool, which is
 * not part of LWJGL. The renderer's shaders are a few lines each, so they are
 * compiled when the game starts instead and wrapped in bgfx's container by
 * {@link BgfxShaderBinary}.
 */
final class HlslCompiler {

    private static final int D3DCOMPILE_OPTIMIZATION_LEVEL3 = 1 << 15;
    private static final int D3DCOMPILE_ENABLE_STRICTNESS = 1 << 11;

    private final MethodHandle compile;
    private final Linker linker = Linker.nativeLinker();

    HlslCompiler() {
        SymbolLookup library = SymbolLookup.libraryLookup("d3dcompiler_47", Arena.global());
        compile = linker.downcallHandle(library.findOrThrow("D3DCompile"),
                FunctionDescriptor.of(ValueLayout.JAVA_INT,
                        ValueLayout.ADDRESS, ValueLayout.JAVA_LONG, ValueLayout.ADDRESS, ValueLayout.ADDRESS,
                        ValueLayout.ADDRESS, ValueLayout.ADDRESS, ValueLayout.ADDRESS,
                        ValueLayout.JAVA_INT, ValueLayout.JAVA_INT, ValueLayout.ADDRESS, ValueLayout.ADDRESS));
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
            int result = (int) compile.invokeExact(sourceSegment, (long) text.length, arena.allocateFrom(name),
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
