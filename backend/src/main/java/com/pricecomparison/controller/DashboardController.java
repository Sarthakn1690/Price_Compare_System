package com.pricecomparison.controller;

import com.pricecomparison.model.Price;
import com.pricecomparison.model.PriceAlert;
import com.pricecomparison.model.Product;
import com.pricecomparison.model.RecentSearch;
import com.pricecomparison.model.TrackedProduct;
import com.pricecomparison.repository.PriceAlertRepository;
import com.pricecomparison.repository.PriceRepository;
import com.pricecomparison.repository.RecentSearchRepository;
import com.pricecomparison.repository.TrackedProductRepository;
import com.pricecomparison.security.AuthUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.*;

/**
 * Provides consolidated dashboard data for the authenticated user.
 *
 * <p>All endpoints under /dashboard require a valid JWT token (enforced by SecurityConfig).
 */
@RestController
@RequestMapping("/dashboard")
@RequiredArgsConstructor
@Slf4j
public class DashboardController {

    private final TrackedProductRepository trackedProductRepository;
    private final RecentSearchRepository recentSearchRepository;
    private final PriceAlertRepository priceAlertRepository;
    private final PriceRepository priceRepository;

    /* ════════════════════════════════════════════════════════════════════
     *  GET /dashboard
     *  Returns all dashboard data for the authenticated user in one call.
     * ════════════════════════════════════════════════════════════════════ */

    @GetMapping
    public ResponseEntity<Map<String, Object>> getDashboard() {
        String userId = AuthUtil.requireEmail();
        log.info("Dashboard requested for user '{}'", userId);

        // ── 1. Watchlist ───────────────────────────────────────────────
        List<TrackedProduct> trackedList = trackedProductRepository.findByUserId(userId);
        List<Map<String, Object>> watchlistItems = new ArrayList<>();
        BigDecimal totalCurrentPrice = BigDecimal.ZERO;
        int priceCount = 0;

        for (TrackedProduct tp : trackedList) {
            Product product = tp.getProduct();
            if (product == null) continue;

            List<Price> prices = priceRepository.findByProductIdOrderByPriceAsc(product.getId());

            BigDecimal bestPrice = prices.stream()
                    .filter(p -> p.getPrice() != null && p.getPrice().compareTo(BigDecimal.ZERO) > 0)
                    .map(Price::getPrice)
                    .findFirst()
                    .orElse(null);

            String bestPlatform = prices.stream()
                    .filter(p -> p.getPrice() != null && p.getPrice().compareTo(BigDecimal.ZERO) > 0)
                    .map(Price::getPlatform)
                    .findFirst()
                    .orElse(null);

            Map<String, Object> item = new LinkedHashMap<>();
            item.put("productId", product.getId());
            item.put("productName", product.getName());
            item.put("imageUrl", product.getImageUrl());
            item.put("bestPrice", bestPrice);
            item.put("bestPlatform", bestPlatform);
            item.put("addedAt", tp.getCreatedAt());
            watchlistItems.add(item);

            if (bestPrice != null) {
                totalCurrentPrice = totalCurrentPrice.add(bestPrice);
                priceCount++;
            }
        }

        // ── 2. Recent Searches ─────────────────────────────────────────
        List<RecentSearch> recentSearches = recentSearchRepository.findTop10ByUserIdOrderBySearchedAtDesc(userId);
        long totalSearchCount = recentSearchRepository.countByUserId(userId);

        // ── 3. Price Alerts ────────────────────────────────────────────
        List<PriceAlert> alerts = priceAlertRepository.findByUserId(userId);
        List<Map<String, Object>> alertItems = new ArrayList<>();
        long activeAlerts = 0;

        for (PriceAlert alert : alerts) {
            if (alert.getProduct() == null) continue;

            // Determine current best price
            List<Price> prices = priceRepository.findByProductIdOrderByPriceAsc(alert.getProduct().getId());
            BigDecimal currentBest = prices.stream()
                    .filter(p -> p.getPrice() != null && p.getPrice().compareTo(BigDecimal.ZERO) > 0
                            && !"fallback".equalsIgnoreCase(p.getSource()))
                    .map(Price::getPrice)
                    .findFirst()
                    .orElse(null);

            String status;
            if (alert.getTriggeredAt() != null) {
                status = "TRIGGERED";
            } else if (!Boolean.TRUE.equals(alert.getActive())) {
                status = "PAUSED";
            } else {
                status = "WAITING";
                activeAlerts++;
            }

            Map<String, Object> alertMap = new LinkedHashMap<>();
            alertMap.put("id", alert.getId());
            alertMap.put("productId", alert.getProduct().getId());
            alertMap.put("productName", alert.getProduct().getName());
            alertMap.put("targetPrice", alert.getTargetPrice());
            alertMap.put("currentBestPrice", currentBest);
            alertMap.put("status", status);
            alertItems.add(alertMap);
        }

        // ── 4. Stats ───────────────────────────────────────────────────
        // avgSavingsPercent: compare each tracked product's best vs. any second price
        double avgSavingsPercent = computeAvgSavings(trackedList);

        Map<String, Object> stats = new LinkedHashMap<>();
        stats.put("totalSearches", totalSearchCount);
        stats.put("totalSaved", watchlistItems.size());
        stats.put("activeAlerts", activeAlerts);
        stats.put("avgSavingsPercent", Math.round(avgSavingsPercent));

        // ── 5. Assemble response ───────────────────────────────────────
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("user", userId);
        response.put("watchlistCount", watchlistItems.size());
        response.put("recentSearches", recentSearches);
        response.put("watchlist", watchlistItems);
        response.put("alerts", alertItems);
        response.put("stats", stats);

        return ResponseEntity.ok(response);
    }

