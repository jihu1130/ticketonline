package io.github.jihu1130.ticketonline.domain.order.service;

import io.github.jihu1130.ticketonline.domain.order.entity.ProductOrder;

/**
 * 재고를 줄이고 주문을 만드는 부분. 동시성 처리 방식을 비교하기 위해 갈아 끼울 수 있게 나눴다.
 * - A: PessimisticPurchaseProcessor (SELECT ... FOR UPDATE)  <- 지금 사용
 * - B: 낙관적 락 (@Version, 최대 3번 재시도)                   <- 8단계
 * - C: Redis 재고 (DECRBY / INCRBY 되돌리기)                   <- 9단계
 *
 * 구현은 1인 한도(LIMIT_EXCEEDED), 매진(SOLD_OUT), 남은 수량 부족(INSUFFICIENT_STOCK)을 검사하고
 * 초과 판매가 절대 일어나지 않게 해야 한다. 호출하는 쪽(OrderService)의 트랜잭션 안에서 실행된다.
 */
public interface PurchaseProcessor {

    ProductOrder process(PurchaseCommand command);
}
