package nfm.lit;
import fallk.logmaster.HLogger;

import java.awt.*;
import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.Arrays;

/**
 * models
 *
 * @author Omar Waly, Kaffeinated
 */
public class ContO {
    private final Trackers t;
    public final Plane[] p;
    public int npl = 0;
    public int x = 0;
    public int y = 0;
    public int z = 0;
    public int xz = 0;
    public int xy = 0;
    public int zy = 0;
    public int wxz = 0;
    public int wzy = 0;
    public int dist = 0;
    public int maxR = 0;
    private DrawState drawState;
    private int disp = 0;
    private int disline = 7;
    private boolean shadow = false;
    private boolean noline = false;
    private float grounded = 1.0F;
    public int grat = 0;
    public final int[] keyx = new int[4];
    public final int[] keyz = new int[4];
    public final int[] sx = new int[4];
    public final int[] sy = new int[4];
    public final int[] sz = new int[4];
    public final int[] stg = new int[4];
    public final int[] dov = new int[4];
    public final float[] smag = new float[4];
    public final int[] scx = new int[4];
    public final int[] scz = new int[4];
    public final boolean[] fulls = new boolean[4];
    public boolean elec = false;
    public boolean roted = false;
    private final int[] edl = new int[4];
    private final int[] edr = new int[4];
    private final int[] elc = new int[4];
    public boolean fix = false;
    public int fcnt = 0;
    public int checkpoint = 0;
    private int tnt = 0;
    private int[] txy;
    private int[] tzy;
    private int[][] tc;
    private int[] tradx;
    private int[] tradz;
    private int[] trady;
    private int[] tx;
    private int[] ty;
    private int[] tz;
    private int[] skd;
    private int[] dam;
    private boolean[] notwall;

    public boolean blackout = false;

    private boolean bool16 = false;

    /** A puff's corners' distances from its centre, for smooth effects (game thread). */
    private static final double[] PUFF_CORNERS = new double[16];
    /** The puffs as last drawn, for smooth effects; made when first needed. */
    private EffectTrails.Puff[] puffTrails;

    private EffectTrails.Puff puffTrail(int puff) {
        if (puffTrails == null) {
            puffTrails = new EffectTrails.Puff[4];
        }
        EffectTrails.Puff t = puffTrails[puff];
        if (t == null) {
            t = new EffectTrails.Puff();
            puffTrails[puff] = t;
        }
        return t;
    }

    /**
     * The corners of a puff centred at {@code (x, y, z)} with corners
     * {@code corners} away, as this frame's camera puts them on screen, into
     * EffectTrails.trailX/Y: the same sums as {@link #pdust} makes.
     */
    private static void trailCorners(int x, int y, int z, double[] corners) {
        int viewX = Medium.cx + (int) ((x - Medium.x - Medium.cx) * RadicalMath.cos(Medium.xz)
                - (z - Medium.z - Medium.cz) * RadicalMath.sin(Medium.xz));
        int depth = Medium.cz + (int) ((x - Medium.x - Medium.cx) * RadicalMath.sin(Medium.xz)
                + (z - Medium.z - Medium.cz) * RadicalMath.cos(Medium.xz));
        int viewY = Medium.cy + (int) ((y - Medium.y - Medium.cy) * RadicalMath.cos(Medium.zy)
                - (depth - Medium.cz) * RadicalMath.sin(Medium.zy));
        depth = Medium.cz + (int) ((y - Medium.y - Medium.cy) * RadicalMath.sin(Medium.zy)
                + (depth - Medium.cz) * RadicalMath.cos(Medium.zy));
        for (int k = 0; k < 8; k++) {
            EffectTrails.trailX[k] = Utility.cXs((int) (viewX + corners[2 * k]), depth);
            EffectTrails.trailY[k] = Utility.cYs((int) (viewY + corners[2 * k + 1]), depth);
        }
    }

