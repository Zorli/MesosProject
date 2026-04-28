package it.polimi.ingsoft.mesos.client.view.tui;

import it.polimi.ingsoft.mesos.shared.payload.server.BoardPayload;
import it.polimi.ingsoft.mesos.shared.payload.server.CardPayload;
import it.polimi.ingsoft.mesos.shared.payload.server.ErrorPayload;
import it.polimi.ingsoft.mesos.shared.payload.server.GamePayload;
import it.polimi.ingsoft.mesos.shared.payload.server.LobbyListPayload;
import it.polimi.ingsoft.mesos.shared.payload.server.LobbySummaryPayload;
import it.polimi.ingsoft.mesos.shared.payload.server.OfferSpacePayload;
import it.polimi.ingsoft.mesos.shared.payload.server.PlayerPayload;
import it.polimi.ingsoft.mesos.shared.payload.server.PreMatchUpdatePayload;
import it.polimi.ingsoft.mesos.shared.payload.server.StartingBlockPayload;
import it.polimi.ingsoft.mesos.shared.payload.server.TribePayload;
import it.polimi.ingsoft.mesos.shared.payload.server.TurnOrderPayload;

import java.util.ArrayList;
import java.util.List;

/**
 * Renders the terminal user interface from immutable {@link TuiState} snapshots.
 */
final class TuiRenderer {
    private static final String NL = System.lineSeparator();

    /**
     * Renders the screen associated with the supplied state.
     *
     * @param state current terminal state
     * @return rendered text
     */
    String render(TuiState state) {
        if (state.getScreen() == TuiScreen.HELP) {
            return this.renderHelp();
        }
        return this.renderDashboard(state);
    }

    /**
     * Renders the main dashboard view using the active screen as the source of visible sections.
     *
     * @param state current terminal state
     * @return rendered dashboard text
     */
    private String renderDashboard(TuiState state) {
        StringBuilder builder = new StringBuilder();
        this.appendLine(builder, "MESOS TUI");
        this.appendLine(builder, "=========");
        this.appendLine(builder, this.buildConnectionLine(state));
        this.appendLine(builder, this.buildIdentityLine(state));

        if (state.getLastError() != null) {
            this.appendLine(builder, "");
            this.appendSectionTitle(builder, "Last Error");
            this.appendLine(builder, this.formatError(state.getLastError()));
        }

        if (this.shouldRenderPreMatchEvent(state)) {
            this.appendLine(builder, "");
            this.appendSectionTitle(builder, "Latest Server Event");
            this.appendLine(builder, this.formatPreMatchUpdate(state.getLastPreMatchUpdate()));
        }

        if (this.shouldRenderLobbyContext(state)) {
            this.appendLine(builder, "");
            this.appendSectionTitle(builder, "Lobby");
            this.appendLine(builder, this.formatLobbyContext(state));
        }

        if (this.shouldRenderLobbyList(state)) {
            this.appendLine(builder, "");
            this.appendSectionTitle(builder, "Open Lobbies");
            this.appendLobbyList(builder, state.getLastLobbyList());
        }

        if (this.shouldRenderMatchBootstrap(state)) {
            this.appendLine(builder, "");
            this.appendSectionTitle(builder, "Match");
            this.appendLine(builder, "Match setup has started. Waiting for the initial game snapshot...");
        }

        if (this.shouldRenderGameSnapshot(state)) {
            this.appendLine(builder, "");
            this.appendSectionTitle(builder, "Match");
            this.appendGameSnapshot(builder, state, state.getLastGamePayload());
        }

        this.appendLine(builder, "");
        this.appendSectionTitle(builder, "Suggested Commands");
        this.appendSuggestedCommands(builder, state);
        return builder.toString();
    }

    /**
     * Renders the help screen.
     *
     * @return rendered help text
     */
    private String renderHelp() {
        return String.join(
                NL,
                "MESOS TUI COMMANDS",
                "==================",
                "help                      Show this help screen",
                "status                    Re-render the current dashboard",
                "auth <nickname>           Authenticate on the server",
                "list-lobbies              Request the open lobby list",
                "create-lobby <capacity>   Create a new lobby",
                "join-lobby <lobby-id>     Join an existing lobby",
                "quit-lobby                Leave the current lobby",
                "start-lobby               Start the current lobby if you are the host",
                "move-totem <tile>         Place your totem on an offer space",
                "choose-card <card-id>     Draw a card during the normal draw phase",
                "extra-draw <card-id>      Draw a bonus card during the extra draw phase",
                "quit                      Close the client",
                "",
                "Aliases: login, ls, create, join, leave, start, move, choose, draw, exit"
        );
    }

