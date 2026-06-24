package com.mcbans.plugin.bukkit;

import com.mcbans.plugin.core.model.BanSyncAction;
import com.mcbans.plugin.core.model.Notice;
import com.mcbans.plugin.core.platform.BanSyncHandler;
import com.mcbans.plugin.core.util.Identifiers;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

/**
 * Applies pushed ban-sync actions on Bukkit: a {@code ban} kicks the matching online player; an
 * {@code unban} is a no-op locally (the next login check sees the lifted ban). Push frames arrive on
 * the network thread, so every Bukkit API touch is bounced to the main thread.
 */
final class BukkitBanSyncHandler implements BanSyncHandler {

    private final Plugin plugin;
    private final String kickMessage;

    BukkitBanSyncHandler(Plugin plugin, String kickMessage) {
        this.plugin = plugin;
        this.kickMessage = kickMessage;
    }

    @Override
    public void enforce(BanSyncAction action) {
        Bukkit.getScheduler().runTask(plugin, () -> {
            Player target = resolve(action);
            if (target != null && target.isOnline()) {
                target.kickPlayer(MessageUtil.color(kickMessage.replace("{reason}", "Banned via MCBans.")));
                plugin.getLogger().info("[MCBans] Kicked " + target.getName() + " (ban-sync " + action.id() + ").");
            }
        });
    }

    @Override
    public void lift(BanSyncAction action) {
        // Server-side local unban state is authoritative on MCBans; nothing to enforce locally.
    }

    @Override
    public void onNotice(Notice notice) {
        plugin.getLogger().info("[MCBans] Notice: " + notice.message());
    }

    private Player resolve(BanSyncAction action) {
        String uuid = action.uuid();
        if (uuid != null && !uuid.isBlank()) {
            try {
                Player p = Bukkit.getPlayer(Identifiers.parseUuid(uuid));
                if (p != null) {
                    return p;
                }
            } catch (IllegalArgumentException ignored) {
                // fall through to name lookup
            }
        }
        return action.name() == null || action.name().isBlank() ? null : Bukkit.getPlayerExact(action.name());
    }
}
