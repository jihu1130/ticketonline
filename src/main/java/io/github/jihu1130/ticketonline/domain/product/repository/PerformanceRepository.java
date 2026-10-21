package io.github.jihu1130.ticketonline.domain.product.repository;

import io.github.jihu1130.ticketonline.domain.product.entity.Performance;
import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PerformanceRepository extends JpaRepository<Performance, Long> {

    /** 여러 상품의 회차 (공연 일시 순) */
    List<Performance> findByProductIdInOrderByEventAtAsc(Collection<Long> productIds);

    @Modifying
    @Query("delete from Performance p where p.product.id = :productId")
    void deleteByProductId(@Param("productId") Long productId);
}
