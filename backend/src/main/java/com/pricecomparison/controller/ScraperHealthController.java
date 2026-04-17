package com.pricecomparison.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/health")
@RequiredArgsConstructor
public class ScraperHealthController {

    @Value("${serpapi.key:}")
    private String serpApiKey;

    @GetMapping("/scrapers")
    public ResponseEntity<Map<String, Object>> getScraperHealth() {
        boolean serpApiConfigured = serpApiKey != null && !serpApiKey.trim().isEmpty();
        return ResponseEntity.ok(Map.of(
                "serpApiConfigured", serpApiConfigured,
                "status", serpApiConfigured ? "OK" : "WARNING",
                "platformsCount", 10,
                "message", serpApiConfigured ? "SerpAPI configured and operational" : "SerpAPI key not configured"
        ));
    }
}
