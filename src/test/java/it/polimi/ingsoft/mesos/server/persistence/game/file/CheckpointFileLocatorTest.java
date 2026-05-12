package it.polimi.ingsoft.mesos.server.persistence.game.file;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertIterableEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CheckpointFileLocatorTest {
    @TempDir
    Path tempDirectory;

    private CheckpointFileLocator locator;

    @org.junit.jupiter.api.BeforeEach
    void setUp() {
        this.locator = new CheckpointFileLocator(tempDirectory);
    }

    @Test
    void resolvesCheckpointAndTemporaryPaths() {
        assertEquals("game-42.json", locator.checkpointFileFor(42).getFileName().toString());
        assertEquals("game-42.json.tmp", locator.temporaryFileFor(42).getFileName().toString());
        assertEquals("game-42.json.corrupt", locator.corruptedFileFor(42).getFileName().toString());
        assertFalse(locator.isTemporary(locator.checkpointFileFor(42)));
        assertTrue(locator.isTemporary(locator.temporaryFileFor(42)));
    }

    @Test
    void treatsCheckpointJsonAsCandidateAndSkipsTemporaryFiles() throws IOException {
        Files.writeString(locator.checkpointFileFor(1), "{}", java.nio.charset.StandardCharsets.UTF_8);
        Files.writeString(locator.temporaryFileFor(2), "{}", java.nio.charset.StandardCharsets.UTF_8);
        Files.writeString(locator.corruptedFileFor(3), "{}", java.nio.charset.StandardCharsets.UTF_8);

        List<Path> snapshots = locator.listCheckpointFiles()
                .map(Path::getFileName)
                .map(Path::getFileName)
                .toList();

        assertIterableEquals(List.of(locator.checkpointFileFor(1).getFileName()), snapshots);
    }

    @Test
    void returnsEmptyStreamWhenDirectoryDoesNotExist() throws IOException {
        CheckpointFileLocator emptyDirectoryLocator = new CheckpointFileLocator(tempDirectory.resolve("missing"));

        List<Path> snapshots = emptyDirectoryLocator.listCheckpointFiles().toList();

        assertTrue(snapshots.isEmpty());
    }
}
