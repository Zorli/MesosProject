package it.polimi.ingsoft.mesos.shared.payload.servertoclient.game;
import java.io.Serializable;
/**
 * Represents a snapshot/summary of a single player's personal state in the game.
 * <p>
 * This contains the player's nickname, resource counts (food and prestige points),
 * totem identifier, current tribe cards, and network connection status.
 */
public class PlayerPayload implements Serializable {
    /** The unique nickname identifying the player. */
    public final String nickname;

    /** The current amount of food resources possessed by the player. */
    public final int food;

    /** The current number of prestige/victory points accumulated by the player. */
    public final int prestigePoints;

    /** The identifier/color name of the player's totem piece. */
    public final String totem;

    /** The player's tribe containing all drafted character and building cards. */
    public final TribePayload tribe;

    /** Flag indicating whether the player is currently connected to the preLobby. */
    public final boolean isConnected;

    /**
     * Constructs a new PlayerPayload.
     *
     * @param nickname player nickname
     * @param food amount of food
     * @param prestigePoints number of prestige points
     * @param totem player totem name/color
     * @param tribe player tribe snapshot
     * @param isConnected connection status of the player
     */
    public PlayerPayload(
             String nickname,
             int food,
             int prestigePoints,
             String totem,
             TribePayload tribe, boolean isConnected
     ) {
        this.nickname = nickname;
        this.food = food;
        this.prestigePoints = prestigePoints;
        this.totem = totem;
        this.tribe = tribe;
        this.isConnected = isConnected;
    }
}
