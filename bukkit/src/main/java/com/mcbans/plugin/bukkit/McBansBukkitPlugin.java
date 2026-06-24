package com.mcbans.plugin.bukkit;

import com.mcbans.plugin.core.McBansConfig;
import com.mcbans.plugin.core.McBansCore;
import com.mcbans.plugin.core.model.LoginResult;
import com.mcbans.plugin.core.platform.FileCursorStore;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerPreLoginEvent;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.concurrent.TimeUnit;

/**
 * Bukkit/Spigot/Paper entry point. Wires the generic {@link McBansCore} to the server: builds config
 * from {@code config.yml}, opens the WebSocket session, gates joins via the async pre-login event,
 * and registers the admin command set.
 */
public class McBansBukkitPlugin extends JavaPlugin implements Listener {

    private McBansCore core;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        FileConfiguration c = getConfig();

        if (c.getString("api-key", "").isBlank()) {
            getLogger().severe("No api-key set in config.yml — MCBans is disabled. "
                    + "Add your server API key (servers.server_api) and restart.");
            return;
        }

        McBansConfig config = McBansConfig.builder()
                .host(c.getString("host", "www.mcbans.com"))
                .apiKey(c.getString("api-key", ""))
                .version(c.getInt("protocol-version", 3))
                .tls(c.getBoolean("tls", true))
                .kickMessage(c.getString("kick-message", "&cYou are banned.\n&7{reason}"))
                .failOpen(c.getBoolean("fail-open", true))
                .denyOnBannedStatus(c.getBoolean("deny-on-banned-status", false))
                .loginTimeoutMs(c.getLong("login-timeout-ms", 5000))
                .build();

        BukkitLogger log = new BukkitLogger(getLogger());
        FileCursorStore cursors = new FileCursorStore(getDataFolder().toPath().resolve("cursor.dat"), log);
        BukkitBanSyncHandler handler = new BukkitBanSyncHandler(this, config.kickMessage());

        this.core = new McBansCore(config, log, handler, cursors);
        this.core.start();

        getServer().getPluginManager().registerEvents(this, this);
        var executor = new McBansCommand(core);
        getCommand("mcbans").setExecutor(executor);
        getCommand("mcban").setExecutor(executor);
        getCommand("mctempban").setExecutor(executor);
        getCommand("mcunban").setExecutor(executor);
        getCommand("mclookup").setExecutor(executor);

        getLogger().info("MCBans enabled (connecting to " + config.endpoint() + ").");
    }

    @Override
    public void onDisable() {
        if (core != null) {
            core.shutdown();
        }
    }

    /**
     * On-join ban check. Runs on Bukkit's async pre-login thread, so blocking on the network round
     * trip is safe and recommended — the core's own timeout + fail-open/closed policy guarantees a
     * decision even if the server is slow or unreachable.
     */
    @EventHandler(priority = EventPriority.HIGH)
    public void onPreLogin(AsyncPlayerPreLoginEvent event) {
        String uuid = event.getUniqueId().toString();
        String name = event.getName();
        String ip = event.getAddress().getHostAddress();

        LoginResult result;
        try {
            result = core.checkLogin(uuid, name, ip)
                    .get(core.config().loginTimeoutMs() + 2000, TimeUnit.MILLISECONDS);
        } catch (Exception e) {
            getLogger().warning("[MCBans] login check error for " + name + ": " + e.getMessage());
            if (!core.config().failOpen()) {
                event.disallow(AsyncPlayerPreLoginEvent.Result.KICK_BANNED,
                        MessageUtil.color("&cUnable to verify ban status. Try again shortly."));
            }
            return;
        }

        if (core.shouldDeny(result)) {
            String msg = core.config().kickMessage().replace("{reason}",
                    result.reason().isBlank() ? "Banned via MCBans." : result.reason());
            event.disallow(AsyncPlayerPreLoginEvent.Result.KICK_BANNED, MessageUtil.color(msg));
        }
    }
}
