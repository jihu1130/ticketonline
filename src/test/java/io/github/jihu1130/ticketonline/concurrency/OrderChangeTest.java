package io.github.jihu1130.ticketonline.concurrency;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.jihu1130.ticketonline.domain.order.dto.OrderRequest;
import io.github.jihu1130.ticketonline.domain.order.dto.OrderResponse;
import io.github.jihu1130.ticketonline.domain.order.entity.OrderStatus;
import io.github.jihu1130.ticketonline.domain.order.entity.PurchaseCountId;
import io.github.jihu1130.ticketonline.domain.order.service.OrderChangeService;
import io.github.jihu1130.ticketonline.domain.product.entity.DeliveryType;
import io.github.jihu1130.ticketonline.global.exception.CustomException;
import io.github.jihu1130.ticketonline.global.exception.ErrorCode;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * 내 주문 취소·변경 (실제 DB).
 * 확인할 것: 재고(sold_quantity)와 1인 한도(bought_quantity)가 취소·변경한 만큼 정확히 되돌아가는지,
 * 구매와 동시에 일어나도 "판매 수량 = 취소되지 않은 주문 수량 합계"가 유지되는지, 교착이 없는지.
 */
class OrderChangeTest extends ConcurrencyTestSupport {

    @Autowired private OrderChangeService orderChangeService;

    // ---------- 전체 취소 ----------

    @Test
    void cancel_restoresStockAndLimit() {
        GoodsFixture goods = createGoods(10, 2);
        Long buyer = createMembers(1).get(0);
        OrderResponse order = buy(buyer, goods.productId(), goods.saleItemId(), 2);

        OrderResponse canceled = orderChangeService.cancel(buyer, order.orderId());

        assertThat(canceled.status()).isEqualTo(OrderStatus.CANCELED);
        assertThat(canceled.changeableUntil()).isNull();
        assertThat(soldQuantity(goods.saleItemId())).isZero();
        assertThat(boughtQuantity(buyer, goods.productId(), PurchaseCountId.WHOLE_PRODUCT)).isZero();
        // 한도가 돌아왔으니 다시 2개 살 수 있다
        buy(buyer, goods.productId(), goods.saleItemId(), 2);
        assertThat(soldQuantity(goods.saleItemId())).isEqualTo(orderedQuantity(goods.saleItemId())).isEqualTo(2);
    }

    @Test
    void cancel_twice_alreadyCanceled() {
        GoodsFixture goods = createGoods(10, 2);
        Long buyer = createMembers(1).get(0);
        OrderResponse order = buy(buyer, goods.productId(), goods.saleItemId(), 1);
        orderChangeService.cancel(buyer, order.orderId());

        assertError(() -> orderChangeService.cancel(buyer, order.orderId()), ErrorCode.ALREADY_CANCELED);
        assertThat(soldQuantity(goods.saleItemId())).isZero();
    }

    @Test
    void othersOrder_looksLikeNotFound() {
        GoodsFixture goods = createGoods(10, 2);
        List<Long> members = createMembers(2);
        OrderResponse order = buy(members.get(0), goods.productId(), goods.saleItemId(), 1);

        assertError(() -> orderChangeService.cancel(members.get(1), order.orderId()), ErrorCode.ORDER_NOT_FOUND);
        assertError(() -> orderChangeService.cancel(members.get(1), -1L), ErrorCode.ORDER_NOT_FOUND);
    }

    @Test
    void afterBookingClose_cannotCancelOrChange() {
        TwoPerformanceTicket ticket = createTicketTwoPerformances(4, 10);
        Long buyer = createMembers(1).get(0);
        OrderResponse order = buy(buyer, ticket.productId(), ticket.saleItemIds().get(0), 2);
        // 공연 1시간 전 마감인데 공연이 30분 뒤 -> 마감됨
        jdbc.update("update performance set event_at = date_add(now(), interval 30 minute) where performance_id = ?",
                ticket.performanceIds().get(0));

        assertError(() -> orderChangeService.cancel(buyer, order.orderId()), ErrorCode.CHANGE_CLOSED);
        assertError(() -> orderChangeService.reduceQuantity(buyer, order.orderId(), 1), ErrorCode.CHANGE_CLOSED);
        assertThat(soldQuantity(ticket.saleItemIds().get(0))).isEqualTo(2);
    }

    // ---------- 수량 줄이기 ----------

    @Test
    void reduceQuantity_returnsTheDifference() {
        GoodsFixture goods = createGoods(10, 4);
        Long buyer = createMembers(1).get(0);
        OrderResponse order = buy(buyer, goods.productId(), goods.saleItemId(), 3);

        OrderResponse reduced = orderChangeService.reduceQuantity(buyer, order.orderId(), 1);

        assertThat(reduced.quantity()).isEqualTo(1);
        assertThat(reduced.totalPrice()).isEqualTo(10000);
        assertThat(reduced.changeableUntil()).isNotNull();
        assertThat(soldQuantity(goods.saleItemId())).isEqualTo(1);
        assertThat(boughtQuantity(buyer, goods.productId(), PurchaseCountId.WHOLE_PRODUCT)).isEqualTo(1);
        assertError(() -> orderChangeService.reduceQuantity(buyer, order.orderId(), 1), ErrorCode.INVALID_INPUT);
        assertError(() -> orderChangeService.reduceQuantity(buyer, order.orderId(), 5), ErrorCode.INVALID_INPUT);
    }

