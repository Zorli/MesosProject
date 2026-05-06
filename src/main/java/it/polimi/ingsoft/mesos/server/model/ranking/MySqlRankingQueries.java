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

    /** Creates the table containing one immutable header row per completed match. */
    static final String CREATE_MATCHES_TABLE = """
            CREATE TABLE IF NOT EXISTS matches (
                game_id VARCHAR(255) NOT NULL,
                played_at DATETIME(6) NOT NULL,
                player_count INTEGER NOT NULL,
                PRIMARY KEY (game_id),
                CONSTRAINT chk_matches_player_count CHECK(player_count BETWEEN 2 AND 5),
                INDEX idx_matches_history(played_at DESC, game_id ASC),
                INDEX idx_matches_player_count(player_count, played_at DESC)
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
                elo_before INTEGER NOT NULL,
                elo_delta INTEGER NOT NULL,
                elo_after INTEGER NOT NULL,
                previous_global_rank INTEGER NOT NULL,
                new_global_rank INTEGER NOT NULL,
                win_streak_after INTEGER NOT NULL,
                PRIMARY KEY (id),
                CONSTRAINT fk_match_results_match FOREIGN KEY(game_id) REFERENCES matches(game_id),
                CONSTRAINT fk_match_results_user FOREIGN KEY(nickname) REFERENCES users(nickname),
                CONSTRAINT uq_match_results_game_nickname UNIQUE(game_id, nickname),
                CONSTRAINT chk_match_results_placement CHECK(placement >= 1),
                CONSTRAINT chk_match_results_elo_before CHECK(elo_before >= 0),
                CONSTRAINT chk_match_results_elo_after CHECK(elo_after >= 0),
                CONSTRAINT chk_match_results_previous_rank CHECK(previous_global_rank >= 1),
                CONSTRAINT chk_match_results_new_rank CHECK(new_global_rank >= 1),
                CONSTRAINT chk_match_results_win_streak CHECK(win_streak_after >= 0),
                INDEX idx_match_results_history(game_id ASC, placement ASC, nickname ASC)
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

    /** Inserts one immutable match header row. */
    static final String INSERT_MATCH = """
            INSERT INTO matches(game_id, played_at, player_count)
            VALUES(?, ?, ?)
            """;

    /** Inserts one match result row for one player. */
    static final String INSERT_MATCH_RESULT = """
            INSERT INTO match_results(
                game_id, nickname, final_score, placement,
                elo_before, elo_delta, elo_after,
                previous_global_rank, new_global_rank, win_streak_after
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
            SELECT m.game_id, m.played_at, m.player_count, r.nickname, r.final_score,
                   r.placement, r.elo_before, r.elo_delta, r.elo_after,
                   r.previous_global_rank, r.new_global_rank, r.win_streak_after
            FROM matches m
            JOIN match_results r ON r.game_id = m.game_id
            ORDER BY m.played_at DESC, m.game_id ASC, r.placement ASC, r.nickname ASC
            """;

    /** Loads match history filtered by a specific player count. */
    static final String SELECT_MATCH_HISTORY_BY_PLAYER_COUNT = """
            SELECT m.game_id, m.played_at, m.player_count, r.nickname, r.final_score,
                   r.placement, r.elo_before, r.elo_delta, r.elo_after,
                   r.previous_global_rank, r.new_global_rank, r.win_streak_after
            FROM matches m
            JOIN match_results r ON r.game_id = m.game_id
            WHERE m.player_count = ?
            ORDER BY m.played_at DESC, m.game_id ASC, r.placement ASC, r.nickname ASC
            """;

    /** Checks whether a match already exists for a given game id. */
    static final String EXISTS_MATCH_BY_ID = """
            SELECT 1
            FROM matches
            WHERE game_id = ?
            LIMIT 1
            """;

    /** Utility class: not instantiable. */
    private MySqlRankingQueries() {
    }
}
