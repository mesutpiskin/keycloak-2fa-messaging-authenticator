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

public class WhatsAppMessageSender implements MessageSender {

    private static final Logger logger = Logger.getLogger(WhatsAppMessageSender.class);

    private final HttpClient httpClient;
    private String apiUrl;
    private String apiToken;
    @SuppressWarnings("unused") private String fromNumber; // recorded for completeness; Cloud API derives sender from URL

    public WhatsAppMessageSender() { this(HttpClient.newHttpClient()); }
    public WhatsAppMessageSender(HttpClient httpClient) { this.httpClient = httpClient; }

    @Override public String getProviderId() { return "whatsapp"; }
    @Override public MessageChannel getChannel() { return MessageChannel.WHATSAPP; }

    @Override
    public void configure(Map<String, String> config) {
        if (config == null) config = Map.of();
        this.apiUrl = config.get(MessagingConstants.WHATSAPP_API_URL);
        this.apiToken = config.get(MessagingConstants.WHATSAPP_API_TOKEN);
        this.fromNumber = config.get(MessagingConstants.WHATSAPP_FROM_NUMBER);
    }

    @Override
    public boolean isAvailable() {
        return notBlank(apiUrl) && notBlank(apiToken) && notBlank(fromNumber);
    }

    @Override
    public void send(OtpMessage message) throws MessageDeliveryException {
        if (!isAvailable()) {
            throw new MessageDeliveryException("WhatsApp is not configured (whatsapp.api.url/token/from.number required)");
        }

        String text = "Your verification code is: " + message.getCode()
                + " (valid for " + message.getTtlSeconds() + " seconds).";
        String to = message.getTo().startsWith("+") ? message.getTo().substring(1) : message.getTo();
        String body = "{\"messaging_product\":\"whatsapp\",\"to\":\"" + jsonEscape(to)
                + "\",\"type\":\"text\",\"text\":{\"body\":\"" + jsonEscape(text) + "\"}}";

        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(apiUrl))
                .header("Authorization", "Bearer " + apiToken)
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8))
                .build();

        try {
            HttpResponse<String> resp = httpClient.send(req, HttpResponse.BodyHandlers.ofString());
            if (resp.statusCode() < 200 || resp.statusCode() >= 300) {
                throw new MessageDeliveryException("WhatsApp HTTP " + resp.statusCode() + ": " + resp.body());
            }
            logger.debugf("WhatsApp message sent");
        } catch (IOException e) {
            throw new MessageDeliveryException("Failed to send WhatsApp message", e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new MessageDeliveryException("Interrupted while sending WhatsApp message", e);
        }
    }

    private static String jsonEscape(String s) {
        return s.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n").replace("\r", "\\r").replace("\t", "\\t");
    }

    private static boolean notBlank(String s) { return s != null && !s.isBlank(); }
}
