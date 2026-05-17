package it.polimi.ingsoft.mesos.server.model.game.game;

/**
 * Executes public actions of {@link Game}.
 *
 * <p>Every public action of the game follows the same technical flow:
 * <ol>
 *     <li>remember the current game state version;</li>
 *     <li>run the action;</li>
 *     <li>if the action changed the game state, notify listeners with the new payload;</li>
 *     <li>if the action is invalid, notify listeners with an error payload.</li>
 * </ol>
 *
 * <p>This class exists so that {@link Game} can focus on exposing game actions,
 * while this executor focuses on the common notification and error-handling
 * pattern.
 *
 * <p>The executor directly depends on {@code Game}. This is intentional: the
 * code is easier to read than a callback-based solution, and the dependency is
 * still simple because the executor is part of the game model package.
 */
public class GameActionExecutor {
    private final Game game;

    /**
     * Creates an executor for one game instance.
     *
     * @param game game whose public actions will be executed
     */
    public GameActionExecutor(Game game) {
        this.game = game;
    }

    /**
     * Runs a game action and sends the correct notification.
     *
     * <p>If the action changes the game state version, listeners receive the
     * current game payload. If the action throws an
     * {@link IllegalArgumentException} or {@link IllegalStateException},
     * listeners receive an error payload instead.
     *
     * @param action action to execute
     */
    public void execute(Runnable action) {
        long previousVersion = this.game.getStateVersion();
        //TODO: Qui dovremmo applicare forse meglio il command patter. Esempio:Oggetto che wrappa runnable e sappiamo
        //che azione è, cosi quando arribviamo qua nel try con l'oggetto che ha il codice per run possiamo fare,
        // controllo sul numero di giocatori attivi, c'è ne sono almeno 2? Allora accettiamo qualsiasi azione, se c'è ne 1 solo
        // allora accettiamo solo comandi di connessione e disconnessione. Se in particolare arriva una disconnessione dall'unico
        //rimasto attivo allora chiudo il gioco mandando eccezzione e riporto tutti in pre lobby.
        try {
            action.run();
            this.notifyPayloadIfStateChanged(previousVersion);
        } catch (IllegalArgumentException | IllegalStateException exception) {
            this.game.notifyError(
                    exception.getClass().getSimpleName(),
                    exception.getMessage()
            );
        }
    }

    /**
     * Sends the updated game payload only when the action changed model state.
     *
     * <p>Every state-changing handler calls {@link Game#markStateChanged()}.
     * The executor compares the version before and after the action. If the
     * version is different, clients need the new game payload. If the version is
     * unchanged, there is nothing new to broadcast.
     *
     * @param previousVersion state version captured before the action ran
     */
    private void notifyPayloadIfStateChanged(long previousVersion) {
        long currentVersion = this.game.getStateVersion();

        if (currentVersion != previousVersion) {
            Object payload = this.game.buildPayload();
            this.game.notifyListeners(payload);
        }
    }
}
