package it.polimi.ingsoft.mesos.server.persistence.savedmatch;

/**
 * Centralized PostgreSQL SQL statements for saved match persistence.
 */
final class PostgresSavedMatchQueries {
    static final String CREATE_SAVED_MATCHES_TABLE = """
            CREATE TABLE IF NOT EXISTS saved_matches (
                game_id TEXT PRIMARY KEY,
                host_nickname TEXT NOT NULL,
                player_count INTEGER NOT NULL CHECK (player_count BETWEEN 2 AND 5),
                status TEXT NOT NULL CHECK (status IN ('IN_PROGRESS', 'COMPLETED')),
                current_era TEXT NOT NULL CHECK (current_era IN ('first', 'second', 'third')),
                game_status TEXT NOT NULL CHECK (game_status IN ('SETUP', 'IN_PROGRESS', 'FINISHED')),
                round_number INTEGER NOT NULL CHECK (round_number >= 0),
                round_phase TEXT NOT NULL CHECK (round_phase IN (
                    'PLACING_TOTEMS',
                    'PLAYER_DRAWS',
                    'EXTRA_DRAWS',
                    'RESOLVING_EFFECTS',
                    'RESETTING_BOARD',
                    'FINAL_SCORING'
                )),
                turn_phase TEXT NOT NULL CHECK (turn_phase IN ('WAITING_FOR_CARD_SELECTION', 'TURN_COMPLETED')),
                active_player_nickname TEXT NULL,
                current_player_index INTEGER NOT NULL CHECK (current_player_index >= 0),
                last_checkpoint_type TEXT NOT NULL CHECK (last_checkpoint_type IN (
                    'GAME_INITIALIZED',
                    'TURN_STARTED',
                    'ERA_CHANGED',
                    'TURN_FINISHED',
                    'MATCH_COMPLETED'
                )),
                created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
                updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
            )
            """;

    static final String CREATE_SAVED_MATCH_PLAYERS_TABLE = """
            CREATE TABLE IF NOT EXISTS saved_match_players (
                game_id TEXT NOT NULL,
                nickname TEXT NOT NULL,
                player_index INTEGER NOT NULL CHECK (player_index >= 0),
                totem_color TEXT NOT NULL CHECK (totem_color IN ('RED', 'BLUE', 'WHITE', 'YELLOW', 'BLACK')),
                food INTEGER NOT NULL,
                prestige_points INTEGER NOT NULL,
                is_online BOOLEAN NOT NULL DEFAULT FALSE,
                session_token TEXT NULL,
                last_seen_at TIMESTAMPTZ NULL,
                PRIMARY KEY (game_id, nickname),
                CONSTRAINT fk_saved_match_players_match
                    FOREIGN KEY (game_id) REFERENCES saved_matches(game_id) ON DELETE CASCADE,
                CONSTRAINT uq_saved_match_players_index UNIQUE (game_id, player_index),
                CONSTRAINT chk_saved_match_players_presence
                    CHECK (
                        (is_online = FALSE AND session_token IS NULL)
                        OR
                        (is_online = TRUE AND session_token IS NOT NULL)
                    )
            )
            """;

    static final String CREATE_SAVED_MATCH_TURN_ORDER_TABLE = """
            CREATE TABLE IF NOT EXISTS saved_match_turn_order (
                game_id TEXT NOT NULL,
                turn_position INTEGER NOT NULL CHECK (turn_position >= 0),
                nickname TEXT NOT NULL,
                PRIMARY KEY (game_id, turn_position),
                CONSTRAINT fk_saved_match_turn_order_match
                    FOREIGN KEY (game_id) REFERENCES saved_matches(game_id) ON DELETE CASCADE,
                CONSTRAINT fk_saved_match_turn_order_player
                    FOREIGN KEY (game_id, nickname) REFERENCES saved_match_players(game_id, nickname) ON DELETE CASCADE,
                CONSTRAINT uq_saved_match_turn_order_player UNIQUE (game_id, nickname)
            )
            """;

