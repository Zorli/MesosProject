package it.polimi.ingsoft.mesos.server.model.ranking;

/**
 * Centralized SQL statements used by the MySQL ranking persistence layer.
 */
final class MySqlRankingQueries {
    /** Creates the table of registered users. */
    static final String CREATE_USERS_TABLE = """
            CREATE TABLE IF NOT EXISTS users (
                nickname VARCHAR(255) NOT NULL,
                created_at DATETIME(6) NOT NULL,
                PRIMARY KEY (nickname)
            ) ENGINE=InnoDB DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_bin
            """;

    /** Creates the table containing global rating aggregates per user. */
    static final String CREATE_USER_RATINGS_TABLE = """
            CREATE TABLE IF NOT EXISTS user_ratings (
                nickname VARCHAR(255) NOT NULL,
                elo INTEGER NOT NULL,
                games_played INTEGER NOT NULL DEFAULT 0,
                wins INTEGER NOT NULL DEFAULT 0,
                win_streak INTEGER NOT NULL DEFAULT 0,
                last_updated DATETIME(6) NOT NULL,
                PRIMARY KEY (nickname),
                CONSTRAINT fk_user_ratings_user FOREIGN KEY(nickname) REFERENCES users(nickname),
                INDEX idx_user_ratings_leaderboard(elo DESC, wins DESC, nickname ASC)
            ) ENGINE=InnoDB DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_bin
            """;

    /** Creates the table containing immutable per-match player results. */
    static final String CREATE_MATCH_RESULTS_TABLE = """
            CREATE TABLE IF NOT EXISTS match_results (
                id BIGINT NOT NULL AUTO_INCREMENT,
                game_id VARCHAR(255) NOT NULL,
                nickname VARCHAR(255) NOT NULL,
                final_score INTEGER NOT NULL,
                placement INTEGER NOT NULL,
                played_at DATETIME(6) NOT NULL,
                player_count INTEGER NOT NULL,
                elo_before INTEGER NOT NULL,
                elo_delta INTEGER NOT NULL,
                elo_after INTEGER NOT NULL,
                win_streak_after INTEGER NOT NULL,
                PRIMARY KEY (id),
                CONSTRAINT fk_match_results_user FOREIGN KEY(nickname) REFERENCES users(nickname),
                CONSTRAINT uq_match_results_game_nickname UNIQUE(game_id, nickname),
                CONSTRAINT chk_match_results_final_score CHECK(final_score >= 0),
                CONSTRAINT chk_match_results_placement CHECK(placement >= 1),
                CONSTRAINT chk_match_results_player_count CHECK(player_count BETWEEN 2 AND 5),
                CONSTRAINT chk_match_results_elo_before CHECK(elo_before >= 0),
                CONSTRAINT chk_match_results_elo_after CHECK(elo_after >= 0),
                CONSTRAINT chk_match_results_win_streak CHECK(win_streak_after >= 0),
                INDEX idx_match_results_history(played_at DESC, game_id ASC, placement ASC),
                INDEX idx_match_results_player_count(player_count, played_at DESC)
            ) ENGINE=InnoDB DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_bin
            """;

    /** Inserts a user row if nickname is not already present. */
    static final String INSERT_USER = """
            INSERT INTO users(nickname, created_at)
            VALUES(?, ?)
            """;

    /** Upserts a user row while keeping existing rows unchanged. */
    static final String INSERT_USER_IF_MISSING = """
            INSERT INTO users(nickname, created_at)
            VALUES(?, ?)
            ON DUPLICATE KEY UPDATE nickname = nickname
            """;

    /** Inserts an initial rating row if nickname does not have one yet. */
    static final String INSERT_RATING_IF_MISSING = """
            INSERT INTO user_ratings(nickname, elo, games_played, wins, win_streak, last_updated)
            VALUES(?, ?, 0, 0, 0, ?)
            ON DUPLICATE KEY UPDATE nickname = nickname
            """;

    /** Checks if a nickname already exists in users table. */
    static final String CHECK_USER_EXISTS =
            "SELECT 1 FROM users WHERE nickname = ?";

    /** Checks if a game id was already persisted. */
    static final String CHECK_GAME_EXISTS =
            "SELECT 1 FROM match_results WHERE game_id = ? LIMIT 1";

    /** Loads a single rating snapshot by nickname. */
    static final String SELECT_RATING = """
            SELECT nickname, elo, games_played, wins, win_streak
            FROM user_ratings
            WHERE nickname = ?
            """;

    /** Prefix used to build IN clause for loading snapshots by nickname set. */
    static final String SELECT_RATINGS_BY_NICKNAMES_PREFIX =
            "SELECT nickname, elo, games_played, wins, win_streak FROM user_ratings WHERE nickname IN (";

    /** Updates aggregated rating state for a player. */
    static final String UPDATE_RATING = """
            UPDATE user_ratings
            SET elo = ?, games_played = ?, wins = ?, win_streak = ?, last_updated = ?
            WHERE nickname = ?
            """;

    /** Inserts one match result row for one player. */
    static final String INSERT_MATCH_RESULT = """
            INSERT INTO match_results(
                game_id, nickname, final_score, placement, played_at, player_count,
                elo_before, elo_delta, elo_after, win_streak_after
            )
            VALUES(?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            """;

    /** Loads full global leaderboard with SQL rank computed by Elo/wins/nickname. */
    static final String SELECT_GLOBAL_LEADERBOARD = """
            SELECT nickname, elo, wins, win_streak, `rank`
            FROM (
                SELECT nickname,
                       elo,
                       wins,
                       win_streak,
                       RANK() OVER (ORDER BY elo DESC, wins DESC, nickname ASC) AS `rank`
                FROM user_ratings
            ) ranked
            ORDER BY `rank` ASC, nickname ASC
            """;

    /** Loads global rank for a single nickname. */
    static final String SELECT_PLAYER_GLOBAL_RANK = """
            SELECT `rank`
            FROM (
                SELECT nickname,
                       RANK() OVER (ORDER BY elo DESC, wins DESC, nickname ASC) AS `rank`
                FROM user_ratings
            ) ranked
            WHERE nickname = ?
            """;

    /** Loads complete match history across all player counts. */
    static final String SELECT_MATCH_HISTORY_ALL = """
            SELECT game_id, played_at, player_count, nickname, final_score,
                   placement, elo_before, elo_delta, elo_after, win_streak_after
            FROM match_results
            ORDER BY played_at DESC, game_id ASC, placement ASC, nickname ASC
            """;

    /** Loads match history filtered by a specific player count. */
    static final String SELECT_MATCH_HISTORY_BY_PLAYER_COUNT = """
            SELECT game_id, played_at, player_count, nickname, final_score,
                   placement, elo_before, elo_delta, elo_after, win_streak_after
            FROM match_results
            WHERE player_count = ?
            ORDER BY played_at DESC, game_id ASC, placement ASC, nickname ASC
            """;

    /** Utility class: not instantiable. */
    private MySqlRankingQueries() {
    }
}
