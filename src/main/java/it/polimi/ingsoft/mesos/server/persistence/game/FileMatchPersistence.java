package it.polimi.ingsoft.mesos.server.persistence.game;

import com.fasterxml.jackson.databind.ObjectMapper;
import it.polimi.ingsoft.mesos.server.persistence.game.dto.SavedMatchSnapshot;
import it.polimi.ingsoft.mesos.server.persistence.game.file.AtomicCheckpointWriter;
import it.polimi.ingsoft.mesos.server.persistence.game.file.CheckpointFileLocator;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Stream;

/**
 * Filesystem checkpoint persistence implementation.
 */
public final class FileMatchPersistence implements MatchPersistence {
    private static final Logger log = Logger.getLogger(FileMatchPersistence.class.getName());
    private static final int SCHEMA_VERSION = 1;
    private static final String CHECKPOINT_PREFIX = "game-";
    private static final String FILE_EXTENSION = ".json";

    private final boolean enabled;
    private final Path persistenceDirectory;
    private final ObjectMapper objectMapper;
    private final Clock clock;
    private final CheckpointFileLocator fileLocator;
    private final AtomicCheckpointWriter writer;

    public FileMatchPersistence(
            boolean enabled,
            Path persistenceDirectory,
            ObjectMapper objectMapper,
            Clock clock
    ) {
        this(
                enabled,
                persistenceDirectory,
                objectMapper,
                clock,
                new CheckpointFileLocator(persistenceDirectory),
                new AtomicCheckpointWriter()
        );
    }

    public FileMatchPersistence(
            boolean enabled,
            Path persistenceDirectory,
            ObjectMapper objectMapper
    ) {
        this(
                enabled,
                persistenceDirectory,
                objectMapper,
                Clock.systemUTC()
        );
    }

    public FileMatchPersistence(
            boolean enabled,
            Path persistenceDirectory,
            ObjectMapper objectMapper,
            Clock clock,
            CheckpointFileLocator fileLocator,
            AtomicCheckpointWriter writer
    ) {
        this.enabled = enabled;
        this.persistenceDirectory = Objects.requireNonNull(persistenceDirectory, "persistenceDirectory cannot be null");
        this.objectMapper = Objects.requireNonNull(objectMapper, "objectMapper cannot be null");
        this.clock = Objects.requireNonNull(clock, "clock cannot be null");
        this.fileLocator = Objects.requireNonNull(fileLocator, "fileLocator cannot be null");
        this.writer = Objects.requireNonNull(writer, "writer cannot be null");
    }

    @Override
    public List<SavedMatchSnapshot> loadActiveMatches() {
        if (!this.enabled) {
            log.info("[Persistence] Game persistence is disabled — skipping checkpoint load.");
            return List.of();
        }

        log.info("[Persistence] Loading checkpoints from: " + this.persistenceDirectory.toAbsolutePath());
        try (Stream<Path> snapshots = this.fileLocator.listCheckpointFiles()) {
            List<SavedMatchSnapshot> result = snapshots
                    .map(this::safeReadCheckpoint)
                    .filter(Objects::nonNull)
                    .toList();
            log.info("[Persistence] Loaded " + result.size() + " checkpoint(s).");
            return result;
        } catch (Exception exception) {
            throw new IllegalStateException("Unable to list checkpoint files", exception);
        }
    }

    private SavedMatchSnapshot safeReadCheckpoint(Path path) {
        try {
            SavedMatchSnapshot snapshot = this.readCheckpoint(path);
            log.info("[Persistence] Read checkpoint: " + path.getFileName() + " (game=" + snapshot.game().id() + ")");
            return snapshot;
        } catch (RuntimeException exception) {
            log.warning("[Persistence] Corrupt checkpoint, quarantining: " + path + " — " + exception.getMessage());
            this.moveToQuarantine(path);
            return null;
        }
    }

    @Override
    public void save(SavedMatchSnapshot snapshot) {
        if (!this.enabled) {
            return;
        }
        Objects.requireNonNull(snapshot, "snapshot cannot be null");
        int gameId = snapshot.game().id();
        log.info("[Persistence] Saving checkpoint for game " + gameId + " to: "
                + this.fileLocator.checkpointFileFor(gameId).toAbsolutePath());
        SavedMatchSnapshot normalizedSnapshot = this.normalize(snapshot);
        this.writer.writeAtomically(
                this.fileLocator.checkpointFileFor(gameId),
                this.fileLocator.temporaryFileFor(gameId),
                this.serialize(normalizedSnapshot)
        );
    }

    @Override
    public void delete(int gameId) {
        if (!this.enabled) {
            return;
        }

        Path checkpointFile = this.fileLocator.checkpointFileFor(gameId);
        try {
            boolean deleted = Files.deleteIfExists(checkpointFile);
            if (deleted) {
                log.info("[Persistence] Checkpoint deleted for game " + gameId + ": " + checkpointFile.toAbsolutePath());
            }
        } catch (Exception exception) {
            throw new IllegalStateException("Unable to delete checkpoint for game " + gameId, exception);
        }
    }

    private SavedMatchSnapshot normalize(SavedMatchSnapshot snapshot) {
        int schemaVersion = snapshot.schemaVersion() != 0 ? snapshot.schemaVersion() : SCHEMA_VERSION;
        String savedAtUtc = snapshot.savedAtUtc() != null ? snapshot.savedAtUtc() : this.clock.instant().toString();
        return new SavedMatchSnapshot(
                schemaVersion,
                savedAtUtc,
                snapshot.game(),
                snapshot.players(),
                snapshot.board(),
                snapshot.turnOrder(),
                snapshot.turnState()
        );
    }

    private SavedMatchSnapshot readCheckpoint(Path path) {
        try {
            return this.objectMapper.readValue(
                    Files.readString(path, StandardCharsets.UTF_8),
                    SavedMatchSnapshot.class
            );
        } catch (Exception exception) {
            throw new IllegalStateException("Unable to read checkpoint " + path, exception);
        }
    }

    private void moveToQuarantine(Path path) {
        String fileName = path.getFileName().toString();
        String gameIdAsString = fileName.replace(CHECKPOINT_PREFIX, "");
        int dotIndex = gameIdAsString.indexOf(FILE_EXTENSION);
        if (dotIndex <= 0) {
            return;
        }

        String gameIdText = gameIdAsString.substring(0, dotIndex);
        try {
            int gameId = Integer.parseInt(gameIdText);
            Files.move(
                    path,
                    this.fileLocator.corruptedFileFor(gameId),
                    java.nio.file.StandardCopyOption.REPLACE_EXISTING
            );
        } catch (Exception ignored) {
            // best-effort quarantine: malformed file names are ignored, still considered corrupted
        }
    }

    private byte[] serialize(SavedMatchSnapshot snapshot) {
        try {
            return this.objectMapper
                    .writerWithDefaultPrettyPrinter()
                    .writeValueAsBytes(snapshot);
        } catch (Exception exception) {
            log.log(Level.SEVERE, "[Persistence] Jackson serialization error", exception);
            throw new IllegalStateException("Unable to serialize checkpoint payload", exception);
        }
    }
}
