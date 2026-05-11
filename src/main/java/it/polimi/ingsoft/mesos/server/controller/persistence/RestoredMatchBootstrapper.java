package it.polimi.ingsoft.mesos.server.controller.persistence;

import it.polimi.ingsoft.mesos.server.controller.GameOrchestrator.GameOrchestrator;
import it.polimi.ingsoft.mesos.server.controller.LobbyController;
import it.polimi.ingsoft.mesos.server.controller.ServerSessionRegistry;
import it.polimi.ingsoft.mesos.server.model.enums.GameStatus;
import it.polimi.ingsoft.mesos.server.model.enums.LobbyStatus;
import it.polimi.ingsoft.mesos.server.model.lobby.Lobby;
import it.polimi.ingsoft.mesos.server.model.player.Player;
import it.polimi.ingsoft.mesos.server.model.ranking.RankingService;
import it.polimi.ingsoft.mesos.server.persistence.game.MatchPersistence;
import it.polimi.ingsoft.mesos.server.persistence.game.RestoredMatch;

import java.util.List;

public final class RestoredMatchBootstrapper {
    private RestoredMatchBootstrapper() {
    }

    public static int restoreActiveMatches(
            MatchPersistence matchPersistence,
            RankingService rankingService,
            ServerSessionRegistry sessions
    ) {
        int maxRestoredGameId = -1;
        for (RestoredMatch restoredMatch : matchPersistence.loadActiveMatches()) {
            if (restoredMatch.game().getStatus() == GameStatus.FINISHED) {
                matchPersistence.delete(restoredMatch.game().getId());
                continue;
            }
            maxRestoredGameId = Math.max(maxRestoredGameId, restoredMatch.game().getId());

            GameOrchestrator orchestrator = new GameOrchestrator(
                    restoredMatch.game(),
                    restoredMatch.hostNickname(),
                    rankingService,
                    matchPersistence,
                    sessions
            );
            List<String> nicknames = restoredMatch.players().stream()
                    .map(Player::getNickname)
                    .toList();
            Lobby lobby = new Lobby(
                    restoredMatch.game().getId(),
                    restoredMatch.hostNickname(),
                    nicknames,
                    LobbyStatus.RESUMABLE
            );
            LobbyController controller = new LobbyController(
                    lobby,
                    sessions,
                    rankingService,
                    matchPersistence,
                    orchestrator
            );
            sessions.registerLobbyModel(lobby);
            sessions.registerLobbyController(lobby.getId(), controller);
            new Thread(controller, "Lobby-" + lobby.getId() + "-Resume-Worker").start();
        }
        return maxRestoredGameId;
    }
}
