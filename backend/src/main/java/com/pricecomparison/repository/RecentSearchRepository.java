package com.pricecomparison.repository;

import com.pricecomparison.model.RecentSearch;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

@Repository
public interface RecentSearchRepository extends JpaRepository<RecentSearch, Long> {

    /** All searches for a user, newest first. */
    List<RecentSearch> findByUserIdOrderBySearchedAtDesc(String userId);

    /** Last 10 searches for a user, newest first. */
    List<RecentSearch> findTop10ByUserIdOrderBySearchedAtDesc(String userId);

    /** Delete a specific search entry for a user. */
    @Modifying
    @Transactional
    void deleteByUserIdAndProductId(String userId, Long productId);

    /** Check if a query already exists for this user. */
    boolean existsByUserIdAndQuery(String userId, String query);

    /** Count of searches for a user. */
    long countByUserId(String userId);

    /**
     * Find the oldest entries beyond the top-N, used for trimming.
     * Returns ids of entries to delete.
     */
    @Query(value = """
            SELECT id FROM recent_searches
            WHERE user_id = :userId
            ORDER BY searched_at DESC
            OFFSET :keepCount ROWS
            """, nativeQuery = true)
    List<Long> findIdsToTrim(@Param("userId") String userId, @Param("keepCount") int keepCount);

    /** Delete all searches for a user. */
    @Modifying
    @Transactional
    @Query("DELETE FROM RecentSearch rs WHERE rs.userId = :userId")
    void deleteAllByUserId(@Param("userId") String userId);
}
