package com.mcbans.plugin.core.model;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LoginResultTest {

    /**
     * A real {@code loginNew} JSON body sends {@code altList} / {@code nameChanges} / {@code dnsbl}
     * as JSON <b>arrays</b>. Gson's {@code getAsString()} throws on an array, so parsing must not
     * blow up — otherwise the whole login check aborts and a clean player joins with no ban gate or
     * staff notice. This guards the exact prod shape that surfaced the bug (a staff member with a
     * name history was silently not flagged after loginNew started returning JSON).
     */
    @Test
    void parsesLoginNewJsonWithArrayFields() {
        JsonObject o = JsonParser.parseString("{"
                + "\"banStatus\":\"n\",\"ban\":\"\",\"banId\":\"0\",\"banAdmin\":\"\","
                + "\"banType\":\"\",\"playerRep\":10,\"disputeCount\":\"0\","
                + "\"connectMessage\":\"\",\"bans\":[],"
                + "\"nameChanges\":[\"firestarthe\",\"\",\"timtim12355\"],"
                + "\"dnsbl\":[],\"altList\":[],\"altCount\":0,\"premium\":\"1\","
                + "\"banReason\":\"\",\"is_mcbans_mod\":\"y\"}").getAsJsonObject();

        LoginResult r = LoginResult.fromJson(o);

        assertEquals(BanStatus.CLEAN, r.status());
        assertTrue(r.isMcBansStaff());                       // is_mcbans_mod survives the parse
        assertFalse(r.shouldDeny());
        assertEquals("firestarthe, timtim12355", r.nameChanges()); // array joined, padding dropped
        assertEquals("", r.altList());                       // empty array -> empty string, no throw
    }

    @Test
    void parsesLoginNewGlobalBanJson() {
        JsonObject o = JsonParser.parseString("{"
                + "\"banStatus\":\"l\",\"banId\":\"5636665\",\"banAdmin\":\"firestarthe\","
                + "\"banType\":\"global\",\"playerRep\":7.27,\"bans\":[],"
                + "\"nameChanges\":[],\"dnsbl\":[],\"altList\":[],\"altCount\":0,"
                + "\"premium\":\"1\",\"banReason\":\"REASON: x\",\"is_mcbans_mod\":\"n\"}")
                .getAsJsonObject();

        LoginResult r = LoginResult.fromJson(o);

        assertEquals(BanStatus.LOCAL, r.status());           // deny-action code l
        assertTrue(r.shouldDeny());
        assertEquals("global", r.banType());                 // real type for display -> GLOBAL
        assertEquals("firestarthe", r.banAdmin());
    }

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
