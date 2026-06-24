package com.mcbans.plugin.core.permission;

/**
 * The MCBans permission node tree (ported from the legacy {@code Perms} enum). Core only knows the
 * node strings; each platform adapter checks them against its own permission system
 * (Bukkit SuperPerms, Sponge subjects, Forge permission API, ...).
 */
public enum Perm {
    ADMIN("admin"),

    BAN_GLOBAL("ban.global"),
    BAN_LOCAL("ban.local"),
    BAN_TEMP("ban.temp"),
    BAN_ROLLBACK("ban.rollback"),
    BAN_IP("ban.ip"),
    UNBAN("unban"),
    KICK("kick"),
    VERIFY("verify"),

    VIEW_ALTS("view.alts"),
    VIEW_BANS("view.bans"),
    VIEW_STAFF("view.staff"),
    VIEW_PREVIOUS("view.previous"),
    VIEW_PROXY("view.proxy"),
    ANNOUNCE("announce"),
    HIDE_VIEW("hideview"),

    EXEMPT_KICK("kick.exempt"),
    EXEMPT_BAN("ban.exempt"),
    EXEMPT_MAXALTS("maxalts.exempt"),

    LOOKUP_PLAYER("lookup.player"),
    LOOKUP_BAN("lookup.ban"),
    LOOKUP_ALT("lookup.alt");

    public static final String HEADER = "mcbans.";

    private final String node;

    Perm(String node) {
        this.node = HEADER + node;
    }

    /** The full permission node, e.g. {@code mcbans.ban.global}. */
    public String node() {
        return node;
    }
}
