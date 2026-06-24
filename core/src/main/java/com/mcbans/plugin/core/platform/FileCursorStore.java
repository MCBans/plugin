package com.mcbans.plugin.core.platform;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Default {@link CursorStore}: a single-line flat file holding the cursor. Works on every platform
 * (each adapter passes a path inside its data folder). Reads/writes are tiny and infrequent
 * (once per applied ban-sync batch), so plain blocking IO is fine.
 */
public final class FileCursorStore implements CursorStore {

    private final Path file;
    private final PluginLogger log;

    public FileCursorStore(Path file, PluginLogger log) {
        this.file = file;
        this.log = log;
    }

    @Override
    public synchronized long load() {
        try {
            if (!Files.exists(file)) {
                return 0L;
            }
            String s = Files.readString(file, StandardCharsets.UTF_8).trim();
            return s.isEmpty() ? 0L : Long.parseLong(s);
        } catch (IOException | NumberFormatException e) {
            log.warn("Could not read ban-sync cursor from " + file + " (" + e.getMessage()
                    + "); resuming from 0.");
            return 0L;
        }
    }

    @Override
    public synchronized void save(long cursor) {
        try {
            Path parent = file.getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
            Files.writeString(file, Long.toString(cursor), StandardCharsets.UTF_8);
        } catch (IOException e) {
            log.error("Failed to persist ban-sync cursor " + cursor + " to " + file, e);
        }
    }
}
