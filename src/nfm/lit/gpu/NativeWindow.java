package nfm.lit.gpu;

import static org.lwjgl.system.jawt.JAWTFunctions.*;

import java.awt.Component;
import org.lwjgl.system.jawt.JAWT;
import org.lwjgl.system.jawt.JAWTDrawingSurface;
import org.lwjgl.system.jawt.JAWTDrawingSurfaceInfo;
import org.lwjgl.system.jawt.JAWTWin32DrawingSurfaceInfo;

/** The Windows window handle (HWND) behind a heavyweight AWT component. */
public final class NativeWindow {

    private NativeWindow() {
    }

    /**
     * Returns the HWND of {@code component}, which must be displayable, or 0
     * when it cannot be had (not on Windows, or AWT refuses).
     */
    public static long handle(Component component) {
        if (!System.getProperty("os.name", "").startsWith("Windows") || !component.isDisplayable()) {
            return 0L;
        }
        try {
            GpuLibraries.prepare();
            JAWT awt = JAWT.calloc();
            try {
                awt.version(JAWT_VERSION_9);
                if (!JAWT_GetAWT(awt)) {
                    return 0L;
                }
                JAWTDrawingSurface surface = JAWT_GetDrawingSurface(component, awt.GetDrawingSurface());
                if (surface == null) {
                    return 0L;
                }
                try {
                    int lock = JAWT_DrawingSurface_Lock(surface, surface.Lock());
                    if ((lock & JAWT_LOCK_ERROR) != 0) {
                        return 0L;
                    }
                    try {
                        JAWTDrawingSurfaceInfo info = JAWT_DrawingSurface_GetDrawingSurfaceInfo(surface,
                                surface.GetDrawingSurfaceInfo());
                        if (info == null) {
                            return 0L;
                        }
                        long hwnd = JAWTWin32DrawingSurfaceInfo.create(info.platformInfo()).hwnd();
                        JAWT_DrawingSurface_FreeDrawingSurfaceInfo(info, surface.FreeDrawingSurfaceInfo());
                        return hwnd;
                    } finally {
                        JAWT_DrawingSurface_Unlock(surface, surface.Unlock());
                    }
                } finally {
                    JAWT_FreeDrawingSurface(surface, awt.FreeDrawingSurface());
                }
            } finally {
                awt.free();
            }
        } catch (Throwable ex) {
            fallk.logmaster.HLogger.warn("Cannot get the window handle: " + ex);
            return 0L;
        }
    }
}
