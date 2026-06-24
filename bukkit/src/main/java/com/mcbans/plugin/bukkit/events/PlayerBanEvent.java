package com.mcbans.plugin.bukkit.events;

import com.mcbans.plugin.core.command.BanKind;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;

/** Fired after MCBans bans a player (local/global/temp). Other plugins may listen. */
public final class PlayerBanEvent extends Event {

    private static final HandlerList HANDLERS = new HandlerList();

    private final String target;
    private final String admin;
    private final String reason;
    private final BanKind kind;

    public PlayerBanEvent(String target, String admin, String reason, BanKind kind) {
        this.target = target;
        this.admin = admin;
        this.reason = reason;
        this.kind = kind;
    }

    public String getTarget() {
        return target;
    }

    public String getAdmin() {
        return admin;
    }

    public String getReason() {
        return reason;
    }

    public BanKind getKind() {
        return kind;
    }

    @Override
    public HandlerList getHandlers() {
        return HANDLERS;
    }

    public static HandlerList getHandlerList() {
        return HANDLERS;
    }
}
