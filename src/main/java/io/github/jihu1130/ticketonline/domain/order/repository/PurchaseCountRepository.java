package io.github.jihu1130.ticketonline.domain.order.repository;

import io.github.jihu1130.ticketonline.domain.order.entity.PurchaseCount;
import io.github.jihu1130.ticketonline.domain.order.entity.PurchaseCountId;
import jakarta.persistence.LockModeType;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PurchaseCountRepository extends JpaRepository<PurchaseCount, PurchaseCountId> {

    /**
     * 처음 사는 사람이면 행을 만들고, 이미 있으면 그대로 둔다. 어느 쪽이든 이 행에 쓰기 잠금이 걸린다.
     * INSERT IGNORE를 쓰지 않는 이유: 이미 행이 있을 때 IGNORE는 읽기 잠금만 잡아서,
     * 두 요청이 둘 다 읽기 잠금을 잡은 뒤 서로 쓰기 잠금을 기다리는 교착(데드락)이 생길 수 있다.
     */
    @Modifying
    @Query(value = "INSERT INTO purchase_count (member_id, product_id, scope_id, bought_quantity, updated_at)"
            + " VALUES (:memberId, :productId, :scopeId, 0, NOW(6))"
            + " ON DUPLICATE KEY UPDATE bought_quantity = bought_quantity",
            nativeQuery = true)
    void insertIfAbsent(@Param("memberId") Long memberId, @Param("productId") Long productId,
                        @Param("scopeId") Long scopeId);

    /**
     * 구매 방식 C(Redis): 산 수량을 늘린다 (처음 사면 행을 만든다). 이 행에 쓰기 잠금이 걸리므로
     * 이어서 읽는 findBoughtQuantity 는 같은 트랜잭션 안에서 다른 요청에 방해받지 않는다.
     */
    @Modifying
    @Query(value = "INSERT INTO purchase_count (member_id, product_id, scope_id, bought_quantity, updated_at, version)"
            + " VALUES (:memberId, :productId, :scopeId, :quantity, NOW(6), 0)"
            + " ON DUPLICATE KEY UPDATE bought_quantity = bought_quantity + :quantity, updated_at = NOW(6)",
            nativeQuery = true)
    void addBought(@Param("memberId") Long memberId, @Param("productId") Long productId,
                   @Param("scopeId") Long scopeId, @Param("quantity") int quantity);

    /** 방식 C: Redis 에 산 수량이 없을 때 DB 값으로 채운다 (행이 없으면 0) */
    @Query(value = "SELECT bought_quantity FROM purchase_count"
            + " WHERE member_id = :memberId AND product_id = :productId AND scope_id = :scopeId",
            nativeQuery = true)
    Optional<Integer> findBoughtQuantity(@Param("memberId") Long memberId, @Param("productId") Long productId,
                                         @Param("scopeId") Long scopeId);

    /** 구매 방식 A(비관적 락): 1인 한도 행을 잠근다 */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select c from PurchaseCount c where c.id = :id")
    Optional<PurchaseCount> findByIdForUpdate(@Param("id") PurchaseCountId id);
}
