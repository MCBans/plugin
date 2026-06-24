package com.mcbans.plugin.bukkit;

import com.mcbans.plugin.core.McBansConfig;
import com.mcbans.plugin.core.McBansCore;
import com.mcbans.plugin.core.OfflineBanList;
import com.mcbans.plugin.core.i18n.Messages;
import com.mcbans.plugin.core.platform.FileCursorStore;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * Bukkit/Spigot/Paper entry point. Wires the generic {@link McBansCore} to the server: builds config
 * from {@code config.yml}, opens the WebSocket session, registers the on-join ban gate + connect
 * notifications ({@link McBansListener}) and the full admin command set ({@link McBansCommands}).
 */
public class McBansBukkitPlugin extends JavaPlugin {

    private McBansCore core;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        FileConfiguration c = getConfig();

        String apiKey = firstNonBlank(c.getString("apiKey"), c.getString("api-key"));
        if (apiKey == null || apiKey.isBlank() || "YOUR_API_KEY_HERE".equals(apiKey)) {
            getLogger().severe("No apiKey set in config.yml — MCBans is disabled. "
                    + "Add your server API key (servers.server_api) and restart.");
            return;
        }

        McBansConfig config = McBansConfig.builder()
                .host(c.getString("host", "www.mcbans.com"))
                .apiKey(apiKey)
                .version(c.getInt("protocol-version", 3))
                .tls(c.getBoolean("tls", true))
                .prefix(c.getString("prefix", "&cMCBans &8>&r "))
                .language(c.getString("language", "default"))
                .kickMessage(c.getString("kick-message", "&cYou are banned.\n&7{reason}"))
                // "failsafe: true" means deny on error → failOpen is its inverse.
                .failOpen(!c.getBoolean("failsafe", false))
                .denyOnBannedStatus(c.getBoolean("deny-on-banned-status", false))
                .loginTimeoutMs(c.getLong("timeout", 10) * 1000L)
                .defaultLocalReason(c.getString("defaultLocal", "You have been banned!"))
                .defaultTempReason(c.getString("defaultTemp", "You have been temporarily banned!"))
                .defaultKickReason(c.getString("defaultKick", "You have been kicked!"))
                .minReputation(c.getInt("minRep", -1))
                .enableMaxAlts(c.getBoolean("enableMaxAlts", false))
                .maxAlts(c.getInt("maxAlts", 2))
                .onJoinMessage(c.getBoolean("onJoinMCBansMessage", false))
                .sendDetailPrevBans(c.getBoolean("sendDetailPrevBansOnJoin", true))
                .build();

        BukkitLogger log = new BukkitLogger(getLogger());
        FileCursorStore cursors = new FileCursorStore(getDataFolder().toPath().resolve("cursor.dat"), log);
        OfflineBanList offline = new OfflineBanList(getDataFolder().toPath().resolve("offline-bans.json"), log);
        BukkitBanSyncHandler handler = new BukkitBanSyncHandler(this, config.kickMessage());

        this.core = new McBansCore(config, log, handler, cursors, offline);
        this.core.start();

        getServer().getPluginManager().registerEvents(new McBansListener(this), this);

        McBansCommands cmd = new McBansCommands(this);
        for (String name : new String[] {"ban", "globalban", "tempban", "banip", "kick", "unban", "rban",
                "lookup", "banlookup", "altlookup", "namelookup", "mcbans", "mcbs"}) {
            if (getCommand(name) != null) {
                getCommand(name).setExecutor(cmd);
                getCommand(name).setTabCompleter(cmd);
            }
        }

        getLogger().info("MCBans enabled (connecting to " + config.endpoint() + ", language="
                + config.language() + ").");
    }

    @Override
    public void onDisable() {
        if (core != null) {
            core.shutdown();
        }
    }

    McBansCore core() {
        return core;
    }

    Messages messages() {
        return core.messages();
    }

    /** Send a prefixed, colour-translated message to a sender. */
    void send(CommandSender to, String message) {
        to.sendMessage(Messages.color(core.config().prefix()) + message);
    }

    private static String firstNonBlank(String a, String b) {
        if (a != null && !a.isBlank()) {
            return a;
        }
        return b;
    }
}
