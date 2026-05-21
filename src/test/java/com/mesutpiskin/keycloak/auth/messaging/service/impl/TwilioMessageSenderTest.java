package com.mesutpiskin.keycloak.auth.messaging.service.impl;

import com.mesutpiskin.keycloak.auth.messaging.MessagingConstants;
import com.mesutpiskin.keycloak.auth.messaging.model.MessageChannel;
import com.mesutpiskin.keycloak.auth.messaging.model.OtpMessage;
import com.mesutpiskin.keycloak.auth.messaging.service.MessageDeliveryException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@DisplayName("TwilioMessageSender Tests")
class TwilioMessageSenderTest {

    private HttpClient http;

    @BeforeEach void setUp() { http = mock(HttpClient.class); }

    private Map<String, String> goodConfig() {
        return Map.of(
                MessagingConstants.TWILIO_ACCOUNT_SID, "ACxxxx",
                MessagingConstants.TWILIO_AUTH_TOKEN, "tokenxxxx",
                MessagingConstants.TWILIO_FROM_NUMBER, "+15550001111");
    }

    @Test @DisplayName("getChannel()=SMS, getProviderId()=twilio")
    void identity() {
        var s = new TwilioMessageSender(http);
        assertEquals(MessageChannel.SMS, s.getChannel());
        assertEquals("twilio", s.getProviderId());
    }

    @Test @DisplayName("Not available before configure")
    void notAvailable() { assertFalse(new TwilioMessageSender(http).isAvailable()); }

    @Test @DisplayName("Available after valid configure")
    void available() {
        var s = new TwilioMessageSender(http); s.configure(goodConfig());
        assertTrue(s.isAvailable());
    }

    @Test @DisplayName("Missing required config throws on send")
    void missingConfig() {
        var s = new TwilioMessageSender(http); s.configure(Map.of());
        OtpMessage m = OtpMessage.builder().to("+905001234567").code("123456").ttlSeconds(300).build();
        assertThrows(MessageDeliveryException.class, () -> s.send(m));
    }

    @Test @DisplayName("Sends with HTTP POST on 2xx")
    @SuppressWarnings("unchecked")
    void send2xx() throws Exception {
        HttpResponse<String> resp = mock(HttpResponse.class);
        when(resp.statusCode()).thenReturn(201);
        when(http.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class))).thenReturn(resp);

        var s = new TwilioMessageSender(http); s.configure(goodConfig());
        s.send(OtpMessage.builder().to("+905001234567").code("123456").ttlSeconds(300).build());

        verify(http).send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class));
    }

    @Test @DisplayName("Throws on 4xx")
    @SuppressWarnings("unchecked")
    void send4xx() throws Exception {
        HttpResponse<String> resp = mock(HttpResponse.class);
        when(resp.statusCode()).thenReturn(401);
        when(resp.body()).thenReturn("unauthorized");
        when(http.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class))).thenReturn(resp);

        var s = new TwilioMessageSender(http); s.configure(goodConfig());
        OtpMessage m = OtpMessage.builder().to("+905001234567").code("123456").ttlSeconds(300).build();
        assertThrows(MessageDeliveryException.class, () -> s.send(m));
    }

    @Test @DisplayName("Throws on IOException")
    @SuppressWarnings("unchecked")
    void sendIoEx() throws Exception {
        when(http.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
                .thenThrow(new IOException("connection refused"));
        var s = new TwilioMessageSender(http); s.configure(goodConfig());
        OtpMessage m = OtpMessage.builder().to("+905001234567").code("123456").ttlSeconds(300).build();
        assertThrows(MessageDeliveryException.class, () -> s.send(m));
    }
}
