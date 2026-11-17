package nfm.lit;
public class Control {

    /**
     * mute music
     */
    public static boolean mutem = false;
    /**
     * mute sound
     */
    public static boolean mutes = false;
    /** The race's dice: every decision the AI leaves to chance. */
    private final World world;
    public boolean left;
    public boolean right;
    public boolean up;
    public boolean down;
    public boolean handb;
    public int lookback;
    public boolean enter;
    public boolean arrace;
    private int pan;
    private int attack;
    private int acr;
    private boolean afta;
    private final int[] fpnt;
    private int trfix;
    private boolean forget;
    private boolean bulistc;
    private int runbul;
    private int acuracy;
    private int upwait;
    private boolean agressed;
    private float skiplev;
    private int clrnce;
    private int rampp;
    private int turntyp;
    private float aim;
    private int saftey;
    private boolean perfection;
    private float mustland;
    private boolean usebounce;
    private float trickprf;
    private int stuntf;
    public boolean zyinv;
    private boolean lastl;
    private boolean wlastl;
    private int hold;
    public int wall;
    private int lwall;
    private int stcnt;
    private int statusque;
    private int turncnt;
    private int randtcnt;
    private int upcnt;
    private int trickfase;
    private int swat;
    private boolean udcomp;
    private boolean lrcomp;
    private boolean udbare;
    private boolean lrbare;
    private boolean onceu;
    private boolean onced;
    private boolean oncel;
    private boolean oncer;
    private int lrdirect;
    private int uddirect;
    private int lrstart;
    private int udstart;
    private int oxy;
    private int ozy;
    private int flycnt;
    private boolean lrswt;
    private boolean udswt;
    private boolean gowait;
    private int actwait;
    private int cntrn;
    private int revstart;
    private int oupnt;
    private int wtz;
    private int wtx;
    private int frx;
    private int frz;
    private int frad;
    private int apunch;
    private boolean exitattack;
    private int avoidnlev;

