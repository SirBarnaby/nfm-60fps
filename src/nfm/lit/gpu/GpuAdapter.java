package nfm.lit.gpu;

import java.lang.foreign.Arena;
import java.lang.foreign.FunctionDescriptor;
import java.lang.foreign.Linker;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.SymbolLookup;
import java.lang.foreign.ValueLayout;
import java.lang.invoke.MethodHandle;

/**
 * The graphics card Windows calls high-performance: on a laptop with two,
 * the discrete one rather than the one built into the processor, which is
 * what Direct3D gives a program that does not ask. Found through DXGI
 * (IDXGIFactory6::EnumAdapterByGpuPreference, Windows 10 1803 and later)
 * with Java's foreign function interface, and handed to bgfx by its PCI
 * vendor and device ids.
 */
final class GpuAdapter {

    /** PCI vendor and device id; 0 and 0 let bgfx take Direct3D's default. */
    final short vendor;
    final short device;
    /** The card's name, or "" for the default. */
    final String name;

    private static final GpuAdapter DEFAULT = new GpuAdapter((short) 0, (short) 0, "");

    private static final int DXGI_GPU_PREFERENCE_HIGH_PERFORMANCE = 2;
    private static final int DXGI_ADAPTER_FLAG_SOFTWARE = 2;
    /* COM vtable slots */
    private static final int RELEASE = 2;
    private static final int ENUM_ADAPTER_BY_GPU_PREFERENCE = 29; // IDXGIFactory6
    private static final int GET_DESC1 = 10; // IDXGIAdapter1
    private static final int QUERY_INTERFACE = 0;
    private static final int ENUM_ADAPTERS1 = 12; // IDXGIFactory1
    private static final int ENUM_OUTPUTS = 7; // IDXGIAdapter
    private static final int OUTPUT6_GET_DESC1 = 27; // IDXGIOutput6
    /** IID_IDXGIFactory1 {770aae78-f26f-4dba-a829-253c83d1b387}, IID_IDXGIOutput6 {068346e8-aaec-4b84-add7-137f513f77a1}. */
    private static final long[] IID_FACTORY1 = {0x4dbaf26f770aae78L, 0x87b3d1833c2529a8L};
    private static final long[] IID_OUTPUT6 = {0x4b84aaec068346e8L, 0xa1773f517f13d7adL};
    /* DXGI_OUTPUT_DESC1: Monitor (HMONITOR) and MaxLuminance (nits) */
    private static final int DESC1_SIZE = 152;
    private static final int DESC1_MONITOR = 88;
    private static final int DESC1_MAX_LUMINANCE = 140;
    /** IID_IDXGIFactory6 {c1b6694f-ff09-44a9-b03c-77900a0a1d17}, IID_IDXGIAdapter1 {29038f61-3839-4626-91fd-086879011a05}. */
    private static final long[] IID_FACTORY6 = {0x44a9ff09c1b6694fL, 0x171d0a0a90773cb0L};
    private static final long[] IID_ADAPTER1 = {0x4626383929038f61L, 0x051a01796808fd91L};

    private GpuAdapter(short vendor, short device, String name) {
        this.vendor = vendor;
        this.device = device;
        this.name = name;
    }

