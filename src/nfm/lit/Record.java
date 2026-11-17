package nfm.lit;
import java.awt.*;

class Record {

    public final ContO[] ocar;
    public final ContO[] starcar;
    public final ContO[][] car;
    public int caught;
    public boolean hcaught;
    private boolean prepit;
    private int cntf;
    private final int[][] squash;
    public final int[] fix;
    public final int[] dest;
    private final int[][] x;
    private final int[][] y;
    private final int[][] z;
    private final int[][] xy;
    private final int[][] zy;
    private final int[][] xz;
    private final int[][] wxz;
    private final int[][] wzy;
    private final int[][] ns;
    private final int[][][] sspark;
    private final int[][][] sx;
    private final int[][][] sy;
    private final int[][][] sz;
    private final float[][][] smag;
    private final int[][][] scx;
    private final int[][][] scz;
    private final boolean[][][] fulls;
    private final int[][] nry;
    private final int[][][] ry;
    private final int[][][] magy;
    private final boolean[][] mtouch;
    private final int[][] nrx;
    private final int[][][] rx;
    private final int[][][] magx;
    private final int[][] nrz;
    private final int[][][] rz;
    private final int[][][] magz;
    private final int[] checkpoint;
    private final boolean[] lastcheck;
    public int wasted;
    public int whenwasted;
    public int powered;
    public int closefinish;
    private final int[] hsquash;
    public final int[] hfix = {
            -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1,
            -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1,
            -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1,
    };
    private final int[] hdest = {
            -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1,
            -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1,
            -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1,
    };
    private final int[][] hx;
    private final int[][] hy;
    private final int[][] hz;
    private final int[][] hxy;
    private final int[][] hzy;
    private final int[][] hxz;
    private final int[][] hwxz;
    private final int[][] hwzy;
    private final int[][] hns;
    private final int[][][] hsspark;
    private final int[][][] hsx;
    private final int[][][] hsy;
    private final int[][][] hsz;
    private final float[][][] hsmag;
    private final int[][][] hscx;
    private final int[][][] hscz;
    private final boolean[][][] hfulls;
    private final int[][] hnry;
    private final int[][][] hry;
    private final int[][][] hmagy;
    private final int[][] hnrx;
    private final int[][][] hrx;
    private final int[][][] hmagx;
    private final int[][] hnrz;
    private final int[][][] hrz;
    private final int[][][] hmagz;
    private final boolean[][] hmtouch;
    private final int[] hcheckpoint;
    private final boolean[] hlastcheck;
    public final int[] cntdest;
    private int lastfr;

