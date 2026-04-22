package it.polimi.ingsoft.mesos.server.model.lobby;

import it.polimi.ingsoft.mesos.server.controller.GameOrchestrator.GameOrchestrator;
import it.polimi.ingsoft.mesos.server.model.game.CardRowSelectionNotAllowedException;
import it.polimi.ingsoft.mesos.server.model.game.Game;
import it.polimi.ingsoft.mesos.server.model.game.NotPlayersTurnException;
import it.polimi.ingsoft.mesos.server.model.game.RoundPhase;
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
        Player firstRoundLeader = game.getTurnOrderTrack().getCurrentPlayer();
        Player secondPlayer = players.stream()
                .filter(player -> !player.equals(firstRoundLeader))
                .findFirst()
                .orElseThrow();

        assertThrows(NotPlayersTurnException.class, () -> game.moveTotem(secondPlayer, "B"));

        orchestrator.moveTotem(game.getTurnOrderTrack().getCurrentPlayer().getNickname(), "B");
        orchestrator.moveTotem(game.getTurnOrderTrack().getCurrentPlayer().getNickname(), "C");

        assertEquals(RoundPhase.PLAYER_DRAWS, game.getTurnOrderTrack().getCurrentRoundPhase());
        Player drawPhaseLeader = game.getTurnOrderTrack().getCurrentPlayer();
        Player waitingPlayer = players.stream()
                .filter(player -> !player.equals(drawPhaseLeader))
                .findFirst()
                .orElseThrow();
        assertThrows(
                CardRowSelectionNotAllowedException.class,
                () -> game.chooseCard(drawPhaseLeader, MesosTestFixtures.findUpperRowCard(game, 3).getId())
        );
        assertThrows(
                NotPlayersTurnException.class,
                () -> game.chooseCard(waitingPlayer, MesosTestFixtures.findUpperRowCard(game, 3).getId())
        );

        game.chooseCard(drawPhaseLeader, MesosTestFixtures.findLowerRowCard(game, 10).getId());
        game.chooseCard(waitingPlayer, MesosTestFixtures.findUpperRowCard(game, 3).getId());

        assertEquals(RoundPhase.RESOLVING_EFFECTS, game.getTurnOrderTrack().getCurrentRoundPhase());
        game.resolveEffects();
        game.resetBoard();

        assertEquals(RoundPhase.PLACING_TOTEMS, game.getTurnOrderTrack().getCurrentRoundPhase());
        assertEquals(2, game.getTurnOrderTrack().getRoundNumber());
        assertEquals(3, drawPhaseLeader.getFood());
        assertEquals(2, waitingPlayer.getFood());
        assertEquals(0, waitingPlayer.getPrestigePoints());
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

        orchestrator.moveTotem(players.get(0).getNickname(), "B");
        orchestrator.moveTotem(players.get(1).getNickname(), "C");
        orchestrator.moveTotem(players.get(2).getNickname(), "D");
        orchestrator.moveTotem(players.get(3).getNickname(), "E");
        orchestrator.moveTotem(players.get(4).getNickname(), "A");

        assertEquals(RoundPhase.PLAYER_DRAWS, game.getTurnOrderTrack().getCurrentRoundPhase());
        assertIterableEquals(
                players.subList(0, 4).stream().map(Player::getNickname).toList(),
                game.getTurnOrderTrack().getTurnOrder().stream().map(Player::getNickname).toList()
        );
        assertEquals(players.getFirst().getNickname(), game.getTurnOrderTrack().getCurrentPlayer().getNickname());
        assertEquals(10, players.get(4).getFood());
        assertEquals(players.get(4).getNickname(), game.getBoard().getStartingBlocks().getFirst().getOccupiedBy().getNickname());
        assertTrue(
                game.getBoard().getAvailableOfferSpaces().stream()
                        .noneMatch(space -> "A".equals(space.getCode()) && space.getOccupiedBy() != null)
        );
    }
}
