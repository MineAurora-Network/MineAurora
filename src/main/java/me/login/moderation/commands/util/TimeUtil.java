package me.login.moderation.commands.util;

import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class TimeUtil {

    private static final Pattern TIME_PATTERN = Pattern.compile("(\\d+)([smh])");

    /**
     * Parses a time string like "1h30m10s" into game ticks.
     * @param timeString The string to parse.
     * @return The total number of ticks.
     * @throws IllegalArgumentException If the format is invalid.
     */
    public static long parseTime(String timeString) throws IllegalArgumentException {
        long totalSeconds = 0;
        Matcher matcher = TIME_PATTERN.matcher(timeString.toLowerCase());
        boolean found = false;

        while (matcher.find()) {
            found = true;
            int value = Integer.parseInt(matcher.group(1));
            String unit = matcher.group(2);

            switch (unit) {
                case "s":
                    totalSeconds += value;
                    break;
                case "m":
                    totalSeconds += value * 60;
                    break;
                case "h":
                    totalSeconds += value * 3600;
                    break;
            }
        }

        if (!found) {
            throw new IllegalArgumentException("Invalid time format. Use 1h, 30m, 10s.");
        }

        return totalSeconds * 20; // Convert seconds to ticks
    }

    /**
     * Formats a duration in seconds into a human-readable string like "1h 30m 10s".
     * @param totalSeconds The total seconds.
     * @return A formatted string.
     */
    public static String formatTime(long totalSeconds) {
        if (totalSeconds < 0) {
            return "0s";
        }

        long hours = TimeUnit.SECONDS.toHours(totalSeconds);
        long minutes = TimeUnit.SECONDS.toMinutes(totalSeconds) % 60;
        long seconds = totalSeconds % 60;

        StringBuilder sb = new StringBuilder();
        if (hours > 0) {
            sb.append(hours).append("h ");
        }
        if (minutes > 0) {
            sb.append(minutes).append("m ");
        }
        if (seconds > 0 || sb.length() == 0) {
            sb.append(seconds).append("s");
        }

        return sb.toString().trim();
    }
}