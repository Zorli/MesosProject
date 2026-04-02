package it.polimi.ingsoft.mesos.server.model.persistence.savedmatch;

import it.polimi.ingsoft.mesos.server.model.game.Game;
import it.polimi.ingsoft.mesos.server.model.cards.BuildingCard;
import it.polimi.ingsoft.mesos.server.model.cards.Card;
import it.polimi.ingsoft.mesos.server.model.cards.characters.CharacterCard;
import it.polimi.ingsoft.mesos.server.model.enums.SavedMatchStatus;
import it.polimi.ingsoft.mesos.server.model.player.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Translates the live domain aggregate into the normalized saved-match persistence model.
 */
final class SavedMatchMapper {
    /**
     * Prevents instantiation of the mapping utility class.
     */
    private SavedMatchMapper() {
    }

    /**
     * Builds the normalized persistence aggregate for one live game.
     *
     * @param game game to persist
     * @param hostNickname original host nickname
     * @param checkpointType checkpoint responsible for the save
     * @return normalized persistence aggregate
     */
    static SavedMatchAggregate fromGame(Game game, String hostNickname, SavedMatchCheckpointType checkpointType) {
        List<Player> players = game.getPlayers();
        List<SavedMatchPlayerRecord> playerRecords = new ArrayList<>();
        List<SavedMatchPlayerCardRecord> playerCardRecords = new ArrayList<>();
        List<SavedMatchPlayerCardRuntimeStateRecord> playerCardRuntimeStates = new ArrayList<>();

        for (int playerIndex = 0; playerIndex < players.size(); playerIndex++) {
            Player player = players.get(playerIndex);
            playerRecords.add(new SavedMatchPlayerRecord(
                    playerIndex,
                    player.getNickname(),
                    player.getTotem(),
                    player.getFood(),
                    player.getPrestigePoints(),
                    false,
                    null,
                    null
            ));

            int characterPosition = 0;
            for (CharacterCard characterCard : player.getTribe().getCharacters()) {
                playerCardRecords.add(new SavedMatchPlayerCardRecord(
                        player.getNickname(),
                        SavedMatchPlayerCardZone.TRIBE_CHARACTER,
                        characterPosition++,
                        characterCard.getId()
                ));
            }

            int buildingPosition = 0;
            for (BuildingCard buildingCard : player.getTribe().getBuildings()) {
                playerCardRecords.add(new SavedMatchPlayerCardRecord(
                        player.getNickname(),
                        SavedMatchPlayerCardZone.TRIBE_BUILDING,
                        buildingPosition++,
                        buildingCard.getId()
                ));

                for (Map.Entry<String, Integer> runtimeStateEntry : buildingCard.exportRuntimeState().entrySet()) {
                    playerCardRuntimeStates.add(new SavedMatchPlayerCardRuntimeStateRecord(
                            player.getNickname(),
                            buildingCard.getId(),
                            runtimeStateEntry.getKey(),
                            runtimeStateEntry.getValue(),
                            null
                    ));
                }
            }
        }

        List<SavedMatchTurnOrderRecord> turnOrderRecords = new ArrayList<>();
        List<Player> turnOrder = game.getTurnOrderTrack().getTurnOrder();
        for (int position = 0; position < turnOrder.size(); position++) {
            turnOrderRecords.add(new SavedMatchTurnOrderRecord(position, turnOrder.get(position).getNickname()));
        }

        List<SavedMatchTurnChoiceRecord> turnChoiceRecords = new ArrayList<>();
        for (Map.Entry<Player, Integer> entry : game.getTurnState().getRemainingUpperChoicesByPlayer().entrySet()) {
            Player player = entry.getKey();
            turnChoiceRecords.add(new SavedMatchTurnChoiceRecord(
                    player.getNickname(),
                    entry.getValue(),
                    game.getTurnState().getRemainingLowerChoices(player)
            ));
        }

        List<SavedMatchOfferSpaceRecord> offerSpaceRecords = game.getBoard().getOfferSpaces().stream()
                .map(space -> new SavedMatchOfferSpaceRecord(
                        space.getCode(),
                        space.getPositionIndex(),
                        space.getOccupiedBy() == null ? null : space.getOccupiedBy().getNickname()
                ))
                .toList();

        List<SavedMatchStartingBlockRecord> startingBlockRecords = game.getBoard().getStartingBlocks().stream()
                .map(block -> new SavedMatchStartingBlockRecord(
                        block.getPosition(),
                        block.getFoodModifier(),
                        block.getPrestigeModifier(),
                        block.getOccupiedBy() == null ? null : block.getOccupiedBy().getNickname()
                ))
                .toList();

        List<SavedMatchBoardCardRecord> boardCardRecords = new ArrayList<>();
        appendBoardCardRecords(boardCardRecords, SavedMatchBoardCardZone.UPPER_TRIBE, extractNonBuildingCards(game.getBoard().getUpperRowCards()));
        appendBoardCardRecords(boardCardRecords, SavedMatchBoardCardZone.UPPER_BUILDING, extractBuildingCards(game.getBoard().getUpperRowCards()));
        appendBoardCardRecords(boardCardRecords, SavedMatchBoardCardZone.LOWER_TRIBE, extractNonBuildingCards(game.getBoard().getLowerRowCards()));
        appendBoardCardRecords(boardCardRecords, SavedMatchBoardCardZone.LOWER_BUILDING, extractBuildingCards(game.getBoard().getLowerRowCards()));
        appendBoardCardRecords(boardCardRecords, SavedMatchBoardCardZone.TRIBE_DECK, game.getBoard().getRemainingTribeDeckCards());
        appendBoardCardRecords(boardCardRecords, SavedMatchBoardCardZone.BUILDING_DECK, game.getBoard().getRemainingBuildingDeckCards());

        SavedMatchAggregate aggregate = new SavedMatchAggregate(
                String.valueOf(game.getId()),
                hostNickname,
                players.size(),
                game.getStatus() == it.polimi.ingsoft.mesos.server.model.enums.GameStatus.FINISHED
                        ? SavedMatchStatus.COMPLETED
                        : SavedMatchStatus.IN_PROGRESS,
                game.getCurrentEra(),
                game.getStatus(),
                game.getTurnOrderTrack().getRoundNumber(),
                game.getTurnOrderTrack().getCurrentRoundPhase(),
                game.getTurnState().getPhase(),
                game.getTurnState().isWaitingForCardSelection() ? game.getTurnOrderTrack().getCurrentPlayer().getNickname() : null,
                game.getTurnOrderTrack().getCurrentPlayerIndex(),
                checkpointType,
                playerRecords,
                turnOrderRecords,
                turnChoiceRecords,
                offerSpaceRecords,
                startingBlockRecords,
                boardCardRecords,
                playerCardRecords,
                playerCardRuntimeStates
        );

        SavedMatchConsistencyChecker.validate(aggregate);
        return aggregate;
    }

