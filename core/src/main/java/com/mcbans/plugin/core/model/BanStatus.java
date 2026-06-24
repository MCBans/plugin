package com.mcbans.plugin.core.model;

/**
 * Outcome of an on-join ban check ({@code login} / {@code loginNew}).
 *
 * <p>The wire codes come straight from the MCBans plugin API:
 * {@code g} global · {@code l} local · {@code t} temp · {@code b} banned / no account ·
 * {@code n} clean (has account) · {@code i} MCBans security block (IP lock).
 */
public enum BanStatus {
    GLOBAL('g'),
    LOCAL('l'),
    TEMP('t'),
    /** Player is banned somewhere / has no MCBans account — gate per policy. */
    BANNED('b'),
    /** Clean: no active ban. */
    CLEAN('n'),
    /** MCBans security block (IP lock). */
    IP_LOCK('i'),
    /** Unrecognised / missing status code. */
    UNKNOWN('?');

    private final char code;

    BanStatus(char code) {
        this.code = code;
    }

    public char code() {
        return code;
    }

    /** Maps a wire status code (e.g. {@code "g"}) to its {@link BanStatus}. */
    public static BanStatus fromCode(String raw) {
        if (raw == null || raw.isEmpty()) {
            return UNKNOWN;
        }
        char c = Character.toLowerCase(raw.charAt(0));
        for (BanStatus s : values()) {
            if (s.code == c) {
                return s;
            }
        }
        return UNKNOWN;
    }

    /**
     * Whether a player with this status should be denied login under the strict default policy.
     * Callers may widen/narrow this (e.g. allow {@link #BANNED}) via config.
     */
    public boolean isHardBan() {
        return this == GLOBAL || this == LOCAL || this == TEMP || this == IP_LOCK;
    }
}
