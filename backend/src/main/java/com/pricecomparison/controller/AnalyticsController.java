package com.pricecomparison.controller;

import com.pricecomparison.repository.PriceRepository;
import com.pricecomparison.repository.ProductRepository;
import com.pricecomparison.repository.TrackedProductRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/analytics")
@RequiredArgsConstructor
public class AnalyticsController {

    private final ProductRepository productRepository;
    private final PriceRepository priceRepository;
    private final TrackedProductRepository trackedProductRepository;

    @GetMapping("/summary")
    public ResponseEntity<Map<String, Object>> getSummary() {
        long products = productRepository.count();
        long alerts = 0; // simplified
        long tracked = trackedProductRepository.count();

        return ResponseEntity.ok(Map.of(
                "totalProducts", products,
                "totalWatchlist", tracked,
                "activeAlerts", alerts,
                "supportedPlatforms", 10,
                "systemStatus", "Healthy"
        ));
    }
}
