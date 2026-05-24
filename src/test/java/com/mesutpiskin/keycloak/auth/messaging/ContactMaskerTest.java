package com.mesutpiskin.keycloak.auth.messaging;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

@DisplayName("ContactMasker Tests")
class ContactMaskerTest {

    @Test @DisplayName("Phone-like input shows + country and last 2 digits")
    void phone() {
        assertEquals("+90 5** *** **67", ContactMasker.maskPhone("+905001234567"));
    }

    @Test @DisplayName("Short phone falls back gracefully")
    void shortPhone() { assertEquals("****", ContactMasker.maskPhone("+12")); }

    @Test @DisplayName("Null/blank returns empty mask")
    void nullVal() {
        assertEquals("", ContactMasker.maskPhone(null));
        assertEquals("", ContactMasker.maskPhone(""));
    }

    @Test @DisplayName("maskGeneric keeps first+last char")
    void generic() {
        assertEquals("a*******t", ContactMasker.maskGeneric("alphabet"));
    }
}
