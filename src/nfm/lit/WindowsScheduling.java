package nfm.lit;

import fallk.logmaster.HLogger;
import java.lang.foreign.Arena;
import java.lang.foreign.FunctionDescriptor;
import java.lang.foreign.Linker;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.SymbolLookup;
import java.lang.foreign.ValueLayout;
import java.lang.invoke.MethodHandle;

/**
 * Tells Windows the game is a game, through Java's foreign function
 * interface:
 * <ul>
 * <li>the threads that run ticks and draw frames join the Multimedia Class
 * Scheduler Service's "Games" task, so Windows lets them run ahead of
 * background work (an update, a virus scan, a browser) and a frame is not
 * held up by it; MMCSS still keeps a fifth of the processor for everything
 * else, so nothing starves;</li>
 * <li>the process opts out of power throttling (EcoQoS), so Windows does not
 * move it to efficiency clocks or efficiency cores when its window is in the
 * background or the laptop is on battery and thinks it is unimportant.</li>
 * </ul>
 * Neither needs administrator rights, and both end with the process. Where a
 * call does not exist (older Windows) or fails, nothing changes. It also says
 * whether the machine runs on battery, for the battery frame cap.
 */
final class WindowsScheduling {

    private static final int AVRT_PRIORITY_NORMAL = 0;
    private static final int AVRT_PRIORITY_HIGH = 1;
    private static final int PROCESS_POWER_THROTTLING = 4; // PROCESS_INFORMATION_CLASS
    private static final int THROTTLING_VERSION = 1;
    private static final int THROTTLE_EXECUTION_SPEED = 0x1;
    private static final int IGNORE_TIMER_RESOLUTION = 0x4;

    private static final boolean ON = !"false".equalsIgnoreCase(System.getProperty("nfm.mmcss"));

    private static MethodHandle powerStatus;
    private static boolean powerStatusUnavailable;

    private WindowsScheduling() {
    }

    /** True while the machine runs on its battery; false when plugged in, with no battery, or if Windows cannot say. */
    static synchronized boolean onBattery() {
        if (!GameSparker.IS_WINDOWS || powerStatusUnavailable) {
            return false;
        }
        try {
            if (powerStatus == null) {
                SymbolLookup kernel32 = SymbolLookup.libraryLookup("kernel32", Arena.global());
                powerStatus = Linker.nativeLinker().downcallHandle(kernel32.findOrThrow("GetSystemPowerStatus"),
                        FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.ADDRESS));
            }
            try (Arena arena = Arena.ofConfined()) {
                MemorySegment status = arena.allocate(12, 4); // SYSTEM_POWER_STATUS
                if ((int) powerStatus.invokeExact(status) == 0) {
                    return false;
                }
                return status.get(ValueLayout.JAVA_BYTE, 0) == 0; // ACLineStatus: 0 off the mains, 1 on, 255 unknown
            }
        } catch (Throwable ex) {
            powerStatusUnavailable = true;
            return false;
        }
    }

    /**
     * Puts the calling thread in MMCSS's "Games" task, at high priority
     * within it for the thread whose ticks the frames wait for, normal for
     * the one that draws. For the rest of the thread's life.
     */
    static void joinGamesTask(boolean high) {
        if (!ON || !GameSparker.IS_WINDOWS) {
            return;
        }
        String thread = Thread.currentThread().getName();
        try {
            Linker linker = Linker.nativeLinker();
            SymbolLookup avrt = SymbolLookup.libraryLookup("avrt", Arena.global());
            MethodHandle join = linker.downcallHandle(avrt.findOrThrow("AvSetMmThreadCharacteristicsW"),
                    FunctionDescriptor.of(ValueLayout.ADDRESS, ValueLayout.ADDRESS, ValueLayout.ADDRESS));
            MethodHandle priority = linker.downcallHandle(avrt.findOrThrow("AvSetMmThreadPriority"),
                    FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.ADDRESS, ValueLayout.JAVA_INT));
            MemorySegment task;
            try (Arena arena = Arena.ofConfined()) {
                MemorySegment name = arena.allocateFrom("Games", java.nio.charset.StandardCharsets.UTF_16LE);
                MemorySegment index = arena.allocate(ValueLayout.JAVA_INT);
                task = (MemorySegment) join.invokeExact(name, index);
            }
            if (task.equals(MemorySegment.NULL)) {
                HLogger.warn("Windows would not schedule " + thread + " as a game thread (MMCSS)");
                return;
            }
            int ok = (int) priority.invokeExact(task, high ? AVRT_PRIORITY_HIGH : AVRT_PRIORITY_NORMAL);
            HLogger.info("Windows schedules " + thread + " as a game thread (MMCSS Games"
                    + (high && ok != 0 ? ", high)" : ")"));
        } catch (Throwable ex) {
            HLogger.warn("Cannot schedule " + thread + " as a game thread (MMCSS): " + ex);
        }
    }

    /** Asks Windows never to throttle the game's processor speed to save power (EcoQoS). */
    static void noPowerThrottling() {
        if (!ON || !GameSparker.IS_WINDOWS) {
            return;
        }
        try {
            Linker linker = Linker.nativeLinker();
            SymbolLookup kernel32 = SymbolLookup.libraryLookup("kernel32", Arena.global());
            MethodHandle currentProcess = linker.downcallHandle(kernel32.findOrThrow("GetCurrentProcess"),
                    FunctionDescriptor.of(ValueLayout.ADDRESS));
            MethodHandle setInformation = linker.downcallHandle(kernel32.findOrThrow("SetProcessInformation"),
                    FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.ADDRESS, ValueLayout.JAVA_INT,
                            ValueLayout.ADDRESS, ValueLayout.JAVA_INT));
            MemorySegment process = (MemorySegment) currentProcess.invokeExact();
            try (Arena arena = Arena.ofConfined()) {
                // PROCESS_POWER_THROTTLING_STATE: version, what is controlled, and 0 = not throttled
                MemorySegment state = arena.allocate(12, 4);
                state.set(ValueLayout.JAVA_INT, 0, THROTTLING_VERSION);
                state.set(ValueLayout.JAVA_INT, 4, THROTTLE_EXECUTION_SPEED | IGNORE_TIMER_RESOLUTION);
                state.set(ValueLayout.JAVA_INT, 8, 0);
                int ok = (int) setInformation.invokeExact(process, PROCESS_POWER_THROTTLING, state, 12);
                if (ok == 0) {
                    // Windows 10 does not know the timer resolution bit
                    state.set(ValueLayout.JAVA_INT, 4, THROTTLE_EXECUTION_SPEED);
                    ok = (int) setInformation.invokeExact(process, PROCESS_POWER_THROTTLING, state, 12);
                }
                if (ok != 0) {
                    HLogger.info("Windows will not throttle the game to save power (EcoQoS off)");
                } else {
                    HLogger.warn("Windows would not stop throttling the game to save power");
                }
            }
        } catch (Throwable ex) {
            HLogger.warn("Cannot stop Windows throttling the game to save power: " + ex);
        }
    }
}
