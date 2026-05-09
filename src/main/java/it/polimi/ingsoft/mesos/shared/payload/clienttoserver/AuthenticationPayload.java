package it.polimi.ingsoft.mesos.shared.payload.clienttoserver;
import java.io.Serializable;

public class AuthenticationPayload implements Serializable, PreLobbyClientPayload{
    public final String nickname;

    public AuthenticationPayload(String nickname) {
        this.nickname = nickname;
    }
}
