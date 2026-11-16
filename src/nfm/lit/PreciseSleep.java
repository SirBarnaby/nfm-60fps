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
 * Sleeps until a moment with sub-millisecond precision, without keeping a
 * core busy.
 * <p>
 * Thread.sleep() on Windows wakes on the system timer tick, up to 2 ms late,
 * so the frame loop used to spend the last 2 ms before each frame yielding in
 * a loop, which keeps a core awake and takes turbo power from the thread that
 * draws. Windows 10 1803 and later have a high-resolution waitable timer that
 * wakes within a fraction of a millisecond; this calls it through Java's
 * foreign function interface. Elsewhere, or if it cannot be created, sleeping
 * falls back to Thread.sleep() and a short yield.
 * <p>
 * One timer per instance: use it from one thread.
 */
final class PreciseSleep {

    private static final int CREATE_WAITABLE_TIMER_HIGH_RESOLUTION = 0x2;
    private static final int TIMER_ALL_ACCESS = 0x1F0003;
    private static final int INFINITE = -1;
    /** Waking this much early and yielding the rest absorbs the timer's own lateness. */
    private static final long MARGIN_NANOS = 300_000L;

    private final MethodHandle setWaitableTimer;
    private final MethodHandle waitForSingleObject;
    private final MemorySegment timer;
    private final MemorySegment dueTime;

    PreciseSleep() {
        MethodHandle set = null;
        MethodHandle wait = null;
        MemorySegment handle = MemorySegment.NULL;
        MemorySegment due = MemorySegment.NULL;
        if (System.getProperty("os.name", "").startsWith("Windows")) {
            try {
                Linker linker = Linker.nativeLinker();
                SymbolLookup kernel32 = SymbolLookup.libraryLookup("kernel32", Arena.global());
                MethodHandle create = linker.downcallHandle(kernel32.findOrThrow("CreateWaitableTimerExW"),
                        FunctionDescriptor.of(ValueLayout.ADDRESS, ValueLayout.ADDRESS, ValueLayout.ADDRESS,
                                ValueLayout.JAVA_INT, ValueLayout.JAVA_INT));
                set = linker.downcallHandle(kernel32.findOrThrow("SetWaitableTimer"),
                        FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.ADDRESS, ValueLayout.ADDRESS,
                                ValueLayout.JAVA_INT, ValueLayout.ADDRESS, ValueLayout.ADDRESS, ValueLayout.JAVA_INT));
                wait = linker.downcallHandle(kernel32.findOrThrow("WaitForSingleObject"),
                        FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.ADDRESS, ValueLayout.JAVA_INT));
                handle = (MemorySegment) create.invokeExact(MemorySegment.NULL, MemorySegment.NULL,
                        CREATE_WAITABLE_TIMER_HIGH_RESOLUTION, TIMER_ALL_ACCESS);
                due = Arena.global().allocate(ValueLayout.JAVA_LONG);
            } catch (Throwable ex) {
                HLogger.warn("High-resolution timer unavailable, using Thread.sleep: " + ex);
                handle = MemorySegment.NULL;
            }
        }
        precise = !handle.equals(MemorySegment.NULL);
        setWaitableTimer = set;
        waitForSingleObject = wait;
        timer = handle;
        dueTime = due;
    }

    private boolean precise;

    /** True while the high-resolution timer is in use. */
    boolean isPrecise() {
        return precise;
    }

    /** Returns at {@code deadline} (a System.nanoTime() value), or soon after. */
    void sleepUntil(long deadline) throws InterruptedException {
        long remaining = deadline - System.nanoTime();
        if (precise && remaining > MARGIN_NANOS) {
            if (waitNative(remaining - MARGIN_NANOS)) {
                remaining = deadline - System.nanoTime();
            } else {
                precise = false;
                HLogger.warn("High-resolution timer failed, using Thread.sleep from now on");
            }
        }
        if (!precise && remaining > 2_000_000L) {
            Thread.sleep(remaining / 1_000_000L - 1L);
            remaining = deadline - System.nanoTime();
        }
        while (remaining > 0L) {
            if (Thread.interrupted()) {
                throw new InterruptedException();
            }
            Thread.yield();
            remaining = deadline - System.nanoTime();
        }
    }

    /** Waits about {@code nanos}; false if the call failed. */
    private boolean waitNative(long nanos) {
        // relative due times are negative, in 100 ns units
        dueTime.set(ValueLayout.JAVA_LONG, 0L, -Math.max(1L, nanos / 100L));
        try {
            int set = (int) setWaitableTimer.invokeExact(timer, dueTime, 0, MemorySegment.NULL,
                    MemorySegment.NULL, 0);
            if (set == 0) {
                return false;
            }
            int waited = (int) waitForSingleObject.invokeExact(timer, INFINITE);
            return waited == 0;
        } catch (Throwable ex) {
            return false;
        }
    }
}
