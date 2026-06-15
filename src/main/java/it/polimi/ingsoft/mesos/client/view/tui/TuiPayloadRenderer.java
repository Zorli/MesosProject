package it.polimi.ingsoft.mesos.client.view.tui;

import it.polimi.ingsoft.mesos.shared.payload.servertoclient.error.ErrorPayload;
import it.polimi.ingsoft.mesos.shared.payload.servertoclient.game.BoardPayload;
import it.polimi.ingsoft.mesos.shared.payload.servertoclient.game.CardPayload;
import it.polimi.ingsoft.mesos.shared.payload.servertoclient.game.GamePayload;
import it.polimi.ingsoft.mesos.shared.payload.servertoclient.game.OfferSpacePayload;
import it.polimi.ingsoft.mesos.shared.payload.servertoclient.game.PlayerPayload;
import it.polimi.ingsoft.mesos.shared.payload.servertoclient.game.RankingPayload;
import it.polimi.ingsoft.mesos.shared.payload.servertoclient.game.StartingBlockPayload;
import it.polimi.ingsoft.mesos.shared.payload.servertoclient.game.TribePayload;
import it.polimi.ingsoft.mesos.shared.payload.servertoclient.game.TurnOrderPayload;
import it.polimi.ingsoft.mesos.shared.payload.servertoclient.lobby.LobbyUpdatePayload;
import it.polimi.ingsoft.mesos.server.model.game.enums.TotemColor;
import it.polimi.ingsoft.mesos.shared.payload.servertoclient.prelobby.AuthenticationResultPayload;
import it.polimi.ingsoft.mesos.shared.payload.servertoclient.prelobby.LobbyListPayload;
import it.polimi.ingsoft.mesos.shared.payload.servertoclient.prelobby.LobbySummaryPayload;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Converts server payloads into colored, framed text blocks for the TUI.
 *
 * <p>The renderer is stateless and pure: each method depends only on its
 * parameters. {@link TuiView} keeps one instance and calls it for every
 * incoming server payload.
 *
 * <p>Game state rendering uses a full-screen layout: an ANSI clear sequence is
 * emitted before the header so each update redraws a clean frame.
 */
final class TuiPayloadRenderer {

    private static final int MINI_CARD_WIDTH = 15;
    private static final int OFFER_TILE_WIDTH = 8;
    private static final int MIN_PANEL_WIDTH = 96;

    private static final int COL_MARKER = 3;
    private static final int COL_NAME = 18;
    private static final int COL_TOTEM = 12;
    private static final int COL_FOOD = 8;
    private static final int COL_PRESTIGE = 10;

    // ─────────────────────────────────────────────────────────────────────────
    //  Welcome / help
    // ─────────────────────────────────────────────────────────────────────────

    String renderFarewell() {
        return TuiStyle.CLEAR_SCREEN
                + TuiStyle.BRIGHT_YELLOW + "◆ " + TuiStyle.RESET
                + TuiStyle.DIM + "client closed. see you next era." + TuiStyle.RESET;
    }

    String renderWelcome() {
        List<String> lines = new ArrayList<>();
        lines.add(TuiStyle.BOLD + TuiStyle.BRIGHT_CYAN
                + "  M E S O S  " + TuiStyle.RESET
                + TuiStyle.DIM + "  ·  Progetto Ingegneria del Software  ·" + TuiStyle.RESET);
        lines.add("");
        lines.add(TuiStyle.DIM + "  type " + TuiStyle.RESET
                + TuiStyle.BRIGHT_YELLOW + "help" + TuiStyle.RESET
                + TuiStyle.DIM + " to list commands  ·  start with " + TuiStyle.RESET
                + TuiStyle.BRIGHT_GREEN + "auth <nickname>" + TuiStyle.RESET);
        return TuiStyle.CLEAR_SCREEN + TuiBox.frame(null, lines);
    }

