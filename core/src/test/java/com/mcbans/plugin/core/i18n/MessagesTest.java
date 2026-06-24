package com.mcbans.plugin.core.i18n;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MessagesTest {

    @Test
    void substitutesPlaceholdersAndTranslatesColor() {
        Messages m = new Messages("default");
        String s = m.localize("kickSuccess", Messages.PLAYER, "Notch", Messages.ADMIN, "Op", Messages.REASON, "spam");
        assertTrue(s.contains("Notch"));
        assertTrue(s.contains("Op"));
        assertTrue(s.contains("spam"));
        // & colour codes are translated to the section sign
        assertFalse(s.contains("&7"));
        assertTrue(s.indexOf('§') >= 0);
    }

    @Test
    void missingLocaleFallsBackToDefault() {
        Messages m = new Messages("does-not-exist");
        // still resolves a known key from the default pack
        assertFalse(m.localize("permissionDenied").startsWith("!"));
    }

    @Test
    void unknownKeyReturnsSentinel() {
        Messages m = new Messages("default");
        assertEquals("!no_such_key!", m.localize("no_such_key"));
    }

    @Test
    void germanPackLoads() {
        Messages m = new Messages("german");
        assertEquals("german", m.locale());
        assertFalse(m.localize("kickSuccess").startsWith("!"));
    }
}
