package it.polimi.ingsoft.mesos.server.persistence;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.logging.Logger;

/**
 * Persistence gateway for the history of finished matches (advanced feature
 * "Classifica partite su DB").
 *
 * <p>The history is stored in MySQL with a normalized, three-table model:
 * <ul>
 *     <li>{@code utenti} – one row per distinct player nickname, added the first time
 *     the nickname is seen and kept afterwards;</li>
 *     <li>{@code partite} – one row per completed match, holding the date it was
 *     played and the number of players;</li>
 *     <li>{@code partecipazioni} – the N-to-N relationship between users and matches,
 *     carrying the score (prestige points) a player obtained in that specific match.</li>
 * </ul>
 * The same nickname therefore appears in several {@code partecipazioni} rows, one for
 * each game it completed, which is what the all-time ranking lists.
 *
 * <p>This class belongs to the persistence layer and returns only plain data
 * ({@link RankingResult}); it never builds network payloads. No connection pool is
 * used: a match ending is a rare event, so every method opens and closes its own
 * short-lived {@link Connection}. Methods declare {@link SQLException} so the caller
 * can decide how to degrade when the database is unreachable; the server treats such
 * failures as non-fatal and simply skips the ranking instead of interrupting play.
 */
public final class GameResultRepository {

    private static final Logger log = Logger.getLogger(GameResultRepository.class.getName());

    static {
        // Best-effort explicit driver registration. The shaded server jar bundles the
        // connector into this module, so the class is reachable; when it is not, the
        // ranking feature simply stays unavailable instead of crashing the server.
        try {
            Class.forName("com.mysql.cj.jdbc.Driver");
        } catch (ClassNotFoundException exception) {
            log.warning("MySQL JDBC driver not found; the ranking feature will be unavailable.");
        }
    }

    private final String jdbcUrl;
    private final String user;
    private final String password;

    /**
     * Creates a repository bound to one MySQL database. The constructor does not
     * open any connection.
     *
     * @param jdbcUrl JDBC URL of the database (host, port and schema)
     * @param user database user
     * @param password database password
     */
    public GameResultRepository(String jdbcUrl, String user, String password) {
        this.jdbcUrl = jdbcUrl;
        this.user = user;
        this.password = password;
    }

    /**
     * Creates the {@code utenti}, {@code partite} and {@code partecipazioni} tables
     * if they do not already exist.
     *
     * <p>Called once at server startup so the server works against a vanilla MySQL
     * image without any manual schema setup. Tables are created in dependency order
     * because {@code partecipazioni} references the other two.
     *
     * @throws SQLException if the database cannot be reached or a statement fails
     */
    public void ensureSchema() throws SQLException {
        String utenti = "CREATE TABLE IF NOT EXISTS utenti ("
                + "id BIGINT AUTO_INCREMENT PRIMARY KEY,"
                + "nickname VARCHAR(64) NOT NULL UNIQUE)";
        String partite = "CREATE TABLE IF NOT EXISTS partite ("
                + "id BIGINT AUTO_INCREMENT PRIMARY KEY,"
                + "data DATETIME(3) NOT NULL,"
                + "num_giocatori INT NOT NULL,"
                + "INDEX idx_num_giocatori (num_giocatori))";
        String partecipazioni = "CREATE TABLE IF NOT EXISTS partecipazioni ("
                + "id_partita BIGINT NOT NULL,"
                + "id_utente BIGINT NOT NULL,"
                + "punteggio INT NOT NULL,"
                + "PRIMARY KEY (id_partita, id_utente),"
                + "FOREIGN KEY (id_partita) REFERENCES partite(id),"
                + "FOREIGN KEY (id_utente) REFERENCES utenti(id),"
                + "INDEX idx_punteggio (punteggio))";
        try (Connection connection = this.openConnection();
             Statement statement = connection.createStatement()) {
            statement.executeUpdate(utenti);
            statement.executeUpdate(partite);
            statement.executeUpdate(partecipazioni);
        }
    }

