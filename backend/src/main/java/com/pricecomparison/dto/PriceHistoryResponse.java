package com.pricecomparison.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PriceHistoryResponse {

    private Long productId;
    private Map<String, List<DataPoint>> historyByPlatform;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class DataPoint {
        private Instant date;
        private BigDecimal price;
    }
}
