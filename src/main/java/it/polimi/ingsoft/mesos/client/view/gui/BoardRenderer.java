package it.polimi.ingsoft.mesos.client.view.gui;

import it.polimi.ingsoft.mesos.shared.payload.servertoclient.game.BoardPayload;
import it.polimi.ingsoft.mesos.shared.payload.servertoclient.game.CardPayload;
import it.polimi.ingsoft.mesos.shared.payload.servertoclient.game.GamePayload;
import it.polimi.ingsoft.mesos.shared.payload.servertoclient.game.OfferSpacePayload;
import it.polimi.ingsoft.mesos.shared.payload.servertoclient.game.PlayerPayload;
import it.polimi.ingsoft.mesos.shared.payload.servertoclient.game.RankingPayload;
import it.polimi.ingsoft.mesos.shared.payload.servertoclient.game.StartingBlockPayload;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.function.Supplier;

/**
 * Renders everything inside the game page: the market rows, the offer path,
 * the player list, the per-player tribes, the final ranking and the loading
 * placeholders for each of these areas.
 *
 * <p>The class is a collaborator of {@link GUIView}. It receives the JavaFX
 * containers and a {@link Supplier} for the latest {@link GamePayload}, which
 * is used to look up totem assignments. Click handling on cards and offer-path
 * tiles is delegated upstream through the interaction callbacks because the
 * action rules live in {@link GameTurnController}.</p>
 */
final class BoardRenderer {

    private static final String CARD_RESOURCE_PATTERN = "/it/polimi/ingsoft/mesos/assets/cards/%d.jpg";
    private static final String OFFER_PATH_RESOURCE_PATTERN = "/it/polimi/ingsoft/mesos/assets/offerPath/%s.png";
    private static final String TOTEM_RESOURCE_PATTERN = "/it/polimi/ingsoft/mesos/assets/totems/%s.png";

    private static final double CARD_WIDTH = 108;
    private static final double CARD_HEIGHT = 154;
    private static final double OFFER_TILE_WIDTH = 148;
    private static final double OFFER_TILE_HEIGHT = 214;
    private static final double OCCUPANT_TOTEM_WIDTH = 32;
    private static final double OCCUPANT_TOTEM_HEIGHT = 48;

    private final HBox upperRowContainer;
    private final HBox lowerRowContainer;
    private final HBox offerPathContainer;
    private final VBox playerListContainer;
    private final VBox tribeCardsContainer;
    private final VBox finalRankingContainer;
    private final VBox allTimeRankingContainer;

    private final Supplier<GamePayload> gameSupplier;
    private final Supplier<String> nicknameSupplier;

    /**
     * Interaction callbacks supplied by {@link GameTurnController}. They are
     * set after construction because the turn controller itself needs a
     * reference to this renderer.
     */
    private Predicate<String> canChooseFromRow = row -> false;
    private Predicate<OfferSpacePayload> canMoveTotemTo = offer -> false;
    private BiConsumer<Integer, String> onCardClicked = (id, row) -> { };
    private Consumer<String> onOfferTileClicked = tile -> { };

    BoardRenderer(
            HBox upperRowContainer,
            HBox lowerRowContainer,
            HBox offerPathContainer,
            VBox playerListContainer,
            VBox tribeCardsContainer,
            VBox finalRankingContainer,
            VBox allTimeRankingContainer,
            Supplier<GamePayload> gameSupplier,
            Supplier<String> nicknameSupplier
    ) {
        this.upperRowContainer = upperRowContainer;
        this.lowerRowContainer = lowerRowContainer;
        this.offerPathContainer = offerPathContainer;
        this.playerListContainer = playerListContainer;
        this.tribeCardsContainer = tribeCardsContainer;
        this.finalRankingContainer = finalRankingContainer;
        this.allTimeRankingContainer = allTimeRankingContainer;
        this.gameSupplier = gameSupplier;
        this.nicknameSupplier = nicknameSupplier;
    }

