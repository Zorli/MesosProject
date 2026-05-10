package it.polimi.ingsoft.mesos.shared.payload.servertoclient.game;
import java.io.Serializable;
import java.util.List;

public class TurnOrderPayload implements Serializable {
    public final int roundNumber;
    public final String currentRoundPhase;
    public final String currentPlayerNickname;
    public final List<String> turnOrder;

    public TurnOrderPayload(
            int roundNumber,
            String currentRoundPhase,
            String currentPlayerNickname,
            List<String> turnOrder
    ) {
        this.roundNumber = roundNumber;
        this.currentRoundPhase = currentRoundPhase;
        this.currentPlayerNickname = currentPlayerNickname;
        this.turnOrder = turnOrder;
    }
}