    // ---------- 배송지 변경 ----------

    @Test
    void changeAddress_onlyParcelGoods() {
        GoodsFixture parcel = createGoods(10, 2, DeliveryType.PARCEL);
        GoodsFixture pickup = createGoods(10, 2, DeliveryType.PICKUP);
        Long buyer = createMembers(1).get(0);
        OrderResponse parcelOrder = purchaseFacade.purchase(buyer, parcel.productId(),
                new OrderRequest(parcel.saleItemId(), 1, "서울시 송파구"));
        OrderResponse pickupOrder = buy(buyer, pickup.productId(), pickup.saleItemId(), 1);

        OrderResponse changed = orderChangeService.changeAddress(buyer, parcelOrder.orderId(), "  부산시 해운대구  ");

        assertThat(changed.address()).isEqualTo("부산시 해운대구");
        assertError(() -> orderChangeService.changeAddress(buyer, pickupOrder.orderId(), "주소"), ErrorCode.INVALID_INPUT);
    }

    // ---------- 회차·등급 바꾸기 ----------

    @Test
    void changeItem_samePerformance_movesStockKeepsLimit() {
        TwoPerformanceTicket ticket = createTicketTwoPerformances(4, 10);
        Long buyer = createMembers(1).get(0);
        Long grade0 = ticket.saleItemIds().get(0);
        Long grade1 = ticket.saleItemIds().get(1);
        OrderResponse order = buy(buyer, ticket.productId(), grade0, 2);

        OrderResponse changed = orderChangeService.changeItem(buyer, order.orderId(), grade1);

        assertThat(changed.orderId()).isEqualTo(order.orderId());
        assertThat(changed.saleItemId()).isEqualTo(grade1);
        assertThat(changed.unitPrice()).isEqualTo(20000);
        assertThat(changed.totalPrice()).isEqualTo(40000);
        assertThat(soldQuantity(grade0)).isZero();
        assertThat(soldQuantity(grade1)).isEqualTo(2);
        assertThat(boughtQuantity(buyer, ticket.productId(), ticket.performanceIds().get(0))).isEqualTo(2);
        assertError(() -> orderChangeService.changeItem(buyer, order.orderId(), grade1), ErrorCode.INVALID_INPUT);
    }

    @Test
    void changeItem_otherPerformance_checksThatPerformanceLimit() {
        TwoPerformanceTicket ticket = createTicketTwoPerformances(2, 10);
        Long buyer = createMembers(1).get(0);
        Long perf1 = ticket.performanceIds().get(0);
        Long perf2 = ticket.performanceIds().get(1);
        OrderResponse order = buy(buyer, ticket.productId(), ticket.saleItemIds().get(0), 2);
        OrderResponse perf2Order = buy(buyer, ticket.productId(), ticket.saleItemIds().get(2), 1);

        // 2회차는 이미 1매라 2매를 옮길 수 없다 -> 원래 주문 그대로
        assertError(() -> orderChangeService.changeItem(buyer, order.orderId(), ticket.saleItemIds().get(3)),
                ErrorCode.LIMIT_EXCEEDED);
        assertThat(soldQuantity(ticket.saleItemIds().get(0))).isEqualTo(2);
        assertThat(boughtQuantity(buyer, ticket.productId(), perf1)).isEqualTo(2);

        orderChangeService.cancel(buyer, perf2Order.orderId());
        OrderResponse changed = orderChangeService.changeItem(buyer, order.orderId(), ticket.saleItemIds().get(3));

        assertThat(changed.performanceId()).isEqualTo(perf2);
        assertThat(boughtQuantity(buyer, ticket.productId(), perf1)).isZero();
        assertThat(boughtQuantity(buyer, ticket.productId(), perf2)).isEqualTo(2);
        assertThat(soldQuantity(ticket.saleItemIds().get(0))).isZero();
        assertThat(soldQuantity(ticket.saleItemIds().get(3))).isEqualTo(2);
    }

    @Test
    void changeItem_soldOut_keepsOriginalOrder() {
        TwoPerformanceTicket ticket = createTicketTwoPerformances(4, 2);
        List<Long> members = createMembers(2);
        Long grade0 = ticket.saleItemIds().get(0);
        Long grade1 = ticket.saleItemIds().get(1);
        OrderResponse order = buy(members.get(0), ticket.productId(), grade0, 1);
        buy(members.get(1), ticket.productId(), grade1, 2);

        assertError(() -> orderChangeService.changeItem(members.get(0), order.orderId(), grade1), ErrorCode.SOLD_OUT);
        assertThat(soldQuantity(grade0)).isEqualTo(1);
        assertThat(soldQuantity(grade1)).isEqualTo(2);
    }

