package nfm.lit.audio;

import fallk.logmaster.HLogger;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;

/**
 * A stage's or a menu's music played by the 3D sound itself, decoded by
 * {@link MusicCodecs} instead of BASS. The game holds it as it held a
 * {@link RadicalBASS}, and it behaves the same: one piece of music at a
 * time, looping until it is unloaded.
 * <p>
 * If the 3D sound is not running when it should play (Classic chosen
 * meanwhile, or no sound device), it hands itself to BASS, and back to the
 * 3D sound when that starts again, so the music never stops for a switch.
 */
public final class StreamMusic implements RadicalMusic {

    /** The music playing or paused, the one a switch of sound hands over. */
    private static volatile StreamMusic current;

    private final String name;
    private final byte[] module;
    private final File file;
    private boolean playing;
    private boolean paused;
    private boolean unloaded;
    /** True while the 3D sound plays this; otherwise BASS may. */
    private boolean in3d;
    private RadicalBASS classic;

    private StreamMusic(String name, byte[] module, File file) {
        this.name = name;
        this.module = module;
        this.file = file;
    }

    /**
     * True when new music should be a StreamMusic: the 3D sound is chosen
     * and libopenmpt is here. Otherwise it is BASS, exactly as before.
     */
    static boolean wanted() {
        return Sound3D.isWanted() && MusicCodecs.available();
    }

    /** The modules' stereo separation by default, in percent. */
    public static final int DEFAULT_SEPARATION = MusicCodecs.DEFAULT_SEPARATION;

    /** The modules' stereo separation in percent: 0 mono, 100 an Amiga's hard left and right, up to 200. */
    public static int separation() {
        return MusicCodecs.separation();
    }

    /** Sets the stereo separation; the music playing takes it at once. */
    public static void setSeparation(int percent) {
        MusicCodecs.setSeparation(percent);
    }

    /** True when libopenmpt is here, so the 3D sound can play the music itself. */
    public static boolean available() {
        return MusicCodecs.available();
    }

    /** A tracker module from its bytes, as it comes out of its zip. */
    static StreamMusic module(String name, byte[] data) {
        return new StreamMusic(name, data, null);
    }

    /** An MP3, Ogg Vorbis or WAV recording from its file. */
    static StreamMusic recording(File file) {
        return new StreamMusic(file.getName(), null, file);
    }

    /** Opens a decoder for the 3D sound, on its thread. */
    MusicCodecs.Decoder open(int rate) throws IOException {
        return module != null ? MusicCodecs.module(name, module, rate) : MusicCodecs.recording(file);
    }

    String name() {
        return name;
    }

    @Override
    public Type getType() {
        return Type.TYPE_MOD;
    }

    /* the game calls this every frame on some screens: it only starts or resumes */
    @Override
    public synchronized void play() {
        if (unloaded || playing && !paused) {
            return;
        }
        boolean resume = playing;
        playing = true;
        paused = false;
        current = this;
        if (classic != null) {
            classic.play();
        } else if (resume && in3d) {
            in3d = Sound3D.music(this, Sound3D.MUSIC_RESUME);
            if (!in3d) {
                toClassic();
            }
        } else {
            in3d = Sound3D.music(this, Sound3D.MUSIC_PLAY);
            if (!in3d) {
                toClassic();
            }
        }
    }

    @Override
    public synchronized void setPaused(boolean pause) {
        if (!pause) {
            play();
            return;
        }
        if (unloaded || !playing || paused) {
            return;
        }
        paused = true;
        if (classic != null) {
            classic.stop();
        } else if (in3d) {
            Sound3D.music(this, Sound3D.MUSIC_PAUSE);
        }
    }

    @Override
    public boolean isPaused() {
        return paused;
    }

    @Override
    public synchronized void unload() {
        if (unloaded) {
            return;
        }
        unloaded = true;
        if (current == this) {
            current = null;
        }
        if (in3d) {
            Sound3D.music(this, Sound3D.MUSIC_UNLOAD);
            in3d = false;
        }
        if (classic != null) {
            classic.unload();
            classic = null;
        }
    }

    @Override
    @Deprecated
    public void stop() {
        setPaused(true);
    }

    @Override
    @Deprecated
    public void resume() {
        setPaused(false);
    }

    /* ------------------------------------------------- switching the sound */

    /** The 3D sound stopped or never started: the music carries on through BASS. */
    static void handToClassic() {
        StreamMusic m = current;
        if (m != null) {
            synchronized (m) {
                m.in3d = false;
                m.toClassic();
            }
        }
    }

    /** The 3D sound started: it takes the music back from BASS, from the start as BASS would. */
    static void handTo3d() {
        StreamMusic m = current;
        if (m == null) {
            return;
        }
        synchronized (m) {
            if (m.unloaded || m.in3d || !m.playing) {
                return;
            }
            if (m.classic != null) {
                m.classic.unload();
                m.classic = null;
            }
            m.in3d = Sound3D.music(m, Sound3D.MUSIC_PLAY);
            if (m.in3d && m.paused) {
                Sound3D.music(m, Sound3D.MUSIC_PAUSE);
            } else if (!m.in3d) {
                m.toClassic();
            }
        }
    }

    /* on this object's lock */
    private void toClassic() {
        // a hidden test run plays nothing out loud: the 3D sound renders the music or nothing does
        if (unloaded || classic != null || !playing || SoundManager.isSilenced()) {
            return;
        }
        try {
            File f = file;
            if (f == null) {
                f = new File("data/music/temp_" + name);
                Files.write(f.toPath(), module);
                f.deleteOnExit();
            }
            classic = new RadicalBASS(f);
            classic.play();
            if (paused) {
                classic.stop();
            }
        } catch (IOException ex) {
            HLogger.warn("Music: cannot hand " + name + " to BASS: " + ex);
        }
    }
}