    String renderHelp() {
        StringBuilder out = new StringBuilder();
        out.append(TuiStyle.CLEAR_SCREEN);

        List<String> general = new ArrayList<>();
        general.add(commandLine("help", null, "show this help"));
        general.add(commandLine("auth", "<nickname>", "log in with a nickname"));
        general.add(commandLine("quit", null, "close the client"));

        List<String> lobby = new ArrayList<>();
        lobby.add(commandLine("list-lobbies", null, "show open lobbies"));
        lobby.add(commandLine("create-lobby", "<capacity> <color>", "create a new lobby (2-5)"));
        lobby.add(commandLine("join-lobby", "<lobby-id> <color>", "join an existing lobby"));
        lobby.add(commandLine("quit-lobby", null, "leave the current lobby"));
        lobby.add(commandLine("start-lobby", null, "start the match (host only)"));

        List<String> match = new ArrayList<>();
        match.add(commandLine("move-totem", "<tile>", "place your totem on an offer space"));
        match.add(commandLine("choose-card", "<card-id>", "draft a card from the board"));
        match.add(commandLine("extra-draw", "<card-id>", "take an extra card if entitled"));

        List<String> aliases = new ArrayList<>();
        aliases.add(aliasRow("h", "help",            "q",       "quit"));
        aliases.add(aliasRow("ll", "list-lobbies",   "cl",      "create-lobby"));
        aliases.add(aliasRow("jl", "join-lobby",     "ql",      "quit-lobby"));
        aliases.add(aliasRow("sl", "start-lobby",    "mt",      "move-totem"));
        aliases.add(aliasRow("cc", "choose-card",    "ed",      "extra-draw"));
        aliases.add(aliasRow("exit", "quit",         "lobbies", "list-lobbies"));
        aliases.add(aliasRow("create", "create-lobby", "join",  "join-lobby"));

        out.append(TuiBox.frame(TuiStyle.BRIGHT_GREEN + "GENERAL" + TuiStyle.RESET, general))
                .append('\n');
        out.append(TuiBox.frame(TuiStyle.BRIGHT_CYAN + "LOBBY" + TuiStyle.RESET, lobby))
                .append('\n');
        out.append(TuiBox.frame(TuiStyle.BRIGHT_MAGENTA + "MATCH" + TuiStyle.RESET, match))
                .append('\n');
        out.append(TuiBox.frame(TuiStyle.DIM + "ALIASES" + TuiStyle.RESET, aliases));
        return out.toString();
    }

    private static String commandLine(String name, String args, String description) {
        String head = TuiStyle.BRIGHT_YELLOW + name + TuiStyle.RESET;
        if (args != null) {
            head = head + " " + TuiStyle.BRIGHT_BLUE + args + TuiStyle.RESET;
        }
        head = TuiBox.padRight(head, 30);
        return head + " " + TuiStyle.DIM + description + TuiStyle.RESET;
    }

    private static String aliasRow(String alias1, String target1, String alias2, String target2) {
        String left = TuiBox.padRight(
                TuiStyle.BRIGHT_YELLOW + alias1 + TuiStyle.RESET
                        + TuiStyle.DIM + " → " + TuiStyle.RESET
                        + target1, 30);
        String right = TuiStyle.BRIGHT_YELLOW + alias2 + TuiStyle.RESET
                + TuiStyle.DIM + " → " + TuiStyle.RESET
                + target2;
        return left + "  " + right;
    }

    // ─────────────────────────────────────────────────────────────────────────
    //  Error / auth / lobby
    // ─────────────────────────────────────────────────────────────────────────

    String renderError(ErrorPayload payload, GamePayload lastGamePayload, String localNickname) {
        StringBuilder out = new StringBuilder();
        out.append(TuiStyle.CLEAR_SCREEN);
        if (lastGamePayload != null) {
            out.append(renderGameContent(lastGamePayload, localNickname)).append('\n').append('\n');
        }
        out.append(TuiStyle.BRIGHT_RED).append(TuiStyle.BOLD).append("✗ ERROR ").append(TuiStyle.RESET)
                .append(TuiStyle.BRIGHT_RED).append("[").append(payload.code).append("] ")
                .append(TuiStyle.RESET).append(payload.message);
        return out.toString();
    }

    String renderAuthenticationResult(AuthenticationResultPayload payload) {
        StringBuilder out = new StringBuilder();
        out.append(TuiStyle.CLEAR_SCREEN);
        out.append(TuiStyle.BRIGHT_GREEN).append(TuiStyle.BOLD).append("✓ AUTH ").append(TuiStyle.RESET)
                .append("logged in as ")
                .append(TuiStyle.BOLD).append(payload.nickname).append(TuiStyle.RESET);
        if (payload.message != null && !payload.message.isBlank()) {
            out.append("  ").append(TuiStyle.DIM).append(payload.message).append(TuiStyle.RESET);
        }
        return out.toString();
    }

    String renderLobbyUpdate(LobbyUpdatePayload payload) {
        StringBuilder out = new StringBuilder();
        out.append(TuiStyle.CLEAR_SCREEN);
        out.append(TuiStyle.BRIGHT_CYAN).append(TuiStyle.BOLD)
                .append("◆ LOBBY ").append(payload.event).append(TuiStyle.RESET);

        if (payload.lobbyId != null) {
            out.append("  ").append(TuiStyle.DIM).append("#").append(payload.lobbyId).append(TuiStyle.RESET);
        }
        if (payload.message != null && !payload.message.isBlank()) {
            out.append("  ").append(payload.message);
        }
        if (payload.players != null && !payload.players.isEmpty()) {
            out.append('\n').append(TuiStyle.DIM).append("Players: ").append(TuiStyle.RESET);
            out.append(formatLobbyPlayers(payload.players, payload.playerTotemColors));
        }
        return out.toString();
    }

