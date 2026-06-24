package com.mcbans.plugin.core;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

/**
 * A flat-file ({@code .properties}) config loader shared by the adapters that don't have a native
 * config system as convenient as Bukkit's YAML (Sponge, Forge, BungeeCord). On first run it writes a
 * commented default file next to the plugin and returns a {@link McBansConfig} built from it.
 */
public final class PropertiesConfig {

    private PropertiesConfig() {
    }

    /** Load (creating defaults if absent) and build a {@link McBansConfig}. */
    public static McBansConfig loadOrCreate(Path file) throws IOException {
        Properties p = new Properties();
        if (!Files.exists(file)) {
            writeDefault(file);
        }
        try (InputStream in = Files.newInputStream(file)) {
            p.load(in);
        }
        return McBansConfig.builder()
                .host(p.getProperty("host", "www.mcbans.com").trim())
                .apiKey(p.getProperty("api-key", "").trim())
                .version(parseInt(p.getProperty("protocol-version"), 3))
                .tls(Boolean.parseBoolean(p.getProperty("tls", "true")))
                .kickMessage(p.getProperty("kick-message",
                        "You are banned from this server.\nReason: {reason}\nAppeal at www.mcbans.com"))
                .failOpen(Boolean.parseBoolean(p.getProperty("fail-open", "true")))
                .denyOnBannedStatus(Boolean.parseBoolean(p.getProperty("deny-on-banned-status", "false")))
                .loginTimeoutMs(parseLong(p.getProperty("login-timeout-ms"), 5000))
                .build();
    }

    private static void writeDefault(Path file) throws IOException {
        Path parent = file.getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }
        try (OutputStream out = Files.newOutputStream(file)) {
            String text = """
                    # MCBans plugin configuration.
                    # Your MCBans server API key (servers.server_api). REQUIRED.
                    api-key=
                    # MCBans host; the plugin connects to wss://<host>/plugin/ws
                    host=www.mcbans.com
                    # Protocol version: 3 (recommended) or 2
                    protocol-version=3
                    # Use TLS (wss://). false only for local ws:// dev.
                    tls=true
                    # Shown to denied players; {reason} is substituted.
                    kick-message=You are banned from this server. Reason: {reason}. Appeal at www.mcbans.com
                    # On login-check timeout/error: true = allow in, false = deny.
                    fail-open=true
                    # Whether soft 'b' (banned elsewhere / no account) also denies login.
                    deny-on-banned-status=false
                    # Max ms to wait for the on-join ban check.
                    login-timeout-ms=5000
                    """;
            out.write(text.getBytes(java.nio.charset.StandardCharsets.UTF_8));
        }
    }

    private static int parseInt(String s, int def) {
        try {
            return s == null ? def : Integer.parseInt(s.trim());
        } catch (NumberFormatException e) {
            return def;
        }
    }

    private static long parseLong(String s, long def) {
        try {
            return s == null ? def : Long.parseLong(s.trim());
        } catch (NumberFormatException e) {
            return def;
        }
    }
}
