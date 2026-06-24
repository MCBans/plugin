package com.mcbans.plugin.forge;

import com.mcbans.plugin.core.platform.PluginLogger;
import org.slf4j.Logger;

/** Bridges the core {@link PluginLogger} onto Forge's SLF4J logger. */
final class ForgeLogger implements PluginLogger {

    private final Logger logger;

    ForgeLogger(Logger logger) {
        this.logger = logger;
    }

    @Override
    public void info(String message) {
        logger.info(message);
    }

    @Override
    public void warn(String message) {
        logger.warn(message);
    }

    @Override
    public void error(String message) {
        logger.error(message);
    }

    @Override
    public void error(String message, Throwable t) {
        logger.error(message, t);
    }
}
