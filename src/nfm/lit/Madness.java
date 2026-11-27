package nfm.lit;
import java.awt.*;

/**
 * Madness is where the stats, collisions, locations of the cars, and more, are handled.
 *
 * @author Kaffeinated, Omar Waly
 */
class Madness {

    public Stat stat;
    private final Record rpd;
    /** Hears crashes, skids, repairs and checkpoint progress: the race draws and plays nothing itself. */
    private final RaceEvents events;
    /** The race's dice. */
    private final World world;
    private final boolean[] dominate;
    private final boolean[] caught;
    public int pzy;
    public int pxy;
    public float speed;
    private float forca;
    public final float[] scy;
    public final float[] scz;
    public final float[] scx;
    public boolean mtouch;
    public boolean wtouch;
    private int cntouch;
    public boolean capsized;
    private int txz;
    private int fxz;
    private int pmlt;
    private int nmlt;
    private int dcnt;
    public int skid;
    private boolean pushed;
    public boolean gtouch;
    private boolean pl;
    private boolean pr;
    private boolean pd;
    private boolean pu;
    public int loop;
    public float ucomp;
    public float dcomp;
    public float lcomp;
    public float rcomp;
    private int lxz;
    public int travxy;
    public int travzy;
    public int travxz;
    public int trcnt;
    public int capcnt;
    private int srfcnt;
    public boolean rtab;
    public boolean ftab;
    public boolean btab;
    public boolean surfer;
    public float powerup;
    private int xtpower;
    private float tilt;
    public int squash;
    private int nbsq;
    public int hitmag;
    public int cntdest;
    public boolean dest;
    public boolean newcar;
    public int pcleared;
    public int clear;
    public int nlaps;
    private int focus;
    public float power;
    public int missedcp;
    public int lastcolido;
    public int point;
    public boolean nofocus;
    private int rpdcatch;
    private boolean colidim;
    /**
     * The human whose car just hit this one, while {@link #colidim}: the
     * hit is that player's to hear. Car 0 in the classic game.
     */
    private static int colider;
    public int cn;
    public int im;
    public int mxz;
    public int cxz;
    /* variable for screen shake */
    public int shakedam;

    /**
     * universal rate that speed decreases when it exceeds swits[0][2], that is, top speed
     *
     * @author Kaffeinated
     */
    private static final float speeddec = 2.0F;

    private void regy(int wheel, float force, ContO car) {
        force *= stat.dammult;
        if (force > 100F) {
            rpd.recy(wheel, force, mtouch, im);
            force -= 100F;
            byte pitchSide = 0;
            byte rollSide = 0;
            int pitch = car.zy;
            int roll = car.xy;
            for (; pitch < 360; pitch += 360)
                ;
            for (; pitch > 360; pitch -= 360)
                ;
            if (pitch < 210 && pitch > 150)
                pitchSide = -1;
            if (pitch > 330 || pitch < 30)
                pitchSide = 1;
            for (; roll < 360; roll += 360)
                ;
            for (; roll > 360; roll -= 360)
                ;
            if (roll < 210 && roll > 150)
                rollSide = -1;
            if (roll > 330 || roll < 30)
                rollSide = 1;
            if (pitchSide * rollSide == 0)
                shakedam = (int) ((Math.abs(force) + shakedam) / 2.0F);
            nfm.lit.audio.Sound3D.carCrash(im, force, rollSide * pitchSide);
            FeatureBridge.hit(im, wheel, 1, force); // the extras see the hit (damage looks, marks)
            crashEvent(force, rollSide * pitchSide);
            if (rollSide * pitchSide == 0 || mtouch) {
                for (int poly = 0; poly < car.npl; poly++) {
                    float dent = 0.0F;
                    for (int corner = 0; corner < car.p[poly].n; corner++)
                        if (car.p[poly].wz == 0
                                && Utility.py(car.keyx[wheel], car.p[poly].ox[corner], car.keyz[wheel], car.p[poly].oz[corner]) < stat.clrad) {
                            dent = (force / 20F) * world.random();
                            car.p[poly].oz[corner] += dent * RadicalMath.sin(pitch);
                            car.p[poly].ox[corner] -= dent * RadicalMath.sin(roll);
                            hitmag += Math.abs(dent);
                        }

                    if (dent != 0.0F) {
                        if (Math.abs(dent) >= 1.0F) {
                            car.p[poly].chip = 1;
                            car.p[poly].ctmag = dent;
                        }
                        if (!car.p[poly].nocol && !car.p[poly].glass) {
                            if (car.p[poly].bfase > 20 && car.p[poly].hsb[1] > 0.25D)
                                car.p[poly].hsb[1] = 0.25F;
                            if (car.p[poly].bfase > 25 && car.p[poly].hsb[2] > 0.69999999999999996D)
                                car.p[poly].hsb[2] = 0.7F;
                            if (car.p[poly].bfase > 30 && car.p[poly].hsb[1] > 0.14999999999999999D)
                                car.p[poly].hsb[1] = 0.15F;
                            if (car.p[poly].bfase > 35 && car.p[poly].hsb[2] > 0.59999999999999998D)
                                car.p[poly].hsb[2] = 0.6F;
                            if (car.p[poly].bfase > 40)
                                car.p[poly].hsb[0] = 0.075F;
                            if (car.p[poly].bfase > 50 && car.p[poly].hsb[2] > 0.5D)
                                car.p[poly].hsb[2] = 0.5F;
                            if (car.p[poly].bfase > 60)
                                car.p[poly].hsb[0] = 0.05F;
                            car.p[poly].bfase += dent;
                            new Color(car.p[poly].c[0], car.p[poly].c[1], car.p[poly].c[2]);
                            Color color = Color.getHSBColor(car.p[poly].hsb[0], car.p[poly].hsb[1], car.p[poly].hsb[2]);
                            car.p[poly].c[0] = color.getRed();
                            car.p[poly].c[1] = color.getGreen();
                            car.p[poly].c[2] = color.getBlue();
                        }
                        if (car.p[poly].glass)
                            car.p[poly].gr += Math.abs(dent * 1.5D);
                    }
                }

            }
            if (rollSide * pitchSide == -1)
                if (nbsq > 0) {
                    int squashed = 0;
                    int count = 1;
                    for (int poly = 0; poly < car.npl; poly++) {
                        float dent = 0.0F;
                        for (int corner = 0; corner < car.p[poly].n; corner++)
                            if (car.p[poly].wz == 0) {
                                dent = (force / 15F) * world.random();
                                if ((Math.abs(car.p[poly].oy[corner] - stat.flipy - squash) < stat.msquash * 3
                                        || car.p[poly].oy[corner] < stat.flipy + squash) && squash < stat.msquash) {
                                    car.p[poly].oy[corner] += dent;
                                    squashed = (int) (squashed + dent);
                                    count++;
                                    hitmag += Math.abs(dent);
                                }
                            }

                        if (car.p[poly].glass)
                            car.p[poly].gr += 5;
                        else if (dent != 0.0F)
                            car.p[poly].bfase += dent;
                        if (Math.abs(dent) >= 1.0F) {
                            car.p[poly].chip = 1;
                            car.p[poly].ctmag = dent;
                        }
                    }

                    squash += squashed / count;
                    nbsq = 0;
                } else {
                    nbsq++;
                }
        }
    }

    /** The race this car is in. */
    World world() {
        return world;
    }

    public Madness(Record record, RaceEvents events, World world, int index) {
        cn = 0;
        im = 0;
        mxz = 0;
        cxz = 0;
        dominate = new boolean[51];
        caught = new boolean[51];
        pzy = 0;
        pxy = 0;
        speed = 0.0F;
        forca = 0.0F;
        scy = new float[4];
        scz = new float[4];
        scx = new float[4];
        mtouch = false;
        wtouch = false;
        cntouch = 0;
        capsized = false;
        txz = 0;
        fxz = 0;
        pmlt = 1;
        nmlt = 1;
        dcnt = 0;
        skid = 0;
        pushed = false;
        gtouch = false;
        pl = false;
        pr = false;
        pd = false;
        pu = false;
        loop = 0;
        ucomp = 0.0F;
        dcomp = 0.0F;
        lcomp = 0.0F;
        rcomp = 0.0F;
        lxz = 0;
        travxy = 0;
        travzy = 0;
        travxz = 0;
        trcnt = 0;
        capcnt = 0;
        srfcnt = 0;
        rtab = false;
        ftab = false;
        btab = false;
        surfer = false;
        powerup = 0.0F;
        xtpower = 0;
        tilt = 0.0F;
        squash = 0;
        nbsq = 0;
        hitmag = 0;
        cntdest = 0;
        dest = false;
        newcar = false;
        pcleared = 0;
        clear = 0;
        nlaps = 0;
        focus = -1;
        power = 75F;
        missedcp = 0;
        lastcolido = 0;
        point = 0;
        nofocus = false;
        rpdcatch = 0;
        colidim = false;
        rpd = record;
        this.events = events;
        this.world = world;
        im = index;
        shakedam = 0;
        stat = new Stat();
    }

    public void devFixCar() {
        this.squash = 0;
        this.nbsq = 0;
        this.hitmag = 0;
        this.cntdest = 0;
        this.dest = false;
        this.newcar = true;
    }

