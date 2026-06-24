package com.mcbans.plugin.core.model;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LoginResultTest {

    @Test
    void parsesLegacyGlobalBanTuple() {
        // status;reason;rep;altCount;mcbansmod;rr;altList;bformat
        LoginResult r = LoginResult.fromLegacy("g;x-ray cheating;3;2;y;;;");
        assertEquals(BanStatus.GLOBAL, r.status());
        assertEquals("x-ray cheating", r.reason());
        assertEquals(3, r.reputation());
        assertEquals(2, r.altCount());
        assertTrue(r.isMcBansStaff());
        assertTrue(r.shouldDeny());
    }

    @Test
    void parsesLegacyCleanTuple() {
        LoginResult r = LoginResult.fromLegacy("n");
        assertEquals(BanStatus.CLEAN, r.status());
        assertFalse(r.shouldDeny());
    }

    @Test
    void unknownStatusDoesNotDeny() {
        assertEquals(BanStatus.UNKNOWN, BanStatus.fromCode(""));
        assertFalse(BanStatus.UNKNOWN.isHardBan());
    }

    @Test
    void hardBanStatuses() {
        assertTrue(BanStatus.GLOBAL.isHardBan());
        assertTrue(BanStatus.LOCAL.isHardBan());
        assertTrue(BanStatus.TEMP.isHardBan());
        assertTrue(BanStatus.IP_LOCK.isHardBan());
        assertFalse(BanStatus.BANNED.isHardBan());
        assertFalse(BanStatus.CLEAN.isHardBan());
    }
}
