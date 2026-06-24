package com.mcbans.plugin.sponge;

import com.mcbans.plugin.core.model.BanSyncAction;
import com.mcbans.plugin.core.model.Notice;
import com.mcbans.plugin.core.platform.BanSyncHandler;
import net.kyori.adventure.text.Component;
import org.apache.logging.log4j.Logger;
import org.spongepowered.api.Sponge;
import org.spongepowered.api.entity.living.player.server.ServerPlayer;

import java.util.UUID;

/** Applies pushed ban-sync actions on Sponge: a {@code ban} kicks the matching online player. */
final class SpongeBanSyncHandler implements BanSyncHandler {

    private final Logger logger;
    private final String kickMessage;

    SpongeBanSyncHandler(Logger logger, String kickMessage) {
        this.logger = logger;
        this.kickMessage = kickMessage;
    }

    @Override
    public void enforce(BanSyncAction action) {
        resolve(action).ifPresent(player ->
                player.kick(Component.text(kickMessage.replace("{reason}", "Banned via MCBans."))));
    }

    @Override
    public void lift(BanSyncAction action) {
        // Local unban state is authoritative server-side; nothing to do locally.
    }

    @Override
    public void onNotice(Notice notice) {
        logger.info("[MCBans] Notice: {}", notice.message());
    }

    private java.util.Optional<ServerPlayer> resolve(BanSyncAction action) {
        if (action.uuid() != null && !action.uuid().isBlank()) {
            try {
                return Sponge.server().player(toUuid(action.uuid()));
            } catch (IllegalArgumentException ignored) {
                // fall through to name lookup
            }
        }
        return action.name() == null ? java.util.Optional.empty() : Sponge.server().player(action.name());
    }

    private static UUID toUuid(String raw) {
        String s = raw.replace("-", "");
        return UUID.fromString(s.replaceFirst(
                "(\\p{XDigit}{8})(\\p{XDigit}{4})(\\p{XDigit}{4})(\\p{XDigit}{4})(\\p{XDigit}{12})",
                "$1-$2-$3-$4-$5"));
    }
}
