package it.polimi.ingsoft.mesos.server.persistence.savedmatch;

import it.polimi.ingsoft.mesos.server.model.Factory.ParserJson;
import it.polimi.ingsoft.mesos.server.model.GameState.Game;
import it.polimi.ingsoft.mesos.server.model.GameState.TurnOrderTrack;
import it.polimi.ingsoft.mesos.server.model.GameState.TurnState;
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
import it.polimi.ingsoft.mesos.server.model.player.Player;
import it.polimi.ingsoft.mesos.server.model.player.Tribe;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Rebuilds the live domain aggregate from the normalized saved-match persistence model.
 */
final class SavedMatchRestoreFactory {
    private SavedMatchRestoreFactory() {
    }

    /**
     * Restores a game aggregate from persisted rows.
     *
     * @param aggregate normalized persisted aggregate
     * @return restored game aggregate
     */
    static Game restore(SavedMatchAggregate aggregate) {
        SavedMatchConsistencyChecker.validate(aggregate);

        ArrayList<Player> players = new ArrayList<>();
        Map<String, Player> playersByNickname = new HashMap<>();

        for (SavedMatchPlayerRecord playerRecord : aggregate.players().stream()
                .sorted(Comparator.comparingInt(SavedMatchPlayerRecord::playerIndex))
                .toList()) {
            Player player = new Player(
                    playerRecord.nickname(),
                    playerRecord.food(),
                    playerRecord.prestigePoints(),
                    playerRecord.totemColor(),
                    new Tribe()
            );
            players.add(player);
            playersByNickname.put(player.getNickname(), player);
        }

        Map<String, Map<Integer, BuildingCard>> buildingsByNicknameAndId = restorePlayerCards(aggregate, playersByNickname);
        restorePlayerCardRuntimeState(aggregate, buildingsByNicknameAndId);

        Board board = restoreBoard(aggregate, players, playersByNickname);
        TurnOrderTrack turnOrderTrack = restoreTurnOrderTrack(aggregate, playersByNickname);
        TurnState turnState = restoreTurnState(aggregate, playersByNickname);

        return new Game(
                parseGameId(aggregate.gameId()),
                players,
                turnState,
                turnOrderTrack,
                board,
                aggregate.currentEra(),
                aggregate.gameStatus()
        );
    }

    private static Map<String, Map<Integer, BuildingCard>> restorePlayerCards(
            SavedMatchAggregate aggregate,
            Map<String, Player> playersByNickname
    ) {
        Map<String, Map<Integer, BuildingCard>> buildingsByNicknameAndId = new HashMap<>();

        for (SavedMatchPlayerCardRecord playerCardRecord : aggregate.playerCards()) {
            Player player = resolvePlayer(playersByNickname, playerCardRecord.nickname());
            Card card = ParserJson.getDeckTemplate().createCardById(playerCardRecord.cardId());

            if (playerCardRecord.cardZone() == SavedMatchPlayerCardZone.TRIBE_CHARACTER) {
                player.getTribe().addCharacter((CharacterCard) card);
            } else {
                BuildingCard buildingCard = (BuildingCard) card;
                player.getTribe().addBuilding(buildingCard);
                buildingsByNicknameAndId
                        .computeIfAbsent(player.getNickname(), ignored -> new HashMap<>())
                        .put(buildingCard.getId(), buildingCard);
            }
        }

        return buildingsByNicknameAndId;
    }

    private static void restorePlayerCardRuntimeState(
            SavedMatchAggregate aggregate,
            Map<String, Map<Integer, BuildingCard>> buildingsByNicknameAndId
    ) {
        Map<String, Map<String, Integer>> runtimeStateByBuildingKey = new HashMap<>();

        for (SavedMatchPlayerCardRuntimeStateRecord runtimeStateRecord : aggregate.playerCardRuntimeStates()) {
            String buildingKey = runtimeStateRecord.nickname() + "#" + runtimeStateRecord.cardId();
            runtimeStateByBuildingKey
                    .computeIfAbsent(buildingKey, ignored -> new HashMap<>())
                    .put(runtimeStateRecord.stateKey(), runtimeStateRecord.stateIntValue());
        }

        for (Map.Entry<String, Map<String, Integer>> entry : runtimeStateByBuildingKey.entrySet()) {
            String[] keyParts = entry.getKey().split("#", 2);
            String nickname = keyParts[0];
            int cardId = Integer.parseInt(keyParts[1]);

            BuildingCard buildingCard = buildingsByNicknameAndId.getOrDefault(nickname, Map.of()).get(cardId);
            if (buildingCard == null) {
                throw new SavedMatchSnapshotConsistencyException(
                        "Runtime state references missing player building card: " + entry.getKey()
                );
            }

            buildingCard.restoreRuntimeState(entry.getValue());
        }
    }

