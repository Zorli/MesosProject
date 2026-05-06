package it.polimi.ingsoft.mesos.server.model.ranking;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * MySQL implementation of {@link RankingService}.
 *
 * <p>Responsibilities:
 * validate input, persist completed matches atomically, compute Elo updates,
 * and expose leaderboard and match history queries.
 */
public class MySqlRankingService implements RankingService {
    private final RankingConfig config;
    private final MySqlDatabaseSupport database;
    private final RankingInputValidator validator;
    private final EloRatingCalculator eloCalculator;

    public MySqlRankingService(MySqlConnectionConfig connectionConfig) {
        this(connectionConfig, RankingConfig.defaultConfig());
    }

    public MySqlRankingService(MySqlConnectionConfig connectionConfig, int maxBusyRetries) {
        this(connectionConfig, RankingConfig.defaultConfig().withMaxBusyRetries(maxBusyRetries));
    }

    public MySqlRankingService(MySqlConnectionConfig connectionConfig, RankingConfig config) {
        this.config = config;
        this.database = new MySqlDatabaseSupport(
                connectionConfig,
                config.maxBusyRetries()
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

    @Override
    public boolean registerUser(String nickname) {
        String normalizedNickname = this.validator.normalizeNickname(nickname);

        return this.database.executeWithRetry(() -> {
            try (Connection connection = this.database.openConnection()) {
                connection.setAutoCommit(false);

                try {
                    boolean inserted = this.insertUser(connection, normalizedNickname);
                    this.ensureRatingExists(connection, normalizedNickname);
                    connection.commit();
                    return inserted;
                } catch (Exception exception) {
                    connection.rollback();
                    throw exception;
                }
            }
        });
    }

    @Override
    public boolean isNicknameAvailable(String nickname) {
        String normalizedNickname = this.validator.normalizeNickname(nickname);

        return this.database.executeWithRetry(() -> {
            try (Connection connection = this.database.openConnection();
                 PreparedStatement statement = connection.prepareStatement(MySqlRankingQueries.CHECK_USER_EXISTS)) {
                statement.setString(1, normalizedNickname);

                try (ResultSet resultSet = statement.executeQuery()) {
                    return !resultSet.next();
                }
            }
        });
    }

    @Override
    public RankingResult recordMatchAndGetRanking(String gameId,
                                                 int playerCount,
                                                 Instant playedAtUtc,
                                                 String requestingNickname,
                                                 List<MatchScore> finalScores) {
        MatchSubmission submission = this.prepareMatchSubmission(gameId, playerCount, playedAtUtc, requestingNickname, finalScores);

        return this.database.executeWithRetry(() -> this.executeMatchSubmission(submission));
    }

    @Override
    public List<LeaderboardEntry> getLeaderboard() {
        return this.database.executeWithRetry(() -> {
            try (Connection connection = this.database.openConnection()) {
                return this.loadLeaderboard(connection);
            }
        });
    }

    @Override
    public List<MatchHistory> getMatchHistory() {
        return this.database.executeWithRetry(() -> {
            try (Connection connection = this.database.openConnection()) {
                return this.loadAllHistory(connection);
            }
        });
    }

    @Override
    public List<MatchHistory> getMatchHistory(int playerCount) {
        int normalizedPlayerCount = this.validator.validatePlayerCount(playerCount);

        return this.database.executeWithRetry(() -> {
            try (Connection connection = this.database.openConnection()) {
                return this.loadHistoryByPlayerCount(connection, normalizedPlayerCount);
            }
        });
    }

    private MatchSubmission prepareMatchSubmission(String gameId,
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

        return new MatchSubmission(
                normalizedGameId,
                normalizedPlayerCount,
                normalizedPlayedAt,
                normalizedRequestingNickname,
                normalizedScores
        );
    }

    private RankingResult executeMatchSubmission(MatchSubmission submission) throws SQLException {
        try (Connection connection = this.database.openConnection()) {
            connection.setAutoCommit(false);

            try {
                if (!this.insertMatchHeader(connection, submission.gameId(), submission.playedAtUtc(), submission.playerCount())) {
                    return this.loadExistingMatchResult(connection, submission.gameId(), submission.requestingNickname());
                }

                this.ensurePlayers(connection, submission.finalScores());
                Map<String, Integer> previousRanks = this.loadGlobalRanks(connection, submission.finalScores());

                Map<String, Integer> placements = this.eloCalculator.computePlacements(submission.finalScores());
                Map<String, RatingSnapshot> snapshots = this.loadRatingSnapshots(connection, submission.finalScores());
                List<PlayerMatchUpdate> updates = this.eloCalculator.computeUpdates(
                        submission.finalScores(),
                        placements,
                        snapshots,
                        submission.playerCount()
                );

                this.persistMatchUpdates(connection, submission.gameId(), submission.playedAtUtc(), updates, previousRanks);
                List<LeaderboardEntry> leaderboard = this.loadLeaderboard(connection);
                Map<String, Integer> newRanks = this.ranksFromLeaderboard(leaderboard, submission.finalScores());

                RankingResult rankingResult = new RankingResult(
                        this.loadPlayerGlobalRank(connection, submission.requestingNickname()),
                        leaderboard,
                        this.buildPlayerDeltas(updates, previousRanks, newRanks)
                );

                connection.commit();
                return rankingResult;
            } catch (Exception exception) {
                connection.rollback();

                if (exception instanceof IllegalStateException illegalStateException) {
                    throw illegalStateException;
                }
                if (exception instanceof SQLException sqlException) {
                    throw sqlException;
                }

                throw new RankingDataException("Cannot record match and compute global elo ranking");
            }
        }
    }

    private RankingResult loadExistingMatchResult(Connection connection, String gameId, String requestingNickname)
            throws SQLException {
        if (!this.matchAlreadyExists(connection, gameId)) {
            throw new IllegalStateException("Cannot load persisted ranking for unknown match id: " + gameId);
        }

        return new RankingResult(
                this.loadPlayerGlobalRank(connection, requestingNickname),
                this.loadLeaderboard(connection)
        );
    }

    private boolean insertMatchHeader(Connection connection,
                                     String gameId,
                                     Instant playedAtUtc,
                                     int playerCount) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(MySqlRankingQueries.INSERT_MATCH)) {
            statement.setString(1, gameId);
            statement.setObject(2, this.toDbTimestamp(playedAtUtc));
            statement.setInt(3, playerCount);
            statement.executeUpdate();
            return true;
        } catch (SQLException exception) {
            if (this.isDuplicateKey(exception)) {
                return false;
            }

            throw exception;
        }
    }

