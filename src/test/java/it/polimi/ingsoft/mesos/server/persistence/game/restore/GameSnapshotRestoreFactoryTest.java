package it.polimi.ingsoft.mesos.server.persistence.game.restore;

import it.polimi.ingsoft.mesos.server.model.enums.GameStatus;
import it.polimi.ingsoft.mesos.server.model.game.Game;
import it.polimi.ingsoft.mesos.server.persistence.game.dto.SavedMatchSnapshot;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class GameSnapshotRestoreFactoryTest {

    private static final GameSnapshotRestoreFactory FACTORY = new GameSnapshotRestoreFactory();

    @Test
    void fromSnapshotRestoresGameWithPreservedStateVersion() {
        SavedMatchSnapshot snapshot = minimalInProgressSnapshot(42, 7L);

        RestoredMatch restored = FACTORY.fromSnapshot(snapshot);

        assertNotNull(restored);
        Game game = restored.game();
        assertEquals(42, game.getId());
        assertEquals(7L, game.getStateVersion());
        assertEquals(GameStatus.IN_PROGRESS, game.getStatus());
        assertEquals("Alice", restored.hostNickname());
        assertEquals(2, restored.players().size());
    }

    @Test
    void fromSnapshotRejectsUnknownOfferSpaceCode() {
        SavedMatchSnapshot snapshot = snapshotWithOfferSpaceCode("Z");

        assertThrows(IllegalArgumentException.class, () -> FACTORY.fromSnapshot(snapshot));
    }

    @Test
    void fromSnapshotRejectsDuplicateStartingBlockPosition() {
        SavedMatchSnapshot snapshot = snapshotWithDuplicateStartingBlockPosition();

        assertThrows(IllegalArgumentException.class, () -> FACTORY.fromSnapshot(snapshot));
    }

    private static SavedMatchSnapshot minimalInProgressSnapshot(int gameId, long stateVersion) {
        return new SavedMatchSnapshot(
                1,
                "2026-05-09T10:00:00Z",
                new SavedMatchSnapshot.GameSnapshot(gameId, "Alice", "IN_PROGRESS", "first", stateVersion),
                List.of(
                        new SavedMatchSnapshot.PlayerSnapshot("Alice", "RED", 0, 0,
                                new SavedMatchSnapshot.TribeSnapshot(List.of(), List.of())),
                        new SavedMatchSnapshot.PlayerSnapshot("Bob", "BLUE", 0, 0,
                                new SavedMatchSnapshot.TribeSnapshot(List.of(), List.of()))
                ),
                emptyBoardSnapshot(),
                new SavedMatchSnapshot.TurnOrderSnapshot(List.of("Alice", "Bob"), 0, 1, "PLACING_TOTEMS"),
                new SavedMatchSnapshot.TurnStateSnapshot("TURN_COMPLETED", Map.of(), Map.of())
        );
    }

    private static SavedMatchSnapshot snapshotWithOfferSpaceCode(String invalidCode) {
        return new SavedMatchSnapshot(
                1,
                "2026-05-09T10:00:00Z",
                new SavedMatchSnapshot.GameSnapshot(1, "Alice", "IN_PROGRESS", "first", 1L),
                List.of(
                        new SavedMatchSnapshot.PlayerSnapshot("Alice", "RED", 0, 0,
                                new SavedMatchSnapshot.TribeSnapshot(List.of(), List.of())),
                        new SavedMatchSnapshot.PlayerSnapshot("Bob", "BLUE", 0, 0,
                                new SavedMatchSnapshot.TribeSnapshot(List.of(), List.of()))
                ),
                new SavedMatchSnapshot.BoardSnapshot(
                        List.of(), List.of(), List.of(), List.of(), List.of(), List.of(),
                        List.of(new SavedMatchSnapshot.OfferSpaceSnapshot(invalidCode, 0, 0, 1, 0, null)),
                        List.of(
                                new SavedMatchSnapshot.StartingBlockSnapshot(1, 0, 0, null),
                                new SavedMatchSnapshot.StartingBlockSnapshot(2, 0, 0, null)
                        )
                ),
                new SavedMatchSnapshot.TurnOrderSnapshot(List.of("Alice", "Bob"), 0, 1, "PLACING_TOTEMS"),
                new SavedMatchSnapshot.TurnStateSnapshot("TURN_COMPLETED", Map.of(), Map.of())
        );
    }

    private static SavedMatchSnapshot snapshotWithDuplicateStartingBlockPosition() {
        return new SavedMatchSnapshot(
                1,
                "2026-05-09T10:00:00Z",
                new SavedMatchSnapshot.GameSnapshot(1, "Alice", "IN_PROGRESS", "first", 1L),
                List.of(
                        new SavedMatchSnapshot.PlayerSnapshot("Alice", "RED", 0, 0,
                                new SavedMatchSnapshot.TribeSnapshot(List.of(), List.of())),
                        new SavedMatchSnapshot.PlayerSnapshot("Bob", "BLUE", 0, 0,
                                new SavedMatchSnapshot.TribeSnapshot(List.of(), List.of()))
                ),
                new SavedMatchSnapshot.BoardSnapshot(
                        List.of(), List.of(), List.of(), List.of(), List.of(), List.of(),
                        List.of(
                                new SavedMatchSnapshot.OfferSpaceSnapshot("B", 0, 0, 1, 0, null),
                                new SavedMatchSnapshot.OfferSpaceSnapshot("C", 1, 0, 0, 1, null)
                        ),
                        List.of(
                                new SavedMatchSnapshot.StartingBlockSnapshot(1, 0, 0, null),
                                new SavedMatchSnapshot.StartingBlockSnapshot(1, 0, 0, null)
                        )
                ),
                new SavedMatchSnapshot.TurnOrderSnapshot(List.of("Alice", "Bob"), 0, 1, "PLACING_TOTEMS"),
                new SavedMatchSnapshot.TurnStateSnapshot("TURN_COMPLETED", Map.of(), Map.of())
        );
    }

    private static SavedMatchSnapshot.BoardSnapshot emptyBoardSnapshot() {
        return new SavedMatchSnapshot.BoardSnapshot(
                List.of(), List.of(), List.of(), List.of(), List.of(), List.of(),
                List.of(
                        new SavedMatchSnapshot.OfferSpaceSnapshot("B", 0, 0, 1, 0, null),
                        new SavedMatchSnapshot.OfferSpaceSnapshot("C", 1, 0, 0, 1, null)
                ),
                List.of(
                        new SavedMatchSnapshot.StartingBlockSnapshot(1, 0, 0, null),
                        new SavedMatchSnapshot.StartingBlockSnapshot(2, 0, 0, null)
                )
        );
    }
}