    static final String CREATE_SAVED_MATCH_TURN_CHOICES_TABLE = """
            CREATE TABLE IF NOT EXISTS saved_match_turn_choices (
                game_id TEXT NOT NULL,
                nickname TEXT NOT NULL,
                remaining_upper_choices INTEGER NOT NULL CHECK (remaining_upper_choices >= 0),
                remaining_lower_choices INTEGER NOT NULL CHECK (remaining_lower_choices >= 0),
                PRIMARY KEY (game_id, nickname),
                CONSTRAINT fk_saved_match_turn_choices_player
                    FOREIGN KEY (game_id, nickname) REFERENCES saved_match_players(game_id, nickname) ON DELETE CASCADE
            )
            """;

    static final String CREATE_SAVED_MATCH_OFFER_SPACES_TABLE = """
            CREATE TABLE IF NOT EXISTS saved_match_offer_spaces (
                game_id TEXT NOT NULL,
                offer_space_code TEXT NOT NULL,
                position_index INTEGER NOT NULL CHECK (position_index >= 0),
                occupied_by_nickname TEXT NULL,
                PRIMARY KEY (game_id, offer_space_code),
                CONSTRAINT fk_saved_match_offer_spaces_match
                    FOREIGN KEY (game_id) REFERENCES saved_matches(game_id) ON DELETE CASCADE,
                CONSTRAINT fk_saved_match_offer_spaces_player
                    FOREIGN KEY (game_id, occupied_by_nickname) REFERENCES saved_match_players(game_id, nickname) ON DELETE CASCADE,
                CONSTRAINT uq_saved_match_offer_spaces_position UNIQUE (game_id, position_index)
            )
            """;

    static final String CREATE_SAVED_MATCH_STARTING_BLOCKS_TABLE = """
            CREATE TABLE IF NOT EXISTS saved_match_starting_blocks (
                game_id TEXT NOT NULL,
                block_position INTEGER NOT NULL CHECK (block_position >= 0),
                food_modifier INTEGER NOT NULL,
                prestige_modifier INTEGER NOT NULL,
                occupied_by_nickname TEXT NULL,
                PRIMARY KEY (game_id, block_position),
                CONSTRAINT fk_saved_match_starting_blocks_match
                    FOREIGN KEY (game_id) REFERENCES saved_matches(game_id) ON DELETE CASCADE,
                CONSTRAINT fk_saved_match_starting_blocks_player
                    FOREIGN KEY (game_id, occupied_by_nickname) REFERENCES saved_match_players(game_id, nickname) ON DELETE CASCADE
            )
            """;

    static final String CREATE_SAVED_MATCH_BOARD_CARDS_TABLE = """
            CREATE TABLE IF NOT EXISTS saved_match_board_cards (
                game_id TEXT NOT NULL,
                card_zone TEXT NOT NULL CHECK (card_zone IN (
                    'UPPER_TRIBE',
                    'UPPER_BUILDING',
                    'LOWER_TRIBE',
                    'LOWER_BUILDING',
                    'TRIBE_DECK',
                    'BUILDING_DECK'
                )),
                position_index INTEGER NOT NULL CHECK (position_index >= 0),
                card_id INTEGER NOT NULL,
                PRIMARY KEY (game_id, card_zone, position_index),
                CONSTRAINT fk_saved_match_board_cards_match
                    FOREIGN KEY (game_id) REFERENCES saved_matches(game_id) ON DELETE CASCADE,
                CONSTRAINT uq_saved_match_board_card UNIQUE (game_id, card_id)
            )
            """;

