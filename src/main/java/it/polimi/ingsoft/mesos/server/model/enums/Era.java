package it.polimi.ingsoft.mesos.server.model.enums;

/**
 * Identifies the three eras that structure the progression of a match.
 */
public enum Era {
    /** First era of the match. */
    first,
    /** Second era of the match. */
    second,
    /** Third and final era of the match. */
    third;

    /**
     * Returns the following era in the game progression.
     *
     * @return next era, or {@code third} when already at the final era
     */
    public Era next() {
        return switch (this) {
            case first -> second;
            case second -> third;
            case third -> third;
        };
    }
}
