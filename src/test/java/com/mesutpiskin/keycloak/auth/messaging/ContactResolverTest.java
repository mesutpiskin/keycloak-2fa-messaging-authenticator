package com.mesutpiskin.keycloak.auth.messaging;

import com.mesutpiskin.keycloak.auth.messaging.model.MessageChannel;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.keycloak.models.UserModel;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@DisplayName("ContactResolver Tests")
class ContactResolverTest {

    private UserModel user;

    @BeforeEach
    void setUp() { user = mock(UserModel.class); }

    @Test @DisplayName("Returns value of default attribute for SMS when present")
    void smsDefault() {
        when(user.getFirstAttribute("phoneNumber")).thenReturn("+15551234567");
        assertEquals("+15551234567",
                ContactResolver.resolve(user, MessageChannel.SMS, Map.of()));
    }

    @Test @DisplayName("Respects admin-overridden attribute name")
    void overrideAttr() {
        when(user.getFirstAttribute("mobile")).thenReturn("+15550009999");
        assertEquals("+15550009999",
                ContactResolver.resolve(user, MessageChannel.SMS,
                        Map.of(MessagingConstants.SMS_CONTACT_ATTRIBUTE, "mobile")));
    }

    @Test @DisplayName("Returns null when attribute missing or blank")
    void missing() {
        when(user.getFirstAttribute(anyString())).thenReturn(null);
        assertNull(ContactResolver.resolve(user, MessageChannel.TELEGRAM, Map.of()));

        when(user.getFirstAttribute(anyString())).thenReturn("  ");
        assertNull(ContactResolver.resolve(user, MessageChannel.TELEGRAM, Map.of()));
    }

    @Test @DisplayName("Telegram uses telegramChatId default")
    void telegramDefault() {
        when(user.getFirstAttribute("telegramChatId")).thenReturn("123456789");
        assertEquals("123456789",
                ContactResolver.resolve(user, MessageChannel.TELEGRAM, Map.of()));
    }

    @Test @DisplayName("attributeNameFor returns admin override or default")
    void attrName() {
        assertEquals("phoneNumber",
                ContactResolver.attributeNameFor(MessageChannel.SMS, Map.of()));
        assertEquals("mobile",
                ContactResolver.attributeNameFor(MessageChannel.SMS,
                        Map.of(MessagingConstants.SMS_CONTACT_ATTRIBUTE, "mobile")));
    }
}
