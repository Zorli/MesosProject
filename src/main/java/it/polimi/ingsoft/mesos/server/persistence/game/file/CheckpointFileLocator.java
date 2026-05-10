package it.polimi.ingsoft.mesos.server.persistence.game.file;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;
import java.util.stream.Stream;

/**
 * Utility dedicated to file naming and discovery of match checkpoints.
 */
public final class CheckpointFileLocator {
    private static final String CHECKPOINT_PREFIX = "game-";
    private static final String FILE_EXTENSION = ".json";
    private static final String TEMP_SUFFIX = ".tmp";
    private static final String CORRUPT_SUFFIX = ".corrupt";

    private final Path directory;

    public CheckpointFileLocator(Path directory) {
        this.directory = Objects.requireNonNull(directory, "directory cannot be null");
    }

    public Path checkpointFileFor(int gameId) {
        return this.directory.resolve(CHECKPOINT_PREFIX + gameId + FILE_EXTENSION);
    }

    public Path temporaryFileFor(int gameId) {
        return this.directory.resolve(CHECKPOINT_PREFIX + gameId + FILE_EXTENSION + TEMP_SUFFIX);
    }

    public Path corruptedFileFor(int gameId) {
        return this.directory.resolve(CHECKPOINT_PREFIX + gameId + FILE_EXTENSION + CORRUPT_SUFFIX);
    }

    public boolean isTemporary(Path path) {
        return path.getFileName().toString().endsWith(TEMP_SUFFIX);
    }

    public boolean isCheckpointFile(Path path) {
        return path.getFileName().toString().startsWith(CHECKPOINT_PREFIX)
                && path.getFileName().toString().endsWith(FILE_EXTENSION)
                && !isTemporary(path);
    }

    public Stream<Path> listCheckpointFiles() throws IOException {
        if (!Files.exists(this.directory)) {
            return Stream.empty();
        }
        return Files.list(this.directory)
                .filter(this::isCheckpointFile);
    }
}
