package it.polimi.ingsoft.mesos.server.persistence.savedmatch;

import it.polimi.ingsoft.mesos.server.persistence.postgres.PostgresSchemaModule;

import java.util.List;

/**
 * PostgreSQL schema module for persisted resumable matches.
 */
public final class PostgresSavedMatchSchemaModule implements PostgresSchemaModule {
    private static final PostgresSavedMatchSchemaModule INSTANCE = new PostgresSavedMatchSchemaModule();

    private PostgresSavedMatchSchemaModule() {
    }

    /**
     * Returns the shared saved-match schema module instance.
     *
     * @return singleton schema module
     */
    public static PostgresSavedMatchSchemaModule getInstance() {
        return INSTANCE;
    }

    @Override
    public String moduleName() {
        return "saved_matches";
    }

    @Override
    public List<String> createStatements() {
        return List.of(
                PostgresSavedMatchQueries.CREATE_SAVED_MATCHES_TABLE,
                PostgresSavedMatchQueries.CREATE_SAVED_MATCH_PLAYERS_TABLE,
                PostgresSavedMatchQueries.CREATE_SAVED_MATCH_TURN_ORDER_TABLE,
                PostgresSavedMatchQueries.CREATE_SAVED_MATCH_TURN_CHOICES_TABLE,
                PostgresSavedMatchQueries.CREATE_SAVED_MATCH_OFFER_SPACES_TABLE,
                PostgresSavedMatchQueries.CREATE_SAVED_MATCH_STARTING_BLOCKS_TABLE,
                PostgresSavedMatchQueries.CREATE_SAVED_MATCH_BOARD_CARDS_TABLE,
                PostgresSavedMatchQueries.CREATE_SAVED_MATCH_PLAYER_CARDS_TABLE,
                PostgresSavedMatchQueries.CREATE_SAVED_MATCH_PLAYER_CARD_STATE_TABLE,
                PostgresSavedMatchQueries.CREATE_SAVED_MATCH_STATUS_INDEX,
                PostgresSavedMatchQueries.CREATE_SAVED_MATCH_PLAYER_PRESENCE_INDEX,
                PostgresSavedMatchQueries.CREATE_TOUCH_UPDATED_AT_FUNCTION,
                PostgresSavedMatchQueries.DROP_TOUCH_UPDATED_AT_TRIGGER,
                PostgresSavedMatchQueries.CREATE_TOUCH_UPDATED_AT_TRIGGER,
                PostgresSavedMatchQueries.CREATE_CLEANUP_FUNCTION,
                PostgresSavedMatchQueries.DROP_CLEANUP_TRIGGER,
                PostgresSavedMatchQueries.CREATE_CLEANUP_TRIGGER
        );
    }

    @Override
    public List<String> requiredTables() {
        return List.of(
                "saved_matches",
                "saved_match_players",
                "saved_match_turn_order",
                "saved_match_turn_choices",
                "saved_match_offer_spaces",
                "saved_match_starting_blocks",
                "saved_match_board_cards",
                "saved_match_player_cards",
                "saved_match_player_card_state"
        );
    }
}
