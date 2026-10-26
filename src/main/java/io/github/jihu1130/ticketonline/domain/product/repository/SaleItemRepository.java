package io.github.jihu1130.ticketonline.domain.product.repository;

import io.github.jihu1130.ticketonline.domain.product.entity.SaleItem;
import jakarta.persistence.LockModeType;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface SaleItemRepository extends JpaRepository<SaleItem, Long> {

    List<SaleItem> findByProductIdInOrderByIdAsc(Collection<Long> productIds);

    /** 회차·등급보다 먼저 지워야 한다 (sale_item이 둘을 참조) */
    @Modifying
    @Query("delete from SaleItem s where s.product.id = :productId")
    void deleteByProductId(@Param("productId") Long productId);

    /** 구매 대상 번호만 조회 (엔티티를 읽지 않음, SaleItemKey 설명 참고) */
    @Query("select new io.github.jihu1130.ticketonline.domain.product.repository.SaleItemKey(s.id, s.product.id, p.id)"
            + " from SaleItem s left join s.performance p where s.id = :id")
    Optional<SaleItemKey> findKeyById(@Param("id") Long id);

    /** 상품의 구매 대상 번호들 (굿즈는 1개) */
    @Query("select new io.github.jihu1130.ticketonline.domain.product.repository.SaleItemKey(s.id, s.product.id, p.id)"
            + " from SaleItem s left join s.performance p where s.product.id = :productId order by s.id")
    List<SaleItemKey> findKeysByProductId(@Param("productId") Long productId);

    /**
     * 구매 방식 C(Redis): Redis 에서 통과한 요청만 DB 판매 수량을 늘린다.
     * 총 수량을 넘으면 바꾸지 않는다(0 반환) -> Redis 와 DB 가 잠깐 어긋나도 DB 에서 초과 판매를 막는 마지막 안전장치.
     */
    @Modifying
    @Query("update SaleItem s set s.soldQuantity = s.soldQuantity + :quantity"
            + " where s.id = :id and s.soldQuantity + :quantity <= s.totalQuantity")
    int sellIfAvailable(@Param("id") Long id, @Param("quantity") int quantity);

    /** 방식 C: Redis 에 남은 수량이 없을 때 DB 값으로 채운다 */
    @Query("select s.totalQuantity - s.soldQuantity from SaleItem s where s.id = :id")
    Optional<Integer> findRemainingQuantity(@Param("id") Long id);

    /** 구매 방식 A(비관적 락): SELECT ... FOR UPDATE 로 재고 행을 잠근다 */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select s from SaleItem s where s.id = :id")
    Optional<SaleItem> findByIdForUpdate(@Param("id") Long id);
}
