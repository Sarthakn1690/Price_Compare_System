package com.pricecomparison.repository;

import com.pricecomparison.model.PriceHistory;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;

@Repository
public interface PriceHistoryRepository extends JpaRepository<PriceHistory, Long> {

    @Query("SELECT ph FROM PriceHistory ph WHERE ph.product.id = :productId AND ph.recordedAt >= :since " +
           "ORDER BY ph.recordedAt ASC")
    List<PriceHistory> findByProductIdSince(@Param("productId") Long productId, @Param("since") Instant since);

    @Query("SELECT ph FROM PriceHistory ph WHERE ph.product.id = :productId AND ph.platform = :platform " +
           "AND ph.recordedAt >= :since ORDER BY ph.recordedAt ASC")
    List<PriceHistory> findByProductIdAndPlatformSince(
            @Param("productId") Long productId,
            @Param("platform") String platform,
            @Param("since") Instant since);

    @org.springframework.data.jpa.repository.Modifying
    @Query("DELETE FROM PriceHistory ph WHERE ph.product.id = :productId AND ph.recordedAt < :before")
    void deleteByProductIdAndRecordedAtBefore(@Param("productId") Long productId, @Param("before") Instant before);
}
