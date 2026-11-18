package com.silentecho.silentecho_backend.evidence;

import com.silentecho.silentecho_backend.config.AppProperties;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.UUID;

/**
 * Stores encrypted evidence blobs in a directory outside the web root. Storage keys are random
 * UUIDs chosen by the server, never the user's filename, and every resolved path is checked to
 * stay inside the storage root.
 */
@Component
public class EvidenceStorage {

    private final Path root;

    public EvidenceStorage(AppProperties properties) {
        this.root = Path.of(properties.evidenceDir()).toAbsolutePath().normalize();
        // Fail at startup, not on the first upload, if the persistent volume is missing or read-only.
        try {
            Files.createDirectories(root);
            Path probe = Files.createTempFile(root, ".write-probe-", ".tmp");
            Files.delete(probe);
        } catch (IOException e) {
            throw new IllegalStateException("Evidence directory is missing or not writable: " + root, e);
        }
    }

    public String write(byte[] encryptedBlob) throws IOException {
        Files.createDirectories(root);
        String key = UUID.randomUUID().toString();
        Files.write(resolve(key), encryptedBlob, StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE);
        return key;
    }

    public byte[] read(String storageKey) throws IOException {
        return Files.readAllBytes(resolve(storageKey));
    }

    private Path resolve(String storageKey) {
        if (storageKey == null || !storageKey.matches("[0-9a-fA-F-]{36}")) {
            throw new IllegalArgumentException("Invalid storage key");
        }
        Path target = root.resolve(storageKey + ".bin").normalize();
        if (!target.startsWith(root)) {
            throw new IllegalArgumentException("Storage path escapes the evidence directory");
        }
        return target;
    }
}