    private void regy(int wheel, float force, boolean touching, ContO carModel, Madness madness) {
        if (force > 100F) {
            force -= 100F;
            byte pitchSide = 0;
            byte rollSide = 0;
            int pitch = carModel.zy;
            int roll = carModel.xy;
            for (; pitch < 360; pitch += 360) {
            }
            for (; pitch > 360; pitch -= 360) {
            }
            if (pitch < 210 && pitch > 150) {
                pitchSide = -1;
            }
            if (pitch > 330 || pitch < 30) {
                pitchSide = 1;
            }
            for (; roll < 360; roll += 360) {
            }
            for (; roll > 360; roll -= 360) {
            }
            if (roll < 210 && roll > 150) {
                rollSide = -1;
            }
            if (roll > 330 || roll < 30) {
                rollSide = 1;
            }
            if (rollSide * pitchSide == 0 || touching) {
                for (int poly = 0; poly < carModel.npl; poly++) {
                    float dent = 0.0F;
                    for (int corner = 0; corner < carModel.p[poly].n; corner++) {
                        if (carModel.p[poly].wz == 0 && Utility.py(carModel.keyx[wheel], carModel.p[poly].ox[corner], carModel.keyz[wheel],
                                carModel.p[poly].oz[corner]) < madness.stat.clrad) {
                            dent = (force / 20F) * Medium.fxRandom();
                            carModel.p[poly].oz[corner] += dent * RadicalMath.sin(pitch);
                            carModel.p[poly].ox[corner] -= dent * RadicalMath.sin(roll);
                        }
                    }

                    if (dent != 0.0F) {
                        if (Math.abs(dent) >= 1.0F) {
                            carModel.p[poly].chip = 1;
                            carModel.p[poly].ctmag = dent;
                        }
                        if (!carModel.p[poly].nocol && !carModel.p[poly].glass) {
                            if (carModel.p[poly].bfase > 20 && carModel.p[poly].hsb[1] > 0.20000000000000001D) {
                                carModel.p[poly].hsb[1] = 0.2F;
                            }
                            if (carModel.p[poly].bfase > 30) {
                                if (carModel.p[poly].hsb[2] < 0.5D) {
                                    carModel.p[poly].hsb[2] = 0.5F;
                                }
                                if (carModel.p[poly].hsb[1] > 0.10000000000000001D) {
                                    carModel.p[poly].hsb[1] = 0.1F;
                                }
                            }
                            if (carModel.p[poly].bfase > 40) {
                                carModel.p[poly].hsb[1] = 0.05F;
                            }
                            if (carModel.p[poly].bfase > 50) {
                                if (carModel.p[poly].hsb[2] > 0.80000000000000004D) {
                                    carModel.p[poly].hsb[2] = 0.8F;
                                }
                                carModel.p[poly].hsb[0] = 0.075F;
                                carModel.p[poly].hsb[1] = 0.05F;
                            }
                            if (carModel.p[poly].bfase > 60) {
                                carModel.p[poly].hsb[0] = 0.05F;
                            }
                            carModel.p[poly].bfase += dent;
                            new Color(carModel.p[poly].c[0], carModel.p[poly].c[1], carModel.p[poly].c[2]);
                            Color color = Color.getHSBColor(carModel.p[poly].hsb[0], carModel.p[poly].hsb[1], carModel.p[poly].hsb[2]);
                            carModel.p[poly].c[0] = color.getRed();
                            carModel.p[poly].c[1] = color.getGreen();
                            carModel.p[poly].c[2] = color.getBlue();
                        }
                        if (carModel.p[poly].glass) {
                            carModel.p[poly].gr += Math.abs(dent * 1.5D);
                        }
                    }
                }

            }
            if (rollSide * pitchSide == -1) {
                int squashed = 0;
                int count = 1;
                for (int poly = 0; poly < carModel.npl; poly++) {
                    float dent = 0.0F;
                    for (int corner = 0; corner < carModel.p[poly].n; corner++) {
                        if (carModel.p[poly].wz == 0) {
                            dent = (force / 15F) * Medium.fxRandom();
                            if ((Math
                                    .abs(carModel.p[poly].oy[corner] - madness.stat.flipy
                                            - squash[0][madness.im]) < madness.stat.msquash * 3
                                    || carModel.p[poly].oy[corner] < madness.stat.flipy + squash[0][madness.im])
                                    && squash[0][madness.im] < madness.stat.msquash) {
                                carModel.p[poly].oy[corner] += dent;
                                squashed = (int) (squashed + dent);
                                count++;
                            }
                        }
                    }

                    if (carModel.p[poly].glass) {
                        carModel.p[poly].gr += 5;
                    } else if (dent != 0.0F) {
                        carModel.p[poly].bfase += dent;
                    }
                    if (Math.abs(dent) >= 1.0F) {
                        carModel.p[poly].chip = 1;
                        carModel.p[poly].ctmag = dent;
                    }
                }

                squash[0][madness.im] += squashed / count;
            }
        }
    }

    public void reset(ContO cars[]) {
        caught = 0;
        hcaught = false;
        wasted = 0;
        whenwasted = 0;
        closefinish = 0;
        powered = 0;
        int index = 0;
        do {
            if (prepit) {
                starcar[index] = copy(cars[index]);
            }
            fix[index] = -1;
            dest[index] = -1;
            cntdest[index] = 0;
        } while (++index < 51);
        index = 0;
        do {
            int racer = 0;
            do {
                car[index][racer] = copy(cars[racer]);
                squash[index][racer] = 0;
            } while (++racer < 51);
        } while (++index < 6);
        index = 0;
        do {
            int wheel = 0;
            do {
                int slot = 0;
                do {
                    sspark[index][wheel][slot] = -1;
                    ns[index][wheel] = 0;
                } while (++slot < 30);
                slot = 0;
                do {
                    ry[index][wheel][slot] = -1;
                    nry[index][wheel] = 0;
                    rx[index][wheel][slot] = -1;
                    nrx[index][wheel] = 0;
                    rz[index][wheel][slot] = -1;
                    nrz[index][wheel] = 0;
                } while (++slot < 7);
            } while (++wheel < 4);
        } while (++index < 51);
        prepit = false;
    }

