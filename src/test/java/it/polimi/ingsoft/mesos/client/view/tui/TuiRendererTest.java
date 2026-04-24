package it.polimi.ingsoft.mesos.client.view.tui;

import it.polimi.ingsoft.mesos.shared.payload.server.BoardPayload;
import it.polimi.ingsoft.mesos.shared.payload.server.CardPayload;
import it.polimi.ingsoft.mesos.shared.payload.server.GamePayload;
import it.polimi.ingsoft.mesos.shared.payload.server.LobbyListPayload;
import it.polimi.ingsoft.mesos.shared.payload.server.LobbySummaryPayload;
import it.polimi.ingsoft.mesos.shared.payload.server.OfferSpacePayload;
import it.polimi.ingsoft.mesos.shared.payload.server.PlayerPayload;
import it.polimi.ingsoft.mesos.shared.payload.server.PreMatchEventType;
import it.polimi.ingsoft.mesos.shared.payload.server.PreMatchUpdatePayload;
import it.polimi.ingsoft.mesos.shared.payload.server.StartingBlockPayload;
import it.polimi.ingsoft.mesos.shared.payload.server.TribePayload;
import it.polimi.ingsoft.mesos.shared.payload.server.TurnOrderPayload;
import it.polimi.ingsoft.mesos.shared.payload.server.TurnStatePayload;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertTrue;

class TuiRendererTest {
    private final TuiRenderer renderer = new TuiRenderer();
    private final TuiStateReducer reducer = new TuiStateReducer();

    @Test
    void renderDashboardIncludesLobbyContextAndOpenLobbies() {
        TuiState state = this.reducer.reduceConnection(TuiState.initial(), true);
        state = this.reducer.reducePreMatchUpdate(state, new PreMatchUpdatePayload(
                PreMatchEventType.AUTHENTICATED,
                "Alice",
                "Authentication completed successfully",
                null,
                null,
                null,
                null
        ));
        state = this.reducer.reducePreMatchUpdate(state, new PreMatchUpdatePayload(
                PreMatchEventType.LOBBY_JOINED,
                "Alice",
                "Lobby joined successfully",
                7,
                List.of("Alice", "Bob"),
                "Bob",
                4
        ));
        state = this.reducer.reduceLobbyList(state, new LobbyListPayload(List.of(
                new LobbySummaryPayload(7, "Bob", 2, 4, "OPEN")
        )));

        String rendered = this.renderer.render(state);

        assertTrue(rendered.contains("Identity: Alice"));
        assertTrue(rendered.contains("Lobby #7"));
        assertTrue(rendered.contains("Alice, Bob"));
        assertTrue(rendered.contains("Open Lobbies"));
        assertTrue(rendered.contains("#7 | host=Bob"));
    }

    @Test
    void renderDashboardIncludesGameSnapshotAndActionHintForCurrentPlayer() {
        TuiState state = this.reducer.reduceConnection(TuiState.initial(), true);
        state = this.reducer.reducePreMatchUpdate(state, new PreMatchUpdatePayload(
                PreMatchEventType.AUTHENTICATED,
                "Alice",
                "Authentication completed successfully",
                null,
                null,
                null,
                null
        ));
        state = this.reducer.reduceGameUpdate(state, new GamePayload(
                11,
                "IN_PROGRESS",
                0,
                new TurnOrderPayload(2, "PLAYER_DRAWS", "Alice", List.of("Alice", "Bob")),
                new TurnStatePayload("WAITING_FOR_CARD_SELECTION"),
                List.of(
                        new PlayerPayload(
                                "Alice",
                                5,
                                3,
                                "RED",
                                new TribePayload(
                                        List.of(new CardPayload(12, "Hunter", 0)),
                                        List.of(new CardPayload(22, "CavePaintingBonus", 0))
                                )
                        ),
                        new PlayerPayload(
                                "Bob",
                                4,
                                2,
                                "BLUE",
                                new TribePayload(List.of(), List.of())
                        )
                ),
                new BoardPayload(
                        List.of(new CardPayload(31, "Gatherer", 0)),
                        List.of(new CardPayload(41, "Builder", 0)),
                        List.of(new OfferSpacePayload("A1", 1, 0, "Alice")),
                        List.of(new StartingBlockPayload(0, 2, 0, "Bob"))
                )
        ));

        String rendered = this.renderer.render(state);

        assertTrue(rendered.contains("Game #11"));
        assertTrue(rendered.contains("current player=Alice"));
        assertTrue(rendered.contains("[you, current]"));
        assertTrue(rendered.contains("Hunter"));
        assertTrue(rendered.contains("A1 | top=1 | bottom=0 | occupiedBy=Alice"));
        assertTrue(rendered.contains("choose-card <card-id>"));
    }
}
