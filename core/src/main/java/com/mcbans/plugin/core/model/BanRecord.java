package com.mcbans.plugin.core.model;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

/**
 * One entry from a player's ban history (the {@code bans[]} array in a v3 {@code loginNew} /
 * {@code playerLookup} reply). Used for the "previous bans" notification shown to staff on join.
 */
public final class BanRecord {

    private final String admin;
    private final String reason;
    private final String server;
    private final String type;

    public BanRecord(String admin, String reason, String server, String type) {
        this.admin = admin;
        this.reason = reason;
        this.server = server;
        this.type = type;
    }

    public String admin() {
        return admin;
    }

    public String reason() {
        return reason;
    }

    public String server() {
        return server;
    }

    public String type() {
        return type;
    }

    /**
     * Parse a ban entry. The server nests {@code admin}/{@code server} as either flat strings or
     * sub-objects ({@code admin.name}, {@code server.address}); both shapes are accepted.
     */
    public static BanRecord fromJson(JsonObject o) {
        return new BanRecord(
                nested(o, "admin", "name"),
                str(o, "reason"),
                nested(o, "server", "address"),
                str(o, "type"));
    }

    private static String nested(JsonObject o, String key, String childField) {
        JsonElement e = o.get(key);
        if (e == null || e.isJsonNull()) {
            return "";
        }
        if (e.isJsonObject()) {
            JsonElement c = e.getAsJsonObject().get(childField);
            return c == null || c.isJsonNull() ? "" : c.getAsString();
        }
        return e.getAsString();
    }

    private static String str(JsonObject o, String k) {
        JsonElement e = o.get(k);
        return e == null || e.isJsonNull() ? "" : e.getAsString();
    }
}
