package it.polimi.ingsoft.mesos.shared.payload.servertoclient;
import java.io.Serializable;
public class PlayerPayload implements Serializable {
    public final String nickname;
    public final int food;
    public final int prestigePoints;
    public final String totem;
    public final TribePayload tribe;

    public PlayerPayload(
            String nickname,
            int food,
            int prestigePoints,
            String totem,
            TribePayload tribe
    ) {
        this.nickname = nickname;
        this.food = food;
        this.prestigePoints = prestigePoints;
        this.totem = totem;
        this.tribe = tribe;
    }
}
