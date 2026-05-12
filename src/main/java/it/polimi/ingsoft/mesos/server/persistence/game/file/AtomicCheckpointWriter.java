package it.polimi.ingsoft.mesos.server.persistence.game.file;

import java.io.IOException;
import java.nio.channels.FileChannel;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.util.logging.Logger;

/**
 * Writes checkpoint payloads atomically.
 */
public class AtomicCheckpointWriter {
    private static final Logger log = Logger.getLogger(AtomicCheckpointWriter.class.getName());

    public void writeAtomically(Path target, Path tempFile, byte[] payload) {
        try {
            boolean isFirstWrite = !Files.exists(target);
            Files.createDirectories(target.getParent());
            Files.write(tempFile, payload);
            try (FileChannel channel = FileChannel.open(tempFile, StandardOpenOption.WRITE)) {
                channel.force(true);
            }

            try {
                this.moveTemp(tempFile, target);
            } catch (AtomicMoveNotSupportedException exception) {
                this.moveTempWithoutAtomic(tempFile, target);
            }

            if (isFirstWrite) {
                log.info("[Persistence] Checkpoint file CREATED: " + target.toAbsolutePath() + " (" + payload.length + " bytes)");
            } else {
                log.info("[Persistence] Checkpoint file updated: " + target.toAbsolutePath() + " (" + payload.length + " bytes)");
            }
        } catch (IOException exception) {
            throw new IllegalStateException("Unable to write checkpoint file: " + target, exception);
        }
    }

    protected void moveTemp(Path source, Path target) throws IOException {
        Files.move(
                source,
                target,
                StandardCopyOption.REPLACE_EXISTING,
                StandardCopyOption.ATOMIC_MOVE
        );
    }

    private void moveTempWithoutAtomic(Path source, Path target) throws IOException {
        Files.move(source, target, StandardCopyOption.REPLACE_EXISTING);
    }
}
