package com.pricecomparison.repository;

import com.pricecomparison.model.Price;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface PriceRepository extends JpaRepository<Price, Long> {

    List<Price> findByProductIdOrderByPriceAsc(Long productId);

    List<Price> findByProductIdAndPlatformIgnoreCase(Long productId, String platform);

    Price findFirstByProduct_NameIgnoreCaseAndPlatformIgnoreCaseOrderByRecordedAtDesc(String productName, String platform);

    Optional<Price> findFirstByProductUrlOrderByRecordedAtDesc(String productUrl);

    void deleteByProductId(Long productId);
}
