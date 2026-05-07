package it.polimi.ingsoft.mesos.client.view.gui;

import it.polimi.ingsoft.mesos.client.serverproxy.ServerProxy;
import it.polimi.ingsoft.mesos.client.view.ClientView;
import it.polimi.ingsoft.mesos.shared.payload.server.BoardPayload;
import it.polimi.ingsoft.mesos.shared.payload.server.CardPayload;
import it.polimi.ingsoft.mesos.shared.payload.server.ErrorPayload;
import it.polimi.ingsoft.mesos.shared.payload.server.GamePayload;
import it.polimi.ingsoft.mesos.shared.payload.server.LobbyListPayload;
import it.polimi.ingsoft.mesos.shared.payload.server.LobbySummaryPayload;
import it.polimi.ingsoft.mesos.shared.payload.server.OfferSpacePayload;
import it.polimi.ingsoft.mesos.shared.payload.server.PlayerPayload;
import it.polimi.ingsoft.mesos.shared.payload.server.PreMatchEventType;
import it.polimi.ingsoft.mesos.shared.payload.server.PreMatchUpdatePayload;
import it.polimi.ingsoft.mesos.shared.payload.server.StartingBlockPayload;
import it.polimi.ingsoft.mesos.shared.payload.server.TribePayload;
import it.polimi.ingsoft.mesos.shared.payload.server.TurnOrderPayload;
import it.polimi.ingsoft.mesos.shared.payload.server.TurnStatePayload;
import javafx.animation.KeyFrame;
import javafx.animation.PauseTransition;
import javafx.animation.Timeline;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.geometry.Pos;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.TextField;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.HBox;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.shape.Circle;
import javafx.stage.Stage;
import javafx.util.Duration;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * JavaFX implementation of the client view contract.
 *
 * <p>The controller keeps the same separation documented in {@code LaunchClient}: GUI actions go
 * through {@link ServerProxy}, while server updates arrive through the {@link ClientView} methods.
 */
public class GUIController implements ClientView {
    private static final String FXML_RESOURCE = "gui.fxml";
    private static final String CSS_RESOURCE = "mesos-gui.css";
    private static final String CARD_RESOURCE_PATTERN = "/it/polimi/ingsoft/mesos/client/assets/cards/%d.jpg";
    private static final AtomicBoolean JAVAFX_STARTED = new AtomicBoolean(false);

    private ServerProxy serverProxy;
    private Stage stage;
    private String nickname = "Guest";
    private Integer selectedCardId;
    private GamePayload lastGamePayload;
    private LobbyListPayload lastLobbyListPayload;
    private PreMatchUpdatePayload lastPreMatchUpdatePayload;
    private PauseTransition authRedirectDelay;
    private Timeline lobbyRefreshTimer;
    private Integer currentLobbyId;
    private String currentLobbyHostNickname;
    private String currentLobbyStatus = "WAITING";

    @FXML private StackPane pages;
    @FXML private HBox topBar;
    @FXML private StackPane loginPage;
    @FXML private VBox lobbyPage;
    @FXML private VBox gamePage;
    @FXML private VBox endPage;
    @FXML private TextField nicknameField;
    @FXML private Label loginStatusLabel;
    @FXML private Label sessionNicknameLabel;
    @FXML private Label lobbyStatusLabel;
    @FXML private Label lobbyCountLabel;
    @FXML private Label lobbyActionTitleLabel;
    @FXML private Label lobbyActionSubtitleLabel;
    @FXML private Label currentLobbyTitleLabel;
    @FXML private Label currentLobbyHostLabel;
    @FXML private Label currentLobbyPlayersLabel;
    @FXML private Label currentLobbyStateLabel;
    @FXML private ComboBox<Integer> capacityComboBox;
    @FXML private VBox lobbyListContainer;
    @FXML private Button createLobbyButton;
    @FXML private Button startLobbyButton;
    @FXML private Button exitLobbyButton;
    @FXML private Label matchTitleLabel;
    @FXML private Label matchMetaLabel;
    @FXML private Label turnLabel;
    @FXML private Label selectedCardLabel;
    @FXML private HBox upperRowContainer;
    @FXML private HBox lowerRowContainer;
    @FXML private HBox offerPathContainer;
    @FXML private VBox playerListContainer;
    @FXML private HBox tribeCardsContainer;
    @FXML private VBox finalRankingContainer;
    @FXML private ScrollPane lobbyScrollPane;