    /**
     * Appends the current game snapshot.
     *
     * @param builder target string builder
     * @param state current terminal state
     * @param gamePayload latest game payload
     */
    private void appendGameSnapshot(StringBuilder builder, TuiState state, GamePayload gamePayload) {
        this.appendLine(
                builder,
                "Game #" + gamePayload.id
                        + " | status=" + gamePayload.status
                        + " | era=" + (gamePayload.currentEra + 1)
        );
        this.appendTurnOrder(builder, gamePayload.turnOrder);
        this.appendLine(
                builder,
                "Turn phase: " + (gamePayload.turnState == null ? "-" : gamePayload.turnState.phase)
        );
        this.appendLine(builder, "");
        this.appendLine(builder, "Players:");
        if (gamePayload.players == null || gamePayload.players.isEmpty()) {
            this.appendLine(builder, "  none");
        } else {
            for (PlayerPayload player : gamePayload.players) {
                this.appendPlayer(builder, state, gamePayload.turnOrder, player);
            }
        }

        this.appendLine(builder, "");
        this.appendLine(builder, "Board:");
        this.appendBoard(builder, gamePayload.board);
    }

    /**
     * Appends the current round and turn order details.
     *
     * @param builder target string builder
     * @param turnOrder current turn order payload
     */
    private void appendTurnOrder(StringBuilder builder, TurnOrderPayload turnOrder) {
        if (turnOrder == null) {
            this.appendLine(builder, "Round: - | phase=- | current player=-");
            return;
        }
        this.appendLine(
                builder,
                "Round: " + turnOrder.roundNumber
                        + " | phase=" + turnOrder.currentRoundPhase
                        + " | current player=" + this.valueOrDash(turnOrder.currentPlayerNickname)
        );
        this.appendLine(
                builder,
                "Turn order: " + ((turnOrder.turnOrder == null || turnOrder.turnOrder.isEmpty())
                        ? "-"
                        : String.join(" -> ", turnOrder.turnOrder))
        );
    }

    /**
     * Appends the snapshot for one player.
     *
     * @param builder target string builder
     * @param state current terminal state
     * @param turnOrder current turn order payload
     * @param player player snapshot to render
     */
    private void appendPlayer(
            StringBuilder builder,
            TuiState state,
            TurnOrderPayload turnOrder,
            PlayerPayload player
    ) {
        List<String> tags = new ArrayList<>();
        if (player.nickname != null && player.nickname.equals(state.getNickname())) {
            tags.add("you");
        }
        if (turnOrder != null && player.nickname != null && player.nickname.equals(turnOrder.currentPlayerNickname)) {
            tags.add("current");
        }
        String suffix = tags.isEmpty() ? "" : " [" + String.join(", ", tags) + "]";
        this.appendLine(
                builder,
                "  - " + player.nickname
                        + suffix
                        + " | food=" + player.food
                        + " | prestige=" + player.prestigePoints
                        + " | totem=" + player.totem
        );
        TribePayload tribe = player.tribe;
        if (tribe == null) {
            this.appendLine(builder, "    characters: -");
            this.appendLine(builder, "    buildings: -");
            return;
        }
        this.appendLine(builder, "    characters: " + this.formatCardList(tribe.characters));
        this.appendLine(builder, "    buildings: " + this.formatCardList(tribe.buildings));
    }

    /**
     * Appends the current board snapshot.
     *
     * @param builder target string builder
     * @param board current board snapshot
     */
    private void appendBoard(StringBuilder builder, BoardPayload board) {
        if (board == null) {
            this.appendLine(builder, "  no board snapshot available");
            return;
        }

        this.appendLine(builder, "  upper row: " + this.formatCardList(board.upperRow));
        this.appendLine(builder, "  lower row: " + this.formatCardList(board.lowerRow));
        this.appendLine(builder, "  offer spaces:");
        if (board.offerSpaces == null || board.offerSpaces.isEmpty()) {
            this.appendLine(builder, "    none");
        } else {
            for (OfferSpacePayload offerSpace : board.offerSpaces) {
                this.appendLine(
                        builder,
                        "    - " + offerSpace.code
                                + " | top=" + offerSpace.topRowCardsToTake
                                + " | bottom=" + offerSpace.bottomRowCardsToTake
                                + " | occupiedBy=" + this.valueOrDash(offerSpace.occupiedByNickname)
                );
            }
        }

        this.appendLine(builder, "  starting blocks:");
        if (board.startingBlocks == null || board.startingBlocks.isEmpty()) {
            this.appendLine(builder, "    none");
        } else {
            for (StartingBlockPayload block : board.startingBlocks) {
                this.appendLine(
                        builder,
                        "    - position=" + block.position
                                + " | food=" + block.foodModifier
                                + " | prestige=" + block.prestigeModifier
                                + " | occupiedBy=" + this.valueOrDash(block.occupiedByNickname)
                );
            }
        }
    }