    /**
     * This void is the entire code for the AI in NFM.
     * 
     * @author Omar Waly
     */
    public void preform(Madness madness, ContO car, CheckPoints checkpoints, Trackers trackers, int ncars) {
        left = false;
        right = false;
        up = false;
        down = false;
        handb = false;
        if (!madness.dest) {
            if (madness.mtouch) {
                if (stcnt > statusque) {
                    acuracy = (GameFacts.numberOfPlayers - checkpoints.pos[madness.im]) * checkpoints.pos[0] * (6 - checkpoints.stage * 2);
                    if (acuracy < 0) {
                        acuracy = 0;
                    }
                    clrnce = 5;
                    if (checkpoints.stage == 6 || checkpoints.stage == 11) {
                        clrnce = 2;
                    }
                    if (checkpoints.stage == 12 && (madness.pcleared == 27 || madness.pcleared == 17)) {
                        clrnce = 3;
                    }
                    if (checkpoints.stage == 16 && madness.pcleared == 33) {
                        clrnce = 3;
                    }
                    float waitScale = 0.0F;
                    if (checkpoints.stage == 1) {
                        waitScale = 1.5F;
                    }
                    if (checkpoints.stage == 2) {
                        waitScale = 1.0F;
                    }
                    if (checkpoints.stage == 3 && madness.im != 6) {
                        waitScale = 0.2F;
                    }
                    if (checkpoints.stage == 4) {
                        waitScale = 0.5F;
                    }
                    upwait = (int) ((checkpoints.pos[0] - checkpoints.pos[madness.im])
                            * (checkpoints.pos[0] - checkpoints.pos[madness.im])
                            * (checkpoints.pos[0] - checkpoints.pos[madness.im]) * waitScale);
                    if (upwait > 80) {
                        upwait = 80;
                    }
                    if (checkpoints.stage == 1 && upwait < 20) {
                        upwait = 20;
                    }
                    waitScale = 0.0F;
                    if (checkpoints.stage == 1 || checkpoints.stage == 2) {
                        waitScale = 1.0F;
                    }
                    if (checkpoints.stage == 3) {
                        waitScale = 0.5F;
                    }
                    if (checkpoints.stage == 4) {
                        waitScale = 0.5F;
                    }
                    if (checkpoints.stage == 5) {
                        waitScale = 0.2F;
                    }
                    if (checkpoints.pos[madness.im] - checkpoints.pos[0] >= -1) {
                        skiplev -= 0.10000000000000001D;
                        if (skiplev < 0.0F) {
                            skiplev = 0.0F;
                        }
                    } else {
                        skiplev += 0.20000000000000001D;
                        if (skiplev > waitScale) {
                            skiplev = waitScale;
                        }
                    }
                    if (checkpoints.stage == 8) {
                        if (madness.pcleared >= 10 && madness.pcleared <= 24) {
                            skiplev = 1.0F;
                        } else {
                            skiplev = 0.0F;
                        }
                    }
                    if (checkpoints.stage == 11) {
                        skiplev = 0.0F;
                        if (madness.pcleared == 5) {
                            skiplev = 1.0F;
                        }
                        if (madness.pcleared == 28 || madness.pcleared == 35) {
                            skiplev = 0.5F;
                        }
                    }
                    if (checkpoints.stage == 13) {
                        skiplev = 0.5F;
                    }
                    if (checkpoints.stage == 14 || checkpoints.stage == 12) {
                        skiplev = 1.0F;
                    }
                    if (checkpoints.stage == 16 || checkpoints.stage == 15 || checkpoints.stage == 10) {
                        skiplev = 0.0F;
                    }
                    rampp = (int) (world.random() * 4F - 2.0F);
                    if (madness.power == 98F) {
                        rampp = -1;
                    }
                    if (madness.power < 75F && rampp == -1) {
                        rampp = 0;
                    }
                    if (madness.power < 60F) {
                        rampp = 1;
                    }
                    if (checkpoints.stage == 8 && madness.pcleared >= 45) {
                        rampp = 2;
                    }
                    if (checkpoints.stage == 12 && madness.pcleared == 17) {
                        rampp = 2;
                    }
                    if (checkpoints.stage == 15 || checkpoints.stage == 16) {
                        rampp = 0;
                    }
                    if (cntrn == 0) {
                        agressed = false;
                        turntyp = (int) (world.random() * 4F);
                        if (checkpoints.stage == 3 && madness.im == 6) {
                            turntyp = 1;
                            if (attack == 0) {
                                agressed = true;
                            }
                        }
                        if (checkpoints.pos[0] - checkpoints.pos[madness.im] < 0) {
                            turntyp = (int) (world.random() * 2.0F);
                        }
                        if (checkpoints.stage == 8) {
                            turntyp = 2;
                        }
                        if (checkpoints.stage == 10) {
                            turntyp = 0;
                        }
                        if (checkpoints.stage == 13) {
                            turntyp = 1;
                        }
                        if (checkpoints.stage == 14) {
                            turntyp = 0;
                        }
                        if (attack != 0) {
                            turntyp = 2;
                            if (checkpoints.stage == 9 || checkpoints.stage == 11 || checkpoints.stage == 13
                                    || checkpoints.stage == 17) {
                                turntyp = (int) (world.random() * 3F);
                            }
                            if (checkpoints.stage == 16 && checkpoints.clear[madness.im] - checkpoints.clear[0] >= 5) {
                                turntyp = 0;
                            }
                        }
                        if (checkpoints.stage == 6 || checkpoints.stage == 7 || checkpoints.stage == 9
                                || checkpoints.stage == 10 || checkpoints.stage == 11 || checkpoints.stage == 12
                                || checkpoints.stage == 14 || checkpoints.stage == 16 || checkpoints.stage == 17) {
                            agressed = true;
                        }
                        cntrn = 5;
                    } else {
                        cntrn--;
                    }
                    saftey = (int) ((98F - madness.power) / 2.0F * (world.random() / 2.0F + 0.5D));
                    if (saftey > 20) {
                        saftey = 20;
                    }
                    waitScale = 0.0F;
                    if (checkpoints.stage == 1) {
                        waitScale = 0.9F;
                    }
                    if (checkpoints.stage == 2) {
                        waitScale = 0.7F;
                    }
                    if (checkpoints.stage == 4) {
                        waitScale = 0.4F;
                    }
                    mustland = waitScale + (float) (world.random() / 2.0F - 0.25D);
                    waitScale = 1.0F;
                    if (checkpoints.stage == 1) {
                        waitScale = 5F;
                    }
                    if (checkpoints.stage == 2) {
                        waitScale = 2.0F;
                    }
                    if (checkpoints.stage == 4) {
                        waitScale = 1.5F;
                    }
                    if (madness.power > 50F) {
                        if (checkpoints.pos[0] - checkpoints.pos[madness.im] > 0) {
                            saftey *= waitScale;
                        } else {
                            mustland = 0.0F;
                        }
                    } else {
                        mustland -= 0.5F;
                    }
                    if (checkpoints.stage == 8 || checkpoints.stage == 10 || checkpoints.stage == 12
                            || checkpoints.stage == 14) {
                        mustland = 0.0F;
                    }
                    stuntf = 0;
                    if (checkpoints.stage == 8 && madness.pcleared == 57) {
                        stuntf = 1;
                    }
                    if (checkpoints.stage == 9 && madness.pcleared == 3) {
                        stuntf = 2;
                    }
                    if (checkpoints.stage == 10) {
                        if (checkpoints.pos[0] < checkpoints.pos[madness.im]
                                || Math.abs(checkpoints.clear[0] - madness.clear) >= 2 || madness.clear < 2) {
                            stuntf = 4;
                            saftey = 10;
                        } else {
                            stuntf = 3;
                        }
                    }
                    if (checkpoints.stage == 11 && madness.pcleared == 21) {
                        stuntf = 1;
                    }
                    if (checkpoints.stage == 14) {
                        saftey = 10;
                        if (madness.pcleared >= 4 && madness.pcleared < 70) {
                            stuntf = 4;
                        } else if (madness.cn == 12 || madness.cn == 8) {
                            stuntf = 2;
                        }
                        if (madness.cn == 14) {
                            stuntf = 6;
                        }
                    }
                    if (checkpoints.stage == 16) {
                        mustland = 0.0F;
                        saftey = 10;
                        if ((madness.pcleared == 15 || madness.pcleared == 51)
                                && (world.random() > 0.40000000000000002D || trfix != 0)) {
                            stuntf = 7;
                        }
                        if (madness.pcleared == 42) {
                            stuntf = 1;
                        }
                        if (madness.pcleared == 77) {
                            stuntf = 7;
                        }
                        avoidnlev = (int) (2700F * world.random());
                    }
                    trickprf = (madness.power - 38F) / 50F - world.random() / 2.0F;
                    if (madness.power < 60F) {
                        trickprf = -1F;
                    }
                    if (checkpoints.stage == 3 && madness.im == 6 && trickprf > 0.69999999999999996D) {
                        trickprf = 0.7F;
                    }
                    if (checkpoints.stage == 6 && trickprf > 0.29999999999999999D) {
                        trickprf = 0.3F;
                    }
                    if (checkpoints.stage == 8 && trickprf > 0.20000000000000001D) {
                        trickprf = 0.2F;
                    }
                    if (checkpoints.stage == 9) {
                        if (trickprf > 0.5D) {
                            trickprf = 0.5F;
                        }
                        if ((madness.im == 6 || madness.im == 5) && trickprf > 0.29999999999999999D) {
                            trickprf = 0.3F;
                        }
                    }
                    if (checkpoints.stage == 11 && trickprf != -1F) {
                        trickprf *= 0.75F;
                    }
                    if (checkpoints.stage == 12 && (madness.pcleared == 55 || madness.pcleared == 7)) {
                        trickprf = -1F;
                        stuntf = 5;
                    }
                    if (checkpoints.stage == 13 && trickprf > 0.40000000000000002D) {
                        trickprf = 0.4F;
                    }
                    if (checkpoints.stage == 14 && trickprf > 0.5D) {
                        trickprf = 0.5F;
                    }
                    if (checkpoints.stage == 17) {
                        trickprf = -1F;
                    }
                    usebounce = world.random() > madness.power / 100F;
                    if (checkpoints.stage == 4 || checkpoints.stage == 6) {
                        usebounce = true;
                    }
                    if (checkpoints.stage == 10 || checkpoints.stage == 14) {
                        usebounce = false;
                    }
                    perfection = world.random() <= (float) madness.hitmag / (float) StatList.maxmag[madness.cn];
                    if ((100F * madness.hitmag) / StatList.maxmag[madness.cn] > 60F) {
                        perfection = true;
                    }
                    if (checkpoints.stage == 6 || checkpoints.stage == 8 || checkpoints.stage == 9
                            || checkpoints.stage == 10 || checkpoints.stage == 11 || checkpoints.stage == 12
                            || checkpoints.stage == 14 || checkpoints.stage == 16 || checkpoints.stage == 17) {
                        perfection = true;
                    }
                    if (attack == 0) {
                        boolean mayAttack = true;
                        if (checkpoints.stage == 3 || checkpoints.stage == 1 || checkpoints.stage == 4
                                || checkpoints.stage == 9 || checkpoints.stage == 13 || checkpoints.stage == 16) {
                            mayAttack = afta;
                        }
                        if (checkpoints.stage == 8 || checkpoints.stage == 6 || checkpoints.stage == 10
                                || checkpoints.stage == 14) {
                            mayAttack = false;
                        }
                        boolean aggressive = false;
                        if (checkpoints.stage == 3 && madness.cn == 9) {
                            aggressive = true;
                        }
                        if (checkpoints.stage == 8 && madness.cn == 11) {
                            aggressive = true;
                        }
                        if (checkpoints.stage == 9 && checkpoints.clear[0] >= 20) {
                            aggressive = true;
                        }
                        if (checkpoints.stage == 11 || checkpoints.stage == 12 || checkpoints.stage == 13
                                || checkpoints.stage == 15 || checkpoints.stage == 16) {
                            aggressive = true;
                        }
                        int minPower = 60;
                        if (checkpoints.stage == 3 || checkpoints.stage == 11 || checkpoints.stage == 17
                                || checkpoints.stage == 10 || checkpoints.stage == 8) {
                            minPower = 30;
                        }
                        if ((checkpoints.stage == 2 || checkpoints.stage == 13) && madness.cn == 13) {
                            minPower = 50;
                        }
                        if (checkpoints.stage == 4) {
                            minPower = 20;
                        }
                        if (checkpoints.stage == 5 && madness.im != 6) {
                            minPower = 40;
                        }
                        if (checkpoints.stage == 7) {
                            minPower = 40;
                        }
                        if (checkpoints.stage == 8 && madness.cn == 11) {
                            minPower = 40;
                        }
                        if (checkpoints.stage == 9 && aggressive) {
                            minPower = 30;
                        }
                        if (checkpoints.stage == 11 && bulistc) {
                            minPower = 30;
                        }
                        if (checkpoints.stage == 12) {
                            minPower = 50;
                        }
                        if (checkpoints.stage == 15 && bulistc) {
                            minPower = 40;
                        }
                        if (checkpoints.stage == 16) {
                            if (madness.cn == 11 && checkpoints.clear[0] == 27) {
                                minPower = 0;
                            }
                            if (madness.cn == 15 || madness.cn == 9) {
                                minPower = 50;
                            }
                            if (madness.cn == 11) {
                                minPower = 40;
                            }
                            if (checkpoints.pos[0] > checkpoints.pos[madness.im]) {
                                minPower = 80;
                            }
                        }
                        int rival = 0;
                        do {
                            if (rival != madness.im && checkpoints.clear[rival] != -1) {
                                int heading = car.xz;
                                if (zyinv) {
                                    heading += 180;
                                }
                                for (; heading < 0; heading += 360) {
                                }
                                for (; heading > 180; heading -= 360) {
                                }
                                char offset = '\0';
                                if (checkpoints.opx[rival] - car.x >= 0) {
                                    offset = '\264';
                                }
                                int bearing;
                                for (bearing = (int) (90 + offset + Math
                                        .atan((double) (checkpoints.opz[rival] - car.z)
                                                / (double) (checkpoints.opx[rival] - car.x))
                                        / 0.017453292519943295D); bearing < 0; bearing += 360) {
                                }
                                for (; bearing > 180; bearing -= 360) {
                                }
                                int angleOff = Math.abs(heading - bearing);
                                if (angleOff > 180) {
                                    angleOff = Math.abs(angleOff - 360);
                                }
                                int reach = 2000 * (Math.abs(checkpoints.clear[rival] - madness.clear) + 1);
                                if (checkpoints.stage == 3 && madness.cn == 9 && reach < 12000) {
                                    reach = 12000;
                                }
                                if (checkpoints.stage == 4 && reach < 4000) {
                                    reach = 4000;
                                }
                                if (checkpoints.stage == 8 && madness.cn == 11) {
                                    if (reach < 12000) {
                                        reach = 12000;
                                    }
                                    angleOff = 10;
                                }
                                if (checkpoints.stage == 9
                                        && (madness.pcleared == 13 || madness.pcleared == 33 || aggressive) && reach < 12000) {
                                    reach = 12000;
                                }
                                if (checkpoints.stage == 11) {
                                    if (bulistc) {
                                        reach = 8000;
                                        angleOff = 10;
                                        afta = true;
                                    } else if (reach < 6000) {
                                        reach = 6000;
                                    }
                                }
                                if (checkpoints.stage == 12 && bulistc) {
                                    reach = 6000;
                                    angleOff = 10;
                                }
                                if (checkpoints.stage == 13) {
                                    reach = 21000;
                                }
                                if (checkpoints.stage == 15) {
                                    reach *= Math.abs(checkpoints.clear[rival] - madness.clear) + 1;
                                    if (bulistc) {
                                        reach = 4000 * (Math.abs(checkpoints.clear[rival] - madness.clear) + 1);
                                        angleOff = 10;
                                    }
                                }
                                if (checkpoints.stage == 10) {
                                    reach = 16000;
                                }
                                if (checkpoints.stage == 16) {
                                    if (madness.cn == 13 && bulistc) {
                                        if (oupnt == 33) {
                                            reach = 17000;
                                        }
                                        if (oupnt == 51) {
                                            reach = 30000;
                                        }
                                        if (oupnt == 15 && checkpoints.clear[0] >= 14) {
                                            reach = 60000;
                                        }
                                        angleOff = 10;
                                    }
                                    if (madness.cn == 15 || madness.cn == 9) {
                                        reach *= Math.abs(checkpoints.clear[rival] - madness.clear) + 1;
                                    }
                                    if (madness.cn == 11) {
                                        reach = 4000 * (Math.abs(checkpoints.clear[rival] - madness.clear) + 1);
                                    }
                                }
                                int maxAngle = 85 + 15 * (Math.abs(checkpoints.clear[rival] - madness.clear) + 1);
                                if (checkpoints.stage == 13) {
                                    maxAngle = 45;
                                }
                                if (checkpoints.stage == 16 && (madness.cn == 15 || madness.cn == 9 || madness.cn == 11
                                        || madness.cn == 14)) {
                                    maxAngle = 50 + 70 * Math.abs(checkpoints.clear[rival] - madness.clear);
                                }
                                if (angleOff < maxAngle && Utility.py(car.x / 100, checkpoints.opx[rival] / 100, car.z / 100,
                                        checkpoints.opz[rival] / 100) < reach && afta && madness.power > minPower) {
                                    float spread = 35 - Math.abs(checkpoints.clear[rival] - madness.clear) * 10;
                                    if (spread < 1.0F) {
                                        spread = 1.0F;
                                    }
                                    float chance = (checkpoints.pos[madness.im] + 1) * (5 - checkpoints.pos[rival])
                                            / spread;
                                    if (checkpoints.stage != 17 && chance > 0.69999999999999996D) {
                                        chance = 0.7F;
                                    }
                                    if (rival != 0 && checkpoints.pos[0] < checkpoints.pos[madness.im]) {
                                        chance = 0.0F;
                                    }
                                    if (rival != 0 && aggressive) {
                                        chance = 0.0F;
                                    }
                                    if (checkpoints.stage == 3) {
                                        if (madness.cn == 9 || madness.cn == 13 && bulistc) {
                                            chance *= 2.0F;
                                        } else {
                                            chance *= 0.5F;
                                        }
                                    }
                                    if (checkpoints.stage == 6) {
                                        chance = 0.0F;
                                    }
                                    if (checkpoints.stage == 7 && madness.im == 6 && rival == 0) {
                                        chance = (float) (chance * 1.5D);
                                    }
                                    if (checkpoints.stage == 8) {
                                        if (madness.cn == 11 || madness.cn == 13 && bulistc) {
                                            chance *= 1.5F;
                                        } else {
                                            chance = 0.0F;
                                        }
                                    }
                                    if (checkpoints.stage == 9) {
                                        if (rival != 0) {
                                            chance = (float) (chance * 0.5D);
                                        }
                                        if (madness.pcleared != 13 && madness.pcleared != 33 && !aggressive) {
                                            chance *= 0.5F;
                                        }
                                        if ((madness.im == 6 || madness.im == 5) && rival != 0) {
                                            chance = 0.0F;
                                        }
                                    }
                                    if (checkpoints.stage == 10 && !bulistc) {
                                        chance = 0.0F;
                                    }
                                    if (checkpoints.stage == 11 && bulistc && rival == 0) {
                                        chance = 1.0F;
                                    }
                                    if (checkpoints.stage == 12) {
                                        if (madness.cn != 11 && madness.cn != 13) {
                                            chance = 0.0F;
                                        }
                                        if (madness.cn == 13 && rival == 0) {
                                            chance = 1.0F;
                                        }
                                    }
                                    if (checkpoints.stage == 14) {
                                        chance = 0.0F;
                                    }
                                    if (checkpoints.stage == 15) {
                                        if (checkpoints.pos[madness.im] == 0) {
                                            chance = (float) (chance * 0.5D);
                                        }
                                        if (checkpoints.pos[0] < checkpoints.pos[madness.im]) {
                                            chance *= 2.0F;
                                        }
                                        if (bulistc && rival == 0) {
                                            chance = 1.0F;
                                        }
                                    }
                                    if (checkpoints.stage == 16) {
                                        if (madness.cn != 14) {
                                            if (checkpoints.pos[0] < checkpoints.pos[madness.im]
                                                    && checkpoints.clear[0] - checkpoints.clear[madness.im] != 1) {
                                                chance *= 2.0F;
                                            }
                                        } else {
                                            chance = (float) (chance * 0.5D);
                                        }
                                        if (madness.cn == 13 && rival == 0) {
                                            chance = 1.0F;
                                        }
                                        if (checkpoints.pos[madness.im] == 0
                                                || checkpoints.pos[madness.im] == 1 && checkpoints.pos[0] == 0) {
                                            chance = 0.0F;
                                        }
                                        if (checkpoints.clear[madness.im] - checkpoints.clear[0] >= 5 && rival == 0) {
                                            chance = 1.0F;
                                        }
                                        if (madness.cn == 10 || madness.cn == 12) {
                                            chance = 0.0F;
                                        }
                                    }
                                    if (world.random() < chance) {
                                        attack = 40 * (Math.abs(checkpoints.clear[rival] - madness.clear) + 1);
                                        if (attack > 500) {
                                            attack = 500;
                                        }
                                        aim = 0.0F;
                                        if (checkpoints.stage == 3 && madness.im == 6 && world.random() > world.random()) {
                                            aim = 1.0F;
                                        }
                                        if (checkpoints.stage == 4) {
                                            if (rival == 0 && checkpoints.pos[0] < checkpoints.pos[madness.im]) {
                                                aim = 1.5F;
                                            } else {
                                                aim = world.random();
                                            }
                                        }
                                        if (checkpoints.stage == 5) {
                                            aim = world.random() * 1.5F;
                                        }
                                        if (checkpoints.stage == 7 && madness.im != 6 && (world.random() > world.random()
                                                || checkpoints.pos[0] < checkpoints.pos[madness.im])) {
                                            aim = 1.0F;
                                        }
                                        if (checkpoints.stage == 8 && madness.cn == 11 && world.random() > world.random()) {
                                            aim = 0.76F + world.random() * 0.76F;
                                        }
                                        if (checkpoints.stage == 9
                                                && (madness.pcleared == 13 || madness.pcleared == 33)) {
                                            aim = 1.0F;
                                        }
                                        if (checkpoints.stage == 11) {
                                            if (bulistc) {
                                                aim = 0.7F;
                                                if (attack > 150) {
                                                    attack = 150;
                                                }
                                            } else {
                                                aim = world.random();
                                            }
                                        }
                                        if (checkpoints.stage == 12) {
                                            if (world.random() > world.random()) {
                                                aim = 0.7F;
                                            }
                                            if (bulistc && attack > 150) {
                                                attack = 150;
                                            }
                                        }
                                        if (checkpoints.stage == 13 && attack > 60) {
                                            attack = 60;
                                        }
                                        if (checkpoints.stage == 15) {
                                            aim = world.random() * 1.5F;
                                            attack = attack / 2;
                                            exitattack = world.random() > world.random();
                                        }
                                        if (checkpoints.stage == 16) {
                                            if (madness.cn == 13) {
                                                aim = 0.76F;
                                                attack = 150;
                                            } else {
                                                aim = world.random() * 1.5F;
                                                if (Math.abs(checkpoints.clear[rival] - madness.clear) <= 2
                                                        || madness.cn == 14) {
                                                    attack = attack / 3;
                                                }
                                            }
                                        }
                                        acr = rival;
                                        turntyp = (int) (1.0F + world.random() * 2.0F);
                                    }
                                }
                                if (mayAttack && angleOff > 100
                                        && Utility.py(car.x / 100, checkpoints.opx[rival] / 100, car.z / 100,
                                        checkpoints.opz[rival] / 100) < 300
                                        && world.random() > 0.59999999999999998D
                                        - checkpoints.pos[madness.im] / 10F) {
                                    clrnce = 0;
                                    acuracy = 0;
                                }
                            }
                        } while (++rival < ncars);
                    }
                    boolean skipFix = false;
                    if (checkpoints.stage == 6 || checkpoints.stage == 10 || checkpoints.stage == 11
                            || checkpoints.stage == 17) {
                        skipFix = true;
                    }
                    if (checkpoints.stage == 8 && madness.pcleared != 73) {
                        skipFix = true;
                    }
                    if (trfix != 3) {
                        trfix = 0;
                        int fixDamage = 50;
                        if (checkpoints.stage == 16) {
                            fixDamage = 40;
                        }
                        if ((100F * madness.hitmag) / madness.stat.maxmag > fixDamage) {
                            trfix = 1;
                        }
                        if (!skipFix) {
                            int bigDamage = 80;
                            if (checkpoints.stage == 8 && madness.cn != 11) {
                                bigDamage = 50;
                            }
                            if (checkpoints.stage == 9) {
                                bigDamage = 70;
                            }
                            if (checkpoints.stage == 15 && madness.pcleared == 91) {
                                bigDamage = 50;
                            }
                            if (checkpoints.stage == 16 && checkpoints.clear[madness.im] - checkpoints.clear[0] >= 5
                                    && madness.cn != 10 && madness.cn != 12) {
                                bigDamage = 50;
                            }
                            if ((100F * madness.hitmag) / StatList.maxmag[madness.cn] > bigDamage) {
                                trfix = 2;
                            }
                        }
                    } else {
                        upwait = 0;
                        acuracy = 0;
                        skiplev = 1.0F;
                        clrnce = 2;
                    }
                    if (!bulistc) {
                        if (checkpoints.stage == 8 && madness.cn == 11 && madness.pcleared == 35) {
                            madness.pcleared = 73;
                            madness.clear = 0;
                            bulistc = true;
                            runbul = (int) (100F * world.random());
                        }
                        if (checkpoints.stage == 11 && madness.cn == 13) {
                            bulistc = true;
                        }
                        if (checkpoints.stage == 12 && madness.cn == 13) {
                            bulistc = true;
                        }
                        if (checkpoints.stage == 15 && checkpoints.clear[0] - madness.clear >= 3 && trfix == 0) {
                            bulistc = true;
                            oupnt = -1;
                        }
                        if (checkpoints.stage == 16) {
                            if (madness.cn == 13 && checkpoints.pcleared == 8) {
                                bulistc = true;
                                attack = 0;
                            }
                            if (madness.cn == 11 && checkpoints.clear[0] - madness.clear >= 2 && trfix == 0) {
                                bulistc = true;
                                oupnt = -1;
                            }
                        }
                        if ((checkpoints.stage == 2 || checkpoints.stage == 3 || checkpoints.stage == 4
                                || checkpoints.stage == 5 || checkpoints.stage == 8 || checkpoints.stage == 10
                                || checkpoints.stage == 13) && madness.cn == 13
                                && Math.abs(checkpoints.clear[0] - madness.clear) >= 2) {
                            bulistc = true;
                        }
                    } else if (checkpoints.stage == 8) {
                        runbul--;
                        if (madness.pcleared == 10) {
                            runbul = 0;
                        }
                        if (runbul <= 0) {
                            bulistc = false;
                        }
                    }
                    stcnt = 0;
                    statusque = (int) (20F * world.random());
                } else {
                    stcnt++;
                }
            }
            boolean grounded;
            if (usebounce) {
                grounded = madness.wtouch;
            } else {
                grounded = madness.mtouch;
            }
            if (grounded) {
                if (trickfase != 0) {
                    trickfase = 0;
                }
                if (trfix == 2 || trfix == 3) {
                    attack = 0;
                }
                if (attack == 0) {
                    if (upcnt < 30) {
                        if (revstart <= 0) {
                            up = true;
                        } else {
                            down = true;
                            revstart--;
                        }
                    }
                    if (upcnt < 25 + actwait) {
                        upcnt++;
                    } else {
                        upcnt = 0;
                        actwait = upwait;
                    }
                    int target = madness.point;
                    int chasePower = 50;
                    if (checkpoints.stage == 8) {
                        chasePower = 20;
                    }
                    if (checkpoints.stage == 15) {
                        chasePower = 40;
                    }
                    if (checkpoints.stage == 16) {
                        chasePower = 20;
                    }
                    if (!bulistc || trfix == 2 || trfix == 3 || trfix == 4 || madness.power < chasePower) {
                        if (rampp == 1 && checkpoints.typ[target] <= 0) {
                            int nextPoint = target + 1;
                            if (nextPoint == checkpoints.n) {
                                nextPoint = 0;
                            }
                            if (checkpoints.typ[nextPoint] == -2) {
                                target = nextPoint;
                            }
                        }
                        if (rampp == -1 && checkpoints.typ[target] == -2 && ++target == checkpoints.n) {
                            target = 0;
                        }
                        if (world.random() > skiplev) {
                            int ahead = target;
                            boolean notYet = false;
                            if (checkpoints.typ[ahead] > 0) {
                                int before = 0;
                                for (int point = 0; point < checkpoints.n; point++) {
                                    if (checkpoints.typ[point] > 0 && point < ahead) {
                                        before++;
                                    }
                                }

                                notYet = madness.clear != before + madness.nlaps * checkpoints.nsp;
                            }
                            while (checkpoints.typ[ahead] == 0 || checkpoints.typ[ahead] == -1 || checkpoints.typ[ahead] == -3
                                    || notYet) {
                                target = ahead;
                                if (++ahead == checkpoints.n) {
                                    ahead = 0;
                                }
                                notYet = false;
                                if (checkpoints.typ[ahead] > 0) {
                                    int before = 0;
                                    for (int point = 0; point < checkpoints.n; point++) {
                                        if (checkpoints.typ[point] > 0 && point < ahead) {
                                            before++;
                                        }
                                    }

                                    notYet = madness.clear != before + madness.nlaps * checkpoints.nsp;
                                }
                            }
                        } else if (world.random() > skiplev) {
                            while (checkpoints.typ[target] == -1) {
                                if (++target == checkpoints.n) {
                                    target = 0;
                                }
                            }
                        }
                        if (checkpoints.stage == 8 && madness.pcleared == 73 && trfix == 0 && madness.clear != 0) {
                            target = 10;
                        }
                        if (checkpoints.stage == 9 && madness.pcleared == 18 && trfix == 0) {
                            target = 27;
                        }
                        if (checkpoints.stage == 11) {
                            if (madness.pcleared == 5 && trfix == 0 && madness.power < 70F) {
                                if (target <= 16) {
                                    target = 16;
                                } else {
                                    target = 21;
                                }
                            }
                            if (madness.pcleared == 50) {
                                target = 57;
                            }
                        }
                        if (checkpoints.stage == 12 && (madness.pcleared == 27 || madness.pcleared == 37)) {
                            while (checkpoints.typ[target] == -1) {
                                if (++target == checkpoints.n) {
                                    target = 0;
                                }
                            }
                        }
                        if (checkpoints.stage == 13) {
                            while (checkpoints.typ[target] == -1) {
                                if (++target == checkpoints.n) {
                                    target = 0;
                                }
                            }
                        }
                        if (checkpoints.stage == 14) {
                            while (checkpoints.typ[target] == -1) {
                                if (++target == checkpoints.n) {
                                    target = 0;
                                }
                            }
                            if (!madness.gtouch) {
                                while (checkpoints.typ[target] == -2) {
                                    if (++target == checkpoints.n) {
                                        target = 0;
                                    }
                                }
                            }
                            if (oupnt >= 68) {
                                target = 70;
                            } else {
                                oupnt = target;
                            }
                        }
                        if (checkpoints.stage == 15) {
                            if (madness.pcleared != 91 && checkpoints.pos[0] < checkpoints.pos[madness.im]
                                    && madness.cn != 13
                                    || checkpoints.pos[madness.im] == 0
                                    && (madness.clear == 12 || madness.clear == 20)) {
                                while (checkpoints.typ[target] == -4) {
                                    if (++target == checkpoints.n) {
                                        target = 0;
                                    }
                                }
                            }
                            if (madness.pcleared == 9) {
                                if (Utility.py(car.x / 100, 297, car.z / 100, 347) < 400) {
                                    oupnt = 1;
                                }
                                if (oupnt == 1 && target < 22) {
                                    target = 22;
                                }
                            }
                            if (madness.pcleared == 67) {
                                if (Utility.py(car.x / 100, 28, car.z / 100, 494) < 4000) {
                                    oupnt = 2;
                                }
                                if (oupnt == 2) {
                                    target = 76;
                                }
                            }
                            if (madness.pcleared == 76) {
                                if (Utility.py(car.x / 100, -50, car.z / 100, 0) < 2000) {
                                    oupnt = 3;
                                }
                                if (oupnt == 3) {
                                    target = 91;
                                } else {
                                    target = 89;
                                }
                            }
                        }
                        if (checkpoints.stage == 16) {
                            if (madness.pcleared == 128) {
                                if (Utility.py(car.x / 100, 0, car.z / 100, 229) < 1500 || car.z > 23000) {
                                    oupnt = 128;
                                }
                                if (oupnt != 128) {
                                    target = 3;
                                }
                            }
                            if (madness.pcleared == 8) {
                                if (Utility.py(car.x / 100, -207, car.z / 100, 549) < 1500 || car.x < -20700) {
                                    oupnt = 8;
                                }
                                if (oupnt != 8) {
                                    target = 12;
                                }
                            }
                            if (madness.pcleared == 33) {
                                if (Utility.py(car.x / 100, -60, car.z / 100, 168) < 250 || car.z > 17000) {
                                    oupnt = 331;
                                }
                                if (Utility.py(car.x / 100, -112, car.z / 100, 414) < 10000 || car.z > 40000) {
                                    oupnt = 332;
                                }
                                if (oupnt != 331 && oupnt != 332) {
                                    if (trfix != 1) {
                                        target = 38;
                                    } else {
                                        target = 39;
                                    }
                                }
                                if (oupnt == 331) {
                                    target = 71;
                                }
                            }
                            if (madness.pcleared == 42) {
                                if (Utility.py(car.x / 100, -269, car.z / 100, 493) < 100 || car.x < -27000) {
                                    oupnt = 142;
                                }
                                if (oupnt != 142) {
                                    target = 47;
                                }
                            }
                            if (madness.pcleared == 51) {
                                if (Utility.py(car.x / 100, -352, car.z / 100, 260) < 100 || car.z < 25000) {
                                    oupnt = 511;
                                }
                                if (Utility.py(car.x / 100, -325, car.z / 100, 10) < 2000 || car.x > -32000) {
                                    oupnt = 512;
                                }
                                if (oupnt != 511 && oupnt != 512) {
                                    target = 80;
                                }
                                if (oupnt == 511) {
                                    target = 61;
                                }
                            }
                            if (madness.pcleared == 77) {
                                if (Utility.py(car.x / 100, -371, car.z / 100, 319) < 100 || car.z < 31000) {
                                    oupnt = 77;
                                }
                                if (oupnt != 77) {
                                    target = 78;
                                    madness.nofocus = true;
                                }
                            }
                            if (madness.pcleared == 105) {
                                if (Utility.py(car.x / 100, -179, car.z / 100, 10) < 2300 || car.z < 1050) {
                                    oupnt = 105;
                                }
                                if (oupnt != 105) {
                                    target = 65;
                                } else {
                                    target = 125;
                                }
                            }
                            if (trfix == 3) {
                                if (Utility.py(car.x / 100, -52, car.z / 100, 448) < 100 || car.z > 45000) {
                                    oupnt = 176;
                                }
                                if (oupnt != 176) {
                                    target = 41;
                                } else {
                                    target = 43;
                                }
                            }
                            if (checkpoints.clear[madness.im] - checkpoints.clear[0] >= 2
                                    && Utility.py(car.x / 100, checkpoints.opx[0] / 100, car.z / 100,
                                    checkpoints.opz[0] / 100) < 1000 + avoidnlev) {
                                int heading = car.xz;
                                if (zyinv) {
                                    heading += 180;
                                }
                                for (; heading < 0; heading += 360) {
                                }
                                for (; heading > 180; heading -= 360) {
                                }
                                char offset = '\0';
                                if (checkpoints.opx[0] - car.x >= 0) {
                                    offset = '\264';
                                }
                                int bearing;
                                for (bearing = (int) (90 + offset + Math
                                        .atan((double) (checkpoints.opz[0] - car.z)
                                                / (double) (checkpoints.opx[0] - car.x))
                                        / 0.017453292519943295D); bearing < 0; bearing += 360) {
                                }
                                for (; bearing > 180; bearing -= 360) {
                                }
                                int angleOff = Math.abs(heading - bearing);
                                if (angleOff > 180) {
                                    angleOff = Math.abs(angleOff - 360);
                                }
                                if (angleOff < 90) {
                                    wall = 0;
                                }
                            }
                        }
                        if (rampp == 2) {
                            int nextPoint = target + 1;
                            if (nextPoint == checkpoints.n) {
                                nextPoint = 0;
                            }
                            if (checkpoints.typ[nextPoint] == -2 && target != madness.point && --target < 0) {
                                target += checkpoints.n;
                            }
                        }
                        if (bulistc) {
                            madness.nofocus = true;
                            if (gowait) {
                                gowait = false;
                            }
                        }
                    } else {
                        if (checkpoints.stage != 15 && checkpoints.stage != 16 || runbul == 0) {
                            if ((target -= 2) < 0) {
                                target += checkpoints.n;
                            }
                            while (checkpoints.typ[target] == -4) {
                                if (--target < 0) {
                                    target += checkpoints.n;
                                }
                            }
                        }
                        if (checkpoints.stage == 11) {
                            if (target >= 14 && target <= 19) {
                                target = 13;
                            }
                            if (oupnt == 72 && target != 56) {
                                target = 57;
                            } else if (oupnt == 54 && target != 52) {
                                target = 53;
                            } else if (oupnt == 39 && target != 37) {
                                target = 38;
                            } else {
                                oupnt = target;
                            }
                        }
                        if (checkpoints.stage == 12) {
                            if (!gowait) {
                                if (checkpoints.clear[0] == 0) {
                                    wtx = -3500;
                                    wtz = 19000;
                                    frx = -3500;
                                    frz = 39000;
                                    frad = 12000;
                                    oupnt = 37;
                                    gowait = true;
                                    afta = false;
                                }
                                if (checkpoints.clear[0] == 7) {
                                    wtx = -44800;
                                    wtz = 40320;
                                    frx = -44800;
                                    frz = 34720;
                                    frad = 30000;
                                    oupnt = 27;
                                    gowait = true;
                                    afta = false;
                                }
                                if (checkpoints.clear[0] == 10) {
                                    wtx = 0;
                                    wtz = 48739;
                                    frx = 0;
                                    frz = 38589;
                                    frad = 0x15f90;
                                    oupnt = 55;
                                    gowait = true;
                                    afta = false;
                                }
                                if (checkpoints.clear[0] == 14) {
                                    wtx = -3500;
                                    wtz = 19000;
                                    frx = -14700;
                                    frz = 39000;
                                    frad = 45000;
                                    oupnt = 37;
                                    gowait = true;
                                    afta = false;
                                }
                                if (checkpoints.clear[0] == 18) {
                                    wtx = -48300;
                                    wtz = -4550;
                                    frx = -48300;
                                    frz = 5600;
                                    frad = 0x15f90;
                                    oupnt = 17;
                                    gowait = true;
                                    afta = false;
                                }
                            }
                            if (gowait) {
                                if (Utility.py(car.x / 100, wtx / 100, car.z / 100, wtz / 100) < 10000
                                        && madness.speed > 50F) {
                                    up = false;
                                }
                                if (Utility.py(car.x / 100, wtx / 100, car.z / 100, wtz / 100) < 200) {
                                    up = false;
                                    handb = true;
                                }
                                if (checkpoints.pcleared == oupnt && Utility.py(checkpoints.opx[0] / 100, frx / 100,
                                        checkpoints.opz[0] / 100, frz / 100) < frad) {
                                    afta = true;
                                    gowait = false;
                                }
                                if (Utility.py(car.x / 100, checkpoints.opx[0] / 100, car.z / 100,
                                        checkpoints.opz[0] / 100) < 25) {
                                    afta = true;
                                    gowait = false;
                                    attack = 200;
                                    acr = 0;
                                }
                            }
                        }
                        if (checkpoints.stage == 15) {
                            if (oupnt == -1) {
                                int bestDistance = -10;
                                for (int point = 0; point < checkpoints.n; point++) {
                                    if ((checkpoints.typ[point] == -2 || checkpoints.typ[point] == -4) && (point < 50 || point > 54)
                                            && (Utility.py(car.x / 100, checkpoints.x[point] / 100, car.z / 100,
                                            checkpoints.z[point] / 100) < bestDistance || bestDistance == -10)) {
                                        bestDistance = Utility.py(car.x / 100, checkpoints.x[point] / 100, car.z / 100,
                                                checkpoints.z[point] / 100);
                                        oupnt = point;
                                    }
                                }

                                oupnt--;
                                if (target < 0) {
                                    oupnt += checkpoints.n;
                                }
                            }
                            if (oupnt >= 0 && oupnt < checkpoints.n) {
                                target = oupnt;
                                if (Utility.py(car.x / 100, checkpoints.x[target] / 100, car.z / 100,
                                        checkpoints.z[target] / 100) < 800) {
                                    oupnt = -(int) (75F + world.random() * 200F);
                                    runbul = (int) (50F + world.random() * 100F);
                                }
                            }
                            if (oupnt < -1) {
                                oupnt++;
                            }
                            if (runbul != 0) {
                                runbul--;
                            }
                        }
                        if (checkpoints.stage == 16) {
                            boolean roam = false;
                            if (madness.cn == 13) {
                                if (!gowait) {
                                    if (checkpoints.clear[0] == 1) {
                                        if (world.random() > 0.5D) {
                                            wtx = -14000;
                                            wtz = 48000;
                                            frx = -5600;
                                            frz = 47600;
                                            frad = 0x157c0;
                                            oupnt = 33;
                                        } else {
                                            wtx = -5600;
                                            wtz = 8000;
                                            frx = -7350;
                                            frz = -4550;
                                            frad = 22000;
                                            oupnt = 15;
                                        }
                                        gowait = true;
                                        afta = false;
                                    }
                                    if (checkpoints.clear[0] == 4) {
                                        wtx = -12700;
                                        wtz = 14000;
                                        frx = -31000;
                                        frz = 1050;
                                        frad = 11000;
                                        oupnt = 51;
                                        gowait = true;
                                        afta = false;
                                    }
                                    if (checkpoints.clear[0] == 14) {
                                        wtx = -35350;
                                        wtz = 6650;
                                        frx = -48300;
                                        frz = 54950;
                                        frad = 11000;
                                        oupnt = 15;
                                        gowait = true;
                                        afta = false;
                                    }
                                    if (checkpoints.clear[0] == 17) {
                                        wtx = -42700;
                                        wtz = 41000;
                                        frx = -40950;
                                        frz = 49350;
                                        frad = 7000;
                                        oupnt = 42;
                                        gowait = true;
                                        afta = false;
                                    }
                                    if (checkpoints.clear[0] == 21) {
                                        wtx = -1750;
                                        wtz = -15750;
                                        frx = -25900;
                                        frz = -14000;
                                        frad = 11000;
                                        oupnt = 125;
                                        gowait = true;
                                        afta = false;
                                    }
                                }
                                if (gowait) {
                                    if (Utility.py(car.x / 100, wtx / 100, car.z / 100, wtz / 100) < 10000
                                            && madness.speed > 50F) {
                                        up = false;
                                    }
                                    if (Utility.py(car.x / 100, wtx / 100, car.z / 100, wtz / 100) < 200) {
                                        up = false;
                                        handb = true;
                                    }
                                    if (checkpoints.pcleared == oupnt && Utility.py(checkpoints.opx[0] / 100, frx / 100,
                                            checkpoints.opz[0] / 100, frz / 100) < frad) {
                                        runbul = 0;
                                        afta = true;
                                        gowait = false;
                                    }
                                    if (Utility.py(car.x / 100, checkpoints.opx[0] / 100, car.z / 100,
                                            checkpoints.opz[0] / 100) < 25) {
                                        afta = true;
                                        gowait = false;
                                        attack = 200;
                                        acr = 0;
                                    }
                                    if (checkpoints.clear[0] == 21 && oupnt != 125) {
                                        gowait = false;
                                    }
                                }
                                if (checkpoints.clear[0] >= 11 && !gowait
                                        || madness.power < 60F && checkpoints.clear[0] < 21) {
                                    roam = true;
                                    if (!exitattack) {
                                        oupnt = -1;
                                        exitattack = true;
                                    }
                                } else if (exitattack) {
                                    exitattack = false;
                                }
                            }
                            if (madness.cn == 11) {
                                roam = true;
                            }
                            if (roam) {
                                if (oupnt == -1) {
                                    int bestDistance = -10;
                                    for (int point = 0; point < checkpoints.n; point++) {
                                        if (checkpoints.typ[point] == -4 && (Utility.py(car.x / 100, checkpoints.x[point] / 100,
                                                car.z / 100, checkpoints.z[point] / 100) < bestDistance
                                                && world.random() > 0.59999999999999998D || bestDistance == -10)) {
                                            bestDistance = Utility.py(car.x / 100, checkpoints.x[point] / 100, car.z / 100,
                                                    checkpoints.z[point] / 100);
                                            oupnt = point;
                                        }
                                    }

                                    oupnt--;
                                    if (target < 0) {
                                        oupnt += checkpoints.n;
                                    }
                                }
                                if (oupnt >= 0 && oupnt < checkpoints.n) {
                                    target = oupnt;
                                    if (Utility.py(car.x / 100, checkpoints.x[target] / 100, car.z / 100,
                                            checkpoints.z[target] / 100) < 800) {
                                        oupnt = -(int) (75F + world.random() * 200F);
                                        runbul = (int) (50F + world.random() * 100F);
                                    }
                                }
                                if (oupnt < -1) {
                                    oupnt++;
                                }
                                if (runbul != 0) {
                                    runbul--;
                                }
                            }
                        }
                        madness.nofocus = true;
                    }
                    if (checkpoints.stage != 17) {
                        if (checkpoints.stage == 9 || checkpoints.stage == 8 && madness.pcleared == 73
                                || checkpoints.stage == 16) {
                            forget = true;
                        }
                        if ((madness.missedcp == 0 || forget || trfix == 4) && trfix != 0) {
                            byte firstFixer = 0;
                            if (checkpoints.stage == 15 || checkpoints.stage == 16) {
                                firstFixer = 3;
                            }
                            if (trfix == 2) {
                                int bestDistance = -10;
                                int bestFixer = 0;
                                for (int fixer = firstFixer; fixer < checkpoints.fn; fixer++) {
                                    if (Utility.py(car.x / 100, checkpoints.x[fpnt[fixer]] / 100, car.z / 100,
                                            checkpoints.z[fpnt[fixer]] / 100) < bestDistance || bestDistance == -10) {
                                        bestDistance = Utility.py(car.x / 100, checkpoints.x[fpnt[fixer]] / 100, car.z / 100,
                                                checkpoints.z[fpnt[fixer]] / 100);
                                        bestFixer = fixer;
                                    }
                                }

                                if (checkpoints.stage == 8 || checkpoints.stage == 12) {
                                    bestFixer = 1;
                                }
                                target = fpnt[bestFixer];
                                forget = checkpoints.special[bestFixer];
                            }
                            for (int fixer = firstFixer; fixer < checkpoints.fn; fixer++) {
                                if (Utility.py(car.x / 100, checkpoints.x[fpnt[fixer]] / 100, car.z / 100,
                                        checkpoints.z[fpnt[fixer]] / 100) < 2000) {
                                    forget = false;
                                    actwait = 0;
                                    upwait = 0;
                                    turntyp = 2;
                                    randtcnt = -1;
                                    acuracy = 0;
                                    rampp = 0;
                                    trfix = 3;
                                }
                            }

                            if (trfix == 3) {
                                madness.nofocus = true;
                            }
                        }
                    }
                    if (turncnt > randtcnt) {
                        if (!gowait) {
                            char offset = '\0';
                            if (checkpoints.x[target] - car.x >= 0) {
                                offset = '\264';
                            }
                            pan = (int) (90 + offset + Math
                                    .atan((double) (checkpoints.z[target] - car.z) / (double) (checkpoints.x[target] - car.x))
                                    / 0.017453292519943295D);
                        } else {
                            char offset = '\0';
                            if (wtx - car.x >= 0) {
                                offset = '\264';
                            }
                            pan = (int) (90 + offset
                                    + StrictMath.atan((double) (wtz - car.z) / (double) (wtx - car.x))
                                    / 0.017453292519943295D);
                        }
                        turncnt = 0;
                        randtcnt = (int) (acuracy * world.random());
                    } else {
                        turncnt++;
                    }
                } else {
                    up = true;
                    char offset = '\0';
                    int lead = (int) ((Utility.pys(car.x, checkpoints.opx[acr], car.z, checkpoints.opz[acr]) / 2.0F)
                            * aim);
                    int aimX = (int) (checkpoints.opx[acr] - lead * RadicalMath.sin(checkpoints.omxz[acr]));
                    int aimZ = (int) (checkpoints.opz[acr] + lead * RadicalMath.cos(checkpoints.omxz[acr]));
                    if (aimX - car.x >= 0) {
                        offset = '\264';
                    }
                    pan = (int) (90 + offset
                            + StrictMath.atan((double) (aimZ - car.z) / (double) (aimX - car.x)) / 0.017453292519943295D);
                    attack--;
                    if (attack <= 0) {
                        attack = 0;
                    }
                    if (checkpoints.stage == 15 && exitattack && !bulistc && madness.missedcp != 0) {
                        attack = 0;
                    }
                    if (checkpoints.stage == 16 && madness.cn == 13 && (checkpoints.clear[0] == 4
                            || checkpoints.clear[0] == 13 || checkpoints.clear[0] == 21)) {
                        attack = 0;
                    }
                    if (checkpoints.stage == 16 && madness.missedcp != 0 && (checkpoints.pos[madness.im] == 0
                            || checkpoints.pos[madness.im] == 1 && checkpoints.pos[0] == 0)) {
                        attack = 0;
                    }
                    if (checkpoints.stage == 16 && checkpoints.pos[0] > checkpoints.pos[madness.im]
                            && madness.power < 80F) {
                        attack = 0;
                    }
                }
                int heading = car.xz;
                if (zyinv) {
                    heading += 180;
                }
                for (; heading < 0; heading += 360) {
                }
                for (; heading > 180; heading -= 360) {
                }
                for (; pan < 0; pan += 360) {
                }
                for (; pan > 180; pan -= 360) {
                }
                if (wall != -1 && hold == 0) {
                    clrnce = 0;
                }
                if (hold == 0) {
                    if (Math.abs(heading - pan) < 180) {
                        if (Math.abs(heading - pan) > clrnce) {
                            if (heading < pan) {
                                left = true;
                                lastl = true;
                            } else {
                                right = true;
                                lastl = false;
                            }
                            if (Math.abs(heading - pan) > 50 && madness.speed > StatList.swits[madness.cn][0]
                                    && turntyp != 0) {
                                if (turntyp == 1) {
                                    down = true;
                                }
                                if (turntyp == 2) {
                                    handb = true;
                                }
                                if (!agressed) {
                                    up = false;
                                }
                            }
                        }
                    } else if (Math.abs(heading - pan) < 360 - clrnce) {
                        if (heading < pan) {
                            right = true;
                            lastl = false;
                        } else {
                            left = true;
                            lastl = true;
                        }
                        if (Math.abs(heading - pan) < 310 && madness.speed > StatList.swits[madness.cn][0]
                                && turntyp != 0) {
                            if (turntyp == 1) {
                                down = true;
                            }
                            if (turntyp == 2) {
                                handb = true;
                            }
                            if (!agressed) {
                                up = false;
                            }
                        }
                    }
                }
                if (checkpoints.stage == 14 && wall != -1) {
                    if (trackers.dam[wall] == 0 || madness.pcleared == 45) {
                        wall = -1;
                    }
                    if (madness.pcleared == 58 && checkpoints.opz[madness.im] < 36700) {
                        wall = -1;
                        hold = 0;
                    }
                }
                if (wall != -1) {
                    if (lwall != wall) {
                        if (lastl) {
                            left = true;
                        } else {
                            right = true;
                        }
                        wlastl = lastl;
                        lwall = wall;
                    } else if (wlastl) {
                        left = true;
                    } else {
                        right = true;
                    }
                    if (trackers.dam[wall] != 0) {
                        byte holdStep = 1;
                        if (trackers.skd[wall] == 1) {
                            holdStep = 3;
                        }
                        hold += holdStep;
                        if (hold > 10 * holdStep) {
                            hold = 10 * holdStep;
                        }
                    } else {
                        hold = 1;
                    }
                    wall = -1;
                } else if (hold != 0) {
                    hold--;
                }
            } else {
                if (trickfase == 0) {
                    int jumpHeight = (int) (((madness.scy[0] + madness.scy[1] + madness.scy[2] + madness.scy[3])
                            * (car.y - 300)) / 4000F);
                    int trickDivisor = 3;
                    if (checkpoints.stage == 15) {
                        trickDivisor = 10;
                    }
                    if (jumpHeight > 7 && (world.random() > trickprf / trickDivisor || stuntf == 4 || stuntf == 3 || stuntf == 5
                            || stuntf == 6 || checkpoints.stage == 16)) {
                        oxy = madness.pxy;
                        ozy = madness.pzy;
                        flycnt = 0;
                        uddirect = 0;
                        lrdirect = 0;
                        udswt = false;
                        lrswt = false;
                        trickfase = 1;
                        if (jumpHeight < 16) {
                            if (stuntf != 6) {
                                uddirect = -1;
                                udstart = 0;
                                udswt = false;
                            } else if (oupnt != 70) {
                                uddirect = 1;
                                udstart = 0;
                                udswt = false;
                            }
                        } else if (world.random() > world.random() && stuntf != 1 || stuntf == 4 || stuntf == 6
                                || stuntf == 7) {
                            if ((world.random() > world.random() || stuntf == 2 || stuntf == 7) && stuntf != 4 && stuntf != 6) {
                                uddirect = -1;
                            } else {
                                uddirect = 1;
                            }
                            udstart = (int) (10F * world.random() * trickprf);
                            if (stuntf == 6) {
                                udstart = 0;
                            }
                            if (checkpoints.stage == 16) {
                                udstart = 0;
                            }
                            if (checkpoints.stage == 14 && (oupnt == 68 || oupnt == 69)) {
                                apunch = 20;
                                oupnt = 70;
                            }
                            if (world.random() > 0.84999999999999998D && stuntf != 4 && stuntf != 3 && stuntf != 6
                                    && checkpoints.stage != 16) {
                                udswt = true;
                            }
                            if (world.random() > trickprf + 0.3F && stuntf != 4 && stuntf != 6) {
                                if (world.random() > world.random()) {
                                    lrdirect = -1;
                                } else {
                                    lrdirect = 1;
                                }
                                lrstart = (int) (30F * world.random());
                                if (world.random() > 0.75D) {
                                    lrswt = true;
                                }
                            }
                        } else {
                            if (world.random() > world.random()) {
                                lrdirect = -1;
                            } else {
                                lrdirect = 1;
                            }
                            lrstart = (int) (10F * world.random() * trickprf);
                            if (world.random() > 0.75D && checkpoints.stage != 16) {
                                lrswt = true;
                            }
                            if (world.random() > trickprf + 0.3F) {
                                if (world.random() > world.random()) {
                                    uddirect = -1;
                                } else {
                                    uddirect = 1;
                                }
                                udstart = (int) (30F * world.random());
                                if (world.random() > 0.84999999999999998D) {
                                    udswt = true;
                                }
                            }
                        }
                        if (trfix == 3 || trfix == 4) {
                            if (checkpoints.stage != 8) {
                                if (checkpoints.stage != 15 && lrdirect == -1) {
                                    if (checkpoints.stage != 9) {
                                        uddirect = -1;
                                    } else {
                                        uddirect = 1;
                                    }
                                }
                                lrdirect = 0;
                                if ((checkpoints.stage == 9 || checkpoints.stage == 15) && uddirect == -1) {
                                    uddirect = 1;
                                }
                                if (madness.power < 60F) {
                                    uddirect = -1;
                                }
                            } else {
                                if (uddirect != 0) {
                                    uddirect = -1;
                                }
                                lrdirect = 0;
                            }
                            if (checkpoints.stage == 10) {
                                uddirect = 1;
                                lrdirect = 0;
                            }
                            if (checkpoints.stage == 16) {
                                uddirect = -1;
                                lrdirect = 0;
                                if (madness.cn != 11 && madness.cn != 13) {
                                    udstart = 7;
                                    if (madness.cn == 14 && madness.power > 30F) {
                                        udstart = 14;
                                    }
                                } else {
                                    udstart = 0;
                                }
                                if (madness.cn == 11) {
                                    lrdirect = -1;
                                    lrstart = 0;
                                }
                            }
                        }
                    } else {
                        trickfase = -1;
                    }
                    if (!afta) {
                        afta = true;
                    }
                    if (trfix == 3) {
                        trfix = 4;
                        statusque += 30;
                    }
                }
                if (trickfase == 1) {
                    flycnt++;
                    if (lrdirect != 0 && flycnt > lrstart) {
                        if (lrswt && Math.abs(madness.pxy - oxy) > 180) {
                            if (lrdirect == -1) {
                                lrdirect = 1;
                            } else {
                                lrdirect = -1;
                            }
                            lrswt = false;
                        }
                        if (lrdirect == -1) {
                            handb = true;
                            left = true;
                        } else {
                            handb = true;
                            right = true;
                        }
                    }
                    if (uddirect != 0 && flycnt > udstart) {
                        if (udswt && Math.abs(madness.pzy - ozy) > 180) {
                            if (uddirect == -1) {
                                uddirect = 1;
                            } else {
                                uddirect = -1;
                            }
                            udswt = false;
                        }
                        if (uddirect == -1) {
                            handb = true;
                            down = true;
                        } else {
                            handb = true;
                            up = true;
                            if (apunch > 0) {
                                down = true;
                                apunch--;
                            }
                        }
                    }
                    if (((madness.scy[0] + madness.scy[1] + madness.scy[2] + madness.scy[3]) * 100F)
                            / (car.y - 300) < (-saftey)) {
                        onceu = false;
                        onced = false;
                        oncel = false;
                        oncer = false;
                        lrcomp = false;
                        udcomp = false;
                        udbare = false;
                        lrbare = false;
                        trickfase = 2;
                        swat = 0;
                    }
                }
                if (trickfase == 2) {
                    if (swat == 0) {
                        if (madness.dcomp != 0.0F || madness.ucomp != 0.0F) {
                            udbare = true;
                        }
                        if (madness.lcomp != 0.0F || madness.rcomp != 0.0F) {
                            lrbare = true;
                        }
                        swat = 1;
                    }
                    if (madness.wtouch) {
                        if (swat == 1) {
                            swat = 2;
                        }
                    } else if (swat == 2) {
                        if (madness.capsized && world.random() > mustland) {
                            if (udbare) {
                                lrbare = true;
                                udbare = false;
                            } else if (lrbare) {
                                udbare = true;
                                lrbare = false;
                            }
                        }
                        swat = 3;
                    }
                    if (udbare) {
                        int pitchLean;
                        for (pitchLean = madness.pzy + 90; pitchLean < 0; pitchLean += 360) {
                        }
                        for (; pitchLean > 180; pitchLean -= 360) {
                        }
                        pitchLean = Math.abs(pitchLean);
                        if (madness.lcomp - madness.rcomp < 5F && (onced || onceu)) {
                            udcomp = true;
                        }
                        if (madness.dcomp > madness.ucomp) {
                            if (madness.capsized) {
                                if (udcomp) {
                                    if (pitchLean > 90) {
                                        up = true;
                                    } else {
                                        down = true;
                                    }
                                } else if (!onced) {
                                    down = true;
                                }
                            } else {
                                if (udcomp) {
                                    if (perfection && Math.abs(pitchLean - 90) > 30) {
                                        if (pitchLean > 90) {
                                            up = true;
                                        } else {
                                            down = true;
                                        }
                                    }
                                } else if (world.random() > mustland) {
                                    up = true;
                                }
                                onced = true;
                            }
                        } else if (madness.capsized) {
                            if (udcomp) {
                                if (pitchLean > 90) {
                                    up = true;
                                } else {
                                    down = true;
                                }
                            } else if (!onceu) {
                                up = true;
                            }
                        } else {
                            if (udcomp) {
                                if (perfection && Math.abs(pitchLean - 90) > 30) {
                                    if (pitchLean > 90) {
                                        up = true;
                                    } else {
                                        down = true;
                                    }
                                }
                            } else if (world.random() > mustland) {
                                down = true;
                            }
                            onceu = true;
                        }
                    }
                    if (lrbare) {
                        int rollLean = madness.pxy + 90;
                        if (zyinv) {
                            rollLean += 180;
                        }
                        for (; rollLean < 0; rollLean += 360) {
                        }
                        for (; rollLean > 180; rollLean -= 360) {
                        }
                        rollLean = Math.abs(rollLean);
                        if (madness.lcomp - madness.rcomp < 10F && (oncel || oncer)) {
                            lrcomp = true;
                        }
                        if (madness.lcomp > madness.rcomp) {
                            if (madness.capsized) {
                                if (lrcomp) {
                                    if (rollLean > 90) {
                                        left = true;
                                    } else {
                                        right = true;
                                    }
                                } else if (!oncel) {
                                    left = true;
                                }
                            } else {
                                if (lrcomp) {
                                    if (perfection && Math.abs(rollLean - 90) > 30) {
                                        if (rollLean > 90) {
                                            left = true;
                                        } else {
                                            right = true;
                                        }
                                    }
                                } else if (world.random() > mustland) {
                                    right = true;
                                }
                                oncel = true;
                            }
                        } else if (madness.capsized) {
                            if (lrcomp) {
                                if (rollLean > 90) {
                                    left = true;
                                } else {
                                    right = true;
                                }
                            } else if (!oncer) {
                                right = true;
                            }
                        } else {
                            if (lrcomp) {
                                if (perfection && Math.abs(rollLean - 90) > 30) {
                                    if (rollLean > 90) {
                                        left = true;
                                    } else {
                                        right = true;
                                    }
                                }
                            } else if (world.random() > mustland) {
                                left = true;
                            }
                            oncer = true;
                        }
                    }
                }
            }
        }
    }

