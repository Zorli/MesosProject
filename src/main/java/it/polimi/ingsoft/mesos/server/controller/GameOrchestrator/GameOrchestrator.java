package it.polimi.ingsoft.mesos.server.controller.GameOrchestrator;


import it.polimi.ingsoft.mesos.server.controller.Factory.GameFactory;
import it.polimi.ingsoft.mesos.server.model.game.Game;
import it.polimi.ingsoft.mesos.server.model.player.Player;
import it.polimi.ingsoft.mesos.server.model.ranking.RankingService;
import it.polimi.ingsoft.mesos.shared.payload.server.ErrorPayload;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Coordinates one running match from the perspective of external callers.
 *
 * <p>The orchestrator is responsible for owning the {@link Game} created for a lobby and keeping
 * the list of players participating in that match. Higher layers are expected to route all
 * game-specific requests through this class so that access checks and orchestration logic stay
 * centralized.
 */
public class GameOrchestrator {
    private final Game game;
    private final List<Player> players;
    private final String hostNickname;
    private final RankingService rankingService;
    private boolean frozen;

    // Constructors

    /**
     * Creates an orchestrator for a new match using the provided game factory and player list.
     *
     * @param gameFactory factory used to bootstrap the underlying game aggregate
     * @param players players transferred from the lobby into the match
     */
    public GameOrchestrator(GameFactory gameFactory, ArrayList<Player> players) {
        this(
                gameFactory,
                players,
                resolveDefaultHostNickname(players)
        );
    }

    /**
     * Creates an orchestrator for a new match.
     *
     * @param gameFactory factory used to bootstrap the underlying game aggregate
     * @param players players transferred from the lobby into the match
     * @param hostNickname original host nickname associated with the match
     */
    public GameOrchestrator(GameFactory gameFactory,
                            ArrayList<Player> players,
                            String hostNickname) {
        this(gameFactory, players, hostNickname, null);
    }

    /**
     * Creates an orchestrator for a new match and wires final ranking registration.
     *
     * @param gameFactory factory used to bootstrap the underlying game aggregate
     * @param players players transferred from the lobby into the match
     * @param hostNickname original host nickname associated with the match
     * @param rankingService ranking service used to register completed matches; nullable when disabled
     */
    public GameOrchestrator(GameFactory gameFactory,
                            ArrayList<Player> players,
                            String hostNickname,
                            RankingService rankingService) {
        Objects.requireNonNull(gameFactory, "Game factory cannot be null");
        if (players == null || players.isEmpty()) {
            throw new IllegalArgumentException("Players cannot be null or empty");
        }

        this.game = gameFactory.createGame(players);
        this.players = List.copyOf(players);
        this.hostNickname = requireHostNickname(hostNickname);
        this.rankingService = rankingService;
    }

    // Getters

    /**
     * Returns the game aggregate coordinated by this orchestrator.
     *
     * @return coordinated game
     */
    public Game getGame() {
        return this.game;
    }

    /**
     * Returns the players participating in the coordinated match.
     *
     * @return match players
     */
    public List<Player> getPlayers() {
        return this.players;
    }

    /**
     * Returns whether the match has been frozen.
     *
     * @return {@code true} when gameplay commands are no longer accepted
     */
    public boolean isFrozen() {
        return this.frozen;
    }

    // Public gameplay API

    /**
     * Performs match setup.
     */
    public void setupMatch() {
        this.ensureMatchIsWritable();
        this.game.setup();
    }

    /**
     * Places a player's totem.
     *
     * @param nickname of the acting player
     * @param tile target offer-space code
     */
    public void moveTotem(String nickname, String tile) {
        Player player = this.resolvePlayer(nickname);
        this.ensureMatchIsWritable();
        this.game.moveTotem(player, tile);
    }

    /**
     * Delegates one standard card draw.
     *
     * @param nickname of acting player
     * @param cardId of the chosen card
     */
    public void chooseCard(String nickname, int cardId) {
        Player player = this.resolvePlayer(nickname);
        this.ensureMatchIsWritable();
        this.game.chooseCard(player, cardId);
    }

    /**
     * Delegates one extra draw.
     *
     * @param nickname acting player
     * @param cardId chosen extra-draw card
     */
    public void extraDrawn(String nickname, int cardId) {
        Player player = this.resolvePlayer(nickname);
        this.ensureMatchIsWritable();
        this.game.extraDrawn(player, cardId);
    }

    public void freezeForDisconnectedPlayer(String nickname) {
        this.frozen = true;
        this.game.notifyError(new ErrorPayload("PLAYER_DISCONNECTED", "Player disconnected: " + nickname));
    }

    // Private methods

    private Player resolvePlayer(String nickname) {
        return this.players.stream()
                .filter(p -> p.getNickname().equals(nickname))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Player not in this match: " + nickname));
    }

    private void ensureMatchIsWritable() {
        if (this.frozen) {
            throw new MatchFrozenException();
        }
    }

    // Helper methods

    private static String resolveDefaultHostNickname(List<Player> players) {
        if (players == null || players.isEmpty()) {
            throw new IllegalArgumentException("Players cannot be null or empty");
        }

        Player host = players.getFirst();
        if (host == null || host.getNickname() == null || host.getNickname().isBlank()) {
            throw new IllegalArgumentException("Match host nickname cannot be null or blank");
        }

        return requireHostNickname(host.getNickname());
    }

    private static String requireHostNickname(String hostNickname) {
        if (hostNickname == null || hostNickname.isBlank()) {
            throw new IllegalArgumentException("Match host nickname cannot be null or blank");
        }
        return hostNickname;
    }
}
