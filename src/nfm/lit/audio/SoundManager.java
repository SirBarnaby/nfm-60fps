package nfm.lit.audio;
import fallk.logmaster.HLogger;

import java.util.HashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class SoundManager {

    private final ExecutorService executor = Executors.newSingleThreadExecutor();

    private final HashMap<String, SoundClip> clips = new HashMap<>();

    /**
     * Set for the hidden developer runs (race recording and verification), which
     * must never make a sound on the user's desktop. Music is silenced at the
     * same switch, see {@link TrackZipLoader}.
     */
    private static volatile boolean silenced;

    public static void silenceAll() {
        silenced = true;
    }

    public static boolean isSilenced() {
        return silenced;
    }

    /** Sees every sound command the game gives, heard or silenced: the race log keeps them. */
    public interface Listener {
        /** @param command "play", "loop" or "stop", a space, then the clip */
        void heard(String command);
    }

    private static volatile Listener listener;

    /**
     * While set, every sound command is thrown away, unheard and unlogged: a
     * speculative tick, which may never happen, must not make a sound.
     */
    private static volatile boolean discarding;

    public static void setListener(Listener l) {
        listener = l;
    }

    public static void setDiscarding(boolean discard) {
        discarding = discard;
    }

    /** Passes a command to the listener; false if it is to be thrown away. */
    private static boolean pass(String op, String name) {
        if (discarding) {
            return false;
        }
        Listener l = listener;
        if (l != null) {
            l.heard(op + " " + name);
        }
        return true;
    }

    public void add(String name, SoundClip clip) {
        clips.put(name, clip);
    }

    public void play(String name) {
        if (!pass("play", name) || silenced) {
            return;
        }
        SoundClip soundClip = clips.get(name);
        if (soundClip != null) {
            if (soundClip instanceof SoundClipThreaded) {
                executor.execute(((SoundClipThreaded) soundClip));
            } else {
                soundClip.play();
            }
        } else {
            HLogger.warn("clip not found: " + name);
        }

    }

    public void stop(String name) {
        if (!pass("stop", name)) {
            return;
        }
        SoundClip soundClip = clips.get(name);
        if (soundClip != null) {
            if (soundClip instanceof SoundClipUnthreaded) {
                soundClip.stop();
            } else {
                HLogger.warn("threaded clips cannot be stopped! " + name);
            }
        } else {
            HLogger.warn("clip not found: " + name);
        }
    }

    public void loop(String name) {
        if (!pass("loop", name) || silenced) {
            return;
        }
        SoundClip soundClip = clips.get(name);
        if (soundClip != null) {
            if (soundClip instanceof SoundClipUnthreaded) {
                soundClip.loop();
            } else {
                HLogger.warn("threaded clips cannot be looped! " + name);
            }
        } else {
            HLogger.warn("clip not found: " + name);
        }
    }

}