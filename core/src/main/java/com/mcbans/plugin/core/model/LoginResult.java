package com.mcbans.plugin.core.model;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Parsed result of a {@code login} / {@code loginNew} ban check.
 *
 * <p>{@code loginNew} (v3) returns a rich JSON object; the legacy {@code login} (v2 / client
 * {@code 4.1}) returns a {@code ;}-delimited tuple
 * {@code status;reason;rep;altCount;mcbansmod;rr;altList;bformat}. This type normalises both so
 * platform code never branches on the version. The v3 path additionally exposes the player's ban
 * history, alt list, recent name changes, premium flag and connect message for the on-join
 * notifications.
 */
public final class LoginResult {

    private final BanStatus status;
    private final String reason;
    private final int reputation;
    private final int altCount;
    private final boolean mcbansStaff;
    private final String altList;
    private final String nameChanges;
    private final String connectMessage;
    private final boolean premium;
    private final List<BanRecord> bans;
    private final String banId;

    private LoginResult(Builder b) {
        this.status = b.status;
        this.reason = b.reason;
        this.reputation = b.reputation;
        this.altCount = b.altCount;
        this.mcbansStaff = b.mcbansStaff;
        this.altList = b.altList;
        this.nameChanges = b.nameChanges;
        this.connectMessage = b.connectMessage;
        this.premium = b.premium;
        this.bans = b.bans;
        this.banId = b.banId;
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

    /** Comma-separated alt account names (premium servers); empty otherwise. */
    public String altList() {
        return altList;
    }

    /** Comma-separated recent name changes; empty when none/unavailable. */
    public String nameChanges() {
        return nameChanges;
    }

    public String connectMessage() {
        return connectMessage;
    }

    public boolean premium() {
        return premium;
    }

    /** The player's ban history (v3 only); empty list otherwise. */
    public List<BanRecord> bans() {
        return bans;
    }

    /** The active ban's id, for the {@code banReturnMessage} appeal link. */
    public String banId() {
        return banId;
    }

    /** Convenience: should this player be kicked under the strict default policy? */
    public boolean shouldDeny() {
        return status.isHardBan();
    }

    public static Builder builder() {
        return new Builder();
    }

    /** Parse a v3 {@code loginNew} JSON object. */
    public static LoginResult fromJson(JsonObject o) {
        Builder b = builder()
                .status(BanStatus.fromCode(asString(o, "banStatus")))
                .reason(asString(o, "banReason"))
                .reputation(asInt(o, "playerRep", 0))
                .altCount(asInt(o, "altCount", 0))
                .mcbansStaff("y".equalsIgnoreCase(asString(o, "is_mcbans_mod")))
                .altList(asString(o, "altList"))
                .nameChanges(asString(o, "nameChanges"))
                .connectMessage(asString(o, "connectMessage"))
                .premium(asBool(o, "premium"))
                .banId(asString(o, "banId"));

        JsonElement bansEl = o.get("bans");
        if (bansEl != null && bansEl.isJsonArray()) {
            List<BanRecord> list = new ArrayList<>();
            for (JsonElement e : bansEl.getAsJsonArray()) {
                if (e.isJsonObject()) {
                    list.add(BanRecord.fromJson(e.getAsJsonObject()));
                }
            }
            b.bans(list);
        }
        return b.build();
    }

    /**
     * Parse a v2 / {@code 4.1} {@code ;}-delimited login tuple:
     * {@code status;reason;rep;altCount;mcbansmod;rr;altList;bformat}.
     */
    public static LoginResult fromLegacy(String tuple) {
        String[] p = tuple == null ? new String[0] : tuple.split(";", -1);
        return builder()
                .status(BanStatus.fromCode(part(p, 0)))
                .reason(part(p, 1))
                .reputation(parseInt(part(p, 2), 0))
                .altCount(parseInt(part(p, 3), 0))
                .mcbansStaff("y".equalsIgnoreCase(part(p, 4)))
                .altList(part(p, 6))
                .build();
    }

    public static final class Builder {
        private BanStatus status = BanStatus.UNKNOWN;
        private String reason = "";
        private int reputation;
        private int altCount;
        private boolean mcbansStaff;
        private String altList = "";
        private String nameChanges = "";
        private String connectMessage = "";
        private boolean premium;
        private List<BanRecord> bans = Collections.emptyList();
        private String banId = "";

        public Builder status(BanStatus v) { this.status = v; return this; }
        public Builder reason(String v) { this.reason = v == null ? "" : v; return this; }
        public Builder reputation(int v) { this.reputation = v; return this; }
        public Builder altCount(int v) { this.altCount = v; return this; }
        public Builder mcbansStaff(boolean v) { this.mcbansStaff = v; return this; }
        public Builder altList(String v) { this.altList = v == null ? "" : v; return this; }
        public Builder nameChanges(String v) { this.nameChanges = v == null ? "" : v; return this; }
        public Builder connectMessage(String v) { this.connectMessage = v == null ? "" : v; return this; }
        public Builder premium(boolean v) { this.premium = v; return this; }
        public Builder bans(List<BanRecord> v) { this.bans = v == null ? Collections.emptyList() : v; return this; }
        public Builder banId(String v) { this.banId = v == null ? "" : v; return this; }

        public LoginResult build() {
            return new LoginResult(this);
        }
    }

    private static String part(String[] p, int i) {
        return i < p.length ? p[i] : "";
    }

    private static String asString(JsonObject o, String k) {
        JsonElement e = o.get(k);
        return e == null || e.isJsonNull() ? "" : e.getAsString();
    }

    private static boolean asBool(JsonObject o, String k) {
        JsonElement e = o.get(k);
        if (e == null || e.isJsonNull()) {
            return false;
        }
        try {
            if (e.getAsJsonPrimitive().isBoolean()) {
                return e.getAsBoolean();
            }
        } catch (RuntimeException ignored) {
            // fall through to string check
        }
        String s = e.getAsString();
        return "y".equalsIgnoreCase(s) || "true".equalsIgnoreCase(s) || "1".equals(s);
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
                + ", staff=" + mcbansStaff + ", bans=" + bans.size() + '}';
    }
}
