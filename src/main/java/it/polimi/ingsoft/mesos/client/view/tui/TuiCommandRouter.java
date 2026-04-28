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
                System.out.println("Authentication request sent.");
                serverProxy.authenticate(command.getTextArgument());
                yield RouteResult.forContinueWithoutPrompt();
            }
            case LIST_LOBBIES -> {
                System.out.println("Lobby list request sent.");
                serverProxy.listLobbies();
                yield RouteResult.forContinueWithoutPrompt();
            }
            case CREATE_LOBBY -> {
                System.out.println("Create-lobby request sent.");
                serverProxy.createLobby(command.getIntegerArgument());
                yield RouteResult.forContinueWithoutPrompt();
            }
            case JOIN_LOBBY -> {
                System.out.println("Join-lobby request sent.");
                serverProxy.joinLobby(command.getIntegerArgument());
                yield RouteResult.forContinueWithoutPrompt();
            }
            case EXIT_LOBBY -> {
                System.out.println("Exit-lobby request sent.");
                serverProxy.exitLobby();
                yield RouteResult.forContinueWithoutPrompt();
            }
            case START_LOBBY -> {
                System.out.println("Start-lobby request sent.");
                serverProxy.startLobby();
                yield RouteResult.forContinueWithoutPrompt();
            }
            case MOVE_TOTEM -> {
                System.out.println("Move-totem request sent.");
                serverProxy.moveTotem(command.getTextArgument());
                yield RouteResult.forContinueWithoutPrompt();
            }
            case CHOOSE_CARD -> {
                System.out.println("Choose card request sent.");
                serverProxy.chooseCard(command.getIntegerArgument());
                yield RouteResult.forContinueWithoutPrompt();
            }
            case EXTRA_DRAW -> {
                System.out.println("Extra draw request sent.");
                serverProxy.extraDraw(command.getIntegerArgument());
                yield RouteResult.forContinueWithoutPrompt();
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
