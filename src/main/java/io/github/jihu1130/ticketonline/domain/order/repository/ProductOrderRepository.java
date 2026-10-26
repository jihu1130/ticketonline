package io.github.jihu1130.ticketonline.domain.order.repository;

import io.github.jihu1130.ticketonline.domain.order.entity.ProductOrder;
import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ProductOrderRepository extends JpaRepository<ProductOrder, Long> {

    /** 내 구매 내역 (최근 순). 상품·회차·등급을 함께 읽어 화면에 필요한 정보를 한 번에 가져온다 */
    @Query("select o from ProductOrder o"
            + " join fetch o.saleItem s"
            + " join fetch s.product"
            + " left join fetch s.performance"
            + " left join fetch s.seatGrade"
            + " where o.member.id = :memberId"
            + " order by o.orderedAt desc, o.id desc")
    List<ProductOrder> findMyOrders(@Param("memberId") Long memberId);

    /**
     * 주문 취소·변경: 같은 주문을 두 번 동시에 바꾸지 않게 주문 행을 잠근다.
     * 재고(SaleItem)는 여기서 함께 읽지 않는다 (잠그기 전에 읽으면 옛 판매 수량을 쓰게 됨, SaleItemKey 참고)
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select o from ProductOrder o where o.id = :id")
    Optional<ProductOrder> findByIdForUpdate(@Param("id") Long id);
}
