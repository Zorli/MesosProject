package it.polimi.ingsoft.mesos.server.persistence.savedmatch;

import it.polimi.ingsoft.mesos.server.ServerRuntimeConfig;
import it.polimi.ingsoft.mesos.server.model.GameState.Game;
import it.polimi.ingsoft.mesos.server.model.GameState.RoundPhase;
import it.polimi.ingsoft.mesos.server.model.Orchestration.GameOrchestrator;
import it.polimi.ingsoft.mesos.server.model.lobby.Lobby;
import it.polimi.ingsoft.mesos.server.model.lobby.LobbyManager;
import it.polimi.ingsoft.mesos.server.model.cards.Card;
import it.polimi.ingsoft.mesos.server.model.player.Player;
import it.polimi.ingsoft.mesos.server.persistence.postgres.PostgresDatabaseBootstrap;
import it.polimi.ingsoft.mesos.server.testing.GameStateAssertions;
import it.polimi.ingsoft.mesos.server.testing.MesosTestFixtures;
import it.polimi.ingsoft.mesos.server.testing.PostgresIntegrationTestSupport;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertIterableEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@Tag("integration")
class PostgresSavedMatchGameplayFlowIntegrationTest extends PostgresIntegrationTestSupport {

    @Test
    void resumedLobbyRestoresMidRoundGameAndKeepsOriginalAndRestoredMatchesInLockstep() {
        PostgresSavedMatchService service = this.createService("saved_matches_gameplay_flow");

        ArrayList<Player> livePlayers = MesosTestFixtures.createPlayers("Alice", "Bob");
        LobbyManager liveManager = new LobbyManager(MesosTestFixtures.createTwoPlayerGameplayFactory());
        Lobby liveLobby = liveManager.createLobby(livePlayers.getFirst());
        liveManager.joinLobby(liveLobby.getId(), livePlayers.get(1));

        GameOrchestrator liveOrchestrator = liveManager.createGameFromLobby(liveLobby.getId(), service);
        liveOrchestrator.setupMatch();
        Game liveGame = liveOrchestrator.getGame();

        playFirstRoundAndOpenSecondRound(liveOrchestrator, liveGame);
        playSecondRoundPlacements(liveOrchestrator, liveGame);
        liveGame.chooseCard(liveGame.getTurnOrderTrack().getCurrentPlayer(), MesosTestFixtures.findLowerRowCard(liveGame, 11));
        service.saveMatch(liveGame, "Alice", SavedMatchCheckpointType.TURN_FINISHED);

        LobbyManager resumeManager = new LobbyManager();
        Lobby resumeLobby = resumeManager.createResumeLobby(String.valueOf(liveGame.getId()), List.of("Alice", "Bob"));
        ArrayList<Player> reconnectingPlayers = MesosTestFixtures.createPlayers("Alice", "Bob");
        resumeManager.joinLobby(resumeLobby.getId(), reconnectingPlayers.getFirst());
        resumeManager.joinLobby(resumeLobby.getId(), reconnectingPlayers.get(1));

        GameOrchestrator restoredOrchestrator = resumeManager.resumeGameFromLobby(resumeLobby.getId(), service);
        Game restoredGame = restoredOrchestrator.getGame();

        GameStateAssertions.assertEquivalent(liveGame, restoredGame);

        liveGame.chooseCard(liveGame.getTurnOrderTrack().getCurrentPlayer(), MesosTestFixtures.findUpperRowCard(liveGame, 12));
        restoredGame.chooseCard(restoredGame.getTurnOrderTrack().getCurrentPlayer(), MesosTestFixtures.findUpperRowCard(restoredGame, 12));
        GameStateAssertions.assertEquivalent(liveGame, restoredGame);

        liveGame.resolveEffects();
        restoredGame.resolveEffects();
        GameStateAssertions.assertEquivalent(liveGame, restoredGame);
        assertEquals(RoundPhase.RESETTING_BOARD, liveGame.getTurnOrderTrack().getCurrentRoundPhase());
        assertEquals(2, liveGame.getPlayers().getFirst().getFood());
        assertEquals(-3, liveGame.getPlayers().get(1).getPrestigePoints());

        liveGame.resetBoard();
        restoredGame.resetBoard();
        GameStateAssertions.assertEquivalent(liveGame, restoredGame);
        assertEquals(3, liveGame.getTurnOrderTrack().getRoundNumber());
        assertEquals(RoundPhase.PLACING_TOTEMS, liveGame.getTurnOrderTrack().getCurrentRoundPhase());
    }

