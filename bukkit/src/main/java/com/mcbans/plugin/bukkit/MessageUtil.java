package com.mcbans.plugin.bukkit;

import org.bukkit.ChatColor;

/** Small helper for translating {@code &}-style colour codes in configured messages. */
final class MessageUtil {

    private MessageUtil() {
    }

    static String color(String s) {
        return s == null ? "" : ChatColor.translateAlternateColorCodes('&', s);
    }
}
