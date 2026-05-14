package it.polimi.ingsoft.mesos.client.view.tui;

/**
 * Supported command types of the text user interface.
 *
 * <p>Each enum constant describes one command after it has been recognized by
 * {@link TuiCommandParser}. The enum stores the small amount of metadata needed
 * by the parser:
 * <ul>
 *     <li>the usage string printed when the user writes the command incorrectly;</li>
 *     <li>whether the command requires an argument;</li>
 *     <li>whether that argument must be an integer.</li>
 * </ul>
 *
 * <p>Aliases such as {@code h}, {@code ll}, or {@code q} are not stored here.
 * They are mapped to these command types by {@link TuiCommandParser}.
 */
enum TuiCommandType {
    HELP("help", false, false),
    AUTH("auth <nickname>", true, false),
    LIST_LOBBIES("list-lobbies", false, false),
    CREATE_LOBBY("create-lobby <capacity>", true, true),
    JOIN_LOBBY("join-lobby <lobby-id>", true, true),
    QUIT_LOBBY("quit-lobby", false, false),
    START_LOBBY("start-lobby", false, false),
    MOVE_TOTEM("move-totem <tile>", true, false),
    CHOOSE_CARD("choose-card <card-id>", true, true),
    EXTRA_DRAW("extra-draw <card-id>", true, true),
    QUIT("quit", false, false);

    private final String usage;
    private final boolean argumentRequired;
    private final boolean integerArgument;

    /**
     * Creates one command type description.
     *
     * @param usage text that explains how the command must be written
     * @param argumentRequired {@code true} if the command needs one argument
     * @param integerArgument {@code true} if the argument must be parsed as an integer
     */
    TuiCommandType(String usage, boolean argumentRequired, boolean integerArgument) {
        this.usage = usage;
        this.argumentRequired = argumentRequired;
        this.integerArgument = integerArgument;
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
     * Tells whether this command requires exactly one argument.
     *
     * @return {@code true} when the command needs an argument
     */
    boolean argumentRequired() {
        return this.argumentRequired;
    }

    /**
     * Tells whether the command argument must be an integer.
     *
     * <p>This is meaningful only when {@link #argumentRequired()} returns
     * {@code true}.
     *
     * @return {@code true} when the parser must validate the argument as an integer
     */
    boolean integerArgument() {
        return this.integerArgument;
    }
}
