package it.polimi.ingsoft.mesos.client.view.tui;

import it.polimi.ingsoft.mesos.client.virtualserver.ServerProxy;

import java.io.IOException;
import java.util.Objects;

/**
 * Routes parsed terminal commands to either local UI actions or the transport-agnostic server proxy.
 */
final class TuiCommandRouter {
    /**
     * Executes a command and reports how the controller should react.
     *
     * @param command parsed command
     * @param serverProxy transport-agnostic server proxy
     * @param localActions local UI actions for screens that do not hit the server
     * @return routing outcome
     * @throws IOException when closing the underlying connection fails
     */
    RouteResult route(TuiCommand command, ServerProxy serverProxy, LocalActions localActions) throws IOException {
        Objects.requireNonNull(command, "command cannot be null");
        Objects.requireNonNull(serverProxy, "serverProxy cannot be null");
        Objects.requireNonNull(localActions, "localActions cannot be null");

        return switch (command.getType()) {
            case NO_OP -> RouteResult.forPromptOnly();
            case HELP -> {
                localActions.showHelp();
                yield RouteResult.forContinueWithoutPrompt();
            }
            case STATUS -> {
                localActions.showStatus();
                yield RouteResult.forContinueWithoutPrompt();
            }
            case AUTHENTICATE -> {
                serverProxy.authenticate(command.getTextArgument());
                yield RouteResult.forFeedback("Authentication request sent.");
            }
            case LIST_LOBBIES -> {
                serverProxy.listLobbies();
                yield RouteResult.forFeedback("Lobby list request sent.");
            }
            case CREATE_LOBBY -> {
                serverProxy.createLobby(command.getIntegerArgument());
                yield RouteResult.forFeedback("Create-lobby request sent.");
            }
            case JOIN_LOBBY -> {
                serverProxy.joinLobby(command.getIntegerArgument());
                yield RouteResult.forFeedback("Join-lobby request sent.");
            }
            case EXIT_LOBBY -> {
                serverProxy.exitLobby();
                yield RouteResult.forFeedback("Quit-lobby request sent.");
            }
            case START_LOBBY -> {
                serverProxy.startLobby();
                yield RouteResult.forFeedback("Start-lobby request sent.");
            }
            case MOVE_TOTEM -> {
                serverProxy.moveTotem(command.getTextArgument());
                yield RouteResult.forFeedback("Move-totem request sent.");
            }
            case CHOOSE_CARD -> {
                serverProxy.chooseCard(command.getIntegerArgument());
                yield RouteResult.forFeedback("Choose-card request sent.");
            }
            case EXTRA_DRAW -> {
                serverProxy.extraDraw(command.getIntegerArgument());
                yield RouteResult.forFeedback("Extra-draw request sent.");
            }
            case QUIT -> {
                serverProxy.close();
                yield RouteResult.forExit("Client closed.");
            }
        };
    }

    /**
     * Local UI actions triggered by non-network commands.
     */
    interface LocalActions {
        /**
         * Renders the help screen.
         */
        void showHelp();

        /**
         * Re-renders the current status screen.
         */
        void showStatus();
    }

    /**
     * Describes what the controller should do after routing a command.
     */
    record RouteResult(boolean shouldExit, boolean promptOnly, String feedbackMessage) {
        /**
         * Creates a prompt-only result.
         *
         * @return prompt-only result
         */
        static RouteResult forPromptOnly() {
            return new RouteResult(false, true, null);
        }

        /**
         * Creates a result with a feedback message.
         *
         * @param feedbackMessage feedback to print
         * @return feedback result
         */
        static RouteResult forFeedback(String feedbackMessage) {
            return new RouteResult(false, false, feedbackMessage);
        }

        /**
         * Creates a result for locally rendered commands that already handled the prompt.
         *
         * @return no-op result
         */
        static RouteResult forContinueWithoutPrompt() {
            return new RouteResult(false, false, null);
        }

        /**
         * Creates a result that terminates the controller loop.
         *
         * @param feedbackMessage closing feedback
         * @return exit result
         */
        static RouteResult forExit(String feedbackMessage) {
            return new RouteResult(true, false, feedbackMessage);
        }
    }
}
