/*
 * Copyright (C) 2026 piko <https://github.com/crimera/piko>
 *
 * See the included NOTICE file for GPLv3 §7(b) terms that apply to this code.
 */
package app.morphe.extension.crimera.logging;

import java.util.regex.Pattern;

/**
 * Redacts credentials, payloads and URLs from diagnostics before they are stored or exported.
 * Exported logs are attached to bug reports, so anything that could identify the user or carry
 * a session must not survive formatting.
 */
public final class LogSanitizer {
    private static final String BASE_SENSITIVE_KEYS =
            "authorization|cookie|set-cookie|access_token|refresh_token|id_token|"
                    + "oauth_token|api[_-]?key|client_secret|password|secret|token|"
                    + "media[_-]?url|request[_-]?body|response[_-]?body|payload|body";
    private static final Pattern URI_PATTERN = Pattern.compile(
            "(?i)\\b(?:https?|ftp|content|file)://[^\\s\\[\\]<>\\\"']+"
    );

    private final Pattern sensitiveValuePattern;

    private LogSanitizer(Pattern sensitiveValuePattern) {
        this.sensitiveValuePattern = sensitiveValuePattern;
    }

    /** The generic key set: credentials, bodies and media URLs. */
    public static LogSanitizer standard() {
        return withExtraKeys();
    }

    /**
     * The generic key set plus app-specific keys. Each key is a case-insensitive regex fragment
     * matched as a whole word, for example {@code "bounce[_-]?deeplink"}.
     */
    public static LogSanitizer withExtraKeys(String... extraKeyPatterns) {
        StringBuilder keys = new StringBuilder(BASE_SENSITIVE_KEYS);
        for (String keyPattern : extraKeyPatterns) {
            keys.append('|').append(keyPattern);
        }
        return new LogSanitizer(Pattern.compile(
                "(?i)(\\b(?:" + keys + ")\\b\\s*[:=]\\s*)[^\\s,;)}\\]]+"
        ));
    }

    public String sanitize(String value) {
        String sanitized = sensitiveValuePattern.matcher(value).replaceAll("$1[redacted]");
        return URI_PATTERN.matcher(sanitized).replaceAll("[url redacted]");
    }
}