    public void attachServerProxy(ServerProxy serverProxy) {
        this.serverProxy = Objects.requireNonNull(serverProxy, "serverProxy cannot be null");
    }

    public void start() {
        if (Platform.isFxApplicationThread()) {
            this.openStage(new Stage());
            return;
        }

        if (JAVAFX_STARTED.compareAndSet(false, true)) {
            Platform.startup(() -> this.openStage(new Stage()));
        } else {
            Platform.runLater(() -> this.openStage(new Stage()));
        }
    }

    public void start(Stage stage) {
        if (!Platform.isFxApplicationThread()) {
            Platform.runLater(() -> this.openStage(stage));
            return;
        }
        this.openStage(stage);
    }

    @FXML
    private void initialize() {
        this.capacityComboBox.getItems().setAll(2, 3, 4, 5);
        this.capacityComboBox.getSelectionModel().select(Integer.valueOf(4));
        this.lobbyScrollPane.setFitToWidth(true);
        this.showPage(this.loginPage);
        this.renderMockGame();
        this.renderNoCurrentLobby();
        this.updateLobbyActions();
        this.configureLobbyAutoRefresh();
    }

    @FXML
    private void handleLogin() {
        String typedNickname = this.nicknameField.getText() == null ? "" : this.nicknameField.getText().trim();
        if (typedNickname.isBlank()) {
            this.showTransientStatus(this.loginStatusLabel, "Enter a nickname to join.");
            return;
        }

        this.nickname = typedNickname;
        this.sessionNicknameLabel.setText(this.nickname);
        this.loginStatusLabel.setText("Signing in...");
        this.callServer(() -> this.serverProxy.authenticate(this.nickname));
    }

    @FXML
    private void handleCreateLobby() {
        int capacity = this.capacityComboBox.getValue() == null ? 4 : this.capacityComboBox.getValue();
        this.callServer(() -> this.serverProxy.createLobby(capacity));
    }

    @FXML
    private void handleStartLobby() {
        this.callServer(() -> this.serverProxy.startLobby());
    }

    @FXML
    private void handleExitLobby() {
        this.callServer(() -> this.serverProxy.exitLobby());
    }

    @FXML
    private void handleRanking() {
        this.lobbyStatusLabel.setText("Ranking in arrivo: pulsante presente, dati mock nella schermata finale.");
    }

    @FXML
    private void handleMoveTotemA() {
        this.callServer(() -> this.serverProxy.moveTotem("A"));
    }

    @FXML
    private void handleMoveTotemB() {
        this.callServer(() -> this.serverProxy.moveTotem("B"));
    }

    @FXML
    private void handleMoveTotemC() {
        this.callServer(() -> this.serverProxy.moveTotem("C"));
    }

    @FXML
    private void handleChooseCard() {
        if (this.selectedCardId == null) {
            this.selectedCardLabel.setText("Seleziona una carta dal mercato.");
            return;
        }
        this.callServer(() -> this.serverProxy.chooseCard(this.selectedCardId));
        this.selectedCardLabel.setText("Carta " + this.selectedCardId + " scelta.");
    }

    @FXML
    private void handleExtraDraw() {
        if (this.selectedCardId == null) {
            this.selectedCardLabel.setText("Seleziona una carta per la pesca extra.");
            return;
        }
        this.callServer(() -> this.serverProxy.extraDraw(this.selectedCardId));
        this.selectedCardLabel.setText("Pesca extra richiesta sulla carta " + this.selectedCardId + ".");
    }

