package it.polimi.ingsoft.mesos.client.view.tui;

record TuiCommand(TuiCommandType type, String argument) {
    int intArgument() {
        return Integer.parseInt(this.argument);
    }
}
