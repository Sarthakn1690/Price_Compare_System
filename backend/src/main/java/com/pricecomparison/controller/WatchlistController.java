package com.pricecomparison.controller;

import com.pricecomparison.model.Price;
import com.pricecomparison.model.Product;
import com.pricecomparison.model.TrackedProduct;
import com.pricecomparison.repository.PriceRepository;
import com.pricecomparison.repository.ProductRepository;
import com.pricecomparison.repository.TrackedProductRepository;
import com.pricecomparison.security.AuthUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.*;

@RestController
@RequestMapping("/watchlist")
@RequiredArgsConstructor
@Slf4j
public class WatchlistController {

    private final TrackedProductRepository trackedProductRepository;
    private final ProductRepository productRepository;
    private final PriceRepository priceRepository;

    /**
     * GET /watchlist — return all tracked products for the authenticated user
     * with full product details: id, name, imageUrl, bestPrice, addedAt
     */
    @GetMapping
    public ResponseEntity<Map<String, Object>> getWatchlist() {
        String uid = AuthUtil.requireEmail();
        List<TrackedProduct> tracked = trackedProductRepository.findByUserId(uid);

        List<Map<String, Object>> items = new ArrayList<>();
        for (TrackedProduct tp : tracked) {
            Product product = tp.getProduct();
            if (product == null) continue;

            // Find the best (lowest) price for this product
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
            item.put("name", product.getName());
            item.put("imageUrl", product.getImageUrl());
            item.put("brand", product.getBrand());
            item.put("category", product.getCategory());
            item.put("bestPrice", bestPrice);
            item.put("bestPlatform", bestPlatform);
            item.put("addedAt", tp.getCreatedAt());
            items.add(item);
        }

        log.info("Watchlist for user '{}': {} items", uid, items.size());

        return ResponseEntity.ok(Map.of(
                "user", uid,
                "count", items.size(),
                "items", items
        ));
    }

    /**
     * POST /watchlist/{productId} — add a product to the user's watchlist
     * Returns 200 if added, 409 if already tracked
     */
    @PostMapping("/{productId}")
    public ResponseEntity<Map<String, String>> addToWatchlist(@PathVariable Long productId) {
        String uid = AuthUtil.requireEmail();

        // Check if already tracked
        if (trackedProductRepository.existsByProductIdAndUserId(productId, uid)) {
            log.info("Product {} already in watchlist for user '{}'", productId, uid);
            return ResponseEntity.status(HttpStatus.CONFLICT)
                    .body(Map.of("message", "Product already in watchlist"));
        }

        // Verify product exists
        Optional<Product> productOpt = productRepository.findById(productId);
        if (productOpt.isEmpty()) {
            log.warn("Product {} not found — cannot add to watchlist", productId);
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Map.of("message", "Product not found"));
        }

        TrackedProduct tp = TrackedProduct.builder()
                .product(productOpt.get())
                .userId(uid)
                .build();
        trackedProductRepository.save(tp);

        log.info("Added product {} to watchlist for user '{}'", productId, uid);
        return ResponseEntity.ok(Map.of("message", "Added to watchlist"));
    }

    /**
     * DELETE /watchlist/{productId} — remove a product from the user's watchlist
     * Returns 204 No Content
     */
    @DeleteMapping("/{productId}")
    public ResponseEntity<Void> removeFromWatchlist(@PathVariable Long productId) {
        String uid = AuthUtil.requireEmail();

        Optional<TrackedProduct> tracked = trackedProductRepository.findByProductIdAndUserId(productId, uid);
        if (tracked.isPresent()) {
            trackedProductRepository.delete(tracked.get());
            log.info("Removed product {} from watchlist for user '{}'", productId, uid);
        } else {
            log.info("Product {} was not in watchlist for user '{}'", productId, uid);
        }

        return ResponseEntity.noContent().build();
    }
}
