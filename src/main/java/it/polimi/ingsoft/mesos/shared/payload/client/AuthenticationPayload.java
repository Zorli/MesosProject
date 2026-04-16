package it.polimi.ingsoft.mesos.shared.payload.client;
import java.io.Serializable;

public class AuthenticationPayload implements Serializable{
    public final String nickname;

    public AuthenticationPayload(String nickname) {
        this.nickname = nickname;
    }
}
