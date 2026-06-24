package com.mcbans.plugin.bukkit.events;

import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;

/** Fired after MCBans unbans a player or IP. */
public final class PlayerUnbanEvent extends Event {

    private static final HandlerList HANDLERS = new HandlerList();

    private final String target;
    private final String admin;

    public PlayerUnbanEvent(String target, String admin) {
        this.target = target;
        this.admin = admin;
    }

    public String getTarget() {
        return target;
    }

    public String getAdmin() {
        return admin;
    }

    @Override
    public HandlerList getHandlers() {
        return HANDLERS;
    }

    public static HandlerList getHandlerList() {
        return HANDLERS;
    }
}
