package com.mcbans.plugin.core.i18n;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Properties;

/**
 * Localised message lookup, ported from the legacy {@code I18n}. Language packs live in
 * {@code /messages/<locale>.properties} on the classpath (auto-converted from the legacy
 * {@code languages/*.yml}); {@code default} is the English fallback. Missing keys in a locale fall
 * back to {@code default}.
 *
 * <p>{@link #localize(String, Object...)} takes the message key followed by alternating
 * {@code (%TOKEN%, value)} pairs (e.g. {@code localize("kickSuccess", PLAYER, name, ADMIN, who)}),
 * substitutes them, translates {@code &} colour codes to the section sign understood by every
 * platform, and turns {@code \n} into real newlines.
 */
public final class Messages {

    // Placeholder tokens (match the legacy I18n constants and the language packs).
    public static final String PLAYER  = "%PLAYER%";
    public static final String ADMIN   = "%ADMIN%";
    public static final String ADMINS  = "%ADMINS%";
    public static final String REASON  = "%REASON%";
    public static final String BANID   = "%BANID%";
    public static final String SERVER  = "%SERVER%";
    public static final String TYPE    = "%TYPE%";
    public static final String PLAYERS = "%PLAYERS%";
    public static final String BADWORD = "%BADWORD%";
    public static final String ALTS    = "%ALTS%";
    public static final String COUNT   = "%COUNT%";
    public static final String IP      = "%IP%";
    public static final String VERSION = "%VERSION%";

    private final Properties fallback = new Properties();
    private final Properties active = new Properties();
    private final String locale;

    public Messages(String locale) {
        this.locale = locale == null || locale.isBlank() ? "default" : locale;
        load("default", fallback);
        if (!"default".equals(this.locale)) {
            load(this.locale, active);
        }
    }

    private void load(String name, Properties into) {
        String path = "/messages/" + name + ".properties";
        try (InputStream in = Messages.class.getResourceAsStream(path)) {
            if (in != null) {
                into.load(new InputStreamReader(in, StandardCharsets.UTF_8));
            }
        } catch (IOException ignored) {
            // leave empty; lookups fall back to default / the bare key
        }
    }

    /** Look up and format a message. Extra args are alternating {@code (%TOKEN%, value)} pairs. */
    public String localize(String key, Object... binds) {
        String msg = active.getProperty(key);
        if (msg == null || msg.isEmpty()) {
            msg = fallback.getProperty(key);
        }
        if (msg == null || msg.isEmpty()) {
            return "!" + key + "!";
        }
        for (int i = 0; i + 1 < binds.length; i += 2) {
            String token = String.valueOf(binds[i]);
            String value = binds[i + 1] == null ? "" : String.valueOf(binds[i + 1]);
            msg = msg.replace(token, value);
        }
        return color(msg);
    }

    public String locale() {
        return locale;
    }

    /** Translate {@code &}-style colour codes to the section sign used by Minecraft platforms. */
    public static String color(String s) {
        if (s == null) {
            return "";
        }
        char[] b = s.toCharArray();
        for (int i = 0; i < b.length - 1; i++) {
            if (b[i] == '&' && "0123456789AaBbCcDdEeFfKkLlMmNnOoRrXx".indexOf(b[i + 1]) > -1) {
                b[i] = '§';
                b[i + 1] = Character.toLowerCase(b[i + 1]);
            }
        }
        return new String(b);
    }
}
