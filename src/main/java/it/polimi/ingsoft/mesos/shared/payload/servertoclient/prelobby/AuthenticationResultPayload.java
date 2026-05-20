package it.polimi.ingsoft.mesos.shared.payload.servertoclient.prelobby;

import java.io.Serializable;

/**
 * Payload sent by the server to inform a client of the result of their login attempt.
 * <p>
 * If authentication succeeds, the assigned player nickname is returned.
 * Otherwise, the payload contains details of the failure message.
 */
public class AuthenticationResultPayload implements Serializable, PreLobbyServerPayload {
    /** The authenticated player nickname (or requested nickname). */
    public final String nickname;

    /** The message outcome of authentication (e.g. success confirmation or error description). */
    public final String message;

    /**
     * Constructs a new AuthenticationResultPayload.
     *
     * @param nickname the player nickname
     * @param message the message outcome/result details
     */
    public AuthenticationResultPayload(String nickname, String message) {
        this.nickname = nickname;
        this.message = message;
    }
}
