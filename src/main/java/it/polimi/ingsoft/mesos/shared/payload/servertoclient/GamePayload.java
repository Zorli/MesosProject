package it.polimi.ingsoft.mesos.shared.payload.servertoclient;
import java.io.Serializable;
import java.util.List;
//Upper class of the payload
public class GamePayload implements Serializable {
    public final int id;
    public final String status;
    public final int currentEra;
    public final TurnOrderPayload turnOrder;
    public final TurnStatePayload turnState;
    public final List<PlayerPayload> players;
    public final BoardPayload board;

    public GamePayload(
            int id,
            String status,
            int currentEra,
            TurnOrderPayload turnOrder,
            TurnStatePayload turnState,
            List<PlayerPayload> players,
            BoardPayload board
    ) {
        this.id = id;
        this.status = status;
        this.currentEra = currentEra;
        this.turnOrder = turnOrder;
        this.turnState = turnState;
        this.players = players;
        this.board = board;
    }
}
