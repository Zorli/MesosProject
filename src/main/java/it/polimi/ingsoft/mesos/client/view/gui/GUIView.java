package it.polimi.ingsoft.mesos.client.view.gui;

import it.polimi.ingsoft.mesos.client.serverproxy.ServerProxy;
import it.polimi.ingsoft.mesos.client.view.ClientView;
import it.polimi.ingsoft.mesos.shared.payload.servertoclient.error.ErrorPayload;
import it.polimi.ingsoft.mesos.shared.payload.servertoclient.game.GamePayload;
import it.polimi.ingsoft.mesos.shared.payload.servertoclient.lobby.LobbyEventType;
import it.polimi.ingsoft.mesos.shared.payload.servertoclient.lobby.LobbyUpdatePayload;
import it.polimi.ingsoft.mesos.shared.payload.servertoclient.prelobby.AuthenticationResultPayload;
import it.polimi.ingsoft.mesos.shared.payload.servertoclient.prelobby.LobbyListPayload;
import javafx.animation.KeyFrame;
import javafx.animation.PauseTransition;
import javafx.animation.Timeline;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.TextField;
import javafx.scene.layout.HBox;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;
import javafx.util.Duration;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * JavaFX implementation of the {@link ClientView} contract.
 *
 * <p>This class is the controller behind {@code gui.fxml}. The FXML file describes the
 * static structure of the window: the top bar, the login page, the lobby list page, the
 * lobby command page, the game page, and the final-result page. Fields annotated with
 * {@link FXML} are injected by {@link FXMLLoader} by matching their names with the
 * {@code fx:id} attributes in the FXML file. Methods annotated with {@link FXML} are
 * event handlers referenced from the FXML file through attributes such as
 * {@code onAction="#handleLogin"}.</p>
 *
 * <p>{@code GUIView} now keeps only the controller responsibilities: FXML field
 * ownership, FXML event handlers, the {@link ClientView} callbacks, the JavaFX
 * lifecycle, page navigation and server-call dispatch. The bulk of the rendering
 * work is delegated to three collaborators:</p>
 * <ul>
 *     <li>{@link LobbyRenderer} for the lobby pages and the current-lobby snapshot.</li>
 *     <li>{@link BoardRenderer} for the game board, the player rows, the tribe
 *         cards, the final ranking and the loading placeholders.</li>
 *     <li>{@link GameTurnController} for the local turn state, the legal-action
 *         rules and the optimistic card-pick counters.</li>
 * </ul>
 *
 * <p>All JavaFX nodes must be read or modified on the JavaFX Application Thread.
 * Button handlers are already executed on that thread, but server updates can arrive
 * from TCP or RMI networking threads. For this reason every public {@link ClientView}
 * update method delegates UI work through {@link #runOnFx(Runnable)}.</p>
 */
public class GUIView implements ClientView {

    private static final String FXML_RESOURCE = "gui.fxml";
    private static final String CSS_RESOURCE = "mesos-gui.css";

    /*
     * JavaFX can be started only once in the JVM. This flag lets the client
     * support both first startup and later GUI openings without calling
     * Platform.startup twice.
     */
    private static final AtomicBoolean JAVAFX_STARTED = new AtomicBoolean(false);

    /*
     * Runtime state owned by the controller. State that belongs only to lobby
     * or turn flows has moved into the collaborators created in initialize().
     */
    /** Network proxy used to communicate with the remote server. */
    private ServerProxy serverProxy;
    /** Current player's nickname, defaults to "Guest". */
    private String nickname = "Guest";
    /** Last game payload received from the server. */
    private GamePayload lastGamePayload;
    /** Transition timer used to delay redirection after a successful login. */
    private PauseTransition authRedirectDelay;
    /** Timeline timer used to periodically auto-refresh the lobby list. */
    private Timeline lobbyRefreshTimer;

    /** Collaborator responsible for rendering lobby-related screens and tables. */
    private LobbyRenderer lobbyRenderer;
    /** Collaborator responsible for rendering the game board, players, and final rankings. */
    private BoardRenderer boardRenderer;
    /** Collaborator responsible for managing turn logic, card pick rules, and client-side validation. */
    private GameTurnController gameTurnController;

    /*
     * Nodes injected from gui.fxml. They live on the controller because the
     * FXMLLoader resolves fx:id attributes by reflection against this object.
     * The collaborators receive the references they need in their constructors.
     */
    /** StackPane container that holds every page of the application as a sibling node. */
    @FXML private StackPane pages;
    /** Top navigation/status bar visible on every page except the login page. */
    @FXML private HBox topBar;
    /** Nickname entry / login screen. */
    @FXML private StackPane loginPage;
    /** Screen that lists all open lobbies and enables searching/refreshing. */
    @FXML private VBox lobbyPage;
    /** Screen used to configure a new lobby or manage a joined one. */
    @FXML private VBox lobbyFormPage;
    /** Screen displaying the active match (board, players, cards, paths). */
    @FXML private ScrollPane gamePage;
    /** Screen displaying the final rankings once a match ends. */
    @FXML private VBox endPage;
    /** Input field where the user enters their nickname. */
    @FXML private TextField nicknameField;
    /** Button clicked to submit the nickname and log in. */
    @FXML private Button loginButton;
    /** Status line displaying login validation errors or server messages. */
    @FXML private Label loginStatusLabel;
    /** Right-aligned pill displaying the authenticated nickname. */
    @FXML private Label sessionNicknameLabel;
    /** Subtitle on the lobby page used to show status and event feedback. */
    @FXML private Label lobbyStatusLabel;
    /** Pill counting the total number of currently open lobbies. */
    @FXML private Label lobbyCountLabel;
    /** Title of the lobby action panel (e.g. "Create table" / "Your table"). */
    @FXML private Label lobbyActionTitleLabel;
    /** Subtitle of the lobby action panel displaying action errors or feedback. */
    @FXML private Label lobbyActionSubtitleLabel;
    /** Label displaying the current lobby ID (e.g. "Lobby #123"). */
    @FXML private Label currentLobbyTitleLabel;
    /** Label displaying the host's nickname. */
    @FXML private Label currentLobbyHostLabel;
    /** Label displaying player count vs capacity (e.g. "Players: 3/4"). */
    @FXML private Label currentLobbyPlayersLabel;
    /** Pill indicating current lobby status (Waiting, Ready, Starting...). */
    @FXML private Label currentLobbyStateLabel;
    /** Text area listing every player currently seated in the lobby. */
    @FXML private Label playersInLobby;
    /** Capacity selector label. */
    @FXML private Label capacityLabel;
    /** Capacity selector ComboBox. */
    @FXML private ComboBox<Integer> capacityComboBox;
    /** Vertical container hosting cards of available lobbies. */
    @FXML private VBox lobbyListContainer;
    /** Button that opens the create lobby screen. */
    @FXML private Button openCreateTableButton;
    /** Button that returns the user to the lobby list. */
    @FXML private Button backToLobbyListButton;
    /** Button clicked to create a lobby with the chosen capacity. */
    @FXML private Button createLobbyButton;
    /** Button clicked by the host to start the game. */
    @FXML private Button startLobbyButton;
    /** Button clicked to exit the current lobby. */
    @FXML private Button exitLobbyButton;
    /** Label showing the current match identifier. */
    @FXML private Label matchTitleLabel;
    /** Label displaying current era and match state. */
    @FXML private Label matchMetaLabel;
    /** Status pill displaying round and active player information. */
    @FXML private Label turnLabel;
    /** Action-hint bar providing instructions for the local player's next move. */
    @FXML private Label selectedCardLabel;
    /** Horizontal container hosting the upper row market cards. */
    @FXML private HBox upperRowContainer;
    /** Horizontal container hosting the lower row market cards. */
    @FXML private HBox lowerRowContainer;
    /** Horizontal container hosting the offer path and starting tiles. */
    @FXML private HBox offerPathContainer;
    /** Vertical container hosting player summary rows. */
    @FXML private VBox playerListContainer;
    /** Vertical container hosting tribe cards for all players. */
    @FXML private VBox tribeCardsContainer;
    /** Vertical container hosting final ranking slots. */
    @FXML private VBox finalRankingContainer;
    /** ScrollPane wrapper around the lobby list. */
    @FXML private ScrollPane lobbyScrollPane;

    // ---------------------------------------------------------------------
    // JavaFX lifecycle and window management
    // ---------------------------------------------------------------------

    /**
     * Attaches the network proxy used by the GUI to send commands to the server.
     */
    public void attachServerProxy(ServerProxy serverProxy) {
        this.serverProxy = Objects.requireNonNull(serverProxy, "serverProxy cannot be null");
    }

    /**
     * Starts the JavaFX user interface on a new primary stage.
     *
     * <p>If the JavaFX runtime has not been started yet, this method starts it.
     * Otherwise it schedules the window creation on the JavaFX Application Thread.
     */
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

    /**
     * Starts the JavaFX user interface on an existing stage.
     */
    public void start(Stage stage) {
        if (!Platform.isFxApplicationThread()) {
            Platform.runLater(() -> this.openStage(stage));
            return;
        }
        this.openStage(stage);
    }

    /**
     * Initializes injected FXML nodes and creates the rendering collaborators
     * after the FXML file has been loaded.
     */
    @FXML
    private void initialize() {
        this.capacityComboBox.getItems().setAll(2, 3, 4, 5);
        this.capacityComboBox.getSelectionModel().select(Integer.valueOf(4));
        this.lobbyScrollPane.setFitToWidth(true);

        this.lobbyRenderer = new LobbyRenderer(
                this.lobbyStatusLabel, this.lobbyCountLabel,
                this.lobbyActionTitleLabel, this.lobbyActionSubtitleLabel,
                this.currentLobbyTitleLabel, this.currentLobbyHostLabel,
                this.currentLobbyPlayersLabel, this.currentLobbyStateLabel,
                this.playersInLobby, this.capacityLabel, this.capacityComboBox,
                this.lobbyListContainer,
                this.openCreateTableButton, this.backToLobbyListButton,
                this.createLobbyButton, this.startLobbyButton, this.exitLobbyButton,
                () -> this.nickname,
                this.serverProxy,
                this::callServer
        );
        this.boardRenderer = new BoardRenderer(
                this.upperRowContainer, this.lowerRowContainer, this.offerPathContainer,
                this.playerListContainer, this.tribeCardsContainer, this.finalRankingContainer,
                () -> this.lastGamePayload,
                () -> this.nickname
        );
        this.gameTurnController = new GameTurnController(
                this.selectedCardLabel,
                () -> this.lastGamePayload,
                () -> this.nickname,
                this.serverProxy,
                this::callServer,
                board -> this.boardRenderer.renderBoard(board)
        );
        this.boardRenderer.setInteractionHandlers(
                this.gameTurnController::canChooseFromRow,
                this.gameTurnController::canMoveTotemTo,
                this.gameTurnController::chooseCardFromBoard,
                this.gameTurnController::moveTotemFromBoard
        );

        this.showPage(this.loginPage);
        this.resetGameHeader();
        this.boardRenderer.renderGameSkeleton();
        this.lobbyRenderer.renderNoCurrentLobby();
        this.lobbyRenderer.updateLobbyActions();
        this.configureLobbyAutoRefresh();
    }

    /**
     * Loads the FXML file, applies the stylesheet, and shows the main window.
     */
    private void openStage(Stage targetStage) {
        try {
            FXMLLoader loader = new FXMLLoader(GUIView.class.getResource(FXML_RESOURCE));
            loader.setController(this);
            Parent root = loader.load();
            Scene scene = new Scene(root, 1180, 760);
            String stylesheet = GUIView.class.getResource(CSS_RESOURCE).toExternalForm();
            scene.getStylesheets().add(stylesheet);
            targetStage.setTitle("Mesos");
            targetStage.setMinWidth(980);
            targetStage.setMinHeight(680);
            targetStage.setScene(scene);
            targetStage.setMaximized(true);
            targetStage.setOnCloseRequest(event -> this.shutdownClient());
            targetStage.show();
        } catch (IOException exception) {
            throw new UncheckedIOException("Cannot start Mesos GUI", exception);
        }
    }

    /**
     * Closes client-side network resources and terminates the process.
     */
    private void shutdownClient() {
        try {
            if (this.serverProxy != null) {
                this.serverProxy.close();
            }
        } catch (IOException ignored) {
            // The client is already shutting down.
        } finally {
            Platform.exit();
            System.exit(0);
        }
    }

    /**
     * Runs a UI update on the JavaFX Application Thread.
     */
    private void runOnFx(Runnable runnable) {
        if (Platform.isFxApplicationThread()) {
            runnable.run();
        } else {
            Platform.runLater(runnable);
        }
    }

    // ---------------------------------------------------------------------
    // FXML event handlers
    // ---------------------------------------------------------------------

    @FXML
    private void handleLogin() {
        String typedNickname = this.nicknameField.getText() == null ? "" : this.nicknameField.getText().trim();
        if (typedNickname.isBlank()) {
            this.loginStatusLabel.setText("Enter a nickname to join.");
            return;
        }

        this.nicknameField.setDisable(true);
        this.loginButton.setDisable(true);
        this.loginStatusLabel.setText("Signing in...");
        this.callServer(() -> this.serverProxy.authenticate(typedNickname));
    }

    @FXML
    private void handleOpenCreateTable() {
        this.lobbyRenderer.renderNoCurrentLobby();
        this.lobbyRenderer.updateLobbyActions();
        this.showPage(this.lobbyFormPage);
    }

    @FXML
    private void handleBackToLobbyList() {
        if (this.lobbyRenderer.isInLobby()) {
            return;
        }
        this.lobbyRenderer.renderNoCurrentLobby();
        this.lobbyRenderer.updateLobbyActions();
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
    private void handleBackToLobby() {
        this.resetAfterMatch();
        this.showPage(this.lobbyPage);
        this.lobbyRenderer.renderLobbyLoading();
        this.requestLobbyList();
    }

    // ---------------------------------------------------------------------
    // ClientView callbacks
    // ---------------------------------------------------------------------

    @Override
    public void showError(ErrorPayload payload) {
        this.runOnFx(() -> {
            String message = payload == null ? "Unknown error." : payload.message;
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
                this.gameTurnController.rollbackPendingDraftChoice();
                this.gameTurnController.setStatusMessage(message);
            } else if (this.lobbyFormPage.isVisible()) {
                this.lobbyRenderer.setLobbyActionSubtitle(message);
            } else {
                this.lobbyRenderer.setLobbyStatus(message);
            }
        });
    }

    @Override
    public void showAuthenticationResult(AuthenticationResultPayload payload) {
        this.runOnFx(() -> {
            if (payload == null) {
                return;
            }
            if (payload.nickname != null && !payload.nickname.isBlank()) {
                this.nickname = payload.nickname;
                this.sessionNicknameLabel.setText(this.nickname);
            }
            String message = payload.message == null ? "Login successful." : payload.message;
            this.loginStatusLabel.setText(message + "\nLoading lobby...");
            this.scheduleAuthenticatedRedirect();
        });
    }

    @Override
    public void showLobbyUpdate(LobbyUpdatePayload payload) {
        this.runOnFx(() -> {
            this.lobbyRenderer.renderLobbyUpdate(payload);
            if (payload == null) {
                return;
            }
            if (payload.event == LobbyEventType.LOBBY_CREATED
                    || payload.event == LobbyEventType.LOBBY_JOINED
                    || payload.event == LobbyEventType.LOBBY_LEFT) {
                if (!this.lobbyRenderer.isInLobby()) {
                    this.showPage(this.lobbyPage);
                    this.requestLobbyList();
                } else {
                    this.showPage(this.lobbyFormPage);
                }
            }
            if (payload.event == LobbyEventType.LOBBY_STARTED) {
                this.cancelAuthenticatedRedirect();
                this.showPage(this.gamePage);
            }
        });
    }

    @Override
    public void showLobbyList(LobbyListPayload payload) {
        this.runOnFx(() -> {
            boolean rendered = this.lobbyRenderer.renderLobbyList(payload);
            if (!rendered) {
                return;
            }
            if (!this.gamePage.isVisible() && !this.endPage.isVisible() && !this.lobbyFormPage.isVisible()) {
                this.showPage(this.lobbyPage);
            }
        });
    }

    @Override
    public void showGameUpdate(GamePayload payload) {
        this.runOnFx(() -> {
            this.cancelAuthenticatedRedirect();
            this.lastGamePayload = payload;
            if (payload == null) {
                this.matchTitleLabel.setText("Match");
                this.matchMetaLabel.setText("Waiting for server");
                this.turnLabel.setText("No match update received");
                this.boardRenderer.renderGameSkeleton();
                this.showPage(this.gamePage);
                return;
            }
            this.gameTurnController.updateLocalDraftTurn(payload);
            this.gameTurnController.clearPendingDraftRow();
            this.matchTitleLabel.setText("Match #" + payload.id);
            this.matchMetaLabel.setText("Era " + displayEra(payload.currentEra) + " / " + payload.status);
            this.turnLabel.setText(GameTurnController.renderTurn(payload.turnOrder, payload.turnState));
            this.boardRenderer.renderBoard(payload.board);
            this.boardRenderer.renderPlayers(payload.players);
            this.boardRenderer.renderTribe(payload.players);
            this.gameTurnController.updateActionHint();
            this.showPage(this.gamePage);
        });
    }

    @Override
    public void showFinalGameResult(GamePayload gamePayload) {
        this.runOnFx(() -> {
            this.boardRenderer.renderFinalRanking(gamePayload);
            this.showPage(this.endPage);
        });
    }

    // ---------------------------------------------------------------------
    // Authentication and lobby navigation flow
    // ---------------------------------------------------------------------

    /** Starts the short post-login delay before showing the lobby list. */
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

    /** Cancels the pending post-login transition. */
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
                && !this.lobbyRenderer.isInLobby();
    }

    /** Creates the timer that refreshes the lobby list while the list page is open. */
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
                && !this.lobbyRenderer.isInLobby()
                && this.serverProxy != null
                && this.serverProxy.isConnected();
    }

    /** Requests the lobby list unless the user is already seated in a lobby. */
    private void requestLobbyList() {
        if (this.lobbyRenderer.isInLobby()) {
            this.lobbyRenderer.renderLobbyListUnavailable();
            return;
        }
        this.callServer(() -> this.serverProxy.listLobbies());
    }

    // ---------------------------------------------------------------------
    // Shared helpers
    // ---------------------------------------------------------------------

    /** Resets all match-specific state after the final result screen. */
    private void resetAfterMatch() {
        this.lastGamePayload = null;
        this.gameTurnController.resetAfterMatch();
        this.lobbyRenderer.resetState();
        this.resetGameHeader();
        this.boardRenderer.renderGameSkeleton();
        this.lobbyRenderer.setLobbyStatus("Match complete. Choose a new lobby or create one.");
    }

    /** Resets the game header labels to their loading state. */
    private void resetGameHeader() {
        this.matchTitleLabel.setText("Match");
        this.matchMetaLabel.setText("Syncing");
        this.turnLabel.setText("Loading");
        this.selectedCardLabel.setText("The board will appear as soon as the first update arrives.");
    }

    /** Returns the era value already normalized by the server payload. */
    private static int displayEra(int currentEra) {
        return currentEra;
    }

    /**
     * Shows one page from the FXML page stack and hides the others.
     *
     * <p>This method also starts or stops the lobby auto-refresh timer based on
     * which page is visible.
     */
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

    /**
     * Executes a server command and converts local GUI-side failures into visible errors.
     * Package-private so the renderer collaborators can dispatch through it.
     */
    void callServer(Runnable call) {
        if (this.serverProxy == null) {
            this.showError(new ErrorPayload("CLIENT_GUI_ERROR", "ServerProxy not attached."));
            return;
        }
        try {
            call.run();
        } catch (RuntimeException exception) {
            this.showError(new ErrorPayload("CLIENT_GUI_ERROR", exception.getMessage()));
        }
    }

    private boolean isConnectionClosed(ErrorPayload payload) {
        return payload != null && "CONNECTION_CLOSED".equals(payload.code);
    }
}
