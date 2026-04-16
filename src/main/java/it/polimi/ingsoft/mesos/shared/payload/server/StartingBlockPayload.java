package it.polimi.ingsoft.mesos.shared.payload.server;
import java.io.Serializable;
public class StartingBlockPayload implements Serializable {
    public final int position;
    public final int foodModifier;
    public final int prestigeModifier;
    public final String occupiedByNickname;

    public StartingBlockPayload(
            int position,
            int foodModifier,
            int prestigeModifier,
            String occupiedByNickname
    ) {
        this.position = position;
        this.foodModifier = foodModifier;
        this.prestigeModifier = prestigeModifier;
        this.occupiedByNickname = occupiedByNickname;
    }
}
