package it.polimi.ingsoft.mesos.client.view.tui;

/**
 * Parsed representation of one command typed in the text user interface.
 *
 * <p>This class is a small package-private value object used only inside the TUI
 * package. {@link TuiCommandParser} creates it after reading a raw input line,
 * and {@link TuiView} reads it to decide which method must be called on
 * the server proxy.
 *
 * <p>The class does not validate the command. Validation belongs to
 * {@link TuiCommandParser}. For this reason, a {@code TuiCommand} should be
 * created only after the parser has already checked that the command type and
 * the argument are coherent.
 */
final class TuiCommand {
    private final TuiCommandType type;
    private final String argument;

    /**
     * Creates a parsed command.
     *
     * @param type command selected by the parser
     * @param argument command argument, or {@code null} when the command does not
     *                 need an argument
     */
    TuiCommand(TuiCommandType type, String argument) {
        this.type = type;
        this.argument = argument;
    }

    /**
     * Returns the parsed command type.
     *
     * @return command type used by the controller switch
     */
    TuiCommandType type() {
        return this.type;
    }

    /**
     * Returns the parsed argument as text.
     *
     * @return argument text, or {@code null} when the command has no argument
     */
    String argument() {
        return this.argument;
    }

    /**
     * Returns the parsed argument as an integer.
     *
     * <p>This method is intended only for command types whose argument was already
     * validated as an integer by {@link TuiCommandParser}.
     *
     * @return argument converted to an integer
     * @throws NumberFormatException if the stored argument is missing or is not a
     *                               valid integer
     */
    int intArgument() {
        return Integer.parseInt(this.argument);
    }
}
