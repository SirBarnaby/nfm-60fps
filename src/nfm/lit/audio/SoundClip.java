package nfm.lit.audio;
public interface SoundClip {

    public void play();

    public void loop();

    public void stop();

    /** The clip's file as loaded (a WAV), for the 3D sound to decode; null if it was not kept. */
    public default byte[] data() {
        return null;
    }

}