    /* ════════════════════════════════════════════════════════════════════
     *  GET /dashboard/recent-searches
     *  Returns last 10 recent searches for the authenticated user.
     * ════════════════════════════════════════════════════════════════════ */

    @GetMapping("/recent-searches")
    public ResponseEntity<List<RecentSearch>> getRecentSearches() {
        String userId = AuthUtil.requireEmail();
        return ResponseEntity.ok(recentSearchRepository.findTop10ByUserIdOrderBySearchedAtDesc(userId));
    }

    /* ════════════════════════════════════════════════════════════════════
     *  DELETE /dashboard/recent-searches/{id}
     *  Deletes a single recent search entry belonging to the current user.
     * ════════════════════════════════════════════════════════════════════ */

    @DeleteMapping("/recent-searches/{id}")
    public ResponseEntity<Map<String, String>> deleteRecentSearch(@PathVariable Long id) {
        String userId = AuthUtil.requireEmail();
        recentSearchRepository.findById(id).ifPresent(rs -> {
            if (userId.equals(rs.getUserId())) {
                recentSearchRepository.delete(rs);
                log.info("Deleted recent search {} for user '{}'", id, userId);
            }
        });
        return ResponseEntity.ok(Map.of("message", "Deleted"));
    }

    /* ════════════════════════════════════════════════════════════════════
     *  DELETE /dashboard/recent-searches
     *  Clears all recent searches for the current user.
     * ════════════════════════════════════════════════════════════════════ */

    @DeleteMapping("/recent-searches")
    public ResponseEntity<Map<String, String>> clearRecentSearches() {
        String userId = AuthUtil.requireEmail();
        recentSearchRepository.deleteAllByUserId(userId);
        log.info("Cleared all recent searches for user '{}'", userId);
        return ResponseEntity.ok(Map.of("message", "All recent searches cleared"));
    }

    /* ── Private helpers ──────────────────────────────────────────────── */

    /**
     * Computes the average savings % across all watchlist items.
     * Savings is the % difference between the highest and lowest price
     * for a given product. Returns 0 if there are not enough data points.
     */
    private double computeAvgSavings(List<TrackedProduct> trackedList) {
        if (trackedList.isEmpty()) return 0.0;

        double totalSavings = 0;
        int counted = 0;

        for (TrackedProduct tp : trackedList) {
            if (tp.getProduct() == null) continue;
            List<Price> prices = priceRepository.findByProductIdOrderByPriceAsc(tp.getProduct().getId());

            List<BigDecimal> validPrices = prices.stream()
                    .filter(p -> p.getPrice() != null && p.getPrice().compareTo(BigDecimal.ZERO) > 0
                            && !"fallback".equalsIgnoreCase(p.getSource()))
                    .map(Price::getPrice)
                    .toList();

            if (validPrices.size() >= 2) {
                BigDecimal min = validPrices.get(0);
                BigDecimal max = validPrices.get(validPrices.size() - 1);
                double savings = max.subtract(min)
                        .divide(max, 4, RoundingMode.HALF_UP)
                        .doubleValue() * 100;
                totalSavings += savings;
                counted++;
            }
        }

        return counted == 0 ? 0.0 : totalSavings / counted;
    }
}
