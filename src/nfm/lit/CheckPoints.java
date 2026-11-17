package nfm.lit;

class CheckPoints {

    public final int[] x = new int[1400];
    public final int[] z = new int[1400];
    public final int[] y = new int[1400];
    public final int[] typ = new int[1400];
    public int pcs = 0;
    public int nsp = 0;
    public int n = 0;
    public final int[] fx = new int[5];
    public final int[] fz = new int[5];
    public final int[] fy = new int[5];
    public final boolean[] roted = new boolean[5];
    public final boolean[] special = new boolean[5];
    static boolean customTrack = false;
    static String trackname = "";
    static String trackformat = "";
    public int fn = 0;
    public int stage = 1;
    public int nlaps = 0;
    /**
     * stage name<br>
     * <b>hogan rewish</b> if none is found
     */
    public String name = "hogan rewish";
    public final int[] pos = {
           50, 50, 50, 50, 50, 50, 50, 50, 50, 50, 50, 50, 50, 50, 50, 50, 50,
           50, 50, 50, 50, 50, 50, 50, 50, 50, 50, 50, 50, 50, 50, 50, 50, 50,
           50, 50, 50, 50, 50, 50, 50, 50, 50, 50, 50, 50, 50, 50, 50, 50, 50
    };
    /**cum
     * connected to number of cars
     */
    public final int[] clear = new int[51];
    /**
     * connected to number of cars
     */
    public final int[] dested = new int[51];
    public int wasted = 0;
    public boolean haltall = false;
    public int pcleared = 0;
    public final int[] opx = new int[51];
    public final int[] opz = new int[51];
    public final int[] onscreen = new int[51];
    public final int[] omxz = new int[51];
    public int catchfin = 0;
    private int postwo = 0;

    public void checkstat(Madness cars[], ContO models[], Record record, int ncars) {
        if (!haltall) {
            pcleared = cars[0].pcleared;
            int racer = 0;
            do {
                pos[racer] = 0;
                onscreen[racer] = models[racer].dist;
                opx[racer] = models[racer].x;
                opz[racer] = models[racer].z;
                omxz[racer] = cars[racer].mxz;
                if (dested[racer] == 0) {
                    clear[racer] = cars[racer].clear;
                } else {
                    clear[racer] = -1;
                }
            } while (++racer < ncars);
            racer = 0;
            do {
                for (int other = racer + 1; other < ncars; other++) {
                    if (clear[racer] != clear[other]) {
                        if (clear[racer] < clear[other]) {
                            pos[racer]++;
                        } else {
                            pos[other]++;
                        }
                    } else {
                        int nextCheck;
                        for (nextCheck = cars[racer].pcleared + 1; typ[nextCheck] <= 0; ) {
                            if (++nextCheck == n) {
                                nextCheck = 0;
                            }
                        }

                        if (Utility.py(models[racer].x / 100, x[nextCheck] / 100, models[racer].z / 100, z[nextCheck] / 100) > Utility.py(models[other].x / 100,
                                x[nextCheck] / 100, models[other].z / 100, z[nextCheck] / 100)) {
                            pos[racer]++;
                        } else {
                            pos[other]++;
                        }
                    }
                }

            } while (++racer < ncars);
            if (stage > 2) {
                int leader = 0;
                do {
                    if (clear[leader] == nlaps * nsp && pos[leader] == 0) {
                        if (leader == 0) {
                            int other = 0;
                            do {
                                if (pos[other] == 1) {
                                    postwo = other;
                                }
                            } while (++other < ncars);
                            if (Utility.py(opx[0] / 100, opx[postwo] / 100, opz[0] / 100, opz[postwo] / 100) < 14000
                                    && clear[0] - clear[postwo] == 1) {
                                catchfin = 30;
                            }
                        } else if (pos[0] == 1 && Utility.py(opx[0] / 100, opx[leader] / 100, opz[0] / 100, opz[leader] / 100) < 14000
                                && clear[leader] - clear[0] == 1) {
                            catchfin = 30;
                            postwo = leader;
                        }
                    }
                } while (++leader < ncars);
            }
        }
        wasted = 0;
        int opponent = 1;
        do {
            if (cars[opponent].dest) {
                wasted++;
            }
        } while (++opponent < ncars);
        if (catchfin != 0) {
            catchfin--;
            if (catchfin == 0) {
                record.cotchinow(postwo);
                record.closefinish = pos[0] + 1;
            }
        }
    }
}
