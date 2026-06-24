package com.mcbans.plugin.core.platform;

/**
 * Durable storage for the ban-sync cursor (the highest {@code lastid} the plugin has applied).
 *
 * <p>Persisting this is the one thing a client must get right: it is fed back as
 * {@code lastBanSyncId} on every {@code register} so the server replays only what was missed.
 * {@link FileCursorStore} is the default flat-file implementation; a platform may supply its own
 * (e.g. backed by its config system or a database).
 */
public interface CursorStore {

    /** @return the last persisted cursor, or {@code 0} if none has been stored yet. */
    long load();

    /** Persist the cursor durably. Called after each applied ban-sync batch. */
    void save(long cursor);
}
