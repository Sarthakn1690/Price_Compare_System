package com.pricecomparison.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ProductSearchRequest {

    @NotBlank(message = "URL is required")
    @Pattern(
        regexp = "^(https?://)(www\\.)?(amazon\\.in|flipkart\\.com|myntra\\.com|meesho\\.com|ajio\\.com|nykaa\\.com|snapdeal\\.com|croma\\.com|tatacliq\\.com|reliancedigital\\.in).*",
        message = "Invalid e-commerce URL. Supported: Amazon, Flipkart, Myntra, Meesho, Ajio, Nykaa, Snapdeal, Croma, TataCliq, RelianceDigital"
    )
    private String url;
}
