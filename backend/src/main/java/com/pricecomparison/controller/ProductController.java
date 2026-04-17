package com.pricecomparison.controller;

import com.pricecomparison.dto.*;
import com.pricecomparison.model.Price;
import com.pricecomparison.model.PriceHistory;
import com.pricecomparison.model.Product;
import com.pricecomparison.model.RecentSearch;
import com.pricecomparison.repository.PriceHistoryRepository;
import com.pricecomparison.repository.PriceRepository;
import com.pricecomparison.repository.ProductRepository;
import com.pricecomparison.repository.RecentSearchRepository;
import com.pricecomparison.security.AuthUtil;
import com.pricecomparison.service.ProductSearchService;
import com.pricecomparison.service.ProductService;
import com.pricecomparison.service.PriceTrackingService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.net.URI;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@RestController
@RequestMapping("/products")
@RequiredArgsConstructor
@Slf4j
public class ProductController {

    private final ProductService productService;
    private final ProductSearchService productSearchService;
    private final ProductRepository productRepository;
    private final PriceRepository priceRepository;
    private final PriceHistoryRepository priceHistoryRepository;
    private final PriceTrackingService priceTrackingService;
    private final RecentSearchRepository recentSearchRepository;

    /* ── In-memory search result cache (30 min TTL) ─────────────────── */

    private static final long CACHE_TTL_MS = 30 * 60 * 1000; // 30 minutes

    private record CachedResult(ProductResponse result, Instant cachedAt) {
        boolean isExpired() {
            return Instant.now().isAfter(cachedAt.plusMillis(CACHE_TTL_MS));
        }
    }

    private final ConcurrentHashMap<String, CachedResult> searchCache = new ConcurrentHashMap<>();

    /* ════════════════════════════════════════════════════════════════════
     *  GET /products/search?query=...
     *  Text-based product search — the main search endpoint
     * ════════════════════════════════════════════════════════════════════ */

    @GetMapping("/search")
    public ResponseEntity<ProductResponse> searchByQuery(@RequestParam(name = "query") String query) {
        String normalized = query.trim().toLowerCase();
        if (normalized.isEmpty()) {
            return ResponseEntity.badRequest().build();
        }

        // 1. Check cache
        CachedResult cached = searchCache.get(normalized);
        if (cached != null && !cached.isExpired()) {
            log.info("Cache hit for query: '{}'", query);
            return ResponseEntity.ok(cached.result());
        }

        log.info("Searching for product: '{}'", query);

        // 2. Call SerpAPI via ProductSearchService
        ProductResponse searchResult = productSearchService.searchByQuery(query.trim());

        // 3. Persist to DB — find or create the Product entity
        ProductResponse persisted = persistSearchResult(searchResult, query.trim());

        // 4. Update cache
        searchCache.put(normalized, new CachedResult(persisted, Instant.now()));

        // 5. Save recent search for authenticated user (best-effort, never blocks response)
        saveRecentSearch(query.trim(), persisted);

        return ResponseEntity.ok(persisted);
    }

    /* ════════════════════════════════════════════════════════════════════
     *  POST /products/search
     *  URL-based product search — extracts product name from URL
     * ════════════════════════════════════════════════════════════════════ */

    @PostMapping("/search")
    public ResponseEntity<ProductResponse> searchByUrl(@Valid @RequestBody ProductSearchRequest request) {
        String url = request.getUrl().trim();
        log.info("URL-based search: {}", url);

        // Extract a search query from the URL
        String extractedQuery = extractProductNameFromUrl(url);
        log.info("Extracted query from URL: '{}'", extractedQuery);

        // Delegate to the text search flow
        ProductResponse searchResult = productSearchService.searchByQuery(extractedQuery);
        ProductResponse persisted = persistSearchResult(searchResult, extractedQuery);

        // Cache it
        String normalized = extractedQuery.toLowerCase();
        searchCache.put(normalized, new CachedResult(persisted, Instant.now()));

        return ResponseEntity.ok(persisted);
    }

