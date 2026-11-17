package nfm.lit.audio;
//package nfm.territories.music;

// Credits to DragShot
// for his shit

import ds.nfm.Module;
import ds.nfm.ModuleLoader;
import ds.nfm.ModuleSlayer;

import nfm.lit.GameSparker;

import java.net.URL;

/**
 * The Class RadicalMod.
 */
public class RadicalMod implements RadicalMusic {

    private static String name = "";
    SuperClip sClip;
    boolean playing;
    int loaded;
    int rvol;

    String imod;

    String pmod;

    /**
     * Instantiates a new radical mod.
     */
    public RadicalMod() {
        playing = false;
        loaded = 0;
        rvol = 0;
        imod = "";
        pmod = "";
        loaded = 0;
        System.gc();
    }

    /**
     * Instantiates a new radical mod.
     *
     * @param path   the module file
     * @param volume how loud to render it
     * @param rate   playback rate, 8000 being normal
     * @param level  the module renderer's last setting (125 as a rule)
     * @param loud   render at the louder level, and remember how loud it came out
     * @param online fetch it from the multiplayer server instead
     */
    public RadicalMod(String path, int volume, int rate, final int level, final boolean loud, final boolean online) {
        playing = false;
        loaded = 0;
        rvol = 0;
        imod = "";
        pmod = "";
        final int sampleRate = 22000;
        rate = (int) (rate / 8000F * 2.0F * sampleRate);
        volume = (int) (volume * 0.8F);
        try {
            Module module;
            if (!online) {
                module = ModuleLoader.loadMod(path);
            } else {
                path = path.replace(' ', '_');
                final URL url = new URL("http://multiplayer.needformadness.com/tracks/music/" + path + ".zip");
                module = ModuleLoader.loadMod(url);
            }
            if (module.isLoaded()) {
                name = module.getName();
                if (name.trim().equals("")) {
                    name = "Untitled";
                }
                final ModuleSlayer slayer = ModuleLoader.prepareSlayer(module, rate, volume, level);
                final byte pcm[] = slayer.turnbytesNorm(loud);
                if (loud) {
                    rvol = slayer.olav;
                }
                sClip = new SuperClip(pcm, slayer.oln, sampleRate);
                sClip.rollBackPos = slayer.rollBackPos;
                sClip.rollBackTrig = slayer.oln - slayer.rollBackTrig;
                loaded = 2;
            }
        } catch (final Exception ex) {
            ex.printStackTrace();
            if (GameSparker.DEBUG) {
                System.out.println("Error downloading and making Mod: " + ex);
            }
            loaded = 0;
        }
        System.runFinalization();
        System.gc();
    }

    /**
     * Instantiates a new radical mod.
     *
     * @param path   the module file
     */
    public RadicalMod(final String path) {
        playing = false;
        loaded = 0;
        rvol = 0;
        imod = "";
        pmod = "";
        loaded = 1;
        imod = path;
        loadimod(false);
    }

    /**
     * Instantiates a new radical mod.
     *
     * @param path   the module file
     * @param loud   render at the louder level, and remember how loud it came out
     */
    public RadicalMod(final String path, final boolean loud) {
        playing = false;
        loaded = 0;
        rvol = 0;
        imod = "";
        pmod = "";
        loaded = 1;
        pmod = path;
        loadpmod(true);
    }

    /**
     * Loadimod.
     *
     * @param loud render at the louder level
     */
    private void loadimod(final boolean loud) {
        if (loaded == 1) {
            final int rate = 44000;
            int volume = 160;
            if (loud) {
                volume = 300;
            }
            final int level = 125;
            try {
                final Module module = ModuleLoader.loadMod(imod);
                if (module.isLoaded()) {
                    name = module.getName();
                    if (name.trim().equals("")) {
                        name = "Untitled";
                    }
                    final ModuleSlayer slayer = ModuleLoader.prepareSlayer(module, rate, volume, level);
                    final byte pcm[] = slayer.turnbytesNorm(loud);
                    if (loud) {
                        rvol = slayer.olav;
                    }
                    sClip = new SuperClip(pcm, slayer.oln, 22000);
                    sClip.rollBackPos = slayer.rollBackPos;
                    sClip.rollBackTrig = slayer.oln - slayer.rollBackTrig;
                    loaded = 2;
                }
            } catch (final Exception ex) {
                if (GameSparker.DEBUG) {
                    System.out.println("Error making a imod: " + ex);
                }
                loaded = 0;
            }
            System.runFinalization();
            System.gc();
        }
    }

