package it.polimi.ingsoft.mesos.client.view.tui;

/**
 * Represents one parsed terminal command together with its typed argument, if any.
 */
final class TuiCommand {
    private final Type type;
    private final String textArgument;
    private final Integer integerArgument;

    /**
     * Creates a command instance.
     *
     * @param type command kind
     * @param textArgument optional textual argument
     * @param integerArgument optional integer argument
     */
    TuiCommand(Type type, String textArgument, Integer integerArgument) {
        this.type = type;
        this.textArgument = textArgument;
        this.integerArgument = integerArgument;
    }

    /**
     * Returns the parsed command kind.
     *
     * @return command kind
     */
    Type getType() {
        return this.type;
    }

    /**
     * Returns the textual argument when the command expects one.
     *
     * @return textual argument, or {@code null} when absent
     */
    String getTextArgument() {
        return this.textArgument;
    }

    /**
     * Returns the numeric argument when the command expects one.
     *
     * @return numeric argument, or {@code null} when absent
     */
    Integer getIntegerArgument() {
        return this.integerArgument;
    }

    /**
     * Enumerates all commands supported by the terminal client.
     */
    enum Type {
        NO_OP,
        HELP,
        STATUS,
        AUTHENTICATE,
        LIST_LOBBIES,
        CREATE_LOBBY,
        JOIN_LOBBY,
        EXIT_LOBBY,
        START_LOBBY,
        MOVE_TOTEM,
        CHOOSE_CARD,
        EXTRA_DRAW,
        QUIT
    }
}
