package it.polimi.ingsoft.mesos.server.model.persistence.mysql;

/**
 * Signals that MySQL cannot be used because it is unreachable or its schema is unhealthy.
 */
public final class MySqlDatabaseHealthException extends IllegalStateException {
    /**
     * High-level health category used to distinguish connectivity from schema problems.
     */
    public enum Reason {
        UNREACHABLE,
        UNHEALTHY
    }

    private final Reason reason;

    /**
     * Creates a database health exception with reason and message.
     *
     * @param reason failure category
     * @param message diagnostic message
     */
    public MySqlDatabaseHealthException(Reason reason, String message) {
        super(message);
        this.reason = reason;
    }

    /**
     * Creates a database health exception with reason, message and cause.
     *
     * @param reason failure category
     * @param message diagnostic message
     * @param cause root cause
     */
    public MySqlDatabaseHealthException(Reason reason, String message, Throwable cause) {
        super(message, cause);
        this.reason = reason;
    }

    /**
     * Returns the high-level failure category.
     *
     * @return failure reason
     */
    public Reason reason() {
        return this.reason;
    }
}
