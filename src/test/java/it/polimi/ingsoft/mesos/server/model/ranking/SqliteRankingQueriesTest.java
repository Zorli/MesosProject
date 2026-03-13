package it.polimi.ingsoft.mesos.server.model.ranking;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SqliteRankingQueriesTest {

    @Test
    void allSqlQueriesAreDefined() {
        List<String> queries = List.of(
                SqliteRankingQueries.CREATE_USERS_TABLE,
                SqliteRankingQueries.CREATE_USER_RATINGS_TABLE,
                SqliteRankingQueries.CREATE_MATCH_RESULTS_TABLE,
                SqliteRankingQueries.CREATE_RATINGS_INDEX,
                SqliteRankingQueries.CREATE_MATCH_HISTORY_INDEX,
                SqliteRankingQueries.CREATE_MATCH_PLAYER_COUNT_INDEX,
                SqliteRankingQueries.INSERT_USER_IF_MISSING,
                SqliteRankingQueries.INSERT_RATING_IF_MISSING,
                SqliteRankingQueries.CHECK_USER_EXISTS,
                SqliteRankingQueries.CHECK_GAME_EXISTS,
                SqliteRankingQueries.SELECT_RATING,
                SqliteRankingQueries.SELECT_RATINGS_BY_NICKNAMES_PREFIX,
                SqliteRankingQueries.UPDATE_RATING,
                SqliteRankingQueries.INSERT_MATCH_RESULT,
                SqliteRankingQueries.SELECT_GLOBAL_LEADERBOARD,
                SqliteRankingQueries.SELECT_PLAYER_GLOBAL_RANK,
                SqliteRankingQueries.SELECT_MATCH_HISTORY_ALL,
                SqliteRankingQueries.SELECT_MATCH_HISTORY_BY_PLAYER_COUNT
        );

        for (String query : queries) {
            assertFalse(query.isBlank());
        }
    }

    @Test
    void matchResultsQueryEnforcesSupportedPlayerRange() {
        assertTrue(SqliteRankingQueries.CREATE_MATCH_RESULTS_TABLE.contains("CHECK(player_count BETWEEN 2 AND 5)"));
    }

    @Test
    void leaderboardQueryUsesSqlRanking() {
        assertTrue(SqliteRankingQueries.SELECT_GLOBAL_LEADERBOARD.contains("RANK() OVER"));
    }
}
