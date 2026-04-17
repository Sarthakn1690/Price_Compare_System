package com.pricecomparison.model;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.Instant;

@Entity
@Table(name = "recent_searches", indexes = {
        @Index(name = "idx_recent_search_user", columnList = "user_id"),
        @Index(name = "idx_recent_search_user_at", columnList = "user_id, searched_at")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RecentSearch {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** The email of the authenticated user who performed the search. */
    @Column(name = "user_id", nullable = false, length = 200)
    private String userId;

    /** The raw search query string. */
    @Column(name = "query", nullable = false, length = 500)
    private String query;

    /** Display name of the top product result. */
    @Column(name = "product_name", length = 500)
    private String productName;

    /** Thumbnail image URL of the top product result. */
    @Column(name = "image_url", length = 1000)
    private String imageUrl;

    /** The persisted Product id corresponding to this search result. */
    @Column(name = "product_id")
    private Long productId;

    /** The best/lowest price found at the time of search. */
    @Column(name = "best_price_found", precision = 12, scale = 2)
    private BigDecimal bestPriceFound;

    /** The platform name that offered the best price. */
    @Column(name = "best_platform", length = 100)
    private String bestPlatform;

    /** Timestamp of when the search was performed — set automatically on insert. */
    @Column(name = "searched_at", nullable = false, updatable = false)
    private Instant searchedAt;

    @PrePersist
    protected void prePersist() {
        if (searchedAt == null) {
            searchedAt = Instant.now();
        }
    }
}
