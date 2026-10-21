package io.github.jihu1130.ticketonline.concurrency;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.jihu1130.ticketonline.domain.order.service.OptimisticPurchaseProcessor;
import io.github.jihu1130.ticketonline.global.exception.ErrorCode;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import org.junit.jupiter.api.Test;
import org.springframework.test.context.TestPropertySource;

/**
 * 구매 방식 B(낙관적 락) 동시성 검증 (8단계). 7단계 A와 같은 시나리오.
 *
 * 꼭 지켜야 하는 것: 초과 판매 0건, DB 판매 수량 = 주문 수량 = 성공한 요청 수량, 1인 한도, 예상 밖 예외 0건.
 * A와 다른 점: 한 재고에 요청이 몰리면 충돌이 많아 3번 모두 실패하는 요청(TOO_MANY_REQUESTS)이 생길 수 있다.
 * 그래서 "성공 정확히 100건" 대신 위 조건을 검사하고, 다시 시도 끝에 실패한 건수를 출력해 A와 비교한다.
 */
@TestPropertySource(properties = "app.purchase.strategy=optimistic")
class OptimisticPurchaseConcurrencyTest extends ConcurrencyTestSupport {

    @Test
    void usesStrategyB() {
        assertThat(purchaseProcessor).isInstanceOf(OptimisticPurchaseProcessor.class);
    }

    @Test
    void stock100_buyers1000_eachOne_neverOversell() throws InterruptedException {
        GoodsFixture goods = createGoods(100, 1);
        List<Purchase> purchases = createMembers(1000).stream()
                .map(id -> new Purchase(id, goods.productId(), goods.saleItemId(), 1))
                .toList();

        Result result = runConcurrently("B: 재고 100, 1000명 1개씩", purchases);

        assertThat(result.finished()).isTrue();
        assertThat(result.unexpected()).isEmpty();
        assertSoldMatchesOrders(goods.saleItemId(), result);
        assertThat(result.failure(ErrorCode.SOLD_OUT) + result.failure(ErrorCode.TOO_MANY_REQUESTS))
                .isEqualTo(result.requests() - result.successCount());
    }

    @Test
    void sameMember_tenRequests_limit2_neverOverLimit() throws InterruptedException {
        TicketFixture ticket = createTicket(2, 100);
        Long buyer = createMembers(1).get(0);
        List<Purchase> purchases = new ArrayList<>();
        for (int i = 0; i < 10; i++) {
            purchases.add(new Purchase(buyer, ticket.productId(), ticket.saleItemIds().get(0), 1));
        }

        Result result = runConcurrently("B: 같은 사람 10번 동시 (회차당 2매)", purchases);

        assertThat(result.unexpected()).isEmpty();
        assertThat(result.successCount()).isLessThanOrEqualTo(2);
        assertThat(boughtQuantity(buyer, ticket.productId(), ticket.performanceId())).isEqualTo(result.successQuantity());
        assertSoldMatchesOrders(ticket.saleItemIds().get(0), result);
        assertThat(result.failure(ErrorCode.LIMIT_EXCEEDED) + result.failure(ErrorCode.TOO_MANY_REQUESTS))
                .isEqualTo(result.requests() - result.successCount());
    }

    @Test
    void mixedQuantities_neverOversell_andNoPartialSale() throws InterruptedException {
        GoodsFixture goods = createGoods(100, 3);
        Random random = new Random(7); // A와 같은 요청
        List<Purchase> purchases = createMembers(500).stream()
                .map(id -> new Purchase(id, goods.productId(), goods.saleItemId(), 1 + random.nextInt(3)))
                .toList();

        Result result = runConcurrently("B: 재고 100, 500명 1~3개씩", purchases);

        assertThat(result.unexpected()).isEmpty();
        assertSoldMatchesOrders(goods.saleItemId(), result);
        assertThat(result.failure(ErrorCode.SOLD_OUT) + result.failure(ErrorCode.INSUFFICIENT_STOCK)
                + result.failure(ErrorCode.TOO_MANY_REQUESTS))
                .isEqualTo(result.requests() - result.successCount());
    }

    @Test
    void sameMember_differentGrades_sameTime_limitKept() throws InterruptedException {
        TicketFixture ticket = createTicket(2, 50, 50);
        Long buyer = createMembers(1).get(0);
        List<Purchase> purchases = new ArrayList<>();
        for (int i = 0; i < 10; i++) {
            purchases.add(new Purchase(buyer, ticket.productId(), ticket.saleItemIds().get(i % 2), 1));
        }

        Result result = runConcurrently("B: 같은 사람 두 등급 번갈아 10번 (회차당 2매)", purchases);

        assertThat(result.unexpected()).isEmpty();
        assertThat(result.successCount()).isLessThanOrEqualTo(2);
        int soldBothGrades = soldQuantity(ticket.saleItemIds().get(0)) + soldQuantity(ticket.saleItemIds().get(1));
        assertThat(soldBothGrades).isEqualTo(result.successQuantity());
        assertThat(boughtQuantity(buyer, ticket.productId(), ticket.performanceId())).isEqualTo(result.successQuantity());
    }

    /** 초과 판매 없음 + DB 판매 수량 = 저장된 주문 수량 = 성공한 요청 수량 */
    private void assertSoldMatchesOrders(Long saleItemId, Result result) {
        int sold = soldQuantity(saleItemId);
        assertThat(sold).isLessThanOrEqualTo(totalQuantity(saleItemId));
        assertThat(sold).isEqualTo(result.successQuantity());
        assertThat(orderedQuantity(saleItemId)).isEqualTo(sold);
    }
}
