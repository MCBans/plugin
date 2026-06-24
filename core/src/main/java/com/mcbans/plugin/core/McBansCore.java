package com.mcbans.plugin.core;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.mcbans.plugin.core.i18n.Messages;
import com.mcbans.plugin.core.model.BanResult;
import com.mcbans.plugin.core.model.BanStatus;
import com.mcbans.plugin.core.model.LoginResult;
import com.mcbans.plugin.core.model.VerifyResult;
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
    private final Messages messages;
    private final OfflineBanList offlineBanList;

    public McBansCore(McBansConfig config, PluginLogger log, BanSyncHandler handler, CursorStore cursors) {
        this(config, log, handler, cursors, null);
    }

    public McBansCore(McBansConfig config, PluginLogger log, BanSyncHandler handler, CursorStore cursors,
                      OfflineBanList offlineBanList) {
        this.config = config;
        this.log = log;
        this.client = new McBansSocketClient(config, log, handler, cursors);
        this.messages = new Messages(config.language());
        this.offlineBanList = offlineBanList;
    }

    /** Localised messages (language pack chosen by {@link McBansConfig#language()}). */
    public Messages messages() {
        return messages;
    }

    /** The failsafe offline ban cache (may be {@code null} if the adapter didn't supply one). */
    public OfflineBanList offlineBanList() {
        return offlineBanList;
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
        final String normalizedUuid = uuid == null ? null : uuid.replace("-", "");
        return client.sendCommand(cmd, Json.obj(data))
                .orTimeout(config.loginTimeoutMs(), java.util.concurrent.TimeUnit.MILLISECONDS)
                .handle((reply, err) -> {
                    if (err != null) {
                        log.warn("[MCBans] login check for " + name + " failed: " + err.getMessage()
                                + " (failing " + (config.failOpen() ? "open" : "closed") + ").");
                        return failPolicyResult();
                    }
                    LoginResult result = parseLogin(reply);
                    cacheOffline(normalizedUuid, result);
                    return result;
                });
    }

    /** Keep the failsafe cache in step with the live result: record hard bans, clear when clean. */
    private void cacheOffline(String uuid, LoginResult result) {
        if (offlineBanList == null || uuid == null || uuid.isBlank()) {
            return;
        }
        if (result.status().isHardBan()) {
            offlineBanList.put(uuid, new OfflineBanList.Entry(
                    result.reason(), banAdmin(result), result.banId(), typeLabel(result.status())));
        } else if (result.status() == BanStatus.CLEAN) {
            offlineBanList.remove(uuid);
        }
    }

    private static String banAdmin(LoginResult result) {
        return result.bans().isEmpty() ? "" : result.bans().get(0).admin();
    }

    private static String typeLabel(BanStatus status) {
        return switch (status) {
            case GLOBAL -> "GLOBAL";
            case LOCAL -> "LOCAL";
            case TEMP -> "TEMPORARY";
            case IP_LOCK -> "IP";
            default -> "";
        };
    }

    /**
     * Evaluate the full login policy and return the localized kick message if the player must be
     * denied, or {@code null} to allow. Covers: active ban, the soft {@code b} status (per config),
     * the minimum-reputation gate and the max-alts gate.
     */
    public String loginKickMessage(LoginResult result) {
        if (result.status().isHardBan() || (config.denyOnBannedStatus() && result.status() == BanStatus.BANNED)) {
            return messages.localize("banReturnMessage",
                    Messages.ADMIN, banAdmin(result),
                    Messages.REASON, result.reason().isBlank() ? "Banned via MCBans." : result.reason(),
                    Messages.TYPE, typeLabel(result.status()),
                    Messages.BANID, result.banId());
        }
        if (config.minReputation() >= 0 && result.reputation() < config.minReputation()) {
            return messages.localize("underMinRep");
        }
        if (config.enableMaxAlts() && result.altCount() > config.maxAlts()) {
            return messages.localize("overMaxAlts");
        }
        return null;
    }

    /** Render the offline-cache kick message for a player when the API was unreachable. */
    public String offlineKickMessage(OfflineBanList.Entry entry) {
        return messages.localize("banReturnMessage",
                Messages.ADMIN, entry.admin(),
                Messages.REASON, entry.reason().isBlank() ? "Banned via MCBans." : entry.reason(),
                Messages.TYPE, entry.type(),
                Messages.BANID, entry.banId());
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

    /** Ban detail by numeric ban id. */
    public CompletableFuture<JsonObject> banLookup(String banId) {
        return client.sendCommand("banLookup", Json.obj(Map.of("ban", nz(banId)))).thenApply(this::dataObject);
    }

    /** Alt-account list for a player (premium servers only). */
    public CompletableFuture<JsonObject> altList(String name, String uuid) {
        return client.sendCommand("altList", Json.obj(Map.of("player", nz(name), "player_uuid", nz(uuid))))
                .thenApply(this::dataObject);
    }

    /** Previous-names / UUID resolution lookup. */
    public CompletableFuture<JsonObject> uuidLookup(String name, String uuid) {
        return client.sendCommand("uuidLookup", Json.obj(Map.of("player", nz(name), "player_uuid", nz(uuid))))
                .thenApply(this::dataObject);
    }

    /** Health/heartbeat ping. */
    public CompletableFuture<JsonObject> ping() {
        return client.sendCommand("ping", new JsonObject()).thenApply(this::dataObject);
    }

    /** Change a server setting (staff key). {@code expr} is the space-delimited setting expression. */
    public CompletableFuture<JsonObject> setting(String expr) {
        return client.sendCommand("setting", Json.obj(Map.of("setting", nz(expr)))).thenApply(this::dataObject);
    }

    /** DNSBL/proxy check for an IP. */
    public CompletableFuture<JsonObject> dnsblCheck(String ip) {
        return client.sendCommand("dnsblCheck", Json.obj(Map.of("playerip", nz(ip)))).thenApply(this::dataObject);
    }

    /** Whether this server is premium. */
    public CompletableFuture<JsonObject> serverPremium() {
        return client.sendCommand("serverPremium", new JsonObject()).thenApply(this::dataObject);
    }

    /**
     * Link an in-game account to an MCBans account using a one-time code the player generated on
     * the website. Sends {@code verify_user} (v3) / {@code verifyUser} (v2) with the player name and
     * auth code. This is a <em>protected</em> command: the server's API key/address must be in the
     * API's verify-user allowlist, otherwise the result is {@link VerifyResult.State#BLOCKED}.
     */
    public CompletableFuture<VerifyResult> verifyUser(String player, String code) {
        String cmd = config.version() >= 3 ? "verify_user" : "verifyUser";
        // Send under several field names so both the v3 (segment) and v2 (param) handlers resolve it.
        return client.sendCommand(cmd, Json.obj(Map.of(
                        "player", nz(player), "name", nz(player), "auth", nz(code), "authcode", nz(code)))
                ).thenApply(VerifyResult::fromReply);
    }

    /** Notify MCBans that a player left this server's online list. */
    public CompletableFuture<JsonObject> playerDisconnect(String name) {
        return client.sendCommand("playerDisconnect", Json.obj(Map.of("player", nz(name))))
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
