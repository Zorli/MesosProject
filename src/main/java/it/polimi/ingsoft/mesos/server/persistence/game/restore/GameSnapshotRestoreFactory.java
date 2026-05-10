package it.polimi.ingsoft.mesos.server.persistence.game.restore;

import it.polimi.ingsoft.mesos.server.controller.Factory.DeckTemplate;
import it.polimi.ingsoft.mesos.server.controller.Factory.ParserJson;
import it.polimi.ingsoft.mesos.server.model.board.Board;
import it.polimi.ingsoft.mesos.server.model.board.LowerPile;
import it.polimi.ingsoft.mesos.server.model.board.UpperPile;
import it.polimi.ingsoft.mesos.server.model.board.offerpath.OfferPath;
import it.polimi.ingsoft.mesos.server.model.board.offerpath.OfferSpace;
import it.polimi.ingsoft.mesos.server.model.board.offerpath.StartingBlock;
import it.polimi.ingsoft.mesos.server.model.board.offerpath.StartingTile;
import it.polimi.ingsoft.mesos.server.model.cards.BuildingCard;
import it.polimi.ingsoft.mesos.server.model.cards.BuildingDeck;
import it.polimi.ingsoft.mesos.server.model.cards.Card;
import it.polimi.ingsoft.mesos.server.model.cards.TribeCard;
import it.polimi.ingsoft.mesos.server.model.cards.TribeDeck;
import it.polimi.ingsoft.mesos.server.model.cards.characters.CharacterCard;
import it.polimi.ingsoft.mesos.server.model.enums.Era;
import it.polimi.ingsoft.mesos.server.model.enums.GameStatus;
import it.polimi.ingsoft.mesos.server.model.game.RoundPhase;
import it.polimi.ingsoft.mesos.server.model.enums.TurnPhase;
import it.polimi.ingsoft.mesos.server.model.enums.TotemColor;
import it.polimi.ingsoft.mesos.server.model.game.Game;
import it.polimi.ingsoft.mesos.server.model.game.TurnOrderTrack;
import it.polimi.ingsoft.mesos.server.model.game.TurnState;
import it.polimi.ingsoft.mesos.server.model.player.Player;
import it.polimi.ingsoft.mesos.server.model.player.Tribe;
import it.polimi.ingsoft.mesos.server.persistence.game.dto.SavedCardSnapshot;
import it.polimi.ingsoft.mesos.server.persistence.game.dto.SavedMatchSnapshot;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Rebuilds runtime model objects from persisted snapshots.
 */
public final class GameSnapshotRestoreFactory {
    private static final int MIN_PLAYERS = 2;
    private static final String TURN_PHASE_ERROR_PREFIX = "Unsupported turn phase";
    private static final Set<String> SUPPORTED_OFFER_SPACES = Set.of("A", "B", "C", "D", "E", "F", "G");

    private final SnapshotValidator validator;
    private final CardInstanceResolver cardInstanceResolver;
    private final DeckTemplate deckTemplate;

    /**
     * Creates a restore factory with the default validator and deck template.
     */
    public GameSnapshotRestoreFactory() {
        this(new SnapshotValidator(), new CardInstanceResolver(), ParserJson.getDeckTemplate());
    }

    /**
     * Creates a restore factory with injected collaborators.
     *
     * @param validator schema and reference validator
     * @param cardInstanceResolver helper to materialize cards by identifier
     * @param deckTemplate deck template used for availability checks
     */
    public GameSnapshotRestoreFactory(
            SnapshotValidator validator,
            CardInstanceResolver cardInstanceResolver,
            DeckTemplate deckTemplate
    ) {
        this.validator = Objects.requireNonNull(validator, "validator cannot be null");
        this.cardInstanceResolver = Objects.requireNonNull(cardInstanceResolver, "cardInstanceResolver cannot be null");
        this.deckTemplate = Objects.requireNonNull(deckTemplate, "deckTemplate cannot be null");
    }