    String renderLobbyList(LobbyListPayload payload) {
        if (payload.lobbies == null || payload.lobbies.isEmpty()) {
            return TuiStyle.CLEAR_SCREEN
                    + TuiStyle.BRIGHT_YELLOW + "◆ LOBBIES" + TuiStyle.RESET + "  no open lobbies.";
        }

        List<String> lines = new ArrayList<>();
        lines.add(TuiStyle.BOLD + "ID    HOST              PLAYERS   STATUS      AVAILABLE COLORS" + TuiStyle.RESET);
        for (LobbySummaryPayload lobby : payload.lobbies) {
            String id = TuiBox.padRight("#" + lobby.lobbyId, 5);
            String host = TuiBox.padRight(safe(lobby.hostNickname), 17);
            String players = TuiBox.padRight(lobby.currentPlayers + "/" + lobby.maxPlayers, 9);
            String status = TuiBox.padRight(TuiStyle.paint(TuiStyle.forStatus(lobby.status), safe(lobby.status)), 11);
            String colors = formatTotemColors(lobby.availableTotemColors);
            lines.add(id + " " + host + " " + players + " " + status + " " + colors);
        }
        return TuiStyle.CLEAR_SCREEN
                + TuiBox.frame(TuiStyle.BRIGHT_CYAN + "OPEN LOBBIES" + TuiStyle.RESET, lines);
    }

    private static String formatLobbyPlayers(List<String> players, Map<String, TotemColor> playerTotemColors) {
        if (playerTotemColors == null || playerTotemColors.isEmpty()) {
            return String.join(", ", players);
        }

        List<String> renderedPlayers = new ArrayList<>(players.size());
        for (String player : players) {
            TotemColor color = playerTotemColors.get(player);
            if (color == null) {
                renderedPlayers.add(player);
            } else {
                renderedPlayers.add(player + " (" + color.name().toLowerCase() + ")");
            }
        }
        return String.join(", ", renderedPlayers);
    }

    private static String formatTotemColors(List<TotemColor> colors) {
        if (colors == null || colors.isEmpty()) {
            return "-";
        }

        return colors.stream()
                .map(color -> color.name().toLowerCase())
                .sorted()
                .reduce((left, right) -> left + ", " + right)
                .orElse("-");
    }

    // ─────────────────────────────────────────────────────────────────────────
    //  Game state
    // ─────────────────────────────────────────────────────────────────────────

    String renderGame(GamePayload payload, String localNickname) {
        return TuiStyle.CLEAR_SCREEN + renderGameContent(payload, localNickname);
    }

    private String renderGameContent(GamePayload payload, String localNickname) {
        // Render the board first so we know its natural width; then align the
        // header, players and tribe panels to it. The board is always the
        // widest panel (it holds the card rows) so it sets the baseline.
        String board = renderBoard(payload.board, payload.players);
        int boardOuter = firstLineWidth(board);
        int minInner = Math.max(MIN_PANEL_WIDTH, boardOuter - 4);

        StringBuilder out = new StringBuilder();
        out.append(renderHeader(payload, localNickname, minInner)).append('\n');
        out.append(renderPlayers(payload.players, payload.turnOrder, localNickname, minInner))
                .append('\n');
        out.append(board).append('\n');
        out.append(renderTribe(payload.players, localNickname, minInner));
        return out.toString();
    }

    private static int firstLineWidth(String multilineFrame) {
        int nl = multilineFrame.indexOf('\n');
        String first = nl < 0 ? multilineFrame : multilineFrame.substring(0, nl);
        return TuiBox.visibleLength(first);
    }

    private static String renderTribe(List<PlayerPayload> players, String localNickname,
                                      int minInnerWidth) {
        PlayerPayload me = findPlayer(players, localNickname);
        if (me == null || me.tribe == null) {
            return TuiBox.frame(TuiStyle.BRIGHT_CYAN + "YOUR TRIBE" + TuiStyle.RESET,
                    List.of(TuiStyle.DIM + "no tribe yet" + TuiStyle.RESET),
                    minInnerWidth);
        }

        List<CardPayload> chars = me.tribe.characters;
        List<CardPayload> builds = me.tribe.buildings;

        List<String> lines = new ArrayList<>();
        lines.add(TuiStyle.bold("CHARACTERS")
                + TuiStyle.DIM + "  (" + (chars == null ? 0 : chars.size()) + ")"
                + TuiStyle.RESET);
        if (chars == null || chars.isEmpty()) {
            lines.add(TuiStyle.DIM + "  (none drafted yet)" + TuiStyle.RESET);
        } else {
            lines.addAll(splitLines(renderCardRow(chars)));
        }
        lines.add("");
        lines.add(TuiStyle.bold("BUILDINGS")
                + TuiStyle.DIM + "  (" + (builds == null ? 0 : builds.size()) + ")"
                + TuiStyle.RESET);
        if (builds == null || builds.isEmpty()) {
            lines.add(TuiStyle.DIM + "  (none built yet)" + TuiStyle.RESET);
        } else {
            lines.addAll(splitLines(renderCardRow(builds)));
        }

        return TuiBox.frame(TuiStyle.BRIGHT_CYAN + "YOUR TRIBE" + TuiStyle.RESET, lines,
                minInnerWidth);
    }

