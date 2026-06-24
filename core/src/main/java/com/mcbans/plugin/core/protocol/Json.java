package com.mcbans.plugin.core.protocol;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.util.Map;

/** Tiny JSON facade over Gson so the rest of core never touches Gson types directly. */
public final class Json {

    private static final Gson GSON = new Gson();

    private Json() {
    }

    public static JsonObject parse(String text) {
        JsonElement el = JsonParser.parseString(text);
        return el.isJsonObject() ? el.getAsJsonObject() : new JsonObject();
    }

    public static String write(JsonObject o) {
        return GSON.toJson(o);
    }

    /** Build a flat object from key/value pairs; null/blank values are skipped. */
    public static JsonObject obj(Map<String, ?> fields) {
        JsonObject o = new JsonObject();
        for (Map.Entry<String, ?> e : fields.entrySet()) {
            Object v = e.getValue();
            if (v == null) {
                continue;
            }
            if (v instanceof Number n) {
                o.addProperty(e.getKey(), n);
            } else if (v instanceof Boolean b) {
                o.addProperty(e.getKey(), b);
            } else {
                String s = v.toString();
                if (!s.isEmpty()) {
                    o.addProperty(e.getKey(), s);
                }
            }
        }
        return o;
    }

    public static String str(JsonObject o, String key) {
        JsonElement e = o == null ? null : o.get(key);
        return e == null || e.isJsonNull() ? "" : e.getAsString();
    }

    public static boolean bool(JsonObject o, String key) {
        JsonElement e = o == null ? null : o.get(key);
        return e != null && !e.isJsonNull() && e.getAsBoolean();
    }
}
