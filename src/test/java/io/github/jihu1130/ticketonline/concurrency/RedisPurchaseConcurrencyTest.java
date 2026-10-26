package io.github.jihu1130.ticketonline.concurrency;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.jihu1130.ticketonline.domain.order.dto.OrderRequest;
import io.github.jihu1130.ticketonline.domain.order.entity.PurchaseCountId;
import io.github.jihu1130.ticketonline.domain.order.service.RedisPurchaseProcessor;
import io.github.jihu1130.ticketonline.global.exception.CustomException;
import io.github.jihu1130.ticketonline.global.exception.ErrorCode;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.context.TestPropertySource;

/**
 * 구매 방식 C(Redis) 동시성 검증 (9단계). 방식 A·B 와 같은 시나리오.
 * 목표: 초과 판매 0건, 1인 한도 준수, 그리고 끝난 뒤 Redis 남은 수량 = DB 남은 수량.
 */
@TestPropertySource(properties = "app.purchase.strategy=redis")
class RedisPurchaseConcurrencyTest extends ConcurrencyTestSupport {

    @BeforeEach
    void setUp() {
        cleanRedisOrSkip();
    }

    @Test
    void usesStrategyC() {
        assertThat(purchaseProcessor).isInstanceOf(RedisPurchaseProcessor.class);
    }

    @Test
    void stock100_buyers1000_eachOne_sellsExactly100() throws InterruptedException {
        GoodsFixture goods = createGoods(100, 1);
        List<Purchase> purchases = createMembers(1000).stream()
                .map(id -> new Purchase(id, goods.productId(), goods.saleItemId(), 1))
                .toList();

        Result result = runConcurrently("C: 재고 100, 1000명 1개씩", purchases);

        assertThat(result.finished()).isTrue();
        assertThat(result.unexpected()).isEmpty();
        assertThat(result.successCount()).isEqualTo(100);
        assertThat(result.failure(ErrorCode.SOLD_OUT)).isEqualTo(900);
        assertSoldMatchesOrders(goods.saleItemId(), result);
        assertThat(redisStock(goods.saleItemId())).isZero();
    }

    @Test
    void mixedQuantities_neverOversell_andNoPartialSale() throws InterruptedException {
        GoodsFixture goods = createGoods(100, 3);
        Random random = new Random(7); // A·B 와 같은 요청
        List<Purchase> purchases = createMembers(500).stream()
                .map(id -> new Purchase(id, goods.productId(), goods.saleItemId(), 1 + random.nextInt(3)))
                .toList();

        Result result = runConcurrently("C: 재고 100, 500명 1~3개씩", purchases);

        assertThat(result.unexpected()).isEmpty();
        assertSoldMatchesOrders(goods.saleItemId(), result);
        assertThat(soldQuantity(goods.saleItemId())).isEqualTo(100); // 1개 요청이 많아 끝까지 다 팔린다
        assertThat(result.failure(ErrorCode.SOLD_OUT) + result.failure(ErrorCode.INSUFFICIENT_STOCK))
                .isEqualTo(result.requests() - result.successCount());
        assertThat(redisStock(goods.saleItemId())).isZero();
    }

    @Test
    void sameMember_tenRequests_limit2_onlyTwoSucceed() throws InterruptedException {
        TicketFixture ticket = createTicket(2, 100);
        Long buyer = createMembers(1).get(0);
        List<Purchase> purchases = new ArrayList<>();
        for (int i = 0; i < 10; i++) {
            purchases.add(new Purchase(buyer, ticket.productId(), ticket.saleItemIds().get(0), 1));
        }

        Result result = runConcurrently("C: 같은 사람 10번 동시 (회차당 2매)", purchases);

        assertThat(result.unexpected()).isEmpty();
        assertThat(result.successCount()).isEqualTo(2);
        assertThat(result.failure(ErrorCode.LIMIT_EXCEEDED)).isEqualTo(8);
        assertThat(boughtQuantity(buyer, ticket.productId(), ticket.performanceId())).isEqualTo(2);
        assertThat(redisBought(ticket.productId(), ticket.performanceId(), buyer)).isEqualTo(2);
        assertSoldMatchesOrders(ticket.saleItemIds().get(0), result);
    }

