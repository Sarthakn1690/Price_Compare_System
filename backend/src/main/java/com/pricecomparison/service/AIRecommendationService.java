package com.pricecomparison.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.pricecomparison.dto.RecommendationResponse;
import com.pricecomparison.dto.RecommendationResponse.RecommendationType;
import com.pricecomparison.model.Price;
import com.pricecomparison.model.Product;
import com.pricecomparison.model.PriceHistory;
import com.pricecomparison.repository.PriceHistoryRepository;
import com.pricecomparison.repository.PriceRepository;
import com.pricecomparison.repository.ProductRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.*;

@Service
@RequiredArgsConstructor
@Slf4j
public class AIRecommendationService {

    private final PriceHistoryRepository priceHistoryRepository;
    private final PriceRepository priceRepository;
    private final ProductRepository productRepository;
    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;

    @Value("${openai.api.key:}")
    private String openaiApiKey;

    @Value("${anthropic.api.key:}")
    private String anthropicApiKey;

    private static final String CLAUDE_API_URL = "https://api.anthropic.com/v1/messages";
    private static final String OPENAI_API_URL = "https://api.openai.com/v1/chat/completions";
    private static final Duration API_TIMEOUT = Duration.ofSeconds(5);

    /* ════════════════════════════════════════════════════════════════════
     *  PUBLIC ENTRY POINT
     * ════════════════════════════════════════════════════════════════════ */

    /**
     * Generate a buy/wait recommendation for a product.
     * Strategy: try Claude → try OpenAI → fall back to rule-based logic.
     */
    public RecommendationResponse getRecommendation(Long productId) {
        // 1. Gather data
        List<PriceHistory> history = priceHistoryRepository.findByProductIdSince(
                productId, Instant.now().minus(14, ChronoUnit.DAYS));
        List<Price> currentPrices = priceRepository.findByProductIdOrderByPriceAsc(productId);

        if (currentPrices.isEmpty()) {
            return defaultRecommendation("No current price data");
        }

        BigDecimal lowestCurrent = currentPrices.get(0).getPrice();
        BigDecimal avgHistorical = averagePrice(history);
        boolean hasHistory = !history.isEmpty() && avgHistorical != null;

        // 2. If an LLM key is configured, attempt AI-powered recommendation
        boolean hasAnthropicKey = anthropicApiKey != null && !anthropicApiKey.isBlank();
        boolean hasOpenAIKey = openaiApiKey != null && !openaiApiKey.isBlank();

        if (hasAnthropicKey || hasOpenAIKey) {
            try {
                String prompt = buildAIPrompt(productId, lowestCurrent, history, currentPrices);
                String aiResponse = null;

                if (hasAnthropicKey) {
                    log.info("Calling Claude API for product {} recommendation", productId);
                    aiResponse = callClaudeAPI(prompt);
                }

                if (aiResponse == null && hasOpenAIKey) {
                    log.info("Calling OpenAI API for product {} recommendation", productId);
                    aiResponse = callOpenAIAPI(prompt);
                }

                if (aiResponse != null) {
                    RecommendationResponse parsed = parseAIResponse(aiResponse);
                    if (parsed != null) {
                        log.info("AI recommendation for product {}: {} (confidence: {}%)",
                                productId, parsed.getRecommendation(), parsed.getConfidenceScore());
                        return parsed;
                    }
                }

                log.warn("AI call returned null or unparseable response for product {}. Falling back to rules.", productId);
            } catch (Exception e) {
                log.warn("AI recommendation failed for product {}: {}. Falling back to rules.",
                        productId, e.getMessage());
            }
        }

        // 3. Fallback: rule-based recommendation
        return getRuleBasedRecommendation(lowestCurrent, avgHistorical, hasHistory, history);
    }

    /* ════════════════════════════════════════════════════════════════════
     *  AI PROMPT BUILDER
     * ════════════════════════════════════════════════════════════════════ */