    /**
     * Extracts only non-building cards from a mixed board-card list.
     *
     * @param cards mixed board-card list
     * @return filtered list containing only tribe or event cards
     */
    private static List<Card> extractNonBuildingCards(List<Card> cards) {
        List<Card> filteredCards = new ArrayList<>();
        for (Card card : cards) {
            if (!(card instanceof BuildingCard)) {
                filteredCards.add(card);
            }
        }
        return filteredCards;
    }

    /**
     * Extracts only building cards from a mixed board-card list.
     *
     * @param cards mixed board-card list
     * @return filtered list containing only building cards
     */
    private static List<Card> extractBuildingCards(List<Card> cards) {
        List<Card> filteredCards = new ArrayList<>();
        for (Card card : cards) {
            if (card instanceof BuildingCard) {
                filteredCards.add(card);
            }
        }
        return filteredCards;
    }

    /**
     * Appends normalized board-card rows using the supplied zone and list order.
     *
     * @param target destination list for normalized records
     * @param zone persistence zone assigned to every appended card
     * @param cards ordered cards to serialize
     */
    private static void appendBoardCardRecords(List<SavedMatchBoardCardRecord> target,
                                               SavedMatchBoardCardZone zone,
                                               List<Card> cards) {
        for (int position = 0; position < cards.size(); position++) {
            target.add(new SavedMatchBoardCardRecord(zone, position, cards.get(position).getId()));
        }
    }
}
