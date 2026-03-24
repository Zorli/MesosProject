package it.polimi.ingsoft.mesos.server.model.persistence.savedmatch;

import it.polimi.ingsoft.mesos.server.model.persistence.ServerRuntimeConfig;
import it.polimi.ingsoft.mesos.server.controller.Factory.DefaultGameFactory;
import it.polimi.ingsoft.mesos.server.model.GameState.Game;
import it.polimi.ingsoft.mesos.server.model.GameState.RoundPhase;
import it.polimi.ingsoft.mesos.server.model.cards.Card;
import it.polimi.ingsoft.mesos.server.model.enums.Era;
import it.polimi.ingsoft.mesos.server.model.enums.GameStatus;
import it.polimi.ingsoft.mesos.server.model.enums.TotemColor;
import it.polimi.ingsoft.mesos.server.controller.player.Player;
import it.polimi.ingsoft.mesos.server.model.persistence.postgres.PostgresDatabaseBootstrap;
import it.polimi.ingsoft.mesos.server.model.persistence.savedmatch.PostgresSavedMatchSchemaModule;
import it.polimi.ingsoft.mesos.server.model.persistence.savedmatch.PostgresSavedMatchService;
import it.polimi.ingsoft.mesos.server.model.persistence.savedmatch.SavedMatchCheckpointType;
import it.polimi.ingsoft.mesos.server.model.persistence.savedmatch.SavedMatchRestoreResult;
import it.polimi.ingsoft.mesos.server.testing.AdvancedGameStateFixtures;
import it.polimi.ingsoft.mesos.server.testing.GameStateAssertions;
import it.polimi.ingsoft.mesos.server.testing.MesosTestFixtures;
import it.polimi.ingsoft.mesos.server.testing.PostgresIntegrationTestSupport;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

@Tag("integration")
class PostgresSavedMatchExtendedFlowContractTest extends PostgresIntegrationTestSupport {

    @Test
    void restoreAfterTurnFinishedCheckpointPreservesMidDrawStateAndOriginalHost() {
        PostgresSavedMatchService service = this.createService("saved_matches_extended_mid_draw");
        Game game = createGame(2);
        game.setup();

        playPlacementPhase(game);
        Card chosenCard = selectDeterministicCard(game);
        game.chooseCard(game.getTurnOrderTrack().getCurrentPlayer(), chosenCard);

        SavedMatchRestoreResult restoredMatch = saveAndRestore(service, game, SavedMatchCheckpointType.TURN_FINISHED);
        Game restoredGame = restoredMatch.game();

        assertEquals("Alice", restoredMatch.hostNickname());
        assertEquals(RoundPhase.PLAYER_DRAWS, restoredGame.getTurnOrderTrack().getCurrentRoundPhase());
        GameStateAssertions.assertEquivalent(game, restoredGame);

        while (game.getTurnOrderTrack().getCurrentRoundPhase() == RoundPhase.PLAYER_DRAWS) {
            Card liveCard = selectDeterministicCard(game);
            Card restoredCard = selectDeterministicCard(restoredGame);
            game.chooseCard(game.getTurnOrderTrack().getCurrentPlayer(), liveCard);
            restoredGame.chooseCard(restoredGame.getTurnOrderTrack().getCurrentPlayer(), restoredCard);
            GameStateAssertions.assertEquivalent(game, restoredGame);
        }
    }

    @Test
    void restoreAfterEraChangedCheckpointPreservesNewEraAndSupportsNextRoundPlacements() {
        PostgresSavedMatchService service = this.createService("saved_matches_extended_era_change");
        Game game = createGame(2);
        game.setup();

        while (game.getCurrentEra() == Era.first) {
            playFullRound(game);
        }

        SavedMatchRestoreResult restoredMatch = saveAndRestore(service, game, SavedMatchCheckpointType.ERA_CHANGED);
        Game restoredGame = restoredMatch.game();

        assertEquals("Alice", restoredMatch.hostNickname());
        assertEquals(Era.second, restoredGame.getCurrentEra());
        assertEquals(RoundPhase.PLACING_TOTEMS, restoredGame.getTurnOrderTrack().getCurrentRoundPhase());
        GameStateAssertions.assertEquivalent(game, restoredGame);

        game.moveTotem(game.getTurnOrderTrack().getCurrentPlayer(), "B");
        restoredGame.moveTotem(restoredGame.getTurnOrderTrack().getCurrentPlayer(), "B");
        GameStateAssertions.assertEquivalent(game, restoredGame);
    }

    @Test
    void restoreAfterExtraDrawCheckpointPreservesLatePhaseStateAndContinuation() {
        PostgresSavedMatchService service = this.createService("saved_matches_extended_extra_draw");
        Game game = AdvancedGameStateFixtures.createExtraDrawScenario();

        SavedMatchRestoreResult restoredMatch = saveAndRestore(service, game, SavedMatchCheckpointType.TURN_FINISHED);
        Game restoredGame = restoredMatch.game();

        assertEquals("Alice", restoredMatch.hostNickname());
        assertEquals(RoundPhase.EXTRA_DRAWS, restoredGame.getTurnOrderTrack().getCurrentRoundPhase());
        GameStateAssertions.assertEquivalent(game, restoredGame);

        Card liveCard = selectDeterministicCard(game);
        Card restoredCard = selectDeterministicCard(restoredGame);
        game.extraDrawn(game.getTurnOrderTrack().getCurrentPlayer(), liveCard);
        restoredGame.extraDrawn(restoredGame.getTurnOrderTrack().getCurrentPlayer(), restoredCard);
        GameStateAssertions.assertEquivalent(game, restoredGame);

        game.resolveEffects();
        restoredGame.resolveEffects();
        GameStateAssertions.assertEquivalent(game, restoredGame);

        game.resetBoard();
        restoredGame.resetBoard();
        GameStateAssertions.assertEquivalent(game, restoredGame);
    }

