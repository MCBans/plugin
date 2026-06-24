package com.mcbans.plugin.core.model;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

/**
 * Parsed result of a {@code login} / {@code loginNew} ban check.
 *
 * <p>{@code loginNew} (v3) returns a JSON object; the legacy {@code login} (v2 / client {@code 4.1})
 * returns a {@code ;}-delimited tuple {@code status;reason;rep;altCount;mcbansmod;rr;altList;bformat}.
 * This type normalises both into the same shape so platform code never branches on the version.
 */
public final class LoginResult {

    private final BanStatus status;
    private final String reason;
    private final int reputation;
    private final int altCount;
    private final boolean mcbansStaff;

    private LoginResult(BanStatus status, String reason, int reputation, int altCount, boolean mcbansStaff) {
        this.status = status;
        this.reason = reason;
        this.reputation = reputation;
        this.altCount = altCount;
        this.mcbansStaff = mcbansStaff;
    }

    public BanStatus status() {
        return status;
    }

    /** Human-readable ban reason (empty when not banned). */
    public String reason() {
        return reason;
    }

    public int reputation() {
        return reputation;
    }

    public int altCount() {
        return altCount;
    }

    public boolean isMcBansStaff() {
        return mcbansStaff;
    }

    /** Convenience: should this player be kicked under the strict default policy? */
    public boolean shouldDeny() {
        return status.isHardBan();
    }

    /** Parse a v3 {@code loginNew} JSON object. */
    public static LoginResult fromJson(JsonObject o) {
        BanStatus status = BanStatus.fromCode(asString(o, "banStatus"));
        String reason = asString(o, "banReason");
        int rep = asInt(o, "playerRep", 0);
        int altCount = asInt(o, "altCount", 0);
        boolean staff = "y".equalsIgnoreCase(asString(o, "is_mcbans_mod"));
        return new LoginResult(status, reason, rep, altCount, staff);
    }

    /**
     * Parse a v2 / {@code 4.1} {@code ;}-delimited login tuple:
     * {@code status;reason;rep;altCount;mcbansmod;rr;altList;bformat}.
     */
    public static LoginResult fromLegacy(String tuple) {
        String[] p = tuple == null ? new String[0] : tuple.split(";", -1);
        BanStatus status = BanStatus.fromCode(part(p, 0));
        String reason = part(p, 1);
        int rep = parseInt(part(p, 2), 0);
        int altCount = parseInt(part(p, 3), 0);
        boolean staff = "y".equalsIgnoreCase(part(p, 4));
        return new LoginResult(status, reason, rep, altCount, staff);
    }

    private static String part(String[] p, int i) {
        return i < p.length ? p[i] : "";
    }

    private static String asString(JsonObject o, String k) {
        JsonElement e = o.get(k);
        return e == null || e.isJsonNull() ? "" : e.getAsString();
    }

    private static int asInt(JsonObject o, String k, int def) {
        JsonElement e = o.get(k);
        if (e == null || e.isJsonNull()) {
            return def;
        }
        try {
            return e.getAsInt();
        } catch (NumberFormatException ex) {
            return parseInt(e.getAsString(), def);
        }
    }

    private static int parseInt(String s, int def) {
        try {
            return s == null || s.isEmpty() ? def : Integer.parseInt(s.trim());
        } catch (NumberFormatException ex) {
            return def;
        }
    }

    @Override
    public String toString() {
        return "LoginResult{status=" + status + ", rep=" + reputation + ", altCount=" + altCount
                + ", staff=" + mcbansStaff + (reason.isEmpty() ? "" : ", reason='" + reason + '\'') + '}';
    }
}