    @FXML
    private void handleMockEndMatch() {
        this.renderFinalRanking(this.mockPlayersForRanking());
        this.showPage(this.endPage);
    }

    @FXML
    private void handleBackToLobby() {
        this.selectedCardId = null;
        this.showLobbyList(this.lastLobbyListPayload == null ? new LobbyListPayload(List.of()) : this.lastLobbyListPayload);
        this.showPage(this.lobbyPage);
    }

    @Override
    public void showError(ErrorPayload payload) {
        this.runOnFx(() -> {
            String message = payload == null ? "Errore sconosciuto." : payload.message;
            if (this.loginPage.isVisible()) {
                this.loginStatusLabel.setText(message);
            } else {
                this.lobbyStatusLabel.setText(message);
            }
        });
    }

    @Override
    public void showPreMatchUpdate(PreMatchUpdatePayload payload) {
        this.runOnFx(() -> {
            this.lastPreMatchUpdatePayload = payload;
            if (payload == null) {
                return;
            }
            String message = payload.message == null ? this.describePreMatchEvent(payload.event) : payload.message;
            if (payload.event == PreMatchEventType.AUTHENTICATED) {
                if (payload.nickname != null && !payload.nickname.isBlank()) {
                    this.nickname = payload.nickname;
                    this.sessionNicknameLabel.setText(this.nickname);
                }
                this.loginStatusLabel.setText(message + "\nLoading lobby...");
                this.scheduleAuthenticatedRedirect();
                return;
            }
            if (payload.event != PreMatchEventType.LOBBY_LEFT || this.isCurrentUser(payload.nickname)) {
                this.lobbyStatusLabel.setText(message);
            }
            this.renderCurrentLobby(payload);
            this.updateLobbyActions();
            if (payload.event == PreMatchEventType.LOBBY_CREATED
                    || payload.event == PreMatchEventType.LOBBY_JOINED
                    || payload.event == PreMatchEventType.LOBBY_LEFT) {
                this.showPage(this.lobbyPage);
                if (this.currentLobbyId == null) {
                    this.requestLobbyList();
                } else {
                    this.renderLobbyListUnavailable();
                }
            }
            if (payload.event == PreMatchEventType.LOBBY_STARTED) {
                this.showPage(this.gamePage);
            }
        });
    }

