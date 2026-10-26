package com.finemp.identity;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.HexFormat;

public final class OpaqueAccountToken {

    private static final SecureRandom RANDOM = new SecureRandom();

    private final String rawValue;
    private final String hash;

    private OpaqueAccountToken(String rawValue) {
        this.rawValue = rawValue;
        this.hash = hash(rawValue);
    }

    public static OpaqueAccountToken generate() {
        byte[] bytes = new byte[32];
        RANDOM.nextBytes(bytes);
        return new OpaqueAccountToken(Base64.getUrlEncoder().withoutPadding().encodeToString(bytes));
    }

    public static String hash(String rawValue) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(rawValue.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("Required digest is unavailable.", exception);
        }
    }

    public String rawValue() {
        return rawValue;
    }

    public String hash() {
        return hash;
    }

    @Override
    public String toString() {
        return "OpaqueAccountToken[redacted]";
    }
}