    /**
     * Appends the last requested lobby list.
     *
     * @param builder target string builder
     * @param lobbyListPayload last lobby list payload
     */
    private void appendLobbyList(StringBuilder builder, LobbyListPayload lobbyListPayload) {
        if (lobbyListPayload.lobbies == null || lobbyListPayload.lobbies.isEmpty()) {
            this.appendLine(builder, "No open lobbies.");
            return;
        }
        for (LobbySummaryPayload lobby : lobbyListPayload.lobbies) {
            this.appendLine(
                    builder,
                    "  - #" + lobby.lobbyId
                            + " | host=" + lobby.hostNickname
                            + " | players=" + lobby.currentPlayers + "/" + lobby.maxPlayers
                            + " | status=" + lobby.status
            );
        }
    }

    /**
     * Appends the suggested commands for the current state.
     *
     * @param builder target string builder
     * @param state current terminal state
     */
    private void appendSuggestedCommands(StringBuilder builder, TuiState state) {
        for (String command : this.buildSuggestedCommands(state)) {
            this.appendLine(builder, "  - " + command);
        }
    }

    /**
     * Computes command hints for the active screen instead of inferring commands from cached payloads.
     *
     * @param state current terminal state
     * @return suggested command list
     */
    private List<String> buildSuggestedCommands(TuiState state) {
        if (!state.isAuthenticated()) {
            return List.of("auth <nickname>", "help");
        }

        switch (state.getScreen()) {
            case IN_MATCH:
                return this.buildInMatchCommands(state);
            case WAITING_ROOM:
                return List.of("status", "start-lobby", "quit-lobby");
            case MATCH_BOOTSTRAP:
                return List.of("status", "quit-lobby");
            case LOBBY_BROWSER:
                return List.of("list-lobbies", "create-lobby <capacity>", "join-lobby <lobby-id>", "help");
            case HOME:
            default:
                return List.of("list-lobbies", "create-lobby <capacity>", "join-lobby <lobby-id>", "help");
        }
    }

    /**
     * Returns whether the active screen allows the cached lobby list to be displayed.
     *
     * @param state current terminal state
     * @return {@code true} when the lobby browser should show an available lobby list
     */
    private boolean shouldRenderLobbyList(TuiState state) {
        return state.getScreen() == TuiScreen.LOBBY_BROWSER && state.getLastLobbyList() != null;
    }

    /**
     * Returns whether the active screen allows current lobby context to be displayed.
     * Lobby browser stays a pure discovery screen, while waiting-room screens own the current lobby view.
     *
     * @param state current terminal state
     * @return {@code true} when the screen is allowed to show the current lobby roster
     */
    private boolean shouldRenderLobbyContext(TuiState state) {
        return state.hasLobbyContext()
                && (state.getScreen() == TuiScreen.WAITING_ROOM
                || state.getScreen() == TuiScreen.MATCH_BOOTSTRAP);
    }

    /**
     * Returns whether the active screen allows the latest pre-match event to be displayed.
     *
     * @param state current terminal state
     * @return {@code true} when a waiting-room event should be visible
     */
    private boolean shouldRenderPreMatchEvent(TuiState state) {
        return state.getScreen() == TuiScreen.WAITING_ROOM && state.getLastPreMatchUpdate() != null;
    }

    /**
     * Returns whether the active screen should show the match-bootstrap waiting message.
     *
     * @param state current terminal state
     * @return {@code true} when waiting for the first active match snapshot
     */
    private boolean shouldRenderMatchBootstrap(TuiState state) {
        return state.getScreen() == TuiScreen.MATCH_BOOTSTRAP;
    }

    /**
     * Returns whether the active screen allows the cached game snapshot to be displayed.
     *
     * @param state current terminal state
     * @return {@code true} when the in-match screen has a snapshot to render
     */
    private boolean shouldRenderGameSnapshot(TuiState state) {
        return state.getScreen() == TuiScreen.IN_MATCH && state.hasGameSnapshot();
    }