    @Test
    void saveRestoreRoundTripPreservesFivePlayerOfferSpaceAResolutionAndDrawOrder() {
        PostgresSavedMatchService service = this.createService("saved_matches_offer_a");
        ArrayList<Player> players = MesosTestFixtures.createPlayers(5);
        LobbyManager manager = new LobbyManager();
        Lobby lobby = manager.createLobby(players.getFirst());
        for (int index = 1; index < players.size(); index++) {
            manager.joinLobby(lobby.getId(), players.get(index));
        }

        GameOrchestrator orchestrator = manager.createGameFromLobby(lobby.getId(), service);
        orchestrator.setupMatch();
        Game originalGame = orchestrator.getGame();

        orchestrator.moveTotem(players.get(0), "B");
        orchestrator.moveTotem(players.get(1), "C");
        orchestrator.moveTotem(players.get(2), "D");
        orchestrator.moveTotem(players.get(3), "E");
        orchestrator.moveTotem(players.get(4), "A");

        Game restoredGame = service.restoreGame(String.valueOf(originalGame.getId()));
        GameStateAssertions.assertEquivalent(originalGame, restoredGame);

        assertEquals(RoundPhase.PLAYER_DRAWS, restoredGame.getTurnOrderTrack().getCurrentRoundPhase());
        assertIterableEquals(
                List.of("Alice", "Bob", "Carol", "Dave"),
                restoredGame.getTurnOrderTrack().getTurnOrder().stream().map(Player::getNickname).toList()
        );
        assertEquals(6, restoredGame.getPlayers().get(4).getFood());
    }

    @Test
    void repeatedRestoreKeepsLongDeterministicMatchAlignedAcrossEraChangesAndFinalScoring() {
        PostgresSavedMatchService service = this.createService("saved_matches_long_gameplay");

        ArrayList<Player> players = MesosTestFixtures.createPlayers("Alice", "Bob");
        LobbyManager manager = new LobbyManager(MesosTestFixtures.createFullCatalogFactory(2));
        Lobby lobby = manager.createLobby(players.getFirst());
        manager.joinLobby(lobby.getId(), players.get(1));

        GameOrchestrator orchestrator = manager.createGameFromLobby(lobby.getId(), service);
        orchestrator.setupMatch();
        Game liveGame = orchestrator.getGame();
        Game restoredGame = null;

        Set<Integer> checkpointRoundsBeforeResolve = Set.of(1, 4, 7, 10);
        int restoreCount = 0;
        boolean sawSecondEra = false;
        boolean sawThirdEra = false;

        while (liveGame.getStatus() != it.polimi.ingsoft.mesos.server.model.enums.GameStatus.FINISHED) {
            playPlacementPhase(liveGame, restoredGame);
            playDrawPhase(liveGame, restoredGame);
            playExtraDrawPhase(liveGame, restoredGame);

            if (checkpointRoundsBeforeResolve.contains(liveGame.getTurnOrderTrack().getRoundNumber())) {
                restoredGame = restoreFreshSnapshot(service, liveGame, SavedMatchCheckpointType.TURN_FINISHED);
                GameStateAssertions.assertEquivalent(liveGame, restoredGame);
                restoreCount++;
            }

            resolveEffects(liveGame, restoredGame);

            if (liveGame.getTurnOrderTrack().getCurrentRoundPhase() == RoundPhase.FINAL_SCORING) {
                restoredGame = restoreFreshSnapshot(service, liveGame, SavedMatchCheckpointType.MATCH_COMPLETED);
                GameStateAssertions.assertEquivalent(liveGame, restoredGame);
                restoreCount++;

                liveGame.calculateFinalScore();
                restoredGame.calculateFinalScore();
                GameStateAssertions.assertEquivalent(liveGame, restoredGame);

                Game finishedRestoredGame = restoreFreshSnapshot(service, liveGame, SavedMatchCheckpointType.MATCH_COMPLETED);
                GameStateAssertions.assertEquivalent(liveGame, finishedRestoredGame);
                restoreCount++;
                break;
            }

            var previousEra = liveGame.getCurrentEra();
            resetBoard(liveGame, restoredGame);

            if (liveGame.getCurrentEra() != previousEra) {
                restoredGame = restoreFreshSnapshot(service, liveGame, SavedMatchCheckpointType.ERA_CHANGED);
                GameStateAssertions.assertEquivalent(liveGame, restoredGame);
                restoreCount++;
            }

            if (liveGame.getCurrentEra() == it.polimi.ingsoft.mesos.server.model.enums.Era.second) {
                sawSecondEra = true;
            }
            if (liveGame.getCurrentEra() == it.polimi.ingsoft.mesos.server.model.enums.Era.third) {
                sawThirdEra = true;
            }
        }

        assertTrue(sawSecondEra);
        assertTrue(sawThirdEra);
        assertTrue(restoreCount >= 5);
        assertEquals(it.polimi.ingsoft.mesos.server.model.enums.GameStatus.FINISHED, liveGame.getStatus());
        assertEquals(10, liveGame.getTurnOrderTrack().getRoundNumber());
    }

