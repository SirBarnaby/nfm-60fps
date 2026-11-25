package nfm.lit.features;

/**
 * A feature that draws the cars' bodies somewhere other than on their rigid
 * frames: the suspension handling's body riding on its springs, pitched,
 * rolled and lowered over the wheels. The game asks it for every polygon of a
 * race car's body (not its wheels', which {@link CarLook#bend} may bend), on
 * the processor and for the kept scene alike. At most one feature is asked
 * ({@link Features#carBody}). Its pose for a frame is worked out in
 * {@link Feature#queue}, which comes first, with the frame's
 * {@link DrawQueue#share}.
 * <p>
 * Called on the game thread only, many times a frame: nothing allocated.
 */
public interface CarBody {

    /**
     * Car {@code car}'s body polygon {@code plane}, corners {@code x, y, z}
     * ({@code n} of them, in the car's own numbers): if it is drawn
     * elsewhere, its corners as drawn into {@code outX, outY, outZ} and
     * true; false to draw it as it is.
     */
    boolean body(int car, int plane, int[] x, int[] y, int[] z, int n, int[] outX, int[] outY, int[] outZ);
}
