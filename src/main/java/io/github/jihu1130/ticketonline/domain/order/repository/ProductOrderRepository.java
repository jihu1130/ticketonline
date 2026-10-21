package io.github.jihu1130.ticketonline.domain.order.repository;

import io.github.jihu1130.ticketonline.domain.order.entity.ProductOrder;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
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
}
