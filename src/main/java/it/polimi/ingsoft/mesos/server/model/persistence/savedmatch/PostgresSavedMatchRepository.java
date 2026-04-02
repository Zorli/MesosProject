package it.polimi.ingsoft.mesos.server.model.persistence.savedmatch;

import it.polimi.ingsoft.mesos.server.model.game.RoundPhase;
import it.polimi.ingsoft.mesos.server.model.enums.Era;
import it.polimi.ingsoft.mesos.server.model.enums.GameStatus;
import it.polimi.ingsoft.mesos.server.model.enums.SavedMatchStatus;
import it.polimi.ingsoft.mesos.server.model.enums.TotemColor;
import it.polimi.ingsoft.mesos.server.model.enums.TurnPhase;
import it.polimi.ingsoft.mesos.server.model.ranking.PostgresConnectionConfig;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * PostgreSQL implementation of {@link SavedMatchRepository}.
 */
public final class PostgresSavedMatchRepository implements SavedMatchRepository {
    private final PostgresSavedMatchDatabaseSupport database;

    /**
     * Creates the repository with PostgreSQL connection settings and retry limit.
     *
     * @param connectionConfig PostgreSQL connection settings
     * @param maxBusyRetries maximum retries for transient PostgreSQL failures
     */
    public PostgresSavedMatchRepository(PostgresConnectionConfig connectionConfig, int maxBusyRetries) {
        this.database = new PostgresSavedMatchDatabaseSupport(
                connectionConfig,
                maxBusyRetries,
                List.of(PostgresSavedMatchSchemaModule.getInstance())
        );
        this.database.initializeSchema();
    }

    /**
     * Persists a complete saved-match aggregate inside a single database transaction.
     *
     * @param aggregate normalized saved-match snapshot to store
     */
    @Override
    public void save(SavedMatchAggregate aggregate) {
        SavedMatchConsistencyChecker.validate(aggregate);

        this.database.executeWithRetry(() -> {
            try (Connection connection = this.database.openConnection()) {
                connection.setAutoCommit(false);

                try {
                    this.upsertMatch(connection, aggregate);
                    this.upsertPlayers(connection, aggregate.players(), aggregate.gameId());
                    this.replaceTurnOrder(connection, aggregate.gameId(), aggregate.turnOrder());
                    this.replaceTurnChoices(connection, aggregate.gameId(), aggregate.turnChoices());
                    this.replaceOfferSpaces(connection, aggregate.gameId(), aggregate.offerSpaces());
                    this.replaceStartingBlocks(connection, aggregate.gameId(), aggregate.startingBlocks());
                    this.replaceBoardCards(connection, aggregate.gameId(), aggregate.boardCards());
                    this.replacePlayerCards(connection, aggregate.gameId(), aggregate.playerCards(), aggregate.playerCardRuntimeStates());
                    connection.commit();
                    return null;
                } catch (Exception exception) {
                    connection.rollback();
                    if (exception instanceof SQLException sqlException) {
                        throw sqlException;
                    }
                    if (exception instanceof IllegalStateException illegalStateException) {
                        throw illegalStateException;
                    }
                    throw new IllegalStateException("Cannot persist saved match", exception);
                }
            }
        });
    }

    /**
     * Loads a saved-match aggregate and validates its structural consistency.
     *
     * @param gameId identifier of the match to restore
     * @return validated aggregate loaded from PostgreSQL
     */
    @Override
    public SavedMatchAggregate load(String gameId) {
        return this.database.executeWithRetry(() -> {
            try (Connection connection = this.database.openConnection()) {
                SavedMatchAggregate aggregate = this.loadAggregate(connection, gameId);
                SavedMatchConsistencyChecker.validate(aggregate);
                return aggregate;
            }
        });
    }

