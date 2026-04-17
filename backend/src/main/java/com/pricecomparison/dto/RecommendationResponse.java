package com.pricecomparison.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RecommendationResponse {

    public enum RecommendationType {
        BUY_NOW,
        WAIT_FOR_BETTER_PRICE,
        PRICE_INCREASING
    }

    private RecommendationType recommendation;
    private Integer confidenceScore; // 0-100
    private String explanation;
    private String predictedTrend;
}
