package com.pricecomparison.model;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.Instant;

@Entity
@Table(name = "tracked_product", indexes = {
    @Index(name = "idx_tracked_product_user", columnList = "product_id")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TrackedProduct {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "product_id", nullable = false)
    private Product product;

    @Column(name = "user_id", length = 100)
    @Builder.Default
    private String userId = "default";

    @CreationTimestamp
    @Column(name = "created_at")
    private Instant createdAt;
}
