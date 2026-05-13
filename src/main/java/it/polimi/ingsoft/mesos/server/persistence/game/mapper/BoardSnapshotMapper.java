package it.polimi.ingsoft.mesos.server.persistence.game.mapper;

import it.polimi.ingsoft.mesos.server.model.board.Board;
import it.polimi.ingsoft.mesos.server.model.board.offerpath.OfferSpace;
import it.polimi.ingsoft.mesos.server.model.board.offerpath.StartingBlock;
import it.polimi.ingsoft.mesos.server.model.cards.Card;
import it.polimi.ingsoft.mesos.server.model.cards.BuildingCard;
import it.polimi.ingsoft.mesos.server.model.cards.TribeCard;
import it.polimi.ingsoft.mesos.server.model.player.Player;
import it.polimi.ingsoft.mesos.server.persistence.game.dto.SavedMatchSnapshot;

import java.util.List;

/**
 * Maps board-level information to persistence snapshots.
 */
public final class BoardSnapshotMapper {
    public SavedMatchSnapshot.BoardSnapshot toSnapshot(Board board) {
        if (board == null) {
            throw new IllegalArgumentException("board cannot be null");
        }

        return new SavedMatchSnapshot.BoardSnapshot(
                cardIds(board.getRemainingTribeDeckCards()),
                cardIds(board.getRemainingBuildingDeckCards()),
                tribesIds(board.getUpperRowCards()),
                buildingsIds(board.getUpperRowCards()),
                tribesIds(board.getLowerRowCards()),
                buildingsIds(board.getLowerRowCards()),
                board.getOfferSpaces().stream()
                        .map(this::toSnapshot)
                        .toList(),
                board.getStartingBlocks().stream()
                        .map(this::toSnapshot)
                        .toList()
        );
    }

    private List<Integer> cardIds(List<Card> cards) {
        return cards.stream()
                .map(Card::getId)
                .toList();
    }

    private List<Integer> tribesIds(List<Card> cards) {
        return cards.stream()
                .filter(TribeCard.class::isInstance)
                .map(Card::getId)
                .toList();
    }

    private List<Integer> buildingsIds(List<Card> cards) {
        return cards.stream()
                .filter(BuildingCard.class::isInstance)
                .map(Card::getId)
                .toList();
    }

    private SavedMatchSnapshot.OfferSpaceSnapshot toSnapshot(OfferSpace offerSpace) {
        return new SavedMatchSnapshot.OfferSpaceSnapshot(
                offerSpace.getCode(),
                offerSpace.getPositionIndex(),
                offerSpace.getFoodBonus(),
                offerSpace.getTopRowCardsToTake(),
                offerSpace.getBottomRowCardsToTake(),
                toNickname(offerSpace.getOccupiedBy())
        );
    }

    private SavedMatchSnapshot.StartingBlockSnapshot toSnapshot(StartingBlock startingBlock) {
        return new SavedMatchSnapshot.StartingBlockSnapshot(
                startingBlock.getPosition(),
                startingBlock.getFoodModifier(),
                startingBlock.getPrestigeModifier(),
                toNickname(startingBlock.getOccupiedBy())
        );
    }

    private String toNickname(Player player) {
        return player == null ? null : player.getNickname();
    }
}
