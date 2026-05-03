package it.polimi.ingsoft.mesos.server.model.ranking;

import it.polimi.ingsoft.mesos.server.model.ranking.MySqlRankingQueries;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MySqlRankingQueriesTest {

    @Test
    void allSqlQueriesAreDefined() {
        List<String> queries = List.of(
                MySqlRankingQueries.CREATE_USERS_TABLE,
                MySqlRankingQueries.CREATE_USER_RATINGS_TABLE,
                MySqlRankingQueries.CREATE_MATCHES_TABLE,
                MySqlRankingQueries.CREATE_MATCH_RESULTS_TABLE,
                MySqlRankingQueries.INSERT_USER,
                MySqlRankingQueries.INSERT_USER_IF_MISSING,
                MySqlRankingQueries.INSERT_RATING_IF_MISSING,
                MySqlRankingQueries.CHECK_USER_EXISTS,
                MySqlRankingQueries.SELECT_RATING,
                MySqlRankingQueries.SELECT_RATINGS_BY_NICKNAMES_PREFIX,
                MySqlRankingQueries.UPDATE_RATING,
                MySqlRankingQueries.INSERT_MATCH,
                MySqlRankingQueries.INSERT_MATCH_RESULT,
                MySqlRankingQueries.SELECT_GLOBAL_LEADERBOARD,
                MySqlRankingQueries.SELECT_PLAYER_GLOBAL_RANK,
                MySqlRankingQueries.SELECT_MATCH_HISTORY_ALL,
                MySqlRankingQueries.SELECT_MATCH_HISTORY_BY_PLAYER_COUNT
        );

        for (String query : queries) {
            assertFalse(query.isBlank());
        }
    }

    @Test
    void matchResultsQueryEnforcesSupportedPlayerRange() {
        assertTrue(MySqlRankingQueries.CREATE_MATCHES_TABLE.contains("CHECK(player_count BETWEEN 2 AND 5)"));
    }

    @Test
    void matchResultsQueryAllowsNegativeScores() {
        assertFalse(MySqlRankingQueries.CREATE_MATCH_RESULTS_TABLE.contains("CHECK(final_score >= 0)"));
    }

    @Test
    void leaderboardQueryUsesSqlRanking() {
        assertTrue(MySqlRankingQueries.SELECT_GLOBAL_LEADERBOARD.contains("RANK() OVER"));
    }

    @Test
    void insertQueriesUseMySqlConflictHandling() {
        assertTrue(MySqlRankingQueries.INSERT_USER_IF_MISSING.contains("ON DUPLICATE KEY UPDATE"));
        assertTrue(MySqlRankingQueries.INSERT_RATING_IF_MISSING.contains("ON DUPLICATE KEY UPDATE"));
    }

    @Test
    void tableDefinitionsInlineIndexesForIdempotentInitialization() {
        assertTrue(MySqlRankingQueries.CREATE_USER_RATINGS_TABLE.contains("idx_user_ratings_leaderboard"));
        assertTrue(MySqlRankingQueries.CREATE_MATCHES_TABLE.contains("idx_matches_history"));
        assertTrue(MySqlRankingQueries.CREATE_MATCHES_TABLE.contains("idx_matches_player_count"));
        assertTrue(MySqlRankingQueries.CREATE_MATCH_RESULTS_TABLE.contains("idx_match_results_history"));
    }
}
