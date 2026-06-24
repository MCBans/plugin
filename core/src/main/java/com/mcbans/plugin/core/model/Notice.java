package com.mcbans.plugin.core.model;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

/** A server-originated notice ({@code {push:"notice"}}) — e.g. "Your plugin is out of date." */
public final class Notice {

    private final long id;
    private final String message;

    public Notice(long id, String message) {
        this.id = id;
        this.message = message;
    }

    public long id() {
        return id;
    }

    public String message() {
        return message;
    }

    public static Notice fromJson(JsonObject o) {
        JsonElement idEl = o.get("id");
        long id = idEl == null || idEl.isJsonNull() ? 0L : idEl.getAsLong();
        JsonElement msgEl = o.get("message");
        return new Notice(id, msgEl == null || msgEl.isJsonNull() ? "" : msgEl.getAsString());
    }
}