    @Test
    void repeatedRestoreKeepsThreePlayerMatchAlignedWithAlternatingChoiceStrategy() {
        PostgresSavedMatchService service = this.createService("saved_matches_three_player_long_gameplay");

        ArrayList<Player> players = MesosTestFixtures.createPlayers("Alice", "Bob", "Carol");
        LobbyManager manager = new LobbyManager(MesosTestFixtures.createFullCatalogFactory(3));
        Lobby lobby = manager.createLobby(players.getFirst());
        manager.joinLobby(lobby.getId(), players.get(1));
        manager.joinLobby(lobby.getId(), players.get(2));

        GameOrchestrator orchestrator = manager.createGameFromLobby(lobby.getId(), service);
        orchestrator.setupMatch();
        Game liveGame = orchestrator.getGame();
        Game restoredGame = null;

        int restoreCount = 0;
        boolean sawSecondEra = false;
        boolean sawThirdEra = false;

        while (liveGame.getStatus() != it.polimi.ingsoft.mesos.server.model.enums.GameStatus.FINISHED) {
            playPlacementPhase(liveGame, restoredGame);

            if (liveGame.getTurnOrderTrack().getRoundNumber() % 2 == 1) {
                restoredGame = restoreFreshSnapshot(service, liveGame, SavedMatchCheckpointType.TURN_STARTED);
                GameStateAssertions.assertEquivalent(liveGame, restoredGame);
                restoreCount++;
            }

            playDrawPhase(liveGame, restoredGame, true);
            playExtraDrawPhase(liveGame, restoredGame, true);
            resolveEffects(liveGame, restoredGame);

            if (liveGame.getTurnOrderTrack().getCurrentRoundPhase() == RoundPhase.FINAL_SCORING) {
                restoredGame = restoreFreshSnapshot(service, liveGame, SavedMatchCheckpointType.MATCH_COMPLETED);
                GameStateAssertions.assertEquivalent(liveGame, restoredGame);
                restoreCount++;

                liveGame.calculateFinalScore();
                restoredGame.calculateFinalScore();
                GameStateAssertions.assertEquivalent(liveGame, restoredGame);
                break;
            }

            var previousEra = liveGame.getCurrentEra();
            resetBoard(liveGame, restoredGame);

            if (liveGame.getCurrentEra() != previousEra || liveGame.getTurnOrderTrack().getRoundNumber() % 3 == 0) {
                SavedMatchCheckpointType checkpointType = liveGame.getCurrentEra() != previousEra
                        ? SavedMatchCheckpointType.ERA_CHANGED
                        : SavedMatchCheckpointType.TURN_STARTED;
                restoredGame = restoreFreshSnapshot(service, liveGame, checkpointType);
                GameStateAssertions.assertEquivalent(liveGame, restoredGame);
                restoreCount++;
            }

            if (liveGame.getCurrentEra() == it.polimi.ingsoft.mesos.server.model.enums.Era.second) {
                sawSecondEra = true;
            }
            if (liveGame.getCurrentEra() == it.polimi.ingsoft.mesos.server.model.enums.Era.third) {
                sawThirdEra = true;
            }
        }

        assertTrue(sawSecondEra);
        assertTrue(sawThirdEra);
        assertTrue(restoreCount >= 6);
        assertEquals(it.polimi.ingsoft.mesos.server.model.enums.GameStatus.FINISHED, liveGame.getStatus());
        assertEquals(10, liveGame.getTurnOrderTrack().getRoundNumber());
        assertEquals(3, liveGame.getPlayers().size());
    }

