package com.silentecho.silentecho_backend.security;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.Base64;

import static org.junit.jupiter.api.Assertions.*;

class CryptoServiceTest {

    private static final byte[] KEY = "0123456789abcdef0123456789abcdef".getBytes(StandardCharsets.US_ASCII);
    private final CryptoService crypto = new CryptoService(KEY, new SecureRandom());

    @Test
    void textRoundTripsAndUsesVersionedFormat() {
        String token = crypto.encryptText("I was pushed in the corridor", "message:case-1");
        assertTrue(token.startsWith("v1."));
        assertEquals("I was pushed in the corridor", crypto.decryptText(token, "message:case-1"));
    }

    @Test
    void ciphertextDoesNotContainPlaintext() {
        String secret = "PLAINTEXT-MARKER-XYZ";
        String token = crypto.encryptText(secret, "complaint:case-1:description");
        assertFalse(token.contains(secret));
    }

    @Test
    void sameInputEncryptsDifferentlyBecauseIvIsFresh() {
        String a = crypto.encryptText("same", "aad");
        String b = crypto.encryptText("same", "aad");
        assertNotEquals(a, b);
    }

    @Test
    void bytesRoundTrip() {
        byte[] data = new byte[4096];
        new SecureRandom().nextBytes(data);
        byte[] blob = crypto.encryptBytes(data, "evidence-bytes:case-1:file-1");
        assertArrayEquals(data, crypto.decryptBytes(blob, "evidence-bytes:case-1:file-1"));
    }

    @Test
    void flippingAnyCiphertextByteIsDetected() {
        byte[] blob = crypto.encryptBytes("hello evidence".getBytes(StandardCharsets.UTF_8), "aad");
        for (int i = 0; i < blob.length; i++) {
            byte[] tampered = Arrays.copyOf(blob, blob.length);
            tampered[i] ^= 0x01;
            final int index = i;
            assertThrows(CryptoService.CryptoException.class,
                    () -> crypto.decryptBytes(tampered, "aad"), "tamper at byte " + index + " not detected");
        }
    }

    @Test
    void ciphertextCannotBeReplayedIntoAnotherRecord() {
        String token = crypto.encryptText("secret", "message:case-A");
        assertThrows(CryptoService.CryptoException.class, () -> crypto.decryptText(token, "message:case-B"));
    }

    @Test
    void wrongKeyFailsToDecrypt() {
        String token = crypto.encryptText("secret", "aad");
        CryptoService other = new CryptoService(
                "ffffffffffffffffffffffffffffffff".getBytes(StandardCharsets.US_ASCII), new SecureRandom());
        assertThrows(CryptoService.CryptoException.class, () -> other.decryptText(token, "aad"));
    }

    @Test
    void rejectsKeysThatAreNotAes256() {
        assertThrows(IllegalArgumentException.class, () -> new CryptoService(new byte[16], new SecureRandom()));
        assertThrows(IllegalArgumentException.class, () -> CryptoService.fromBase64(null, new SecureRandom()));
        assertThrows(IllegalArgumentException.class, () -> CryptoService.fromBase64("!!!not-base64", new SecureRandom()));
    }

    @Test
    void fromBase64AcceptsAValidKey() {
        String b64 = Base64.getEncoder().encodeToString(KEY);
        CryptoService fromB64 = CryptoService.fromBase64(b64, new SecureRandom());
        assertEquals("ok", fromB64.decryptText(fromB64.encryptText("ok", null), null));
    }

    @Test
    void rejectsTruncatedAndUnversionedInput() {
        assertThrows(CryptoService.CryptoException.class, () -> crypto.decryptBytes(new byte[5], null));
        assertThrows(CryptoService.CryptoException.class, () -> crypto.decryptText("no-prefix", null));
    }
}