    /**
     * Saves or restores, through {@code b}, everything the replay buffer holds
     * (see {@link RaceSnapshot}): the last 300 ticks of every car, the
     * highlight it keeps, and which model copies it points at. The copies
     * themselves never change once made, so they are kept by reference.
     */
    void syncRaceState(StateBuffer b) {
        b.contents(ocar);
        b.contents(starcar);
        for (ContO[] row : car) {
            b.contents(row);
        }
        caught = b.value(caught);
        hcaught = b.value(hcaught);
        prepit = b.value(prepit);
        cntf = b.value(cntf);
        wasted = b.value(wasted);
        whenwasted = b.value(whenwasted);
        powered = b.value(powered);
        closefinish = b.value(closefinish);
        lastfr = b.value(lastfr);
        b.contents(squash);
        b.contents(fix);
        b.contents(dest);
        b.contents(x);
        b.contents(y);
        b.contents(z);
        b.contents(xy);
        b.contents(zy);
        b.contents(xz);
        b.contents(wxz);
        b.contents(wzy);
        b.contents(ns);
        b.contents(sspark);
        b.contents(sx);
        b.contents(sy);
        b.contents(sz);
        b.contents(smag);
        b.contents(scx);
        b.contents(scz);
        b.contents(fulls);
        b.contents(nry);
        b.contents(ry);
        b.contents(magy);
        b.contents(mtouch);
        b.contents(nrx);
        b.contents(rx);
        b.contents(magx);
        b.contents(nrz);
        b.contents(rz);
        b.contents(magz);
        b.contents(checkpoint);
        b.contents(lastcheck);
        b.contents(hsquash);
        b.contents(hfix);
        b.contents(hdest);
        b.contents(hx);
        b.contents(hy);
        b.contents(hz);
        b.contents(hxy);
        b.contents(hzy);
        b.contents(hxz);
        b.contents(hwxz);
        b.contents(hwzy);
        b.contents(hns);
        b.contents(hsspark);
        b.contents(hsx);
        b.contents(hsy);
        b.contents(hsz);
        b.contents(hsmag);
        b.contents(hscx);
        b.contents(hscz);
        b.contents(hfulls);
        b.contents(hnry);
        b.contents(hry);
        b.contents(hmagy);
        b.contents(hnrx);
        b.contents(hrx);
        b.contents(hmagx);
        b.contents(hnrz);
        b.contents(hrz);
        b.contents(hmagz);
        b.contents(hmtouch);
        b.contents(hcheckpoint);
        b.contents(hlastcheck);
        b.contents(cntdest);
    }

    public Record() {
        caught = 0;
        hcaught = false;
        prepit = true;
        ocar = new ContO[51];
        cntf = 50;
        car = new ContO[6][51];
        squash = new int[6][51];
        fix = new int[51];
        dest = new int[51];
        x = new int[300][51];
        y = new int[300][51];
        z = new int[300][51];
        xy = new int[300][51];
        zy = new int[300][51];
        xz = new int[300][51];
        wxz = new int[300][51];
        wzy = new int[300][51];
        ns = new int[51][4];
        sspark = new int[51][4][30];
        sx = new int[51][4][30];
        sy = new int[51][4][30];
        sz = new int[51][4][30];
        smag = new float[51][4][30];
        scx = new int[51][4][30];
        scz = new int[51][4][30];
        fulls = new boolean[51][4][30];
        nry = new int[51][4];
        ry = new int[51][4][7]; //no
        magy = new int[51][4][7]; //no
        mtouch = new boolean[51][7]; //no
        nrx = new int[51][4];
        rx = new int[51][4][7]; //no
        magx = new int[51][4][7]; //no
        nrz = new int[51][4];
        rz = new int[51][4][7]; //no
        magz = new int[51][4][7]; //no
        checkpoint = new int[300];
        lastcheck = new boolean[300];
        wasted = 0;
        whenwasted = 0;
        powered = 0;
        closefinish = 0;
        starcar = new ContO[51];
        hsquash = new int[51];
        hx = new int[300][51];
        hy = new int[300][51];
        hz = new int[300][51];
        hxy = new int[300][51];
        hzy = new int[300][51];
        hxz = new int[300][51];
        hwxz = new int[300][51];
        hwzy = new int[300][51];
        hns = new int[51][4];
        hsspark = new int[51][4][30];
        hsx = new int[51][4][30];
        hsy = new int[51][4][30];
        hsz = new int[51][4][30];
        hsmag = new float[51][4][30];
        hscx = new int[51][4][30];
        hscz = new int[51][4][30];
        hfulls = new boolean[51][4][30];
        hnry = new int[51][4];
        hry = new int[51][4][7]; //no
        hmagy = new int[51][4][7]; //no
        hnrx = new int[51][4];
        hrx = new int[51][4][7]; //no
        hmagx = new int[51][4][7]; //no
        hnrz = new int[51][4];
        hrz = new int[51][4][7]; //no
        hmagz = new int[51][4][7]; //no
        hmtouch = new boolean[51][7]; //no
        hcheckpoint = new int[300];
        hlastcheck = new boolean[300];
        cntdest = new int[51];
        lastfr = 0;
        caught = 0;
        cotchinow(0);
    }