    @Override
    public void showLobbyList(LobbyListPayload payload) {
        this.runOnFx(() -> {
            if (this.currentLobbyId != null) {
                this.renderLobbyListUnavailable();
                this.updateLobbyActions();
                return;
            }

            this.lastLobbyListPayload = payload;
            List<LobbySummaryPayload> lobbies = payload == null || payload.lobbies == null
                    ? List.of()
                    : payload.lobbies;

            this.lobbyListContainer.getChildren().clear();
            if (lobbies.isEmpty()) {
                this.lobbyCountLabel.setText("0 tables");
                this.lobbyListContainer.getChildren().add(
                        this.emptyState("No active lobbies", "Create a table and wait for the other players."));
            } else {
                this.lobbyCountLabel.setText(lobbies.size() == 1 ? "1 table" : lobbies.size() + " tables");
                lobbies.stream()
                        .sorted(Comparator.comparingInt(lobby -> lobby.lobbyId))
                        .map(this::lobbyCard)
                        .forEach(this.lobbyListContainer.getChildren()::add);
            }

            if (this.lastPreMatchUpdatePayload != null
                    && this.currentLobbyId != null
                    && !lobbies.isEmpty()) {
                lobbies.stream()
                        .filter(l -> l.lobbyId == this.currentLobbyId)
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

            if (!this.gamePage.isVisible() && !this.endPage.isVisible()) {
                this.showPage(this.lobbyPage);
            }
        });
    }

    @Override
    public void showGameUpdate(GamePayload payload) {
        this.runOnFx(() -> {
            this.lastGamePayload = payload;
            if (payload == null) {
                this.matchTitleLabel.setText("Partita");
                this.matchMetaLabel.setText("In attesa del server");
                this.turnLabel.setText("Nessun aggiornamento partita ricevuto");
                this.showPage(this.gamePage);
                return;
            }
            this.matchTitleLabel.setText("Partita #" + payload.id);
            this.matchMetaLabel.setText("Era " + payload.currentEra + " / " + payload.status);
            this.turnLabel.setText(this.renderTurn(payload.turnOrder, payload.turnState));
            this.renderBoard(payload.board);
            this.renderPlayers(payload.players);
            this.renderTribe(payload.players);
            this.showPage(this.gamePage);
            if (payload.status != null && payload.status.toLowerCase().contains("end")) {
                this.renderFinalRanking(payload.players);
                this.showPage(this.endPage);
            }
        });
    }

    private void openStage(Stage targetStage) {
        try {
            FXMLLoader loader = new FXMLLoader(GUIController.class.getResource(FXML_RESOURCE));
            loader.setController(this);
            Parent root = loader.load();
            Scene scene = new Scene(root, 1180, 760);
            String stylesheet = GUIController.class.getResource(CSS_RESOURCE).toExternalForm();
            scene.getStylesheets().add(stylesheet);
            targetStage.setTitle("Mesos");
            targetStage.setMinWidth(980);
            targetStage.setMinHeight(680);
            targetStage.setScene(scene);
            targetStage.setMaximized(true);
            targetStage.show();
            this.stage = targetStage;
        } catch (IOException exception) {
            throw new UncheckedIOException("Cannot start Mesos GUI", exception);
        }
    }

    private void renderBoard(BoardPayload board) {
        BoardPayload boardPayload = board == null ? this.mockBoard() : board;
        this.renderCardRow(this.upperRowContainer, boardPayload.upperRow, "upper-card");
        this.renderCardRow(this.lowerRowContainer, boardPayload.lowerRow, "lower-card");
        this.renderOfferPath(boardPayload.offerSpaces, boardPayload.startingBlocks);
    }

    private void renderCardRow(HBox container, List<CardPayload> cards, String styleClass) {
        container.getChildren().clear();
        List<CardPayload> cardPayloads = cards == null ? List.of() : cards;
        if (cardPayloads.isEmpty()) {
            container.getChildren().add(this.emptyState("Mercato vuoto", "In attesa del server."));
            return;
        }
        for (CardPayload card : cardPayloads) {
            StackPane cardNode = this.cardNode(card, styleClass);
            cardNode.setOnMouseClicked(event -> this.selectCard(card.id));
            container.getChildren().add(cardNode);
        }
    }

    private StackPane cardNode(CardPayload card, String styleClass) {
        StackPane wrapper = new StackPane();
        wrapper.getStyleClass().addAll("card-frame", styleClass);
        ImageView imageView = new ImageView(this.cardImage(card.id));
        imageView.setPreserveRatio(true);
        imageView.setFitWidth(108);
        imageView.setFitHeight(154);
        Label chip = new Label("#" + card.id + " " + card.type);
        chip.getStyleClass().add("card-chip");
        StackPane.setAlignment(chip, Pos.BOTTOM_CENTER);
        wrapper.getChildren().addAll(imageView, chip);
        return wrapper;
    }

    private Image cardImage(int cardId) {
        String resource = String.format(CARD_RESOURCE_PATTERN, cardId);
        return new Image(Objects.requireNonNullElse(
                GUIController.class.getResourceAsStream(resource),
                GUIController.class.getResourceAsStream(String.format(CARD_RESOURCE_PATTERN, 1))
        ));
    }

    private void selectCard(int cardId) {
        this.selectedCardId = cardId;
        this.selectedCardLabel.setText("Carta selezionata: " + cardId);
    }

    private void renderOfferPath(List<OfferSpacePayload> offers, List<StartingBlockPayload> starts) {
        this.offerPathContainer.getChildren().clear();
        List<StartingBlockPayload> startingBlocks = starts == null ? List.of() : starts;
        for (StartingBlockPayload block : startingBlocks) {
            VBox tile = this.tile("Start " + block.position, block.foodModifier + " food / " + block.prestigeModifier + " pp", block.occupiedByNickname);
            this.offerPathContainer.getChildren().add(tile);
        }
        List<OfferSpacePayload> offerSpaces = offers == null ? List.of() : offers;
        for (OfferSpacePayload offer : offerSpaces) {
            VBox tile = this.tile(offer.code, offer.topRowCardsToTake + " sopra / " + offer.bottomRowCardsToTake + " sotto", offer.occupiedByNickname);
            this.offerPathContainer.getChildren().add(tile);
        }
    }

    private VBox tile(String title, String detail, String occupant) {
        VBox tile = new VBox(4);
        tile.getStyleClass().add("offer-tile");
        Label titleLabel = new Label(title);
        titleLabel.getStyleClass().add("offer-title");
        Label detailLabel = new Label(detail);
        detailLabel.getStyleClass().add("muted");
        Label occupantLabel = new Label(occupant == null ? "Libero" : occupant);
        occupantLabel.getStyleClass().add(occupant == null ? "free-pill" : "occupied-pill");
        tile.getChildren().addAll(titleLabel, detailLabel, occupantLabel);
        return tile;
    }

    private void renderPlayers(List<PlayerPayload> players) {
        this.playerListContainer.getChildren().clear();
        List<PlayerPayload> playerPayloads = players == null || players.isEmpty()
                ? this.mockPlayersForRanking()
                : players;
        for (PlayerPayload player : playerPayloads) {
            HBox row = new HBox(12);
            row.getStyleClass().add("player-row");
            Label name = new Label(player.nickname);
            name.getStyleClass().add("player-name");
            Label stats = new Label(player.food + " food  |  " + player.prestigePoints + " pp  |  " + player.totem);
            stats.getStyleClass().add("muted");
            row.getChildren().addAll(name, stats);
            this.playerListContainer.getChildren().add(row);
        }
    }

    private void renderTribe(List<PlayerPayload> players) {
        this.tribeCardsContainer.getChildren().clear();
        PlayerPayload current = players == null || players.isEmpty()
                ? this.mockPlayersForRanking().get(0)
                : players.get(0);
        List<CardPayload> cards = new ArrayList<>();
        if (current.tribe != null) {
            if (current.tribe.characters != null) {
                cards.addAll(current.tribe.characters);
            }
            if (current.tribe.buildings != null) {
                cards.addAll(current.tribe.buildings);
            }
        }
        if (cards.isEmpty()) {
            cards.addAll(List.of(
                    new CardPayload(4, "HUNTER", 1),
                    new CardPayload(13, "BUILDING", 1),
                    new CardPayload(70, "INVENTION", 2)));
        }
        cards.stream()
                .limit(6)
                .map(card -> this.cardNode(card, "tribe-card"))
                .forEach(this.tribeCardsContainer.getChildren()::add);
    }

    private void renderFinalRanking(List<PlayerPayload> players) {
        this.finalRankingContainer.getChildren().clear();
        List<PlayerPayload> rankedPlayers = new ArrayList<>(
                players == null || players.isEmpty() ? this.mockPlayersForRanking() : players);
        rankedPlayers.sort(Comparator.comparingInt((PlayerPayload player) -> player.prestigePoints).reversed());
        for (int i = 0; i < rankedPlayers.size(); i++) {
            PlayerPayload player = rankedPlayers.get(i);
            HBox row = new HBox(16);
            row.getStyleClass().add(i == 0 ? "winner-row" : "ranking-row");
            Label position = new Label(String.valueOf(i + 1));
            position.getStyleClass().add("rank-number");
            Label name = new Label(player.nickname);
            name.getStyleClass().add("player-name");
            Label score = new Label(player.prestigePoints + " pp");
            score.getStyleClass().add("score-pill");
            row.getChildren().addAll(position, name, score);
            this.finalRankingContainer.getChildren().add(row);
        }
    }

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
        HBox seats = this.seatTrack(lobby.currentPlayers, lobby.maxPlayers);
        details.getChildren().addAll(title, subtitle, seats);

        Label status = new Label(lobby.status);
        status.getStyleClass().add("status-pill");

        if (this.isCurrentLobby(lobby.lobbyId)) {
            Label seatedLabel = new Label("Your table");
            seatedLabel.getStyleClass().add("status-pill");
            card.getChildren().addAll(details, status, seatedLabel);
        } else {
            Button joinButton = new Button("Join");
            joinButton.getStyleClass().add("secondary-button");
            joinButton.setDisable(this.currentLobbyId != null || this.isReadyLobby(lobby));
            joinButton.setOnAction(event ->
                    this.callServer(() -> this.serverProxy.joinLobby(lobby.lobbyId))
            );
            card.getChildren().addAll(details, status, joinButton);
        }
        HBox.setHgrow(details, javafx.scene.layout.Priority.ALWAYS);
        return card;
    }