    /**
     * Rebuilds a full match from a persisted snapshot.
     *
     * @param snapshot persisted checkpoint
     * @return restored runtime aggregate
     */
    public RestoredMatch fromSnapshot(SavedMatchSnapshot snapshot) {
        this.validator.validate(snapshot);
        if (snapshot.players().size() < MIN_PLAYERS) {
            throw new IllegalArgumentException("Restore requires at least " + MIN_PLAYERS + " players");
        }

        int playerCount = snapshot.players().size();
        Set<Integer> validTribeCardIds = this.deckTemplate.createTribeDeck(playerCount)
                .getCardsInOrder()
                .stream()
                .map(Card::getId)
                .collect(Collectors.toSet());
        Set<Integer> validBuildingCardIds = this.deckTemplate.createBuildingDeck()
                .getCardsInOrder()
                .stream()
                .map(Card::getId)
                .collect(Collectors.toSet());

        List<Player> restoredPlayers = this.restorePlayers(snapshot.players(), validTribeCardIds, validBuildingCardIds);
        Map<String, Player> playersByNickname = this.indexPlayers(restoredPlayers);
        TurnOrderTrack restoredTurnOrder = this.restoreTurnOrder(snapshot.turnOrder(), playersByNickname);
        TurnState restoredTurnState = this.restoreTurnState(snapshot.turnState(), playersByNickname);

        Game restoredGame = this.restoreGame(snapshot, restoredPlayers, restoredTurnOrder, restoredTurnState, playerCount);

        return new RestoredMatch(
                restoredGame,
                List.copyOf(restoredPlayers),
                snapshot.game().hostNickname()
        );
    }

    private List<Player> restorePlayers(
            List<SavedMatchSnapshot.PlayerSnapshot> players,
            Set<Integer> validTribeCardIds,
            Set<Integer> validBuildingCardIds
    ) {
        return players.stream()
                .map(player -> this.restorePlayer(player, validTribeCardIds, validBuildingCardIds))
                .toList();
    }

    private Player restorePlayer(
            SavedMatchSnapshot.PlayerSnapshot snapshot,
            Set<Integer> validTribeCardIds,
            Set<Integer> validBuildingCardIds
    ) {
        Tribe restoredTribe = this.restoreTribe(snapshot.tribe(), validTribeCardIds, validBuildingCardIds);
        return new Player(
                snapshot.nickname(),
                snapshot.food(),
                snapshot.prestigePoints(),
                parseTotem(snapshot.totem()),
                restoredTribe
        );
    }

    private Tribe restoreTribe(
            SavedMatchSnapshot.TribeSnapshot tribeSnapshot,
            Set<Integer> validTribeCardIds,
            Set<Integer> validBuildingCardIds
    ) {
        List<CharacterCard> restoredCharacters = tribeSnapshot.characters().stream()
                .map(id -> this.restoreCharacterCard(id, validTribeCardIds))
                .toList();

        List<BuildingCard> restoredBuildings = tribeSnapshot.buildings().stream()
                .map(snapshot -> this.restoreBuildingCard(snapshot, validBuildingCardIds))
                .toList();

        return new Tribe(restoredCharacters, restoredBuildings);
    }

    private CharacterCard restoreCharacterCard(int cardId, Set<Integer> validCharacterCards) {
        if (!validCharacterCards.contains(cardId)) {
            throw new IllegalArgumentException("Character card id is not valid for current deck: " + cardId);
        }
        Card card = this.cardInstanceResolver.byId(cardId);
        if (!(card instanceof CharacterCard characterCard)) {
            throw new IllegalArgumentException("Expected character card id: " + cardId);
        }
        return characterCard;
    }

    private BuildingCard restoreBuildingCard(SavedCardSnapshot snapshot, Set<Integer> validBuildingCards) {
        if (!validBuildingCards.contains(snapshot.id())) {
            throw new IllegalArgumentException("Building card id is not valid for current deck: " + snapshot.id());
        }
        Card card = this.cardInstanceResolver.byId(snapshot.id());
        if (!(card instanceof BuildingCard buildingCard)) {
            throw new IllegalArgumentException("Expected building card id: " + snapshot.id());
        }

        Map<String, Integer> runtimeState = snapshot.runtimeState() == null ? Map.of() : snapshot.runtimeState();
        this.cardInstanceResolver.restoreRuntimeStateIfSupported(buildingCard, runtimeState);
        return buildingCard;
    }

    private Game restoreGame(
            SavedMatchSnapshot snapshot,
            List<Player> restoredPlayers,
            TurnOrderTrack restoredTurnOrder,
            TurnState restoredTurnState,
            int playerCount
    ) {
        Board restoredBoard = this.restoreBoard(
                snapshot.board(),
                restoredPlayers,
                playerCount,
                restoredTurnOrder.getTurnOrder()
        );
        return new Game(
                snapshot.game().id(),
                restoredPlayers,
                restoredTurnState,
                restoredTurnOrder,
                restoredBoard,
                Era.valueOf(snapshot.game().currentEra()),
                GameStatus.valueOf(snapshot.game().status()),
                snapshot.game().stateVersion()
        );
    }

