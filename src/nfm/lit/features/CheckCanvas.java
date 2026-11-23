package nfm.lit.features;

/**
 * A canvas for the extras' checks: it draws nothing and counts what would
 * be drawn, so a check can tell that a feature draws, and how much.
 */
public final class CheckCanvas implements Canvas {

    public int polygons;
    public int corners;
    public int lastColor;
    public float lastCoverage = 1F;
    public float maxGlow;
    public int moving;
    public int onCar;

    public void reset() {
        polygons = 0;
        corners = 0;
        moving = 0;
        onCar = 0;
        maxGlow = 0F;
    }

    @Override
    public void color(int rgb) {
        lastColor = rgb;
    }

    @Override
    public int fog(int rgb, float x, float y, float z) {
        return rgb;
    }

    @Override
    public void dither(float coverage) {
        lastCoverage = coverage;
    }

    @Override
    public void glow(float level) {
        maxGlow = Math.max(maxGlow, level);
    }

    @Override
    public void world(float[] x, float[] y, float[] z, int n) {
        polygons++;
        corners += n;
    }

    @Override
    public void moving(float[] x, float[] y, float[] z, float vx, float vy, float vz, int n) {
        polygons++;
        corners += n;
        moving++;
    }

    @Override
    public void moving(float[] x, float[] y, float[] z, float[] vx, float[] vy, float[] vz, int n) {
        polygons++;
        corners += n;
        moving++;
    }

    @Override
    public void onCar(int car, float[] x, float[] y, float[] z, int n) {
        polygons++;
        corners += n;
        onCar++;
    }

    /** Queues and draws everything {@code feature} draws in a frame seen from {@code (x, y, z)}; returns the polygons. */
    public int frame(Feature feature, float x, float y, float z, int tick) {
        DrawQueue queue = new DrawQueue();
        queue.begin(x, y, z, tick);
        queue.owner(0);
        feature.queue(queue);
        queue.sort();
        reset();
        DrawQueue.Drawer drawer = (f, item) -> feature.draw(this, item);
        queue.before(Integer.MAX_VALUE, false, drawer);
        for (int car = 0; car < 16; car++) {
            queue.after(car, drawer);
        }
        queue.end(drawer);
        return polygons;
    }
}
