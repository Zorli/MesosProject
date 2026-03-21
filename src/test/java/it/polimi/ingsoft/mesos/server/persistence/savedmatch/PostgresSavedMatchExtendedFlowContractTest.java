package it.polimi.ingsoft.mesos.server.persistence.savedmatch;

import it.polimi.ingsoft.mesos.server.ServerRuntimeConfig;
import it.polimi.ingsoft.mesos.server.model.Factory.DefaultGameFactory;
import it.polimi.ingsoft.mesos.server.model.GameState.Game;
import it.polimi.ingsoft.mesos.server.model.GameState.RoundPhase;
import it.polimi.ingsoft.mesos.server.model.cards.Card;
import it.polimi.ingsoft.mesos.server.model.enums.TotemColor;
import it.polimi.ingsoft.mesos.server.model.player.Player;
import it.polimi.ingsoft.mesos.server.persistence.postgres.PostgresDatabaseBootstrap;
import it.polimi.ingsoft.mesos.server.testing.PostgresIntegrationTestSupport;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertIterableEquals;

/**
 * Contract tests for the save/restore flow after the game-state methods beyond totem placement
 * become fully implemented.
 *
 * <p>The current domain model still contains stub implementations for:
 * {@link Game#chooseCard(Player, Card)},
 * {@link Game#extraDrawn(Player, Card)},
 * {@link Game#resolveEffects()},
 * {@link Game#resetBoard()},
 * {@link Game#calculateFinalScore()}.
 *
 * <p>These tests are intentionally disabled until those methods become behaviorally complete.
 * At that point they should be enabled without redesigning the persistence layer.
 */
@Disabled("Enable when draw/effect/reset/final-scoring game-state methods are implemented")
@Tag("integration")
class PostgresSavedMatchExtendedFlowContractTest extends PostgresIntegrationTestSupport {

    @Test
    void restoreAfterDrawPhasePreservesBoardTurnStateAndPlayerTribes() {
        PostgresSavedMatchService service = this.createService("saved_matches_draw_phase");
        Game game = createGame(2);
        game.setup();

        game.moveTotem(game.getTurnOrderTrack().getCurrentPlayer(), "B");
        game.moveTotem(game.getTurnOrderTrack().getCurrentPlayer(), "C");
        assertEquals(RoundPhase.PLAYER_DRAWS, game.getTurnOrderTrack().getCurrentRoundPhase());

        Player activePlayer = game.getTurnOrderTrack().getCurrentPlayer();
        Card chosenCard = game.getBoard().getDraftableLowerCards(activePlayer).getFirst();
        game.chooseCard(activePlayer, chosenCard);

        service.saveMatch(game, game.getPlayers().getFirst().getNickname(), SavedMatchCheckpointType.TURN_STARTED);

        Game restoredGame = service.restoreGame(String.valueOf(game.getId()));
        assertEquivalentState(game, restoredGame);
    }

    @Test
    void restoreAfterBoardResetAndEraChangePreservesDeckOrderAndVisibleRows() {
        PostgresSavedMatchService service = this.createService("saved_matches_reset_board");
        Game game = createGame(5);
        game.setup();

        playFullPlacementRound(game, List.of("B", "C", "D", "E", "F"));
        game.resetBoard();

        service.saveMatch(game, game.getPlayers().getFirst().getNickname(), SavedMatchCheckpointType.ERA_CHANGED);

        Game restoredGame = service.restoreGame(String.valueOf(game.getId()));
        assertEquivalentState(game, restoredGame);
    }

    @Test
    void restoreAfterFinalScoringPreservesCompletedMatchState() {
        PostgresSavedMatchService service = this.createService("saved_matches_final_scoring");
        Game game = createGame(3);
        game.setup();

        playFullPlacementRound(game, List.of("B", "C", "D"));
        game.calculateFinalScore();

        service.saveMatch(game, game.getPlayers().getFirst().getNickname(), SavedMatchCheckpointType.MATCH_COMPLETED);

        Game restoredGame = service.restoreGame(String.valueOf(game.getId()));
        assertEquivalentState(game, restoredGame);
    }

    @Test
    void restoredGameCanContinueAcrossMultipleCompletedCheckpoints() {
        PostgresSavedMatchService service = this.createService("saved_matches_multi_checkpoint");
        Game originalGame = createGame(4);
        originalGame.setup();

        playFullPlacementRound(originalGame, List.of("B", "C", "D", "E"));
        service.saveMatch(originalGame, originalGame.getPlayers().getFirst().getNickname(), SavedMatchCheckpointType.TURN_STARTED);

        Game restoredGame = service.restoreGame(String.valueOf(originalGame.getId()));
        assertEquivalentState(originalGame, restoredGame);

        originalGame.resetBoard();
        restoredGame.resetBoard();
        assertEquivalentState(originalGame, restoredGame);

        originalGame.resolveEffects();
        restoredGame.resolveEffects();
        assertEquivalentState(originalGame, restoredGame);
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

    private static Game createGame(int playerCount) {
        ArrayList<Player> players = new ArrayList<>();
        TotemColor[] totemColors = TotemColor.values();
        String[] nicknames = new String[]{"Alice", "Bob", "Carol", "Dave", "Eve"};

        for (int index = 0; index < playerCount; index++) {
            players.add(new Player(nicknames[index], 0, 0, totemColors[index], null));
        }

        return DefaultGameFactory.getInstance().createGame(players);
    }

    private static void playFullPlacementRound(Game game, List<String> placementSequence) {
        for (String tile : placementSequence) {
            game.moveTotem(game.getTurnOrderTrack().getCurrentPlayer(), tile);
        }
    }

    private static void assertEquivalentState(Game expected, Game actual) {
        assertEquals(expected.getId(), actual.getId());
        assertEquals(expected.getCurrentEra(), actual.getCurrentEra());
        assertEquals(expected.getStatus(), actual.getStatus());
        assertEquals(expected.getTurnOrderTrack().getRoundNumber(), actual.getTurnOrderTrack().getRoundNumber());
        assertEquals(expected.getTurnOrderTrack().getCurrentRoundPhase(), actual.getTurnOrderTrack().getCurrentRoundPhase());
        assertEquals(expected.getTurnOrderTrack().getCurrentPlayerIndex(), actual.getTurnOrderTrack().getCurrentPlayerIndex());
        assertEquals(expected.getTurnState().getPhase(), actual.getTurnState().getPhase());
        assertIterableEquals(
                expected.getPlayers().stream().map(Player::getNickname).toList(),
                actual.getPlayers().stream().map(Player::getNickname).toList()
        );
        assertIterableEquals(
                expected.getBoard().getUpperRowCards().stream().map(Card::getId).toList(),
                actual.getBoard().getUpperRowCards().stream().map(Card::getId).toList()
        );
        assertIterableEquals(
                expected.getBoard().getLowerRowCards().stream().map(Card::getId).toList(),
                actual.getBoard().getLowerRowCards().stream().map(Card::getId).toList()
        );
        assertIterableEquals(
                expected.getBoard().getRemainingTribeDeckCards().stream().map(Card::getId).toList(),
                actual.getBoard().getRemainingTribeDeckCards().stream().map(Card::getId).toList()
        );
        assertIterableEquals(
                expected.getBoard().getRemainingBuildingDeckCards().stream().map(Card::getId).toList(),
                actual.getBoard().getRemainingBuildingDeckCards().stream().map(Card::getId).toList()
        );
    }
}
