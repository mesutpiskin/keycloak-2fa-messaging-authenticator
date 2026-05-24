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
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.*;

@DisplayName("WhatsAppMessageSender Tests")
class WhatsAppMessageSenderTest {

    private HttpClient http;
    @BeforeEach void setUp() { http = mock(HttpClient.class); }

    private Map<String, String> goodConfig() {
        return Map.of(
                MessagingConstants.WHATSAPP_API_URL, "https://graph.facebook.com/v18.0/12345/messages",
                MessagingConstants.WHATSAPP_API_TOKEN, "tok",
                MessagingConstants.WHATSAPP_FROM_NUMBER, "+15550001111");
    }

    @Test @DisplayName("identity")
    void identity() {
        var s = new WhatsAppMessageSender(http);
        assertEquals(MessageChannel.WHATSAPP, s.getChannel());
        assertEquals("whatsapp", s.getProviderId());
    }

    @Test @DisplayName("Available with required config")
    void available() {
        var s = new WhatsAppMessageSender(http); s.configure(goodConfig());
        assertTrue(s.isAvailable());
    }

    @Test @DisplayName("Throws when not configured")
    void missing() {
        var s = new WhatsAppMessageSender(http); s.configure(Map.of());
        assertThrows(MessageDeliveryException.class,
                () -> s.send(OtpMessage.builder().to("+15551234567").code("1").ttlSeconds(60).build()));
    }

    @Test @DisplayName("Sends with bearer auth on 2xx")
    @SuppressWarnings("unchecked")
    void sendOk() throws Exception {
        HttpResponse<String> r = mock(HttpResponse.class);
        when(r.statusCode()).thenReturn(200);
        when(r.body()).thenReturn("{\"messages\":[{\"id\":\"wamid\"}]}");
        when(http.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class))).thenReturn(r);

        var s = new WhatsAppMessageSender(http); s.configure(goodConfig());
        s.send(OtpMessage.builder().to("+15551234567").code("123456").ttlSeconds(60).build());

        verify(http).send(argThat((HttpRequest req) ->
                "Bearer tok".equals(req.headers().firstValue("Authorization").orElse(""))),
                any(HttpResponse.BodyHandler.class));
    }

    @Test @DisplayName("Throws on 4xx")
    @SuppressWarnings("unchecked")
    void send4xx() throws Exception {
        HttpResponse<String> r = mock(HttpResponse.class);
        when(r.statusCode()).thenReturn(400);
        when(r.body()).thenReturn("{\"error\":{\"message\":\"invalid\"}}");
        when(http.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class))).thenReturn(r);

        var s = new WhatsAppMessageSender(http); s.configure(goodConfig());
        assertThrows(MessageDeliveryException.class,
                () -> s.send(OtpMessage.builder().to("+15551234567").code("1").ttlSeconds(60).build()));
    }

    @Test @DisplayName("Throws on IOException")
    @SuppressWarnings("unchecked")
    void ioErr() throws Exception {
        when(http.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
                .thenThrow(new IOException("conn refused"));
        var s = new WhatsAppMessageSender(http); s.configure(goodConfig());
        assertThrows(MessageDeliveryException.class,
                () -> s.send(OtpMessage.builder().to("+15551234567").code("1").ttlSeconds(60).build()));
    }
}