    private TurnOrderTrack restoreTurnOrder(
            SavedMatchSnapshot.TurnOrderSnapshot snapshot,
            Map<String, Player> playersByNickname
    ) {
        List<Player> turnOrder = snapshot.nicknames().stream()
                .map(playersByNickname::get)
                .toList();
        if (turnOrder.contains(null)) {
            throw new IllegalArgumentException("Unknown players referenced in turn order");
        }
        if (snapshot.currentPlayerIndex() < 0 || snapshot.currentPlayerIndex() >= turnOrder.size()) {
            throw new IllegalArgumentException("Turn order index out of bounds");
        }

        return new TurnOrderTrack(
                turnOrder,
                snapshot.currentPlayerIndex(),
                snapshot.roundNumber(),
                RoundPhase.valueOf(snapshot.currentRoundPhase())
        );
    }

    private TurnState restoreTurnState(
            SavedMatchSnapshot.TurnStateSnapshot snapshot,
            Map<String, Player> playersByNickname
    ) {
        try {
            TurnPhase phase = TurnPhase.valueOf(snapshot.phase());
            return new TurnState(
                    phase,
                    this.restoreChoiceMap(snapshot.remainingUpperChoicesByNickname(), playersByNickname),
                    this.restoreChoiceMap(snapshot.remainingLowerChoicesByNickname(), playersByNickname)
            );
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException(TURN_PHASE_ERROR_PREFIX, exception);
        }
    }

    private Map<Player, Integer> restoreChoiceMap(
            Map<String, Integer> choiceByNickname,
            Map<String, Player> playersByNickname
    ) {
        if (choiceByNickname == null || choiceByNickname.isEmpty()) {
            return Map.of();
        }

        Map<Player, Integer> restored = new LinkedHashMap<>();
        for (Map.Entry<String, Integer> entry : choiceByNickname.entrySet()) {
            Player player = playersByNickname.get(entry.getKey());
            if (player == null) {
                throw new IllegalArgumentException("Turn state references unknown player: " + entry.getKey());
            }
            Integer value = entry.getValue();
            if (value == null || value < 0) {
                throw new IllegalArgumentException("Choice counters must be non-negative: " + entry.getKey());
            }
            restored.put(player, value);
        }
        return restored;
    }

    private Board restoreBoard(
            SavedMatchSnapshot.BoardSnapshot snapshot,
            List<Player> players,
            int playerCount,
            List<Player> orderedPlayers
    ) {
        TribeDeck tribeDeck = new TribeDeck(restoreCardListForDeck(snapshot.remainingTribeDeck()));
        BuildingDeck buildingDeck = new BuildingDeck(restoreCardListForDeck(snapshot.remainingBuildingDeck()));
        UpperPile upperPile = new UpperPile(
                restoreCardList(snapshot.upperTribeCards(), TribeCard.class),
                restoreCardList(snapshot.upperBuildingCards(), BuildingCard.class)
        );
        LowerPile lowerPile = new LowerPile(
                restoreCardList(snapshot.lowerTribeCards(), TribeCard.class),
                restoreCardList(snapshot.lowerBuildingCards(), BuildingCard.class)
        );
        List<StartingBlock> startingBlocks = this.restoreStartingBlocks(snapshot.startingBlocks(), players);
        List<OfferSpace> offerSpaces = this.restoreOfferSpaces(
                snapshot.offerSpaces(),
                this.indexPlayers(players),
                playerCount
        );
        OfferPath offerPath = new OfferPath(
                new StartingTile(startingBlocks),
                offerSpaces
        );

        return new Board(
                tribeDeck,
                buildingDeck,
                orderedPlayers,
                upperPile,
                lowerPile,
                offerPath
        );
    }

    private <T extends Card> ArrayList<T> restoreCardList(List<Integer> ids, Class<T> type) {
        if (ids == null || ids.isEmpty()) {
            return new ArrayList<>();
        }

        ArrayList<T> cards = new ArrayList<>();
        for (Integer cardId : ids) {
            int normalizedId = this.requirePositiveId(cardId);
            Card card = this.cardInstanceResolver.byId(normalizedId);
            if (!type.isInstance(card)) {
                throw new IllegalArgumentException("Unexpected card type for id " + cardId);
            }
            cards.add(type.cast(card));
        }
        return cards;
    }

    private ArrayList<Card> restoreCardListForDeck(List<Integer> ids) {
        if (ids == null || ids.isEmpty()) {
            return new ArrayList<>();
        }

        ArrayList<Card> cards = new ArrayList<>();
        for (Integer cardId : ids) {
            int normalizedId = this.requirePositiveId(cardId);
            cards.add(this.cardInstanceResolver.byId(normalizedId));
        }
        return cards;
    }

