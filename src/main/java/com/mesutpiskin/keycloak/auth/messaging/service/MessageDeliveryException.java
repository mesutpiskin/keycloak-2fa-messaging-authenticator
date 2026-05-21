package com.mesutpiskin.keycloak.auth.messaging.service;

public class MessageDeliveryException extends Exception {
    public MessageDeliveryException(String message) { super(message); }
    public MessageDeliveryException(String message, Throwable cause) { super(message, cause); }
}
