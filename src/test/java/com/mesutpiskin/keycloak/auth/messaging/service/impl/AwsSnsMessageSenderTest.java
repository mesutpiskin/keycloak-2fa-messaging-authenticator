package com.mesutpiskin.keycloak.auth.messaging.service.impl;

import com.mesutpiskin.keycloak.auth.messaging.MessagingConstants;
import com.mesutpiskin.keycloak.auth.messaging.model.MessageChannel;
import com.mesutpiskin.keycloak.auth.messaging.model.OtpMessage;
import com.mesutpiskin.keycloak.auth.messaging.service.MessageDeliveryException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import software.amazon.awssdk.services.sns.SnsClient;
import software.amazon.awssdk.services.sns.model.PublishRequest;
import software.amazon.awssdk.services.sns.model.PublishResponse;
import software.amazon.awssdk.services.sns.model.SnsException;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@DisplayName("AwsSnsMessageSender Tests")
class AwsSnsMessageSenderTest {

    private Map<String, String> goodConfig() {
        return Map.of(
                MessagingConstants.AWS_SNS_REGION, "us-east-1",
                MessagingConstants.AWS_ACCESS_KEY_ID, "AKIA...",
                MessagingConstants.AWS_SECRET_ACCESS_KEY, "secret...");
    }

    @Test @DisplayName("getChannel=SMS, getProviderId=aws-sns")
    void identity() {
        var s = new AwsSnsMessageSender();
        assertEquals(MessageChannel.SMS, s.getChannel());
        assertEquals("aws-sns", s.getProviderId());
    }

    @Test @DisplayName("Not available before configure")
    void notAvailable() { assertFalse(new AwsSnsMessageSender().isAvailable()); }

    @Test @DisplayName("Available after valid configure")
    void available() {
        var s = new AwsSnsMessageSender(); s.configure(goodConfig());
        assertTrue(s.isAvailable());
    }

    @Test @DisplayName("Missing region throws on send")
    void missingRegion() {
        var s = new AwsSnsMessageSender();
        s.configure(Map.of(MessagingConstants.AWS_ACCESS_KEY_ID, "k", MessagingConstants.AWS_SECRET_ACCESS_KEY, "s"));
        OtpMessage m = OtpMessage.builder().to("+15551234567").code("123456").ttlSeconds(60).build();
        assertThrows(MessageDeliveryException.class, () -> s.send(m));
    }

    @Test @DisplayName("Sends via injected SnsClient on happy path")
    void sendOk() throws Exception {
        SnsClient client = mock(SnsClient.class);
        when(client.publish(any(PublishRequest.class))).thenReturn(PublishResponse.builder().messageId("mid").build());

        var s = new AwsSnsMessageSender((cfg) -> client);
        s.configure(goodConfig());
        s.send(OtpMessage.builder().to("+15551234567").code("123456").ttlSeconds(60).build());

        verify(client).publish(any(PublishRequest.class));
    }

    @Test @DisplayName("Throws MessageDeliveryException on SnsException")
    void sendFail() {
        SnsClient client = mock(SnsClient.class);
        when(client.publish(any(PublishRequest.class))).thenThrow(SnsException.builder().message("denied").build());

        var s = new AwsSnsMessageSender((cfg) -> client);
        s.configure(goodConfig());
        OtpMessage m = OtpMessage.builder().to("+15551234567").code("123456").ttlSeconds(60).build();
        assertThrows(MessageDeliveryException.class, () -> s.send(m));
    }
}