    /**
     * Marks the given player as online, guarding against concurrent sessions for the same slot.
     *
     * @param gameId match identifier
     * @param nickname player nickname
     * @param sessionToken token of the connecting session
     */
    @Override
    public void markPlayerOnline(String gameId, String nickname, String sessionToken) {
        this.database.executeWithRetry(() -> {
            try (Connection connection = this.database.openConnection()) {
                connection.setAutoCommit(false);

                try (PreparedStatement selectStatement = connection.prepareStatement(PostgresSavedMatchQueries.SELECT_PLAYER_PRESENCE_FOR_UPDATE);
                     PreparedStatement updateStatement = connection.prepareStatement(PostgresSavedMatchQueries.UPDATE_PLAYER_ONLINE)) {
                    selectStatement.setString(1, gameId);
                    selectStatement.setString(2, nickname);

                    try (ResultSet resultSet = selectStatement.executeQuery()) {
                        if (!resultSet.next()) {
                            throw new IllegalArgumentException("Saved-match player not found: " + nickname);
                        }

                        boolean online = resultSet.getBoolean("is_online");
                        String currentToken = resultSet.getString("session_token");
                        if (online && currentToken != null && !currentToken.equals(sessionToken)) {
                            throw new SavedMatchPlayerConnectionException(
                                    "Player " + nickname + " is already connected to saved match " + gameId
                            );
                        }
                    }

                    updateStatement.setString(1, sessionToken);
                    updateStatement.setString(2, gameId);
                    updateStatement.setString(3, nickname);
                    updateStatement.executeUpdate();

                    connection.commit();
                    return null;
                } catch (Exception exception) {
                    connection.rollback();
                    if (exception instanceof SQLException sqlException) {
                        throw sqlException;
                    }
                    if (exception instanceof RuntimeException runtimeException) {
                        throw runtimeException;
                    }
                    throw new IllegalStateException("Cannot mark saved-match player online", exception);
                }
            }
        });
    }

    /**
     * Marks the given player as offline in the persisted saved-match snapshot.
     *
     * @param gameId match identifier
     * @param nickname player nickname
     */
    @Override
    public void markPlayerOffline(String gameId, String nickname) {
        this.database.executeWithRetry(() -> {
            try (Connection connection = this.database.openConnection();
                 PreparedStatement statement = connection.prepareStatement(PostgresSavedMatchQueries.UPDATE_PLAYER_OFFLINE)) {
                statement.setString(1, gameId);
                statement.setString(2, nickname);
                statement.executeUpdate();
                return null;
            }
        });
    }

    /**
     * Deletes in-progress matches that exceeded the configured retention window.
     *
     * @return number of deleted saved matches
     */
    @Override
    public int deleteExpiredInProgressMatches() {
        return this.database.executeWithRetry(() -> {
            try (Connection connection = this.database.openConnection();
                 PreparedStatement statement = connection.prepareStatement(PostgresSavedMatchQueries.DELETE_EXPIRED_IN_PROGRESS_MATCHES)) {
                return statement.executeUpdate();
            }
        });
    }

