package it.polimi.ingsoft.mesos.client.view.tui;

enum TuiCommandType {
    //All the command supported here, with the common usage
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

    TuiCommandType(String usage, boolean argumentRequired, boolean integerArgument) {
        this.usage = usage;
        this.argumentRequired = argumentRequired;
        this.integerArgument = integerArgument;
    }

    String usage() {
        return this.usage;
    }

    boolean argumentRequired() {
        return this.argumentRequired;
    }

    boolean integerArgument() {
        return this.integerArgument;
    }
}