    private boolean isReadyLobby(LobbySummaryPayload lobby) {
        return lobby.status != null && "READY".equalsIgnoreCase(lobby.status);
    }

    private HBox seatTrack(int currentPlayers, int maxPlayers) {
        HBox seats = new HBox(5);
        seats.getStyleClass().add("seat-track");
        for (int index = 0; index < maxPlayers; index++) {
            Circle dot = new Circle(4);
            dot.getStyleClass().add(index < currentPlayers ? "seat-filled" : "seat-empty");
            seats.getChildren().add(dot);
        }
        return seats;
    }

    private VBox emptyState(String title, String detail) {
        VBox empty = new VBox(4);
        empty.getStyleClass().add("empty-state");
        Label titleLabel = new Label(title);
        titleLabel.getStyleClass().add("empty-title");
        Label detailLabel = new Label(detail);
        detailLabel.getStyleClass().add("muted");
        empty.getChildren().addAll(titleLabel, detailLabel);
        return empty;
    }

    private void renderMockGame() {
        this.renderBoard(this.mockBoard());
        this.renderPlayers(this.mockPlayersForRanking());
        this.renderTribe(this.mockPlayersForRanking());
        this.renderFinalRanking(this.mockPlayersForRanking());
    }

    private String renderTurn(TurnOrderPayload order, TurnStatePayload state) {
        String phase = state == null ? "setup" : state.phase;
        if (order == null) {
            return "Fase " + phase + " | turno in attesa";
        }
        return "Round " + order.roundNumber + " | " + order.currentRoundPhase
                + " | turno di " + order.currentPlayerNickname;
    }

