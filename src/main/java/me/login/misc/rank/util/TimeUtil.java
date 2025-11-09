package me.login.misc.rank.util;

import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class TimeUtil {

    private static final Pattern DURATION_PATTERN = Pattern.compile("(\\d+)([smhd])");

    /**
     * Parses a duration string (e.g., "30d", "1h", "permanent") into milliseconds.
     * @param input The duration string.
     * @return Duration in milliseconds, or -1 for permanent.
     * @throws IllegalArgumentException if the format is invalid.
     */
    public static long parseDuration(String input) throws IllegalArgumentException {
        input = input.toLowerCase().trim();
        if (input.equals("permanent") || input.equals("perm")) {
            return -1;
        }

        Matcher matcher = DURATION_PATTERN.matcher(input);
        if (!matcher.matches()) {
            throw new IllegalArgumentException("Invalid format. Use: 1h, 30d, 10s, or 'permanent'");
        }

        long value = Long.parseLong(matcher.group(1));
        String unit = matcher.group(2);

        return switch (unit) {
            case "s" -> TimeUnit.SECONDS.toMillis(value);
            case "m" -> TimeUnit.MINUTES.toMillis(value);
            case "h" -> TimeUnit.HOURS.toMillis(value);
            case "d" -> TimeUnit.DAYS.toMillis(value);
            default -> throw new IllegalArgumentException("Unknown time unit: " + unit);
        };
    }

    /**
     * Formats a duration in milliseconds into a readable string (e.g., "1d 2h 30m").
     * @param millis The duration in milliseconds.
     * @return A formatted string.
     */
    public static String formatDuration(long millis) {
        if (millis < 0) {
            return "Permanent";
        }
        if (millis == 0) {
            return "0s";
        }

        long days = TimeUnit.MILLISECONDS.toDays(millis);
        millis -= TimeUnit.DAYS.toMillis(days);
        long hours = TimeUnit.MILLISECONDS.toHours(millis);
        millis -= TimeUnit.HOURS.toMillis(hours);
        long minutes = TimeUnit.MILLISECONDS.toMinutes(millis);
        millis -= TimeUnit.MINUTES.toMillis(minutes);
        long seconds = TimeUnit.MILLISECONDS.toSeconds(millis);

        StringBuilder sb = new StringBuilder();
        if (days > 0) sb.append(days).append("d ");
        if (hours > 0) sb.append(hours).append("h ");
        if (minutes > 0) sb.append(minutes).append("m ");
        if (seconds > 0 || sb.length() == 0) sb.append(seconds).append("s");

        return sb.toString().trim();
    }
}