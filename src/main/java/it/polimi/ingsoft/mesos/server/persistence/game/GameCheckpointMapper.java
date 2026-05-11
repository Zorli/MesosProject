package it.polimi.ingsoft.mesos.server.persistence.game;

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
import it.polimi.ingsoft.mesos.server.model.enums.TotemColor;
import it.polimi.ingsoft.mesos.server.model.enums.TurnPhase;
import it.polimi.ingsoft.mesos.server.model.game.Game;
import it.polimi.ingsoft.mesos.server.model.game.RoundPhase;
import it.polimi.ingsoft.mesos.server.model.game.TurnOrderTrack;
import it.polimi.ingsoft.mesos.server.model.game.TurnState;
import it.polimi.ingsoft.mesos.server.model.player.Player;
import it.polimi.ingsoft.mesos.server.model.player.Tribe;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

final class GameCheckpointMapper {
    private static final int SCHEMA_VERSION = 1;

    private final Clock clock;
    private final DeckTemplate deckTemplate;

    GameCheckpointMapper() {
        this(Clock.systemUTC(), ParserJson.getDeckTemplate());
    }

    GameCheckpointMapper(Clock clock, DeckTemplate deckTemplate) {
        this.clock = clock;
        this.deckTemplate = deckTemplate;
    }

    GameCheckpoint toCheckpoint(Game game, String hostNickname) {
        return new GameCheckpoint(
                SCHEMA_VERSION,
                Instant.now(this.clock).toString(),
                new GameCheckpoint.GameData(
                        game.getId(),
                        hostNickname,
                        game.getStatus().name(),
                        game.getCurrentEra().name(),
                        game.getStateVersion()
                ),
                game.getPlayers().stream().map(this::playerData).toList(),
                this.boardData(game.getBoard()),
                this.turnOrderData(game.getTurnOrderTrack()),
                this.turnStateData(game.getTurnState(), game.getPlayers())
        );
    }

    RestoredMatch fromCheckpoint(GameCheckpoint checkpoint) {
        if (checkpoint == null || checkpoint.game() == null) {
            throw new IllegalArgumentException("Invalid checkpoint");
        }

        List<Player> players = checkpoint.players().stream().map(this::restorePlayer).toList();
        Map<String, Player> playersByNickname = this.indexPlayers(players);
        TurnOrderTrack turnOrderTrack = this.restoreTurnOrder(checkpoint.turnOrder(), playersByNickname);
        TurnState turnState = this.restoreTurnState(checkpoint.turnState(), playersByNickname);
        Board board = this.restoreBoard(checkpoint.board(), players, turnOrderTrack.getTurnOrder());
        Game game = new Game(
                checkpoint.game().id(),
                players,
                turnState,
                turnOrderTrack,
                board,
                Era.valueOf(checkpoint.game().currentEra()),
                GameStatus.valueOf(checkpoint.game().status()),
                checkpoint.game().stateVersion()
        );
        return new RestoredMatch(game, players, checkpoint.game().hostNickname());
    }

    private GameCheckpoint.PlayerData playerData(Player player) {
        return new GameCheckpoint.PlayerData(
                player.getNickname(),
                player.getTotem().name(),
                player.getFood(),
                player.getPrestigePoints(),
                this.tribeData(player.getTribe())
        );
    }

    private GameCheckpoint.TribeData tribeData(Tribe tribe) {
        return new GameCheckpoint.TribeData(
                tribe.getCharacters().stream().map(Card::getId).toList(),
                tribe.getBuildings().stream().map(this::cardData).toList()
        );
    }

    private GameCheckpoint.CardData cardData(BuildingCard card) {
        return new GameCheckpoint.CardData(card.getId(), card.exportRuntimeState());
    }

    private GameCheckpoint.BoardData boardData(Board board) {
        return new GameCheckpoint.BoardData(
                ids(board.getRemainingTribeDeckCards()),
                ids(board.getRemainingBuildingDeckCards()),
                idsOf(board.getUpperRowCards(), TribeCard.class),
                idsOf(board.getUpperRowCards(), BuildingCard.class),
                idsOf(board.getLowerRowCards(), TribeCard.class),
                idsOf(board.getLowerRowCards(), BuildingCard.class),
                board.getOfferSpaces().stream().map(this::offerSpaceData).toList(),
                board.getStartingBlocks().stream().map(this::startingBlockData).toList()
        );
    }

