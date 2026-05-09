package it.polimi.ingsoft.mesos.shared.payload.clienttoserver;

import it.polimi.ingsoft.mesos.server.model.Listener;

public class SystemJoinPayload implements ClientPayload {
    public final String nickname;
    public final Listener clientView;
    public SystemJoinPayload(String nickname, Listener clientView) {
        this.nickname = nickname;
        this.clientView = clientView;
    }

}
