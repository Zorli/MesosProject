package it.polimi.ingsoft.mesos.client.view.tui;

import java.util.ArrayList;
import java.util.List;

/**
 * Parses one textual command entered in the TUI.
 *
 * <p>The parser accepts commands with this shape:
 * <pre>
 * command
 * command argument...
 * </pre>
 *
 * <p>The first word is always the command keyword. The remaining words are command
 * arguments separated by whitespace. The parser checks only the command type and
 * the number of arguments. Type-specific meanings, such as integer conversion or
 * totem color validation, are handled later by the command execution code.
 */
final class TuiCommandParser {
    /**
     * Converts one input line into a {@link TuiCommand}.
     *
     * <p>Examples:
     * <ul>
     *     <li>{@code help} becomes a command with no argument.</li>
     *     <li>{@code auth ada} becomes command {@code auth} with argument {@code ada}.</li>
     *     <li>{@code create-lobby 2 red} becomes command {@code create-lobby} with two arguments.</li>
     * </ul>
     *
     * @param line the raw line typed by the user
     * @return the parsed command type and its arguments
     * @throws IllegalArgumentException if the command is unknown or has the wrong number of arguments
     */
    TuiCommand parse(String line) {
        String[] parts = splitLineIntoWords(line);
        TuiCommandType type = parseType(parts[0]);

        int argumentCount = parts.length - 1;
        if (argumentCount != type.argumentCount()) {
            throw new IllegalArgumentException("Usage: " + type.usage());
        }

        List<String> arguments = new ArrayList<>(argumentCount);
        for (int index = 1; index < parts.length; index++) {
            arguments.add(parts[index]);
        }

        return new TuiCommand(type, arguments);
    }

    /**
     * Splits a command line into command words.
     *
     * <p>{@code trim()} removes spaces before the first word and after the last word.
     *
     * <p>{@code split("\\s+")} separates the line every time it finds one or more
     * whitespace characters. In practice, whitespace means spaces, tabs, or similar
     * separators. The {@code +} means that many consecutive spaces are treated as one
     * separator. This is why {@code join-lobby 3 red} becomes three words:
     * command keyword, lobby id, and totem color.
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
        String s = keyword.toLowerCase();
        return switch (s) {
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
            case "skip-extra-draw", "sed" -> TuiCommandType.SKIP_EXTRA_DRAW;
            case "quit", "exit", "q" -> TuiCommandType.QUIT;
            default -> throw new IllegalArgumentException("Unknown command. Type 'help'.");
        };
    }
}
