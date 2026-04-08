package it.polimi.ingsoft.mesos.server.stubs;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import it.polimi.ingsoft.mesos.server.messages.MessageType;
import it.polimi.ingsoft.mesos.server.messages.ServerMessage;
import it.polimi.ingsoft.mesos.server.controller.GameOrchestrator.GameOrchestrator;
import it.polimi.ingsoft.mesos.server.model.cards.BuildingCard;
import it.polimi.ingsoft.mesos.server.model.cards.Card;
import it.polimi.ingsoft.mesos.server.model.cards.characters.CharacterCard;
import it.polimi.ingsoft.mesos.server.model.game.Game;
import it.polimi.ingsoft.mesos.server.model.game.RoundPhase;
import it.polimi.ingsoft.mesos.server.model.lobby.Lobby;
import it.polimi.ingsoft.mesos.server.model.lobby.LobbyManager;
import it.polimi.ingsoft.mesos.server.model.player.Player;
import it.polimi.ingsoft.mesos.server.model.user.User;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;


//Stub used to test the model + some controller functionalities from a server perspective
public final class GameCliMain {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private final LobbyManager lobbyManager;
    private final BufferedReader reader;
    private final PrintStream out;
    private final Map<String, User> usersByNickname;
    private boolean printJson;

    private Integer currentLobbyId;
    private GameOrchestrator orchestrator;

    private GameCliMain(LobbyManager lobbyManager, BufferedReader reader, PrintStream out) {
        this.lobbyManager = lobbyManager;
        this.reader = reader;
        this.out = out;
        this.usersByNickname = new HashMap<>();
    }

    public static void main(String[] args) throws IOException {
        BufferedReader reader = new BufferedReader(new InputStreamReader(System.in, StandardCharsets.UTF_8));
        new GameCliMain(new LobbyManager(), reader, System.out).run();
    }

    private void run() throws IOException {
        this.printBanner();
        this.askOutputMode();
        this.printState();

        while (true) {
            this.out.print("mesos> ");
            String line = this.reader.readLine();
            if (line == null) {
                this.out.println();
                return;
            }

            String trimmed = line.trim();
            if (trimmed.isEmpty()) {
                continue;
            }

            if (!this.executeCommand(trimmed)) {
                return;
            }
        }
    }

    private boolean executeCommand(String input) {
        List<String> parts = Arrays.stream(input.split("\\s+"))
                .filter(part -> !part.isBlank())
                .toList();
        String command = parts.getFirst().toLowerCase(Locale.ROOT);

        try {
            switch (command) {
                case "help" -> this.printHelp();
                case "state" -> this.printState();
                case "lobby" -> this.printCurrentLobby();
                case "lobbies" -> this.printOpenLobbies();
                case "create-lobby" -> {
                    this.expectArgCount(parts, 3, "create-lobby <host> <size>");
                    this.createLobby(parts.get(1), parts.get(2));
                    this.printState();
                }
                case "join-lobby" -> {
                    this.expectArgCount(parts, 2, "join-lobby <nickname>");
                    this.joinLobby(parts.get(1));
                    this.printState();
                }
                case "exit-lobby" -> {
                    this.expectArgCount(parts, 2, "exit-lobby <nickname>");
                    this.exitLobby(parts.get(1));
                    this.printState();
                }
                case "start-lobby" -> {
                    this.expectArgCount(parts, 1, "start-lobby");
                    this.startLobby();
                    this.printState();
                }
                case "players" -> {
                    this.ensureGameStarted();
                    this.printPlayers();
                }
                case "board", "rows" -> {
                    this.ensureGameStarted();
                    this.printBoard();
                }
                case "spaces" -> {
                    this.ensureGameStarted();
                    this.printSpaces();
                }
                case "setup" -> {
                    this.expectArgCount(parts, 1, "setup");
                    this.ensureGameStarted();
                    this.orchestrator.setupMatch();
                    this.printState();
                    this.printSerializedMessage();
                }
                case "move" -> {
                    this.expectArgCount(parts, 3, "move <player> <space>");
                    this.ensureGameStarted();
                    this.orchestrator.moveTotem(this.findPlayer(parts.get(1)), parts.get(2).toUpperCase(Locale.ROOT));
                    this.printState();
                    this.printSerializedMessage();
                }
                case "choose" -> {
                    this.expectArgCount(parts, 3, "choose <player> <cardId>");
                    this.ensureGameStarted();
                    this.orchestrator.chooseCard(this.findPlayer(parts.get(1)), this.findBoardCardById(parts.get(2)));
                    this.printState();
                    this.printSerializedMessage();
                }
                case "extra" -> {
                    this.expectArgCount(parts, 3, "extra <player> <cardId>");
                    this.ensureGameStarted();
                    this.orchestrator.extraDrawn(this.findPlayer(parts.get(1)), this.findBoardCardById(parts.get(2)));
                    this.printState();
                    this.printSerializedMessage();
                }
                case "resolve" -> {
                    this.expectArgCount(parts, 1, "resolve");
                    this.ensureGameStarted();
                    this.orchestrator.resolveEffects();
                    this.printState();
                    this.printSerializedMessage();
                }
                case "reset" -> {
                    this.expectArgCount(parts, 1, "reset");
                    this.ensureGameStarted();
                    this.orchestrator.resetBoard();
                    this.printState();
                    this.printSerializedMessage();
                }
                case "final" -> {
                    this.expectArgCount(parts, 1, "final");
                    this.ensureGameStarted();
                    Optional<?> rankingResult = this.orchestrator.calculateFinalScore();
                    this.printState();
                    this.printSerializedMessage();
                    if (rankingResult.isEmpty()) {
                        this.out.println("Ranking non configurato per questa sessione.");
                    } else {
                        this.out.println("Ranking result: " + rankingResult.get());
                    }
                }
                case "quit", "exit" -> {
                    return false;
                }
                default -> this.out.println("Comando non riconosciuto. Usa 'help'.");
            }
        } catch (RuntimeException exception) {
            this.out.println("Errore: " + exception.getClass().getSimpleName() + " - " + exception.getMessage());
        }

        return true;
    }

