package com.mcbans.plugin.bukkit;

import com.mcbans.plugin.core.McBansCore;
import com.mcbans.plugin.core.OfflineBanList;
import com.mcbans.plugin.core.i18n.Messages;
import com.mcbans.plugin.core.model.LoginResult;
import com.mcbans.plugin.core.permission.Perm;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerPreLoginEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

/**
 * On-join handling, ported from the legacy {@code PlayerListener}:
 *
 * <ul>
 *   <li><b>Pre-login</b> (async): run the MCBans login check and apply the ban / min-rep / max-alts
 *       gates; on a network error, fall back to the offline ban list, then the fail-open/closed
 *       policy.</li>
 *   <li><b>Join</b>: surface previous-bans (to {@code mcbans.view.bans}), alt accounts
 *       ({@code mcbans.view.alts}), recent name changes ({@code mcbans.view.previous}), and the
 *       MCBans-staff notice ({@code mcbans.view.staff}); optionally the "secured by MCBans" advert.</li>
 * </ul>
 */
final class McBansListener implements Listener {

    private final McBansBukkitPlugin plugin;
    private final McBansCore core;
    private final Messages msg;
    // Carries the pre-login result to the join handler (so notifications run with a real Player).
    private final ConcurrentHashMap<String, LoginResult> pending = new ConcurrentHashMap<>();

    McBansListener(McBansBukkitPlugin plugin) {
        this.plugin = plugin;
        this.core = plugin.core();
        this.msg = core.messages();
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPreLogin(AsyncPlayerPreLoginEvent event) {
        String uuid = event.getUniqueId().toString();
        String name = event.getName();
        String ip = event.getAddress().getHostAddress();

        LoginResult result;
        try {
            result = core.checkLogin(uuid, name, ip)
                    .get(core.config().loginTimeoutMs() + 2000, TimeUnit.MILLISECONDS);
        } catch (Exception e) {
            // API unreachable: consult the offline failsafe, then the configured policy.
            OfflineBanList offline = core.offlineBanList();
            if (offline != null && offline.isBanned(uuid)) {
                event.disallow(AsyncPlayerPreLoginEvent.Result.KICK_BANNED,
                        core.offlineKickMessage(offline.get(uuid)));
            } else if (!core.config().failOpen()) {
                event.disallow(AsyncPlayerPreLoginEvent.Result.KICK_BANNED,
                        msg.localize("unavailable"));
            }
            return;
        }

        String kick = core.loginKickMessage(result);
        if (kick != null) {
            event.disallow(AsyncPlayerPreLoginEvent.Result.KICK_BANNED, kick);
            return;
        }
        // Allowed — stash the result so onJoin can show notifications.
        pending.put(name.toLowerCase(), result);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        LoginResult result = pending.remove(player.getName().toLowerCase());
        if (result == null) {
            return;
        }

        // Previous bans → notify staff.
        if (!result.bans().isEmpty()) {
            BukkitPerms.message(Perm.VIEW_BANS, msg.localize("previousBans", Messages.PLAYER, player.getName()));
            if (core.config().sendDetailPrevBans()) {
                result.bans().forEach(ban -> BukkitPerms.message(Perm.VIEW_BANS,
                        msg.localize("banInformation",
                                Messages.ADMIN, ban.admin(),
                                Messages.REASON, ban.reason(),
                                Messages.SERVER, ban.server())));
            }
        }

        // Alt accounts.
        if (result.altCount() > 0 && !result.altList().isBlank()) {
            BukkitPerms.message(Perm.VIEW_ALTS,
                    msg.localize("altAccounts", Messages.PLAYER, player.getName(), Messages.ALTS, result.altList()));
        }

        // Recent name changes.
        if (!result.nameChanges().isBlank()) {
            BukkitPerms.message(Perm.VIEW_PREVIOUS, msg.localize("previousNamesHas",
                    Messages.PLAYER, player.getName(), Messages.PLAYERS, result.nameChanges()));
        }

        // MCBans staff notice.
        if (result.isMcBansStaff()) {
            Bukkit.getConsoleSender().sendMessage(msg.localize("isMCBansMod", Messages.PLAYER, player.getName()));
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                BukkitPerms.message(Perm.VIEW_STAFF, msg.localize("isMCBansMod", Messages.PLAYER, player.getName()));
                player.sendMessage(msg.localize("mcbansStaffVersion",
                        Messages.VERSION, plugin.getDescription().getVersion()));
            }, 1L);
        }

        if (core.config().onJoinMessage()) {
            player.sendMessage(msg.localize("mcbansServer"));
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        // Best-effort: tell MCBans the player left this server's online list.
        core.playerDisconnect(event.getPlayer().getName());
    }
}
