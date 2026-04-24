package it.polimi.ingsoft.mesos.client.view.tui;

import it.polimi.ingsoft.mesos.shared.payload.server.BoardPayload;
import it.polimi.ingsoft.mesos.shared.payload.server.GamePayload;
import it.polimi.ingsoft.mesos.shared.payload.server.PreMatchEventType;
import it.polimi.ingsoft.mesos.shared.payload.server.PreMatchUpdatePayload;
import it.polimi.ingsoft.mesos.shared.payload.server.TurnOrderPayload;
import it.polimi.ingsoft.mesos.shared.payload.server.TurnStatePayload;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TuiStateReducerTest {
    private final TuiStateReducer reducer = new TuiStateReducer();

    @Test
    void reduceAuthenticatedUpdateStoresNicknameAndHomeScreen() {
        TuiState updated = this.reducer.reducePreMatchUpdate(
                this.reducer.reduceConnection(TuiState.initial(), true),
                new PreMatchUpdatePayload(
                        PreMatchEventType.AUTHENTICATED,
                        "Alice",
                        "Authentication completed successfully",
                        null,
                        null,
                        null,
                        null
                )
        );

        assertTrue(updated.isConnected());
        assertTrue(updated.isAuthenticated());
        assertEquals("Alice", updated.getNickname());
        assertEquals(TuiScreen.HOME, updated.getScreen());
    }

    @Test
    void reduceLobbyStartedSwitchesToBootstrapScreen() {
        TuiState updated = this.reducer.reducePreMatchUpdate(
                this.reducer.reducePreMatchUpdate(
                        TuiState.initial(),
                        new PreMatchUpdatePayload(
                                PreMatchEventType.AUTHENTICATED,
                                "Alice",
                                "Authentication completed successfully",
                                null,
                                null,
                                null,
                                null
                        )
                ),
                new PreMatchUpdatePayload(
                        PreMatchEventType.LOBBY_STARTED,
                        "Alice",
                        "Lobby started",
                        8,
                        List.of("Alice", "Bob"),
                        "Alice",
                        2
                )
        );

        assertEquals(TuiScreen.MATCH_BOOTSTRAP, updated.getScreen());
        assertEquals(8, updated.getCurrentLobbyId());
        assertEquals(List.of("Alice", "Bob"), updated.getCurrentLobbyPlayers());
    }

    @Test
    void reduceGameUpdateSwitchesToInMatchScreen() {
        TuiState updated = this.reducer.reduceGameUpdate(
                TuiState.initial(),
                new GamePayload(
                        3,
                        "IN_PROGRESS",
                        0,
                        new TurnOrderPayload(1, "PLACING_TOTEMS", "Alice", List.of("Alice")),
                        new TurnStatePayload("WAITING_FOR_CARD_SELECTION"),
                        List.of(),
                        new BoardPayload(List.of(), List.of(), List.of(), List.of())
                )
        );

        assertEquals(TuiScreen.IN_MATCH, updated.getScreen());
        assertTrue(updated.hasGameSnapshot());
        assertEquals(3, updated.getLastGamePayload().id);
    }
}
