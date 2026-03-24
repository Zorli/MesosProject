package it.polimi.ingsoft.mesos.server.controller.ranking;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * PostgreSQL implementation of {@link RankingService}.
 *
 * <p>Responsibilities:
 * validate input, persist completed matches atomically, compute Elo updates,
 * and expose leaderboard and match history queries.
 */
public class PostgresRankingService implements RankingService {
    private final RankingConfig config;
    private final PostgresDatabaseSupport database;
    private final RankingInputValidator validator;
    private final EloRatingCalculator eloCalculator;

    /**
     * Creates the service with default ranking configuration.
     *
     * @param connectionConfig PostgreSQL connection settings
     */
    public PostgresRankingService(PostgresConnectionConfig connectionConfig) {
        this(connectionConfig, RankingConfig.defaultConfig());
    }

    /**
     * Creates the service with default ranking settings and a custom retry limit.
     *
     * @param connectionConfig PostgreSQL connection settings
     * @param maxBusyRetries maximum retries for transient database operations
     */
    public PostgresRankingService(PostgresConnectionConfig connectionConfig, int maxBusyRetries) {
        this(connectionConfig, defaultConfigWithRetries(maxBusyRetries));
    }

    /**
     * Creates the service with a fully custom ranking configuration.
     *
     * @param connectionConfig PostgreSQL connection settings
     * @param config ranking configuration
     */
    public PostgresRankingService(PostgresConnectionConfig connectionConfig, RankingConfig config) {
        this.config = config;
        this.database = new PostgresDatabaseSupport(
                connectionConfig,
                config.maxBusyRetries(),
                List.of(PostgresRankingSchemaModule.getInstance())
        );
        this.validator = new RankingInputValidator(config.minPlayers(), config.maxPlayers());
        this.eloCalculator = new EloRatingCalculator(
                config.minPlayers(),
                config.baseK(),
                config.kStepPerExtraPlayer(),
                config.streakBonusStep(),
                config.streakBonusCap(),
                config.minElo()
        );
        this.database.initializeSchema();
    }

    /**
     * Produces a copy of default configuration overriding only busy retries.
     *
     * @param maxBusyRetries retry limit to inject into the default configuration
     * @return default ranking configuration with the requested retry limit
     */
    private static RankingConfig defaultConfigWithRetries(int maxBusyRetries) {
        RankingConfig baseConfig = RankingConfig.defaultConfig();

        return new RankingConfig(
                baseConfig.minPlayers(),
                baseConfig.maxPlayers(),
                baseConfig.initialElo(),
                baseConfig.minElo(),
                baseConfig.baseK(),
                baseConfig.kStepPerExtraPlayer(),
                baseConfig.streakBonusStep(),
                baseConfig.streakBonusCap(),
                maxBusyRetries
        );
    }

    /** {@inheritDoc} */
    @Override
    public boolean registerUser(String nickname) {
        String normalizedNickname = this.validator.normalizeNickname(nickname);

        return this.database.executeWithRetry(() -> {
            try (Connection connection = this.database.openConnection()) {
                boolean inserted;

                try (PreparedStatement statement = connection.prepareStatement(PostgresRankingQueries.INSERT_USER_IF_MISSING)) {
                    statement.setString(1, normalizedNickname);
                    statement.setObject(2, this.currentTimestampUtc());
                    inserted = statement.executeUpdate() > 0;
                }

                this.ensureRatingExists(connection, normalizedNickname);
                return inserted;
            }
        });
    }

    /** {@inheritDoc} */
    @Override
    public boolean isNicknameAvailable(String nickname) {
        String normalizedNickname = this.validator.normalizeNickname(nickname);

        return this.database.executeWithRetry(() -> {
            try (Connection connection = this.database.openConnection();
                 PreparedStatement statement = connection.prepareStatement(PostgresRankingQueries.CHECK_USER_EXISTS)) {
                statement.setString(1, normalizedNickname);

                try (ResultSet resultSet = statement.executeQuery()) {
                    return !resultSet.next();
                }
            }
        });
    }