    private void regz(int wheel, float force, ContO car) {
        force *= stat.dammult;
        FeatureBridge.touch(im, wheel, 2, force); // the extras see every touch, a hit or not (scratches)
        if (Math.abs(force) > 100F) {
            rpd.recz(wheel, force, im);
            if (force > 100F)
                force -= 100F;
            if (force < -100F)
                force += 100F;
            shakedam = (int) ((Math.abs(force) + shakedam) / 2.0F);
            nfm.lit.audio.Sound3D.carCrash(im, force, 0); // heard from every car with the 3D sound
            FeatureBridge.hit(im, wheel, 2, force);
            crashEvent(force, 0);
            for (int poly = 0; poly < car.npl; poly++) {
                float dent = 0.0F;
                for (int corner = 0; corner < car.p[poly].n; corner++)
                    if (car.p[poly].wz == 0
                            && Utility.py(car.keyx[wheel], car.p[poly].ox[corner], car.keyz[wheel], car.p[poly].oz[corner]) < stat.clrad) {
                        dent = (force / 20F) * world.random();
                        car.p[poly].oz[corner] += dent * RadicalMath.cos(car.xz) * RadicalMath.cos(car.zy);
                        car.p[poly].ox[corner] += dent * RadicalMath.sin(car.xz) * RadicalMath.cos(car.xy);
                        hitmag += Math.abs(dent);
                    }

                if (dent != 0.0F) {
                    if (Math.abs(dent) >= 1.0F) {
                        car.p[poly].chip = 1;
                        car.p[poly].ctmag = dent;
                    }
                    if (!car.p[poly].nocol && !car.p[poly].glass) {
                        if (car.p[poly].bfase > 20 && car.p[poly].hsb[1] > 0.25D)
                            car.p[poly].hsb[1] = 0.25F;
                        if (car.p[poly].bfase > 25 && car.p[poly].hsb[2] > 0.69999999999999996D)
                            car.p[poly].hsb[2] = 0.7F;
                        if (car.p[poly].bfase > 30 && car.p[poly].hsb[1] > 0.14999999999999999D)
                            car.p[poly].hsb[1] = 0.15F;
                        if (car.p[poly].bfase > 35 && car.p[poly].hsb[2] > 0.59999999999999998D)
                            car.p[poly].hsb[2] = 0.6F;
                        if (car.p[poly].bfase > 40)
                            car.p[poly].hsb[0] = 0.075F;
                        if (car.p[poly].bfase > 50 && car.p[poly].hsb[2] > 0.5D)
                            car.p[poly].hsb[2] = 0.5F;
                        if (car.p[poly].bfase > 60)
                            car.p[poly].hsb[0] = 0.05F;
                        car.p[poly].bfase += Math.abs(dent);
                        new Color(car.p[poly].c[0], car.p[poly].c[1], car.p[poly].c[2]);
                        Color color = Color.getHSBColor(car.p[poly].hsb[0], car.p[poly].hsb[1], car.p[poly].hsb[2]);
                        car.p[poly].c[0] = color.getRed();
                        car.p[poly].c[1] = color.getGreen();
                        car.p[poly].c[2] = color.getBlue();
                    }
                    if (car.p[poly].glass)
                        car.p[poly].gr += Math.abs(dent * 1.5D);
                }
            }

        }
    }

    public void colide(ContO car, Madness other, ContO otherCar) {
        float wheelX[] = new float[4];
        float wheelY[] = new float[4];
        float wheelZ[] = new float[4];
        float otherX[] = new float[4];
        float otherY[] = new float[4];
        float otherZ[] = new float[4];
        int wheel = 0;
        do {
            wheelX[wheel] = car.x + car.keyx[wheel];
            if (capsized)
                wheelY[wheel] = car.y + stat.flipy + squash;
            else
                wheelY[wheel] = car.y + car.grat;
            wheelZ[wheel] = car.z + car.keyz[wheel];
            otherX[wheel] = otherCar.x + otherCar.keyx[wheel];
            if (capsized)
                otherY[wheel] = otherCar.y + StatList.flipy[other.cn] + other.squash;
            else
                otherY[wheel] = otherCar.y + otherCar.grat;
            otherZ[wheel] = otherCar.z + otherCar.keyz[wheel];
        } while (++wheel < 4);
        Utility.rot(wheelX, wheelY, car.x, car.y, car.xy, 4);
        Utility.rot(wheelY, wheelZ, car.y, car.z, car.zy, 4);
        Utility.rot(wheelX, wheelZ, car.x, car.z, car.xz, 4);
        Utility.rot(otherX, otherY, otherCar.x, otherCar.y, otherCar.xy, 4);
        Utility.rot(otherY, otherZ, otherCar.y, otherCar.z, otherCar.zy, 4);
        Utility.rot(otherX, otherZ, otherCar.x, otherCar.z, otherCar.xz, 4);
        if (Utility.rpy(car.x, otherCar.x, car.y, otherCar.y, car.z,
                otherCar.z) < (car.maxR * car.maxR + otherCar.maxR * otherCar.maxR) * 1.5D) {
            if (!caught[other.im] && (speed != 0.0F || other.speed != 0.0F)) {
                if (Math.abs(power * speed * stat.moment) != Math
                        .abs(other.power * other.speed * StatList.moment[other.cn])) {
                    dominate[other.im] = Math.abs(power * speed * stat.moment) > Math
                            .abs(other.power * other.speed * StatList.moment[other.cn]);
                } else dominate[other.im] = stat.moment > StatList.moment[other.cn];
                caught[other.im] = true;
            }
        } else if (caught[other.im])
            caught[other.im] = false;
        if (dominate[other.im]) {
            int closing = (int) (((((((((scz[0] - other.scz[0]) + scz[1]) - other.scz[1]) + scz[2]) - other.scz[2])
                    + scz[3]) - other.scz[3])
                    * (((((((scz[0] - other.scz[0]) + scz[1]) - other.scz[1]) + scz[2]) - other.scz[2]) + scz[3])
                    - other.scz[3])
                    + (((((((scx[0] - other.scx[0]) + scx[1]) - other.scx[1]) + scx[2]) - other.scx[2]) + scx[3])
                    - other.scx[3])
                    * (((((((scx[0] - other.scx[0]) + scx[1]) - other.scx[1]) + scx[2]) - other.scx[2])
                    + scx[3]) - other.scx[3]))
                    / 16F);
            int myWheel = 0;
            do {
                int otherWheel = 0;
                do
                    if (Utility.rpy(wheelX[myWheel], otherX[otherWheel], wheelY[myWheel], otherY[otherWheel], wheelZ[myWheel], otherZ[otherWheel]) < (closing + 7000)
                            * (stat.comprad + stat.comprad)) {
                        FeatureBridge.raceTouch(im, myWheel, other.im, otherWheel); // a race extra's (the tailwhip): nothing without one
                        if (Math.abs(scx[myWheel] * stat.moment) > Math.abs(other.scx[otherWheel] * StatList.moment[other.cn])) {
                            float pushBackX = other.scx[otherWheel] * stat.revpush;
                            if (pushBackX > 300F)
                                pushBackX = 300F;
                            if (pushBackX < -300F)
                                pushBackX = -300F;
                            float pushX = scx[myWheel] * stat.push;
                            if (pushX > 300F)
                                pushX = 300F;
                            if (pushX < -300F)
                                pushX = -300F;
                            float heavyX = 1.0F;
                            if (other.cn == 13)
                                heavyX = stat.moment;
                            other.scx[otherWheel] += pushX;
                            if (human()) {
                                other.colidim = true;
                                colider = im;
                            }
                            other.regx(otherWheel, pushX * stat.moment * heavyX, otherCar);
                            FeatureBridge.ram(im, myWheel, other.im, otherWheel, pushX); // the extras see who rammed whom
                            if (other.colidim)
                                other.colidim = false;
                            scx[myWheel] -= pushBackX;
                            regx(myWheel, -pushBackX * StatList.moment[cn], car);
                            scy[myWheel] -= stat.revlift;
                            if (human()) {
                                other.colidim = true;
                                colider = im;
                            }
                            other.regy(otherWheel, stat.revlift * 7, otherCar);
                            if (other.colidim)
                                other.colidim = false;
                        }
                        if (Math.abs(scz[myWheel] * stat.moment) > Math.abs(other.scz[otherWheel] * StatList.moment[other.cn])) {
                            float pushBackZ = other.scz[otherWheel] * stat.revpush;
                            if (pushBackZ > 300F)
                                pushBackZ = 300F;
                            if (pushBackZ < -300F)
                                pushBackZ = -300F;
                            float pushZ = scz[myWheel] * stat.push;
                            if (pushZ > 300F)
                                pushZ = 300F;
                            if (pushZ < -300F)
                                pushZ = -300F;
                            float heavyZ = 1.0F;
                            if (other.cn == 13)
                                heavyZ = stat.moment;
                            other.scz[otherWheel] += pushZ;
                            if (human()) {
                                other.colidim = true;
                                colider = im;
                            }
                            other.regz(otherWheel, pushZ * stat.moment * heavyZ, otherCar);
                            FeatureBridge.ram(im, myWheel, other.im, otherWheel, pushZ);
                            if (other.colidim)
                                other.colidim = false;
                            scz[myWheel] -= pushBackZ;
                            regz(myWheel, -pushBackZ * StatList.moment[cn], car);
                            scy[myWheel] -= stat.revlift;
                            if (human()) {
                                other.colidim = true;
                                colider = im;
                            }
                            other.regy(otherWheel, stat.revlift * 7, otherCar);
                            if (other.colidim)
                                other.colidim = false;
                        }
                        if (human()) {
                            other.lastcolido = 70;
                            SplitRace.hit(other.im, im);
                        }
                        if (other.human()) {
                            lastcolido = 70;
                            SplitRace.hit(im, other.im);
                        }
                        other.scy[otherWheel] -= stat.lift;
                    }
                while (++otherWheel < 4);
            } while (++myWheel < 4);

        }
    }

    /** A car people drive: car 0, or car 1 too in split screen. */
    boolean human() {
        return im < GameFacts.humans;
    }

