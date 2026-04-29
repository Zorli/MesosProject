package it.polimi.ingsoft.mesos.client.view.tui;

final class TuiCommandParser {
    //Simple and trivial class to pars a command, it return a known command,
    //the only scope of this class is to parse, raise error if necessary and return the
    //inferrred command
    TuiCommand parse(String line) {
        //Split the args
        String[] parts = line.trim().split("\\s+");
        //Parse the first keyword
        TuiCommandType type = parseType(parts[0]);


        boolean hasArgument = parts.length == 2;
        //If it has required args but was not provided with args we return the usage
        if (parts.length > 2 || hasArgument != type.argumentRequired()) {
            throw new IllegalArgumentException("Usage: " + type.usage());
        }
        //parse the first argument
        String argument = hasArgument ? parts[1] : null;
        //If it's an int try to convert it
        if (type.integerArgument()) {
            try {
                Integer.parseInt(argument);
            } catch (NumberFormatException exception) {
                throw new IllegalArgumentException("Usage: " + type.usage());
            }
        }

        return new TuiCommand(type, argument);
    }

    private static TuiCommandType parseType(String keyword) {
        return switch (keyword) {
            case "help" -> TuiCommandType.HELP;
            case "auth" -> TuiCommandType.AUTH;
            case "list-lobbies", "lobbies" -> TuiCommandType.LIST_LOBBIES;
            case "create-lobby", "create" -> TuiCommandType.CREATE_LOBBY;
            case "join-lobby", "join" -> TuiCommandType.JOIN_LOBBY;
            case "quit-lobby" -> TuiCommandType.QUIT_LOBBY;
            case "start-lobby" -> TuiCommandType.START_LOBBY;
            case "move-totem" -> TuiCommandType.MOVE_TOTEM;
            case "choose-card" -> TuiCommandType.CHOOSE_CARD;
            case "extra-draw" -> TuiCommandType.EXTRA_DRAW;
            case "quit", "exit" -> TuiCommandType.QUIT;
            default -> throw new IllegalArgumentException("Unknown command. Type 'help'.");
        };
    }
}
