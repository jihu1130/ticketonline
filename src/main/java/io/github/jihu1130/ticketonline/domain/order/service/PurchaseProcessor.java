package io.github.jihu1130.ticketonline.domain.order.service;

import io.github.jihu1130.ticketonline.domain.order.entity.ProductOrder;

/**
 * 재고를 줄이고 주문을 만드는 부분. 동시성 처리 방식을 비교하기 위해 갈아 끼울 수 있게 나눴다.
 * application.yaml 의 app.purchase.strategy 로 고른다.
 * - A: PessimisticPurchaseProcessor (pessimistic, 기본) SELECT ... FOR UPDATE
 * - B: OptimisticPurchaseProcessor  (optimistic)  @Version, 충돌하면 최대 3번 시도
 * - C: Redis 재고 (DECRBY / INCRBY 되돌리기)    <- 9단계
 *
 * 구현은 1인 한도(LIMIT_EXCEEDED), 매진(SOLD_OUT), 남은 수량 부족(INSUFFICIENT_STOCK)을 검사하고
 * 초과 판매가 절대 일어나지 않게 해야 한다. 호출하는 쪽(OrderService)의 트랜잭션 안에서 실행된다.
 */
public interface PurchaseProcessor {

    ProductOrder process(PurchaseCommand command);

    /**
     * 충돌했을 때 처음부터 다시 해 보는 최대 횟수 (첫 시도 포함).
     * 다시 시도는 트랜잭션 바깥(PurchaseFacade)에서 매번 새 트랜잭션으로 한다.
     */
    default int maxAttempts() {
        return 1;
    }
}
