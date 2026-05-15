package it.polimi.ingsoft.mesos.shared.payload.servertoclient.game;
import java.io.Serializable;
public class PlayerPayload implements Serializable {
    public final String nickname;
    public final int food;
    public final int prestigePoints;
    public final String totem;
    public final TribePayload tribe;
    public final boolean isConnected;

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
