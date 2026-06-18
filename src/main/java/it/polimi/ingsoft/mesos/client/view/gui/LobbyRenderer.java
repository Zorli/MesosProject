package it.polimi.ingsoft.mesos.client.view.gui;

import it.polimi.ingsoft.mesos.client.serverproxy.ServerProxy;
import it.polimi.ingsoft.mesos.server.model.game.enums.TotemColor;
import it.polimi.ingsoft.mesos.shared.payload.servertoclient.lobby.LobbyEventType;
import it.polimi.ingsoft.mesos.shared.payload.servertoclient.lobby.LobbyUpdatePayload;
import it.polimi.ingsoft.mesos.shared.payload.servertoclient.prelobby.LobbyListPayload;
import it.polimi.ingsoft.mesos.shared.payload.servertoclient.prelobby.LobbySummaryPayload;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.scene.shape.Circle;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.Supplier;
import java.util.stream.Collectors;

/**
 * Renders the lobby pages and owns the local snapshot of the lobby the client
 * is currently seated at.
 *
 * <p>This class is a collaborator of {@link GUIView}: it holds the JavaFX nodes
 * that belong to the login/lobby/lobby-form pages and translates the lobby
 * payloads received from the server into UI updates. Navigation between pages
 * stays on {@link GUIView}; this renderer only manipulates the lobby labels,
 * the lobby list container and the action buttons.</p>
 */
final class LobbyRenderer {

    private static final String DEFAULT_LOBBY_STATUS = "WAITING";

    private final Label lobbyStatusLabel;
    private final Label lobbyCountLabel;
    private final Label lobbyActionTitleLabel;
    private final Label lobbyActionSubtitleLabel;
    private final Label currentLobbyTitleLabel;
    private final Label currentLobbyHostLabel;
    private final Label currentLobbyPlayersLabel;
    private final Label currentLobbyStateLabel;
    private final Label playersInLobby;
    private final Label capacityLabel;
    private final Label totemColorLabel;
    private final ComboBox<Integer> capacityComboBox;
    private final ComboBox<TotemColor> totemColorComboBox;
    private final ComboBox<TotemColor> joinTotemColorComboBox;
    private final VBox lobbyListContainer;
    private final Button openCreateTableButton;
    private final Button backToLobbyListButton;
    private final Button createLobbyButton;
    private final Button startLobbyButton;
    private final Button exitLobbyButton;

    private final Supplier<String> nicknameSupplier;
    private final ServerProxy serverProxy;
    private final Consumer<Runnable> serverDispatcher;

    private Integer currentLobbyId;
    private String currentLobbyHostNickname;
    private String currentLobbyStatus = DEFAULT_LOBBY_STATUS;
    private LobbyUpdatePayload lastLobbyUpdatePayload;
    private LobbyListPayload lastLobbyListPayload;

    LobbyRenderer(
            Label lobbyStatusLabel,
            Label lobbyCountLabel,
            Label lobbyActionTitleLabel,
            Label lobbyActionSubtitleLabel,
            Label currentLobbyTitleLabel,
            Label currentLobbyHostLabel,
            Label currentLobbyPlayersLabel,
            Label currentLobbyStateLabel,
            Label playersInLobby,
            Label capacityLabel,
            Label totemColorLabel,
            ComboBox<Integer> capacityComboBox,
            ComboBox<TotemColor> totemColorComboBox,
            ComboBox<TotemColor> joinTotemColorComboBox,
            VBox lobbyListContainer,
            Button openCreateTableButton,
            Button backToLobbyListButton,
            Button createLobbyButton,
            Button startLobbyButton,
            Button exitLobbyButton,
            Supplier<String> nicknameSupplier,
            ServerProxy serverProxy,
            Consumer<Runnable> serverDispatcher
    ) {
        this.lobbyStatusLabel = lobbyStatusLabel;
        this.lobbyCountLabel = lobbyCountLabel;
        this.lobbyActionTitleLabel = lobbyActionTitleLabel;
        this.lobbyActionSubtitleLabel = lobbyActionSubtitleLabel;
        this.currentLobbyTitleLabel = currentLobbyTitleLabel;
        this.currentLobbyHostLabel = currentLobbyHostLabel;
        this.currentLobbyPlayersLabel = currentLobbyPlayersLabel;
        this.currentLobbyStateLabel = currentLobbyStateLabel;
        this.playersInLobby = playersInLobby;
        this.capacityLabel = capacityLabel;
        this.totemColorLabel = totemColorLabel;
        this.capacityComboBox = capacityComboBox;
        this.totemColorComboBox = totemColorComboBox;
        this.joinTotemColorComboBox = joinTotemColorComboBox;
        this.lobbyListContainer = lobbyListContainer;
        this.openCreateTableButton = openCreateTableButton;
        this.backToLobbyListButton = backToLobbyListButton;
        this.createLobbyButton = createLobbyButton;
        this.startLobbyButton = startLobbyButton;
        this.exitLobbyButton = exitLobbyButton;
        this.nicknameSupplier = nicknameSupplier;
        this.serverProxy = serverProxy;
        this.serverDispatcher = serverDispatcher;
    }

