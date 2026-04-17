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
public class PriceResponse {

    private Long id;
    private String platform;
    private BigDecimal price;
    private String currency;
    private String productUrl;
    private Boolean availability;
    private Instant recordedAt;
    private Integer percentSavings; // compared to highest price
    private String source;
}
