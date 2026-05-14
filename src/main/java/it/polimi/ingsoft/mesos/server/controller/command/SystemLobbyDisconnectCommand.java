package it.polimi.ingsoft.mesos.server.controller.command;

import it.polimi.ingsoft.mesos.server.virtualview.Listener;

import java.util.Objects;

public class SystemLobbyDisconnectCommand implements QueuedCommand {
    public final String nickname;
    public final Listener leavingListener;

    public SystemLobbyDisconnectCommand(String nickname, Listener leavingListener) {
        this.nickname = nickname;
        this.leavingListener = leavingListener;
    }

    @Override
    public boolean equals(Object object) {
        if (this == object) {
            return true;
        }
        if (!(object instanceof SystemLobbyDisconnectCommand that)) {
            return false;
        }
        return Objects.equals(this.nickname, that.nickname)
                && Objects.equals(this.leavingListener, that.leavingListener);
    }

    @Override
    public int hashCode() {
        return Objects.hash(this.nickname, this.leavingListener);
    }
}