    private String describePreMatchEvent(PreMatchEventType event) {
        if (event == null) {
            return "Aggiornamento lobby ricevuto.";
        }
        return switch (event) {
            case AUTHENTICATED -> "Login successful.";
            case LOBBY_CREATED -> "Lobby creata.";
            case LOBBY_JOINED -> "Lobby raggiunta.";
            case LOBBY_LEFT -> "Sei uscito dalla lobby.";
            case LOBBY_STARTED -> "Partita avviata.";
        };
    }

    private void showPage(javafx.scene.Node page) {
        for (javafx.scene.Node node : this.pages.getChildren()) {
            node.setVisible(node == page);
            node.setManaged(node == page);
        }
        boolean loginVisible = page == this.loginPage;
        this.topBar.setVisible(!loginVisible);
        this.topBar.setManaged(!loginVisible);
        if (page == this.lobbyPage) {
            if (this.lobbyRefreshTimer != null) {
                this.lobbyRefreshTimer.play();
            }
        } else if (this.lobbyRefreshTimer != null) {
            this.lobbyRefreshTimer.stop();
        }
    }

    private void callServer(ServerCall call) {
        if (this.serverProxy == null) {
            this.showError(new ErrorPayload("CLIENT_GUI_ERROR", "ServerProxy non collegato."));
            return;
        }
        try {
            call.run();
        } catch (RuntimeException exception) {
            this.showError(new ErrorPayload("CLIENT_GUI_ERROR", exception.getMessage()));
        }
    }

