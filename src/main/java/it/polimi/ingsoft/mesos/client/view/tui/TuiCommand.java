package it.polimi.ingsoft.mesos.client.view.tui;

import java.util.List;

/**
 * Parsed representation of one command typed in the text user interface.
 *
 * <p>This class is a small package-private value object used only inside the TUI
 * package. {@link TuiCommandParser} creates it after reading a raw input line,
 * and {@link TuiView} reads it to decide which method must be called on
 * the server proxy.
 *
 * <p>The parser validates only the command keyword and the number of arguments
 * before creating this object. This class stores those already separated
 * arguments and provides small typed accessors, such as {@link #intArgument(int)},
 * for the command execution code.
 */
final class TuiCommand {
    private final TuiCommandType type;
    private final List<String> arguments;

    /**
     * Creates a parsed command.
     *
     * @param type command selected by the parser
     * @param arguments command arguments
     */
    TuiCommand(TuiCommandType type, List<String> arguments) {
        this.type = type;
        this.arguments = List.copyOf(arguments);
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
     * Returns one parsed argument as text.
     *
     * @param index zero-based argument index
     * @return argument text
     */
    String argument(int index) {
        return this.arguments.get(index);
    }

    /**
     * Returns one parsed argument as an integer.
     *
     * <p>This method is intended only for command positions that the command
     * execution expects to be integers.
     *
     * @param index zero-based argument index
     * @return argument converted to an integer
     * @throws IllegalArgumentException if the stored argument is not a valid integer;
     *                                  the message uses the command usage string so
     *                                  the TUI can show the user the correct syntax
     */
    int intArgument(int index) {
        try {
            return Integer.parseInt(this.argument(index));
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException("Usage: " + this.type.usage());
        }
    }
}