    static final String CREATE_SAVED_MATCH_PLAYER_CARDS_TABLE = """
            CREATE TABLE IF NOT EXISTS saved_match_player_cards (
                game_id TEXT NOT NULL,
                nickname TEXT NOT NULL,
                card_zone TEXT NOT NULL CHECK (card_zone IN ('TRIBE_CHARACTER', 'TRIBE_BUILDING')),
                position_index INTEGER NOT NULL CHECK (position_index >= 0),
                card_id INTEGER NOT NULL,
                PRIMARY KEY (game_id, nickname, card_zone, position_index),
                CONSTRAINT fk_saved_match_player_cards_player
                    FOREIGN KEY (game_id, nickname) REFERENCES saved_match_players(game_id, nickname) ON DELETE CASCADE,
                CONSTRAINT uq_saved_match_player_card UNIQUE (game_id, card_id),
                CONSTRAINT uq_saved_match_player_card_for_player UNIQUE (game_id, nickname, card_id)
            )
            """;

    static final String CREATE_SAVED_MATCH_PLAYER_CARD_STATE_TABLE = """
            CREATE TABLE IF NOT EXISTS saved_match_player_card_state (
                game_id TEXT NOT NULL,
                nickname TEXT NOT NULL,
                card_id INTEGER NOT NULL,
                state_key TEXT NOT NULL,
                state_int_value INTEGER NULL,
                state_text_value TEXT NULL,
                PRIMARY KEY (game_id, nickname, card_id, state_key),
                CONSTRAINT fk_saved_match_player_card_state_card
                    FOREIGN KEY (game_id, nickname, card_id)
                    REFERENCES saved_match_player_cards(game_id, nickname, card_id)
                    ON DELETE CASCADE
            )
            """;

    static final String CREATE_SAVED_MATCH_STATUS_INDEX = """
            CREATE INDEX IF NOT EXISTS idx_saved_matches_status_updated_at
            ON saved_matches(status, updated_at DESC)
            """;

    static final String CREATE_SAVED_MATCH_PLAYER_PRESENCE_INDEX = """
            CREATE INDEX IF NOT EXISTS idx_saved_match_players_presence
            ON saved_match_players(game_id, is_online)
            """;

    static final String CREATE_TOUCH_UPDATED_AT_FUNCTION = """
            CREATE OR REPLACE FUNCTION touch_saved_matches_updated_at()
            RETURNS TRIGGER AS $$
            BEGIN
                NEW.updated_at = CURRENT_TIMESTAMP;
                RETURN NEW;
            END;
            $$ LANGUAGE plpgsql
            """;

    static final String DROP_TOUCH_UPDATED_AT_TRIGGER = """
            DROP TRIGGER IF EXISTS trg_saved_matches_touch_updated_at ON saved_matches
            """;

    static final String CREATE_TOUCH_UPDATED_AT_TRIGGER = """
            CREATE TRIGGER trg_saved_matches_touch_updated_at
            BEFORE UPDATE ON saved_matches
            FOR EACH ROW
            EXECUTE FUNCTION touch_saved_matches_updated_at()
            """;

    static final String CREATE_CLEANUP_FUNCTION = """
            CREATE OR REPLACE FUNCTION cleanup_stale_saved_matches()
            RETURNS TRIGGER AS $$
            BEGIN
                DELETE FROM saved_matches
                WHERE status = 'IN_PROGRESS'
                  AND updated_at < CURRENT_TIMESTAMP - INTERVAL '2 hours';
                RETURN NULL;
            END;
            $$ LANGUAGE plpgsql
            """;

    static final String DROP_CLEANUP_TRIGGER = """
            DROP TRIGGER IF EXISTS trg_saved_matches_cleanup_stale ON saved_matches
            """;

    static final String CREATE_CLEANUP_TRIGGER = """
            CREATE TRIGGER trg_saved_matches_cleanup_stale
            AFTER INSERT OR UPDATE ON saved_matches
            FOR EACH STATEMENT
            EXECUTE FUNCTION cleanup_stale_saved_matches()
            """;