    private void createLobby(String hostNickname, String rawCapacity) {
        int capacity = this.parseCapacity(rawCapacity);
        User host = this.userFor(hostNickname);
        Lobby lobby = this.lobbyManager.createLobby(host, capacity);
        this.currentLobbyId = lobby.getId();
        this.orchestrator = null;
    }

    private void joinLobby(String nickname) {
        Lobby lobby = this.requireCurrentLobby();
        this.lobbyManager.joinLobby(lobby.getId(), this.userFor(nickname));
    }

    private void exitLobby(String nickname) {
        Lobby lobby = this.requireCurrentLobby();
        this.lobbyManager.exitLobby(lobby.getId(), this.existingUserFor(nickname));
    }

    private void startLobby() {
        Lobby lobby = this.requireCurrentLobby();
        this.orchestrator = this.lobbyManager.createGameFromLobby(lobby.getId());
    }

    private int parseCapacity(String rawCapacity) {
        try {
            int capacity = Integer.parseInt(rawCapacity);
            if (capacity < 2 || capacity > 5) {
                throw new IllegalArgumentException("Capacita lobby non valida: " + rawCapacity);
            }
            return capacity;
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException("Capacita lobby non valida: " + rawCapacity);
        }
    }

    private User userFor(String nickname) {
        String normalized = normalizeNickname(nickname);
        return this.usersByNickname.computeIfAbsent(normalized, User::new);
    }

    private User existingUserFor(String nickname) {
        String normalized = normalizeNickname(nickname);
        User user = this.usersByNickname.get(normalized);
        if (user == null) {
            throw new IllegalArgumentException("Utente non noto: " + nickname);
        }
        return user;
    }

    private String normalizeNickname(String nickname) {
        if (nickname == null || nickname.isBlank()) {
            throw new IllegalArgumentException("Nickname non valido");
        }
        return nickname.trim();
    }

    private Lobby requireCurrentLobby() {
        if (this.currentLobbyId == null) {
            throw new IllegalStateException("Nessuna lobby attiva. Usa create-lobby prima.");
        }
        return this.lobbyManager.getLobby(this.currentLobbyId)
                .orElseThrow(() -> new IllegalStateException("Lobby attiva non trovata: " + this.currentLobbyId));
    }

    private void ensureGameStarted() {
        if (this.orchestrator == null) {
            throw new IllegalStateException("Game non avviato. Usa start-lobby prima.");
        }
    }

    private void printBanner() {
        this.out.println("Mesos CLI playground");
        this.out.println("Pipeline attiva: LobbyManager -> createGameFromLobby -> GameOrchestrator -> Game");
        this.out.println("Usa 'help' per la lista comandi.");
    }

