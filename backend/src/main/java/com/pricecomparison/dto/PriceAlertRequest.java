package com.pricecomparison.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.Data;

import java.math.BigDecimal;

@Data
public class PriceAlertRequest {
    @NotNull
    private Long productId;
    
    @NotNull
    @Positive
    private BigDecimal targetPrice;

    private String email;
}
