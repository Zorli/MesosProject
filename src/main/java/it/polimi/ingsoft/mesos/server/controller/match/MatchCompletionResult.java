package it.polimi.ingsoft.mesos.server.controller.match;

import it.polimi.ingsoft.mesos.server.model.ranking.RankingResult;
import it.polimi.ingsoft.mesos.shared.payload.server.GamePayload;

import java.util.Objects;
import java.util.Optional;

/**
 * Result returned by the runtime when a match reaches final scoring completion.
 *
 * @param gamePayload final client-facing game payload
 * @param rankingResult optional ranking update produced by the configured runtime
 */
public record MatchCompletionResult(GamePayload gamePayload, Optional<RankingResult> rankingResult) {
    /**
     * Validates and normalizes the completion result.
     */
    public MatchCompletionResult {
        Objects.requireNonNull(gamePayload, "Game payload cannot be null");
        rankingResult = rankingResult == null ? Optional.empty() : rankingResult;
    }
}
