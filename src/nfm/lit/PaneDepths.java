package nfm.lit;

/**
 * The depths one split-screen pane's drawing measured, for its next drawing
 * to sort by. The game draws objects from the farthest by the distance each
 * measured as it was last drawn ({@code ContO.dist}), and an object's
 * polygons from the farthest by theirs ({@code Plane.av}): with one player
 * that is the frame before, but in split screen the last drawing was often
 * the other player's pane, seen from somewhere else, and a near wall could be
 * drawn behind a car it hides. Each pane keeps its own instead: put in before
 * it is drawn, taken back after.
 * <p>
 * Drawing only: the race reads the distances the draw step measures, which
 * every drawing puts back as it was (see GameSparker#restoreSceneDrawState).
 */
final class PaneDepths {

    /* the objects the depths were taken from, to know they are still the race's */
    private ContO[] objects = new ContO[0];
    private int count;
    private int[] distances = new int[0];
    private int[] polygonDepths = new int[0];
    private int polygons;

    /**
     * Puts this pane's depths into the first {@code count} of {@code scene}
     * before it is drawn: the polygons', and with {@code distances} the
     * objects' too. Nothing the first time, or for another race's objects.
     */
    void putIn(ContO[] scene, int count, boolean distances) {
        if (!same(scene, count)) {
            return;
        }
        int at = 0;
        for (int i = 0; i < count; i++) {
            ContO o = scene[i];
            if (distances) {
                o.dist = this.distances[i];
            }
            if (at + o.npl > polygons) {
                return; // a rewind changed the object since: its polygons sort as they are
            }
            for (int k = 0; k < o.npl; k++) {
                o.p[k].av = polygonDepths[at++];
            }
        }
    }

    /** Takes back the depths the pane's drawing of the first {@code count} of {@code scene} measured. */
    void takeBack(ContO[] scene, int count) {
        int polygons = 0;
        for (int i = 0; i < count; i++) {
            polygons += scene[i].npl;
        }
        if (objects.length < count) {
            objects = new ContO[count];
            distances = new int[count];
        }
        if (polygonDepths.length < polygons) {
            polygonDepths = new int[polygons];
        }
        int at = 0;
        for (int i = 0; i < count; i++) {
            ContO o = scene[i];
            objects[i] = o;
            distances[i] = o.dist;
            for (int k = 0; k < o.npl; k++) {
                polygonDepths[at++] = o.p[k].av;
            }
        }
        this.count = count;
        this.polygons = polygons;
    }

    private boolean same(ContO[] scene, int count) {
        if (count != this.count) {
            return false;
        }
        for (int i = 0; i < count; i++) {
            if (scene[i] != objects[i]) {
                return false;
            }
        }
        return true;
    }
}
