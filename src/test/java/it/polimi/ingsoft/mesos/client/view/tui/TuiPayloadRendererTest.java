package it.polimi.ingsoft.mesos.client.view.tui;

import it.polimi.ingsoft.mesos.shared.payload.server.BoardPayload;
import it.polimi.ingsoft.mesos.shared.payload.server.CardPayload;
import it.polimi.ingsoft.mesos.shared.payload.server.ErrorPayload;
import it.polimi.ingsoft.mesos.shared.payload.server.GamePayload;
import it.polimi.ingsoft.mesos.shared.payload.server.LobbyListPayload;
import it.polimi.ingsoft.mesos.shared.payload.server.LobbySummaryPayload;
import it.polimi.ingsoft.mesos.shared.payload.server.OfferSpacePayload;
import it.polimi.ingsoft.mesos.shared.payload.server.PlayerPayload;
import it.polimi.ingsoft.mesos.shared.payload.server.PreMatchEventType;
import it.polimi.ingsoft.mesos.shared.payload.server.PreMatchUpdatePayload;
import it.polimi.ingsoft.mesos.shared.payload.server.TribePayload;
import it.polimi.ingsoft.mesos.shared.payload.server.TurnOrderPayload;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TuiPayloadRendererTest {
    private final TuiPayloadRenderer renderer = new TuiPayloadRenderer();

    @Test
    void rendersErrorWithoutLastGame() {
        String rendered = this.renderer.renderError(new ErrorPayload("BAD_COMMAND", "Invalid command"), null);

        assertEquals("[ERROR] BAD_COMMAND: Invalid command", rendered);
    }

    @Test
    void rendersErrorWithLastGame() {
        String rendered = this.renderer.renderError(new ErrorPayload("MOVE_DENIED", "Tile occupied"), gamePayload());

        assertTrue(rendered.contains("[ERROR] MOVE_DENIED: Tile occupied"));
        assertTrue(rendered.contains("[SERVER][GAME] #42 status=RUNNING era=2"));
        assertTrue(rendered.contains("- ada food=3 prestige=5 totem=A1"));
    }

    @Test
    void rendersPreMatchUpdate() {
        String rendered = this.renderer.renderPreMatchUpdate(new PreMatchUpdatePayload(
                PreMatchEventType.LOBBY_JOINED,
                "ada",
                "Lobby joined successfully",
                9,
                List.of("ada", "bob"),
                "ada",
                2
        ));

        assertEquals(
                "[SERVER][PRE-MATCH] LOBBY_JOINED | Lobby joined successfully | lobby=9 | players=ada, bob",
                rendered
        );
    }

    @Test
    void rendersEmptyLobbyList() {
        String rendered = this.renderer.renderLobbyList(new LobbyListPayload(List.of()));

        assertEquals("[SERVER][LOBBIES] No open lobbies.", rendered);
    }

    @Test
    void rendersNonEmptyLobbyList() {
        String rendered = this.renderer.renderLobbyList(new LobbyListPayload(List.of(
                new LobbySummaryPayload(1, "ada", 1, 2, "WAITING"),
                new LobbySummaryPayload(2, "bob", 2, 4, "READY")
        )));

        assertTrue(rendered.contains("[SERVER][LOBBIES]"));
        assertTrue(rendered.contains("- #1 host=ada players=1/2 status=WAITING"));
        assertTrue(rendered.contains("- #2 host=bob players=2/4 status=READY"));
    }

    @Test
    void rendersGameBasics() {
        String rendered = this.renderer.renderGame(gamePayload());

        assertTrue(rendered.contains("[SERVER][GAME] #42 status=RUNNING era=2"));
        assertTrue(rendered.contains("Round 3 | phase=ACTION | current=ada"));
        assertTrue(rendered.contains("- ada food=3 prestige=5 totem=A1"));
        assertTrue(rendered.contains("- bob food=2 prestige=4 totem=-"));
        assertTrue(rendered.contains("Upper row: #10 HUNTER era=1"));
        assertTrue(rendered.contains("Lower row: #20 HUT era=2"));
        assertTrue(rendered.contains("- A1 top=1 bottom=0 occupiedBy=ada"));
        assertTrue(rendered.contains("- B2 top=0 bottom=1 occupiedBy=-"));
    }

    private static GamePayload gamePayload() {
        return new GamePayload(
                42,
                "RUNNING",
                1,
                new TurnOrderPayload(3, "ACTION", "ada", List.of("ada", "bob")),
                null,
                List.of(
                        new PlayerPayload("ada", 3, 5, "A1", new TribePayload(List.of(), List.of())),
                        new PlayerPayload("bob", 2, 4, null, new TribePayload(List.of(), List.of()))
                ),
                new BoardPayload(
                        List.of(new CardPayload(10, "HUNTER", 0)),
                        List.of(new CardPayload(20, "HUT", 1)),
                        List.of(
                                new OfferSpacePayload("A1", 1, 0, "ada"),
                                new OfferSpacePayload("B2", 0, 1, null)
                        ),
                        List.of()
                )
        );
    }
}
