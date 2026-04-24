package it.polimi.ingsoft.mesos.client.view.tui;

import java.io.PrintStream;
import java.util.Objects;

/**
 * Provides synchronized terminal output primitives for redraws, prompts, and transient messages.
 */
final class TuiTerminal {
    private static final String CLEAR_SCREEN = "\u001B[H\u001B[2J";

    private final PrintStream out;
    private final boolean clearScreenEnabled;

    /**
     * Creates a terminal adapter.
     *
     * @param out terminal output stream
     * @param clearScreenEnabled whether full redraws should clear the screen first
     */
    TuiTerminal(PrintStream out, boolean clearScreenEnabled) {
        this.out = Objects.requireNonNull(out, "out cannot be null");
        this.clearScreenEnabled = clearScreenEnabled;
    }

    /**
     * Renders terminal content and then prints the prompt.
     *
     * @param content content to render
     * @param redraw whether the render is a full redraw
     * @param bannerLines optional banner lines printed above the content
     */
    synchronized void render(String content, boolean redraw, String... bannerLines) {
        if (redraw && this.clearScreenEnabled) {
            this.out.print(CLEAR_SCREEN);
            this.out.flush();
        } else {
            this.out.println();
        }
        for (String bannerLine : bannerLines) {
            if (bannerLine != null && !bannerLine.isBlank()) {
                this.out.println(bannerLine);
                if (redraw) {
                    this.out.println();
                }
            }
        }
        this.out.print(content);
        if (!content.endsWith(System.lineSeparator())) {
            this.out.println();
        }
        this.prompt();
    }

    /**
     * Prints a transient one-line message and then prints the prompt.
     *
     * @param message message to print
     */
    synchronized void printMessage(String message) {
        this.out.println();
        this.out.println(message);
        this.prompt();
    }

    /**
     * Prints the interactive prompt.
     */
    synchronized void prompt() {
        this.out.print("> ");
        this.out.flush();
    }
}