    static final String UPSERT_SAVED_MATCH = """
            INSERT INTO saved_matches (
                game_id,
                host_nickname,
                player_count,
                status,
                current_era,
                game_status,
                round_number,
                round_phase,
                turn_phase,
                active_player_nickname,
                current_player_index,
                last_checkpoint_type
            )
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            ON CONFLICT (game_id) DO UPDATE SET
                host_nickname = EXCLUDED.host_nickname,
                player_count = EXCLUDED.player_count,
                status = EXCLUDED.status,
                current_era = EXCLUDED.current_era,
                game_status = EXCLUDED.game_status,
                round_number = EXCLUDED.round_number,
                round_phase = EXCLUDED.round_phase,
                turn_phase = EXCLUDED.turn_phase,
                active_player_nickname = EXCLUDED.active_player_nickname,
                current_player_index = EXCLUDED.current_player_index,
                last_checkpoint_type = EXCLUDED.last_checkpoint_type
            """;

    static final String UPSERT_SAVED_MATCH_PLAYER = """
            INSERT INTO saved_match_players (
                game_id,
                nickname,
                player_index,
                totem_color,
                food,
                prestige_points
            )
            VALUES (?, ?, ?, ?, ?, ?)
            ON CONFLICT (game_id, nickname) DO UPDATE SET
                player_index = EXCLUDED.player_index,
                totem_color = EXCLUDED.totem_color,
                food = EXCLUDED.food,
                prestige_points = EXCLUDED.prestige_points
            """;

    static final String DELETE_SAVED_MATCH_TURN_ORDER = """
            DELETE FROM saved_match_turn_order
            WHERE game_id = ?
            """;

    static final String INSERT_SAVED_MATCH_TURN_ORDER = """
            INSERT INTO saved_match_turn_order (game_id, turn_position, nickname)
            VALUES (?, ?, ?)
            """;

    static final String DELETE_SAVED_MATCH_TURN_CHOICES = """
            DELETE FROM saved_match_turn_choices
            WHERE game_id = ?
            """;

    static final String INSERT_SAVED_MATCH_TURN_CHOICES = """
            INSERT INTO saved_match_turn_choices (
                game_id,
                nickname,
                remaining_upper_choices,
                remaining_lower_choices
            )
            VALUES (?, ?, ?, ?)
            """;

    static final String DELETE_SAVED_MATCH_OFFER_SPACES = """
            DELETE FROM saved_match_offer_spaces
            WHERE game_id = ?
            """;

    static final String INSERT_SAVED_MATCH_OFFER_SPACE = """
            INSERT INTO saved_match_offer_spaces (
                game_id,
                offer_space_code,
                position_index,
                occupied_by_nickname
            )
            VALUES (?, ?, ?, ?)
            """;

    static final String DELETE_SAVED_MATCH_STARTING_BLOCKS = """
            DELETE FROM saved_match_starting_blocks
            WHERE game_id = ?
            """;

    static final String INSERT_SAVED_MATCH_STARTING_BLOCK = """
            INSERT INTO saved_match_starting_blocks (
                game_id,
                block_position,
                food_modifier,
                prestige_modifier,
                occupied_by_nickname
            )
            VALUES (?, ?, ?, ?, ?)
            """;

    static final String DELETE_SAVED_MATCH_BOARD_CARDS = """
            DELETE FROM saved_match_board_cards
            WHERE game_id = ?
            """;

    static final String INSERT_SAVED_MATCH_BOARD_CARD = """
            INSERT INTO saved_match_board_cards (
                game_id,
                card_zone,
                position_index,
                card_id
            )
            VALUES (?, ?, ?, ?)
            """;

    static final String DELETE_SAVED_MATCH_PLAYER_CARD_STATE = """
            DELETE FROM saved_match_player_card_state
            WHERE game_id = ?
            """;

    static final String DELETE_SAVED_MATCH_PLAYER_CARDS = """
            DELETE FROM saved_match_player_cards
            WHERE game_id = ?
            """;

    static final String INSERT_SAVED_MATCH_PLAYER_CARD = """
            INSERT INTO saved_match_player_cards (
                game_id,
                nickname,
                card_zone,
                position_index,
                card_id
            )
            VALUES (?, ?, ?, ?, ?)
            """;

