package it.polimi.ingsoft.mesos.server.model.lobby;

import it.polimi.ingsoft.mesos.server.controller.GameOrchestrator.GameOrchestrator;
import it.polimi.ingsoft.mesos.server.model.GameState.CardRowSelectionNotAllowedException;
import it.polimi.ingsoft.mesos.server.model.GameState.Game;
import it.polimi.ingsoft.mesos.server.model.GameState.NotPlayersTurnException;
import it.polimi.ingsoft.mesos.server.model.GameState.RoundPhase;
import it.polimi.ingsoft.mesos.server.model.player.Player;
import it.polimi.ingsoft.mesos.server.model.user.User;
import it.polimi.ingsoft.mesos.server.testing.MesosTestFixtures;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertIterableEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LobbyManagerGameplayFlowTest {

    @Test
    void lobbyStartSupportsScriptedRoundFlowAndAppliesRoundResetConsistently() {
        ArrayList<User> users = MesosTestFixtures.createUsers("Alice", "Bob");
        LobbyManager manager = new LobbyManager(MesosTestFixtures.createTwoPlayerGameplayFactory());
        Lobby lobby = manager.createLobby(users.getFirst(), 2);
        manager.joinLobby(lobby.getId(), users.get(1));

        GameOrchestrator orchestrator = manager.createGameFromLobby(lobby.getId());
        orchestrator.setupMatch();
        Game game = orchestrator.getGame();
        List<Player> players = orchestrator.getPlayers();
        Player alice = players.getFirst();
        Player bob = players.get(1);

        assertThrows(NotPlayersTurnException.class, () -> game.moveTotem(bob, "B"));

        orchestrator.moveTotem(game.getTurnOrderTrack().getCurrentPlayer(), "B");
        orchestrator.moveTotem(game.getTurnOrderTrack().getCurrentPlayer(), "C");

        assertEquals(RoundPhase.PLAYER_DRAWS, game.getTurnOrderTrack().getCurrentRoundPhase());
        assertThrows(
                CardRowSelectionNotAllowedException.class,
                () -> game.chooseCard(alice, MesosTestFixtures.findUpperRowCard(game, 3))
        );
        assertThrows(
                NotPlayersTurnException.class,
                () -> game.chooseCard(bob, MesosTestFixtures.findUpperRowCard(game, 3))
        );

        game.chooseCard(alice, MesosTestFixtures.findLowerRowCard(game, 10));
        game.chooseCard(bob, MesosTestFixtures.findUpperRowCard(game, 3));

        assertEquals(RoundPhase.RESOLVING_EFFECTS, game.getTurnOrderTrack().getCurrentRoundPhase());
        game.resolveEffects();
        game.resetBoard();

        assertEquals(RoundPhase.PLACING_TOTEMS, game.getTurnOrderTrack().getCurrentRoundPhase());
        assertEquals(2, game.getTurnOrderTrack().getRoundNumber());
        assertEquals(1, alice.getFood());
        assertEquals(-2, bob.getPrestigePoints());
        assertIterableEquals(
                List.of(85, 86, 11, 15, 27),
                game.getBoard().getLowerRowCards().stream().map(card -> card.getId()).toList()
        );
    }

    @Test
    void fivePlayerLobbyFlowResolvesOfferSpaceAImmediatelyAndExcludesItFromDrawOrder() {
        ArrayList<User> users = MesosTestFixtures.createUsers(5);
        LobbyManager manager = new LobbyManager();
        Lobby lobby = manager.createLobby(users.getFirst(), 5);
        for (int index = 1; index < users.size(); index++) {
            manager.joinLobby(lobby.getId(), users.get(index));
        }

        GameOrchestrator orchestrator = manager.createGameFromLobby(lobby.getId());
        orchestrator.setupMatch();
        Game game = orchestrator.getGame();
        List<Player> players = orchestrator.getPlayers();

        orchestrator.moveTotem(players.get(0), "B");
        orchestrator.moveTotem(players.get(1), "C");
        orchestrator.moveTotem(players.get(2), "D");
        orchestrator.moveTotem(players.get(3), "E");
        orchestrator.moveTotem(players.get(4), "A");

        assertEquals(RoundPhase.PLAYER_DRAWS, game.getTurnOrderTrack().getCurrentRoundPhase());
        assertIterableEquals(
                List.of("Alice", "Bob", "Carol", "Dave"),
                game.getTurnOrderTrack().getTurnOrder().stream().map(Player::getNickname).toList()
        );
        assertEquals("Alice", game.getTurnOrderTrack().getCurrentPlayer().getNickname());
        assertEquals(6, players.get(4).getFood());
        assertEquals("Eve", game.getBoard().getStartingBlocks().getFirst().getOccupiedBy().getNickname());
        assertTrue(
                game.getBoard().getAvailableOfferSpaces().stream()
                        .noneMatch(space -> "A".equals(space.getCode()) && space.getOccupiedBy() != null)
        );
    }
}
