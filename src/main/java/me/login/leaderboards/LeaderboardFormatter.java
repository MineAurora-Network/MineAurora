package me.login.leaderboards;

import java.text.DecimalFormat;
import java.util.Map;
import java.util.NavigableMap;
import java.util.TreeMap;

public class LeaderboardFormatter {

    private static final NavigableMap<Long, String> suffixes = new TreeMap<>();

    static {
        suffixes.put(1_000L, "k");
        suffixes.put(1_000_000L, "M");
        suffixes.put(1_000_000_000L, "B");
        suffixes.put(1_000_000_000_000L, "T");
        suffixes.put(1_000_000_000_000_000L, "Q");
    }

    public static String formatSuffix(double value) {
        if (value == Long.MIN_VALUE) return formatSuffix(Long.MIN_VALUE + 1);
        if (value < 0) return "-" + formatSuffix(-value);
        if (value < 1000) return new DecimalFormat("#").format(value);

        Map.Entry<Long, String> e = suffixes.floorEntry((long) value);
        Long divideBy = e.getKey();
        String suffix = e.getValue();

        long truncated = (long) value / (divideBy / 10);
        boolean hasDecimal = truncated < 100 && (truncated / 10d) != (truncated / 10);
        return hasDecimal ? (truncated / 10d) + suffix : (truncated / 10) + suffix;
    }

    public static String formatNoDecimal(double value) {
        return new DecimalFormat("#,###").format(value);
    }
}