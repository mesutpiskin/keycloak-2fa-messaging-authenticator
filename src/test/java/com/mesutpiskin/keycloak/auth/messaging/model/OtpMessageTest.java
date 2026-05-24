package com.mesutpiskin.keycloak.auth.messaging.model;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

@DisplayName("OtpMessage Tests")
class OtpMessageTest {

    @Test
    @DisplayName("Builder produces immutable message with required fields")
    void builderHappy() {
        OtpMessage m = OtpMessage.builder().to("+905001234567").code("847291").ttlSeconds(300).locale("tr").build();
        assertEquals("+905001234567", m.getTo());
        assertEquals("847291", m.getCode());
        assertEquals(300, m.getTtlSeconds());
        assertEquals("tr", m.getLocale());
    }

    @Test
    @DisplayName("Null 'to' throws")
    void requireTo() {
        assertThrows(NullPointerException.class,
                () -> OtpMessage.builder().code("123456").ttlSeconds(300).build());
    }

    @Test
    @DisplayName("Null 'code' throws")
    void requireCode() {
        assertThrows(NullPointerException.class,
                () -> OtpMessage.builder().to("+15551234").ttlSeconds(300).build());
    }

    @Test
    @DisplayName("Non-positive ttl throws")
    void requirePositiveTtl() {
        assertThrows(IllegalArgumentException.class,
                () -> OtpMessage.builder().to("+15551234").code("123456").ttlSeconds(0).build());
    }

    @Test
    @DisplayName("locale defaults to 'en' when not set")
    void defaultLocale() {
        OtpMessage m = OtpMessage.builder().to("+15551234").code("123456").ttlSeconds(60).build();
        assertEquals("en", m.getLocale());
    }
}
