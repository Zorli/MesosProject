package it.polimi.ingsoft.mesos.server.persistence.game.file;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

class AtomicCheckpointWriterTest {
    private static final byte[] PAYLOAD = "snapshot".getBytes(java.nio.charset.StandardCharsets.UTF_8);

    @TempDir
    Path tempDirectory;

    @Test
    void writeAtomicallyStoresPayload() throws Exception {
        AtomicCheckpointWriter writer = new AtomicCheckpointWriter();
        Path target = tempDirectory.resolve("game-7.json");
        Path tempFile = tempDirectory.resolve("game-7.json.tmp");

        writer.writeAtomically(target, tempFile, PAYLOAD);

        assertEquals("snapshot", readPayload(target));
        assertEquals("snapshot", new String(PAYLOAD));
        assertFalse(Files.exists(tempFile));
    }

    @Test
    void writeAtomicallyFallsBackWhenAtomicMoveNotSupported() throws Exception {
        AtomicCheckpointWriter writer = new AtomicCheckpointWriter() {
            @Override
            protected void moveTemp(Path source, Path target) throws IOException {
                throw new AtomicMoveNotSupportedException(
                        source.toString(),
                        target.toString(),
                        "Atomic move not supported"
                );
            }
        };
        Path target = tempDirectory.resolve("game-8.json");
        Path tempFile = tempDirectory.resolve("game-8.json.tmp");
        writer.writeAtomically(target, tempFile, PAYLOAD);

        assertEquals("snapshot", readPayload(target));
        assertFalse(Files.exists(tempFile));
    }

    @Test
    void writeAtomicallyPropagatesIOExceptionAsIllegalStateAndLeavesNoTargetFile() {
        AtomicCheckpointWriter writer = new AtomicCheckpointWriter() {
            @Override
            protected void moveTemp(Path source, Path target) throws IOException {
                throw new IOException("disk full");
            }
        };
        Path target = tempDirectory.resolve("game-9.json");
        Path tempFile = tempDirectory.resolve("game-9.json.tmp");

        assertThrows(IllegalStateException.class, () -> writer.writeAtomically(target, tempFile, PAYLOAD));
        assertFalse(Files.exists(target));
    }

    private static String readPayload(Path path) throws Exception {
        try {
            return Files.readString(path);
        } catch (Exception exception) {
            throw new AssertionError("Expected file to exist: " + path, exception);
        }
    }
}
