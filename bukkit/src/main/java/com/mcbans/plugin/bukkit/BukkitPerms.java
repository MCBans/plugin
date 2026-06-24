package com.mcbans.plugin.bukkit;

import com.mcbans.plugin.core.permission.Perm;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.permissions.Permissible;

import java.util.HashSet;
import java.util.Set;

/** Checks the core {@link Perm} nodes against Bukkit's SuperPerms and finds permission holders. */
final class BukkitPerms {

    private BukkitPerms() {
    }

    static boolean has(Permissible who, Perm perm) {
        return who != null && who.hasPermission(perm.node());
    }

    /** Online players holding the given permission (for staff/announce notifications). */
    static Set<Player> holders(Perm perm) {
        Set<Player> set = new HashSet<>();
        for (Player p : Bukkit.getOnlinePlayers()) {
            if (p.hasPermission(perm.node())) {
                set.add(p);
            }
        }
        return set;
    }

    /** Send a message to every online holder of the permission. */
    static void message(Perm perm, String message) {
        for (Player p : holders(perm)) {
            p.sendMessage(message);
        }
    }
}
