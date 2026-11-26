package nfm.lit.features;

/**
 * A made-up race for the race extras' checks, as plain arrays a check sets:
 * a straight along z with a checkpoint every 1500 units, the cars beside
 * it on flat ground at 250, pointing along z. It counts the pushes and the
 * damage the extras give each car.
 */
public final class CheckRace implements RaceTick {
    public final int cars;
    public final int gates;
    public final float[] x;
    public final float[] y;
    public final float[] z;
    public final int[] place;
    public final boolean[] key;
    /** Wasted cars: out of the race, as the game leaves them burning. */
    public final boolean[] wasted;
    public final float[] pushed;
    public final float[] damaged;

    public CheckRace(int cars, int gates) {
        this.cars = cars;
        this.gates = gates;
        x = new float[cars];
        y = new float[cars];
        z = new float[cars];
        place = new int[cars];
        key = new boolean[cars];
        wasted = new boolean[cars];
        pushed = new float[cars];
        damaged = new float[cars];
        for (int c = 0; c < cars; c++) {
            x[c] = -900F - 400F * c; // beside the boxes
            y[c] = 220F;
            z[c] = -3000F;
            place[c] = c;
        }
    }

    /** The same race again, its cars where they are now (the pushes and damage not copied). */
    public CheckRace copy() {
        CheckRace t = new CheckRace(cars, gates);
        System.arraycopy(x, 0, t.x, 0, cars);
        System.arraycopy(y, 0, t.y, 0, cars);
        System.arraycopy(z, 0, t.z, 0, cars);
        System.arraycopy(place, 0, t.place, 0, cars);
        return t;
    }

    @Override
    public int cars() {
        return cars;
    }

    @Override
    public int humans() {
        return 1;
    }

    @Override
    public float x(int car) {
        return x[car];
    }

    @Override
    public float y(int car) {
        return y[car];
    }

    @Override
    public float z(int car) {
        return z[car];
    }

    @Override
    public int yaw(int car) {
        return 0;
    }

    @Override
    public float speed(int car) {
        return 0F;
    }

    @Override
    public float topSpeed(int car) {
        return 100F;
    }

    @Override
    public float vx(int car) {
        return 0F;
    }

    @Override
    public float vy(int car) {
        return 0F;
    }

    @Override
    public float vz(int car) {
        return 0F;
    }

    @Override
    public int radius(int car) {
        return 120;
    }

    @Override
    public float wheelX(int car, int wheel) {
        return x[car] + ((wheel & 1) == 0 ? -40F : 40F);
    }

    @Override
    public float wheelZ(int car, int wheel) {
        return z[car] + (wheel < 2 ? 70F : -70F);
    }

    @Override
    public boolean grounded(int car) {
        return true;
    }

    @Override
    public boolean wasted(int car) {
        return wasted[car];
    }

    @Override
    public boolean throttle(int car) {
        return false;
    }

    @Override
    public boolean steering(int car) {
        return false;
    }

    @Override
    public boolean useKey(int car) {
        return car == 0 && key[0];
    }

    @Override
    public int place(int car) {
        return place[car];
    }

    @Override
    public int wheelDrop(int car) {
        return 30;
    }

    @Override
    public int points() {
        return gates * 2;
    }

    @Override
    public float pointX(int point) {
        return 0F;
    }

    @Override
    public float pointY(int point) {
        return 250F;
    }

    @Override
    public float pointZ(int point) {
        return point * 750F;
    }

    @Override
    public int pointKind(int point) {
        return point % 2 == 1 ? 1 : 0;
    }

    @Override
    public float ground(float px, float py, float pz) {
        return 250F;
    }

    @Override
    public void push(int car, int wheel, float vx, float vy, float vz) {
        pushed[car] += (float) Math.sqrt(vx * vx + vy * vy + vz * vz);
    }

    @Override
    public void damage(int car, int wheel, float share) {
        damaged[car] += share;
    }
}
