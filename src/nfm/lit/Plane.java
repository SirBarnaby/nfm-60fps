package nfm.lit;
import java.awt.*;
import java.util.Arrays;

public class Plane {
    private final Trackers t;
    public final int[] ox;
    public final int[] oy;
    public final int[] oz;
    public int n;
    public final int[] c;
    public final int[] oc;
    public final float[] hsb;
    public boolean glass;
    public int gr;
    public int fs;
    private int disline;
    public boolean road;
    public int light;
    public int master;
    public int wx;
    public int wz;
    public int wy;
    private float deltaf;
    private float projf;
    public int av;
    public int bfase;
    public boolean nocol;
    public int chip;
    public float ctmag;
    private int cxz;
    private int cxy;
    private int czy;
    private final int[] cox;
    private final int[] coz;
    private final int[] coy;
    private int dx;
    private int dy;
    private int dz;
    private int vx;
    private int vy;
    private int vz;
    public int embos;
    private int typ;
    private int pa;
    private int pb;
    public int flx;
    public boolean solo;
    private DrawState drawState;

    /**
     * The fields d() advances while drawing: the wreck and flame animation
     * (embos, hsb, pa, pb), flying debris (chip and its motion) and the repair
     * flash (flx). See {@link ContO#saveDrawState()}.
     */
    private static final class DrawState {
        final float[] hsb = new float[3];
        final int[] cox = new int[3];
        final int[] coy = new int[3];
        final int[] coz = new int[3];
        int bfase;
        int chip;
        float ctmag;
        int cxz;
        int cxy;
        int czy;
        int dx;
        int dy;
        int dz;
        int vx;
        int vy;
        int vz;
        int embos;
        int pa;
        int pb;
        int flx;
    }

    void saveDrawState() {
        DrawState s = drawState;
        if (s == null) {
            s = new DrawState();
            drawState = s;
        }
        System.arraycopy(hsb, 0, s.hsb, 0, 3);
        System.arraycopy(cox, 0, s.cox, 0, 3);
        System.arraycopy(coy, 0, s.coy, 0, 3);
        System.arraycopy(coz, 0, s.coz, 0, 3);
        s.bfase = bfase;
        s.chip = chip;
        s.ctmag = ctmag;
        s.cxz = cxz;
        s.cxy = cxy;
        s.czy = czy;
        s.dx = dx;
        s.dy = dy;
        s.dz = dz;
        s.vx = vx;
        s.vy = vy;
        s.vz = vz;
        s.embos = embos;
        s.pa = pa;
        s.pb = pb;
        s.flx = flx;
    }

    void restoreDrawState() {
        DrawState s = drawState;
        if (s == null) {
            return;
        }
        System.arraycopy(s.hsb, 0, hsb, 0, 3);
        System.arraycopy(s.cox, 0, cox, 0, 3);
        System.arraycopy(s.coy, 0, coy, 0, 3);
        System.arraycopy(s.coz, 0, coz, 0, 3);
        bfase = s.bfase;
        chip = s.chip;
        ctmag = s.ctmag;
        cxz = s.cxz;
        cxy = s.cxy;
        czy = s.czy;
        dx = s.dx;
        dy = s.dy;
        dz = s.dz;
        vx = s.vx;
        vy = s.vy;
        vz = s.vz;
        embos = s.embos;
        pa = s.pa;
        pb = s.pb;
        flx = s.flx;
    }

    /**
     * Saves or restores, through {@code b}, everything a race can change on
     * this polygon (see {@link RaceSnapshot}). On every polygon, drawing
     * advances the distance the next draw sorts by, the projection factor,
     * how many corners a round part is drawn with, and the flash. Only a car's
     * polygons change otherwise ({@code onCar}): a crash dents the corners,
     * dirties and repaints them and starts the debris, wreck and flame
     * animation, and the repair hoop recolours them.
     */
    void syncRaceState(StateBuffer b, boolean onCar) {
        av = b.value(av);
        projf = b.value(projf);
        n = b.value(n);
        flx = b.value(flx);
        if (!onCar) {
            return;
        }
        b.contents(ox);
        b.contents(oy);
        b.contents(oz);
        b.contents(c);
        b.contents(hsb);
        b.contents(cox);
        b.contents(coy);
        b.contents(coz);
        gr = b.value(gr);
        bfase = b.value(bfase);
        chip = b.value(chip);
        ctmag = b.value(ctmag);
        cxz = b.value(cxz);
        cxy = b.value(cxy);
        czy = b.value(czy);
        dx = b.value(dx);
        dy = b.value(dy);
        dz = b.value(dz);
        vx = b.value(vx);
        vy = b.value(vy);
        vz = b.value(vz);
        embos = b.value(embos);
        pa = b.value(pa);
        pb = b.value(pb);
    }

    public void loadprojf() {
        projf = 1.0F;
        int corner = 0;
        do {
            int other = 0;
            do {
                if (other != corner) {
                    projf *= (float) (Math.sqrt((ox[corner] - ox[other]) * (ox[corner] - ox[other]) + (oz[corner] - oz[other]) * (oz[corner] - oz[other]))
                            / 100D);
                }
            } while (++other < 3);
        } while (++corner < 3);
        projf = projf / 3F;
    }

    /**
     * Polygons of type -15 (the sand offroad pieces) get a random tint, and
     * their corners nudged by a few units, every time the piece is copied, as
     * in the original. The numbers come from this stream instead of the clock:
     * seeded the same at start-up, and from the race seed whenever a stage is
     * set up, so a stage looks the same every time a race on it is replayed.
     */
    private static final RaceRandom shapeJitter = new RaceRandom();

    static {
        seedShapes(0L);
        initialize(EffectTrails.class);
        initialize(Glow.class);
    }

    /**
     * Initializes a class {@link #d} uses only on its rarer paths (flying
     * debris, glowing lights). Java can compile d() fully while the menus draw
     * cars, before a race has initialized those classes; d() is then compiled
     * with a trap where it uses one, and the first race to take that path
     * (the first chip of debris, about 1.7 s in) threw the whole compiled
     * method away: two or three ticks of 50 to 100 ms while d() ran slowly and
     * was compiled again. Neither class sets up anything the race could see.
     */
    private static void initialize(Class<?> type) {
        try {
            Class.forName(type.getName(), true, type.getClassLoader());
        } catch (ClassNotFoundException ex) {
            throw new IllegalStateException(ex);
        }
    }

    /** Called with the race seed when a stage is set up, see {@link Medium#seedRace(long)}. */
    static void seedShapes(long raceSeed) {
        shapeJitter.reseed(RaceRandom.derive(raceSeed, RaceRandom.STREAM_SHAPES));
    }

