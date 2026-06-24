package com.mcbans.plugin.forge;

import java.util.UUID;

/** Parses an undashed or dashed UUID string. */
final class UuidUtil {

    private UuidUtil() {
    }

    static UUID parse(String raw) {
        String s = raw.replace("-", "");
        return UUID.fromString(s.replaceFirst(
                "(\\p{XDigit}{8})(\\p{XDigit}{4})(\\p{XDigit}{4})(\\p{XDigit}{4})(\\p{XDigit}{12})",
                "$1-$2-$3-$4-$5"));
    }
}
