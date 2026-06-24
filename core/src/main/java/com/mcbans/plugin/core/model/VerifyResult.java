package com.mcbans.plugin.core.model;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.mcbans.plugin.core.protocol.Json;

/**
 * Result of a {@code verify_user} / {@code verifyUser} account-link call. Normalises the two wire
 * shapes: v3 returns a {@code ;}-delimited string ({@code y;<name>;Registration Complete...} /
 * {@code a;...already...} / {@code n;Invalid code!}); v2 returns JSON ({@code {state, name}}). A
 * transport-level {@code ok:false} (e.g. the protected-function gate) maps to {@link State#BLOCKED}.
 */
public final class VerifyResult {

    public enum State { SUCCESS, ALREADY, INVALID, BLOCKED }

    private final State state;
    private final String name;
    private final String message;

    private VerifyResult(State state, String name, String message) {
        this.state = state;
        this.name = name;
        this.message = message;
    }

    public State state() {
        return state;
    }

    /** The linked MCBans account name (only meaningful for {@link State#SUCCESS}). */
    public String name() {
        return name;
    }

    /** Any server-supplied message (e.g. the block reason); may be empty. */
    public String message() {
        return message;
    }

    /** Parse from a WebSocket reply frame ({@code {ok, data}}). */
    public static VerifyResult fromReply(JsonObject reply) {
        if (!Json.bool(reply, "ok")) {
            String err = Json.str(reply, "error");
            JsonElement data = reply.get("data");
            if (err.isEmpty() && data != null && data.isJsonObject()) {
                err = Json.str(data.getAsJsonObject(), "error");
            }
            return new VerifyResult(State.BLOCKED, "", err);
        }
        JsonElement data = reply.get("data");
        if (data == null || data.isJsonNull()) {
            return new VerifyResult(State.INVALID, "", "");
        }
        if (data.isJsonObject()) {
            JsonObject o = data.getAsJsonObject();
            return new VerifyResult(toState(Json.str(o, "state")), Json.str(o, "name"), "");
        }
        // v3 `;`-delimited string: state;name;message  (or  state;message)
        String[] p = data.getAsString().split(";", -1);
        State st = toState(p.length > 0 ? p[0] : "");
        if (st == State.SUCCESS) {
            return new VerifyResult(st, p.length > 1 ? p[1] : "", p.length > 2 ? p[2] : "");
        }
        return new VerifyResult(st, "", p.length > 1 ? p[1] : "");
    }

    private static State toState(String code) {
        if (code == null || code.isEmpty()) {
            return State.INVALID;
        }
        return switch (Character.toLowerCase(code.charAt(0))) {
            case 'y' -> State.SUCCESS;
            case 'a' -> State.ALREADY;
            default -> State.INVALID;
        };
    }
}
