package com.silentecho.silentecho_backend.security;

import java.security.SecureRandom;
import java.util.Base64;
import java.util.Objects;
import java.util.regex.Pattern;

/**
 * Generates private sign-in IDs of the form {@code SE-<43 Base64URL characters>},
 * which encodes 32 bytes (256 bits) of SecureRandom output.
 */
public final class SignInIdGenerator {

    public static final String PREFIX = "SE-";
    public static final int ENTROPY_BYTES = 32;

    private static final Pattern WELL_FORMED = Pattern.compile("^SE-[A-Za-z0-9_-]{43}$");
    private static final Base64.Encoder ENCODER = Base64.getUrlEncoder().withoutPadding();

    private final SecureRandom random;

    public SignInIdGenerator(SecureRandom random) {
        this.random = Objects.requireNonNull(random, "random");
    }

    public String generate() {
        byte[] bytes = new byte[ENTROPY_BYTES];
        random.nextBytes(bytes);
        return PREFIX + ENCODER.encodeToString(bytes);
    }

    /** Cheap format check so malformed input is rejected before any database work. */
    public static boolean isWellFormed(String candidate) {
        return candidate != null && WELL_FORMED.matcher(candidate).matches();
    }
}
