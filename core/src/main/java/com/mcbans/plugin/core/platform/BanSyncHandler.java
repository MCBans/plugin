package com.mcbans.plugin.core.platform;

import com.mcbans.plugin.core.model.BanSyncAction;
import com.mcbans.plugin.core.model.Notice;

/**
 * Platform hook for real-time propagation. The core invokes these when the server pushes a
 * {@code banSync} or {@code notice} frame (and on explicit catch-up). Implementations live in each
 * adapter and must hop to the game/main thread before touching the platform API — the core calls
 * them off the network thread.
 *
 * <p>The core advances and persists the {@link CursorStore} after a batch; an implementation only
 * has to enforce/lift the action (e.g. kick an online banned player, drop them from a local cache).
 */
public interface BanSyncHandler {

    /** Apply a {@code ban} action: if the named/uuid player is online, remove them. */
    void enforce(BanSyncAction action);

    /** Apply an {@code unban} action: clear any local block for the player. */
    void lift(BanSyncAction action);

    /** Surface a server notice (log it, message staff, ...). */
    void onNotice(Notice notice);

    /** A no-op handler for adapters (e.g. login-only proxies) that don't apply pushed bans. */
    BanSyncHandler NOOP = new BanSyncHandler() {
        @Override public void enforce(BanSyncAction action) { }
        @Override public void lift(BanSyncAction action) { }
        @Override public void onNotice(Notice notice) { }
    };
}