    /** {@inheritDoc} */
    @Override
    public RankingResult recordMatchAndGetRanking(String gameId,
                                                  int playerCount,
                                                  Instant playedAtUtc,
                                                  String requestingNickname,
                                                  List<MatchScore> finalScores) {
        String normalizedGameId = this.validator.normalizeGameId(gameId);
        int normalizedPlayerCount = this.validator.validatePlayerCount(playerCount);
        Instant normalizedPlayedAt = this.validator.normalizePlayedAt(playedAtUtc);
        String normalizedRequestingNickname = this.validator.normalizeNickname(requestingNickname);
        List<MatchScore> normalizedScores = this.validator.normalizeScores(finalScores, normalizedPlayerCount);

        boolean requesterIncluded = normalizedScores.stream()
                .anyMatch(score -> score.nickname().equals(normalizedRequestingNickname));

        if (!requesterIncluded) {
            throw new IllegalArgumentException("Requesting nickname is not present in final scores");
        }

        return this.database.executeWithRetry(() -> this.executeMatchTransaction(
                normalizedGameId,
                normalizedPlayerCount,
                normalizedPlayedAt,
                normalizedRequestingNickname,
                normalizedScores
        ));
    }

    /** {@inheritDoc} */
    @Override
    public List<LeaderboardEntry> getLeaderboard() {
        return this.database.executeWithRetry(() -> {
            try (Connection connection = this.database.openConnection()) {
                return this.loadGlobalLeaderboard(connection);
            }
        });
    }

    /** {@inheritDoc} */
    @Override
    public List<MatchHistory> getMatchHistory() {
        return this.database.executeWithRetry(() -> {
            try (Connection connection = this.database.openConnection()) {
                return this.loadMatchHistory(connection);
            }
        });
    }

    /** {@inheritDoc} */
    @Override
    public List<MatchHistory> getMatchHistory(int playerCount) {
        int normalizedPlayerCount = this.validator.validatePlayerCount(playerCount);

        return this.database.executeWithRetry(() -> {
            try (Connection connection = this.database.openConnection()) {
                return this.loadMatchHistory(connection, normalizedPlayerCount);
            }
        });
    }

    /**
     * Executes match persistence and ranking computation in a single transaction.
     */
    private RankingResult executeMatchTransaction(String gameId,
                                                  int playerCount,
                                                  Instant playedAtUtc,
                                                  String requestingNickname,
                                                  List<MatchScore> finalScores) throws SQLException {
        try (Connection connection = this.database.openConnection()) {
            connection.setAutoCommit(false);

            try {
                if (this.gameAlreadyRecorded(connection, gameId)) {
                    throw new DuplicateGameException(gameId);
                }

                this.ensureUsersAndRatingsExist(connection, finalScores);

                Map<String, Integer> placements = this.eloCalculator.computePlacements(finalScores);
                Map<String, RatingSnapshot> snapshots = this.loadRatingSnapshots(connection, finalScores);
                List<PlayerMatchUpdate> updates = this.eloCalculator.computeUpdates(
                        finalScores,
                        placements,
                        snapshots,
                        playerCount
                );

                this.persistEloUpdatesAndMatchRows(connection, gameId, playedAtUtc, playerCount, updates);

                List<LeaderboardEntry> leaderboard = this.loadGlobalLeaderboard(connection);
                int playerRank = this.loadGlobalRankForNickname(connection, requestingNickname);

                connection.commit();
                return new RankingResult(playerRank, leaderboard);
            } catch (Exception exception) {
                connection.rollback();

                if (exception instanceof SQLException sqlException) {
                    throw sqlException;
                }
                if (exception instanceof IllegalStateException illegalStateException) {
                    throw illegalStateException;
                }

                throw new RankingDataException("Cannot record match and compute global elo ranking");
            }
        }
    }

