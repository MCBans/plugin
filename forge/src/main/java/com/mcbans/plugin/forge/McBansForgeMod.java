package com.mcbans.plugin.forge;

import com.mcbans.plugin.core.McBansConfig;
import com.mcbans.plugin.core.McBansCore;
import com.mcbans.plugin.core.OfflineBanList;
import com.mcbans.plugin.core.PropertiesConfig;
import com.mcbans.plugin.core.model.BanSyncAction;
import com.mcbans.plugin.core.model.Notice;
import com.mcbans.plugin.core.platform.BanSyncHandler;
import com.mcbans.plugin.core.platform.FileCursorStore;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.server.ServerStartingEvent;
import net.minecraftforge.event.server.ServerStoppingEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.loading.FMLPaths;
import net.minecraftforge.common.MinecraftForge;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Forge server-side entry point. Forge has no async pre-login gate as clean as Bukkit's, so we let
 * the player connect and disconnect them the moment the (async) ban check resolves to a ban — the
 * core's timeout + fail-open/closed policy still applies. Ban-sync pushes kick matching online
 * players the same way.
 */
@Mod(McBansForgeMod.MODID)
public final class McBansForgeMod {

    public static final String MODID = "mcbans";
    private static final Logger LOGGER = LoggerFactory.getLogger("McBans");

    private McBansCore core;
    private MinecraftServer server;

    public McBansForgeMod() {
        MinecraftForge.EVENT_BUS.register(this);
    }

    @SubscribeEvent
    public void onServerStarting(ServerStartingEvent event) {
        this.server = event.getServer();
        try {
            McBansConfig config = PropertiesConfig.loadOrCreate(
                    FMLPaths.CONFIGDIR.get().resolve("mcbans.properties"));
            ForgeLogger log = new ForgeLogger(LOGGER);
            FileCursorStore cursors = new FileCursorStore(
                    FMLPaths.CONFIGDIR.get().resolve("mcbans-cursor.dat"), log);
            OfflineBanList offline = new OfflineBanList(
                    FMLPaths.CONFIGDIR.get().resolve("mcbans-offline-bans.json"), log);
            this.core = new McBansCore(config, log, new ForgeBanSyncHandler(), cursors, offline);
            this.core.start();
            LOGGER.info("MCBans enabled (connecting to {}).", config.endpoint());
        } catch (Exception e) {
            LOGGER.error("MCBans failed to start: {}", e.getMessage(), e);
        }
    }

    @SubscribeEvent
    public void onServerStopping(ServerStoppingEvent event) {
        if (core != null) {
            core.shutdown();
        }
    }

    @SubscribeEvent
    public void onPlayerLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (core == null || !(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        String uuid = player.getUUID().toString();
        String name = player.getGameProfile().getName();
        String ip = extractIp(player);

        core.checkLogin(uuid, name, ip).thenAccept(result -> {
            String kick = core.loginKickMessage(result);
            if (kick != null) {
                // Disconnect must happen on the server thread.
                server.execute(() -> player.connection.disconnect(Component.literal(kick)));
            }
        });
    }

    private static String extractIp(ServerPlayer player) {
        try {
            String raw = player.connection.connection.getRemoteAddress().toString();
            // e.g. "/1.2.3.4:54321" -> "1.2.3.4"
            int slash = raw.indexOf('/');
            String s = slash >= 0 ? raw.substring(slash + 1) : raw;
            int colon = s.lastIndexOf(':');
            return colon >= 0 ? s.substring(0, colon) : s;
        } catch (Exception e) {
            return "";
        }
    }

    /** Applies pushed ban-sync actions: a ban kicks the matching online player. */
    private final class ForgeBanSyncHandler implements BanSyncHandler {
        @Override
        public void enforce(BanSyncAction action) {
            if (server == null) {
                return;
            }
            server.execute(() -> {
                ServerPlayer p = resolve(action);
                if (p != null) {
                    p.connection.disconnect(Component.literal(
                            core.config().kickMessage().replace("{reason}", "Banned via MCBans.")));
                }
            });
        }

        @Override
        public void lift(BanSyncAction action) {
            // Authoritative server-side; nothing to enforce locally.
        }

        @Override
        public void onNotice(Notice notice) {
            LOGGER.info("[MCBans] Notice: {}", notice.message());
        }

        private ServerPlayer resolve(BanSyncAction action) {
            if (action.uuid() != null && !action.uuid().isBlank()) {
                try {
                    ServerPlayer p = server.getPlayerList().getPlayer(
                            com.mcbans.plugin.core.util.Identifiers.parseUuid(action.uuid()));
                    if (p != null) {
                        return p;
                    }
                } catch (IllegalArgumentException ignored) {
                    // fall through to name lookup
                }
            }
            return action.name() == null ? null : server.getPlayerList().getPlayerByName(action.name());
        }
    }
}