    private void pdust(int puff, Graphics2D rd, int side) {
        if (side * dov[puff] > 0) {
            int thickness;
            if (fulls[puff]) {
                thickness = stg[puff] * stg[puff];
            } else {
                thickness = stg[puff] * stg[puff] * stg[puff] + 1;
            }
            int red = (Medium.cgrnd[0] * thickness + Medium.cfade[0] * 2 + Medium.csky[0]) / (3 + thickness);
            int green = (Medium.cgrnd[1] * thickness + Medium.cfade[0] * 2 + Medium.csky[1]) / (3 + thickness);
            int blue = (Medium.cgrnd[2] * thickness + Medium.cfade[0] * 2 + Medium.csky[2]) / (3 + thickness);
            for (int piece = 0; piece < t.nt; piece++) {
                if (Math.abs(t.zy[piece]) != 90 && Math.abs(t.xy[piece]) != 90 && Math.abs(sx[puff] - t.x[piece]) < t.radx[piece]
                        && Math.abs(sz[puff] - t.z[piece]) < t.radz[piece]) {
                    if (t.skd[piece] == 0) {
                        thickness = stg[puff] * stg[puff] * stg[puff] + 2;
                    }
                    red = (int) ((t.c[piece][0] * 0.87D * thickness + Medium.cfade[0] * 2
                            + Medium.csky[0]) / (3 + thickness));
                    green = (int) ((t.c[piece][1] * 0.87D * thickness + Medium.cfade[0] * 2
                            + Medium.csky[1]) / (3 + thickness));
                    blue = (int) ((t.c[piece][2] * 0.87D * thickness + Medium.cfade[0] * 2
                            + Medium.csky[2]) / (3 + thickness));
                }
            }

            if (sy[puff] > 250) {
                sy[puff] = 250;
            }
            int drawnStage = stg[puff];
            int centreX = sx[puff];
            int centreY = sy[puff];
            int centreZ = sz[puff];
            if (Precise.on) {
                Precise.anchor(sx[puff] - Medium.x, sy[puff] - Medium.y, sz[puff] - Medium.z); // the puff stays where it is
            }
            int viewX = Medium.cx
                    + (int) ((sx[puff] - Medium.x - Medium.cx) * RadicalMath.cos(Medium.xz) - (sz[puff] - Medium.z - Medium.cz) * RadicalMath.sin(Medium.xz));
            int depth = Medium.cz
                    + (int) ((sx[puff] - Medium.x - Medium.cx) * RadicalMath.sin(Medium.xz) + (sz[puff] - Medium.z - Medium.cz) * RadicalMath.cos(Medium.xz));
            int viewY = Medium.cy + (int) ((sy[puff] - Medium.y - Medium.cy) * RadicalMath.cos(Medium.zy) - (depth - Medium.cz) * RadicalMath.sin(Medium.zy));
            depth = Medium.cz + (int) ((sy[puff] - Medium.y - Medium.cy) * RadicalMath.sin(Medium.zy) + (depth - Medium.cz) * RadicalMath.cos(Medium.zy));
            int distance = (int) Math.sqrt((Medium.cy - viewY) * (Medium.cy - viewY) + (Medium.cx - viewX) * (Medium.cx - viewX) + depth * depth);
            int band = 0;
            do {
                if (distance > Medium.fade[band]) {
                    red = (red * Medium.fogd + Medium.cfade[0]) / (Medium.fogd + 1);
                    green = (green * Medium.fogd + Medium.cfade[1]) / (Medium.fogd + 1);
                    blue = (blue * Medium.fogd + Medium.cfade[2]) / (Medium.fogd + 1);
                }
            } while (++band < 8);
            if (Math.abs(scx[puff]) + Math.abs(scz[puff]) > 150) {
                sy[puff] -= 3F + 27F * smag[puff];
            } else {
                sy[puff] -= 23F + 7F * smag[puff];
            }
            sx[puff] += scx[puff] / ((stg[puff] + 1) * smag[puff]);
            sz[puff] += scz[puff] / ((stg[puff] + 1) * smag[puff]);
            int puffX[] = new int[8];
            int puffY[] = new int[8];
            int growth = stg[puff] - 3;
            /*
             * Each corner is the centre plus a random distance, in the order the
             * game always drew them: across then up for each corner, 18 or 7.5
             * at the base. The distances are kept for smooth effects.
             */
            float a0 = (18F + Medium.fxRandom() * 18F + growth * 6) * smag[puff];
            puffX[0] = Utility.cXs((int) (viewX - a0), depth);
            double b0 = (7.5D + Medium.fxRandom() * 7.5D + growth * 2.5D) * smag[puff];
            puffY[0] = Utility.cYs((int) (viewY - b0), depth);
            if (puffY[0] < 45 && Medium.flex != 0) {
                Medium.flex = 0;
            }
            float a1 = (18F + Medium.fxRandom() * 18F + growth * 6) * smag[puff];
            puffX[1] = Utility.cXs((int) (viewX - a1), depth);
            double b1 = (7.5D + Medium.fxRandom() * 7.5D + growth * 2.5D) * smag[puff];
            puffY[1] = Utility.cYs((int) (viewY + b1), depth);
            double b2 = (7.5D + Medium.fxRandom() * 7.5D + growth * 2.5D) * smag[puff];
            puffX[2] = Utility.cXs((int) (viewX - b2), depth);
            float a2 = (18F + Medium.fxRandom() * 18F + growth * 6) * smag[puff];
            puffY[2] = Utility.cYs((int) (viewY + a2), depth);
            double b3 = (7.5D + Medium.fxRandom() * 7.5D + growth * 2.5D) * smag[puff];
            puffX[3] = Utility.cXs((int) (viewX + b3), depth);
            float a3 = (18F + Medium.fxRandom() * 18F + growth * 6) * smag[puff];
            puffY[3] = Utility.cYs((int) (viewY + a3), depth);
            float a4 = (18F + Medium.fxRandom() * 18F + growth * 6) * smag[puff];
            puffX[4] = Utility.cXs((int) (viewX + a4), depth);
            double b4 = (7.5D + Medium.fxRandom() * 7.5D + growth * 2.5D) * smag[puff];
            puffY[4] = Utility.cYs((int) (viewY + b4), depth);
            float a5 = (18F + Medium.fxRandom() * 18F + growth * 6) * smag[puff];
            puffX[5] = Utility.cXs((int) (viewX + a5), depth);
            double b5 = (7.5D + Medium.fxRandom() * 7.5D + growth * 2.5D) * smag[puff];
            puffY[5] = Utility.cYs((int) (viewY - b5), depth);
            double b6 = (7.5D + Medium.fxRandom() * 7.5D + growth * 2.5D) * smag[puff];
            puffX[6] = Utility.cXs((int) (viewX + b6), depth);
            float a6 = (18F + Medium.fxRandom() * 18F + growth * 6) * smag[puff];
            puffY[6] = Utility.cYs((int) (viewY - a6), depth);
            double b7 = (7.5D + Medium.fxRandom() * 7.5D + growth * 2.5D) * smag[puff];
            puffX[7] = Utility.cXs((int) (viewX - b7), depth);
            float a7 = (18F + Medium.fxRandom() * 18F + growth * 6) * smag[puff];
            puffY[7] = Utility.cYs((int) (viewY - a7), depth);
            boolean trail = false;
            if (EffectTrails.on) {
                double[] corners = PUFF_CORNERS;
                corners[0] = -a0;
                corners[1] = -b0;
                corners[2] = -a1;
                corners[3] = b1;
                corners[4] = -b2;
                corners[5] = a2;
                corners[6] = b3;
                corners[7] = a3;
                corners[8] = a4;
                corners[9] = b4;
                corners[10] = a5;
                corners[11] = -b5;
                corners[12] = b6;
                corners[13] = -a6;
                corners[14] = -b7;
                corners[15] = -a7;
                trail = puffTrail(puff).note(drawnStage, centreX, centreY, centreZ, corners);
            }
            boolean visible = true;
            int above = 0;
            int below = 0;
            int leftOf = 0;
            int rightOf = 0;
            int corner = 0;
            do {
                if (puffY[corner] < -Medium.cullY || depth < 10) {
                    above++;
                }
                if (puffY[corner] > Medium.h + Medium.cullY || depth < 10) {
                    below++;
                }
                if (puffX[corner] < -Medium.cullX || depth < 10) {
                    leftOf++;
                }
                if (puffX[corner] > Medium.w + Medium.cullX || depth < 10) {
                    rightOf++;
                }
                if (puffY[corner] < 45 && Medium.flex != 0) {
                    Medium.flex = 0;
                }
            } while (++corner < 8);
            if (leftOf == 4 || above == 4 || below == 4 || rightOf == 4) {
                visible = false;
            }
            if (visible) {
                rd.setColor(Colors.of(red, green, blue));
                if (trail) {
                    EffectTrails.Puff before = puffTrail(puff);
                    trailCorners(before.xBefore, before.yBefore, before.zBefore, before.cornersBefore);
                    if (EffectTrails.direct()) {
                        EffectTrails.between(puffX, puffY, 8);
                        rd.fillPolygon(EffectTrails.drawX, EffectTrails.drawY, 8);
                    } else {
                        Precise.trail(EffectTrails.trailX, EffectTrails.trailY, 8);
                        rd.fillPolygon(puffX, puffY, 8);
                    }
                } else {
                    rd.fillPolygon(puffX, puffY, 8);
                }
            }
            if (dov[puff] == 1) {
                dov[puff] = -1;
            }
            if (stg[puff] == 4) {
                stg[puff] = 0;
            } else {
                stg[puff]++;
                if (stg[puff] == 2 && fulls[puff]) {
                    dov[puff] = 0;
                }
            }
        } else if (dov[puff] == 0) {
            dov[puff] = 1;
        }
    }