    @Test
    void sameMember_differentGrades_sameTime_limitKept() throws InterruptedException {
        TicketFixture ticket = createTicket(2, 50, 50);
        Long buyer = createMembers(1).get(0);
        List<Purchase> purchases = new ArrayList<>();
        for (int i = 0; i < 10; i++) {
            purchases.add(new Purchase(buyer, ticket.productId(), ticket.saleItemIds().get(i % 2), 1));
        }

        Result result = runConcurrently("C: 같은 사람 두 등급 번갈아 10번 (회차당 2매)", purchases);

        assertThat(result.unexpected()).isEmpty();
        assertThat(result.successCount()).isEqualTo(2);
        int soldBothGrades = soldQuantity(ticket.saleItemIds().get(0)) + soldQuantity(ticket.saleItemIds().get(1));
        assertThat(soldBothGrades).isEqualTo(2);
        assertThat(boughtQuantity(buyer, ticket.productId(), ticket.performanceId())).isEqualTo(2);
    }

    @Test
    void redisValuesAreLoadedFromDb() {
        GoodsFixture goods = createGoods(10, 5);
        Long buyer = createMembers(1).get(0);
        // Redis 가 비어 있는 상태에서 DB 에 이미 3개 팔린 기록이 있다 (예: Redis 를 껐다 켬)
        jdbc.update("update sale_item set sold_quantity = 3 where sale_item_id = ?", goods.saleItemId());

        purchaseFacade.purchase(buyer, goods.productId(), new OrderRequest(goods.saleItemId(), 2, null));

        assertThat(redisStock(goods.saleItemId())).isEqualTo(5); // 10 - 3 - 2
        assertThat(redisBought(goods.productId(), PurchaseCountId.WHOLE_PRODUCT, buyer)).isEqualTo(2);
        assertThat(soldQuantity(goods.saleItemId())).isEqualTo(5);
    }

    @Test
    void dbRejects_redisIsRestored() {
        GoodsFixture goods = createGoods(10, 5);
        List<Long> buyers = createMembers(2);
        purchaseFacade.purchase(buyers.get(0), goods.productId(), new OrderRequest(goods.saleItemId(), 1, null));
        assertThat(redisStock(goods.saleItemId())).isEqualTo(9);
        // Redis 는 9개 남았다고 알지만 DB 는 매진 (어긋난 상황을 일부러 만든다)
        jdbc.update("update sale_item set sold_quantity = total_quantity where sale_item_id = ?", goods.saleItemId());

        assertThatThrownBy(() -> purchaseFacade.purchase(buyers.get(1), goods.productId(),
                new OrderRequest(goods.saleItemId(), 1, null)))
                .isInstanceOf(CustomException.class).extracting("errorCode").isEqualTo(ErrorCode.SOLD_OUT);

        // DB 에는 아무것도 남지 않고, Redis 산 수량은 되돌려지고, 재고 키는 지워져 다음에 DB 값으로 다시 채워진다
        assertThat(orderCount(goods.saleItemId())).isEqualTo(1);
        assertThat(boughtQuantity(buyers.get(1), goods.productId(), PurchaseCountId.WHOLE_PRODUCT)).isZero();
        assertThat(redisBought(goods.productId(), PurchaseCountId.WHOLE_PRODUCT, buyers.get(1))).isZero();
        assertThat(redisStock(goods.saleItemId())).isNull();
        assertThatThrownBy(() -> purchaseFacade.purchase(buyers.get(1), goods.productId(),
                new OrderRequest(goods.saleItemId(), 1, null)))
                .extracting("errorCode").isEqualTo(ErrorCode.SOLD_OUT);
        assertThat(redisStock(goods.saleItemId())).isZero();
    }

    /** 초과 판매 없음 + DB 판매 수량 = 저장된 주문 수량 = 성공한 요청 수량 */
    private void assertSoldMatchesOrders(Long saleItemId, Result result) {
        int sold = soldQuantity(saleItemId);
        assertThat(sold).isLessThanOrEqualTo(totalQuantity(saleItemId));
        assertThat(sold).isEqualTo(result.successQuantity());
        assertThat(orderedQuantity(saleItemId)).isEqualTo(sold);
    }
}