    private boolean matchAlreadyExists(Connection connection, String gameId) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(MySqlRankingQueries.EXISTS_MATCH_BY_ID)) {
            statement.setString(1, gameId);

            try (ResultSet resultSet = statement.executeQuery()) {
                return resultSet.next();
            }
        }
    }

    private boolean insertUser(Connection connection, String normalizedNickname) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(MySqlRankingQueries.INSERT_USER)) {
            statement.setString(1, normalizedNickname);
            statement.setObject(2, this.currentUtcTime());
            statement.executeUpdate();
            return true;
        } catch (SQLException exception) {
            if (this.isDuplicateKey(exception)) {
                return false;
            }

            throw exception;
        }
    }

    private void ensurePlayers(Connection connection, List<MatchScore> finalScores) throws SQLException {
        try (PreparedStatement userStatement = connection.prepareStatement(MySqlRankingQueries.INSERT_USER_IF_MISSING);
             PreparedStatement ratingStatement = connection.prepareStatement(MySqlRankingQueries.INSERT_RATING_IF_MISSING)) {
            LocalDateTime now = this.currentUtcTime();

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

    private void ensureRatingExists(Connection connection, String nickname) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(MySqlRankingQueries.INSERT_RATING_IF_MISSING)) {
            statement.setString(1, nickname);
            statement.setInt(2, this.config.initialElo());
            statement.setObject(3, this.currentUtcTime());
            statement.executeUpdate();
        }
    }

    private Map<String, RatingSnapshot> loadRatingSnapshots(Connection connection,
                                                            List<MatchScore> finalScores) throws SQLException {
        String placeholders = String.join(",", Collections.nCopies(finalScores.size(), "?"));
        String query = MySqlRankingQueries.SELECT_RATINGS_BY_NICKNAMES_PREFIX + placeholders + ")";

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

        Map<String, RatingSnapshot> rankedSnapshots = new LinkedHashMap<>();
        for (MatchScore score : finalScores) {
            RatingSnapshot snapshot = snapshotsByNickname.get(score.nickname());
            if (snapshot == null) {
                throw new RankingDataException("Rating row not found for nickname: " + score.nickname());
            }
            rankedSnapshots.put(score.nickname(), snapshot);
        }

        return rankedSnapshots;
    }

    private void persistMatchUpdates(Connection connection,
                                               String gameId,
                                               Instant playedAtUtc,
                                               List<PlayerMatchUpdate> updates,
                                               Map<String, Integer> previousRanks) throws SQLException {
        LocalDateTime playedAt = this.toDbTimestamp(playedAtUtc);

        try (PreparedStatement updateRating = connection.prepareStatement(MySqlRankingQueries.UPDATE_RATING)) {
            for (PlayerMatchUpdate update : updates) {
                updateRating.setInt(1, update.eloAfter());
                updateRating.setInt(2, update.gamesAfter());
                updateRating.setInt(3, update.winsAfter());
                updateRating.setInt(4, update.winStreakAfter());
                updateRating.setObject(5, playedAt);
                updateRating.setString(6, update.nickname());
                updateRating.addBatch();
            }

            updateRating.executeBatch();
        }

        Map<String, Integer> newRanks = this.loadGlobalRanks(connection, updates);

        try (PreparedStatement insertMatch = connection.prepareStatement(MySqlRankingQueries.INSERT_MATCH_RESULT)) {
            for (PlayerMatchUpdate update : updates) {
                insertMatch.setString(1, gameId);
                insertMatch.setString(2, update.nickname());
                insertMatch.setInt(3, update.finalScore());
                insertMatch.setInt(4, update.placement());
                insertMatch.setInt(5, update.eloBefore());
                insertMatch.setInt(6, update.eloDelta());
                insertMatch.setInt(7, update.eloAfter());
                insertMatch.setInt(8, previousRanks.get(update.nickname()));
                insertMatch.setInt(9, newRanks.get(update.nickname()));
                insertMatch.setInt(10, update.winStreakAfter());
                insertMatch.addBatch();
            }

            insertMatch.executeBatch();
        }
    }

    private Map<String, Integer> loadGlobalRanks(Connection connection, List<?> players) throws SQLException {
        List<String> nicknames = players.stream()
                .map(player -> {
                    if (player instanceof MatchScore score) {
                        return score.nickname();
                    }
                    if (player instanceof PlayerMatchUpdate update) {
                        return update.nickname();
                    }
                    throw new IllegalArgumentException("Unsupported rank lookup source: " + player);
                })
                .toList();
        List<LeaderboardEntry> leaderboard = this.loadLeaderboard(connection);
        Map<String, Integer> ranks = new LinkedHashMap<>();
        for (LeaderboardEntry entry : leaderboard) {
            if (nicknames.contains(entry.nickname())) {
                ranks.put(entry.nickname(), entry.rank());
            }
        }
        for (String nickname : nicknames) {
            if (!ranks.containsKey(nickname)) {
                throw new RankingDataException("Global rank not found for nickname: " + nickname);
            }
        }
        return ranks;
    }

    private Map<String, Integer> ranksFromLeaderboard(List<LeaderboardEntry> leaderboard, List<MatchScore> scores) {
        List<String> nicknames = scores.stream().map(MatchScore::nickname).toList();
        Map<String, Integer> ranks = new LinkedHashMap<>();
        for (LeaderboardEntry entry : leaderboard) {
            if (nicknames.contains(entry.nickname())) {
                ranks.put(entry.nickname(), entry.rank());
            }
        }
        return ranks;
    }

    private List<PlayerRankingDelta> buildPlayerDeltas(List<PlayerMatchUpdate> updates,
                                                       Map<String, Integer> previousRanks,
                                                       Map<String, Integer> newRanks) {
        return updates.stream()
                .map(update -> new PlayerRankingDelta(
                        update.nickname(),
                        update.eloBefore(),
                        update.eloAfter(),
                        previousRanks.get(update.nickname()),
                        newRanks.get(update.nickname())
                ))
                .toList();
    }

    private List<LeaderboardEntry> loadLeaderboard(Connection connection) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(MySqlRankingQueries.SELECT_GLOBAL_LEADERBOARD);
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

    private int loadPlayerGlobalRank(Connection connection, String nickname) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(MySqlRankingQueries.SELECT_PLAYER_GLOBAL_RANK)) {
            statement.setString(1, nickname);

            try (ResultSet resultSet = statement.executeQuery()) {
                if (!resultSet.next()) {
                    throw new RankingDataException("Global rank not found for nickname: " + nickname);
                }

                return resultSet.getInt("rank");
            }
        }
    }

    private List<MatchHistory> loadAllHistory(Connection connection) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(MySqlRankingQueries.SELECT_MATCH_HISTORY_ALL);
             ResultSet resultSet = statement.executeQuery()) {
            return this.mapMatchHistoryRows(resultSet);
        }
    }

    private List<MatchHistory> loadHistoryByPlayerCount(Connection connection, int playerCount) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(MySqlRankingQueries.SELECT_MATCH_HISTORY_BY_PLAYER_COUNT)) {
            statement.setInt(1, playerCount);

            try (ResultSet resultSet = statement.executeQuery()) {
                return this.mapMatchHistoryRows(resultSet);
            }
        }
    }

    private List<MatchHistory> mapMatchHistoryRows(ResultSet resultSet) throws SQLException {
        Map<String, MatchHistoryAccumulator> matchesByGameId = new LinkedHashMap<>();

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
                    resultSet.getInt("previous_global_rank"),
                    resultSet.getInt("new_global_rank"),
                    resultSet.getInt("win_streak_after")
            );

            MatchHistoryAccumulator accumulator = matchesByGameId.computeIfAbsent(
                    gameId,
                    ignored -> new MatchHistoryAccumulator(playedAtUtc, playerCount, new ArrayList<>())
            );

            accumulator.ranking().add(rankingEntry);
        }

        List<MatchHistory> history = new ArrayList<>();
        for (Map.Entry<String, MatchHistoryAccumulator> entry : matchesByGameId.entrySet()) {
            history.add(new MatchHistory(
                    entry.getKey(),
                    entry.getValue().playedAtUtc(),
                    entry.getValue().playerCount(),
                    entry.getValue().ranking()
            ));
        }

        return history;
    }

    private LocalDateTime currentUtcTime() {
        return LocalDateTime.ofInstant(Instant.now(), ZoneOffset.UTC);
    }

    private LocalDateTime toDbTimestamp(Instant instant) {
        return LocalDateTime.ofInstant(instant, ZoneOffset.UTC);
    }

    private Instant readInstant(ResultSet resultSet, String columnLabel) throws SQLException {
        return resultSet.getObject(columnLabel, LocalDateTime.class).toInstant(ZoneOffset.UTC);
    }

    private boolean isDuplicateKey(SQLException exception) {
        return "23000".equals(exception.getSQLState()) && exception.getErrorCode() == 1062;
    }

    private record MatchSubmission(String gameId,
                                  int playerCount,
                                  Instant playedAtUtc,
                                  String requestingNickname,
                                  List<MatchScore> finalScores) {
    }

    private static final record MatchHistoryAccumulator(Instant playedAtUtc, int playerCount, List<MatchRankingEntry> ranking) {
    }
}
