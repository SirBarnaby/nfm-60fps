package nfm.lit.audio;
import fallk.logmaster.HLogger;

import java.util.Collections;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class SoundManager {

    private final ExecutorService executor;

    /* the game loads clips on its loading thread while the 3D sound may already read them */
    private final Map<String, SoundClip> clips;

    /** Whose sounds these are: 0, or 1 for split screen's Player 2 (see {@link #forPlayer}). */
    private final int player;

    public SoundManager() {
        this(Executors.newSingleThreadExecutor(), new ConcurrentHashMap<>(), 0);
    }

    private SoundManager(ExecutorService executor, Map<String, SoundClip> clips, int player) {
        this.executor = executor;
        this.clips = clips;
        this.player = player;
    }

    /**
     * The same clips played for split screen's Player 2: the new sound plays
     * them with voices of their own, or on Player 2's speaker (see
     * {@link Sound3D#setSplitSpeakers}). Classic plays them as Player 1's.
     */
    public SoundManager forPlayer(int player) {
        return new SoundManager(executor, clips, player);
    }

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

    /** True during a speculative tick, which may never happen: nothing it does may be felt or heard. */
    public static boolean isDiscarding() {
        return discarding;
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

    /** Every clip the game loaded, by name, for the 3D sound to load too. */
    public Map<String, SoundClip> clips() {
        return Collections.unmodifiableMap(clips);
    }

    /** Stops every Classic clip now, when the 3D sound takes over in the middle of a race. */
    public void stopClassic() {
        for (SoundClip clip : clips.values()) {
            if (clip instanceof SoundClipUnthreaded) {
                clip.stop();
            }
        }
    }

    public void play(String name) {
        if (!pass("play", name) || Sound3D.command("play", name, player) || silenced) {
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
        if (!pass("stop", name) || Sound3D.command("stop", name, player)) {
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
        if (!pass("loop", name) || Sound3D.command("loop", name, player) || silenced) {
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