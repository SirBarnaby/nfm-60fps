package nfm.lit;

import java.awt.Graphics2D;
import nfm.lit.features.Canvas;

/**
 * Draws the extras' polygons into the race's scene as the game draws its own:
 * whole pixels for software, and while a frame is recorded for the graphics
 * card the exact corners ({@link Precise}), kept as points in a kept scene so
 * the card places them again for every frame. See-through polygons are
 * dithered on the card ({@link SceneRecorder#dither}) and drawn with alpha
 * in software. Used on the game thread, inside {@code renderScene} only.
 */
final class FeatureCanvas implements Canvas {

    private Graphics2D g;
    /** The recorder when the scene is recorded for the card, else null. */
    private SceneRecorder recorder;
    private float share = 1F;
    private float base = 1F;
    private ContO[] scene;
    private int cars;

    private final int[] sx = new int[MAX_CORNERS];
    private final int[] sy = new int[MAX_CORNERS];
    private int rgb;
    private boolean colored;
    private int coverage = 255;
    private boolean glowing;

    /**
     * Starts a frame drawn into {@code target}, {@code share} of the way
     * between the ticks shown, the last real tick at {@code base} (1, or 0
     * when the tick ahead is shown); car {@code c} is {@code scene[c]}.
     */
    void begin(Graphics2D target, float share, float base, ContO[] scene, int cars) {
        this.g = target;
        this.recorder = target instanceof SceneRecorder r ? r : null;
        this.share = share;
        this.base = base;
        this.scene = scene;
        this.cars = cars;
    }

    /** Before each item: opaque, not glowing, no colour yet. */
    void startItem() {
        colored = false;
        coverage = 255;
    }

    /** After each item: back to what the game's own drawing expects. */
    void endItem() {
        if (coverage != 255 && recorder != null) {
            recorder.dither(255);
        }
        coverage = 255;
        if (glowing) {
            Glow.off();
            glowing = false;
        }
    }

    void end() {
        g = null;
        recorder = null;
        scene = null;
    }

    @Override
    public void color(int rgb) {
        this.rgb = rgb & 0xffffff;
        colored = true;
        apply();
    }

    private void apply() {
        if (recorder != null) {
            recorder.dither(coverage);
            g.setColor(Colors.ofRgb(rgb));
        } else {
            g.setColor(coverage >= 255 ? Colors.ofRgb(rgb) : Colors.ofArgb(coverage << 24 | rgb));
        }
    }

    @Override
    public void dither(float level) {
        int c = Math.max(0, Math.min(255, Math.round(level * 255F)));
        if (c != coverage) {
            coverage = c;
            if (colored) {
                apply();
            }
        }
    }

    @Override
    public void glow(float level) {
        int l = Math.max(0, Math.min(255, Math.round(level * 255F)));
        Glow.set(l);
        glowing = l != 0;
    }

    @Override
    public int fog(int color, float x, float y, float z) {
        int r = lit(color >> 16 & 0xff, 0);
        int gr = lit(color >> 8 & 0xff, 1);
        int b = lit(color & 0xff, 2);
        if (!Medium.trk) {
            float av = Precise.featureDistance(x, y, z);
            int fogd = Medium.fogd;
            for (int band = 0; band < 8; band++) {
                if (av > Medium.fade[band]) {
                    r = (r * fogd + Medium.cfade[0]) / (fogd + 1);
                    gr = (gr * fogd + Medium.cfade[1]) / (fogd + 1);
                    b = (b * fogd + Medium.cfade[2]) / (fogd + 1);
                }
            }
        }
        return r << 16 | gr << 8 | b;
    }

    /** A channel in the stage's light, as {@code Plane} lights a model's colour. */
    private static int lit(int value, int channel) {
        int c = (int) (value + value * (Medium.snap[channel] / 100F));
        return Math.max(0, Math.min(255, c));
    }

    @Override
    public void world(float[] x, float[] y, float[] z, int n) {
        if (n >= 3 && n <= MAX_CORNERS && Precise.featureWorld(x, y, z, n, sx, sy)) {
            g.fillPolygon(sx, sy, n);
        }
    }

    @Override
    public void moving(float[] x, float[] y, float[] z, float vx, float vy, float vz, int n) {
        if (n >= 3 && n <= MAX_CORNERS && Precise.featureMoving(x, y, z, vx, vy, vz, n, share, base, sx, sy)) {
            g.fillPolygon(sx, sy, n);
        }
    }

    @Override
    public void onCar(int car, float[] x, float[] y, float[] z, int n) {
        if (car >= 0 && car < cars && n >= 3 && n <= MAX_CORNERS
                && Precise.featureOnCar(scene[car], x, y, z, n, sx, sy)) {
            g.fillPolygon(sx, sy, n);
        }
    }
}
