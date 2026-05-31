package it.polimi.ingsoft.mesos.client.view.tui;

/**
 * Visual constants for the text user interface.
 *
 * <p>This class centralises every ANSI escape sequence, every Unicode glyph and
 * every semantic-to-color mapping used by the TUI renderer. Keeping them here
 * avoids scattering raw escape codes through the rendering logic and makes
 * theme tweaks (e.g. toggling colors off) a one-file change.
 *
 * <p>The class is not meant to be instantiated.
 */
final class TuiStyle {

    private TuiStyle() {
    }

    // ─────────────────────────────────────────────────────────────────────────
    //  ANSI control sequences
    // ─────────────────────────────────────────────────────────────────────────

    static final String ESC = "[";
    static final String RESET = ESC + "0m";
    static final String BOLD = ESC + "1m";
    static final String DIM = ESC + "2m";
    static final String ITALIC = ESC + "3m";
    static final String UNDERLINE = ESC + "4m";

    /** Clear the entire screen and move the cursor to row 1 column 1. */
    static final String CLEAR_SCREEN = ESC + "2J" + ESC + "H";

    // ─────────────────────────────────────────────────────────────────────────
    //  ANSI foreground colors
    // ─────────────────────────────────────────────────────────────────────────

    static final String BLACK = ESC + "30m";
    static final String RED = ESC + "31m";
    static final String GREEN = ESC + "32m";
    static final String YELLOW = ESC + "33m";
    static final String BLUE = ESC + "34m";
    static final String MAGENTA = ESC + "35m";
    static final String CYAN = ESC + "36m";
    static final String WHITE = ESC + "37m";
    static final String GRAY = ESC + "90m";

    static final String BRIGHT_RED = ESC + "91m";
    static final String BRIGHT_GREEN = ESC + "92m";
    static final String BRIGHT_YELLOW = ESC + "93m";
    static final String BRIGHT_BLUE = ESC + "94m";
    static final String BRIGHT_MAGENTA = ESC + "95m";
    static final String BRIGHT_CYAN = ESC + "96m";
    static final String BRIGHT_WHITE = ESC + "97m";

    // ─────────────────────────────────────────────────────────────────────────
    //  Semantic mappings
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Returns the ANSI color associated with a totem color name.
     *
     * <p>Totem colors come from the preLobby as upper-case strings: RED, BLUE,
     * WHITE, YELLOW, BLACK. Unknown or null values fall back to gray.
     */
    static String forTotem(String totemColor) {
        if (totemColor == null) {
            return GRAY;
        }
        return switch (totemColor.toUpperCase()) {
            case "RED" -> BRIGHT_RED;
            case "BLUE" -> BRIGHT_BLUE;
            case "WHITE" -> BRIGHT_WHITE;
            case "YELLOW" -> BRIGHT_YELLOW;
            case "BLACK" -> GRAY;
            default -> GRAY;
        };
    }

    /**
     * Returns the ANSI color associated with a card type.
     */
    static String forCardType(String cardType) {
        if (cardType == null) {
            return WHITE;
        }
        return switch (cardType.toUpperCase()) {
            case "HUNTER" -> BRIGHT_RED;
            case "SHAMAN" -> BRIGHT_MAGENTA;
            case "ARTIST" -> BRIGHT_CYAN;
            case "BUILDER" -> BRIGHT_YELLOW;
            case "INVENTOR" -> BRIGHT_BLUE;
            case "GATHERER" -> BRIGHT_GREEN;
            case "EVENT" -> BRIGHT_WHITE;
            case "BUILDING" -> YELLOW;
            default -> WHITE;
        };
    }

    /**
     * Returns the ANSI color associated with a game/lobby status string.
     */
    static String forStatus(String status) {
        if (status == null) {
            return GRAY;
        }
        return switch (status.toUpperCase()) {
            case "IN_PROGRESS", "STARTED", "READY" -> BRIGHT_GREEN;
            case "SETUP", "WAITING" -> BRIGHT_YELLOW;
            case "FINISHED", "CLOSED" -> GRAY;
            default -> WHITE;
        };
    }

    /**
     * One-glyph icon for a card type. Returned with no color attached so the
     * caller can decide the surrounding style.
     */
    static String iconForCardType(String cardType) {
        if (cardType == null) {
            return "?";
        }
        // Single-byte ASCII letters: width is unambiguous on every terminal,
        // unlike Ⓗ/Ⓢ/... (Enclosed Alphanumerics) which are East Asian Ambiguous
        // and may render 2-wide, breaking column alignment.
        return switch (cardType.toUpperCase()) {
            case "HUNTER" -> "H";
            case "SHAMAN" -> "S";
            case "ARTIST" -> "A";
            case "BUILDER" -> "B";
            case "INVENTOR" -> "I";
            case "GATHERER" -> "G";
            case "EVENT" -> "E";
            case "BUILDING" -> "#";
            default -> "?";
        };
    }

    /**
     * Compact 3-letter abbreviation of a card type, suitable for tight cells.
     */
    static String shortTypeForCardType(String cardType) {
        if (cardType == null) {
            return "???";
        }
        return switch (cardType.toUpperCase()) {
            case "HUNTER" -> "HUN";
            case "SHAMAN" -> "SHA";
            case "ARTIST" -> "ART";
            case "BUILDER" -> "BLD";
            case "INVENTOR" -> "INV";
            case "GATHERER" -> "GAT";
            case "EVENT" -> "EVT";
            case "BUILDING" -> "BDG";
            default -> "???";
        };
    }

    /**
     * Wraps a string with the given color and a trailing reset.
     */
    static String paint(String color, String text) {
        return color + text + RESET;
    }

    /**
     * Wraps a string with bold and a trailing reset.
     */
    static String bold(String text) {
        return BOLD + text + RESET;
    }
}
