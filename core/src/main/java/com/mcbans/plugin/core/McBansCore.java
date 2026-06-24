package com.mcbans.plugin.core;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.mcbans.plugin.core.model.BanResult;
import com.mcbans.plugin.core.model.LoginResult;
import com.mcbans.plugin.core.net.McBansSocketClient;
import com.mcbans.plugin.core.platform.BanSyncHandler;
import com.mcbans.plugin.core.platform.CursorStore;
import com.mcbans.plugin.core.platform.PluginLogger;
import com.mcbans.plugin.core.protocol.Json;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * The generic plugin engine each platform adapter drives. It owns the {@link McBansSocketClient}
 * and exposes typed, platform-neutral operations (login check, ban writes, lookups). Adapters
 * supply the platform glue ({@link PluginLogger}, {@link CursorStore}, {@link BanSyncHandler}) and
 * the config; everything protocol-related lives here so there is exactly one implementation of the
 * MCBans wire behaviour shared by Bukkit, Spigot, Sponge, Forge and BungeeCord.
 */
public final class McBansCore {

    private final McBansConfig config;
    private final PluginLogger log;
    private final McBansSocketClient client;

    public McBansCore(McBansConfig config, PluginLogger log, BanSyncHandler handler, CursorStore cursors) {
        this.config = config;
        this.log = log;
        this.client = new McBansSocketClient(config, log, handler, cursors);
    }

    public void start() {
        client.start();
    }

    public void shutdown() {
        client.stop();
    }

    public boolean isReady() {
        return client.isRegistered();
    }

    public McBansConfig config() {
        return config;
    }

    // ---- on-join ban check --------------------------------------------------------------------

    /**
     * The most important call: check whether a joining player may connect. Uses {@code loginNew}
     * on v3 (JSON reply) and {@code login} on v2 (legacy {@code ;}-tuple), normalising both into a
     * {@link LoginResult}. On timeout/error it resolves to a fail-open or fail-closed result per
     * {@link McBansConfig#failOpen()} so the caller always gets a decision.
     */
    public CompletableFuture<LoginResult> checkLogin(String uuid, String name, String ip) {
        Map<String, Object> data = new HashMap<>();
        if (uuid != null && !uuid.isBlank()) {
            data.put("uuid", uuid.replace("-", ""));
        }
        if (name != null && !name.isBlank()) {
            data.put("name", name);
        }
        data.put("ip", ip);
        data.put("clientVersion", "4.1");

        String cmd = config.version() >= 3 ? "loginNew" : "login";
        return client.sendCommand(cmd, Json.obj(data))
                .orTimeout(config.loginTimeoutMs(), java.util.concurrent.TimeUnit.MILLISECONDS)
                .handle((reply, err) -> {
                    if (err != null) {
                        log.warn("[MCBans] login check for " + name + " failed: " + err.getMessage()
                                + " (failing " + (config.failOpen() ? "open" : "closed") + ").");
                        return failPolicyResult();
                    }
                    return parseLogin(reply);
                });
    }

    private LoginResult parseLogin(JsonObject reply) {
        JsonElement data = reply.get("data");
        if (data == null || data.isJsonNull()) {
            return LoginResult.fromLegacy("");
        }
        if (data.isJsonObject()) {
            return LoginResult.fromJson(data.getAsJsonObject());
        }
        // v2 / 4.1 path: a ;-delimited string.
        return LoginResult.fromLegacy(data.getAsString());
    }

    /** Synthetic result used when a login check could not complete. */
    private LoginResult failPolicyResult() {
        // CLEAN allows the player in (fail open); BANNED denies (fail closed).
        return config.failOpen()
                ? LoginResult.fromLegacy("n")
                : LoginResult.fromLegacy("b");
    }

    /** Apply the configured policy to a {@link LoginResult}: should this player be kicked? */
    public boolean shouldDeny(LoginResult result) {
        if (result.status().isHardBan()) {
            return true;
        }
        return config.denyOnBannedStatus() && result.status() == com.mcbans.plugin.core.model.BanStatus.BANNED;
    }

    // ---- ban writes ---------------------------------------------------------------------------

    public CompletableFuture<BanResult> globalBan(String name, String uuid, String admin, String reason,
                                                  long durationSeconds, String playerIp) {
        return banWrite("globalBan", Map.of(
                "player", nz(name),
                "player_uuid", nz(uuid),
                "admin", nz(admin),
                "reason", nz(reason),
                "duration", String.valueOf(durationSeconds),
                "player_ip", nz(playerIp)));
    }

    public CompletableFuture<BanResult> localBan(String name, String uuid, String admin, String reason,
                                                 String playerIp) {
        return banWrite("localBan", Map.of(
                "player", nz(name),
                "player_uuid", nz(uuid),
                "admin", nz(admin),
                "reason", nz(reason),
                "player_ip", nz(playerIp)));
    }

    public CompletableFuture<BanResult> tempBan(String name, String uuid, String admin, String reason,
                                                String measure, long duration, String playerIp) {
        return banWrite("tempBan", Map.of(
                "player", nz(name),
                "player_uuid", nz(uuid),
                "admin", nz(admin),
                "reason", nz(reason),
                "measure", nz(measure),
                "duration", String.valueOf(duration),
                "playerip", nz(playerIp)));
    }

    public CompletableFuture<BanResult> ipBan(String ip, String admin, String reason) {
        return banWrite("ipBan", Map.of("ip", nz(ip), "admin", nz(admin), "reason", nz(reason)));
    }

    /** Unban a player (by name/uuid) or an IPv4 — the server auto-detects which. */
    public CompletableFuture<BanResult> unBan(String playerOrIp, String uuid) {
        return banWrite("unBan", Map.of("player", nz(playerOrIp), "player_uuid", nz(uuid)));
    }

    private CompletableFuture<BanResult> banWrite(String cmd, Map<String, Object> data) {
        return client.sendCommand(cmd, Json.obj(data))
                .thenApply(reply -> {
                    JsonElement body = reply.get("data");
                    if (body != null && body.isJsonObject()) {
                        return BanResult.fromJson(body.getAsJsonObject());
                    }
                    return BanResult.fromJson(new JsonObject());
                });
    }

    // ---- lookups ------------------------------------------------------------------------------

    /** Raw player ban-history lookup; returns the server's JSON {@code data} body. */
    public CompletableFuture<JsonObject> playerLookup(String name, String uuid, String admin) {
        JsonObject data = Json.obj(Map.of(
                "player", nz(name), "player_uuid", nz(uuid), "admin", nz(admin)));
        return client.sendCommand("playerLookup", data).thenApply(this::dataObject);
    }

    /** Whether a player is MCBans staff (and their cape URL when so). */
    public CompletableFuture<JsonObject> isMcBansStaff(String name) {
        return client.sendCommand("isMCBansStaff", Json.obj(Map.of("player", nz(name))))
                .thenApply(this::dataObject);
    }

    /** Low-level escape hatch: send any command/data and get the raw reply frame. */
    public CompletableFuture<JsonObject> send(String cmd, Map<String, Object> data) {
        return client.sendCommand(cmd, Json.obj(data));
    }

    private JsonObject dataObject(JsonObject reply) {
        JsonElement body = reply.get("data");
        return body != null && body.isJsonObject() ? body.getAsJsonObject() : new JsonObject();
    }

    private static String nz(String s) {
        return s == null ? "" : s;
    }
}
