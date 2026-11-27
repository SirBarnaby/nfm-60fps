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
    public int onWheel;
    /** Polygons drawn in each colour since the last reset. */
    public final java.util.Map<Integer, Integer> colors = new java.util.HashMap<>();
    /** The widest polygon drawn in each colour since the last reset: the farthest two of its corners apart. */
    public final java.util.Map<Integer, Float> spans = new java.util.HashMap<>();

    /** While true, every polygon's corners are kept in {@link #kept} (x, y, z of each in turn) with its colour in {@link #keptColor}. */
    public boolean keeping;
    public final java.util.List<float[]> kept = new java.util.ArrayList<>();
    public final java.util.List<Integer> keptColor = new java.util.ArrayList<>();
    public final java.util.List<Float> keptGlow = new java.util.ArrayList<>();
    private float glowNow;

    public void reset() {
        kept.clear();
        keptColor.clear();
        keptGlow.clear();
        glowNow = 0F;
        polygons = 0;
        corners = 0;
        moving = 0;
        onCar = 0;
        onWheel = 0;
        onScreen = 0;
        screenArea = 0F;
        maxGlow = 0F;
        colors.clear();
        spans.clear();
    }

    /** How wide the widest polygon drawn in colour {@code rgb} since the last reset is; 0 if none. */
    public float span(int rgb) {
        return spans.getOrDefault(rgb, 0F);
    }

    /** Polygons drawn in colour {@code rgb} since the last reset. */
    public int drawn(int rgb) {
        return colors.getOrDefault(rgb, 0);
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
        glowNow = level;
    }

    @Override
    public void world(float[] x, float[] y, float[] z, int n) {
        count(x, y, z, n);
    }

    @Override
    public void moving(float[] x, float[] y, float[] z, float vx, float vy, float vz, int n) {
        count(x, y, z, n);
        moving++;
    }

    @Override
    public void moving(float[] x, float[] y, float[] z, float[] vx, float[] vy, float[] vz, int n) {
        count(x, y, z, n);
        moving++;
    }

    /** Polygons fixed on the picture since the last reset, and how much of the picture they cover together (overlaps counted twice). */
    public int onScreen;
    public float screenArea;

    @Override
    public void screen(float[] x, float[] y, int n) {
        float twice = 0F;
        for (int k = 0; k < n; k++) {
            int next = (k + 1) % n;
            twice += x[k] * y[next] - x[next] * y[k];
        }
        screenArea += Math.abs(twice) / 2F;
        onScreen++;
        polygons++;
        corners += n;
        colors.merge(lastColor, 1, Integer::sum);
    }

    @Override
    public void onCar(int car, float[] x, float[] y, float[] z, int n) {
        count(x, y, z, n);
        onCar++;
    }

    @Override
    public void onWheel(int car, float[] x, float[] y, float[] z, int n, int steerX, int hubY, int hubZ) {
        count(x, y, z, n);
        onWheel++;
    }

    private void count(float[] x, float[] y, float[] z, int n) {
        polygons++;
        corners += n;
        if (keeping) {
            float[] c = new float[n * 3];
            for (int i = 0; i < n; i++) {
                c[i * 3] = x[i];
                c[i * 3 + 1] = y[i];
                c[i * 3 + 2] = z[i];
            }
            kept.add(c);
            keptColor.add(lastColor);
            keptGlow.add(glowNow);
        }
        colors.merge(lastColor, 1, Integer::sum);
        float widest = 0F;
        for (int i = 0; i < n; i++) {
            for (int j = i + 1; j < n; j++) {
                float dx = x[i] - x[j];
                float dy = y[i] - y[j];
                float dz = z[i] - z[j];
                widest = Math.max(widest, (float) Math.sqrt(dx * dx + dy * dy + dz * dz));
            }
        }
        spans.merge(lastColor, widest, Math::max);
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
            queue.under(car, drawer);
            queue.after(car, drawer);
        }
        queue.end(drawer);
        return polygons;
    }
}