    /** The high-performance card, or the default when there is no choice to make or Windows cannot say. */
    static GpuAdapter highPerformance() {
        try (Arena arena = Arena.ofConfined()) {
            Linker linker = Linker.nativeLinker();
            SymbolLookup dxgi = SymbolLookup.libraryLookup("dxgi", arena);
            MethodHandle createFactory = linker.downcallHandle(dxgi.findOrThrow("CreateDXGIFactory1"),
                    FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.ADDRESS, ValueLayout.ADDRESS));
            MemorySegment out = arena.allocate(ValueLayout.ADDRESS);
            if ((int) createFactory.invokeExact(guid(arena, IID_FACTORY6), out) < 0) {
                return DEFAULT; // before Windows 10 1803
            }
            MemorySegment factory = out.get(ValueLayout.ADDRESS, 0);
            try {
                MethodHandle enumByPreference = method(linker, factory, ENUM_ADAPTER_BY_GPU_PREFERENCE,
                        FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.ADDRESS, ValueLayout.JAVA_INT,
                                ValueLayout.JAVA_INT, ValueLayout.ADDRESS, ValueLayout.ADDRESS));
                if ((int) enumByPreference.invokeExact(factory, 0, DXGI_GPU_PREFERENCE_HIGH_PERFORMANCE,
                        guid(arena, IID_ADAPTER1), out) < 0) {
                    return DEFAULT;
                }
                MemorySegment adapter = out.get(ValueLayout.ADDRESS, 0);
                try {
                    MethodHandle getDesc1 = method(linker, adapter, GET_DESC1,
                            FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.ADDRESS, ValueLayout.ADDRESS));
                    MemorySegment desc = arena.allocate(312, 8); // DXGI_ADAPTER_DESC1
                    if ((int) getDesc1.invokeExact(adapter, desc) < 0
                            || (desc.get(ValueLayout.JAVA_INT, 304) & DXGI_ADAPTER_FLAG_SOFTWARE) != 0) {
                        return DEFAULT;
                    }
                    return new GpuAdapter((short) desc.get(ValueLayout.JAVA_INT, 256),
                            (short) desc.get(ValueLayout.JAVA_INT, 260), wide(desc.asSlice(0, 256)));
                } finally {
                    release(linker, adapter);
                }
            } finally {
                release(linker, factory);
            }
        } catch (Throwable ex) {
            return DEFAULT;
        }
    }

    /**
     * The brightest the monitor {@code monitor} (an HMONITOR) shows, in nits,
     * as it tells DXGI (IDXGIOutput6, Windows 10 1703 and later): from its
     * EDID, or from the Windows HDR Calibration app's profile. 0 if it does
     * not say.
     */
    static float peakNits(long monitor) {
        try (Arena arena = Arena.ofConfined()) {
            Linker linker = Linker.nativeLinker();
            SymbolLookup dxgi = SymbolLookup.libraryLookup("dxgi", arena);
            MethodHandle createFactory = linker.downcallHandle(dxgi.findOrThrow("CreateDXGIFactory1"),
                    FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.ADDRESS, ValueLayout.ADDRESS));
            MemorySegment out = arena.allocate(ValueLayout.ADDRESS);
            if ((int) createFactory.invokeExact(guid(arena, IID_FACTORY1), out) < 0) {
                return 0F;
            }
            MemorySegment factory = out.get(ValueLayout.ADDRESS, 0);
            try {
                MethodHandle enumAdapters = method(linker, factory, ENUM_ADAPTERS1,
                        FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.ADDRESS, ValueLayout.JAVA_INT,
                                ValueLayout.ADDRESS));
                for (int a = 0; (int) enumAdapters.invokeExact(factory, a, out) >= 0; a++) {
                    MemorySegment adapter = out.get(ValueLayout.ADDRESS, 0);
                    try {
                        float peak = peakOnAdapter(linker, arena, adapter, monitor);
                        if (peak >= 0F) {
                            return peak;
                        }
                    } finally {
                        release(linker, adapter);
                    }
                }
                return 0F;
            } finally {
                release(linker, factory);
            }
        } catch (Throwable ex) {
            return 0F;
        }
    }

    /** The monitor's peak if one of this adapter's outputs drives it, else -1. */
    private static float peakOnAdapter(Linker linker, Arena arena, MemorySegment adapter, long monitor)
            throws Throwable {
        MemorySegment out = arena.allocate(ValueLayout.ADDRESS);
        MethodHandle enumOutputs = method(linker, adapter, ENUM_OUTPUTS,
                FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.ADDRESS, ValueLayout.JAVA_INT,
                        ValueLayout.ADDRESS));
        for (int o = 0; (int) enumOutputs.invokeExact(adapter, o, out) >= 0; o++) {
            MemorySegment output = out.get(ValueLayout.ADDRESS, 0);
            try {
                MethodHandle query = method(linker, output, QUERY_INTERFACE,
                        FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.ADDRESS, ValueLayout.ADDRESS,
                                ValueLayout.ADDRESS));
                if ((int) query.invokeExact(output, guid(arena, IID_OUTPUT6), out) < 0) {
                    continue; // before Windows 10 1703
                }
                MemorySegment output6 = out.get(ValueLayout.ADDRESS, 0);
                try {
                    MethodHandle getDesc1 = method(linker, output6, OUTPUT6_GET_DESC1,
                            FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.ADDRESS, ValueLayout.ADDRESS));
                    MemorySegment desc = arena.allocate(DESC1_SIZE, 8);
                    if ((int) getDesc1.invokeExact(output6, desc) >= 0
                            && desc.get(ValueLayout.JAVA_LONG, DESC1_MONITOR) == monitor) {
                        return Math.max(0F, desc.get(ValueLayout.JAVA_FLOAT, DESC1_MAX_LUMINANCE));
                    }
                } finally {
                    release(linker, output6);
                }
            } finally {
                release(linker, output);
            }
        }
        return -1F;
    }

    /** A COM method: slot {@code slot} of the object's vtable. */
    private static MethodHandle method(Linker linker, MemorySegment object, int slot, FunctionDescriptor type) {
        MemorySegment vtable = object.reinterpret(ValueLayout.ADDRESS.byteSize()).get(ValueLayout.ADDRESS, 0);
        MemorySegment function = vtable.reinterpret((slot + 1L) * ValueLayout.ADDRESS.byteSize())
                .getAtIndex(ValueLayout.ADDRESS, slot);
        return linker.downcallHandle(function, type);
    }

    private static void release(Linker linker, MemorySegment object) throws Throwable {
        MethodHandle release = method(linker, object, RELEASE,
                FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.ADDRESS));
        int references = (int) release.invokeExact(object); // what is left is DXGI's own
        assert references >= 0;
    }

    /** A GUID from its two halves as little-endian longs. */
    private static MemorySegment guid(Arena arena, long[] halves) {
        MemorySegment g = arena.allocate(16, 8);
        g.set(ValueLayout.JAVA_LONG, 0, halves[0]);
        g.set(ValueLayout.JAVA_LONG, 8, halves[1]);
        return g;
    }

    private static String wide(MemorySegment chars) {
        StringBuilder b = new StringBuilder();
        for (long at = 0; at + 1 < chars.byteSize(); at += 2) {
            char c = chars.get(ValueLayout.JAVA_CHAR, at);
            if (c == 0) {
                break;
            }
            b.append(c);
        }
        return b.toString().trim();
    }
}