    @Test
    void repeatedRestoreKeepsFourPlayerMatchAlignedAcrossFullGame() {
        PostgresSavedMatchService service = this.createService("saved_matches_four_player_long_gameplay");

        ArrayList<Player> players = MesosTestFixtures.createPlayers("Alice", "Bob", "Carol", "Dave");
        LobbyManager manager = new LobbyManager(MesosTestFixtures.createFullCatalogFactory(4));
        Lobby lobby = manager.createLobby(players.getFirst());
        manager.joinLobby(lobby.getId(), players.get(1));
        manager.joinLobby(lobby.getId(), players.get(2));
        manager.joinLobby(lobby.getId(), players.get(3));

        GameOrchestrator orchestrator = manager.createGameFromLobby(lobby.getId(), service);
        orchestrator.setupMatch();
        Game liveGame = orchestrator.getGame();
        Game restoredGame = null;

        int restoreCount = 0;
        boolean sawSecondEra = false;
        boolean sawThirdEra = false;

        while (liveGame.getStatus() != it.polimi.ingsoft.mesos.server.model.enums.GameStatus.FINISHED) {
            playPlacementPhase(liveGame, restoredGame);

            if (liveGame.getTurnOrderTrack().getRoundNumber() == 2 || liveGame.getTurnOrderTrack().getRoundNumber() == 6) {
                restoredGame = restoreFreshSnapshot(service, liveGame, SavedMatchCheckpointType.TURN_STARTED);
                GameStateAssertions.assertEquivalent(liveGame, restoredGame);
                restoreCount++;
            }

            playDrawPhase(liveGame, restoredGame, true);
            playExtraDrawPhase(liveGame, restoredGame, true);

            if (liveGame.getTurnOrderTrack().getRoundNumber() % 4 == 0) {
                restoredGame = restoreFreshSnapshot(service, liveGame, SavedMatchCheckpointType.TURN_FINISHED);
                GameStateAssertions.assertEquivalent(liveGame, restoredGame);
                restoreCount++;
            }

            resolveEffects(liveGame, restoredGame);

            if (liveGame.getTurnOrderTrack().getCurrentRoundPhase() == RoundPhase.FINAL_SCORING) {
                restoredGame = restoreFreshSnapshot(service, liveGame, SavedMatchCheckpointType.MATCH_COMPLETED);
                GameStateAssertions.assertEquivalent(liveGame, restoredGame);
                restoreCount++;

                liveGame.calculateFinalScore();
                restoredGame.calculateFinalScore();
                GameStateAssertions.assertEquivalent(liveGame, restoredGame);
                break;
            }

            var previousEra = liveGame.getCurrentEra();
            resetBoard(liveGame, restoredGame);

            if (liveGame.getCurrentEra() != previousEra || liveGame.getTurnOrderTrack().getRoundNumber() == 9) {
                SavedMatchCheckpointType checkpointType = liveGame.getCurrentEra() != previousEra
                        ? SavedMatchCheckpointType.ERA_CHANGED
                        : SavedMatchCheckpointType.TURN_STARTED;
                restoredGame = restoreFreshSnapshot(service, liveGame, checkpointType);
                GameStateAssertions.assertEquivalent(liveGame, restoredGame);
                restoreCount++;
            }

            if (liveGame.getCurrentEra() == it.polimi.ingsoft.mesos.server.model.enums.Era.second) {
                sawSecondEra = true;
            }
            if (liveGame.getCurrentEra() == it.polimi.ingsoft.mesos.server.model.enums.Era.third) {
                sawThirdEra = true;
            }
        }

        assertTrue(sawSecondEra);
        assertTrue(sawThirdEra);
        assertTrue(restoreCount >= 6);
        assertEquals(it.polimi.ingsoft.mesos.server.model.enums.GameStatus.FINISHED, liveGame.getStatus());
        assertEquals(10, liveGame.getTurnOrderTrack().getRoundNumber());
        assertEquals(4, liveGame.getPlayers().size());
    }

