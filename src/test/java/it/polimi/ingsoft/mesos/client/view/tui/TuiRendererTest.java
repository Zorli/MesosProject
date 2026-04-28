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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TuiRendererTest {
    private final TuiRenderer renderer = new TuiRenderer();
    private final TuiStateReducer reducer = new TuiStateReducer();

    @Test
    void renderLobbyBrowserIncludesOpenLobbiesWithoutCurrentLobbyContext() {
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

        assertEquals(TuiScreen.LOBBY_BROWSER, state.getScreen());
        String rendered = this.renderer.render(state);

        assertTrue(rendered.contains("Identity: Alice"));
        assertTrue(rendered.contains("Open Lobbies"));
        assertTrue(rendered.contains("#7 | host=Bob"));
        assertTrue(rendered.contains("list-lobbies"));
        assertTrue(rendered.contains("create-lobby"));
        assertTrue(rendered.contains("join-lobby"));
        assertFalse(rendered.contains("Lobby #7"));
        assertFalse(rendered.contains("Alice, Bob"));
    }

    @Test
    void renderInMatchFiltersPreMatchAndLobbyBrowserSectionsFromHistoricalPayloads() {
        TuiState state = this.reducer.reduceConnection(TuiState.initial(), true);
        state = this.reducer.reducePreMatchUpdate(state, authenticated("Alice"));
        state = this.reducer.reduceLobbyList(state, openLobbyList());
        state = this.reducer.reducePreMatchUpdate(state, lobbyJoined());
        state = this.reducer.reducePreMatchUpdate(state, lobbyStarted());
        state = this.reducer.reduceGameUpdate(state, gameSnapshot());

        assertEquals(TuiScreen.IN_MATCH, state.getScreen());
        String rendered = this.renderer.render(state);

        assertTrue(rendered.contains("Game #11"));
        assertTrue(rendered.contains("current player=Alice"));
        assertTrue(rendered.contains("Board:"));
        assertTrue(rendered.contains("choose-card <card-id>"));
        assertFalse(rendered.contains("Open Lobbies"));
        assertFalse(rendered.contains("Lobby #7"));
        assertFalse(rendered.contains("Latest Server Event"));
        assertFalse(rendered.contains("LOBBY_STARTED"));
        assertFalse(rendered.contains("Match setup has started"));
    }

    @Test
    void renderMatchBootstrapHidesHistoricalLobbyBrowserAndGameSnapshot() {
        TuiState state = stateWithHistoricalCaches(TuiScreen.MATCH_BOOTSTRAP);

        assertEquals(TuiScreen.MATCH_BOOTSTRAP, state.getScreen());
        String rendered = this.renderer.render(state);

        assertTrue(rendered.contains("Waiting for the initial game snapshot"));
        assertFalse(rendered.contains("Open Lobbies"));
        assertFalse(rendered.contains("Game #11"));
        assertFalse(rendered.contains("Board:"));
    }

    @Test
    void renderHomeAfterOwnLobbyLeftHidesHistoricalLobbySections() {
        TuiState state = this.reducer.reduceConnection(TuiState.initial(), true);
        state = this.reducer.reducePreMatchUpdate(state, authenticated("Alice"));
        state = this.reducer.reduceLobbyList(state, openLobbyList());
        state = this.reducer.reducePreMatchUpdate(state, lobbyJoined());
        state = this.reducer.reducePreMatchUpdate(state, new PreMatchUpdatePayload(
                PreMatchEventType.LOBBY_LEFT,
                "Alice",
                "Lobby left successfully",
                7,
                List.of("Bob"),
                "Bob",
                4
        ));

        assertEquals(TuiScreen.HOME, state.getScreen());
        String rendered = this.renderer.render(state);

        assertTrue(rendered.contains("Identity: Alice"));
        assertFalse(rendered.contains("Open Lobbies"));
        assertFalse(rendered.contains("Lobby #7"));
        assertFalse(rendered.contains("Game #"));
        assertFalse(rendered.contains("Board:"));
    }

    @Test
    void renderDashboardIncludesGameSnapshotAndActionHintForCurrentPlayer() {
        TuiState state = this.reducer.reduceConnection(TuiState.initial(), true);
        state = this.reducer.reducePreMatchUpdate(state, authenticated("Alice"));
        state = this.reducer.reduceGameUpdate(state, gameSnapshot());

        String rendered = this.renderer.render(state);

        assertTrue(rendered.contains("Game #11"));
        assertTrue(rendered.contains("current player=Alice"));
        assertTrue(rendered.contains("[you, current]"));
        assertTrue(rendered.contains("Hunter"));
        assertTrue(rendered.contains("A1 | top=1 | bottom=0 | occupiedBy=Alice"));
        assertTrue(rendered.contains("choose-card <card-id>"));
    }

    /**
     * Builds an authentication payload that establishes the supplied nickname as the local identity.
     *
     * @param nickname authenticated nickname
     * @return authentication update fixture
     */
    private static PreMatchUpdatePayload authenticated(String nickname) {
        return new PreMatchUpdatePayload(
                PreMatchEventType.AUTHENTICATED,
                nickname,
                "Authentication completed successfully",
                null,
                null,
                null,
                null
        );
    }

    /**
     * Builds the waiting-room payload shared by stale-cache rendering scenarios.
     *
     * @return lobby-joined update fixture for lobby 7
     */
    private static PreMatchUpdatePayload lobbyJoined() {
        return new PreMatchUpdatePayload(
                PreMatchEventType.LOBBY_JOINED,
                "Alice",
                "Lobby joined successfully",
                7,
                List.of("Alice", "Bob"),
                "Bob",
                4
        );
    }

    /**
     * Builds the match-bootstrap payload that must not leak into the in-match screen.
     *
     * @return lobby-started update fixture for lobby 7
     */
    private static PreMatchUpdatePayload lobbyStarted() {
        return new PreMatchUpdatePayload(
                PreMatchEventType.LOBBY_STARTED,
                "Alice",
                "Match setup has started",
                7,
                List.of("Alice", "Bob"),
                "Bob",
                4
        );
    }

    /**
     * Builds a lobby-list payload cached before the active screen changes.
     *
     * @return lobby-list fixture containing lobby 7
     */
    private static LobbyListPayload openLobbyList() {
        return new LobbyListPayload(List.of(
                new LobbySummaryPayload(7, "Bob", 2, 4, "OPEN")
        ));
    }

    /**
     * Builds a complete game snapshot with Alice as the current player.
     *
     * @return game snapshot fixture for game 11
     */
    private static GamePayload gameSnapshot() {
        return new GamePayload(
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
        );
    }

    /**
     * Builds renderer state with intentionally stale caches so tests can assert the active screen,
     * rather than cache presence, controls which sections are visible.
     *
     * @param screen active screen under test
     * @return state carrying lobby-list, lobby-context, pre-match, and game caches
     */
    private static TuiState stateWithHistoricalCaches(TuiScreen screen) {
        return new TuiState(
                true,
                true,
                "Alice",
                screen,
                7,
                "Bob",
                4,
                List.of("Alice", "Bob"),
                openLobbyList(),
                lobbyStarted(),
                null,
                gameSnapshot()
        );
    }
}
