package com.pricecomparison.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ProductResponse {

    private Long id;
    private String name;
    private String brand;
    private String category;
    private String imageUrl;
    private Map<String, String> specifications;
    private List<PriceResponse> prices;
    private PriceResponse bestPrice;
    private String comparisonStatus;
}