    /** Returns whether the given game id already exists in persisted match rows. */
    private boolean gameAlreadyRecorded(Connection connection, String gameId) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(PostgresRankingQueries.CHECK_GAME_EXISTS)) {
            statement.setString(1, gameId);

            try (ResultSet resultSet = statement.executeQuery()) {
                return resultSet.next();
            }
        }
    }

    /** Ensures that both user and rating rows exist for all final score nicknames. */
    private void ensureUsersAndRatingsExist(Connection connection, List<MatchScore> finalScores) throws SQLException {
        try (PreparedStatement userStatement = connection.prepareStatement(PostgresRankingQueries.INSERT_USER_IF_MISSING);
             PreparedStatement ratingStatement = connection.prepareStatement(PostgresRankingQueries.INSERT_RATING_IF_MISSING)) {
            OffsetDateTime now = this.currentTimestampUtc();

            for (MatchScore score : finalScores) {
                userStatement.setString(1, score.nickname());
                userStatement.setObject(2, now);
                userStatement.addBatch();

                ratingStatement.setString(1, score.nickname());
                ratingStatement.setInt(2, this.config.initialElo());
                ratingStatement.setObject(3, now);
                ratingStatement.addBatch();
            }

            userStatement.executeBatch();
            ratingStatement.executeBatch();
        }
    }

    /** Loads rating snapshots for all match players preserving input score order. */
    private Map<String, RatingSnapshot> loadRatingSnapshots(Connection connection,
                                                            List<MatchScore> finalScores) throws SQLException {
        String placeholders = String.join(",", java.util.Collections.nCopies(finalScores.size(), "?"));
        String query = PostgresRankingQueries.SELECT_RATINGS_BY_NICKNAMES_PREFIX + placeholders + ")";

        Map<String, RatingSnapshot> snapshotsByNickname = new LinkedHashMap<>();

        try (PreparedStatement statement = connection.prepareStatement(query)) {
            int index = 1;
            for (MatchScore score : finalScores) {
                statement.setString(index++, score.nickname());
            }

            try (ResultSet resultSet = statement.executeQuery()) {
                while (resultSet.next()) {
                    String nickname = resultSet.getString("nickname");
                    snapshotsByNickname.put(nickname, new RatingSnapshot(
                            nickname,
                            resultSet.getInt("elo"),
                            resultSet.getInt("games_played"),
                            resultSet.getInt("wins"),
                            resultSet.getInt("win_streak")
                    ));
                }
            }
        }

        Map<String, RatingSnapshot> orderedSnapshots = new LinkedHashMap<>();
        for (MatchScore score : finalScores) {
            RatingSnapshot snapshot = snapshotsByNickname.get(score.nickname());
            if (snapshot == null) {
                throw new RankingDataException("Rating row not found for nickname: " + score.nickname());
            }
            orderedSnapshots.put(score.nickname(), snapshot);
        }

        return orderedSnapshots;
    }

    /** Persists aggregated ratings and immutable match result rows in batch mode. */
    private void persistEloUpdatesAndMatchRows(Connection connection,
                                               String gameId,
                                               Instant playedAtUtc,
                                               int playerCount,
                                               List<PlayerMatchUpdate> updates) throws SQLException {
        try (PreparedStatement updateRating = connection.prepareStatement(PostgresRankingQueries.UPDATE_RATING);
             PreparedStatement insertMatch = connection.prepareStatement(PostgresRankingQueries.INSERT_MATCH_RESULT)) {
            OffsetDateTime playedAt = this.toOffsetDateTime(playedAtUtc);

            for (PlayerMatchUpdate update : updates) {
                updateRating.setInt(1, update.eloAfter());
                updateRating.setInt(2, update.gamesAfter());
                updateRating.setInt(3, update.winsAfter());
                updateRating.setInt(4, update.winStreakAfter());
                updateRating.setObject(5, playedAt);
                updateRating.setString(6, update.nickname());
                updateRating.addBatch();

                insertMatch.setString(1, gameId);
                insertMatch.setString(2, update.nickname());
                insertMatch.setInt(3, update.finalScore());
                insertMatch.setInt(4, update.placement());
                insertMatch.setObject(5, playedAt);
                insertMatch.setInt(6, playerCount);
                insertMatch.setInt(7, update.eloBefore());
                insertMatch.setInt(8, update.eloDelta());
                insertMatch.setInt(9, update.eloAfter());
                insertMatch.setInt(10, update.winStreakAfter());
                insertMatch.addBatch();
            }

            updateRating.executeBatch();
            insertMatch.executeBatch();
        }
    }

    /** Loads the complete global leaderboard ordered by rank. */
    private List<LeaderboardEntry> loadGlobalLeaderboard(Connection connection) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(PostgresRankingQueries.SELECT_GLOBAL_LEADERBOARD);
             ResultSet resultSet = statement.executeQuery()) {
            List<LeaderboardEntry> leaderboard = new ArrayList<>();

            while (resultSet.next()) {
                leaderboard.add(new LeaderboardEntry(
                        resultSet.getString("nickname"),
                        resultSet.getInt("elo"),
                        resultSet.getInt("wins"),
                        resultSet.getInt("win_streak"),
                        resultSet.getInt("rank")
                ));
            }

            return leaderboard;
        }
    }

    /** Loads global rank for one nickname and fails if player is missing. */
    private int loadGlobalRankForNickname(Connection connection, String nickname) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(PostgresRankingQueries.SELECT_PLAYER_GLOBAL_RANK)) {
            statement.setString(1, nickname);

            try (ResultSet resultSet = statement.executeQuery()) {
                if (!resultSet.next()) {
                    throw new RankingDataException("Global rank not found for nickname: " + nickname);
                }

                return resultSet.getInt("rank");
            }
        }
    }

    /** Ensures a rating aggregate row exists for a nickname. */
    private void ensureRatingExists(Connection connection, String nickname) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(PostgresRankingQueries.INSERT_RATING_IF_MISSING)) {
            statement.setString(1, nickname);
            statement.setInt(2, this.config.initialElo());
            statement.setObject(3, this.currentTimestampUtc());
            statement.executeUpdate();
        }
    }

    /** Loads full match history without player count filter. */
    private List<MatchHistory> loadMatchHistory(Connection connection) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(PostgresRankingQueries.SELECT_MATCH_HISTORY_ALL);
             ResultSet resultSet = statement.executeQuery()) {
            return this.mapMatchHistoryRows(resultSet);
        }
    }

    /** Loads match history filtered by the requested player count. */
    private List<MatchHistory> loadMatchHistory(Connection connection, int playerCount) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(PostgresRankingQueries.SELECT_MATCH_HISTORY_BY_PLAYER_COUNT)) {
            statement.setInt(1, playerCount);

            try (ResultSet resultSet = statement.executeQuery()) {
                return this.mapMatchHistoryRows(resultSet);
            }
        }
    }

    /** Maps raw SQL rows to immutable match history aggregates grouped by game id. */
    private List<MatchHistory> mapMatchHistoryRows(ResultSet resultSet) throws SQLException {
        /**
         * Mutable accumulator used while grouping multiple SQL rows belonging to the same match.
         *
         * @param playedAtUtc match timestamp in UTC
         * @param playerCount number of players that took part in the match
         * @param ranking ranking rows accumulated for the match
         */
        record MatchHistoryBuilder(Instant playedAtUtc, int playerCount, List<MatchRankingEntry> ranking) {
        }

        Map<String, MatchHistoryBuilder> matchesByGameId = new LinkedHashMap<>();

        while (resultSet.next()) {
            String gameId = resultSet.getString("game_id");
            Instant playedAtUtc = this.readInstant(resultSet, "played_at");
            int playerCount = resultSet.getInt("player_count");

            MatchRankingEntry rankingEntry = new MatchRankingEntry(
                    resultSet.getString("nickname"),
                    resultSet.getInt("final_score"),
                    resultSet.getInt("placement"),
                    resultSet.getInt("elo_before"),
                    resultSet.getInt("elo_delta"),
                    resultSet.getInt("elo_after"),
                    resultSet.getInt("win_streak_after")
            );

            MatchHistoryBuilder builder = matchesByGameId.computeIfAbsent(
                    gameId,
                    ignored -> new MatchHistoryBuilder(playedAtUtc, playerCount, new ArrayList<>())
            );

            builder.ranking().add(rankingEntry);
        }

        List<MatchHistory> history = new ArrayList<>();

        for (Map.Entry<String, MatchHistoryBuilder> entry : matchesByGameId.entrySet()) {
            history.add(new MatchHistory(
                    entry.getKey(),
                    entry.getValue().playedAtUtc(),
                    entry.getValue().playerCount(),
                    entry.getValue().ranking()
            ));
        }

        return history;
    }

    /** Returns current UTC timestamp in PostgreSQL-friendly form. */
    private OffsetDateTime currentTimestampUtc() {
        return OffsetDateTime.now(ZoneOffset.UTC);
    }

    /** Converts an instant to a UTC offset date time for JDBC writes. */
    private OffsetDateTime toOffsetDateTime(Instant instant) {
        return instant.atOffset(ZoneOffset.UTC);
    }

    /** Reads a PostgreSQL timestamptz column as an instant. */
    private Instant readInstant(ResultSet resultSet, String columnLabel) throws SQLException {
        return resultSet.getObject(columnLabel, OffsetDateTime.class).toInstant();
    }

    // TODO(controller): a fine partita invocare recordMatchAndGetRanking(...) e inviare al client rank globale Elo + leaderboard globale.
    // TODO(controller): in fase di login invocare isNicknameAvailable(...) e registerUser(...) per enforce nickname univoco.
}
