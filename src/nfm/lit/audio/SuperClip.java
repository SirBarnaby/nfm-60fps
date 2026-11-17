package nfm.lit.audio;
//package nfm.territories.music;

// Decompiled by Jad v1.5.8g. Copyright 2001 Pavel Kouznetsov.
// Jad home page: http://www.kpdus.com/jad.html
// Decompiler options: packimports(3)
// Source File Name:   SuperClip.java

import nfm.lit.GameSparker;

import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.SourceDataLine;
import java.io.ByteArrayInputStream;

/**
 * The Class SuperClip.
 */
class SuperClip implements Runnable {

    int stoped;
    ByteArrayInputStream stream;
    int rollBackPos;
    int rollBackTrig;
    private int skiprate;
    private Thread cliper;
    private SourceDataLine source;

    /**
     * Instantiates a new super clip.
     *
     * @param data       the sound, 16-bit mono PCM
     * @param length     how many of its bytes to play
     * @param sampleRate samples per second
     */
    SuperClip(final byte data[], final int length, final int sampleRate) {
        skiprate = 0;
        stoped = 1;
        source = null;
        rollBackPos = 0;
        rollBackTrig = 0;
        // changeGain = false;
        stoped = 2;
        skiprate = sampleRate;
        stream = new ByteArrayInputStream(data, 0, length);
    }

    /*
     * (non-Javadoc)
     *
     * @see java.lang.Runnable#run()
     */
    @Override
    public void run() {
        try {
            final AudioFormat format = new AudioFormat(javax.sound.sampled.AudioFormat.Encoding.PCM_SIGNED,
                    skiprate, 16, 1, 2, skiprate, false);
            final javax.sound.sampled.DataLine.Info info = new javax.sound.sampled.DataLine.Info(SourceDataLine.class,
                    format);
            source = (SourceDataLine) AudioSystem.getLine(info);
            source.open(format);
            source.start();
        } catch (final Exception ex) {
            stoped = 1;
        }
        boolean rewind = false;
        while (stoped == 0) {
            try {
                final int chunk = skiprate;
                int left = stream.available();
                if (left % 2 != 0) {
                    left++;
                }
                byte buffer[] = new byte[left <= chunk ? left : chunk];
                final int read = stream.read(buffer, 0, buffer.length);
                if (read == -1 || rollBackPos != 0 && left < rollBackTrig) {
                    rewind = true;
                }
                if (rewind) {
                    if (read != -1) {
                        source.write(buffer, 0, buffer.length);
                    }
                    stream.reset();
                    if (rollBackPos != 0) {
                        stream.skip(rollBackPos);
                    }
                    int remaining = stream.available();
                    if (remaining % 2 != 0) {
                        remaining++;
                    }
                    buffer = new byte[remaining <= chunk ? remaining : chunk];
                    stream.read(buffer, 0, buffer.length);
                    rewind = false;
                }
                source.write(buffer, 0, buffer.length);
            } catch (final Exception ex) {
                if (GameSparker.DEBUG) {
                    System.out.println("Play error: " + ex);
                }
                stoped = 1;
            }
            try {
                Thread.sleep(200L);
            } catch (final InterruptedException ex) {
            }
        }
        source.stop();
        source.close();
        source = null;
        stoped = 2;
    }

    /**
     * Play.
     */
    public void play() {
        if (stoped == 2) {
            stoped = 0;
            try {
                stream.reset();
            } catch (final Exception ex) {
            }
            cliper = new Thread(this);
            cliper.start();
        }
    }

    /**
     * Resume.
     */
    public void resume() {
        if (stoped == 2) {
            stoped = 0;
            cliper = new Thread(this);
            cliper.start();
        }
    }

    /**
     * Stop.
     */
    public void stop() {
        if (stoped == 0) {
            stoped = 1;
            if (source != null) {
                source.stop();
            }
        }
    }

    /**
     * Close.
     */
    public void close() {
        try {
            stream.close();
            stream = null;
        } catch (final Exception ex) {
        }
    }
    // Removed unused code found by UCDetector
    // private boolean changeGain;
}
