package com.mcbans.plugin.core;

import com.google.gson.JsonObject;
import com.mcbans.plugin.core.platform.PluginLogger;
import com.mcbans.plugin.core.protocol.Json;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * A durable cache of known-banned players, used as a failsafe: if the MCBans API can't be reached
 * during the on-join check, the adapter consults this list to still reject players it has seen
 * banned before. Entries are recorded whenever a login check resolves to a ban and cleared on
 * unban. Persisted as a small JSON file (keyed by dashless lowercase UUID).
 */
public final class OfflineBanList {

    /** A cached ban: enough to render the kick/return message offline. */
    public record Entry(String reason, String admin, String banId, String type) {
    }

    private final Path file;
    private final PluginLogger log;
    private final Map<String, Entry> entries = new ConcurrentHashMap<>();

    public OfflineBanList(Path file, PluginLogger log) {
        this.file = file;
        this.log = log;
        load();
    }

    private static String key(String uuid) {
        return uuid == null ? "" : uuid.replace("-", "").toLowerCase();
    }

    public boolean isBanned(String uuid) {
        return entries.containsKey(key(uuid));
    }

    public Entry get(String uuid) {
        return entries.get(key(uuid));
    }

    public void put(String uuid, Entry entry) {
        if (uuid == null || uuid.isBlank()) {
            return;
        }
        entries.put(key(uuid), entry);
        save();
    }

    public void remove(String uuid) {
        if (entries.remove(key(uuid)) != null) {
            save();
        }
    }

    private synchronized void load() {
        try {
            if (!Files.exists(file)) {
                return;
            }
            JsonObject root = Json.parse(Files.readString(file, StandardCharsets.UTF_8));
            for (String k : root.keySet()) {
                JsonObject o = root.getAsJsonObject(k);
                entries.put(k, new Entry(
                        Json.str(o, "reason"), Json.str(o, "admin"),
                        Json.str(o, "banId"), Json.str(o, "type")));
            }
        } catch (IOException | RuntimeException e) {
            log.warn("[MCBans] Could not read offline ban list (" + e.getMessage() + ").");
        }
    }

    private synchronized void save() {
        try {
            Path parent = file.getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
            JsonObject root = new JsonObject();
            entries.forEach((k, e) -> {
                JsonObject o = new JsonObject();
                o.addProperty("reason", e.reason());
                o.addProperty("admin", e.admin());
                o.addProperty("banId", e.banId());
                o.addProperty("type", e.type());
                root.add(k, o);
            });
            Files.writeString(file, Json.write(root), StandardCharsets.UTF_8);
        } catch (IOException e) {
            log.error("[MCBans] Failed to persist offline ban list", e);
        }
    }
}
