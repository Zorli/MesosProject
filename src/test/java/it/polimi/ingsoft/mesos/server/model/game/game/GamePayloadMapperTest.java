package it.polimi.ingsoft.mesos.server.model.game.game;

import it.polimi.ingsoft.mesos.server.factory.DefaultGameFactory;
import it.polimi.ingsoft.mesos.server.model.game.enums.GameStatus;
import it.polimi.ingsoft.mesos.server.model.game.enums.TotemColor;
import it.polimi.ingsoft.mesos.server.model.game.game.enums.RoundPhase;
import it.polimi.ingsoft.mesos.server.model.game.player.Player;
import it.polimi.ingsoft.mesos.shared.payload.servertoclient.error.ErrorPayload;
import it.polimi.ingsoft.mesos.shared.payload.servertoclient.game.GamePayload;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Unit tests for {@link GamePayloadMapper}.
 *
 * <p>The mapper is verified through client-visible payload fields. The tests do
 * not assert every card or board detail because those belong to board/card
 * tests; here we check that game lifecycle, turn order, players, and errors are
 * represented coherently.
 */
class GamePayloadMapperTest {

    /**
     * A game payload can be built before setup. In that state no current player
     * exists yet, so the turn order section is empty but lifecycle data and
     * players are still visible.
     */
    @Test
    void gamePayloadCanRepresentGameBeforeSetup() {
        Player alice = player("Alice", TotemColor.RED);
        Player bob = player("Bob", TotemColor.BLUE);
        Game game = game(alice, bob);

        GamePayload payload = GamePayloadMapper.from(game);

        assertEquals(1, payload.id);
        assertEquals(GameStatus.SETUP.name(), payload.status);
        assertEquals(1, payload.currentEra);
        assertEquals(0, payload.turnOrder.roundNumber);
        assertEquals(RoundPhase.PLACING_TOTEMS.name(), payload.turnOrder.currentRoundPhase);
        assertNull(payload.turnOrder.currentPlayerNickname);
        assertTrue(payload.turnOrder.turnOrder.isEmpty());
        assertEquals(List.of("Alice", "Bob"), payload.players.stream().map(player -> player.nickname).toList());
        assertTrue(payload.players.stream().allMatch(player -> player.isConnected));
    }

    /**
     * After setup, the payload exposes the active round, phase order, and
     * current player selected by the game flow.
     */
    @Test
    void gamePayloadRepresentsInitializedTurnOrderAfterSetup() {
        Player alice = player("Alice", TotemColor.RED);
        Player bob = player("Bob", TotemColor.BLUE);
        Game game = game(alice, bob);

        game.setup();

        GamePayload payload = GamePayloadMapper.from(game);

        assertEquals(GameStatus.IN_PROGRESS.name(), payload.status);
        assertEquals(1, payload.turnOrder.roundNumber);
        assertEquals(RoundPhase.PLACING_TOTEMS.name(), payload.turnOrder.currentRoundPhase);
        assertEquals("Alice", payload.turnOrder.currentPlayerNickname);
        assertEquals(List.of("Alice", "Bob"), payload.turnOrder.turnOrder);
    }

    /**
     * Player connection state is part of the payload sent to clients.
     */
    @Test
    void gamePayloadIncludesDisconnectedPlayers() {
        Player alice = player("Alice", TotemColor.RED);
        Player bob = player("Bob", TotemColor.BLUE);
        Game game = game(alice, bob);

        game.disconnectedPlayer(alice);

        GamePayload payload = GamePayloadMapper.from(game);

        assertEquals(false, payload.players.stream()
                .filter(player -> player.nickname.equals("Alice"))
                .findFirst()
                .orElseThrow()
                .isConnected);
        assertEquals(true, payload.players.stream()
                .filter(player -> player.nickname.equals("Bob"))
                .findFirst()
                .orElseThrow()
                .isConnected);
    }

    /**
     * Error payloads preserve the code and message chosen by the caller.
     */
    @Test
    void errorPayloadContainsCodeAndMessage() {
        ErrorPayload payload = GamePayloadMapper.toErrorPayload("ERR", "Something went wrong");

        assertEquals("ERR", payload.code);
        assertEquals("Something went wrong", payload.message);
    }

    private static Game game(Player... players) {
        return DefaultGameFactory.getInstance().createGame(1, List.of(players));
    }

    private static Player player(String nickname, TotemColor color) {
        return new Player(nickname, 0, 0, color);
    }
}
