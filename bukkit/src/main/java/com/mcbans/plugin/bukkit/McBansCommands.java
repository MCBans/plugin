package com.mcbans.plugin.bukkit;

import com.mcbans.plugin.bukkit.events.PlayerBanEvent;
import com.mcbans.plugin.bukkit.events.PlayerKickEvent;
import com.mcbans.plugin.bukkit.events.PlayerUnbanEvent;
import com.mcbans.plugin.core.McBansCore;
import com.mcbans.plugin.core.command.BanKind;
import com.mcbans.plugin.core.command.CommandParsing;
import com.mcbans.plugin.core.i18n.Messages;
import com.mcbans.plugin.core.model.BanResult;
import com.mcbans.plugin.core.permission.Perm;
import com.mcbans.plugin.core.util.Identifiers;
import com.google.gson.JsonObject;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.command.CommandExecutor;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

/**
 * The full MCBans admin command set, ported from the legacy {@code Command*} classes onto the
 * generic {@link McBansCore}. Every network call is async; replies are messaged back from the
 * callback (hopping to the main thread for player kicks / event dispatch). Each verb is permission
 * gated via the {@link Perm} tree and all output is localised through {@link Messages}.
 */
final class McBansCommands implements CommandExecutor, TabCompleter {

    private final McBansBukkitPlugin plugin;
    private final McBansCore core;
    private final Messages msg;

    McBansCommands(McBansBukkitPlugin plugin) {
        this.plugin = plugin;
        this.core = plugin.core();
        this.msg = core.messages();
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        switch (command.getName().toLowerCase()) {
            case "ban"       -> doBan(sender, args, null);
            case "rban"      -> doBan(sender, args, null);   // rollback engine not enabled; behaves as ban
            case "globalban" -> doBan(sender, args, BanKind.GLOBAL);
            case "tempban"   -> doTempBan(sender, args);
            case "banip"     -> doBanIp(sender, args);
            case "kick"      -> doKick(sender, args);
            case "unban"     -> doUnban(sender, args);
            case "lookup"    -> doLookup(sender, args);
            case "banlookup" -> doBanLookup(sender, args);
            case "altlookup" -> doAltLookup(sender, args);
            case "namelookup"-> doNameLookup(sender, args);
            case "mcbs"      -> doSetting(sender, args);
            case "mcbans"    -> doInfo(sender);
            default          -> { return false; }
        }
        return true;
    }

    // ---- ban / globalban / rban ---------------------------------------------------------------

    private void doBan(CommandSender sender, String[] args, BanKind forced) {
        if (args.length < 1) {
            err(sender, "formatError");
            return;
        }
        Target t = resolve(args[0]);
        List<String> tail = new ArrayList<>(Arrays.asList(args).subList(1, args.length));

        CommandParsing.BanIntent intent = forced == BanKind.GLOBAL
                ? new CommandParsing.BanIntent(BanKind.GLOBAL, "0", "",
                        tail.isEmpty() ? core.config().defaultLocalReason() : String.join(" ", tail))
                : CommandParsing.parseBan(tail, core.config().defaultLocalReason());

        if (!require(sender, intent.kind.permission())) {
            return;
        }

        CompletableFuture<BanResult> future = switch (intent.kind) {
            case GLOBAL -> core.globalBan(t.name, t.uuid, sender.getName(), intent.reason, 0, t.ip);
            case TEMP -> core.tempBan(t.name, t.uuid, sender.getName(), intent.reason,
                    intent.measure.isEmpty() ? "m" : intent.measure, parseLong(intent.duration), t.ip);
            case LOCAL -> core.localBan(t.name, t.uuid, sender.getName(), intent.reason, t.ip);
        };
        future.whenComplete((r, e) -> sync(() -> handleBan(sender, intent.kind, t, intent.reason, r, e)));
    }

