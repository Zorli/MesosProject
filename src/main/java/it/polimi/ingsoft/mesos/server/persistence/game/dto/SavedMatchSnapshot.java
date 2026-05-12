package it.polimi.ingsoft.mesos.server.persistence.game.dto;

import java.util.List;
import java.util.Map;

/**
 * JSON-friendly immutable representation of an in-progress or recently completed match.
 */
public record SavedMatchSnapshot(
        int schemaVersion,
        String savedAtUtc,
        GameSnapshot game,
        List<PlayerSnapshot> players,
        BoardSnapshot board,
        TurnOrderSnapshot turnOrder,
        TurnStateSnapshot turnState
) {
    /**
     * Snapshot of the game aggregate root.
     */
    public record GameSnapshot(
            int id,
            String hostNickname,
            String status,
            String currentEra,
            long stateVersion
    ) {
    }

    /**
     * Snapshot of one player.
     */
    public record PlayerSnapshot(
            String nickname,
            String totem,
            int food,
            int prestigePoints,
            TribeSnapshot tribe
    ) {
    }

    /**
     * Snapshot of player tribe.
     */
    public record TribeSnapshot(
            List<Integer> characters,
            List<SavedCardSnapshot> buildings
    ) {
    }

    /**
     * Snapshot of deck and board state.
     */
    public record BoardSnapshot(
            List<Integer> remainingTribeDeck,
            List<Integer> remainingBuildingDeck,
            List<Integer> upperTribeCards,
            List<Integer> upperBuildingCards,
            List<Integer> lowerTribeCards,
            List<Integer> lowerBuildingCards,
            List<OfferSpaceSnapshot> offerSpaces,
            List<StartingBlockSnapshot> startingBlocks
    ) {
    }

    /**
     * Snapshot of one offer space.
     */
    public record OfferSpaceSnapshot(
            String code,
            int positionIndex,
            int foodBonus,
            int topRowCardsToTake,
            int bottomRowCardsToTake,
            String occupiedByNickname
    ) {
    }

    /**
     * Snapshot of one starting block.
     */
    public record StartingBlockSnapshot(
            int position,
            int foodModifier,
            int prestigeModifier,
            String occupiedByNickname
    ) {
    }

    /**
     * Snapshot of player round-turn order.
     */
    public record TurnOrderSnapshot(
            List<String> nicknames,
            int currentPlayerIndex,
            int roundNumber,
            String currentRoundPhase
    ) {
    }

    /**
     * Snapshot of draw-phase state.
     */
    public record TurnStateSnapshot(
            String phase,
            Map<String, Integer> remainingUpperChoicesByNickname,
            Map<String, Integer> remainingLowerChoicesByNickname
    ) {
    }
}