    public ContO(byte data[], Trackers trackers) {
        t = trackers;
        p = new Plane[270];
        boolean inPolygon = false;
        boolean inTrack = false;
        int points = 0;
        float size = 1.0F;
        float width = 1.0F;
        int pointX[] = new int[100];
        int pointY[] = new int[100];
        int pointZ[] = new int[100];
        int color[] = new int[3];
        boolean isGlass = false;
        boolean isRoad = false;
        boolean noOutline = false;
        Wheels wheels = new Wheels();
        boolean hasTracks = false;
        int wheel = 0;
        int group = 1;
        int faceSign = 0;
        int wheelGroup = 0;
        byte lamp = 0;

        float axisScale[] = {
                1.0F, 1.0F, 1.0F
        };

        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader((new ByteArrayInputStream(data))))) {
            for (String line; (line = reader.readLine()) != null; ) {
                line = line.trim();

                if (line.startsWith("<p>")) {
                    inPolygon = true;
                    points = 0;
                    group = 0;
                    faceSign = 0;
                    lamp = 0;
                    noOutline = false;
                    if (!bool16) {
                        noOutline = false;
                    }
                }
                if (inPolygon) {
                    if (line.startsWith("gr")) {
                        group = Utility.getint("gr", line, 0);
                    }
                    if (line.startsWith("fs")) {
                        faceSign = Utility.getint("fs", line, 0);
                    }
                    if (line.startsWith("c")) {
                        isGlass = false;
                        color[0] = Utility.getint("c", line, 0);
                        color[1] = Utility.getint("c", line, 1);
                        color[2] = Utility.getint("c", line, 2);
                    }
                    if (line.startsWith("glass")) {
                        isGlass = true;
                    }
                    if (line.startsWith("lightF")) {
                        lamp = 1;
                    } else if (line.startsWith("lightB")) {
                        lamp = 2;
                    } else if (line.startsWith("light")) {
                        lamp = 1;
                    }
                    if (line.startsWith("noOutline")) {
                        noOutline = true;
                    }
                    if (line.startsWith("p")) {
                        pointX[points] = (int) ((Utility.getint("p", line, 0) * size * width) * axisScale[0]);
                        pointY[points] = (int) ((Utility.getint("p", line, 1) * size) * axisScale[1]);
                        pointZ[points] = (int) ((Utility.getint("p", line, 2) * size) * axisScale[2]);
                        int reach = (int) Math.sqrt(pointX[points] * pointX[points] + pointY[points] * pointY[points] + pointZ[points] * pointZ[points]);
                        if (reach > maxR) {
                            maxR = reach;
                        }
                        points++;
                    }
                }
                if (line.startsWith("</p>")) {
                    p[npl] = new Plane(t, pointX, pointZ, pointY, points, color, isGlass, group, faceSign, 0, 0, 0, disline, 0, isRoad, lamp, noOutline);
                    npl++;
                    inPolygon = false;
                }
                if (line.startsWith("rims")) {
                    wheels.setrims(Utility.getint("rims", line, 0), Utility.getint("rims", line, 1),
                            Utility.getint("rims", line, 2), Utility.getint("rims", line, 3),
                            Utility.getint("rims", line, 4));
                }
                if (line.startsWith("w")) {
                    keyx[wheel] = (int) (Utility.getint("w", line, 0) * size * axisScale[0]);
                    keyz[wheel] = (int) (Utility.getint("w", line, 2) * size * axisScale[2]);
                    wheel++;
                    wheels.make(t, p, npl, (int) (Utility.getint("w", line, 0) * size * width * axisScale[0]),
                            (int) (Utility.getint("w", line, 1) * size * axisScale[1]),
                            (int) (Utility.getint("w", line, 2) * size * axisScale[2]), Utility.getint("w", line, 3),
                            (int) (Utility.getint("w", line, 4) * size * width), (int) (Utility.getint("w", line, 5) * size),
                            wheelGroup);
                    npl += 15;
                }
                if (line.startsWith("tracks")) {
                    int count = Utility.getint("tracks", line, 0);
                    txy = new int[count];
                    tzy = new int[count];
                    tc = new int[count][3];
                    tradx = new int[count];
                    tradz = new int[count];
                    trady = new int[count];
                    tx = new int[count];
                    ty = new int[count];
                    tz = new int[count];
                    skd = new int[count];
                    dam = new int[count];
                    notwall = new boolean[count];
                    hasTracks = true; //fix from nfmm, this ensures that collision related stuff only get read if tracks(x) exists, otherwise vanilla nfm crashes without this
                }
                if (hasTracks) {
                    if (line.startsWith("<track>")) {
                        inTrack = true;
                        notwall[tnt] = false;
                        dam[tnt] = 1;
                        skd[tnt] = 0;
                        ty[tnt] = 0;
                        tx[tnt] = 0;
                        tz[tnt] = 0;
                        txy[tnt] = 0;
                        tzy[tnt] = 0;
                        trady[tnt] = 0;
                        tradx[tnt] = 0;
                        tradz[tnt] = 0;
                        tc[tnt][0] = 0;
                        tc[tnt][1] = 0;
                        tc[tnt][2] = 0;
                    }
                    if (inTrack) {
                        if (line.startsWith("c")) {
                            tc[tnt][0] = Utility.getint("c", line, 0);
                            tc[tnt][1] = Utility.getint("c", line, 1);
                            tc[tnt][2] = Utility.getint("c", line, 2);
                        }
                        if (line.startsWith("xy")) {
                            txy[tnt] = Utility.getint("xy", line, 0);
                        }
                        if (line.startsWith("zy")) {
                            tzy[tnt] = Utility.getint("zy", line, 0);
                        }
                        if (line.startsWith("radx")) {
                            tradx[tnt] = (int) (Utility.getint("radx", line, 0) * size);
                        }
                        if (line.startsWith("rady")) {
                            trady[tnt] = (int) (Utility.getint("rady", line, 0) * size);
                        }
                        if (line.startsWith("radz")) {
                            tradz[tnt] = (int) (Utility.getint("radz", line, 0) * size);
                        }
                        if (line.startsWith("ty")) {
                            ty[tnt] = (int) (Utility.getint("ty", line, 0) * size);
                        }
                        if (line.startsWith("tx")) {
                            tx[tnt] = (int) (Utility.getint("tx", line, 0) * size);
                        }
                        if (line.startsWith("tz")) {
                            tz[tnt] = (int) (Utility.getint("tz", line, 0) * size);
                        }
                        if (line.startsWith("skid")) {
                            skd[tnt] = Utility.getint("skid", line, 0);
                        }
                        if (line.startsWith("dam")) {
                            dam[tnt] = 3;
                        }
                        if (line.startsWith("notwall")) {
                            notwall[tnt] = true;
                        }
                    }
                    if (line.startsWith("</track>")) {
                        inTrack = false;
                        tnt++;
                    }
                }
                if (line.startsWith("disp")) {
                    disp = Utility.getint("disp", line, 0);
                }
                if (line.startsWith("disline")) {
                    disline = Utility.getint("disline", line, 0);
                }
                if (line.startsWith("shadow")) {
                    shadow = true;
                }
                if (line.startsWith("stonecold")) {
                    noline = true;
                }
                if (line.startsWith("newstone")) {
                    noline = true;
                    noOutline = true;
                    bool16 = true;
                }
                if (line.startsWith("road")) {
                    isRoad = true;
                }
                if (line.startsWith("notroad")) {
                    isRoad = false;
                }
                if (line.startsWith("grounded")) {
                    grounded = Utility.getint("grounded", line, 0) / 100F;
                }
                if (line.startsWith("div")) {
                    size = Utility.getint("div", line, 0) / 10F;
                }
                if (line.startsWith("idiv")) {
                    size = Utility.getint("idiv", line, 0) / 100F;
                }
                if (line.startsWith("iwid")) {
                    width = Utility.getint("iwid", line, 0) / 100F;
                }
                if (line.startsWith("gwgr")) {
                    wheelGroup = Utility.getint("gwgr", line, 0);
                }
                if (line.startsWith("ScaleX")) {
                    axisScale[0] = Utility.getint("ScaleX", line, 0) / 100F;
                }
                if (line.startsWith("ScaleY")) {
                    axisScale[1] = Utility.getint("ScaleY", line, 0) / 100F;
                }
                if (line.startsWith("ScaleZ")) {
                    axisScale[2] = Utility.getint("ScaleZ", line, 0) / 100F;
                }
            }
        } catch (IOException e) {
            HLogger.error("Error loading " + Arrays.toString(data) + ".rad");
            e.printStackTrace();
        }
        grat = wheels.ground;
    }

    public ContO(ContO model, int atX, int atY, int atZ, int turn) {
        t = model.t;
        npl = model.npl;
        maxR = model.maxR;
        disp = model.disp;
        disline = model.disline;
        noline = model.noline;
        shadow = model.shadow;
        grounded = model.grounded;
        grat = model.grat;
        p = new Plane[model.npl];
        for (int poly = 0; poly < npl; poly++) {
            if (model.p[poly].master != 0) {
                model.p[poly].n = 16;
            }
            p[poly] = new Plane(t, model.p[poly].ox, model.p[poly].oz, model.p[poly].oy, model.p[poly].n, model.p[poly].oc,
                    model.p[poly].glass, model.p[poly].gr, model.p[poly].fs, model.p[poly].wx, model.p[poly].wy, model.p[poly].wz,
                    model.disline, model.p[poly].bfase, model.p[poly].road, model.p[poly].light, model.p[poly].solo);
        }

        x = atX;
        y = atY;
        z = atZ;
        for (int poly = 0; poly < npl; poly++) {
            p[poly].master = model.p[poly].master;
            Utility.rot(p[poly].ox, p[poly].oz, 0, 0, turn, p[poly].n);
            p[poly].loadprojf();
        }

        if (model.tnt != 0) {
            for (int piece = 0; piece < model.tnt; piece++) {
                t.xy[t.nt] = (int) (model.txy[piece] * RadicalMath.cos(turn) - model.tzy[piece] * RadicalMath.sin(turn));
                t.zy[t.nt] = (int) (model.tzy[piece] * RadicalMath.cos(turn) + model.txy[piece] * RadicalMath.sin(turn));
                int channel = 0;
                do {
                    t.c[t.nt][channel] = (int) (model.tc[piece][channel]
                            + model.tc[piece][channel] * (Medium.snap[channel] / 100F));
                    if (t.c[t.nt][channel] > 255) {
                        t.c[t.nt][channel] = 255;
                    }
                    if (t.c[t.nt][channel] < 0) {
                        t.c[t.nt][channel] = 0;
                    }
                } while (++channel < 3);
                t.x[t.nt] = (int) ((x + model.tx[piece] * RadicalMath.cos(turn)) - model.tz[piece] * RadicalMath.sin(turn));
                t.z[t.nt] = (int) (z + model.tz[piece] * RadicalMath.cos(turn) + model.tx[piece] * RadicalMath.sin(turn));
                t.y[t.nt] = y + model.ty[piece];
                t.skd[t.nt] = model.skd[piece];
                t.dam[t.nt] = model.dam[piece];
                t.notwall[t.nt] = model.notwall[piece];
                channel = Math.abs(turn);
                if (channel == 180) {
                    channel = 0;
                }
                t.radx[t.nt] = (int) Math
                        .abs(model.tradx[piece] * RadicalMath.cos(channel) + model.tradz[piece] * RadicalMath.sin(channel));
                t.radz[t.nt] = (int) Math
                        .abs(model.tradx[piece] * RadicalMath.sin(channel) + model.tradz[piece] * RadicalMath.cos(channel));
                t.rady[t.nt] = model.trady[piece];
                t.nt++;
            }

        }
        int wheel = 0;
        do {
            stg[wheel] = 0;
            keyx[wheel] = model.keyx[wheel];
            keyz[wheel] = model.keyz[wheel];
        } while (++wheel < 4);
    }

    /** Scratch for {@link #polygonOrder}: objects are drawn one at a time, on the game thread. */
    private static long[] polygonKeys = new long[64];

    /**
     * Sorts the polygons for drawing back to front: by depth ({@code Plane.av})
     * from the farthest, and of two at the same depth the earlier one first,
     * the order the original's pairwise ranking produced. Each key holds the
     * depth and {@code npl - 1 - index}, sorted ascending, so walking the keys
     * from the last gives that order.
     *
     * @return {@code npl} sorted keys; the low 32 bits of each are {@code npl - 1 - index}
     */
    private long[] polygonOrder() {
        if (polygonKeys.length < npl) {
            polygonKeys = new long[Math.max(npl, polygonKeys.length * 2)];
        }
        long[] keys = polygonKeys;
        for (int k = 0; k < npl; k++) {
            keys[k] = ((long) p[k].av << 32) | (npl - 1 - k);
        }
        Arrays.sort(keys, 0, npl);
        return keys;
    }

    public void d(Graphics2D rd) {
        if (dist != 0) {
            dist = 0;
        }
        if (Precise.on) {
            Precise.object(this);
        }
        int viewX = Medium.cx + (int) ((x - Medium.x - Medium.cx) * RadicalMath.cos(Medium.xz) - (z - Medium.z - Medium.cz) * RadicalMath.sin(Medium.xz));
        int viewZ = Medium.cz + (int) ((x - Medium.x - Medium.cx) * RadicalMath.sin(Medium.xz) + (z - Medium.z - Medium.cz) * RadicalMath.cos(Medium.xz));
        int depth = Medium.cz + (int) ((y - Medium.y - Medium.cy) * RadicalMath.sin(Medium.zy) + (viewZ - Medium.cz) * RadicalMath.cos(Medium.zy));
        int screenSize = Utility.cXs(viewX + maxR, depth) - Utility.cXs(viewX - maxR, depth);
        if (Utility.cXs(viewX + maxR * 2, depth) > -Medium.cullX && Utility.cXs(viewX - maxR * 2, depth) < Medium.w + Medium.cullX && depth > -maxR && (depth < Medium.fade[disline] + maxR || Medium.trk)
                && (screenSize > disp || Medium.trk)) {
            if (shadow) {
                if (!Medium.crs) {
                    if (depth < 2000) {
                        boolean overPiece = false;
                        for (int piece = t.nt - 1; piece >= 0; piece--) {
                            if (Math.abs(t.zy[piece]) == 90 || Math.abs(t.xy[piece]) == 90
                                    || Math.abs(x - t.x[piece]) >= t.radx[piece] + maxR
                                    || Math.abs(z - t.z[piece]) >= t.radz[piece] + maxR) {
                                continue;
                            }
                            overPiece = true;
                            break;
                        }

                        rd.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                        if (overPiece) {
                            for (int poly = 0; poly < npl; poly++) {
                                p[poly].s(rd, x - Medium.x, y - Medium.y, z - Medium.z, xz, xy, zy, 0);
                            }

                        } else {
                            int groundY = Medium.cy + (int) ((Medium.ground - Medium.cy) * RadicalMath.cos(Medium.zy)
                                    - (viewZ - Medium.cz) * RadicalMath.sin(Medium.zy));
                            int groundDepth = Medium.cz + (int) ((Medium.ground - Medium.cy) * RadicalMath.sin(Medium.zy)
                                    + (viewZ - Medium.cz) * RadicalMath.cos(Medium.zy));
                            if (Utility.cYs(groundY + maxR, groundDepth) > -Medium.cullY && Utility.cYs(groundY - maxR, groundDepth) < Medium.h + Medium.cullY) {
                                for (int poly = 0; poly < npl; poly++) {
                                    p[poly].s(rd, x - Medium.x, y - Medium.y, z - Medium.z, xz, xy, zy, 1);
                                }

                            }
                        }
                        Medium.addSp(x - Medium.x, z - Medium.z, (int) (maxR * 0.80000000000000004D));
                        rd.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_OFF);
                    } else {
                        lowshadow(rd, depth);
                    }
                } else {
                    for (int poly = 0; poly < npl; poly++) {
                        p[poly].s(rd, x - Medium.x, y - Medium.y, z - Medium.z, xz, xy, zy, 2);
                    }
                }
            }
            int viewY = Medium.cy + (int) ((y - Medium.y - Medium.cy) * RadicalMath.cos(Medium.zy) - (viewZ - Medium.cz) * RadicalMath.sin(Medium.zy));
            if (Utility.cYs(viewY + maxR, depth) > -Medium.cullY && Utility.cYs(viewY - maxR, depth) < Medium.h + Medium.cullY) {
                if (elec) {
                    Glow.set(Glow.ELECTRIC);
                    electrify(rd);
                    Glow.off();
                }
                if (fix) {
                    Glow.set(Glow.REPAIR);
                    fixit(rd);
                    Glow.off();
                }
                if (checkpoint != 0 && checkpoint - 1 == Medium.checkpoint) {
                    screenSize = -1;
                }
                int wheel = 0;
                do {
                    if (stg[wheel] != 0) {
                        pdust(wheel, rd, -1);
                    }
                } while (++wheel < 4);
                if (Precise.on) {
                    Precise.object(this); // what is worked out on screen moves with the object again
                }
                long[] order = polygonOrder();
                for (int rank = npl - 1; rank >= 0; rank--) {
                    Plane plane = p[npl - 1 - (int) order[rank]];
                    plane.d(rd, x - Medium.x, y - Medium.y, z - Medium.z, xz, xy, zy, wxz, wzy, noline, screenSize, blackout);
                    if (plane.master != 0 && stg[plane.master - 1] != 0) {
                        pdust(plane.master - 1, rd, 1);
                        if (Precise.on) {
                            Precise.object(this);
                        }
                    }
                }

                dist = (int) (Math.sqrt((int) Math.sqrt(((Medium.x + Medium.cx) - x) * ((Medium.x + Medium.cx) - x) + (Medium.z - z) * (Medium.z - z)
                        + ((Medium.y + Medium.cy) - y) * ((Medium.y + Medium.cy) - y))) * grounded);
            }
        }
        if (dist == 0) {
            int wheel = 0;
            do {
                if (stg[wheel] != 0) {
                    if (stg[wheel] == 4) {
                        stg[wheel] = 0;
                    } else {
                        stg[wheel]++;
                    }
                }
            } while (++wheel < 4);
        }
    }

    /**
     * Remembers everything {@link #d(Graphics2D)} advances while it draws: the
     * repair animation (fix, fcnt), dust puffs, the electric arcs and spin of a
     * repair hoop, and the per-plane wreck, debris and flash animation.
     * <p>
     * The game was written to draw each object once per simulation tick, and the
     * simulation depends on it: a car only leaves a repair hoop once a tick sees
     * fcnt reach 7, which fixit() advances while drawing. The presentation pass
     * keeps the first frame drawn after each tick and puts this state back after
     * every further frame, so these animations still step once per tick.
     */
    void saveDrawState() {
        DrawState s = drawState;
        if (s == null) {
            s = new DrawState();
            drawState = s;
        }
        s.fix = fix;
        s.fcnt = fcnt;
        s.xy = xy;
        s.zy = zy;
        System.arraycopy(sx, 0, s.sx, 0, 4);
        System.arraycopy(sy, 0, s.sy, 0, 4);
        System.arraycopy(sz, 0, s.sz, 0, 4);
        System.arraycopy(stg, 0, s.stg, 0, 4);
        System.arraycopy(dov, 0, s.dov, 0, 4);
        System.arraycopy(edl, 0, s.edl, 0, 4);
        System.arraycopy(edr, 0, s.edr, 0, 4);
        System.arraycopy(elc, 0, s.elc, 0, 4);
        for (int i = 0; i < npl; i++) {
            p[i].saveDrawState();
        }
    }

    /**
     * Undoes everything {@link #d(Graphics2D)} advanced since
     * {@link #saveDrawState()}.
     */
    void restoreDrawState() {
        DrawState s = drawState;
        if (s == null) {
            return;
        }
        fix = s.fix;
        fcnt = s.fcnt;
        xy = s.xy;
        zy = s.zy;
        System.arraycopy(s.sx, 0, sx, 0, 4);
        System.arraycopy(s.sy, 0, sy, 0, 4);
        System.arraycopy(s.sz, 0, sz, 0, 4);
        System.arraycopy(s.stg, 0, stg, 0, 4);
        System.arraycopy(s.dov, 0, dov, 0, 4);
        System.arraycopy(s.edl, 0, edl, 0, 4);
        System.arraycopy(s.edr, 0, edr, 0, 4);
        System.arraycopy(s.elc, 0, elc, 0, 4);
        for (int i = 0; i < npl; i++) {
            p[i].restoreDrawState();
        }
    }

    /**
     * Saves or restores, through {@code b}, everything a race can change on
     * this object itself (see {@link RaceSnapshot}): where it is and how it
     * is turned, its distance from the camera, dust, sparks, the electric
     * arcs and the repair-hoop sequence. Its polygons are synced separately,
     * and the track pieces it was built with only change when a stage loads.
     */
    void syncRaceState(StateBuffer b) {
        npl = b.value(npl);
        x = b.value(x);
        y = b.value(y);
        z = b.value(z);
        xz = b.value(xz);
        xy = b.value(xy);
        zy = b.value(zy);
        wxz = b.value(wxz);
        wzy = b.value(wzy);
        dist = b.value(dist);
        maxR = b.value(maxR);
        disp = b.value(disp);
        disline = b.value(disline);
        shadow = b.value(shadow);
        noline = b.value(noline);
        grounded = b.value(grounded);
        grat = b.value(grat);
        b.contents(keyx);
        b.contents(keyz);
        b.contents(sx);
        b.contents(sy);
        b.contents(sz);
        b.contents(stg);
        b.contents(dov);
        b.contents(smag);
        b.contents(scx);
        b.contents(scz);
        b.contents(fulls);
        elec = b.value(elec);
        roted = b.value(roted);
        b.contents(edl);
        b.contents(edr);
        b.contents(elc);
        fix = b.value(fix);
        fcnt = b.value(fcnt);
        checkpoint = b.value(checkpoint);
        tnt = b.value(tnt);
        blackout = b.value(blackout);
        bool16 = b.value(bool16);
    }

    private static final class DrawState {
        boolean fix;
        int fcnt;
        int xy;
        int zy;
        final int[] sx = new int[4];
        final int[] sy = new int[4];
        final int[] sz = new int[4];
        final int[] stg = new int[4];
        final int[] dov = new int[4];
        final int[] edl = new int[4];
        final int[] edr = new int[4];
        final int[] elc = new int[4];
    }

    public void dust(int wheel, float atX, float atY, float atZ, float speedX, float speedZ, float size, boolean full, int turn) {
        boolean skip = false;
        if (turn > 5 && (wheel == 0 || wheel == 2)) {
            skip = true;
        }
        if (turn < -5 && (wheel == 1 || wheel == 3)) {
            skip = true;
        }
        if (stg[wheel] == 0 && Math.abs(speedX) + Math.abs(speedZ) > 100F && !skip) {
            sx[wheel] = (int) atX;
            sy[wheel] = (int) atY;
            sz[wheel] = (int) atZ;
            stg[wheel] = 1;
            dov[wheel] = -1;
            smag[wheel] = size;
            scx[wheel] = (int) speedX;
            scz[wheel] = (int) speedZ;
            fulls[wheel] = full;
        }
    }

    private void lowshadow(Graphics2D rd, int distance) {
        int cornerX[] = new int[4];
        int cornerY[] = new int[4];
        int cornerZ[] = new int[4];
        byte pitchSign = 1;
        int pitchAngle;
        for (pitchAngle = Math.abs(zy); pitchAngle > 270; pitchAngle -= 360) {
        }
        pitchAngle = Math.abs(pitchAngle);
        if (pitchAngle > 90) {
            pitchSign = -1;
        }
        cornerX[0] = (int) ((keyx[0] * 1.2D + x) - Medium.x);
        cornerZ[0] = (int) (((keyz[0] + 30) * pitchSign * 1.2D + z) - Medium.z);
        cornerX[1] = (int) ((keyx[1] * 1.2D + x) - Medium.x);
        cornerZ[1] = (int) (((keyz[1] + 30) * pitchSign * 1.2D + z) - Medium.z);
        cornerX[2] = (int) ((keyx[3] * 1.2D + x) - Medium.x);
        cornerZ[2] = (int) (((keyz[3] - 30) * pitchSign * 1.2D + z) - Medium.z);
        cornerX[3] = (int) ((keyx[2] * 1.2D + x) - Medium.x);
        cornerZ[3] = (int) (((keyz[2] - 30) * pitchSign * 1.2D + z) - Medium.z);
        Utility.rot(cornerX, cornerZ, x - Medium.x, z - Medium.z, xz, 4);
        int red = (int) (Medium.cgrnd[0] / 1.5D);
        int green = (int) (Medium.cgrnd[1] / 1.5D);
        int blue = (int) (Medium.cgrnd[2] / 1.5D);
        int flatCorner = 0;
        do {
            cornerY[flatCorner] = Medium.ground;
        } while (++flatCorner < 4);
        for (int piece = t.nt - 1; piece >= 0; piece--) {
            int inside = 0;
            int pieceCorner = 0;
            do {
                if (Math.abs(t.zy[piece]) != 90 && Math.abs(t.xy[piece]) != 90
                        && Math.abs(cornerX[pieceCorner] - (t.x[piece] - Medium.x)) < t.radx[piece]
                        && Math.abs(cornerZ[pieceCorner] - (t.z[piece] - Medium.z)) < t.radz[piece]) {
                    inside++;
                }
            } while (++pieceCorner < 4);
            if (inside <= 2) {
                continue;
            }
            pieceCorner = 0;
            do {
                cornerY[pieceCorner] = t.y[piece] - Medium.y;
                if (t.zy[piece] != 0) {
                    cornerY[pieceCorner] += ((cornerZ[pieceCorner] - (t.z[piece] - Medium.z - t.radz[piece])) * RadicalMath.sin(t.zy[piece]))
                            / RadicalMath.sin(90 - t.zy[piece]) - (t.radz[piece] * RadicalMath.sin(t.zy[piece])) / RadicalMath.sin(90 - t.zy[piece]);
                }
                if (t.xy[piece] != 0) {
                    cornerY[pieceCorner] += ((cornerX[pieceCorner] - (t.x[piece] - Medium.x - t.radx[piece])) * RadicalMath.sin(t.xy[piece]))
                            / RadicalMath.sin(90 - t.xy[piece]) - (t.radx[piece] * RadicalMath.sin(t.xy[piece])) / RadicalMath.sin(90 - t.xy[piece]);
                }
            } while (++pieceCorner < 4);
            red = (int) (t.c[piece][0] / 1.5D);
            green = (int) (t.c[piece][1] / 1.5D);
            blue = (int) (t.c[piece][2] / 1.5D);
            break;
        }

        Utility.rot(cornerX, cornerZ, Medium.cx, Medium.cz, Medium.xz, 4);
        Utility.rot(cornerY, cornerZ, Medium.cy, Medium.cz, Medium.zy, 4);
        boolean visible = true;
        int above = 0;
        int below = 0;
        int leftOf = 0;
        int rightOf = 0;
        int screenCorner = 0;
        do {
            cornerX[screenCorner] = Utility.cXs(cornerX[screenCorner], cornerZ[screenCorner]);
            cornerY[screenCorner] = Utility.cYs(cornerY[screenCorner], cornerZ[screenCorner]);
            if (cornerY[screenCorner] < -Medium.cullY || cornerZ[screenCorner] < 10) {
                above++;
            }
            if (cornerY[screenCorner] > Medium.h + Medium.cullY || cornerZ[screenCorner] < 10) {
                below++;
            }
            if (cornerX[screenCorner] < -Medium.cullX || cornerZ[screenCorner] < 10) {
                leftOf++;
            }
            if (cornerX[screenCorner] > Medium.w + Medium.cullX || cornerZ[screenCorner] < 10) {
                rightOf++;
            }
        } while (++screenCorner < 4);
        if (leftOf == 4 || above == 4 || below == 4 || rightOf == 4) {
            visible = false;
        }
        if (visible) {
            int band = 0;
            do {
                if (distance > Medium.fade[band]) {
                    red = (red * Medium.fogd + Medium.cfade[0]) / (Medium.fogd + 1);
                    green = (green * Medium.fogd + Medium.cfade[1]) / (Medium.fogd + 1);
                    blue = (blue * Medium.fogd + Medium.cfade[2]) / (Medium.fogd + 1);
                }
            } while (++band < 8);
            rd.setColor(Colors.of(red, green, blue));
            rd.fillPolygon(cornerX, cornerY, 4);
        }
    }

    private void fixit(Graphics2D rd) {
        if (fcnt == 1) {
            for (int poly = 0; poly < npl; poly++) {
                // COLOR OF CAR GOING THROUGH FIX HOOP
                p[poly].hsb[0] = 0.57F;
                p[poly].hsb[2] = 0.8F;
                p[poly].hsb[1] = 0.8F;
                Color color = Colors.ofHsb(p[poly].hsb[0], p[poly].hsb[1], p[poly].hsb[2]);
                int red = (int) (color.getRed() + color.getRed() * (Medium.snap[0] / 100F));
                if (red > 255) {
                    red = 255;
                }
                if (red < 0) {
                    red = 0;
                }
                int green = (int) (color.getGreen() + color.getGreen() * (Medium.snap[1] / 100F));
                if (green > 255) {
                    green = 255;
                }
                if (green < 0) {
                    green = 0;
                }
                int blue = (int) (color.getBlue() + color.getBlue() * (Medium.snap[2] / 100F));
                if (blue > 255) {
                    blue = 255;
                }
                if (blue < 0) {
                    blue = 0;
                }
                Color.RGBtoHSB(red, green, blue, p[poly].hsb);
                p[poly].flx = 1;
            }

        }
        if (fcnt == 2) {
            for (int poly = 0; poly < npl; poly++) {
                p[poly].flx = 1;
            }

        }
        if (fcnt == 4) {
            for (int poly = 0; poly < npl; poly++) {
                p[poly].flx = 3;
            }

        }
        if (fcnt == 1 || fcnt > 2) {
            int flashX[] = new int[8];
            int flashY[] = new int[8];
            int flashZ[] = new int[4];
            int width = 0;
            do {
                flashX[width] = (keyx[width] + x) - Medium.x;
                flashY[width] = (grat + y) - Medium.y;
                flashZ[width] = (keyz[width] + z) - Medium.z;
            } while (++width < 4);
            Utility.rot(flashX, flashY, x - Medium.x, y - Medium.y, xy, 4);
            Utility.rot(flashY, flashZ, y - Medium.y, z - Medium.y, zy, 4);
            Utility.rot(flashX, flashZ, x - Medium.x, z - Medium.z, xz, 4);
            Utility.rot(flashX, flashZ, Medium.cx, Medium.cz, Medium.xz, 4);
            Utility.rot(flashY, flashZ, Medium.cy, Medium.cz, Medium.zy, 4);
            width = 0;
            int height = 0;
            int diagonal = 0;
            int corner = 0;
            do {
                int other = 0;
                do {
                    if (Math.abs(flashX[corner] - flashX[other]) > width) {
                        width = Math.abs(flashX[corner] - flashX[other]);
                    }
                    if (Math.abs(flashY[corner] - flashY[other]) > height) {
                        height = Math.abs(flashY[corner] - flashY[other]);
                    }
                    if (Utility.py(flashX[corner], flashX[other], flashY[corner], flashY[other]) > diagonal) {
                        diagonal = Utility.py(flashX[corner], flashX[other], flashY[corner], flashY[other]);
                    }
                } while (++other < 4);
            } while (++corner < 4);
            diagonal = (int) (Math.sqrt(diagonal) / 1.5D);
            if (width < diagonal) {
                width = diagonal;
            }
            if (height < diagonal) {
                height = diagonal;
            }
            corner = Medium.cx + (int) ((x - Medium.x - Medium.cx) * RadicalMath.cos(Medium.xz) - (z - Medium.z - Medium.cz) * RadicalMath.sin(Medium.xz));
            int depth = Medium.cz + (int) ((x - Medium.x - Medium.cx) * RadicalMath.sin(Medium.xz) + (z - Medium.z - Medium.cz) * RadicalMath.cos(Medium.xz));
            int centerY = Medium.cy + (int) ((y - Medium.y - Medium.cy) * RadicalMath.cos(Medium.zy) - (depth - Medium.cz) * RadicalMath.sin(Medium.zy));
            depth = Medium.cz + (int) ((y - Medium.y - Medium.cy) * RadicalMath.sin(Medium.zy) + (depth - Medium.cz) * RadicalMath.cos(Medium.zy));
            flashX[0] = Utility.cXs((int) (corner - width / 0.80000000000000004D
                    - Medium.fxRandom() * (width / 2.3999999999999999D)), depth);
            flashY[0] = Utility.cYs((int) (centerY - height / 1.9199999999999999D
                    - Medium.fxRandom() * (height / 5.6699999999999999D)), depth);
            flashX[1] = Utility.cXs((int) (corner - width / 0.80000000000000004D
                    - Medium.fxRandom() * (width / 2.3999999999999999D)), depth);
            flashY[1] = Utility.cYs((int) (centerY + height / 1.9199999999999999D
                    + Medium.fxRandom() * (height / 5.6699999999999999D)), depth);
            flashX[2] = Utility.cXs((int) (corner - width / 1.9199999999999999D
                    - Medium.fxRandom() * (width / 5.6699999999999999D)), depth);
            flashY[2] = Utility.cYs((int) (centerY + height / 0.80000000000000004D
                    + Medium.fxRandom() * (height / 2.3999999999999999D)), depth);
            flashX[3] = Utility.cXs((int) (corner + width / 1.9199999999999999D
                    + Medium.fxRandom() * (width / 5.6699999999999999D)), depth);
            flashY[3] = Utility.cYs((int) (centerY + height / 0.80000000000000004D
                    + Medium.fxRandom() * (height / 2.3999999999999999D)), depth);
            flashX[4] = Utility.cXs((int) (corner + width / 0.80000000000000004D
                    + Medium.fxRandom() * (width / 2.3999999999999999D)), depth);
            flashY[4] = Utility.cYs((int) (centerY + height / 1.9199999999999999D
                    + Medium.fxRandom() * (height / 5.6699999999999999D)), depth);
            flashX[5] = Utility.cXs((int) (corner + width / 0.80000000000000004D
                    + Medium.fxRandom() * (width / 2.3999999999999999D)), depth);
            flashY[5] = Utility.cYs((int) (centerY - height / 1.9199999999999999D
                    - Medium.fxRandom() * (height / 5.6699999999999999D)), depth);
            flashX[6] = Utility.cXs((int) (corner + width / 1.9199999999999999D
                    + Medium.fxRandom() * (width / 5.6699999999999999D)), depth);
            flashY[6] = Utility.cYs((int) (centerY - height / 0.80000000000000004D
                    - Medium.fxRandom() * (height / 2.3999999999999999D)), depth);
            flashX[7] = Utility.cXs((int) (corner - width / 1.9199999999999999D
                    - Medium.fxRandom() * (width / 5.6699999999999999D)), depth);
            flashY[7] = Utility.cYs((int) (centerY - height / 0.80000000000000004D
                    - Medium.fxRandom() * (height / 2.3999999999999999D)), depth);
            if (fcnt == 3) {
                Utility.rot(flashX, flashY, Utility.cXs(corner, depth), Utility.cYs(centerY, depth), 22, 8);
            }
            if (fcnt == 4) {
                Utility.rot(flashX, flashY, Utility.cXs(corner, depth), Utility.cYs(centerY, depth), 22, 8);
            }
            if (fcnt == 5) {
                Utility.rot(flashX, flashY, Utility.cXs(corner, depth), Utility.cYs(centerY, depth), 0, 8);
            }
            if (fcnt == 6) {
                Utility.rot(flashX, flashY, Utility.cXs(corner, depth), Utility.cYs(centerY, depth), -22, 8);
            }
            if (fcnt == 7) {
                Utility.rot(flashX, flashY, Utility.cXs(corner, depth), Utility.cYs(centerY, depth), -22, 8);
            }
            int red = (int) (191F + 191F * (Medium.snap[0] / 350F));
            if (red > 255) {
                red = 255;
            }
            if (red < 0) {
                red = 0;
            }
            int green = (int) (232F + 232F * (Medium.snap[1] / 350F));
            if (green > 255) {
                green = 255;
            }
            if (green < 0) {
                green = 0;
            }
            int blue = (int) (255F + 255F * (Medium.snap[2] / 350F));
            if (blue > 255) {
                blue = 255;
            }
            if (blue < 0) {
                blue = 0;
            }
            rd.setColor(Colors.of(red, green, blue));
            rd.fillPolygon(flashX, flashY, 8);
            flashX[0] = Utility.cXs((int) (corner - width - Medium.fxRandom() * (width / 4)), depth);
            flashY[0] = Utility.cYs((int) (centerY - height / 2.3999999999999999D
                    - Medium.fxRandom() * (height / 9.5999999999999996D)), depth);
            flashX[1] = Utility.cXs((int) (corner - width - Medium.fxRandom() * (width / 4)), depth);
            flashY[1] = Utility.cYs((int) (centerY + height / 2.3999999999999999D
                    + Medium.fxRandom() * (height / 9.5999999999999996D)), depth);
            flashX[2] = Utility.cXs((int) (corner - width / 2.3999999999999999D
                    - Medium.fxRandom() * (width / 9.5999999999999996D)), depth);
            flashY[2] = Utility.cYs((int) (centerY + height + Medium.fxRandom() * (height / 4)), depth);
            flashX[3] = Utility.cXs((int) (corner + width / 2.3999999999999999D
                    + Medium.fxRandom() * (width / 9.5999999999999996D)), depth);
            flashY[3] = Utility.cYs((int) (centerY + height + Medium.fxRandom() * (height / 4)), depth);
            flashX[4] = Utility.cXs((int) (corner + width + Medium.fxRandom() * (width / 4)), depth);
            flashY[4] = Utility.cYs((int) (centerY + height / 2.3999999999999999D
                    + Medium.fxRandom() * (height / 9.5999999999999996D)), depth);
            flashX[5] = Utility.cXs((int) (corner + width + Medium.fxRandom() * (width / 4)), depth);
            flashY[5] = Utility.cYs((int) (centerY - height / 2.3999999999999999D
                    - Medium.fxRandom() * (height / 9.5999999999999996D)), depth);
            flashX[6] = Utility.cXs((int) (corner + width / 2.3999999999999999D
                    + Medium.fxRandom() * (width / 9.5999999999999996D)), depth);
            flashY[6] = Utility.cYs((int) (centerY - height - Medium.fxRandom() * (height / 4)), depth);
            flashX[7] = Utility.cXs((int) (corner - width / 2.3999999999999999D
                    - Medium.fxRandom() * (width / 9.5999999999999996D)), depth);
            flashY[7] = Utility.cYs((int) (centerY - height - Medium.fxRandom() * (height / 4)), depth);
            red = (int) (213F + 213F * (Medium.snap[0] / 350F));
            if (red > 255) {
                red = 255;
            }
            if (red < 0) {
                red = 0;
            }
            green = (int) (239F + 239F * (Medium.snap[1] / 350F));
            if (green > 255) {
                green = 255;
            }
            if (green < 0) {
                green = 0;
            }
            blue = (int) (255F + 255F * (Medium.snap[2] / 350F));
            if (blue > 255) {
                blue = 255;
            }
            if (blue < 0) {
                blue = 0;
            }
            rd.setColor(Colors.of(red, green, blue));
            rd.fillPolygon(flashX, flashY, 8);
        }
        if (fcnt > 7) {
            fcnt = 0;
            fix = false;
        } else {
            fcnt++;
        }
    }

    private void electrify(Graphics2D rd) {
        int arc = 0;
        do {
            if (elc[arc] == 0) {
                edl[arc] = (int) (380F - Medium.fxRandom() * 760F);
                edr[arc] = (int) (380F - Medium.fxRandom() * 760F);
                elc[arc] = 1;
            }
            int leftBend = (int) (edl[arc] + (190F - Medium.fxRandom() * 380F));
            int rightBend = (int) (edr[arc] + (190F - Medium.fxRandom() * 380F));
            int rightJitter = (int) (Medium.fxRandom() * 126F);
            int leftJitter = (int) (Medium.fxRandom() * 126F);
            int arcX[] = new int[8];
            int arcY[] = new int[8];
            int arcZ[] = new int[8];
            int depthPoint = 0;
            do {
                arcZ[depthPoint] = z - Medium.z;
            } while (++depthPoint < 8);
            arcX[0] = x - Medium.x - 504;
            arcY[0] = y - Medium.y - edl[arc] - 5 - (int) (Medium.fxRandom() * 5F);
            arcX[1] = (x - Medium.x - 252) + leftJitter;
            arcY[1] = y - Medium.y - leftBend - 5 - (int) (Medium.fxRandom() * 5F);
            arcX[2] = ((x - Medium.x) + 252) - rightJitter;
            arcY[2] = y - Medium.y - rightBend - 5 - (int) (Medium.fxRandom() * 5F);
            arcX[3] = (x - Medium.x) + 504;
            arcY[3] = y - Medium.y - edr[arc] - 5 - (int) (Medium.fxRandom() * 5F);
            arcX[4] = (x - Medium.x) + 504;
            arcY[4] = (y - Medium.y - edr[arc]) + 5 + (int) (Medium.fxRandom() * 5F);
            arcX[5] = ((x - Medium.x) + 252) - rightJitter;
            arcY[5] = (y - Medium.y - rightBend) + 5 + (int) (Medium.fxRandom() * 5F);
            arcX[6] = (x - Medium.x - 252) + leftJitter;
            arcY[6] = (y - Medium.y - leftBend) + 5 + (int) (Medium.fxRandom() * 5F);
            arcX[7] = x - Medium.x - 504;
            arcY[7] = (y - Medium.y - edl[arc]) + 5 + (int) (Medium.fxRandom() * 5F);
            if (roted) {
                Utility.rot(arcX, arcZ, x - Medium.x, z - Medium.z, 90, 8);
            }
            Utility.rot(arcX, arcZ, Medium.cx, Medium.cz, Medium.xz, 8);
            Utility.rot(arcY, arcZ, Medium.cy, Medium.cz, Medium.zy, 8);
            boolean visible = true;
            int above = 0;
            int below = 0;
            int leftOf = 0;
            int rightOf = 0;
            int screenX[] = new int[8];
            int screenY[] = new int[8];
            int point = 0;
            do {
                screenX[point] = Utility.cXs(arcX[point], arcZ[point]);
                screenY[point] = Utility.cYs(arcY[point], arcZ[point]);
                if (screenY[point] < -Medium.cullY || arcZ[point] < 10) {
                    above++;
                }
                if (screenY[point] > Medium.h + Medium.cullY || arcZ[point] < 10) {
                    below++;
                }
                if (screenX[point] < -Medium.cullX || arcZ[point] < 10) {
                    leftOf++;
                }
                if (screenX[point] > Medium.w + Medium.cullX || arcZ[point] < 10) {
                    rightOf++;
                }
            } while (++point < 8);
            if (leftOf == 8 || above == 8 || below == 8 || rightOf == 8) {
                visible = false;
            }
            if (visible) {
                int red = (int) (160F + 160F * (Medium.snap[0] / 500F));
                if (red > 255) {
                    red = 255;
                }
                if (red < 0) {
                    red = 0;
                }
                int green = (int) (238F + 238F * (Medium.snap[1] / 500F));
                if (green > 255) {
                    green = 255;
                }
                if (green < 0) {
                    green = 0;
                }
                int blue = (int) (255F + 255F * (Medium.snap[2] / 500F));
                if (blue > 255) {
                    blue = 255;
                }
                if (blue < 0) {
                    blue = 0;
                }
                red = (red * 2 + 214 * (elc[arc] - 1)) / (elc[arc] + 1);
                green = (green * 2 + 236 * (elc[arc] - 1)) / (elc[arc] + 1);
                if (Medium.trk) {
                    red = 255;
                    green = 128;
                    blue = 0;
                }
                rd.setColor(Colors.of(red, green, blue));
                rd.fillPolygon(screenX, screenY, 8);
                if (arcZ[0] < 4000) {
                    int edgeRed = (int) (150F + 150F * (Medium.snap[0] / 500F));
                    if (edgeRed > 255) {
                        edgeRed = 255;
                    }
                    if (edgeRed < 0) {
                        edgeRed = 0;
                    }
                    int edgeGreen = (int) (227F + 227F * (Medium.snap[1] / 500F));
                    if (edgeGreen > 255) {
                        edgeGreen = 255;
                    }
                    if (edgeGreen < 0) {
                        edgeGreen = 0;
                    }
                    int edgeBlue = (int) (255F + 255F * (Medium.snap[2] / 500F));
                    if (edgeBlue > 255) {
                        edgeBlue = 255;
                    }
                    if (edgeBlue < 0) {
                        edgeBlue = 0;
                    }
                    rd.setColor(Colors.of(edgeRed, edgeGreen, edgeBlue));
                    rd.drawPolygon(screenX, screenY, 8);
                }
            }
            if (elc[arc] > Medium.fxRandom() * 60F) {
                elc[arc] = 0;
            } else {
                elc[arc]++;
            }
        } while (++arc < 4);
        if (!roted) {
            xy += 11;
        } else {
            zy += 11;
        }
    }
}
