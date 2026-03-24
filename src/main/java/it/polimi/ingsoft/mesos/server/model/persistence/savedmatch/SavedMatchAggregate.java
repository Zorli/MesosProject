package it.polimi.ingsoft.mesos.server.model.persistence.savedmatch;

import it.polimi.ingsoft.mesos.server.model.GameState.RoundPhase;
import it.polimi.ingsoft.mesos.server.model.enums.Era;
import it.polimi.ingsoft.mesos.server.model.enums.GameStatus;
import it.polimi.ingsoft.mesos.server.model.enums.SavedMatchStatus;
import it.polimi.ingsoft.mesos.server.model.enums.TotemColor;
import it.polimi.ingsoft.mesos.server.model.enums.TurnPhase;

import java.time.OffsetDateTime;
import java.util.List;

/**
 * Fully normalized persisted representation of one resumable match.
 *
 * @param gameId match identifier
 * @param hostNickname nickname of the player that created the original match
 * @param playerCount number of players in the match
 * @param status saved-match lifecycle status
 * @param currentEra current era of the match
 * @param gameStatus lifecycle status of the domain aggregate
 * @param roundNumber current round number
 * @param roundPhase current round phase
 * @param turnPhase current fine-grained turn phase
 * @param activePlayerNickname nickname of the active player, or null when no player is active
 * @param currentPlayerIndex index of the next player inside turn order
 * @param lastCheckpointType last application checkpoint that updated the match
 * @param players persisted player rows
 * @param turnOrder persisted turn-order rows
 * @param turnChoices persisted sparse turn-choice rows
 * @param offerSpaces persisted offer-space rows
 * @param startingBlocks persisted starting-block rows
 * @param boardCards persisted board and deck cards
 * @param playerCards persisted player-owned cards
 * @param playerCardRuntimeStates persisted mutable state for cards with internal counters
 */
public record SavedMatchAggregate(
        String gameId,
        String hostNickname,
        int playerCount,
        SavedMatchStatus status,
        Era currentEra,
        GameStatus gameStatus,
        int roundNumber,
        RoundPhase roundPhase,
        TurnPhase turnPhase,
        String activePlayerNickname,
        int currentPlayerIndex,
        SavedMatchCheckpointType lastCheckpointType,
        List<SavedMatchPlayerRecord> players,
        List<SavedMatchTurnOrderRecord> turnOrder,
        List<SavedMatchTurnChoiceRecord> turnChoices,
        List<SavedMatchOfferSpaceRecord> offerSpaces,
        List<SavedMatchStartingBlockRecord> startingBlocks,
        List<SavedMatchBoardCardRecord> boardCards,
        List<SavedMatchPlayerCardRecord> playerCards,
        List<SavedMatchPlayerCardRuntimeStateRecord> playerCardRuntimeStates
) {
}

/**
 * Persisted player row for one saved match.
 */
record SavedMatchPlayerRecord(
        int playerIndex,
        String nickname,
        TotemColor totemColor,
        int food,
        int prestigePoints,
        boolean online,
        String sessionToken,
        OffsetDateTime lastSeenAt
) {
}

/**
 * Persisted turn-order row for one saved match.
 */
record SavedMatchTurnOrderRecord(int turnPosition, String nickname) {
}

/**
 * Persisted sparse turn-choice row for one saved match.
 */
record SavedMatchTurnChoiceRecord(String nickname, int remainingUpperChoices, int remainingLowerChoices) {
}

/**
 * Persisted offer-space row for one saved match.
 */
record SavedMatchOfferSpaceRecord(String offerSpaceCode, int positionIndex, String occupiedByNickname) {
}

/**
 * Persisted starting-block row for one saved match.
 */
record SavedMatchStartingBlockRecord(int blockPosition, int foodModifier, int prestigeModifier, String occupiedByNickname) {
}

/**
 * Persisted board/deck card row for one saved match.
 */
record SavedMatchBoardCardRecord(SavedMatchBoardCardZone cardZone, int positionIndex, int cardId) {
}

/**
 * Persisted player-owned card row for one saved match.
 */
record SavedMatchPlayerCardRecord(String nickname, SavedMatchPlayerCardZone cardZone, int positionIndex, int cardId) {
}

/**
 * Persisted runtime-state row for one mutable player-owned card.
 */
record SavedMatchPlayerCardRuntimeStateRecord(
        String nickname,
        int cardId,
        String stateKey,
        Integer stateIntValue,
        String stateTextValue
) {
}
