package io.github.jihu1130.ticketonline.concurrency;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.jihu1130.ticketonline.domain.order.dto.OrderRequest;
import io.github.jihu1130.ticketonline.domain.order.dto.OrderResponse;
import io.github.jihu1130.ticketonline.domain.order.entity.PurchaseCountId;
import io.github.jihu1130.ticketonline.domain.order.service.OrderChangeService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.TestPropertySource;

/**
 * 구매 방식 C(Redis)일 때 주문 취소·변경: OrderChangeTest 12개를 그대로 다시 실행하고,
 * 취소·변경이 커밋된 뒤 Redis 남은 수량과 산 수량이 DB 와 같아지는지 확인한다.
 */
@TestPropertySource(properties = "app.purchase.strategy=redis")
class RedisOrderChangeTest extends OrderChangeTest {

    @Autowired private OrderChangeService changes;

    @BeforeEach
    void setUp() {
        cleanRedisOrSkip();
    }

    @Test
    void cancelAndChange_keepRedisEqualToDb() {
        TwoPerformanceTicket ticket = createTicketTwoPerformances(4, 10);
        Long buyer = createMembers(1).get(0);
        Long perf1 = ticket.performanceIds().get(0);
        Long perf2 = ticket.performanceIds().get(1);
        OrderResponse first = purchaseFacade.purchase(buyer, ticket.productId(),
                new OrderRequest(ticket.saleItemIds().get(0), 3, null));
        assertThat(redisStock(ticket.saleItemIds().get(0))).isEqualTo(7);

        changes.reduceQuantity(buyer, first.orderId(), 2);   // 1매 돌려줌
        assertThat(redisStock(ticket.saleItemIds().get(0))).isEqualTo(8);
        assertThat(redisBought(ticket.productId(), perf1, buyer)).isEqualTo(2);

        purchaseFacade.purchase(buyer, ticket.productId(), new OrderRequest(ticket.saleItemIds().get(3), 1, null));
        assertThat(redisStock(ticket.saleItemIds().get(3))).isEqualTo(9);   // 2회차를 불러옴
        changes.changeItem(buyer, first.orderId(), ticket.saleItemIds().get(3)); // 1회차 등급0 2매 -> 2회차 등급1
        assertThat(redisStock(ticket.saleItemIds().get(0))).isEqualTo(10);
        assertThat(redisStock(ticket.saleItemIds().get(3))).isEqualTo(7);
        assertThat(redisBought(ticket.productId(), perf1, buyer)).isZero();
        assertThat(redisBought(ticket.productId(), perf2, buyer)).isEqualTo(3);

        changes.cancel(buyer, first.orderId());
        assertThat(redisStock(ticket.saleItemIds().get(3))).isEqualTo(9);
        assertThat(redisBought(ticket.productId(), perf2, buyer)).isEqualTo(1);

        for (Long itemId : ticket.saleItemIds()) {
            Integer cached = redisStock(itemId);
            if (cached != null) {
                assertThat(cached).as("판매 단위 %d", itemId).isEqualTo(totalQuantity(itemId) - soldQuantity(itemId));
            }
        }
        assertThat(boughtQuantity(buyer, ticket.productId(), perf2)).isEqualTo(1);
        assertThat(boughtQuantity(buyer, ticket.productId(), perf1)).isZero();
    }

    @Test
    void goodsCancel_restoresRedis() {
        GoodsFixture goods = createGoods(5, 5);
        Long buyer = createMembers(1).get(0);
        OrderResponse order = purchaseFacade.purchase(buyer, goods.productId(), new OrderRequest(goods.saleItemId(), 5, null));
        assertThat(redisStock(goods.saleItemId())).isZero();

        changes.cancel(buyer, order.orderId());

        assertThat(redisStock(goods.saleItemId())).isEqualTo(5);
        assertThat(redisBought(goods.productId(), PurchaseCountId.WHOLE_PRODUCT, buyer)).isZero();
    }
}
