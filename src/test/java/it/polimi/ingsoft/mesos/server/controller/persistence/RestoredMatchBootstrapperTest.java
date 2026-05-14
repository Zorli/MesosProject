package it.polimi.ingsoft.mesos.server.controller.persistence;

import it.polimi.ingsoft.mesos.server.controller.ServerSessionRegistry;
import it.polimi.ingsoft.mesos.server.model.board.Board;
import it.polimi.ingsoft.mesos.server.model.cards.BuildingDeck;
import it.polimi.ingsoft.mesos.server.model.cards.TribeDeck;
import it.polimi.ingsoft.mesos.server.model.enums.Era;
import it.polimi.ingsoft.mesos.server.model.enums.GameStatus;
import it.polimi.ingsoft.mesos.server.model.enums.LobbyStatus;
import it.polimi.ingsoft.mesos.server.model.game.*;
import it.polimi.ingsoft.mesos.server.model.game.enums.RoundPhase;
import it.polimi.ingsoft.mesos.server.model.player.Player;
import it.polimi.ingsoft.mesos.server.persistence.game.MatchPersistence;
import it.polimi.ingsoft.mesos.server.persistence.game.dto.SavedMatchSnapshot;
import it.polimi.ingsoft.mesos.server.persistence.game.mapper.GameSnapshotMapper;
import it.polimi.ingsoft.mesos.server.testing.MesosTestFixtures;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;

class RestoredMatchBootstrapperTest {

    @Test
    void restoreActiveMatchRegistersResumableLobby() {
        RecordingMatchPersistence persistence = new RecordingMatchPersistence();
        ServerSessionRegistry sessions = new ServerSessionRegistry();
        persistence.snapshots = List.of(createSnapshot(42, GameStatus.IN_PROGRESS, "Alice"));

        RestoredMatchBootstrapper.restoreActiveMatches(
                persistence,
                sessions
        );

        assertTrue(sessions.getGameOrchestrator(42) == null);
        assertNotNull(sessions.getLobbyController(42));
        assertEquals(LobbyStatus.RESUMABLE, sessions.getLobbyModel(42).orElseThrow().getStatus());
        assertEquals(1, persistence.loadCount);
        assertEquals(0, persistence.deleteCount);
    }

    @Test
    void invalidSnapshotIsSkippedAndDeletedFromPersistence() {
        RecordingMatchPersistence persistence = new RecordingMatchPersistence();
        ServerSessionRegistry sessions = new ServerSessionRegistry();
        persistence.snapshots = List.of(corruptedSnapshot(44));

        RestoredMatchBootstrapper.restoreActiveMatches(
                persistence,
                sessions
        );

        assertEquals(1, persistence.deleteCount);
        assertEquals(1, persistence.loadCount);
        assertTrue(sessions.getGameOrchestrator(44) == null);
    }

    @Test
    void restoreActiveMatchSkipsInvalidAndRestoresValidOne() {
        RecordingMatchPersistence persistence = new RecordingMatchPersistence();
        ServerSessionRegistry sessions = new ServerSessionRegistry();
        persistence.snapshots = List.of(
                corruptedSnapshot(44),
                createSnapshot(42, GameStatus.IN_PROGRESS, "Alice")
        );

        RestoredMatchBootstrapper.restoreActiveMatches(
                persistence,
                sessions
        );

        assertEquals(1, persistence.deleteCount);
        assertTrue(sessions.getGameOrchestrator(42) == null);
        assertNotNull(sessions.getLobbyController(42));
        assertEquals(LobbyStatus.RESUMABLE, sessions.getLobbyModel(42).orElseThrow().getStatus());
    }

    @Test
    void finishedSnapshotIsDeletedAndNotRestored() {
        RecordingMatchPersistence persistence = new RecordingMatchPersistence();
        ServerSessionRegistry sessions = new ServerSessionRegistry();
        persistence.snapshots = List.of(createSnapshot(43, GameStatus.FINISHED, "Alice"));

        RestoredMatchBootstrapper.restoreActiveMatches(
                persistence,
                sessions
        );

        assertEquals(1, persistence.deleteCount);
        assertTrue(sessions.getGameOrchestrator(43) == null);
    }

    @Test
    void loadFailureDoesNotBreakRestoreAndNothingIsRestored() {
        RecordingMatchPersistence persistence = new RecordingMatchPersistence() {
            @Override
            public List<SavedMatchSnapshot> loadActiveMatches() {
                this.loadCount++;
                throw new RuntimeException("i/o error");
            }
        };
        ServerSessionRegistry sessions = new ServerSessionRegistry();

        RestoredMatchBootstrapper.restoreActiveMatches(
                persistence,
                sessions
        );

        assertEquals(1, persistence.loadCount);
        assertEquals(0, persistence.deleteCount);
        assertTrue(sessions.getGameOrchestrator(99) == null);
    }

    private SavedMatchSnapshot corruptedSnapshot(int gameId) {
        return new SavedMatchSnapshot(
                1,
                "2026-01-01T00:00:00Z",
                new SavedMatchSnapshot.GameSnapshot(
                        gameId,
                        "Alice",
                        "NOT_A_STATUS",
                        Era.first.name(),
                        1L
                ),
                List.of(),
                new SavedMatchSnapshot.BoardSnapshot(
                        List.of(),
                        List.of(),
                        List.of(),
                        List.of(),
                        List.of(),
                        List.of(),
                        List.of(),
                        List.of()
                ),
                new SavedMatchSnapshot.TurnOrderSnapshot(List.of("Alice"), 0, 1, "PLACING_TOTEMS"),
                new SavedMatchSnapshot.TurnStateSnapshot("WAITING_FOR_CARD_SELECTION", java.util.Map.of(), java.util.Map.of())
        );
    }

    private SavedMatchSnapshot createSnapshot(int gameId, GameStatus status, String hostNickname) {
        ArrayList<Player> players = MesosTestFixtures.createPlayers("Alice", "Bob");
        Game game = new Game(
                gameId,
                players,
                new PlayersTurnFSM(),
                new TurnOrderTrack(players, 0, 1, RoundPhase.PLACING_TOTEMS),
                new Board(
                        TribeDeck.fromOrderedCards(new ArrayList<>()),
                        BuildingDeck.fromOrderedCards(new ArrayList<>()),
                        players
                ),
                Era.first,
                status
        );

        SavedMatchSnapshot snapshot = new GameSnapshotMapper().toSnapshot(game, hostNickname);
        if (status == GameStatus.SETUP) {
            return snapshot;
        }

        return withStatus(snapshot, status);
    }

    private SavedMatchSnapshot withStatus(SavedMatchSnapshot snapshot, GameStatus status) {
        return new SavedMatchSnapshot(
                snapshot.schemaVersion(),
                snapshot.savedAtUtc(),
                new SavedMatchSnapshot.GameSnapshot(
                        snapshot.game().id(),
                        snapshot.game().hostNickname(),
                        status.name(),
                        snapshot.game().currentEra(),
                        snapshot.game().stateVersion()
                ),
                snapshot.players(),
                snapshot.board(),
                snapshot.turnOrder(),
                snapshot.turnState()
        );
    }

    private static class RecordingMatchPersistence implements MatchPersistence {
        int loadCount;
        int deleteCount;
        List<SavedMatchSnapshot> snapshots = List.of();

        @Override
        public List<SavedMatchSnapshot> loadActiveMatches() {
            this.loadCount++;
            return snapshots;
        }

        @Override
        public void save(SavedMatchSnapshot snapshot) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void delete(int gameId) {
            this.deleteCount++;
        }
    }
}
