package com.silentecho.silentecho_backend.controller;

import com.silentecho.silentecho_backend.service.AiAnalysisService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * Optional advisory triage. Requires an authenticated session (enforced by SecurityConfig).
 * The caller opts in on each submission. The response is advisory only, and a human responder decides.
 */
@RestController
@RequestMapping("/api/ai")
public class AiAnalysisController {

    private final AiAnalysisService aiAnalysisService;

    public AiAnalysisController(AiAnalysisService aiAnalysisService) {
        this.aiAnalysisService = aiAnalysisService;
    }

    @PostMapping("/analyze")
    public ResponseEntity<?> analyze(@Valid @RequestBody AnalysisRequest request) {
        try {
            String suggestion = aiAnalysisService.analyzeReport(
                    request.title(), request.category(), request.description());
            return ResponseEntity.ok(Map.of("suggestion", suggestion, "humanReviewRequired", true));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.BAD_GATEWAY)
                    .body(Map.of("message", "AI analysis temporarily unavailable"));
        }
    }

    public record AnalysisRequest(
            @NotBlank @Size(max = 120) String title,
            @NotBlank @Size(max = 60) String category,
            @NotBlank @Size(max = 5000) String description) {
    }
}