    /**
     * Computes the command hints while the client is in a match.
     *
     * @param state current terminal state
     * @return suggested command list
     */
    private List<String> buildInMatchCommands(TuiState state) {
        GamePayload gamePayload = state.getLastGamePayload();
        if (gamePayload == null || gamePayload.turnOrder == null) {
            return List.of("status");
        }
        String currentPlayer = gamePayload.turnOrder.currentPlayerNickname;
        if (state.getNickname() == null || !state.getNickname().equals(currentPlayer)) {
            return List.of("status", "help");
        }
        String roundPhase = gamePayload.turnOrder.currentRoundPhase;
        if ("PLACING_TOTEMS".equals(roundPhase)) {
            return List.of("move-totem <tile>", "status");
        }
        if ("PLAYER_DRAWS".equals(roundPhase)) {
            return List.of("choose-card <card-id>", "status");
        }
        if ("EXTRA_DRAWS".equals(roundPhase)) {
            return List.of("extra-draw <card-id>", "status");
        }
        return List.of("status", "help");
    }

    /**
     * Builds the top-line connection summary.
     *
     * @param state current terminal state
     * @return connection summary line
     */
    private String buildConnectionLine(TuiState state) {
        return "Connection: " + (state.isConnected() ? "online" : "offline");
    }

    /**
     * Builds the authenticated identity summary.
     *
     * @param state current terminal state
     * @return identity summary line
     */
    private String buildIdentityLine(TuiState state) {
        if (!state.isAuthenticated()) {
            return "Identity: not authenticated";
        }
        return "Identity: " + this.valueOrDash(state.getNickname());
    }

    /**
     * Formats the current lobby snapshot.
     *
     * @param state current terminal state
     * @return lobby summary line
     */
    private String formatLobbyContext(TuiState state) {
        return "Lobby #" + state.getCurrentLobbyId()
                + " | host=" + this.valueOrDash(state.getCurrentLobbyHost())
                + " | players="
                + state.getCurrentLobbyPlayers().size()
                + "/"
                + this.valueOrDash(state.getCurrentLobbyMaxPlayers())
                + " | members="
                + (state.getCurrentLobbyPlayers().isEmpty()
                ? "-"
                : String.join(", ", state.getCurrentLobbyPlayers()));
    }

    /**
     * Formats one pre-match update payload.
     *
     * @param payload payload to format
     * @return formatted summary
     */
    private String formatPreMatchUpdate(PreMatchUpdatePayload payload) {
        StringBuilder builder = new StringBuilder(payload.event.name());
        if (payload.message != null && !payload.message.isBlank()) {
            builder.append(" | ").append(payload.message);
        }
        if (payload.nickname != null) {
            builder.append(" | nickname=").append(payload.nickname);
        }
        if (payload.lobbyId != null) {
            builder.append(" | lobby=").append(payload.lobbyId);
        }
        return builder.toString();
    }

    /**
     * Formats one error payload.
     *
     * @param payload payload to format
     * @return formatted summary
     */
    private String formatError(ErrorPayload payload) {
        return payload.code + ": " + payload.message;
    }

    /**
     * Formats a card list into a compact textual representation.
     *
     * @param cards cards to format
     * @return formatted card list
     */
    private String formatCardList(List<CardPayload> cards) {
        if (cards == null || cards.isEmpty()) {
            return "-";
        }

        List<String> renderedCards = new ArrayList<>();
        for (CardPayload card : cards) {
            renderedCards.add(
                    "#" + card.id
                            + " " + card.type
                            + " (era " + (card.era + 1) + ")"
            );
        }
        return String.join(", ", renderedCards);
    }

    /**
     * Converts nullable values to a printable placeholder.
     *
     * @param value value to render
     * @return printable value
     */
    private String valueOrDash(Object value) {
        return value == null ? "-" : String.valueOf(value);
    }

    /**
     * Appends one section header.
     *
     * @param builder target string builder
     * @param title section title
     */
    private void appendSectionTitle(StringBuilder builder, String title) {
        this.appendLine(builder, title + ":");
    }

    /**
     * Appends one line to the renderer output.
     *
     * @param builder target string builder
     * @param line line content
     */
    private void appendLine(StringBuilder builder, String line) {
        builder.append(line).append(NL);
    }
}
