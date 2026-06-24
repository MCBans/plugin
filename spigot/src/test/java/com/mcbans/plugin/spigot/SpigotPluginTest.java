package com.mcbans.plugin.spigot;

import be.seeseemelk.mockbukkit.MockBukkit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The Spigot adapter is the Bukkit adapter under a Spigot-branded main class + plugin.yml. This
 * confirms it loads/enables and registers its commands on a (mock) Spigot server. The behavioural
 * command/permission tests live in the Bukkit module since the logic is shared.
 */
class SpigotPluginTest {

    private McBansSpigotPlugin plugin;

    @BeforeEach
    void setUp() {
        MockBukkit.mock();
        plugin = MockBukkit.load(McBansSpigotPlugin.class);
        plugin.getConfig().set("apiKey", "unit-test-key");
        plugin.getConfig().set("host", "127.0.0.1:1");
        plugin.getConfig().set("tls", false);
        plugin.onEnable();
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    @Test
    void enablesAndRegistersCommands() {
        assertTrue(plugin.isEnabled());
        assertNotNull(plugin.getCommand("ban"));
        assertNotNull(plugin.getCommand("ban").getExecutor());
        assertNotNull(plugin.getCommand("globalban"));
        assertNotNull(plugin.getCommand("mcbs"));
    }
}