    /**
     * Inserts or updates the top-level saved-match row.
     *
     * @param connection active transaction connection
     * @param aggregate aggregate whose root row must be persisted
     * @throws SQLException if the update fails
     */
    private void upsertMatch(Connection connection, SavedMatchAggregate aggregate) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(PostgresSavedMatchQueries.UPSERT_SAVED_MATCH)) {
            statement.setString(1, aggregate.gameId());
            statement.setString(2, aggregate.hostNickname());
            statement.setInt(3, aggregate.playerCount());
            statement.setString(4, aggregate.status().name());
            statement.setString(5, aggregate.currentEra().name());
            statement.setString(6, aggregate.gameStatus().name());
            statement.setInt(7, aggregate.roundNumber());
            statement.setString(8, aggregate.roundPhase().name());
            statement.setString(9, aggregate.turnPhase().name());
            statement.setString(10, aggregate.activePlayerNickname());
            statement.setInt(11, aggregate.currentPlayerIndex());
            statement.setString(12, aggregate.lastCheckpointType().name());
            statement.executeUpdate();
        }
    }

    /**
     * Inserts or updates the normalized player rows belonging to one saved match.
     *
     * @param connection active transaction connection
     * @param players player rows to persist
     * @param gameId identifier of the owning match
     * @throws SQLException if the batch update fails
     */
    private void upsertPlayers(Connection connection, List<SavedMatchPlayerRecord> players, String gameId) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(PostgresSavedMatchQueries.UPSERT_SAVED_MATCH_PLAYER)) {
            for (SavedMatchPlayerRecord player : players) {
                statement.setString(1, gameId);
                statement.setString(2, player.nickname());
                statement.setInt(3, player.playerIndex());
                statement.setString(4, player.totemColor().name());
                statement.setInt(5, player.food());
                statement.setInt(6, player.prestigePoints());
                statement.addBatch();
            }
            statement.executeBatch();
        }
    }

    /**
     * Replaces the persisted turn order with the supplied normalized order rows.
     *
     * @param connection active transaction connection
     * @param gameId identifier of the owning match
     * @param turnOrder ordered turn rows to persist
     * @throws SQLException if the replacement fails
     */
    private void replaceTurnOrder(Connection connection, String gameId, List<SavedMatchTurnOrderRecord> turnOrder) throws SQLException {
        this.deleteByGameId(connection, PostgresSavedMatchQueries.DELETE_SAVED_MATCH_TURN_ORDER, gameId);
        try (PreparedStatement statement = connection.prepareStatement(PostgresSavedMatchQueries.INSERT_SAVED_MATCH_TURN_ORDER)) {
            for (SavedMatchTurnOrderRecord turnOrderRecord : turnOrder) {
                statement.setString(1, gameId);
                statement.setInt(2, turnOrderRecord.turnPosition());
                statement.setString(3, turnOrderRecord.nickname());
                statement.addBatch();
            }
            statement.executeBatch();
        }
    }

    /**
     * Replaces the persisted per-player turn-choice counters.
     *
     * @param connection active transaction connection
     * @param gameId identifier of the owning match
     * @param turnChoices normalized turn-choice rows to persist
     * @throws SQLException if the replacement fails
     */
    private void replaceTurnChoices(Connection connection, String gameId, List<SavedMatchTurnChoiceRecord> turnChoices) throws SQLException {
        this.deleteByGameId(connection, PostgresSavedMatchQueries.DELETE_SAVED_MATCH_TURN_CHOICES, gameId);
        try (PreparedStatement statement = connection.prepareStatement(PostgresSavedMatchQueries.INSERT_SAVED_MATCH_TURN_CHOICES)) {
            for (SavedMatchTurnChoiceRecord turnChoice : turnChoices) {
                statement.setString(1, gameId);
                statement.setString(2, turnChoice.nickname());
                statement.setInt(3, turnChoice.remainingUpperChoices());
                statement.setInt(4, turnChoice.remainingLowerChoices());
                statement.addBatch();
            }
            statement.executeBatch();
        }
    }

    /**
     * Replaces the persisted offer-path occupancy rows.
     *
     * @param connection active transaction connection
     * @param gameId identifier of the owning match
     * @param offerSpaces normalized offer-space rows to persist
     * @throws SQLException if the replacement fails
     */
    private void replaceOfferSpaces(Connection connection, String gameId, List<SavedMatchOfferSpaceRecord> offerSpaces) throws SQLException {
        this.deleteByGameId(connection, PostgresSavedMatchQueries.DELETE_SAVED_MATCH_OFFER_SPACES, gameId);
        try (PreparedStatement statement = connection.prepareStatement(PostgresSavedMatchQueries.INSERT_SAVED_MATCH_OFFER_SPACE)) {
            for (SavedMatchOfferSpaceRecord offerSpace : offerSpaces) {
                statement.setString(1, gameId);
                statement.setString(2, offerSpace.offerSpaceCode());
                statement.setInt(3, offerSpace.positionIndex());
                statement.setString(4, offerSpace.occupiedByNickname());
                statement.addBatch();
            }
            statement.executeBatch();
        }
    }

    /**
     * Replaces the persisted starting-block occupancy rows.
     *
     * @param connection active transaction connection
     * @param gameId identifier of the owning match
     * @param startingBlocks normalized starting-block rows to persist
     * @throws SQLException if the replacement fails
     */
    private void replaceStartingBlocks(Connection connection, String gameId, List<SavedMatchStartingBlockRecord> startingBlocks) throws SQLException {
        this.deleteByGameId(connection, PostgresSavedMatchQueries.DELETE_SAVED_MATCH_STARTING_BLOCKS, gameId);
        try (PreparedStatement statement = connection.prepareStatement(PostgresSavedMatchQueries.INSERT_SAVED_MATCH_STARTING_BLOCK)) {
            for (SavedMatchStartingBlockRecord startingBlock : startingBlocks) {
                statement.setString(1, gameId);
                statement.setInt(2, startingBlock.blockPosition());
                statement.setInt(3, startingBlock.foodModifier());
                statement.setInt(4, startingBlock.prestigeModifier());
                statement.setString(5, startingBlock.occupiedByNickname());
                statement.addBatch();
            }
            statement.executeBatch();
        }
    }

    /**
     * Replaces the persisted board-card rows for both visible piles and remaining decks.
     *
     * @param connection active transaction connection
     * @param gameId identifier of the owning match
     * @param boardCards normalized board-card rows to persist
     * @throws SQLException if the replacement fails
     */
    private void replaceBoardCards(Connection connection, String gameId, List<SavedMatchBoardCardRecord> boardCards) throws SQLException {
        this.deleteByGameId(connection, PostgresSavedMatchQueries.DELETE_SAVED_MATCH_BOARD_CARDS, gameId);
        try (PreparedStatement statement = connection.prepareStatement(PostgresSavedMatchQueries.INSERT_SAVED_MATCH_BOARD_CARD)) {
            for (SavedMatchBoardCardRecord boardCard : boardCards) {
                statement.setString(1, gameId);
                statement.setString(2, boardCard.cardZone().name());
                statement.setInt(3, boardCard.positionIndex());
                statement.setInt(4, boardCard.cardId());
                statement.addBatch();
            }
            statement.executeBatch();
        }
    }

    /**
     * Replaces player-owned cards together with their optional runtime state rows.
     *
     * @param connection active transaction connection
     * @param gameId identifier of the owning match
     * @param playerCards normalized player-card rows to persist
     * @param runtimeStates normalized runtime-state rows to persist
     * @throws SQLException if the replacement fails
     */
    private void replacePlayerCards(Connection connection,
                                    String gameId,
                                    List<SavedMatchPlayerCardRecord> playerCards,
                                    List<SavedMatchPlayerCardRuntimeStateRecord> runtimeStates) throws SQLException {
        this.deleteByGameId(connection, PostgresSavedMatchQueries.DELETE_SAVED_MATCH_PLAYER_CARD_STATE, gameId);
        this.deleteByGameId(connection, PostgresSavedMatchQueries.DELETE_SAVED_MATCH_PLAYER_CARDS, gameId);

        try (PreparedStatement playerCardStatement = connection.prepareStatement(PostgresSavedMatchQueries.INSERT_SAVED_MATCH_PLAYER_CARD);
             PreparedStatement runtimeStateStatement = connection.prepareStatement(PostgresSavedMatchQueries.INSERT_SAVED_MATCH_PLAYER_CARD_STATE)) {
            for (SavedMatchPlayerCardRecord playerCard : playerCards) {
                playerCardStatement.setString(1, gameId);
                playerCardStatement.setString(2, playerCard.nickname());
                playerCardStatement.setString(3, playerCard.cardZone().name());
                playerCardStatement.setInt(4, playerCard.positionIndex());
                playerCardStatement.setInt(5, playerCard.cardId());
                playerCardStatement.addBatch();
            }
            playerCardStatement.executeBatch();

            for (SavedMatchPlayerCardRuntimeStateRecord runtimeState : runtimeStates) {
                runtimeStateStatement.setString(1, gameId);
                runtimeStateStatement.setString(2, runtimeState.nickname());
                runtimeStateStatement.setInt(3, runtimeState.cardId());
                runtimeStateStatement.setString(4, runtimeState.stateKey());
                if (runtimeState.stateIntValue() == null) {
                    runtimeStateStatement.setNull(5, Types.INTEGER);
                } else {
                    runtimeStateStatement.setInt(5, runtimeState.stateIntValue());
                }
                runtimeStateStatement.setString(6, runtimeState.stateTextValue());
                runtimeStateStatement.addBatch();
            }
            runtimeStateStatement.executeBatch();
        }
    }

    /**
     * Loads the top-level saved-match aggregate and all of its normalized child collections.
     *
     * @param connection active read connection
     * @param gameId identifier of the match to load
     * @return fully assembled normalized aggregate
     * @throws SQLException if the query fails
     */
    private SavedMatchAggregate loadAggregate(Connection connection, String gameId) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(PostgresSavedMatchQueries.SELECT_SAVED_MATCH)) {
            statement.setString(1, gameId);

            try (ResultSet resultSet = statement.executeQuery()) {
                if (!resultSet.next()) {
                    throw new IllegalArgumentException("Saved match not found: " + gameId);
                }

                return new SavedMatchAggregate(
                        resultSet.getString("game_id"),
                        resultSet.getString("host_nickname"),
                        resultSet.getInt("player_count"),
                        SavedMatchStatus.valueOf(resultSet.getString("status")),
                        Era.valueOf(resultSet.getString("current_era")),
                        GameStatus.valueOf(resultSet.getString("game_status")),
                        resultSet.getInt("round_number"),
                        RoundPhase.valueOf(resultSet.getString("round_phase")),
                        TurnPhase.valueOf(resultSet.getString("turn_phase")),
                        resultSet.getString("active_player_nickname"),
                        resultSet.getInt("current_player_index"),
                        SavedMatchCheckpointType.valueOf(resultSet.getString("last_checkpoint_type")),
                        this.loadPlayers(connection, gameId),
                        this.loadTurnOrder(connection, gameId),
                        this.loadTurnChoices(connection, gameId),
                        this.loadOfferSpaces(connection, gameId),
                        this.loadStartingBlocks(connection, gameId),
                        this.loadBoardCards(connection, gameId),
                        this.loadPlayerCards(connection, gameId),
                        this.loadPlayerCardRuntimeStates(connection, gameId)
                );
            }
        }
    }

    /**
     * Loads persisted players for the requested match.
     *
     * @param connection active read connection
     * @param gameId identifier of the match to load
     * @return ordered saved-match player rows
     * @throws SQLException if the query fails
     */
    private List<SavedMatchPlayerRecord> loadPlayers(Connection connection, String gameId) throws SQLException {
        List<SavedMatchPlayerRecord> players = new ArrayList<>();
        try (PreparedStatement statement = connection.prepareStatement(PostgresSavedMatchQueries.SELECT_SAVED_MATCH_PLAYERS)) {
            statement.setString(1, gameId);
            try (ResultSet resultSet = statement.executeQuery()) {
                while (resultSet.next()) {
                    players.add(new SavedMatchPlayerRecord(
                            resultSet.getInt("player_index"),
                            resultSet.getString("nickname"),
                            TotemColor.valueOf(resultSet.getString("totem_color")),
                            resultSet.getInt("food"),
                            resultSet.getInt("prestige_points"),
                            resultSet.getBoolean("is_online"),
                            resultSet.getString("session_token"),
                            resultSet.getObject("last_seen_at", OffsetDateTime.class)
                    ));
                }
            }
        }
        return players;
    }

    /**
     * Loads persisted turn-order rows for the requested match.
     *
     * @param connection active read connection
     * @param gameId identifier of the match to load
     * @return ordered turn-order rows
     * @throws SQLException if the query fails
     */
    private List<SavedMatchTurnOrderRecord> loadTurnOrder(Connection connection, String gameId) throws SQLException {
        List<SavedMatchTurnOrderRecord> turnOrder = new ArrayList<>();
        try (PreparedStatement statement = connection.prepareStatement(PostgresSavedMatchQueries.SELECT_SAVED_MATCH_TURN_ORDER)) {
            statement.setString(1, gameId);
            try (ResultSet resultSet = statement.executeQuery()) {
                while (resultSet.next()) {
                    turnOrder.add(new SavedMatchTurnOrderRecord(
                            resultSet.getInt("turn_position"),
                            resultSet.getString("nickname")
                    ));
                }
            }
        }
        return turnOrder;
    }

    /**
     * Loads persisted turn-choice counters for the requested match.
     *
     * @param connection active read connection
     * @param gameId identifier of the match to load
     * @return normalized turn-choice rows
     * @throws SQLException if the query fails
     */
    private List<SavedMatchTurnChoiceRecord> loadTurnChoices(Connection connection, String gameId) throws SQLException {
        List<SavedMatchTurnChoiceRecord> turnChoices = new ArrayList<>();
        try (PreparedStatement statement = connection.prepareStatement(PostgresSavedMatchQueries.SELECT_SAVED_MATCH_TURN_CHOICES)) {
            statement.setString(1, gameId);
            try (ResultSet resultSet = statement.executeQuery()) {
                while (resultSet.next()) {
                    turnChoices.add(new SavedMatchTurnChoiceRecord(
                            resultSet.getString("nickname"),
                            resultSet.getInt("remaining_upper_choices"),
                            resultSet.getInt("remaining_lower_choices")
                    ));
                }
            }
        }
        return turnChoices;
    }

    /**
     * Loads persisted offer-space rows for the requested match.
     *
     * @param connection active read connection
     * @param gameId identifier of the match to load
     * @return normalized offer-space rows
     * @throws SQLException if the query fails
     */
    private List<SavedMatchOfferSpaceRecord> loadOfferSpaces(Connection connection, String gameId) throws SQLException {
        List<SavedMatchOfferSpaceRecord> offerSpaces = new ArrayList<>();
        try (PreparedStatement statement = connection.prepareStatement(PostgresSavedMatchQueries.SELECT_SAVED_MATCH_OFFER_SPACES)) {
            statement.setString(1, gameId);
            try (ResultSet resultSet = statement.executeQuery()) {
                while (resultSet.next()) {
                    offerSpaces.add(new SavedMatchOfferSpaceRecord(
                            resultSet.getString("offer_space_code"),
                            resultSet.getInt("position_index"),
                            resultSet.getString("occupied_by_nickname")
                    ));
                }
            }
        }
        return offerSpaces;
    }

    /**
     * Loads persisted starting-block rows for the requested match.
     *
     * @param connection active read connection
     * @param gameId identifier of the match to load
     * @return normalized starting-block rows
     * @throws SQLException if the query fails
     */
    private List<SavedMatchStartingBlockRecord> loadStartingBlocks(Connection connection, String gameId) throws SQLException {
        List<SavedMatchStartingBlockRecord> startingBlocks = new ArrayList<>();
        try (PreparedStatement statement = connection.prepareStatement(PostgresSavedMatchQueries.SELECT_SAVED_MATCH_STARTING_BLOCKS)) {
            statement.setString(1, gameId);
            try (ResultSet resultSet = statement.executeQuery()) {
                while (resultSet.next()) {
                    startingBlocks.add(new SavedMatchStartingBlockRecord(
                            resultSet.getInt("block_position"),
                            resultSet.getInt("food_modifier"),
                            resultSet.getInt("prestige_modifier"),
                            resultSet.getString("occupied_by_nickname")
                    ));
                }
            }
        }
        return startingBlocks;
    }

    /**
     * Loads persisted board-card rows for the requested match.
     *
     * @param connection active read connection
     * @param gameId identifier of the match to load
     * @return normalized board-card rows
     * @throws SQLException if the query fails
     */
    private List<SavedMatchBoardCardRecord> loadBoardCards(Connection connection, String gameId) throws SQLException {
        List<SavedMatchBoardCardRecord> boardCards = new ArrayList<>();
        try (PreparedStatement statement = connection.prepareStatement(PostgresSavedMatchQueries.SELECT_SAVED_MATCH_BOARD_CARDS)) {
            statement.setString(1, gameId);
            try (ResultSet resultSet = statement.executeQuery()) {
                while (resultSet.next()) {
                    boardCards.add(new SavedMatchBoardCardRecord(
                            SavedMatchBoardCardZone.valueOf(resultSet.getString("card_zone")),
                            resultSet.getInt("position_index"),
                            resultSet.getInt("card_id")
                    ));
                }
            }
        }
        boardCards.sort(Comparator
                .comparing(SavedMatchBoardCardRecord::cardZone)
                .thenComparingInt(SavedMatchBoardCardRecord::positionIndex));
        return boardCards;
    }

    /**
     * Loads persisted player-owned card rows for the requested match.
     *
     * @param connection active read connection
     * @param gameId identifier of the match to load
     * @return normalized player-card rows
     * @throws SQLException if the query fails
     */
    private List<SavedMatchPlayerCardRecord> loadPlayerCards(Connection connection, String gameId) throws SQLException {
        List<SavedMatchPlayerCardRecord> playerCards = new ArrayList<>();
        try (PreparedStatement statement = connection.prepareStatement(PostgresSavedMatchQueries.SELECT_SAVED_MATCH_PLAYER_CARDS)) {
            statement.setString(1, gameId);
            try (ResultSet resultSet = statement.executeQuery()) {
                while (resultSet.next()) {
                    playerCards.add(new SavedMatchPlayerCardRecord(
                            resultSet.getString("nickname"),
                            SavedMatchPlayerCardZone.valueOf(resultSet.getString("card_zone")),
                            resultSet.getInt("position_index"),
                            resultSet.getInt("card_id")
                    ));
                }
            }
        }
        return playerCards;
    }

    /**
     * Loads persisted runtime-state rows associated with player-owned building cards.
     *
     * @param connection active read connection
     * @param gameId identifier of the match to load
     * @return normalized runtime-state rows
     * @throws SQLException if the query fails
     */
    private List<SavedMatchPlayerCardRuntimeStateRecord> loadPlayerCardRuntimeStates(Connection connection, String gameId) throws SQLException {
        List<SavedMatchPlayerCardRuntimeStateRecord> runtimeStates = new ArrayList<>();
        try (PreparedStatement statement = connection.prepareStatement(PostgresSavedMatchQueries.SELECT_SAVED_MATCH_PLAYER_CARD_STATE)) {
            statement.setString(1, gameId);
            try (ResultSet resultSet = statement.executeQuery()) {
                while (resultSet.next()) {
                    Integer stateIntValue = (Integer) resultSet.getObject("state_int_value");
                    runtimeStates.add(new SavedMatchPlayerCardRuntimeStateRecord(
                            resultSet.getString("nickname"),
                            resultSet.getInt("card_id"),
                            resultSet.getString("state_key"),
                            stateIntValue,
                            resultSet.getString("state_text_value")
                    ));
                }
            }
        }
        return runtimeStates;
    }

    /**
     * Executes a single-parameter delete statement scoped to one saved-match identifier.
     *
     * @param connection active transaction connection
     * @param sql delete statement accepting the match id as first parameter
     * @param gameId identifier of the match whose child rows must be deleted
     * @throws SQLException if the delete fails
     */
    private void deleteByGameId(Connection connection, String sql, String gameId) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, gameId);
            statement.executeUpdate();
        }
    }
}
