package com.pricecomparison.model;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.math.BigDecimal;
import java.time.Instant;

@Entity
@Table(name = "price", indexes = {
    @Index(name = "idx_product_platform", columnList = "product_id, platform")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Price {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "product_id", nullable = false)
    private Product product;

    @Column(nullable = false, length = 50)
    private String platform;

    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal price;

    @Column(length = 10)
    @Builder.Default
    private String currency = "INR";

    @Column(name = "product_url", length = 2000)
    private String productUrl;

    @Column(nullable = false)
    @Builder.Default
    private Boolean availability = true;

    @CreationTimestamp
    @Column(name = "recorded_at")
    private Instant recordedAt;

    @Column(length = 50)
    @Builder.Default
    private String source = "live";
}
