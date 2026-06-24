package com.mcbans.plugin.core.util;

import java.util.UUID;
import java.util.regex.Pattern;

/**
 * Shared identifier helpers used by every adapter: UUID normalisation/parsing (dashed or undashed)
 * and IPv4 validation. Kept in core so the logic is implemented and tested once rather than copied
 * into each platform module.
 */
public final class Identifiers {

    private static final Pattern UUID_RE = Pattern.compile(
            "(?i)[0-9a-f]{8}-?[0-9a-f]{4}-?[0-9a-f]{4}-?[0-9a-f]{4}-?[0-9a-f]{12}");
    private static final Pattern IPV4_RE = Pattern.compile(
            "(25[0-5]|2[0-4]\\d|1?\\d?\\d)(\\.(25[0-5]|2[0-4]\\d|1?\\d?\\d)){3}");

    private Identifiers() {
    }

    /** Whether the token is a UUID (with or without dashes). */
    public static boolean isUuid(String token) {
        return token != null && UUID_RE.matcher(token).matches();
    }

    /** Whether the token is a valid dotted-quad IPv4 address. */
    public static boolean isIpv4(String token) {
        return token != null && IPV4_RE.matcher(token).matches();
    }

    /** Dashless lowercase form of a UUID string, or {@code ""} for null/blank input. */
    public static String normalizeUuid(String uuid) {
        return uuid == null ? "" : uuid.replace("-", "").toLowerCase();
    }

    /**
     * Parse a dashed or undashed UUID string into a {@link UUID}.
     *
     * @throws IllegalArgumentException if the input is not a valid UUID
     */
    public static UUID parseUuid(String raw) {
        if (raw == null) {
            throw new IllegalArgumentException("uuid is null");
        }
        String s = raw.replace("-", "");
        if (s.length() != 32) {
            throw new IllegalArgumentException("not a uuid: " + raw);
        }
        return UUID.fromString(s.replaceFirst(
                "([0-9a-fA-F]{8})([0-9a-fA-F]{4})([0-9a-fA-F]{4})([0-9a-fA-F]{4})([0-9a-fA-F]{12})",
                "$1-$2-$3-$4-$5"));
    }
}
