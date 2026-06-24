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