    public void reset(CheckPoints checkpoints, int carType) {
        pan = 0;
        attack = 0;
        acr = 0;
        afta = false;
        trfix = 0;
        acuracy = 0;
        upwait = 0;
        forget = false;
        bulistc = false;
        runbul = 0;
        revstart = 0;
        oupnt = 0;
        gowait = false;
        apunch = 0;
        exitattack = false;
        if (checkpoints.stage == 6 || checkpoints.stage == 8) {
            hold = 50;
        }
        if (checkpoints.stage == 7) {
            hold = 10;
        }
        if (checkpoints.stage == 10) {
            hold = 30;
        }
        if (checkpoints.stage == 11) {
            if (carType != 13) {
                hold = 35;
                revstart = 25;
            } else {
                hold = 5;
            }
            statusque = 0;
        }
        if (checkpoints.stage == 12) {
            if (carType != 13) {
                hold = (int) (20F + 10F * world.random());
                revstart = (int) (10F + 10F * world.random());
            } else {
                hold = 5;
            }
            statusque = 0;
        }
        if (checkpoints.stage == 14) {
            hold = 30;
            statusque = 0;
            if (carType != 14) {
                revstart = 1;
            }
        }
        if (checkpoints.stage == 15) {
            hold = 40;
        }
        if (checkpoints.stage == 16) {
            hold = 20;
        }
        if (checkpoints.stage != 9 && checkpoints.stage != 16) {
            for (int fixer = 0; fixer < checkpoints.fn; fixer++) {
                int bestDistance = -10;
                for (int point = 0; point < checkpoints.n; point++) {
                    if (Utility.py(checkpoints.fx[fixer] / 100, checkpoints.x[point] / 100, checkpoints.fz[fixer] / 100,
                            checkpoints.z[point] / 100) < bestDistance || bestDistance == -10) {
                        bestDistance = Utility.py(checkpoints.fx[fixer] / 100, checkpoints.x[point] / 100, checkpoints.fz[fixer] / 100,
                                checkpoints.z[point] / 100);
                        fpnt[fixer] = point;
                    }
                }

            }

            for (int fixer = 0; fixer < checkpoints.fn; fixer++) {
                fpnt[fixer] -= 4;
                if (fpnt[fixer] < 0) {
                    fpnt[fixer] += checkpoints.nsp;
                }
            }

        } else {
            if (checkpoints.stage == 9) {
                fpnt[0] = 14;
                fpnt[1] = 36;
            }
            if (checkpoints.stage == 16) {
                fpnt[3] = 39;
            }
        }
        left = false;
        right = false;
        up = false;
        down = false;
        handb = false;
        lookback = 0;
        arrace = false;
    }