    public Plane(Trackers trackers, int pointX[], int pointZ[], int pointY[], int count, int color[], boolean isGlass,
                 int group, int faceSign, int wheelX, int wheelY, int wheelZ, int outline, int burn, boolean isRoad, int lamp, boolean isSolo) {
        c = new int[3];
        oc = new int[3];
        hsb = new float[3];
        glass = false;
        gr = 0;
        fs = 0;
        disline = 7;
        road = false;
        light = 0;
        master = 0;
        wx = 0;
        wz = 0;
        wy = 0;
        deltaf = 1.0F;
        projf = 1.0F;
        av = 0;
        bfase = 0;
        nocol = false;
        chip = 0;
        ctmag = 0.0F;
        cxz = 0;
        cxy = 0;
        czy = 0;
        cox = new int[3];
        coz = new int[3];
        coy = new int[3];
        dx = 0;
        dy = 0;
        dz = 0;
        vx = 0;
        vy = 0;
        vz = 0;
        embos = 0;
        typ = 0;
        pa = 0;
        pb = 0;
        flx = 0;
        t = trackers;
        n = count;
        ox = new int[n];
        oz = new int[n];
        oy = new int[n];
        for (int corner = 0; corner < n; corner++) {
            ox[corner] = pointX[corner];
            oy[corner] = pointY[corner];
            oz[corner] = pointZ[corner];
        }

        int spanX = Math.abs(ox[2] - ox[1]);
        int spanY = Math.abs(oy[2] - oy[1]);
        int spanZ = Math.abs(oz[2] - oz[1]);
        if (spanY <= spanX && spanY <= spanZ) {
            typ = 2;
        }
        if (spanX <= spanY && spanX <= spanZ) {
            typ = 1;
        }
        if (spanZ <= spanX && spanZ <= spanY) {
            typ = 3;
        }
        int channel = 0;
        do {
            oc[channel] = color[channel];
        } while (++channel < 3);
        if (group == -15) {
            channel = (int) (185D + shapeJitter.nextDouble() * 30D);
            color[0] = (217 + channel) / 2;
            color[1] = (189 + channel) / 2;
            color[2] = (132 + channel) / 2;
            for (int corner = 0; corner < n; corner++) {
                if (shapeJitter.nextDouble() > shapeJitter.nextDouble()) {
                    ox[corner] += (int) (8D * shapeJitter.nextDouble() - 4D);
                }
                if (shapeJitter.nextDouble() > shapeJitter.nextDouble()) {
                    oy[corner] += (int) (8D * shapeJitter.nextDouble() - 4D);
                }
                if (shapeJitter.nextDouble() > shapeJitter.nextDouble()) {
                    oz[corner] += (int) (8D * shapeJitter.nextDouble() - 4D);
                }
            }

        }
        if (color[0] == color[1] && color[1] == color[2]) {
            nocol = true;
        }
        if (!isGlass) {
            channel = 0;
            do {
                c[channel] = (int) (color[channel] + color[channel] * (Medium.snap[channel] / 100F));
                if (c[channel] > 255) {
                    c[channel] = 255;
                }
                if (c[channel] < 0) {
                    c[channel] = 0;
                }
            } while (++channel < 3);
        }
        if (isGlass) {
            channel = 0;
            do {
                c[channel] = (Medium.csky[channel] * Medium.fade[0] * 2 + Medium.cfade[channel] * 3000) / (Medium.fade[0] * 2 + 3000);
            } while (++channel < 3);
        }
        disline = outline;
        bfase = burn;
        glass = isGlass;
        Color.RGBtoHSB(c[0], c[1], c[2], hsb);
        if (!nocol && !glass) {
            if (bfase > 20 && hsb[1] > 0.25D) {
                hsb[1] = 0.25F;
            }
            if (bfase > 25 && hsb[2] > 0.69999999999999996D) {
                hsb[2] = 0.7F;
            }
            if (bfase > 30 && hsb[1] > 0.14999999999999999D) {
                hsb[1] = 0.15F;
            }
            if (bfase > 35 && hsb[2] > 0.59999999999999998D) {
                hsb[2] = 0.6F;
            }
            if (bfase > 40) {
                hsb[0] = 0.075F;
            }
            if (bfase > 50 && hsb[2] > 0.5D) {
                hsb[2] = 0.5F;
            }
            if (bfase > 60) {
                hsb[0] = 0.05F;
            }
        }
        road = isRoad;
        light = lamp;
        solo = isSolo;
        gr = group;
        fs = faceSign;
        wx = wheelX;
        wy = wheelY;
        wz = wheelZ;
        channel = 0;
        do {
            int other = 0;
            do {
                if (other != channel) {
                    deltaf *= (float) (Math.sqrt((ox[other] - ox[channel]) * (ox[other] - ox[channel])
                            + (oy[other] - oy[channel]) * (oy[other] - oy[channel]) + (oz[other] - oz[channel]) * (oz[other] - oz[channel])) / 100D);
                }
            } while (++other < 3);
        } while (++channel < 3);
        deltaf = deltaf / 3F;
    }

    /**
     * Vertex arrays reused by {@link #d} and {@link #s} instead of new ones for
     * every polygon drawn, one set per drawing thread. {@link #scratch} hands
     * each out zeroed, exactly like a new array.
     */
    private static final ThreadLocal<int[][]> SCRATCH = ThreadLocal.withInitial(() -> new int[16][16]);

    private static int[] scratch(int[][] slots, int slot, int length) {
        int[] array = slots[slot];
        if (array.length < length) {
            array = new int[Math.max(length, array.length * 2)];
            slots[slot] = array;
        }
        Arrays.fill(array, 0, length, 0);
        return array;
    }

