package it.polimi.ingsoft.mesos.server.persistence.game;

import java.util.List;
import java.util.Map;

public record GameCheckpoint(
        int schemaVersion,
        String savedAtUtc,
        GameData game,
        List<PlayerData> players,
        BoardData board,
        TurnOrderData turnOrder,
        TurnStateData turnState
) {
    public record GameData(int id, String hostNickname, String status, String currentEra, long stateVersion) {
    }

    public record PlayerData(String nickname, String totem, int food, int prestigePoints, TribeData tribe) {
    }

    public record TribeData(List<Integer> characters, List<CardData> buildings) {
    }

    public record CardData(int id, Map<String, Integer> runtimeState) {
    }

    public record BoardData(
            List<Integer> remainingTribeDeck,
            List<Integer> remainingBuildingDeck,
            List<Integer> upperTribeCards,
            List<Integer> upperBuildingCards,
            List<Integer> lowerTribeCards,
            List<Integer> lowerBuildingCards,
            List<OfferSpaceData> offerSpaces,
            List<StartingBlockData> startingBlocks
    ) {
    }

    public record OfferSpaceData(
            String code,
            int positionIndex,
            int foodBonus,
            int topRowCardsToTake,
            int bottomRowCardsToTake,
            String occupiedByNickname
    ) {
    }

    public record StartingBlockData(
            int position,
            int foodModifier,
            int prestigeModifier,
            String occupiedByNickname
    ) {
    }

    public record TurnOrderData(
            List<String> nicknames,
            int currentPlayerIndex,
            int roundNumber,
            String currentRoundPhase
    ) {
    }

    public record TurnStateData(
            String phase,
            Map<String, Integer> remainingUpperChoicesByNickname,
            Map<String, Integer> remainingLowerChoicesByNickname
    ) {
    }
}