    @Test
    @Disabled("Tracked in issue #67: PLAYER_DRAWS can target a player with quota but no legal draft in the allowed row")
    void repeatedRestoreKeepsFivePlayerMatchAlignedIncludingOfferSpaceAResolution() {
        PostgresSavedMatchService service = this.createService("saved_matches_five_player_long_gameplay");

        ArrayList<Player> players = MesosTestFixtures.createPlayers("Alice", "Bob", "Carol", "Dave", "Eve");
        LobbyManager manager = new LobbyManager(MesosTestFixtures.createFullCatalogFactory(5));
        Lobby lobby = manager.createLobby(players.getFirst());
        manager.joinLobby(lobby.getId(), players.get(1));
        manager.joinLobby(lobby.getId(), players.get(2));
        manager.joinLobby(lobby.getId(), players.get(3));
        manager.joinLobby(lobby.getId(), players.get(4));

        GameOrchestrator orchestrator = manager.createGameFromLobby(lobby.getId(), service);
        orchestrator.setupMatch();
        Game liveGame = orchestrator.getGame();
        Game restoredGame = null;

        int restoreCount = 0;
        boolean sawSecondEra = false;
        boolean sawThirdEra = false;
        boolean sawOfferSpaceAResolution = false;

        while (liveGame.getStatus() != it.polimi.ingsoft.mesos.server.model.enums.GameStatus.FINISHED) {
            boolean useOfferSpaceA = liveGame.getTurnOrderTrack().getRoundNumber() % 2 == 1;
            List<String> placementSequence = useOfferSpaceA
                    ? List.of("B", "C", "D", "E", "A")
                    : List.of("B", "C", "D", "E", "F");
            playPlacementPhase(liveGame, restoredGame, placementSequence);

            if (useOfferSpaceA) {
                assertEquals(4, liveGame.getTurnOrderTrack().getTurnOrder().size());
                if (restoredGame != null) {
                    assertEquals(4, restoredGame.getTurnOrderTrack().getTurnOrder().size());
                }
                sawOfferSpaceAResolution = true;
            }

            if (liveGame.getTurnOrderTrack().getRoundNumber() == 1
                    || liveGame.getTurnOrderTrack().getRoundNumber() == 5) {
                restoredGame = restoreFreshSnapshot(service, liveGame, SavedMatchCheckpointType.TURN_STARTED);
                GameStateAssertions.assertEquivalent(liveGame, restoredGame);
                restoreCount++;
            }

            playDrawPhase(liveGame, restoredGame, true);
            playExtraDrawPhase(liveGame, restoredGame, true);

            if (liveGame.getTurnOrderTrack().getRoundNumber() == 3
                    || liveGame.getTurnOrderTrack().getRoundNumber() == 8) {
                restoredGame = restoreFreshSnapshot(service, liveGame, SavedMatchCheckpointType.TURN_FINISHED);
                GameStateAssertions.assertEquivalent(liveGame, restoredGame);
                restoreCount++;
            }

            resolveEffects(liveGame, restoredGame);

            if (liveGame.getTurnOrderTrack().getCurrentRoundPhase() == RoundPhase.FINAL_SCORING) {
                restoredGame = restoreFreshSnapshot(service, liveGame, SavedMatchCheckpointType.MATCH_COMPLETED);
                GameStateAssertions.assertEquivalent(liveGame, restoredGame);
                restoreCount++;

                liveGame.calculateFinalScore();
                restoredGame.calculateFinalScore();
                GameStateAssertions.assertEquivalent(liveGame, restoredGame);
                break;
            }

            var previousEra = liveGame.getCurrentEra();
            resetBoard(liveGame, restoredGame);

            if (liveGame.getCurrentEra() != previousEra
                    || liveGame.getTurnOrderTrack().getRoundNumber() == 7
                    || liveGame.getTurnOrderTrack().getRoundNumber() == 10) {
                SavedMatchCheckpointType checkpointType = liveGame.getCurrentEra() != previousEra
                        ? SavedMatchCheckpointType.ERA_CHANGED
                        : SavedMatchCheckpointType.TURN_STARTED;
                restoredGame = restoreFreshSnapshot(service, liveGame, checkpointType);
                GameStateAssertions.assertEquivalent(liveGame, restoredGame);
                restoreCount++;
            }

            if (liveGame.getCurrentEra() == it.polimi.ingsoft.mesos.server.model.enums.Era.second) {
                sawSecondEra = true;
            }
            if (liveGame.getCurrentEra() == it.polimi.ingsoft.mesos.server.model.enums.Era.third) {
                sawThirdEra = true;
            }
        }

        assertTrue(sawOfferSpaceAResolution);
        assertTrue(sawSecondEra);
        assertTrue(sawThirdEra);
        assertTrue(restoreCount >= 7);
        assertEquals(it.polimi.ingsoft.mesos.server.model.enums.GameStatus.FINISHED, liveGame.getStatus());
        assertEquals(10, liveGame.getTurnOrderTrack().getRoundNumber());
        assertEquals(5, liveGame.getPlayers().size());
    }

