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

@DisplayName("SignalMessageSender Tests")
class SignalMessageSenderTest {

    private HttpClient http;
    @BeforeEach void setUp() { http = mock(HttpClient.class); }

    private Map<String, String> good() {
        return Map.of(
                MessagingConstants.SIGNAL_CLI_REST_URL, "http://signal:8080",
                MessagingConstants.SIGNAL_FROM_NUMBER, "+15550001111");
    }

    @Test @DisplayName("identity")
    void identity() {
        var s = new SignalMessageSender(http);
        assertEquals(MessageChannel.SIGNAL, s.getChannel());
        assertEquals("signal", s.getProviderId());
    }

    @Test @DisplayName("Available after configure")
    void available() {
        var s = new SignalMessageSender(http); s.configure(good());
        assertTrue(s.isAvailable());
    }

    @Test @DisplayName("Throws when not configured")
    void missing() {
        var s = new SignalMessageSender(http); s.configure(Map.of());
        assertThrows(MessageDeliveryException.class,
                () -> s.send(OtpMessage.builder().to("+15551234567").code("1").ttlSeconds(60).build()));
    }

    @Test @DisplayName("POSTs to /v2/send on the configured URL")
    @SuppressWarnings("unchecked")
    void sendOk() throws Exception {
        HttpResponse<String> r = mock(HttpResponse.class);
        when(r.statusCode()).thenReturn(201);
        when(r.body()).thenReturn("{}");
        when(http.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class))).thenReturn(r);

        var s = new SignalMessageSender(http); s.configure(good());
        s.send(OtpMessage.builder().to("+15551234567").code("123456").ttlSeconds(60).build());

        verify(http).send(argThat((HttpRequest req) ->
                req.uri().toString().equals("http://signal:8080/v2/send")),
                any(HttpResponse.BodyHandler.class));
    }

    @Test @DisplayName("Throws on 4xx")
    @SuppressWarnings("unchecked")
    void send4xx() throws Exception {
        HttpResponse<String> r = mock(HttpResponse.class);
        when(r.statusCode()).thenReturn(400);
        when(r.body()).thenReturn("bad");
        when(http.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class))).thenReturn(r);

        var s = new SignalMessageSender(http); s.configure(good());
        assertThrows(MessageDeliveryException.class,
                () -> s.send(OtpMessage.builder().to("+15551234567").code("1").ttlSeconds(60).build()));
    }

    @Test @DisplayName("Throws on IOException")
    @SuppressWarnings("unchecked")
    void ioErr() throws Exception {
        when(http.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
                .thenThrow(new IOException("conn refused"));
        var s = new SignalMessageSender(http); s.configure(good());
        assertThrows(MessageDeliveryException.class,
                () -> s.send(OtpMessage.builder().to("+15551234567").code("1").ttlSeconds(60).build()));
    }
}
