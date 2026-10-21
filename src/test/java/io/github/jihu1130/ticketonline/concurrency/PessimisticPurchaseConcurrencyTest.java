package io.github.jihu1130.ticketonline.concurrency;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.jihu1130.ticketonline.domain.order.entity.PurchaseCountId;
import io.github.jihu1130.ticketonline.domain.order.service.PessimisticPurchaseProcessor;
import io.github.jihu1130.ticketonline.global.exception.ErrorCode;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import org.junit.jupiter.api.Test;

/**
 * 구매 방식 A(비관적 락) 동시성 검증 (7단계).
 * 목표: 초과 판매 0건, 1인 한도 준수, 교착(데드락) 없음.
 * 8·9단계에서 B·C 방식도 같은 시나리오로 비교한다.
 */
class PessimisticPurchaseConcurrencyTest extends ConcurrencyTestSupport {

    @Test
    void usesStrategyA() {
        assertThat(purchaseProcessor).isInstanceOf(PessimisticPurchaseProcessor.class);
    }

    @Test
    void stock100_buyers1000_eachOne_sellsExactly100() throws InterruptedException {
        GoodsFixture goods = createGoods(100, 1);
        List<Long> buyers = createMembers(1000);
        List<Purchase> purchases = buyers.stream()
                .map(id -> new Purchase(id, goods.productId(), goods.saleItemId(), 1))
                .toList();

        Result result = runConcurrently("A: 재고 100, 1000명 1개씩", purchases);

        assertThat(result.finished()).isTrue();
        assertThat(result.unexpected()).isEmpty();
        assertThat(result.successCount()).isEqualTo(100);
        assertThat(result.failure(ErrorCode.SOLD_OUT)).isEqualTo(900);
        // DB: 판매 수량 = 주문 수량 = 재고, 남은 수량 0
        assertThat(soldQuantity(goods.saleItemId())).isEqualTo(100);
        assertThat(orderedQuantity(goods.saleItemId())).isEqualTo(100);
        assertThat(orderCount(goods.saleItemId())).isEqualTo(100);
    }

    @Test
    void sameMember_tenRequests_limit2_onlyTwoSucceed() throws InterruptedException {
        TicketFixture ticket = createTicket(2, 100);
        Long buyer = createMembers(1).get(0);
        List<Purchase> purchases = new ArrayList<>();
        for (int i = 0; i < 10; i++) {
            purchases.add(new Purchase(buyer, ticket.productId(), ticket.saleItemIds().get(0), 1));
        }

        Result result = runConcurrently("A: 같은 사람 10번 동시 (회차당 2매)", purchases);

        assertThat(result.unexpected()).isEmpty();
        assertThat(result.successCount()).isEqualTo(2);
        assertThat(result.failure(ErrorCode.LIMIT_EXCEEDED)).isEqualTo(8);
        assertThat(boughtQuantity(buyer, ticket.productId(), ticket.performanceId())).isEqualTo(2);
        assertThat(soldQuantity(ticket.saleItemIds().get(0))).isEqualTo(2);
    }

    @Test
    void mixedQuantities_neverOversell_andNoPartialSale() throws InterruptedException {
        GoodsFixture goods = createGoods(100, 3);
        List<Long> buyers = createMembers(500);
        Random random = new Random(7); // 같은 결과가 나오도록 고정
        List<Purchase> purchases = buyers.stream()
                .map(id -> new Purchase(id, goods.productId(), goods.saleItemId(), 1 + random.nextInt(3)))
                .toList();

        Result result = runConcurrently("A: 재고 100, 500명 1~3개씩", purchases);

        assertThat(result.unexpected()).isEmpty();
        int sold = soldQuantity(goods.saleItemId());
        // 초과 판매 없음, 판매 수량 = 성공한 요청 수량 합계 = 저장된 주문 수량 합계
        assertThat(sold).isLessThanOrEqualTo(100);
        assertThat(sold).isEqualTo(result.successQuantity());
        assertThat(orderedQuantity(goods.saleItemId())).isEqualTo(sold);
        // 실패는 매진 또는 남은 수량 부족(전체 거부)뿐
        assertThat(result.failure(ErrorCode.SOLD_OUT) + result.failure(ErrorCode.INSUFFICIENT_STOCK))
                .isEqualTo(result.requests() - result.successCount());
        // 남은 수량이 3개 이상이면 누군가는 더 살 수 있었다 -> 요청이 이렇게 많으면 남을 수 없다
        assertThat(totalQuantity(goods.saleItemId()) - sold).isLessThan(3);
    }

    @Test
    void sameMember_differentGrades_sameTime_noDeadlockAndLimitKept() throws InterruptedException {
        TicketFixture ticket = createTicket(2, 50, 50);
        Long buyer = createMembers(1).get(0);
        List<Purchase> purchases = new ArrayList<>();
        for (int i = 0; i < 10; i++) {
            Long item = ticket.saleItemIds().get(i % 2); // 두 등급을 번갈아
            purchases.add(new Purchase(buyer, ticket.productId(), item, 1));
        }

        Result result = runConcurrently("A: 같은 사람 두 등급 번갈아 10번 (회차당 2매)", purchases);

        // 데드락 등 예상 밖 예외 없음
        assertThat(result.unexpected()).isEmpty();
        assertThat(result.successCount()).isEqualTo(2);
        assertThat(result.failure(ErrorCode.LIMIT_EXCEEDED)).isEqualTo(8);
        int soldBothGrades = soldQuantity(ticket.saleItemIds().get(0)) + soldQuantity(ticket.saleItemIds().get(1));
        assertThat(soldBothGrades).isEqualTo(2);
        assertThat(boughtQuantity(buyer, ticket.productId(), ticket.performanceId())).isEqualTo(2);
        assertThat(boughtQuantity(buyer, ticket.productId(), PurchaseCountId.WHOLE_PRODUCT)).isZero();
    }
}
