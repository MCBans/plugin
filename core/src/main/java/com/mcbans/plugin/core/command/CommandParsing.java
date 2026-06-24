package com.mcbans.plugin.core.command;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Shared, platform-neutral command-argument parsing (ported from the legacy {@code CommandBan} /
 * {@code CommandTempban}). Adapters tokenise their platform's args, hand the tail here, and get back
 * a {@link BanIntent} they can execute via {@code McBansCore}.
 */
public final class CommandParsing {

    // "15m", "15 minutes", "1week", "30s" ... -> (number, unit)
    private static final Pattern DURATION = Pattern.compile(
            "([0-9]+)\\s*(minutes?|m|seconds?|s|hours?|h|days?|d|weeks?|w)",
            Pattern.CASE_INSENSITIVE | Pattern.DOTALL);

    private CommandParsing() {
    }

    /** A parsed ban request: kind, optional temp duration/measure, and the joined reason. */
    public static final class BanIntent {
        public final BanKind kind;
        public final String duration; // numeric multiplier (temp only)
        public final String measure;  // unit: m|h|d|w (temp only)
        public final String reason;

        public BanIntent(BanKind kind, String duration, String measure, String reason) {
            this.kind = kind;
            this.duration = duration;
            this.measure = measure;
            this.reason = reason;
        }
    }

    /**
     * Parse the arguments of a generic {@code /ban} command (the tokens AFTER the target).
     * Supports the legacy modifiers: a leading {@code g}/{@code -g} → global, {@code t}/{@code -t}
     * {@code <num> <unit>} → temp; otherwise local.
     *
     * @param tail          args after the target name
     * @param defaultReason reason to use when none is supplied
     */
    public static BanIntent parseBan(List<String> tail, String defaultReason) {
        List<String> a = new ArrayList<>(tail);
        BanKind kind = BanKind.LOCAL;
        String duration = "0";
        String measure = "";

        if (!a.isEmpty()) {
            String first = a.get(0).toLowerCase();
            if (first.equals("g") || first.equals("-g") || first.equals("global")) {
                kind = BanKind.GLOBAL;
                a.remove(0);
            } else if (first.equals("t") || first.equals("-t") || first.equals("temp")) {
                kind = BanKind.TEMP;
                a.remove(0);
                if (!a.isEmpty()) {
                    String[] dm = parseDuration(a.get(0));
                    if (dm != null) {
                        duration = dm[0];
                        measure = dm[1];
                        a.remove(0);
                        // optional separate unit token, e.g. "15 m"
                        if (measure.isEmpty() && !a.isEmpty() && parseUnit(a.get(0)) != null) {
                            measure = parseUnit(a.remove(0));
                        }
                    }
                }
            }
        }

        String reason = a.isEmpty() ? defaultReason : String.join(" ", a);
        return new BanIntent(kind, duration, measure, reason);
    }

    /**
     * Parse a duration token like {@code "15m"} or {@code "15"} into {@code [number, unit]}; the
     * unit is empty if the token was a bare number. Returns {@code null} if it isn't a duration.
     */
    public static String[] parseDuration(String token) {
        if (token == null) {
            return null;
        }
        Matcher m = DURATION.matcher(token);
        if (m.matches()) {
            return new String[] {m.group(1), normalizeUnit(m.group(2))};
        }
        if (token.matches("[0-9]+")) {
            return new String[] {token, ""};
        }
        return null;
    }

    private static String parseUnit(String token) {
        if (token == null) {
            return null;
        }
        String t = token.toLowerCase();
        if (t.matches("minutes?|m|seconds?|s|hours?|h|days?|d|weeks?|w")) {
            return normalizeUnit(t);
        }
        return null;
    }

    private static String normalizeUnit(String u) {
        char c = Character.toLowerCase(u.charAt(0));
        return switch (c) {
            case 's' -> "s";
            case 'h' -> "h";
            case 'd' -> "d";
            case 'w' -> "w";
            default -> "m";
        };
    }
}