    private GameCheckpoint.OfferSpaceData offerSpaceData(OfferSpace space) {
        Player occupiedBy = space.getOccupiedBy();
        return new GameCheckpoint.OfferSpaceData(
                space.getCode(),
                space.getPositionIndex(),
                space.getFoodBonus(),
                space.getTopRowCardsToTake(),
                space.getBottomRowCardsToTake(),
                occupiedBy == null ? null : occupiedBy.getNickname()
        );
    }

    private GameCheckpoint.StartingBlockData startingBlockData(StartingBlock block) {
        Player occupiedBy = block.getOccupiedBy();
        return new GameCheckpoint.StartingBlockData(
                block.getPosition(),
                block.getFoodModifier(),
                block.getPrestigeModifier(),
                occupiedBy == null ? null : occupiedBy.getNickname()
        );
    }

    private GameCheckpoint.TurnOrderData turnOrderData(TurnOrderTrack track) {
        return new GameCheckpoint.TurnOrderData(
                track.getTurnOrder().stream().map(Player::getNickname).toList(),
                track.getCurrentPlayerIndex(),
                track.getRoundNumber(),
                track.getCurrentRoundPhase().name()
        );
    }

    private GameCheckpoint.TurnStateData turnStateData(TurnState state, List<Player> players) {
        return new GameCheckpoint.TurnStateData(
                state.getPhase().name(),
                choicesByNickname(players, state.getRemainingUpperChoicesByPlayer()),
                choicesByNickname(players, state.getRemainingLowerChoicesByPlayer())
        );
    }

    private Player restorePlayer(GameCheckpoint.PlayerData data) {
        Tribe tribe = new Tribe();
        for (Integer cardId : data.tribe().characters()) {
            tribe.addCharacter((CharacterCard) this.cardById(cardId));
        }
        for (GameCheckpoint.CardData cardData : data.tribe().buildings()) {
            BuildingCard building = (BuildingCard) this.cardById(cardData.id());
            building.restoreRuntimeState(cardData.runtimeState() == null ? Map.of() : cardData.runtimeState());
            tribe.addBuilding(building);
        }
        return new Player(
                data.nickname(),
                data.food(),
                data.prestigePoints(),
                TotemColor.valueOf(data.totem()),
                tribe
        );
    }

    private TurnOrderTrack restoreTurnOrder(GameCheckpoint.TurnOrderData data, Map<String, Player> playersByNickname) {
        List<Player> turnOrder = data.nicknames().stream()
                .map(playersByNickname::get)
                .toList();
        if (turnOrder.contains(null)) {
            throw new IllegalArgumentException("Unknown player in turn order");
        }
        return new TurnOrderTrack(
                turnOrder,
                data.currentPlayerIndex(),
                data.roundNumber(),
                RoundPhase.valueOf(data.currentRoundPhase())
        );
    }

    private TurnState restoreTurnState(GameCheckpoint.TurnStateData data, Map<String, Player> playersByNickname) {
        return new TurnState(
                TurnPhase.valueOf(data.phase()),
                restoreChoices(data.remainingUpperChoicesByNickname(), playersByNickname),
                restoreChoices(data.remainingLowerChoicesByNickname(), playersByNickname)
        );
    }

    private Board restoreBoard(GameCheckpoint.BoardData data, List<Player> players, List<Player> orderedPlayers) {
        TribeDeck tribeDeck = TribeDeck.fromOrderedCards(this.cards(data.remainingTribeDeck()));
        BuildingDeck buildingDeck = BuildingDeck.fromOrderedCards(this.cards(data.remainingBuildingDeck()));
        UpperPile upperPile = new UpperPile(
                this.typedCards(data.upperTribeCards(), TribeCard.class),
                this.typedCards(data.upperBuildingCards(), BuildingCard.class)
        );
        LowerPile lowerPile = new LowerPile(
                this.typedCards(data.lowerTribeCards(), TribeCard.class),
                this.typedCards(data.lowerBuildingCards(), BuildingCard.class)
        );
        OfferPath offerPath = new OfferPath(
                new StartingTile(this.restoreStartingBlocks(data.startingBlocks(), players)),
                this.restoreOfferSpaces(data.offerSpaces(), players)
        );
        return new Board(tribeDeck, buildingDeck, orderedPlayers, upperPile, lowerPile, offerPath);
    }

