package com.mcbans.plugin.bukkit;

import com.mcbans.plugin.core.platform.PluginLogger;

import java.util.logging.Level;
import java.util.logging.Logger;

/** Bridges the core {@link PluginLogger} onto Bukkit's java.util.logging logger. */
final class BukkitLogger implements PluginLogger {

    private final Logger logger;

    BukkitLogger(Logger logger) {
        this.logger = logger;
    }

    @Override
    public void info(String message) {
        logger.info(message);
    }

    @Override
    public void warn(String message) {
        logger.warning(message);
    }

    @Override
    public void error(String message) {
        logger.severe(message);
    }

    @Override
    public void error(String message, Throwable t) {
        logger.log(Level.SEVERE, message, t);
    }
}
