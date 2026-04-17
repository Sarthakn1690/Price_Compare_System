package com.pricecomparison.service;
import com.pricecomparison.dto.PriceResponse;
import com.pricecomparison.dto.ProductResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.*;
import java.util.stream.Collectors;

@Service
@Slf4j
public class ProductSearchService {

    private final RestTemplate restTemplate;

    @Value("${serpapi.key:}")
    private String serpApiKey;

    @Value("${serpapi.url:https://serpapi.com/search.json}")
    private String serpApiUrl;

    // All 10 supported Indian e-commerce platforms (display names)
    private static final List<String> SUPPORTED_PLATFORMS = List.of(
        "Amazon", "Flipkart", "Myntra", "Ajio", "Nykaa",
        "Meesho", "Snapdeal", "Croma", "TataCliq", "RelianceDigital"
    );

    // Platform mapping: keyword (lowercase) → display name
    // Order matters for contains-based matching — more specific keys first
    private static final Map<String, String> PLATFORM_MAPPING = Map.ofEntries(
        Map.entry("amazon", "AMAZON"),
        Map.entry("amazon.in", "AMAZON"),
        Map.entry("flipkart", "FLIPKART"),
        Map.entry("flipkart.com", "FLIPKART"),
        Map.entry("myntra", "MYNTRA"),
        Map.entry("myntra.com", "MYNTRA"),
        Map.entry("ajio", "AJIO"),
        Map.entry("ajio.com", "AJIO"),
        Map.entry("nykaa", "NYKAA"),
        Map.entry("nykaa.com", "NYKAA"),
        Map.entry("meesho", "MEESHO"),
        Map.entry("meesho.com", "MEESHO"),
        Map.entry("snapdeal", "SNAPDEAL"),
        Map.entry("snapdeal.com", "SNAPDEAL"),
        Map.entry("croma", "CROMA"),
        Map.entry("croma.com", "CROMA"),
        Map.entry("tata cliq", "TATACLIQ"),
        Map.entry("tatacliq", "TATACLIQ"),
        Map.entry("tatacliq.com", "TATACLIQ"),
        Map.entry("reliance digital", "RELIANCEDIGITAL"),
        Map.entry("reliancedigital", "RELIANCEDIGITAL"),
        Map.entry("reliancedigital.in", "RELIANCEDIGITAL"),
        Map.entry("jiomart", "JIOMART"),
        Map.entry("vijay sales", "VIJAYSALES"),
        Map.entry("vijaysales", "VIJAYSALES")
    );

    public ProductSearchService(RestTemplate restTemplate) {
        this.restTemplate = restTemplate;
    }

    /**
     * Search for products using SerpAPI Google Shopping.
     * Returns a ProductResponse with prices from multiple platforms.
     */
    public ProductResponse searchByQuery(String query) {
        if (query == null || query.trim().isEmpty()) {
            throw new IllegalArgumentException("Query cannot be empty");
        }

        // Validate API key early
        if (serpApiKey == null || serpApiKey.isBlank()) {
            throw new IllegalStateException(
                "SerpAPI key not configured. Add serpapi.key to application.properties"
            );
        }

        try {
            log.info("Searching for product: '{}' using SerpAPI Google Shopping", query);

            // Build SerpAPI URL with expanded parameters
            String url = UriComponentsBuilder.fromHttpUrl(serpApiUrl)
                    .queryParam("q", query)
                    .queryParam("engine", "google_shopping")
                    .queryParam("api_key", serpApiKey)
                    .queryParam("gl", "in")          // Country: India
                    .queryParam("hl", "en")          // Language: English
                    .queryParam("num", "20")         // Request 20 results for broader platform coverage
                    .queryParam("tbs", "p_ord:p")    // Sort by price low-to-high
                    .toUriString();

            // Call SerpAPI
            @SuppressWarnings({"unchecked", "null"})
            Map<String, Object> response = restTemplate.getForObject(url, Map.class);

            if (response == null) {
                log.warn("Empty response from SerpAPI for query: '{}'", query);
                return createEmptyResponse(query);
            }

            // Parse shopping results array
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> shoppingResults =
                    (List<Map<String, Object>>) response.get("shopping_results");

            if (shoppingResults == null || shoppingResults.isEmpty()) {
                log.warn("No shopping_results found in SerpAPI response for query: '{}'", query);
                return createEmptyResponse(query);
            }

            log.info("SerpAPI returned {} shopping results for query: '{}'",
                    shoppingResults.size(), query);

            @SuppressWarnings("unchecked")
            List<Map<String, Object>> organicResults =
                    (List<Map<String, Object>>) response.get("organic_results");

            return mapToProductResponse(query, shoppingResults, organicResults);

        } catch (IllegalStateException e) {
            // Re-throw config errors — don't swallow them
            throw e;
        } catch (Exception e) {
            log.error("Error searching for product '{}': {}", query, e.getMessage(), e);
            return createEmptyResponse(query);
        }
    }