    private void showTransientStatus(Label label, String message) {
        label.setText(message);
    }

    private void scheduleAuthenticatedRedirect() {
        if (this.authRedirectDelay != null) {
            this.authRedirectDelay.stop();
        }
        this.authRedirectDelay = new PauseTransition(Duration.seconds(3));
        this.authRedirectDelay.setOnFinished(event -> {
            this.showPage(this.lobbyPage);
            this.requestLobbyList();
        });
        this.authRedirectDelay.play();
    }

    private void configureLobbyAutoRefresh() {
        this.lobbyRefreshTimer = new Timeline(new KeyFrame(Duration.seconds(3), event -> {
            if (!this.canAutoRefreshLobby()) {
                return;
            }
            this.requestLobbyList();
        }));
        this.lobbyRefreshTimer.setCycleCount(Timeline.INDEFINITE);
    }

    private boolean canAutoRefreshLobby() {
        return this.lobbyPage.isVisible()
                && !this.gamePage.isVisible()
                && !this.endPage.isVisible()
                && this.currentLobbyId == null
                && this.serverProxy != null
                && this.serverProxy.isConnected();
    }

    private void requestLobbyList() {
        if (this.currentLobbyId != null) {
            this.renderLobbyListUnavailable();
            return;
        }
        this.callServer(() -> this.serverProxy.listLobbies());
    }

    private void renderLobbyListUnavailable() {
        this.lobbyCountLabel.setText("In lobby");
        this.lobbyListContainer.getChildren().clear();
        this.lobbyListContainer.getChildren().add(
                this.emptyState("Table list unavailable", "Exit your lobby to browse available tables."));
    }

    private void renderCurrentLobby(PreMatchUpdatePayload payload) {
        if (payload == null) {
            return;
        }
        if (payload.event == PreMatchEventType.LOBBY_LEFT) {
            if (this.isCurrentUser(payload.nickname)) {
                this.currentLobbyId = null;
                this.currentLobbyHostNickname = null;
                this.currentLobbyStatus = "WAITING";
                this.renderNoCurrentLobby();
            } else {
                this.renderCurrentLobbySnapshot(payload);
                this.updateLobbyActions();
            }
            return;
        }
        if (payload.event != PreMatchEventType.LOBBY_CREATED
                && payload.event != PreMatchEventType.LOBBY_JOINED
                && payload.event != PreMatchEventType.LOBBY_STARTED) {
            return;
        }

        this.renderCurrentLobbySnapshot(payload);
    }

    private void renderCurrentLobbySnapshot(PreMatchUpdatePayload payload) {
        this.currentLobbyId = payload.lobbyId;
        this.currentLobbyHostNickname = payload.hostNickname;
        this.currentLobbyStatus = this.statusFromPreMatchUpdate(payload);
        if (this.currentLobbyTitleLabel != null)
            this.currentLobbyTitleLabel.setText(
                    payload.lobbyId == null ? "Current lobby" : "Lobby #" + payload.lobbyId);
        if (this.currentLobbyHostLabel != null)
            this.currentLobbyHostLabel.setText(
                    payload.hostNickname == null ? "Host: -" : "Host: " + payload.hostNickname);
        int currentPlayers = payload.players == null ? 0 : payload.players.size();
        String maxPlayers = payload.maxPlayers == null ? "-" : String.valueOf(payload.maxPlayers);
        if (this.currentLobbyPlayersLabel != null)
            this.currentLobbyPlayersLabel.setText("Players: " + currentPlayers + "/" + maxPlayers);
        if (this.currentLobbyStateLabel != null)
            this.currentLobbyStateLabel.setText(this.displayLobbyStatus(this.currentLobbyStatus));
    }

    private void renderNoCurrentLobby() {
        if (this.currentLobbyTitleLabel != null)   this.currentLobbyTitleLabel.setText("No lobby selected");
        if (this.currentLobbyHostLabel != null)    this.currentLobbyHostLabel.setText("Host: -");
        if (this.currentLobbyPlayersLabel != null) this.currentLobbyPlayersLabel.setText("Players: -");
        if (this.currentLobbyStateLabel != null)   this.currentLobbyStateLabel.setText("Waiting");
    }

