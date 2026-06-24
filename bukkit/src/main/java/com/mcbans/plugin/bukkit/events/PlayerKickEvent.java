package com.mcbans.plugin.bukkit.events;

import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;

/** Fired after MCBans kicks a player. */
public final class PlayerKickEvent extends Event {

    private static final HandlerList HANDLERS = new HandlerList();

    private final String target;
    private final String admin;
    private final String reason;

    public PlayerKickEvent(String target, String admin, String reason) {
        this.target = target;
        this.admin = admin;
        this.reason = reason;
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

    @Override
    public HandlerList getHandlers() {
        return HANDLERS;
    }

    public static HandlerList getHandlerList() {
        return HANDLERS;
    }
}
