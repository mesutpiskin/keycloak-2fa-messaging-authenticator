package com.mesutpiskin.keycloak.auth.messaging;

public final class ContactMasker {

    private ContactMasker() {
        throw new UnsupportedOperationException("ContactMasker is a utility class and cannot be instantiated");
    }

    public static String maskPhone(String phone) {
        if (phone == null || phone.isBlank()) return "";
        String digits = phone.replaceAll("[^\\d]", "");
        if (digits.length() < 5) return "****";
        String country = phone.startsWith("+") ? "+" + digits.substring(0, Math.min(2, digits.length() - 4)) : "";
        String start = digits.substring(country.length() == 0 ? 0 : country.length() - 1, Math.min(country.length() == 0 ? 1 : country.length(), digits.length() - 2));
        String last2 = digits.substring(digits.length() - 2);
        // Simple deterministic mask "+CC X** *** **YY"
        int middle = digits.length() - (country.length() == 0 ? 0 : country.length() - 1) - 2;
        StringBuilder masked = new StringBuilder();
        if (!country.isEmpty()) masked.append(country).append(' ');
        if (digits.length() >= 7) {
            masked.append(digits.charAt(country.length() == 0 ? 0 : country.length() - 1)).append("** *** **").append(last2);
        } else {
            masked.append("***").append(last2);
        }
        return masked.toString();
    }

    public static String maskGeneric(String value) {
        if (value == null || value.isBlank()) return "";
        if (value.length() <= 2) return "*".repeat(value.length());
        return value.charAt(0) + "*".repeat(value.length() - 1) + value.charAt(value.length() - 1);
    }
}