    private static PlayerPayload findPlayer(List<PlayerPayload> players, String nickname) {
        if (players == null || nickname == null) {
            return null;
        }
        for (PlayerPayload p : players) {
            if (nickname.equals(p.nickname)) {
                return p;
            }
        }
        return null;
    }

    String renderFinalGameResult(GamePayload payload) {
        StringBuilder out = new StringBuilder();
        out.append(TuiStyle.CLEAR_SCREEN);

        List<String> headerLines = new ArrayList<>();
        headerLines.add(TuiStyle.bold(TuiStyle.BRIGHT_YELLOW + "  GAME FINISHED" + TuiStyle.RESET)
                + TuiStyle.DIM + "   game #" + payload.id + TuiStyle.RESET);
        out.append(TuiBox.frame(null, headerLines)).append('\n');

        if (payload.players == null || payload.players.isEmpty()) {
            return out.append(TuiStyle.DIM).append("No players in final results.").append(TuiStyle.RESET)
                    .toString();
        }

        List<PlayerPayload> connected = new ArrayList<>();
        List<PlayerPayload> disconnected = new ArrayList<>();
        for (PlayerPayload p : payload.players) {
            if (p.isConnected) {
                connected.add(p);
            } else {
                disconnected.add(p);
            }
        }
        connected.sort(Comparator.comparingInt((PlayerPayload p) -> p.prestigePoints).reversed());
        disconnected.sort(Comparator.comparing(p -> p.nickname));

        List<String> body = new ArrayList<>();
        body.add(TuiStyle.bold("RANK  PLAYER              TOTEM     PRESTIGE   FOOD"));
        for (int i = 0; i < connected.size(); i++) {
            PlayerPayload p = connected.get(i);
            String medal = switch (i) {
                case 0 -> TuiStyle.BRIGHT_YELLOW + " 1st " + TuiStyle.RESET;
                case 1 -> TuiStyle.BRIGHT_WHITE + " 2nd " + TuiStyle.RESET;
                case 2 -> TuiStyle.YELLOW + " 3rd " + TuiStyle.RESET;
                default -> TuiBox.padRight(" " + (i + 1) + (i >= 9 ? "  " : "th "), 5);
            };
            String name = TuiBox.padRight(TuiStyle.bold(p.nickname), 28);
            String totem = TuiBox.padRight(formatTotem(p.totem), 18);
            String prestige = TuiBox.padRight(
                    TuiStyle.BRIGHT_YELLOW + "★ " + p.prestigePoints + TuiStyle.RESET, 19);
            String food = TuiStyle.GREEN + "F " + p.food + TuiStyle.RESET;
            body.add(medal + " " + name + " " + totem + " " + prestige + " " + food);
        }
        for (PlayerPayload p : disconnected) {
            body.add(TuiStyle.DIM + " --  " + p.nickname
                    + "  (disconnected, not ranked)" + TuiStyle.RESET);
        }

        out.append(TuiBox.frame(TuiStyle.BRIGHT_GREEN + "LEADERBOARD" + TuiStyle.RESET, body));
        return out.toString();
    }

    /**
     * Renders the all-time ranking of matches played with the same number of
     * players as the match that just finished.
     *
     * <p>Rows produced by the just-finished match are highlighted, and the local
     * player's own placement is shown on top.
     *
     * @param payload all-time ranking to render
     * @param localNickname nickname of the player using this client, used to point
     *                      out its placement; may be {@code null}
     * @return the framed, colored ranking block
     */
    String renderRanking(RankingPayload payload, String localNickname) {
        StringBuilder out = new StringBuilder();

        if (payload.rows == null || payload.rows.isEmpty()) {
            out.append(TuiStyle.DIM).append("No ranking data available yet.").append(TuiStyle.RESET);
            return TuiBox.frame(rankingTitle(payload), List.of(out.toString()));
        }

        List<String> body = new ArrayList<>();
        for (RankingPayload.RankingRow row : payload.rows) {
            if (row.currentGame && localNickname != null && localNickname.equals(row.nickname)) {
                body.add(TuiStyle.BRIGHT_YELLOW + "You placed #" + row.rank
                        + " of " + payload.rows.size() + TuiStyle.RESET);
            }
        }
        if (!body.isEmpty()) {
            body.add("");
        }
        body.add(TuiStyle.bold("RANK  PLAYER               SCORE      DATE"));
        for (RankingPayload.RankingRow row : payload.rows) {
            String rankCol = TuiBox.padRight("#" + row.rank, 5);
            String nameCol = TuiBox.padRight(row.nickname, 20);
            String scoreCol = TuiBox.padRight(TuiStyle.BRIGHT_YELLOW + "★ " + row.score + TuiStyle.RESET, 10);
            String line = rankCol + " " + nameCol + " " + scoreCol + " "
                    + TuiStyle.DIM + shortDate(row.datePlayed) + TuiStyle.RESET;
            body.add(row.currentGame ? TuiStyle.BRIGHT_GREEN + "➤ " + TuiStyle.RESET + line : "  " + line);
        }

        out.append(TuiBox.frame(rankingTitle(payload), body));
        return out.toString();
    }