    boolean isInLobby() {
        return this.currentLobbyId != null;
    }

    Integer getCurrentLobbyId() {
        return this.currentLobbyId;
    }

    /**
     * Applies a lobby update from the server: refreshes the status message,
     * updates the snapshot of the current lobby and enables or disables the
     * lobby action buttons accordingly.
     */
    void renderLobbyUpdate(LobbyUpdatePayload payload) {
        this.lastLobbyUpdatePayload = payload;
        if (payload == null) {
            return;
        }
        String message = payload.message == null ? describeLobbyEvent(payload.event) : payload.message;
        if (payload.event != LobbyEventType.LOBBY_LEFT || this.isCurrentUser(payload.nickname)) {
            this.lobbyStatusLabel.setText(message);
        }
        this.renderCurrentLobby(payload);
        this.updateLobbyActions();
    }

    /**
     * Renders the available lobbies. Returns {@code false} when the client is
     * already seated, so the caller can keep the list page hidden.
     */
    boolean renderLobbyList(LobbyListPayload payload) {
        if (this.currentLobbyId != null) {
            this.renderLobbyListUnavailable();
            this.updateLobbyActions();
            return false;
        }

        this.lastLobbyListPayload = payload;
        List<LobbySummaryPayload> lobbies = payload == null || payload.lobbies == null
                ? List.of()
                : payload.lobbies;

        this.lobbyListContainer.getChildren().clear();
        if (lobbies.isEmpty()) {
            this.lobbyCountLabel.setText("0 tables");
            this.lobbyListContainer.getChildren().add(
                    BoardRenderer.emptyState("No active lobbies", "Create a table and wait for the other players."));
        } else {
            this.lobbyCountLabel.setText(lobbies.size() == 1 ? "1 table" : lobbies.size() + " tables");
            lobbies.stream()
                    .sorted(Comparator.comparingInt(lobby -> lobby.lobbyId))
                    .map(this::lobbyCard)
                    .forEach(this.lobbyListContainer.getChildren()::add);
        }

        if (this.lastLobbyUpdatePayload != null && !lobbies.isEmpty()) {
            lobbies.stream()
                    .filter(l -> this.isCurrentLobby(l.lobbyId))
                    .findFirst()
                    .ifPresent(l -> {
                        this.currentLobbyHostNickname = l.hostNickname;
                        this.currentLobbyStatus = l.status;
                        this.currentLobbyTitleLabel.setText("Lobby #" + l.lobbyId);
                        this.currentLobbyHostLabel.setText("Host: " + l.hostNickname);
                        this.currentLobbyPlayersLabel.setText(
                                "Players: " + l.currentPlayers + "/" + l.maxPlayers);
                        this.currentLobbyStateLabel.setText(
                                "WAITING".equalsIgnoreCase(l.status) ? "Waiting" : l.status);
                    });
        }

        this.updateLobbyActions();
        return true;
    }

    /** Sets the lobby status label, used by the controller for error feedback. */
    void setLobbyStatus(String message) {
        this.lobbyStatusLabel.setText(message);
    }

    /** Sets the lobby form subtitle, used by the controller for error feedback. */
    void setLobbyActionSubtitle(String message) {
        this.lobbyActionSubtitleLabel.setText(message);
    }

    /** Shows an empty lobby-list state while the user is already inside a lobby. */
    void renderLobbyListUnavailable() {
        this.lobbyCountLabel.setText("In lobby");
        this.lobbyListContainer.getChildren().clear();
        this.lobbyListContainer.getChildren().add(
                BoardRenderer.emptyState("Table list unavailable", "Exit your lobby to browse available tables."));
    }

    /** Shows placeholder rows while waiting for the lobby list payload. */
    void renderLobbyLoading() {
        this.lobbyCountLabel.setText("Loading");
        this.lobbyListContainer.getChildren().clear();
        for (int index = 0; index < 3; index++) {
            HBox row = new HBox(16);
            row.getStyleClass().addAll("lobby-card", "skeleton-row");
            row.getChildren().addAll(
                    BoardRenderer.skeletonLine(170),
                    BoardRenderer.skeletonLine(120),
                    BoardRenderer.skeletonLine(74));
            this.lobbyListContainer.getChildren().add(row);
        }
    }

    /** Resets the current-lobby labels to their neutral state. */
    void renderNoCurrentLobby() {
        this.currentLobbyTitleLabel.setText("No lobby selected");
        this.currentLobbyHostLabel.setText("Host: -");
        this.currentLobbyPlayersLabel.setText("Players: -");
        this.playersInLobby.setText("Players: -");
        this.currentLobbyStateLabel.setText("Waiting");
    }

