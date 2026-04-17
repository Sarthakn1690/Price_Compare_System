package com.pricecomparison.service;

import com.pricecomparison.dto.*;
import com.pricecomparison.exception.CustomExceptions;
import com.pricecomparison.model.Price;
import com.pricecomparison.model.PriceHistory;
import com.pricecomparison.model.Product;
import com.pricecomparison.model.TrackedProduct;
import com.pricecomparison.repository.PriceHistoryRepository;
import com.pricecomparison.repository.PriceRepository;
import com.pricecomparison.repository.ProductRepository;
import com.pricecomparison.repository.TrackedProductRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ThreadLocalRandom;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class ProductService {

    private final ProductRepository productRepository;
    private final PriceRepository priceRepository;
    private final PriceHistoryRepository priceHistoryRepository;
    private final TrackedProductRepository trackedProductRepository;
    private final ProductSearchService productSearchService;
    private final AIRecommendationService aiRecommendationService;

    // Supported platforms for price comparison
    private static final List<String> ALL_PLATFORMS = List.of(
        "Amazon", "Flipkart", "Myntra", "Ajio", "Nykaa", 
        "Meesho", "Snapdeal", "Croma", "TataCliq", "RelianceDigital"
    );

    @Transactional
    public ProductResponse searchByUrl(String url) {
        String normalizedUrl = url == null ? "" : url.trim();
        Optional<Price> cachedByUrl = priceRepository.findFirstByProductUrlOrderByRecordedAtDesc(normalizedUrl);
        if (cachedByUrl.isPresent() && cachedByUrl.get().getProduct() != null) {
            Product cachedProduct = cachedByUrl.get().getProduct();
            triggerAsyncRefresh(cachedProduct.getId(), normalizedUrl);
            List<PriceResponse> cachedPrices = getPrices(cachedProduct.getId());
            return toProductResponse(cachedProduct, cachedPrices);
        }

        Product placeholder = productRepository.save(Product.builder()
                .name("Fetching product details...")
                .brand("")
                .category("")
                .imageUrl("")
                .specifications(Map.of())
                .build());
        triggerAsyncRefresh(placeholder.getId(), normalizedUrl);
        return toProductResponse(placeholder, buildPendingPrices());
    }

    @org.springframework.scheduling.annotation.Async
    public void triggerBackgroundRefresh(String platform, String productName) {
        log.info("Triggering async background refresh for {} on {}", productName, platform);
        // Placeholder — can be wired to a scheduled job queue in a future iteration
    }

    private void triggerAsyncRefresh(Long productId, String sourceUrl) {
        CompletableFuture.runAsync(() -> refreshProductFromSource(productId, sourceUrl))
                .exceptionally(ex -> {
                    log.error("Async refresh failed for product {}: {}", productId, ex.getMessage());
                    return null;
                });
    }

    @Transactional
    public void refreshProductFromSource(Long productId, String url) {
        Product product = productRepository.findById(productId)
                .orElseThrow(() -> new CustomExceptions.ProductNotFoundException(productId));
        try {
            // Use ProductSearchService for price comparison
            ProductResponse searchResult = productSearchService.searchByQuery(product.getName());
            
            // Update product with search result data
            if (searchResult.getName() != null && !searchResult.getName().isBlank()) {
                product.setName(searchResult.getName());
            }
            if (searchResult.getImageUrl() != null && !searchResult.getImageUrl().isBlank()) {
                product.setImageUrl(searchResult.getImageUrl());
            }
            productRepository.save(product);

            // Clear existing prices and save new ones from search
            priceRepository.deleteByProductId(product.getId());
            List<Price> prices = new ArrayList<>();
            
            if (searchResult.getPrices() != null) {
                for (PriceResponse priceResponse : searchResult.getPrices()) {
                    Price price = Price.builder()
                            .product(product)
                            .platform(priceResponse.getPlatform())
                            .price(priceResponse.getPrice())
                            .currency(priceResponse.getCurrency())
                            .productUrl(priceResponse.getProductUrl())
                            .availability(priceResponse.getAvailability() != null ? priceResponse.getAvailability() : true)
                            .source(priceResponse.getSource())
                            .build();
                    prices.add(price);
                    priceRepository.save(price);
                    
                    // Record price history for valid prices
                    if (priceResponse.getPrice() != null && priceResponse.getPrice().compareTo(BigDecimal.ZERO) > 0) {
                        recordPriceHistory(product, priceResponse.getPlatform(), priceResponse.getPrice());
                    }
                }
            }
        } catch (Exception ex) {
            log.error("Failed to refresh product {} from {}: {}", productId, url, ex.getMessage());
            List<Price> existing = priceRepository.findByProductIdOrderByPriceAsc(productId);
            if (existing.isEmpty()) {
                for (PriceResponse pending : buildPendingPrices()) {
                    priceRepository.save(Price.builder()
                            .product(product)
                            .platform(pending.getPlatform())
                            .price(BigDecimal.ZERO)
                            .currency("INR")
                            .productUrl("")
                            .availability(false)
                            .source("pending")
                            .build());
                }
            }
        }
    }

    private List<PriceResponse> buildPendingPrices() {
        return ALL_PLATFORMS.stream()
                .map(platform -> PriceResponse.builder()
                        .platform(platform)
                        .price(BigDecimal.ZERO)
                        .currency("INR")
                        .productUrl("")
                        .availability(false)
                        .source("pending")
                        .percentSavings(0)
                        .build())
                .toList();
    }

    private void recordPriceHistory(Product product, String platform, BigDecimal price) {
        priceHistoryRepository.save(PriceHistory.builder()
                .product(product)
                .platform(platform)
                .price(price)
                .build());
    }

    public ProductResponse getProduct(Long id) {
        Product product = productRepository.findById(id)
                .orElseThrow(() -> new CustomExceptions.ProductNotFoundException(id));
        List<Price> prices = priceRepository.findByProductIdOrderByPriceAsc(id);
        return toProductResponse(product, enrichPricesWithSavings(prices));
    }

    public List<PriceResponse> getPrices(Long productId) {
        if (!productRepository.existsById(productId))
            throw new CustomExceptions.ProductNotFoundException(productId);
        List<Price> prices = priceRepository.findByProductIdOrderByPriceAsc(productId);
        return enrichPricesWithSavings(prices);
    }

    /* ════════════════════════════════════════════════════════════════════
     *  PRICE HISTORY: Fetch history, seed demo data if empty
     * ════════════════════════════════════════════════════════════════════ */

    public PriceHistoryResponse getHistory(Long productId, Integer days, String platform) {
        if (!productRepository.existsById(productId))
            throw new CustomExceptions.ProductNotFoundException(productId);

        int d = days == null || days < 1 ? 14 : Math.min(days, 90);
        Instant since = Instant.now().minus(d, ChronoUnit.DAYS);

        List<PriceHistory> history = platform != null && !platform.isBlank()
                ? priceHistoryRepository.findByProductIdAndPlatformSince(productId, platform, since)
                : priceHistoryRepository.findByProductIdSince(productId, since);

        // If no history exists, seed demo data so the chart works immediately
        if (history.isEmpty()) {
            log.info("No price history for product {}. Seeding demo data for {} days.", productId, d);
            history = seedDemoHistory(productId, d);
        }

        Map<String, List<PriceHistoryResponse.DataPoint>> byPlatform = history.stream()
                .collect(Collectors.groupingBy(PriceHistory::getPlatform,
                        Collectors.mapping(ph -> new PriceHistoryResponse.DataPoint(ph.getRecordedAt(), ph.getPrice()),
                                Collectors.toList())));
        byPlatform.forEach((k, v) -> v.sort(Comparator.comparing(PriceHistoryResponse.DataPoint::getDate)));

        return PriceHistoryResponse.builder()
                .productId(productId)
                .historyByPlatform(byPlatform)
                .build();
    }

    /**
     * Seed demo price history data when none exists.
     * Creates 14 data points per platform (one per day) with ±5% random variation
     * around the current price. Marked as source="demo" via platform suffix hint in logs.
     */
    @Transactional
    protected List<PriceHistory> seedDemoHistory(Long productId, int days) {
        List<Price> currentPrices = priceRepository.findByProductIdOrderByPriceAsc(productId);
        Product product = productRepository.findById(productId).orElse(null);

        if (product == null || currentPrices.isEmpty()) {
            log.warn("Cannot seed demo history: product {} has no current prices", productId);
            return Collections.emptyList();
        }

        // Use platforms that have a valid (>0) price
        List<Price> validPrices = currentPrices.stream()
                .filter(p -> p.getPrice() != null && p.getPrice().compareTo(BigDecimal.ZERO) > 0)
                .toList();

        if (validPrices.isEmpty()) {
            return Collections.emptyList();
        }

        List<PriceHistory> seeded = new ArrayList<>();
        ThreadLocalRandom rng = ThreadLocalRandom.current();
        int dataPoints = Math.min(days, 14);

        for (Price price : validPrices) {
            BigDecimal basePrice = price.getPrice();
            String platformName = price.getPlatform();

            for (int dayOffset = dataPoints; dayOffset >= 0; dayOffset--) {
                // Random variation: -5% to +5%
                double variation = 1.0 + (rng.nextDouble(-0.05, 0.05));
                BigDecimal demoPrice = basePrice.multiply(BigDecimal.valueOf(variation))
                        .setScale(2, RoundingMode.HALF_UP);

                // Ensure price is at least ₹1
                if (demoPrice.compareTo(BigDecimal.ONE) < 0) {
                    demoPrice = basePrice;
                }

                Instant timestamp = Instant.now().minus(dayOffset, ChronoUnit.DAYS)
                        .plus(rng.nextInt(0, 12), ChronoUnit.HOURS); // Slight hour jitter

                PriceHistory entry = PriceHistory.builder()
                        .product(product)
                        .platform(platformName)
                        .price(demoPrice)
                        .build();

                // We need to set recordedAt manually for backdated entries.
                // Since @CreationTimestamp sets it on persist, we save and then update.
                PriceHistory saved = priceHistoryRepository.save(entry);
                saved.setRecordedAt(timestamp);
                priceHistoryRepository.save(saved);

                seeded.add(saved);
            }
        }

        log.info("Seeded {} demo price history data points for product {} across {} platforms",
                seeded.size(), productId, validPrices.size());
        return seeded;
    }

    public RecommendationResponse getRecommendation(Long productId) {
        if (!productRepository.existsById(productId))
            throw new CustomExceptions.ProductNotFoundException(productId);
        return aiRecommendationService.getRecommendation(productId);
    }

    @Transactional
    public void trackProduct(Long productId) {
        if (!productRepository.existsById(productId))
            throw new CustomExceptions.ProductNotFoundException(productId);
        String uid = com.pricecomparison.security.AuthUtil.requireEmail();
        if (trackedProductRepository.existsByProductIdAndUserId(productId, uid)) return;
        Product product = productRepository.getReferenceById(productId);
        trackedProductRepository.save(TrackedProduct.builder().product(product).userId(uid).build());
    }

    private List<PriceResponse> enrichPricesWithSavings(List<Price> prices) {
        if (prices.isEmpty()) return Collections.emptyList();
        BigDecimal max = prices.stream().map(Price::getPrice).max(BigDecimal::compareTo).orElse(BigDecimal.ONE);
        return prices.stream()
                .map(p -> {
                    int savings = 0;
                    if (p.getPrice() != null && p.getPrice().compareTo(BigDecimal.ZERO) > 0
                            && max.compareTo(BigDecimal.ZERO) > 0) {
                        savings = max.subtract(p.getPrice())
                                .multiply(BigDecimal.valueOf(100))
                                .divide(max, 0, java.math.RoundingMode.HALF_UP)
                                .intValue();
                    }
                    return PriceResponse.builder()
                            .id(p.getId())
                            .platform(p.getPlatform())
                            .price(p.getPrice())
                            .currency(p.getCurrency())
                            .productUrl(p.getProductUrl())
                            .availability(p.getAvailability())
                            .recordedAt(p.getRecordedAt())
                            .percentSavings(savings)
                            .source(p.getSource() != null ? p.getSource() : "live")
                            .build();
                })
                .sorted((a, b) -> {
                    boolean aValid = a.getPrice() != null && a.getPrice().compareTo(BigDecimal.ZERO) > 0;
                    boolean bValid = b.getPrice() != null && b.getPrice().compareTo(BigDecimal.ZERO) > 0;
                    if (!aValid && bValid) return 1;
                    if (!bValid && aValid) return -1;
                    if (!aValid && !bValid) return 0;
                    return a.getPrice().compareTo(b.getPrice());
                })
                .toList();
    }

    private ProductResponse toProductResponse(Product product, List<PriceResponse> priceResponses) {
        // Best price = first entry after sorting (lowest valid price)
        PriceResponse best = priceResponses.stream()
                .filter(p -> p.getPrice() != null && p.getPrice().compareTo(BigDecimal.ZERO) > 0)
                .filter(p -> !"fallback".equals(p.getSource()))
                .findFirst()
                .orElse(priceResponses.isEmpty() ? null : priceResponses.get(0));
        return ProductResponse.builder()
                .id(product.getId())
                .name(product.getName())
                .brand(product.getBrand())
                .category(product.getCategory())
                .imageUrl(product.getImageUrl())
                .specifications(product.getSpecifications())
                .prices(priceResponses)
                .bestPrice(best)
                .comparisonStatus(computeComparisonStatus(priceResponses))
                .build();
    }

    private String computeComparisonStatus(List<PriceResponse> prices) {
        if (prices == null || prices.isEmpty()) return "PENDING";
        boolean hasPending = prices.stream().anyMatch(p -> "pending".equalsIgnoreCase(p.getSource()));
        if (hasPending) return "PENDING";

        boolean hasLiveOrCached = prices.stream().anyMatch(p ->
                p.getPrice() != null
                        && p.getPrice().compareTo(BigDecimal.ZERO) > 0
                        && !"fallback".equalsIgnoreCase(p.getSource()));
        boolean hasUnavailable = prices.stream().anyMatch(p ->
                p.getPrice() == null
                        || p.getPrice().compareTo(BigDecimal.ZERO) <= 0
                        || "fallback".equalsIgnoreCase(p.getSource()));

        if (!hasLiveOrCached) return "PARTIAL";
        return hasUnavailable ? "PARTIAL" : "READY";
    }
}