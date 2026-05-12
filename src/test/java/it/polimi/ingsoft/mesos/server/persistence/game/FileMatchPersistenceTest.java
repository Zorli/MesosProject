package it.polimi.ingsoft.mesos.server.persistence.game;

import com.fasterxml.jackson.databind.ObjectMapper;
import it.polimi.ingsoft.mesos.server.persistence.game.dto.SavedCardSnapshot;
import it.polimi.ingsoft.mesos.server.persistence.game.dto.SavedMatchSnapshot;
import it.polimi.ingsoft.mesos.server.persistence.game.file.CheckpointFileLocator;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FileMatchPersistenceTest {
    @TempDir
    Path tempDirectory;

    @Test
    void saveAndLoadRoundTripsSnapshot() {
        FileMatchPersistence persistence = new FileMatchPersistence(
                true,
                tempDirectory,
                new ObjectMapper(),
                Clock.fixed(Instant.parse("2026-05-09T10:00:00Z"), ZoneOffset.UTC)
        );
        SavedMatchSnapshot snapshot = sampleSnapshot(7, 1);

        persistence.save(snapshot);
        List<SavedMatchSnapshot> loaded = persistence.loadActiveMatches();

        assertEquals(1, loaded.size());
        assertEquals(snapshot.schemaVersion(), loaded.getFirst().schemaVersion());
        assertEquals(snapshot.game().id(), loaded.getFirst().game().id());
    }

    @Test
    void loadIgnoresCorruptCheckpointAndQuarantinesIt() throws Exception {
        Files.writeString(new CheckpointFileLocator(tempDirectory).checkpointFileFor(8), "NOT_JSON");
        FileMatchPersistence persistence = new FileMatchPersistence(
                true,
                tempDirectory,
                new ObjectMapper(),
                Clock.fixed(Instant.parse("2026-05-09T10:00:00Z"), ZoneOffset.UTC)
        );

        persistence.save(sampleSnapshot(7, 1));
        List<SavedMatchSnapshot> loaded = persistence.loadActiveMatches();

        assertEquals(1, loaded.size());
        assertEquals(7, loaded.getFirst().game().id());
        assertTrue(Files.exists(new CheckpointFileLocator(tempDirectory).corruptedFileFor(8)));
    }

    @Test
    void loadReturnsEmptyWhenDirectoryIsMissing() {
        Path missingDirectory = tempDirectory.resolve("missing");
        FileMatchPersistence persistence = new FileMatchPersistence(
                true,
                missingDirectory,
                new ObjectMapper(),
                Clock.systemUTC()
        );

        assertEquals(0, persistence.loadActiveMatches().size());
    }

    @Test
    void disabledPersistenceDoesNotCreateOrDeleteFiles() throws Exception {
        FileMatchPersistence persistence = new FileMatchPersistence(
                false,
                tempDirectory,
                new ObjectMapper(),
                Clock.systemUTC()
        );

        persistence.save(sampleSnapshot(7, 1));
        persistence.delete(7);

        try (Stream<Path> snapshots = Files.list(tempDirectory)) {
            assertTrue(snapshots.toList().isEmpty());
        }
        assertTrue(persistence.loadActiveMatches().isEmpty());
    }

    @Test
    void saveNormalizesLegacySchemaAndDeleteIsIdempotent() {
        CheckpointFileLocator locator = new CheckpointFileLocator(tempDirectory);
        FileMatchPersistence persistence = new FileMatchPersistence(
                true,
                tempDirectory,
                new ObjectMapper(),
                Clock.fixed(Instant.parse("2026-05-09T10:00:00Z"), ZoneOffset.UTC)
        );

        persistence.save(sampleSnapshotLegacyVersion(9));
        List<SavedMatchSnapshot> loaded = persistence.loadActiveMatches();
        assertEquals(1, loaded.size());
        assertEquals(1, loaded.getFirst().schemaVersion());

        persistence.delete(9);
        persistence.delete(9);
        assertFalse(Files.exists(locator.checkpointFileFor(9)));
    }

    private static SavedMatchSnapshot sampleSnapshot(int gameId, int schemaVersion) {
        return new SavedMatchSnapshot(
                schemaVersion,
                "2026-05-09T10:00:00Z",
                new SavedMatchSnapshot.GameSnapshot(gameId, "host", "IN_PROGRESS", "first", 55L),
                List.of(
                        new SavedMatchSnapshot.PlayerSnapshot(
                                "Alice",
                                "RED",
                                5,
                                2,
                                new SavedMatchSnapshot.TribeSnapshot(
                                        List.of(100),
                                        List.of(new SavedCardSnapshot(200, Map.of("PAIR", 1)))
                                )
                        )
                ),
                new SavedMatchSnapshot.BoardSnapshot(
                        List.of(1, 2),
                        List.of(10, 11),
                        List.of(3),
                        List.of(12),
                        List.of(4),
                        List.of(13),
                        List.of(),
                        List.of(
                                new SavedMatchSnapshot.StartingBlockSnapshot(
                                        1,
                                        0,
                                        0,
                                        "Alice"
                                ),
                                new SavedMatchSnapshot.StartingBlockSnapshot(
                                        2,
                                        -1,
                                        -2,
                                        null
                                )
                        )
                ),
                new SavedMatchSnapshot.TurnOrderSnapshot(
                        List.of("Alice"),
                        0,
                        1,
                        "PLAYER_TURN"
                ),
                new SavedMatchSnapshot.TurnStateSnapshot(
                        "TURN_COMPLETED",
                        Map.of("Alice", 0),
                        Map.of("Alice", 0)
                )
        );
    }

    private static SavedMatchSnapshot sampleSnapshotLegacyVersion(int gameId) {
        return sampleSnapshot(gameId, 0);
    }
}
