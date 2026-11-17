package nfm.lit.gpu;

import java.lang.foreign.Arena;
import java.lang.foreign.FunctionDescriptor;
import java.lang.foreign.Linker;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.SymbolLookup;
import java.lang.foreign.ValueLayout;
import java.lang.invoke.MethodHandle;

/**
 * What Windows says about the monitor a window is on: whether HDR is on,
 * and how bright it shows SDR white (the "SDR content brightness" slider), so
 * the game's colours look in HDR exactly as they do on the desktop. Read
 * through Windows' display configuration API with Java's foreign function
 * interface; cheap enough to ask again every few seconds, which is how a
 * change of the setting, or the window moving to another monitor, is noticed.
 */
public final class DisplayColor {

    /** On an SDR monitor, or when Windows cannot tell: white is 80 nits, sRGB's reference. */
    public static final float SDR_WHITE_NITS = 80F;

    /** HDR (advanced colour) is on for the monitor. */
    public final boolean hdr;
    /** How bright Windows shows SDR white on the monitor, in nits. */
    public final float whiteNits;
    /** The brightest the monitor shows, in nits, as it tells Windows; 0 if it does not say. */
    public final float peakNits;

    private DisplayColor(boolean hdr, float whiteNits, float peakNits) {
        this.hdr = hdr;
        this.whiteNits = whiteNits;
        this.peakNits = peakNits;
    }

    @Override
    public boolean equals(Object o) {
        return o instanceof DisplayColor d && d.hdr == hdr && d.whiteNits == whiteNits && d.peakNits == peakNits;
    }

    @Override
    public int hashCode() {
        return (Boolean.hashCode(hdr) * 31 + Float.hashCode(whiteNits)) * 31 + Float.hashCode(peakNits);
    }

    @Override
    public String toString() {
        return hdr ? "HDR on, SDR white " + Math.round(whiteNits) + " nits, peak "
                + (peakNits > 0F ? Math.round(peakNits) + " nits" : "unknown") : "SDR";
    }

    private static final DisplayColor SDR = new DisplayColor(false, SDR_WHITE_NITS, 0F);

    /**
     * HDR as {@code monitor} has it, or on an SDR monitor HDR with white at
     * 80 nits, which Windows shows exactly as SDR: for tests.
     */
    static DisplayColor forced(DisplayColor monitor) {
        return monitor.hdr ? monitor : new DisplayColor(true, SDR_WHITE_NITS, 0F);
    }

    /* the last monitor whose peak was asked for, and its answer: asking DXGI takes a few milliseconds */
    private static long peakMonitor;
    private static float peakOfMonitor;

    private static final int MONITOR_DEFAULTTONEAREST = 2;
    private static final int QDC_ONLY_ACTIVE_PATHS = 2;
    private static final int PATH_SIZE = 72;
    private static final int MODE_SIZE = 64;
    private static final int GET_SOURCE_NAME = 1;
    private static final int GET_ADVANCED_COLOR_INFO = 9;
    private static final int GET_SDR_WHITE_LEVEL = 11;
    private static final int ADVANCED_COLOR_ENABLED = 0x2;

    private static MethodHandle monitorFromWindow;
    private static MethodHandle getMonitorInfo;
    private static MethodHandle getBufferSizes;
    private static MethodHandle queryDisplayConfig;
    private static MethodHandle getDeviceInfo;
    private static boolean unavailable;

