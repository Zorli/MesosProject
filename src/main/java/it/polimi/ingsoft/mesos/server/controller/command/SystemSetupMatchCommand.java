package it.polimi.ingsoft.mesos.server.controller.command;

public class SystemSetupMatchCommand implements QueuedCommand {
    @Override
    public boolean equals(Object object) {
        return object instanceof SystemSetupMatchCommand;
    }

    @Override
    public int hashCode() {
        return SystemSetupMatchCommand.class.hashCode();
    }
}
