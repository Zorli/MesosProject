package it.polimi.ingsoft.mesos.client.view.tui;

import it.polimi.ingsoft.mesos.shared.payload.servertoclient.error.ErrorPayload;
import it.polimi.ingsoft.mesos.shared.payload.servertoclient.game.BoardPayload;
import it.polimi.ingsoft.mesos.shared.payload.servertoclient.game.CardPayload;
import it.polimi.ingsoft.mesos.shared.payload.servertoclient.game.GamePayload;
import it.polimi.ingsoft.mesos.shared.payload.servertoclient.game.OfferSpacePayload;
import it.polimi.ingsoft.mesos.shared.payload.servertoclient.game.PlayerPayload;
import it.polimi.ingsoft.mesos.shared.payload.servertoclient.game.StartingBlockPayload;
import it.polimi.ingsoft.mesos.shared.payload.servertoclient.lobby.LobbyUpdatePayload;
import it.polimi.ingsoft.mesos.shared.payload.servertoclient.prelobby.AuthenticationResultPayload;
import it.polimi.ingsoft.mesos.shared.payload.servertoclient.prelobby.LobbyListPayload;
import it.polimi.ingsoft.mesos.shared.payload.servertoclient.prelobby.LobbySummaryPayload;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Converts server payloads into plain text for the text user interface.
 *
 * <p>The renderer does not read user input, does not print directly to the terminal,
 * and does not send messages to the server. Its only responsibility is formatting:
 * {@link TuiController} gives it payload objects, and this class returns strings
 * that can be printed with a {@link java.io.PrintStream}.
 *
 * <p>The class is stateless. Every method depends only on its parameters, so the
 * controller can safely keep one renderer instance and reuse it for all server
 * updates.
 */
final class TuiPayloadRenderer {
    /**
     * Formats an error message.
     *
     * <p>When a game state is available, the method appends the rendered game state
     * after the error. This keeps the current board visible after errors such as an
     * invalid move.
     *
     * @param payload error payload received from the server or created by the client
     * @param lastGamePayload last known game state, or {@code null} outside a match
     * @param localNickname nickname of this client, or {@code null} before authentication
     * @return text to print in the TUI
     */
    String renderError(ErrorPayload payload, GamePayload lastGamePayload, String localNickname) {
        StringBuilder builder = new StringBuilder();
        builder.append("[ERROR] ")
                .append(payload.code)
                .append(": ")
                .append(payload.message);

        if (lastGamePayload != null) {
            builder.append(System.lineSeparator())
                    .append(System.lineSeparator())
                    .append(this.renderGame(lastGamePayload, localNickname));
        }

        return builder.toString();
    }

    /**
     * Formats a successful authentication result.
     *
     * @param payload authentication result received from the server
     * @return text to print in the TUI
     */
    String renderAuthenticationResult(AuthenticationResultPayload payload) {
        StringBuilder builder = new StringBuilder("[SERVER][AUTH] ");
        builder.append(payload.nickname);
        if (payload.message != null && !payload.message.isBlank()) {
            builder.append(" | ").append(payload.message);
        }
        return builder.toString();
    }

    /**
     * Formats a lobby update.
     *
     * <p>The output always contains the lobby event. Optional fields are appended
     * only when present: message, lobby id, and player list.
     *
     * @param payload lobby update received from the server
     * @return text to print in the TUI
     */
    String renderLobbyUpdate(LobbyUpdatePayload payload) {
        StringBuilder builder = new StringBuilder("[SERVER][LOBBY] ");
        builder.append(payload.event);
        if (payload.message != null && !payload.message.isBlank()) {
            builder.append(" | ").append(payload.message);
        }
        if (payload.lobbyId != null) {
            builder.append(" | lobby=").append(payload.lobbyId);
        }
        if (payload.players != null && !payload.players.isEmpty()) {
            builder.append(" | players=").append(String.join(", ", payload.players));
        }
        return builder.toString();
    }

