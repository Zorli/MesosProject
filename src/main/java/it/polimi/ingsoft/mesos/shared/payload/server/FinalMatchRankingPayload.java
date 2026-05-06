package it.polimi.ingsoft.mesos.shared.payload.server;

import java.io.Serializable;
import java.util.List;

public class FinalMatchRankingPayload implements Serializable {
    public final String gameId;
    public final FinalMatchRankingStatus status;
    public final List<FinalMatchRankingDeltaPayload> deltas;
    public final String message;

    public FinalMatchRankingPayload(String gameId,
                                    FinalMatchRankingStatus status,
                                    List<FinalMatchRankingDeltaPayload> deltas,
                                    String message) {
        this.gameId = gameId;
        this.status = status;
        this.deltas = deltas == null ? List.of() : List.copyOf(deltas);
        this.message = message;
    }
}