    private static Board restoreBoard(
            SavedMatchAggregate aggregate,
            ArrayList<Player> players,
            Map<String, Player> playersByNickname
    ) {
        UpperPile upperPile = new UpperPile(
                toTribeCards(extractBoardCards(aggregate, SavedMatchBoardCardZone.UPPER_TRIBE)),
                toBuildingCards(extractBoardCards(aggregate, SavedMatchBoardCardZone.UPPER_BUILDING))
        );
        LowerPile lowerPile = new LowerPile(
                toTribeCards(extractBoardCards(aggregate, SavedMatchBoardCardZone.LOWER_TRIBE)),
                toBuildingCards(extractBoardCards(aggregate, SavedMatchBoardCardZone.LOWER_BUILDING))
        );
        TribeDeck tribeDeck = TribeDeck.fromOrderedCards(new ArrayList<>(extractBoardCards(aggregate, SavedMatchBoardCardZone.TRIBE_DECK)));
        BuildingDeck buildingDeck = BuildingDeck.fromOrderedCards(new ArrayList<>(extractBoardCards(aggregate, SavedMatchBoardCardZone.BUILDING_DECK)));

        List<OfferSpace> offerSpaces = new ArrayList<>();
        for (SavedMatchOfferSpaceRecord offerSpaceRecord : aggregate.offerSpaces()) {
            OfferSpaceDefinition definition = OfferSpaceDefinition.fromCode(offerSpaceRecord.offerSpaceCode());
            OfferSpace offerSpace = new OfferSpace(
                    definition.positionIndex(),
                    definition.code(),
                    definition.minPlayers(),
                    definition.foodBonus(),
                    definition.topRowCardsToTake(),
                    definition.bottomRowCardsToTake()
            );
            if (offerSpaceRecord.occupiedByNickname() != null) {
                offerSpace.occupy(resolvePlayer(playersByNickname, offerSpaceRecord.occupiedByNickname()));
            }
            offerSpaces.add(offerSpace);
        }

        List<StartingBlock> startingBlocks = new ArrayList<>();
        for (SavedMatchStartingBlockRecord startingBlockRecord : aggregate.startingBlocks()) {
            startingBlocks.add(new StartingBlock(
                    startingBlockRecord.blockPosition(),
                    startingBlockRecord.foodModifier(),
                    startingBlockRecord.prestigeModifier(),
                    startingBlockRecord.occupiedByNickname() == null
                            ? null
                            : resolvePlayer(playersByNickname, startingBlockRecord.occupiedByNickname())
            ));
        }

        StartingTile startingTile = new StartingTile(startingBlocks);
        OfferPath offerPath = new OfferPath(startingTile, offerSpaces);
        return new Board(tribeDeck, buildingDeck, players, upperPile, lowerPile, offerPath);
    }

    private static TurnOrderTrack restoreTurnOrderTrack(
            SavedMatchAggregate aggregate,
            Map<String, Player> playersByNickname
    ) {
        List<Player> turnOrder = aggregate.turnOrder().stream()
                .sorted(Comparator.comparingInt(SavedMatchTurnOrderRecord::turnPosition))
                .map(turnOrderRecord -> resolvePlayer(playersByNickname, turnOrderRecord.nickname()))
                .toList();

        return new TurnOrderTrack(
                turnOrder,
                aggregate.currentPlayerIndex(),
                aggregate.roundNumber(),
                aggregate.roundPhase()
        );
    }

    private static TurnState restoreTurnState(
            SavedMatchAggregate aggregate,
            Map<String, Player> playersByNickname
    ) {
        Map<Player, Integer> remainingUpperChoices = new HashMap<>();
        Map<Player, Integer> remainingLowerChoices = new HashMap<>();

        for (SavedMatchTurnChoiceRecord turnChoiceRecord : aggregate.turnChoices()) {
            Player player = resolvePlayer(playersByNickname, turnChoiceRecord.nickname());
            remainingUpperChoices.put(player, turnChoiceRecord.remainingUpperChoices());
            remainingLowerChoices.put(player, turnChoiceRecord.remainingLowerChoices());
        }

        return new TurnState(
                aggregate.turnPhase(),
                remainingUpperChoices,
                remainingLowerChoices
        );
    }

    private static List<Card> extractBoardCards(SavedMatchAggregate aggregate, SavedMatchBoardCardZone zone) {
        return aggregate.boardCards().stream()
                .filter(boardCardRecord -> boardCardRecord.cardZone() == zone)
                .sorted(Comparator.comparingInt(SavedMatchBoardCardRecord::positionIndex))
                .map(boardCardRecord -> ParserJson.getDeckTemplate().createCardById(boardCardRecord.cardId()))
                .toList();
    }

    private static List<TribeCard> toTribeCards(List<Card> cards) {
        return cards.stream()
                .map(card -> (TribeCard) card)
                .toList();
    }

    private static List<BuildingCard> toBuildingCards(List<Card> cards) {
        return cards.stream()
                .map(card -> (BuildingCard) card)
                .toList();
    }

    private static Player resolvePlayer(Map<String, Player> playersByNickname, String nickname) {
        Player player = playersByNickname.get(nickname);
        if (player == null) {
            throw new SavedMatchSnapshotConsistencyException("Unknown player referenced by saved match: " + nickname);
        }
        return player;
    }

    private static int parseGameId(String gameId) {
        try {
            return Integer.parseInt(gameId);
        } catch (NumberFormatException exception) {
            throw new SavedMatchSnapshotConsistencyException("Saved game id is not a valid integer: " + gameId);
        }
    }

    private record OfferSpaceDefinition(
            int positionIndex,
            String code,
            int minPlayers,
            int foodBonus,
            int topRowCardsToTake,
            int bottomRowCardsToTake
    ) {
        private static OfferSpaceDefinition fromCode(String code) {
            return switch (code) {
                case "A" -> new OfferSpaceDefinition(1, "A", 5, 3, 0, 0);
                case "B" -> new OfferSpaceDefinition(2, "B", 2, 0, 0, 1);
                case "C" -> new OfferSpaceDefinition(3, "C", 2, 0, 1, 0);
                case "D" -> new OfferSpaceDefinition(4, "D", 3, 0, 0, 2);
                case "E" -> new OfferSpaceDefinition(5, "E", 2, 0, 1, 1);
                case "F" -> new OfferSpaceDefinition(6, "F", 2, 0, 2, 0);
                case "G" -> new OfferSpaceDefinition(7, "G", 4, 0, 2, 1);
                default -> throw new SavedMatchSnapshotConsistencyException("Unknown offer-space code: " + code);
            };
        }
    }
}
