package nfm.lit;

import java.lang.reflect.Field;

/**
 * Split screen's Player 2 as the HUD and the race's sounds know a player:
 * the captions on show and their timers, the arrow's mode, the stunt being
 * announced, which checkpoint was last cleared, the crash, skid and engine
 * sounds' turns and the voices they play with, which cars the player has seen wasted. {@link GameUi}
 * keeps Player 1's in its own fields, exactly as the classic game does;
 * this holds Player 2's, and {@link #exchange} swaps them in while Player
 * 2's HUD is drawn or sounds are decided, and back after. The fields are
 * read and written through {@link FieldSlot}: every tick swaps them twice,
 * and Java rebuilds each reflected field's accessor after 127 uses.
 */
final class PlayerHud {

    /** The GameUi fields that belong to one player. */
    private static final String[] NAMES = {"say", "wasay", "tcnt", "tflk", "auscnt", "asay", "aflk", "flk", "pwflk",
        "pwcnt", "looped", "cntan", "cntovn", "ana", "arrace", "clear", "cntwis", "crshturn", "crashup", "skflg",
        "dskflg", "skidup", "bfcrash", "bfskid", "pwait", "aird", "grrd", "stopcnt", "pwastd", "dmcnt", "dmflk",
        "loop", "spin", "sm", "spectate"};
    /** One player's arrays: their contents are swapped. */
    private static final String[] ARRAYS = {"dested", "pengs"};
    private static final FieldSlot[] FIELDS = fields(NAMES);
    private static final FieldSlot[] ARRAY_FIELDS = fields(ARRAYS);
    private static final int SPECTATE = NAMES.length - 1;
    private static final int VOICES = NAMES.length - 2;

    /** Player 2's values of {@link #FIELDS}: a primitive's bits, or the object a reference points at. */
    private final long[] bits = new long[FIELDS.length];
    private final Object[] refs = new Object[FIELDS.length];
    private final Object[] arrays = new Object[ARRAY_FIELDS.length];

    private static FieldSlot[] fields(String[] names) {
        Field[] f = new Field[names.length];
        for (int i = 0; i < names.length; i++) {
            try {
                f[i] = GameUi.class.getDeclaredField(names[i]);
            } catch (NoSuchFieldException ex) {
                throw new IllegalStateException("GameUi has no field " + names[i], ex);
            }
        }
        return StateCopy.slots(f);
    }

    /**
     * Player 2 as Player 1 is now in {@code ui}: called as the race is set
     * up, so both start alike. {@code car} is Player 2's car, whose HUD it is.
     */
    PlayerHud(GameUi ui, int car) {
        for (int i = 0; i < FIELDS.length; i++) {
            FieldSlot f = FIELDS[i];
            if (f.primitive()) {
                bits[i] = f.bits(ui);
            } else {
                refs[i] = f.ref(ui);
            }
        }
        for (int i = 0; i < ARRAY_FIELDS.length; i++) {
            Object a = ARRAY_FIELDS[i].ref(ui);
            arrays[i] = a instanceof int[] ints ? ints.clone() : ((boolean[]) a).clone();
        }
        bits[SPECTATE] = car; // spectate: whose car the HUD follows
        refs[VOICES] = ui.sm.forPlayer(car); // sm: the player's own voices
    }

    /** Swaps the player in {@code ui} with the one held here; call twice to swap back. */
    void exchange(GameUi ui) {
        for (int i = 0; i < FIELDS.length; i++) {
            FieldSlot f = FIELDS[i];
            if (f.primitive()) {
                long now = f.bits(ui);
                f.putBits(ui, bits[i]);
                bits[i] = now;
            } else {
                Object now = f.ref(ui);
                f.putRef(ui, refs[i]);
                refs[i] = now;
            }
        }
        for (int i = 0; i < ARRAY_FIELDS.length; i++) {
            Object a = ARRAY_FIELDS[i].ref(ui);
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
    }
}
