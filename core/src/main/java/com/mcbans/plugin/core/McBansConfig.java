package com.mcbans.plugin.core;

/**
 * Connection + behaviour configuration shared by every adapter. Each platform builds one of these
 * from its own config system (config.yml, mods.toml-adjacent file, Sponge config, ...) via
 * {@link Builder}.
 */
public final class McBansConfig {

    private final String host;
    private final String apiKey;
    private final int version;
    private final boolean tls;
    private final String kickMessage;
    private final boolean failOpen;
    private final boolean denyOnBannedStatus;
    private final long loginTimeoutMs;
    private final long requestTimeoutMs;
    private final int reportedClientVersion;
    private final String prefix;
    private final String language;
    private final String defaultLocalReason;
    private final String defaultTempReason;
    private final String defaultKickReason;
    private final int minReputation;
    private final boolean enableMaxAlts;
    private final int maxAlts;
    private final boolean onJoinMessage;
    private final boolean sendDetailPrevBans;

    private McBansConfig(Builder b) {
        this.host = b.host;
        this.apiKey = b.apiKey;
        this.version = b.version;
        this.tls = b.tls;
        this.kickMessage = b.kickMessage;
        this.failOpen = b.failOpen;
        this.denyOnBannedStatus = b.denyOnBannedStatus;
        this.loginTimeoutMs = b.loginTimeoutMs;
        this.requestTimeoutMs = b.requestTimeoutMs;
        this.reportedClientVersion = b.reportedClientVersion;
        this.prefix = b.prefix;
        this.language = b.language;
        this.defaultLocalReason = b.defaultLocalReason;
        this.defaultTempReason = b.defaultTempReason;
        this.defaultKickReason = b.defaultKickReason;
        this.minReputation = b.minReputation;
        this.enableMaxAlts = b.enableMaxAlts;
        this.maxAlts = b.maxAlts;
        this.onJoinMessage = b.onJoinMessage;
        this.sendDetailPrevBans = b.sendDetailPrevBans;
    }

    /** Host (and optional port/path), e.g. {@code www.mcbans.com} or {@code localhost:8080}. */
    public String host() {
        return host;
    }

    public String apiKey() {
        return apiKey;
    }

    /** Protocol version: {@code 3} (recommended) or {@code 2}. */
    public int version() {
        return version;
    }

    /** {@code wss://} when true (production), {@code ws://} for local dev. */
    public boolean tls() {
        return tls;
    }

    /** Full endpoint URI, e.g. {@code wss://www.mcbans.com/plugin/ws}. */
    public String endpoint() {
        return (tls ? "wss://" : "ws://") + host + "/plugin/ws";
    }

    /** Message shown to a denied player. {reason} is substituted with the ban reason. */
    public String kickMessage() {
        return kickMessage;
    }

    /** When a login check times out / errors: {@code true} = allow in, {@code false} = deny. */
    public boolean failOpen() {
        return failOpen;
    }

    /** Whether the soft {@code b} (banned / no account) status also denies login. */
    public boolean denyOnBannedStatus() {
        return denyOnBannedStatus;
    }

    /** Max time to wait for an on-join ban check before applying the fail-open/closed policy. */
    public long loginTimeoutMs() {
        return loginTimeoutMs;
    }

    /** Max time to wait for a generic command reply. */
    public long requestTimeoutMs() {
        return requestTimeoutMs;
    }

    /** Plugin build number reported to the server (used by {@code callBack}/version checks). */
    public int reportedClientVersion() {
        return reportedClientVersion;
    }

    /** Message prefix prepended to plugin chat output (supports {@code &} colour codes). */
    public String prefix() {
        return prefix;
    }

    /** Language pack name (e.g. {@code default}, {@code german}). */
    public String language() {
        return language;
    }

    public String defaultLocalReason() {
        return defaultLocalReason;
    }

    public String defaultTempReason() {
        return defaultTempReason;
    }

    public String defaultKickReason() {
        return defaultKickReason;
    }

    /** Minimum reputation a player may have before being denied login ({@code -1} disables). */
    public int minReputation() {
        return minReputation;
    }

    /** Whether the max-alts gate is active. */
    public boolean enableMaxAlts() {
        return enableMaxAlts;
    }

    /** Maximum alt accounts allowed before denying login (when {@link #enableMaxAlts()}). */
    public int maxAlts() {
        return maxAlts;
    }

    /** Show the "secured by MCBans" advert on join. */
    public boolean onJoinMessage() {
        return onJoinMessage;
    }

    /** Show detailed previous-ban lines (admin/reason/server) to staff on join. */
    public boolean sendDetailPrevBans() {
        return sendDetailPrevBans;
    }

    public static Builder builder() {
        return new Builder();
    }

    public static final class Builder {
        private String host = "www.mcbans.com";
        private String apiKey = "";
        private int version = 3;
        private boolean tls = true;
        private String kickMessage = "&cYou are banned from this network.\n&7{reason}";
        private boolean failOpen = true;
        private boolean denyOnBannedStatus = false;
        private long loginTimeoutMs = 5_000L;
        private long requestTimeoutMs = 10_000L;
        private int reportedClientVersion = 1;
        private String prefix = "&cMCBans &8>&r ";
        private String language = "default";
        private String defaultLocalReason = "You have been banned!";
        private String defaultTempReason = "You have been temporarily banned!";
        private String defaultKickReason = "You have been kicked!";
        private int minReputation = -1;
        private boolean enableMaxAlts = false;
        private int maxAlts = 2;
        private boolean onJoinMessage = false;
        private boolean sendDetailPrevBans = true;

        public Builder host(String v) { this.host = v; return this; }
        public Builder apiKey(String v) { this.apiKey = v; return this; }
        public Builder version(int v) { this.version = v; return this; }
        public Builder tls(boolean v) { this.tls = v; return this; }
        public Builder kickMessage(String v) { this.kickMessage = v; return this; }
        public Builder failOpen(boolean v) { this.failOpen = v; return this; }
        public Builder denyOnBannedStatus(boolean v) { this.denyOnBannedStatus = v; return this; }
        public Builder loginTimeoutMs(long v) { this.loginTimeoutMs = v; return this; }
        public Builder requestTimeoutMs(long v) { this.requestTimeoutMs = v; return this; }
        public Builder reportedClientVersion(int v) { this.reportedClientVersion = v; return this; }
        public Builder prefix(String v) { this.prefix = v; return this; }
        public Builder language(String v) { this.language = v; return this; }
        public Builder defaultLocalReason(String v) { this.defaultLocalReason = v; return this; }
        public Builder defaultTempReason(String v) { this.defaultTempReason = v; return this; }
        public Builder defaultKickReason(String v) { this.defaultKickReason = v; return this; }
        public Builder minReputation(int v) { this.minReputation = v; return this; }
        public Builder enableMaxAlts(boolean v) { this.enableMaxAlts = v; return this; }
        public Builder maxAlts(int v) { this.maxAlts = v; return this; }
        public Builder onJoinMessage(boolean v) { this.onJoinMessage = v; return this; }
        public Builder sendDetailPrevBans(boolean v) { this.sendDetailPrevBans = v; return this; }

        public McBansConfig build() {
            if (apiKey == null || apiKey.isBlank()) {
                throw new IllegalStateException("MCBans api-key is required (servers.server_api).");
            }
            if (host == null || host.isBlank()) {
                throw new IllegalStateException("MCBans host is required.");
            }
            return new McBansConfig(this);
        }
    }
}