    private void doTempBan(CommandSender sender, String[] args) {
        if (args.length < 2) {
            err(sender, "formatError");
            return;
        }
        if (!require(sender, Perm.BAN_TEMP)) {
            return;
        }
        Target t = resolve(args[0]);
        String[] dm = CommandParsing.parseDuration(args[1]);
        String duration = dm != null ? dm[0] : "30";
        String measure = dm != null && !dm[1].isEmpty() ? dm[1] : "m";
        int reasonStart = 2;
        // optional separate unit token ("/tban x 15 m reason")
        if (dm != null && dm[1].isEmpty() && args.length > 2 && args[2].matches("(?i)m|h|d|w|s")) {
            measure = args[2].toLowerCase();
            reasonStart = 3;
        }
        String reason = args.length > reasonStart
                ? String.join(" ", Arrays.asList(args).subList(reasonStart, args.length))
                : core.config().defaultTempReason();
        core.tempBan(t.name, t.uuid, sender.getName(), reason, measure, parseLong(duration), t.ip)
                .whenComplete((r, e) -> sync(() -> handleBan(sender, BanKind.TEMP, t, reason, r, e)));
    }

    private void handleBan(CommandSender sender, BanKind kind, Target t, String reason, BanResult r, Throwable e) {
        String p = kind.messagePrefix();
        if (e != null) {
            err(sender, p + "Error");
            return;
        }
        switch (r.code()) {
            case SUCCESS -> {
                String resolved = r.player().isBlank() ? t.name : r.player();
                BukkitPerms.message(Perm.ANNOUNCE, msg.localize(p + "Success",
                        Messages.PLAYER, resolved, Messages.ADMIN, sender.getName(), Messages.REASON, reason));
                plugin.send(sender, msg.localize(p + "Success",
                        Messages.PLAYER, resolved, Messages.ADMIN, sender.getName(), Messages.REASON, reason));
                // kick the player if online
                Player online = Bukkit.getPlayerExact(t.name);
                if (online != null) {
                    online.kickPlayer(msg.localize(p + "Player",
                            Messages.ADMIN, sender.getName(), Messages.REASON, reason));
                }
                Bukkit.getPluginManager().callEvent(new PlayerBanEvent(resolved, sender.getName(), reason, kind));
            }
            case ALREADY -> plugin.send(sender, msg.localize(p + "Already", Messages.PLAYER, t.name));
            case PROFANITY -> plugin.send(sender, msg.localize("globalBanWarning",
                    Messages.BADWORD, r.message().isBlank() ? reason : r.message()));
            default -> err(sender, p + "Error");
        }
    }

    // ---- banip ---------------------------------------------------------------------------------

    private void doBanIp(CommandSender sender, String[] args) {
        if (args.length < 1) {
            err(sender, "formatError");
            return;
        }
        if (!require(sender, Perm.BAN_IP)) {
            return;
        }
        String ip = args[0];
        if (!Identifiers.isIpv4(ip)) {
            err(sender, "invalidIP");
            return;
        }
        String reason = args.length > 1
                ? String.join(" ", Arrays.asList(args).subList(1, args.length)) : core.config().defaultLocalReason();
        core.ipBan(ip, sender.getName(), reason).whenComplete((r, e) -> sync(() -> {
            if (e != null) {
                err(sender, "ipBanError", Messages.IP, ip);
            } else if (r.isSuccess()) {
                plugin.send(sender, msg.localize("ipBanSuccess"));
            } else if (r.code() == BanResult.Code.ALREADY) {
                plugin.send(sender, msg.localize("ipBanAlready", Messages.IP, ip));
            } else {
                err(sender, "ipBanError", Messages.IP, ip);
            }
        }));
    }

    // ---- kick (local) --------------------------------------------------------------------------

