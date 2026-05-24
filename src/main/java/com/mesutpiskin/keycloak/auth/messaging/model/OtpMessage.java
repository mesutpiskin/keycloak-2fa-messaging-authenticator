package com.mesutpiskin.keycloak.auth.messaging.model;

import java.util.Objects;

public final class OtpMessage {

    private final String to;
    private final String code;
    private final int ttlSeconds;
    private final String locale;

    private OtpMessage(Builder b) {
        this.to = Objects.requireNonNull(b.to, "recipient ('to') cannot be null");
        this.code = Objects.requireNonNull(b.code, "code cannot be null");
        if (b.ttlSeconds <= 0) throw new IllegalArgumentException("ttlSeconds must be positive");
        this.ttlSeconds = b.ttlSeconds;
        this.locale = (b.locale == null || b.locale.isBlank()) ? "en" : b.locale;
    }

    public String getTo() { return to; }
    public String getCode() { return code; }
    public int getTtlSeconds() { return ttlSeconds; }
    public String getLocale() { return locale; }

    public static Builder builder() { return new Builder(); }

    public static final class Builder {
        private String to;
        private String code;
        private int ttlSeconds;
        private String locale;

        private Builder() {}

        public Builder to(String to) { this.to = to; return this; }
        public Builder code(String code) { this.code = code; return this; }
        public Builder ttlSeconds(int ttlSeconds) { this.ttlSeconds = ttlSeconds; return this; }
        public Builder locale(String locale) { this.locale = locale; return this; }
        public OtpMessage build() { return new OtpMessage(this); }
    }

    @Override
    public String toString() {
        return "OtpMessage{to='" + to + "', code=*****, ttlSeconds=" + ttlSeconds + ", locale='" + locale + "'}";
    }
}
