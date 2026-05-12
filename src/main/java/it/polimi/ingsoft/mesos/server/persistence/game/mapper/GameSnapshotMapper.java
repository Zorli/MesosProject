package it.polimi.ingsoft.mesos.server.persistence.game.mapper;

import it.polimi.ingsoft.mesos.server.model.game.Game;
import it.polimi.ingsoft.mesos.server.persistence.game.dto.SavedMatchSnapshot;

import java.time.Clock;
import java.time.Instant;
import java.util.Objects;

/**
 * Coordinates mapping of a full {@link Game} runtime state into a persistence snapshot.
 */
public final class GameSnapshotMapper {
    private static final int SNAPSHOT_SCHEMA_VERSION = 1;

    private final Clock clock;
    private final PlayerSnapshotMapper playerSnapshotMapper;
    private final BoardSnapshotMapper boardSnapshotMapper;
    private final TurnSnapshotMapper turnSnapshotMapper;

    public GameSnapshotMapper() {
        this(Clock.systemUTC());
    }

    public GameSnapshotMapper(Clock clock) {
        this.clock = Objects.requireNonNull(clock, "clock cannot be null");
        this.playerSnapshotMapper = new PlayerSnapshotMapper();
        this.boardSnapshotMapper = new BoardSnapshotMapper();
        this.turnSnapshotMapper = new TurnSnapshotMapper();
    }

    public SavedMatchSnapshot toSnapshot(Game game, String hostNickname) {
        if (game == null) {
            throw new IllegalArgumentException("game cannot be null");
        }
        if (hostNickname == null || hostNickname.isBlank()) {
            throw new IllegalArgumentException("hostNickname cannot be null or blank");
        }

        return new SavedMatchSnapshot(
                SNAPSHOT_SCHEMA_VERSION,
                Instant.now(this.clock).toString(),
                new SavedMatchSnapshot.GameSnapshot(
                        game.getId(),
                        hostNickname,
                        game.getStatus().name(),
                        game.getCurrentEra().name(),
                        game.getStateVersion()
                ),
                game.getPlayers()
                        .stream()
                        .map(this.playerSnapshotMapper::toSnapshot)
                        .toList(),
                this.boardSnapshotMapper.toSnapshot(game.getBoard()),
                this.turnSnapshotMapper.toSnapshot(game.getTurnOrderTrack()),
                this.turnSnapshotMapper.toSnapshot(game.getTurnState(), game.getPlayers())
        );
    }
}