    private static String rankingTitle(RankingPayload payload) {
        return TuiStyle.BRIGHT_CYAN + "ALL-TIME RANKING  (" + payload.numPlayers + " players)" + TuiStyle.RESET;
    }

    private static String shortDate(String isoDateTime) {
        if (isoDateTime == null) {
            return "";
        }
        String trimmed = isoDateTime.length() >= 16 ? isoDateTime.substring(0, 16) : isoDateTime;
        return trimmed.replace('T', ' ');
    }

    // ─────────────────────────────────────────────────────────────────────────
    //  Header
    // ─────────────────────────────────────────────────────────────────────────

    private static String renderHeader(GamePayload payload, String localNickname,
                                       int minInnerWidth) {
        TurnOrderPayload turn = payload.turnOrder;

        String title = TuiStyle.BOLD + TuiStyle.BRIGHT_CYAN + "MESOS" + TuiStyle.RESET
                + TuiStyle.DIM + "  game #" + payload.id + TuiStyle.RESET;

        String status = TuiStyle.paint(TuiStyle.forStatus(payload.status),
                "● " + safe(payload.status));
        String era = TuiStyle.BRIGHT_YELLOW + "Era " + payload.currentEra + "/3" + TuiStyle.RESET;

        String round = turn == null ? "" : TuiStyle.BRIGHT_BLUE + "Round " + turn.roundNumber + TuiStyle.RESET;
        String phase = turn == null ? "" : TuiStyle.BRIGHT_MAGENTA
                + "Phase: " + safe(turn.currentRoundPhase) + TuiStyle.RESET;

        String current = "";
        if (turn != null && turn.currentPlayerNickname != null) {
            boolean isMe = turn.currentPlayerNickname.equals(localNickname);
            current = TuiStyle.BOLD + "Turn: " + TuiStyle.RESET + turn.currentPlayerNickname
                    + (isMe ? "  " + TuiStyle.BRIGHT_GREEN + "← YOU" + TuiStyle.RESET : "");
        }

        String you = TuiStyle.DIM + "you: " + TuiStyle.RESET
                + TuiStyle.bold(safe(localNickname));

        List<String> lines = new ArrayList<>();
        lines.add(title + "    " + status + "   " + era);
        if (!round.isEmpty() || !phase.isEmpty()) {
            lines.add(round + "   " + phase);
        }
        if (!current.isEmpty()) {
            lines.add(current + "    " + you);
        } else {
            lines.add(you);
        }

        return TuiBox.frame(null, lines, minInnerWidth);
    }

    // ─────────────────────────────────────────────────────────────────────────
    //  Players panel
    // ─────────────────────────────────────────────────────────────────────────

    private static String renderPlayers(List<PlayerPayload> players,
                                        TurnOrderPayload turn,
                                        String localNickname,
                                        int minInnerWidth) {
        if (players == null || players.isEmpty()) {
            return TuiBox.frame(TuiStyle.BRIGHT_BLUE + "PLAYERS" + TuiStyle.RESET,
                    List.of(TuiStyle.DIM + "no players" + TuiStyle.RESET),
                    minInnerWidth);
        }

        String currentNickname = turn == null ? null : turn.currentPlayerNickname;

        List<String> lines = new ArrayList<>();
        lines.add(playerRow(
                " ".repeat(COL_MARKER),
                TuiStyle.bold("PLAYER"),
                TuiStyle.bold("TOTEM"),
                TuiStyle.bold("FOOD"),
                TuiStyle.bold("PRESTIGE"),
                TuiStyle.bold("TRIBE")));
        for (PlayerPayload p : players) {
            boolean isCurrent = p.nickname.equals(currentNickname);
            boolean isMe = p.nickname.equals(localNickname);

            String marker = isCurrent
                    ? TuiStyle.BRIGHT_GREEN + " ▶ " + TuiStyle.RESET
                    : "   ";
            String nameStyled = isMe
                    ? TuiStyle.BOLD + TuiStyle.BRIGHT_CYAN + p.nickname + TuiStyle.RESET
                    : (p.isConnected ? p.nickname : TuiStyle.DIM + p.nickname + TuiStyle.RESET);
            String food = TuiStyle.GREEN + "F " + p.food + TuiStyle.RESET;
            String prestige = TuiStyle.BRIGHT_YELLOW + "★ " + p.prestigePoints + TuiStyle.RESET;
            String tribe = p.isConnected
                    ? formatTribeSummary(p.tribe)
                    : TuiStyle.DIM + "[disconnected]" + TuiStyle.RESET;

            lines.add(playerRow(marker, nameStyled, formatTotem(p.totem), food, prestige, tribe));
        }
        return TuiBox.frame(TuiStyle.BRIGHT_BLUE + "PLAYERS" + TuiStyle.RESET, lines,
                minInnerWidth);
    }