    public void playh(ContO carModel, Madness madness, int carIndex, int frame) {
        carModel.x = hx[frame][carIndex];
        carModel.y = hy[frame][carIndex];
        carModel.z = hz[frame][carIndex];
        carModel.zy = hzy[frame][carIndex];
        carModel.xy = hxy[frame][carIndex];
        carModel.xz = hxz[frame][carIndex];
        carModel.wxz = hwxz[frame][carIndex];
        carModel.wzy = hwzy[frame][carIndex];
        if (carIndex == 0) {
            Medium.checkpoint = hcheckpoint[frame];
            Medium.lastcheck = hlastcheck[frame];
        }
        if (frame == 0) {
            cntdest[carIndex] = 0;
        }
        if (hdest[carIndex] == frame) {
            cntdest[carIndex] = 7;
        }
        if (frame == 0 && hdest[carIndex] < -1) {
            for (int poly = 0; poly < carModel.npl; poly++) {
                if (carModel.p[poly].wz == 0 || carModel.p[poly].gr == -17 || carModel.p[poly].gr == -16) {
                    carModel.p[poly].embos = 13;
                }
            }

        }
        if (cntdest[carIndex] != 0) {
            for (int poly = 0; poly < carModel.npl; poly++) {
                if (carModel.p[poly].wz == 0 || carModel.p[poly].gr == -17 || carModel.p[poly].gr == -16) {
                    carModel.p[poly].embos = 1;
                }
            }

            cntdest[carIndex]--;
        }
        int wheel = 0;
        do {
            int slot = 0;
            do {
                if (hsspark[carIndex][wheel][slot] == frame) {
                    carModel.stg[wheel] = 1;
                    carModel.dov[wheel] = -1;
                    carModel.sx[wheel] = hsx[carIndex][wheel][slot];
                    carModel.sy[wheel] = hsy[carIndex][wheel][slot];
                    carModel.sz[wheel] = hsz[carIndex][wheel][slot];
                    carModel.smag[wheel] = hsmag[carIndex][wheel][slot];
                    carModel.scx[wheel] = hscx[carIndex][wheel][slot];
                    carModel.scz[wheel] = hscz[carIndex][wheel][slot];
                    carModel.fulls[wheel] = hfulls[carIndex][wheel][slot];
                }
            } while (++slot < 30);
            slot = 0;
            do {
                if (hry[carIndex][wheel][slot] == frame && lastfr != frame) {
                    regy(wheel, hmagy[carIndex][wheel][slot], hmtouch[carIndex][slot], carModel, madness);
                }
                if (hrx[carIndex][wheel][slot] == frame) {
                    if (lastfr != frame) {
                        regx(wheel, hmagx[carIndex][wheel][slot], carModel, madness);
                    } else {
                        chipx(wheel, hmagx[carIndex][wheel][slot], carModel, madness);
                    }
                }
                if (hrz[carIndex][wheel][slot] == frame) {
                    if (lastfr != frame) {
                        regz(wheel, hmagz[carIndex][wheel][slot], carModel, madness);
                    } else {
                        chipz(wheel, hmagz[carIndex][wheel][slot], carModel, madness);
                    }
                }
            } while (++slot < 7);
        } while (++wheel < 4);
        lastfr = frame;
    }

    private void chipz(int wheel, float force, ContO carModel, Madness madness) {
        if (Math.abs(force) > 100F) {
            if (force > 100F) {
                force -= 100F;
            }
            if (force < -100F) {
                force += 100F;
            }
            for (int poly = 0; poly < carModel.npl; poly++) {
                float dent = 0.0F;
                for (int corner = 0; corner < carModel.p[poly].n; corner++) {
                    if (carModel.p[poly].wz == 0 && Utility.py(carModel.keyx[wheel], carModel.p[poly].ox[corner], carModel.keyz[wheel],
                            carModel.p[poly].oz[corner]) < madness.stat.clrad) {
                        dent = (force / 20F) * Medium.fxRandom();
                    }
                }

                if (dent != 0.0F && Math.abs(dent) >= 1.0F) {
                    carModel.p[poly].chip = 1;
                    carModel.p[poly].ctmag = dent;
                }
            }

        }
    }

