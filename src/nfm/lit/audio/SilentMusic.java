package nfm.lit.audio;

/** Music that plays nothing, for runs silenced with {@link SoundManager#silenceAll()}. */
final class SilentMusic implements RadicalMusic {

    private boolean paused;

    @Override
    public Type getType() {
        return Type.TYPE_UNKNOWN;
    }

    @Override
    @Deprecated
    public void stop() {
        paused = true;
    }

    @Override
    @Deprecated
    public void resume() {
        paused = false;
    }

    @Override
    public void unload() {
        paused = true;
    }

    @Override
    public void play() {
        paused = false;
    }

    @Override
    public boolean isPaused() {
        return paused;
    }

    @Override
    public void setPaused(boolean pause) {
        paused = pause;
    }
}