    private static String playerRow(String marker, String name, String totem,
                                    String food, String prestige, String tribe) {
        return TuiBox.padRight(marker, COL_MARKER)
                + " " + TuiBox.padRight(name, COL_NAME)
                + " " + TuiBox.padRight(totem, COL_TOTEM)
                + " " + TuiBox.padRight(food, COL_FOOD)
                + " " + TuiBox.padRight(prestige, COL_PRESTIGE)
                + " " + tribe;
    }

    private static String formatTotem(String totem) {
        if (totem == null) {
            return TuiStyle.DIM + "—" + TuiStyle.RESET;
        }
        String color = TuiStyle.forTotem(totem);
        return color + "● " + totem + TuiStyle.RESET;
    }

    private static String formatTribeSummary(TribePayload tribe) {
        if (tribe == null) {
            return TuiStyle.DIM + "—" + TuiStyle.RESET;
        }

        Map<String, Integer> counts = new LinkedHashMap<>();
        if (tribe.characters != null) {
            for (CardPayload card : tribe.characters) {
                counts.merge(safe(card.type), 1, Integer::sum);
            }
        }
        int buildings = tribe.buildings == null ? 0 : tribe.buildings.size();

        if (counts.isEmpty() && buildings == 0) {
            return TuiStyle.DIM + "empty" + TuiStyle.RESET;
        }

        List<String> parts = new ArrayList<>();
        for (Map.Entry<String, Integer> e : counts.entrySet()) {
            String color = TuiStyle.forCardType(e.getKey());
            parts.add(color + e.getValue() + TuiStyle.iconForCardType(e.getKey()) + TuiStyle.RESET);
        }
        if (buildings > 0) {
            parts.add(TuiStyle.YELLOW + buildings + "#" + TuiStyle.RESET);
        }
        return String.join(" ", parts);
    }

    // ─────────────────────────────────────────────────────────────────────────
    //  Board
    // ─────────────────────────────────────────────────────────────────────────

    private static String renderBoard(BoardPayload board, List<PlayerPayload> players) {
        if (board == null) {
            return TuiBox.frame(TuiStyle.BRIGHT_MAGENTA + "BOARD" + TuiStyle.RESET,
                    List.of(TuiStyle.DIM + "no board state" + TuiStyle.RESET));
        }

        List<String> lines = new ArrayList<>();
        lines.add(TuiStyle.bold("UPPER ROW"));
        lines.addAll(splitLines(renderCardRow(board.upperRow)));

        boolean hasPath = (board.offerSpaces != null && !board.offerSpaces.isEmpty())
                || (board.startingBlocks != null && !board.startingBlocks.isEmpty());
        if (hasPath) {
            lines.add("");
            lines.add(TuiStyle.bold("OFFER PATH")
                    + TuiStyle.DIM + "   (#N start | ^ from upper | v from lower)"
                    + TuiStyle.RESET);
            lines.addAll(splitLines(renderOfferPath(
                    board.offerSpaces, board.startingBlocks, players)));
            lines.add("");
        } else {
            lines.add("");
        }

        lines.add(TuiStyle.bold("LOWER ROW"));
        lines.addAll(splitLines(renderCardRow(board.lowerRow)));

        return TuiBox.frame(TuiStyle.BRIGHT_MAGENTA + "BOARD" + TuiStyle.RESET, lines,
                MIN_PANEL_WIDTH);
    }

    private static String renderCardRow(List<CardPayload> cards) {
        if (cards == null || cards.isEmpty()) {
            return TuiStyle.DIM + "(empty)" + TuiStyle.RESET;
        }
        List<String> blocks = new ArrayList<>();
        for (CardPayload card : cards) {
            blocks.add(renderMiniCard(card));
        }
        return TuiBox.joinSideBySide(blocks, " ");
    }

    private static String renderMiniCard(CardPayload card) {
        String type = safe(card.type);
        String color = TuiStyle.forCardType(type);
        String icon = TuiStyle.iconForCardType(type);
        String shortType = TuiStyle.shortTypeForCardType(type);

        String typeLine = color + TuiStyle.BOLD + icon + " " + shortType + TuiStyle.RESET;
        String idLine = TuiStyle.DIM + "#" + card.id + " e" + card.era + TuiStyle.RESET;
        String detail = detailForCard(card);

        int width = MINI_CARD_WIDTH;
        String top = color + TuiBox.CARD_TL + TuiBox.INNER_H.repeat(width) + TuiBox.CARD_TR
                + TuiStyle.RESET;
        String bot = color + TuiBox.CARD_BL + TuiBox.INNER_H.repeat(width) + TuiBox.CARD_BR
                + TuiStyle.RESET;
        String side = color + TuiBox.INNER_V + TuiStyle.RESET;

        StringBuilder out = new StringBuilder();
        out.append(top).append('\n');
        out.append(side).append(TuiBox.center(typeLine, width)).append(side).append('\n');
        out.append(side).append(TuiBox.center(idLine, width)).append(side).append('\n');
        out.append(side).append(TuiBox.center(detail, width)).append(side).append('\n');
        out.append(bot);
        return out.toString();
    }

