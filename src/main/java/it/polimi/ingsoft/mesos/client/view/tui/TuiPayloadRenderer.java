package it.polimi.ingsoft.mesos.client.view.tui;

import it.polimi.ingsoft.mesos.shared.payload.servertoclient.game.BoardPayload;
import it.polimi.ingsoft.mesos.shared.payload.servertoclient.game.CardPayload;
import it.polimi.ingsoft.mesos.shared.payload.servertoclient.error.ErrorPayload;
import it.polimi.ingsoft.mesos.shared.payload.servertoclient.game.GamePayload;
import it.polimi.ingsoft.mesos.shared.payload.servertoclient.prelobby.LobbyListPayload;
import it.polimi.ingsoft.mesos.shared.payload.servertoclient.prelobby.LobbySummaryPayload;
import it.polimi.ingsoft.mesos.shared.payload.servertoclient.game.OfferSpacePayload;
import it.polimi.ingsoft.mesos.shared.payload.servertoclient.game.PlayerPayload;
import it.polimi.ingsoft.mesos.shared.payload.servertoclient.lobby.PreMatchUpdatePayload;

import java.util.ArrayList;
import java.util.List;
//This class offer the service of transforming into a string the received payload
final class TuiPayloadRenderer {
    String renderError(ErrorPayload payload, GamePayload lastGamePayload) {
        StringBuilder builder = new StringBuilder();
        builder.append("[ERROR] ")
                .append(payload.code)
                .append(": ")
                .append(payload.message);

        if (lastGamePayload != null) {
            builder.append(System.lineSeparator())
                    .append(System.lineSeparator())
                    .append(this.renderGame(lastGamePayload));
        }

        return builder.toString();
    }

    String renderPreMatchUpdate(PreMatchUpdatePayload payload) {
        StringBuilder builder = new StringBuilder("[SERVER][PRE-MATCH] ");
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

    String renderGame(GamePayload payload) {
        StringBuilder builder = new StringBuilder();
        builder.append("[SERVER][GAME] #")
                .append(payload.id)
                .append(" status=")
                .append(payload.status)
                .append(" era=")
                .append(payload.currentEra + 1);

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
                .append("Upper row: ")
                .append(formatCards(board.upperRow))
                .append(System.lineSeparator())
                .append("Lower row: ")
                .append(formatCards(board.lowerRow));

        if (board.offerSpaces != null && !board.offerSpaces.isEmpty()) {
            builder.append(System.lineSeparator()).append("Offer spaces:");
            for (OfferSpacePayload offerSpace : board.offerSpaces) {
                builder.append(System.lineSeparator())
                        .append("- ")
                        .append(offerSpace.code)
                        .append(" top=")
                        .append(offerSpace.topRowCardsToTake)
                        .append(" bottom=")
                        .append(offerSpace.bottomRowCardsToTake)
                        .append(" occupiedBy=")
                        .append(valueOrDash(offerSpace.occupiedByNickname));
            }
        }
    }

    private static String formatCards(List<CardPayload> cards) {
        if (cards == null || cards.isEmpty()) {
            return "-";
        }

        List<String> renderedCards = new ArrayList<>();
        for (CardPayload card : cards) {
            renderedCards.add("#" + card.id + " " + card.type + " era=" + (card.era + 1));
        }
        return String.join(", ", renderedCards);
    }

    private static String valueOrDash(Object value) {
        return value == null ? "-" : String.valueOf(value);
    }
}
