package it.polimi.ingsoft.mesos.server.controller.match;

import it.polimi.ingsoft.mesos.server.controller.GameOrchestrator.GameOrchestrator;
import it.polimi.ingsoft.mesos.server.model.lobby.LobbyManager;
import it.polimi.ingsoft.mesos.server.model.persistence.savedmatch.SavedMatchService;
import it.polimi.ingsoft.mesos.server.model.ranking.RankingService;

import java.util.Objects;
import java.util.function.Function;

/**
 * Coordinates creation, registration, resume and shutdown of active match runtimes.
 */
public final class MatchLifecycleService {
    private final LobbyManager lobbyManager;
    private final MatchRegistry matchRegistry;
    private final SavedMatchService savedMatchService;
    private final RankingService rankingService;
    private final Function<GameOrchestrator, MatchRuntime> runtimeFactory;

    /**
     * Creates the lifecycle service.
     *
     * @param lobbyManager lobby manager used to create or restore orchestrators
     * @param matchRegistry active runtime registry
     * @param savedMatchService persistence service wired into created orchestrators
     * @param rankingService ranking service used at completion; nullable
     */
    public MatchLifecycleService(LobbyManager lobbyManager,
                                 MatchRegistry matchRegistry,
                                 SavedMatchService savedMatchService,
                                 RankingService rankingService) {
        this(lobbyManager, matchRegistry, savedMatchService, rankingService, MatchRuntime::new);
    }

    MatchLifecycleService(LobbyManager lobbyManager,
                          MatchRegistry matchRegistry,
                          SavedMatchService savedMatchService,
                          RankingService rankingService,
                          Function<GameOrchestrator, MatchRuntime> runtimeFactory) {
        this.lobbyManager = Objects.requireNonNull(lobbyManager, "Lobby manager cannot be null");
        this.matchRegistry = Objects.requireNonNull(matchRegistry, "Match registry cannot be null");
        this.savedMatchService = Objects.requireNonNull(savedMatchService, "Saved-match service cannot be null");
        this.rankingService = rankingService;
        this.runtimeFactory = Objects.requireNonNull(runtimeFactory, "Match runtime factory cannot be null");
    }

    /**
     * Starts a fresh ready lobby and registers its runtime. Setup is intentionally left to the caller.
     *
     * @param lobbyId lobby identifier
     * @return registered match runtime
     */
    public MatchRuntime startMatchFromLobby(int lobbyId) {
        GameOrchestrator orchestrator = this.lobbyManager.createGameFromLobby(
                lobbyId,
                this.savedMatchService,
                this.rankingService
        );
        return this.registerRuntime(orchestrator);
    }

    /**
     * Resumes a ready resumable lobby and registers its runtime.
     *
     * @param lobbyId lobby identifier
     * @return registered match runtime
     */
    public MatchRuntime resumeMatchFromLobby(int lobbyId) {
        GameOrchestrator orchestrator = this.lobbyManager.resumeGameFromLobby(
                lobbyId,
                this.savedMatchService,
                this.rankingService
        );
        return this.registerRuntime(orchestrator);
    }

    /**
     * Closes and unregisters one active match.
     *
     * @param gameId game identifier
     */
    public void closeMatch(int gameId) {
        this.matchRegistry.closeMatch(gameId);
    }

    private MatchRuntime registerRuntime(GameOrchestrator orchestrator) {
        MatchRuntime runtime = this.runtimeFactory.apply(orchestrator);
        try {
            this.matchRegistry.register(runtime);
            return runtime;
        } catch (RuntimeException exception) {
            runtime.close();
            throw exception;
        }
    }
}