    /**
     * Map SerpAPI shopping results to a unified ProductResponse.
     * Keeps only the first (lowest-price) result per platform.
     * Fills in missing platforms with availability=false placeholders.
     */
    private ProductResponse mapToProductResponse(String query, List<Map<String, Object>> results, List<Map<String, Object>> organicResults) {
        // Use first result for product name and image
        Map<String, Object> firstResult = results.get(0);
        String name = safeString(firstResult.get("title"), query);
        String imageUrl = safeString(firstResult.get("thumbnail"), "");

        // Collect prices — keep only first occurrence per platform (results are already
        // sorted by price low-to-high from tbs=p_ord:p, so first = cheapest)
        Map<String, PriceResponse> platformPriceMap = new LinkedHashMap<>();

        for (Map<String, Object> result : results) {
            PriceResponse priceResponse = mapToPriceResponse(result);
            if (priceResponse != null) {
                String platformKey = priceResponse.getPlatform().toLowerCase();
                if (!platformPriceMap.containsKey(platformKey)) {
                    platformPriceMap.put(platformKey, priceResponse);
                }
            }
        }

        // Supplement URLs from organic results for missing ones
        if (organicResults != null) {
            for (Map<String, Object> org : organicResults) {
                String link = safeString(org.get("link"), "");
                if (link.contains("amazon.in")) {
                    PriceResponse p = platformPriceMap.get("amazon");
                    if (p != null && (p.getProductUrl() == null || p.getProductUrl().isEmpty())) {
                        p.setProductUrl(link);
                    }
                } else if (link.contains("flipkart.com")) {
                    PriceResponse p = platformPriceMap.get("flipkart");
                    if (p != null && (p.getProductUrl() == null || p.getProductUrl().isEmpty())) {
                        p.setProductUrl(link);
                    }
                }
            }
        }

        log.info("Found prices from {} unique platforms: {}",
                platformPriceMap.size(),
                platformPriceMap.keySet().stream().collect(Collectors.joining(", ")));

        // Build the prices list from the map
        List<PriceResponse> prices = new ArrayList<>(platformPriceMap.values());

        // Sort by price ascending (lowest first)
        prices.sort((a, b) -> {
            if (a.getPrice() == null) return 1;
            if (b.getPrice() == null) return -1;
            return a.getPrice().compareTo(b.getPrice());
        });

        // Determine best price (lowest available)
        PriceResponse bestPrice = prices.stream()
                .filter(p -> p.getAvailability() != null && p.getAvailability())
                .filter(p -> p.getPrice() != null && p.getPrice().compareTo(BigDecimal.ZERO) > 0)
                .findFirst()
                .orElse(null);

        // Calculate percent savings relative to the highest price
        if (bestPrice != null && prices.size() > 1) {
            BigDecimal highestPrice = prices.stream()
                    .filter(p -> p.getPrice() != null && p.getPrice().compareTo(BigDecimal.ZERO) > 0)
                    .map(PriceResponse::getPrice)
                    .max(BigDecimal::compareTo)
                    .orElse(BigDecimal.ZERO);

            if (highestPrice.compareTo(BigDecimal.ZERO) > 0) {
                for (PriceResponse p : prices) {
                    if (p.getPrice() != null && p.getPrice().compareTo(BigDecimal.ZERO) > 0) {
                        int savings = highestPrice.subtract(p.getPrice())
                                .multiply(BigDecimal.valueOf(100))
                                .divide(highestPrice, 0, RoundingMode.HALF_UP)
                                .intValue();
                        p.setPercentSavings(Math.max(savings, 0));
                    }
                }
            }
        }

        // Fill in missing supported platforms with availability=false placeholders
        fillMissingPlatforms(prices, platformPriceMap);

        // Determine comparison status
        long livePlatformCount = prices.stream()
                .filter(p -> "live".equals(p.getSource()))
                .count();
        String status = livePlatformCount >= 2 ? "READY" : "PARTIAL";

        return ProductResponse.builder()
                .id(System.currentTimeMillis())
                .name(name)
                .brand("")
                .category("")
                .imageUrl(imageUrl)
                .specifications(Map.of())
                .prices(prices)
                .bestPrice(bestPrice)
                .comparisonStatus(status)
                .build();
    }

    /**
     * Fill in any of the 10 supported platforms that weren't found in search results
     * with availability=false, price=0 placeholders so the frontend always sees all platforms.
     */
    private void fillMissingPlatforms(List<PriceResponse> prices, Map<String, PriceResponse> foundPlatforms) {
        for (String platform : SUPPORTED_PLATFORMS) {
            if (!foundPlatforms.containsKey(platform.toLowerCase())) {
                prices.add(PriceResponse.builder()
                        .id(generateUniqueId(platform))
                        .platform(platform)
                        .price(BigDecimal.ZERO)
                        .currency("INR")
                        .productUrl("")
                        .availability(false)
                        .source("fallback")
                        .percentSavings(0)
                        .build());
            }
        }
    }

