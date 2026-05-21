package com.mesutpiskin.keycloak.auth.messaging;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

@DisplayName("OtpHashUtils Tests")
class OtpHashUtilsTest {

    @Test
    @DisplayName("Same input always produces the same hash")
    void deterministic() { assertEquals(OtpHashUtils.hash("123456"), OtpHashUtils.hash("123456")); }

    @Test
    @DisplayName("Different inputs produce different hashes")
    void collisionResistance() { assertNotEquals(OtpHashUtils.hash("123456"), OtpHashUtils.hash("654321")); }

    @Test
    @DisplayName("Hash never equal to the raw code")
    void noLeak() { assertNotEquals("123456", OtpHashUtils.hash("123456")); }

    @Test
    @DisplayName("matches() true for correct code")
    void matchesOk() { assertTrue(OtpHashUtils.matches("123456", OtpHashUtils.hash("123456"))); }

    @Test
    @DisplayName("matches() false for wrong code")
    void matchesBad() { assertFalse(OtpHashUtils.matches("654321", OtpHashUtils.hash("123456"))); }
}