    /* ════════════════════════════════════════════════════════════════════
     *  GET /products/search/status/{productId}
     *  Polling endpoint for search status
     * ════════════════════════════════════════════════════════════════════ */

    @GetMapping("/search/status/{productId}")
    public ResponseEntity<Map<String, String>> getSearchStatus(@PathVariable Long productId) {
        if (!productRepository.existsById(productId)) {
            return ResponseEntity.ok(Map.of(
                    "status", "NOT_FOUND",
                    "message", "Product does not exist"
            ));
        }

        List<Price> prices = priceRepository.findByProductIdOrderByPriceAsc(productId);
        if (prices.isEmpty()) {
            return ResponseEntity.ok(Map.of(
                    "status", "PENDING",
                    "message", "Fetching prices from platforms..."
            ));
        }

        boolean hasPending = prices.stream()
                .anyMatch(p -> "pending".equalsIgnoreCase(p.getSource()));
        if (hasPending) {
            return ResponseEntity.ok(Map.of(
                    "status", "PENDING",
                    "message", "Still fetching from some platforms..."
            ));
        }

        long validPrices = prices.stream()
                .filter(p -> p.getPrice() != null && p.getPrice().compareTo(BigDecimal.ZERO) > 0)
                .filter(p -> !"fallback".equalsIgnoreCase(p.getSource()))
                .count();

        if (validPrices == 0) {
            return ResponseEntity.ok(Map.of(
                    "status", "PARTIAL",
                    "message", "No live prices found. Showing cached data."
            ));
        }

        return ResponseEntity.ok(Map.of(
                "status", "READY",
                "message", "Prices loaded from " + validPrices + " platforms"
        ));
    }

    /* ════════════════════════════════════════════════════════════════════
     *  Existing endpoints (unchanged)
     * ════════════════════════════════════════════════════════════════════ */

    @GetMapping("/{id}")
    public ResponseEntity<ProductResponse> getProduct(@PathVariable Long id) {
        return ResponseEntity.ok(productService.getProduct(id));
    }

    @GetMapping("/{id}/prices")
    public ResponseEntity<java.util.List<PriceResponse>> getPrices(@PathVariable Long id) {
        return ResponseEntity.ok(productService.getPrices(id));
    }

    @GetMapping("/{id}/history")
    public ResponseEntity<PriceHistoryResponse> getHistory(
            @PathVariable Long id,
            @RequestParam(required = false) Integer days,
            @RequestParam(required = false) String platform) {
        return ResponseEntity.ok(productService.getHistory(id, days, platform));
    }

    @GetMapping("/{id}/recommendation")
    public ResponseEntity<RecommendationResponse> getRecommendation(@PathVariable Long id) {
        return ResponseEntity.ok(productService.getRecommendation(id));
    }

    @PostMapping("/{id}/track")
    public ResponseEntity<Map<String, String>> track(@PathVariable Long id) {
        productService.trackProduct(id);
        return ResponseEntity.ok(Map.of("message", "Product added to watchlist"));
    }

    /* ════════════════════════════════════════════════════════════════════
     *  PRIVATE HELPERS
     * ════════════════════════════════════════════════════════════════════ */