    private void regz(int wheel, float force, ContO carModel, Madness madness) {
        if (Math.abs(force) > 100F) {
            if (force > 100F) {
                force -= 100F;
            }
            if (force < -100F) {
                force += 100F;
            }
            for (int poly = 0; poly < carModel.npl; poly++) {
                float dent = 0.0F;
                for (int corner = 0; corner < carModel.p[poly].n; corner++) {
                    if (carModel.p[poly].wz == 0 && Utility.py(carModel.keyx[wheel], carModel.p[poly].ox[corner], carModel.keyz[wheel],
                            carModel.p[poly].oz[corner]) < madness.stat.clrad) {
                        dent = (force / 20F) * Medium.fxRandom();
                        carModel.p[poly].oz[corner] += dent * RadicalMath.cos(carModel.xz) * RadicalMath.cos(carModel.zy);
                        carModel.p[poly].ox[corner] += dent * RadicalMath.sin(carModel.xz) * RadicalMath.cos(carModel.xy);
                    }
                }

                if (dent != 0.0F) {
                    if (Math.abs(dent) >= 1.0F) {
                        carModel.p[poly].chip = 1;
                        carModel.p[poly].ctmag = dent;
                    }
                    if (!carModel.p[poly].nocol && !carModel.p[poly].glass) {
                        if (carModel.p[poly].bfase > 20 && carModel.p[poly].hsb[1] > 0.20000000000000001D) {
                            carModel.p[poly].hsb[1] = 0.2F;
                        }
                        if (carModel.p[poly].bfase > 30) {
                            if (carModel.p[poly].hsb[2] < 0.5D) {
                                carModel.p[poly].hsb[2] = 0.5F;
                            }
                            if (carModel.p[poly].hsb[1] > 0.10000000000000001D) {
                                carModel.p[poly].hsb[1] = 0.1F;
                            }
                        }
                        if (carModel.p[poly].bfase > 40) {
                            carModel.p[poly].hsb[1] = 0.05F;
                        }
                        if (carModel.p[poly].bfase > 50) {
                            if (carModel.p[poly].hsb[2] > 0.80000000000000004D) {
                                carModel.p[poly].hsb[2] = 0.8F;
                            }
                            carModel.p[poly].hsb[0] = 0.075F;
                            carModel.p[poly].hsb[1] = 0.05F;
                        }
                        if (carModel.p[poly].bfase > 60) {
                            carModel.p[poly].hsb[0] = 0.05F;
                        }
                        carModel.p[poly].bfase += Math.abs(dent);
                        new Color(carModel.p[poly].c[0], carModel.p[poly].c[1], carModel.p[poly].c[2]);
                        Color color = Color.getHSBColor(carModel.p[poly].hsb[0], carModel.p[poly].hsb[1], carModel.p[poly].hsb[2]);
                        carModel.p[poly].c[0] = color.getRed();
                        carModel.p[poly].c[1] = color.getGreen();
                        carModel.p[poly].c[2] = color.getBlue();
                    }
                    if (carModel.p[poly].glass) {
                        carModel.p[poly].gr += Math.abs(dent * 1.5D);
                    }
                }
            }

        }
    }