    private PostgresSavedMatchService createService(String dbPrefix) {
        ServerRuntimeConfig runtimeConfig = this.createRuntimeConfig(dbPrefix, true);
        PostgresDatabaseBootstrap.getInstance().ensureReady(
                runtimeConfig,
                List.of(PostgresSavedMatchSchemaModule.getInstance())
        );

        return new PostgresSavedMatchService(
                runtimeConfig.toPostgresConnectionConfig(),
                runtimeConfig.maxBusyRetries()
        );
    }

    private static void playFirstRoundAndOpenSecondRound(GameOrchestrator orchestrator, Game game) {
        orchestrator.moveTotem(game.getTurnOrderTrack().getCurrentPlayer(), "B");
        orchestrator.moveTotem(game.getTurnOrderTrack().getCurrentPlayer(), "C");
        game.chooseCard(game.getTurnOrderTrack().getCurrentPlayer(), MesosTestFixtures.findLowerRowCard(game, 10));
        game.chooseCard(game.getTurnOrderTrack().getCurrentPlayer(), MesosTestFixtures.findUpperRowCard(game, 3));
        game.resolveEffects();
        game.resetBoard();
    }

    private static void playSecondRoundPlacements(GameOrchestrator orchestrator, Game game) {
        orchestrator.moveTotem(game.getTurnOrderTrack().getCurrentPlayer(), "B");
        orchestrator.moveTotem(game.getTurnOrderTrack().getCurrentPlayer(), "C");
        assertEquals(RoundPhase.PLAYER_DRAWS, game.getTurnOrderTrack().getCurrentRoundPhase());
    }

    private static Game restoreFreshSnapshot(PostgresSavedMatchService service, Game liveGame, SavedMatchCheckpointType checkpointType) {
        service.saveMatch(liveGame, liveGame.getPlayers().getFirst().getNickname(), checkpointType);
        return service.restoreGame(String.valueOf(liveGame.getId()));
    }

    private static void playPlacementPhase(Game liveGame, Game restoredGame) {
        List<String> placementSequence = MesosTestFixtures.placementSequenceFor(liveGame.getPlayers().size());
        playPlacementPhase(liveGame, restoredGame, placementSequence);
    }

    private static void playPlacementPhase(Game liveGame, Game restoredGame, List<String> placementSequence) {
        for (String tile : placementSequence) {
            liveGame.moveTotem(liveGame.getTurnOrderTrack().getCurrentPlayer(), tile);
            if (restoredGame != null) {
                restoredGame.moveTotem(restoredGame.getTurnOrderTrack().getCurrentPlayer(), tile);
                GameStateAssertions.assertEquivalent(liveGame, restoredGame);
            }
        }
        assertEquals(RoundPhase.PLAYER_DRAWS, liveGame.getTurnOrderTrack().getCurrentRoundPhase());
    }

    private static void playDrawPhase(Game liveGame, Game restoredGame) {
        playDrawPhase(liveGame, restoredGame, false);
    }

    private static void playDrawPhase(Game liveGame, Game restoredGame, boolean alternateChoiceStrategy) {
        while (liveGame.getTurnOrderTrack().getCurrentRoundPhase() == RoundPhase.PLAYER_DRAWS) {
            Card liveCard = selectDeterministicCard(liveGame, alternateChoiceStrategy);
            liveGame.chooseCard(liveGame.getTurnOrderTrack().getCurrentPlayer(), liveCard);
            if (restoredGame != null) {
                Card restoredCard = selectDeterministicCard(restoredGame, alternateChoiceStrategy);
                restoredGame.chooseCard(restoredGame.getTurnOrderTrack().getCurrentPlayer(), restoredCard);
                GameStateAssertions.assertEquivalent(liveGame, restoredGame);
            }
        }
    }