    private String buildAIPrompt(Long productId, BigDecimal lowestCurrent,
                                  List<PriceHistory> history, List<Price> currentPrices) {
        // Get product name
        String productName = productRepository.findById(productId)
                .map(Product::getName)
                .orElse("Unknown Product");

        BigDecimal avgPrice = averagePrice(history);
        BigDecimal minPrice = history.stream()
                .map(PriceHistory::getPrice)
                .filter(p -> p.compareTo(BigDecimal.ZERO) > 0)
                .min(BigDecimal::compareTo)
                .orElse(lowestCurrent);
        BigDecimal maxPrice = history.stream()
                .map(PriceHistory::getPrice)
                .filter(p -> p.compareTo(BigDecimal.ZERO) > 0)
                .max(BigDecimal::compareTo)
                .orElse(lowestCurrent);
        long platformCount = currentPrices.stream()
                .filter(p -> p.getPrice().compareTo(BigDecimal.ZERO) > 0)
                .count();

        return """
                You are a shopping advisor. Analyze this product pricing data and give a buy recommendation.
                
                Product: %s
                Current lowest price: ₹%s
                14-day average price: ₹%s
                14-day lowest: ₹%s
                14-day highest: ₹%s
                Number of platforms selling: %d
                
                Respond in this exact JSON format only (no extra text, no markdown fences):
                {
                  "recommendation": "BUY_NOW" or "WAIT_FOR_BETTER_PRICE" or "PRICE_INCREASING",
                  "confidenceScore": <number 0-100>,
                  "explanation": "<1-2 sentence explanation for the user>",
                  "predictedTrend": "<Stable or Increasing or Decreasing>"
                }
                """.formatted(
                productName,
                lowestCurrent.toPlainString(),
                avgPrice != null ? avgPrice.toPlainString() : lowestCurrent.toPlainString(),
                minPrice.toPlainString(),
                maxPrice.toPlainString(),
                platformCount
        );
    }

    /* ════════════════════════════════════════════════════════════════════
     *  CLAUDE (ANTHROPIC) API
     * ════════════════════════════════════════════════════════════════════ */

    /**
     * Call Claude API to get a recommendation.
     * Returns the text content, or null on any error.
     */
    private String callClaudeAPI(String prompt) {
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.set("x-api-key", anthropicApiKey);
            headers.set("anthropic-version", "2023-06-01");

            Map<String, Object> message = Map.of("role", "user", "content", prompt);
            Map<String, Object> body = Map.of(
                    "model", "claude-haiku-4-5-20251001",
                    "max_tokens", 300,
                    "messages", List.of(message)
            );

            HttpEntity<Map<String, Object>> request = new HttpEntity<>(body, headers);

            ResponseEntity<String> response = restTemplate.exchange(
                    CLAUDE_API_URL, HttpMethod.POST, request, String.class);

            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                JsonNode root = objectMapper.readTree(response.getBody());
                JsonNode content = root.path("content");
                if (content.isArray() && !content.isEmpty()) {
                    String text = content.get(0).path("text").asText("");
                    log.debug("Claude response: {}", text);
                    return text.isBlank() ? null : text;
                }
            }

