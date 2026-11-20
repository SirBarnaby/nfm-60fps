package nfm.lit;

import java.lang.reflect.Field;

/**
 * Split screen's Player 2 as the HUD and the race's sounds know a player:
 * the captions on show and their timers, the arrow's mode, the stunt being
 * announced, which checkpoint was last cleared, the crash, skid and engine
 * sounds' turns and the voices they play with, which cars the player has seen wasted. {@link GameUi}
 * keeps Player 1's in its own fields, exactly as the classic game does;
 * this holds Player 2's, and {@link #exchange} swaps them in while Player
 * 2's HUD is drawn or sounds are decided, and back after.
 */
final class PlayerHud {

    /** The GameUi fields that belong to one player. */
    private static final String[] NAMES = {"say", "wasay", "tcnt", "tflk", "auscnt", "asay", "aflk", "flk", "pwflk",
        "pwcnt", "looped", "cntan", "cntovn", "ana", "arrace", "clear", "cntwis", "crshturn", "crashup", "skflg",
        "dskflg", "skidup", "bfcrash", "bfskid", "pwait", "aird", "grrd", "stopcnt", "pwastd", "dmcnt", "dmflk",
        "loop", "spin", "sm", "spectate"};
    /** One player's arrays: their contents are swapped. */
    private static final String[] ARRAYS = {"dested", "pengs"};
    private static final Field[] FIELDS = fields(NAMES);
    private static final Field[] ARRAY_FIELDS = fields(ARRAYS);

    private final Object[] values = new Object[FIELDS.length];
    private final Object[] arrays = new Object[ARRAY_FIELDS.length];

    private static Field[] fields(String[] names) {
        Field[] f = new Field[names.length];
        for (int i = 0; i < names.length; i++) {
            try {
                f[i] = GameUi.class.getDeclaredField(names[i]);
                f[i].setAccessible(true);
            } catch (NoSuchFieldException ex) {
                throw new IllegalStateException("GameUi has no field " + names[i], ex);
            }
        }
        return f;
    }

    /**
     * Player 2 as Player 1 is now in {@code ui}: called as the race is set
     * up, so both start alike. {@code car} is Player 2's car, whose HUD it is.
     */
    PlayerHud(GameUi ui, int car) {
        try {
            for (int i = 0; i < FIELDS.length; i++) {
                values[i] = FIELDS[i].get(ui);
            }
            for (int i = 0; i < ARRAY_FIELDS.length; i++) {
                Object a = ARRAY_FIELDS[i].get(ui);
                arrays[i] = a instanceof int[] ints ? ints.clone() : ((boolean[]) a).clone();
            }
        } catch (IllegalAccessException ex) {
            throw new IllegalStateException(ex);
        }
        values[NAMES.length - 1] = car; // spectate: whose car the HUD follows
        values[NAMES.length - 2] = ui.sm.forPlayer(car); // sm: the player's own voices
    }

    /** Swaps the player in {@code ui} with the one held here; call twice to swap back. */
    void exchange(GameUi ui) {
        try {
            for (int i = 0; i < FIELDS.length; i++) {
                Object now = FIELDS[i].get(ui);
                FIELDS[i].set(ui, values[i]);
                values[i] = now;
            }
            for (int i = 0; i < ARRAY_FIELDS.length; i++) {
                Object a = ARRAY_FIELDS[i].get(ui);
                if (a instanceof int[] ints) {
                    int[] mine = (int[]) arrays[i];
                    for (int k = 0; k < ints.length; k++) {
                        int t = ints[k];
                        ints[k] = mine[k];
                        mine[k] = t;
                    }
                } else {
                    boolean[] flags = (boolean[]) a;
                    boolean[] mine = (boolean[]) arrays[i];
                    for (int k = 0; k < flags.length; k++) {
                        boolean t = flags[k];
                        flags[k] = mine[k];
                        mine[k] = t;
                    }
                }
            }
        } catch (IllegalAccessException ex) {
            throw new IllegalStateException(ex);
        }
    }
}
