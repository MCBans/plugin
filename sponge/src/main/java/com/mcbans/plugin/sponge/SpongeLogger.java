package com.mcbans.plugin.sponge;

import com.mcbans.plugin.core.platform.PluginLogger;
import org.apache.logging.log4j.Logger;

/** Bridges the core {@link PluginLogger} onto Sponge's Log4j logger. */
final class SpongeLogger implements PluginLogger {

    private final Logger logger;

    SpongeLogger(Logger logger) {
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