    /** Enables, disables, shows and hides lobby buttons according to the current lobby state. */
    void updateLobbyActions() {
        boolean inLobby = this.isInLobby();
        boolean isHost = this.isCurrentUserHost();
        boolean isReady = "READY".equalsIgnoreCase(this.currentLobbyStatus);
        this.lobbyActionTitleLabel.setText(inLobby ? "Your table" : "Create table");
        this.lobbyActionSubtitleLabel.setText(inLobby
                ? "You are already seated. Use the table actions below or exit from the lobby list."
                : "Pick a table size from 2 to 5 players and become the host.");
        this.capacityLabel.setVisible(!inLobby);
        this.capacityLabel.setManaged(!inLobby);
        this.totemColorLabel.setVisible(!inLobby);
        this.totemColorLabel.setManaged(!inLobby);
        this.capacityComboBox.setVisible(!inLobby);
        this.capacityComboBox.setManaged(!inLobby);
        this.totemColorComboBox.setVisible(!inLobby);
        this.totemColorComboBox.setManaged(!inLobby);
        this.createLobbyButton.setVisible(!inLobby);
        this.createLobbyButton.setManaged(!inLobby);
        this.capacityComboBox.setDisable(inLobby);
        this.totemColorComboBox.setDisable(inLobby);
        this.joinTotemColorComboBox.setDisable(inLobby);
        this.createLobbyButton.setDisable(inLobby);
        this.startLobbyButton.setDisable(!isHost || !isReady);
        this.exitLobbyButton.setDisable(!inLobby);
        this.openCreateTableButton.setDisable(inLobby);
        this.backToLobbyListButton.setDisable(inLobby);
    }

    /** Clears all lobby snapshots, used after a match ends. */
    void resetState() {
        this.currentLobbyId = null;
        this.currentLobbyHostNickname = null;
        this.currentLobbyStatus = DEFAULT_LOBBY_STATUS;
        this.lastLobbyUpdatePayload = null;
        this.lastLobbyListPayload = null;
        this.renderNoCurrentLobby();
        this.updateLobbyActions();
    }

    /** Applies a lobby update to the local lobby snapshot and visible lobby panel. */
    private void renderCurrentLobby(LobbyUpdatePayload payload) {
        if (payload.event == LobbyEventType.LOBBY_LEFT) {
            if (this.isCurrentUser(payload.nickname)) {
                this.currentLobbyId = null;
                this.currentLobbyHostNickname = null;
                this.currentLobbyStatus = DEFAULT_LOBBY_STATUS;
                this.renderNoCurrentLobby();
            } else {
                this.renderCurrentLobbySnapshot(payload);
                this.updateLobbyActions();
            }
            return;
        }
        if (payload.event != LobbyEventType.LOBBY_CREATED
                && payload.event != LobbyEventType.LOBBY_JOINED
                && payload.event != LobbyEventType.LOBBY_STARTED) {
            return;
        }

        this.renderCurrentLobbySnapshot(payload);
    }

    /** Copies the latest lobby payload into the labels that describe the current table. */
    private void renderCurrentLobbySnapshot(LobbyUpdatePayload payload) {
        this.currentLobbyId = payload.lobbyId;
        this.currentLobbyHostNickname = payload.hostNickname;
        this.currentLobbyStatus = this.statusFromLobbyUpdate(payload);
        this.currentLobbyTitleLabel.setText(
                payload.lobbyId == null ? "Current lobby" : "Lobby #" + payload.lobbyId);
        this.currentLobbyHostLabel.setText(
                payload.hostNickname == null ? "Host: -" : "Host: " + payload.hostNickname);
        int currentPlayers = payload.players == null ? 0 : payload.players.size();
        String maxPlayers = payload.maxPlayers == null ? "-" : String.valueOf(payload.maxPlayers);
        this.currentLobbyPlayersLabel.setText("Players: " + currentPlayers + "/" + maxPlayers);
        this.currentLobbyStateLabel.setText(this.displayLobbyStatus(this.currentLobbyStatus));

        List<String> lobbyPlayers = payload.players == null ? List.of() : payload.players;
        this.playersInLobby.setText("Players in lobby:\n"
                + String.join("\n", this.formatLobbyPlayers(lobbyPlayers, payload.playerTotemColors)));
    }