    private void doKick(CommandSender sender, String[] args) {
        if (args.length < 1) {
            err(sender, "formatError");
            return;
        }
        if (!require(sender, Perm.KICK)) {
            return;
        }
        Player target = Bukkit.getPlayerExact(args[0]);
        if (target == null) {
            plugin.send(sender, msg.localize("kickNoPlayer", Messages.PLAYER, args[0]));
            return;
        }
        if (BukkitPerms.has(target, Perm.EXEMPT_KICK)) {
            plugin.send(sender, msg.localize("kickExemptPlayer", Messages.PLAYER, target.getName()));
            return;
        }
        String reason = args.length > 1
                ? String.join(" ", Arrays.asList(args).subList(1, args.length)) : core.config().defaultKickReason();
        target.kickPlayer(msg.localize("kickPlayer", Messages.ADMIN, sender.getName(), Messages.REASON, reason));
        BukkitPerms.message(Perm.ANNOUNCE, msg.localize("kickSuccess",
                Messages.PLAYER, target.getName(), Messages.ADMIN, sender.getName(), Messages.REASON, reason));
        plugin.send(sender, msg.localize("kickSuccess",
                Messages.PLAYER, target.getName(), Messages.ADMIN, sender.getName(), Messages.REASON, reason));
        Bukkit.getPluginManager().callEvent(new PlayerKickEvent(target.getName(), sender.getName(), reason));
    }

    // ---- unban ---------------------------------------------------------------------------------

    private void doUnban(CommandSender sender, String[] args) {
        if (args.length < 1) {
            err(sender, "formatError");
            return;
        }
        if (!require(sender, Perm.UNBAN)) {
            return;
        }
        Target t = resolve(args[0]);
        core.unBan(t.name, t.uuid).whenComplete((r, e) -> sync(() -> {
            if (e != null) {
                err(sender, "unBanError", Messages.PLAYER, t.name);
                return;
            }
            switch (r.code()) {
                case SUCCESS -> {
                    if (core.offlineBanList() != null && t.uuid != null) {
                        core.offlineBanList().remove(t.uuid);
                    }
                    plugin.send(sender, msg.localize("unBanSuccess",
                            Messages.PLAYER, r.player().isBlank() ? t.name : r.player(), Messages.ADMIN, sender.getName()));
                    Bukkit.getPluginManager().callEvent(new PlayerUnbanEvent(t.name, sender.getName()));
                }
                case NOT_FOUND -> plugin.send(sender, msg.localize("unBanNot", Messages.PLAYER, t.name));
                default -> err(sender, "unBanError", Messages.PLAYER, t.name);
            }
        }));
    }

    // ---- lookups -------------------------------------------------------------------------------

    private void doLookup(CommandSender sender, String[] args) {
        if (args.length < 1) {
            err(sender, "formatError");
            return;
        }
        if (!require(sender, Perm.LOOKUP_PLAYER)) {
            return;
        }
        Target t = resolve(args[0]);
        core.playerLookup(t.name, t.uuid, sender.getName()).whenComplete((d, e) -> sync(() -> {
            if (e != null) {
                err(sender, "unavailable");
            } else {
                plugin.send(sender, "§b" + args[0] + "§7 — total: §f" + opt(d, "total")
                        + " §7reputation: §f" + opt(d, "reputation"));
            }
        }));
    }

    private void doBanLookup(CommandSender sender, String[] args) {
        if (args.length < 1) {
            err(sender, "formatError");
            return;
        }
        if (!require(sender, Perm.LOOKUP_BAN)) {
            return;
        }
        core.banLookup(args[0]).whenComplete((d, e) -> sync(() -> {
            if (e != null || d.has("error")) {
                plugin.send(sender, "§7Ban §c#" + args[0] + " §7not found.");
            } else {
                plugin.send(sender, "§7Ban §b#" + args[0] + "§7: §f" + opt(d, "player")
                        + " §8— §f" + opt(d, "reason") + " §8(" + opt(d, "type") + ") by §f" + opt(d, "admin"));
            }
        }));
    }

