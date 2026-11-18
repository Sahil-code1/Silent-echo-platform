package com.silentecho.silentecho_backend.security;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.Objects;

/**
 * AES-256-GCM authenticated encryption for complaint text, messages, evidence
 * filenames and evidence bytes.
 *
 * <p>Each call draws a fresh 96-bit IV. The associated data (AAD) binds a ciphertext to its
 * record (for example {@code "message:<caseId>"}), so a ciphertext copied to another record
 * fails authentication. Tampering with any byte also fails. The key is supplied from the
 * environment. The operator holds the key, so this is server-side encryption, not end-to-end.
 */
public final class CryptoService {

    private static final String TRANSFORMATION = "AES/GCM/NoPadding";
    private static final int KEY_BYTES = 32;
    private static final int IV_BYTES = 12;
    private static final int TAG_BITS = 128;
    private static final String TEXT_PREFIX = "v1.";

    private final SecretKeySpec key;
    private final SecureRandom random;

    public CryptoService(byte[] rawKey, SecureRandom random) {
        if (rawKey == null || rawKey.length != KEY_BYTES) {
            throw new IllegalArgumentException("Encryption key must decode to exactly 32 bytes (AES-256)");
        }
        this.key = new SecretKeySpec(rawKey.clone(), "AES");
        this.random = Objects.requireNonNull(random, "random");
    }

    public static CryptoService fromBase64(String base64Key, SecureRandom random) {
        if (base64Key == null || base64Key.isBlank()) {
            throw new IllegalArgumentException("SILENTECHO_ENCRYPTION_KEY is not set");
        }
        byte[] raw;
        try {
            raw = Base64.getDecoder().decode(base64Key.trim());
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("SILENTECHO_ENCRYPTION_KEY is not valid Base64", e);
        }
        return new CryptoService(raw, random);
    }

    /** Returns {@code v1.<base64url(iv || ciphertext || tag)>}, suitable for a text column. */
    public String encryptText(String plaintext, String aad) {
        byte[] blob = encryptBytes(plaintext.getBytes(StandardCharsets.UTF_8), aad);
        return TEXT_PREFIX + Base64.getUrlEncoder().withoutPadding().encodeToString(blob);
    }

    public String decryptText(String token, String aad) {
        if (token == null || !token.startsWith(TEXT_PREFIX)) {
            throw new CryptoException("Unsupported ciphertext format");
        }
        byte[] blob;
        try {
            blob = Base64.getUrlDecoder().decode(token.substring(TEXT_PREFIX.length()));
        } catch (IllegalArgumentException e) {
            throw new CryptoException("Malformed ciphertext", e);
        }
        return new String(decryptBytes(blob, aad), StandardCharsets.UTF_8);
    }

    /** Returns {@code iv || ciphertext || tag}. */
    public byte[] encryptBytes(byte[] plaintext, String aad) {
        byte[] iv = new byte[IV_BYTES];
        random.nextBytes(iv);
        try {
            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            cipher.init(Cipher.ENCRYPT_MODE, key, new GCMParameterSpec(TAG_BITS, iv));
            if (aad != null) {
                cipher.updateAAD(aad.getBytes(StandardCharsets.UTF_8));
            }
            byte[] ciphertext = cipher.doFinal(plaintext);
            byte[] out = new byte[IV_BYTES + ciphertext.length];
            System.arraycopy(iv, 0, out, 0, IV_BYTES);
            System.arraycopy(ciphertext, 0, out, IV_BYTES, ciphertext.length);
            return out;
        } catch (GeneralSecurityException e) {
            throw new CryptoException("Encryption failed", e);
        }
    }

    public byte[] decryptBytes(byte[] blob, String aad) {
        if (blob == null || blob.length < IV_BYTES + TAG_BITS / 8) {
            throw new CryptoException("Ciphertext is too short");
        }
        try {
            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            cipher.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(TAG_BITS, blob, 0, IV_BYTES));
            if (aad != null) {
                cipher.updateAAD(aad.getBytes(StandardCharsets.UTF_8));
            }
            return cipher.doFinal(blob, IV_BYTES, blob.length - IV_BYTES);
        } catch (GeneralSecurityException e) {
            throw new CryptoException("Decryption failed: data was altered or the key is wrong", e);
        }
    }

    public static final class CryptoException extends RuntimeException {
    private static final long serialVersionUID = 1L;

        public CryptoException(String message) {
            super(message);
        }

        public CryptoException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