    @Test
    void changeItem_goodsOrOtherProduct_rejected() {
        GoodsFixture goods = createGoods(10, 2);
        TwoPerformanceTicket ticket = createTicketTwoPerformances(4, 10);
        TwoPerformanceTicket other = createTicketTwoPerformances(4, 10);
        Long buyer = createMembers(1).get(0);
        OrderResponse goodsOrder = buy(buyer, goods.productId(), goods.saleItemId(), 1);
        OrderResponse ticketOrder = buy(buyer, ticket.productId(), ticket.saleItemIds().get(0), 1);

        assertError(() -> orderChangeService.changeItem(buyer, goodsOrder.orderId(), goods.saleItemId()),
                ErrorCode.INVALID_INPUT);
        assertError(() -> orderChangeService.changeItem(buyer, ticketOrder.orderId(), other.saleItemIds().get(1)),
                ErrorCode.INVALID_INPUT);
    }

    // ---------- 동시성 ----------

    @Test
    void cancelsAndPurchasesAtOnce_soldEqualsOrdered() throws InterruptedException {
        GoodsFixture goods = createGoods(50, 1);
        List<Long> firstBuyers = createMembers(50);
        List<Long> orderIds = new ArrayList<>();
        for (Long buyer : firstBuyers) {
            orderIds.add(buy(buyer, goods.productId(), goods.saleItemId(), 1).orderId());
        }
        List<Long> newBuyers = createMembers(100);

        List<Callable<Object>> tasks = new ArrayList<>();
        for (int i = 0; i < firstBuyers.size(); i++) {
            Long buyer = firstBuyers.get(i);
            Long orderId = orderIds.get(i);
            tasks.add(() -> orderChangeService.cancel(buyer, orderId));
        }
        for (Long buyer : newBuyers) {
            tasks.add(() -> purchaseFacade.purchase(buyer, goods.productId(), new OrderRequest(goods.saleItemId(), 1, null)));
        }
        List<Throwable> unexpected = runAll(tasks);

        assertThat(unexpected).isEmpty();
        int sold = soldQuantity(goods.saleItemId());
        assertThat(sold).isEqualTo(orderedQuantity(goods.saleItemId())).isLessThanOrEqualTo(50);
        // 취소 50건이 모두 되었으니 새 구매자는 자리가 난 만큼(최대 50명) 샀다
        assertThat(sold).isEqualTo(orderCount(goods.saleItemId()));
    }

    @Test
    void changeItemBothWaysAtOnce_noDeadlock() throws InterruptedException {
        TwoPerformanceTicket ticket = createTicketTwoPerformances(4, 100);
        Long grade0 = ticket.saleItemIds().get(0);
        Long grade1 = ticket.saleItemIds().get(1);
        List<Long> members = createMembers(40);
        List<Callable<Object>> tasks = new ArrayList<>();
        for (int i = 0; i < members.size(); i++) {
            Long member = members.get(i);
            Long from = i % 2 == 0 ? grade0 : grade1;
            Long to = i % 2 == 0 ? grade1 : grade0;
            Long orderId = buy(member, ticket.productId(), from, 1).orderId();
            tasks.add(() -> orderChangeService.changeItem(member, orderId, to));
        }

        List<Throwable> unexpected = runAll(tasks);

        assertThat(unexpected).isEmpty();
        assertThat(soldQuantity(grade0)).isEqualTo(orderedQuantity(grade0)).isEqualTo(20);
        assertThat(soldQuantity(grade1)).isEqualTo(orderedQuantity(grade1)).isEqualTo(20);
    }

    // ---------- 도우미 ----------

    private OrderResponse buy(Long memberId, Long productId, Long saleItemId, int quantity) {
        return purchaseFacade.purchase(memberId, productId, new OrderRequest(saleItemId, quantity, null));
    }

    private static void assertError(org.assertj.core.api.ThrowableAssert.ThrowingCallable call, ErrorCode code) {
        assertThatThrownBy(call).isInstanceOf(CustomException.class).extracting("errorCode").isEqualTo(code);
    }

    /** 한 번에 출발시키고, 업무 오류(매진 등)가 아닌 예외(교착 등)만 모은다 */
    private static List<Throwable> runAll(List<Callable<Object>> tasks) throws InterruptedException {
        ExecutorService pool = Executors.newFixedThreadPool(THREADS);
        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(tasks.size());
        List<Throwable> unexpected = new CopyOnWriteArrayList<>();
        for (Callable<Object> task : tasks) {
            pool.submit(() -> {
                try {
                    start.await();
                    task.call();
                } catch (CustomException ignored) {
                    // 매진·한도 초과 같은 정상적인 거절
                } catch (Throwable e) {
                    unexpected.add(e);
                } finally {
                    done.countDown();
                }
            });
        }
        start.countDown();
        assertThat(done.await(120, TimeUnit.SECONDS)).isTrue();
        pool.shutdownNow();
        return unexpected;
    }
}
