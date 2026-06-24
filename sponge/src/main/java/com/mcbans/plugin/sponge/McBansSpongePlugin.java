package com.mcbans.plugin.sponge;

import com.google.inject.Inject;
import com.mcbans.plugin.core.McBansConfig;
import com.mcbans.plugin.core.McBansCore;
import com.mcbans.plugin.core.OfflineBanList;
import com.mcbans.plugin.core.PropertiesConfig;
import com.mcbans.plugin.core.model.LoginResult;
import com.mcbans.plugin.core.platform.FileCursorStore;
import net.kyori.adventure.text.Component;
import org.apache.logging.log4j.Logger;
import org.spongepowered.api.Server;
import org.spongepowered.api.config.ConfigDir;
import org.spongepowered.api.event.Listener;
import org.spongepowered.api.event.lifecycle.StartedEngineEvent;
import org.spongepowered.api.event.lifecycle.StoppingEngineEvent;
import org.spongepowered.api.event.network.ServerSideConnectionEvent;
import org.spongepowered.plugin.builtin.jvm.Plugin;

import java.net.InetSocketAddress;
import java.nio.file.Path;
import java.util.concurrent.TimeUnit;

/**
 * SpongeAPI 8 entry point. Mirrors the Bukkit adapter: builds config from a properties file in the
 * plugin config dir, opens the WebSocket session on engine start, and gates joins on
 * {@link ServerSideConnectionEvent.Login}.
 */
@Plugin("mcbans")
public final class McBansSpongePlugin {

    private final Logger logger;
    private final Path configDir;
    private McBansCore core;

    @Inject
    McBansSpongePlugin(Logger logger, @ConfigDir(sharedRoot = false) Path configDir) {
        this.logger = logger;
        this.configDir = configDir;
    }

    @Listener
    public void onStart(StartedEngineEvent<Server> event) {
        try {
            McBansConfig config = PropertiesConfig.loadOrCreate(configDir.resolve("mcbans.properties"));
            SpongeLogger log = new SpongeLogger(logger);
            FileCursorStore cursors = new FileCursorStore(configDir.resolve("cursor.dat"), log);
            OfflineBanList offline = new OfflineBanList(configDir.resolve("offline-bans.json"), log);
            SpongeBanSyncHandler handler = new SpongeBanSyncHandler(logger, config.kickMessage());
            this.core = new McBansCore(config, log, handler, cursors, offline);
            this.core.start();
            logger.info("MCBans enabled (connecting to {}).", config.endpoint());
        } catch (Exception e) {
            logger.error("MCBans failed to start: {}", e.getMessage(), e);
        }
    }

    @Listener
    public void onStop(StoppingEngineEvent<Server> event) {
        if (core != null) {
            core.shutdown();
        }
    }

    @Listener
    public void onLogin(ServerSideConnectionEvent.Login event) {
        if (core == null) {
            return;
        }
        String uuid = event.profile().uniqueId().toString();
        String name = event.profile().name().orElse("");
        InetSocketAddress addr = event.connection().address();
        String ip = addr == null ? "" : addr.getAddress().getHostAddress();

        try {
            LoginResult result = core.checkLogin(uuid, name, ip)
                    .get(core.config().loginTimeoutMs() + 2000, TimeUnit.MILLISECONDS);
            String kick = core.loginKickMessage(result);
            if (kick != null) {
                event.setCancelled(true);
                event.setMessage(Component.text(kick));
            }
        } catch (Exception e) {
            logger.warn("[MCBans] login check error for {}: {}", name, e.getMessage());
            OfflineBanList offline = core.offlineBanList();
            if (offline != null && offline.isBanned(uuid)) {
                event.setCancelled(true);
                event.setMessage(Component.text(core.offlineKickMessage(offline.get(uuid))));
            } else if (!core.config().failOpen()) {
                event.setCancelled(true);
                event.setMessage(Component.text(core.messages().localize("unavailable")));
            }
        }
    }
}
