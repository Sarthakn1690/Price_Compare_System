package com.pricecomparison.repository;

import com.pricecomparison.model.PriceAlert;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface PriceAlertRepository extends JpaRepository<PriceAlert, Long> {
    List<PriceAlert> findByUserId(String userId);
    List<PriceAlert> findByProductIdAndActiveTrue(Long productId);
    boolean existsByProductIdAndUserIdAndActiveTrue(Long productId, String userId);
    List<PriceAlert> findByActiveTrue();
}
