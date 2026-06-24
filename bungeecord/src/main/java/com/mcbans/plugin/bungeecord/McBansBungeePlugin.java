package com.mcbans.plugin.bungeecord;

import com.mcbans.plugin.core.McBansConfig;
import com.mcbans.plugin.core.McBansCore;
import com.mcbans.plugin.core.PropertiesConfig;
import com.mcbans.plugin.core.platform.BanSyncHandler;
import com.mcbans.plugin.core.platform.FileCursorStore;
import net.md_5.bungee.api.chat.TextComponent;
import net.md_5.bungee.api.connection.PendingConnection;
import net.md_5.bungee.api.event.LoginEvent;
import net.md_5.bungee.api.plugin.Listener;
import net.md_5.bungee.api.plugin.Plugin;
import net.md_5.bungee.event.EventHandler;

import java.net.InetSocketAddress;
import java.util.UUID;

/**
 * BungeeCord proxy plugin — LOGIN / ban-check ONLY.
 *
 * <p>It opens the same MCBans {@code /plugin/ws} session as the server plugin but uses it solely to
 * run the on-join {@code login}/{@code loginNew} check and reject banned players at the proxy edge.
 * It registers a {@link BanSyncHandler#NOOP} (it does not apply pushed bans) and exposes no admin
 * commands — those belong to the server-side plugin.
 */
public final class McBansBungeePlugin extends Plugin implements Listener {

    private McBansCore core;

    @Override
    public void onEnable() {
        try {
            getDataFolder().mkdirs();
            McBansConfig config = PropertiesConfig.loadOrCreate(
                    getDataFolder().toPath().resolve("mcbans.properties"));

            if (config.apiKey().isBlank()) {
                getLogger().severe("No api-key set in mcbans.properties — MCBans is disabled.");
                return;
            }

            BungeeLogger log = new BungeeLogger(getLogger());
            FileCursorStore cursors = new FileCursorStore(getDataFolder().toPath().resolve("cursor.dat"), log);
            // Login-only: no ban-sync application, no notice surfacing beyond logs.
            this.core = new McBansCore(config, log, BanSyncHandler.NOOP, cursors);
            this.core.start();

            getProxy().getPluginManager().registerListener(this, this);
            getLogger().info("MCBans (login gate) enabled — connecting to " + config.endpoint() + ".");
        } catch (Exception e) {
            getLogger().severe("MCBans failed to start: " + e.getMessage());
        }
    }

    @Override
    public void onDisable() {
        if (core != null) {
            core.shutdown();
        }
    }

    /**
     * Async on-join ban check. We hold the login open via Bungee's intent mechanism, run the check
     * off-thread, and either cancel (kick) or complete the intent when the result arrives — never
     * blocking a proxy thread.
     */
    @EventHandler
    public void onLogin(LoginEvent event) {
        if (core == null) {
            return;
        }
        event.registerIntent(this);
        PendingConnection conn = event.getConnection();
        String name = conn.getName();
        UUID uuid = conn.getUniqueId();
        String ip = conn.getSocketAddress() instanceof InetSocketAddress addr
                ? addr.getAddress().getHostAddress() : "";

        core.checkLogin(uuid == null ? null : uuid.toString(), name, ip).whenComplete((result, err) -> {
            try {
                if (err != null) {
                    getLogger().warning("[MCBans] login check error for " + name + ": " + err.getMessage());
                    if (!core.config().failOpen()) {
                        deny(event, "Unable to verify ban status. Try again shortly.");
                    }
                    return;
                }
                if (core.shouldDeny(result)) {
                    String reason = result.reason().isBlank() ? "Banned via MCBans." : result.reason();
                    deny(event, core.config().kickMessage().replace("{reason}", reason));
                }
            } finally {
                event.completeIntent(this);
            }
        });
    }

    private static void deny(LoginEvent event, String message) {
        event.setCancelled(true);
        event.setCancelReason(TextComponent.fromLegacyText(message.replace('&', '§')));
    }
}
