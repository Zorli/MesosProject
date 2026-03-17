package it.polimi.ingsoft.mesos.server.persistence.savedmatch;

/**
 * Identifies where one persisted board card currently lives inside a match.
 */
public enum SavedMatchBoardCardZone {
    UPPER_TRIBE,
    UPPER_BUILDING,
    LOWER_TRIBE,
    LOWER_BUILDING,
    TRIBE_DECK,
    BUILDING_DECK
}
