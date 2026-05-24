package com.mesutpiskin.keycloak.auth.messaging;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

final class OtpHashUtils {
    static final String HASH_ALGORITHM = "SHA-256";

    private OtpHashUtils() {
        throw new UnsupportedOperationException("OtpHashUtils is a utility class and cannot be instantiated");
    }

    static String hash(String code) {
        return HexFormat.of().formatHex(digestBytes(code));
    }

    static boolean matches(String submittedCode, String storedHash) {
        byte[] submittedBytes = digestBytes(submittedCode);
        byte[] storedBytes = HexFormat.of().parseHex(storedHash);
        return MessageDigest.isEqual(submittedBytes, storedBytes);
    }

    private static byte[] digestBytes(String code) {
        try {
            return MessageDigest.getInstance(HASH_ALGORITHM).digest(code.getBytes(StandardCharsets.UTF_8));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(HASH_ALGORITHM + " algorithm not available", e);
        }
    }
}
