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

@DisplayName("VonageMessageSender Tests")
class VonageMessageSenderTest {

    private HttpClient http;
    @BeforeEach void setUp() { http = mock(HttpClient.class); }

    private Map<String, String> goodConfig() {
        return Map.of(
                MessagingConstants.VONAGE_API_KEY, "k",
                MessagingConstants.VONAGE_API_SECRET, "s",
                MessagingConstants.VONAGE_FROM_NUMBER, "MyApp");
    }

    @Test @DisplayName("identity")
    void identity() {
        var s = new VonageMessageSender(http);
        assertEquals(MessageChannel.SMS, s.getChannel());
        assertEquals("vonage", s.getProviderId());
    }

    @Test @DisplayName("Not available before configure")
    void notAvailable() { assertFalse(new VonageMessageSender(http).isAvailable()); }

    @Test @DisplayName("Available after configure")
    void available() {
        var s = new VonageMessageSender(http); s.configure(goodConfig());
        assertTrue(s.isAvailable());
    }

    @Test @DisplayName("Throws when not configured")
    void missingConfig() {
        var s = new VonageMessageSender(http); s.configure(Map.of());
        assertThrows(MessageDeliveryException.class,
                () -> s.send(OtpMessage.builder().to("+15551234567").code("1").ttlSeconds(60).build()));
    }

    @Test @DisplayName("Sends on 2xx with status=0 in JSON")
    @SuppressWarnings("unchecked")
    void send2xx() throws Exception {
        HttpResponse<String> r = mock(HttpResponse.class);
        when(r.statusCode()).thenReturn(200);
        when(r.body()).thenReturn("{\"messages\":[{\"status\":\"0\"}]}");
        when(http.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class))).thenReturn(r);

        var s = new VonageMessageSender(http); s.configure(goodConfig());
        s.send(OtpMessage.builder().to("+15551234567").code("123456").ttlSeconds(60).build());
        verify(http).send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class));
    }

    @Test @DisplayName("Throws on 2xx with non-zero Vonage status")
    @SuppressWarnings("unchecked")
    void sendVonageError() throws Exception {
        HttpResponse<String> r = mock(HttpResponse.class);
        when(r.statusCode()).thenReturn(200);
        when(r.body()).thenReturn("{\"messages\":[{\"status\":\"4\",\"error-text\":\"Bad credentials\"}]}");
        when(http.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class))).thenReturn(r);

        var s = new VonageMessageSender(http); s.configure(goodConfig());
        assertThrows(MessageDeliveryException.class,
                () -> s.send(OtpMessage.builder().to("+15551234567").code("1").ttlSeconds(60).build()));
    }

    @Test @DisplayName("Throws on HTTP 5xx")
    @SuppressWarnings("unchecked")
    void send5xx() throws Exception {
        HttpResponse<String> r = mock(HttpResponse.class);
        when(r.statusCode()).thenReturn(500);
        when(r.body()).thenReturn("boom");
        when(http.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class))).thenReturn(r);

        var s = new VonageMessageSender(http); s.configure(goodConfig());
        assertThrows(MessageDeliveryException.class,
                () -> s.send(OtpMessage.builder().to("+15551234567").code("1").ttlSeconds(60).build()));
    }

    @Test @DisplayName("Throws on IOException")
    @SuppressWarnings("unchecked")
    void ioErr() throws Exception {
        when(http.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
                .thenThrow(new IOException("conn refused"));
        var s = new VonageMessageSender(http); s.configure(goodConfig());
        assertThrows(MessageDeliveryException.class,
                () -> s.send(OtpMessage.builder().to("+15551234567").code("1").ttlSeconds(60).build()));
    }
}
