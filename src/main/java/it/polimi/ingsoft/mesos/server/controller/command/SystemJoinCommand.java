package it.polimi.ingsoft.mesos.server.controller.command;

import it.polimi.ingsoft.mesos.server.virtualview.Listener;

import java.util.Objects;

public class SystemJoinCommand implements QueuedCommand {
    public final String nickname;
    public final Listener clientView;

    public SystemJoinCommand(String nickname, Listener clientView) {
        this.nickname = nickname;
        this.clientView = clientView;
    }

    @Override
    public boolean equals(Object object) {
        if (this == object) {
            return true;
        }
        if (!(object instanceof SystemJoinCommand that)) {
            return false;
        }
        return Objects.equals(this.nickname, that.nickname)
                && Objects.equals(this.clientView, that.clientView);
    }

    @Override
    public int hashCode() {
        return Objects.hash(this.nickname, this.clientView);
    }
}
