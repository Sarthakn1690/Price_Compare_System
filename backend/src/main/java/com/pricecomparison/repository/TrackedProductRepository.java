package com.pricecomparison.repository;

import com.pricecomparison.model.TrackedProduct;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface TrackedProductRepository extends JpaRepository<TrackedProduct, Long> {

    List<TrackedProduct> findByUserId(String userId);

    Optional<TrackedProduct> findByProductIdAndUserId(Long productId, String userId);

    boolean existsByProductIdAndUserId(Long productId, String userId);
}
