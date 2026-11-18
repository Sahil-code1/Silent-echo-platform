package com.silentecho.silentecho_backend.evidence;

import com.silentecho.silentecho_backend.exception.ApiException;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.*;

class EvidenceValidatorTest {

    private final EvidenceValidator validator = new EvidenceValidator();

    private static byte[] pdf() {
        return "%PDF-1.7\n1 0 obj<<>>endobj\n".getBytes(StandardCharsets.US_ASCII);
    }

    private static byte[] png() {
        return new byte[]{(byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A, 0, 0, 0, 13};
    }

    private static byte[] jpg() {
        return new byte[]{(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xE0, 0, 16};
    }

    private static byte[] txt() {
        return "Plain text evidence, with accents: café".getBytes(StandardCharsets.UTF_8);
    }

    @Test
    void acceptsEachAllowedTypeWithMatchingSignature() {
        EvidenceValidator.Validated p = validator.validate("notes.pdf", "application/pdf", pdf());
        assertEquals(EvidenceValidator.Kind.PDF, p.kind());
        assertEquals("application/pdf", p.contentType());
        assertEquals(EvidenceValidator.Kind.PNG, validator.validate("shot.png", "image/png", png()).kind());
        assertEquals(EvidenceValidator.Kind.JPG, validator.validate("photo.JPEG", "image/jpeg", jpg()).kind());
        assertEquals(EvidenceValidator.Kind.TXT, validator.validate("log.txt", "text/plain", txt()).kind());
    }

    @Test
    void rejectsExecutableAndOtherExtensions() {
        ApiException e = assertThrows(ApiException.class,
                () -> validator.validate("setup.exe", "application/octet-stream", new byte[]{'M', 'Z', 0, 0}));
        assertEquals(400, e.getStatus());
        assertThrows(ApiException.class, () -> validator.validate("run.sh", null, txt()));
        assertThrows(ApiException.class, () -> validator.validate("page.html", "text/html", txt()));
    }

    @Test
    void rejectsExecutableRenamedAsPdf() {
        byte[] exe = new byte[]{'M', 'Z', (byte) 0x90, 0, 3, 0, 0, 0};
        assertThrows(ApiException.class, () -> validator.validate("invoice.pdf", "application/pdf", exe));
    }

    @Test
    void rejectsPngContentWithJpegBytes() {
        assertThrows(ApiException.class, () -> validator.validate("fake.png", "image/png", jpg()));
    }

    @Test
    void rejectsEmptyAndOversizedFiles() {
        assertThrows(ApiException.class, () -> validator.validate("a.txt", null, new byte[0]));
        byte[] tooBig = new byte[(int) EvidenceValidator.MAX_BYTES + 1];
        System.arraycopy(txt(), 0, tooBig, 0, txt().length);
        ApiException e = assertThrows(ApiException.class, () -> validator.validate("big.txt", null, tooBig));
        assertEquals(413, e.getStatus());
    }

    @Test
    void acceptsFileExactlyAtTheTwoMegabyteLimit() {
        byte[] exact = new byte[(int) EvidenceValidator.MAX_BYTES];
        System.arraycopy(pdf(), 0, exact, 0, pdf().length);
        assertEquals(EvidenceValidator.Kind.PDF, validator.validate("max.pdf", "application/pdf", exact).kind());
    }

    @Test
    void txtMustBeUtf8WithoutNulBytes() {
        assertThrows(ApiException.class, () -> validator.validate("a.txt", null, new byte[]{'h', 0, 'i'}));
        assertThrows(ApiException.class, () -> validator.validate("b.txt", null, new byte[]{'o', 'k', (byte) 0xC3, 0x28}));
    }

    @Test
    void declaredContentTypeMustAgreeWithExtensionUnlessGeneric() {
        assertThrows(ApiException.class, () -> validator.validate("notes.pdf", "image/png", pdf()));
        assertDoesNotThrow(() -> validator.validate("notes.pdf", "application/octet-stream", pdf()));
        assertDoesNotThrow(() -> validator.validate("notes.pdf", "", pdf()));
    }

    @Test
    void sanitisesPathTraversalAndControlCharacters() {
        assertEquals("passwd.txt", validator.sanitiseFilename("../../etc/passwd.txt"));
        assertEquals("evil.png", validator.sanitiseFilename("..\\..\\evil.png"));
        String name = validator.sanitiseFilename("re\u0000port<script>.pdf");
        assertFalse(name.contains("/"));
        assertFalse(name.contains("\\"));
        assertFalse(name.contains(".."));
        assertTrue(name.endsWith(".pdf"));
    }

    @Test
    void truncatesLongNamesWhileKeepingExtension() {
        String longBase = "x".repeat(300);
        String safe = validator.sanitiseFilename(longBase + ".pdf");
        assertTrue(safe.length() <= EvidenceValidator.MAX_FILENAME_CHARS);
        assertTrue(safe.endsWith(".pdf"));
    }

    @Test
    void rejectsBlankOrNullNames() {
        assertThrows(ApiException.class, () -> validator.sanitiseFilename(null));
        assertThrows(ApiException.class, () -> validator.sanitiseFilename("   "));
    }
}