    private void printHelp() {
        this.out.println("Comandi lobby:");
        this.out.println("  create-lobby <host> <size>   crea una lobby standard");
        this.out.println("  join-lobby <nickname>        aggiunge un utente alla lobby attiva");
        this.out.println("  exit-lobby <nickname>        rimuove un utente dalla lobby attiva");
        this.out.println("  lobby                        mostra la lobby attiva");
        this.out.println("  lobbies                      mostra le lobby aperte");
        this.out.println("  start-lobby                  usa LobbyManager#createGameFromLobby");
        this.out.println("Comandi game:");
        this.out.println("  setup                        chiama orchestrator.setupMatch()");
        this.out.println("  state                        mostra snapshot complessivo");
        this.out.println("  players                      mostra risorse e tribe dei player");
        this.out.println("  board                        mostra upper/lower row");
        this.out.println("  spaces                       mostra offer spaces e starting blocks");
        this.out.println("  move <player> <space>        chiama moveTotem");
        this.out.println("  choose <player> <cardId>     chiama chooseCard");
        this.out.println("  extra <player> <cardId>      chiama extraDrawn");
        this.out.println("  resolve                      chiama resolveEffects");
        this.out.println("  reset                        chiama resetBoard");
        this.out.println("  final                        chiama calculateFinalScore");
        this.out.println("  exit                         chiude la CLI");
    }

    private void printState() {
        this.out.println();
        this.printCurrentLobby();
        if (this.orchestrator != null) {
            this.printGameState();
        }
        this.out.println();
    }

    private void printCurrentLobby() {
        if (this.currentLobbyId == null) {
            this.out.println("Lobby: nessuna lobby attiva");
            return;
        }

        Lobby lobby = this.requireCurrentLobby();
        this.out.println("Lobby #" + lobby.getId()
                + " | status=" + lobby.getStatus()
                + " | host=" + lobby.getHostNickname()
                + " | users=" + lobby.getUsers().size() + "/" + lobby.getChosenCapacity()
                + " | canStart=" + lobby.canStart());
        this.out.println("Lobby users: " + lobby.getUsers().stream()
                .map(User::getNickname)
                .collect(Collectors.joining(", ", "[", "]")));
    }

    private void printOpenLobbies() {
        List<Lobby> lobbies = this.lobbyManager.listOpenLobbies();
        if (lobbies.isEmpty()) {
            this.out.println("Nessuna lobby aperta.");
            return;
        }

        for (Lobby lobby : lobbies) {
            this.out.println("  - #" + lobby.getId()
                    + " status=" + lobby.getStatus()
                    + " host=" + lobby.getHostNickname()
                    + " users=" + lobby.getUsers().stream().map(User::getNickname).collect(Collectors.joining(", ", "[", "]"))
                    + " capacity=" + lobby.getChosenCapacity());
        }
    }

    private void printGameState() {
        Game game = this.orchestrator.getGame();
        List<Player> turnOrder = this.safeTurnOrder();

        this.out.println("Game #" + game.getId()
                + " | status=" + game.getStatus()
                + " | era=" + game.getCurrentEra()
                + " | round=" + game.getTurnOrderTrack().getRoundNumber()
                + " | phase=" + game.getTurnOrderTrack().getCurrentRoundPhase()
                + " | turnPhase=" + game.getTurnState().getPhase()
                + " | frozen=" + this.orchestrator.isFrozen());

        String currentPlayer = turnOrder.isEmpty()
                ? "-"
                : game.getTurnOrderTrack().getCurrentPlayer().getNickname();
        this.out.println("Current player: " + currentPlayer);
        this.out.println("Turn order: " + turnOrder.stream()
                .map(Player::getNickname)
                .collect(Collectors.joining(" -> ")));

        this.printPlayers();
        this.printBoard();
        this.printSpaces();
        this.printCurrentActionHints();
    }

    private void printPlayers() {
        Game game = this.orchestrator.getGame();
        this.out.println("Players:");
        for (Player player : game.getPlayers()) {
            this.out.println("  - " + player.getNickname()
                    + " food=" + player.getFood()
                    + " prestige=" + player.getPrestigePoints()
                    + " totem=" + player.getTotem()
                    + " chars=" + player.getTribe().getCharacters().stream()
                    .map(this::describeOwnedCard)
                    .collect(Collectors.joining(", ", "[", "]"))
                    + " buildings=" + player.getTribe().getBuildings().stream()
                    .map(this::describeOwnedCard)
                    .collect(Collectors.joining(", ", "[", "]")));
        }
    }

    private void printBoard() {
        Game game = this.orchestrator.getGame();
        this.out.println("Upper row: " + game.getBoard().getUpperRowCards().stream()
                .map(this::describeBoardCard)
                .collect(Collectors.joining(", ", "[", "]")));
        this.out.println("Lower row: " + game.getBoard().getLowerRowCards().stream()
                .map(this::describeBoardCard)
                .collect(Collectors.joining(", ", "[", "]")));
    }

