package nfm.lit.gpu;

import org.lwjgl.system.Configuration;

/** LWJGL settings, applied once before any LWJGL class does work. */
final class GpuLibraries {

    private static boolean prepared;

    private GpuLibraries() {
    }

    static synchronized void prepare() {
        if (prepared) {
            return;
        }
        prepared = true;
        // Java's foreign memory API instead of sun.misc.Unsafe, which Java 26 warns about
        Configuration.MEMORY_BACKEND.set("ffm");
    }
}
