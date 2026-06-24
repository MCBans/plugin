package com.mcbans.plugin.core.model;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

/**
 * A single ban-sync action pushed by the server ({@code {push:"banSync"}}) or returned by an
 * explicit {@code banSync} catch-up: ban or unban a player by uuid/name.
 */
public final class BanSyncAction {

    public enum Op { BAN, UNBAN }

    private final String uuid;
    private final String name;
    private final String id;
    private final Op op;

    public BanSyncAction(String uuid, String name, String id, Op op) {
        this.uuid = uuid;
        this.name = name;
        this.id = id;
        this.op = op;
    }

    public String uuid() {
        return uuid;
    }

    public String name() {
        return name;
    }

    /** The sync row id (the cursor advances past this once applied). */
    public String id() {
        return id;
    }

    public Op op() {
        return op;
    }

    public boolean isBan() {
        return op == Op.BAN;
    }

    public static BanSyncAction fromJson(JsonObject o) {
        Op op = "unban".equalsIgnoreCase(str(o, "do")) ? Op.UNBAN : Op.BAN;
        return new BanSyncAction(str(o, "uuid"), str(o, "name"), str(o, "id"), op);
    }

    private static String str(JsonObject o, String k) {
        JsonElement e = o.get(k);
        return e == null || e.isJsonNull() ? "" : e.getAsString();
    }

    @Override
    public String toString() {
        return "BanSyncAction{" + op + " " + name + "/" + uuid + " id=" + id + '}';
    }
}