    private static String detailForCard(CardPayload card) {
        String type = card.type == null ? "" : card.type;
        return switch (type) {
            case "HUNTER" -> card.hunterHasEffect != null && card.hunterHasEffect
                    ? TuiStyle.BRIGHT_RED + "+effect" + TuiStyle.RESET
                    : TuiStyle.DIM + "· plain" + TuiStyle.RESET;
            case "SHAMAN" -> {
                int stars = card.sciamanStars == null ? 0 : card.sciamanStars;
                yield TuiStyle.BRIGHT_YELLOW + "★".repeat(Math.max(1, stars)) + TuiStyle.RESET;
            }
            case "BUILDER" -> {
                String discount = card.discount == null ? "0" : String.valueOf(card.discount);
                String pp = card.finalPrestigePoints == null ? "0" : String.valueOf(card.finalPrestigePoints);
                yield TuiStyle.YELLOW + "⊟-" + discount + TuiStyle.RESET
                        + " " + TuiStyle.BRIGHT_YELLOW + "★+" + pp + TuiStyle.RESET;
            }
            case "INVENTOR" -> TuiStyle.BRIGHT_BLUE + "✦ "
                    + truncate(safe(card.inventorPerk), 7) + TuiStyle.RESET;
            case "BUILDING" -> TuiStyle.YELLOW + "F " + safe(card.buildingCost) + TuiStyle.RESET
                    + " " + TuiStyle.BRIGHT_YELLOW + "★ " + safe(card.buildingPrestigePoints) + TuiStyle.RESET;
            case "EVENT" -> TuiStyle.BRIGHT_WHITE
                    + truncate(safe(card.eventCompleteName), 9) + TuiStyle.RESET;
            case "ARTIST" -> TuiStyle.BRIGHT_CYAN + "✧ art set" + TuiStyle.RESET;
            case "GATHERER" -> TuiStyle.BRIGHT_GREEN + "❀ harvest" + TuiStyle.RESET;
            default -> TuiStyle.DIM + "—" + TuiStyle.RESET;
        };
    }

    private static String renderOfferPath(List<OfferSpacePayload> spaces,
                                          List<StartingBlockPayload> startingBlocks,
                                          List<PlayerPayload> players) {
        Map<String, String> totems = buildTotemMap(players);
        List<String> tiles = new ArrayList<>();

        // The starting tile is one wide combined box (matches the server model:
        // a single StartingTile that owns ordered StartingBlock rows).
        boolean hasStarting = startingBlocks != null && !startingBlocks.isEmpty();
        String startingCombined = hasStarting
                ? renderStartingTileCombined(startingBlocks, totems)
                : null;
        if (startingCombined != null) {
            tiles.add(startingCombined);
        }

        // All offer tiles get padded to the combined tile's height so the row
        // looks visually uniform side-by-side.
        int targetHeight = startingCombined != null
                ? lineCount(startingCombined)
                : 6;

        if (spaces != null) {
            for (OfferSpacePayload space : spaces) {
                tiles.add(renderOfferTile(space, totems, targetHeight));
            }
        }
        return TuiBox.joinSideBySide(tiles, " ");
    }

    private static String renderStartingTileCombined(List<StartingBlockPayload> blocks,
                                                     Map<String, String> totems) {
        // Inner width is wider than a single offer tile because each row holds:
        // "#N F+X ★+Y  ◉ nickname". 26 visible chars comfortably fit 12-char names.
        int width = 26;
        String borderColor = TuiStyle.BRIGHT_YELLOW;

        String header = borderColor + TuiStyle.BOLD + "STARTING" + TuiStyle.RESET;
        String top = borderColor + TuiBox.CARD_TL + TuiBox.INNER_H.repeat(width)
                + TuiBox.CARD_TR + TuiStyle.RESET;
        String bot = borderColor + TuiBox.CARD_BL + TuiBox.INNER_H.repeat(width)
                + TuiBox.CARD_BR + TuiStyle.RESET;
        String side = borderColor + TuiBox.INNER_V + TuiStyle.RESET;

        StringBuilder out = new StringBuilder();
        out.append(top).append('\n');
        out.append(side).append(TuiBox.center(header, width)).append(side).append('\n');
        for (StartingBlockPayload block : blocks) {
            String row = buildStartingRow(block, totems);
            out.append(side).append(' ')
                    .append(TuiBox.padRight(row, width - 2))
                    .append(' ').append(side).append('\n');
        }
        out.append(bot);
        return out.toString();
    }

