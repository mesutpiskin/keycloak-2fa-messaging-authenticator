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
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class TelegramMessageSender implements MessageSender {

    private static final Logger logger = Logger.getLogger(TelegramMessageSender.class);
    private static final Pattern OK = Pattern.compile("\"ok\"\\s*:\\s*(true|false)");
    private static final Pattern DESC = Pattern.compile("\"description\"\\s*:\\s*\"([^\"]*)\"");

    private final HttpClient httpClient;
    private String botToken;

    public TelegramMessageSender() { this(HttpClient.newHttpClient()); }
    public TelegramMessageSender(HttpClient httpClient) { this.httpClient = httpClient; }

    @Override public String getProviderId() { return "telegram"; }
    @Override public MessageChannel getChannel() { return MessageChannel.TELEGRAM; }

    @Override
    public void configure(Map<String, String> config) {
        if (config == null) config = Map.of();
        this.botToken = config.get(MessagingConstants.TELEGRAM_BOT_TOKEN);
    }

    @Override public boolean isAvailable() { return notBlank(botToken); }

    @Override
    public void send(OtpMessage message) throws MessageDeliveryException {
        if (!isAvailable()) {
            throw new MessageDeliveryException("Telegram is not configured (telegram.bot.token required)");
        }

        String text = "Your verification code is: " + message.getCode()
                + " (valid for " + message.getTtlSeconds() + " seconds).";
        String url = "https://api.telegram.org/bot" + botToken + "/sendMessage";
        String body = "chat_id=" + URLEncoder.encode(message.getTo(), StandardCharsets.UTF_8)
                + "&text=" + URLEncoder.encode(text, StandardCharsets.UTF_8);

        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .header("Content-Type", "application/x-www-form-urlencoded")
                .POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8))
                .build();

        try {
            HttpResponse<String> resp = httpClient.send(req, HttpResponse.BodyHandlers.ofString());
            if (resp.statusCode() < 200 || resp.statusCode() >= 300) {
                throw new MessageDeliveryException("Telegram HTTP " + resp.statusCode() + ": " + resp.body());
            }
            String b = resp.body() == null ? "" : resp.body();
            Matcher ok = OK.matcher(b);
            if (!ok.find() || !"true".equals(ok.group(1))) {
                Matcher d = DESC.matcher(b);
                String desc = d.find() ? d.group(1) : "unknown";
                throw new MessageDeliveryException("Telegram API error: " + desc);
            }
            logger.debugf("Telegram message sent");
        } catch (IOException e) {
            throw new MessageDeliveryException("Failed to send Telegram message", e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new MessageDeliveryException("Interrupted while sending Telegram message", e);
        }
    }

    private static boolean notBlank(String s) { return s != null && !s.isBlank(); }
}
