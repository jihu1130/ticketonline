package io.github.jihu1130.ticketonline.concurrency;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.jihu1130.ticketonline.domain.order.dto.OrderRequest;
import io.github.jihu1130.ticketonline.domain.order.entity.PurchaseCountId;
import io.github.jihu1130.ticketonline.domain.product.entity.DeliveryType;
import io.github.jihu1130.ticketonline.global.exception.CustomException;
import io.github.jihu1130.ticketonline.global.exception.ErrorCode;
import jakarta.persistence.EntityManagerFactory;
import java.util.List;
import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.TestPropertySource;

/**
 * 방식 C 개선: DB 연결을 잡기 전에 Redis 에서 거절한다 (PurchaseFacade -> RedisStock.reserveAhead).
 * Hibernate 통계로 "DB 에 보낸 쿼리 수"를 세어 매진 요청이 DB 를 전혀 쓰지 않는지 확인한다.
 */
@TestPropertySource(properties = {"app.purchase.strategy=redis", "spring.jpa.properties.hibernate.generate_statistics=true"})
class RedisFastRejectTest extends ConcurrencyTestSupport {

    @Autowired private EntityManagerFactory entityManagerFactory;
    private Statistics statistics;

    @BeforeEach
    void setUp() {
        cleanRedisOrSkip();
        statistics = entityManagerFactory.unwrap(SessionFactory.class).getStatistics();
    }

    @Test
    void soldOut_rejectedWithoutAnyDbQuery() {
        GoodsFixture goods = createGoods(1, 1);
        List<Long> buyers = createMembers(2);
        purchaseFacade.purchase(buyers.get(0), goods.productId(), new OrderRequest(goods.saleItemId(), 1, null));

        statistics.clear();
        assertError(() -> purchaseFacade.purchase(buyers.get(1), goods.productId(),
                new OrderRequest(goods.saleItemId(), 1, null)), ErrorCode.SOLD_OUT);

        assertThat(statistics.getPrepareStatementCount()).as("매진 요청이 DB 에 보낸 쿼리 수").isZero();
        assertThat(redisStock(goods.saleItemId())).isZero();
    }

    @Test
    void limitExceeded_rejectedWithoutAnyDbQuery() {
        GoodsFixture goods = createGoods(10, 1);
        Long buyer = createMembers(1).get(0);
        purchaseFacade.purchase(buyer, goods.productId(), new OrderRequest(goods.saleItemId(), 1, null));

        statistics.clear();
        assertError(() -> purchaseFacade.purchase(buyer, goods.productId(),
                new OrderRequest(goods.saleItemId(), 1, null)), ErrorCode.LIMIT_EXCEEDED);

        assertThat(statistics.getPrepareStatementCount()).isZero();
        assertThat(redisStock(goods.saleItemId())).isEqualTo(9);
    }

    @Test
    void otherProductsItem_orNotOnSale_rejectedBeforeReserving() {
        GoodsFixture goods = createGoods(10, 2);
        GoodsFixture other = createGoods(10, 2);
        Long buyer = createMembers(1).get(0);

        assertError(() -> purchaseFacade.purchase(buyer, goods.productId(),
                new OrderRequest(other.saleItemId(), 1, null)), ErrorCode.INVALID_INPUT);
        assertThat(redisStock(other.saleItemId())).isNull(); // 차감하지 않아 재고를 불러오지도 않았다

        GoodsFixture upcoming = createGoods(10, 2);
        jdbc.update("update product set sale_start_at = date_add(now(), interval 1 day) where product_id = ?",
                upcoming.productId());
        assertError(() -> purchaseFacade.purchase(buyer, upcoming.productId(),
                new OrderRequest(upcoming.saleItemId(), 1, null)), ErrorCode.NOT_ON_SALE);
        assertThat(redisStock(upcoming.saleItemId())).isNull();
    }

    @Test
    void dbCheckFails_redisReservationIsReleased() {
        // 택배 굿즈인데 주소가 없다: Redis 는 통과하지만 DB 단계(OrderService)에서 거절된다
        GoodsFixture parcel = createGoods(10, 2, DeliveryType.PARCEL);
        Long buyer = createMembers(1).get(0);

        assertError(() -> purchaseFacade.purchase(buyer, parcel.productId(),
                new OrderRequest(parcel.saleItemId(), 2, null)), ErrorCode.INVALID_INPUT);

        assertThat(redisStock(parcel.saleItemId())).isEqualTo(10);
        assertThat(redisBought(parcel.productId(), PurchaseCountId.WHOLE_PRODUCT, buyer)).isZero();
        assertThat(soldQuantity(parcel.saleItemId())).isZero();

        // 주소를 넣으면 산다
        purchaseFacade.purchase(buyer, parcel.productId(), new OrderRequest(parcel.saleItemId(), 2, "서울시 송파구"));
        assertThat(redisStock(parcel.saleItemId())).isEqualTo(8);
        assertThat(soldQuantity(parcel.saleItemId())).isEqualTo(2);
    }

    private static void assertError(org.assertj.core.api.ThrowableAssert.ThrowingCallable call, ErrorCode code) {
        assertThatThrownBy(call).isInstanceOf(CustomException.class).extracting("errorCode").isEqualTo(code);
    }
}