    /**
     * Saves a RecentSearch record for the currently authenticated user.
     * Safe to call at any time — skips silently if:
     * - User is not authenticated
     * - Same query was already searched by this user today
     * Keeps only the last 20 searches per user (trims older ones).
     */
    private void saveRecentSearch(String query, ProductResponse result) {
        try {
            String userId = AuthUtil.requireEmail();

            // Skip if this exact query already exists for this user
            if (recentSearchRepository.existsByUserIdAndQuery(userId, query)) {
                log.debug("Recent search already exists for user '{}', query '{}'", userId, query);
                return;
            }

            // Build the RecentSearch record
            PriceResponse best = result.getBestPrice();
            RecentSearch rs = RecentSearch.builder()
                    .userId(userId)
                    .query(query)
                    .productName(result.getName())
                    .imageUrl(result.getImageUrl())
                    .productId(result.getId())
                    .bestPriceFound(best != null ? best.getPrice() : null)
                    .bestPlatform(best != null ? best.getPlatform() : null)
                    .build();
            recentSearchRepository.save(rs);

            // Trim to last 20 searches: delete excess oldest entries
            long count = recentSearchRepository.countByUserId(userId);
            if (count > 20) {
                List<Long> idsToDelete = recentSearchRepository.findIdsToTrim(userId, 20);
                if (!idsToDelete.isEmpty()) {
                    recentSearchRepository.deleteAllById(idsToDelete);
                    log.debug("Trimmed {} old recent searches for user '{}'", idsToDelete.size(), userId);
                }
            }

            log.debug("Saved recent search for user '{}': '{}'", userId, query);
        } catch (Exception e) {
            // Not authenticated or any other issue — silently skip
            log.debug("Skipping recent search save: {}", e.getMessage());
        }
    }

    /**
     * Persist a search result to the database.
     * Finds an existing Product by name or creates a new one,
     * then saves all prices and seeds initial price history.
     */
    private ProductResponse persistSearchResult(ProductResponse searchResult, String query) {
        try {
            // Find existing product by name, or create new
            String productName = searchResult.getName() != null && !searchResult.getName().isBlank()
                    ? searchResult.getName()
                    : query;

            Optional<Product> existingOpt = productRepository.findByNameIgnoreCase(productName);
            Product product;

            if (existingOpt.isPresent()) {
                product = existingOpt.get();
                // Update image if we got a better one
                if (searchResult.getImageUrl() != null && !searchResult.getImageUrl().isBlank()) {
                    product.setImageUrl(searchResult.getImageUrl());
                    productRepository.save(product);
                }
                log.info("Found existing product: id={}, name='{}'", product.getId(), product.getName());
            } else {
                product = Product.builder()
                        .name(productName)
                        .brand(searchResult.getBrand() != null ? searchResult.getBrand() : "")
                        .category(searchResult.getCategory() != null ? searchResult.getCategory() : "")
                        .imageUrl(searchResult.getImageUrl() != null ? searchResult.getImageUrl() : "")
                        .specifications(Map.of())
                        .build();
                product = productRepository.save(product);
                log.info("Created new product: id={}, name='{}'", product.getId(), product.getName());
            }

            // Clear existing prices and save fresh ones
            priceRepository.deleteByProductId(product.getId());

            List<PriceResponse> persistedPrices = new ArrayList<>();
            PriceResponse bestPrice = null;

            if (searchResult.getPrices() != null) {
                for (PriceResponse pr : searchResult.getPrices()) {
                    Price price = Price.builder()
                            .product(product)
                            .platform(pr.getPlatform())
                            .price(pr.getPrice() != null ? pr.getPrice() : BigDecimal.ZERO)
                            .currency(pr.getCurrency() != null ? pr.getCurrency() : "INR")
                            .productUrl(pr.getProductUrl() != null ? pr.getProductUrl() : "")
                            .availability(pr.getAvailability() != null ? pr.getAvailability() : false)
                            .source(pr.getSource() != null ? pr.getSource() : "live")
                            .build();
                    Price saved = priceRepository.save(price);

                    // Record price history for valid prices
                    if (pr.getPrice() != null && pr.getPrice().compareTo(BigDecimal.ZERO) > 0) {
                        priceHistoryRepository.save(PriceHistory.builder()
                                .product(product)
                                .platform(pr.getPlatform())
                                .price(pr.getPrice())
                                .build());
                    }

                    PriceResponse persisted = PriceResponse.builder()
                            .id(saved.getId())
                            .platform(saved.getPlatform())
                            .price(saved.getPrice())
                            .currency(saved.getCurrency())
                            .productUrl(saved.getProductUrl())
                            .availability(saved.getAvailability())
                            .recordedAt(saved.getRecordedAt())
                            .percentSavings(pr.getPercentSavings() != null ? pr.getPercentSavings() : 0)
                            .source(saved.getSource())
                            .build();
                    persistedPrices.add(persisted);

                    // Track best price
                    if (saved.getPrice() != null && saved.getPrice().compareTo(BigDecimal.ZERO) > 0
                            && !"fallback".equals(saved.getSource())) {
                        if (bestPrice == null || saved.getPrice().compareTo(bestPrice.getPrice()) < 0) {
                            bestPrice = persisted;
                        }
                    }
                }
            }

            log.info("Persisted {} prices for product {} ('{}')",
                    persistedPrices.size(), product.getId(), product.getName());

            return ProductResponse.builder()
                    .id(product.getId())
                    .name(product.getName())
                    .brand(product.getBrand())
                    .category(product.getCategory())
                    .imageUrl(product.getImageUrl())
                    .specifications(product.getSpecifications() != null ? product.getSpecifications() : Map.of())
                    .prices(persistedPrices)
                    .bestPrice(bestPrice)
                    .comparisonStatus(searchResult.getComparisonStatus())
                    .build();

        } catch (Exception e) {
            log.error("Failed to persist search result: {}", e.getMessage());
            // Return the original (non-persisted) result as fallback
            return searchResult;
        }
    }

