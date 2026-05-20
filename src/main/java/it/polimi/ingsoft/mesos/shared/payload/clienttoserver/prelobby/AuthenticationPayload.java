package it.polimi.ingsoft.mesos.shared.payload.clienttoserver.prelobby;

import java.io.Serializable;

/**
 * Payload sent by a client to request login authentication.
 * <p>
 * This is the first message sent by a client to establish a session,
 * binding a requested unique nickname to the client's connection.
 */
public class AuthenticationPayload implements Serializable, PreLobbyClientPayload{
    /** The unique nickname requested by the player. */
    public final String nickname;

    /**
     * Constructs a new AuthenticationPayload.
     *
     * @param nickname the requested player nickname
     */
    public AuthenticationPayload(String nickname) {
        this.nickname = nickname;
    }
}
