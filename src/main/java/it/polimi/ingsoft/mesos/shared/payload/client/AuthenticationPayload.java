package it.polimi.ingsoft.mesos.shared.payload.client;

public class AuthenticationPayload {
    public final String nickname;

    public AuthenticationPayload(String nickname) {
        this.nickname = nickname;
    }
}
