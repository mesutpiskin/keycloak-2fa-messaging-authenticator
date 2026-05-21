package com.mesutpiskin.keycloak.auth.messaging;

import java.util.regex.Pattern;

public final class E164Validator {
    private static final Pattern E164 = Pattern.compile("^\\+[1-9]\\d{6,14}$");

    private E164Validator() {
        throw new UnsupportedOperationException("E164Validator is a utility class and cannot be instantiated");
    }

    public static boolean isValid(String value) {
        if (value == null || value.isBlank()) return false;
        return E164.matcher(value.trim()).matches();
    }

    public static String normalize(String value) {
        if (value == null) return null;
        return value.replaceAll("[\\s-()]", "").trim();
    }
}
