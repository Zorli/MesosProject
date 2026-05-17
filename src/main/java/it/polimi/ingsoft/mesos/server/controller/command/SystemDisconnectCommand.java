package it.polimi.ingsoft.mesos.server.controller.command;

import it.polimi.ingsoft.mesos.server.virtualview.Listener;
import it.polimi.ingsoft.mesos.shared.payload.clienttoserver.ClientPayload;

import java.util.Objects;

public class SystemDisconnectCommand implements QueuedCommand {
    public final Listener leavingListener;
    public final String sessionId;

    public SystemDisconnectCommand(String sessionId , Listener leavingListener) {
        this.sessionId = sessionId;
        this.leavingListener = leavingListener;
    }

    @Override
    public String getSessionId() {
        return sessionId;
    }
    @Override
    public ClientPayload getPayload() {
        return null;
    }
}
