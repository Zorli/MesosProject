package it.polimi.ingsoft.mesos.shared.payload.servertoclient.error;

import java.io.Serializable;
/**
 * Payload sent by the preLobby to report an application or validation error to the client.
 * <p>
 * This contains a standardized error code along with a human-readable message description.
 */
public class ErrorPayload implements Serializable, ErrorServerPayload {
    /** The standardized error code category (e.g. AUTH_ERROR, PHASE_ERROR, JOIN_ERROR). */
    public final String code;

    /** The detailed description explaining why the error occurred. */
    public final String message;

    /**
     * Constructs a new ErrorPayload.
     *
     * @param code the category/code of the error
     * @param message the detailed descriptive message
     */
    public ErrorPayload(String code, String message) {
        this.code = code;
        this.message = message;
    }
}
