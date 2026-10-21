package io.github.jihu1130.ticketonline.domain.product.repository;

import io.github.jihu1130.ticketonline.domain.product.entity.SaleItem;
import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** 구매 방식 A(비관적 락)/B(낙관적 락)용 조회 메서드는 6·8단계에서 추가한다. */
public interface SaleItemRepository extends JpaRepository<SaleItem, Long> {

    List<SaleItem> findByProductIdInOrderByIdAsc(Collection<Long> productIds);

    /** 회차·등급보다 먼저 지워야 한다 (sale_item이 둘을 참조) */
    @Modifying
    @Query("delete from SaleItem s where s.product.id = :productId")
    void deleteByProductId(@Param("productId") Long productId);
}
