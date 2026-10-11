package it.polimi.ingsoft.mesos.client.view.tui;

/**
 * Supported command types of the text user interface.
 *
 * <p>Each enum constant describes one command after it has been recognized by
 * {@link TuiCommandParser}. The enum stores the small amount of metadata needed
 * by the parser:
 * <ul>
 *     <li>the usage string printed when the user writes the command incorrectly;</li>
 *     <li>the exact number of arguments accepted by the command.</li>
 * </ul>
 *
 * <p>Aliases such as {@code h}, {@code ll}, or {@code q} are not stored here.
 * They are mapped to these command types by {@link TuiCommandParser}.
 */
enum TuiCommandType {
    SKIP_EXTRA_DRAW("skip-extra-draw", 0),
    HELP("help", 0),
    AUTH("auth <nickname>", 1),
    LIST_LOBBIES("list-lobbies", 0),
    CREATE_LOBBY("create-lobby <capacity> <totem-color>", 2),
    JOIN_LOBBY("join-lobby <lobby-id> <totem-color>", 2),
    QUIT_LOBBY("quit-lobby", 0),
    START_LOBBY("start-lobby", 0),
    MOVE_TOTEM("move-totem <tile>", 1),
    CHOOSE_CARD("choose-card <card-id>", 1),
    EXTRA_DRAW("extra-draw <card-id>", 1),
    QUIT("quit", 0);


    private final String usage;
    private final int argumentCount;

    /**
     * Creates one command type description.
     *
     * @param usage text that explains how the command must be written
     * @param argumentCount exact number of arguments accepted by the command
     */
    TuiCommandType(String usage, int argumentCount) {
        this.usage = usage;
        this.argumentCount = argumentCount;
    }

    /**
     * Returns the usage text shown in parser errors.
     *
     * @return command usage string
     */
    String usage() {
        return this.usage;
    }

    /**
     * Returns how many words must appear after the command keyword.
     *
     * <p>For example, {@code help} expects {@code 0} arguments, while
     * {@code join-lobby <lobby-id> <totem-color>} expects {@code 2}.
     *
     * @return exact number of arguments accepted by this command type
     */
    int argumentCount() {
        return this.argumentCount;
    }
}
