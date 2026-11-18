package com.silentecho.silentecho_backend.security;

import org.junit.jupiter.api.Test;

import java.security.SecureRandom;
import java.util.Base64;
import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class SignInIdGeneratorTest {

    private final SignInIdGenerator generator = new SignInIdGenerator(new SecureRandom());

    @Test
    void generatedIdHasPrefixAndFortyThreeBase64UrlCharacters() {
        String id = generator.generate();
        assertTrue(id.startsWith("SE-"));
        assertEquals(46, id.length());
        assertTrue(SignInIdGenerator.isWellFormed(id));
    }

    @Test
    void encodedRandomPartDecodesToAtLeastThirtyTwoBytes() {
        String body = generator.generate().substring(3);
        byte[] decoded = Base64.getUrlDecoder().decode(body);
        assertEquals(SignInIdGenerator.ENTROPY_BYTES, decoded.length);
        assertTrue(decoded.length >= 32);
    }

    @Test
    void thousandIdsAreUnique() {
        Set<String> seen = new HashSet<>();
        for (int i = 0; i < 1000; i++) {
            assertTrue(seen.add(generator.generate()), "duplicate sign-in ID generated");
        }
    }

    @Test
    void rejectsMalformedCandidates() {
        String good = generator.generate();
        assertFalse(SignInIdGenerator.isWellFormed(null));
        assertFalse(SignInIdGenerator.isWellFormed(""));
        assertFalse(SignInIdGenerator.isWellFormed(good.substring(0, good.length() - 1)));
        assertFalse(SignInIdGenerator.isWellFormed("se-" + good.substring(3)));
        assertFalse(SignInIdGenerator.isWellFormed("SE-" + "a".repeat(42) + "+"));
        assertFalse(SignInIdGenerator.isWellFormed("SE-" + "a".repeat(42) + "/"));
        assertFalse(SignInIdGenerator.isWellFormed(good + " "));
    }
}
