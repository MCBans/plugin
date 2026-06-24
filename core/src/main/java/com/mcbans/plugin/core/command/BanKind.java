package com.mcbans.plugin.core.command;

import com.mcbans.plugin.core.permission.Perm;

/** The kind of ban a command issues, with its permission node and message-key prefix. */
public enum BanKind {
    LOCAL(Perm.BAN_LOCAL, "localBan"),
    GLOBAL(Perm.BAN_GLOBAL, "globalBan"),
    TEMP(Perm.BAN_TEMP, "tempBan");

    private final Perm permission;
    private final String messagePrefix;

    BanKind(Perm permission, String messagePrefix) {
        this.permission = permission;
        this.messagePrefix = messagePrefix;
    }

    public Perm permission() {
        return permission;
    }

    /** Message-key prefix, e.g. {@code "globalBan"} → {@code globalBanSuccess}/{@code globalBanPlayer}. */
    public String messagePrefix() {
        return messagePrefix;
    }
}
