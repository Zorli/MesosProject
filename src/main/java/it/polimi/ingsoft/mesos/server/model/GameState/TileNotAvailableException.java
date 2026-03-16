package it.polimi.ingsoft.mesos.server.model.GameState;

/**
 * Signals that the selected tile cannot be chosen because it is not available.
 */
public class TileNotAvailableException extends IllegalStateException {

    public TileNotAvailableException(int tileIndex) {
        super("Selected tile is not available: " + tileIndex);
    }

    public TileNotAvailableException(String tileCode) {
        super("Selected tile is not available: " + tileCode);
    }
}
