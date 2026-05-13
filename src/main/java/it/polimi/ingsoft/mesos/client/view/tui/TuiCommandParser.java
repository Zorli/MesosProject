package it.polimi.ingsoft.mesos.client.view.tui;

/**
 * Parses one textual command entered in the TUI.
 *
 * <p>The parser accepts commands with this shape:
 * <pre>
 * command
 * command argument
 * </pre>
 *
 * <p>The first word is always the command keyword. The optional second word is the
 * command argument. More than one argument is rejected.
 */
final class TuiCommandParser {
    /**
     * Converts one input line into a {@link TuiCommand}.
     *
     * <p>Examples:
     * <ul>
     *     <li>{@code help} becomes a command with no argument.</li>
     *     <li>{@code auth ada} becomes command {@code auth} with argument {@code ada}.</li>
     *     <li>{@code create-lobby 2} becomes command {@code create-lobby} with argument {@code 2}.</li>
     * </ul>
     *
     * @param line the raw line typed by the user
     * @return the parsed command type and its optional argument
     * @throws IllegalArgumentException if the command is unknown, has the wrong number of arguments,
     *                                  or requires an integer argument but receives a non-integer one
     */
    TuiCommand parse(String line) {
        String[] parts = splitLineIntoWords(line);
        TuiCommandType type = parseType(parts[0]);

        int argumentCount = parts.length - 1;

        boolean hasArgument = argumentCount == 1;
        boolean tooManyArguments = argumentCount > 1;
        boolean missingRequiredArgument = type.argumentRequired() && !hasArgument;
        boolean unexpectedArgument = !type.argumentRequired() && hasArgument;

        if (tooManyArguments || missingRequiredArgument || unexpectedArgument) {
            throw new IllegalArgumentException("Usage: " + type.usage());
        }

        String argument = hasArgument ? parts[1] : null;

        if (type.integerArgument()) {
            try {
                Integer.parseInt(argument);
            } catch (NumberFormatException exception) {
                throw new IllegalArgumentException("Usage: " + type.usage());
            }
        }

        return new TuiCommand(type, argument);
    }

    /**
     * Splits a command line into words.
     *
     * <p>{@code trim()} removes spaces before the first word and after the last word.
     *
     * <p>{@code split("\\s+")} separates the line every time it finds one or more
     * whitespace characters. In practice, whitespace means spaces, tabs, or similar
     * separators. The {@code +} means that many consecutive spaces are treated as one
     * separator.
     *
     * <p>Example:
     * <pre>
     * "  auth    ada  " -> ["auth", "ada"]
     * </pre>
     */
    private static String[] splitLineIntoWords(String line) {
        return line.trim().split("\\s+");
    }

    /**
     * Maps the first word of the input line to one supported command type.
     *
     * @param keyword the command word or one of its aliases
     * @return the command type represented by the keyword
     * @throws IllegalArgumentException if the keyword is not a known command or alias
     */
    private static TuiCommandType parseType(String keyword) {
        return switch (keyword) {
            case "help", "h" -> TuiCommandType.HELP;
            case "auth", "a" -> TuiCommandType.AUTH;
            case "list-lobbies", "lobbies", "ll" -> TuiCommandType.LIST_LOBBIES;
            case "create-lobby", "create", "cl" -> TuiCommandType.CREATE_LOBBY;
            case "join-lobby", "join", "jl" -> TuiCommandType.JOIN_LOBBY;
            case "quit-lobby", "ql" -> TuiCommandType.QUIT_LOBBY;
            case "start-lobby", "sl" -> TuiCommandType.START_LOBBY;
            case "move-totem", "mt" -> TuiCommandType.MOVE_TOTEM;
            case "choose-card", "cc" -> TuiCommandType.CHOOSE_CARD;
            case "extra-draw", "ed" -> TuiCommandType.EXTRA_DRAW;
            case "quit", "exit", "q" -> TuiCommandType.QUIT;
            default -> throw new IllegalArgumentException("Unknown command. Type 'help'.");
        };
    }
}
