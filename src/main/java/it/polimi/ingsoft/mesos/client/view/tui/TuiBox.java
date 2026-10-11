package it.polimi.ingsoft.mesos.client.view.tui;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.regex.Pattern;

/**
 * Box-drawing helpers for the TUI renderer.
 *
 * <p>This class produces strings with Unicode box-drawing characters. All width
 * calculations strip ANSI escape sequences before counting characters, so
 * colored content lines up correctly inside frames.
 *
 * <p>The class is not meant to be instantiated; every helper is static.
 */
final class TuiBox {

    private static final Pattern ANSI = Pattern.compile("\\[[;\\d]*m");

    // ─── Box-drawing glyphs ──────────────────────────────────────────────────

    static final String OUTER_TL = "╔";
    static final String OUTER_TR = "╗";
    static final String OUTER_BL = "╚";
    static final String OUTER_BR = "╝";
    static final String OUTER_H = "═";
    static final String OUTER_V = "║";
    static final String OUTER_LT = "╠";
    static final String OUTER_RT = "╣";

    static final String INNER_TL = "┌";
    static final String INNER_TR = "┐";
    static final String INNER_BL = "└";
    static final String INNER_BR = "┘";
    static final String INNER_H = "─";
    static final String INNER_V = "│";

    static final String CARD_TL = "╭";
    static final String CARD_TR = "╮";
    static final String CARD_BL = "╰";
    static final String CARD_BR = "╯";

    private TuiBox() {
    }

    /**
     * Returns the visible width of a string, ignoring ANSI escape sequences.
     */
    static int visibleLength(String text) {
        if (text == null || text.isEmpty()) {
            return 0;
        }
        return ANSI.matcher(text).replaceAll("").length();
    }

    /**
     * Right-pads a string with spaces so its visible width matches the target.
     *
     * <p>If the string is already wider than the target, it is returned
     * unchanged. ANSI escape sequences do not consume width.
     */
    static String padRight(String text, int targetWidth) {
        int padding = targetWidth - visibleLength(text);
        if (padding <= 0) {
            return text;
        }
        return text + " ".repeat(padding);
    }

    /**
     * Centers a string inside a field of the given width.
     */
    static String center(String text, int width) {
        int slack = width - visibleLength(text);
        if (slack <= 0) {
            return text;
        }
        int left = slack / 2;
        int right = slack - left;
        return " ".repeat(left) + text + " ".repeat(right);
    }

    /**
     * Wraps a list of content lines in a double-line outer frame.
     *
     * <p>An optional title is rendered at the top, embedded in the horizontal
     * border. All lines are padded to the same visible width.
     */
    static String frame(String title, List<String> lines) {
        return frame(title, lines, 0);
    }

    /**
     * Same as {@link #frame(String, List)} but enforces a minimum inner width.
     *
     * <p>Useful to align several stacked frames to the same visual width: pass
     * the natural width of the widest frame as {@code minInnerWidth} to the
     * narrower ones so all bottom borders line up.
     */
    static String frame(String title, List<String> lines, int minInnerWidth) {
        int innerWidth = Math.max(0, minInnerWidth);
        if (title != null) {
            innerWidth = Math.max(innerWidth, visibleLength(title) + 4);
        }
        for (String line : lines) {
            innerWidth = Math.max(innerWidth, visibleLength(line));
        }

        StringBuilder out = new StringBuilder();
        out.append(OUTER_TL);
        if (title != null && !title.isEmpty()) {
            // Bottom border has `innerWidth + 2` horizontal glyphs between corners.
            // Top border must match: 1 prefix `═` + banner + suffix `═...═` == innerWidth + 2.
            String banner = " " + title + " ";
            out.append(OUTER_H);
            out.append(banner);
            int suffix = innerWidth + 1 - visibleLength(banner);
            if (suffix < 1) {
                suffix = 1;
            }
            out.append(OUTER_H.repeat(suffix));
        } else {
            out.append(OUTER_H.repeat(innerWidth + 2));
        }
        out.append(OUTER_TR).append('\n');

        for (String line : lines) {
            out.append(OUTER_V).append(' ');
            out.append(padRight(line, innerWidth));
            out.append(' ').append(OUTER_V).append('\n');
        }

        out.append(OUTER_BL);
        out.append(OUTER_H.repeat(innerWidth + 2));
        out.append(OUTER_BR);
        return out.toString();
    }

    /**
     * Wraps content lines in a single-line inner frame (no title).
     */
    static String panel(List<String> lines) {
        int innerWidth = 0;
        for (String line : lines) {
            innerWidth = Math.max(innerWidth, visibleLength(line));
        }

        StringBuilder out = new StringBuilder();
        out.append(INNER_TL).append(INNER_H.repeat(innerWidth + 2)).append(INNER_TR).append('\n');
        for (String line : lines) {
            out.append(INNER_V).append(' ')
                    .append(padRight(line, innerWidth))
                    .append(' ').append(INNER_V).append('\n');
        }
        out.append(INNER_BL).append(INNER_H.repeat(innerWidth + 2)).append(INNER_BR);
        return out.toString();
    }

    /**
     * Joins a list of multi-line blocks horizontally.
     *
     * <p>Each block is split into lines, padded to the block's visible width,
     * and concatenated row by row with the given separator. Blocks with fewer
     * rows are padded with blank lines.
     *
     * @param blocks list of multi-line strings to place side by side
     * @param separator separator inserted between blocks on every row
     * @return joined multi-line string
     */
    static String joinSideBySide(List<String> blocks, String separator) {
        if (blocks == null || blocks.isEmpty()) {
            return "";
        }

        List<String[]> blockLines = new ArrayList<>();
        List<Integer> blockWidths = new ArrayList<>();
        int maxRows = 0;

        for (String block : blocks) {
            String[] lines = block.split("\n", -1);
            int width = 0;
            for (String line : lines) {
                width = Math.max(width, visibleLength(line));
            }
            blockLines.add(lines);
            blockWidths.add(width);
            maxRows = Math.max(maxRows, lines.length);
        }

        StringBuilder out = new StringBuilder();
        for (int row = 0; row < maxRows; row++) {
            if (row > 0) {
                out.append('\n');
            }
            for (int i = 0; i < blockLines.size(); i++) {
                if (i > 0) {
                    out.append(separator);
                }
                String[] lines = blockLines.get(i);
                int width = blockWidths.get(i);
                String line = row < lines.length ? lines[row] : "";
                out.append(padRight(line, width));
            }
        }
        return out.toString();
    }

    /**
     * Convenience overload joining blocks given as varargs.
     */
    static String joinSideBySide(String... blocks) {
        return joinSideBySide(Arrays.asList(blocks), " ");
    }
}
