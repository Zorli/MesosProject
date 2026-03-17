package it.polimi.ingsoft.mesos.server.model.ranking;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PostgresRankingQueriesTest {

    @Test
    void allSqlQueriesAreDefined() {
        List<String> queries = List.of(
                PostgresRankingQueries.CREATE_USERS_TABLE,
                PostgresRankingQueries.CREATE_USER_RATINGS_TABLE,
                PostgresRankingQueries.CREATE_MATCH_RESULTS_TABLE,
                PostgresRankingQueries.CREATE_RATINGS_INDEX,
                PostgresRankingQueries.CREATE_MATCH_HISTORY_INDEX,
                PostgresRankingQueries.CREATE_MATCH_PLAYER_COUNT_INDEX,
                PostgresRankingQueries.INSERT_USER_IF_MISSING,
                PostgresRankingQueries.INSERT_RATING_IF_MISSING,
                PostgresRankingQueries.CHECK_USER_EXISTS,
                PostgresRankingQueries.CHECK_GAME_EXISTS,
                PostgresRankingQueries.SELECT_RATING,
                PostgresRankingQueries.SELECT_RATINGS_BY_NICKNAMES_PREFIX,
                PostgresRankingQueries.UPDATE_RATING,
                PostgresRankingQueries.INSERT_MATCH_RESULT,
                PostgresRankingQueries.SELECT_GLOBAL_LEADERBOARD,
                PostgresRankingQueries.SELECT_PLAYER_GLOBAL_RANK,
                PostgresRankingQueries.SELECT_MATCH_HISTORY_ALL,
                PostgresRankingQueries.SELECT_MATCH_HISTORY_BY_PLAYER_COUNT
        );

        for (String query : queries) {
            assertFalse(query.isBlank());
        }
    }

    @Test
    void matchResultsQueryEnforcesSupportedPlayerRange() {
        assertTrue(PostgresRankingQueries.CREATE_MATCH_RESULTS_TABLE.contains("CHECK(player_count BETWEEN 2 AND 5)"));
    }

    @Test
    void leaderboardQueryUsesSqlRanking() {
        assertTrue(PostgresRankingQueries.SELECT_GLOBAL_LEADERBOARD.contains("RANK() OVER"));
    }

    @Test
    void insertQueriesUsePostgresConflictHandling() {
        assertTrue(PostgresRankingQueries.INSERT_USER_IF_MISSING.contains("ON CONFLICT"));
        assertTrue(PostgresRankingQueries.INSERT_RATING_IF_MISSING.contains("ON CONFLICT"));
    }
}