    /**
     * Extract a product name from an e-commerce URL.
     * Falls back to using the full URL as the search query.
     */
    private String extractProductNameFromUrl(String url) {
        try {
            URI uri = URI.create(url);
            String host = uri.getHost() != null ? uri.getHost().toLowerCase() : "";
            String path = uri.getPath() != null ? uri.getPath() : "";

            if (host.contains("amazon")) {
                // Amazon URLs: /Product-Name/dp/ASIN or /dp/ASIN/
                String[] segments = path.split("/");
                for (int i = 0; i < segments.length; i++) {
                    if ("dp".equals(segments[i]) && i > 0 && !segments[i - 1].isEmpty()) {
                        return cleanUrlSegment(segments[i - 1]);
                    }
                }
                // Fallback: use first meaningful path segment
                for (String seg : segments) {
                    if (!seg.isBlank() && !"dp".equals(seg) && seg.length() > 3) {
                        return cleanUrlSegment(seg);
                    }
                }
            }

            if (host.contains("flipkart")) {
                // Flipkart URLs: /product-name/p/ITEMID
                String[] segments = path.split("/");
                for (int i = 0; i < segments.length; i++) {
                    if ("p".equals(segments[i]) && i > 0 && !segments[i - 1].isEmpty()) {
                        return cleanUrlSegment(segments[i - 1]);
                    }
                }
                for (String seg : segments) {
                    if (!seg.isBlank() && !"p".equals(seg) && seg.length() > 3) {
                        return cleanUrlSegment(seg);
                    }
                }
            }

            // Generic: use the longest path segment as product name
            String[] segments = path.split("/");
            String longest = "";
            for (String seg : segments) {
                if (seg.length() > longest.length()) {
                    longest = seg;
                }
            }
            if (!longest.isBlank()) {
                return cleanUrlSegment(longest);
            }

        } catch (Exception e) {
            log.warn("Failed to parse product URL '{}': {}", url, e.getMessage());
        }

        // Ultimate fallback: use the URL itself as query
        return url;
    }

    /**
     * Clean a URL path segment into a readable product name.
     * "Apple-iPhone-15-Pro-128GB" → "Apple iPhone 15 Pro 128GB"
     */
    private String cleanUrlSegment(String segment) {
        return segment.replace("-", " ").replace("_", " ").replace("%20", " ").trim();
    }
}