            log.warn("Claude API returned unexpected response: status={}", response.getStatusCode());
            return null;

        } catch (Exception e) {
            log.error("Claude API call failed: {}", e.getMessage());
            return null;
        }
    }

    /* ════════════════════════════════════════════════════════════════════
     *  OPENAI API
     * ════════════════════════════════════════════════════════════════════ */

    /**
     * Call OpenAI API to get a recommendation.
     * Returns the text content, or null on any error.
     */
    private String callOpenAIAPI(String prompt) {
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.setBearerAuth(openaiApiKey);

            Map<String, Object> message = Map.of("role", "user", "content", prompt);
            Map<String, Object> body = Map.of(
                    "model", "gpt-3.5-turbo",
                    "max_tokens", 300,
                    "messages", List.of(message)
            );

            HttpEntity<Map<String, Object>> request = new HttpEntity<>(body, headers);

            ResponseEntity<String> response = restTemplate.exchange(
                    OPENAI_API_URL, HttpMethod.POST, request, String.class);

            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                JsonNode root = objectMapper.readTree(response.getBody());
                JsonNode choices = root.path("choices");
                if (choices.isArray() && !choices.isEmpty()) {
                    String text = choices.get(0).path("message").path("content").asText("");
                    log.debug("OpenAI response: {}", text);
                    return text.isBlank() ? null : text;
                }
            }

            log.warn("OpenAI API returned unexpected response: status={}", response.getStatusCode());
            return null;

        } catch (Exception e) {
            log.error("OpenAI API call failed: {}", e.getMessage());
            return null;
        }
    }

    /* ════════════════════════════════════════════════════════════════════
     *  AI RESPONSE PARSER
     * ════════════════════════════════════════════════════════════════════ */

    /**
     * Parse the JSON response from the AI into a RecommendationResponse.
     * Returns null if parsing fails.
     */
    private RecommendationResponse parseAIResponse(String aiText) {
        try {
            // Strip markdown code fences if AI wraps response
            String cleaned = aiText.trim();
            if (cleaned.startsWith("```")) {
                cleaned = cleaned.replaceAll("^```(?:json)?\\s*", "").replaceAll("\\s*```$", "");
            }

            JsonNode node = objectMapper.readTree(cleaned);

            String recStr = node.path("recommendation").asText("BUY_NOW")
                    .toUpperCase().replace(" ", "_");
            RecommendationType type;
            try {
                type = RecommendationType.valueOf(recStr);
            } catch (IllegalArgumentException e) {
                log.warn("Unknown recommendation type from AI: '{}', defaulting to BUY_NOW", recStr);
                type = RecommendationType.BUY_NOW;
            }

            int confidence = node.path("confidenceScore").asInt(50);
            confidence = Math.max(0, Math.min(100, confidence));

            String explanation = node.path("explanation").asText("AI-generated recommendation");
            String trend = node.path("predictedTrend").asText("Unknown");

            return RecommendationResponse.builder()
                    .recommendation(type)
                    .confidenceScore(confidence)
                    .explanation(explanation)
                    .predictedTrend(trend)
                    .build();

        } catch (Exception e) {
            log.warn("Failed to parse AI response JSON: {}", e.getMessage());
            return null;
        }
    }

    /* ════════════════════════════════════════════════════════════════════
     *  RULE-BASED FALLBACK
     * ════════════════════════════════════════════════════════════════════ */

    /**
     * Original rule-based recommendation logic.
     * Used as fallback when no LLM API key is configured or API call fails.
     */
    private RecommendationResponse getRuleBasedRecommendation(
            BigDecimal lowestCurrent, BigDecimal avgHistorical,
            boolean hasHistory, List<PriceHistory> history) {

        if (!hasHistory || avgHistorical == null) {
            return RecommendationResponse.builder()
                    .recommendation(RecommendationType.BUY_NOW)
                    .confidenceScore(50)
                    .explanation("Insufficient history. Current best price: ₹" + lowestCurrent)
                    .predictedTrend("Unknown - need more data")
                    .build();
        }

        int trend = lowestCurrent.compareTo(avgHistorical);
        double percentDiff = avgHistorical.doubleValue() > 0
                ? (lowestCurrent.subtract(avgHistorical).doubleValue() / avgHistorical.doubleValue()) * 100
                : 0;

        RecommendationType type;
        String explanation;
        int confidence;

        if (trend <= 0 && percentDiff <= -5) {
            type = RecommendationType.BUY_NOW;
            explanation = String.format("Price is %.1f%% below 14-day average. Good time to buy.", -percentDiff);
            confidence = Math.min(90, 70 + (int) (-percentDiff));
        } else if (trend > 0 && percentDiff > 5) {
            type = RecommendationType.PRICE_INCREASING;
            explanation = String.format("Price is %.1f%% above recent average. Consider waiting.", percentDiff);
            confidence = 75;
        } else {
            type = RecommendationType.WAIT_FOR_BETTER_PRICE;
            explanation = "Price is near average. Waiting may yield a better deal.";
            confidence = 65;
        }

        return RecommendationResponse.builder()
                .recommendation(type)
                .confidenceScore(Math.min(95, confidence))
                .explanation(explanation)
                .predictedTrend(trend <= 0 ? "Stable or decreasing" : "Increasing")
                .build();
    }

    /* ════════════════════════════════════════════════════════════════════
     *  UTILITY METHODS
     * ════════════════════════════════════════════════════════════════════ */

    private BigDecimal averagePrice(List<PriceHistory> history) {
        if (history.isEmpty()) return null;
        BigDecimal sum = history.stream()
                .map(PriceHistory::getPrice)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        return sum.divide(BigDecimal.valueOf(history.size()), 2, RoundingMode.HALF_UP);
    }

    private RecommendationResponse defaultRecommendation(String reason) {
        return RecommendationResponse.builder()
                .recommendation(RecommendationType.BUY_NOW)
                .confidenceScore(50)
                .explanation(reason)
                .predictedTrend("Unknown")
                .build();
    }
}