    public Control(World world) {
        this.world = world;
        left = false;
        right = false;
        up = false;
        down = false;
        handb = false;
        lookback = 0;
        enter = false;
        arrace = false;
        pan = 0;
        attack = 0;
        acr = 0;
        afta = false;
        fpnt = new int[5];
        trfix = 0;
        forget = false;
        bulistc = false;
        runbul = 0;
        acuracy = 0;
        upwait = 0;
        agressed = false;
        skiplev = 1.0F;
        clrnce = 5;
        rampp = 0;
        turntyp = 0;
        aim = 0.0F;
        saftey = 30;
        perfection = false;
        mustland = 0.5F;
        usebounce = false;
        trickprf = 0.5F;
        stuntf = 0;
        zyinv = false;
        lastl = false;
        wlastl = false;
        hold = 0;
        wall = -1;
        lwall = -1;
        stcnt = 0;
        statusque = 0;
        turncnt = 0;
        randtcnt = 0;
        upcnt = 0;
        trickfase = 0;
        swat = 0;
        udcomp = false;
        lrcomp = false;
        udbare = false;
        lrbare = false;
        onceu = false;
        onced = false;
        oncel = false;
        oncer = false;
        lrdirect = 0;
        uddirect = 0;
        lrstart = 0;
        udstart = 0;
        oxy = 0;
        ozy = 0;
        flycnt = 0;
        lrswt = false;
        udswt = false;
        gowait = false;
        actwait = 0;
        cntrn = 0;
        revstart = 0;
        oupnt = 0;
        wtz = 0;
        wtx = 0;
        frx = 0;
        frz = 0;
        frad = 0;
        apunch = 0;
        exitattack = false;
        avoidnlev = 0;
    }

    public void falseo() {
        left = false;
        right = false;
        up = false;
        down = false;
        handb = false;
        lookback = 0;
        enter = false;
        arrace = false;
    }
}
