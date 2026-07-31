package org.telegram.messenger;

/**
 * PrimeGram: the guided tour of the settings, as a script.
 *
 * <p>This fork adds a lot, and almost none of it announces itself - a switch called "Боковая
 * панель" tells you what it is named, not what it does or why you would want it. The tour walks
 * the settings, stops on the handful of things worth knowing about, and says what each one is for
 * in a sentence.
 *
 * <p>The state is static because the settings are not one screen: each section is its own
 * fragment, and pressing "Далее" on a step that lives elsewhere opens that elsewhere. A tour held
 * in a fragment's fields would end at the first such step.
 *
 * <p>Steps naming a row that does not exist on the current build - a feature behind a switch that
 * is off - are skipped rather than shown pointing at nothing.
 */
public final class PrimeGuide {

    /** One stop on the tour. */
    public static final class Step {
        /** Which settings page this step lives on. */
        public final int section;
        /** The row to point at, or 0 for a step that speaks about the page as a whole. */
        public final int itemId;
        public final String title;
        public final String text;

        Step(int section, int itemId, String title, String text) {
            this.section = section;
            this.itemId = itemId;
            this.title = title;
            this.text = text;
        }
    }

    private static final String PREF_SHOWN = "primegram_guide_shown";

    private static Step[] steps;
    private static int current = -1;

    private PrimeGuide() {
    }

    /**
     * Installs the script. Called by the settings screen, which owns the section and row ids -
     * keeping the numbers there rather than duplicating them here means a renumbered row cannot
     * quietly point the tour at the wrong thing.
     */
    public static void setSteps(Step[] value) {
        steps = value;
    }

    public static Step step(int section, int itemId, String title, String text) {
        return new Step(section, itemId, title, text);
    }

    public static boolean isRunning() {
        return steps != null && current >= 0 && current < steps.length;
    }

    public static Step currentStep() {
        return isRunning() ? steps[current] : null;
    }

    public static int currentIndex() {
        return current;
    }

    public static int total() {
        return steps == null ? 0 : steps.length;
    }

    public static void start() {
        current = 0;
        MessagesController.getGlobalMainSettings().edit().putBoolean(PREF_SHOWN, true).apply();
    }

    /** Advances, and returns the step now current, or null when the tour is over. */
    public static Step next() {
        if (!isRunning()) {
            return null;
        }
        current++;
        return currentStep();
    }

    public static void stop() {
        current = -1;
    }

    /** Whether the user has ever started it - used to offer it once, unobtrusively. */
    public static boolean wasShown() {
        return MessagesController.getGlobalMainSettings().getBoolean(PREF_SHOWN, false);
    }
}