    /**
     * Connects the rendered cards and offer-path tiles to the rules and the
     * action sinks owned by {@link GameTurnController}.
     */
    void setInteractionHandlers(
            Predicate<String> canChooseFromRow,
            Predicate<OfferSpacePayload> canMoveTotemTo,
            BiConsumer<Integer, String> onCardClicked,
            Consumer<String> onOfferTileClicked
    ) {
        this.canChooseFromRow = canChooseFromRow;
        this.canMoveTotemTo = canMoveTotemTo;
        this.onCardClicked = onCardClicked;
        this.onOfferTileClicked = onOfferTileClicked;
    }

    // ---------------------------------------------------------------------
    // Board
    // ---------------------------------------------------------------------

    /** Renders the complete board area from the latest board payload. */
    void renderBoard(BoardPayload board) {
        if (board == null) {
            this.renderBoardSkeleton();
            return;
        }
        this.renderCardRow(this.upperRowContainer, board.upperRow, "upper-card", "UPPER");
        this.renderCardRow(this.lowerRowContainer, board.lowerRow, "lower-card", "LOWER");
        this.renderOfferPath(board.offerSpaces, board.startingBlocks);
    }

    /** Renders one market row and enables only cards that are legal for the current phase. */
    private void renderCardRow(HBox container, List<CardPayload> cards, String styleClass, String row) {
        container.getChildren().clear();
        List<CardPayload> safeCards = cards == null ? List.of() : cards;
        if (safeCards.isEmpty()) {
            container.getChildren().add(emptyState("Empty market", "Waiting for server."));
            return;
        }
        boolean rowEnabled = this.canChooseFromRow.test(row);
        for (CardPayload card : safeCards) {
            StackPane cardNode = cardNode(card, styleClass);
            boolean cardEnabled = rowEnabled && !isEventCard(card);
            cardNode.setDisable(!cardEnabled);
            if (!cardEnabled) {
                cardNode.getStyleClass().add("disabled-card");
            }
            cardNode.setOnMouseClicked(event -> this.onCardClicked.accept(card.id, row));
            container.getChildren().add(cardNode);
        }
    }

    /** Creates the visual node for one card in the market or a tribe. */
    private static StackPane cardNode(CardPayload card, String styleClass) {
        StackPane wrapper = new StackPane();
        wrapper.getStyleClass().addAll("card-frame", styleClass);
        ImageView imageView = new ImageView(cardImage(card.id));
        imageView.setPreserveRatio(true);
        imageView.setFitWidth(CARD_WIDTH);
        imageView.setFitHeight(CARD_HEIGHT);
        Label chip = new Label("#" + card.id + " " + card.type);
        chip.getStyleClass().add("card-chip");
        StackPane.setAlignment(chip, Pos.BOTTOM_CENTER);
        wrapper.getChildren().addAll(imageView, chip);
        return wrapper;
    }

    /** Loads a card image, falling back to card 1 if the requested resource is missing. */
    private static Image cardImage(int cardId) {
        String resource = String.format(CARD_RESOURCE_PATTERN, cardId);
        return new Image(Objects.requireNonNullElse(
                BoardRenderer.class.getResourceAsStream(resource),
                BoardRenderer.class.getResourceAsStream(String.format(CARD_RESOURCE_PATTERN, 1))
        ));
    }

    /** Returns whether a card payload represents an event card. */
    static boolean isEventCard(CardPayload card) {
        return card != null
                && card.type != null
                && card.type.toUpperCase(Locale.ROOT).contains("EVENT");
    }

    // ---------------------------------------------------------------------
    // Offer path
    // ---------------------------------------------------------------------

