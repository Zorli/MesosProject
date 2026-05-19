package it.polimi.ingsoft.mesos.client.view.gui;

import it.polimi.ingsoft.mesos.client.serverproxy.ServerProxy;
import it.polimi.ingsoft.mesos.client.view.ClientView;
import it.polimi.ingsoft.mesos.shared.payload.servertoclient.error.ErrorPayload;
import it.polimi.ingsoft.mesos.shared.payload.servertoclient.game.BoardPayload;
import it.polimi.ingsoft.mesos.shared.payload.servertoclient.game.CardPayload;
import it.polimi.ingsoft.mesos.shared.payload.servertoclient.game.GamePayload;
import it.polimi.ingsoft.mesos.shared.payload.servertoclient.game.OfferSpacePayload;
import it.polimi.ingsoft.mesos.shared.payload.servertoclient.game.PlayerPayload;
import it.polimi.ingsoft.mesos.shared.payload.servertoclient.game.StartingBlockPayload;
import it.polimi.ingsoft.mesos.shared.payload.servertoclient.game.TurnOrderPayload;
import it.polimi.ingsoft.mesos.shared.payload.servertoclient.game.TurnStatePayload;
import it.polimi.ingsoft.mesos.shared.payload.servertoclient.lobby.LobbyEventType;
import it.polimi.ingsoft.mesos.shared.payload.servertoclient.lobby.LobbyUpdatePayload;
import it.polimi.ingsoft.mesos.shared.payload.servertoclient.prelobby.LobbyListPayload;
import it.polimi.ingsoft.mesos.shared.payload.servertoclient.prelobby.LobbySummaryPayload;
import javafx.animation.KeyFrame;
import javafx.animation.PauseTransition;
import javafx.animation.Timeline;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.geometry.Insets;
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
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.shape.Circle;
import javafx.stage.Stage;
import javafx.util.Duration;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * JavaFX implementation of the client view contract.
 *
 * <p>GUI actions go through {@link ServerProxy}, while server updates arrive through the
 * {@link ClientView} methods.
 */
public class GUIController implements ClientView {
    private static final String FXML_RESOURCE = "gui.fxml";
    private static final String CSS_RESOURCE = "mesos-gui.css";
    private static final String CARD_RESOURCE_PATTERN = "/it/polimi/ingsoft/mesos/client/assets/cards/%d.jpg";
    private static final String OFFER_PATH_RESOURCE_PATTERN = "/it/polimi/ingsoft/mesos/client/assets/offerPath/%s.png";
    private static final String TOTEM_RESOURCE_PATTERN = "/it/polimi/ingsoft/mesos/client/assets/totems/%s.png";
    private static final AtomicBoolean JAVAFX_STARTED = new AtomicBoolean(false);

    private ServerProxy serverProxy;
    private Stage stage;
    private String nickname = "Guest";
    private GamePayload lastGamePayload;
    private LobbyListPayload lastLobbyListPayload;
    private LobbyUpdatePayload lastLobbyUpdatePayload;
    private PauseTransition authRedirectDelay;
    private Timeline lobbyRefreshTimer;
    private Integer currentLobbyId;
    private String currentLobbyHostNickname;
    private String currentLobbyStatus = "WAITING";
    private String localDraftTurnKey;
    private int localUpperChoicesUsed;
    private int localLowerChoicesUsed;
    private String pendingDraftRow;
    private boolean returnedToLobbyAfterMatch;

    @FXML private StackPane pages;
    @FXML private HBox topBar;
    @FXML private StackPane loginPage;
    @FXML private VBox lobbyPage;
    @FXML private VBox lobbyFormPage;
    @FXML private ScrollPane gamePage;
    @FXML private VBox endPage;
    @FXML private TextField nicknameField;
    @FXML private Button loginButton;
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
    @FXML private Label capacityLabel;
    @FXML private ComboBox<Integer> capacityComboBox;
    @FXML private VBox lobbyListContainer;
    @FXML private Button openCreateTableButton;
    @FXML private Button backToLobbyListButton;
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
    @FXML private VBox tribeCardsContainer;
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
        this.renderGameSkeleton();
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