    private static synchronized boolean bind() {
        if (monitorFromWindow != null) {
            return true;
        }
        if (unavailable) {
            return false;
        }
        try {
            Linker linker = Linker.nativeLinker();
            SymbolLookup user32 = SymbolLookup.libraryLookup("user32", Arena.global());
            monitorFromWindow = linker.downcallHandle(user32.findOrThrow("MonitorFromWindow"),
                    FunctionDescriptor.of(ValueLayout.ADDRESS, ValueLayout.ADDRESS, ValueLayout.JAVA_INT));
            getMonitorInfo = linker.downcallHandle(user32.findOrThrow("GetMonitorInfoW"),
                    FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.ADDRESS, ValueLayout.ADDRESS));
            getBufferSizes = linker.downcallHandle(user32.findOrThrow("GetDisplayConfigBufferSizes"),
                    FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.JAVA_INT, ValueLayout.ADDRESS,
                            ValueLayout.ADDRESS));
            queryDisplayConfig = linker.downcallHandle(user32.findOrThrow("QueryDisplayConfig"),
                    FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.JAVA_INT, ValueLayout.ADDRESS,
                            ValueLayout.ADDRESS, ValueLayout.ADDRESS, ValueLayout.ADDRESS, ValueLayout.ADDRESS));
            getDeviceInfo = linker.downcallHandle(user32.findOrThrow("DisplayConfigGetDeviceInfo"),
                    FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.ADDRESS));
            return true;
        } catch (Throwable ex) {
            unavailable = true;
            return false;
        }
    }

    /**
     * The monitor {@code window} (an HWND) is on, or mostly on. SDR if
     * Windows cannot tell, which is also the answer before Windows 10 1709.
     */
    public static DisplayColor of(long window) {
        if (window == 0L || !bind()) {
            return SDR;
        }
        try (Arena arena = Arena.ofConfined()) {
            MemorySegment monitor = (MemorySegment) monitorFromWindow.invokeExact(MemorySegment.ofAddress(window),
                    MONITOR_DEFAULTTONEAREST);
            String device = monitorDevice(arena, monitor);
            if (device == null) {
                return SDR;
            }
            MemorySegment counts = arena.allocate(ValueLayout.JAVA_INT, 2);
            if ((int) getBufferSizes.invokeExact(QDC_ONLY_ACTIVE_PATHS, counts, counts.asSlice(4)) != 0) {
                return SDR;
            }
            int paths = counts.get(ValueLayout.JAVA_INT, 0);
            int modes = counts.get(ValueLayout.JAVA_INT, 4);
            MemorySegment pathInfo = arena.allocate((long) PATH_SIZE * Math.max(1, paths), 8);
            MemorySegment modeInfo = arena.allocate((long) MODE_SIZE * Math.max(1, modes), 8);
            if ((int) queryDisplayConfig.invokeExact(QDC_ONLY_ACTIVE_PATHS, counts, pathInfo, counts.asSlice(4),
                    modeInfo, MemorySegment.NULL) != 0) {
                return SDR;
            }
            paths = counts.get(ValueLayout.JAVA_INT, 0);
            for (int i = 0; i < paths; i++) {
                MemorySegment path = pathInfo.asSlice((long) PATH_SIZE * i, PATH_SIZE);
                // sourceInfo: adapter LUID at 0, id at 8; targetInfo: adapter LUID at 20, id at 28
                if (device.equalsIgnoreCase(sourceName(arena, path.get(ValueLayout.JAVA_LONG_UNALIGNED, 0),
                        path.get(ValueLayout.JAVA_INT, 8)))) {
                    long adapter = path.get(ValueLayout.JAVA_LONG_UNALIGNED, 20);
                    int target = path.get(ValueLayout.JAVA_INT, 28);
                    MemorySegment color = header(arena, GET_ADVANCED_COLOR_INFO, 32, adapter, target);
                    if ((int) getDeviceInfo.invokeExact(color) != 0
                            || (color.get(ValueLayout.JAVA_INT, 20) & ADVANCED_COLOR_ENABLED) == 0) {
                        forgetPeak(); // DXGI's answer can differ once HDR is on again
                        return SDR;
                    }
                    MemorySegment white = header(arena, GET_SDR_WHITE_LEVEL, 24, adapter, target);
                    float nits = (int) getDeviceInfo.invokeExact(white) == 0
                            ? white.get(ValueLayout.JAVA_INT, 20) / 1000F * SDR_WHITE_NITS : SDR_WHITE_NITS;
                    return new DisplayColor(true, Math.max(SDR_WHITE_NITS, nits), peakOf(monitor.address()));
                }
            }
            return SDR;
        } catch (Throwable ex) {
            return SDR;
        }
    }

    /** The peak of an HDR monitor, asked for once while it stays the window's monitor in HDR. */
    private static synchronized float peakOf(long monitor) {
        if (monitor != peakMonitor) {
            peakMonitor = monitor;
            peakOfMonitor = GpuAdapter.peakNits(monitor);
        }
        return peakOfMonitor;
    }

    /** The GDI name of a monitor (HMONITOR), such as \\.\DISPLAY1. */
    private static String monitorDevice(Arena arena, MemorySegment monitor) throws Throwable {
        if (monitor.equals(MemorySegment.NULL)) {
            return null;
        }
        MemorySegment info = arena.allocate(104, 4); // MONITORINFOEXW
        info.set(ValueLayout.JAVA_INT, 0, 104);
        if ((int) getMonitorInfo.invokeExact(monitor, info) == 0) {
            return null;
        }
        return wide(info.asSlice(40, 64));
    }

    private static synchronized void forgetPeak() {
        peakMonitor = 0L;
    }

    private static String sourceName(Arena arena, long adapter, int source) throws Throwable {
        MemorySegment name = header(arena, GET_SOURCE_NAME, 84, adapter, source);
        return (int) getDeviceInfo.invokeExact(name) == 0 ? wide(name.asSlice(20, 64)) : "";
    }

    /** A DISPLAYCONFIG request of {@code size} bytes: its header filled in, the rest zero. */
    private static MemorySegment header(Arena arena, int type, int size, long adapter, int id) {
        MemorySegment s = arena.allocate(size, 4);
        s.set(ValueLayout.JAVA_INT, 0, type);
        s.set(ValueLayout.JAVA_INT, 4, size);
        s.set(ValueLayout.JAVA_LONG_UNALIGNED, 8, adapter);
        s.set(ValueLayout.JAVA_INT, 16, id);
        return s;
    }

    private static String wide(MemorySegment chars) {
        StringBuilder b = new StringBuilder();
        for (long at = 0; at + 1 < chars.byteSize(); at += 2) {
            char c = chars.get(ValueLayout.JAVA_CHAR_UNALIGNED, at);
            if (c == 0) {
                break;
            }
            b.append(c);
        }
        return b.toString();
    }
}