    private List<StartingBlock> restoreStartingBlocks(List<GameCheckpoint.StartingBlockData> data, List<Player> players) {
        Map<String, Player> playersByNickname = this.indexPlayers(players);
        List<StartingBlock> blocks = new ArrayList<>();
        for (GameCheckpoint.StartingBlockData block : data) {
            blocks.add(new StartingBlock(
                    block.position(),
                    block.foodModifier(),
                    block.prestigeModifier(),
                    block.occupiedByNickname() == null ? null : playersByNickname.get(block.occupiedByNickname())
            ));
        }
        return blocks;
    }

    private List<OfferSpace> restoreOfferSpaces(List<GameCheckpoint.OfferSpaceData> data, List<Player> players) {
        Map<String, Player> playersByNickname = this.indexPlayers(players);
        List<OfferSpace> spaces = new ArrayList<>();
        for (GameCheckpoint.OfferSpaceData spaceData : data) {
            OfferSpace space = new OfferSpace(
                    spaceData.positionIndex(),
                    spaceData.code(),
                    minPlayersForOfferSpace(spaceData.code()),
                    spaceData.foodBonus(),
                    spaceData.topRowCardsToTake(),
                    spaceData.bottomRowCardsToTake()
            );
            if (spaceData.occupiedByNickname() != null) {
                space.occupy(playersByNickname.get(spaceData.occupiedByNickname()));
            }
            spaces.add(space);
        }
        return spaces;
    }

    private List<Integer> ids(List<Card> cards) {
        return cards.stream().map(Card::getId).toList();
    }

    private <T extends Card> List<Integer> idsOf(List<Card> cards, Class<T> type) {
        return cards.stream().filter(type::isInstance).map(Card::getId).toList();
    }

    private <T extends Card> ArrayList<T> typedCards(List<Integer> ids, Class<T> type) {
        ArrayList<T> cards = new ArrayList<>();
        for (Integer id : ids) {
            cards.add(type.cast(this.cardById(id)));
        }
        return cards;
    }

    private ArrayList<Card> cards(List<Integer> ids) {
        ArrayList<Card> cards = new ArrayList<>();
        for (Integer id : ids) {
            cards.add(this.cardById(id));
        }
        return cards;
    }

    private Card cardById(int id) {
        return this.deckTemplate.createCardById(id);
    }

    private Map<String, Integer> choicesByNickname(List<Player> players, Map<Player, Integer> choices) {
        Map<String, Integer> result = new LinkedHashMap<>();
        for (Player player : players) {
            Integer value = choices.get(player);
            if (value != null) {
                result.put(player.getNickname(), value);
            }
        }
        return result;
    }

    private Map<Player, Integer> restoreChoices(Map<String, Integer> choices, Map<String, Player> playersByNickname) {
        Map<Player, Integer> result = new LinkedHashMap<>();
        if (choices == null) {
            return result;
        }
        for (Map.Entry<String, Integer> entry : choices.entrySet()) {
            Player player = playersByNickname.get(entry.getKey());
            if (player == null) {
                throw new IllegalArgumentException("Unknown player in turn state: " + entry.getKey());
            }
            result.put(player, entry.getValue());
        }
        return result;
    }

    private Map<String, Player> indexPlayers(List<Player> players) {
        Map<String, Player> result = new LinkedHashMap<>();
        for (Player player : players) {
            result.put(player.getNickname(), player);
        }
        return result;
    }

    private int minPlayersForOfferSpace(String code) {
        return switch (code) {
            case "A" -> 5;
            case "B", "C", "E", "F" -> 2;
            case "D" -> 3;
            case "G" -> 4;
            default -> throw new IllegalArgumentException("Unknown offer space: " + code);
        };
    }
}