    @Test
    void restoreAcrossFinalScoringKeepsOwnershipMetadataBeforeAndAfterCompletion() {
        PostgresSavedMatchService service = this.createService("saved_matches_extended_final_scoring");
        Game game = AdvancedGameStateFixtures.createFinalScoringScenario();

        SavedMatchRestoreResult preScoreRestore = saveAndRestore(service, game, SavedMatchCheckpointType.MATCH_COMPLETED);
        Game restoredGame = preScoreRestore.game();

        assertEquals("Alice", preScoreRestore.hostNickname());
        assertEquals(RoundPhase.FINAL_SCORING, restoredGame.getTurnOrderTrack().getCurrentRoundPhase());
        GameStateAssertions.assertEquivalent(game, restoredGame);

        game.calculateFinalScore();
        restoredGame.calculateFinalScore();
        GameStateAssertions.assertEquivalent(game, restoredGame);
        assertEquals(GameStatus.FINISHED, restoredGame.getStatus());
        assertEquals(28, restoredGame.getPlayers().getFirst().getPrestigePoints());

        SavedMatchRestoreResult finishedRestore = saveAndRestore(service, game, SavedMatchCheckpointType.MATCH_COMPLETED);
        assertEquals("Alice", finishedRestore.hostNickname());
        GameStateAssertions.assertEquivalent(game, finishedRestore.game());
        assertEquals(GameStatus.FINISHED, finishedRestore.game().getStatus());
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

    private static SavedMatchRestoreResult saveAndRestore(PostgresSavedMatchService service,
                                                          Game game,
                                                          SavedMatchCheckpointType checkpointType) {
        service.saveMatch(game, game.getPlayers().getFirst().getNickname(), checkpointType);
        return service.restoreMatch(String.valueOf(game.getId()));
    }

    private static Game createGame(int playerCount) {
        ArrayList<Player> players = new ArrayList<>();
        TotemColor[] totemColors = TotemColor.values();
        String[] nicknames = new String[]{"Alice", "Bob", "Carol", "Dave", "Eve"};

        for (int index = 0; index < playerCount; index++) {
            players.add(new Player(nicknames[index], 0, 0, totemColors[index], null));
        }

        return DefaultGameFactory.getInstance().createGame(players);
    }

    private static void playFullRound(Game game) {
        playPlacementPhase(game);
        playDrawPhase(game);
        playExtraDrawPhase(game);
        assertEquals(RoundPhase.RESOLVING_EFFECTS, game.getTurnOrderTrack().getCurrentRoundPhase());
        game.resolveEffects();
        assertEquals(RoundPhase.RESETTING_BOARD, game.getTurnOrderTrack().getCurrentRoundPhase());
        game.resetBoard();
        assertEquals(RoundPhase.PLACING_TOTEMS, game.getTurnOrderTrack().getCurrentRoundPhase());
    }

    private static void playPlacementPhase(Game game) {
        for (String tile : MesosTestFixtures.placementSequenceFor(game.getPlayers().size())) {
            game.moveTotem(game.getTurnOrderTrack().getCurrentPlayer(), tile);
        }
        assertEquals(RoundPhase.PLAYER_DRAWS, game.getTurnOrderTrack().getCurrentRoundPhase());
    }

    private static void playDrawPhase(Game game) {
        while (game.getTurnOrderTrack().getCurrentRoundPhase() == RoundPhase.PLAYER_DRAWS) {
            game.chooseCard(game.getTurnOrderTrack().getCurrentPlayer(), selectDeterministicCard(game));
        }
    }

    private static void playExtraDrawPhase(Game game) {
        while (game.getTurnOrderTrack().getCurrentRoundPhase() == RoundPhase.EXTRA_DRAWS) {
            game.extraDrawn(game.getTurnOrderTrack().getCurrentPlayer(), selectDeterministicCard(game));
        }
    }

    private static Card selectDeterministicCard(Game game) {
        Player currentPlayer = game.getTurnOrderTrack().getCurrentPlayer();
        List<Card> draftableLowerCards = game.getBoard().getDraftableLowerCards(currentPlayer);
        List<Card> draftableUpperCards = game.getBoard().getDraftableUpperCards(currentPlayer);
        boolean playerDrawPhase = game.getTurnOrderTrack().getCurrentRoundPhase() == RoundPhase.PLAYER_DRAWS;
        boolean canChooseLower = !playerDrawPhase || game.getTurnState().getRemainingLowerChoices(currentPlayer) > 0;
        boolean canChooseUpper = !playerDrawPhase || game.getTurnState().getRemainingUpperChoices(currentPlayer) > 0;

        if (canChooseLower && !draftableLowerCards.isEmpty()) {
            return draftableLowerCards.stream().min(java.util.Comparator.comparingInt(Card::getId)).orElseThrow();
        }
        if (canChooseUpper && !draftableUpperCards.isEmpty()) {
            return draftableUpperCards.stream().min(java.util.Comparator.comparingInt(Card::getId)).orElseThrow();
        }

        throw new IllegalStateException(
                "No draftable cards available for player " + currentPlayer.getNickname()
                        + " during phase " + game.getTurnOrderTrack().getCurrentRoundPhase()
        );
    }
}
