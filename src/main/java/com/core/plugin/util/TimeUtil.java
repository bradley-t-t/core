package com.core.plugin.util;

import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Parses human-readable duration strings (e.g., "1d12h30m") and formats
 * millisecond durations into readable text.
 */
public final class TimeUtil {

    private static final Pattern DURATION_PATTERN = Pattern.compile(
            "(?:(\\d+)d)?(?:(\\d+)h)?(?:(\\d+)m)?(?:(\\d+)s)?",
            Pattern.CASE_INSENSITIVE
    );

    private TimeUtil() {}

    /**
     * Parse a duration string like "1d12h30m" or "30m" into milliseconds.
     * Returns -1 if the string is unparseable.
     */
    public static long parseDuration(String input) {
        Matcher matcher = DURATION_PATTERN.matcher(input.trim());
        if (!matcher.matches()) return -1;

        long total = 0;
        if (matcher.group(1) != null) total += TimeUnit.DAYS.toMillis(Long.parseLong(matcher.group(1)));
        if (matcher.group(2) != null) total += TimeUnit.HOURS.toMillis(Long.parseLong(matcher.group(2)));
        if (matcher.group(3) != null) total += TimeUnit.MINUTES.toMillis(Long.parseLong(matcher.group(3)));
        if (matcher.group(4) != null) total += TimeUnit.SECONDS.toMillis(Long.parseLong(matcher.group(4)));

        return total > 0 ? total : -1;
    }

    /** Format a millisecond duration into a human-readable string like "1d 2h 30m". */
    public static String formatDuration(long millis) {
        if (millis <= 0) return "0s";

        long days = TimeUnit.MILLISECONDS.toDays(millis);
        millis -= TimeUnit.DAYS.toMillis(days);
        long hours = TimeUnit.MILLISECONDS.toHours(millis);
        millis -= TimeUnit.HOURS.toMillis(hours);
        long minutes = TimeUnit.MILLISECONDS.toMinutes(millis);
        millis -= TimeUnit.MINUTES.toMillis(minutes);
        long seconds = TimeUnit.MILLISECONDS.toSeconds(millis);

        var sb = new StringBuilder();
        if (days > 0) sb.append(days).append("d ");
        if (hours > 0) sb.append(hours).append("h ");
        if (minutes > 0) sb.append(minutes).append("m ");
        if (seconds > 0 || sb.isEmpty()) sb.append(seconds).append("s");
        return sb.toString().trim();
    }

    /** Format a unix timestamp into a relative "X ago" string. */
    public static String formatRelative(long timestampMillis) {
        long elapsed = System.currentTimeMillis() - timestampMillis;
        if (elapsed < 0) return "just now";
        return formatDuration(elapsed) + " ago";
    }
}
