package com.pricecomparison.controller;

import com.pricecomparison.dto.PriceAlertRequest;
import com.pricecomparison.dto.PriceAlertResponse;
import com.pricecomparison.model.Price;
import com.pricecomparison.model.PriceAlert;
import com.pricecomparison.model.Product;
import com.pricecomparison.repository.PriceAlertRepository;
import com.pricecomparison.repository.PriceRepository;
import com.pricecomparison.repository.ProductRepository;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import com.pricecomparison.security.AuthUtil;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/alerts")
@RequiredArgsConstructor
public class AlertController {

    private final PriceAlertRepository priceAlertRepository;
    private final ProductRepository productRepository;
    private final PriceRepository priceRepository;

    @PostMapping
    public ResponseEntity<PriceAlertResponse> setAlert(@Valid @RequestBody PriceAlertRequest request) {
        Product product = productRepository.findById(request.getProductId())
                .orElseThrow(() -> new RuntimeException("Product not found"));

        String userId = AuthUtil.requireEmail();

        PriceAlert alert = priceAlertRepository.save(PriceAlert.builder()
                .product(product)
                .userId(userId)
                .userEmail(request.getEmail() != null ? request.getEmail() : userId)
                .targetPrice(request.getTargetPrice())
                .active(true)
                .build());

        return ResponseEntity.ok(toResponse(alert));
    }

    @GetMapping
    public ResponseEntity<List<PriceAlertResponse>> getAlerts() {
        String uid = AuthUtil.requireEmail();
        List<PriceAlert> alerts = priceAlertRepository.findByUserId(uid);
        return ResponseEntity.ok(alerts.stream().map(this::toResponse).collect(Collectors.toList()));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Map<String, String>> deleteAlert(@PathVariable Long id) {
        priceAlertRepository.deleteById(id);
        return ResponseEntity.ok(Map.of("message", "Alert removed"));
    }

    @PatchMapping("/{id}/toggle")
    public ResponseEntity<PriceAlertResponse> toggleAlert(@PathVariable Long id) {
        PriceAlert alert = priceAlertRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Alert not found"));
        
        alert.setActive(!alert.getActive());
        if (alert.getActive()) {
            alert.setTriggeredAt(null); // Reset triggered state when manually resumed
        }
        
        priceAlertRepository.save(alert);
        return ResponseEntity.ok(toResponse(alert));
    }

    private PriceAlertResponse toResponse(PriceAlert alert) {
        List<Price> currentPrices = priceRepository.findByProductIdOrderByPriceAsc(alert.getProduct().getId());
        java.math.BigDecimal currentBest = null;
        
        // Find best valid price
        for (Price p : currentPrices) {
            if (p.getPrice() != null && p.getPrice().compareTo(java.math.BigDecimal.ZERO) > 0 && !"fallback".equals(p.getSource())) {
                currentBest = p.getPrice();
                break;
            }
        }
        
        // Unsaved fallback to absolute first if no valid live prices
        if (currentBest == null && !currentPrices.isEmpty()) {
            currentBest = currentPrices.get(0).getPrice();
        }

        String status;
        if (alert.getTriggeredAt() != null) {
            status = "TRIGGERED";
        } else if (!alert.getActive()) {
            status = "PAUSED";
        } else {
            status = "WAITING";
        }

        return PriceAlertResponse.builder()
                .id(alert.getId())
                .productId(alert.getProduct().getId())
                .productName(alert.getProduct().getName())
                .imageUrl(alert.getProduct().getImageUrl())
                .targetPrice(alert.getTargetPrice())
                .currentBestPrice(currentBest)
                .active(alert.getActive())
                .createdAt(alert.getCreatedAt())
                .triggeredAt(alert.getTriggeredAt())
                .status(status)
                .build();
    }
}
