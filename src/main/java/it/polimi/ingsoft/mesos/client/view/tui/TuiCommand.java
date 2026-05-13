package it.polimi.ingsoft.mesos.client.view.tui;

final class TuiCommand {
    private final TuiCommandType type;
    private final String argument;

    TuiCommand(TuiCommandType type, String argument) {
        this.type = type;
        this.argument = argument;
    }

    TuiCommandType type() {
        return this.type;
    }

    String argument() {
        return this.argument;
    }

    int intArgument() {
        return Integer.parseInt(this.argument);
    }
}