    public void play(ContO carModel, Madness madness, int carIndex, int frame) {
        carModel.x = x[frame][carIndex];
        carModel.y = y[frame][carIndex];
        carModel.z = z[frame][carIndex];
        carModel.zy = zy[frame][carIndex];
        carModel.xy = xy[frame][carIndex];
        carModel.xz = xz[frame][carIndex];
        carModel.wxz = wxz[frame][carIndex];
        carModel.wzy = wzy[frame][carIndex];
        if (carIndex == 0) {
            Medium.checkpoint = checkpoint[frame];
            Medium.lastcheck = lastcheck[frame];
        }
        if (frame == 0) {
            cntdest[carIndex] = 0;
        }
        if (dest[carIndex] == frame) {
            cntdest[carIndex] = 7;
        }
        if (frame == 0 && dest[carIndex] < -1) {
            for (int poly = 0; poly < carModel.npl; poly++) {
                if (carModel.p[poly].wz == 0 || carModel.p[poly].gr == -17 || carModel.p[poly].gr == -16) {
                    carModel.p[poly].embos = 13;
                }
            }

        }
        if (cntdest[carIndex] != 0) {
            for (int poly = 0; poly < carModel.npl; poly++) {
                if (carModel.p[poly].wz == 0 || carModel.p[poly].gr == -17 || carModel.p[poly].gr == -16) {
                    carModel.p[poly].embos = 1;
                }
            }

            cntdest[carIndex]--;
        }
        int wheel = 0;
        do {
            int slot = 0;
            do {
                if (sspark[carIndex][wheel][slot] == frame) {
                    carModel.stg[wheel] = 1;
                    carModel.dov[wheel] = -1;
                    carModel.sx[wheel] = sx[carIndex][wheel][slot];
                    carModel.sy[wheel] = sy[carIndex][wheel][slot];
                    carModel.sz[wheel] = sz[carIndex][wheel][slot];
                    carModel.smag[wheel] = smag[carIndex][wheel][slot];
                    carModel.scx[wheel] = scx[carIndex][wheel][slot];
                    carModel.scz[wheel] = scz[carIndex][wheel][slot];
                    carModel.fulls[wheel] = fulls[carIndex][wheel][slot];
                }
            } while (++slot < 30);
            slot = 0;
            do {
                if (ry[carIndex][wheel][slot] == frame) {
                    regy(wheel, magy[carIndex][wheel][slot], mtouch[carIndex][slot], carModel, madness);
                }
                if (rx[carIndex][wheel][slot] == frame) {
                    regx(wheel, magx[carIndex][wheel][slot], carModel, madness);
                }
                if (rz[carIndex][wheel][slot] == frame) {
                    regz(wheel, magz[carIndex][wheel][slot], carModel, madness);
                }
            } while (++slot < 7);
        } while (++wheel < 4);
    }

    /**
     * Copies one of the first 51 stage objects for the replay buffer. Only the
     * cars' copies are ever shown, but the original copies all 51 slots. A
     * stage with fewer objects (stage 17) has empty slots there when it is the
     * first stage of a session, and copying those used to freeze the game at
     * the start of the race. Later stages find the previous stage's objects in
     * those slots, as in the original.
     */
    private static ContO copy(ContO model) {
        return model == null ? null : new ContO(model, 0, 0, 0, 0);
    }

    public void rec(ContO carModel, int carIndex, int squashed, int lastHit, int wreckCount) {
        if (carIndex == 0) {
            caught++;
        }
        if (cntf == 50) {
            int snapshot = 0;
            do {
                car[snapshot][carIndex] = new ContO(car[snapshot + 1][carIndex], 0, 0, 0, 0);
                squash[snapshot][carIndex] = squash[snapshot + 1][carIndex];
            } while (++snapshot < 5);
            car[5][carIndex] = new ContO(carModel, 0, 0, 0, 0);
            squash[5][carIndex] = squashed;
            cntf = 0;
        } else {
            cntf++;
        }
        fix[carIndex]--;
        if (wreckCount != 0) {
            dest[carIndex]--;
        }
        if (dest[carIndex] == 230) {
            if (carIndex == 0) {
                cotchinow(0);
                whenwasted = 229;
            } else if (lastHit != 0) {
                cotchinow(carIndex);
                whenwasted = 165 + lastHit;
            }
        }
        int index = 0;
        do {
            x[index][carIndex] = x[index + 1][carIndex];
            y[index][carIndex] = y[index + 1][carIndex];
            z[index][carIndex] = z[index + 1][carIndex];
            zy[index][carIndex] = zy[index + 1][carIndex];
            xy[index][carIndex] = xy[index + 1][carIndex];
            xz[index][carIndex] = xz[index + 1][carIndex];
            wxz[index][carIndex] = wxz[index + 1][carIndex];
            wzy[index][carIndex] = wzy[index + 1][carIndex];
        } while (++index < 299);
        x[299][carIndex] = carModel.x;
        y[299][carIndex] = carModel.y;
        z[299][carIndex] = carModel.z;
        xy[299][carIndex] = carModel.xy;
        zy[299][carIndex] = carModel.zy;
        xz[299][carIndex] = carModel.xz;
        wxz[299][carIndex] = carModel.wxz;
        wzy[299][carIndex] = carModel.wzy;
        if (carIndex == 0) {
            index = 0;
            do {
                checkpoint[index] = checkpoint[index + 1];
                lastcheck[index] = lastcheck[index + 1];
            } while (++index < 299);
            checkpoint[299] = Medium.checkpoint;
            lastcheck[299] = Medium.lastcheck;
        }
        index = 0;
        do {
            if (carModel.stg[index] == 1) {
                sspark[carIndex][index][ns[carIndex][index]] = 300;
                sx[carIndex][index][ns[carIndex][index]] = carModel.sx[index];
                sy[carIndex][index][ns[carIndex][index]] = carModel.sy[index];
                sz[carIndex][index][ns[carIndex][index]] = carModel.sz[index];
                smag[carIndex][index][ns[carIndex][index]] = carModel.smag[index];
                scx[carIndex][index][ns[carIndex][index]] = carModel.scx[index];
                scz[carIndex][index][ns[carIndex][index]] = carModel.scz[index];
                fulls[carIndex][index][ns[carIndex][index]] = carModel.fulls[index];
                ns[carIndex][index]++;
                if (ns[carIndex][index] == 30) {
                    ns[carIndex][index] = 0;
                }
            }
            int slot = 0;
            do {
                sspark[carIndex][index][slot]--;
            } while (++slot < 30);
            slot = 0;
            do {
                ry[carIndex][index][slot]--;
                rx[carIndex][index][slot]--;
                rz[carIndex][index][slot]--;
            } while (++slot < 7);
        } while (++index < 4);
    }

