package it.polimi.ingsoft.mesos.client.view.tui;

import it.polimi.ingsoft.mesos.shared.payload.servertoclient.game.BoardPayload;
import it.polimi.ingsoft.mesos.shared.payload.servertoclient.game.CardPayload;
import it.polimi.ingsoft.mesos.shared.payload.servertoclient.error.ErrorPayload;
import it.polimi.ingsoft.mesos.shared.payload.servertoclient.game.GamePayload;
import it.polimi.ingsoft.mesos.shared.payload.servertoclient.prelobby.LobbyListPayload;
import it.polimi.ingsoft.mesos.shared.payload.servertoclient.prelobby.LobbySummaryPayload;
import it.polimi.ingsoft.mesos.shared.payload.servertoclient.game.OfferSpacePayload;
import it.polimi.ingsoft.mesos.shared.payload.servertoclient.game.PlayerPayload;
import it.polimi.ingsoft.mesos.shared.payload.servertoclient.game.StartingBlockPayload;
import it.polimi.ingsoft.mesos.shared.payload.servertoclient.lobby.LobbyUpdatePayload;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
//This class offer the service of transforming into a string the received payload
final class TuiPayloadRenderer {
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

    String renderGame(GamePayload payload, String localNickname) {
        StringBuilder builder = new StringBuilder();
        builder.append("[SERVER][GAME] #")
                .append(payload.id)
                .append(" status=")
                .append(payload.status)
                .append(" era=")
                .append(payload.currentEra + 1);

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

    String renderFinalGameResult(GamePayload payload) {
        StringBuilder builder = new StringBuilder();
        builder.append("[SERVER][GAME FINISHED] #")
                .append(payload.id)
                .append(System.lineSeparator())
                .append("Final ranking:");

        if (payload.players == null || payload.players.isEmpty()) {
            builder.append(" -");
            return builder.toString();
        }

        List<PlayerPayload> players = new ArrayList<>(payload.players);
        players.sort(Comparator.comparingInt((PlayerPayload player) -> player.prestigePoints).reversed());

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
                    .append(valueOrDash(player.totem));
        }
    }

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

    private static String formatCards(List<CardPayload> cards) {
        if (cards == null || cards.isEmpty()) {
            return "-";
        }

        List<String> renderedCards = new ArrayList<>();
        for (CardPayload card : cards) {
            renderedCards.add("[" + card.id + " " + card.type + " e" + (card.era + 1) + "]");
        }
        return String.join(" ", renderedCards);
    }

    private static String formatOfferSpaces(List<OfferSpacePayload> offerSpaces) {
        List<String> renderedOfferSpaces = new ArrayList<>();
        for (OfferSpacePayload offerSpace : offerSpaces) {
            renderedOfferSpaces.add(
                    offerSpace.code
                            + " top=" + offerSpace.topRowCardsToTake
                            + " bot=" + offerSpace.bottomRowCardsToTake
                            + " occ=" + valueOrDash(offerSpace.occupiedByNickname)
            );
        }
        return String.join(" | ", renderedOfferSpaces);
    }

    private static String formatStartingBlocks(List<StartingBlockPayload> startingBlocks) {
        List<String> renderedStartingBlocks = new ArrayList<>();
        for (StartingBlockPayload startingBlock : startingBlocks) {
            renderedStartingBlocks.add(
                    "#" + startingBlock.position
                            + " food=" + formatModifier(startingBlock.foodModifier)
                            + " pp=" + formatModifier(startingBlock.prestigeModifier)
                            + " occ=" + valueOrDash(startingBlock.occupiedByNickname)
            );
        }
        return String.join(" | ", renderedStartingBlocks);
    }

    private static String formatModifier(int value) {
        return value >= 0 ? "+" + value : String.valueOf(value);
    }

    private static String valueOrDash(Object value) {
        return value == null ? "-" : String.valueOf(value);
    }
}
