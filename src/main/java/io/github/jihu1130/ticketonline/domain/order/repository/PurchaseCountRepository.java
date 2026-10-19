package io.github.jihu1130.ticketonline.domain.order.repository;

import io.github.jihu1130.ticketonline.domain.order.entity.PurchaseCount;
import io.github.jihu1130.ticketonline.domain.order.entity.PurchaseCountId;
import org.springframework.data.jpa.repository.JpaRepository;

/** 1인 구매 제한 확인용 잠금 조회 메서드는 6단계에서 추가한다. */
public interface PurchaseCountRepository extends JpaRepository<PurchaseCount, PurchaseCountId> {
}