    public void d(Graphics2D rd, int x, int y, int z, int yaw, int roll, int pitch, int wheelTurn, int wheelSpin, boolean noOutline, int objectSize, boolean blackout) {
        int[][] slots = SCRATCH.get();
        // for the graphics card, the corners again without rounding (not while the plane is breaking up)
        boolean exact = Precise.on && embos == 0;
        boolean mirrored = (gr == -11 || gr == -13) && Medium.lastmaf == 1;
        if (Precise.on) {
            Precise.recorder.unbind();
            Precise.recorder.tag(this, 0);
        }
        if (master != 0) {
            if (av > 1500) {
                n = 8;
            } else {
                n = 16;
            }
        }
        int px[] = scratch(slots, 0, n);
        int pz[] = scratch(slots, 1, n);
        int py[] = scratch(slots, 2, n);
        if (embos == 0) {
            for (int corner = 0; corner < n; corner++) {
                px[corner] = ox[corner] + x;
                py[corner] = oy[corner] + y;
                pz[corner] = oz[corner] + z;
            }

            if ((gr == -11 || gr == -13) && Medium.lastmaf == 1) {
                for (int corner = 0; corner < n; corner++) {
                    px[corner] = -ox[corner] + x;
                    py[corner] = oy[corner] + y;
                    pz[corner] = -oz[corner] + z;
                }

            }
        } else {
            if (embos <= 11 && Medium.fxRandom() > 0.5D && !glass) {
                for (int corner = 0; corner < n; corner++) {
                    px[corner] = (int) (ox[corner] + x + (15F - Medium.fxRandom() * 30F));
                    py[corner] = (int) (oy[corner] + y + (15F - Medium.fxRandom() * 30F));
                    pz[corner] = (int) (oz[corner] + z + (15F - Medium.fxRandom() * 30F));
                }

                Utility.rot(px, py, x, y, roll, n);
                Utility.rot(py, pz, y, z, pitch, n);
                Utility.rot(px, pz, x, z, yaw, n);
                Utility.rot(px, pz, Medium.cx, Medium.cz, Medium.xz, n);
                Utility.rot(py, pz, Medium.cy, Medium.cz, Medium.zy, n);
                int flashX[] = scratch(slots, 3, n);
                int flashY[] = scratch(slots, 5, n);
                for (int corner = 0; corner < n; corner++) {
                    flashX[corner] = Utility.xs(px[corner], pz[corner]);
                    flashY[corner] = Utility.ys(py[corner], pz[corner], 0);
                }

                rd.setColor(Colors.of(230, 230, 230));
                Glow.set(Glow.FLAME);
                rd.fillPolygon(flashX, flashY, n);
                Glow.off();
            }
            float burst = 1.0F;
            if (embos <= 4)/// explode outward a little + white flame
            {
                burst = 1.0F + Medium.fxRandom() / 5F;
            }
            if (embos > 4 && embos <= 7)/// explode outward a little + white flame a bit bigger than previous
            {
                burst = 1.0F + Medium.fxRandom() / 4F;
            }
            if (embos > 7 && embos <= 9)/////// /// explode outward a little + white flame a bit bigger than previous + polUtility.ys start to darken
            {
                burst = 1.0F + Medium.fxRandom() / 3F;
                if (hsb[2] > 0.69999999999999996D) {
                    hsb[2] = 0.7F;
                }
            }
            if (embos > 9 && embos <= 10)/////// /// explode outward a little + white flame a bit bigger than previous + maor darkening
            {
                burst = 1.0F + Medium.fxRandom() / 2.0F;
                if (hsb[2] > 0.59999999999999998D) {
                    hsb[2] = 0.6F;
                }
            }
            if (embos > 10 && embos <= 12)/////// /// explode outward a little + white flame a bit bigger than previous + more darkkkk liek coffee beans
            {
                burst = 1.0F + Medium.fxRandom() / 1.0F;
                if (hsb[2] > 0.5D) {
                    hsb[2] = 0.5F;
                }
            }
            if (embos == 12)/////// polUtility.ys explode outward
            {
                chip = 1;
                ctmag = 2.0F;
                bfase = -7;
            }
            if (embos == 13)/////// dark polUtility.ys
            {
                hsb[1] = 0.2F;
                hsb[2] = 0.4F;
            }
            if (embos == 16)//// flames
            {
                pa = (int) (Medium.fxRandom() * n);
                for (pb = (int) (Medium.fxRandom() * n); pa == pb; pb = (int) (Medium.fxRandom() * n)) {
                }
            }
            if (embos >= 16) ///// flames as well
            {
                byte pitchSign = 1;
                byte rollSign = 1;
                int pitchAngle;
                for (pitchAngle = Math.abs(pitch); pitchAngle > 270; pitchAngle -= 360) {
                }
                pitchAngle = Math.abs(pitchAngle);
                if (pitchAngle > 90) {
                    pitchSign = -1;
                }
                int rollAngle;
                for (rollAngle = Math.abs(roll); rollAngle > 270; rollAngle -= 360) {
                }
                rollAngle = Math.abs(rollAngle);
                if (rollAngle > 90) {
                    rollSign = -1;
                }
                int flameX[] = scratch(slots, 12, 3);
                int flameY[] = scratch(slots, 13, 3);
                px[0] = ox[pa] + x;
                py[0] = oy[pa] + y;
                pz[0] = oz[pa] + z;
                px[1] = ox[pb] + x;
                py[1] = oy[pb] + y;
                for (pz[1] = oz[pb] + z; Math.abs(px[0] - px[1]) > 100; ) {
                    if (px[1] > px[0]) {
                        px[1] -= 30;
                    } else {
                        px[1] += 30;
                    }
                }

                while (Math.abs(pz[0] - pz[1]) > 100) {
                    if (pz[1] > pz[0]) {
                        pz[1] -= 30;
                    } else {
                        pz[1] += 30;
                    }
                }
                int wobbleX = (int) (Math.abs(px[0] - px[1]) / 3 * (0.5D - Medium.fxRandom()));
                int wobbleZ = (int) (Math.abs(pz[0] - pz[1]) / 3 * (0.5D - Medium.fxRandom()));
                px[2] = (px[0] + px[1]) / 2 + wobbleX;
                pz[2] = (pz[0] + pz[1]) / 2 + wobbleZ;
                int flameHeight = (int) (((Math.abs(px[0] - px[1]) + Math.abs(pz[0] - pz[1])) / 1.5D)
                        * (Medium.fxRandom() / 2.0F + 0.5D));
                py[2] = (py[0] + py[1]) / 2 - pitchSign * rollSign * flameHeight;
                Utility.rot(px, py, x, y, roll, 3);
                Utility.rot(py, pz, y, z, pitch, 3);
                Utility.rot(px, pz, x, z, yaw, 3);
                Utility.rot(px, pz, Medium.cx, Medium.cz, Medium.xz, 3);
                Utility.rot(py, pz, Medium.cy, Medium.cz, Medium.zy, 3);
                int red = 0;
                do {
                    flameX[red] = Utility.xs(px[red], pz[red]);
                    flameY[red] = Utility.ys(py[red], pz[red], 0);
                } while (++red < 3);
                // Flame color. Outer Flame Red Value
                red = (int) (255F + 255F * (Medium.snap[0] / 400F));
                if (red > 255) {
                    red = 255;
                }
                if (red < 0) {
                    red = 0;
                }
                // Flame color. Outer Flame Green Value
                int green = (int) (169F + 169F * (Medium.snap[1] / 300F));
                if (green > 255) {
                    green = 255;
                }
                if (green < 0) {
                    green = 0;
                }
                // Flame color. Outer Flame Blue Value
                int blue = (int) (89F + 89F * (Medium.snap[2] / 200F));
                if (blue > 255) {
                    blue = 255;
                }
                if (blue < 0) {
                    blue = 0;
                }
                rd.setColor(Colors.of(red, green, blue));
                Glow.set(Glow.FLAME);
                rd.fillPolygon(flameX, flameY, 3);
                Glow.off();
                px[0] = ox[pa] + x;
                py[0] = oy[pa] + y;
                pz[0] = oz[pa] + z;
                px[1] = ox[pb] + x;
                py[1] = oy[pb] + y;
                for (pz[1] = oz[pb] + z; Math.abs(px[0] - px[1]) > 100; ) {
                    if (px[1] > px[0]) {
                        px[1] -= 30;
                    } else {
                        px[1] += 30;
                    }
                }

                while (Math.abs(pz[0] - pz[1]) > 100) {
                    if (pz[1] > pz[0]) {
                        pz[1] -= 30;
                    } else {
                        pz[1] += 30;
                    }
                }
                px[2] = (px[0] + px[1]) / 2 + wobbleX;
                pz[2] = (pz[0] + pz[1]) / 2 + wobbleZ;
                flameHeight = (int) (flameHeight * 0.80000000000000004D);
                py[2] = (py[0] + py[1]) / 2 - pitchSign * rollSign * flameHeight;
                Utility.rot(px, py, x, y, roll, 3);
                Utility.rot(py, pz, y, z, pitch, 3);
                Utility.rot(px, pz, x, z, yaw, 3);
                Utility.rot(px, pz, Medium.cx, Medium.cz, Medium.xz, 3);
                Utility.rot(py, pz, Medium.cy, Medium.cz, Medium.zy, 3);
                int point = 0;
                do {
                    flameX[point] = Utility.xs(px[point], pz[point]);
                    flameY[point] = Utility.ys(py[point], pz[point], 0);
                } while (++point < 3);
                // Flame color. Inner Flame Red Value
                red = (int) (255F + 255F * (Medium.snap[0] / 400F));
                if (red > 255) {
                    red = 255;
                }
                if (red < 0) {
                    red = 0;
                }
                // Flame color. Inner Flame Green Value
                green = (int) (207F + 207F * (Medium.snap[1] / 300F));
                if (green > 255) {
                    green = 255;
                }
                if (green < 0) {
                    green = 0;
                }
                // Flame color. Inner Flame Blue Value
                blue = (int) (136F + 136F * (Medium.snap[2] / 200F));
                if (blue > 255) {
                    blue = 255;
                }
                if (blue < 0) {
                    blue = 0;
                }
                rd.setColor(Colors.of(red, green, blue));
                Glow.set(Glow.FLAME);
                rd.fillPolygon(flameX, flameY, 3);
                Glow.off();
            }
            for (int corner = 0; corner < n; corner++) {
                if (typ == 1) {
                    px[corner] = (int) (ox[corner] * burst + x);
                } else {
                    px[corner] = ox[corner] + x;
                }
                if (typ == 2) {
                    py[corner] = (int) (oy[corner] * burst + y);
                } else {
                    py[corner] = oy[corner] + y;
                }
                if (typ == 3) {
                    pz[corner] = (int) (oz[corner] * burst + z);
                } else {
                    pz[corner] = oz[corner] + z;
                }
            }

            if (embos != 70) {
                embos++;
            } else {
                embos = 16;
            }
        }
        if (wz != 0) {
            Utility.rot(py, pz, wy + y, wz + z, wheelSpin, n);
        }
        if (wx != 0) {
            Utility.rot(px, pz, wx + x, wz + z, wheelTurn, n);
        }
        if (chip == 1 && (Medium.fxRandom() > 0.59999999999999998D || bfase == 0)) {
            chip = 0;
            if (bfase == 0 && nocol) {
                bfase = 1;
            }
        }
        if (chip != 0) {
            boolean chipStarts = chip == 1; // just broken off: nowhere to glide from
            if (chip == 1) {
                cxz = yaw;
                cxy = roll;
                czy = pitch;
                int corner = (int) (Medium.fxRandom() * n);
                cox[0] = ox[corner];
                coz[0] = oz[corner];
                coy[0] = oy[corner];
                if (ctmag > 3F) {
                    ctmag = 3F;
                }
                if (ctmag < -3F) {
                    ctmag = -3F;
                }
                cox[1] = (int) (cox[0] + ctmag * (10F - Medium.fxRandom() * 20F));
                cox[2] = (int) (cox[0] + ctmag * (10F - Medium.fxRandom() * 20F));
                coy[1] = (int) (coy[0] + ctmag * (10F - Medium.fxRandom() * 20F));
                coy[2] = (int) (coy[0] + ctmag * (10F - Medium.fxRandom() * 20F));
                coz[1] = (int) (coz[0] + ctmag * (10F - Medium.fxRandom() * 20F));
                coz[2] = (int) (coz[0] + ctmag * (10F - Medium.fxRandom() * 20F));
                dx = 0;
                dy = 0;
                dz = 0;
                if (bfase != -7) {
                    vx = (int) (ctmag * (30F - Medium.fxRandom() * 60F));
                    vz = (int) (ctmag * (30F - Medium.fxRandom() * 60F));
                    vy = (int) (ctmag * (30F - Medium.fxRandom() * 60F));
                } else {
                    vx = (int) (ctmag * (10F - Medium.fxRandom() * 20F));
                    vz = (int) (ctmag * (10F - Medium.fxRandom() * 20F));
                    vy = (int) (ctmag * (10F - Medium.fxRandom() * 20F));
                }
                chip = 2;
            }
            int chipX[] = scratch(slots, 4, 3);
            int chipZ[] = scratch(slots, 6, 3);
            int chipY[] = scratch(slots, 8, 3);
            int point = 0;
            do {
                chipX[point] = cox[point] + x;
                chipY[point] = coy[point] + y;
                chipZ[point] = coz[point] + z;
            } while (++point < 3);
            Utility.rot(chipX, chipY, x, y, cxy, 3);
            Utility.rot(chipY, chipZ, y, z, czy, 3);
            Utility.rot(chipX, chipZ, x, z, cxz, 3);
            point = 0;
            do {
                chipX[point] += dx;
                chipY[point] += dy;
                chipZ[point] += dz;
            } while (++point < 3);
            boolean trail = EffectTrails.on && !chipStarts;
            if (trail) {
                // where it was shown a tick ago: one step back, the step before gravity added 7
                int[] tx = EffectTrails.worldX;
                int[] ty = EffectTrails.worldY;
                int[] tz = EffectTrails.worldZ;
                for (int k = 0; k < 3; k++) {
                    tx[k] = chipX[k] - vx;
                    ty[k] = chipY[k] - (vy - 7);
                    tz[k] = chipZ[k] - vz;
                }
                Utility.rot(tx, tz, Medium.cx, Medium.cz, Medium.xz, 3);
                Utility.rot(ty, tz, Medium.cy, Medium.cz, Medium.zy, 3);
                for (int k = 0; k < 3; k++) {
                    EffectTrails.trailX[k] = Utility.xs(tx[k], tz[k]);
                    EffectTrails.trailY[k] = Utility.ys(ty[k], tz[k], 0);
                }
            }
            dx += vx;
            dz += vz;
            dy += vy;
            vy += 7;
            if (chipY[0] > Medium.ground) {
                chip = 19;
            }
            Utility.rot(chipX, chipZ, Medium.cx, Medium.cz, Medium.xz, 3);
            Utility.rot(chipY, chipZ, Medium.cy, Medium.cz, Medium.zy, 3);
            int chipScreenX[] = scratch(slots, 10, 3);
            int chipScreenY[] = scratch(slots, 11, 3);
            int shade = 0;
            do {
                chipScreenX[shade] = Utility.xs(chipX[shade], chipZ[shade]);
                chipScreenY[shade] = Utility.ys(chipY[shade], chipZ[shade], 0);
                if (chipScreenY[shade] < 45 && Medium.flex != 0) {
                    Medium.flex = 0;
                }
            } while (++shade < 3);
            shade = (int) (Medium.fxRandom() * 3F);
            if (bfase != -7) {
                if (shade == 0) {
                    rd.setColor((Colors.of(c[0], c[1], c[2])).darker());
                }
                if (shade == 1) {
                    rd.setColor(Colors.of(c[0], c[1], c[2]));
                }
                if (shade == 2) {
                    rd.setColor((Colors.of(c[0], c[1], c[2])).brighter());
                }
            } else {
                rd.setColor(Colors.ofHsb(hsb[0], hsb[1], hsb[2]));
            }
            if (!trail) {
                rd.fillPolygon(chipScreenX, chipScreenY, 3);
            } else if (EffectTrails.direct()) {
                EffectTrails.between(chipScreenX, chipScreenY, 3);
                rd.fillPolygon(EffectTrails.drawX, EffectTrails.drawY, 3);
            } else {
                Precise.trail(EffectTrails.trailX, EffectTrails.trailY, 3);
                rd.fillPolygon(chipScreenX, chipScreenY, 3);
            }
            chip++;
            if (chip == 20) {
                chip = 0;
            }
        }
        Utility.rot(px, py, x, y, roll, n);
        Utility.rot(py, pz, y, z, pitch, n);
        Utility.rot(px, pz, x, z, yaw, n);
        if (roll != 0 || pitch != 0 || yaw != 0) {
            projf = 1.0F;
            int corner = 0;
            do {
                int other = 0;
                do {
                    if (other != corner) {
                        projf *= (float) (Math
                                .sqrt((px[corner] - px[other]) * (px[corner] - px[other]) + (pz[corner] - pz[other]) * (pz[corner] - pz[other]))
                                / 100D);
                    }
                } while (++other < 3);
            } while (++corner < 3);
            projf = projf / 3F;
        }
        Utility.rot(px, pz, Medium.cx, Medium.cz, Medium.xz, n);
        boolean backFacing = false;
        int flatX[] = scratch(slots, 7, n);
        int flatY[] = scratch(slots, 9, n);
        int best = 500;
        for (int corner = 0; corner < n; corner++) {
            flatX[corner] = Utility.xs(px[corner], pz[corner]);
            flatY[corner] = Utility.ys(py[corner], pz[corner], 0);
        }

        int top = 0;
        int bottom = 1;
        for (int corner = 0; corner < n; corner++) {
            for (int other = 0; other < n; other++) {
                if (corner != other && Math.abs(flatX[corner] - flatX[other]) - Math.abs(flatY[corner] - flatY[other]) < best) {
                    bottom = corner;
                    top = other;
                    best = Math.abs(flatX[corner] - flatX[other]) - Math.abs(flatY[corner] - flatY[other]);
                }
            }

        }

        if (flatY[top] < flatY[bottom]) {
            int swap = top;
            top = bottom;
            bottom = swap;
        }
        if (Utility.spy(px[top], pz[top]) > Utility.spy(px[bottom], pz[bottom])) {
            backFacing = true;
            int level = 0;
            for (int corner = 0; corner < n; corner++) {
                if (pz[corner] < 50 && py[corner] > Medium.cy) {
                    backFacing = false;
                } else if (py[corner] == py[0]) {
                    level++;
                }
            }

            if (level == n && py[0] > Medium.cy) {
                backFacing = false;
            }
        }
        Utility.rot(py, pz, Medium.cy, Medium.cz, Medium.zy, n);
        boolean visible = true;
        int screenX[] = scratch(slots, 14, n);
        int screenY[] = scratch(slots, 15, n);
        int above = 0;
        int below = 0;
        int leftOf = 0;
        int rightOf = 0;
        int behind = 0;
        for (int corner = 0; corner < n; corner++) {
            screenX[corner] = Utility.xs(px[corner], pz[corner]);
            screenY[corner] = Utility.ys(py[corner], pz[corner], 0);
            if (screenY[corner] < -Medium.cullY || pz[corner] < 10) {
                above++;
            }
            if (screenY[corner] > Medium.h + Medium.cullY || pz[corner] < 10) {
                below++;
            }
            if (screenX[corner] < -Medium.cullX || pz[corner] < 10) {
                leftOf++;
            }
            if (screenX[corner] > Medium.w + Medium.cullX || pz[corner] < 10) {
                rightOf++;
            }
            if (screenY[corner] < 45 && Medium.flex != 0) {
                Medium.flex = 0;
            }
            if (pz[corner] < 10) {
                behind++;
            }
            if(screenY[corner] <= -30000 || screenX[corner] <= -30000 || screenX[corner] >= 30000) {
                visible = false;
            }
        }

        if (objectSize != -1) {
            int width = 0;
            int height = 0;
            for (int corner = 0; corner < n; corner++) {
                for (int other = corner; other < n; other++) {
                    if (corner != other) {
                        if (Math.abs(screenX[corner] - screenX[other]) > width) {
                            width = Math.abs(screenX[corner] - screenX[other]);
                        }
                        if (Math.abs(screenY[corner] - screenY[other]) > height) {
                            height = Math.abs(screenY[corner] - screenY[other]);
                        }
                    }
                }

            }

            if (width == 0 || height == 0) {
                visible = false;
            } else if (width < 3 && height < 3 && objectSize / width > 15 && objectSize / height > 15) {
                visible = false;
            }
        }
        if (leftOf == n || above == n || below == n || rightOf == n) {
            visible = false;
        }
        /*if (Medium.trk && (leftOf != 0 || above != 0 || below != 0 || rightOf != 0)) {
            flag2 = false;
        }*/
        if (behind != 0) {
            noOutline = true;
        }
        if (visible) {
            int facing = 1;
            byte sideA;
            byte sideB;
            if (Math.abs(screenY[0] - screenY[1]) > Math.abs(screenY[2] - screenY[1])) {
                sideA = 0;
                sideB = 2;
            } else {
                sideA = 2;
                sideB = 0;
                facing *= -1;
            }
            if (screenY[1] > screenY[sideA]) {
                facing *= -1;
            }
            if (screenX[1] > screenX[sideB]) {
                facing *= -1;
            }
            int bias = gr;
            if (bias < 0 && bias >= -17) {
                bias = 0;
            }
            if (gr == -11) {
                bias = -90;
            }
            if (gr == -14 || gr == -15) {
                bias = -50;
            }
            if (gr == -16) {
                bias = 35;
            }
            if (fs != 0) {
                facing *= fs;
                if (facing == -1) {
                    bias += 40;
                    if (!road) {
                        facing = -111;
                    }
                }
            }
            if (Medium.lightson && light == 2) {
                bias -= 40;
            }
            int maxY = 0;
            int minY = 0;
            int maxX = 0;
            int minX = 0;
            int maxZ = 0;
            int minZ = 0;
            for (int corner = 0; corner < n; corner++) {
                int geY = 0;
                int leY = 0;
                int geX = 0;
                int leX = 0;
                int geZ = 0;
                int leZ = 0;
                for (int other = 0; other < n; other++) {
                    if (py[corner] >= py[other]) {
                        geY++;
                    }
                    if (py[corner] <= py[other]) {
                        leY++;
                    }
                    if (px[corner] >= px[other]) {
                        geX++;
                    }
                    if (px[corner] <= px[other]) {
                        leX++;
                    }
                    if (pz[corner] >= pz[other]) {
                        geZ++;
                    }
                    if (pz[corner] <= pz[other]) {
                        leZ++;
                    }
                }

                if (geY == n) {
                    maxY = py[corner];
                }
                if (leY == n) {
                    minY = py[corner];
                }
                if (geX == n) {
                    maxX = px[corner];
                }
                if (leX == n) {
                    minX = px[corner];
                }
                if (geZ == n) {
                    maxZ = pz[corner];
                }
                if (leZ == n) {
                    minZ = pz[corner];
                }
            }

            int midY = (maxY + minY) / 2;
            int midX = (maxX + minX) / 2;
            int midZ = (maxZ + minZ) / 2;
            av = (int) Math
                    .sqrt((Medium.cy - midY) * (Medium.cy - midY) + (Medium.cx - midX) * (Medium.cx - midX) + midZ * midZ + bias * bias * bias);
            if (!Medium.trk && (av > Medium.fade[disline] || av == 0)) {
                visible = false;
            }
            if (facing == -111 && av > 4500) {
                visible = false;
            }
            if (facing == -111 && av > 1500) {
                noOutline = true;
            }
            if (av > 3000 && Medium.adv <= 900) {
                noOutline = true;
            }
            if (gr == -12 && av < 11200) {
                Medium.lastmaf = facing;
            }
            if (gr == -13 && (!Medium.lastcheck || objectSize != -1)) {
                visible = false;
            }
            if (gr == -16 && av > 1500) {
                visible = false;
            }
            if (flx != 0 && Medium.fxRandom() > 0.29999999999999999D) {
                visible = false;
            }
        }
        if (visible) {
            float shade = (float) (projf / deltaf + 0.29999999999999999D);
            if (noOutline && !solo) {
                boolean steep = false;
                if (shade > 1.0F) {
                    if (shade >= 1.27D) {
                        steep = true;
                    }
                    shade = 1.0F;
                }
                if (steep) {
                    shade = (float) (shade * 0.89000000000000001D);
                } else {
                    shade = (float) (shade * 0.85999999999999999D);
                }
                if (shade < 0.37D) {
                    shade = 0.37F;
                }
                if (gr == -9) {
                    shade = 0.7F;
                }
                if (gr == -4) {
                    shade = 0.74F;
                }
                if (gr != -7 && backFacing) {
                    shade = 0.32F;
                }
                if (gr == -8 || gr == -14 || gr == -15) {
                    shade = 1.0F;
                }
                if (gr == -11) {
                    shade = 0.67F;
                    if (objectSize == -1) {
                        if (Medium.cpflik || Medium.nochekflk && !Medium.lastcheck) {
                            shade = 1.0F;
                        } else {
                            shade = 0.76F;
                        }
                    }
                }
                if (gr == -13 && objectSize == -1) {
                    if (Medium.cpflik) {
                        shade = 0.0F;
                    } else {
                        shade = 0.76F;
                    }
                }
                if (gr == -6) {
                    shade = 0.62F;
                }
                if (gr == -5) {
                    shade = 0.55F;
                }
            } else {
                if (shade > 1.0F) {
                    shade = 1.0F;
                }
                if (shade < 0.59999999999999998D || backFacing) {
                    shade = 0.6F;
                }
            }
            Color color;
            if (!Medium.trk) {
                color = Colors.ofHsb(hsb[0], hsb[1], hsb[2] * shade);
            } else {
                float trackHsb[] = new float[3];
                Color.RGBtoHSB(oc[0], oc[1], oc[2], trackHsb);
                color = Colors.ofHsb(hsb[0], hsb[1], hsb[2] * shade);
            }
            int red = color.getRed();
            int green = color.getGreen();
            int blue = color.getBlue();
            if (Medium.lightson && light != 0) {
                red = oc[0];
                if (red > 255) {
                    red = 255;
                }
                if (red < 0) {
                    red = 0;
                }
                green = oc[1];
                if (green > 255) {
                    green = 255;
                }
                if (green < 0) {
                    green = 0;
                }
                blue = oc[2];
                if (blue > 255) {
                    blue = 255;
                }
                if (blue < 0) {
                    blue = 0;
                }
            }
            if (!Medium.trk) {
                int band = 0;
                do {
                    if (av > Medium.fade[band]) {
                        red = (red * Medium.fogd + Medium.cfade[0]) / (Medium.fogd + 1);
                        green = (green * Medium.fogd + Medium.cfade[1]) / (Medium.fogd + 1);
                        blue = (blue * Medium.fogd + Medium.cfade[2]) / (Medium.fogd + 1);
                    }
                } while (++band < 8);
            }
            if (blackout){
                rd.setColor(Colors.of(0, 0, 0));
            } else {
                rd.setColor(Colors.of(red, green, blue));
            }
            if (exact) {
                Precise.plane(ox, oy, oz, mirrored, n, x, y, z, yaw, roll, pitch, wheelTurn, wheelSpin, wx, wy, wz, screenX, screenY);
            }
            boolean lamp = Medium.lightson && light != 0 && !blackout;
            if (lamp) {
                Glow.set(Glow.LAMP);
            }
            rd.fillPolygon(screenX, screenY, n);
            if (Medium.trk && gr == -10) {
                noOutline = false;
            }
            if (!noOutline) {
                if (flx == 0) {
                    if (!solo) {
                        red = 0;
                        green = 0;
                        blue = 0;
                        if (Medium.lightson && light != 0) {
                            red = oc[0] / 2;
                            if (red > 255) {
                                red = 255;
                            }
                            if (red < 0) {
                                red = 0;
                            }
                            green = oc[1] / 2;
                            if (green > 255) {
                                green = 255;
                            }
                            if (green < 0) {
                                green = 0;
                            }
                            blue = oc[2] / 2;
                            if (blue > 255) {
                                blue = 255;
                            }
                            if (blue < 0) {
                                blue = 0;
                            }
                        }
                        rd.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                        rd.setColor(Colors.of(red, green, blue));
                        rd.drawPolygon(screenX, screenY, n);
                        rd.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_OFF);
                    }
                } else {
                    if (flx == 2) {
                        rd.setColor(Colors.of(0, 0, 0));
                        rd.drawPolygon(screenX, screenY, n);
                    }
                    if (flx == 1) {
                        red = 0;
                        green = (int) (223F + 223F * (Medium.snap[1] / 100F));
                        if (green > 255) {
                            green = 255;
                        }
                        if (green < 0) {
                            green = 0;
                        }
                        blue = (int) (255F + 255F * (Medium.snap[2] / 100F));
                        if (blue > 255) {
                            blue = 255;
                        }
                        if (blue < 0) {
                            blue = 0;
                        }
                        rd.setColor(Colors.of(red, green, blue));
                        Glow.set(Glow.REPAIR);
                        rd.drawPolygon(screenX, screenY, n);
                        flx = 2;
                    }
                    if (flx == 3) {
                        red = 0;
                        green = (int) (255F + 255F * (Medium.snap[1] / 100F));
                        if (green > 255) {
                            green = 255;
                        }
                        if (green < 0) {
                            green = 0;
                        }
                        blue = (int) (223F + 223F * (Medium.snap[2] / 100F));
                        if (blue > 255) {
                            blue = 255;
                        }
                        if (blue < 0) {
                            blue = 0;
                        }
                        rd.setColor(Colors.of(red, green, blue));
                        Glow.set(Glow.REPAIR);
                        rd.drawPolygon(screenX, screenY, n);
                        flx = 2;
                    }
                }
            } else if (road && av <= 3000 && !Medium.trk && Medium.fade[0] > 4000) {
                if ((red -= 10) < 0) {
                    red = 0;
                }
                if ((green -= 10) < 0) {
                    green = 0;
                }
                if ((blue -= 10) < 0) {
                    blue = 0;
                }
                rd.setColor(Colors.of(red, green, blue));
                rd.drawPolygon(screenX, screenY, n);
            }
            Glow.off(); // a lamp's glow, or the repair flash's, ends with its polygon
            if (gr == -10) {
                if (!Medium.trk) {
                    int lineRed = c[0];
                    int lineGreen = c[1];
                    int lineBlue = c[2];
                    if (objectSize == -1) {
                        if (Medium.nochekflk && !Medium.lastcheck) {
                            lineRed = (int) (lineRed * 1.25D);
                            if (lineRed > 255) {
                                lineRed = 255;
                            }
                            lineGreen = (int) (lineGreen * 1.25D);
                            if (lineGreen > 255) {
                                lineGreen = 255;
                            }
                            lineBlue = (int) (lineBlue * 1.25D);
                            if (lineBlue > 255) {
                                lineBlue = 255;
                            }
                        } else if (Medium.cpflik) {
                            lineRed = (int) (lineRed * 1.5D);
                            if (lineRed > 255) {
                                lineRed = 255;
                            }
                            lineGreen = (int) (lineGreen * 1.5D);
                            if (lineGreen > 255) {
                                lineGreen = 255;
                            }
                            lineBlue = (int) (lineBlue * 1.5D);
                            if (lineBlue > 255) {
                                lineBlue = 255;
                            }
                        }
                    }
                    int band = 0;
                    do {
                        if (av > Medium.fade[band]) {
                            lineRed = (lineRed * Medium.fogd + Medium.cfade[0]) / (Medium.fogd + 1);
                            lineGreen = (lineGreen * Medium.fogd + Medium.cfade[1]) / (Medium.fogd + 1);
                            lineBlue = (lineBlue * Medium.fogd + Medium.cfade[2]) / (Medium.fogd + 1);
                        }
                    } while (++band < 8);
                    rd.setColor(Colors.of(lineRed, lineGreen, lineBlue));
                    rd.drawPolygon(screenX, screenY, n);
                } else if (Medium.cpflik && Medium.hit == 5000) {
                    int flicker = (int) (Medium.fxRandom.nextDouble() * 115D);
                    int sparkRed = flicker * 2 - 54;
                    if (sparkRed < 0) {
                        sparkRed = 0;
                    }
                    if (sparkRed > 255) {
                        sparkRed = 255;
                    }
                    int sparkBlue = 202 + flicker * 2;
                    if (sparkBlue < 0) {
                        sparkBlue = 0;
                    }
                    if (sparkBlue > 255) {
                        sparkBlue = 255;
                    }
                    if ((flicker += 101) < 0) {
                        flicker = 0;
                    }
                    if (flicker > 255) {
                        flicker = 255;
                    }
                    rd.setColor(Colors.of(sparkRed, flicker, sparkBlue));
                    rd.drawPolygon(screenX, screenY, n);
                }
            }
            if (gr == -18 && !Medium.trk) {
                int arcRed = c[0];
                int arcGreen = c[1];
                int arcBlue = c[2];
                if (Medium.cpflik && Medium.elecr >= 0.0F) {
                    arcRed = (int) (25.5F * Medium.elecr);
                    if (arcRed > 255) {
                        arcRed = 255;
                    }
                    arcGreen = (int) (128F + 12.8F * Medium.elecr);
                    if (arcGreen > 255) {
                        arcGreen = 255;
                    }
                    arcBlue = 255;
                }
                int band = 0;
                do {
                    if (av > Medium.fade[band]) {
                        arcRed = (arcRed * Medium.fogd + Medium.cfade[0]) / (Medium.fogd + 1);
                        arcGreen = (arcGreen * Medium.fogd + Medium.cfade[1]) / (Medium.fogd + 1);
                        arcBlue = (arcBlue * Medium.fogd + Medium.cfade[2]) / (Medium.fogd + 1);
                    }
                } while (++band < 8);
                rd.setColor(Colors.of(arcRed, arcGreen, arcBlue));
                rd.drawPolygon(screenX, screenY, n);
            }
        }
    }


    public void s(Graphics2D rd, int x, int y, int z, int yaw, int roll, int pitch, int kind) {
        int[][] slots = SCRATCH.get();
        if (Precise.on) {
            Precise.recorder.unbind();
            Precise.recorder.tag(this, 1);
        }
        int px[] = scratch(slots, 0, n);
        int pz[] = scratch(slots, 1, n);
        int py[] = scratch(slots, 2, n);
        for (int corner = 0; corner < n; corner++) {
            px[corner] = ox[corner] + x;
            py[corner] = oy[corner] + y;
            pz[corner] = oz[corner] + z;
        }

        Utility.rot(px, py, x, y, roll, n);
        Utility.rot(py, pz, y, z, pitch, n);
        Utility.rot(px, pz, x, z, yaw, n);
        int red = (int) (Medium.cgrnd[0] / 1.5D);
        int green = (int) (Medium.cgrnd[1] / 1.5D);
        int blue = (int) (Medium.cgrnd[2] / 1.5D);
        for (int corner = 0; corner < n; corner++) {
            py[corner] = Medium.ground;
        }

        if (kind == 0) {
            int maxX = 0;
            int minX = 0;
            int maxZ = 0;
            int minZ = 0;
            for (int corner = 0; corner < n; corner++) {
                int geX = 0;
                int leX = 0;
                int geZ = 0;
                int leZ = 0;
                for (int other = 0; other < n; other++) {
                    if (px[corner] >= px[other]) {
                        geX++;
                    }
                    if (px[corner] <= px[other]) {
                        leX++;
                    }
                    if (pz[corner] >= pz[other]) {
                        geZ++;
                    }
                    if (pz[corner] <= pz[other]) {
                        leZ++;
                    }
                }

                if (geX == n) {
                    maxX = px[corner];
                }
                if (leX == n) {
                    minX = px[corner];
                }
                if (geZ == n) {
                    maxZ = pz[corner];
                }
                if (leZ == n) {
                    minZ = pz[corner];
                }
            }

            int midX = (maxX + minX) / 2;
            int midZ = (maxZ + minZ) / 2;
            for (int piece = t.nt - 1; piece >= 0; piece--) {
                int under = 0;
                if (Math.abs(t.zy[piece]) != 90 && Math.abs(t.xy[piece]) != 90 && Math.abs(midX - (t.x[piece] - Medium.x)) < t.radx[piece]
                        && Math.abs(midZ - (t.z[piece] - Medium.z)) < t.radz[piece]) {
                    under++;
                }
                if (under == 0) {
                    continue;
                }
                for (int corner = 0; corner < n; corner++) {
                    py[corner] = t.y[piece] - Medium.y;
                    if (t.zy[piece] != 0) {
                        py[corner] += ((pz[corner] - (t.z[piece] - Medium.z - t.radz[piece])) * RadicalMath.sin(t.zy[piece]))
                                / RadicalMath.sin(90 - t.zy[piece]) - (t.radz[piece] * RadicalMath.sin(t.zy[piece])) / RadicalMath.sin(90 - t.zy[piece]);
                    }
                    if (t.xy[piece] != 0) {
                        py[corner] += ((px[corner] - (t.x[piece] - Medium.x - t.radx[piece])) * RadicalMath.sin(t.xy[piece]))
                                / RadicalMath.sin(90 - t.xy[piece]) - (t.radx[piece] * RadicalMath.sin(t.xy[piece])) / RadicalMath.sin(90 - t.xy[piece]);
                    }
                }

                red = (int) (t.c[piece][0] / 1.5D);
                green = (int) (t.c[piece][1] / 1.5D);
                blue = (int) (t.c[piece][2] / 1.5D);
                break;
            }

        }
        boolean visible = true;
        int screenX[] = scratch(slots, 3, n);
        int screenY[] = scratch(slots, 4, n);
        if (kind == 2) {
            red = 80;
            green = 80;
            blue = 80;
        } else {
            for (int spot = 0; spot < Medium.nsp; spot++) {
                for (int corner = 0; corner < n; corner++) {
                    if (Math.abs(px[corner] - Medium.spx[spot]) < Medium.sprad[spot] && Math.abs(pz[corner] - Medium.spz[spot]) < Medium.sprad[spot]) {
                        visible = false;
                    }
                }

            }

        }
        if (visible) {
            if (Precise.on) {
                Precise.keepGround(py, n);
            }
            Utility.rot(px, pz, Medium.cx, Medium.cz, Medium.xz, n);
            Utility.rot(py, pz, Medium.cy, Medium.cz, Medium.zy, n);
            int above = 0;
            int below = 0;
            int leftOf = 0;
            int rightOf = 0;
            for (int corner = 0; corner < n; corner++) {
                screenX[corner] = Utility.xs(px[corner], pz[corner]);
                screenY[corner] = Utility.ys(py[corner], pz[corner], 0);
                if (screenY[corner] < -Medium.cullY || pz[corner] < 10) {
                    above++;
                }
                if (screenY[corner] > Medium.h + Medium.cullY || pz[corner] < 10) {
                    below++;
                }
                if (screenX[corner] < -Medium.cullX || pz[corner] < 10) {
                    leftOf++;
                }
                if (screenX[corner] > Medium.w + Medium.cullX || pz[corner] < 10) {
                    rightOf++;
                }
            }

            if (leftOf == n || above == n || below == n || rightOf == n) {
                visible = false;
            }
        }
        if (visible) {
            int band = 0;
            do {
                if (av > Medium.fade[band]) {
                    red = (red * Medium.fogd + Medium.cfade[0]) / (Medium.fogd + 1);
                    green = (green * Medium.fogd + Medium.cfade[1]) / (Medium.fogd + 1);
                    blue = (blue * Medium.fogd + Medium.cfade[2]) / (Medium.fogd + 1);
                }
            } while (++band < 8);
            rd.setColor(Colors.of(red, green, blue));
            if (Precise.on) {
                Precise.shadow(ox, oy, oz, n, x, y, z, yaw, roll, pitch, screenX, screenY);
            }
            rd.fillPolygon(screenX, screenY, n);
        }
    }
}