    private static String buildStartingRow(StartingBlockPayload block, Map<String, String> totems) {
        String pos = TuiStyle.BOLD + "#" + block.position + TuiStyle.RESET;
        String food = TuiStyle.GREEN + "F" + signed(block.foodModifier) + TuiStyle.RESET;
        String pp = TuiStyle.BRIGHT_YELLOW + "*" + signed(block.prestigeModifier) + TuiStyle.RESET;

        String occ;
        if (block.occupiedByNickname != null) {
            String totemColor = totems.get(block.occupiedByNickname);
            occ = TuiStyle.forTotem(totemColor) + "o " + TuiStyle.RESET
                    + truncate(block.occupiedByNickname, 12);
        } else {
            occ = TuiStyle.DIM + ". empty" + TuiStyle.RESET;
        }
        return pos + " " + food + " " + pp + "  " + occ;
    }

    private static String renderOfferTile(OfferSpacePayload space, Map<String, String> totems,
                                          int targetHeight) {
        int width = OFFER_TILE_WIDTH;

        String borderColor;
        String occupantLine;
        if (space.occupiedByNickname != null) {
            String totemColor = totems.get(space.occupiedByNickname);
            borderColor = TuiStyle.forTotem(totemColor);
            occupantLine = TuiStyle.forTotem(totemColor) + "o " + TuiStyle.RESET
                    + truncate(space.occupiedByNickname, 5);
        } else {
            borderColor = TuiStyle.BRIGHT_MAGENTA;
            occupantLine = TuiStyle.DIM + "(free)" + TuiStyle.RESET;
        }

        String code = borderColor + TuiStyle.BOLD + space.code + TuiStyle.RESET;
        String up = TuiStyle.BOLD + TuiStyle.BRIGHT_CYAN + "^ " + TuiStyle.RESET
                + TuiStyle.BOLD + TuiStyle.BRIGHT_WHITE + space.topRowCardsToTake + TuiStyle.RESET;
        String dn = TuiStyle.BOLD + TuiStyle.BRIGHT_BLUE + "v " + TuiStyle.RESET
                + TuiStyle.BOLD + TuiStyle.BRIGHT_WHITE + space.bottomRowCardsToTake + TuiStyle.RESET;

        String top = borderColor + TuiBox.CARD_TL + TuiBox.INNER_H.repeat(width)
                + TuiBox.CARD_TR + TuiStyle.RESET;
        String bot = borderColor + TuiBox.CARD_BL + TuiBox.INNER_H.repeat(width)
                + TuiBox.CARD_BR + TuiStyle.RESET;
        String side = borderColor + TuiBox.INNER_V + TuiStyle.RESET;
        String emptyRow = side + TuiBox.center("", width) + side;

        StringBuilder out = new StringBuilder();
        out.append(top).append('\n');
        out.append(side).append(TuiBox.center(code, width)).append(side).append('\n');
        out.append(side).append(TuiBox.center(up, width)).append(side).append('\n');
        out.append(side).append(TuiBox.center(dn, width)).append(side).append('\n');
        out.append(side).append(TuiBox.center(occupantLine, width)).append(side).append('\n');

        // Pad with empty interior rows to reach targetHeight (top + 4 content + bot = 6 baseline).
        int extra = Math.max(0, targetHeight - 6);
        for (int i = 0; i < extra; i++) {
            out.append(emptyRow).append('\n');
        }
        out.append(bot);
        return out.toString();
    }

    private static int lineCount(String multiline) {
        int count = 1;
        for (int i = 0; i < multiline.length(); i++) {
            if (multiline.charAt(i) == '\n') {
                count++;
            }
        }
        return count;
    }

    private static Map<String, String> buildTotemMap(List<PlayerPayload> players) {
        Map<String, String> totems = new LinkedHashMap<>();
        if (players != null) {
            for (PlayerPayload p : players) {
                if (p.nickname != null) {
                    totems.put(p.nickname, p.totem);
                }
            }
        }
        return totems;
    }

    // ─────────────────────────────────────────────────────────────────────────
    //  Utilities
    // ─────────────────────────────────────────────────────────────────────────

    private static String safe(Object value) {
        return value == null ? "—" : value.toString();
    }

    private static String signed(int value) {
        return value >= 0 ? "+" + value : String.valueOf(value);
    }

    private static String truncate(String text, int max) {
        if (text == null) {
            return "—";
        }
        if (text.length() <= max) {
            return text;
        }
        return text.substring(0, Math.max(0, max - 1)) + "…";
    }

    private static List<String> splitLines(String multiLine) {
        List<String> out = new ArrayList<>();
        for (String line : multiLine.split("\n", -1)) {
            out.add(line);
        }
        return out;
    }
}
