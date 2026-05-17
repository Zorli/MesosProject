package it.polimi.ingsoft.mesos.server.controller.command;

import it.polimi.ingsoft.mesos.server.virtualview.Listener;
import it.polimi.ingsoft.mesos.shared.payload.clienttoserver.ClientPayload;

import java.util.Objects;

public class SystemJoinCommand implements QueuedCommand {
    public final String sessionId;
    public final Listener clientView;


    public SystemJoinCommand(String nickname, Listener clientView) {
        this.sessionId = nickname;
        this.clientView = clientView;
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