    private static void playExtraDrawPhase(Game liveGame, Game restoredGame) {
        playExtraDrawPhase(liveGame, restoredGame, false);
    }

    private static void playExtraDrawPhase(Game liveGame, Game restoredGame, boolean alternateChoiceStrategy) {
        while (liveGame.getTurnOrderTrack().getCurrentRoundPhase() == RoundPhase.EXTRA_DRAWS) {
            Card liveCard = selectDeterministicCard(liveGame, alternateChoiceStrategy);
            liveGame.extraDrawn(liveGame.getTurnOrderTrack().getCurrentPlayer(), liveCard);
            if (restoredGame != null) {
                Card restoredCard = selectDeterministicCard(restoredGame, alternateChoiceStrategy);
                restoredGame.extraDrawn(restoredGame.getTurnOrderTrack().getCurrentPlayer(), restoredCard);
                GameStateAssertions.assertEquivalent(liveGame, restoredGame);
            }
        }
    }

    private static void resolveEffects(Game liveGame, Game restoredGame) {
        assertEquals(RoundPhase.RESOLVING_EFFECTS, liveGame.getTurnOrderTrack().getCurrentRoundPhase());
        liveGame.resolveEffects();
        if (restoredGame != null) {
            restoredGame.resolveEffects();
            GameStateAssertions.assertEquivalent(liveGame, restoredGame);
        }
    }

    private static void resetBoard(Game liveGame, Game restoredGame) {
        assertEquals(RoundPhase.RESETTING_BOARD, liveGame.getTurnOrderTrack().getCurrentRoundPhase());
        liveGame.resetBoard();
        if (restoredGame != null) {
            restoredGame.resetBoard();
            GameStateAssertions.assertEquivalent(liveGame, restoredGame);
        }
    }

    private static Card selectDeterministicCard(Game game, boolean alternateChoiceStrategy) {
        Player currentPlayer = game.getTurnOrderTrack().getCurrentPlayer();
        boolean preferUpper = alternateChoiceStrategy
                && (game.getTurnOrderTrack().getRoundNumber() + playerIndex(game, currentPlayer)) % 2 == 0;
        List<Card> draftableUpperCards = game.getBoard().getDraftableUpperCards(currentPlayer);
        List<Card> draftableLowerCards = game.getBoard().getDraftableLowerCards(currentPlayer);
        boolean playerDrawPhase = game.getTurnOrderTrack().getCurrentRoundPhase() == RoundPhase.PLAYER_DRAWS;
        boolean canChooseUpper = !playerDrawPhase || game.getTurnState().getRemainingUpperChoices(currentPlayer) > 0;
        boolean canChooseLower = !playerDrawPhase || game.getTurnState().getRemainingLowerChoices(currentPlayer) > 0;

        if (preferUpper && canChooseUpper && !draftableUpperCards.isEmpty()) {
            return draftableUpperCards.stream()
                    .min(java.util.Comparator.comparingInt(Card::getId))
                    .orElseThrow();
        }
        if (canChooseLower && !draftableLowerCards.isEmpty()) {
            return draftableLowerCards.stream()
                    .min(java.util.Comparator.comparingInt(Card::getId))
                    .orElseThrow();
        }
        if (canChooseUpper && !draftableUpperCards.isEmpty()) {
            return draftableUpperCards.stream()
                    .min(java.util.Comparator.comparingInt(Card::getId))
                    .orElseThrow();
        }
        throw new IllegalStateException(
                "No draftable cards available for player " + currentPlayer.getNickname()
                        + " during phase " + game.getTurnOrderTrack().getCurrentRoundPhase()
                        + " with upperChoices=" + game.getTurnState().getRemainingUpperChoices(currentPlayer)
                        + ", lowerChoices=" + game.getTurnState().getRemainingLowerChoices(currentPlayer)
                        + ", upperDraftable=" + draftableUpperCards.stream().map(Card::getId).toList()
                        + ", lowerDraftable=" + draftableLowerCards.stream().map(Card::getId).toList()
        );
    }

    private static int playerIndex(Game game, Player player) {
        return game.getPlayers().stream()
                .map(Player::getNickname)
                .toList()
                .indexOf(player.getNickname());
    }
}