    private String statusFromPreMatchUpdate(PreMatchUpdatePayload payload) {
        if (payload.event == PreMatchEventType.LOBBY_STARTED) {
            return "STARTING";
        }
        int currentPlayers = payload.players == null ? 0 : payload.players.size();
        return payload.maxPlayers != null && currentPlayers >= payload.maxPlayers ? "READY" : "WAITING";
    }

    private String displayLobbyStatus(String status) {
        if ("WAITING".equalsIgnoreCase(status)) {
            return "Waiting";
        }
        if ("STARTING".equalsIgnoreCase(status)) {
            return "Starting";
        }
        return status == null ? "Waiting" : status;
    }

    private boolean isCurrentLobby(int lobbyId) {
        return this.currentLobbyId != null && this.currentLobbyId == lobbyId;
    }

    private boolean isCurrentUser(String nickname) {
        return nickname != null && nickname.equals(this.nickname);
    }

    private boolean isCurrentUserHost() {
        return this.currentLobbyId != null
                && this.currentLobbyHostNickname != null
                && this.currentLobbyHostNickname.equals(this.nickname);
    }

    private void updateLobbyActions() {
        boolean inLobby = this.currentLobbyId != null;
        boolean isHost = this.isCurrentUserHost();
        boolean isReady = "READY".equalsIgnoreCase(this.currentLobbyStatus);
        this.lobbyActionTitleLabel.setText(inLobby ? "Your table" : "Create table");
        this.lobbyActionSubtitleLabel.setText(inLobby
                ? "You are already seated. Use the table actions below or exit from the lobby list."
                : "Pick a table size from 2 to 5 players and become the host.");
        this.capacityComboBox.setDisable(inLobby);
        this.createLobbyButton.setDisable(inLobby);
        this.startLobbyButton.setDisable(!isHost || !isReady);
        this.exitLobbyButton.setDisable(!inLobby);
    }

    private void runOnFx(Runnable runnable) {
        if (Platform.isFxApplicationThread()) {
            runnable.run();
        } else {
            Platform.runLater(runnable);
        }
    }

    private BoardPayload mockBoard() {
        return new BoardPayload(
                List.of(
                        new CardPayload(1, "EVENT", 1),
                        new CardPayload(4, "HUNTER", 1),
                        new CardPayload(13, "BUILDING", 1),
                        new CardPayload(17, "ARTIST", 1)),
                List.of(
                        new CardPayload(62, "GATHERER", 2),
                        new CardPayload(70, "INVENTION", 2),
                        new CardPayload(81, "SHAMAN", 2),
                        new CardPayload(99, "BUILDING", 3)),
                List.of(
                        new OfferSpacePayload("A", 2, 0, null),
                        new OfferSpacePayload("B", 1, 1, "Ada"),
                        new OfferSpacePayload("C", 0, 2, null)),
                List.of(
                        new StartingBlockPayload(1, 2, 0, this.nickname),
                        new StartingBlockPayload(2, 1, 1, null),
                        new StartingBlockPayload(3, 0, 2, "Nico"))
        );
    }

    private List<PlayerPayload> mockPlayersForRanking() {
        return List.of(
                new PlayerPayload(this.nickname, 5, 32, "A", new TribePayload(
                        List.of(new CardPayload(4, "HUNTER", 1), new CardPayload(17, "ARTIST", 1)),
                        List.of(new CardPayload(70, "INVENTION", 2))
                )),
                new PlayerPayload("Ada", 4, 39, "B", new TribePayload(List.of(), List.of())),
                new PlayerPayload("Nico", 7, 25, "C", new TribePayload(List.of(), List.of())),
                new PlayerPayload("Lia", 3, 29, "D", new TribePayload(List.of(), List.of()))
        );
    }

    @FunctionalInterface
    private interface ServerCall {
        void run();
    }
}