    public void recx(int wheel, float force, int carIndex) {
        rx[carIndex][wheel][nry[carIndex][wheel]] = 300;
        magx[carIndex][wheel][nry[carIndex][wheel]] = (int) force;
        nrx[carIndex][wheel]++;
        if (nrx[carIndex][wheel] == 7) {
            nrx[carIndex][wheel] = 0;
        }
    }

    public void recy(int wheel, float force, boolean touching, int carIndex) {
        ry[carIndex][wheel][nry[carIndex][wheel]] = 300;
        magy[carIndex][wheel][nry[carIndex][wheel]] = (int) force;
        mtouch[carIndex][nry[carIndex][wheel]] = touching;
        nry[carIndex][wheel]++;
        if (nry[carIndex][wheel] == 7) {
            nry[carIndex][wheel] = 0;
        }
    }

    public void cotchinow(int carIndex) {
        if (caught >= 300) {
            wasted = carIndex;
            int index = 0;
            do {
                starcar[index] = copy(car[0][index]);
                hsquash[index] = squash[0][index];
                hfix[index] = fix[index];
                hdest[index] = dest[index];
            } while(++index < 51);
            index = 0;
            do {
                int racer = 0;
                do {
                    hx[index][racer] = x[index][racer];
                    hy[index][racer] = y[index][racer];
                    hz[index][racer] = z[index][racer];
                    hxy[index][racer] = xy[index][racer];
                    hzy[index][racer] = zy[index][racer];
                    hxz[index][racer] = xz[index][racer];
                    hwxz[index][racer] = wxz[index][racer];
                    hwzy[index][racer] = wzy[index][racer];
                } while(++racer < 51);
                hcheckpoint[index] = checkpoint[index];
                hlastcheck[index] = lastcheck[index];
            } while (++index < 300);
            index = 0;
            do {
                int wheel = 0;
                do {
                    hns[index][wheel] = ns[index][wheel];
                    int slot = 0;
                    do {
                        hsspark[index][wheel][slot] = sspark[index][wheel][slot];
                        hsx[index][wheel][slot] = sx[index][wheel][slot];
                        hsy[index][wheel][slot] = sy[index][wheel][slot];
                        hsz[index][wheel][slot] = sz[index][wheel][slot];
                        hsmag[index][wheel][slot] = smag[index][wheel][slot];
                        hscx[index][wheel][slot] = scx[index][wheel][slot];
                        hscz[index][wheel][slot] = scz[index][wheel][slot];
                        hfulls[index][wheel][slot] = fulls[index][wheel][slot];
                    } while (++slot < 30);
                } while (++wheel < 4);
            } while(++index < 51);
            index = 0;
            do {
                int wheel = 0;
                do {
                    hnry[index][wheel] = nry[index][wheel];
                    hnrx[index][wheel] = nrx[index][wheel];
                    hnrz[index][wheel] = nrz[index][wheel];
                    int slot = 0;
                    do {
                        hry[index][wheel][slot] = ry[index][wheel][slot];
                        hmagy[index][wheel][slot] = magy[index][wheel][slot];
                        hrx[index][wheel][slot] = rx[index][wheel][slot];
                        hmagx[index][wheel][slot] = magx[index][wheel][slot];
                        hrz[index][wheel][slot] = rz[index][wheel][slot];
                        hmagz[index][wheel][slot] = magz[index][wheel][slot];
                    } while (++slot < 7);
                } while (++wheel < 4);
            } while(++index < 51);
            index = 0;
            do {
                int slot = 0;
                do {
                    hmtouch[index][slot] = mtouch[index][slot];
                } while (++slot < 7);
            } while(++index < 51);
            hcaught = true;
        }
    }