    /**
     * Formats the list of lobbies available before joining a match.
     *
     * @param payload lobby list received from the server
     * @return text to print in the TUI
     */
    String renderLobbyList(LobbyListPayload payload) {
        if (payload.lobbies == null || payload.lobbies.isEmpty()) {
            return "[SERVER][LOBBIES] No open lobbies.";
        }

        StringBuilder builder = new StringBuilder("[SERVER][LOBBIES]");
        for (LobbySummaryPayload lobby : payload.lobbies) {
            builder.append(System.lineSeparator())
                    .append("- #")
                    .append(lobby.lobbyId)
                    .append(" host=")
                    .append(lobby.hostNickname)
                    .append(" players=")
                    .append(lobby.currentPlayers)
                    .append("/")
                    .append(lobby.maxPlayers)
                    .append(" status=")
                    .append(lobby.status);
        }
        return builder.toString();
    }

    /**
     * Formats the current game state.
     *
     * <p>The result contains the game header, the local nickname, turn information,
     * players, and board. Missing optional values are rendered as {@code -}.
     *
     * @param payload game state received from the server
     * @param localNickname nickname of this client, or {@code null} before authentication
     * @return text to print in the TUI
     */
    String renderGame(GamePayload payload, String localNickname) {
        StringBuilder builder = new StringBuilder();
        builder.append("[SERVER][GAME] #")
                .append(payload.id)
                .append(" status=")
                .append(payload.status)
                .append(" era=")
                .append(payload.currentEra);

        builder.append(System.lineSeparator())
                .append("Your nickname: ")
                .append(valueOrDash(localNickname));

        if (payload.turnOrder != null) {
            builder.append(System.lineSeparator())
                    .append("Round ")
                    .append(payload.turnOrder.roundNumber)
                    .append(" | phase=")
                    .append(payload.turnOrder.currentRoundPhase)
                    .append(" | current=")
                    .append(valueOrDash(payload.turnOrder.currentPlayerNickname));
        }

        appendPlayers(builder, payload.players);
        appendBoard(builder, payload.board);
        return builder.toString();
    }

    /**
     * Formats the final game result.
     *
     * <p>Players are copied into a new list and sorted by prestige points in
     * descending order. The original payload is not modified.
     *
     * @param payload final game state received from the server
     * @return text to print in the TUI
     */
    String renderFinalGameResult(GamePayload payload) {
        StringBuilder builder = new StringBuilder();
        builder.append("[SERVER][GAME FINISHED] #")
                .append(payload.id)
                .append(System.lineSeparator())
                .append("Final results:");

        if (payload.players == null || payload.players.isEmpty()) {
            builder.append(" -");
            return builder.toString();
        }

        List<PlayerPayload> players = new ArrayList<>(payload.players);
        players.sort(new Comparator<PlayerPayload>() {
            @Override
            public int compare(PlayerPayload firstPlayer, PlayerPayload secondPlayer) {
                if (firstPlayer.isConnected != secondPlayer.isConnected) {
                    return Boolean.compare(secondPlayer.isConnected, firstPlayer.isConnected);
                }
                return Integer.compare(secondPlayer.prestigePoints, firstPlayer.prestigePoints);
            }
        });

        for (int i = 0; i < players.size(); i++) {
            PlayerPayload player = players.get(i);
            builder.append(System.lineSeparator())
                    .append(i + 1)
                    .append(". ")
                    .append(player.nickname)
                    .append(" prestige=")
                    .append(player.prestigePoints);
        }

        return builder.toString();
    }

    /**
     * Appends the player section to an existing game rendering.
     */
    private static void appendPlayers(StringBuilder builder, List<PlayerPayload> players) {
        builder.append(System.lineSeparator()).append("Players:");
        if (players == null || players.isEmpty()) {
            builder.append(" -");
            return;
        }

        for (PlayerPayload player : players) {
            builder.append(System.lineSeparator())
                    .append("- ")
                    .append(player.nickname)
                    .append(" food=")
                    .append(player.food)
                    .append(" prestige=")
                    .append(player.prestigePoints)
                    .append(" totem=")
                    .append(valueOrDash(player.totem))
                    .append(" connected=")
                    .append(player.isConnected);
        }
    }