    /** Renders the offer path and wires each available offer space to the totem move action. */
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
            boolean tileEnabled = this.canMoveTotemTo.test(offer);
            tile.setDisable(!tileEnabled);
            if (!tileEnabled) {
                tile.getStyleClass().add("disabled-tile");
            }
            tile.setOnMouseClicked(event -> this.onOfferTileClicked.accept(offer.code));
            this.offerPathContainer.getChildren().add(tile);
        }
    }

    /** Builds one offer-path tile using the image asset when available and a text fallback otherwise. */
    private StackPane offerPathTile(
            String imageKey,
            String fallbackTitle,
            String fallbackDetail,
            String occupant,
            List<StartingBlockPayload> startingBlocks
    ) {
        StackPane tile = new StackPane();
        tile.getStyleClass().add("offer-image-tile");

        Image image = resourceImage(String.format(OFFER_PATH_RESOURCE_PATTERN, imageKey));
        if (image == null) {
            tile.getChildren().add(textTile(fallbackTitle, fallbackDetail, occupant));
        } else {
            ImageView imageView = new ImageView(image);
            imageView.getStyleClass().add("offer-path-image");
            imageView.setFitWidth(OFFER_TILE_WIDTH);
            imageView.setFitHeight(OFFER_TILE_HEIGHT);
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

    /** Loads an image resource from the classpath. */
    private static Image resourceImage(String resource) {
        InputStream stream = BoardRenderer.class.getResourceAsStream(resource);
        if (stream == null) {
            return null;
        }
        return new Image(stream);
    }

    /** Adds the occupant totem or a textual fallback over an offer-path tile. */
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
        totemView.setFitWidth(OCCUPANT_TOTEM_WIDTH);
        totemView.setFitHeight(OCCUPANT_TOTEM_HEIGHT);
        totemView.setPreserveRatio(true);
        StackPane.setAlignment(totemView, Pos.TOP_CENTER);
        StackPane.setMargin(totemView, new Insets(14, 0, 0, 0));
        tile.getChildren().add(totemView);
    }

    /** Adds starting-order totems over the starting tile according to player position. */
    private void addStartingOrderTotems(StackPane tile, List<StartingBlockPayload> startingBlocks) {
        List<StartingBlockPayload> orderedBlocks = startingBlocks.stream()
                .sorted(Comparator.comparingInt(block -> block.position))
                .toList();
        double[] topOffsets = startingTotemTopOffsets(orderedBlocks.size());
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

    /** Returns vertical offsets used to align starting totems on the starting tile image. */
    private static double[] startingTotemTopOffsets(int slots) {
        return switch (slots) {
            case 2 -> new double[]{48, 86};
            case 3 -> new double[]{37, 75, 113};
            case 4 -> new double[]{25, 63, 101, 139};
            default -> new double[]{23, 58, 93, 128, 163};
        };
    }

    /** Resolves and loads the totem image assigned to a player. */
    private Image totemImageForPlayer(String nickname) {
        String totem = this.totemForPlayer(nickname);
        if (totem == null || totem.isBlank()) {
            return null;
        }
        return resourceImage(String.format(TOTEM_RESOURCE_PATTERN, totem.toLowerCase(Locale.ROOT)));
    }

    /** Looks up the totem name for a player in the latest game payload. */
    private String totemForPlayer(String nickname) {
        GamePayload payload = this.gameSupplier.get();
        if (nickname == null || payload == null || payload.players == null) {
            return null;
        }
        return payload.players.stream()
                .filter(player -> nickname.equals(player.nickname))
                .map(player -> player.totem)
                .filter(Objects::nonNull)
                .findFirst()
                .orElse(null);
    }

    /** Builds a text fallback tile when an offer-path image cannot be loaded. */
    private static VBox textTile(String title, String detail, String occupant) {
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

    // ---------------------------------------------------------------------
    // Players, tribes and final ranking
    // ---------------------------------------------------------------------

    /** Renders the compact player status list. */
    void renderPlayers(List<PlayerPayload> players) {
        this.playerListContainer.getChildren().clear();
        if (players == null || players.isEmpty()) {
            this.renderPlayersSkeleton();
            return;
        }
        GamePayload payload = this.gameSupplier.get();
        String activePlayer = payload != null && payload.turnOrder != null
                ? payload.turnOrder.currentPlayerNickname
                : null;
        String currentUser = this.nicknameSupplier.get();
        for (PlayerPayload player : players) {
            HBox row = new HBox(12);
            row.getStyleClass().add("player-row");
            if (currentUser.equals(player.nickname)) {
                row.getStyleClass().add("current-user-row");
            }
            if (player.nickname.equals(activePlayer)) {
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
            if (player.nickname.equals(activePlayer)) {
                Label turn = new Label("turn");
                turn.getStyleClass().add("micro-pill");
                row.getChildren().add(turn);
            }
            this.playerListContainer.getChildren().add(row);
        }
    }

    /** Renders each player's personal tribe cards. */
    void renderTribe(List<PlayerPayload> players) {
        this.tribeCardsContainer.getChildren().clear();
        if (players == null || players.isEmpty()) {
            this.renderTribeSkeleton();
            return;
        }
        for (PlayerPayload player : players) {
            this.tribeCardsContainer.getChildren().add(playerTribeSection(player));
        }
    }

    /** Builds the card strip for one player's tribe section. */
    private static VBox playerTribeSection(PlayerPayload player) {
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
            cardsRow.getChildren().add(emptyState("No personal cards", "This player has no tribe cards yet."));
        } else {
            cards.stream()
                    .map(card -> cardNode(card, "tribe-card"))
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

    /** Renders final ranking ordered by prestige points and then food. */
    void renderFinalRanking(GamePayload gamePayload) {
        this.finalRankingContainer.getChildren().clear();
        if (gamePayload == null) {
            this.finalRankingContainer.getChildren().add(
                    emptyState("Final ranking unavailable", "Waiting for the final match payload."));
            return;
        }

        List<PlayerPayload> players = new ArrayList<>(
                gamePayload.players.stream().filter(player -> player.isConnected).toList()
        );
        List<PlayerPayload> disconnectedPlayers = gamePayload.players.stream()
                .filter(player -> !player.isConnected)
                .toList();

        players.sort(Comparator
                .comparingInt((PlayerPayload player) -> player.prestigePoints)
                .thenComparingInt(player -> player.food)
                .reversed());
        for (int i = 0; i < players.size(); i++) {
            PlayerPayload player = players.get(i);
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
        Label disconnect = new Label("Disconnected players (not ranked)");
        disconnect.getStyleClass().add("final-disconnected-title");
        this.finalRankingContainer.getChildren().add(disconnect);
        for (PlayerPayload player : disconnectedPlayers) {
            HBox row = new HBox(16);
            row.getStyleClass().add("final-disconnected-row");
            Label name = new Label(player.nickname);
            name.getStyleClass().add("player-name");
            row.getChildren().add(name);
            this.finalRankingContainer.getChildren().add(row);
        }
    }

    /**
     * Renders the all-time ranking for the player count of the match just played.
     *
     * <p>Rows produced by the match that just finished are highlighted, and the
     * local player's own row is emphasized so it can read its placement.
     *
     * @param payload all-time ranking received from the server
     */
    void renderAllTimeRanking(RankingPayload payload) {
        this.allTimeRankingContainer.getChildren().clear();
        if (payload == null || payload.rows == null || payload.rows.isEmpty()) {
            this.allTimeRankingContainer.getChildren().add(
                    emptyState("Ranking unavailable", "No completed games stored yet for this player count."));
            return;
        }
        String localNickname = this.nicknameSupplier.get();
        for (RankingPayload.RankingRow entry : payload.rows) {
            HBox row = new HBox(16);
            row.getStyleClass().add("ranking-row");
            if (entry.currentGame) {
                row.getStyleClass().add("winner-row");
            }
            if (entry.currentGame && entry.nickname.equals(localNickname)) {
                row.getStyleClass().add("current-user-row");
            }
            Label position = new Label(String.valueOf(entry.rank));
            position.getStyleClass().add("rank-number");
            Label name = new Label(entry.nickname);
            name.getStyleClass().add("player-name");
            Label score = new Label(entry.score + " pp");
            score.getStyleClass().add("score-pill");
            Label date = new Label(shortDate(entry.datePlayed));
            date.getStyleClass().add("muted");
            row.getChildren().addAll(position, name, score, date);
            this.allTimeRankingContainer.getChildren().add(row);
        }
    }

    private static String shortDate(String isoDateTime) {
        if (isoDateTime == null) {
            return "";
        }
        String trimmed = isoDateTime.length() >= 16 ? isoDateTime.substring(0, 16) : isoDateTime;
        return trimmed.replace('T', ' ');
    }

    // ---------------------------------------------------------------------
    // Skeletons and empty state
    // ---------------------------------------------------------------------

    /** Resets every game area to its loading state. */
    void renderGameSkeleton() {
        this.renderBoardSkeleton();
        this.renderPlayersSkeleton();
        this.renderTribeSkeleton();
        this.renderFinalRankingSkeleton();
    }

    /** Fills the board area with placeholder cards and offer tiles. */
    private void renderBoardSkeleton() {
        this.upperRowContainer.getChildren().clear();
        this.lowerRowContainer.getChildren().clear();
        this.offerPathContainer.getChildren().clear();
        for (int index = 0; index < 4; index++) {
            this.upperRowContainer.getChildren().add(skeletonCard());
            this.lowerRowContainer.getChildren().add(skeletonCard());
        }
        for (int index = 0; index < 6; index++) {
            this.offerPathContainer.getChildren().add(skeletonTile());
        }
    }

    /** Fills the player list with placeholder rows. */
    private void renderPlayersSkeleton() {
        this.playerListContainer.getChildren().clear();
        for (int index = 0; index < 4; index++) {
            HBox row = new HBox(12);
            row.getStyleClass().addAll("player-row", "skeleton-row");
            row.getChildren().addAll(skeletonLine(130), skeletonLine(210));
            this.playerListContainer.getChildren().add(row);
        }
    }

    /** Fills the tribe section with placeholder card rows. */
    private void renderTribeSkeleton() {
        this.tribeCardsContainer.getChildren().clear();
        for (int sectionIndex = 0; sectionIndex < 2; sectionIndex++) {
            VBox section = new VBox(8);
            section.getStyleClass().add("player-tribe-section");
            HBox header = new HBox(8);
            header.getChildren().addAll(skeletonLine(130), skeletonLine(190));
            HBox cards = new HBox(10);
            cards.getStyleClass().add("card-row");
            for (int cardIndex = 0; cardIndex < 3; cardIndex++) {
                cards.getChildren().add(skeletonCard());
            }
            section.getChildren().addAll(header, cards);
            this.tribeCardsContainer.getChildren().add(section);
        }
    }

    /** Fills the final ranking container with placeholder rows. */
    private void renderFinalRankingSkeleton() {
        this.finalRankingContainer.getChildren().clear();
        for (int index = 0; index < 4; index++) {
            HBox row = new HBox(16);
            row.getStyleClass().addAll("ranking-row", "skeleton-row");
            row.getChildren().addAll(skeletonLine(28), skeletonLine(180), skeletonLine(72));
            this.finalRankingContainer.getChildren().add(row);
        }
    }

    /** Builds one disabled placeholder card. */
    private static StackPane skeletonCard() {
        StackPane card = new StackPane();
        card.getStyleClass().addAll("card-frame", "skeleton-card");
        card.setDisable(true);
        return card;
    }

    /** Builds one disabled placeholder offer tile. */
    private static VBox skeletonTile() {
        VBox tile = new VBox(8);
        tile.getStyleClass().addAll("offer-tile", "skeleton-tile");
        tile.setDisable(true);
        tile.getChildren().addAll(skeletonLine(60), skeletonLine(86), skeletonLine(50));
        return tile;
    }

    /** Builds one fixed-width placeholder line. Package-private so the lobby renderer can reuse it. */
    static Region skeletonLine(double width) {
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

    /** Builds a reusable empty-state block with a title and detail text. */
    static VBox emptyState(String title, String detail) {
        VBox empty = new VBox(4);
        empty.getStyleClass().add("empty-state");
        Label titleLabel = new Label(title);
        titleLabel.getStyleClass().add("empty-title");
        Label detailLabel = new Label(detail);
        detailLabel.getStyleClass().add("muted");
        empty.getChildren().addAll(titleLabel, detailLabel);
        return empty;
    }
}
