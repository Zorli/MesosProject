package it.polimi.ingsoft.mesos.shared.payload.servertoclient;
import java.io.Serializable;
public class ErrorPayload implements Serializable {
    public final String code;
    public final String message;

    public ErrorPayload(String code, String message) {
        this.code = code;
        this.message = message;
    }
}
