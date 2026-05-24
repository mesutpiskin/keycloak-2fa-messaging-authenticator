package com.mesutpiskin.keycloak.auth.messaging.service.impl;

import com.mesutpiskin.keycloak.auth.messaging.MessagingConstants;
import com.mesutpiskin.keycloak.auth.messaging.model.MessageChannel;
import com.mesutpiskin.keycloak.auth.messaging.model.OtpMessage;
import com.mesutpiskin.keycloak.auth.messaging.service.MessageDeliveryException;
import com.mesutpiskin.keycloak.auth.messaging.service.MessageSender;
import org.jboss.logging.Logger;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.Map;

public class SignalMessageSender implements MessageSender {

    private static final Logger logger = Logger.getLogger(SignalMessageSender.class);

    private final HttpClient httpClient;
    private String restUrl;
    private String fromNumber;

    public SignalMessageSender() { this(HttpClient.newHttpClient()); }
    public SignalMessageSender(HttpClient httpClient) { this.httpClient = httpClient; }

    @Override public String getProviderId() { return "signal"; }
    @Override public MessageChannel getChannel() { return MessageChannel.SIGNAL; }

    @Override
    public void configure(Map<String, String> config) {
        if (config == null) config = Map.of();
        this.restUrl = trimTrailingSlash(config.get(MessagingConstants.SIGNAL_CLI_REST_URL));
        this.fromNumber = config.get(MessagingConstants.SIGNAL_FROM_NUMBER);
    }

    @Override
    public boolean isAvailable() {
        return notBlank(restUrl) && notBlank(fromNumber);
    }

    @Override
    public void send(OtpMessage message) throws MessageDeliveryException {
        if (!isAvailable()) {
            throw new MessageDeliveryException("Signal is not configured (signal.cli.rest.url/from.number required)");
        }

        String text = "Your verification code is: " + message.getCode()
                + " (valid for " + message.getTtlSeconds() + " seconds).";
        String body = "{\"message\":\"" + jsonEscape(text)
                + "\",\"number\":\"" + jsonEscape(fromNumber)
                + "\",\"recipients\":[\"" + jsonEscape(message.getTo()) + "\"]}";

        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(restUrl + "/v2/send"))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8))
                .build();

        try {
            HttpResponse<String> resp = httpClient.send(req, HttpResponse.BodyHandlers.ofString());
            if (resp.statusCode() < 200 || resp.statusCode() >= 300) {
                throw new MessageDeliveryException("Signal HTTP " + resp.statusCode() + ": " + resp.body());
            }
            logger.debugf("Signal message sent");
        } catch (IOException e) {
            throw new MessageDeliveryException("Failed to send Signal message", e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new MessageDeliveryException("Interrupted while sending Signal message", e);
        }
    }

    private static String trimTrailingSlash(String s) {
        if (s == null) return null;
        String t = s.trim();
        return t.endsWith("/") ? t.substring(0, t.length() - 1) : t;
    }

    private static String jsonEscape(String s) {
        return s.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n").replace("\r", "\\r").replace("\t", "\\t");
    }

    private static boolean notBlank(String s) { return s != null && !s.isBlank(); }
}
