package com.clubflow.common;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

public final class Formats {
    private static final DateTimeFormatter DEADLINE =
            DateTimeFormatter.ofPattern("d MMM yyyy, h:mm a", Locale.ENGLISH);

    private Formats() {}

    public static String dateTime(Instant instant, String zone) {
        if (instant == null) {
            return "";
        }
        return DEADLINE.format(instant.atZone(ZoneId.of(zone)));
    }
}
