package nfm.lit;
import fallk.logmaster.HLogger;

import javax.imageio.ImageIO;

import java.awt.Image;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.net.URL;

/**
 * Contains often used functions, and some new ones
 *
 * @author Rafa
 * @author Kaffeinated
 * @author Omar Waly
 */
public class Utility {

    private static long startTime;

    /**
     * Gets a value from a string in format:
     * string(value1,value2,value3...)
     *
     * @param name   The variable name (e.g: foo(bar) = foo)
     * @param source The string (single line) to get the value from
     * @param index  The position of the value (starting from 0)
     * @return An integer containing the value
     */
    public static int getvalue(final String name, final String source, final int index) {
        int var = 0;
        String part = "";
        for (int k = name.length() + 1; k < source.length(); k++) {
            final String strChar = new StringBuilder().append("").append(source.charAt(k)).toString();
            if ((",").equals(strChar) || (")").equals(strChar)) {
                var++;
                k++;
            }
            if (var == index) {
                part = new StringBuilder().append(part).append(source.charAt(k)).toString();
            }
        }
        return Float.valueOf(part).intValue();
    }

    /**
     * Turns a 3D point into a 2D X perspective coordinate.
     *
     * @param x The 3D X point
     * @param z The 3D Z point (its depth)
     * @return The 2D X coordinate.
     */
    public static int xs(final int x, int z) {
        if (z < Medium.cz) {
            z = Medium.cz;
        }
        return (z - Medium.focus_point) * (Medium.cx - x) / z + x;
    }

    public static int cXs(final int x, int z) {
        if (z < 50) {
            z = 50;
        }
        return (z - Medium.focus_point) * (Medium.cx - x) / z + x;
    }

    /**
     * Turns a 3D point into a 2D Y perspective coordinate.
     *
     * @param y The 3D Y point
     * @param z The 3D Z point (its depth)
     * @param m There are two different ys() methods within NFM2, this integer is
     *          used to unify them while providing a way to distinguish between
     *          them.
     *          0 is for using Medium.cz (normal)
     *          1 is for using 10 (only found in Medium)
     * @return The 2D Y coordinate.
     */
    public static int ys(final int y, int z, int m) {
        int value = 0;
        switch (m) {
            case 0:
                value = Medium.cz;
                break;
            case 1:
                value = 10;
                break;
            default:
                break;
        }
        if (z < value) {
            z = value;
        }
        return (z - Medium.focus_point) * (Medium.cy - y) / z + y;
    }

    public static int cYs(final int y, int z) {
        if (z < 50) {
            z = 50;
        }
        return (z - Medium.focus_point) * (Medium.cy - y) / z + y;
    }

    /**
     * Gets an integer from a string in format:
     * string(int1,int2,int3...)
     *
     * @param name   The beginning of the string (e.g: foo(0,0,1) = foo)
     * @param source The string (single line) to get the value from
     * @param index  The position of the value (starting from 0)
     * @return An integer containing the value
     */
    public static int getint(final String name, final String source, final int index) {
        int var = 0;
        String part = "";
        for (int k = name.length() + 1; k < source.length(); k++) {
            final String strChar = new StringBuilder().append("").append(source.charAt(k)).toString();
            if ((",").equals(strChar) || (")").equals(strChar)) {
                var++;
                k++;
            }
            if (var == index) {
                part = new StringBuilder().append(part).append(source.charAt(k)).toString();
            }
        }
        return Integer.valueOf(part);
    }

    /**
     * Gets a substring, nicely
     *
     * @param name   The beginning of the string (e.g: foo(0,0,1) = foo)
     * @param source The string (single line) to get the value from
     * @param index  The position of the string (starting from 0)
     * @return A string
     */
    public static String getstring(final String name, final String source, final int index) {
        int var = 0;
        String part = "";
        for (int k = name.length() + 1; k < source.length(); k++) {
            final String strChar = new StringBuilder().append("").append(source.charAt(k)).toString();
            if ((",").equals(strChar) || (")").equals(strChar)) {
                var++;
                k++;
            }
            if (var == index) {
                part = new StringBuilder().append(part).append(source.charAt(k)).toString();
            }
        }
        return part;
    }

    public static BufferedImage webGet(String url) throws IOException {
        HLogger.info("Loaded " + url);
        return ImageIO.read(new URL(url));
    }

