package com.mcbans.plugin.bukkit;

import com.mcbans.plugin.core.McBansCore;
import com.mcbans.plugin.core.model.BanResult;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.Arrays;

/**
 * Admin command surface. All MCBans calls are async (never block the main thread): we fire the
 * core future and message the sender from the callback. Permissions gate each verb.
 */
final class McBansCommand implements CommandExecutor {

    private final McBansCore core;

    McBansCommand(McBansCore core) {
        this.core = core;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        String name = command.getName().toLowerCase();
        switch (name) {
            case "mcbans" -> {
                sender.sendMessage(MessageUtil.color(core.isReady()
                        ? "&aMCBans: connected and registered."
                        : "&eMCBans: connecting / not yet registered."));
                return true;
            }
            case "mcban" -> {
                if (!check(sender, "mcbans.ban", args, 2, "/mcban <player> <reason>")) return true;
                String target = args[0];
                String reason = join(args, 1);
                core.globalBan(target, null, sender.getName(), reason, 0, ipOf(sender, target))
                        .whenComplete((r, e) -> reply(sender, "ban " + target, r, e));
                return true;
            }
            case "mctempban" -> {
                if (!check(sender, "mcbans.tempban", args, 4, "/mctempban <player> <duration> <w|d|h|m> <reason>"))
                    return true;
                String target = args[0];
                long duration = parseLong(args[1]);
                String measure = args[2];
                String reason = join(args, 3);
                core.tempBan(target, null, sender.getName(), reason, measure, duration, ipOf(sender, target))
                        .whenComplete((r, e) -> reply(sender, "tempban " + target, r, e));
                return true;
            }
            case "mcunban" -> {
                if (!check(sender, "mcbans.unban", args, 1, "/mcunban <player|ip>")) return true;
                String target = args[0];
                core.unBan(target, null).whenComplete((r, e) -> reply(sender, "unban " + target, r, e));
                return true;
            }
            case "mclookup" -> {
                if (!check(sender, "mcbans.lookup", args, 1, "/mclookup <player>")) return true;
                String target = args[0];
                core.playerLookup(target, null, sender.getName()).whenComplete((data, e) -> {
                    if (e != null) {
                        sender.sendMessage(MessageUtil.color("&cLookup failed: " + e.getMessage()));
                    } else {
                        sender.sendMessage(MessageUtil.color("&b" + target + "&7: total="
                                + opt(data, "total") + " reputation=" + opt(data, "reputation")));
                    }
                });
                return true;
            }
            default -> {
                return false;
            }
        }
    }

    private boolean check(CommandSender sender, String perm, String[] args, int min, String usage) {
        if (!sender.hasPermission(perm)) {
            sender.sendMessage(MessageUtil.color("&cYou lack permission: " + perm));
            return false;
        }
        if (args.length < min) {
            sender.sendMessage(MessageUtil.color("&cUsage: " + usage));
            return false;
        }
        return true;
    }

    private void reply(CommandSender sender, String what, BanResult r, Throwable e) {
        if (e != null) {
            sender.sendMessage(MessageUtil.color("&c" + what + " failed: " + e.getMessage()));
            return;
        }
        sender.sendMessage(MessageUtil.color((r.isSuccess() ? "&a" : "&e") + what + ": "
                + (r.message().isBlank() ? r.code() : r.message())));
    }

    private static String ipOf(CommandSender sender, String target) {
        Player p = sender.getServer().getPlayerExact(target);
        return p != null && p.getAddress() != null ? p.getAddress().getAddress().getHostAddress() : "";
    }

    private static String opt(com.google.gson.JsonObject o, String k) {
        return o != null && o.has(k) && !o.get(k).isJsonNull() ? o.get(k).getAsString() : "?";
    }

    private static String join(String[] args, int from) {
        return String.join(" ", Arrays.copyOfRange(args, from, args.length));
    }

    private static long parseLong(String s) {
        try {
            return Long.parseLong(s);
        } catch (NumberFormatException e) {
            return 1;
        }
    }
}