    static final String INSERT_SAVED_MATCH_PLAYER_CARD_STATE = """
            INSERT INTO saved_match_player_card_state (
                game_id,
                nickname,
                card_id,
                state_key,
                state_int_value,
                state_text_value
            )
            VALUES (?, ?, ?, ?, ?, ?)
            """;

    static final String SELECT_SAVED_MATCH = """
            SELECT
                game_id,
                host_nickname,
                player_count,
                status,
                current_era,
                game_status,
                round_number,
                round_phase,
                turn_phase,
                active_player_nickname,
                current_player_index,
                last_checkpoint_type
            FROM saved_matches
            WHERE game_id = ?
            """;

    static final String SELECT_SAVED_MATCH_PLAYERS = """
            SELECT
                player_index,
                nickname,
                totem_color,
                food,
                prestige_points,
                is_online,
                session_token,
                last_seen_at
            FROM saved_match_players
            WHERE game_id = ?
            ORDER BY player_index
            """;

    static final String SELECT_SAVED_MATCH_TURN_ORDER = """
            SELECT turn_position, nickname
            FROM saved_match_turn_order
            WHERE game_id = ?
            ORDER BY turn_position
            """;

    static final String SELECT_SAVED_MATCH_TURN_CHOICES = """
            SELECT nickname, remaining_upper_choices, remaining_lower_choices
            FROM saved_match_turn_choices
            WHERE game_id = ?
            ORDER BY nickname
            """;

    static final String SELECT_SAVED_MATCH_OFFER_SPACES = """
            SELECT offer_space_code, position_index, occupied_by_nickname
            FROM saved_match_offer_spaces
            WHERE game_id = ?
            ORDER BY position_index
            """;

    static final String SELECT_SAVED_MATCH_STARTING_BLOCKS = """
            SELECT block_position, food_modifier, prestige_modifier, occupied_by_nickname
            FROM saved_match_starting_blocks
            WHERE game_id = ?
            ORDER BY block_position
            """;

    static final String SELECT_SAVED_MATCH_BOARD_CARDS = """
            SELECT card_zone, position_index, card_id
            FROM saved_match_board_cards
            WHERE game_id = ?
            ORDER BY card_zone, position_index
            """;

    static final String SELECT_SAVED_MATCH_PLAYER_CARDS = """
            SELECT nickname, card_zone, position_index, card_id
            FROM saved_match_player_cards
            WHERE game_id = ?
            ORDER BY nickname, card_zone, position_index
            """;

    static final String SELECT_SAVED_MATCH_PLAYER_CARD_STATE = """
            SELECT nickname, card_id, state_key, state_int_value, state_text_value
            FROM saved_match_player_card_state
            WHERE game_id = ?
            ORDER BY nickname, card_id, state_key
            """;

    static final String SELECT_PLAYER_PRESENCE_FOR_UPDATE = """
            SELECT is_online, session_token
            FROM saved_match_players
            WHERE game_id = ? AND nickname = ?
            FOR UPDATE
            """;

    static final String UPDATE_PLAYER_ONLINE = """
            UPDATE saved_match_players
            SET is_online = TRUE,
                session_token = ?,
                last_seen_at = CURRENT_TIMESTAMP
            WHERE game_id = ? AND nickname = ?
            """;

    static final String UPDATE_PLAYER_OFFLINE = """
            UPDATE saved_match_players
            SET is_online = FALSE,
                session_token = NULL,
                last_seen_at = CURRENT_TIMESTAMP
            WHERE game_id = ? AND nickname = ?
            """;

    static final String DELETE_EXPIRED_IN_PROGRESS_MATCHES = """
            DELETE FROM saved_matches
            WHERE status = 'IN_PROGRESS'
              AND updated_at < CURRENT_TIMESTAMP - INTERVAL '2 hours'
            """;

    /**
     * Prevents instantiation of the SQL-constants holder class.
     */
    private PostgresSavedMatchQueries() {
    }
}