        this.nicknameField.setDisable(true);
        this.loginButton.setDisable(true);
        this.loginStatusLabel.setText("Signing in...");
        this.callServer(() -> this.serverProxy.authenticate(typedNickname));
    }

    @FXML
    private void handleOpenCreateTable() {
        this.renderNoCurrentLobby();
        this.updateLobbyActions();
        this.showPage(this.lobbyFormPage);
    }

    @FXML
    private void handleBackToLobbyList() {
        if (this.currentLobbyId != null) {
            return;
        }
        this.renderNoCurrentLobby();
        this.updateLobbyActions();
        this.showPage(this.lobbyPage);
        this.requestLobbyList();
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
        this.moveTotemFromBoard("A");
    }

    @FXML
    private void handleMoveTotemB() {
        this.moveTotemFromBoard("B");
    }

    @FXML
    private void handleMoveTotemC() {
        this.moveTotemFromBoard("C");
    }

    @FXML
    private void handleChooseCard() {
        this.updateActionHint();
    }

    @FXML
    private void handleExtraDraw() {
        this.updateActionHint();
    }

    @FXML
    private void handleBackToLobby() {
        this.resetAfterMatch();
        this.showPage(this.lobbyPage);
        this.renderLobbyLoading();
        this.requestLobbyList();
    }

    @Override
    public void showError(ErrorPayload payload) {
        this.runOnFx(() -> {
            String message = payload == null ? "Errore sconosciuto." : payload.message;
            if (this.isConnectionClosed(payload)) {
                this.cancelAuthenticatedRedirect();
                if (this.lobbyRefreshTimer != null) {
                    this.lobbyRefreshTimer.stop();
                }
            }
            if (this.loginPage.isVisible()) {
                this.loginStatusLabel.setText(message);
                this.nicknameField.setDisable(false);
                this.loginButton.setDisable(false);
            } else if (this.gamePage.isVisible()) {
                this.rollbackPendingDraftChoice();
                this.selectedCardLabel.setText(message);
            } else if (this.lobbyFormPage.isVisible()) {
                this.lobbyActionSubtitleLabel.setText(message);
            } else {
                this.lobbyStatusLabel.setText(message);
            }
        });
    }

    @Override
    public void showLobbyUpdate(LobbyUpdatePayload payload) {
        this.runOnFx(() -> {
            this.lastLobbyUpdatePayload = payload;
            if (payload == null) {
                return;
            }
            String message = payload.message == null ? this.describeLobbyEvent(payload.event) : payload.message;
            if (payload.event == LobbyEventType.AUTHENTICATED) {
                if (payload.nickname != null && !payload.nickname.isBlank()) {
                    this.nickname = payload.nickname;
                    this.sessionNicknameLabel.setText(this.nickname);
                }
                this.loginStatusLabel.setText(message + "\nLoading lobby...");
                this.scheduleAuthenticatedRedirect();
                return;
            }
            if (payload.event != LobbyEventType.LOBBY_LEFT || this.isCurrentUser(payload.nickname)) {
                this.lobbyStatusLabel.setText(message);
            }
            this.renderCurrentLobby(payload);
            this.updateLobbyActions();
            if (payload.event == LobbyEventType.LOBBY_CREATED
                    || payload.event == LobbyEventType.LOBBY_JOINED
                    || payload.event == LobbyEventType.LOBBY_LEFT) {
                if (this.currentLobbyId == null) {
                    this.showPage(this.lobbyPage);
                    this.requestLobbyList();
                } else {
                    this.showPage(this.lobbyFormPage);
                }
            }
            if (payload.event == LobbyEventType.LOBBY_STARTED) {
                this.returnedToLobbyAfterMatch = false;
                this.cancelAuthenticatedRedirect();
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

            if (this.lastLobbyUpdatePayload != null
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

            if (!this.gamePage.isVisible() && !this.endPage.isVisible() && !this.lobbyFormPage.isVisible()) {
                this.showPage(this.lobbyPage);
            }
        });
    }

    @Override
    public void showGameUpdate(GamePayload payload) {
        this.runOnFx(() -> {
            this.cancelAuthenticatedRedirect();
            if (this.returnedToLobbyAfterMatch && this.isFinalScoring(payload)) {
                return;
            }
            if (payload != null && !this.isFinalScoring(payload)) {
                this.returnedToLobbyAfterMatch = false;
            }
            this.lastGamePayload = payload;
            if (payload == null) {
                this.matchTitleLabel.setText("Match");
                this.matchMetaLabel.setText("Waiting for server");
                this.turnLabel.setText("No match update received");
                this.renderGameSkeleton();
                this.showPage(this.gamePage);
                return;
            }
            this.updateLocalDraftTurn(payload);
            this.pendingDraftRow = null;
            this.matchTitleLabel.setText("Match #" + payload.id);
            this.matchMetaLabel.setText("Era " + this.displayEra(payload.currentEra) + " / " + payload.status);
            this.turnLabel.setText(this.renderTurn(payload.turnOrder, payload.turnState));
            this.renderBoard(payload.board);
            this.renderPlayers(payload.players);
            this.renderTribe(payload.players);
            this.updateActionHint();
            if (this.isFinalScoring(payload)) {
                this.renderFinalRanking(payload.players);
                this.showPage(this.endPage);
            } else {
                this.showPage(this.gamePage);
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
        if (board == null) {
            this.renderBoardSkeleton();
            return;
        }
        BoardPayload boardPayload = board;
        this.renderCardRow(this.upperRowContainer, boardPayload.upperRow, "upper-card", "UPPER");
        this.renderCardRow(this.lowerRowContainer, boardPayload.lowerRow, "lower-card", "LOWER");
        this.renderOfferPath(boardPayload.offerSpaces, boardPayload.startingBlocks);
    }

    private void renderCardRow(HBox container, List<CardPayload> cards, String styleClass, String row) {
        container.getChildren().clear();
        List<CardPayload> cardPayloads = cards == null ? List.of() : cards;
        if (cardPayloads.isEmpty()) {
            container.getChildren().add(this.emptyState("Empty market", "Waiting for server."));
            return;
        }
        boolean rowEnabled = this.canChooseFromRow(row);
        for (CardPayload card : cardPayloads) {
            StackPane cardNode = this.cardNode(card, styleClass);
            boolean cardEnabled = rowEnabled && !this.isEventCard(card);
            cardNode.setDisable(!cardEnabled);
            if (!cardEnabled) {
                cardNode.getStyleClass().add("disabled-card");
            }
            cardNode.setOnMouseClicked(event -> this.chooseCardFromBoard(card.id, row));
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

    private void renderOfferPath(List<OfferSpacePayload> offers, List<StartingBlockPayload> starts) {
        this.offerPathContainer.getChildren().clear();
        List<StartingBlockPayload> startingBlocks = starts == null ? List.of() : starts;
        if (!startingBlocks.isEmpty()) {
            StackPane tile = this.offerPathTile(
                    "offer_" + startingBlocks.size(),
                    "Starting tile",
                    startingBlocks.size() + " players",
                    null,
                    startingBlocks
            );
            tile.setMouseTransparent(true);
            this.offerPathContainer.getChildren().add(tile);
        }
        List<OfferSpacePayload> offerSpaces = offers == null ? List.of() : offers;
        for (OfferSpacePayload offer : offerSpaces) {
            StackPane tile = this.offerPathTile(
                    offer.code,
                    offer.code,
                    offer.topRowCardsToTake + " upper / " + offer.bottomRowCardsToTake + " lower",
                    offer.occupiedByNickname,
                    List.of()
            );
            boolean tileEnabled = this.canMoveTotemTo(offer);
            tile.setDisable(!tileEnabled);
            if (!tileEnabled) {
                tile.getStyleClass().add("disabled-tile");
            }
            tile.setOnMouseClicked(event -> this.moveTotemFromBoard(offer.code));
            this.offerPathContainer.getChildren().add(tile);
        }
    }

    private StackPane offerPathTile(
            String imageKey,
            String fallbackTitle,
            String fallbackDetail,
            String occupant,
            List<StartingBlockPayload> startingBlocks
    ) {
        StackPane tile = new StackPane();
        tile.getStyleClass().add("offer-image-tile");

        Image image = this.resourceImage(String.format(OFFER_PATH_RESOURCE_PATTERN, imageKey));
        if (image == null) {
            tile.getChildren().add(this.tile(fallbackTitle, fallbackDetail, occupant));
        } else {
            ImageView imageView = new ImageView(image);
            imageView.getStyleClass().add("offer-path-image");
            imageView.setFitWidth(148);
            imageView.setFitHeight(214);
            imageView.setPreserveRatio(true);
            tile.getChildren().add(imageView);
            if (!startingBlocks.isEmpty()) {
                this.addStartingOrderTotems(tile, startingBlocks);
            } else {
                this.addTotemOverlay(tile, occupant);
            }
        }

        return tile;
    }

    private Image resourceImage(String resource) {
        InputStream stream = GUIController.class.getResourceAsStream(resource);
        if (stream == null) {
            return null;
        }
        return new Image(stream);
    }

    private void addTotemOverlay(StackPane tile, String occupant) {
        if (occupant == null || occupant.isBlank()) {
            return;
        }

        Image totemImage = this.totemImageForPlayer(occupant);
        if (totemImage == null) {
            Label label = new Label(occupant);
            label.getStyleClass().add("offer-occupant-name");
            StackPane.setAlignment(label, Pos.TOP_CENTER);
            StackPane.setMargin(label, new Insets(14, 8, 0, 8));
            tile.getChildren().add(label);
            return;
        }

        ImageView totemView = new ImageView(totemImage);
        totemView.getStyleClass().add("totem-on-offer");
        totemView.setFitWidth(32);
        totemView.setFitHeight(48);
        totemView.setPreserveRatio(true);
        StackPane.setAlignment(totemView, Pos.TOP_CENTER);
        StackPane.setMargin(totemView, new Insets(14, 0, 0, 0));
        tile.getChildren().add(totemView);
    }

    private void addStartingOrderTotems(StackPane tile, List<StartingBlockPayload> startingBlocks) {
        List<StartingBlockPayload> orderedBlocks = startingBlocks.stream()
                .sorted(Comparator.comparingInt(block -> block.position))
                .toList();
        double[] topOffsets = this.startingTotemTopOffsets(orderedBlocks.size());
        int limit = Math.min(orderedBlocks.size(), topOffsets.length);
        double totemWidth = orderedBlocks.size() == 5 ? 17 : 24;
        double totemHeight = orderedBlocks.size() == 5 ? 24 : 34;
        for (int index = 0; index < limit; index++) {
            StartingBlockPayload block = orderedBlocks.get(index);
            if (block.occupiedByNickname == null || block.occupiedByNickname.isBlank()) {
                continue;
            }

            Image totemImage = this.totemImageForPlayer(block.occupiedByNickname);
            if (totemImage == null) {
                continue;
            }

            ImageView totemView = new ImageView(totemImage);
            totemView.getStyleClass().add("starting-order-totem");
            totemView.setFitWidth(totemWidth);
            totemView.setFitHeight(totemHeight);
            totemView.setPreserveRatio(true);
            StackPane.setAlignment(totemView, Pos.TOP_CENTER);
            StackPane.setMargin(totemView, new Insets(topOffsets[index], 0, 0, 0));
            tile.getChildren().add(totemView);
        }
    }

    private double[] startingTotemTopOffsets(int slots) {
        return switch (slots) {
            case 2 -> new double[]{48, 86};
            case 3 -> new double[]{37, 75, 113};
            case 4 -> new double[]{25, 63, 101, 139};
            default -> new double[]{23, 58, 93, 128, 163};
        };
    }

    private Image totemImageForPlayer(String nickname) {
        String totem = this.totemForPlayer(nickname);
        if (totem == null || totem.isBlank()) {
            return null;
        }
        return this.resourceImage(String.format(
                TOTEM_RESOURCE_PATTERN,
                totem.toLowerCase(Locale.ROOT)
        ));
    }

    private String totemForPlayer(String nickname) {
        if (nickname == null || this.lastGamePayload == null || this.lastGamePayload.players == null) {
            return null;
        }
        return this.lastGamePayload.players.stream()
                .filter(player -> nickname.equals(player.nickname))
                .map(player -> player.totem)
                .filter(Objects::nonNull)
                .findFirst()
                .orElse(null);
    }

    private VBox tile(String title, String detail, String occupant) {
        VBox tile = new VBox(4);
        tile.getStyleClass().add("offer-tile");
        Label titleLabel = new Label(title);
        titleLabel.getStyleClass().add("offer-title");
        Label detailLabel = new Label(detail);
        detailLabel.getStyleClass().add("muted");
        Label occupantLabel = new Label(occupant == null ? "Free" : occupant);
        occupantLabel.getStyleClass().add(occupant == null ? "free-pill" : "occupied-pill");
        tile.getChildren().addAll(titleLabel, detailLabel, occupantLabel);
        return tile;
    }

    private void chooseCardFromBoard(int cardId, String row) {
        if (!this.isCurrentUsersTurn()) {
            this.selectedCardLabel.setText("Wait for your turn: " + this.currentTurnPlayerName() + " is playing.");
            return;
        }
        if (this.isRoundPhase("EXTRA_DRAWS")) {
            this.selectedCardLabel.setText("Extra draw requested for card " + cardId + ".");
            this.callServer(() -> this.serverProxy.extraDraw(cardId));
            return;
        }
        if (this.isRoundPhase("PLAYER_DRAWS")) {
            this.registerLocalCardChoice(row);
            this.pendingDraftRow = row;
            this.selectedCardLabel.setText("Card " + cardId + " selected.");
            if (this.lastGamePayload != null) {
                this.renderBoard(this.lastGamePayload.board);
                this.updateActionHint();
            }
            this.callServer(() -> this.serverProxy.chooseCard(cardId));
            return;
        }
        this.updateActionHint();
    }

    private boolean isEventCard(CardPayload card) {
        return card != null
                && card.type != null
                && card.type.toUpperCase().contains("EVENT");
    }

    private boolean isFinalScoring(GamePayload payload) {
        return payload != null
                && payload.turnOrder != null
                && "FINAL_SCORING".equals(payload.turnOrder.currentRoundPhase);
    }

    private void moveTotemFromBoard(String tile) {
        if (!this.isCurrentUsersTurn()) {
            this.selectedCardLabel.setText("Wait for your turn: " + this.currentTurnPlayerName() + " is playing.");
            return;
        }
        if (!this.isRoundPhase("PLACING_TOTEMS")) {
            this.updateActionHint();
            return;
        }
        this.selectedCardLabel.setText("Totem sent to " + tile + ".");
        this.callServer(() -> this.serverProxy.moveTotem(tile));
    }

    private boolean canMoveTotemTo(OfferSpacePayload offer) {
        return offer != null
                && offer.occupiedByNickname == null
                && this.isCurrentUsersTurn()
                && this.isRoundPhase("PLACING_TOTEMS");
    }

    private boolean canChooseFromRow(String row) {
        if (!this.isCurrentUsersTurn()) {
            return false;
        }
        if (this.isRoundPhase("EXTRA_DRAWS")) {
            return true;
        }
        if (!this.isRoundPhase("PLAYER_DRAWS")) {
            return false;
        }
        OfferSpacePayload currentOffer = this.currentPlayersOfferSpace();
        if (currentOffer == null) {
            return true;
        }
        if ("UPPER".equals(row)) {
            return currentOffer.topRowCardsToTake - this.localUpperChoicesUsed > 0;
        }
        if ("LOWER".equals(row)) {
            return currentOffer.bottomRowCardsToTake - this.localLowerChoicesUsed > 0;
        }
        return false;
    }

    private void updateLocalDraftTurn(GamePayload payload) {
        String newKey = this.draftTurnKey(payload);
        if (!Objects.equals(this.localDraftTurnKey, newKey)) {
            this.localDraftTurnKey = newKey;
            this.localUpperChoicesUsed = 0;
            this.localLowerChoicesUsed = 0;
        }
    }

    private String draftTurnKey(GamePayload payload) {
        if (payload == null || payload.turnOrder == null) {
            return null;
        }
        return payload.id + "|"
                + payload.turnOrder.roundNumber + "|"
                + payload.turnOrder.currentRoundPhase + "|"
                + payload.turnOrder.currentPlayerNickname;
    }

    private void registerLocalCardChoice(String row) {
        if ("UPPER".equals(row)) {
            this.localUpperChoicesUsed++;
        } else if ("LOWER".equals(row)) {
            this.localLowerChoicesUsed++;
        }
    }

    private void rollbackPendingDraftChoice() {
        if (this.pendingDraftRow == null) {
            return;
        }
        if ("UPPER".equals(this.pendingDraftRow) && this.localUpperChoicesUsed > 0) {
            this.localUpperChoicesUsed--;
        } else if ("LOWER".equals(this.pendingDraftRow) && this.localLowerChoicesUsed > 0) {
            this.localLowerChoicesUsed--;
        }
        this.pendingDraftRow = null;
        if (this.lastGamePayload != null) {
            this.renderBoard(this.lastGamePayload.board);
        }
    }

    private OfferSpacePayload currentPlayersOfferSpace() {
        if (this.lastGamePayload == null
                || this.lastGamePayload.board == null
                || this.lastGamePayload.board.offerSpaces == null
                || this.lastGamePayload.turnOrder == null) {
            return null;
        }
        String currentPlayer = this.lastGamePayload.turnOrder.currentPlayerNickname;
        if (currentPlayer == null) {
            return null;
        }
        return this.lastGamePayload.board.offerSpaces.stream()
                .filter(offer -> currentPlayer.equals(offer.occupiedByNickname))
                .findFirst()
                .orElse(null);
    }

    private boolean isCurrentUsersTurn() {
        return this.lastGamePayload != null
                && this.lastGamePayload.turnOrder != null
                && this.nickname.equals(this.lastGamePayload.turnOrder.currentPlayerNickname);
    }

    private boolean isRoundPhase(String phase) {
        return this.lastGamePayload != null
                && this.lastGamePayload.turnOrder != null
                && phase.equals(this.lastGamePayload.turnOrder.currentRoundPhase);
    }

    private String currentTurnPlayerName() {
        if (this.lastGamePayload == null
                || this.lastGamePayload.turnOrder == null
                || this.lastGamePayload.turnOrder.currentPlayerNickname == null) {
            return "another player";
        }
        return this.lastGamePayload.turnOrder.currentPlayerNickname;
    }

    private void updateActionHint() {
        if (this.selectedCardLabel == null) {
            return;
        }
        if (this.lastGamePayload == null || this.lastGamePayload.turnOrder == null) {
            this.selectedCardLabel.setText("Waiting for match data.");
            return;
        }
        if (!this.isCurrentUsersTurn()) {
            this.selectedCardLabel.setText(this.currentTurnPlayerName() + "'s turn: your actions are paused.");
            return;
        }
        if (this.isRoundPhase("PLACING_TOTEMS")) {
            this.selectedCardLabel.setText("Your turn: click a free offer-path space.");
            return;
        }
        if (this.isRoundPhase("PLAYER_DRAWS")) {
            OfferSpacePayload currentOffer = this.currentPlayersOfferSpace();
            int upper = currentOffer == null ? 0 : Math.max(0, currentOffer.topRowCardsToTake - this.localUpperChoicesUsed);
            int lower = currentOffer == null ? 0 : Math.max(0, currentOffer.bottomRowCardsToTake - this.localLowerChoicesUsed);
            this.selectedCardLabel.setText("Your turn: choose cards from the market. Upper: "
                    + upper + " | lower: " + lower + ".");
            return;
        }
        if (this.isRoundPhase("EXTRA_DRAWS")) {
            this.selectedCardLabel.setText("Extra draw: click an available card.");
            return;
        }
        this.selectedCardLabel.setText("Phase " + this.lastGamePayload.turnOrder.currentRoundPhase + " in progress.");
    }

    private void renderPlayers(List<PlayerPayload> players) {
        this.playerListContainer.getChildren().clear();
        if (players == null || players.isEmpty()) {
            this.renderPlayersSkeleton();
            return;
        }
        List<PlayerPayload> playerPayloads = players;
        for (PlayerPayload player : playerPayloads) {
            HBox row = new HBox(12);
            row.getStyleClass().add("player-row");
            if (this.nickname.equals(player.nickname)) {
                row.getStyleClass().add("current-user-row");
            }
            if (this.lastGamePayload != null
                    && this.lastGamePayload.turnOrder != null
                    && player.nickname.equals(this.lastGamePayload.turnOrder.currentPlayerNickname)) {
                row.getStyleClass().add("active-player-row");
            }
            if (!player.isConnected) {
                row.getStyleClass().add("disconnected-player-row");
            }
            Label name = new Label(player.nickname);
            name.getStyleClass().add("player-name");
            Label stats = new Label(player.food + " food  |  " + player.prestigePoints + " pp  |  totem " + player.totem);
            stats.getStyleClass().add("muted");
            Label connectionState = new Label(player.isConnected ? "connected" : "disconnected");
            connectionState.getStyleClass().add(player.isConnected ? "connected-pill" : "disconnected-pill");
            row.getChildren().addAll(name, stats, connectionState);
            if (player.nickname.equals(this.currentTurnPlayerName())) {
                Label turn = new Label("turn");
                turn.getStyleClass().add("micro-pill");
                row.getChildren().add(turn);
            }
            this.playerListContainer.getChildren().add(row);
        }
    }

    private void renderTribe(List<PlayerPayload> players) {
        this.tribeCardsContainer.getChildren().clear();
        if (players == null || players.isEmpty()) {
            this.renderTribeSkeleton();
            return;
        }
        List<PlayerPayload> playerPayloads = players;
        for (PlayerPayload player : playerPayloads) {
            this.tribeCardsContainer.getChildren().add(this.playerTribeSection(player));
        }
    }

    private VBox playerTribeSection(PlayerPayload player) {
        VBox section = new VBox(8);
        section.getStyleClass().add("player-tribe-section");

        HBox header = new HBox(8);
        header.setAlignment(Pos.CENTER_LEFT);
        Label title = new Label(player.nickname + "'s tribe");
        title.getStyleClass().add("panel-title");
        Label details = new Label(player.food + " food  |  " + player.prestigePoints + " pp  |  " + player.totem);
        details.getStyleClass().add("muted");
        header.getChildren().addAll(title, details);

        HBox cardsRow = new HBox(10);
        cardsRow.getStyleClass().add("card-row");
        List<CardPayload> cards = new ArrayList<>();
        if (player.tribe != null) {
            if (player.tribe.characters != null) {
                cards.addAll(player.tribe.characters);
            }
            if (player.tribe.buildings != null) {
                cards.addAll(player.tribe.buildings);
            }
        }
        if (cards.isEmpty()) {
            cardsRow.getChildren().add(this.emptyState("No personal cards", "This player has no tribe cards yet."));
        } else {
            cards.stream()
                    .map(card -> this.cardNode(card, "tribe-card"))
                    .forEach(cardsRow.getChildren()::add);
        }

        ScrollPane cardsScroll = new ScrollPane(cardsRow);
        cardsScroll.setFitToHeight(true);
        cardsScroll.setFitToWidth(false);
        cardsScroll.setHbarPolicy(ScrollPane.ScrollBarPolicy.AS_NEEDED);
        cardsScroll.setVbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        cardsScroll.getStyleClass().add("row-scroll");

        section.getChildren().addAll(header, cardsScroll);
        return section;
    }

    private void renderFinalRanking(List<PlayerPayload> players) {
        this.finalRankingContainer.getChildren().clear();
        if (players == null || players.isEmpty()) {
            this.finalRankingContainer.getChildren().add(
                    this.emptyState("Final ranking unavailable", "Waiting for the final match payload."));
            return;
        }
        List<PlayerPayload> rankedPlayers = new ArrayList<>(players);
        rankedPlayers.sort(Comparator
                .comparingInt((PlayerPayload player) -> player.prestigePoints)
                .thenComparingInt(player -> player.food)
                .reversed());
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

    private void renderGameSkeleton() {
        this.matchTitleLabel.setText("Match");
        this.matchMetaLabel.setText("Syncing");
        this.turnLabel.setText("Loading");
        this.selectedCardLabel.setText("The board will appear as soon as the first update arrives.");
        this.renderBoardSkeleton();
        this.renderPlayersSkeleton();
        this.renderTribeSkeleton();
        this.renderFinalRankingSkeleton();
    }

    private void renderBoardSkeleton() {
        this.upperRowContainer.getChildren().clear();
        this.lowerRowContainer.getChildren().clear();
        this.offerPathContainer.getChildren().clear();
        for (int index = 0; index < 4; index++) {
            this.upperRowContainer.getChildren().add(this.skeletonCard());
            this.lowerRowContainer.getChildren().add(this.skeletonCard());
        }
        for (int index = 0; index < 6; index++) {
            this.offerPathContainer.getChildren().add(this.skeletonTile());
        }
    }

    private void renderPlayersSkeleton() {
        this.playerListContainer.getChildren().clear();
        for (int index = 0; index < 4; index++) {
            HBox row = new HBox(12);
            row.getStyleClass().addAll("player-row", "skeleton-row");
            row.getChildren().addAll(this.skeletonLine(130), this.skeletonLine(210));
            this.playerListContainer.getChildren().add(row);
        }
    }

    private void renderTribeSkeleton() {
        this.tribeCardsContainer.getChildren().clear();
        for (int sectionIndex = 0; sectionIndex < 2; sectionIndex++) {
            VBox section = new VBox(8);
            section.getStyleClass().add("player-tribe-section");
            HBox header = new HBox(8);
            header.getChildren().addAll(this.skeletonLine(130), this.skeletonLine(190));
            HBox cards = new HBox(10);
            cards.getStyleClass().add("card-row");
            for (int cardIndex = 0; cardIndex < 3; cardIndex++) {
                cards.getChildren().add(this.skeletonCard());
            }
            section.getChildren().addAll(header, cards);
            this.tribeCardsContainer.getChildren().add(section);
        }
    }

    private void renderFinalRankingSkeleton() {
        this.finalRankingContainer.getChildren().clear();
        for (int index = 0; index < 4; index++) {
            HBox row = new HBox(16);
            row.getStyleClass().addAll("ranking-row", "skeleton-row");
            row.getChildren().addAll(this.skeletonLine(28), this.skeletonLine(180), this.skeletonLine(72));
            this.finalRankingContainer.getChildren().add(row);
        }
    }

    private StackPane skeletonCard() {
        StackPane card = new StackPane();
        card.getStyleClass().addAll("card-frame", "skeleton-card");
        card.setDisable(true);
        return card;
    }

    private VBox skeletonTile() {
        VBox tile = new VBox(8);
        tile.getStyleClass().addAll("offer-tile", "skeleton-tile");
        tile.setDisable(true);
        tile.getChildren().addAll(this.skeletonLine(60), this.skeletonLine(86), this.skeletonLine(50));
        return tile;
    }

    private Region skeletonLine(double width) {
        Region line = new Region();
        line.getStyleClass().add("skeleton-line");
        line.setMinWidth(width);
        line.setPrefWidth(width);
        line.setMaxWidth(width);
        line.setMinHeight(12);
        line.setPrefHeight(12);
        line.setMaxHeight(12);
        return line;
    }

    private String renderTurn(TurnOrderPayload order, TurnStatePayload state) {
        String phase = state == null ? "setup" : state.phase;
        if (order == null) {
            return "Phase " + phase + " | waiting for turn";
        }
        return "Round " + order.roundNumber + " | " + order.currentRoundPhase
                + " | " + order.currentPlayerNickname + "'s turn";
    }

    private int displayEra(int currentEra) {
        return currentEra + 1;
    }

    private String describeLobbyEvent(LobbyEventType event) {
        if (event == null) {
            return "Aggiornamento lobby ricevuto.";
        }
        return switch (event) {
            case AUTHENTICATED -> "Login successful.";
            case LOBBY_CREATED -> "Lobby creata.";
            case LOBBY_JOINED -> "Lobby raggiunta.";
            case LOBBY_LEFT -> "Sei uscito dalla lobby.";
            case LOBBY_STARTED -> "Match started.";
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
            this.authRedirectDelay = null;
            if (!this.shouldOpenLobbyAfterAuthentication()) {
                return;
            }
            this.showPage(this.lobbyPage);
            this.requestLobbyList();
        });
        this.authRedirectDelay.play();
    }

    private void cancelAuthenticatedRedirect() {
        if (this.authRedirectDelay == null) {
            return;
        }
        this.authRedirectDelay.stop();
        this.authRedirectDelay = null;
    }

    private boolean shouldOpenLobbyAfterAuthentication() {
        return this.loginPage.isVisible()
                && !this.gamePage.isVisible()
                && !this.endPage.isVisible()
                && this.currentLobbyId == null;
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

    private boolean isConnectionClosed(ErrorPayload payload) {
        return payload != null && "CONNECTION_CLOSED".equals(payload.code);
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

    private void renderLobbyLoading() {
        this.lobbyCountLabel.setText("Loading");
        this.lobbyListContainer.getChildren().clear();
        for (int index = 0; index < 3; index++) {
            HBox row = new HBox(16);
            row.getStyleClass().addAll("lobby-card", "skeleton-row");
            row.getChildren().addAll(this.skeletonLine(170), this.skeletonLine(120), this.skeletonLine(74));
            this.lobbyListContainer.getChildren().add(row);
        }
    }

    private void renderCurrentLobby(LobbyUpdatePayload payload) {
        if (payload == null) {
            return;
        }
        if (payload.event == LobbyEventType.LOBBY_LEFT) {
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
        if (payload.event != LobbyEventType.LOBBY_CREATED
                && payload.event != LobbyEventType.LOBBY_JOINED
                && payload.event != LobbyEventType.LOBBY_STARTED) {
            return;
        }

        this.renderCurrentLobbySnapshot(payload);
    }

    private void renderCurrentLobbySnapshot(LobbyUpdatePayload payload) {
        this.currentLobbyId = payload.lobbyId;
        this.currentLobbyHostNickname = payload.hostNickname;
        this.currentLobbyStatus = this.statusFromLobbyUpdate(payload);
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
        if (this.currentLobbyTitleLabel != null) this.currentLobbyTitleLabel.setText("No lobby selected");
        if (this.currentLobbyHostLabel != null) this.currentLobbyHostLabel.setText("Host: -");
        if (this.currentLobbyPlayersLabel != null) this.currentLobbyPlayersLabel.setText("Players: -");
        if (this.currentLobbyStateLabel != null) this.currentLobbyStateLabel.setText("Waiting");
    }

    private void resetAfterMatch() {
        this.lastGamePayload = null;
        this.localDraftTurnKey = null;
        this.localUpperChoicesUsed = 0;
        this.localLowerChoicesUsed = 0;
        this.pendingDraftRow = null;
        this.currentLobbyId = null;
        this.currentLobbyHostNickname = null;
        this.currentLobbyStatus = "WAITING";
        this.lastLobbyUpdatePayload = null;
        this.lastLobbyListPayload = null;
        this.returnedToLobbyAfterMatch = true;
        this.renderNoCurrentLobby();
        this.updateLobbyActions();
        this.renderGameSkeleton();
        this.lobbyStatusLabel.setText("Match complete. Choose a new lobby or create one.");
    }

    private String statusFromLobbyUpdate(LobbyUpdatePayload payload) {
        if (payload.event == LobbyEventType.LOBBY_STARTED) {
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
        this.capacityLabel.setVisible(!inLobby);
        this.capacityLabel.setManaged(!inLobby);
        this.capacityComboBox.setVisible(!inLobby);
        this.capacityComboBox.setManaged(!inLobby);
        this.createLobbyButton.setVisible(!inLobby);
        this.createLobbyButton.setManaged(!inLobby);
        this.capacityComboBox.setDisable(inLobby);
        this.createLobbyButton.setDisable(inLobby);
        this.startLobbyButton.setDisable(!isHost || !isReady);
        this.exitLobbyButton.setDisable(!inLobby);
        this.openCreateTableButton.setDisable(inLobby);
        this.backToLobbyListButton.setDisable(inLobby);
    }


    //remember that this code will be runned both by the thread that is charge of displaying the UI
    //(platform thread) and by the thread that is charge of receiving the server updates (server listener thread),
    // so we need to make sure that any update to the UI is runned on the platform thread
    private void runOnFx(Runnable runnable) {
        if (Platform.isFxApplicationThread()) {
            runnable.run();
        } else {
            Platform.runLater(runnable);
        }
    }

    @FunctionalInterface
    private interface ServerCall {
        void run();
    }
}
