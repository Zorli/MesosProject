package it.polimi.ingsoft.mesos.shared.payload;

public class ErrorPayload {
    public final String code;
    public final String message;

    public ErrorPayload(String code, String message) {
        this.code = code;
        this.message = message;
    }
}
