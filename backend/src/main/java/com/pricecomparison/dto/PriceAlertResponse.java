package com.pricecomparison.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PriceAlertResponse {
    private Long id;
    private Long productId;
    private String productName;
    private String imageUrl;
    private BigDecimal targetPrice;
    private BigDecimal currentBestPrice;
    private Boolean active;
    private Instant createdAt;
    private Instant triggeredAt;
    private String status;
}
