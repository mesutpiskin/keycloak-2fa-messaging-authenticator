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
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.stream.Collectors;

public class TwilioMessageSender implements MessageSender {

    private static final Logger logger = Logger.getLogger(TwilioMessageSender.class);
    private static final String API_BASE = "https://api.twilio.com/2010-04-01/Accounts/";

    private final HttpClient httpClient;
    private String accountSid;
    private String authToken;
    private String fromNumber;

    public TwilioMessageSender() { this(HttpClient.newHttpClient()); }
    public TwilioMessageSender(HttpClient httpClient) { this.httpClient = httpClient; }

    @Override public String getProviderId() { return "twilio"; }
    @Override public MessageChannel getChannel() { return MessageChannel.SMS; }

    @Override
    public void configure(Map<String, String> config) {
        if (config == null) config = Map.of();
        this.accountSid = config.get(MessagingConstants.TWILIO_ACCOUNT_SID);
        this.authToken = config.get(MessagingConstants.TWILIO_AUTH_TOKEN);
        this.fromNumber = config.get(MessagingConstants.TWILIO_FROM_NUMBER);
    }

    @Override
    public boolean isAvailable() {
        return notBlank(accountSid) && notBlank(authToken) && notBlank(fromNumber);
    }

    @Override
    public void send(OtpMessage message) throws MessageDeliveryException {
        if (!isAvailable()) {
            throw new MessageDeliveryException("Twilio is not configured (accountSid/authToken/fromNumber required)");
        }

        String body = buildBody(message.getCode(), message.getTtlSeconds());

        Map<String, String> form = new LinkedHashMap<>();
        form.put("To", message.getTo());
        form.put("From", fromNumber);
        form.put("Body", body);
        String encoded = form.entrySet().stream()
                .map(e -> URLEncoder.encode(e.getKey(), StandardCharsets.UTF_8)
                        + "=" + URLEncoder.encode(e.getValue(), StandardCharsets.UTF_8))
                .collect(Collectors.joining("&"));

        String credentials = Base64.getEncoder()
                .encodeToString((accountSid + ":" + authToken).getBytes(StandardCharsets.UTF_8));

        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(API_BASE + accountSid + "/Messages.json"))
                .header("Authorization", "Basic " + credentials)
                .header("Content-Type", "application/x-www-form-urlencoded")
                .POST(HttpRequest.BodyPublishers.ofString(encoded, StandardCharsets.UTF_8))
                .build();

        try {
            HttpResponse<String> resp = httpClient.send(req, HttpResponse.BodyHandlers.ofString());
            if (resp.statusCode() < 200 || resp.statusCode() >= 300) {
                throw new MessageDeliveryException(
                        "Twilio API returned status " + resp.statusCode() + ": " + resp.body());
            }
            logger.debugf("Twilio SMS sent (status=%d)", resp.statusCode());
        } catch (IOException e) {
            throw new MessageDeliveryException("Failed to send Twilio SMS", e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new MessageDeliveryException("Interrupted while sending Twilio SMS", e);
        }
    }

    private static String buildBody(String code, int ttlSeconds) {
        return "Your verification code is: " + code + " (valid for " + ttlSeconds + " seconds).";
    }

    private static boolean notBlank(String s) { return s != null && !s.isBlank(); }
}
