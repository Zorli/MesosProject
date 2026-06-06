package it.polimi.ingsoft.mesos.server.model.game.enums;

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

    /**
     * Returns the 1-based number of this era ({@code first} = 1, {@code second} = 2, {@code third} = 3).
     *
     * @return the era number, from 1 to 3
     */
    public int getIntegerEra(){
        return this.ordinal()+1;
    }
}