    /**
     * Detect and normalize platform name from SerpAPI source string.
     * Uses contains-based matching against PLATFORM_MAPPING.
     * Unknown sources are returned as-is (not discarded).
     */
    private String detectPlatform(String source) {
        if (source == null || source.isEmpty()) {
            return "Unknown";
        }

        String lower = source.toLowerCase().trim();

        // Check contains-based matching
        for (Map.Entry<String, String> entry : PLATFORM_MAPPING.entrySet()) {
            if (lower.contains(entry.getKey())) {
                return entry.getValue();
            }
        }

        // Handle specific cases if needed or return as-is
        return source.trim();
    }

    /**
     * Map a single SerpAPI result to a PriceResponse.*/
    private PriceResponse mapToPriceResponse(Map<String, Object> result) {
        try {
            String source = safeString(result.get("source"), "");
            String priceStr = safeString(result.get("price"), "");

            // DEBUG: log all keys from SerpAPI result to identify the correct URL field
            log.info("SerpAPI result keys for source='{}': {}", source, result.keySet());

            // Try "link" first, then "product_link" — SerpAPI shopping results may use either
            String link = safeString(result.get("link"), "");
            if (link.isEmpty()) {
                link = safeString(result.get("product_link"), "");
            }
            log.info("  -> resolved link='{}' for source='{}'", link, source);

            // Extract platform from source
            String platform = detectPlatform(source);

            // Parse price
            BigDecimal parsedPrice = parsePrice(priceStr);

            // Only include results with valid prices
            if (parsedPrice.compareTo(BigDecimal.ZERO) <= 0) {
                return null;
            }
            // Validate and clean the link
            String cleanLink = "";
            if (link != null && (link.startsWith("http://") || link.startsWith("https://"))) {
                cleanLink = link;
            }

            return PriceResponse.builder()
                    .id(generateUniqueId(platform))
                    .platform(platform)
                    .price(parsedPrice)
                    .currency("INR")
                    .productUrl(cleanLink)
                    .availability(true)
                    .source("live")
                    .percentSavings(0)
                    .build();

        } catch (Exception e) {
            log.warn("Failed to parse price result: {}", e.getMessage());
            return null;
        }
    }

    /**
     * Parse price string to BigDecimal.
     * Handles formats: "₹15,999", "₹15999", "$199.99", "15,999.00", "1 299", etc.
     */
    private BigDecimal parsePrice(String priceStr) {
        if (priceStr == null || priceStr.trim().isEmpty()) {
            return BigDecimal.ZERO;
        }

        try {
            // Strip everything except digits and dots
            String cleaned = priceStr
                    .replaceAll("[^0-9.]", "")
                    .trim();

            if (cleaned.isEmpty()) {
                return BigDecimal.ZERO;
            }

            // Handle multiple dots (e.g., from bad parsing) — keep only the last one as decimal
            long dotCount = cleaned.chars().filter(c -> c == '.').count();
            if (dotCount > 1) {
                int lastDot = cleaned.lastIndexOf('.');
                cleaned = cleaned.substring(0, lastDot).replace(".", "") + cleaned.substring(lastDot);
            }

            return new BigDecimal(cleaned).setScale(2, RoundingMode.HALF_UP);

        } catch (Exception e) {
            log.warn("Failed to parse price '{}': {}", priceStr, e.getMessage());
            return BigDecimal.ZERO;
        }
    }

    /**
     * Create an empty response with all platforms showing as unavailable.
     */
    private ProductResponse createEmptyResponse(String query) {
        List<PriceResponse> fallbackPrices = SUPPORTED_PLATFORMS.stream()
                .map(platform -> PriceResponse.builder()
                        .id(generateUniqueId(platform))
                        .platform(platform)
                        .price(BigDecimal.ZERO)
                        .currency("INR")
                        .productUrl("")
                        .availability(false)
                        .source("fallback")
                        .percentSavings(0)
                        .build())
                .toList();

        return ProductResponse.builder()
                .id(System.currentTimeMillis())
                .name(query)
                .brand("")
                .category("")
                .imageUrl("")
                .specifications(Map.of())
                .prices(fallbackPrices)
                .bestPrice(null)
                .comparisonStatus("PARTIAL")
                .build();
    }

    private String safeString(Object value, String defaultValue) {
        if (value == null) return defaultValue;
        String str = value.toString();
        return str.isEmpty() || "null".equalsIgnoreCase(str) ? defaultValue : str;
    }

    private Double parseDouble(Object value) {
        if (value == null) return null;
        try {
            return Double.parseDouble(value.toString());
        } catch (Exception e) {
            return null;
        }
    }

    private Integer parseInteger(Object value) {
        if (value == null) return null;
        try {
            String cleaned = value.toString().replaceAll("[^0-9]", "");
            return cleaned.isEmpty() ? null : Integer.parseInt(cleaned);
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * Generate a unique ID for a PriceResponse based on platform name and current time.
     */
    private long generateUniqueId(String platform) {
        return System.nanoTime() + platform.hashCode();
    }
}
