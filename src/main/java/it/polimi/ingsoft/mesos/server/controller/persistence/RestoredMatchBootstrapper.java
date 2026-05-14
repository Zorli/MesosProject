package it.polimi.ingsoft.mesos.server.controller.persistence;

import it.polimi.ingsoft.mesos.server.controller.GameOrchestrator.GameOrchestrator;
import it.polimi.ingsoft.mesos.server.controller.LobbyController;
import it.polimi.ingsoft.mesos.server.controller.ServerSessionRegistry;
import it.polimi.ingsoft.mesos.server.model.enums.GameStatus;
import it.polimi.ingsoft.mesos.server.model.enums.LobbyStatus;
import it.polimi.ingsoft.mesos.server.model.lobby.Lobby;
import it.polimi.ingsoft.mesos.server.model.player.Player;
import it.polimi.ingsoft.mesos.server.persistence.game.MatchPersistence;
import it.polimi.ingsoft.mesos.server.persistence.game.dto.SavedMatchSnapshot;
import it.polimi.ingsoft.mesos.server.persistence.game.restore.GameSnapshotRestoreFactory;
import it.polimi.ingsoft.mesos.server.persistence.game.restore.RestoredMatch;

import java.util.List;
import java.util.Objects;
import java.util.logging.Logger;

/**
 * Restores persisted matches after server startup.
 *
 * <p>Checks all available checkpoint files, validates and replays the snapshots into
 * domain objects and creates a {@link GameOrchestrator} for each runnable match.
 */
public final class RestoredMatchBootstrapper {
    private static final Logger log = Logger.getLogger(RestoredMatchBootstrapper.class.getName());

    private final MatchPersistence matchPersistence;
    private final ServerSessionRegistry sessions;
    private final GameSnapshotRestoreFactory restoreFactory;

    /**
     * Restores all matches currently persisted at startup.
     *
     * @param matchPersistence snapshot storage
     * @param sessions session registry used for routing and restore mapping
     */
    public static void restoreActiveMatches(
            MatchPersistence matchPersistence,
            ServerSessionRegistry sessions
    ) {
        new RestoredMatchBootstrapper(
                Objects.requireNonNull(matchPersistence, "matchPersistence cannot be null"),
                Objects.requireNonNull(sessions, "sessions cannot be null"),
                new GameSnapshotRestoreFactory()
        ).restore();
    }

    public RestoredMatchBootstrapper(
            MatchPersistence matchPersistence,
            ServerSessionRegistry sessions,
            GameSnapshotRestoreFactory restoreFactory
    ) {
        this.matchPersistence = Objects.requireNonNull(matchPersistence, "matchPersistence cannot be null");
        this.sessions = Objects.requireNonNull(sessions, "sessions cannot be null");
        this.restoreFactory = Objects.requireNonNull(restoreFactory, "restoreFactory cannot be null");
    }

    private void restore() {
        final List<SavedMatchSnapshot> snapshots;
        try {
            snapshots = this.matchPersistence.loadActiveMatches();
        } catch (RuntimeException exception) {
            log.warning("[Restore] Failed to load checkpoints: " + exception.getMessage());
            return;
        }

        log.info("[Restore] Found " + snapshots.size() + " checkpoint(s) to restore.");
        int restored = 0;
        for (SavedMatchSnapshot snapshot : snapshots) {
            if (this.restoreMatch(snapshot)) restored++;
        }
        log.info("[Restore] Restored " + restored + "/" + snapshots.size() + " match(es).");
    }

    private boolean restoreMatch(SavedMatchSnapshot snapshot) {
        if (snapshot == null) {
            return false;
        }

        RestoredMatch restoredMatch;
        try {
            restoredMatch = this.restoreFactory.fromSnapshot(snapshot);
        } catch (RuntimeException exception) {
            log.warning("[Restore] Failed to deserialize checkpoint for game "
                    + (snapshot.game() != null ? snapshot.game().id() : "?") + ": " + exception.getMessage());
            this.tryDeleteSnapshotFromInput(snapshot);
            return false;
        }

        var restoredGame = restoredMatch.game();

        if (restoredGame.getStatus() == GameStatus.FINISHED) {
            log.info("[Restore] Game " + restoredGame.getId() + " was already FINISHED — discarding checkpoint.");
            this.tryDeleteSnapshot(restoredGame.getId());
            return false;
        }

        try {
            GameOrchestrator orchestrator = new GameOrchestrator(
                    restoredGame,
                    restoredMatch.hostNickname(),
                    this.matchPersistence,
                    this.sessions
            );
            int gameId = orchestrator.getGame().getId();

            List<String> playerNicknames = restoredMatch.players().stream()
                    .map(Player::getNickname)
                    .toList();

            Lobby restoredLobby = new Lobby(gameId, restoredMatch.hostNickname(), playerNicknames, LobbyStatus.RESUMABLE);
            LobbyController lobbyController = new LobbyController(
                    restoredLobby,
                    this.sessions,
                    this.matchPersistence,
                    orchestrator
            );
            this.sessions.registerLobbyModel(restoredLobby);
            this.sessions.registerLobbyController(restoredLobby.getId(), lobbyController);
            Thread lobbyWorker = new Thread(lobbyController, "Lobby-" + restoredLobby.getId() + "-Resume-Worker");
            lobbyWorker.start();

            log.info("[Restore] Game " + gameId + " restored successfully (players: " + playerNicknames + ").");
            return true;
        } catch (RuntimeException exception) {
            log.warning("[Restore] Failed to start orchestrator for game " + restoredGame.getId()
                    + ": " + exception.getMessage());
            this.tryDeleteSnapshot(restoredGame.getId());
            return false;
        }
    }

    private void tryDeleteSnapshotFromInput(SavedMatchSnapshot snapshot) {
        if (snapshot == null || snapshot.game() == null) {
            return;
        }
        this.tryDeleteSnapshot(snapshot.game().id());
    }

    private void tryDeleteSnapshot(int gameId) {
        try {
            this.matchPersistence.delete(gameId);
        } catch (RuntimeException ignored) {
            // best-effort: snapshot cleanup should never block recovery of other matches
        }
    }
}
