package com.silentecho.silentecho_backend.evidence;

import com.silentecho.silentecho_backend.exception.ApiException;

import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.util.Locale;

/**
 * Server-side evidence checks. Browser-supplied names and content types are not trusted.
 * A file is accepted only when all of these hold:
 * the sanitised extension is on the allow-list (pdf, jpg, jpeg, png, txt), the size is
 * between 1 byte and 2 MB, the magic bytes match the extension, and a declared content
 * type, if present, agrees with the extension.
 */
public final class EvidenceValidator {

    public static final long MAX_BYTES = 2L * 1024 * 1024;
    public static final int MAX_FILENAME_CHARS = 120;

    public enum Kind {
        PDF("application/pdf"),
        JPG("image/jpeg"),
        PNG("image/png"),
        TXT("text/plain");

        private final String contentType;

        Kind(String contentType) {
            this.contentType = contentType;
        }

        public String contentType() {
            return contentType;
        }
    }

    public record Validated(String safeFilename, Kind kind, String contentType) {
    }

    public Validated validate(String originalFilename, String declaredContentType, byte[] content) {
        if (content == null || content.length == 0) {
            throw new ApiException(400, "The selected file is empty.");
        }
        if (content.length > MAX_BYTES) {
            throw new ApiException(413, "Evidence files must be 2 MB or smaller.");
        }
        String safeName = sanitiseFilename(originalFilename);
        Kind kind = kindFromName(safeName);
        if (!hasValidSignature(kind, content)) {
            throw new ApiException(400, "The file contents do not match its extension.");
        }
        if (declaredContentType != null && !declaredContentType.isBlank()
                && !"application/octet-stream".equalsIgnoreCase(declaredContentType.trim())
                && !declaredMatches(kind, declaredContentType.trim().toLowerCase(Locale.ROOT))) {
            throw new ApiException(400, "The declared file type does not match the file.");
        }
        return new Validated(safeName, kind, kind.contentType());
    }

    /** Strips any path, control characters and unsafe symbols, and bounds the length. */
    public String sanitiseFilename(String raw) {
        String name = raw == null ? "" : raw;
        int slash = Math.max(name.lastIndexOf('/'), name.lastIndexOf('\\'));
        name = name.substring(slash + 1);
        name = name.replaceAll("\\p{Cntrl}", "").trim();
        name = name.replaceAll("[^A-Za-z0-9._ -]", "_");
        while (name.startsWith(".")) {
            name = name.substring(1);
        }
        while (name.contains("..")) {
            name = name.replace("..", "_");
        }
        if (name.length() > MAX_FILENAME_CHARS) {
            int dot = name.lastIndexOf('.');
            String ext = dot > 0 ? name.substring(dot) : "";
            name = name.substring(0, MAX_FILENAME_CHARS - ext.length()) + ext;
        }
        if (name.isBlank()) {
            throw new ApiException(400, "The file needs a name.");
        }
        return name;
    }

    private Kind kindFromName(String name) {
        int dot = name.lastIndexOf('.');
        String ext = dot < 0 ? "" : name.substring(dot + 1).toLowerCase(Locale.ROOT);
        return switch (ext) {
            case "pdf" -> Kind.PDF;
            case "jpg", "jpeg" -> Kind.JPG;
            case "png" -> Kind.PNG;
            case "txt" -> Kind.TXT;
            default -> throw new ApiException(400, "Only PDF, JPG, PNG and TXT files are allowed.");
        };
    }

    boolean hasValidSignature(Kind kind, byte[] c) {
        return switch (kind) {
            case PDF -> startsWith(c, 0x25, 0x50, 0x44, 0x46, 0x2D);                // %PDF-
            case JPG -> startsWith(c, 0xFF, 0xD8, 0xFF);
            case PNG -> startsWith(c, 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A);
            case TXT -> isPlainUtf8Text(c);
        };
    }

    private static boolean startsWith(byte[] content, int... signature) {
        if (content.length < signature.length) {
            return false;
        }
        for (int i = 0; i < signature.length; i++) {
            if ((content[i] & 0xFF) != signature[i]) {
                return false;
            }
        }
        return true;
    }

    private static boolean isPlainUtf8Text(byte[] content) {
        for (byte b : content) {
            if (b == 0) {
                return false;
            }
        }
        try {
            StandardCharsets.UTF_8.newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT)
                    .decode(ByteBuffer.wrap(content));
            return true;
        } catch (CharacterCodingException e) {
            return false;
        }
    }

    private static boolean declaredMatches(Kind kind, String declared) {
        return switch (kind) {
            case PDF -> declared.equals("application/pdf") || declared.equals("application/x-pdf");
            case JPG -> declared.equals("image/jpeg") || declared.equals("image/jpg") || declared.equals("image/pjpeg");
            case PNG -> declared.equals("image/png");
            case TXT -> declared.startsWith("text/plain");
        };
    }
}