    private List<StartingBlock> restoreStartingBlocks(
            List<SavedMatchSnapshot.StartingBlockSnapshot> snapshots,
            List<Player> players
    ) {
        if (snapshots == null) {
            throw new IllegalArgumentException("board.startingBlocks cannot be null");
        }
        Set<Integer> usedPositions = new LinkedHashSet<>();
        Map<String, Player> playersByNickname = this.indexPlayers(players);
        List<StartingBlock> restored = new ArrayList<>();

        for (SavedMatchSnapshot.StartingBlockSnapshot snapshot : snapshots) {
            if (!usedPositions.add(snapshot.position())) {
                throw new IllegalArgumentException("Duplicate starting block position: " + snapshot.position());
            }
            Player occupiedBy = snapshot.occupiedByNickname() == null ? null : playersByNickname.get(snapshot.occupiedByNickname());
            if (snapshot.occupiedByNickname() != null && occupiedBy == null) {
                throw new IllegalArgumentException("Unknown player on starting block: " + snapshot.occupiedByNickname());
            }
            if (snapshot.position() <= 0 || snapshot.position() > players.size()) {
                throw new IllegalArgumentException("Invalid starting block position: " + snapshot.position());
            }
            restored.add(new StartingBlock(
                    snapshot.position(),
                    snapshot.foodModifier(),
                    snapshot.prestigeModifier(),
                    occupiedBy
            ));
        }
        return restored;
    }

    private List<OfferSpace> restoreOfferSpaces(
            List<SavedMatchSnapshot.OfferSpaceSnapshot> snapshots,
            Map<String, Player> playersByNickname,
            int playerCount
    ) {
        if (snapshots == null) {
            throw new IllegalArgumentException("board.offerSpaces cannot be null");
        }

        Map<Integer, OfferSpace> restoredByCodePosition = new LinkedHashMap<>();
        Set<String> usedCodes = new LinkedHashSet<>();
        for (SavedMatchSnapshot.OfferSpaceSnapshot snapshot : snapshots) {
            this.assertOfferSpaceCode(snapshot.code(), playerCount);

            if (snapshot.positionIndex() < 0) {
                throw new IllegalArgumentException("Offer space position cannot be negative: " + snapshot.positionIndex());
            }

            if (!usedCodes.add(snapshot.code())) {
                throw new IllegalArgumentException("Duplicate offer space code: " + snapshot.code());
            }
            if (restoredByCodePosition.containsKey(snapshot.positionIndex())) {
                throw new IllegalArgumentException("Duplicate offer space position: " + snapshot.positionIndex());
            }

            OfferSpace restoredOfferSpace = new OfferSpace(
                    snapshot.positionIndex(),
                    snapshot.code(),
                    this.minPlayersForOfferSpace(snapshot.code()),
                    snapshot.foodBonus(),
                    snapshot.topRowCardsToTake(),
                    snapshot.bottomRowCardsToTake()
            );
            String occupiedByNickname = snapshot.occupiedByNickname();
            if (occupiedByNickname != null) {
                Player occupiedBy = playersByNickname.get(occupiedByNickname);
                if (occupiedBy == null) {
                    throw new IllegalArgumentException("Unknown player on offer space: " + occupiedByNickname);
                }
                restoredOfferSpace.occupy(occupiedBy);
            }
            restoredByCodePosition.put(snapshot.positionIndex(), restoredOfferSpace);
        }

        return restoredByCodePosition.values().stream().toList();
    }

    private void assertOfferSpaceCode(String code, int playerCount) {
        if (code == null || code.isBlank()) {
            throw new IllegalArgumentException("Offer space code cannot be null or blank");
        }
        if (!SUPPORTED_OFFER_SPACES.contains(code)) {
            throw new IllegalArgumentException("Unknown offer space code: " + code);
        }
        if (playerCount < this.minPlayersForOfferSpace(code)) {
            throw new IllegalArgumentException("Offer space code not available for " + playerCount + " players: " + code);
        }
    }

    private int minPlayersForOfferSpace(String code) {
        return switch (code) {
            case "A" -> 5;
            case "B", "C", "E", "F" -> 2;
            case "D", "G" -> 3;
            default -> throw new AssertionError("Unreachable: unknown code passed validation: " + code);
        };
    }

    private Map<String, Player> indexPlayers(List<Player> players) {
        Map<String, Player> indexed = new LinkedHashMap<>();
        for (Player player : players) {
            String nickname = player.getNickname();
            if (indexed.putIfAbsent(nickname, player) != null) {
                throw new IllegalArgumentException("Duplicate player nickname in snapshot: " + nickname);
            }
        }
        return indexed;
    }

    private TotemColor parseTotem(String totem) {
        if (totem == null || totem.isBlank()) {
            throw new IllegalArgumentException("Player totem cannot be null or blank");
        }
        return TotemColor.valueOf(totem);
    }

    private int requirePositiveId(Integer cardId) {
        if (cardId == null) {
            throw new IllegalArgumentException("Card id cannot be null");
        }
        if (cardId <= 0) {
            throw new IllegalArgumentException("Card id must be positive: " + cardId);
        }
        return cardId;
    }
}
