package it.polimi.ingsoft.mesos.shared.payload.servertoclient.prelobby;

import java.io.Serializable;

public class AuthenticationResultPayload implements Serializable, PreLobbyServerPayload {
    public final String nickname;
    public final String message;

    public AuthenticationResultPayload(String nickname, String message) {
        this.nickname = nickname;
        this.message = message;
    }
}
