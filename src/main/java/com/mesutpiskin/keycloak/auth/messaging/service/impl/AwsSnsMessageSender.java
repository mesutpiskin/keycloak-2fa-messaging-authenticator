package com.mesutpiskin.keycloak.auth.messaging.service.impl;

import com.mesutpiskin.keycloak.auth.messaging.MessagingConstants;
import com.mesutpiskin.keycloak.auth.messaging.model.MessageChannel;
import com.mesutpiskin.keycloak.auth.messaging.model.OtpMessage;
import com.mesutpiskin.keycloak.auth.messaging.service.MessageDeliveryException;
import com.mesutpiskin.keycloak.auth.messaging.service.MessageSender;
import org.jboss.logging.Logger;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.sns.SnsClient;
import software.amazon.awssdk.services.sns.model.PublishRequest;
import software.amazon.awssdk.services.sns.model.SnsException;

import java.util.Map;
import java.util.function.Function;

public class AwsSnsMessageSender implements MessageSender {

    private static final Logger logger = Logger.getLogger(AwsSnsMessageSender.class);

    public interface SnsClientFactory extends Function<Map<String, String>, SnsClient> {}

    private final SnsClientFactory clientFactory;
    private Map<String, String> config = Map.of();

    public AwsSnsMessageSender() {
        this(AwsSnsMessageSender::defaultClient);
    }

    public AwsSnsMessageSender(SnsClientFactory clientFactory) {
        this.clientFactory = clientFactory;
    }

    @Override public String getProviderId() { return "aws-sns"; }
    @Override public MessageChannel getChannel() { return MessageChannel.SMS; }

    @Override public void configure(Map<String, String> config) { this.config = config == null ? Map.of() : config; }

    @Override
    public boolean isAvailable() {
        return notBlank(config.get(MessagingConstants.AWS_SNS_REGION))
                && notBlank(config.get(MessagingConstants.AWS_ACCESS_KEY_ID))
                && notBlank(config.get(MessagingConstants.AWS_SECRET_ACCESS_KEY));
    }

    @Override
    public void send(OtpMessage message) throws MessageDeliveryException {
        if (!isAvailable()) {
            throw new MessageDeliveryException("AWS SNS is not configured (region/accessKeyId/secretAccessKey required)");
        }
        String body = "Your verification code is: " + message.getCode()
                + " (valid for " + message.getTtlSeconds() + " seconds).";

        try (SnsClient client = clientFactory.apply(config)) {
            client.publish(PublishRequest.builder().phoneNumber(message.getTo()).message(body).build());
            logger.debugf("AWS SNS SMS published to %s", message.getTo());
        } catch (SnsException e) {
            throw new MessageDeliveryException("AWS SNS publish failed", e);
        } catch (RuntimeException e) {
            throw new MessageDeliveryException("AWS SNS publish failed", e);
        }
    }

    private static SnsClient defaultClient(Map<String, String> cfg) {
        return SnsClient.builder()
                .region(Region.of(cfg.get(MessagingConstants.AWS_SNS_REGION)))
                .credentialsProvider(StaticCredentialsProvider.create(
                        AwsBasicCredentials.create(
                                cfg.get(MessagingConstants.AWS_ACCESS_KEY_ID),
                                cfg.get(MessagingConstants.AWS_SECRET_ACCESS_KEY))))
                .build();
    }

    private static boolean notBlank(String s) { return s != null && !s.isBlank(); }
}