    private void doAltLookup(CommandSender sender, String[] args) {
        if (args.length < 1) {
            err(sender, "formatError");
            return;
        }
        if (!require(sender, Perm.LOOKUP_ALT)) {
            return;
        }
        Target t = resolve(args[0]);
        core.altList(t.name, t.uuid).whenComplete((d, e) -> sync(() -> {
            if (e != null || "n".equals(opt(d, "result"))) {
                plugin.send(sender, "§7No alt data (premium servers only).");
            } else {
                plugin.send(sender, msg.localize("altAccounts",
                        Messages.PLAYER, args[0], Messages.ALTS, opt(d, "altList")));
            }
        }));
    }

    private void doNameLookup(CommandSender sender, String[] args) {
        if (args.length < 1) {
            err(sender, "formatError");
            return;
        }
        if (!require(sender, Perm.LOOKUP_PLAYER)) {
            return;
        }
        Target t = resolve(args[0]);
        core.uuidLookup(t.name, t.uuid).whenComplete((d, e) -> sync(() -> {
            if (e != null || !"y".equals(opt(d, "result"))) {
                plugin.send(sender, msg.localize("previousNamesNone", Messages.PLAYER, args[0]));
            } else {
                plugin.send(sender, msg.localize("previousNames",
                        Messages.PLAYER, opt(d, "player"), Messages.PLAYERS, opt(d, "players")));
            }
        }));
    }

    // ---- settings / info -----------------------------------------------------------------------

    private void doSetting(CommandSender sender, String[] args) {
        if (!require(sender, Perm.ADMIN)) {
            return;
        }
        if (args.length < 1) {
            err(sender, "formatError");
            return;
        }
        String expr = String.join(" ", args);
        core.setting(expr).whenComplete((d, e) -> sync(() -> {
            if (e != null) {
                plugin.send(sender, msg.localize("failSetting", Messages.REASON, expr));
            } else if ("y".equals(opt(d, "result"))) {
                plugin.send(sender, msg.localize("successSetting", Messages.REASON, opt(d, "reason")));
            } else {
                plugin.send(sender, msg.localize("failSetting", Messages.REASON, opt(d, "reason")));
            }
        }));
    }

    private void doInfo(CommandSender sender) {
        plugin.send(sender, core.isReady()
                ? "§aConnected & registered (v" + plugin.getDescription().getVersion() + ")."
                : "§eConnecting / not yet registered…");
    }

    // ---- helpers -------------------------------------------------------------------------------

    private record Target(String name, String uuid, String ip) {
    }

    /** Resolve a target token to (name, uuid, ip), filling uuid/ip from the online player if present. */
    private Target resolve(String token) {
        // a raw UUID token
        if (Identifiers.isUuid(token)) {
            return new Target(token, Identifiers.normalizeUuid(token), "");
        }
        Player online = Bukkit.getPlayerExact(token);
        if (online != null) {
            String ip = online.getAddress() != null ? online.getAddress().getAddress().getHostAddress() : "";
            return new Target(online.getName(), online.getUniqueId().toString().replace("-", ""), ip);
        }
        return new Target(token, "", "");
    }

    private boolean require(CommandSender sender, Perm perm) {
        if (sender.hasPermission(perm.node()) || BukkitPerms.has(sender instanceof Player p ? p : null, Perm.ADMIN)) {
            return true;
        }
        plugin.send(sender, msg.localize("permissionDenied"));
        return false;
    }

    private void err(CommandSender sender, String key, Object... binds) {
        plugin.send(sender, msg.localize(key, binds));
    }

    private void sync(Runnable r) {
        if (plugin.isEnabled()) {
            Bukkit.getScheduler().runTask(plugin, r);
        }
    }

    private static String opt(JsonObject o, String k) {
        return o != null && o.has(k) && !o.get(k).isJsonNull() ? o.get(k).getAsString() : "?";
    }

    private static long parseLong(String s) {
        try {
            return Long.parseLong(s);
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            String prefix = args[0].toLowerCase();
            return Bukkit.getOnlinePlayers().stream().map(Player::getName)
                    .filter(n -> n.toLowerCase().startsWith(prefix)).collect(Collectors.toList());
        }
        return new ArrayList<>();
    }
}
