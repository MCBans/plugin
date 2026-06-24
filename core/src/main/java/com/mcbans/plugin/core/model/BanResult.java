package com.mcbans.plugin.core.model;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

/**
 * Result of a ban/unban write command ({@code globalBan}, {@code localBan}, {@code tempBan},
 * {@code ipBan}, {@code unBan}).
 *
 * <p>Wire {@code result} codes: {@code y} success · {@code e} error/bad input ·
 * {@code n} not found / no action · {@code a} already exists · {@code w} warning (reason blocked
 * as profanity).
 */
public final class BanResult {

    public enum Code {
        SUCCESS('y'),
        ERROR('e'),
        NOT_FOUND('n'),
        ALREADY('a'),
        PROFANITY('w'),
        UNKNOWN('?');

        private final char c;

        Code(char c) {
            this.c = c;
        }

        static Code from(String raw) {
            if (raw == null || raw.isEmpty()) {
                return UNKNOWN;
            }
            char ch = Character.toLowerCase(raw.charAt(0));
            for (Code code : values()) {
                if (code.c == ch) {
                    return code;
                }
            }
            return UNKNOWN;
        }
    }

    private final Code code;
    private final String message;
    private final String player;

    private BanResult(Code code, String message, String player) {
        this.code = code;
        this.message = message;
        this.player = player;
    }

    public Code code() {
        return code;
    }

    public boolean isSuccess() {
        return code == Code.SUCCESS;
    }

    public String message() {
        return message;
    }

    /** Resolved player name the server echoed back (may be empty on v2 / errors). */
    public String player() {
        return player;
    }

    public static BanResult fromJson(JsonObject o) {
        return new BanResult(Code.from(str(o, "result")), str(o, "msg"), str(o, "player"));
    }

    private static String str(JsonObject o, String k) {
        JsonElement e = o.get(k);
        return e == null || e.isJsonNull() ? "" : e.getAsString();
    }

    @Override
    public String toString() {
        return "BanResult{" + code + (message.isEmpty() ? "" : ", '" + message + '\'') + '}';
    }
}
