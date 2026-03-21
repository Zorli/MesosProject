package it.polimi.ingsoft.mesos.server.persistence.savedmatch;

import it.polimi.ingsoft.mesos.server.ServerRuntimeConfig;
import it.polimi.ingsoft.mesos.server.model.GameState.Game;
import it.polimi.ingsoft.mesos.server.model.GameState.RoundPhase;
import it.polimi.ingsoft.mesos.server.persistence.postgres.PostgresDatabaseBootstrap;
import it.polimi.ingsoft.mesos.server.testing.AdvancedGameStateFixtures;
import it.polimi.ingsoft.mesos.server.testing.GameStateAssertions;
import it.polimi.ingsoft.mesos.server.testing.PostgresIntegrationTestSupport;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static it.polimi.ingsoft.mesos.server.testing.MesosTestFixtures.findUpperRowCard;
import static org.junit.jupiter.api.Assertions.assertEquals;

@Tag("integration")
class PostgresSavedMatchLatePhaseIntegrationTest extends PostgresIntegrationTestSupport {

    @Test
    void restoreDuringExtraDrawPhasePreservesLateGameCheckpointAndContinuation() {
        PostgresSavedMatchService service = this.createService("saved_matches_extra_draw_phase");
        Game originalGame = AdvancedGameStateFixtures.createExtraDrawScenario();

        service.saveMatch(originalGame, "Alice", SavedMatchCheckpointType.TURN_FINISHED);

        Game restoredGame = service.restoreGame(String.valueOf(originalGame.getId()));
        GameStateAssertions.assertEquivalent(originalGame, restoredGame);
        assertEquals(RoundPhase.EXTRA_DRAWS, restoredGame.getTurnOrderTrack().getCurrentRoundPhase());

        originalGame.extraDrawn(originalGame.getTurnOrderTrack().getCurrentPlayer(), findUpperRowCard(originalGame, 66));
        restoredGame.extraDrawn(restoredGame.getTurnOrderTrack().getCurrentPlayer(), findUpperRowCard(restoredGame, 66));
        GameStateAssertions.assertEquivalent(originalGame, restoredGame);
        assertEquals(RoundPhase.RESOLVING_EFFECTS, restoredGame.getTurnOrderTrack().getCurrentRoundPhase());

        originalGame.resolveEffects();
        restoredGame.resolveEffects();
        GameStateAssertions.assertEquivalent(originalGame, restoredGame);
        assertEquals(RoundPhase.RESETTING_BOARD, restoredGame.getTurnOrderTrack().getCurrentRoundPhase());
    }

    @Test
    void restoreDuringFinalScoringPreservesLateGameRosterAndFinalScoreOutcome() {
        PostgresSavedMatchService service = this.createService("saved_matches_final_scoring_phase");
        Game originalGame = AdvancedGameStateFixtures.createFinalScoringScenario();

        service.saveMatch(originalGame, "Alice", SavedMatchCheckpointType.MATCH_COMPLETED);

        Game restoredGame = service.restoreGame(String.valueOf(originalGame.getId()));
        GameStateAssertions.assertEquivalent(originalGame, restoredGame);
        assertEquals(RoundPhase.FINAL_SCORING, restoredGame.getTurnOrderTrack().getCurrentRoundPhase());

        originalGame.calculateFinalScore();
        restoredGame.calculateFinalScore();
        GameStateAssertions.assertEquivalent(originalGame, restoredGame);
        assertEquals(28, restoredGame.getPlayers().getFirst().getPrestigePoints());
        assertEquals(it.polimi.ingsoft.mesos.server.model.enums.GameStatus.FINISHED, restoredGame.getStatus());
    }

    private PostgresSavedMatchService createService(String dbPrefix) {
        ServerRuntimeConfig runtimeConfig = this.createRuntimeConfig(dbPrefix, true);
        PostgresDatabaseBootstrap.getInstance().ensureReady(
                runtimeConfig,
                java.util.List.of(PostgresSavedMatchSchemaModule.getInstance())
        );

        return new PostgresSavedMatchService(
                runtimeConfig.toPostgresConnectionConfig(),
                runtimeConfig.maxBusyRetries()
        );
    }
}