    /** A crash the players hear: their own car's, or that of a car a player hit. */
    private void crashEvent(float force, int kind) {
        if (human())
            events.crash(im, force, kind);
        if (colidim && colider != im)
            events.crash(colider, force, kind);
    }

    private void distruct(ContO car) {
        for (int poly = 0; poly < car.npl; poly++)
            if (car.p[poly].wz == 0 || car.p[poly].gr == -17 || car.p[poly].gr == -16)
                car.p[poly].embos = 1;

    }

    public void reseto(int carType, ContO car, CheckPoints checkpoints) {
        cn = carType;
        int slot = 0;
        do {
            dominate[slot] = false;
            caught[slot] = false;
        } while (++slot < 51);
        if (cn == 11 && human())
            if (checkpoints.stage == 10)
                stat.moment = 2.5F;
            else
                stat.moment = 2.0F;
        if (cn == 9 && !human())
            if (checkpoints.stage == 4)
                stat.maxmag = 8000;
            else
                stat.maxmag = 9700;
        mxz = 0;
        cxz = 0;
        pzy = 0;
        pxy = 0;
        speed = 0.0F;
        slot = 0;
        do {
            scy[slot] = 0.0F;
            scx[slot] = 0.0F;
            scz[slot] = 0.0F;
        } while (++slot < 4);
        forca = (((float) Math.sqrt(car.keyz[0] * car.keyz[0] + car.keyx[0] * car.keyx[0])
                + (float) Math.sqrt(car.keyz[1] * car.keyz[1] + car.keyx[1] * car.keyx[1])
                + (float) Math.sqrt(car.keyz[2] * car.keyz[2] + car.keyx[2] * car.keyx[2])
                + (float) Math.sqrt(car.keyz[3] * car.keyz[3] + car.keyx[3] * car.keyx[3])) / 10000F)
                * (float) (stat.bounce - 0.29999999999999999D);
        mtouch = false;
        wtouch = false;
        txz = 0;
        fxz = 0;
        pmlt = 1;
        nmlt = 1;
        dcnt = 0;
        skid = 0;
        pushed = false;
        gtouch = false;
        pl = false;
        pr = false;
        pd = false;
        pu = false;
        loop = 0;
        ucomp = 0.0F;
        dcomp = 0.0F;
        lcomp = 0.0F;
        rcomp = 0.0F;
        lxz = 0;
        travxy = 0;
        travzy = 0;
        travxz = 0;
        rtab = false;
        ftab = false;
        btab = false;
        powerup = 0.0F;
        xtpower = 0;
        trcnt = 0;
        capcnt = 0;
        tilt = 0.0F;
        pcleared = checkpoints.pcs;
        clear = 0;
        nlaps = 0;
        focus = -1;
        missedcp = 0;
        nofocus = false;
        power = 98F;
        lastcolido = 0;
        checkpoints.dested[im] = 0;
        squash = 0;
        nbsq = 0;
        hitmag = 0;
        cntdest = 0;
        dest = false;
        newcar = false;
        if (human()) {
            events.showCheckpoint(im, -1);
            events.showLastCheckpoint(im, false);
        }
        rpdcatch = 0;
    }

    private void regx(int wheel, float force, ContO car) {
        force *= stat.dammult;
        FeatureBridge.touch(im, wheel, 0, force);
        if (Math.abs(force) > 100F) {
            rpd.recx(wheel, force, im);
            if (force > 100F)
                force -= 100F;
            if (force < -100F)
                force += 100F;
            shakedam = (int) ((Math.abs(force) + shakedam) / 2.0F);
            nfm.lit.audio.Sound3D.carCrash(im, force, 0); // heard from every car with the 3D sound
            FeatureBridge.hit(im, wheel, 0, force);
            crashEvent(force, 0);
            for (int poly = 0; poly < car.npl; poly++) {
                float dent = 0.0F;
                for (int corner = 0; corner < car.p[poly].n; corner++)
                    if (car.p[poly].wz == 0
                            && Utility.py(car.keyx[wheel], car.p[poly].ox[corner], car.keyz[wheel], car.p[poly].oz[corner]) < stat.clrad) {
                        dent = (force / 20F) * world.random();
                        car.p[poly].oz[corner] -= dent * RadicalMath.sin(car.xz) * RadicalMath.cos(car.zy);
                        car.p[poly].ox[corner] += dent * RadicalMath.cos(car.xz) * RadicalMath.cos(car.xy);
                        hitmag += Math.abs(dent);
                    }

                if (dent != 0.0F) {
                    if (Math.abs(dent) >= 1.0F) {
                        car.p[poly].chip = 1;
                        car.p[poly].ctmag = dent;
                    }
                    if (!car.p[poly].nocol && !car.p[poly].glass) {
                        if (car.p[poly].bfase > 20 && car.p[poly].hsb[1] > 0.25D)
                            car.p[poly].hsb[1] = 0.25F;
                        if (car.p[poly].bfase > 25 && car.p[poly].hsb[2] > 0.69999999999999996D)
                            car.p[poly].hsb[2] = 0.7F;
                        if (car.p[poly].bfase > 30 && car.p[poly].hsb[1] > 0.14999999999999999D)
                            car.p[poly].hsb[1] = 0.15F;
                        if (car.p[poly].bfase > 35 && car.p[poly].hsb[2] > 0.59999999999999998D)
                            car.p[poly].hsb[2] = 0.6F;
                        if (car.p[poly].bfase > 40)
                            car.p[poly].hsb[0] = 0.075F;
                        if (car.p[poly].bfase > 50 && car.p[poly].hsb[2] > 0.5D)
                            car.p[poly].hsb[2] = 0.5F;
                        if (car.p[poly].bfase > 60)
                            car.p[poly].hsb[0] = 0.05F;
                        car.p[poly].bfase += Math.abs(dent);
                        new Color(car.p[poly].c[0], car.p[poly].c[1], car.p[poly].c[2]);
                        Color color = Color.getHSBColor(car.p[poly].hsb[0], car.p[poly].hsb[1], car.p[poly].hsb[2]);
                        car.p[poly].c[0] = color.getRed();
                        car.p[poly].c[1] = color.getGreen();
                        car.p[poly].c[2] = color.getBlue();
                    }
                    if (car.p[poly].glass)
                        car.p[poly].gr += Math.abs(dent * 1.5D);
                }
            }

        }
    }

