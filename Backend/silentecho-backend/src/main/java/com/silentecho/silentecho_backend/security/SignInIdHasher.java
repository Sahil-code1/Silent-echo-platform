package com.silentecho.silentecho_backend.security;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.Objects;

/**
 * Keyed HMAC-SHA-256 digests for sign-in IDs and invitation codes.
 *
 * <p>The raw ID is never stored. The database holds only HMAC(secret, domain + value).
 * Domain labels separate the two keyspaces so a sign-in ID digest can never match an
 * invitation digest. Without the server secret, a stolen database cannot be checked
 * against candidate IDs offline.
 */
public final class SignInIdHasher {

    public static final int MIN_SECRET_LENGTH = 32;

    private static final String ALGORITHM = "HmacSHA256";
    private static final String SIGN_IN_DOMAIN = "signin:";
    private static final String INVITE_DOMAIN = "invite:";
    private static final String RECOVERY_DOMAIN = "organisation-recovery:";

    private final SecretKeySpec key;

    public SignInIdHasher(String secret) {
        Objects.requireNonNull(secret, "secret");
        if (secret.length() < MIN_SECRET_LENGTH) {
            throw new IllegalArgumentException(
                    "HMAC secret must be at least " + MIN_SECRET_LENGTH + " characters");
        }
        this.key = new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), ALGORITHM);
    }

    public String digestSignInId(String signInId) {
        return hmac(SIGN_IN_DOMAIN + signInId.trim());
    }

    public String digestInvitationCode(String code) {
        return hmac(INVITE_DOMAIN + code.trim());
    }

    public String digestRecoveryCode(String code) {
        return hmac(RECOVERY_DOMAIN + code.trim());
    }

    /** Compares two hex digests in constant time. */
    public static boolean constantTimeEquals(String a, String b) {
        if (a == null || b == null) {
            return false;
        }
        return MessageDigest.isEqual(a.getBytes(StandardCharsets.UTF_8), b.getBytes(StandardCharsets.UTF_8));
    }

    private String hmac(String message) {
        try {
            Mac mac = Mac.getInstance(ALGORITHM);
            mac.init(key);
            return HexFormat.of().formatHex(mac.doFinal(message.getBytes(StandardCharsets.UTF_8)));
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("HMAC-SHA-256 is unavailable on this JVM", e);
        }
    }
}
