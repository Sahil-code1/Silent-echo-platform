package com.silentecho.silentecho_backend.controller;

import com.silentecho.silentecho_backend.service.EvidenceService;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

import java.nio.charset.StandardCharsets;

/** Builds download responses that force an attachment and forbid MIME sniffing. */
final class EvidenceResponses {

    private EvidenceResponses() {
    }

    static ResponseEntity<byte[]> attachment(EvidenceService.Download download) {
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.attachment().filename(download.filename(), StandardCharsets.UTF_8).build().toString())
                .header("X-Content-Type-Options", "nosniff")
                .contentType(MediaType.parseMediaType(download.contentType()))
                .contentLength(download.bytes().length)
                .body(download.bytes());
    }
}
