package com.mesutpiskin.keycloak.auth.messaging.service.impl;

import com.mesutpiskin.keycloak.auth.messaging.MessagingConstants;
import com.mesutpiskin.keycloak.auth.messaging.model.MessageChannel;
import com.mesutpiskin.keycloak.auth.messaging.model.OtpMessage;
import com.mesutpiskin.keycloak.auth.messaging.service.MessageDeliveryException;
import com.mesutpiskin.keycloak.auth.messaging.service.MessageSender;
import org.jboss.logging.Logger;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

public class VonageMessageSender implements MessageSender {

    private static final Logger logger = Logger.getLogger(VonageMessageSender.class);
    private static final String API_URL = "https://rest.nexmo.com/sms/json";
    private static final Pattern STATUS = Pattern.compile("\"status\"\\s*:\\s*\"(\\d+)\"");
    private static final Pattern ERR = Pattern.compile("\"error-text\"\\s*:\\s*\"([^\"]+)\"");

    private final HttpClient httpClient;
    private String apiKey;
    private String apiSecret;
    private String fromNumber;

    public VonageMessageSender() { this(HttpClient.newHttpClient()); }
    public VonageMessageSender(HttpClient httpClient) { this.httpClient = httpClient; }

    @Override public String getProviderId() { return "vonage"; }
    @Override public MessageChannel getChannel() { return MessageChannel.SMS; }

    @Override
    public void configure(Map<String, String> config) {
        if (config == null) config = Map.of();
        this.apiKey = config.get(MessagingConstants.VONAGE_API_KEY);
        this.apiSecret = config.get(MessagingConstants.VONAGE_API_SECRET);
        this.fromNumber = config.get(MessagingConstants.VONAGE_FROM_NUMBER);
    }

    @Override
    public boolean isAvailable() {
        return notBlank(apiKey) && notBlank(apiSecret) && notBlank(fromNumber);
    }

    @Override
    public void send(OtpMessage message) throws MessageDeliveryException {
        if (!isAvailable()) {
            throw new MessageDeliveryException("Vonage is not configured (apiKey/apiSecret/fromNumber required)");
        }
        String text = "Your verification code is: " + message.getCode()
                + " (valid for " + message.getTtlSeconds() + " seconds).";

        Map<String, String> form = new LinkedHashMap<>();
        form.put("api_key", apiKey);
        form.put("api_secret", apiSecret);
        form.put("from", fromNumber);
        // Vonage expects 'to' without leading + (just digits)
        form.put("to", message.getTo().startsWith("+") ? message.getTo().substring(1) : message.getTo());
        form.put("text", text);

        String body = form.entrySet().stream()
                .map(e -> URLEncoder.encode(e.getKey(), StandardCharsets.UTF_8)
                        + "=" + URLEncoder.encode(e.getValue(), StandardCharsets.UTF_8))
                .collect(Collectors.joining("&"));

        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(API_URL))
                .header("Content-Type", "application/x-www-form-urlencoded")
                .header("Accept", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8))
                .build();

        try {
            HttpResponse<String> resp = httpClient.send(req, HttpResponse.BodyHandlers.ofString());
            if (resp.statusCode() < 200 || resp.statusCode() >= 300) {
                throw new MessageDeliveryException("Vonage HTTP status " + resp.statusCode() + ": " + resp.body());
            }
            // Inspect first message's status; "0" = success per Vonage docs
            String b = resp.body() == null ? "" : resp.body();
            Matcher m = STATUS.matcher(b);
            if (m.find() && !"0".equals(m.group(1))) {
                Matcher em = ERR.matcher(b);
                String err = em.find() ? em.group(1) : "unknown error";
                throw new MessageDeliveryException("Vonage rejected message status=" + m.group(1) + " (" + err + ")");
            }
            logger.debugf("Vonage SMS accepted");
        } catch (IOException e) {
            throw new MessageDeliveryException("Failed to send Vonage SMS", e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new MessageDeliveryException("Interrupted while sending Vonage SMS", e);
        }
    }

    private static boolean notBlank(String s) { return s != null && !s.isBlank(); }
}
