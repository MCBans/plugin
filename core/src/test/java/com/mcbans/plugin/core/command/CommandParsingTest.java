package com.mcbans.plugin.core.command;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class CommandParsingTest {

    @Test
    void localBanByDefault() {
        CommandParsing.BanIntent i = CommandParsing.parseBan(List.of("griefing", "my", "base"), "default");
        assertEquals(BanKind.LOCAL, i.kind);
        assertEquals("griefing my base", i.reason);
    }

    @Test
    void globalModifier() {
        CommandParsing.BanIntent i = CommandParsing.parseBan(List.of("g", "x-ray"), "default");
        assertEquals(BanKind.GLOBAL, i.kind);
        assertEquals("x-ray", i.reason);
    }

    @Test
    void tempModifierWithCombinedDuration() {
        CommandParsing.BanIntent i = CommandParsing.parseBan(List.of("t", "15m", "cool", "down"), "default");
        assertEquals(BanKind.TEMP, i.kind);
        assertEquals("15", i.duration);
        assertEquals("m", i.measure);
        assertEquals("cool down", i.reason);
    }

    @Test
    void emptyReasonFallsBackToDefault() {
        CommandParsing.BanIntent i = CommandParsing.parseBan(List.of(), "You have been banned!");
        assertEquals(BanKind.LOCAL, i.kind);
        assertEquals("You have been banned!", i.reason);
    }

    @Test
    void durationParsing() {
        assertArrayEquals(new String[] {"15", "m"}, CommandParsing.parseDuration("15m"));
        assertArrayEquals(new String[] {"2", "w"}, CommandParsing.parseDuration("2weeks"));
        assertArrayEquals(new String[] {"30", ""}, CommandParsing.parseDuration("30"));
        assertNull(CommandParsing.parseDuration("notaduration"));
    }
}