    /**
     * Loadpmod.
     *
     * @param loud render at the louder level
     */
    private void loadpmod(final boolean loud) {
        if (loaded == 1) {
            final int rate = 44000;
            int volume = 160;
            if (loud) {
                volume = 300;
            }
            final int level = 125;
            try {
                final Module module = ModuleLoader.loadMod(pmod);
                if (module.isLoaded()) {
                    name = module.getName();
                    if (name.trim().equals("")) {
                        name = "Untitled";
                    }
                    final ModuleSlayer slayer = ModuleLoader.prepareSlayer(module, rate, volume, level);
                    final byte pcm[] = slayer.turnbytesNorm(loud);
                    if (loud) {
                        rvol = slayer.olav;
                    }
                    sClip = new SuperClip(pcm, slayer.oln, 22000);
                    sClip.rollBackPos = slayer.rollBackPos;
                    sClip.rollBackTrig = slayer.oln - slayer.rollBackTrig;
                    loaded = 2;
                }
            } catch (final Exception ex) {
                if (GameSparker.DEBUG) {
                    System.out.println("Error making a imod: " + ex);
                }
                loaded = 0;
            }
            System.runFinalization();
            System.gc();
        }
    }

    /*
     * (non-Javadoc)
     *
     * @see com.trashers.skyline.singleplayer.RadicalMusic#play()
     */
    @Override
    public void play() {
        if (!playing && loaded == 2) {
            sClip.play();
            if (sClip.stoped == 0) {
                playing = true;
            }
        }
    }

    /*
     * (non-Javadoc)
     *
     * @see com.trashers.skyline.singleplayer.RadicalMusic#unload()
     */
    @Override
    public void unload() {
        if (playing && loaded == 2) {
            sClip.stop();
            playing = false;
        }
        try {
            sClip.close();
            sClip = null;
        } catch (final Exception ex) {
        }
        try {
            imod = null;
        } catch (final Exception ex) {
        }
        System.gc();
        loaded = 0;
    }

    /*
     * (non-Javadoc)
     *
     * @see com.trashers.skyline.singleplayer.RadicalMusic#resume()
     */
    @Override
    @Deprecated
    public void resume() {
        if (!playing && loaded == 2) {
            sClip.resume();
            if (sClip.stoped == 0) {
                playing = true;
            }
        }
    }

    /*
     * (non-Javadoc)
     *
     * @see com.trashers.skyline.singleplayer.RadicalMusic#stop()
     */
    @Override
    @Deprecated
    public void stop() {
        if (playing && loaded == 2) {
            sClip.stop();
            playing = false;
        }
    }

    /*
     * (non-Javadoc)
     *
     * @see com.trashers.skyline.singleplayer.RadicalMusic#isPaused()
     */
    @Override
    public boolean isPaused() {
        return !playing;
    }

    /*
     * (non-Javadoc)
     *
     * @see com.trashers.skyline.singleplayer.RadicalMusic#setPaused(boolean)
     */
    @Override
    public void setPaused(final boolean pause) {
        if (pause)
            if (playing && loaded == 2) {
                sClip.stop();
                playing = false;
            } else {
            }
        else if (!playing && loaded == 2) {
            sClip.resume();
            if (sClip.stoped == 0) {
                playing = true;
            }
        }
    }

    /*
     * (non-Javadoc)
     *
     * @see com.trashers.skyline.singleplayer.RadicalMusic#getType()
     */
    @Override
    public Type getType() {
        return Type.TYPE_MOD;
    }

}
