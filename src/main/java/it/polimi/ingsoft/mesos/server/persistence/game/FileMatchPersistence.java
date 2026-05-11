package it.polimi.ingsoft.mesos.server.persistence.game;

import com.fasterxml.jackson.databind.ObjectMapper;
import it.polimi.ingsoft.mesos.server.model.game.Game;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Stream;

public final class FileMatchPersistence implements MatchPersistence {
    private static final String PREFIX = "game-";
    private static final String EXTENSION = ".json";

    private final boolean enabled;
    private final Path directory;
    private final ObjectMapper objectMapper;
    private final GameCheckpointMapper mapper;

    public FileMatchPersistence(boolean enabled, Path directory, ObjectMapper objectMapper) {
        this.enabled = enabled;
        this.directory = Objects.requireNonNull(directory);
        this.objectMapper = Objects.requireNonNull(objectMapper);
        this.mapper = new GameCheckpointMapper();
    }

    @Override
    public void save(Game game, String hostNickname) {
        if (!this.enabled) {
            return;
        }
        GameCheckpoint checkpoint = this.mapper.toCheckpoint(game, hostNickname);
        Path target = this.fileFor(game.getId());
        Path temporary = this.temporaryFileFor(game.getId());
        try {
            Files.createDirectories(this.directory);
            Files.writeString(
                    temporary,
                    this.objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(checkpoint),
                    StandardCharsets.UTF_8
            );
            Files.move(temporary, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (Exception exception) {
            throw new IllegalStateException("Unable to save checkpoint for game " + game.getId(), exception);
        }
    }

    @Override
    public List<RestoredMatch> loadActiveMatches() {
        if (!this.enabled || !Files.isDirectory(this.directory)) {
            return List.of();
        }
        try (Stream<Path> files = Files.list(this.directory)) {
            return files
                    .filter(this::isCheckpointFile)
                    .map(this::tryRead)
                    .flatMap(Optional::stream)
                    .toList();
        } catch (Exception exception) {
            throw new IllegalStateException("Unable to load checkpoints from " + this.directory, exception);
        }
    }

    @Override
    public void delete(int gameId) {
        if (!this.enabled) {
            return;
        }
        try {
            Files.deleteIfExists(this.fileFor(gameId));
        } catch (Exception exception) {
            throw new IllegalStateException("Unable to delete checkpoint for game " + gameId, exception);
        }
    }

    private RestoredMatch read(Path file) {
        try {
            GameCheckpoint checkpoint = this.objectMapper.readValue(
                    Files.readString(file, StandardCharsets.UTF_8),
                    GameCheckpoint.class
            );
            return this.mapper.fromCheckpoint(checkpoint);
        } catch (Exception exception) {
            throw new IllegalStateException("Invalid checkpoint: " + file, exception);
        }
    }

    private Optional<RestoredMatch> tryRead(Path file) {
        try {
            return Optional.of(this.read(file));
        } catch (IllegalStateException exception) {
            return Optional.empty();
        }
    }

    private boolean isCheckpointFile(Path file) {
        String name = file.getFileName().toString();
        return name.startsWith(PREFIX) && name.endsWith(EXTENSION);
    }

    private Path fileFor(int gameId) {
        return this.directory.resolve(PREFIX + gameId + EXTENSION);
    }

    private Path temporaryFileFor(int gameId) {
        return this.directory.resolve(PREFIX + gameId + ".tmp");
    }
}