    /**
     * Persists the results of one finished match and returns the updated all-time
     * ranking for that player count.
     *
     * <p>In a single transaction this inserts one {@code partite} row, ensures every
     * player exists in {@code utenti} (inserting the nickname only when missing), and
     * links each player to the match in {@code partecipazioni} with the score. The
     * ranking is then read back, already including the match just stored.
     *
     * @param numPlayers number of players of the match
     * @param results final result of each participant
     * @return the all-time ranking for {@code numPlayers}, with the just-finished
     *         match flagged in {@link RankingResult.RankingEntry#currentGame()}
     * @throws SQLException if the database cannot be reached or a statement fails
     */
    public RankingResult recordResultAndFindRanking(int numPlayers, List<PlayerResult> results)
            throws SQLException {
        Timestamp playedAt = new Timestamp(System.currentTimeMillis());
        // Process players in a stable nickname order so concurrent matches acquire the
        // utenti unique-index locks in the same order, avoiding deadlocks.
        List<PlayerResult> ordered = results.stream()
                .sorted(Comparator.comparing(PlayerResult::nickname))
                .toList();
        try (Connection connection = this.openConnection()) {
            connection.setAutoCommit(false);
            long gameId;
            try {
                gameId = this.insertMatch(connection, playedAt, numPlayers);
                for (PlayerResult result : ordered) {
                    long userId = this.ensureUser(connection, result.nickname());
                    this.insertParticipation(connection, gameId, userId, result.score());
                }
                connection.commit();
            } catch (SQLException exception) {
                connection.rollback();
                throw exception;
            }
            connection.setAutoCommit(true);
            return this.findRanking(connection, numPlayers, gameId);
        }
    }

    private RankingResult findRanking(Connection connection, int numPlayers, long currentGameId)
            throws SQLException {
        String query = "SELECT u.nickname AS nickname, pa.punteggio AS punteggio, "
                + "p.data AS data, p.id AS id_partita "
                + "FROM partecipazioni pa "
                + "JOIN partite p ON pa.id_partita = p.id "
                + "JOIN utenti u ON pa.id_utente = u.id "
                + "WHERE p.num_giocatori = ? "
                + "ORDER BY pa.punteggio DESC, p.data ASC, u.nickname ASC";
        List<RankingResult.RankingEntry> entries = new ArrayList<>();
        try (PreparedStatement statement = connection.prepareStatement(query)) {
            statement.setInt(1, numPlayers);
            try (ResultSet resultSet = statement.executeQuery()) {
                int rank = 1;
                while (resultSet.next()) {
                    String datePlayed = resultSet.getTimestamp("data").toLocalDateTime().toString();
                    entries.add(new RankingResult.RankingEntry(
                            rank++,
                            resultSet.getString("nickname"),
                            resultSet.getInt("punteggio"),
                            datePlayed,
                            resultSet.getLong("id_partita") == currentGameId));
                }
            }
        }
        return new RankingResult(numPlayers, entries);
    }

    private long insertMatch(Connection connection, Timestamp playedAt, int numPlayers) throws SQLException {
        String insert = "INSERT INTO partite (data, num_giocatori) VALUES (?, ?)";
        try (PreparedStatement statement =
                     connection.prepareStatement(insert, Statement.RETURN_GENERATED_KEYS)) {
            statement.setTimestamp(1, playedAt);
            statement.setInt(2, numPlayers);
            statement.executeUpdate();
            try (ResultSet keys = statement.getGeneratedKeys()) {
                keys.next();
                return keys.getLong(1);
            }
        }
    }

    /**
     * Returns the identifier of the user with the given nickname, inserting it when
     * it does not exist yet and leaving it untouched otherwise.
     *
     * <p>Uses an atomic {@code INSERT ... ON DUPLICATE KEY UPDATE} so two matches
     * finishing at the same time on different threads cannot create duplicate users:
     * the database serializes them on the {@code nickname} unique index and the
     * {@code LAST_INSERT_ID(id)} trick returns the existing id on a duplicate.
     */
    private long ensureUser(Connection connection, String nickname) throws SQLException {
        String upsert = "INSERT INTO utenti (nickname) VALUES (?) "
                + "ON DUPLICATE KEY UPDATE id = LAST_INSERT_ID(id)";
        try (PreparedStatement statement =
                     connection.prepareStatement(upsert, Statement.RETURN_GENERATED_KEYS)) {
            statement.setString(1, nickname);
            statement.executeUpdate();
            try (ResultSet keys = statement.getGeneratedKeys()) {
                keys.next();
                return keys.getLong(1);
            }
        }
    }

    private void insertParticipation(Connection connection, long gameId, long userId, int score)
            throws SQLException {
        String insert = "INSERT INTO partecipazioni (id_partita, id_utente, punteggio) VALUES (?, ?, ?)";
        try (PreparedStatement statement = connection.prepareStatement(insert)) {
            statement.setLong(1, gameId);
            statement.setLong(2, userId);
            statement.setInt(3, score);
            statement.executeUpdate();
        }
    }

    private Connection openConnection() throws SQLException {
        return DriverManager.getConnection(this.jdbcUrl, this.user, this.password);
    }

    /**
     * Final result of a single player in a finished match.
     *
     * @param nickname player nickname
     * @param score prestige points scored by the player
     */
    public record PlayerResult(String nickname, int score) {
    }
}
