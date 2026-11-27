package nfm.lit.features;

import java.util.function.BooleanSupplier;
import java.util.function.IntConsumer;
import java.util.function.IntFunction;
import java.util.function.IntSupplier;
import java.util.function.Supplier;

/**
 * A page of a feature's own, such as the custom game's: rows on tabs, in the
 * settings screen's box, with its keys, mouse, scrolling and look. The
 * feature says what rows the page has; the game makes it a screen
 * ({@code SettingsScreen.page}) and opens it.
 */
public interface Page {

    /** Starts a tab, with a line on what it holds. */
    void tab(String name, String help);

    /** The next row starts a group, under this heading. */
    void group(String heading);

    /** A button: {@code action} is written on it, and {@code run} runs when it is pressed. */
    void button(String label, String action, String help, Runnable run);

    /**
     * A choice stepped through {@code values}, each written as {@code names}
     * says; {@code status}, or null, gives a line under the help on how it
     * stands now.
     */
    void choice(String label, String help, Supplier<String> status, int[] values, IntFunction<String> names,
            IntSupplier get, IntConsumer set);

    /** The row added last can only be changed while {@code when} holds; otherwise it is greyed and says {@code why}. */
    void onlyWhen(BooleanSupplier when, String why);
}
