package com.mesutpiskin.keycloak.auth.messaging;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("MessagingConstants Tests")
class MessagingConstantsTest {

    @Test
    @DisplayName("CODE session-note key is stable")
    void codeKey() {
        assertEquals("messagingCode", MessagingConstants.CODE);
    }

    @Test
    @DisplayName("Default OTP parameters match spec defaults")
    void defaults() {
        assertEquals(6, MessagingConstants.DEFAULT_LENGTH);
        assertEquals(300, MessagingConstants.DEFAULT_TTL);
        assertEquals(60, MessagingConstants.DEFAULT_RESEND_COOLDOWN);
        assertEquals(5, MessagingConstants.DEFAULT_MAX_ATTEMPTS);
        assertFalse(MessagingConstants.DEFAULT_SIMULATION_MODE);
        assertTrue(MessagingConstants.DEFAULT_SKIP_SETUP);
    }

    @Test
    @DisplayName("Channel config key has expected name")
    void channelKey() {
        assertEquals("message.channel", MessagingConstants.MESSAGE_CHANNEL);
    }

    @Test
    @DisplayName("Constructor throws — utility class")
    void utilityClass() throws Exception {
        var ctor = MessagingConstants.class.getDeclaredConstructor();
        ctor.setAccessible(true);
        var ex = assertThrows(java.lang.reflect.InvocationTargetException.class, ctor::newInstance);
        assertInstanceOf(UnsupportedOperationException.class, ex.getCause());
    }
}
