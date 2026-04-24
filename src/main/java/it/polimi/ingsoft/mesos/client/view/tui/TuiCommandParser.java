package it.polimi.ingsoft.mesos.client.view.tui;

import java.util.Locale;

/**
 * Converts raw terminal input into typed {@link TuiCommand} instances.
 */
final class TuiCommandParser {
    private TuiCommandParser() {
    }

    /**
     * Parses one raw terminal line.
     *
     * @param commandLine raw terminal input
     * @return parsed command
     */
    static TuiCommand parse(String commandLine) {
        if (commandLine == null || commandLine.isBlank()) {
            return new TuiCommand(TuiCommand.Type.NO_OP, null, null);
        }

        String trimmed = commandLine.trim();
        String[] split = trimmed.split("\\s+", 2);
        String command = split[0].toLowerCase(Locale.ROOT);
        String argument = split.length > 1 ? split[1].trim() : "";

        return switch (command) {
            case "help" -> requireNoArgument(TuiCommand.Type.HELP, argument, "help");
            case "status", "show" -> requireNoArgument(TuiCommand.Type.STATUS, argument, "status");
            case "auth", "login" -> new TuiCommand(
                    TuiCommand.Type.AUTHENTICATE,
                    requireTextArgument(argument, "auth"),
                    null
            );
            case "list-lobbies", "lobbies", "ls" -> requireNoArgument(
                    TuiCommand.Type.LIST_LOBBIES,
                    argument,
                    "list-lobbies"
            );
            case "create-lobby", "create" -> new TuiCommand(
                    TuiCommand.Type.CREATE_LOBBY,
                    null,
                    requireIntArgument(argument, "create-lobby")
            );
            case "join-lobby", "join" -> new TuiCommand(
                    TuiCommand.Type.JOIN_LOBBY,
                    null,
                    requireIntArgument(argument, "join-lobby")
            );
            case "quit-lobby", "leave-lobby", "leave" -> requireNoArgument(
                    TuiCommand.Type.EXIT_LOBBY,
                    argument,
                    "quit-lobby"
            );
            case "start-lobby", "start" -> requireNoArgument(
                    TuiCommand.Type.START_LOBBY,
                    argument,
                    "start-lobby"
            );
            case "move-totem", "move" -> new TuiCommand(
                    TuiCommand.Type.MOVE_TOTEM,
                    requireTextArgument(argument, "move-totem"),
                    null
            );
            case "choose-card", "choose" -> new TuiCommand(
                    TuiCommand.Type.CHOOSE_CARD,
                    null,
                    requireIntArgument(argument, "choose-card")
            );
            case "extra-draw", "draw" -> new TuiCommand(
                    TuiCommand.Type.EXTRA_DRAW,
                    null,
                    requireIntArgument(argument, "extra-draw")
            );
            case "quit", "exit" -> requireNoArgument(TuiCommand.Type.QUIT, argument, "quit");
            default -> throw new IllegalArgumentException("Unsupported command. Type 'help' to list commands.");
        };
    }

    /**
     * Validates that the command has no trailing argument.
     *
     * @param type parsed command kind
     * @param argument raw argument text
     * @param usageCommand usage string for error reporting
     * @return command without arguments
     */
    private static TuiCommand requireNoArgument(TuiCommand.Type type, String argument, String usageCommand) {
        if (!argument.isBlank()) {
            throw new IllegalArgumentException("Usage: " + usageCommand);
        }
        return new TuiCommand(type, null, null);
    }

    /**
     * Validates and returns the textual argument for a command.
     *
     * @param argument raw argument text
     * @param usageCommand usage string for error reporting
     * @return normalized textual argument
     */
    private static String requireTextArgument(String argument, String usageCommand) {
        if (argument.isBlank()) {
            throw new IllegalArgumentException("Usage: " + usageCommand + " <argument>");
        }
        return argument;
    }

    /**
     * Validates and returns the integer argument for a command.
     *
     * @param argument raw argument text
     * @param usageCommand usage string for error reporting
     * @return parsed integer argument
     */
    private static int requireIntArgument(String argument, String usageCommand) {
        if (argument.isBlank()) {
            throw new IllegalArgumentException("Usage: " + usageCommand + " <integer>");
        }
        try {
            return Integer.parseInt(argument);
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException("Usage: " + usageCommand + " <integer>");
        }
    }
}
