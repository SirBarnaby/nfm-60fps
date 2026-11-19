package nfm.lit;

import java.awt.image.BufferedImage;
import java.awt.image.DataBufferInt;
import java.awt.image.Raster;
import java.awt.image.SinglePixelPackedSampleModel;
import java.util.stream.IntStream;

/**
 * Resizes the finished 1280x720 frame to the window's size with a smooth
 * (bilinear) filter, on the processor, split across its cores.
 * <p>
 * Java's own smooth scaling runs in software on most Windows machines (its
 * Direct3D path refuses Intel graphics) and takes about 25 ms for a 1920x1080
 * window, which alone would cap the game near 40 frames a second. This does
 * the same filter in a few milliseconds.
 */
final class FrameScaler {

    /** Rows per piece of work handed to a core. */
    private static final int BAND_ROWS = 64;

    private int sourceWidth;
    private int sourceHeight;
    private int targetWidth;
    private int targetHeight;
    private int imageType;
    /** For each target column: the left source column, and the right one's weight out of 256. */
    private int[] columnIndex;
    private int[] columnWeight;
    /** For each target row: the upper source row, and the lower one's weight out of 256. */
    private int[] rowIndex;
    private int[] rowWeight;
    private BufferedImage target;
    private int[] targetPixels;

    /**
     * Returns {@code source} resized to {@code width} x {@code height}, in an
     * image this scaler reuses until the next call, or null when the source
     * is not a plain packed-int image (the caller then scales it itself).
     */
    synchronized BufferedImage scale(BufferedImage source, int width, int height) {
        int type = source.getType();
        if (type != BufferedImage.TYPE_INT_RGB && type != BufferedImage.TYPE_INT_ARGB
                && type != BufferedImage.TYPE_INT_BGR) {
            return null;
        }
        Raster raster = source.getRaster();
        if (!(raster.getDataBuffer() instanceof DataBufferInt buffer)
                || !(raster.getSampleModel() instanceof SinglePixelPackedSampleModel model)
                || raster.getSampleModelTranslateX() != 0 || raster.getSampleModelTranslateY() != 0
                || source.getWidth() < 2 || source.getHeight() < 2 || width < 1 || height < 1) {
            return null;
        }
        prepare(source.getWidth(), source.getHeight(), width, height, type);
        int[] from = buffer.getData();
        int offset = buffer.getOffset();
        int stride = model.getScanlineStride();
        int bands = (height + BAND_ROWS - 1) / BAND_ROWS;
        IntStream.range(0, bands).parallel().forEach(band -> scaleRows(from, offset, stride,
                band * BAND_ROWS, Math.min(height, (band + 1) * BAND_ROWS)));
        return target;
    }

    private void prepare(int fromWidth, int fromHeight, int width, int height, int type) {
        if (target != null && fromWidth == sourceWidth && fromHeight == sourceHeight
                && width == targetWidth && height == targetHeight && type == imageType) {
            return;
        }
        sourceWidth = fromWidth;
        sourceHeight = fromHeight;
        targetWidth = width;
        targetHeight = height;
        imageType = type;
        columnIndex = new int[width];
        columnWeight = new int[width];
        fillTaps(fromWidth, width, columnIndex, columnWeight);
        rowIndex = new int[height];
        rowWeight = new int[height];
        fillTaps(fromHeight, height, rowIndex, rowWeight);
        target = new BufferedImage(width, height, type);
        targetPixels = ((DataBufferInt) target.getRaster().getDataBuffer()).getData();
    }

    /**
     * Pixel centres line up: target pixel t samples source position
     * (t + 0.5) * from / to - 0.5, between source pixels index and index + 1.
     * The index stays one short of the last pixel so the pair always exists.
     */
    private static void fillTaps(int from, int to, int[] index, int[] weight) {
        double ratio = from / (double) to;
        for (int t = 0; t < to; t++) {
            double position = Math.max(0D, (t + 0.5D) * ratio - 0.5D);
            int left = Math.min((int) position, from - 2);
            int w = (int) Math.round((position - left) * 256D);
            index[t] = left;
            weight[t] = Math.min(256, w);
        }
    }

    /**
     * Scales each source row across once, keeping the last two, then blends
     * those two down into each target row. When the picture is enlarged,
     * neighbouring target rows share source rows, so this does fewer blends
     * than filtering every target pixel from four source pixels.
     */
    private void scaleRows(int[] from, int offset, int stride, int firstRow, int endRow) {
        int[] to = targetPixels;
        int width = targetWidth;
        int[] upperRow = new int[width];
        int[] lowerRow = new int[width];
        int upperSource = -1;
        int lowerSource = -1;
        for (int y = firstRow; y < endRow; y++) {
            int wantUpper = rowIndex[y];
            if (wantUpper != upperSource) {
                if (wantUpper == lowerSource) {
                    int[] swap = upperRow;
                    upperRow = lowerRow;
                    lowerRow = swap;
                } else {
                    scaleAcross(from, offset + wantUpper * stride, upperRow);
                }
                upperSource = wantUpper;
                scaleAcross(from, offset + (wantUpper + 1) * stride, lowerRow);
                lowerSource = wantUpper + 1;
            }
            int down = rowWeight[y];
            int out = y * width;
            if (down == 0) {
                System.arraycopy(upperRow, 0, to, out, width);
            } else {
                for (int x = 0; x < width; x++) {
                    to[out + x] = mix(upperRow[x], lowerRow[x], down);
                }
            }
        }
    }

    private void scaleAcross(int[] from, int start, int[] row) {
        int[] columns = columnIndex;
        int[] weights = columnWeight;
        for (int x = 0; x < row.length; x++) {
            int at = start + columns[x];
            row[x] = mix(from[at], from[at + 1], weights[x]);
        }
    }

    /**
     * Blends two packed pixels, {@code weight}/256 of the second. Two channels
     * are blended at once in each 32-bit multiply: their 8 bits stay apart
     * because a weighted sum never exceeds 255 * 256.
     */
    static int mix(int first, int second, int weight) {
        int keep = 256 - weight;
        int redBlue = ((first & 0xFF00FF) * keep + (second & 0xFF00FF) * weight) >>> 8 & 0xFF00FF;
        int alphaGreen = ((first >>> 8 & 0xFF00FF) * keep + (second >>> 8 & 0xFF00FF) * weight) & 0xFF00FF00;
        return redBlue | alphaGreen;
    }
}
