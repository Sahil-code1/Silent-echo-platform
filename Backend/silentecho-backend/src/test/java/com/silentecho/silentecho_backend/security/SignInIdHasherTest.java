package com.silentecho.silentecho_backend.security;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;

import static org.junit.jupiter.api.Assertions.*;

class SignInIdHasherTest {

    private static final String SECRET = "unit-test-secret-that-is-long-enough-32";
    private static final String ID = "SE-AbCdEfGhIjKlMnOpQrStUvWxYz0123456789_-ABCDE";

    private final SignInIdHasher hasher = new SignInIdHasher(SECRET);

    @Test
    void digestIsDeterministicHexOfSha256Length() {
        String d1 = hasher.digestSignInId(ID);
        assertEquals(d1, hasher.digestSignInId(ID));
        assertEquals(64, d1.length());
        assertTrue(d1.matches("[0-9a-f]{64}"));
    }

    @Test
    void rawIdIsNotPresentInDigestAndDigestIsNotPlainSha256() throws Exception {
        String digest = hasher.digestSignInId(ID);
        assertFalse(digest.contains(ID));
        byte[] plain = MessageDigest.getInstance("SHA-256").digest(ID.getBytes(StandardCharsets.UTF_8));
        assertNotEquals(HexFormat.of().formatHex(plain), digest, "digest must be keyed, not plain SHA-256");
    }

    @Test
    void differentSecretProducesDifferentDigest() {
        SignInIdHasher other = new SignInIdHasher("a-different-secret-value-also-32-chars!");
        assertNotEquals(hasher.digestSignInId(ID), other.digestSignInId(ID));
    }

    @Test
    void differentIdsProduceDifferentDigests() {
        assertNotEquals(hasher.digestSignInId(ID), hasher.digestSignInId(ID.substring(0, ID.length() - 1) + "X"));
    }

    @Test
    void surroundingWhitespaceIsIgnored() {
        assertEquals(hasher.digestSignInId(ID), hasher.digestSignInId("  " + ID + "\n"));
    }

    @Test
    void signInDigestsAndInvitationDigestsAreDomainSeparated() {
        assertNotEquals(hasher.digestSignInId(ID), hasher.digestInvitationCode(ID));
    }

    @Test
    void constantTimeEqualsHandlesNullsAndMismatches() {
        String d = hasher.digestSignInId(ID);
        assertTrue(SignInIdHasher.constantTimeEquals(d, d));
        assertFalse(SignInIdHasher.constantTimeEquals(d, hasher.digestSignInId("SE-other")));
        assertFalse(SignInIdHasher.constantTimeEquals(null, d));
        assertFalse(SignInIdHasher.constantTimeEquals(d, null));
    }

    @Test
    void rejectsShortSecret() {
        assertThrows(IllegalArgumentException.class, () -> new SignInIdHasher("too-short"));
    }
}
