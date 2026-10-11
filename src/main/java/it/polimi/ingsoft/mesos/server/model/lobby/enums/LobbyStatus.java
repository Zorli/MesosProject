package it.polimi.ingsoft.mesos.server.model.lobby.enums;

/**
 * Describes the lifecycle state of a lobby before and after match creation.
 *
 * <p>A lobby manages the pre-game organization of players and undergoes the following status transitions:
 * <ul>
 *   <li>{@link #WAITING}: Default initial state. The lobby is open for new players but does not have enough
 *       players to reach its chosen capacity.</li>
 *   <li>{@link #READY}: Triggered when the current player count matches the chosen capacity. In this state,
 *       only the host player is authorized to start the match. If any player leaves, the status reverts back to {@link #WAITING}.</li>
 *   <li>{@link #STARTED}: Transitioned when the host successfully commands the lobby to start. The lobby transition
 *       process will begin, creating the game instance and starting the game worker thread.</li>
 *   <li>{@link #CLOSED}: Terminal state when the lobby becomes empty or is explicitly disposed of. No further actions
 *       should be accepted.</li>
 * </ul>
 */
public enum LobbyStatus {
    /** The lobby exists but does not yet have enough players to start. */
    WAITING,
    /** The lobby has enough players and can be turned into a game. */
    READY,
    /** A match has been created from the lobby. */
    STARTED,
    /** The lobby has been closed and should no longer accept players. */
    CLOSED
}