    private void chipx(int wheel, float force, ContO carModel, Madness madness) {
        if (Math.abs(force) > 100F) {
            if (force > 100F) {
                force -= 100F;
            }
            if (force < -100F) {
                force += 100F;
            }
            for (int poly = 0; poly < carModel.npl; poly++) {
                float dent = 0.0F;
                for (int corner = 0; corner < carModel.p[poly].n; corner++) {
                    if (carModel.p[poly].wz == 0 && Utility.py(carModel.keyx[wheel], carModel.p[poly].ox[corner], carModel.keyz[wheel],
                            carModel.p[poly].oz[corner]) < madness.stat.clrad) {
                        dent = (force / 20F) * Medium.fxRandom();
                    }
                }

                if (dent != 0.0F && Math.abs(dent) >= 1.0F) {
                    carModel.p[poly].chip = 1;
                    carModel.p[poly].ctmag = dent;
                }
            }

        }
    }

    private void regx(int wheel, float force, ContO carModel, Madness madness) {
        if (Math.abs(force) > 100F) {
            if (force > 100F) {
                force -= 100F;
            }
            if (force < -100F) {
                force += 100F;
            }
            for (int poly = 0; poly < carModel.npl; poly++) {
                float dent = 0.0F;
                for (int corner = 0; corner < carModel.p[poly].n; corner++) {
                    if (carModel.p[poly].wz == 0 && Utility.py(carModel.keyx[wheel], carModel.p[poly].ox[corner], carModel.keyz[wheel],
                            carModel.p[poly].oz[corner]) < madness.stat.clrad) {
                        dent = (force / 20F) * Medium.fxRandom();
                        carModel.p[poly].oz[corner] -= dent * RadicalMath.sin(carModel.xz) * RadicalMath.cos(carModel.zy);
                        carModel.p[poly].ox[corner] += dent * RadicalMath.cos(carModel.xz) * RadicalMath.cos(carModel.xy);
                    }
                }

                if (dent != 0.0F) {
                    if (Math.abs(dent) >= 1.0F) {
                        carModel.p[poly].chip = 1;
                        carModel.p[poly].ctmag = dent;
                    }
                    if (!carModel.p[poly].nocol && !carModel.p[poly].glass) {
                        if (carModel.p[poly].bfase > 20 && carModel.p[poly].hsb[1] > 0.20000000000000001D) {
                            carModel.p[poly].hsb[1] = 0.2F;
                        }
                        if (carModel.p[poly].bfase > 30) {
                            if (carModel.p[poly].hsb[2] < 0.5D) {
                                carModel.p[poly].hsb[2] = 0.5F;
                            }
                            if (carModel.p[poly].hsb[1] > 0.10000000000000001D) {
                                carModel.p[poly].hsb[1] = 0.1F;
                            }
                        }
                        if (carModel.p[poly].bfase > 40) {
                            carModel.p[poly].hsb[1] = 0.05F;
                        }
                        if (carModel.p[poly].bfase > 50) {
                            if (carModel.p[poly].hsb[2] > 0.80000000000000004D) {
                                carModel.p[poly].hsb[2] = 0.8F;
                            }
                            carModel.p[poly].hsb[0] = 0.075F;
                            carModel.p[poly].hsb[1] = 0.05F;
                        }
                        if (carModel.p[poly].bfase > 60) {
                            carModel.p[poly].hsb[0] = 0.05F;
                        }
                        carModel.p[poly].bfase += Math.abs(dent);
                        new Color(carModel.p[poly].c[0], carModel.p[poly].c[1], carModel.p[poly].c[2]);
                        Color color = Color.getHSBColor(carModel.p[poly].hsb[0], carModel.p[poly].hsb[1], carModel.p[poly].hsb[2]);
                        carModel.p[poly].c[0] = color.getRed();
                        carModel.p[poly].c[1] = color.getGreen();
                        carModel.p[poly].c[2] = color.getBlue();
                    }
                    if (carModel.p[poly].glass) {
                        carModel.p[poly].gr += Math.abs(dent * 1.5D);
                    }
                }
            }

        }
    }

    public void recz(int wheel, float force, int carIndex) {
        rz[carIndex][wheel][nry[carIndex][wheel]] = 300;
        magz[carIndex][wheel][nry[carIndex][wheel]] = (int) force;
        nrz[carIndex][wheel]++;
        if (nrz[carIndex][wheel] == 7) {
            nrz[carIndex][wheel] = 0;
        }
    }
}