    public static int spy(int x, int z) {
        return (int) Math.sqrt((x - Medium.cx) * (x - Medium.cx) + z * z);
    }

    public static float pys(int x1, int x2, int z1, int z2) {
        return (float) Math.sqrt((x1 - x2) * (x1 - x2) + (z1 - z2) * (z1 - z2));
    }

    public static int rpy(float x1, float x2, float y1, float y2, float z1, float z2) {
        return (int) ((x1 - x2) * (x1 - x2) + (y1 - y2) * (y1 - y2) + (z1 - z2) * (z1 - z2));
    }

    public static int py(int x1, int x2, int z1, int z2) {
        return (x1 - x2) * (x1 - x2) + (z1 - z2) * (z1 - z2);
    }

    /**
     * Gets the X coordinate an image should be loaded at in order for it to be
     * centered.
     * 
     * @param image The image
     * @return The X coordinate
     */
    public static int centeredImageX(Image image) {
        return (GameFacts.screenWidth / 2) - (image.getWidth(null) / 2);
    }

    /**
     * Gets the Y coordinate an image should be loaded at in order for it to be
     * centered.
     * 
     * @param image The image
     * @return The Y coordinate
     */
    public static int centeredImageY(Image image) {
        return (GameFacts.screenWidth / 2) - (image.getHeight(null) / 2);
    }

    /**
     * Gets the X coordinate an arbitary width component should be loaded at in
     * order for it to be centered.
     * 
     * @param width The width of the component
     * @return The X coordinate
     */
    public static int centeredWidthX(int width) {
        return (GameFacts.screenWidth / 2) - (width / 2);
    }

    /**
     * Gets the Y coordinate an arbitary height component should be loaded at in
     * order for it to be centered.
     * 
     * @param height The height of the component
     * @return The Y coordinate
     */
    public static int centeredHeightY(int height) {
        return (GameFacts.screenHeight / 2) - (height / 2);
    }

    /*
     * Users of this method should use Eclipse's "Refactor > Inline Method" feature
     * to inline calls to it.
     * It's a very simple method and the few nanoseconds spent to call it could be
     * crucial especially if used
     * inside of a large loop.
     * 
     * The usage of Math.pow doesn't affect performance since the Hotspot will
     * inline (turn into x * x) calls to it.
     */
    public static int distance(final int x1, final int y1, final int x2, final int y2) {
        return (int) Math.sqrt(StrictMath.pow(x2 - x1, 2) + StrictMath.pow(y2 - y1, 2));
    }

    public static void rot(float xs[], float ys[], int centerX, int centerY, int angle, int count) {
        if (angle != 0) {
            for (int point = 0; point < count; point++) {
                float u = xs[point];
                float v = ys[point];
                xs[point] = centerX + ((u - centerX) * RadicalMath.cos(angle) - (v - centerY) * RadicalMath.sin(angle));
                ys[point] = centerY + ((u - centerX) * RadicalMath.sin(angle) + (v - centerY) * RadicalMath.cos(angle));
            }

        }
    }

    public static void rot(int xs[], int ys[], int centerX, int centerY, int angle, int count) {
        if (angle != 0) {
            for (int point = 0; point < count; point++) {
                int u = xs[point];
                int v = ys[point];
                xs[point] = centerX + (int) ((u - centerX) * RadicalMath.cos(angle) - (v - centerY) * RadicalMath.sin(angle));
                ys[point] = centerY + (int) ((u - centerX) * RadicalMath.sin(angle) + (v - centerY) * RadicalMath.cos(angle));
            }

        }
    }

    public static int[] rotSingle(int poly1, int poly2, final int center1, final int center2, final int angle,
            final float sin_ang, final float cos_ang) {
        if (angle != 0) {
            final int u = poly1;
            final int v = poly2;
            poly1 = center1 + (int) ((u - center1) * cos_ang - (v - center2) * sin_ang);
            poly2 = center2 + (int) ((u - center1) * sin_ang + (v - center2) * cos_ang);
        }
        return new int[] {
                poly1, poly2
        };
    }

    public static void startTimer() {
        HLogger.info("Timer started!");
        startTime = System.nanoTime();
    }

    public static void stopTimer() {
        long endTime = System.nanoTime();
        long finalTime = (endTime - startTime) / 1000000;
        startTime = 0;

        HLogger.info("Timer ended at " + finalTime + " ms");
    }
}
