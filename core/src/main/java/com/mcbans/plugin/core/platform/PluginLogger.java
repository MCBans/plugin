package com.mcbans.plugin.core.platform;

/**
 * Minimal logging surface the core uses, backed by whatever the host platform provides
 * (java.util.logging on Bukkit/Bungee, Log4j on Forge, the Sponge logger, ...).
 */
public interface PluginLogger {

    void info(String message);

    void warn(String message);

    void error(String message);

    void error(String message, Throwable t);
}