    /** Builds one lobby row for the lobby list. */
    private HBox lobbyCard(LobbySummaryPayload lobby) {
        HBox card = new HBox(18);
        card.getStyleClass().add("lobby-card");
        if (this.isCurrentLobby(lobby.lobbyId)) {
            card.getStyleClass().add("current-lobby-card");
        }

        VBox details = new VBox(4);
        Label title = new Label("Lobby #" + lobby.lobbyId);
        title.getStyleClass().add("lobby-title");
        Label subtitle = new Label("Host " + lobby.hostNickname
                + "  |  " + lobby.currentPlayers + "/" + lobby.maxPlayers + " players");
        subtitle.getStyleClass().add("muted");
        Label availableColors = new Label("Available colors: " + this.formatTotemColors(lobby.availableTotemColors));
        availableColors.getStyleClass().add("muted");
        HBox seats = seatTrack(lobby.currentPlayers, lobby.maxPlayers);
        details.getChildren().addAll(title, subtitle, availableColors, seats);

        Label status = new Label(lobby.status);
        status.getStyleClass().add("status-pill");

        if (this.isCurrentLobby(lobby.lobbyId)) {
            Label seatedLabel = new Label("Your table");
            seatedLabel.getStyleClass().add("status-pill");
            card.getChildren().addAll(details, status, seatedLabel);
        } else {
            Button joinButton = new Button("Join");
            joinButton.getStyleClass().add("secondary-button");
            joinButton.setDisable(this.isInLobby() || isReadyLobby(lobby));
            joinButton.setOnAction(event ->
                    this.serverDispatcher.accept(() -> this.serverProxy.joinLobby(
                            lobby.lobbyId,
                            this.joinTotemColorComboBox.getValue()
                    ))
            );
            card.getChildren().addAll(details, status, joinButton);
        }
        HBox.setHgrow(details, Priority.ALWAYS);
        return card;
    }

    /** Builds the small filled/empty seat indicator shown inside a lobby card. */
    private static HBox seatTrack(int currentPlayers, int maxPlayers) {
        HBox seats = new HBox(5);
        seats.getStyleClass().add("seat-track");
        for (int index = 0; index < maxPlayers; index++) {
            Circle dot = new Circle(4);
            dot.getStyleClass().add(index < currentPlayers ? "seat-filled" : "seat-empty");
            seats.getChildren().add(dot);
        }
        return seats;
    }

    /** Derives the local lobby status label from the latest lobby update. */
    private String statusFromLobbyUpdate(LobbyUpdatePayload payload) {
        if (payload.event == LobbyEventType.LOBBY_STARTED) {
            return "STARTING";
        }
        int currentPlayers = payload.players == null ? 0 : payload.players.size();
        return payload.maxPlayers != null && currentPlayers >= payload.maxPlayers ? "READY" : "WAITING";
    }

    /** Converts internal lobby status values into user-facing labels. */
    private String displayLobbyStatus(String status) {
        if ("WAITING".equalsIgnoreCase(status)) {
            return "Waiting";
        }
        if ("STARTING".equalsIgnoreCase(status)) {
            return "Starting";
        }
        return status == null ? "Waiting" : status;
    }

    private static boolean isReadyLobby(LobbySummaryPayload lobby) {
        return lobby.status != null && "READY".equalsIgnoreCase(lobby.status);
    }

    private List<String> formatLobbyPlayers(List<String> players, Map<String, TotemColor> playerTotemColors) {
        if (playerTotemColors == null || playerTotemColors.isEmpty()) {
            return players;
        }

        return players.stream()
                .map(player -> this.formatLobbyPlayer(player, playerTotemColors))
                .toList();
    }

    private String formatLobbyPlayer(String player, Map<String, TotemColor> playerTotemColors) {
        TotemColor color = playerTotemColors.get(player);
        if (color == null) {
            return player;
        }
        return player + " (" + color.name().toLowerCase() + ")";
    }

    private String formatTotemColors(List<TotemColor> colors) {
        if (colors == null || colors.isEmpty()) {
            return "-";
        }
        return colors.stream()
                .map(color -> color.name().toLowerCase())
                .sorted()
                .collect(Collectors.joining(", "));
    }

    private boolean isCurrentLobby(int lobbyId) {
        return this.currentLobbyId != null && this.currentLobbyId == lobbyId;
    }

    private boolean isCurrentUser(String nickname) {
        return nickname != null && nickname.equals(this.nicknameSupplier.get());
    }

    private boolean isCurrentUserHost() {
        return this.currentLobbyId != null
                && this.currentLobbyHostNickname != null
                && this.currentLobbyHostNickname.equals(this.nicknameSupplier.get());
    }

    /** Converts a lobby event enum into the status message shown in the lobby area. */
    static String describeLobbyEvent(LobbyEventType event) {
        if (event == null) {
            return "Lobby update received.";
        }
        return switch (event) {
            case LOBBY_CREATED -> "Lobby created.";
            case LOBBY_JOINED -> "Lobby joined.";
            case LOBBY_LEFT -> "You left the lobby.";
            case LOBBY_STARTED -> "Match started.";
        };
    }
}
