package com.mcbans.plugin.core.model;

import com.google.gson.JsonObject;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class VerifyResultTest {

    private static JsonObject reply(boolean ok, Object data) {
        JsonObject o = new JsonObject();
        o.addProperty("ok", ok);
        if (data instanceof String s) {
            o.addProperty("data", s);
        } else if (data instanceof JsonObject j) {
            o.add("data", j);
        }
        return o;
    }

    @Test
    void parsesV3SuccessString() {
        VerifyResult r = VerifyResult.fromReply(
                reply(true, "y;Alice;Registration Complete. Thank you for registering with MCBans!"));
        assertEquals(VerifyResult.State.SUCCESS, r.state());
        assertEquals("Alice", r.name());
    }

    @Test
    void parsesV3AlreadyAndInvalid() {
        assertEquals(VerifyResult.State.ALREADY,
                VerifyResult.fromReply(reply(true, "a;This Minecraft account has already been linked!")).state());
        assertEquals(VerifyResult.State.INVALID,
                VerifyResult.fromReply(reply(true, "n;Invalid code!")).state());
    }

    @Test
    void parsesV2JsonObject() {
        JsonObject data = new JsonObject();
        data.addProperty("state", "y");
        data.addProperty("name", "Bob");
        VerifyResult r = VerifyResult.fromReply(reply(true, data));
        assertEquals(VerifyResult.State.SUCCESS, r.state());
        assertEquals("Bob", r.name());
    }

    @Test
    void notOkMapsToBlocked() {
        JsonObject o = new JsonObject();
        o.addProperty("ok", false);
        o.addProperty("error", "v3: Protected function. Action blocked.");
        assertEquals(VerifyResult.State.BLOCKED, VerifyResult.fromReply(o).state());
    }
}
