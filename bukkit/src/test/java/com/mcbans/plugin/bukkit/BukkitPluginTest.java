package com.mcbans.plugin.bukkit;

import be.seeseemelk.mockbukkit.MockBukkit;
import be.seeseemelk.mockbukkit.ServerMock;
import be.seeseemelk.mockbukkit.entity.PlayerMock;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * In-JVM tests for the Bukkit adapter using MockBukkit (a mock Bukkit server). The plugin is loaded
 * with a config pointing at an unreachable host + dummy key, so it fully enables (registering
 * commands and the listener) while the WebSocket client harmlessly retries in the background — the
 * same setup the boot smoke test uses, but entirely in-process.
 */
class BukkitPluginTest {

    private ServerMock server;
    private McBansBukkitPlugin plugin;

    @BeforeEach
    void setUp() {
        server = MockBukkit.mock();
        // First enable runs with the bundled placeholder key, so the plugin early-returns without
        // wiring anything up. Inject a test config (unreachable host + dummy key) and re-run enable:
        // now it registers commands + the listener and starts the WS client, which just retries in
        // the background. This avoids any real network while exercising the full enable path.
        plugin = MockBukkit.load(McBansBukkitPlugin.class);
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
    void pluginEnablesAndRegistersCommands() {
        assertTrue(plugin.isEnabled(), "plugin should enable with a valid api key");
        for (String cmd : new String[] {"ban", "globalban", "tempban", "banip", "kick", "unban",
                "lookup", "banlookup", "altlookup", "namelookup", "mcbans", "mcbs"}) {
            assertNotNull(plugin.getCommand(cmd), "command not registered: " + cmd);
            assertNotNull(plugin.getCommand(cmd).getExecutor(), "no executor for: " + cmd);
        }
    }

    @Test
    void banCommandDeniedWithoutPermission() {
        PlayerMock player = server.addPlayer();              // not op, no permissions
        player.performCommand("ban SomeGriefer being mean");
        // McBansCommands sends the localized "permissionDenied" message synchronously, before any
        // network call — so this is deterministic.
        assertTrue(seenMessageContaining(player, "permission"),
                "expected a permission-denied message");
    }

    @Test
    void banipRejectsInvalidIpForPermittedSender() {
        PlayerMock player = server.addPlayer();
        player.setOp(true);                                   // satisfies the op-default permissions
        player.performCommand("banip not-an-ip");
        assertTrue(seenMessageContaining(player, "Invalid IP"),
                "expected an invalid-IP message");
    }

    @Test
    void banWithNoArgsReportsFormatError() {
        PlayerMock player = server.addPlayer();
        player.setOp(true);
        player.performCommand("ban");
        assertTrue(seenMessageContaining(player, "Incorrect parameters"),
                "expected a format-error message");
    }

    private boolean seenMessageContaining(PlayerMock player, String needle) {
        String msg;
        while ((msg = player.nextMessage()) != null) {
            if (msg.toLowerCase().contains(needle.toLowerCase())) {
                return true;
            }
        }
        return false;
    }
}
