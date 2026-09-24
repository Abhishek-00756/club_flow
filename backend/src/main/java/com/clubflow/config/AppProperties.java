package com.clubflow.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app")
public record AppProperties(
        String frontendUrl,
        String corsAllowedOrigins,
        String timezone,
        Jwt jwt,
        Mail mail,
        Reminders reminders,
        Storage storage,
        Seed seed,
        RateLimit rateLimit) {

    public record Jwt(String secret, long accessTokenMinutes, long refreshTokenDays) {}

    public record Mail(boolean enabled, String from, long dispatchIntervalMs, int maxAttempts) {}

    public record Reminders(int firstHours, int secondHours, long scanIntervalMs) {}

    public record Storage(String dir) {}

    public record Seed(boolean enabled, String superadminName, String superadminEmail,
                       String superadminPassword, boolean demoData) {}

    public record RateLimit(int authPerMinute) {}
}
