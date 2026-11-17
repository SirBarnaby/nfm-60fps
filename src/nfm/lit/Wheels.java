package nfm.lit;
class Wheels {

    public int ground;

    private int mast;
    private final int[] rc = {
            120, 120, 120
    };
    private float size;
    private float depth;

    public Wheels() {
        size = 2.0F;
        depth = 3F;
        ground = 0;
        mast = 0;
    }

    public void setrims(int red, int green, int blue, int rimSize, int rimDepth) {
        rc[0] = red;
        rc[1] = green;
        rc[2] = blue;
        size = rimSize / 10F;
        depth = rimDepth / 10F;
    }

    public void make(Trackers trackers, Plane planes[], int slot, int x, int y, int z, int steering, int width,
                     int scale, int group) {
        int pointX[] = new int[16];
        int pointY[] = new int[16];
        int pointZ[] = new int[16];
        int color[] = {
                45, 45, 45
        };
        int axleX = 0;
        float widthScale = width / 10F;
        float sizeScale = scale / 10F;
        if (steering == 11) {
            axleX = (int) (x + 4F * widthScale);
        }
        byte side = -1;
        if (x < 0) {
            side = 1;
        }
        int code = 0;
        do {
            pointX[code] = (int) (x - 4F * widthScale);
        } while (++code < 16);
        pointY[0] = (int) (y - 12F * sizeScale);
        pointZ[0] = (int) (z + 5F * sizeScale);
        pointY[1] = (int) (y - 12F * sizeScale);
        pointZ[1] = (int) (z - 5F * sizeScale);
        pointY[2] = (int) (y - 5F * sizeScale);
        pointZ[2] = (int) (z - 12F * sizeScale);
        pointY[3] = (int) (y + 5F * sizeScale);
        pointZ[3] = (int) (z - 12F * sizeScale);
        pointY[4] = (int) (y + 12F * sizeScale);
        pointZ[4] = (int) (z - 5F * sizeScale);
        pointY[5] = (int) (y + 12F * sizeScale);
        pointZ[5] = (int) (z + 5F * sizeScale);
        pointY[6] = (int) (y + 5F * sizeScale);
        pointZ[6] = (int) (z + 12F * sizeScale);
        pointY[7] = (int) (y - 5F * sizeScale);
        pointZ[7] = (int) (z + 12F * sizeScale);
        pointY[8] = y;
        pointZ[8] = (int) (z + 10F * size);
        pointY[9] = (int) (y + 8.6600000000000001D * size);
        pointZ[9] = (int) (z + 5F * size);
        pointY[10] = (int) (y + 8.6600000000000001D * size);
        pointZ[10] = (int) (z - 5F * size);
        pointY[11] = y;
        pointZ[11] = (int) (z - 10F * size);
        pointY[12] = (int) (y - 8.6600000000000001D * size);
        pointZ[12] = (int) (z - 5F * size);
        pointY[13] = (int) (y - 8.6600000000000001D * size);
        pointZ[13] = (int) (z + 5F * size);
        pointY[14] = y;
        pointZ[14] = (int) (z + 10F * size);
        pointY[15] = (int) (y - 5F * sizeScale);
        pointZ[15] = (int) (z + 12F * sizeScale);
        planes[slot] = new Plane(trackers, pointX, pointZ, pointY, 16, color, false, group, 0, axleX, y, z, 7, 0, false, 0, false);
        mast++;
        planes[slot].master = mast;
        slot++;
        pointX[2] = (int) (x - depth * widthScale);
        pointY[2] = y;
        pointZ[2] = z;
        code = -16;
        if (group == 21) {
            code = -17;
        }
        pointY[0] = y;
        pointZ[0] = (int) (z + 10F * size);
        pointY[1] = (int) (y + 8.6600000000000001D * size);
        pointZ[1] = (int) (z + 5F * size);
        planes[slot] = new Plane(trackers, pointX, pointZ, pointY, 3, rc, false, code, 0, axleX, y, z, 7, 0, false, 0, false);
        slot++;
        pointY[0] = (int) (y + 8.6600000000000001D * size);
        pointZ[0] = (int) (z + 5F * size);
        pointY[1] = (int) (y + 8.6600000000000001D * size);
        pointZ[1] = (int) (z - 5F * size);
        planes[slot] = new Plane(trackers, pointX, pointZ, pointY, 3, rc, false, code, 0, axleX, y, z, 7, 0, false, 0, false);
        slot++;
        pointY[0] = (int) (y + 8.6600000000000001D * size);
        pointZ[0] = (int) (z - 5F * size);
        pointY[1] = y;
        pointZ[1] = (int) (z - 10F * size);
        planes[slot] = new Plane(trackers, pointX, pointZ, pointY, 3, rc, false, code, 0, axleX, y, z, 7, 0, false, 0, false);
        slot++;
        pointY[0] = y;
        pointZ[0] = (int) (z - 10F * size);
        pointY[1] = (int) (y - 8.6600000000000001D * size);
        pointZ[1] = (int) (z - 5F * size);
        planes[slot] = new Plane(trackers, pointX, pointZ, pointY, 3, rc, false, code, 0, axleX, y, z, 7, 0, false, 0, false);
        slot++;
        pointY[0] = (int) (y - 8.6600000000000001D * size);
        pointZ[0] = (int) (z - 5F * size);
        pointY[1] = (int) (y - 8.6600000000000001D * size);
        pointZ[1] = (int) (z + 5F * size);
        planes[slot] = new Plane(trackers, pointX, pointZ, pointY, 3, rc, false, code, 0, axleX, y, z, 7, 0, false, 0, false);
        slot++;
        pointY[0] = (int) (y - 8.6600000000000001D * size);
        pointZ[0] = (int) (z + 5F * size);
        pointY[1] = y;
        pointZ[1] = (int) (z + 10F * size);
        planes[slot] = new Plane(trackers, pointX, pointZ, pointY, 3, rc, false, code, 0, axleX, y, z, 7, 0, false, 0, false);
        slot++;
        pointX[0] = (int) (x - 4F * widthScale);
        pointY[0] = (int) (y - 12F * sizeScale);
        pointZ[0] = (int) (z + 5F * sizeScale);
        pointX[1] = (int) (x - 4F * widthScale);
        pointY[1] = (int) (y - 12F * sizeScale);
        pointZ[1] = (int) (z - 5F * sizeScale);
        pointX[2] = (int) (x + 4F * widthScale);
        pointY[2] = (int) (y - 12F * sizeScale);
        pointZ[2] = (int) (z - 5F * sizeScale);
        pointX[3] = (int) (x + 4F * widthScale);
        pointY[3] = (int) (y - 12F * sizeScale);
        pointZ[3] = (int) (z + 5F * sizeScale);
        planes[slot] = new Plane(trackers, pointX, pointZ, pointY, 4, color, false, group, -1 * side, axleX, y, z, 7, 0, false, 0, false);
        slot++;
        pointX[0] = (int) (x - 4F * widthScale);
        pointY[0] = (int) (y - 5F * sizeScale);
        pointZ[0] = (int) (z - 12F * sizeScale);
        pointX[1] = (int) (x - 4F * widthScale);
        pointY[1] = (int) (y - 12F * sizeScale);
        pointZ[1] = (int) (z - 5F * sizeScale);
        pointX[2] = (int) (x + 4F * widthScale);
        pointY[2] = (int) (y - 12F * sizeScale);
        pointZ[2] = (int) (z - 5F * sizeScale);
        pointX[3] = (int) (x + 4F * widthScale);
        pointY[3] = (int) (y - 5F * sizeScale);
        pointZ[3] = (int) (z - 12F * sizeScale);
        planes[slot] = new Plane(trackers, pointX, pointZ, pointY, 4, color, false, group, 1 * side, axleX, y, z, 7, 0, false, 0, false);
        slot++;
        pointX[0] = (int) (x - 4F * widthScale);
        pointY[0] = (int) (y - 5F * sizeScale);
        pointZ[0] = (int) (z - 12F * sizeScale);
        pointX[1] = (int) (x - 4F * widthScale);
        pointY[1] = (int) (y + 5F * sizeScale);
        pointZ[1] = (int) (z - 12F * sizeScale);
        pointX[2] = (int) (x + 4F * widthScale);
        pointY[2] = (int) (y + 5F * sizeScale);
        pointZ[2] = (int) (z - 12F * sizeScale);
        pointX[3] = (int) (x + 4F * widthScale);
        pointY[3] = (int) (y - 5F * sizeScale);
        pointZ[3] = (int) (z - 12F * sizeScale);
        planes[slot] = new Plane(trackers, pointX, pointZ, pointY, 4, color, false, group, -1 * side, axleX, y, z, 7, 0, false, 0, false);
        slot++;
        pointX[0] = (int) (x - 4F * widthScale);
        pointY[0] = (int) (y + 12F * sizeScale);
        pointZ[0] = (int) (z - 5F * sizeScale);
        pointX[1] = (int) (x - 4F * widthScale);
        pointY[1] = (int) (y + 5F * sizeScale);
        pointZ[1] = (int) (z - 12F * sizeScale);
        pointX[2] = (int) (x + 4F * widthScale);
        pointY[2] = (int) (y + 5F * sizeScale);
        pointZ[2] = (int) (z - 12F * sizeScale);
        pointX[3] = (int) (x + 4F * widthScale);
        pointY[3] = (int) (y + 12F * sizeScale);
        pointZ[3] = (int) (z - 5F * sizeScale);
        planes[slot] = new Plane(trackers, pointX, pointZ, pointY, 4, color, false, group, 1 * side, axleX, y, z, 7, 0, false, 0, false);
        slot++;
        pointX[0] = (int) (x - 4F * widthScale);
        pointY[0] = (int) (y + 12F * sizeScale);
        pointZ[0] = (int) (z - 5F * sizeScale);
        pointX[1] = (int) (x - 4F * widthScale);
        pointY[1] = (int) (y + 12F * sizeScale);
        pointZ[1] = (int) (z + 5F * sizeScale);
        pointX[2] = (int) (x + 4F * widthScale);
        pointY[2] = (int) (y + 12F * sizeScale);
        pointZ[2] = (int) (z + 5F * sizeScale);
        pointX[3] = (int) (x + 4F * widthScale);
        pointY[3] = (int) (y + 12F * sizeScale);
        pointZ[3] = (int) (z - 5F * sizeScale);
        planes[slot] = new Plane(trackers, pointX, pointZ, pointY, 4, color, false, group, -1 * side, axleX, y, z, 7, 0, false, 0, false);
        slot++;
        if (ground < (int) (y + 12F * sizeScale + 1.0F)) {
            ground = (int) (y + 12F * sizeScale + 1.0F);
        }
        pointX[0] = (int) (x - 4F * widthScale);
        pointY[0] = (int) (y + 5F * sizeScale);
        pointZ[0] = (int) (z + 12F * sizeScale);
        pointX[1] = (int) (x - 4F * widthScale);
        pointY[1] = (int) (y + 12F * sizeScale);
        pointZ[1] = (int) (z + 5F * sizeScale);
        pointX[2] = (int) (x + 4F * widthScale);
        pointY[2] = (int) (y + 12F * sizeScale);
        pointZ[2] = (int) (z + 5F * sizeScale);
        pointX[3] = (int) (x + 4F * widthScale);
        pointY[3] = (int) (y + 5F * sizeScale);
        pointZ[3] = (int) (z + 12F * sizeScale);
        planes[slot] = new Plane(trackers, pointX, pointZ, pointY, 4, color, false, group, 1 * side, axleX, y, z, 7, 0, false, 0, false);
        slot++;
        pointX[0] = (int) (x - 4F * widthScale);
        pointY[0] = (int) (y + 5F * sizeScale);
        pointZ[0] = (int) (z + 12F * sizeScale);
        pointX[1] = (int) (x - 4F * widthScale);
        pointY[1] = (int) (y - 5F * sizeScale);
        pointZ[1] = (int) (z + 12F * sizeScale);
        pointX[2] = (int) (x + 4F * widthScale);
        pointY[2] = (int) (y - 5F * sizeScale);
        pointZ[2] = (int) (z + 12F * sizeScale);
        pointX[3] = (int) (x + 4F * widthScale);
        pointY[3] = (int) (y + 5F * sizeScale);
        pointZ[3] = (int) (z + 12F * sizeScale);
        planes[slot] = new Plane(trackers, pointX, pointZ, pointY, 4, color, false, group, -1 * side, axleX, y, z, 7, 0, false, 0, false);
        slot++;
        pointX[0] = (int) (x - 4F * widthScale);
        pointY[0] = (int) (y - 12F * sizeScale);
        pointZ[0] = (int) (z + 5F * sizeScale);
        pointX[1] = (int) (x - 4F * widthScale);
        pointY[1] = (int) (y - 5F * sizeScale);
        pointZ[1] = (int) (z + 12F * sizeScale);
        pointX[2] = (int) (x + 4F * widthScale);
        pointY[2] = (int) (y - 5F * sizeScale);
        pointZ[2] = (int) (z + 12F * sizeScale);
        pointX[3] = (int) (x + 4F * widthScale);
        pointY[3] = (int) (y - 12F * sizeScale);
        pointZ[3] = (int) (z + 5F * sizeScale);
        planes[slot] = new Plane(trackers, pointX, pointZ, pointY, 4, color, false, group, 1 * side, axleX, y, z, 7, 0, false, 0, false);
        slot++;
    }
}