    /**
     * Appends the board section to an existing game rendering.
     *
     * <p>If the board is missing, nothing is appended.
     */
    private static void appendBoard(StringBuilder builder, BoardPayload board) {
        if (board == null) {
            return;
        }

        builder.append(System.lineSeparator())
                .append("Board:")
                .append(System.lineSeparator())
                .append("UPPER  ")
                .append(formatCards(board.upperRow))
                .append(System.lineSeparator())
                .append("LOWER  ")
                .append(formatCards(board.lowerRow));

        if (board.offerSpaces != null && !board.offerSpaces.isEmpty()) {
            builder.append(System.lineSeparator())
                    .append("OFFERS ")
                    .append(formatOfferSpaces(board.offerSpaces));
        }

        if (board.startingBlocks != null && !board.startingBlocks.isEmpty()) {
            builder.append(System.lineSeparator())
                    .append("START  ")
                    .append(formatStartingBlocks(board.startingBlocks));
        }
    }

    /**
     * Formats one row of cards.
     *
     * <p>Each card is rendered as {@code [id type era]}. The era printed to the
     * user is one-based, so internal era {@code 0} is shown as {@code e1}.
     */
//    HUNTER
//    SHAMAN
//    ARTIST
//    BUILDER
//    INVENTOR
//    GATHERER
//    EVENT
//    BUILDING
    private static String formatCards(List<CardPayload> cards) {
        if (cards == null || cards.isEmpty()) {
            return "-";
        }

        List<String> renderedCards = new ArrayList<>();
        for (CardPayload card : cards) {
            String type = card.type == null ? "" : card.type;
            String base = "[" + card.id + " " + type + " era" + card.era;

            switch (type) {
                case "HUNTER" ->
                        renderedCards.add(base + " has effect: " + card.hunterHasEffect + "]");
                case "SHAMAN" ->
                        renderedCards.add(base + " stars: " + card.sciamanStars + "]");
                case "BUILDER" ->
                        renderedCards.add(base + " discount: " + card.discount + " finalPP: " + card.finalPrestigePoints +
                                "]");
                case "INVENTOR" ->
                        renderedCards.add(base + " inventor type: " + card.inventorPerk + "]");
                case "BUILDING" ->
                        renderedCards.add(base + " cost: " + card.buildingCost + "]");
                case "EVENT" ->
                        renderedCards.add(base + " " + card.eventCompleteName + "]");
                case "ARTIST", "GATHERER"->
                        renderedCards.add(base + "]");
                default ->
                        renderedCards.add(base + "]");
            }
        }

        return String.join(" ", renderedCards);
    }

    /**
     * Formats all offer spaces of the board.
     */
    private static String formatOfferSpaces(List<OfferSpacePayload> offerSpaces) {
        List<String> renderedOfferSpaces = new ArrayList<>();
        for (OfferSpacePayload offerSpace : offerSpaces) {
            renderedOfferSpaces.add(
                    offerSpace.code
                            + " topDraw=" + offerSpace.topRowCardsToTake
                            + " bottomDraw=" + offerSpace.bottomRowCardsToTake
                            + " occupiedBy=" + valueOrDash(offerSpace.occupiedByNickname)
            );
        }
        return String.join(" | ", renderedOfferSpaces);
    }

    /**
     * Formats all starting blocks of the board.
     */
    private static String formatStartingBlocks(List<StartingBlockPayload> startingBlocks) {
        List<String> renderedStartingBlocks = new ArrayList<>();
        for (StartingBlockPayload startingBlock : startingBlocks) {
            renderedStartingBlocks.add(
                    "#" + startingBlock.position
                            + " food=" + formatModifier(startingBlock.foodModifier)
                            + " pp=" + formatModifier(startingBlock.prestigeModifier)
                            + " occupiedBy=" + valueOrDash(startingBlock.occupiedByNickname)
            );
        }
        return String.join(" | ", renderedStartingBlocks);
    }

    /**
     * Formats signed board modifiers.
     *
     * <p>Positive and zero values are printed with {@code +}; negative values keep
     * their normal minus sign.
     */
    private static String formatModifier(int value) {
        if (value >= 0) {
            return "+" + value;
        }

        return String.valueOf(value);
    }

    /**
     * Converts missing optional values to a dash for compact terminal output.
     */
    private static String valueOrDash(Object value) {
        if (value == null) {
            return "-";
        }

        return String.valueOf(value);
    }
}
