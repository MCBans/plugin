package com.mcbans.plugin.core.util;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class IdentifiersTest {

    @Test
    void recognisesUuids() {
        assertTrue(Identifiers.isUuid("069a79f4-44e9-4726-a5be-fca90e38aaf5"));
        assertTrue(Identifiers.isUuid("069a79f444e94726a5befca90e38aaf5"));
        assertFalse(Identifiers.isUuid("Notch"));
        assertFalse(Identifiers.isUuid(null));
    }

    @Test
    void validatesIpv4() {
        assertTrue(Identifiers.isIpv4("1.2.3.4"));
        assertTrue(Identifiers.isIpv4("255.255.255.255"));
        assertFalse(Identifiers.isIpv4("256.1.1.1"));
        assertFalse(Identifiers.isIpv4("1.2.3"));
        assertFalse(Identifiers.isIpv4("Notch"));
    }

    @Test
    void normalisesUuid() {
        assertEquals("069a79f444e94726a5befca90e38aaf5",
                Identifiers.normalizeUuid("069A79F4-44E9-4726-A5BE-FCA90E38AAF5"));
        assertEquals("", Identifiers.normalizeUuid(null));
    }

    @Test
    void parsesDashedAndUndashed() {
        UUID expected = UUID.fromString("069a79f4-44e9-4726-a5be-fca90e38aaf5");
        assertEquals(expected, Identifiers.parseUuid("069a79f4-44e9-4726-a5be-fca90e38aaf5"));
        assertEquals(expected, Identifiers.parseUuid("069a79f444e94726a5befca90e38aaf5"));
        assertThrows(IllegalArgumentException.class, () -> Identifiers.parseUuid("nope"));
    }
}
