package it.polimi.ingsoft.mesos.shared.payload.servertoclient.error;

import java.io.Serializable;
public class ErrorPayload implements Serializable, ErrorServerPayload {
    public final String code;
    public final String message;

    public ErrorPayload(String code, String message) {
        this.code = code;
        this.message = message;
    }
}
