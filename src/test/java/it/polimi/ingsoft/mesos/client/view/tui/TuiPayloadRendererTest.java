package it.polimi.ingsoft.mesos.client.view.tui;

import it.polimi.ingsoft.mesos.shared.payload.servertoclient.game.BoardPayload;
import it.polimi.ingsoft.mesos.shared.payload.servertoclient.game.CardPayload;
import it.polimi.ingsoft.mesos.shared.payload.servertoclient.error.ErrorPayload;
import it.polimi.ingsoft.mesos.shared.payload.servertoclient.game.GamePayload;
import it.polimi.ingsoft.mesos.shared.payload.servertoclient.prelobby.LobbyListPayload;
import it.polimi.ingsoft.mesos.shared.payload.servertoclient.prelobby.LobbySummaryPayload;
import it.polimi.ingsoft.mesos.shared.payload.servertoclient.game.OfferSpacePayload;
import it.polimi.ingsoft.mesos.shared.payload.servertoclient.game.PlayerPayload;
import it.polimi.ingsoft.mesos.shared.payload.servertoclient.game.StartingBlockPayload;
import it.polimi.ingsoft.mesos.shared.payload.servertoclient.lobby.LobbyEventType;
import it.polimi.ingsoft.mesos.shared.payload.servertoclient.lobby.LobbyUpdatePayload;
import it.polimi.ingsoft.mesos.shared.payload.servertoclient.game.TribePayload;
import it.polimi.ingsoft.mesos.shared.payload.servertoclient.game.TurnOrderPayload;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TuiPayloadRendererTest {
    private final TuiPayloadRenderer renderer = new TuiPayloadRenderer();

    @Test
    void rendersErrorWithoutLastGame() {
        String rendered = this.renderer.renderError(new ErrorPayload("BAD_COMMAND", "Invalid command"), null, "ada");

        assertEquals("[ERROR] BAD_COMMAND: Invalid command", rendered);
    }

    @Test
    void rendersErrorWithLastGame() {
        String rendered = this.renderer.renderError(new ErrorPayload("MOVE_DENIED", "Tile occupied"), gamePayload(), "ada");

        assertTrue(rendered.contains("[ERROR] MOVE_DENIED: Tile occupied"));
        assertTrue(rendered.contains("[SERVER][GAME] #42 status=RUNNING era=2"));
        assertTrue(rendered.contains("- ada food=3 prestige=5 totem=A1"));
    }

    @Test
    void rendersLobbyUpdate() {
        String rendered = this.renderer.renderLobbyUpdate(new LobbyUpdatePayload(
                LobbyEventType.LOBBY_JOINED,
                "ada",
                "Lobby joined successfully",
                9,
                List.of("ada", "bob"),
                "ada",
                2
        ));

        assertEquals(
                "[SERVER][LOBBY] LOBBY_JOINED | Lobby joined successfully | lobby=9 | players=ada, bob",
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
        String rendered = this.renderer.renderGame(gamePayload(), "ada");

        assertTrue(rendered.contains("[SERVER][GAME] #42 status=RUNNING era=2"));
        assertTrue(rendered.contains("Your nickname: ada"));
        assertTrue(rendered.contains("Round 3 | phase=ACTION | current=ada"));
        assertTrue(rendered.contains("- ada food=3 prestige=5 totem=A1"));
        assertTrue(rendered.contains("- bob food=2 prestige=4 totem=-"));
        assertTrue(rendered.contains("UPPER  [10 HUNTER e1]"));
        assertTrue(rendered.contains("LOWER  [20 HUT e2]"));
        assertTrue(rendered.contains("OFFERS A1 top=1 bot=0 occ=ada | B2 top=0 bot=1 occ=-"));
        assertTrue(rendered.contains("START  #1 food=+2 pp=+0 occ=- | #2 food=+0 pp=+1 occ=bob"));
    }

    @Test
    void finalResultRanksConnectedPlayerBeforeDisconnectedPlayers() {
        String rendered = this.renderer.renderFinalGameResult(new GamePayload(
                42,
                "FINISHED",
                1,
                new TurnOrderPayload(3, "ACTION", "ada", List.of("ada", "bob")),
                null,
                List.of(
                        new PlayerPayload("ada", 3, 5, "A1", new TribePayload(List.of(), List.of()), true),
                        new PlayerPayload("bob", 2, 99, null, new TribePayload(List.of(), List.of()), false)
                ),
                null
        ));

        assertTrue(rendered.indexOf("1. ada") < rendered.indexOf("2. bob"));
    }

    private static GamePayload gamePayload() {
        return new GamePayload(
                42,
                "RUNNING",
                1,
                new TurnOrderPayload(3, "ACTION", "ada", List.of("ada", "bob")),
                null,
                List.of(
                        new PlayerPayload("ada", 3, 5, "A1", new TribePayload(List.of(), List.of()),true),
                        new PlayerPayload("bob", 2, 4, null, new TribePayload(List.of(), List.of()),true)
                ),
                new BoardPayload(
                        List.of(new CardPayload(10, "HUNTER", 0)),
                        List.of(new CardPayload(20, "HUT", 1)),
                        List.of(
                                new OfferSpacePayload("A1", 1, 0, "ada"),
                                new OfferSpacePayload("B2", 0, 1, null)
                        ),
                        List.of(
                                new StartingBlockPayload(1, 2, 0, null),
                                new StartingBlockPayload(2, 0, 1, "bob")
                        )
                )
        );
    }
}
