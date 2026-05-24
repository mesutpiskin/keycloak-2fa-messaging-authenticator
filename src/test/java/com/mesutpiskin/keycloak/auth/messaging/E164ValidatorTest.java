package com.mesutpiskin.keycloak.auth.messaging;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

@DisplayName("E164Validator Tests")
class E164ValidatorTest {

    @Test @DisplayName("Valid E.164 numbers accepted")
    void valid() {
        assertTrue(E164Validator.isValid("+905001234567"));
        assertTrue(E164Validator.isValid("+15551234567"));
        assertTrue(E164Validator.isValid("+442071234567"));
    }

    @Test @DisplayName("Missing + rejected")
    void missingPlus() { assertFalse(E164Validator.isValid("905001234567")); }

    @Test @DisplayName("Leading zero after + rejected")
    void leadingZero() { assertFalse(E164Validator.isValid("+0905001234567")); }

    @Test @DisplayName("Too short rejected")
    void tooShort() { assertFalse(E164Validator.isValid("+12345")); }

    @Test @DisplayName("Too long rejected")
    void tooLong() { assertFalse(E164Validator.isValid("+1234567890123456")); }

    @Test @DisplayName("Non-digit characters rejected")
    void nonDigit() {
        assertFalse(E164Validator.isValid("+90 500 123 4567"));
        assertFalse(E164Validator.isValid("+1-555-123-4567"));
        assertFalse(E164Validator.isValid("+155abc1234"));
    }

    @Test @DisplayName("Null/blank rejected")
    void nullBlank() {
        assertFalse(E164Validator.isValid(null));
        assertFalse(E164Validator.isValid(""));
        assertFalse(E164Validator.isValid("  "));
    }

    @Test @DisplayName("normalize strips whitespace and dashes; preserves +")
    void normalize() {
        assertEquals("+905001234567", E164Validator.normalize(" +90 500 123 45 67 "));
        assertEquals("+15551234567", E164Validator.normalize("+1-555-123-4567"));
    }
}