    public void drive(Control control, ContO car, Trackers trackers, CheckPoints checkpoints) {
        int sign = 1;
        int factor = 1;
        boolean upsideDown = false;
        boolean reversed = false;
        boolean hitWall = false;
        capsized = false;
        int pitchAngle;
        for (pitchAngle = Math.abs(pzy); pitchAngle > 270; pitchAngle -= 360)
            ;
        pitchAngle = Math.abs(pitchAngle);
        if (pitchAngle > 90)
            upsideDown = true;
        boolean rolledOver = false;
        int rollAngle;
        for (rollAngle = Math.abs(pxy); rollAngle > 270; rollAngle -= 360)
            ;
        rollAngle = Math.abs(rollAngle);
        if (rollAngle > 90) {
            rolledOver = true;
            factor = -1;
        }
        int wheelDrop = car.grat;
        if (upsideDown) {
            if (rolledOver) {
                rolledOver = false;
                reversed = true;
            } else {
                rolledOver = true;
                capsized = true;
            }
            sign = -1;
        } else if (rolledOver)
            capsized = true;
        if (capsized)
            wheelDrop = stat.flipy + squash;
        control.zyinv = upsideDown;
        float airX = 0.0F;
        float airZ = 0.0F;
        float airY = 0.0F;
        if (mtouch)
            loop = 0;
        if (wtouch) {
            if (loop == 2 || loop == -1) {
                loop = -1;
                if (control.left)
                    pl = true;
                if (control.right)
                    pr = true;
                if (control.up)
                    pu = true;
                if (control.down)
                    pd = true;
            }
            ucomp = 0.0F;
            dcomp = 0.0F;
            lcomp = 0.0F;
            rcomp = 0.0F;
        }
        if (control.handb) {
            if (!pushed)
                if (!wtouch) {
                    if (loop == 0)
                        loop = 1;
                } else if (gtouch)
                    pushed = true;
        } else {
            pushed = false;
        }
        if (loop == 1) {
            float meanFall = (scy[0] + scy[1] + scy[2] + scy[3]) / 4F;
            int wheel = 0;
            do
                scy[wheel] = meanFall;
            while (++wheel < 4);
            loop = 2;
        }
        if (!dest)
            if (loop == 2) {
                if (control.up) {
                    if (ucomp == 0.0F) {
                        ucomp = 10F + (scy[0] + 50F) / 20F;
                        if (ucomp < 5F)
                            ucomp = 5F;
                        if (ucomp > 10F)
                            ucomp = 10F;
                        ucomp *= stat.airs;
                    }
                    if (ucomp < 20F)
                        ucomp += 0.5D * stat.airs;
                    airX = (-stat.airc) * RadicalMath.sin(car.xz) * factor;
                    airZ = stat.airc * RadicalMath.cos(car.xz) * factor;
                } else if (ucomp != 0.0F && ucomp > -2F)
                    ucomp -= 0.5D * stat.airs;
                if (control.down) {
                    if (dcomp == 0.0F) {
                        dcomp = 10F + (scy[0] + 50F) / 20F;
                        if (dcomp < 5F)
                            dcomp = 5F;
                        if (dcomp > 10F)
                            dcomp = 10F;
                        dcomp *= stat.airs;
                    }
                    if (dcomp < 20F)
                        dcomp += 0.5D * stat.airs;
                    airY = -stat.airc;
                } else if (dcomp != 0.0F && ucomp > -2F)
                    dcomp -= 0.5D * stat.airs;
                if (control.left) {
                    if (lcomp == 0.0F)
                        lcomp = 5F;
                    if (lcomp < 20F)
                        lcomp += 2.0F * stat.airs;
                    airX = (-stat.airc) * RadicalMath.cos(car.xz) * sign;
                    airZ = (-stat.airc) * RadicalMath.sin(car.xz) * sign;
                } else if (lcomp > 0.0F)
                    lcomp -= 2.0F * stat.airs;
                if (control.right) {
                    if (rcomp == 0.0F)
                        rcomp = 5F;
                    if (rcomp < 20F)
                        rcomp += 2.0F * stat.airs;
                    airX = stat.airc * RadicalMath.cos(car.xz) * sign;
                    airZ = stat.airc * RadicalMath.sin(car.xz) * sign;
                } else if (rcomp > 0.0F)
                    rcomp -= 2.0F * stat.airs;
                pzy += (dcomp - ucomp) * RadicalMath.cos(pxy);
                if (upsideDown)
                    car.xz += (dcomp - ucomp) * RadicalMath.sin(pxy);
                else
                    car.xz -= (dcomp - ucomp) * RadicalMath.sin(pxy);
                pxy += rcomp - lcomp;
            } else {
                float boost = power;
                if (boost < 40F)
                    boost = 40F;
                if (human() && power != 98F)
                    if (checkpoints.stage != 6 && checkpoints.stage != 8)
                        boost = (float) (boost * 0.76000000000000001D);
                    else if (checkpoints.stage != 6)
                        boost = (float) (boost * 0.90000000000000002D);
                boost += FeatureBridge.raceBoost(im); // a race extra's (the powerups' nitrous): 0 without one
                if (control.down)
                    if (speed > 0.0F) {
                        speed -= stat.handb / 2;
                    } else {
                        int gear = 0;
                        int shift = 0;
                        do
                            if (speed <= -(stat.swits[shift] / 2 + boost * (stat.swits[shift]) / 196F))
                                gear++;
                        while (++shift < 2);
                        if (gear != 2)
                            speed -= stat.acelf[gear] / 2.0F + boost * (stat.acelf[gear]) / 196F;
                        else
                            speed = -(stat.swits[1] / 2 + boost * (stat.swits[1]) / 196F);
                    }
                if (control.up)
                    if (speed < 0.0F) {
                        speed += stat.handb;
                    } else {
                        int gear = 0;
                        int shift = 0;
                        do
                            if (speed >= stat.swits[shift] / 2 + (boost * stat.swits[shift]) / 196F)
                                gear++;
                        while (++shift < 3);
                        if (gear != 3)
                            speed += stat.acelf[gear] / 2.0F + (boost * stat.acelf[gear]) / 196F;
                        else
                            speed = stat.swits[2] / 2 + (boost * stat.swits[2]) / 196F;
                    }
                if (control.handb && Math.abs(speed) > stat.handb)
                    if (speed < 0.0F)
                        speed += stat.handb;
                    else
                        speed -= stat.handb;
                if (loop == -1 && car.y < 100) {
                    if (control.left) {
                        if (!pl) {
                            if (lcomp == 0.0F)
                                lcomp = 5F * stat.airs;
                            if (lcomp < 20F)
                                lcomp += 2.0F * stat.airs;
                        }
                    } else {
                        if (lcomp > 0.0F)
                            lcomp -= 2.0F * stat.airs;
                        pl = false;
                    }
                    if (control.right) {
                        if (!pr) {
                            if (rcomp == 0.0F)
                                rcomp = 5F * stat.airs;
                            if (rcomp < 20F)
                                rcomp += 2.0F * stat.airs;
                        }
                    } else {
                        if (rcomp > 0.0F)
                            rcomp -= 2.0F * stat.airs;
                        pr = false;
                    }
                    if (control.up) {
                        if (!pu) {
                            if (ucomp == 0.0F)
                                ucomp = 5F * stat.airs;
                            if (ucomp < 20F)
                                ucomp += 2.0F * stat.airs;
                        }
                    } else {
                        if (ucomp > 0.0F)
                            ucomp -= 2.0F * stat.airs;
                        pu = false;
                    }
                    if (control.down) {
                        if (!pd) {
                            if (dcomp == 0.0F)
                                dcomp = 5F * stat.airs;
                            if (dcomp < 20F)
                                dcomp += 2.0F * stat.airs;
                        }
                    } else {
                        if (dcomp > 0.0F)
                            dcomp -= 2.0F * stat.airs;
                        pd = false;
                    }
                    pzy += (dcomp - ucomp) * RadicalMath.cos(pxy);
                    if (upsideDown)
                        car.xz += (dcomp - ucomp) * RadicalMath.sin(pxy);
                    else
                        car.xz -= (dcomp - ucomp) * RadicalMath.sin(pxy);
                    pxy += rcomp - lcomp;
                }
            }
        float wheelRoll = (20F * speed) / (154F * stat.simag);
        if (wheelRoll > 20F)
            wheelRoll = 20F;
        car.wzy -= wheelRoll;
        if (car.wzy < -45)
            car.wzy += 45;
        if (car.wzy > 45)
            car.wzy -= 45;
        if (control.right) {
            car.wxz -= stat.turn;
            if (car.wxz < -36)
                car.wxz = -36;
        }
        if (control.left) {
            car.wxz += stat.turn;
            if (car.wxz > 36)
                car.wxz = 36;
        }
        if (car.wxz != 0 && !control.left && !control.right)
            if (Math.abs(speed) < 10F) {
                if (Math.abs(car.wxz) == 1)
                    car.wxz = 0;
                if (car.wxz > 0)
                    car.wxz--;
                if (car.wxz < 0)
                    car.wxz++;
            } else {
                if (Math.abs(car.wxz) < stat.turn * 2)
                    car.wxz = 0;
                if (car.wxz > 0)
                    car.wxz -= stat.turn * 2;
                if (car.wxz < 0)
                    car.wxz += stat.turn * 2;
            }
        int steerDivisor = (int) (3600F / (speed * speed));
        if (steerDivisor < 5)
            steerDivisor = 5;
        if (speed < 0.0F)
            steerDivisor = -steerDivisor;
        if (wtouch) {
            if (!capsized) {
                if (!control.handb)
                    fxz = car.wxz / (steerDivisor * 3);
                else
                    fxz = car.wxz / steerDivisor;
                car.xz += car.wxz / steerDivisor;
                car.xz += FeatureBridge.raceTurn(this, car, control, car.wxz / steerDivisor); // a race extra's (the tailwhip): 0 without one
            }
            wtouch = false;
            gtouch = false;
        } else {
            car.xz += fxz;
        }
        if (speed > 30F || speed < -100F) {
            do {
                if (Math.abs(mxz - cxz) <= 180)
                    break;
                if (cxz > mxz)
                    cxz -= 360;
                else if (cxz < mxz)
                    cxz += 360;
            } while (true);
            if (Math.abs(mxz - cxz) < 30) {
                cxz += (mxz - cxz) / 4F;
            } else {
                if (cxz > mxz)
                    cxz -= 10;
                if (cxz < mxz)
                    cxz += 10;
            }
        }
        float wheelX[] = new float[4];
        float wheelZ[] = new float[4];
        float wheelY[] = new float[4];
        int tyre = 0;
        do {
            wheelX[tyre] = car.keyx[tyre] + car.x;
            wheelY[tyre] = wheelDrop + car.y;
            wheelZ[tyre] = car.z + car.keyz[tyre];
            scy[tyre] += 7F;
        } while (++tyre < 4);
        Utility.rot(wheelX, wheelY, car.x, car.y, pxy, 4);
        Utility.rot(wheelY, wheelZ, car.y, car.z, pzy, 4);
        Utility.rot(wheelX, wheelZ, car.x, car.z, car.xz, 4);
        boolean gripped = false;
        int meanSpeedX = (int) ((scx[0] + scx[1] + scx[2] + scx[3]) / 4F);
        int meanSpeedZ = (int) ((scz[0] + scz[1] + scz[2] + scz[3]) / 4F);
        int surface = 0;
        do {
            if (scx[surface] - meanSpeedX > 200F)
                scx[surface] = 200 + meanSpeedX;
            if (scx[surface] - meanSpeedX < -200F)
                scx[surface] = meanSpeedX - 200;
            if (scz[surface] - meanSpeedZ > 200F)
                scz[surface] = 200 + meanSpeedZ;
            if (scz[surface] - meanSpeedZ < -200F)
                scz[surface] = meanSpeedZ - 200;
        } while (++surface < 4);
        surface = 0;
        do {
            wheelY[surface] += scy[surface];
            wheelX[surface] += (scx[0] + scx[1] + scx[2] + scx[3]) / 4F;
            wheelZ[surface] += (scz[0] + scz[1] + scz[2] + scz[3]) / 4F;
        } while (++surface < 4);
        surface = 1;
        for (int piece = 0; piece < trackers.nt; piece++)
            if (Math.abs(trackers.zy[piece]) != 90 && Math.abs(trackers.xy[piece]) != 90
                    && Math.abs(car.x - trackers.x[piece]) < trackers.radx[piece]
                    && Math.abs(car.z - trackers.z[piece]) < trackers.radz[piece])
                surface = trackers.skd[piece];

        if (mtouch) {
            float grip = stat.grip;
            grip -= (Math.abs(txz - car.xz) * speed) / 250F;
            if (control.handb)
                grip -= Math.abs(txz - car.xz) * 4;
            if (grip < stat.grip) {
                if (skid != 2)
                    skid = 1;
                speed -= speed / 100F;
            } else if (skid == 1)
                skid = 2;
            if (surface == 1)
                grip = (float) (grip * 0.75D);
            if (surface == 2)
                grip = (float) (grip * 0.55000000000000004D);
            int targetX = -(int) (speed * RadicalMath.sin(car.xz) * RadicalMath.cos(pzy));
            int targetZ = (int) (speed * RadicalMath.cos(car.xz) * RadicalMath.cos(pzy));
            int targetY = -(int) (speed * RadicalMath.sin(pzy));

            if (!control.up) {
                if (speed <= stat.swits[2]) {
                    if (speed > 0.0F)
                        speed -= 0.5F;
                } else {
                    if (speed > 0.0F)
                        speed -= speeddec;
                }
            }

            if (capsized || dest || checkpoints.haltall) {
                targetX = 0;
                targetZ = 0;
                targetY = 0;
                grip = stat.grip / 5F;
                if (speed > 0.0F)
                    speed -= 2.0F;
                else
                    speed += 2.0F;
            }
            if (grip < 1.0F)
                grip = 1.0F;
            grip = FeatureBridge.raceGrip(im, grip); // a race extra's (nitrous burning is slippery): untouched without one
            // the Suspension handling extra's springs and tyres move the wheels (120 times a second): -1 without it
            float tyreGrip = FeatureBridge.tyres(this, car, targetX, targetY, targetZ, grip, txz,
                    capsized || dest || checkpoints.haltall);
            boolean tyres = tyreGrip >= 0.0F;
            if (tyres)
                grip = tyreGrip; // the grip shown: the dust and skids below follow the tyres' slide
            float sumX = 0.0F;
            float sumZ = 0.0F;
            int wheel = 0;
            do {
                if (!tyres) {
                    if (Math.abs(scx[wheel] - targetX) > grip) {
                        if (scx[wheel] < targetX)
                            scx[wheel] += grip;
                        else
                            scx[wheel] -= grip;
                    } else {
                        scx[wheel] = targetX;
                    }
                    if (Math.abs(scz[wheel] - targetZ) > grip) {
                        if (scz[wheel] < targetZ)
                            scz[wheel] += grip;
                        else
                            scz[wheel] -= grip;
                    } else {
                        scz[wheel] = targetZ;
                    }
                    if (Math.abs(scy[wheel] - targetY) > grip) {
                        if (scy[wheel] < targetY)
                            scy[wheel] += grip;
                        else
                            scy[wheel] -= grip;
                    } else {
                        scy[wheel] = targetY;
                    }
                }
                if (grip < stat.grip) {
                    if (txz != car.xz)
                        dcnt++;
                    else if (dcnt != 0)
                        dcnt = 0;
                    if (dcnt > (40F * grip) / stat.grip || capsized) {
                        float dustSize = 1.0F;
                        if (surface != 0)
                            dustSize = 1.2F;
                        if (world.random() > 0.75D) {
                            car.dust(wheel, wheelX[wheel], wheelY[wheel], wheelZ[wheel], scx[wheel], scz[wheel], dustSize * stat.simag, true,
                                    (int) tilt);
                            if (!capsized)
                                nfm.lit.audio.Sound3D.carSkid(im, surface, (float) Math.sqrt(scx[wheel] * scx[wheel] + scz[wheel] * scz[wheel]));
                            if (human() && !capsized)
                                events.skid(im, surface, (float) Math.sqrt(scx[wheel] * scx[wheel] + scz[wheel] * scz[wheel]));
                        }
                    } else {
                        if (surface == 1 && world.random() > 0.84999999999999998D)
                            car.dust(wheel, wheelX[wheel], wheelY[wheel], wheelZ[wheel], scx[wheel], scz[wheel], 1.1F * stat.simag, false,
                                    (int) tilt);
                        if ((surface == 2 || surface == 3) && world.random() > 0.69999999999999996D)
                            car.dust(wheel, wheelX[wheel], wheelY[wheel], wheelZ[wheel], scx[wheel], scz[wheel], 1.15F * stat.simag, false,
                                    (int) tilt);
                    }
                } else if (dcnt != 0) {
                    dcnt -= 2;
                    if (dcnt < 0)
                        dcnt = 0;
                }
                if (surface == 3) {
                    int bumpWheel = (int) (world.random() * 4F);
                    scy[bumpWheel] = (float) (-100F * world.random() * (speed / stat.swits[2])
                            * (stat.bounce - 0.29999999999999999D));
                }
                if (surface == 4) {
                    int bumpWheel = (int) (world.random() * 4F);
                    scy[bumpWheel] = (float) (-150F * world.random() * (speed / stat.swits[2])
                            * (stat.bounce - 0.29999999999999999D));
                }
                sumX += scx[wheel];
                sumZ += scz[wheel];
            } while (++wheel < 4);
            txz = car.xz;
            if (sumX > 0.0F)
                sign = -1;
            else
                sign = 1;
            double cosHeading = sumZ / Math.sqrt(sumX * sumX + sumZ * sumZ);
            mxz = (int) ((StrictMath.acos(cosHeading) / 0.017453292519943295D) * sign);
            if (skid == 2) {
                if (!capsized) {
                    sumX /= 4F;
                    sumZ /= 4F;
                    if (reversed)
                        speed = -((float) Math.sqrt(sumX * sumX + sumZ * sumZ) * RadicalMath.cos(mxz - car.xz));
                    else
                        speed = (float) Math.sqrt(sumX * sumX + sumZ * sumZ) * RadicalMath.cos(mxz - car.xz);
                }
                skid = 0;
            }
            if (capsized && sumX == 0.0F && sumZ == 0.0F)
                surface = 0;
            mtouch = false;
            gripped = true;
        } else if (skid != 2)
            skid = 2;
        int onGround = 0;
        boolean settled[] = new boolean[4];
        int onRoad = 0;
        do {
            if (wheelY[onRoad] > 245F) {
                onGround++;
                wtouch = true;
                gtouch = true;
                if (!gripped && scy[onRoad] != 7F) {
                    float dustSize = scy[onRoad] / 333.33F;
                    if (dustSize > 0.29999999999999999D)
                        dustSize = 0.3F;
                    if (surface == 0)
                        dustSize = (float) (dustSize + 1.1000000000000001D);
                    else
                        dustSize = (float) (dustSize + 1.2D);
                    car.dust(onRoad, wheelX[onRoad], wheelY[onRoad], wheelZ[onRoad], scx[onRoad], scz[onRoad], dustSize * stat.simag, true, 0);
                }
                wheelY[onRoad] = 250F;
                float bounce = 0.0F;
                do
                    if (onRoad != bounce && wheelY[(int) bounce] <= 245F)
                        wheelY[(int) bounce] -= wheelY[onRoad] - 250F;
                while (++bounce < 4F);
                bounce = Math.abs(RadicalMath.sin(pxy)) + Math.abs(RadicalMath.sin(pzy));
                bounce /= 3F;
                if (bounce > 0.40000000000000002D)
                    bounce = 0.4F;
                bounce += stat.bounce;
                if (bounce < 1.1000000000000001D)
                    bounce = 1.1F;
                regy(onRoad, Math.abs(scy[onRoad] * bounce), car);
                if (scy[onRoad] > 0.0F)
                    scy[onRoad] -= Math.abs(scy[onRoad] * bounce);
            }
            settled[onRoad] = false;
        } while (++onRoad < 4);
        onRoad = 0;
        for (int piece = 0; piece < trackers.nt; piece++) {
            int onSlip = 0;
            int onTop = 0;
            int wheel = 0;
            do
                if (!settled[wheel] && wheelX[wheel] > trackers.x[piece] - trackers.radx[piece]
                        && wheelX[wheel] < trackers.x[piece] + trackers.radx[piece]
                        && wheelZ[wheel] > trackers.z[piece] - trackers.radz[piece]
                        && wheelZ[wheel] < trackers.z[piece] + trackers.radz[piece]
                        && wheelY[wheel] > trackers.y[piece] - trackers.rady[piece]
                        && wheelY[wheel] < trackers.y[piece] + trackers.rady[piece]) {
                    if (trackers.xy[piece] == 0 && trackers.zy[piece] == 0 && trackers.y[piece] != 250
                            && wheelY[wheel] > trackers.y[piece] - 5) {
                        onTop++;
                        wtouch = true;
                        gtouch = true;
                        if (!gripped && scy[wheel] != 7F) {
                            float dustSize = scy[wheel] / 333.33F;
                            if (dustSize > 0.29999999999999999D)
                                dustSize = 0.3F;
                            if (surface == 0)
                                dustSize = (float) (dustSize + 1.1000000000000001D);
                            else
                                dustSize = (float) (dustSize + 1.2D);
                            car.dust(wheel, wheelX[wheel], wheelY[wheel], wheelZ[wheel], scx[wheel], scz[wheel], dustSize * stat.simag, true, 0);
                        }
                        wheelY[wheel] = trackers.y[piece];
                        float bounce = 0.0F;
                        do
                            if (wheel != bounce && wheelY[(int) bounce] <= trackers.y[piece] - 5)
                                wheelY[(int) bounce] -= wheelY[wheel] - trackers.y[piece];
                        while (++bounce < 4F);
                        bounce = Math.abs(RadicalMath.sin(pxy)) + Math.abs(RadicalMath.sin(pzy));
                        bounce /= 3F;
                        if (bounce > 0.40000000000000002D)
                            bounce = 0.4F;
                        bounce += stat.bounce;
                        if (bounce < 1.1000000000000001D)
                            bounce = 1.1F;
                        regy(wheel, Math.abs(scy[wheel] * bounce), car);
                        if (scy[wheel] > 0.0F)
                            scy[wheel] -= Math.abs(scy[wheel] * bounce);
                        settled[wheel] = true;
                    }
                    if (trackers.zy[piece] == -90 && wheelZ[wheel] < trackers.z[piece] + trackers.radz[piece]
                            && scz[wheel] < 0.0F) {
                        wheelZ[wheel] = trackers.z[piece] + trackers.radz[piece];
                        float bounce = 0.0F;
                        do
                            if (wheel != bounce && wheelZ[(int) bounce] >= trackers.z[piece] + trackers.radz[piece])
                                wheelZ[(int) bounce] -= wheelZ[wheel] - (trackers.z[piece] + trackers.radz[piece]);
                        while (++bounce < 4F);
                        bounce = Math.abs(RadicalMath.cos(pxy)) + Math.abs(RadicalMath.cos(pzy));
                        bounce /= 4F;
                        if (bounce > 0.29999999999999999D)
                            bounce = 0.3F;
                        if (gripped)
                            bounce = 0.0F;
                        bounce = (float) (bounce + (stat.bounce - 0.20000000000000001D));
                        if (bounce < 1.1000000000000001D)
                            bounce = 1.1F;
                        regz(wheel, Math.abs(scz[wheel] * bounce * trackers.dam[piece]), car);
                        scz[wheel] += Math.abs(scz[wheel] * bounce);
                        skid = 2;
                        hitWall = true;
                        settled[wheel] = true;
                        control.wall = piece;
                    }
                    if (trackers.zy[piece] == 90 && wheelZ[wheel] > trackers.z[piece] - trackers.radz[piece]
                            && scz[wheel] > 0.0F) {
                        wheelZ[wheel] = trackers.z[piece] - trackers.radz[piece];
                        float bounce = 0.0F;
                        do
                            if (wheel != bounce && wheelZ[(int) bounce] <= trackers.z[piece] - trackers.radz[piece])
                                wheelZ[(int) bounce] -= wheelZ[wheel] - (trackers.z[piece] - trackers.radz[piece]);
                        while (++bounce < 4F);
                        bounce = Math.abs(RadicalMath.cos(pxy)) + Math.abs(RadicalMath.cos(pzy));
                        bounce /= 4F;
                        if (bounce > 0.29999999999999999D)
                            bounce = 0.3F;
                        if (gripped)
                            bounce = 0.0F;
                        bounce = (float) (bounce + (stat.bounce - 0.20000000000000001D));
                        if (bounce < 1.1000000000000001D)
                            bounce = 1.1F;
                        regz(wheel, -Math.abs(scz[wheel] * bounce * trackers.dam[piece]), car);
                        scz[wheel] -= Math.abs(scz[wheel] * bounce);
                        skid = 2;
                        hitWall = true;
                        settled[wheel] = true;
                        control.wall = piece;
                    }
                    if (trackers.xy[piece] == -90 && wheelX[wheel] < trackers.x[piece] + trackers.radx[piece]
                            && scx[wheel] < 0.0F) {
                        wheelX[wheel] = trackers.x[piece] + trackers.radx[piece];
                        float bounce = 0.0F;
                        do
                            if (wheel != bounce && wheelX[(int) bounce] >= trackers.x[piece] + trackers.radx[piece])
                                wheelX[(int) bounce] -= wheelX[wheel] - (trackers.x[piece] + trackers.radx[piece]);
                        while (++bounce < 4F);
                        bounce = Math.abs(RadicalMath.cos(pxy)) + Math.abs(RadicalMath.cos(pzy));
                        bounce /= 4F;
                        if (bounce > 0.29999999999999999D)
                            bounce = 0.3F;
                        if (gripped)
                            bounce = 0.0F;
                        bounce = (float) (bounce + (stat.bounce - 0.20000000000000001D));
                        if (bounce < 1.1000000000000001D)
                            bounce = 1.1F;
                        regx(wheel, Math.abs(scx[wheel] * bounce * trackers.dam[piece]), car);
                        scx[wheel] += Math.abs(scx[wheel] * bounce);
                        skid = 2;
                        hitWall = true;
                        settled[wheel] = true;
                        control.wall = piece;
                    }
                    if (trackers.xy[piece] == 90 && wheelX[wheel] > trackers.x[piece] - trackers.radx[piece]
                            && scx[wheel] > 0.0F) {
                        wheelX[wheel] = trackers.x[piece] - trackers.radx[piece];
                        float bounce = 0.0F;
                        do
                            if (wheel != bounce && wheelX[(int) bounce] <= trackers.x[piece] - trackers.radx[piece])
                                wheelX[(int) bounce] -= wheelX[wheel] - (trackers.x[piece] - trackers.radx[piece]);
                        while (++bounce < 4F);
                        bounce = Math.abs(RadicalMath.cos(pxy)) + Math.abs(RadicalMath.cos(pzy));
                        bounce /= 4F;
                        if (bounce > 0.29999999999999999D)
                            bounce = 0.3F;
                        if (gripped)
                            bounce = 0.0F;
                        bounce = (float) (bounce + (stat.bounce - 0.20000000000000001D));
                        if (bounce < 1.1000000000000001D)
                            bounce = 1.1F;
                        regx(wheel, -Math.abs(scx[wheel] * bounce * trackers.dam[piece]), car);
                        scx[wheel] -= Math.abs(scx[wheel] * bounce);
                        skid = 2;
                        hitWall = true;
                        settled[wheel] = true;
                        control.wall = piece;
                    }
                    if (trackers.zy[piece] != 0 && trackers.zy[piece] != 90 && trackers.zy[piece] != -90) {
                        int rampAngle = 90 + trackers.zy[piece];
                        float steepness = 1.0F + (50 - Math.abs(trackers.zy[piece])) / 30F;
                        if (steepness < 1.0F)
                            steepness = 1.0F;
                        float alongY = trackers.y[piece] + ((wheelY[wheel] - trackers.y[piece]) * RadicalMath.cos(rampAngle)
                                - (wheelZ[wheel] - trackers.z[piece]) * RadicalMath.sin(rampAngle));
                        float across = trackers.z[piece] + ((wheelY[wheel] - trackers.y[piece]) * RadicalMath.sin(rampAngle)
                                + (wheelZ[wheel] - trackers.z[piece]) * RadicalMath.cos(rampAngle));
                        if (across > trackers.z[piece] && across < trackers.z[piece] + 200) {
                            scy[wheel] -= (across - trackers.z[piece]) / steepness;
                            across = trackers.z[piece];
                        }
                        if (across > trackers.z[piece] - 30) {
                            if (trackers.skd[piece] == 2)
                                onSlip++;
                            else
                                onRoad++;
                            wtouch = true;
                            gtouch = false;
                            if (!gripped && surface != 0) {
                                float dustSize = 1.4F;
                                car.dust(wheel, wheelX[wheel], wheelY[wheel], wheelZ[wheel], scx[wheel], scz[wheel], dustSize * stat.simag, true, 0);
                            }
                        }
                        wheelY[wheel] = trackers.y[piece] + ((alongY - trackers.y[piece]) * RadicalMath.cos(-rampAngle)
                                - (across - trackers.z[piece]) * RadicalMath.sin(-rampAngle));
                        wheelZ[wheel] = trackers.z[piece] + ((alongY - trackers.y[piece]) * RadicalMath.sin(-rampAngle)
                                + (across - trackers.z[piece]) * RadicalMath.cos(-rampAngle));
                        settled[wheel] = true;
                    }
                    if (trackers.xy[piece] != 0 && trackers.xy[piece] != 90 && trackers.xy[piece] != -90) {
                        int rampAngle = 90 + trackers.xy[piece];
                        float steepness = 1.0F + (50 - Math.abs(trackers.xy[piece])) / 30F;
                        if (steepness < 1.0F)
                            steepness = 1.0F;
                        float alongY = trackers.y[piece] + ((wheelY[wheel] - trackers.y[piece]) * RadicalMath.cos(rampAngle)
                                - (wheelX[wheel] - trackers.x[piece]) * RadicalMath.sin(rampAngle));
                        float across = trackers.x[piece] + ((wheelY[wheel] - trackers.y[piece]) * RadicalMath.sin(rampAngle)
                                + (wheelX[wheel] - trackers.x[piece]) * RadicalMath.cos(rampAngle));
                        if (across > trackers.x[piece] && across < trackers.x[piece] + 200) {
                            scy[wheel] -= (across - trackers.x[piece]) / steepness;
                            across = trackers.x[piece];
                        }
                        if (across > trackers.x[piece] - 30) {
                            if (trackers.skd[piece] == 2)
                                onSlip++;
                            else
                                onRoad++;
                            wtouch = true;
                            gtouch = false;
                            if (!gripped && surface != 0) {
                                float dustSize = 1.4F;
                                car.dust(wheel, wheelX[wheel], wheelY[wheel], wheelZ[wheel], scx[wheel], scz[wheel], dustSize * stat.simag, true, 0);
                            }
                        }
                        wheelY[wheel] = trackers.y[piece] + ((alongY - trackers.y[piece]) * RadicalMath.cos(-rampAngle)
                                - (across - trackers.x[piece]) * RadicalMath.sin(-rampAngle));
                        wheelX[wheel] = trackers.x[piece] + ((alongY - trackers.y[piece]) * RadicalMath.sin(-rampAngle)
                                + (across - trackers.x[piece]) * RadicalMath.cos(-rampAngle));
                        settled[wheel] = true;
                    }
                }
            while (++wheel < 4);
            if (onSlip == 4)
                mtouch = true;
            if (onTop == 4)
                onGround = 4;
        }

        if (onRoad == 4)
            mtouch = true;
        int pitchLeft = 0;
        int pitchRight = 0;
        int rollFront = 0;
        int rollBack = 0;
        if (scy[2] != scy[0]) {
            if (scy[2] < scy[0])
                sign = -1;
            else
                sign = 1;
            double cosine = Math
                    .sqrt((wheelZ[0] - wheelZ[2]) * (wheelZ[0] - wheelZ[2]) + (wheelY[0] - wheelY[2]) * (wheelY[0] - wheelY[2])
                            + (wheelX[0] - wheelX[2]) * (wheelX[0] - wheelX[2]))
                    / (Math.abs(car.keyz[0]) + Math.abs(car.keyz[2]));
            if (cosine >= 0.99980000000000002D)
                pitchLeft = sign;
            else
                pitchLeft = (int) ((StrictMath.acos(cosine) / 0.017453292519943295D) * sign);
        }
        if (scy[3] != scy[1]) {
            if (scy[3] < scy[1])
                sign = -1;
            else
                sign = 1;
            double cosine = Math
                    .sqrt((wheelZ[1] - wheelZ[3]) * (wheelZ[1] - wheelZ[3]) + (wheelY[1] - wheelY[3]) * (wheelY[1] - wheelY[3])
                            + (wheelX[1] - wheelX[3]) * (wheelX[1] - wheelX[3]))
                    / (Math.abs(car.keyz[1]) + Math.abs(car.keyz[3]));
            if (cosine >= 0.99980000000000002D)
                pitchRight = sign;
            else
                pitchRight = (int) ((StrictMath.acos(cosine) / 0.017453292519943295D) * sign);
        }
        if (scy[1] != scy[0]) {
            if (scy[1] < scy[0])
                sign = -1;
            else
                sign = 1;
            double cosine = Math
                    .sqrt((wheelZ[0] - wheelZ[1]) * (wheelZ[0] - wheelZ[1]) + (wheelY[0] - wheelY[1]) * (wheelY[0] - wheelY[1])
                            + (wheelX[0] - wheelX[1]) * (wheelX[0] - wheelX[1]))
                    / (Math.abs(car.keyx[0]) + Math.abs(car.keyx[1]));
            if (cosine >= 0.99980000000000002D)
                rollFront = sign;
            else
                rollFront = (int) ((StrictMath.acos(cosine) / 0.017453292519943295D) * sign);
        }
        if (scy[3] != scy[2]) {
            if (scy[3] < scy[2])
                sign = -1;
            else
                sign = 1;
            double cosine = Math
                    .sqrt((wheelZ[2] - wheelZ[3]) * (wheelZ[2] - wheelZ[3]) + (wheelY[2] - wheelY[3]) * (wheelY[2] - wheelY[3])
                            + (wheelX[2] - wheelX[3]) * (wheelX[2] - wheelX[3]))
                    / (Math.abs(car.keyx[2]) + Math.abs(car.keyx[3]));
            if (cosine >= 0.99980000000000002D)
                rollBack = sign;
            else
                rollBack = (int) ((StrictMath.acos(cosine) / 0.017453292519943295D) * sign);
        }
        if (hitWall) {
            int angle;
            for (angle = Math.abs(car.xz + 45); angle > 180; angle -= 360)
                ;
            if (Math.abs(angle) > 90)
                pmlt = 1;
            else
                pmlt = -1;
            for (angle = Math.abs(car.xz - 45); angle > 180; angle -= 360)
                ;
            if (Math.abs(angle) > 90)
                nmlt = 1;
            else
                nmlt = -1;
        }
        car.xz += forca
                * (((((scz[0] * nmlt - scz[1] * pmlt) + scz[2] * pmlt) - scz[3] * nmlt)
                + scx[0] * pmlt + scx[1] * nmlt) - scx[2] * nmlt
                - scx[3] * pmlt);
        if (Math.abs(pitchRight) > Math.abs(pitchLeft))
            pitchLeft = pitchRight;
        if (Math.abs(rollBack) > Math.abs(rollFront))
            rollFront = rollBack;
        if (!upsideDown)
            pzy += pitchLeft;
        else
            pzy -= pitchLeft;
        if (!rolledOver)
            pxy += rollFront;
        else
            pxy -= rollFront;
        if (onGround == 4) {
            int levelled = 0;
            while (pzy < 360) {
                pzy += 360;
                car.zy += 360;
            }
            while (pzy > 360) {
                pzy -= 360;
                car.zy -= 360;
            }
            if (pzy < 190 && pzy > 170) {
                pzy = 180;
                car.zy = 180;
                levelled++;
            }
            if (pzy > 350 || pzy < 10) {
                pzy = 0;
                car.zy = 0;
                levelled++;
            }
            while (pxy < 360) {
                pxy += 360;
                car.xy += 360;
            }
            while (pxy > 360) {
                pxy -= 360;
                car.xy -= 360;
            }
            if (pxy < 190 && pxy > 170) {
                pxy = 180;
                car.xy = 180;
                levelled++;
            }
            if (pxy > 350 || pxy < 10) {
                pxy = 0;
                car.xy = 0;
                levelled++;
            }
            if (levelled == 2)
                mtouch = true;
        }
        if (!mtouch && wtouch) {
            if (cntouch == 10)
                mtouch = true;
            else
                cntouch++;
        } else {
            cntouch = 0;
        }
        car.y = (int) (((wheelY[0] + wheelY[1] + wheelY[2] + wheelY[3]) / 4F - wheelDrop * RadicalMath.cos(pzy) * RadicalMath.cos(pxy)) + airY);
        if (upsideDown)
            sign = -1;
        else
            sign = 1;
        car.x = (int) (((((((((((wheelX[0] - car.keyx[0] * RadicalMath.cos(car.xz))
                + sign * car.keyz[0] * RadicalMath.sin(car.xz) + wheelX[1]) - car.keyx[1] * RadicalMath.cos(car.xz))
                + sign * car.keyz[1] * RadicalMath.sin(car.xz) + wheelX[2]) - car.keyx[2] * RadicalMath.cos(car.xz))
                + sign * car.keyz[2] * RadicalMath.sin(car.xz) + wheelX[3]) - car.keyx[3] * RadicalMath.cos(car.xz))
                + sign * car.keyz[3] * RadicalMath.sin(car.xz)) / 4F + wheelDrop * RadicalMath.sin(pxy) * RadicalMath.cos(car.xz))
                - wheelDrop * RadicalMath.sin(pzy) * RadicalMath.sin(car.xz)) + airX);
        car.z = (int) ((((((((((wheelZ[0] - sign * car.keyz[0] * RadicalMath.cos(car.xz)
                - car.keyx[0] * RadicalMath.sin(car.xz)) + wheelZ[1]) - sign * car.keyz[1] * RadicalMath.cos(car.xz)
                - car.keyx[1] * RadicalMath.sin(car.xz)) + wheelZ[2]) - sign * car.keyz[2] * RadicalMath.cos(car.xz)
                - car.keyx[2] * RadicalMath.sin(car.xz)) + wheelZ[3]) - sign * car.keyz[3] * RadicalMath.cos(car.xz)
                - car.keyx[3] * RadicalMath.sin(car.xz)) / 4F + wheelDrop * RadicalMath.sin(pxy) * RadicalMath.sin(car.xz))
                - wheelDrop * RadicalMath.sin(pzy) * RadicalMath.cos(car.xz)) + airZ);
        if (Math.abs(speed) > 10F || !mtouch) {
            if (Math.abs(pxy - car.xy) >= 4) {
                if (pxy > car.xy)
                    car.xy += 2 + (pxy - car.xy) / 2;
                else
                    car.xy -= 2 + (car.xy - pxy) / 2;
            } else {
                car.xy = pxy;
            }
            if (Math.abs(pzy - car.zy) >= 4) {
                if (pzy > car.zy)
                    car.zy += 2 + (pzy - car.zy) / 2;
                else
                    car.zy -= 2 + (car.zy - pzy) / 2;
            } else {
                car.zy = pzy;
            }
        }
        if (wtouch && !capsized && !FeatureBridge.springs()) { // with the suspension handling its springs lean the body
            float maxTilt = (float) ((speed / stat.swits[2]) * 14F
                    * (stat.bounce - 0.40000000000000002D));
            if (control.left && tilt < maxTilt && tilt >= 0.0F)
                tilt += 0.40000000000000002D;
            else if (control.right && tilt > -maxTilt && tilt <= 0.0F)
                tilt -= 0.40000000000000002D;
            else if (Math.abs(tilt) > 3D * (stat.bounce - 0.40000000000000002D)) {
                if (tilt > 0.0F)
                    tilt -= 3D * (stat.bounce - 0.29999999999999999D);
                else
                    tilt += 3D * (stat.bounce - 0.29999999999999999D);
            } else {
                tilt = 0.0F;
            }
            car.xy += tilt;
            if (gtouch)
                car.y -= tilt / 1.5D;
        } else if (tilt != 0.0F)
            tilt = 0.0F;
        if (wtouch && surface == 2) {
            car.zy += (int) (((world.random() * 25F * speed) / stat.swits[2]
                    - (15F * speed) / stat.swits[2]) * (stat.bounce - 0.99999999999999989D));
            car.xy += (int) (((world.random() * 25F * speed) / stat.swits[2]
                    - (15F * speed) / stat.swits[2]) * (stat.bounce - 0.99999999999999989D));
        }
        if (wtouch && surface == 1) {
            car.zy += (int) (((world.random() * 20F * speed) / stat.swits[2]
                    - (10F * speed) / stat.swits[2]) * (stat.bounce - 0.99999999999999989D));
            car.xy += (int) (((world.random() * 20F * speed) / stat.swits[2]
                    - (10F * speed) / stat.swits[2]) * (stat.bounce - 0.99999999999999989D));
        }
        if (hitmag > stat.maxmag && !dest) {
            distruct(car);
            if (cntdest == 7)
                dest = true;
            else
                cntdest++;
            if (cntdest == 1)
                rpd.dest[im] = 300;
        }
        if (car.dist == 0) {
            for (int poly = 0; poly < car.npl; poly++) {
                if (car.p[poly].chip != 0)
                    car.p[poly].chip = 0;
                if (car.p[poly].embos != 0)
                    car.p[poly].embos = 13;
            }

        }
        int nearest = 0;
        int bestDistance = 0;
        int checkCount = 0;
        if (nofocus)
            factor = 1;
        else
            factor = 7;
        for (int point = 0; point < checkpoints.n; point++) {
            if (checkpoints.typ[point] > 0) {
                checkCount++;
                if (checkpoints.typ[point] == 1) {
                    if (clear == checkCount + nlaps * checkpoints.nsp)
                        factor = 1;
                    if (Math.abs(car.z - checkpoints.z[point]) < 60F
                            + Math.abs(scz[0] + scz[1] + scz[2] + scz[3]) / 4F
                            && Math.abs(car.x - checkpoints.x[point]) < 700
                            && Math.abs(car.y - checkpoints.y[point]) < 800
                            && clear == (checkCount + nlaps * checkpoints.nsp) - 1) {
                        clear = checkCount + nlaps * checkpoints.nsp;
                        pcleared = point;
                        focus = -1;
                    }
                }
                if (checkpoints.typ[point] == 2) {
                    if (clear == checkCount + nlaps * checkpoints.nsp)
                        factor = 1;
                    if (Math.abs(car.x - checkpoints.x[point]) < 60F
                            + Math.abs(scx[0] + scx[1] + scx[2] + scx[3]) / 4F
                            && Math.abs(car.z - checkpoints.z[point]) < 700
                            && Math.abs(car.y - checkpoints.y[point]) < 800
                            && clear == (checkCount + nlaps * checkpoints.nsp) - 1) {
                        clear = checkCount + nlaps * checkpoints.nsp;
                        pcleared = point;
                        focus = -1;
                    }
                }
            }
            if (Utility.py(car.x / 100, checkpoints.x[point] / 100, car.z / 100, checkpoints.z[point] / 100) * factor < bestDistance
                    || bestDistance == 0) {
                nearest = point;
                bestDistance = Utility.py(car.x / 100, checkpoints.x[point] / 100, car.z / 100, checkpoints.z[point] / 100) * factor;
            }
        }

        if (clear == checkCount + nlaps * checkpoints.nsp)
            nlaps++;
        if (human()) {
            int next = clear;
            while (next >= checkpoints.nsp)
                next -= checkpoints.nsp;
            events.showCheckpoint(im, next);
            if (clear == checkpoints.nlaps * checkpoints.nsp - 1)
                events.showLastCheckpoint(im, true);
            if (checkpoints.haltall)
                events.showLastCheckpoint(im, false);
        }
        if (focus == -1) {
            if (human())
                nearest += 2;
            else
                nearest++;
            if (!nofocus) {
                int nextCheck;
                for (nextCheck = pcleared + 1; checkpoints.typ[nextCheck] <= 0; )
                    if (++nextCheck == checkpoints.n)
                        nextCheck = 0;

                if (nearest > nextCheck && (clear != nlaps * checkpoints.nsp || nearest < pcleared)) {
                    nearest = nextCheck;
                    focus = nearest;
                }
            }
            if (nearest >= checkpoints.n)
                nearest -= checkpoints.n;
            if (checkpoints.typ[nearest] == -3)
                nearest = 0;
            if (human()) {
                if (missedcp != -1)
                    missedcp = -1;
            } else if (missedcp != 0)
                missedcp = 0;
        } else {
            nearest = focus;
            if (human()) {
                if (missedcp == 0 && mtouch && Math.sqrt(
                        Utility.py(car.x / 10, checkpoints.x[focus] / 10, car.z / 10, checkpoints.z[focus] / 10)) > 800D)
                    missedcp = 1;
                if (missedcp == -2 && Math.sqrt(
                        Utility.py(car.x / 10, checkpoints.x[focus] / 10, car.z / 10, checkpoints.z[focus] / 10)) < 400D)
                    missedcp = 0;
                if (missedcp != 0 && mtouch && Math.sqrt(
                        Utility.py(car.x / 10, checkpoints.x[focus] / 10, car.z / 10, checkpoints.z[focus] / 10)) < 250D)
                    missedcp = 68;
            } else {
                missedcp = 1;
            }
            if (nofocus) {
                focus = -1;
                missedcp = 0;
            }
        }
        if (nofocus)
            nofocus = false;
        point = nearest;
        for (int fixer = 0; fixer < checkpoints.fn; fixer++)
            if (!checkpoints.roted[fixer]) {
                if (Math.abs(car.z - checkpoints.fz[fixer]) < 200 && Utility.py(car.x / 100, checkpoints.fx[fixer] / 100,
                        car.y / 100, checkpoints.fy[fixer] / 100) < 30) {
                    if (car.dist == 0) {
                        car.fcnt = 8;
                    } else {
                        if (human() && !car.fix) {
                            events.carFixed(im);
                        }
                        car.fix = true;
                    }
                    rpd.fix[im] = 300;
                }
            } else if (Math.abs(car.x - checkpoints.fx[fixer]) < 200
                    && Utility.py(car.z / 100, checkpoints.fz[fixer] / 100, car.y / 100, checkpoints.fy[fixer] / 100) < 30) {
                if (car.dist == 0) {
                    car.fcnt = 8;
                } else {
                    if (human() && !car.fix) {
                        events.carFixed(im);
                    }
                    car.fix = true;
                }
                rpd.fix[im] = 300;
            }

        if (car.fcnt == 7 || car.fcnt == 8) {
            squash = 0;
            nbsq = 0;
            hitmag = 0;
            cntdest = 0;
            dest = false;
            newcar = true;
            FeatureBridge.repaired(im); // the extras' own damage is fixed with the car's
        }
        if (!mtouch) {
            if (trcnt != 1) {
                trcnt = 1;
                lxz = car.xz;
            }
            if (loop == 2 || loop == -1) {
                travxy += rcomp - lcomp;
                if (Math.abs(travxy) > 135)
                    rtab = true;
                travzy += ucomp - dcomp;
                if (travzy > 135)
                    ftab = true;
                if (travzy < -135)
                    btab = true;
            }
            if (lxz != car.xz) {
                travxz += lxz - car.xz;
                lxz = car.xz;
            }
            if (srfcnt < 10) {
                if (control.wall != -1)
                    surfer = true;
                srfcnt++;
            }
        } else if (!dest) {
            if (!capsized) {
                if (capcnt != 0)
                    capcnt = 0;
                if (gtouch && trcnt != 0) {
                    if (trcnt == 9) {
                        powerup = 0.0F;
                        if (Math.abs(travxy) > 90)
                            powerup += Math.abs(travxy) / 24F;
                        else if (rtab)
                            powerup += 30F;
                        if (Math.abs(travzy) > 90) {
                            powerup += Math.abs(travzy) / 18F;
                        } else {
                            if (ftab)
                                powerup += 40F;
                            if (btab)
                                powerup += 40F;
                        }
                        if (Math.abs(travxz) > 90)
                            powerup += Math.abs(travxz) / 18F;
                        if (surfer)
                            powerup += 30F;
                        power += powerup;
                        if (im == 0 && (int) powerup > rpd.powered && rpd.wasted == 0
                                && (powerup > 60F || checkpoints.stage <= 2)) {
                            rpdcatch = 30;
                            if (rpd.hcaught)
                                rpd.powered = (int) powerup;
                        }
                        if (power > 98F) {
                            power = 98F;
                            if (powerup > 150F)
                                xtpower = 200;
                            else
                                xtpower = 100;
                        }
                    }
                    if (trcnt == 10) {
                        travxy = 0;
                        travzy = 0;
                        travxz = 0;
                        ftab = false;
                        rtab = false;
                        btab = false;
                        trcnt = 0;
                        srfcnt = 0;
                        surfer = false;
                    } else {
                        trcnt++;
                    }
                }
            } else {
                if (trcnt != 0) {
                    travxy = 0;
                    travzy = 0;
                    travxz = 0;
                    ftab = false;
                    rtab = false;
                    btab = false;
                    trcnt = 0;
                    srfcnt = 0;
                    surfer = false;
                }
                if (capcnt == 0) {
                    int slow = 0;
                    int wheel = 0;
                    do
                        if (Math.abs(scz[wheel]) < 70F && Math.abs(scx[wheel]) < 70F)
                            slow++;
                    while (++wheel < 4);
                    if (slow == 4)
                        capcnt = 1;
                } else {
                    capcnt++;
                    if (capcnt == 30) {
                        speed = 0.0F;
                        car.y += stat.flipy;
                        pxy += 180;
                        car.xy += 180;
                        capcnt = 0;
                    }
                }
            }
            if (trcnt == 0 && speed != 0.0F)
                if (xtpower == 0) {
                    if (power > 0.0F)
                        power -= (power * power * power) / stat.powerloss;
                    else
                        power = 0.0F;
                } else {
                    xtpower--;
                }
        }
        if (human()) {
            if (control.wall != -1)
                control.wall = -1;
            // in split screen the other player can waste a human too: the credit wears off as for the rest
            if (GameFacts.humans > 1 && lastcolido != 0 && !dest)
                lastcolido--;
        } else if (lastcolido != 0 && !dest)
            lastcolido--;
        if (dest) {
            if (checkpoints.dested[im] == 0)
                if (lastcolido == 0)
                    checkpoints.dested[im] = 1;
                else
                    checkpoints.dested[im] = 2;
        } else if (checkpoints.dested[im] != 0)
            checkpoints.dested[im] = 0;
        if (im == 0 && rpd.wasted == 0 && rpdcatch != 0) {
            rpdcatch--;
            if (rpdcatch == 0) {
                rpd.cotchinow(0);
                if (rpd.hcaught)
                    rpd.whenwasted = (int) (185F + world.random() * 20F);
            }
        }
    }
}