    private void printSpaces() {
        Game game = this.orchestrator.getGame();
        this.out.println("Offer spaces: " + game.getBoard().getOfferSpaces().stream()
                .map(space -> space.getCode()
                        + "(" + space.getTopRowCardsToTake()
                        + "U/" + space.getBottomRowCardsToTake()
                        + "L)=" + (space.getOccupiedBy() == null ? "-" : space.getOccupiedBy().getNickname()))
                .collect(Collectors.joining(", ", "[", "]")));
        this.out.println("Starting blocks: " + game.getBoard().getStartingBlocks().stream()
                .map(block -> "#" + block.getPosition()
                        + "(food=" + block.getFoodModifier()
                        + ", prestige=" + block.getPrestigeModifier()
                        + ")=" + (block.getOccupiedBy() == null ? "-" : block.getOccupiedBy().getNickname()))
                .collect(Collectors.joining(", ", "[", "]")));
    }

    private void printCurrentActionHints() {
        Game game = this.orchestrator.getGame();
        RoundPhase phase = game.getTurnOrderTrack().getCurrentRoundPhase();
        if (this.safeTurnOrder().isEmpty()) {
            return;
        }

        Player currentPlayer = game.getTurnOrderTrack().getCurrentPlayer();
        if (phase == RoundPhase.PLAYER_DRAWS) {
            this.out.println("Current draw quotas for " + currentPlayer.getNickname()
                    + ": upper=" + game.getTurnState().getRemainingUpperChoices(currentPlayer)
                    + ", lower=" + game.getTurnState().getRemainingLowerChoices(currentPlayer));
            this.out.println("Draftable upper: " + game.getBoard().getDraftableUpperCards(currentPlayer).stream()
                    .map(this::describeBoardCard)
                    .collect(Collectors.joining(", ", "[", "]")));
            this.out.println("Draftable lower: " + game.getBoard().getDraftableLowerCards(currentPlayer).stream()
                    .map(this::describeBoardCard)
                    .collect(Collectors.joining(", ", "[", "]")));
        } else if (phase == RoundPhase.EXTRA_DRAWS) {
            this.out.println("Extra draw player: " + currentPlayer.getNickname());
            this.out.println("Draftable upper: " + game.getBoard().getDraftableUpperCards(currentPlayer).stream()
                    .map(this::describeBoardCard)
                    .collect(Collectors.joining(", ", "[", "]")));
            this.out.println("Draftable lower: " + game.getBoard().getDraftableLowerCards(currentPlayer).stream()
                    .map(this::describeBoardCard)
                    .collect(Collectors.joining(", ", "[", "]")));
        }
    }

    private Player findPlayer(String nickname) {
        return this.orchestrator.getPlayers().stream()
                .filter(player -> player.getNickname().equalsIgnoreCase(nickname))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Player non trovato: " + nickname));
    }

    private Card findBoardCardById(String rawCardId) {
        int cardId;
        try {
            cardId = Integer.parseInt(rawCardId);
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException("Card id non valido: " + rawCardId);
        }

        return this.allBoardCards().stream()
                .filter(card -> card.getId() == cardId)
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Carta non presente sul board: " + rawCardId));
    }

    private List<Card> allBoardCards() {
        Game game = this.orchestrator.getGame();
        List<Card> cards = new ArrayList<>(game.getBoard().getUpperRowCards());
        cards.addAll(game.getBoard().getLowerRowCards());
        return cards;
    }

    private List<Player> safeTurnOrder() {
        try {
            return this.orchestrator.getGame().getTurnOrderTrack().getTurnOrder();
        } catch (IllegalStateException exception) {
            return List.of();
        }
    }

    private String describeBoardCard(Card card) {
        return "#" + card.getId()
                + ":" + card.getClass().getSimpleName()
                + "(era=" + card.getEra() + ")";
    }

    private String describeOwnedCard(Card card) {
        if (card instanceof BuildingCard buildingCard) {
            return "#" + buildingCard.getId() + ":" + buildingCard.getClass().getSimpleName();
        }
        if (card instanceof CharacterCard characterCard) {
            return "#" + characterCard.getId() + ":" + characterCard.getClass().getSimpleName();
        }
        return "#" + card.getId() + ":" + card.getClass().getSimpleName();
    }

    private void expectArgCount(List<String> parts, int expectedCount, String usage) {
        if (parts.size() != expectedCount) {
            throw new IllegalArgumentException("Uso: " + usage);
        }
    }

    private void askOutputMode() throws IOException {
        this.out.println("Output mode: [1] human-readable  [2] JSON payload");
        this.out.print("Scelta: ");
        String choice = this.reader.readLine();
        this.printJson = "2".equals(choice != null ? choice.trim() : "");
    }

    private void printSerializedMessage() {
        if (!this.printJson) return;
        ServerMessage msg = new ServerMessage(MessageType.GAME_UPDATE, this.orchestrator.buildPayload());
        this.out.println("--- ServerMessage JSON ---");
        this.out.println(GSON.toJson(msg));
        this.out.println("-------------------------");
    }
}
