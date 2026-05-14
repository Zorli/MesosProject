package it.polimi.ingsoft.mesos.server.model.game.exception;

/**
 * Signals that the selected tile cannot be chosen because it is not available.
 */
public class TileNotAvailableException extends IllegalStateException {

    /**
     * Creates the exception for a tile addressed by numeric index.
     *
     * @param tileIndex index of the unavailable tile
     */
    public TileNotAvailableException(int tileIndex) {
        super("Selected tile is not available: " + tileIndex);
    }

    /**
     * Creates the exception for a tile addressed by board code.
     *
     * @param tileCode code of the unavailable tile
     */
    public TileNotAvailableException(String tileCode) {
        super("Selected tile is not available: " + tileCode);
    }
}
