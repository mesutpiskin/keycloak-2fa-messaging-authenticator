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

@DisplayName("TelegramMessageSender Tests")
class TelegramMessageSenderTest {

    private HttpClient http;
    @BeforeEach void setUp() { http = mock(HttpClient.class); }

    @Test @DisplayName("identity")
    void identity() {
        var s = new TelegramMessageSender(http);
        assertEquals(MessageChannel.TELEGRAM, s.getChannel());
        assertEquals("telegram", s.getProviderId());
    }

    @Test @DisplayName("Throws when bot token missing")
    void missingToken() {
        var s = new TelegramMessageSender(http); s.configure(Map.of());
        assertThrows(MessageDeliveryException.class,
                () -> s.send(OtpMessage.builder().to("123456789").code("1").ttlSeconds(60).build()));
    }

    @Test @DisplayName("Available after configure")
    void available() {
        var s = new TelegramMessageSender(http);
        s.configure(Map.of(MessagingConstants.TELEGRAM_BOT_TOKEN, "12345:abc"));
        assertTrue(s.isAvailable());
    }

    @Test @DisplayName("Sends to api.telegram.org with bot token in URL")
    @SuppressWarnings("unchecked")
    void sendOk() throws Exception {
        HttpResponse<String> r = mock(HttpResponse.class);
        when(r.statusCode()).thenReturn(200);
        when(r.body()).thenReturn("{\"ok\":true}");
        when(http.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class))).thenReturn(r);

        var s = new TelegramMessageSender(http);
        s.configure(Map.of(MessagingConstants.TELEGRAM_BOT_TOKEN, "12345:abc"));
        s.send(OtpMessage.builder().to("123456789").code("999000").ttlSeconds(60).build());

        verify(http).send(argThat((HttpRequest req) ->
                req.uri().getHost().equals("api.telegram.org")
                        && req.uri().getPath().contains("bot12345:abc")
                        && req.uri().getPath().endsWith("/sendMessage")),
                any(HttpResponse.BodyHandler.class));
    }

    @Test @DisplayName("Throws on Telegram ok=false")
    @SuppressWarnings("unchecked")
    void notOk() throws Exception {
        HttpResponse<String> r = mock(HttpResponse.class);
        when(r.statusCode()).thenReturn(200);
        when(r.body()).thenReturn("{\"ok\":false,\"description\":\"chat not found\"}");
        when(http.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class))).thenReturn(r);

        var s = new TelegramMessageSender(http);
        s.configure(Map.of(MessagingConstants.TELEGRAM_BOT_TOKEN, "12345:abc"));
        assertThrows(MessageDeliveryException.class,
                () -> s.send(OtpMessage.builder().to("123456789").code("1").ttlSeconds(60).build()));
    }

    @Test @DisplayName("Throws on IOException")
    @SuppressWarnings("unchecked")
    void ioErr() throws Exception {
        when(http.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
                .thenThrow(new IOException("conn refused"));
        var s = new TelegramMessageSender(http);
        s.configure(Map.of(MessagingConstants.TELEGRAM_BOT_TOKEN, "12345:abc"));
        assertThrows(MessageDeliveryException.class,
                () -> s.send(OtpMessage.builder().to("123456789").code("1").ttlSeconds(60).build()));
    }
}
